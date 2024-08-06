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

package com.tang.intellij.lua.editor

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementsAroundOffsetUp
import com.tang.intellij.lua.psi.ExpressionUtil
import com.tang.intellij.lua.psi.LuaExpr
import com.tang.intellij.lua.psi.LuaPsiFile
import com.tang.intellij.lua.psi.LuaStatement


/**
 *
 * Created by teddysjwu on 2024/08/05.
 */
class LuaStatementUpDownMover : StatementUpDownMover() {
    override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
        if (file !is LuaPsiFile) {
            return false
        }
        var element: LuaStatement? = null
        val offsetInFile = editor.caretModel.offset
        val document = editor.document
        val lineNumber = document.getLineNumber(offsetInFile)
        val startOffset = document.getLineStartOffset(lineNumber)
        val endOffset = document.getLineEndOffset(lineNumber)
        val text = document.getText(TextRange(startOffset, endOffset))
        val offset = text.length - text.trimStart().length
        var e = file.findElementAt(startOffset + offset)
        while (e != null) {
            if (e is LuaStatement) {
                element = e
                break
            }
            e = e.parent
        }
        var expr: PsiElement? = null
        if (element is LuaExpr) {
            element = ExpressionUtil.getLuaStatement(element as LuaExpr) as LuaStatement?
        }
        if (element != null) {
            if (down) {
                var nextSibling = element.nextSibling
                while (nextSibling != null) {
                    if (nextSibling is LuaStatement) {
                        expr = nextSibling
                        break
                    }
                    nextSibling = nextSibling.nextSibling
                }
            } else {
                var prevSibling = element.prevSibling
                while (prevSibling != null) {
                    if (prevSibling is LuaStatement) {
                        expr = prevSibling
                        break
                    }
                    prevSibling = prevSibling.prevSibling
                }
            }
            if (expr != null) {
                info.toMove = LineRange(element, element)
                info.toMove2 = LineRange(expr, expr)
                return true
            }
        }

        info.toMove2 = info.toMove
        return true
    }
}
