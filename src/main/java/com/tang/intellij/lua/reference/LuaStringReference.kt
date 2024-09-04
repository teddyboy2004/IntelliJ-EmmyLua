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

package com.tang.intellij.lua.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.project.LuaCustomHandleType
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.reference.LuaReferenceContributor.ReferenceType
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.returnStatement

class LuaStringReference(val expr: LuaLiteralExpr, private val referenceType: ReferenceType, val referenceText: String) : PsiReferenceBase<LuaLiteralExpr>(expr) {

    val id = expr

    override fun getVariants(): Array<Any> {
        return emptyArray()
    }

    @Throws(IncorrectOperationException::class)
    override fun handleElementRename(newElementName: String): PsiElement {
        return expr
    }

    override fun getRangeInElement(): TextRange {
        val start = id.node.startOffset - myElement.node.startOffset
        return TextRange(start, start + id.textLength)
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val text = referenceText
//        when (referenceType) {
//            ReferenceType.None -> {
//                return false
//            }
//
////            ReferenceType.FilePath -> {
////                val shortPath = LuaFileUtil.getShortPath(id.project, element.containingFile.originalFile.virtualFile)
////                val path = shortPath.replace(".lua", "").replace('/', '.')
////                if (path == text) {
////                    return true
////                }
////            }
//
//            ReferenceType.Class -> {
//                if (element is LuaTypeDef) {
//                    if (element.type.displayName == text) {
//                        return true
//                    }
//                }
//            }
//
//            else -> {
//            }
//        }
        return false
    }

    override fun resolve(): PsiElement? {
        val text = referenceText
        // 支持字符跳转文件
        val context = SearchContext.get(expr.project)
        when (referenceType) {
            ReferenceType.FilePath -> {
                val filePsi = resolveRequireFile(text, expr.project)
                if (filePsi != null) {
                    val returnStatement = filePsi.returnStatement()

                    val exprList = returnStatement?.exprList?.exprList
                    if (exprList?.size == 1) {
                        val resolvedNameExpr = exprList.first() as? LuaNameExpr

                        if (resolvedNameExpr != null) {
                            val file = resolveInFile(resolvedNameExpr.name, resolvedNameExpr, context)
                            if (file != null) {
                                return file
                            }
                        }
                    }
                    if (returnStatement != null) {
                        return returnStatement
                    }
                }
                return filePsi
            }

            ReferenceType.Class -> {
                // 支持跳转类型
                val find = LuaShortNamesManager.getInstance(expr.project).findTypeDef(text, context)
                if (find != null) {
                    return find
                }
                return null
            }

            else -> return null
        }
    }

    companion object {
        fun handleGetReference(expr: LuaLiteralExpr): LuaStringReference? {
            var text = LuaString.getContent(expr.text).value
            if (text.isEmpty()) {
                return null
            }
            val parent = expr.parent?.parent
            if (parent is LuaCallExpr) {
                val nameRef = parent.expr
                if (nameRef is LuaNameExpr) {
                    // 跳过require判断
                    if (LuaSettings.isRequireLikeFunctionName(nameRef.getText())) {
                        return null
                    }
                }
            }
            var referenceText = ""
            var referenceType = ReferenceType.None

            if (text.isNotBlank()) {
                var isFilePath = false
                if (text.contains("/")) {
                    text = text.replace('/', '.')
                    isFilePath = true
                }
                referenceText = text
                val project = expr.project
                if (isFilePath || text.contains('.')) {
                    val file = resolveRequireFile(text, project)
                    if (file != null) {
                        referenceType = ReferenceType.FilePath
                    }
                } else {
                    val shortNamesManager = LuaShortNamesManager.getInstance(project)
                    val find = shortNamesManager.findTypeDef(text, SearchContext.get(project))
                    if (find != null) {
                        referenceType = ReferenceType.Class
                    } else {
                        if (expr.parent is LuaArgs) {
                            val callExpr = PsiTreeUtil.getParentOfType(expr, LuaCallExpr::class.java)
                            if (callExpr != null) {
                                val index = callExpr.argList.indexOf(expr)
                                if (index >= 0) {
                                    val cfg = LuaSettings.getCustomHandleType(callExpr, index, LuaCustomHandleType.ClassName.bit)
                                    if (cfg != null) {
                                        val className = cfg.getClassName(text)
                                        shortNamesManager.findTypeDef(className, SearchContext.get(project))?.also {
                                            referenceText = className
                                            referenceType = ReferenceType.Class
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return if (referenceType == ReferenceType.None) {
                null
            } else {
                LuaStringReference(expr, referenceType, referenceText)
            }
        }
    }
}
