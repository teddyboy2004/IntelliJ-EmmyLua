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

package com.tang.intellij.lua.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.TyClass
import com.tang.intellij.lua.ty.TyFunction
import com.tang.intellij.lua.ty.TyUnion

fun resolveLocal(ref: LuaNameExpr, context: SearchContext? = null) = resolveLocal(ref.name, ref, context)

fun resolveLocal(refName:String, ref: PsiElement, context: SearchContext? = null): PsiElement? {
    val element = resolveInFile(refName, ref, context)
    return if (element is LuaNameExpr) null else element
}

fun resolveInFile(refName:String, pin: PsiElement, context: SearchContext?): PsiElement? {
    var ret: PsiElement? = null
    LuaDeclarationTree.get(pin.containingFile).walkUp(pin) { decl ->
        if (decl.name == refName)
            ret = decl.firstDeclaration.psi
        ret == null
    }

    if (ret == null && refName == Constants.WORD_SELF) {
        val methodDef = PsiTreeUtil.getStubOrPsiParentOfType(pin, LuaClassMethodDef::class.java)
        if (methodDef != null && !methodDef.isStatic) {
            val methodName = methodDef.classMethodName
            val expr = methodName.expr
            ret = if (expr is LuaNameExpr && context != null && expr.name != Constants.WORD_SELF)
                resolve(expr, context)
            else
                expr
        }
    }
    return ret
}

fun isUpValue(ref: LuaNameExpr, context: SearchContext): Boolean {
    val funcBody = PsiTreeUtil.getParentOfType(ref, LuaFuncBody::class.java) ?: return false

    val refName = ref.name
    if (refName == Constants.WORD_SELF) {
        val classMethodFuncDef = PsiTreeUtil.getParentOfType(ref, LuaClassMethodDef::class.java)
        if (classMethodFuncDef != null && !classMethodFuncDef.isStatic) {
            val methodFuncBody = classMethodFuncDef.funcBody
            if (methodFuncBody != null)
                return methodFuncBody.textOffset < funcBody.textOffset
        }
    }

    val resolve = resolveLocal(ref, context)
    if (resolve != null) {
        if (!funcBody.textRange.contains(resolve.textRange))
            return true
    }

    return false
}

/**
 * 查找这个引用
 * @param nameExpr 要查找的ref
 * *
 * @param context context
 * *
 * @return PsiElement
 */
fun resolve(nameExpr: LuaNameExpr, context: SearchContext): PsiElement? {
    //search local
    var resolveResult = resolveInFile(nameExpr.name, nameExpr, context)

    //global
    if (resolveResult == null || resolveResult is LuaNameExpr) {
        val target = (resolveResult as? LuaNameExpr) ?: nameExpr
        val refName = target.name
        val moduleName = target.moduleName ?: Constants.WORD_G
        LuaShortNamesManager.getInstance(context.project).processMembers(moduleName, refName, context, {
            resolveResult = it
            false
        })
    }

    return resolveResult
}

fun multiResolve(ref: LuaNameExpr, context: SearchContext): Array<PsiElement> {
    val list = mutableListOf<PsiElement>()
    //search local
    val resolveResult = resolveInFile(ref.name, ref, context)
    if (resolveResult != null) {
        list.add(resolveResult)
    } else {
        val refName = ref.name
        val module = ref.moduleName ?: Constants.WORD_G
        LuaShortNamesManager.getInstance(context.project).processMembers(module, refName, context, {
            list.add(it)
            true
        })
    }
    return list.toTypedArray()
}

fun multiResolve(indexExpr: LuaIndexExpr, context: SearchContext): List<PsiElement> {
    val list = mutableListOf<PsiElement>()
    val name = indexExpr.name ?: return list
    val type = indexExpr.guessParentType(context)
    type.eachTopClass(Processor { ty ->
        val m = ty.findMember(name, context)
        if (m != null)
            list.add(m)
        true
    })
    if (list.isEmpty()) {
        val tree = LuaDeclarationTree.get(indexExpr.containingFile)
        val declaration = tree.find(indexExpr)
        if (declaration != null) {
            list.add(declaration.psi)
        }
    }
    return list
}

fun resolve(indexExpr: LuaIndexExpr, context: SearchContext): PsiElement? {
    val name = indexExpr.name ?: return null
    return resolve(indexExpr, name, context)
}

fun findSuperClassMemeber(type: TyClass?, memberName:String, context: SearchContext, set:HashSet<String>): LuaClassMember?
{
    if (type !is TyClass || type.aliasName == null || !set.add(type.aliasName!!)) return null

    val superClass = type.getSuperClass(context)
    if (superClass is TyClass)
    {
        val classMember = findSuperClassMemeber(superClass as TyClass, memberName, context, set)
        if (classMember != null)
        {
            return classMember
        }
    }
    val findMember = type.findMember(memberName, context)
    if (findMember != null)
    {
        return findMember
    }
    return null
}

fun resolve(indexExpr: LuaIndexExpr, idString: String, context: SearchContext): PsiElement? {
    var type = indexExpr.guessParentType(context)
    var ret: PsiElement? = null

    // 23-07-03 11:04 teddysjwu: 增加__super跳转
    if (LuaSettings.isSuperFieldName(idString)) {
        if (type is TyUnion) {
            val find = indexExpr.nameIdentifier?.text?.let { LuaClassIndex.find(it, context) }
            if (find != null) {
                val superClass = find.superClassNameRef
                if (superClass != null) {
                    return LuaClassIndex.find(superClass.text, context);
                }
            }
        } else {
            val superType = type.getSuperClass(context)
            if (superType != null) {
                return LuaClassIndex.find(superType.displayName, context)
            }
        }
    }

    // 成员变量优先跳转父类, 函数还是优先跳转当前类
    if (type is TyClass) {
        val guessType = indexExpr.guessType(context)
        var isNotFunction = true
        if (guessType is TyFunction) {
            isNotFunction = false
        }
        else if (guessType is TyUnion) {
            guessType.getChildTypes().forEach {
                if (it is TyFunction) {
                    isNotFunction = false
                }
            }
        }
        if (isNotFunction) {
            TyClass.searchClassType.clear()
            val classMember = findSuperClassMemeber(type, idString, SearchContext.get(indexExpr.project), HashSet())
            if (classMember != null) {
                return classMember
            }
        }
    }

    type.eachTopClass(Processor { ty ->
        ret = ty.findMember(idString, context)
        if (ret != null)
            return@Processor false
        true
    })

    if (ret == null) {
        val tree = LuaDeclarationTree.get(indexExpr.containingFile)
        val declaration = tree.find(indexExpr)
        if (declaration != null) {
            return declaration.psi
        }
    }
    return ret
}

/**
 * 找到 require 的文件路径
 * @param pathString 参数字符串 require "aa.bb.cc"
 * *
 * @param project MyProject
 * *
 * @return PsiFile
 */
fun resolveRequireFile(pathString: String?, project: Project): LuaPsiFile? {
    if (pathString == null)
        return null
    val fileName = pathString.replace('.', '/')
    var f = LuaFileUtil.findFile(project, fileName)

    // issue #415, support init.lua
    if (f == null || f.isDirectory) {
        f = LuaFileUtil.findFile(project, "$fileName/init")
    }

    if (f != null) {
        val psiFile = PsiManager.getInstance(project).findFile(f)
        if (psiFile is LuaPsiFile)
            return psiFile
    }
    return null
}