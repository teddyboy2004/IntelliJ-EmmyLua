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
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.tang.intellij.lua.psi.LuaElementFactory
import com.tang.intellij.lua.psi.LuaLocalDef
import com.tang.intellij.lua.psi.LuaTypes

class SplitValueDeclarationIntention : PsiElementBaseIntentionAction() {
    override fun getText(): String {
        return "Split value declaration"
    }

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val arroundAssign = element.elementType == LuaTypes.ASSIGN ||
                element.prevSibling.elementType == LuaTypes.ASSIGN ||
                element.nextSibling.elementType == LuaTypes.ASSIGN
        if (arroundAssign && element.parent is LuaLocalDef) {
            return true
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val psiFile = element.containingFile
//        if (!psiFile.isPhysical) {
//            return
//        }
        val parent = element.parent
        if (parent is LuaLocalDef) {
            val nameDefList = parent.nameList?.nameDefList
            if (nameDefList?.size == 1) {
                val first = nameDefList.first()

                var marker: RangeMarker? = null
                if (editor != null) {
                    marker = editor.document.createRangeMarker(parent.startOffset, parent.endOffset)
                }

                val commonParent = parent.parent
                val text = parent.text
                val before = text.substringBefore("=")
                val after = text.substringAfter("=")
                val firstEle = LuaElementFactory.createWith(project, before)
                commonParent.addBefore(firstEle, parent)
                val psiElement = LuaElementFactory.createWith(project, "${first.text} = $after")
                parent.replace(psiElement)

                val styleManager = CodeStyleManager.getInstance(project)
                styleManager.adjustLineIndent(psiFile, firstEle.textRange)

                if (marker != null && psiFile.isPhysical) {
                    editor?.caretModel?.moveToOffset(marker.startOffset)
                }
            }
        }
    }
}