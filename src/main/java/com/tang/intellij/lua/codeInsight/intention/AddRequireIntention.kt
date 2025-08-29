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

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateEditingListener
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.macro.CompleteMacro
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.tang.intellij.lua.psi.*


// 添加未知类型的，直接添加require
class AddRequireIntention : BaseIntentionAction() {
    override fun getFamilyName() = "Add require line"

    override fun getText() = familyName

    var handleElement: LuaExpr? = null

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val nameExpr = LuaPsiTreeUtil.findElementOfClassAtOffset(file, editor.caretModel.offset, LuaNameExpr::class.java, false) ?: return false
        val expr = LuaPsiTreeUtil.findElementOfClassAtOffset(file, editor.caretModel.offset, LuaIndexExpr::class.java, false) ?: return false
        if (expr.firstChild != nameExpr) { // 不是第一个，返回
            return false
        }
        // 判断类型为未知

        if (nameExpr.reference?.resolve() == null) {
            handleElement = nameExpr
            return true
        }
        return false
    }


    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) {
            return
        }
        val expr = handleElement ?: return
        val luaStatement = ExpressionUtil.getLuaStatement(expr) ?: return
        val marker = editor.document.createRangeMarker(expr.startOffset, expr.endOffset)

        val nameText = expr.text
        val templateManager = TemplateManager.getInstance(project)
        val text = "local $nameText = require(\"$nameText\$Input$\")\n"
        val startOffset = luaStatement.startOffset

        // 保留替换方案
//        val element = LuaElementFactory.createWith(project, text)
//        luaStatement.parent.addBefore(element, luaStatement)
//        invokeLater {
//            LuaPsiTreeUtil.findElementOfClassAtOffset(file, startOffset, LuaLocalDef::class.java, false)?.let {
//                editor.caretModel.moveToOffset(it.endOffset - 2)
//                // 调用代码补全
//                CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor)
//
//            }
//        }
        editor.caretModel.moveToOffset(startOffset)
        val template = templateManager.createTemplate("", "", text)
        template.addVariable("Input", MacroCallNode(CompleteMacro()), TextExpression(""), true)
        template.isToReformat = true
        templateManager.startTemplate(editor, template, object : TemplateEditingAdapter() {
            override fun templateFinished(template: Template, brokenOff: Boolean) {
                editor.caretModel.moveToOffset(marker.startOffset)
            }
        })
    }
}