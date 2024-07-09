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

package com.tang.intellij.lua.ty

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.startOffset
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocCommentString
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.psi.LuaCommentOwner
import com.tang.intellij.lua.psi.LuaDeclaration
import com.tang.intellij.lua.psi.LuaPsiTreeUtil
import com.tang.intellij.lua.search.SearchContext

interface ITyRenderer {
    var renderDetail: Boolean

    fun render(ty: ITy): String
    fun render(ty: ITy, sb: StringBuilder)
    fun renderSignature(sb: StringBuilder, signature: IFunSignature)
}

private val MaxRenderedTableMembers = 10;
private val MaxRenderedUnionMembers = 20;
private val MaxRecursionDepth = 5;
private val MaxSingleLineTableMembers = 3;
private val MaxSingleLineUnionMembers = 5;
private val MaxSingleLineGenericParams = 5;

open class TyRenderer : TyVisitor(), ITyRenderer {
    var _renderDetail = false
    override var renderDetail: Boolean
        get() = _renderDetail
        set(value) {
            _renderDetail = value
        }

    override fun render(ty: ITy): String {
        return buildString { render(ty, this) }
    }

    override fun render(ty: ITy, sb: StringBuilder) {
        ty.accept(object : TyVisitor() {
            override fun visitTy(ty: ITy) {
                when (ty) {
                    is ITyPrimitive -> sb.append(renderType(ty.displayName))
                    is TyVoid -> sb.append(renderType(Constants.WORD_VOID))
                    is TyUnknown -> sb.append(renderType(Constants.WORD_ANY))
                    is TyNil -> sb.append(renderType(Constants.WORD_NIL))
                    is ITyGeneric -> {
                        val list = mutableListOf<String>()
                        ty.params.forEach { list.add(renderType(it.displayName)) }
                        sb.append("${renderType(ty.base.displayName)}&lt;${list.joinToString(", ")}&gt;")
                    }
                    is TyParameter -> {

                    }
                    is TyStringLiteral -> sb.append(ty.toString())
                    is TyPrimitiveLiteral -> sb.append(renderType(ty.displayName))
                    else -> {
                        error("")
                    }
                }
            }

            override fun visitClass(clazz: ITyClass) {
                sb.append(renderClass(clazz))
            }

            override fun visitUnion(u: TyUnion) {
                val list = mutableSetOf<String>()
                u.acceptChildren(object : TyVisitor() {
                    override fun visitTy(ty: ITy) {
                        val s = render(ty)
                        if (s.isNotEmpty()) list.add(s)
                    }
                })
                sb.append(if (list.isEmpty()) Constants.WORD_ANY else list.joinToString("|"))
            }

            override fun visitFun(f: ITyFunction) {
                sb.append("fun")
                renderSignature(sb, f.mainSignature)
            }

            override fun visitArray(array: ITyArray) {
                array.base.accept(this)
                sb.append("[]")
            }

            override fun visitTuple(tuple: TyTuple) {
                val list = tuple.list.map { render(it) }
                if (list.size <= 1) sb.append(list.joinToString(", "))
                else sb.append("(${list.joinToString(", ")})")
            }
        })
    }

    override fun renderSignature(sb: StringBuilder, signature: IFunSignature) {
        val sig = signature.params.map { "${it.name}: ${render(it.ty)}" }
        sb.append("(${sig.joinToString(", ")}): ")
        render(signature.returnTy, sb)
    }

    open fun renderClass(clazz: ITyClass): String {
        return when {
            clazz is TyDocTable -> {
                val list = mutableListOf<String>()
                clazz.table.tableFieldList.forEach { it.ty?.let { ty-> list.add("${it.name}: ${render(ty.getType())}") } }
                "{ ${list.joinToString(", ")} }"
            }
            clazz is TyClass && renderDetail -> renderClassMember(clazz)
            clazz.hasFlag(TyFlags.ANONYMOUS_TABLE) -> {
                var type = Constants.WORD_TABLE
                if (clazz is TyTable) {
                    PsiTreeUtil.getParentOfType(clazz.table, LuaDeclaration::class.java).let {declaration ->
                        val psiNameIdentifierOwner = PsiTreeUtil.findChildOfType(declaration, PsiNameIdentifierOwner::class.java)
                        if (psiNameIdentifierOwner != null && psiNameIdentifierOwner.name != null) {
                            type = psiNameIdentifierOwner.name!!
                        }
                    }
                }
                renderType(type)
            }
            clazz.isAnonymous -> "[local ${clazz.varName}]"
            clazz.isGlobal -> "[global ${clazz.varName}]"
            else -> renderType(clazz.className)
        }
    }

