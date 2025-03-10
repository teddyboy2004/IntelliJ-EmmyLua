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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.util.ui.MouseEventAdapter
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Represents one editor's line (gutter + line text)
 */
internal class StickyLineComponent(private val editor: EditorEx) : JComponent() {
    private var primaryVisualLine: Int = -1
    private var lineOffset: Int = -1
    private var offsetOnClick: Int = -1
    private var debugText: String? = null
    private var dumbTextImage: BufferedImage? = null
    private var isHovered: Boolean = false
    private val mouseListener = StickyMouseListener()

    init {
        border = null
        addMouseListener(mouseListener)
        addMouseMotionListener(mouseListener)
        addMouseWheelListener(mouseListener)
    }

    fun setLine(
        primaryVisualLine: Int,
        lineOffset: Int,
        offsetOnClick: Int,
        debugText: String?,
    ) {
        this.primaryVisualLine = primaryVisualLine
        this.lineOffset = lineOffset
        this.offsetOnClick = offsetOnClick
        this.debugText = debugText
        this.dumbTextImage = null
        this.isHovered = false
        this.mouseListener.isPopup = false
        this.mouseListener.isGutterHovered = false
    }

    fun resetLine() {
        setLine(primaryVisualLine = -1, lineOffset = -1, offsetOnClick = -1, debugText = null)
    }

    fun isEmpty(): Boolean {
        return primaryVisualLine == -1 || lineOffset == -1 || offsetOnClick == -1
    }

