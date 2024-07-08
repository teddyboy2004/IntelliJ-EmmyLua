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

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.*
import com.intellij.ide.util.InheritedMembersNodeProvider
import com.intellij.ide.util.treeView.smartTree.*
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.tang.intellij.lua.LuaBundle
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.editor.structure.*
import com.tang.intellij.lua.psi.LuaClassMethodDef
import com.tang.intellij.lua.psi.LuaPsiFile
import com.tang.intellij.lua.psi.LuaTypeGuessable
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.TyClass
import com.tang.intellij.lua.ty.TyUnion

/**
 * Structure View
 * Created by TangZX on 2016/12/13.
 */
class LuaStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        return object : TreeBasedStructureViewBuilder() {

            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return LuaStructureViewModel(psiFile)
            }
        }
    }

    inner class LuaStructureViewModel(psiFile: PsiFile) : StructureViewModelBase(psiFile, LuaFileElement(psiFile as LuaPsiFile)), StructureViewModel.ElementInfoProvider {

        init {
            withSorters(LuaAlphaSorter())
        }

        override fun isAlwaysShowsPlus(structureViewTreeElement: StructureViewTreeElement): Boolean {
            return false
        }

        override fun isAlwaysLeaf(structureViewTreeElement: StructureViewTreeElement): Boolean {
            return false
        }

        private val NODE_PROVIDERS: Collection<NodeProvider<TreeElement>> = listOf(
            LuaInheritedMembersNodeProvider(),
        )

        override fun getNodeProviders(): Collection<NodeProvider<TreeElement>> {
            return NODE_PROVIDERS
        }
    }

    /**
     * 字母alpha排序，但field排在method前面
     */
    inner class LuaAlphaSorter : Sorter {

        override fun getComparator() = kotlin.Comparator<Any> { o1, o2 ->
            if (o1 is LuaClassFieldElement && o2 is LuaFuncElement)
                return@Comparator -1
            else if (o1 is LuaFuncElement && o2 is LuaClassFieldElement)
                return@Comparator 1

            val s1 = SorterUtil.getStringPresentation(o1)
            val s2 = SorterUtil.getStringPresentation(o2)
            s1.compareTo(s2, ignoreCase = true)
        }

        override fun isVisible(): Boolean {
            return true
        }

        override fun getPresentation(): ActionPresentation {
            return ActionPresentationData(LuaBundle.message("action.sort.alphabetically"), LuaBundle.message("action.sort.alphabetically_desc"), AllIcons.ObjectBrowser.Sorted)
        }

        override fun getName(): String {
            return "Alpha Sorter"
        }
    }

    inner class LuaInheritedMembersNodeProvider : InheritedMembersNodeProvider<TreeElement>() {
        override fun provideNodes(node: TreeElement): Collection<TreeElement> {
            if (node is LuaVarElement && node.parent == null && node.children.isNotEmpty()) {
                var context: SearchContext? = null
                val element = node.element
                var type: ITy? = null
                if (element is LuaTypeGuessable) {
                    context = SearchContext.get(element.project)
                    type = element.guessType(context)
                } else if (element is LuaDocTagClass) {
                    context = SearchContext.get(element.project)
                    type = element.type
                }

                val inherited = mutableListOf<TreeElement>()

                if (context != null && type != null) {
                    val containNames = hashSetOf<String>()
                    val types = mutableListOf<TyClass>()
                    if (type is TyUnion) {
                        type.getChildTypes().forEach {
                            if (it is TyClass) {
                                types.add(it)
                            }
                        }
                    } else if (type is TyClass) {
                        types.add(type)
                    }

                    types.forEach {
                        addAllChildren(node, containNames)
                        addInheritedMembers(it, containNames, inherited, context)
                    }
                }

                return inherited
            }
            return emptyList()
        }

        private fun addAllChildren(node: LuaTreeElement, containNames: HashSet<String>) {
            if (node.children.isEmpty()) {
                return
            }
            node.children.forEach { treeElement ->
                containNames.add((treeElement as LuaTreeElement).name)
                addAllChildren(treeElement, containNames)
            }
        }

        private fun addInheritedMembers(
            clazz: TyClass,
            containNames: HashSet<String>,
            inherited: MutableList<TreeElement>,
            context: SearchContext
        ) {
            val members = LuaClassMemberIndex.instance.get(clazz.hashCode(), context.project, context.scope)

            members.forEach { member ->
                val memberName = member.name
                if (memberName != null) {
                    if (containNames.add(memberName)) {
                        val item: LuaTreeElement = if (member is LuaClassMethodDef) {
                            LuaClassMethodElement(member, memberName, member.paramSignature, member.visibility)
                        } else {
                            LuaClassFieldElement(member, memberName)
                        }
                        item.inherited = true
                        inherited.add(item)
                    }
                }
            }
            val superClass = clazz.getSuperClass(context)
            if (superClass is TyClass) {
                addInheritedMembers(superClass, containNames, inherited, context)
            }
        }
    }

}
