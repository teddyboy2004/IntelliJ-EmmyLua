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

package com.tang.intellij.lua.codeInsight

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ui.FormBuilder
import com.intellij.util.xmlb.XmlSerializerUtil
import com.tang.intellij.lua.editor.formatter.blocks.LuaTableBlock
import com.tang.intellij.lua.psi.*
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField


/**

 * Created by TangZX on 2016/12/14.
 */
class LuaInlayHintsProvider : InlayHintsProvider<LuaInlayHintState> {
    override val key: SettingsKey<LuaInlayHintState>
        get() = SettingsKey("lua.hint.block")
    override val name: String
        get() = "Lua Block Hint"
    override val previewText: String?
        get() = ""

    override fun createSettings(): LuaInlayHintState {
        return LuaInlayHintState.instance
    }

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: LuaInlayHintState, sink: InlayHintsSink): InlayHintsCollector? {
        return LuaInlayHintsCollector(file, editor)
    }

    override fun createConfigurable(settings: LuaInlayHintState): ImmediateConfigurable {
        return LuaInlayHintConfig(settings)
    }

}

class LuaInlayHintConfig(val settings: LuaInlayHintState) : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): JComponent {
        val jTextField = JTextField(settings.numberOfLetters.toString())
        jTextField.addActionListener {
            jTextField.text.toIntOrNull()?.also {
                settings.numberOfLetters = it
                listener.settingsChanged()
            }
        }
        val jTextField1 = JTextField(settings.maxline.toString())
        jTextField1.addActionListener() {
            jTextField1.text.toIntOrNull()?.also {
                settings.maxline = it
                listener.settingsChanged()
            }
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Number of letters", jTextField)
            .addLabeledComponent("Number of max show line", jTextField1)
            .addComponentFillVertically(JPanel(), 0)
            .getPanel();
    }
}


@State(name = "LuaInlayHintState", storages = [(Storage("LuaInlayHintState.xml"))])
class LuaInlayHintState : PersistentStateComponent<LuaInlayHintState> {
    var numberOfLetters = 40
    var maxline = 10

    override fun getState(): LuaInlayHintState {
        return this
    }

    override fun loadState(state: LuaInlayHintState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun noStateLoaded() {
        super.noStateLoaded()
    }

    companion object {
        val instance: LuaInlayHintState
            get() = ApplicationManager.getApplication().getService(LuaInlayHintState::class.java)
    }
}

class LuaInlayHintsCollector(file: PsiFile, editor: Editor) : InlayHintsCollector {
    private val factory: PresentationFactory = PresentationFactory(editor)

    private fun getIfBlockStartElement(element: LuaBlock): PsiElement? {
        val ifStartOffset = element.parent.textRange.startOffset
        var prevSibling = element.prevSibling
        while (prevSibling != null && prevSibling.textRange.startOffset >= ifStartOffset) {
            if (prevSibling.elementType == LuaTypes.IF || prevSibling.elementType == LuaTypes.ELSE || prevSibling.elementType == LuaTypes.ELSEIF) {
                break
            } else {
                prevSibling = prevSibling.prevSibling
            }
        }
        return prevSibling
    }

    override fun collect(psi: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (psi is LuaPsiFile) {
            val document = editor.document
            collectPsi(psi, editor, document, sink)
        }
        return false
    }

    private fun collectPsi(psi: PsiElement, editor: Editor, document: Document, sink: InlayHintsSink) {
        val startLine = document.getLineNumber(psi.startOffset)
        val endLine = document.getLineNumber(psi.endOffset)
        val settings = LuaInlayHintState.instance
        val maxLine = settings.maxline
        if (endLine - startLine < maxLine) {
            return
        }
        handleCollect(psi, editor, sink)
        psi.children.forEach {
            collectPsi(it, editor, document, sink)
        }
    }

