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
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XFetchValueActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.tang.intellij.lua.debugger.emmy.value.LuaXValue
import com.tang.intellij.lua.debugger.remote.value.LuaRValue
import com.tang.intellij.lua.debugger.utils.KeyNameUtil
import org.luaj.vm2.LuaValue
import java.awt.datatransfer.StringSelection

public class XDebugCopyValuePath() : XFetchValueActionBase() {
    override fun handle(project: Project, value: String, tree: XDebuggerTree) {
        val selectPath = tree.selectionPath
        val sb = StringBuffer()
        var type: Int? = null
        var keyName = ""
        selectPath?.path?.forEachIndexed { index, it ->
            if (it is XValueNodeImpl) {
                when (val container = it.valueContainer) {
                    is LuaRValue -> {
                        type = container.key?.type()
                        keyName = container.name
                    }

                    is LuaXValue -> {
                        type = container.value.nameType
                        keyName = container.name
                    }
                }
            }
            if (index == 0) {
                sb.append(keyName)
            } else if (type == LuaValue.TNUMBER) {
                sb.append("[$keyName]")
            } else if (!KeyNameUtil.isValidFieldName(keyName)) {
                sb.append("['$keyName']")
            } else {
                sb.append(".$keyName")
            }
        }

        val pathStr = sb.toString()
        CopyPasteManager.getInstance().setContents(StringSelection(pathStr))
    }

    override fun isEnabled(event: AnActionEvent, node: XValueNodeImpl): Boolean {
        return true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
