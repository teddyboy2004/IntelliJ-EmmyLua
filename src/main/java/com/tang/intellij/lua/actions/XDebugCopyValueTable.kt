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

package com.tang.intellij.lua.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XFetchValueActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.tang.intellij.lua.debugger.emmy.value.TableXValue
import com.tang.intellij.lua.debugger.remote.value.LuaRTable

public class XDebugCopyValueTable(): XFetchValueActionBase() {
    override fun handle(project: Project, value: String, tree: XDebuggerTree) {
        if (tree == null)
            return
        val container = (tree.selectionPath.lastPathComponent as XValueNodeImpl).valueContainer
        when (container) {
            is LuaRTable -> {
                container.copyAsTableString()
            }
            is TableXValue -> {
                container.copyAsTableString()
            }
        }
    }

    override fun isEnabled(event: AnActionEvent, node: XValueNodeImpl): Boolean {
        return node.valueContainer is LuaRTable || node.valueContainer is TableXValue
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
