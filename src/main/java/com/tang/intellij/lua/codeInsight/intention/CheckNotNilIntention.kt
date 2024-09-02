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

package com.tang.intellij.lua.codeInsight.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.IntroduceTargetChooser
import com.intellij.util.ThrowableRunnable
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.psi.*

open class CheckNotNilIntention : PsiElementBaseIntentionAction() {
    open var handleElement: PsiElement? = null

    override fun getText(): String {
        return "Surround nil check"
    }

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (element.elementType == LuaTypes.COLON || element.elementType == LuaTypes.DOT || element.elementType == TokenType.WHITE_SPACE) {
            val prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(element) ?: return false
            return isAvailable(project, editor, prevVisibleLeaf)
        }

        getHandleElement(element)?.let {
            handleElement = it
            return true
        }
        return false
    }

    fun getHandleElement(element: PsiElement): PsiElement? {
        return when {
            element is LuaIndexExpr -> element
            element is LuaNameExpr -> element
            element.parent is LuaIndexExpr -> element.parent
            element.parent is LuaNameExpr -> element.parent
            else -> null
        }
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (!element.containingFile.isPhysical) {
            return
        }
        val expr = handleElement as LuaExpr
        val luaStatement = ExpressionUtil.getLuaStatement(expr) ?: return

        // 把当前元素替换
        val psiFile = element.containingFile
        when (expr) {
            is LuaNameExpr -> {
                val text = expr.text
                handle(editor, project, text, luaStatement, psiFile)
            }

            is LuaIndexExpr -> {
                val texts = mutableListOf<PsiElement>()
                handleLuaIndexExpr(expr, texts)
                if (texts.isEmpty()) {
                    return
                } else if (texts.size == 1) {
                    handle(editor, project, expr.text, luaStatement, psiFile)
                } else if (editor != null) {
                    IntroduceTargetChooser.showChooser(editor, texts, Pass.create {
                        val replaceText = getReplaceText(it, expr, null)
                        handle(editor, project, replaceText, luaStatement, psiFile)
                    }) { it.text }
                }
            }
        }
    }

    private fun getReplaceText(element: PsiElement, srcExpr: LuaExpr, preText: String?): String {
        var text = srcExpr.text
        if (preText != null) {
            text = "$text and $preText"
        }
        if (srcExpr == element) {
            return text
        }
        if (srcExpr is LuaIndexExpr) {
            return getReplaceText(element, srcExpr.exprList.first(), text)
        }
        return text
    }

    private fun handleLuaIndexExpr(srcExpr: LuaExpr, texts: MutableList<PsiElement>) {
        if (srcExpr is LuaNameExpr && (srcExpr.text == Constants.WORD_SELF || srcExpr.text == Constants.WORD_G)) {
            return
        }
        texts.add(srcExpr)
        if (srcExpr is LuaIndexExpr) {
            val expr = srcExpr.exprList.first() as LuaExpr
            handleLuaIndexExpr(expr, texts)
        }
    }

    protected fun handle(editor: Editor?, project: Project, text: String, luaStatement: PsiElement, psiFile: PsiFile) {
        if (editor == null)
            return
        val replaceRunnable = Runnable {
            var marker: RangeMarker? = null
            marker = editor.document.createRangeMarker(luaStatement.textRange)
            val element = handleReplace(project, text, luaStatement)

            val documentManager = PsiDocumentManager.getInstance(project)
            documentManager.doPostponedOperationsAndUnblockDocument(editor.document)
            val styleManager = CodeStyleManager.getInstance(project)
            styleManager.adjustLineIndent(psiFile, element.textRange)
            documentManager.commitDocument(editor.document)

            val moveOffset = getMoveOffset(element, editor, psiFile, marker)
            editor.caretModel.moveToOffset(moveOffset)
        }

        WriteCommandAction.writeCommandAction(luaStatement.project).run(ThrowableRunnable<RuntimeException> { replaceRunnable.run() })
    }

    protected open fun handleReplace(project: Project, text: String, luaStatement: PsiElement): PsiElement {
        val element = LuaElementFactory.createWith(project, "if $text then\n${luaStatement.text}\nend")
        luaStatement.replace(element)
        return element
    }

    protected open fun getMoveOffset(element: PsiElement, editor: Editor, psiFile: PsiFile, marker: RangeMarker): Int {
        val element = psiFile.findElementAt(marker.startOffset)?.parent
        if (element is LuaIfStat) {
            return element.exprList.last().textRange.endOffset
        }
        return marker.startOffset
    }

}