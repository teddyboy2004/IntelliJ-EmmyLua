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

import com.intellij.codeInsight.navigation.actions.TypeDeclarationPlaceAwareProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.tang.intellij.lua.psi.LuaTypeGuessable
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaAliasIndex
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.*

/**
 * Goto Symbol
 * Created by TangZX on 2016/12/12.
 */
class LuaTypeDeclarationProvider : TypeDeclarationPlaceAwareProvider {
    override fun getSymbolTypeDeclarations(p0: PsiElement, p1: Editor?, p2: Int): Array<PsiElement>? {
        if (p0 is LuaTypeGuessable) {
            val project = p0.project
            val context = SearchContext.get(project)
            val type = p0.guessType(context)
            val target = mutableSetOf<PsiElement>()
            if (type is TyUnion) {
                type.getChildTypes().forEach { it ->
                    getPsiElement(it, context)?.also(target::add)
                }
            }
            else{
                getPsiElement(type, context)?.also { target.add(it) }
            }
            return target.toTypedArray()
        }
        return null
    }

    private fun getPsiElement(ty: ITy, context: SearchContext) : PsiElement?
    {
        var type = ty
        if (type is TyArray)
        {
            type = type.base
        }
        if (type is TyTable)
        {
            return type.table
        }
        if (type is TyClass) {
            val className = type.className
            val clazz = LuaClassIndex.find(className, context)
            if (clazz != null) {
                return clazz
            }
            val alias = LuaAliasIndex.find(className, context)
            if (alias != null) {
                return alias
            }
        }
        return null
    }


    override fun getSymbolTypeDeclarations(p0: PsiElement): Array<PsiElement>? {
        return getSymbolTypeDeclarations(p0, null, -1)
    }
}
