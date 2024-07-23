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

package com.tang.intellij.lua.editor.activity

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFile
import com.tang.intellij.lua.editor.services.StickyPanelManager

class LuaStartupActivity: StartupActivity {
    override fun runActivity(project: Project) {
        EditorFactory.getInstance().allEditors.forEach {
            if (it is EditorImpl) {
                try {
                    it.javaClass.getDeclaredMethod("createStickyLinesPanel")
                    return
                }
                catch (e: NoSuchMethodException) {
                }
            }
        }
        val handler = LuaFileEditorManager(project)
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, handler)
        FileEditorManager.getInstance(project).openFiles.getOrNull(0)?.let {
            handler.fileOpened(FileEditorManager.getInstance(project), it)
        }
    }
}

class LuaFileEditorManager(val project: Project): FileEditorManagerListener
{
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        for (textEditor in source.getEditors(file).filterIsInstance<TextEditor>()) {
            val editor = textEditor.editor as? EditorImpl
            StickyPanelManager(project, editor!!, source, textEditor)
        }
    }
}