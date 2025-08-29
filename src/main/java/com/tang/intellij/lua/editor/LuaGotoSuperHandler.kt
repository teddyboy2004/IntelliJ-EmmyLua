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

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.actions.GotoSuperAction
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.lang.LuaFileType
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.TyClass
import com.tang.intellij.lua.ty.returnStatement

/**
 * @author Medvedev Max
 */
class LuaGotoSuperHandler : GotoTargetHandler(), LanguageCodeInsightActionHandler {
    override fun getFeatureUsedKey(): String? {
        return ""
    }

    override fun getSourceAndTargetElements(editor: Editor, file: PsiFile): GotoData? {
        val source = findSource(editor, file) ?: return null
        val targets:Array<PsiElement> = getSupers(source)
        return GotoData(source, targets, emptyList<AdditionalAction>())
    }

    override fun getNotFoundMessage(project: Project, editor: Editor, file: PsiFile): String {
        return "No super found"
    }


    override fun isValidFor(editor: Editor, file: PsiFile?): Boolean {
        return file != null && LuaFileType.INSTANCE == file.fileType
    }

    companion object {
        private val LOG = Logger.getInstance(LuaGotoSuperHandler::class.java)

        private fun findSource(editor: Editor, file: PsiFile): LuaPsiElement? {
            val element = file.findElementAt(editor.caretModel.offset) ?: return null
            val type = PsiTreeUtil.getParentOfType(element, LuaClassMethodDef::class.java)
            if (type != null) {
                return type
            }
            if (file is LuaPsiFile) {
                // 查找函数定义
                val psiFile = file
                val declaration = PsiTreeUtil.getChildOfType(psiFile, LuaClassMethodDef::class.java)
                var ele:LuaPsiElement? = null
                if (declaration != null) {
                    ele = declaration.classMethodName.expr
                }

                // 查找return
                if (ele == null) {
                    val returnStatement = psiFile.returnStatement()
                    val firstChild = returnStatement?.exprList?.firstChild
                    if (firstChild is LuaNameExpr) {
                        ele = firstChild
                    }
                }

                // 查找定义
                if (ele == null) {
                    val defStat = PsiTreeUtil.getChildOfType(psiFile, LuaLocalDef::class.java)
                    if (defStat != null) {
                        ele = defStat.nameList
                    }
                }
                return ele
            }
            return null
        }


        private fun getSupers(element: LuaPsiElement): Array<PsiElement> {
            val project = element.project
            val context = SearchContext.get(project)
            if (element is LuaClassMethodDef) {
                val type = element.guessClassType(context)

                //OverridingMethod
                val classMethodNameId = element.nameIdentifier?.text
                if (type != null && classMethodNameId != null) {
                    val methodName = element.name!!
                    var superType = type.getSuperClass(context)

                    while (superType != null && superType is TyClass) {
                        ProgressManager.checkCanceled()
                        val superTypeName = superType.className
                        val superMethod = LuaShortNamesManager.getInstance(project).findMethod(superTypeName, methodName, context)
                        if (superMethod != null) {
                            return arrayOf(superMethod)
                        }
                        superType = superType.getSuperClass(context)
                    }
                }
            }
            if (element is LuaTypeGuessable) {
                val type = element.guessType(context)
                if (type != null) {
                    val superClass = type.getSuperClass(context)
                    if (superClass != null) {
                        val find = LuaClassIndex.find(superClass.displayName, context)
                        if (find != null) {
                            return arrayOf(find)
                        }
                    }
                }
            }
            return PsiElement.EMPTY_ARRAY
        }
    }
}