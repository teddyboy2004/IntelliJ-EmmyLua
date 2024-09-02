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
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.tang.intellij.lua.psi.LuaBlock
import com.tang.intellij.lua.psi.LuaElementFactory
import com.tang.intellij.lua.psi.LuaIfStat
import com.tang.intellij.lua.psi.LuaTypes

class JoinIfConditionIntention : PsiElementBaseIntentionAction() {
    override fun getText(): String {
        return "Join if condition"
    }

    var activeIfState: LuaIfStat? = null

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (element is PsiWhiteSpace && element.prevSibling != null) {
            return isAvailable(project, editor, element.prevSibling)
        }
        if (element.elementType == LuaTypes.IF) {
            PsiTreeUtil.getParentOfType(element, LuaIfStat::class.java)?.let {
                if (it.children.size == 2 && it.parent is LuaBlock && it.parent.children.size == 1 && it.parent.parent is LuaIfStat) {
                    activeIfState = it
                    return true
                }

            }
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (activeIfState == null) {
            return
        }
        val srcIfStat = activeIfState!!
        PsiTreeUtil.getParentOfType(activeIfState, LuaIfStat::class.java)?.let {
            if (editor == null) {
                return
            }
            val parent = srcIfStat.parent
            val elements = it.children
            val index = elements.indexOf(parent)
            val preElement = elements[index - 1]
            val condition = srcIfStat.children.first()
            val block = srcIfStat.children.last()
            var parentOffset: Int = 0
            if (preElement !is LuaBlock) {
                parentOffset = preElement.textRange.endOffset + 5
                val state = LuaElementFactory.createWith(project, preElement.text + " and " + condition.text)
                preElement.replace(state)
                parent.replace(block)
            } else {
                val startOffset = it.textRange.startOffset
                val offset = preElement.textRange.endOffset - startOffset
                val text = it.text
                val substring = text.substring(0, offset)
                val leaf = PsiTreeUtil.prevVisibleLeaf(srcIfStat)
                if (leaf != null) {
                    parentOffset = leaf.textRange.endOffset + 3
                }
                else{
                    parentOffset = srcIfStat.prevSibling.textRange.startOffset
                }
                val state = LuaElementFactory.createWith(project, substring + "else" + srcIfStat.text)
                it.replace(state)
            }
            it.replace(LuaElementFactory.createWith(project, it.text))

//            val styleManager = CodeStyleManager.getInstance(project)
//            styleManager.adjustLineIndent(element.containingFile, it.textRange)
//            val documentManager = PsiDocumentManager.getInstance(project)
//            documentManager.doPostponedOperationsAndUnblockDocument(editor.document)
//            documentManager.commitDocument(editor.document)

            editor.caretModel.moveToOffset(parentOffset)
        }
    }

}