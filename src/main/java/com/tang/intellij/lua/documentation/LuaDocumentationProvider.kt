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

package com.tang.intellij.lua.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.psi.LuaDocTableTy
import com.tang.intellij.lua.comment.psi.LuaDocTagAlias
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.editor.completion.LuaDocumentationLookupElement
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.*

/**
 * Documentation support
 * Created by tangzx on 2016/12/10.
 */
class LuaDocumentationProvider : AbstractDocumentationProvider(), DocumentationProvider {

    private val renderer: ITyRenderer = object : TyRenderer() {
        override fun renderType(t: String): String {
            return if (t.isNotEmpty()) buildString { DocumentationManagerUtil.createHyperlink(this, t, t, true) } else t
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element != null) {
            when (element) {
                is LuaTypeGuessable -> {
                    val context = SearchContext.get(element.project)
                    val ty = element.guessType(context)
                    return buildString {
                        renderer.project = element.project
                        renderTy(this, ty, renderer)
                    }
                }
            }
        }
        return super<AbstractDocumentationProvider>.getQuickNavigateInfo(element, originalElement)
    }

    override fun getDocumentationElementForLookupItem(psiManager: PsiManager, obj: Any, element: PsiElement?): PsiElement? {
        if (obj is LuaDocumentationLookupElement) {
            return obj.getDocumentationElement(SearchContext.get(psiManager.project))
        }
        return super<AbstractDocumentationProvider>.getDocumentationElementForLookupItem(psiManager, obj, element)
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement?): PsiElement? {
        return LuaClassIndex.find(link, SearchContext.get(psiManager.project))
    }

    override fun generateDoc(psiElement: PsiElement, originalElement: PsiElement?): String? {
        val tyRenderer = renderer
        renderer.project = psiElement.project
        val sb = StringBuilder()
        handleCustomParam(psiElement, originalElement, tyRenderer, sb)

        generateDoc(psiElement, tyRenderer, sb)
        if (sb.isNotEmpty()) {
            return sb.toString()
        }
        return super<AbstractDocumentationProvider>.generateDoc(psiElement, originalElement)
    }

    private fun generateDoc(element: PsiElement, tyRenderer: ITyRenderer, sb: StringBuilder) {
        when (element) {
            is LuaParamNameDef -> renderParamNameDef(sb, element)
            is LuaDocTagAlias -> {
                if (element.children[0] is LuaDocTableTy){
                    tyRenderer.renderTableDetail = true
                    renderAliasDef(sb, element, tyRenderer)
                    tyRenderer.renderTableDetail = false
                }
            }

            is LuaDocTagClass -> {
                tyRenderer.renderClassDetail = true
                renderClassDef(sb, element, tyRenderer)
                tyRenderer.renderClassDetail = false
            }

            is LuaClassMember -> renderClassMember(sb, element)
            is LuaNameDef -> { //local xx

                renderDefinition(sb) {
                    sb.append("local <b>${element.name}</b>:")
                    val ty = element.guessType(SearchContext.get(element.project))
                    tyRenderer.renderClassDetail = true
                    tyRenderer.renderTableDetail = true
                    renderTy(sb, ty, tyRenderer)
                    tyRenderer.renderClassDetail = false
                    tyRenderer.renderTableDetail = false
                }

                val owner = PsiTreeUtil.getParentOfType(element, LuaCommentOwner::class.java)
                owner?.let { renderComment(sb, owner, tyRenderer) }
            }

            is LuaLocalFuncDef -> {
                sb.wrapTag("pre") {
                    sb.append("local function <b>${element.name}</b>")
                    val type = element.guessType(SearchContext.get(element.project)) as ITyFunction
                    renderSignature(sb, type.mainSignature, tyRenderer)
                }
                renderComment(sb, element, tyRenderer)
            }
        }
    }

    private fun handleCustomParam(psiElement: PsiElement, originalElement: PsiElement?, tyRenderer: ITyRenderer, sb: StringBuilder) {
        var element: PsiElement = psiElement
        val luaCallExpr = originalElement?.parent?.parent
        if (psiElement is LuaClassMethodDef && luaCallExpr is LuaCallExpr) {
            LuaSettings.handleCustomParam(luaCallExpr){ cfg, member ->
                if (member is LuaClassMethodDef) {
                    if (member.comment != null) {
                        element = member
                        sb.append("<div style='background-color:#222222;'>")
                        generateDoc(element, tyRenderer, sb)
                        sb.append("</div>")
                    }
                    return@handleCustomParam false
                }
                return@handleCustomParam true
            }
        }
    }

    private fun renderClassMember(sb: StringBuilder, classMember: LuaClassMember) {
        val context = SearchContext.get(classMember.project)
        val parentType = classMember.guessClassType(context)
        val ty = classMember.guessType(context)
        val tyRenderer = renderer

        renderDefinition(sb) {
            //base info
            if (parentType != null) {
                val stringBuilder = StringBuilder()
                renderTy(stringBuilder, parentType, tyRenderer)
                sb.append(stringBuilder.toString().surroundHighlight(bgColor = "transparent", color = "#FED330"))
                tyRenderer.renderClassDetail = true
                with(sb) {
                    var name = classMember.name
                    when (ty) {
                        is TyFunction -> {
                            append(if (ty.isColonCall) ":" else ".")
                            append(name)
                            renderSignature(sb, ty.mainSignature, tyRenderer)
                        }

                        else -> {
                            // array
                            append(".$name:")
                            renderTy(sb, ty, tyRenderer)
                        }
                    }
                }
                tyRenderer.renderClassDetail = false
            } else {
                //NameExpr
                if (classMember is LuaNameExpr) {
                    val nameExpr: LuaNameExpr = classMember
                    with(sb) {
                        append(nameExpr.name)
                        when (ty) {
                            is TyFunction -> renderSignature(sb, ty.mainSignature, tyRenderer)
                            else -> {
                                append(":")
                                tyRenderer.renderClassDetail = true
                                renderTy(sb, ty, tyRenderer)
                                tyRenderer.renderClassDetail = false
                            }
                        }
                    }

                    val stat = nameExpr.parent.parent // VAR_LIST ASSIGN_STAT
                    if (stat is LuaAssignStat) renderComment(sb, stat, tyRenderer)
                }
            }
        }

        //comment content
        when (classMember) {
            is LuaCommentOwner -> {
                renderComment(sb, classMember, tyRenderer)
            }

            is LuaDocTagField -> renderCommentString("  ", null, sb, classMember.commentString)
            is LuaIndexExpr -> {
                val p1 = classMember.parent
                val p2 = p1.parent
                if (p1 is LuaVarList && p2 is LuaAssignStat) {
                    renderComment(sb, p2, tyRenderer)
                }
            }
        }
    }

    private fun renderParamNameDef(sb: StringBuilder, paramNameDef: LuaParamNameDef) {
        val owner = PsiTreeUtil.getParentOfType(paramNameDef, LuaCommentOwner::class.java)
        val docParamDef = owner?.comment?.getParamDef(paramNameDef.name)
        val tyRenderer = renderer
        if (docParamDef != null) {
            renderDocParam(sb, docParamDef, tyRenderer, true)
        } else {
            val ty = infer(paramNameDef, SearchContext.get(paramNameDef.project))
            sb.append("<b>param</b> <code>${paramNameDef.name}</code> : ")
            renderer.renderClassDetail = true
            renderTy(sb, ty, tyRenderer)
            renderer.renderClassDetail = false
        }
    }
}
