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

package com.tang.intellij.lua.actions

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.ide.actions.GotoFileAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.tang.intellij.lua.psi.LuaFileUtil
import kotlinx.coroutines.Runnable
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable


/**
 * 创建方法
 * Created by TangZX on 2017/4/13.
 */
class CopyAndFindFileReferencesAction : AnAction() {
//    @Nls
//    override fun getFamilyName(): String {
//        return "File Reference"
//    }
//
//    override fun getText(): String {
//        return familyName
//    }
//
//    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
//        return IntentionPreviewInfo.EMPTY
//    }
//
//    override fun startInWriteAction(): Boolean {
//        return false
//    }
//
//    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
//        return null
//    }
//
//    override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
//        val virtualFile = psiFile.viewProvider.virtualFile
//        if (!LuaSourceRootManager.getInstance(project).isInSource(virtualFile)) {
//            return false
//        }
//        val element = psiFile.findElementAt(editor.caretModel.offset)
//        return element == null || element.parent is LuaPsiFile // 在空白地方有效
//    }

//    @Throws(IncorrectOperationException::class)
//    override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
//
//
//    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(PlatformDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        // 处理当前文件的引用路径
        val virtualFile = editor.virtualFile
        val shortPath = LuaFileUtil.getShortPath(project, virtualFile)
        val path = shortPath.replace(".lua", "").replace('/', '.')
        val systemClipboard = Toolkit.getDefaultToolkit().systemClipboard
        // 创建一个 Transferable 对象，包含要复制的内容
        val transferable: Transferable = StringSelection(path)
        // 将内容设置到剪切板
        systemClipboard.setContents(transferable, null)

        val id = GotoFileAction.ID

        val findManager = FindManager.getInstance(project)
        val findModel = FindModel()
        findModel.stringToFind = "\"$path\""
        findModel.isCaseSensitive = false // 设置是否区分大小写
        findModel.isWholeWordsOnly = false // 设置是否只查找整个单词
        findModel.isRegularExpressions = false // 设置是否使用正则表达式

        val findSettings = FindSettings.getInstance()
        findSettings.initModelBySetings(findModel)

        findManager.showFindDialog(findModel, object : Runnable {
            override fun run() {

            }
        })
    }
}
