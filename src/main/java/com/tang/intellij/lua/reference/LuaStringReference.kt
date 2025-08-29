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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.project.LuaCustomHandleType
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.reference.LuaReferenceContributor.ReferenceType
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaLiteralIndex

class LuaStringReference(val expr: LuaLiteralExpr, private val referenceType: ReferenceType, val referenceText: String) : PsiReferenceBase<LuaLiteralExpr>(expr) {

    val id = expr

    override fun getVariants(): Array<Any> {
        return emptyArray()
    }

    override fun getElement(): LuaLiteralExpr {
        return id
    }

    override fun bindToElement(element: PsiElement): PsiElement? {
        return null
    }

    @Throws(IncorrectOperationException::class)
    override fun handleElementRename(newElementName: String): PsiElement {
        // 根据引用类型处理元素重命名
        val project = expr.project
        when (referenceType) {
            ReferenceType.FilePath -> {
                // 处理文件路径类型的引用
                val name = FileUtil.getNameWithoutExtension(newElementName) // 获取不带扩展名的文件名
                val last = referenceText.lastIndexOf('.') // 查找最后一个点号的位置
                // 构建新的路径：如果原路径没有点号则直接用新名称，否则保留原路径前缀加上新名称
                val path = if (last == -1) name else referenceText.take(last) + "." + name
                // 创建新的Lua元素并替换原表达式
                val newEle = LuaElementFactory.createWith(project, "\"$path\"")
                expr.replace(newEle.firstChild)
            }

            ReferenceType.Class -> {
                // 处理类类型的引用，直接使用新名称创建元素
                val newEle = LuaElementFactory.createWith(project, "\"$newElementName\"")
                expr.replace(newEle.firstChild)
            }

            else -> {} // 其他类型不做处理
        }
        return expr
    }

    override fun getRangeInElement(): TextRange {
        val start = id.node.startOffset - myElement.node.startOffset
        return TextRange(start + 1, start + id.textLength - 1)
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        when (referenceType) {
            ReferenceType.None -> {
                return false
            }

            else ->
                return myElement.manager.areElementsEquivalent(element, resolve())
        }
    }

    override fun resolve(): PsiElement? {
        val text = referenceText
        // 支持字符跳转文件
        val context = SearchContext.get(expr.project)
        when (referenceType) {
            ReferenceType.FilePath -> {
                val filePsi = resolveRequireFile(text, expr.project)
//                if (filePsi != null) {
//                    val returnStatement = filePsi.returnStatement()
//
//                    val exprList = returnStatement?.exprList?.exprList
//                    if (exprList?.size == 1) {
//                        val resolvedNameExpr = exprList.first() as? LuaNameExpr
//
//                        if (resolvedNameExpr != null) {
//                            val file = resolveInFile(resolvedNameExpr.name, resolvedNameExpr, context)
//                            if (file != null) {
//                                return file
//                            }
//                        }
//                    }
//                    if (returnStatement != null) {
//                        return returnStatement
//                    }
//                }
                return filePsi
            }

            ReferenceType.Class -> {
                // 支持跳转类型
                return LuaShortNamesManager.getInstance(expr.project).findTypeDef(text, context)
            }

            else -> return null
        }
    }

    companion object {
        val fileMaps = mutableMapOf<LuaPsiFile, MutableCollection<LuaLiteralExpr>>()

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
                    if (LuaSettings.isRequireLikeFunctionName(nameRef.text)) {
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
                        var exprs = fileMaps[file]
                        if (exprs == null) {
                            exprs = mutableSetOf()
                        }
                        exprs.add(expr)
                        fileMaps[file] = exprs

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

        fun handleAddReference(target: LuaDocTagClass?, references: MutableCollection<PsiReference?>) {
            if (target == null) {
                return
            }
            // 处理类引用
            val className = target.id.text
            val context = SearchContext.get(target.project)
            LuaLiteralIndex.find(className.hashCode(), context).forEach {
                if (it.stringValue == className) {
                    val reference = handleGetReference(it)
                    if (reference?.referenceText == className) {
                        references.add(reference)
                    }
                }
            }
        }
    }
}
