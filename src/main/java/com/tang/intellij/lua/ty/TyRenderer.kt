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
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocTagAlias
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.documentation.renderComment
import com.tang.intellij.lua.documentation.surroundHighlight
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.refactoring.LuaRefactoringUtil
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaShortNameIndex
import kotlin.math.max

interface ITyRenderer {
    var renderClassDetail: Boolean
    var renderTableDetail: Boolean
    var renderAllMember: Boolean
    var project: Project?

    fun render(ty: ITy): String
    fun render(ty: ITy, sb: StringBuilder)
    fun renderSignature(sb: StringBuilder, signature: IFunSignature)

}

private val MaxRenderedTableMembers = 10;
private val MaxRenderedUnionMembers = 20;
private val MaxRecursionDepth = 5;
private val MaxSingleLineTableMembers = 2;
private val MaxSingleLineUnionMembers = 5;
private val MaxSingleLineGenericParams = 5;

open class TyRenderer : TyVisitor(), ITyRenderer {
    override var renderClassDetail: Boolean = false
    override var renderTableDetail: Boolean = false
    override var renderAllMember: Boolean = false
    override var project: Project? = null

    override fun render(ty: ITy): String {
        return buildString { render(ty, this) }
    }

    override fun render(ty: ITy, sb: StringBuilder) {
        ty.accept(object : TyVisitor() {
            override fun visitTy(ty: ITy) {
                when (ty) {
                    is TyPrimitiveLiteral -> sb.append(renderType(ty.displayName.replace("\"", "&quot;")))
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
                        if (s.isNotEmpty() && s != Constants.WORD_NIL) list.add(s)
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

    open fun renderClass(clazz: ITyClass): Any {
        return when {
            clazz is TyDocTable -> {
                var text = "table"
                if (renderTableDetail) {
                    val sb = StringBuilder()
                    val list = mutableListOf<String>()
                    clazz.table.tableFieldList.forEach { it.ty?.let { ty -> list.add("${it.name}: ${render(ty.getType())}") } }
                    sb.append("{ ${list.joinToString(", ")} }")
                    text = sb.toString()
                } else if ((clazz.table.parent.parent is LuaDocTagAlias)) {
                    val s = (clazz.table.parent.parent as LuaDocTagAlias).id?.text
                    if (!s.isNullOrEmpty()) {
                        text = s
                    }
                }
                text
            }

            clazz is TyClass && (renderClassDetail||renderAllMember) -> renderClassMember(clazz)
            clazz is TySerializedClass -> renderType(clazz.varName)
            clazz.hasFlag(TyFlags.ANONYMOUS_TABLE) -> {
                var type = Constants.WORD_TABLE
                if (clazz is TyTable) {
                    if (clazz.table.parent is LuaTableField) {
                        val tableField = clazz.table.parent as LuaTableField
                        if (tableField.id != null) {
                            type = tableField.id!!.text
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

    private val tableTypeStr = renderType("table")
    private val spaceRegex = Regex("\\s+")

    // 优化类成员提示，增加显示注释
    private fun renderClassMember(clazz: TyClass?): String {
        if (clazz == null) {
            return ""
        }
        var proj: Project? = null
        if (clazz is TyTable || clazz is TyPsiDocClass || clazz is TySerializedClass) {
            proj = project
        }

        var className = ""
        val clazzName = clazz.className
        if (clazzName.startsWith('$')) {
            className = clazzName.substring(1)
        } else if (!clazzName.contains('@')) {
            className = clazzName
        }

        if (proj == null) {
            return className
        }
        val context = SearchContext.get(proj)
        // 全局如果找不到定义就判断为any
        if (clazz is TySerializedClass && clazz.isGlobal) {
            LuaShortNameIndex.find(className, context).firstOrNull() ?: return Ty.UNKNOWN.displayName
        }

        val list = mutableListOf<String>()
        val members = hashSetOf<LuaClassMember>()
        var maxMember = MaxRenderedTableMembers
        if (renderAllMember) {
            maxMember = 999
        }
        clazz.processMembers(context) { _, member ->
            if (list.size >= maxMember) {
                return@processMembers
            }
            if (member is LuaClassMethod) {
                return@processMembers
            }
            if (!members.add(member)) {
                return@processMembers
            }
            var name = member.name
            if (member is LuaTableField) {
                if (name != null && !LuaRefactoringUtil.isLuaIdentifier(name)) {
                    name = "[$name]"
                } else if (name == null && member.idExpr is LuaTypeGuessable) {
                    val type = member.idExpr!!.guessType(context)
                    if (type is TyPrimitiveLiteral) {
                        name = type.displayName
                    }
                }
            }
            val guessType = member.guessType(context)
            val indexTy = if (name == null) guessType else null
            val key = name ?: "[${render(indexTy ?: Ty.VOID)}]"
            guessType.let { fieldTy ->
                renderClassDetail = false
                val renderedFieldTy: String = if (fieldTy is TyTable) {
                    var str: String? = null
                    if (member is LuaTableField) {
                        val text = member.valueExpr?.text
                        if (text!= null) {
                            val s = text.replace(spaceRegex," ")
                            if (s.length <= 80) {
                                str = "$tableTypeStr: $s"
                            }
                        }
                    }
                    if (str == null) {
                        str = tableTypeStr
                    }
                    str
                } else {
                    render(fieldTy ?: Ty.UNKNOWN)
                }
                renderClassDetail = true
                val comment = StringBuilder()
                if (member is LuaCommentOwner) {
                    renderComment(comment, member, this)
                }
                if (member is LuaDocTagField) {
                    val string = member.commentString
                    if (string != null && string.text.isNotBlank()) {
                        comment.append(string.text.replace(Regex("^@"), ""))
                    }
                }
                if (comment.isEmpty()) {
                    comment.append(",")
                } else {
                    comment.insert(0, ", ")
                }
                list.add(
                    if (fieldTy is TyFunction) {
                        "${key}: (${renderedFieldTy})$comment"
                    } else {
                        "${key}: ${renderedFieldTy}$comment"
                    }
                )
            }
        }
        if (clazz is TySerializedClass && list.isEmpty()) {
            return Constants.WORD_NIL
        }
        var maxOnLine = MaxSingleLineTableMembers
        if (renderAllMember) {
            maxOnLine = 999;
        }
        return "${className.surroundHighlight(bgColor = "transparent", color = "#FED330")} ${joinSingleLineOrWrap(list, maxOnLine, " ", "{", "}")}"
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
                if (replace.length > 40) {
                    wrapLine = true
                    break;
                }
                // 有注释换行显示
                else if (replace.contains("--")) {
                    wrapLine = true
                    break
                }
            }
            if (renderAllMember) {
                wrapLine = true
            }
            if (list.size <= maxOnLine && !wrapLine) {
                list.joinToString("$divider ", if (spaceWrapItems) "$prefix " else prefix, if (spaceWrapItems) " $suffix" else suffix)
            } else {
                list.joinToString("$divider<br>  ", "$prefix<br>  ", "<br>$suffix")
            }
        }
    }

    companion object {
        val SIMPLE: ITyRenderer = TyRenderer()
    }
}