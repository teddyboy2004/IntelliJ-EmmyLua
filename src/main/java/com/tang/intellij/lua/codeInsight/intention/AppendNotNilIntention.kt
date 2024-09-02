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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.tang.intellij.lua.psi.*

class AppendNotNilIntention : CheckNotNilIntention() {
    var replaceExpr: LuaExpr? = null
    var moveOffset = -1

    override fun getText(): String {
        return "Append nil check"
    }

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (element.elementType == LuaTypes.COLON || element.elementType == LuaTypes.DOT || element.elementType == TokenType.WHITE_SPACE) {
            val prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(element) ?: return false
            return isAvailable(project, editor, prevVisibleLeaf)
        }

        getHandleElement(element)?.let {
            if (it is LuaIndexExpr) {
                handleElement = it.prefixExpr
                val luaStatement = ExpressionUtil.getLuaStatement(it)
                val handleRange = handleElement!!.textRange
                when (luaStatement) {
                    is LuaIfStat -> {
                        luaStatement.exprList.firstOrNull()?.also { expr ->
                            if (expr.textRange.contains(handleRange)) {
                                replaceExpr = expr
                            }
                        }
                    }

                    is LuaAssignStat -> {
                        luaStatement.valueExprList?.exprList?.forEach {
                            if (it.textRange.contains(handleRange)) {
                                replaceExpr = it
                            }
                        }
                    }

                    is LuaLocalDef -> {
                        luaStatement.exprList?.exprList?.forEach {
                            if (it.textRange.contains(handleRange)) {
                                replaceExpr = it
                            }
                        }
                    }
                }
            }

        }
        return replaceExpr != null
    }

    override fun handleReplace(project: Project, text: String, luaStatement: PsiElement): PsiElement {
        if (replaceExpr == null) {
            return luaStatement
        }
        val expr = replaceExpr!!
        val oldValue = expr.text
        expr.replace(LuaElementFactory.createWith(project, "$text and $oldValue"))
        val code = luaStatement.text
        moveOffset = code.indexOf(oldValue)
        val final = LuaElementFactory.createWith(project, code)
        luaStatement.replace(final)
        return final
    }

    override fun getMoveOffset(element: PsiElement, editor: Editor, psiFile: PsiFile, marker: RangeMarker): Int {
        return marker.startOffset + moveOffset
    }
}