package com.tang.intellij.lua.editor.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.Alarm
import com.tang.intellij.lua.editor.ui.StickyLinesPanel
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JScrollPane

class StickyPanelManager(
    val project: Project,
    val editor: EditorImpl,
    val fem: FileEditorManager,
    val textEditor: TextEditor
) : VisibleAreaListener, Disposable, CaretListener {
    val stickyPanel: StickyLinesPanel = StickyLinesPanel(editor)

    private var activeVisualArea: Rectangle = Rectangle()
    private var activeVisualLine: Int = -1
    private val checkCaretAlarm = Alarm()

    init {
        Disposer.register(editor.disposable, this)
        editor.scrollingModel.addVisibleAreaListener(this, this)
        editor.caretModel.addCaretListener(this, this)
        instance = this
    }

    override fun caretPositionChanged(event: CaretEvent) {
        super.caretPositionChanged(event)
        checkCaretAlarm.cancelAllRequests()
        checkCaretAlarm.addRequest({
            checkCaretPosition()
        }, 10)
    }

    private fun checkCaretPosition() {
        val visualPosition = editor.caretModel.currentCaret.visualPosition
        val xy = editor.visualPositionToXY(visualPosition)
        val minY = activeVisualArea.y + stickyPanel.height
        if (xy.y < minY) {
            editor.scrollingModel.scrollVertically(editor.scrollingModel.verticalScrollOffset - minY + xy.y)
        }
    }

    override fun visibleAreaChanged(event: VisibleAreaEvent) {
        val needAppend = stickyPanel.parent == null
        if (needAppend) {
            val component = getParentComponent()
            if (component is JScrollPane) {
                component.add(stickyPanel, 0 as Any, 7)
            }
        }
        if (isAreaChanged(event) || needAppend) {
            activeVisualArea = event.newRectangle
            if (activeVisualArea.y < 3) {
                // special case when the document starts with a sticky line
                // small visual jump is better than stickied line for good
                resetLines()
            } else if (event.oldRectangle == null || isLineChanged()) {
                recalculateAndRepaintLines()
            } else if (isYChanged(event) || isSizeChanged(event)) {
                repaintLines()
            }
        }
    }

    private fun getParentComponent(): JComponent? {
        var component: JComponent? = textEditor.preferredFocusedComponent
        while (component != null) {
            component.parent?.let {
                if (it is JScrollPane) {
                    return it
                }
                component = it as JComponent
            }
        }
        return component
    }

    fun recalculateAndRepaintLines(force: Boolean = false) {
        if (force) {
            activeVisualArea = editor.scrollingModel.visibleArea
            isLineChanged() // activeVisualLine updated as a side effect
        }
        if (activeVisualLine != -1 && !isPoint(activeVisualArea)) {
            recalculate(activeVisualArea)
            repaintLines()
        }
    }

    private fun recalculate(visibleArea: Rectangle) {
        visualStickyLines.clear()
        val stickyScrollMaxLevel = LuaSettings.instance.stickyScrollMaxLevel
        if (stickyScrollMaxLevel == 0)
            return
        val pos = editor.xyToVisualPosition(
            Point(
                visibleArea.width, visibleArea.y
            )
        )
        for (i in 1 until stickyScrollMaxLevel) {
            val showLevel = tryInitStickyLine(pos, i, stickyScrollMaxLevel)
            if (showLevel == i) {
                return
            }
        }
        visualStickyLines.clear()
        // 没有的话兜底0
        tryInitStickyLine(pos, 0, stickyScrollMaxLevel)
    }

    private fun tryInitStickyLine(pos: VisualPosition, addLine: Int, stickyScrollMaxLevel: Int): Int {
        visualStickyLines.clear()
        var visualPos = pos
        runCatching { visualPos = VisualPosition(visualPos.line + addLine, 0) }

        val positionToOffset = editor.visualPositionToOffset(visualPos) - 1;
        if (positionToOffset < 0) {
            return 0
        }
        val document = editor.document
        val startLine = visualPos.line
        var showLevel = 0
        if (startLine > 0) {
            val psiFile: PsiFile? = PsiDocumentManager.getInstance(project).getPsiFile(document)
            val currentElement = psiFile?.findElementAt(positionToOffset - 1)
            val parents = currentElement?.parents(false)?.filter { acceptElement(it) && it.textRange.contains(positionToOffset) }
            if (parents != null) {
                var preline = -1
                val finalParent = parents.toList().reversed()
                for (parent in finalParent) {
                    val preSize = visualStickyLines.size
                    addStickyLine(parent, document, preline, startLine)
                    if (visualStickyLines.size > preSize) {
                        showLevel++
                        preline = visualStickyLines.last().logicLine
                        if (showLevel >= stickyScrollMaxLevel) {
                            break
                        }
                    }
                }
            }
        }
        return showLevel
    }

    private fun acceptElement(element: PsiElement): Boolean {
        return when {
            element is LuaStatement && element !is LuaExprStat -> true
            element is LuaFuncBodyOwner -> true
            element is LuaTableField -> true
            else -> false
        }
    }

    private fun addStickyLine(element: PsiElement, document: Document, preLine: Int, srcLine: Int) {
        var checkSameLine = true
        val parentStartOffset = element.textRange.startOffset
        val firstChildOffset = when {
            element is LuaIfStat -> {
                val list = element.children.filter(fun(it: PsiElement): Boolean {
                    if (it !is LuaBlock) {
                        return false
                    }
//                    val ifStartEle = getIfBlockStartElement(it) ?: return false
//                    return editor.offsetToVisualLine(ifStartEle.startOffset) <= srcLine
                    return editor.offsetToVisualLine(it.startOffset) <= srcLine
                }).toMutableList()
                val l = list.map { getIfBlockStartElement(it as LuaBlock) }.toMutableList()
                // 只显示最后一个elseif
                if (l.count() > 2) {  // if elseif else
                    l.filter { it.elementType == LuaTypes.ELSEIF }.reversed().forEachIndexed { index, psiElement ->
                        if (index > 0) {
                            l.remove(psiElement)
                        }
                    }
                }
                l.forEach { addStickyLine(it!!, document, preLine, srcLine) }
                return
            }

            element.elementType == LuaTypes.IF || element.elementType == LuaTypes.ELSEIF || element.elementType == LuaTypes.ELSE -> {
                checkSameLine = false
                element.textRange.endOffset
            }

//            element is LuaBlock -> {
//                if (element.parent !is LuaIfStat)
//                    return
//                val startElement = getIfBlockStartElement(element) ?: return
//                startElement.textRange.endOffset
//            }
            else -> {
                when {
                    element.firstChild.textRange.startOffset == parentStartOffset && element.children.size > 1 -> {
                        element.firstChild.nextSibling.textRange.endOffset
                    }

                    else -> {
                        element.firstChild.textRange.endOffset
                    }
                }
            }
        }
        val visualEndLine = editor.offsetToVisualLine(element.textRange.endOffset)
        val visualStartLine = editor.offsetToVisualLine(parentStartOffset)
        if (checkSameLine && visualStartLine == visualEndLine) {
            return
        }
        val startLine = document.getLineNumber(firstChildOffset)
        // 跟之前同一个不显示，当前行大于显示行也不需要，避免注释问题
        if (startLine <= preLine || startLine >= srcLine) {
            return
        }
        visualStickyLines.add(StickyLine(startLine, firstChildOffset, visualStickyLines.size))
    }

    private fun getIfBlockStartElement(element: LuaBlock): PsiElement? {
        val ifStartOffset = element.parent.textRange.startOffset
        var prevSibling = element.prevSibling
        while (prevSibling != null && prevSibling.textRange.startOffset >= ifStartOffset) {
            if (prevSibling.elementType == LuaTypes.IF || prevSibling.elementType == LuaTypes.ELSE || prevSibling.elementType == LuaTypes.ELSEIF) {
                break
            } else {
                prevSibling = prevSibling.prevSibling
            }
        }
        return prevSibling
    }

    private fun resetLines() {
        activeVisualLine = -1
        visualStickyLines.clear()
        repaintLines()
    }

    private fun repaintLines() {
        stickyPanel.repaintLines()
    }

    private fun isAreaChanged(event: VisibleAreaEvent): Boolean {
        val oldRectangle: Rectangle? = event.oldRectangle
        return oldRectangle == null ||
                oldRectangle.y != event.newRectangle.y ||
                oldRectangle.height != event.newRectangle.height ||
                oldRectangle.width != event.newRectangle.width
    }

    private fun isLineChanged(): Boolean {
        val newVisualLine: Int = editor.yToVisualLine(activeVisualArea.y)
        if (activeVisualLine != newVisualLine) {
            activeVisualLine = newVisualLine
            return true
        }
        return false
    }

    private fun isYChanged(event: VisibleAreaEvent): Boolean {
        return event.oldRectangle.y != event.newRectangle.y
    }

    private fun isSizeChanged(event: VisibleAreaEvent): Boolean {
        return event.oldRectangle.width != event.newRectangle.width ||
                event.oldRectangle.height != event.newRectangle.height
    }

    private fun isPoint(rectangle: Rectangle): Boolean {
        return rectangle.x == 0 &&
                rectangle.y == 0 &&
                rectangle.height == 0 &&
                rectangle.width == 0
    }


    override fun dispose() {
    }

    companion object {
        var instance: StickyPanelManager? = null
        val visualStickyLines: MutableList<StickyLine> = mutableListOf()
    }
}

class StickyLine(var logicLine: Int, var navigateOffset: Int, val index: Int) {
}