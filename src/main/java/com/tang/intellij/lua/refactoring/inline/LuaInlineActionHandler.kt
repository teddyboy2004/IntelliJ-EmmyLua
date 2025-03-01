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

package com.tang.intellij.lua.refactoring.inline

import com.intellij.find.FindBundle
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.tang.intellij.lua.lang.LuaLanguage
import com.tang.intellij.lua.psi.LuaLocalDef
import com.tang.intellij.lua.psi.LuaNameDef

class LuaInlineActionHandler : InlineActionHandler() {
    override fun inlineElement(project: Project, editor: Editor, psiElement: PsiElement) {
        if (psiElement is LuaNameDef) {
            val text = psiElement.text
            val stat = psiElement.parent.parent as LuaLocalDef
            val allRefs = ProgressManager.getInstance().runProcessWithProgressSynchronously<Collection<PsiElement>, RuntimeException>(
                {
                    ReferencesSearch.search(psiElement).mapping { obj: PsiReference -> obj.element }.findAll()
                },
                FindBundle.message("find.usages.progress.title"), true, project
            ) ?: return
            if (allRefs.isEmpty()) {
                ApplicationManager.getApplication().invokeLater({
                    val message = RefactoringBundle.message("variable.is.never.used", text)
                    CommonRefactoringUtil.showErrorHint(project, editor, message, "Inline Variable", "refactoring.inlineVariable")
                }, ModalityState.NON_MODAL)
                return
            }
            if (stat.exprList == null) {
                return
            }

            CommandProcessor.getInstance().executeCommand(
                project, {
                    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside {
                        WriteCommandAction.runWriteCommandAction(project) {
                            allRefs.forEach { it.replace(stat.exprList!!) }
                            stat.delete()
                        }
                    }
                },
                RefactoringBundle.message("inline.command", text), null
            )
        }
    }

    override fun isEnabledForLanguage(language: Language): Boolean {
        return language == LuaLanguage.INSTANCE
    }

    override fun canInlineElement(element: PsiElement): Boolean {
        var accept = false
        if (element is LuaNameDef && element.parent.parent is LuaLocalDef && (element.parent.parent as LuaLocalDef).exprList?.text != null) {
            accept = true
        }
        return accept
    }
}