    private fun handleCollect(psi: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        val settings = LuaInlayHintState.instance
        val maxLine = settings.maxline
        val maxLen = settings.numberOfLetters
        var elementOffset: Int = -1
        var exprOffset: Int = -1
        var text: String? = null
        var prefixText = ""
        var placeOffset = psi.endOffset
        var placeAtTheEndOfLine = true
        val manager = PsiDocumentManager.getInstance(psi.project)
        val document = manager.getDocument(psi.containingFile)
        if (psi is LuaBlock) {
            val parent = psi.parent
            if (parent != null) {
                if (document != null) {
                    val startLine = document.getLineNumber(parent.startOffset)
                    val endLine = document.getLineNumber(parent.endOffset)
                    if (startLine + maxLine <= endLine) {
                        when (parent) {
                            is LuaIfStat -> {
                                val elementLine = document.getLineNumber(psi.endOffset)
                                if (elementLine - startLine < maxLine) {
                                    return false
                                }
                                val element = getIfBlockStartElement(psi)
                                val expr = PsiTreeUtil.getNextSiblingOfType(element, LuaExpr::class.java)
                                if (element != null) {
//                                    val elementLine = document.getLineNumber(element.endOffset)
//                                    if (elementLine - startLine < maxLine) {
//                                        return
//                                    }
                                    elementOffset = element.endOffset
                                    prefixText = element.text
                                }
                                val luaExpr = parent.exprList.firstOrNull()
                                if (luaExpr != null) {
                                    exprOffset = luaExpr.startOffset
                                }
                                if (expr != null) {
                                    text = expr.text
                                } else {
                                    text = luaExpr?.text
                                }
                            }

                            is LuaWhileStat -> {
                                parent.expr?.also {
                                    text = it.text
                                    exprOffset = it.startOffset
                                    prefixText = "while"
                                }
                            }

                            is LuaForAStat -> {
                                parent.exprList.forEachIndexed { index, luaExpr ->
                                    if (index == 0) {
                                        text = luaExpr.text
                                        exprOffset = luaExpr.startOffset
                                        prefixText = "for"
                                    } else {
                                        text = "$text, ${luaExpr.text}"
                                    }
                                }
                            }

                            is LuaForBStat -> {
                                parent.exprList?.also {
                                    text = it.text
                                    exprOffset = it.startOffset
                                    prefixText = "for"
                                }
                            }

                            is LuaFuncBody -> {
                                PsiTreeUtil.getParentOfType(parent, LuaFuncBodyOwner::class.java)?.also {
                                    if (it is LuaClassMethodDef && it.comment != null) {
                                        val element = it.children[1]
                                        exprOffset = element.textRange.startOffset
                                        val line = document.getLineNumber(exprOffset)
                                        text = document.getText(TextRange(exprOffset, document.getLineEndOffset(line)))
                                    } else {
                                        val name = it.name ?: ""
                                        text = name + (it.paramSignature ?: "()")
                                        exprOffset = it.startOffset
                                    }
                                    prefixText = "function"

                                    placeAtTheEndOfLine = false
                                    placeOffset = it.endOffset
                                }
                            }
                        }
                    } else {
                        return false
                    }
                }
            }
        } else if (psi is LuaTableExpr) {
            if (document != null) {
                val startLine = document.getLineNumber(psi.startOffset)
                val endLine = document.getLineNumber(psi.endOffset)
                if (startLine + maxLine <= endLine) {
                    exprOffset = psi.startOffset
                    document.getLineNumber(exprOffset).let {
                        document.getText(TextRange(document.getLineStartOffset(it), document.getLineEndOffset(it))).let { text = it.trim() }
                    }
                } else {
                    return false
                }
            }
        }
        if (elementOffset == -1) {
            elementOffset = exprOffset
        }
        if (text != null) {
            val spliterator = if (prefixText.isNotEmpty()) {
                " "
            } else {
                ""
            }
            text = "$prefixText$spliterator$text"
            if (text!!.length >= maxLen) {
                text = text!!.substring(0, maxLen) + "..."
            }
            val smallText = factory.smallText(text!!)
            val presentation: InlayPresentation = factory.roundWithBackground(smallText)
            val p = factory.onClick(presentation, EnumSet.of(MouseButton.Middle, MouseButton.Right)) { mouseEvent, point ->
                var offset = -1
                offset = if (mouseEvent.button == 2) {
                    exprOffset
                } else {
                    elementOffset
                }
                if (offset >= 0) {
                    editor.selectionModel.removeSelection(/* allCarets = */ true)
                    editor.caretModel.moveToOffset(offset)
                    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
                }

            }
            sink.addInlineElement(placeOffset, true, p, placeAtTheEndOfLine)
            return false
        }
        return true
    }

}
