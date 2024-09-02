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
import com.intellij.ui.ColorUtil
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBPanel
import com.tang.intellij.lua.editor.services.StickyLine
import com.tang.intellij.lua.editor.services.StickyPanelManager
import java.awt.*
import javax.swing.BoxLayout

class StickyLinesPanel(
    private val editor: EditorEx,
) : JBPanel<StickyLinesPanel>() {

    private val layeredPane: JBLayeredPane = JBLayeredPane()
    private val stickyComponents: StickyLineComponents = StickyLineComponents(editor, layeredPane)

    private var panelW: Int = 0
    private var panelH: Int = 0

    init {
        border = bottomBorder()
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        layeredPane.layout = null
        add(layeredPane)
    }

    // ------------------------------------------- API -------------------------------------------

    fun repaintLines(startVisualLine: Int, endVisualLine: Int) {
        if (isPanelEnabled()) {
            for (lineComp: StickyLineComponent in stickyComponents.components()) {
                lineComp.repaintIfInRange(startVisualLine, endVisualLine)
            }
        }
    }

    fun repaintLines() {
        if (isPanelEnabled()) {
            repaintLinesImpl()
        }
    }

    // ------------------------------------------- Impl -------------------------------------------

    private fun repaintLinesImpl() {
        val panelWidth: Int = parent.width
        val lineHeight: Int = editor.lineHeight
        var index = 0
        val components: Iterator<StickyLineComponent> = stickyComponents.unboundComponents().iterator()
        val stickyLines = StickyPanelManager.visualStickyLines
        for (stickyLine: StickyLine in stickyLines) {
            val component: StickyLineComponent = components.next()
            component.setLine(
                stickyLine.line,
                stickyLine.index,
                stickyLine.navigateOffset,
                null,
            )
            component.setBounds(0, lineHeight * index, panelWidth, lineHeight)
            component.isVisible = true
            index++
        }
        stickyComponents.resetAfterIndex(index)
        val panelHeight: Int = index * lineHeight
        if (isPanelSizeChanged(panelWidth, panelHeight)) {
            setBounds(0, 0, panelWidth, if (panelHeight == 0) 0 else panelHeight + /*border*/ 1)
            this.panelW = panelWidth
            this.panelH = panelHeight
            layeredPane.setSize(panelWidth, panelHeight)
            revalidate()
        }
        repaint()
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        if (x < 0)
        {
            return
        }
        super.setBounds(x, y, width, height)
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        paintShadow(g)
    }

    private val SHADOW_HEIGHT_FACTOR_LIGHT: Double = 0.2
    private val SHADOW_HEIGHT_FACTOR_DARK: Double = 0.3
    private val SHADOW_COLOR_ALPHA_LIGHT: Int = 13
    private val SHADOW_COLOR_ALPHA_DARK: Int = 45
    private val SHADOW_COLOR_LIGHT = Color(0, 0, 0, SHADOW_COLOR_ALPHA_LIGHT)
    private val SHADOW_COLOR_DARK = Color(0, 0, 0, SHADOW_COLOR_ALPHA_DARK)
    private val SHADOW_COLOR_TRANSPARENT = Color(0, 0, 0, 0)
    fun paintShadow(g: Graphics?) {
        if (g is Graphics2D) {
            val shadowHeight = shadowHeight
            val prevPaint = g.paint
            g.setClip(0, 0, width, height + shadowHeight)
            g.translate(0, height)
            g.paint = GradientPaint(
                0.0f,
                0.0f,
                shadowColor,
                0.0f,
                shadowHeight.toFloat(),
                SHADOW_COLOR_TRANSPARENT
            )
            g.fillRect(0, 0, width, shadowHeight)
            g.paint = prevPaint
            g.translate(0, -height)
            g.setClip(0, 0, width, height)
        }
    }

    private val isDarkColorScheme: Boolean
        get() {
            val background = editor.colorsScheme.defaultBackground
            return ColorUtil.isDark(background)
        }

    private val shadowHeight: Int
        get() {
            val factor = if (isDarkColorScheme) SHADOW_HEIGHT_FACTOR_DARK else SHADOW_HEIGHT_FACTOR_LIGHT
            return (editor.lineHeight * factor).toInt()
        }

    private val shadowColor: Color
        get() {
            return if (isDarkColorScheme) SHADOW_COLOR_DARK else SHADOW_COLOR_LIGHT
        }

    // ------------------------------------------- Utils -------------------------------------------

    private fun isPanelSizeChanged(panelWidth: Int, panelHeight: Int): Boolean {
        return this.panelW != panelWidth || this.panelH != panelHeight
    }

    private fun isPanelEnabled(): Boolean {
        return true
    }

    private fun bottomBorder(): SideBorder {
        return object : SideBorder(null, BOTTOM) {
            override fun getLineColor(): Color {
                val scheme = editor.colorsScheme
                return scheme.defaultBackground
            }
        }
    }
}