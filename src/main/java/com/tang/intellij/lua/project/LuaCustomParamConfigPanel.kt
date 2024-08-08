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

package com.tang.intellij.lua.project

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.tang.intellij.lua.LuaBundle
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel


class LuaCustomParamConfigPanel : JPanel(BorderLayout()) {
    private val columnNames = LuaCustomParamConfig.ColumnNames
    private val defaultTableModel = DefaultTableModel(columnNames, 0)
    private val paramTable = JBTable(defaultTableModel)

    init {
        val component = ToolbarDecorator.createDecorator(paramTable)
            .setAddAction { addAction() }
            .setRemoveAction { removeAction() }
            .setMoveUpAction() { moveUpAction() }
            .setMoveDownAction() { moveDownAction() }
            .addExtraAction(object : AnAction(AllIcons.Actions.Copy) {
                override fun actionPerformed(p0: AnActionEvent) {
                    copyAction()
                }
            })
            .createPanel()
        add(component, BorderLayout.CENTER)
        border = IdeBorderFactory.createTitledBorder(LuaBundle.message("ui.settings.custom_param_cfg"), false)
        setTableNumericCell("ParameterOffset")
    }

    private fun setTableNumericCell(columnName: String) {
        val column = paramTable.getColumn(columnName) ?: return
        column.cellEditor = NumericCellEditor()
    }

    var roots: Array<LuaCustomParamConfig>
        get() {
            val rowCount = defaultTableModel.rowCount
            val list = mutableListOf<LuaCustomParamConfig>()
            for (i in 0 until rowCount) {
                val vector = defaultTableModel.dataVector[i]
                val cfg = LuaCustomParamConfig.createFromVector(vector)
                list.add(cfg)
            }
            return list.toTypedArray()
        }
        set(value) {
            defaultTableModel.dataVector.clear()
            value.forEach {
                addRow(it)
            }
        }

    private fun addRow(it: LuaCustomParamConfig) {
        defaultTableModel.addRow(it.toArray())
    }

    private fun addAction() {
        addRow(LuaCustomParamConfig())
        selectRow(defaultTableModel.rowCount - 1)
    }

    private fun copyAction() {
        val index = paramTable.selectedRow
        val vector = defaultTableModel.dataVector.getOrNull(index)
        if (vector != null) {
            val toIndex = index + 1
            defaultTableModel.insertRow(toIndex, vector.toArray())
            selectRow(toIndex)
        }
    }

    private fun moveUpAction() {
        val index = paramTable.selectedRow
        if (index > 0) {
            val toIndex = index - 1
            defaultTableModel.moveRow(index, index, toIndex)
            selectRow(toIndex)
        }
    }

    private fun moveDownAction() {
        val index = paramTable.selectedRow
        if (index < defaultTableModel.dataVector.size - 1) {
            val toIndex = index + 1
            defaultTableModel.moveRow(index, index, toIndex)
            selectRow(toIndex)
        }
    }

    private fun removeAction() {
        val index = paramTable.selectedRow
        defaultTableModel.removeRow(index)
        paramTable.removeEditor()
        paramTable.selectionModel.clearSelection()
    }

    private fun selectRow(row: Int) {
        paramTable.selectionModel.setSelectionInterval(row, row)
    }
}