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

package com.tang.intellij.lua.refactoring.move

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.usageView.UsageInfo
import com.tang.intellij.lua.psi.LuaFileUtil
import com.tang.intellij.lua.psi.LuaPsiFile
import com.tang.intellij.lua.reference.LuaRequireReference
import com.tang.intellij.lua.reference.LuaStringReference
import java.util.*

class LuaMoveFileHandler : MoveFileHandler() {
    companion object {
        private val REFERENCED_ELEMENT = Key.create<PsiNamedElement>("LUA_REFERENCED_ELEMENT")
    }

    override fun updateMovedFile(file: PsiFile) {

    }

    override fun prepareMovedFile(file: PsiFile, moveDestination: PsiDirectory, oldToNewMap: MutableMap<PsiElement, PsiElement>?) {

    }

    override fun findUsages(file: PsiFile, newParent: PsiDirectory, searchInComments: Boolean, searchInNonJavaFiles: Boolean): MutableList<UsageInfo> {
        val usages = mutableListOf<UsageInfo>()
        val handler = object : FindUsagesHandler(file) {}
        val elementsToProcess = ArrayList<PsiElement>()
        Collections.addAll(elementsToProcess, *handler.primaryElements)
        Collections.addAll(elementsToProcess, *handler.secondaryElements)
        for (e in elementsToProcess) {
            handler.processElementUsages(e, { usageInfo ->
                if (!usageInfo.isNonCodeUsage) {
                    usageInfo.element?.putCopyableUserData(REFERENCED_ELEMENT, file)
                    usages.add(usageInfo)
                }
                true
            }, FindUsagesHandler.createFindUsagesOptions(file.project, null))
        }

        // 处理文本引用
        LuaStringReference.fileMaps[file]?.forEach { expr ->
            val reference = LuaStringReference.handleGetReference(expr)
            reference?.let {
                val usageInfo = UsageInfo(reference)
                usageInfo.element?.putCopyableUserData(REFERENCED_ELEMENT, file)
                usages.add(usageInfo)
                usages.add(usageInfo)
            }
        }
        LuaStringReference.fileMaps[file]?.clear()
        return usages
    }

    override fun retargetUsages(usageInfos: MutableList<out UsageInfo>, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
        for (usageInfo in usageInfos) {
            usageInfo.element?.let {
                val element = usageInfo.element!!
                val file = element.getCopyableUserData(REFERENCED_ELEMENT)
                element.putCopyableUserData(REFERENCED_ELEMENT, null)
                if (file is LuaPsiFile) {
                    val requirePath = LuaFileUtil.asRequirePath(file.project, file.virtualFile)
                    val reference = usageInfo.reference
                    if (reference is LuaRequireReference) {
                        requirePath?.let {
                            reference.setPath(requirePath)
                        }
                    } else if (element is LuaLiteralExpr) { // 处理文本替换引用
                        // 创建新的Lua元素并替换原表达式
                        val newEle = LuaElementFactory.createWith(element.project, "\"$requirePath\"")
                        element.replace(newEle.firstChild)
                    }
                }
            }

        }
    }

    override fun canProcessElement(file: PsiFile): Boolean {
        return file is LuaPsiFile
    }
}