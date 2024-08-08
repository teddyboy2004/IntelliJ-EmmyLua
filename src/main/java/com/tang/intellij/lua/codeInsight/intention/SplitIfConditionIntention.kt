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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.tang.intellij.lua.psi.*

class SplitIfConditionIntention : PsiElementBaseIntentionAction() {
    override fun getText(): String {
        return "Split if condition"
    }

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        getLuaBinaryOp(element)?.let { op ->
            if (op.node.firstChildNode.elementType == LuaTypes.AND && getIfStat(op) != null) {
                return true
            }
        }
        return false
    }

    private fun getLuaBinaryOp(element: PsiElement): LuaBinaryOp? {
        when {
            element is LuaBinaryOp -> {
                return element
            }

            element.parent is LuaBinaryOp -> {
                return element.parent as LuaBinaryOp?
            }

            element.prevSibling is LuaBinaryOp -> {
                return element.prevSibling as LuaBinaryOp
            }

            element.nextSibling is LuaBinaryOp -> return element.nextSibling as LuaBinaryOp
            else -> return null
        }
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        getLuaBinaryOp(element)?.let { op ->
            val parent = Companion.getIfStat(op)
            if (parent is LuaIfStat) {
                var marker: RangeMarker? = null
                if (editor != null) {
                    marker = editor.document.createRangeMarker(parent.startOffset, parent.endOffset)
                }

                val psiFile = parent.containingFile
                val text = parent.text
                val startOffset = parent.startOffset
                val opStartOffset = op.startOffset
                val textOffset = opStartOffset - startOffset
                val preText = text.substring(0, textOffset)
                val postText = text.substring(textOffset + 3)
                val replaceText = "$preText then\n if$postText\nend"
                val psiElement = LuaElementFactory.createWith(project, replaceText)
                parent.replace(psiElement)
                if (psiFile.isPhysical && editor != null) {
                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
                    val styleManager = CodeStyleManager.getInstance(project)

                    if (marker != null) {
                        styleManager.adjustLineIndent(psiFile, marker.textRange)
                        editor.caretModel.moveToOffset(marker.startOffset)
                    }
                }

            }
        }

    }

    companion object {
        fun getIfStat(op: PsiElement): LuaIfStat? {
            var parent = op.parent
            while (parent != null) {
                if (parent is LuaIfStat) {
                    return parent
                }
                else if (parent is LuaBinaryExpr) {
                    parent = parent.parent
                }
                else {
                    return null
                }
            }
            return null
        }
    }
}