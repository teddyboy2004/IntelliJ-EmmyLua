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

package com.tang.intellij.lua.usages

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.MergeQuery
import com.intellij.util.Processor
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.project.LuaCustomHandleType
import com.tang.intellij.lua.project.LuaCustomTypeConfig
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.LuaClassField
import com.tang.intellij.lua.psi.LuaClassMethod
import com.tang.intellij.lua.psi.search.LuaOverridenMethodsSearch
import com.tang.intellij.lua.psi.search.LuaOverridingMethodsSearch
import com.tang.intellij.lua.psi.stringValue
import com.tang.intellij.lua.reference.LuaOverridingMethodReference
import com.tang.intellij.lua.reference.LuaStringReference
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaLiteralIndex
import com.tang.intellij.lua.ty.ITyClass

class LuaFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        if (element is LuaClassMethod)
            return FindMethodUsagesHandler(element)
        if (element is LuaDocTagClass)
            return FindDocTagClassHandler(element)
        return null
    }

    override fun canFindUsages(element: PsiElement): Boolean {
        return element is LuaClassMethod || element is LuaDocTagClass
    }
}

class FindDocTagClassHandler(element: LuaDocTagClass) : FindUsagesHandler(element) {
    override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions): Boolean {
        if (super.processElementUsages(element, processor, options) && element is LuaDocTagClass) {
            val nameMap = mutableMapOf<String, LuaCustomTypeConfig>()
            LuaSettings.instance.customTypeCfg.forEach {
                if (it.HandleType == LuaCustomHandleType.ClassName) {
                    if (!nameMap.contains(it.ExtraParam)) {
                        nameMap[it.ExtraParam] = it
                    }
                }
            }
            ApplicationManager.getApplication().runReadAction {
                val context = SearchContext.get(element.project)
                val name = element.name
                nameMap.forEach {
                    val className = it.value.getSrcName(name)
                    if (className.isNotBlank()) {
                        LuaLiteralIndex.find(className.hashCode(), context).forEach {
                            if (it.stringValue == className) {
                                if (LuaStringReference.handleGetReference(it)?.referenceText == name) {
                                    processor.process(UsageInfo(it))
                                }
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}

/**
 * 查找方法的引用-》同时查找重写的子类方法
 */
class FindMethodUsagesHandler(val methodDef: LuaClassMethod) : FindUsagesHandler(methodDef) {
    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): MutableCollection<PsiReference> {
        val collection = super.findReferencesToHighlight(target, searchScope)
        val query = MergeQuery(LuaOverridingMethodsSearch.search(methodDef), LuaOverridenMethodsSearch.search(methodDef))
        val psiFile = target.containingFile
        query.forEach {
            if (psiFile == it.containingFile)
                collection.add(LuaOverridingMethodReference(it, methodDef))
            collection.addAll(ReferencesSearch.search(it, searchScope).findAll())
        }
        return collection
    }

    override fun getPrimaryElements(): Array<PsiElement> {
        val arr: MutableList<PsiElement> = mutableListOf(methodDef)
        val ctx = SearchContext.get(psiElement.project)
        //base declarations
        val methodName = methodDef.name
        var parentType = methodDef.guessParentType(ctx) as? ITyClass
        while (methodName != null && parentType != null) {
            val superClass = parentType.getSuperClass(ctx) as? ITyClass
            if (superClass != null) {
                val superMethod = superClass.findMember(methodName, ctx)
                if (superMethod != null) arr.add(superMethod)
            }
            parentType = superClass
        }
        return arr.toTypedArray()
    }

    override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions): Boolean {
        if (super.processElementUsages(element, processor, options)) {
            ApplicationManager.getApplication().runReadAction {
                val query = MergeQuery(LuaOverridingMethodsSearch.search(methodDef), LuaOverridenMethodsSearch.search(methodDef))
                query.forEach {
                    // 跳过函数自己引用自己
                    if (it == methodDef) {
                        return@forEach
                    }
                    val identifier = it.nameIdentifier
                    if (identifier != null)
                        processor.process(UsageInfo(identifier))

                    ReferencesSearch.search(it, options.searchScope).forEach { ref ->
                        processor.process(UsageInfo(ref.element))
                    }
                }
            }
        }
        return true
    }
}