    open fun renderType(t: String): String {
        return t
    }

    // 优化类成员提示，增加显示注释
    fun renderClassMember(clazz: TyClass?): String {
        if (clazz == null)
        {
            return ""
        }
        var proj: Project? = null
        if (clazz is TyTable) {
            proj = clazz.table.project
        }
        else if(clazz is TyPsiDocClass)
        {
            proj = clazz.project
        }
        var className = ""
        if (!clazz.className.contains("@")) {
            className = clazz.className
        }
        if (proj == null) {
            return className
        }
        val context = SearchContext.get(proj)
        val list = mutableListOf<String>()
        clazz.processMembers(context) { owner, member ->
            if (list.size >= MaxRenderedTableMembers) {
                return@processMembers
            }
            val name = member.name
            val indexTy = if (name == null) member.guessType(context) else null
            val key = name ?: "[${render(indexTy ?: Ty.VOID)}]"
            member.guessType(context).let { fieldTy ->
                val renderedFieldTy = render(fieldTy ?: Ty.UNKNOWN)

                var comment = StringBuilder()
                if (member is LuaCommentOwner) {
                    if (member.comment != null) {
                        var child = member.comment
                        val string = PsiTreeUtil.findChildOfType(child, LuaDocCommentString::class.java)
                        if (string!= null && string.text.isNotBlank())
                        {
                            comment.append("---")
                            comment.append(string.text)
                        }
                    } else {
                        val doc = PsiDocumentManager.getInstance(context.project).getDocument((member as LuaCommentOwner).containingFile)
                        if (doc != null) {
                            val lineNumber = doc.getLineNumber(member.startOffset)
                            var current: PsiElement? = PsiTreeUtil.nextVisibleLeaf(member)
                            // 支持同一行的--注释
                            while (current != null && lineNumber == doc.getLineNumber(current.startOffset))
                            {
                                if (current is PsiComment && current !is LuaComment) {
                                    // 同一行的注释
                                    comment.append(current.text)
                                    break
                                }
                                current = PsiTreeUtil.nextVisibleLeaf(current)
                            }
                        }
                    }
                }
                if(member is LuaDocTagField)
                {
                    val string = member.commentString
                    if (string!= null && string.text.isNotBlank())
                    {
                        comment.append(string.text.replace(Regex("^@"), ""))
                    }
                }
                if (comment.isEmpty())
                {
                    comment.append(",")
                }
                else
                {
                    comment.insert(0, ", ")
                }
                list.add(if (fieldTy is TyFunction) {
                    "${key}: (${renderedFieldTy})$comment"
                } else {
                    "${key}: ${renderedFieldTy}$comment"
                })
            }
        }

        return "$className ${joinSingleLineOrWrap(list, MaxSingleLineTableMembers, " ", "{", "}")}"
    }

    private val regex = Regex("<.*?>")

    private fun joinSingleLineOrWrap(list: Collection<String>, maxOnLine: Int, divider: String, prefix: String = "", suffix: String = "", spaceWrapItems: Boolean = prefix.isNotEmpty()): String {
        return if (list.isEmpty()) {
            prefix + suffix
        } else {
            var wrapLine = false
            for (s in list) {
                // 过长也换行显示
                val replace = s.replace(regex, "")
                if(replace.length > 40)
                {
                    wrapLine = true
                    break;
                }
                // 有注释换行显示
                else if (replace.contains("--")) {
                    wrapLine = true
                    break
                }
            }
            if (list.size <= maxOnLine && !wrapLine) {
                list.joinToString("$divider ", if (spaceWrapItems) "$prefix " else prefix, if (spaceWrapItems) " $suffix" else suffix)
            } else {
                list.joinToString("$divider\n  ", "$prefix\n  ", "\n" + suffix)
            }
        }
    }

    companion object {
        val SIMPLE: ITyRenderer = TyRenderer()
    }
}