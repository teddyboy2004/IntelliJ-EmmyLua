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
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ProcessingContext
import com.tang.intellij.lua.comment.psi.LuaDocTagAlias
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.stubs.index.LuaShortNameIndex
import com.tang.intellij.lua.ty.returnStatement

/**
 * reference contributor
 * Created by tangzx on 2016/12/14.
 */
class LuaReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(psiReferenceRegistrar: PsiReferenceRegistrar) {
        psiReferenceRegistrar.registerReferenceProvider(psiElement().withElementType(LuaTypes.CALL_EXPR), CallExprReferenceProvider())
        psiReferenceRegistrar.registerReferenceProvider(psiElement().withElementType(LuaTypes.INDEX_EXPR), IndexExprReferenceProvider())
        psiReferenceRegistrar.registerReferenceProvider(psiElement().withElementType(LuaTypes.NAME_EXPR), NameReferenceProvider())
        psiReferenceRegistrar.registerReferenceProvider(psiElement().withElementType(LuaTypes.GOTO_STAT), GotoReferenceProvider())
        psiReferenceRegistrar.registerReferenceProvider(psiElement().withElementType(LuaTypes.FUNC_DEF), FuncReferenceProvider())
        psiReferenceRegistrar.registerReferenceProvider(psiElement().withElementType(LuaTypes.LITERAL_EXPR), LuaStringReferenceProvider())
        psiReferenceRegistrar.registerReferenceProvider(psiElement().withElementType(LuaTypes.RETURN), LuaFileRequireReferenceProvider())
    }

    internal inner class LuaFileRequireReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
            return arrayOf(LuaFileRequireReference(element))
        }

        inner class LuaFileRequireReference(luaReturnStat: PsiElement) : PsiReferenceBase<PsiElement>(luaReturnStat) {
            val id = luaReturnStat

            override fun resolve(): PsiElement? {
                return id.containingFile
            }
        }

    }

    enum class ReferenceType {
        None,
        FilePath,
        Class,
    }

    internal inner class LuaStringReferenceProvider : PsiReferenceProvider() {

        override fun getReferencesByElement(psiElement: PsiElement, processingContext: ProcessingContext): Array<PsiReference> {
            if (psiElement is LuaLiteralExpr && psiElement.text != null) {
                val parent = psiElement.parent?.parent
                if (parent is LuaCallExpr) {
                    val nameRef = parent.expr
                    if (nameRef is LuaNameExpr) {
                        // 跳过require判断
                        if (LuaSettings.isRequireLikeFunctionName(nameRef.getText())) {
                            return PsiReference.EMPTY_ARRAY
                        }
                    }
                }
                return arrayOf(LuaFileStringReference(psiElement))
            }
            return PsiReference.EMPTY_ARRAY
        }

        inner class LuaFileStringReference(val expr: LuaLiteralExpr) : PsiReferenceBase<LuaLiteralExpr>(expr) {
            var referenceType: ReferenceType = ReferenceType.None

            val id = expr
            private var _referenceText: String? = null
            val referenceText: String
                get() {
                    if (_referenceText != null) {
                        return _referenceText!!
                    }
                    var text = LuaString.getContent(id.text).value
                    if (text.isNotBlank()) {
                        var isFilePath = false
                        if (text.contains("/")) {
                            text = text.replace('/', '.')
                            isFilePath = true
                        }
                        _referenceText = text
                        val project = expr.project
                        if (isFilePath || text.contains('.')) {
                            val file = resolveRequireFile(text, project)
                            if (file != null) {
                                referenceType = ReferenceType.FilePath
                            }
                        } else {
                            val find = LuaShortNamesManager.getInstance(project).findTypeDef(text, SearchContext.get(project))
                            if (find != null) {
                                referenceType = ReferenceType.Class
                            }
                        }
                    } else {
                        _referenceText = ""
                    }
                    return _referenceText!!
                }

            override fun getVariants(): Array<Any> = arrayOf()

            @Throws(IncorrectOperationException::class)
            override fun handleElementRename(newElementName: String): PsiElement {
                return expr
            }

            override fun getRangeInElement(): TextRange {
                val start = id.node.startOffset - myElement.node.startOffset
                return TextRange(start, start + id.textLength)
            }

//            override fun isReferenceTo(element: PsiElement): Boolean {
////                val text = referenceText
////                when (referenceType) {
////                    ReferenceType.None -> {
////                        return false
////                    }
////
////                    ReferenceType.FilePath -> {
////                        val shortPath = LuaFileUtil.getShortPath(id.project, element.containingFile.originalFile.virtualFile)
////                        val path = shortPath.replace(".lua", "").replace('/', '.')
////                        if (path == text) {
////                            return true
////                        }
////                    }
////
////                    ReferenceType.Class -> {
////                        if (element is LuaTypeDef) {
////                            if (element.type.displayName == text) {
////                                return true
////                            }
////                        }
////                    }
////                }
//                return false
//            }

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
                            referenceType = ReferenceType.Class
                            return find
                        }
                        return null
                    }

                    else ->
                        return null
                }
            }
        }
    }

    internal inner class FuncReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(psiElement: PsiElement, processingContext: ProcessingContext): Array<PsiReference> {
            if (psiElement is LuaFuncDef) {
                val forwardDeclaration = psiElement.forwardDeclaration
                if (forwardDeclaration != null) {
                    return arrayOf(LuaFuncForwardDecReference(psiElement, forwardDeclaration))
                }
            }
            return PsiReference.EMPTY_ARRAY
        }
    }

    internal inner class GotoReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(psiElement: PsiElement, processingContext: ProcessingContext): Array<PsiReference> {
            if (psiElement is LuaGotoStat && psiElement.id != null)
                return arrayOf(GotoReference(psiElement))
            return PsiReference.EMPTY_ARRAY
        }
    }

    internal inner class CallExprReferenceProvider : PsiReferenceProvider() {

        override fun getReferencesByElement(psiElement: PsiElement, processingContext: ProcessingContext): Array<PsiReference> {
            val expr = psiElement as LuaCallExpr
            val nameRef = expr.expr
            if (nameRef is LuaNameExpr) {
                if (LuaSettings.isRequireLikeFunctionName(nameRef.getText())) {
                    return arrayOf(LuaRequireReference(expr))
                }
            }
            return PsiReference.EMPTY_ARRAY
        }
    }

    internal inner class IndexExprReferenceProvider : PsiReferenceProvider() {

        override fun getReferencesByElement(psiElement: PsiElement, processingContext: ProcessingContext): Array<PsiReference> {
            val indexExpr = psiElement as LuaIndexExpr
            val id = indexExpr.id
            if (id != null) {
                return arrayOf(LuaIndexReference(indexExpr, id))
            }
            val idExpr = indexExpr.idExpr
            return if (idExpr != null) {
                arrayOf(LuaIndexBracketReference(indexExpr, idExpr))
            } else PsiReference.EMPTY_ARRAY
        }
    }

    internal inner class NameReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(psiElement: PsiElement, processingContext: ProcessingContext): Array<PsiReference> {
            return arrayOf(LuaNameReference(psiElement as LuaNameExpr))
        }
    }
}
