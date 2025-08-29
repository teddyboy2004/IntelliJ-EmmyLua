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

package com.tang.intellij.lua.refactoring.rename

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.psi.stringValue
import com.tang.intellij.lua.reference.LuaStringReference
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaLiteralIndex
import org.jetbrains.annotations.Unmodifiable
import kotlin.collections.set

/**
 *
 * Created by tangzx on 2017/3/29.
 */
class RenameLuaClassProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(psiElement: PsiElement): Boolean {
        return psiElement is LuaDocTagClass
    }

    override fun findReferences(element: PsiElement, searchScope: SearchScope, searchInCommentsAndStrings: Boolean): @Unmodifiable Collection<PsiReference?> {
        val references = super.findReferences(element, searchScope, searchInCommentsAndStrings)
        val docClass = element as LuaDocTagClass
        LuaStringReference.handleAddReference(docClass, references)
        return references
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
        val docClass = element as LuaDocTagClass
        FileDocumentManager.getInstance().saveAllDocuments()
        val className = docClass.id.text
        val context = SearchContext.get(element.project)
        LuaLiteralIndex.find(className.hashCode(), context).forEach {
            if (it.stringValue == className) {
                val reference = LuaStringReference.handleGetReference(it)
                if (reference?.referenceText == className) {
                    allRenames[reference.element] = newName
                }
            }
        }

    }
}
