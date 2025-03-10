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

package com.tang.intellij.lua.codeInsight.template.macro

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.editor.LuaNameSuggestionProvider
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

/**
 *
 * Created by TangZX on 2017/4/8.
 */
class SuggestFirstLuaVarNameMacro : Macro() {
    override fun getName(): String {
        return "SuggestFirstLuaVarName"
    }

    override fun getPresentableName(): String {
        return "SuggestFirstLuaVarName(lastID)"
    }

    fun getLastID(expressions: Array<out Expression>, expressionContext: ExpressionContext): String? {
        if (expressions.isEmpty()) {
            return null
        }
        if (expressions.first() !is VariableNode) {
            return null
        }
        if ((expressions.first() as VariableNode).name != "true") {
            return null
        }


        val editor = expressionContext.editor
        if (editor == null) {
            return null
        }
        var template = TemplateManager.getInstance(expressionContext.project).getActiveTemplate(editor) ?: return null
        var index = -1
        for ((i, variable) in template.variables.withIndex()) {
            if (variable.expression is MacroCallNode && (variable.expression as MacroCallNode).macro == this) {
                index = i
                break
            }
        }
        if (index == -1) {
            return null
        }
        var templateState = TemplateManagerImpl.getTemplateState(editor) ?: return null
        if (templateState.currentVariableNumber != index) {
            var isMatch = false
            if (templateState.currentVariableNumber + 1 == index) {
                val stackTrace = Thread.currentThread().stackTrace
                stackTrace.iterator().forEach { stackTraceElement ->
                    if (stackTraceElement.methodName == "nextTab") {
                        isMatch = true
                        return@forEach
                    }
                }
            }
            if (!isMatch) {
                return null
            }
        }

        // 获取当前正确的元素
        var element = expressionContext.psiElementAtStartOffset

        if (element != null) {
            element = element.containingFile.findElementAt(editor.caretModel.offset)
            if (element != null) {
                element = PsiTreeUtil.getParentOfType(element, LuaLocalDef::class.java)
            }
        }
        // 如果当前是定义的话，根据赋值后半部分来返回元素名
        if (element is LuaLocalDef) {
            val lastChild = element.exprList?.lastChild
            if (lastChild is LuaTypeGuessable) {
                val e = PsiTreeUtil.getDeepestVisibleLast(lastChild)
                return getElementSuggestName(e, lastChild)
            }
        }
        return null
    }

    override fun calculateResult(expressions: Array<Expression>, expressionContext: ExpressionContext): Result? {
        val lastID = getLastID(expressions, expressionContext)
        if (lastID != null) {
            return TextResult(lastID)
        }
        return null
    }

    override fun calculateLookupItems(params: Array<out Expression>, context: ExpressionContext): Array<LookupElement>? {
        val list = mutableListOf<LookupElement>()
        val lastID = getLastID(params, context)
        if (lastID != null) {
            list.add(LookupElementBuilder.create(lastID))
        } else {
            val pin = context.psiElementAtStartOffset
            if (pin != null) {
                LuaDeclarationTree.get(pin.containingFile).walkUpLocal(pin) {
                    list.add(LookupElementBuilder.create(it.name))
                }
            }
        }
        return list.toTypedArray()
    }

