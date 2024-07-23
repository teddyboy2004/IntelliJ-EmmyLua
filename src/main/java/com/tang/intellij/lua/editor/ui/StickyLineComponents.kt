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
package com.tang.intellij.lua.editor.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.components.JBLayeredPane

internal class StickyLineComponents(private val editor: EditorEx, private val layeredPane: JBLayeredPane) {
  private val components: MutableList<StickyLineComponent> = mutableListOf()

  fun components(): Sequence<StickyLineComponent> {
    return components.asSequence().filter { !it.isEmpty() }
  }

  fun unboundComponents(): Sequence<StickyLineComponent> {
    return Sequence {
      object : Iterator<StickyLineComponent> {
        private var index = 0

        override fun hasNext(): Boolean {
          return true
        }

        override fun next(): StickyLineComponent {
          val component: StickyLineComponent
          if (index < components.size) {
            component = components[index]
          } else {
            component = StickyLineComponent(editor)
            layeredPane.add(component, (200 - components.size) as Any)
            components.add(component)
          }
          index++
          return component
        }
      }
    }
  }

  fun resetAfterIndex(index: Int) {
    for (i in index..components.lastIndex) {
      components[i].resetLine()
      components[i].isVisible = false
    }
  }

  fun clear(): Boolean {
    if (components.isEmpty()) {
      return false
    }
    components.clear()
    layeredPane.removeAll()
    return true
  }
}