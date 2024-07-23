//package com.tang.intellij.lua.editor.listener
//
//import com.intellij.openapi.Disposable
//import com.intellij.openapi.editor.Document
//import com.intellij.openapi.editor.LogicalPosition
//import com.intellij.openapi.editor.event.VisibleAreaEvent
//import com.intellij.openapi.editor.event.VisibleAreaListener
//import com.intellij.openapi.util.TextRange
//import com.intellij.psi.PsiDocumentManager
//import com.intellij.psi.PsiElement
//import com.intellij.psi.PsiFile
//import com.intellij.psi.util.PsiTreeUtil
//import com.intellij.psi.util.parents
//import com.intellij.refactoring.suggested.endOffset
//import com.intellij.refactoring.suggested.startOffset
//import com.tang.intellij.lua.editor.services.StickyPanelManager
//import com.tang.intellij.lua.editor.ui.MyEditorFragmentComponent
//import com.tang.intellij.lua.project.LuaSettings
//import com.tang.intellij.lua.psi.LuaDeclaration
//import com.tang.intellij.lua.psi.LuaPsiFile
//import java.awt.Point
//import java.awt.Rectangle
//
//class ScrollListener(val stickyPanelManager: StickyPanelManager) : VisibleAreaListener, Disposable {
//
//    val editor = stickyPanelManager.editor
//    private var activeVisualArea: Rectangle = Rectangle()
//    private var activeVisualLine: Int = -1
//    private var activeIsEnabled: Boolean = false
//    private var activeLineLimit: Int = -1
//
//    init {
//        editor.scrollingModel.addVisibleAreaListener(this, stickyPanelManager)
//    }
//
//    override fun visibleAreaChanged(e: VisibleAreaEvent) {
////        var logicalPosition = editor.xyToLogicalPosition(
////            Point(
////                editor.scrollingModel.visibleArea.width, editor.scrollingModel.visibleArea.y
////            )
////        )
////        runCatching { logicalPosition = LogicalPosition(logicalPosition.line - 1, logicalPosition.column) }
////
////        val positionToOffset = editor.logicalPositionToOffset(logicalPosition);
////        val document = editor.document
////        stickyPanelManager.clearPanelList()
////        if (document.getLineNumber(positionToOffset) > 0) {
////            val psiFile: PsiFile? = PsiDocumentManager.getInstance(stickyPanelManager.project).getPsiFile(document)
////            if (psiFile !is LuaPsiFile)
////                return
////            val currentElement = psiFile.findElementAt(positionToOffset - 1)
////            val parents = kotlin.runCatching {
////                currentElement?.parents(false)?.filter { element -> element is LuaDeclaration }
////            }.getOrNull()
////
////            parents?.toList()
////            var yDelta = 0
////            if (parents != null) {
////                yDelta += 1
////                for (parent in parents.toList().reversed().take(LuaSettings.instance.stickyScrollMaxLine)) {
////                   val result = getTextRangeAndStartLine(parent, document)
////
////                    val hint = MyEditorFragmentComponent.showEditorFragmentHint(
////                        editor, result.first, true, false, yDelta * editor.lineHeight
////                    )
////                    hint?.let { stickyPanelManager.addPanel(it, result.second) }
////                }
////            }
////            stickyPanelManager.addTopLabels()
////        }
//        val activeVisualArea = e.newRectangle
//        if (activeVisualArea.y < 3) {
//            // special case when the document starts with a sticky line
//            // small visual jump is better than stickied line for good
//            resetLines()
//        } else if (e.oldRectangle == null || isLineChanged()) {
//            recalculateAndRepaintLines()
//        } else if (isYChanged(e) || isSizeChanged(e)) {
//            repaintLines()
//        }
//    }
//
//    private fun resetLines() {
//        activeVisualLine = -1
//        visualStickyLines.clear()
//        repaintLines()
//    }
//
//    fun getTextRangeAndStartLine(element: PsiElement, document: Document): Pair<TextRange, Int> {
//        val parentStartOffset = element.startOffset
////        val parentLine = document.getLineNumber(parentStartOffset)
////                val parentLine = parent.startLine(document)
////                val parentEndLine = parent.endLine(document)
////                val start = document.getLineStartOffset(parentLine);
//        val firstChildOffset: Int =
//            if (element.firstChild.startOffset == parentStartOffset && element.children.size > 1) {
//                element.firstChild.nextSibling.endOffset
//            } else {
//                element.firstChild.endOffset
//            }
//        return Pair(TextRange(parentStartOffset, firstChildOffset), document.getLineNumber(parentStartOffset))
////        val realText = document.getText(textRange)
//    }
//
//    override fun dispose() {
////        editor.scrollingModel.removeVisibleAreaListener(this)
//    }
//}