    companion object {
        fun getElementSuggestName(e: PsiElement?, element: PsiElement): String? {
            // 根据类型判断命名
            val context = SearchContext.get(element.project)
            var name: String? = null
            if (element is LuaTypeGuessable) {
//                val set = HashSet<String>()
//                LuaNameSuggestionProvider.GetSuggestedNames(element, set)
//                if (set.isNotEmpty())
//                {
//                    return set.elementAt(0)
//                }
                val type = element.guessType(context)
                name = getElementSuggestNameByType(type, context, false)
                if (name != null && name.length > 3 && !LuaNameSuggestionProvider.isKeyword(name)) {
                    return name
                }
            }

            // 根据调用函数名
            if (element is LuaCallExpr && element.expr.name != null) {
                val functionName = element.expr.name
                if (functionName != null) {
                    if (LuaSettings.isRequireLikeFunctionName(functionName)) {
                        val expr = element.argList.first()
                        val guessType = expr.guessType(context)
                        if (guessType is TyPrimitiveLiteral && guessType.primitiveKind == TyPrimitiveKind.String) {
                            val path = guessType.value
                            val file = resolveRequireFile(path, element.project)
                            if (file != null) {
                                return file.virtualFile.nameWithoutExtension
                            }
                        }
                    }
                    val names = NameUtil.getSuggestionsByName(functionName, "", "", false, false, false)
                    if (names.isNotEmpty() && !LuaNameSuggestionProvider.isKeyword(names[0])) {
                        return names[0]
                    }
                }
            }
            if (name != null) {
                return name
            }

            var e1 = e
            var lastText: String? = null
            // 获取最后一个id或者字符
            while (e1 != null && e1.startOffset > element.startOffset && lastText == null) {
                val text = e1.text
                if (text != null) {
                    if (e1.elementType == LuaTypes.ID) {
                        lastText = text
                    } else if (e1.elementType == LuaTypes.STRING) {
                        val value = LuaString.getContent(text).value
                        lastText = value
                    }
                    e1 = PsiTreeUtil.prevVisibleLeaf(e1)
                }
            }
            // 如果是包含路径，可以判断一下是不是引用了
            if (element.text.contains("require(") && lastText != null) {
                val file = resolveRequireFile(lastText, element.project)
                name = getSuggestName(file)
                // 名字过短就用文件名
                if (name != null && name.length <= 3 && file != null) {
                    return file.virtualFile.nameWithoutExtension
                }
                // .只包含最后一个名称
                lastText = lastText.replace(Regex(".*\\."), "")
            }
            if (LuaNameSuggestionProvider.isKeyword(lastText)) {
                return "var"
            }
            return lastText
        }

        fun getSuggestName(file: LuaPsiFile?): String? {
            if (file == null) {
                return null
            }
            val element = file.guessFileElement()

            return when {
                element is LuaDocTagClass -> element.name
                element != null -> element.text
                else -> ""
            }
        }

        // 根据类型判断文件名
        fun getElementSuggestNameByType(ity: ITy?, context: SearchContext, checkPrimitive:Boolean): String? {
            var type = ity
            if (type is TyTuple) {
                val sb = StringBuilder()
                type.list.forEachIndexed() { i, it ->
                    if (i > 0)
                        sb.append(", ")
                    sb.append(getElementSuggestNameByType(it, context, true))
                }
                return sb.toString()
            }
            if (type is TyPrimitive &&  checkPrimitive) {
                return type.displayName
            }

            if (type is TyUnion) {
                type.getChildTypes().forEach() {
                    val name = getElementSuggestNameByType(it, context, false)
                    if (name != null) {
                        return name
                    }
                }
            }
            if (type is TyArray) {
                val name = getElementSuggestNameByType(type.base, context, false)
                if (name != null)
                    return name + "Arr"
            }
            if (type is TyClass) {
                val className = type.className
                if (!className.contains("@") && !className.contains("|") && !className.contains("$")) {
                    return className.replace(Regex(".*\\."), "")
                } else {
                    val superType = type.getSuperClass(context)
                    if (superType != null) {
                        type = superType
                    }
                }
            }
            if (type is TyDocTable && type.kind == TyKind.Class){
                return type.displayName
            }

            if (type is TyTable) {
                val psi = type.table
                val e = PsiTreeUtil.getParentOfType(psi, PsiNameIdentifierOwner::class.java)
                if (e != null && !e.name.isNullOrBlank()) {
                    return e.name.toString()
                }

                val declaration = PsiTreeUtil.getParentOfType(psi, LuaDeclaration::class.java)
                if (declaration != null) {
                    if (declaration is LuaAssignStat) {
                        return PsiTreeUtil.getDeepestLast(declaration.varExprList).text
                    } else if (declaration is LuaLocalDef && declaration.nameList?.lastChild != null) {
                        return PsiTreeUtil.getDeepestLast(declaration.nameList?.lastChild!!).text
                    }
                }
                }
            return null
        }
    }
}