    fun repaintIfInRange(startVisualLine: Int, endVisualLine: Int) {
        if (primaryVisualLine in startVisualLine..endVisualLine) {
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        paintStickyLine(g)
    }

    private fun paintStickyLine(graphicsOrDumb: Graphics?) {
        assert(!isEmpty()) { "sticky panel should mark this line as not visible" }
        val editorY = editorY()
        val lineHeight = lineHeight()
        val gutterWidth = editor.gutterComponentEx.width
        val textWidth = lineWidth() - gutterWidth
        val editorBackground = editor.backgroundColor
        var isBackgroundChanged = false
        try {
            isBackgroundChanged = setStickyLineBackgroundColor()
            if (graphicsOrDumb != null) {
                val editorStartY = if (isLineOutOfPanel()) editorY + y else editorY
                graphicsOrDumb.translate(0, -editorStartY)
                paintGutter(graphicsOrDumb, editorY, lineHeight, gutterWidth)
                paintText(graphicsOrDumb, editorY, lineHeight, gutterWidth, textWidth)
            } else {
                dumbTextImage = prepareDumbTextImage(editorY, lineHeight, textWidth)
            }
        } finally {
            if (isBackgroundChanged) {
                editor.backgroundColor = editorBackground
            }
        }
    }

    private fun setStickyLineBackgroundColor(): Boolean {
        val backgroundColor = if (isHovered) {
            editor.colorsScheme.getColor(EditorColors.CARET_ROW_COLOR)
        } else {
            editor.colorsScheme.defaultBackground
        }
        if (backgroundColor != null) {
            editor.backgroundColor = backgroundColor
            return true
        }
        return false
    }

    @Suppress("SSBasedInspection")
    private fun paintGutter(g: Graphics, editorY: Int, lineHeight: Int, gutterWidth: Int) {
        g.setClip(0, editorY, gutterWidth, lineHeight)
        editor.gutterComponentEx.paint(g)
    }

    @Suppress("SSBasedInspection")
    private fun paintText(g: Graphics, editorY: Int, lineHeight: Int, gutterWidth: Int, textWidth: Int) {
        g.translate(gutterWidth, 0)
        g.setClip(0, editorY, textWidth, lineHeight)
        val textImage = dumbTextImage
        if (textImage != null && (editor as EditorImpl).isDumb) {
            StartupUiUtil.drawImage(g, textImage, 0, editorY, null)
        } else {
            doPaintText(g)
            dumbTextImage = null
        }
        val color = g.color
        g.color = editor.colorsScheme.getColor(EditorColors.CARET_ROW_COLOR)
        val drawY = editorY + lineHeight - 1
        g.drawLine(0, drawY, textWidth, drawY)
        g.color = color
    }

    private fun prepareDumbTextImage(editorY: Int, lineHeight: Int, textWidth: Int): BufferedImage {
        val textImage = UIUtil.createImage(
            editor.contentComponent,
            textWidth,
            lineHeight,
            BufferedImage.TYPE_INT_RGB,
        )
        val textGraphics = textImage.graphics
        EditorUIUtil.setupAntialiasing(textGraphics)
        textGraphics.translate(0, -editorY)
        textGraphics.setClip(0, editorY, textWidth, lineHeight)
        doPaintText(textGraphics)
        textGraphics.dispose()
        return textImage
    }

    private fun doPaintText(g: Graphics) {
        editor.contentComponent.paint(g)
    }

    private fun lineWidth(): Int {
        return width
    }

    private fun lineHeight(): Int {
        val height = editor.lineHeight
        return if (isLineOutOfPanel()) height + y else height
    }

    private fun editorY(): Int {
        val editorY = editor.visualLineToY(primaryVisualLine)
        return if (isLineOutOfPanel()) editorY - y else editorY
    }

    private fun isLineOutOfPanel(): Boolean {
        // IDEA-346734 special case when sticky line is out of sticky panel,
        // need to adjust painting to avoid overlapping sticky line and tab panel
        return y < 0
    }

    override fun toString(): String {
        return "${debugText ?: ""}(primary=$primaryVisualLine, scope=$lineOffset, onClick=$offsetOnClick)"
    }

    internal class MyMouseEvent(e: MouseEvent, source: Component, y: Int) : MouseEvent(
        source,
        e.id,
        e.`when`,
        UIUtil.getAllModifiers(e),
        e.x,
        y,
        e.clickCount,
        e.isPopupTrigger,
        e.button,
    )

    private inner class StickyMouseListener : MouseListener, MouseMotionListener, MouseWheelListener {
        var isPopup = false
        var isGutterHovered = false

        init {
            val actionManager = ActionManager.getInstance()
//      val actionGroup = actionManager.getAction("EditorStickyLinesSettings") as DefaultActionGroup
//      popMenu = actionManager.createActionPopupMenu("StickyLine", actionGroup).component
        }

        override fun mousePressed(e: MouseEvent?) = handleEvent(e)
        override fun mouseReleased(e: MouseEvent?) = handleEvent(e)
        override fun mouseClicked(e: MouseEvent?) = handleEvent(e)
        override fun mouseEntered(e: MouseEvent?) = handleEvent(e)
        override fun mouseExited(e: MouseEvent?) = handleEvent(e)
        override fun mouseDragged(e: MouseEvent?) = handleEvent(e)
        override fun mouseMoved(e: MouseEvent?) = handleEvent(e)
        override fun mouseWheelMoved(e: MouseWheelEvent?) = handleEvent(e)

        private fun handleEvent(event: MouseEvent?) {
            if (event == null || event.isConsumed || isEmpty()) {
                return
            }
            when (event.id) {
                MouseEvent.MOUSE_ENTERED,
                MouseEvent.MOUSE_EXITED,
                MouseEvent.MOUSE_MOVED -> {
                    onHover(event)
                }

                MouseEvent.MOUSE_PRESSED,
                MouseEvent.MOUSE_RELEASED,
                MouseEvent.MOUSE_CLICKED -> {
                    if (isGutterEvent(event)) {
                        gutterClick(event)
                    } else {
                        popupOrNavigate(event)
                    }
                }

                MouseEvent.MOUSE_WHEEL -> {
                    forwardToScrollPane(event)
                }
            }
            event.consume()
        }

        private fun forwardToScrollPane(event: MouseEvent) {
            val converted = MouseEventAdapter.convert(event, editor.scrollPane)
            editor.scrollPane.dispatchEvent(converted)
        }

        private fun onHover(event: MouseEvent) {
            val isGutterEvent = isGutterEvent(event)
            when (event.id) {
                MouseEvent.MOUSE_ENTERED -> {
                    onTextHover(!isGutterHovered)
                    onGutterHover(isGutterHovered)
                }

                MouseEvent.MOUSE_EXITED -> {
                    onTextHover(false)
                    onGutterHover(false)
                }

                MouseEvent.MOUSE_MOVED -> {
                    if (isGutterEvent && isHovered && !isGutterHovered) {
                        onTextHover(false)
                        onGutterHover(true)
                    } else if (!isGutterEvent && !isHovered && isGutterHovered) {
                        onTextHover(true)
                        onGutterHover(false)
                    }
                }

                else -> throwUnhandledEvent(event)
            }
        }

        private fun onTextHover(hovered: Boolean) {
            if (hovered != isHovered) {
                isHovered = hovered
                repaint()
            }
        }

        private fun onGutterHover(hovered: Boolean) {
            if (hovered != isGutterHovered) {
                isGutterHovered = hovered
                //(editor as EditorImpl).onGutterHover(hovered)
                repaint()
            }
        }

        private fun gutterClick(event: MouseEvent) {
//      if (event.id == MouseEvent.MOUSE_PRESSED || (event.id == MouseEvent.MOUSE_RELEASED && event.isPopupTrigger)) {
//        val converted = convert(event)
//        val mouseListener = (editor as EditorImpl).editorMouseListener()
//        if (!event.isPopupTrigger) {
//          event.consume()
//          return
//        }
//        if (event.id == MouseEvent.MOUSE_PRESSED) {
//          mouseListener.mousePressed(converted)
//        } else {
//          mouseListener.mouseReleased(converted)
//        }
//      }
        }

        private fun popupOrNavigate(event: MouseEvent) {
            when (event.id) {
                MouseEvent.MOUSE_PRESSED -> {
                    isPopup = event.isPopupTrigger
                    if (isPopup) {
//            popMenu.show(event.component, event.x, event.y)
                    }
                }

                MouseEvent.MOUSE_RELEASED -> {
                    // From review: on some platform RELEASED event can be a popup trigger
                    if (!isPopup) {
                        isPopup = event.isPopupTrigger
                        if (isPopup) {
//              popMenu.show(event.component, event.x, event.y)
                        }
                    }
                }

                MouseEvent.MOUSE_CLICKED -> {
                    if (!isPopup) {
                        this@StickyLineComponent.requestFocusInWindow() // in case of focused tool window IJPL-157157
                        // wrap into command to support "Back navigation" IJPL-591
                        CommandProcessor.getInstance().executeCommand(
                            editor.project,
                            {
                                if (isEmpty()) {
                                    return@executeCommand
                                }
                                val offset = offsetOnClick
                                val lineNum = editor.offsetToLogicalPosition(offset).line
                                val line = lineNum - lineOffset
                                editor.scrollingModel.scrollTo(LogicalPosition(line, 0), ScrollType.RELATIVE)
                                editor.selectionModel.removeSelection(/* allCarets = */ true)
                                editor.caretModel.moveToOffset(offset)
                                IdeDocumentHistory.getInstance(editor.project).includeCurrentCommandAsNavigation()
                            },
                            "",
                            DocCommandGroupId.noneGroupId(editor.document),
                            UndoConfirmationPolicy.DEFAULT,
                            editor.document
                        )
                    }
                }

                else -> throwUnhandledEvent(event)
            }
        }

        private fun isGutterEvent(event: MouseEvent): Boolean {
            return event.x <= editor.gutterComponentEx.width
        }

        private fun convert(event: MouseEvent): MouseEvent {
            val y = if (event.isPopupTrigger) {
                val point = event.locationOnScreen
                SwingUtilities.convertPointFromScreen(point, editor.gutterComponentEx)
                point.y
            } else {
                editor.visualLineToY(primaryVisualLine) + event.y
            }
            return MyMouseEvent(event, editor.gutterComponentEx, y)
        }

        private fun throwUnhandledEvent(event: MouseEvent) {
            throw IllegalArgumentException("unhandled event $event")
        }
    }
}