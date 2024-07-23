/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.editor.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.ui.LayeredIcon
import com.intellij.util.Processor
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaUnknownClassMemberIndex
import com.tang.intellij.lua.ty.*
import com.vladsch.flexmark.util.html.ui.Color

enum class MemberCompletionMode {
    Dot,    // self.xxx
    Colon,  // self:xxx()
    All     // self.xxx && self:xxx()
}

/**

 * Created by tangzx on 2016/12/25.
 */
open class ClassMemberCompletionProvider : LuaCompletionProvider() {
    protected abstract class HandlerProcessor {
        open fun processLookupString(lookupString: String, member: LuaClassMember, memberTy: ITy?): String = lookupString
        abstract fun process(element: LuaLookupElement, member: LuaClassMember, memberTy: ITy?): LookupElement
    }

    internal class OverrideInsertHandler() : ArgsInsertHandler() {
        override val isVarargs: Boolean
            get() = true
        override fun getParams(): Array<LuaParamInfo> {
            return emptyArray<LuaParamInfo>()
        }
    }

    override fun addCompletions(session: CompletionSession) {
        val completionParameters = session.parameters
        val completionResultSet = session.resultSet

        val psi = completionParameters.position
        val indexExpr = psi.parent

        if (indexExpr is LuaIndexExpr) {
            val isColon = indexExpr.colon != null
            val project = indexExpr.project
            val contextTy = LuaPsiTreeUtil.findContextClass(indexExpr)
            val context = SearchContext.get(project)
            val prefixType = indexExpr.guessParentType(context)
            if (!Ty.isInvalid(prefixType)) {
                complete(isColon, project, contextTy, prefixType, completionResultSet, completionResultSet.prefixMatcher, null)
            }
            //smart
            val nameExpr = indexExpr.prefixExpr
            if (nameExpr is LuaNameExpr) {
                val colon = if (isColon) ":" else "."
                val prefixName = nameExpr.text
                val postfixName = indexExpr.name?.let { it.substring(0, it.indexOf(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)) }

                val matcher = completionResultSet.prefixMatcher.cloneWithPrefix(prefixName)
                LuaDeclarationTree.get(indexExpr.containingFile).walkUpLocal(indexExpr) { d ->
                    val it = d.firstDeclaration.psi
                    val txt = it.name
                    if (it is LuaTypeGuessable && txt != null && prefixName != txt && matcher.prefixMatches(txt)) {
                        val type = it.guessType(context)
                        if (!Ty.isInvalid(prefixType)) {
                            val prefixMatcher = completionResultSet.prefixMatcher
                            val resultSet = completionResultSet.withPrefixMatcher("$prefixName*$postfixName")
                            complete(isColon, project, contextTy, type, resultSet, prefixMatcher, object : HandlerProcessor() {
                                override fun process(element: LuaLookupElement, member: LuaClassMember, memberTy: ITy?): LookupElement {
                                    element.itemText = txt + colon + element.itemText
                                    element.lookupString = txt + colon + element.lookupString
                                    return PrioritizedLookupElement.withPriority(element, -2.0)
                                }
                            })
                        }
                    }
                    true
                }
            }

            // 显示未知调用
            if (LuaSettings.instance.isShowUnknownMethod) {
                var show = true
                if (indexExpr.name == Constants.WORD_SELF) {
                    show = false
                }
                val last = indexExpr.exprList.last()
                if (show) {
                    var prefix = last.name ?: ""
                    if (prefix.isNotBlank()) {
                        prefix = prefix.replace(Regex("_.*"), "")
                        val allKeys = LuaUnknownClassMemberIndex.find(prefix.hashCode(), context)
                        val matchKeySet = HashSet<String>()
                        val occurrences = hashMapOf<String, Int>()
                        allKeys.forEach {
                            val functionName = it.name ?: return@forEach
                            occurrences[functionName] = occurrences.getOrDefault(functionName, 0) + 1
                        }
                        occurrences.forEach { (key, value) ->
                            if (value > 1 && session.addWord(key))
                            {
                                matchKeySet.add(key)
                            }
                        }
                        matchKeySet.forEach {
                            val item = LookupElementBuilder.create(it)
                                .withIcon(LuaIcons.UNKNOWN_METHOD)
                                .withItemTextItalic(true)
                                .withTailText(" ?")
                                .withItemTextForeground(Color.GRAY)
                                .withInsertHandler(OverrideInsertHandler())
//                                .withTypeText("$prefix?", true)
                            completionResultSet.addElement(
                                PrioritizedLookupElement.withPriority(item, -0.5)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun complete(isColon: Boolean,
                         project: Project,
                         contextTy: ITy,
                         prefixType: ITy,
                         completionResultSet: CompletionResultSet,
                         prefixMatcher: PrefixMatcher,
                         handlerProcessor: HandlerProcessor?) {
        val mode = if (isColon) MemberCompletionMode.Colon else MemberCompletionMode.Dot
        prefixType.eachTopClass(Processor { luaType ->
            addClass(contextTy, luaType, project, mode, completionResultSet, prefixMatcher, handlerProcessor)
            true
        })
    }

    protected fun addClass(contextTy: ITy,
                           luaType:ITyClass,
                           project: Project,
                           completionMode:MemberCompletionMode,
                           completionResultSet: CompletionResultSet,
                           prefixMatcher: PrefixMatcher,
                           handlerProcessor: HandlerProcessor?) {
        val context = SearchContext.get(project)
        luaType.lazyInit(context)
        luaType.processMembers(context) { curType, member ->
            ProgressManager.checkCanceled()
            member.name?.let {
                if (prefixMatcher.prefixMatches(it) && curType.isVisibleInScope(project, contextTy, member.visibility)) {
                    addMember(completionResultSet,
                            member,
                            curType,
                            luaType,
                            completionMode,
                            project,
                            handlerProcessor)
                }
            }
        }
    }

    protected fun addMember(completionResultSet: CompletionResultSet,
                            member: LuaClassMember,
                            thisType: ITyClass,
                            callType: ITyClass,
                            completionMode: MemberCompletionMode,
                            project: Project,
                            handlerProcessor: HandlerProcessor?) {
        val type = member.guessType(SearchContext.get(project))
        val bold = thisType == callType
        val className = thisType.displayName
        if (type is ITyFunction) {
            val fn = type.substitute(TySelfSubstitutor(project, null, callType))
            if (fn is ITyFunction)
                addFunction(completionResultSet, bold, completionMode != MemberCompletionMode.Dot, className, member, fn, thisType, callType, handlerProcessor)
        } else if (member is LuaClassField) {
            if (completionMode != MemberCompletionMode.Colon)
                addField(completionResultSet, bold, className, member, type, handlerProcessor)
        }
    }

    protected fun addField(completionResultSet: CompletionResultSet,
                           bold: Boolean,
                           clazzName: String,
                           field: LuaClassField,
                           ty:ITy?,
                           handlerProcessor: HandlerProcessor?) {
        val name = field.name
        if (name != null) {
            this.session?.addWord(name)
            val element = LookupElementFactory.createFieldLookupElement(clazzName, name, field, ty, bold)
            val ele = handlerProcessor?.process(element, field, null) ?: element
            completionResultSet.addElement(ele)
        }
    }

    private fun addFunction(completionResultSet: CompletionResultSet,
                            bold: Boolean,
                            isColonStyle: Boolean,
                            clazzName: String,
                            classMember: LuaClassMember,
                            fnTy: ITyFunction,
                            thisType: ITyClass,
                            callType: ITyClass,
                            handlerProcessor: HandlerProcessor?) {
        val name = classMember.name
        if (name != null) {
            this.session?.addWord(name)
            fnTy.process(Processor {

                val firstParam = it.getFirstParam(thisType, isColonStyle)
                if (isColonStyle) {
                    if (firstParam == null) return@Processor true
                    if (!callType.subTypeOf(firstParam.ty, SearchContext.get(classMember.project), true))
                        return@Processor true
                }

                val lookupString = handlerProcessor?.processLookupString(name, classMember, fnTy) ?: name

                val element = LookupElementFactory.createMethodLookupElement(clazzName,
                        lookupString,
                        classMember,
                        it,
                        bold,
                        isColonStyle,
                        fnTy,
                        LuaIcons.CLASS_METHOD)
                val ele = handlerProcessor?.process(element, classMember, fnTy) ?: element
                completionResultSet.addElement(ele)
                true
            })
        }
    }
}
