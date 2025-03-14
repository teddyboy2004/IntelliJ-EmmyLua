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
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.execution.target.value.constant
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.codeInsight.template.context.LuaFunContextType
import com.tang.intellij.lua.psi.LuaBlock
import com.tang.intellij.lua.psi.LuaClassMethodName
import com.tang.intellij.lua.psi.LuaFuncBodyOwner
import com.tang.intellij.lua.psi.LuaIndexExpr

class LuaFunctionParamSigMacro : Macro() {
    override fun getPresentableName() = "LuaFunctionParamSignature(pre, post, removeSelf)"

    override fun getName() = "LuaFunctionParamSignature"

    private fun checkLuaBlockIsDot(e: PsiElement?): Boolean {
        var isDot = false
        if (e?.parent is LuaBlock) {
            val block = e.parent
            val luaIndexExpr = PsiTreeUtil.findChildOfType(block, LuaIndexExpr::class.java)
            if (luaIndexExpr != null) {
                isDot = luaIndexExpr.dot != null
            }
        }
        return isDot
    }

    private fun getParam(e: LuaFuncBodyOwner, isDot: Boolean, preStr: String, postStr: String): String {
        val str = e.paramSignature
        val methodName = PsiTreeUtil.getChildOfType(e, LuaClassMethodName::class.java)
        var p = str.substring(1, str.length - 1)
        if (p.isNotBlank() && (preStr.isNotBlank() || postStr.isNotBlank())) {
            val strings = p.split(",")
            val sb = StringBuilder()
            strings.forEach {
                if (it.trim() == Constants.WORD_UNDERLINE) {
                    return@forEach
                }
                if (sb.isNotEmpty()) {
                    sb.append(", ")
                }
                if (preStr.isNotBlank()) {
                    sb.append(preStr)
                }
                sb.append(it.trim())
                if (postStr.isNotBlank()) {
                    sb.append(postStr)
                }

            }
            p = sb.toString()
        }
        if (methodName != null && methodName.colon != null && isDot) {
            var sep = ""
            if (p.isNotEmpty()) {
                sep = " ,"
            }
            p = "self$sep$p"
        }
        return p
    }

    private fun getExpressionStringPair(expressions: Array<out Expression>): Triple<String, String, Boolean> {
        var preStr = ""
        var postStr = ""
        var removeSelf = false
        if (expressions.isNotEmpty()) {
            preStr = expressions.first().calculateResult(null).toString()
            if (expressions.size > 1) {
                postStr = expressions[1].calculateResult(null).toString()
            }
            if (expressions.size > 2 && expressions[2] is VariableNode) {
                removeSelf = (expressions[2] as VariableNode).name == "true"
            }
        }
        return Triple(preStr, postStr, removeSelf)
    }
    
    override fun calculateResult(expressions: Array<out Expression>, context: ExpressionContext?): Result? {
        var (preStr, postStr, removeSelf) = getExpressionStringPair(expressions)
        if (context?.editor?.caretModel != null) {
            var e = context?.psiElementAtStartOffset
            while (e != null && e !is PsiFile) {
                e = e.parent
                if (e is LuaFuncBodyOwner) {
                    return TextResult(getParam(e, !removeSelf, preStr, postStr))
                }
            }
        }
        return null
    }

    override fun calculateLookupItems(params: Array<out Expression>, context: ExpressionContext?): Array<LookupElement>? {
        val (preStr, postStr) = getExpressionStringPair(params)
        var e = context?.psiElementAtStartOffset
        val isDot = checkLuaBlockIsDot(e)
        val list = mutableListOf<LookupElement>()
        while (e != null && e !is PsiFile) {
            e = e.parent
            if (e is LuaFuncBodyOwner) {
                val param = getParam(e, isDot, preStr, postStr)
                list.add(LookupElementBuilder.create(param))
            }
        }
        return list.toTypedArray()
    }

    override fun isAcceptableInContext(context: TemplateContextType): Boolean {
        return context is LuaFunContextType
    }
}