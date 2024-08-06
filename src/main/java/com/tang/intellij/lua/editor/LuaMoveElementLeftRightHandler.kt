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

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.psi.PsiElement
import com.tang.intellij.lua.psi.*

/**
 *
 * Created by teddysjwu on 2024/08/05.
 */
class LuaMoveElementLeftRightHandler : MoveElementLeftRightHandler() {
    override fun getMovableSubElements(element: PsiElement): Array<PsiElement> {
        return when (element) {
            is LuaListArgs -> {
                getLuaListArgs(element)
            }

            is LuaParamNameDef -> {
                getLuaParamNameSubElements(element)
            }

            is LuaBinaryExpr -> {
                getBinaryExprSubElements(element)
            }

            else -> PsiElement.EMPTY_ARRAY
        }
    }

    private fun getLuaListArgs(element: LuaListArgs): Array<PsiElement> {
        return element.exprList.toTypedArray()
    }

    private fun getLuaParamNameSubElements(element: LuaParamNameDef): Array<PsiElement> {
        val parent = element.parent
        if (parent is LuaFuncBody) {
            return parent.paramNameDefList.toTypedArray()
        }
        return PsiElement.EMPTY_ARRAY
    }

    private fun getBinaryExprSubElements(element: LuaBinaryExpr): Array<PsiElement> {
        val list = mutableListOf<PsiElement>()
        addBinarySubElements(element, list)
        return list.toTypedArray()
    }

    private fun addBinarySubElements(element: LuaExpr?, list: MutableList<PsiElement>) {
        if (element == null) {
            return
        }
        if (element is LuaBinaryExpr) {
            addBinarySubElements(element.left, list)
            addBinarySubElements(element.right, list)
        } else {
            list.add(element)
        }
    }
}
