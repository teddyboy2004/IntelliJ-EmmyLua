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

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*


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
//        psiReferenceRegistrar.registerReferenceProvider(psiElement().withElementType(LuaTypes.RETURN), LuaFileRequireReferenceProvider())
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
        None, FilePath, Class,
    }

    internal inner class LuaStringReferenceProvider : PsiReferenceProvider() {

        override fun getReferencesByElement(psiElement: PsiElement, processingContext: ProcessingContext): Array<PsiReference> {
            if (psiElement is LuaLiteralExpr) {
                val reference = LuaStringReference.handleGetReference(psiElement)
                if (reference != null) {
                    return arrayOf(reference)
                }
            }
            return PsiReference.EMPTY_ARRAY
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
            if (psiElement is LuaGotoStat && psiElement.id != null) return arrayOf(GotoReference(psiElement))
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
