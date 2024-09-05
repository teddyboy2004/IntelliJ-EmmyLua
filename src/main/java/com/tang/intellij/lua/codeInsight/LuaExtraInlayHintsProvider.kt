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
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ui.FormBuilder
import com.intellij.util.xmlb.XmlSerializerUtil
import com.tang.intellij.lua.project.LuaCustomHandleType
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*
import javax.swing.JComponent


/**

 * Created by TangZX on 2016/12/14.
 */
class LuaExtraInlayHintsProvider : InlayHintsProvider<LuaExtraInlayHintsProvider.LuaExtraInlayHintState> {
    override val key: SettingsKey<LuaExtraInlayHintState>
        get() = SettingsKey("lua.hint.Extra")
    override val name: String
        get() = "Lua Extra Hint"
    override val previewText: String?
        get() = null

    override fun createSettings(): LuaExtraInlayHintState {
        return LuaExtraInlayHintState.instance
    }

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: LuaExtraInlayHintState, sink: InlayHintsSink): InlayHintsCollector? {
        return LuaInlayHintsCollector(file, editor)
    }

    override fun createConfigurable(settings: LuaExtraInlayHintState): ImmediateConfigurable {
        return LuaInlayHintConfig(settings)
    }

    class LuaInlayHintConfig(val settings: LuaExtraInlayHintState) : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent {
            return FormBuilder.createFormBuilder()
                .getPanel();
        }
    }


    @State(name = "LuaExtraInlayHintState", storages = [(Storage("LuaExtraInlayHintState.xml"))])
    class LuaExtraInlayHintState : PersistentStateComponent<LuaExtraInlayHintState> {

        override fun getState(): LuaExtraInlayHintState {
            return this
        }

        override fun loadState(state: LuaExtraInlayHintState) {
            XmlSerializerUtil.copyBean(state, this)
        }

        override fun noStateLoaded() {
            super.noStateLoaded()
        }

        companion object {
            val instance: LuaExtraInlayHintState
                get() = ApplicationManager.getApplication().getService(LuaExtraInlayHintState::class.java)
        }
    }

    class LuaInlayHintsCollector(file: PsiFile, editor: Editor) : InlayHintsCollector {
        private val factory: PresentationFactory = PresentationFactory(editor)

        override fun collect(psi: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            if (psi is LuaPsiFile) {
                collectPsi(psi, editor, sink)
            }
            return false
        }

        private fun collectPsi(psi: PsiElement, editor: Editor, sink: InlayHintsSink) {
            if (psi is LuaCallExpr) {
                handleElement(psi, sink)
            }
            if (psi is LuaIndexExpr) {
                handleElement(psi.prefixExpr, sink)
                return
            }
            psi.children.forEach {
                collectPsi(it, editor, sink)
            }
        }

        fun handleElement(psi: PsiElement, sink: InlayHintsSink) {
            if (psi !is LuaCallExpr) {
                return
            }
            val callExpr = psi
            val args = callExpr.args as? LuaListArgs ?: return
            val exprList = args.exprList
            val project = psi.project
            val context = SearchContext.get(project)
            LuaSettings.getCustomHandleType(callExpr, -1, LuaSettings.ALL_LUA_CUSTOM_HANDLE_TYPE)?.let { config ->
                LuaSettings.getCustomHandleString(config, callExpr, context)?.let {
                    var virtualFile: VirtualFile? = null
                    var moveOffset = -1
                    var showIcon = AllIcons.Chooser.Right
                    if (config.HandleType == LuaCustomHandleType.ClassName) {
                        LuaShortNamesManager.getInstance(project).findTypeDef(it, context)?.let {
                            if (it.containingFile.virtualFile == null) {
                                return
                            }
                            virtualFile = it.containingFile.virtualFile
                            moveOffset = it.startOffset + 6
                        }
                    } else {
                        resolveRequireFile(it, context.project)?.let {
                            virtualFile = it.virtualFile
                            var ty: ITyClass
                            val fileElement = it.guessFileElement()
                            if (fileElement != null) {
                                moveOffset = fileElement.startOffset
                            }
                        }
                    }
                    if (virtualFile != null) {
                        val placeOffset = exprList.get(config.ParamIndex).endOffset
                        var icon = factory.smallScaledIcon(showIcon)
                        icon = factory.roundWithBackground(icon)
                        icon = factory.onClick(icon, MouseButton.Right) { mouseEvent, point ->
                            val fileEditorManager = FileEditorManager.getInstance(project)
                            fileEditorManager.openFile(virtualFile!!, true)
                            val editor = fileEditorManager.selectedTextEditor
                            if (moveOffset != -1 && editor != null) {
                                editor.caretModel.moveToOffset(moveOffset)
                                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                            }
                        }
                        sink.addInlineElement(placeOffset, true, icon, false)
                    }
                }

            }

            LuaSettings.handleCustomParam(callExpr) { cfg, findMember ->
                val memberType = infer(findMember, context)
                var find = false
                if (memberType is ITyFunction) {
                    find = true
                    val extraSig = memberType.mainSignature
                    val paramOffset = cfg.ParameterOffset
                    var colonStyle = true
                    if (findMember is LuaClassMethodDef) {
                        colonStyle = !findMember.isStatic
                    }
                    val type = callExpr.guessParentType(context)
                    val ty = TyUnion.find(type, ITyFunction::class.java) ?: return@handleCustomParam false
                    val sig = ty.findPerfectSignature(callExpr)
                    val preSigSize = sig.params.size
                    extraSig.processArgs(memberType, colonStyle) { index, param ->
                        val extraIndex = index + preSigSize - paramOffset
                        if (extraIndex >= 0) {
                            exprList.getOrNull(extraIndex)?.also {
                                var offset = 0
                                if (findMember is LuaClassMethodDef) {
                                    var paramIndex = index
                                    findMember.paramNameDefList.getOrNull(paramIndex)?.also {
                                        offset = it.textOffset
                                    }
                                } else {
                                    offset = findMember!!.textOffset
                                }

                                var text = factory.smallTextWithoutBackground(param.name + ":")
                                text = factory.roundWithBackgroundAndSmallInset(text)
                                text = factory.onClick(text, MouseButton.Right) { mouseEvent, point ->
                                    val fileEditorManager = FileEditorManager.getInstance(project)
                                    fileEditorManager.openFile(findMember.containingFile.virtualFile, true)
                                    val editor = fileEditorManager.selectedTextEditor
                                    if (editor != null) {
                                        val caretModel = editor.caretModel
                                        caretModel.moveToOffset(offset)
                                        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
                                    }

                                }
                                sink.addInlineElement(it.startOffset, true, text, false)
                            }
                        }
                        true
                    }
                }
                !find
            }
        }
    }


}
