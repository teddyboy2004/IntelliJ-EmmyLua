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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentsWithSelf
import com.tang.intellij.lua.codeInsight.template.context.LuaFunContextType
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.TyClass
import com.tang.intellij.lua.ty.TyTable
import com.tang.intellij.lua.ty.returnStatement

class LuaCurrentClassNameMacro : Macro() {
    override fun getPresentableName() = "LuaCurrentClassName()"

    override fun getName() = "LuaCurrentClassName"

    fun removeFunction(classMethodName: String): String {
        return classMethodName.replace(Regex("[.:].*"), "")
    }

    override fun calculateResult(expressions: Array<out Expression>, context: ExpressionContext?): Result? {
        var e = context?.psiElementAtStartOffset
        val srcElement = e
        while (e != null && e !is PsiFile) {
            e = e.parent
            when (e) {
                is LuaClassMethodDef -> {
                    var classMethodName = e.classMethodName.text
                    return TextResult(removeFunction(classMethodName))
                }
            }
        }
        // 补充在函数外的情况
        val name = findOutsideClassName(srcElement)
        if (name.isNotBlank())
        {
            return TextResult(name)
        }
        return null
    }

    override fun calculateLookupItems(params: Array<out Expression>, context: ExpressionContext?): Array<LookupElement>? {
        var e = context?.psiElementAtStartOffset
        val srcElement = e
        val list = mutableListOf<LookupElement>()
        while (e != null && e !is PsiFile) {
            e = e.parent
            when (e) {
                is LuaClassMethodDef -> {
                    var classMethodName = e.classMethodName.text
                    list.add(LookupElementBuilder.create(removeFunction(classMethodName)))
                }
            }
        }
        // 补充在函数外的情况
        if (list.isEmpty())
        {
            val name = findOutsideClassName(srcElement)
            if (name.isNotBlank())
            {
                list.add(LookupElementBuilder.create(name))
            }
        }
        return list.toTypedArray()
    }

    private fun findOutsideClassName(srcElement: PsiElement?): String {
        if (srcElement == null) {
            return ""
        }
        var element:PsiElement = srcElement
        if (element.parent is LuaFuncDef && (element.parent as LuaFuncDef).nameIdentifier == null) {
            element = element.parent
        }
        // 前一级是文件节点，向上找函数定义
        if (element.parent is PsiFile) {
            var e = element.prevSibling
            while (e != null) {
                when (e) {
                    is LuaClassMethodDef -> {
                        val classMethodName = e.classMethodName.text
                        val lookupString = removeFunction(classMethodName)
                        if (lookupString.isNotBlank()) {
                            return lookupString
                        }
                        break
                    }
                }
                e = e.prevSibling
            }
        }

        // 还是找不到所有 local xxx = {}
        val searchContext = SearchContext.get(element.project)
        val declarations = element.containingFile.childrenOfType<LuaLocalDef>().filter { stat -> stat.exprList?.firstChild is LuaTableExpr }
        declarations.forEach { localDef ->
            if (localDef.exprList?.guessType(searchContext) is TyClass) {
                val tagClass = localDef.comment?.tagClass
                if (tagClass != null) {
                    val nameList = localDef.nameList
                    if (nameList?.text!=null) {
                        return nameList.text
                    }
                }
            }
        }
        if (declarations.isNotEmpty()) {
            val nameList = declarations.first().nameList
            if (nameList?.text!=null) {
                return nameList.text
            }
        }
        // 还是没有就找第一个xxx = {}
        val stat = PsiTreeUtil.getChildrenOfType(element.containingFile, LuaAssignStat::class.java)?.filter { stat -> stat.valueExprList?.guessType(searchContext) is TyClass }
        if (!stat.isNullOrEmpty()) {
            return stat.first().varExprList.firstChild.text
        }
        return ""
    }

    override fun isAcceptableInContext(context: TemplateContextType): Boolean {
        return context is LuaFunContextType
    }
}
