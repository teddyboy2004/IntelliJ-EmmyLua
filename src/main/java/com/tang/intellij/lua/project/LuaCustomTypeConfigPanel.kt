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
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer


class NumericCellEditor : AbstractCellEditor(), TableCellEditor {
    private val textField = JTextField()

    init {
        textField.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                val c = e.keyChar
                if (!Character.isDigit(c) && c.code != KeyEvent.VK_BACK_SPACE) {
                    e.consume() // 忽略非数字输入
                }
            }
        })
    }

    override fun getCellEditorValue(): Any {
        val text = textField.text
        return (if (text.isEmpty()) null else text.toInt())!!
    }

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component {
        textField.text = value.toString()
        return textField
    }
}

class CheckBoxRenderer : JCheckBox(), TableCellRenderer {
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        if (table == null) {
            return this
        }
        var isSelected = isSelected
        isSelected = isSelected || (value as Boolean)
        isSelected = isSelected && !hasFocus
        isSelected = isSelected && (table.isCellSelected(row, 0))

        isSelected = isSelected || (value as Boolean)
        isSelected = isSelected && !hasFocus

        isSelected = isSelected || (value as Boolean)

        this.isSelected = value as Boolean
        background = if (isSelected) table.selectionBackground else table.background
        foreground = if (isSelected) table.selectionForeground else table.foreground

        return this
    }

}

class LuaCustomTypeConfigPanel : JPanel(BorderLayout()) {
    private val returnTypeComboBox = JComboBox(LuaCustomReturnType.values())
    private val handleComboBox = JComboBox(LuaCustomHandleType.values())
    private val columnNames = LuaCustomTypeConfig.ColumnNames
    private val defaultTableModel = DefaultTableModel(columnNames, 0)
    private val typeTable = JBTable(defaultTableModel)

    init {
        val component = ToolbarDecorator.createDecorator(typeTable)
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
//        typeTable.autoResizeMode = JBTable.AUTO_RESIZE_OFF
//        columnNames.forEach {
//            val column = typeTable.getColumn(it)
//            column.minWidth = 150
//        }
        border = IdeBorderFactory.createTitledBorder(LuaBundle.message("ui.settings.custom_type_cfg"), false)
        typeTable.getColumn("ReturnType").cellEditor = DefaultCellEditor(returnTypeComboBox)
        typeTable.getColumn("HandleType").cellEditor = DefaultCellEditor(handleComboBox)
        setTableNumericCell("ParamIndex")
        setTableCheckbox("Break")
    }

    private fun setTableNumericCell(columnName: String) {
        val column = typeTable.getColumn(columnName) ?: return
        column.cellEditor = NumericCellEditor()
    }

    private fun setTableCheckbox(columnName: String) {
        val column = typeTable.getColumn(columnName) ?: return
        column.cellRenderer = CheckBoxRenderer()
        column.cellEditor = DefaultCellEditor(JCheckBox())
    }

    var roots: Array<LuaCustomTypeConfig>
        get() {
            val rowCount = defaultTableModel.rowCount
            val list = mutableListOf<LuaCustomTypeConfig>()
            for (i in 0 until rowCount) {
                val vector = defaultTableModel.dataVector.get(i)
                val cfg = LuaCustomTypeConfig.createFromVector(vector)
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

    private fun addRow(it: LuaCustomTypeConfig) {
        defaultTableModel.addRow(it.toArray())
    }

    private fun addAction() {
        addRow(LuaCustomTypeConfig())
        selectRow(defaultTableModel.rowCount - 1)
    }

    private fun copyAction() {
        val index = typeTable.selectedRow
        val vector = defaultTableModel.dataVector.getOrNull(index)
        if (vector != null) {
            val toIndex = index + 1
            defaultTableModel.insertRow(toIndex, vector.toArray())
            selectRow(toIndex)
        }
    }

    private fun moveUpAction() {
        val index = typeTable.selectedRow
        if (index > 0) {
            val toIndex = index - 1
            defaultTableModel.moveRow(index, index, toIndex)
            selectRow(toIndex)
        }
    }

    private fun moveDownAction() {
        val index = typeTable.selectedRow
        if (index < defaultTableModel.dataVector.size - 1) {
            val toIndex = index + 1
            defaultTableModel.moveRow(index, index, toIndex)
            selectRow(toIndex)
        }
    }

    private fun removeAction() {
        val index = typeTable.selectedRow
        defaultTableModel.removeRow(index)
        typeTable.removeEditor()
        typeTable.selectionModel.clearSelection()
    }

    private fun selectRow(row: Int) {
        typeTable.selectionModel.setSelectionInterval(row, row)
    }
}