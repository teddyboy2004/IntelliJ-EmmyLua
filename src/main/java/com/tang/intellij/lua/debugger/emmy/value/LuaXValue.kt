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

package com.tang.intellij.lua.debugger.emmy.value

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*
import com.tang.intellij.lua.debugger.LuaXBoolPresentation
import com.tang.intellij.lua.debugger.LuaXNumberPresentation
import com.tang.intellij.lua.debugger.LuaXStringPresentation
import com.tang.intellij.lua.debugger.emmy.EmmyDebugStackFrame
import com.tang.intellij.lua.debugger.emmy.LuaValueType
import com.tang.intellij.lua.debugger.emmy.VariableValue
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.project.LuaSettings
import java.awt.datatransfer.StringSelection
import java.util.*
import java.util.Comparator
import javax.swing.Icon

abstract class LuaXValue(val value: VariableValue) : XValue() {
    companion object {
        var isUpvalue: Boolean = true

        fun create(v: VariableValue, frame: EmmyDebugStackFrame): LuaXValue {
            return when (v.valueTypeValue) {
                LuaValueType.TSTRING -> StringXValue(v)
                LuaValueType.TNUMBER -> NumberXValue(v)
                LuaValueType.TBOOLEAN -> BoolXValue(v)
                LuaValueType.TUSERDATA,
                LuaValueType.TTABLE,
                    -> TableXValue(v, frame)

                LuaValueType.GROUP -> GroupXValue(v, frame)
                else -> AnyXValue(v)
            }
        }
    }

    val name: String
        get() {
            return value.name
        }

    var parent: LuaXValue? = null

    fun getIcon(): Icon {
        if (value.nameTypeValue == LuaValueType.TNUMBER) {
            return LuaIcons.LOCAL_NUM_VAR
        }
        return LuaIcons.LOCAL_VAR
    }
}

private object VariableComparator : Comparator<VariableValue> {
    override fun compare(o1: VariableValue, o2: VariableValue): Int {
        val w1 = if (o1.fake) 0 else 1
        val w2 = if (o2.fake) 0 else 1
        if (w1 != w2)
            return w1.compareTo(w2)
        if (o1.nameType != o2.nameType) {
            return o1.nameType.compareTo(o2.nameType)
        }
        val n1 = o1.name.toIntOrNull()
        val n2 = o2.name.toIntOrNull()
        if (n1 != null && n2 != null) {
            return n1.compareTo(n2)
        }
        return o1.name.compareTo(o2.name)
    }
}

class StringXValue(v: VariableValue) : LuaXValue(v) {
    override fun computePresentation(xValueNode: XValueNode, place: XValuePlace) {
        xValueNode.setPresentation(getIcon(), LuaXStringPresentation(value.value), false)
    }
}

class NumberXValue(v: VariableValue) : LuaXValue(v) {
    override fun computePresentation(xValueNode: XValueNode, place: XValuePlace) {
        xValueNode.setPresentation(getIcon(), LuaXNumberPresentation(value.value), false)
    }
}

class BoolXValue(val v: VariableValue) : LuaXValue(v) {
    override fun computePresentation(xValueNode: XValueNode, place: XValuePlace) {
        xValueNode.setPresentation(getIcon(), LuaXBoolPresentation(v.value), false)
    }
}

class AnyXValue(val v: VariableValue) : LuaXValue(v) {
    override fun computePresentation(xValueNode: XValueNode, place: XValuePlace) {
        var icon = getIcon()
        var valueTypeName = v.valueTypeName
        if (valueTypeName == "function") {
            icon = LuaIcons.LOCAL_FUNCTION
        }
        xValueNode.setPresentation(icon, null, v.value, false)
    }
}

class GroupXValue(v: VariableValue, val frame: EmmyDebugStackFrame) : LuaXValue(v) {
    private val children = mutableListOf<LuaXValue>()

    init {
        value.children?.sortedWith(VariableComparator)?.forEach {
            children.add(create(it, frame))
        }
    }

    override fun computePresentation(xValueNode: XValueNode, place: XValuePlace) {
        xValueNode.setPresentation(AllIcons.Nodes.UpLevel, null, value.value, true)
    }

    override fun computeChildren(node: XCompositeNode) {
        val cl = XValueChildrenList()
        children.forEach {
            it.parent = this
            cl.add(it.name, it)
        }
        node.addChildren(cl, true)
    }
}

class TableXValue(v: VariableValue, val frame: EmmyDebugStackFrame) : LuaXValue(v) {

    private val children = mutableListOf<LuaXValue>()

    init {
        value.children?.sortedWith(VariableComparator)?.forEach {
            children.add(create(it, frame))
        }
    }

    override fun computePresentation(xValueNode: XValueNode, place: XValuePlace) {
        var icon = AllIcons.Json.Object
        if (value.nameTypeValue == LuaValueType.TNUMBER) {
            icon = LuaIcons.LOCAL_NUM_TABLE
        }
        if (value.valueTypeName == "C#") {
            icon = LuaIcons.CSHARP
        } else if (value.valueTypeName == "C++") {
            icon = LuaIcons.CPP
        }
        xValueNode.setPresentation(icon, null, value.value, true)
    }

