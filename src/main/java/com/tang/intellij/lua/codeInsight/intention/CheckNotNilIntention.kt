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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.IntroduceTargetChooser
import com.intellij.util.ThrowableRunnable
import com.tang.intellij.lua.psi.*

class CheckNotNilIntention : PsiElementBaseIntentionAction() {
    override fun getText(): String {
        return "Surround nil check"
    }

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return when (element.parent) {
            is LuaIndexExpr -> true
            is LuaNameExpr -> true
            else -> false
        }
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (!element.containingFile.isPhysical) {
            return
        }
        val expr = element.parent as LuaExpr
        val luaStatement = ExpressionUtil.getLuaStatement(expr) ?: return

        // 把当前元素替换
        val psiFile = expr.containingFile
        when (expr) {
            is LuaNameExpr -> {
                val text = expr.text
                handle(editor, project, text, luaStatement, psiFile)
            }

            is LuaIndexExpr -> {
                val texts = mutableListOf<PsiElement>()
                handleLuaIndexExpr(expr, texts)
                if (texts.size == 1) {
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
        texts.add(srcExpr)
        if (srcExpr is LuaIndexExpr) {
            val expr = srcExpr.exprList.first() as LuaExpr
            handleLuaIndexExpr(expr, texts)
        }
    }

    private fun handle(
        editor: Editor?, project: Project, text: String, luaStatement: PsiElement, psiFile: PsiFile
    ) {
        val replaceRunnable = Runnable {
            var marker: RangeMarker? = null
            if (editor != null) {
                marker = editor.document.createRangeMarker(luaStatement.textRange)
            }
            val element = LuaElementFactory.createWith(project, "if $text then\n${luaStatement.text}\nend")
            luaStatement.replace(element)

            val styleManager = CodeStyleManager.getInstance(project)
            styleManager.adjustLineIndent(psiFile, element.textRange)
            if (element is LuaIfStat && editor != null && marker != null) {
                val element = psiFile.findElementAt(marker.startOffset)?.parent
                if (element is LuaIfStat) {
                    editor.caretModel.moveToOffset(element.exprList.last().textRange.endOffset)
                }
            }
        }
        WriteCommandAction.writeCommandAction(luaStatement.project).run(ThrowableRunnable<RuntimeException> { replaceRunnable.run() })

    }
}