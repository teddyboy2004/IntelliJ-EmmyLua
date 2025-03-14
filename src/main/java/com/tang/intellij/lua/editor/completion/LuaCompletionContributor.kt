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

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.findUsages.LanguageFindUsages
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.inline.XInlineWatchesView
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.lang.LuaLanguage
import com.tang.intellij.lua.project.LuaCustomHandleType
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.impl.LuaExprCodeFragmentImpl
import com.tang.intellij.lua.refactoring.LuaRefactoringUtil
import java.util.*

/**

 * Created by tangzx on 2016/11/27.
 */
class LuaCompletionContributor : CompletionContributor() {
    private var suggestWords = true

    init {
        extend(CompletionType.BASIC, IN_TABLE_FIELD, TableCompletionProvider())
        //可以override
        extend(CompletionType.BASIC, SHOW_OVERRIDE, OverrideCompletionProvider())

        extend(CompletionType.BASIC, IN_CLASS_METHOD, SuggestSelfMemberProvider())

        //提示属性, 提示方法
        extend(CompletionType.BASIC, SHOW_CLASS_FIELD, ClassMemberCompletionProvider())

        extend(CompletionType.BASIC, SHOW_REQUIRE_PATH, RequirePathCompletionProvider())

        extend(CompletionType.BASIC, SHOW_CUSTOM_CLASS_NAME, CustomTypeHandleCompleteProvider(LuaCustomHandleType.ClassName))
        extend(CompletionType.BASIC, SHOW_CUSTOM_REQUIRE_PATH, CustomTypeHandleCompleteProvider(LuaCustomHandleType.Require))

        extend(CompletionType.BASIC, LuaStringArgHistoryProvider.STRING_ARG, LuaStringArgHistoryProvider())

        //提示全局函数,local变量,local函数
        extend(CompletionType.BASIC, IN_NAME_EXPR, LocalAndGlobalCompletionProvider(LocalAndGlobalCompletionProvider.ALL))

        extend(CompletionType.BASIC, IN_CLASS_METHOD_NAME, LocalAndGlobalCompletionProvider(LocalAndGlobalCompletionProvider.VARS))

        extend(CompletionType.BASIC, GOTO, object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
                LuaPsiTreeUtil.walkUpLabel(parameters.position) {
                    val name = it.name
                    if (name != null) {
                        resultSet.addElement(LookupElementBuilder.create(name).withIcon(AllIcons.Actions.Rollback))
                    }
                    return@walkUpLabel true
                }
                resultSet.stopHere()
            }
        })

        extend(CompletionType.BASIC, psiElement(LuaTypes.ID).withParent(LuaNameDef::class.java), SuggestLocalNameProvider())

        extend(CompletionType.BASIC, ATTRIBUTE, AttributeCompletionProvider())
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        var p = parameters
        if (file is LuaExprCodeFragmentImpl) {
            p = file.createNewCompletionParameters(parameters)
        }
        val session = CompletionSession(p, result)
        p.editor.putUserData(CompletionSession.KEY, session)
        super.fillCompletionVariants(p, result)
        if (LuaSettings.instance.isShowWordsInFile && suggestWords && session.isSuggestWords && !result.isStopped) {
            suggestWordsInFile(p)
        }
    }

    override fun beforeCompletion(context: CompletionInitializationContext) {
        suggestWords = true
        val file = context.file
        if (file is LuaPsiFile) {
            val element = file.findElementAt(context.caret.offset - 1)
            if (element != null) {
                if (element.parent is LuaLabelStat) {
                    suggestWords = false
                    context.dummyIdentifier = ""
                } else if (!LuaCommentUtil.isComment(element)) {
                    val type = element.node.elementType
                    if (type in IGNORE_SET) {
                        suggestWords = false
                        context.dummyIdentifier = ""
                    }
                }
            }
        }
    }

    companion object {
        private val IGNORE_SET = TokenSet.create(LuaTypes.STRING, LuaTypes.NUMBER, LuaTypes.CONCAT)

        private val SHOW_CLASS_FIELD = psiElement(LuaTypes.ID)
            .withParent(LuaIndexExpr::class.java)

        private val IN_FUNC_NAME = psiElement(LuaTypes.ID)
            .withParent(LuaIndexExpr::class.java)
            .inside(LuaClassMethodName::class.java)
        private val AFTER_FUNCTION = psiElement()
            .afterLeaf(psiElement(LuaTypes.FUNCTION))
        private val IN_CLASS_METHOD_NAME = psiElement().andOr(IN_FUNC_NAME, AFTER_FUNCTION)

        private val IN_NAME_EXPR = psiElement(LuaTypes.ID)
            .withParent(LuaNameExpr::class.java)

        private val SHOW_OVERRIDE = psiElement()
            .withParent(LuaClassMethodName::class.java)
        private val IN_CLASS_METHOD = psiElement(LuaTypes.ID)
            .withParent(LuaNameExpr::class.java)
            .inside(LuaClassMethodDef::class.java)
        private val SHOW_REQUIRE_PATH = psiElement(LuaTypes.STRING)
            .withParent(
                psiElement(LuaTypes.LITERAL_EXPR).withParent(
                    psiElement(LuaArgs::class.java).afterSibling(
                        psiElement().with(RequireLikePatternCondition())
                    )
                )
            )
        private val SHOW_CUSTOM_CLASS_NAME = psiElement(LuaTypes.STRING)
            .withParent(
                psiElement(LuaTypes.LITERAL_EXPR)
                    .withParent(psiElement(LuaArgs::class.java))
            ).with(CustomTypePatternCondition(LuaCustomHandleType.ClassName))
        private val SHOW_CUSTOM_REQUIRE_PATH = psiElement(LuaTypes.STRING)
            .withParent(
                psiElement(LuaTypes.LITERAL_EXPR)
                    .withParent(psiElement(LuaArgs::class.java))
            ).with(CustomTypePatternCondition(LuaCustomHandleType.Require))

        private val GOTO = psiElement(LuaTypes.ID).withParent(LuaGotoStat::class.java)

        private val IN_TABLE_FIELD = psiElement().andOr(
            psiElement().withParent(psiElement(LuaTypes.NAME_EXPR).withParent(LuaTableField::class.java)),
            psiElement(LuaTypes.ID).withParent(LuaTableField::class.java),
        )

        private val ATTRIBUTE = psiElement(LuaTypes.ID).withParent(LuaAttribute::class.java)

        private fun suggestWordsInFile(parameters: CompletionParameters) {
            val session = CompletionSession[parameters]
            val originalPosition = parameters.originalPosition
            if (originalPosition != null)
                session.addWord(originalPosition.text)

            val wordsScanner = LanguageFindUsages.INSTANCE.forLanguage(LuaLanguage.INSTANCE).wordsScanner
            wordsScanner?.processWords(parameters.editor.document.charsSequence) {
                val word = it.baseText.subSequence(it.start, it.end).toString()
                if (word.length > 2 && LuaRefactoringUtil.isLuaIdentifier(word) && session.addWord(word)) {
                    session.resultSet.addElement(
                        PrioritizedLookupElement.withPriority(
                            LookupElementBuilder
                                .create(word)
                                .withIcon(LuaIcons.WORD), -1.0
                        )
                    )
                }
                true
            }
        }
    }
}

class RequireLikePatternCondition : PatternCondition<PsiElement>("requireLike") {
    override fun accepts(psi: PsiElement, context: ProcessingContext?): Boolean {
        val name = (psi as? PsiNamedElement)?.name
        return if (name != null) LuaSettings.isRequireLikeFunctionName(name) else false
    }
}

class CustomTypePatternCondition(val handleType: LuaCustomHandleType) : PatternCondition<PsiElement>("customTypeClassName") {
    override fun accepts(psi: PsiElement, context: ProcessingContext?): Boolean {
        val callExpr = PsiTreeUtil.getParentOfType(psi, LuaCallExpr::class.java)
        val argExpr = PsiTreeUtil.getParentOfType(psi, LuaExpr::class.java)
        if (callExpr == null || argExpr == null) {
            return false
        }
        val index = callExpr.argList.indexOf(argExpr)
        if (index < 0) {
            return false
        }
        return LuaSettings.isCustomHandleType(callExpr, index, handleType)
    }
}