    override fun computeChildren(node: XCompositeNode) {
        val ev = this.frame.evaluator
        if (ev != null) {
            val cacheId = value.cacheId
            ev.eval(evalExpr, cacheId, object : XDebuggerEvaluator.XEvaluationCallback {
                override fun errorOccurred(err: String) {
                    node.setErrorMessage(err)
                }

                override fun evaluated(value: XValue) {
                    if (value is TableXValue) {
                        children.clear()
                        children.addAll(value.children)
                        children.forEach {
                            it.parent = this@TableXValue
                        }
                        addChildrenToNode(node)
                    } else { // todo: table is nil?
                        node.setErrorMessage("nil")
                    }
                }

            }, 2)
        } else super.computeChildren(node)
    }

    fun addChildrenToNode(node: XCompositeNode) {
        val cl = XValueChildrenList()
        val metadataList = mutableListOf<LuaXValue>()
        val funcList = mutableListOf<LuaXValue>()
        val otherList = mutableListOf<LuaXValue>()
        children.forEach {
            if (it.name.startsWith("__") || it.name.startsWith("(metatable")) {
                metadataList.add(it)
            } else if (it.value.valueTypeValue == LuaValueType.TFUNCTION) {
                funcList.add(it)
            } else {
                otherList.add(it)
            }
        }

        val comparator= object : Comparator<LuaXValue> {
            override fun compare(o1: LuaXValue, o2: LuaXValue): Int {
                val n1 = o1.name.toIntOrNull()
                val n2 = o2.name.toIntOrNull()
                if (n1 != null && n2 != null) {
                    return n1.compareTo(n2)
                }
                return o1.name.compareTo(o2.name)
            }
        }
        otherList.sortWith(comparator)
        funcList.sortWith(comparator)
        metadataList.sortWith(comparator)

        otherList.forEach {
            cl.add(it.name, it)
        }
        val ignoreFunction = LuaSettings.instance.debuggerIgnoreFunction
        if (!ignoreFunction) {
            funcList.forEach {
                cl.add(it.name, it)
            }
        }
        val ignoreMetadata = LuaSettings.instance.debuggerIgnoreMetadata
        if (!ignoreMetadata) {
            metadataList.forEach {
                cl.add(it.name, it)
            }
        }
        var remainCount = funcList.size + metadataList.size
        if (!(LuaSettings.instance.debuggerShowMoreValue)) {
            remainCount = 0
        }
        node.addChildren(cl, remainCount == 0)
        if (remainCount > 0) {
            node.tooManyChildren(remainCount, {
                val cl = XValueChildrenList()
                funcList.forEach {
                    cl.add(it.name, it)
                }
                metadataList.forEach {
                    cl.add(it.name, it)
                }
                node.addChildren(cl, true)
            })
        }
    }



    fun convertToLuaString(sb: StringBuffer, name: String, luaValue: LuaXValue) {
        if (name.startsWith("(") || name.startsWith("_")) {
            return
        }
        when (luaValue) {
            is TableXValue -> {
                sb.append(name)
                sb.append(" = {")
                luaValue.children.forEach {
                    val value = it
                    val len = sb.length
                    convertToLuaString(sb, value.value.name, value)
                    if (sb.length != len) {
                        sb.append(", ")
                    }
                }
                sb.append("}")
            }

            is AnyXValue -> {
            }

            is StringXValue -> {
                sb.append(name)
                sb.append(" = '")
                sb.append(luaValue.value.value)
                sb.append("'")
            }

            else -> {
                sb.append(name)
                sb.append(" = ")
                sb.append(luaValue.value.value)
            }
        }
    }

    fun copyAsTableString() {
        val ev = this.frame.evaluator
        ev?.eval(evalExpr, value.cacheId, object : XDebuggerEvaluator.XEvaluationCallback {
            override fun errorOccurred(err: String) {
            }

            override fun evaluated(value: XValue) {
                if (value is TableXValue) {
                    val sb = StringBuffer()
                    convertToLuaString(sb, name, value)
                    CopyPasteManager.getInstance().setContents(StringSelection(sb.toString()))
                }
            }

        }, 5)
    }


    private val evalExpr: String
        get() {
            var name = name
            val properties = ArrayList<String>()
            var parent = this.parent
            while (parent != null) {
                if (!parent.value.fake) {
                    properties.add(name)
                    name = parent.name
                }
                parent = parent.parent
            }

            val sb = StringBuilder(name)
            for (i in properties.indices.reversed()) {
                val parentName = properties[i]
                if (parentName.startsWith("["))
                    sb.append(parentName)
                else
                    sb.append(String.format("[\"%s\"]", parentName))
            }
            return sb.toString()
        }
}