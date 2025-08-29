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

package com.tang.intellij.lua.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.codeInsight.navigation.ImplementationSearcher
import com.intellij.codeInsight.navigation.actions.GotoImplementationAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.psi.LuaClassMethodDef
import com.tang.intellij.lua.psi.findOverridingMethod
import com.tang.intellij.lua.psi.search.LuaClassInheritorsSearch
import com.tang.intellij.lua.psi.search.LuaOverridingMethodsSearch
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex

class LuaGotoImplementationAction : GotoImplementationAction() {
    override fun getHandler(): CodeInsightActionHandler {
        return LuaGotoImplementationHandler()
    }

    // 增加支持lua跳转implementation
    class LuaGotoImplementationHandler : GotoImplementationHandler() {
        override fun getSourceAndTargetElements(editor: Editor, file: PsiFile?): GotoData? {
            val offset = editor.caretModel.offset
            val project = editor.project ?: return null
            val source = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset)
            val scope = GlobalSearchScope.allScope(project)
            val context = SearchContext.get(project)
            val targets = mutableSetOf<PsiElement>()
            when (source) {
                is LuaClassMethodDef -> {
                    val search = LuaOverridingMethodsSearch.search(source)

                    search.forEach {
                        targets.add(it)
                    }
                }

                is LuaDocTagClass -> {
                    val clazz = source.type

                    val search = LuaClassInheritorsSearch.search(scope, project, clazz.className, true)
                    search.forEach {
                        targets.add(it)
                    }
                }

                else -> {

                }
            }
            if (source != null && targets.isNotEmpty()) {
                return GotoData(source, targets.toTypedArray(), emptyList<AdditionalAction>())
            }
            return super.getSourceAndTargetElements(editor, file)
        }

    }
}