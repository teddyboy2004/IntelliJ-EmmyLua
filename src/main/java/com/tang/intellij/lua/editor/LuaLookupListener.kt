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

package com.tang.intellij.lua.editor

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener

/**

 * Created by TangZX on 2016/11/28.
 */
class LuaLookupListener: LookupManagerListener {
    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
        newLookup?.addLookupListener(object : LookupListener {

            override fun beforeItemSelected(event: LookupEvent): Boolean {
                val editor = event.lookup.editor
                // 插入前取消选择，避免一个idea插入的bug（先删除，再替换，一个地方执行两次，导致选择文本后，调用代码补全有问题）
                if (editor.caretModel.caretCount == 1) {
                    editor.caretModel.moveToOffset(editor.selectionModel.selectionEnd)
                    editor.selectionModel.removeSelection()
                }
                return super.beforeItemSelected(event)
            }

            override fun currentItemChanged(event: LookupEvent) {
                super.currentItemChanged(event)
            }
        })
    }
}
