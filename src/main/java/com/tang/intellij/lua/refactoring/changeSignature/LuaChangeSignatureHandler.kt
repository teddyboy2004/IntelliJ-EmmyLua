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

package com.tang.intellij.lua.refactoring.changeSignature

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.refactoring.ui.VisibilityPanelBase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.treeStructure.Tree
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.Consumer
import com.intellij.util.ui.ColumnInfo
import com.tang.intellij.lua.lang.LuaFileType
import com.tang.intellij.lua.lang.LuaLanguage
import com.tang.intellij.lua.psi.LuaClassMethod
import com.tang.intellij.lua.psi.LuaClassMethodDef
import com.tang.intellij.lua.psi.LuaClassMethodName
import com.tang.intellij.lua.psi.LuaParamInfo
import com.tang.intellij.lua.psi.LuaPsiFile
import org.jetbrains.annotations.Nls

/**
 *
 * Created by tangzx on 2017/4/25.
 */
class LuaChangeSignatureHandler : ChangeSignatureHandler {
    override fun findTargetMember(element: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(element, LuaClassMethodDef::class.java)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement?>, dataContext: DataContext?) {
        val method = elements.firstOrNull() as? LuaClassMethodDef ?: return
        invokeOnMethod(project, method)
    }

    override fun getTargetNotFoundMessage(): @NlsContexts.DialogMessage String? {
        return "找不到目标函数"
    }

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
        dataContext: DataContext?
    ) {
        if (editor == null || file == null) return

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        val method = PsiTreeUtil.getParentOfType(element, LuaClassMethodDef::class.java) ?: return

        invokeOnMethod(project, method)
    }

    private fun invokeOnMethod(project: Project, method: LuaClassMethodDef) {
        // 创建并显示修改函数签名的对话框
        val changeSignatureDialog = LuaChangeSignatureDialog(project, LuaMethodDescriptor(method))

        changeSignatureDialog.show()
    }

    class LuaParameterInfo(val param: LuaParamInfo?, val index: Int) : ParameterInfo {
        var paramName = param?.name ?: ""
        var paramDefaultValue = ""
        override fun getName(): @NlsSafe String? {
            return paramName
        }

        override fun getOldIndex(): Int {
            return index
        }

        override fun getDefaultValue(): @NlsSafe String? {
            return paramDefaultValue
        }

        override fun setName(name: @NlsSafe String?) {
            paramName = name!!
        }

        override fun getTypeText(): @NlsSafe String? {
            return paramName
        }

        override fun isUseAnySingleVariable(): Boolean {
            return true
        }

        override fun setUseAnySingleVariable(b: Boolean) {
        }
    }

    class LuaMethodDescriptor(val method: LuaClassMethod) : MethodDescriptor<LuaParameterInfo, String> {
        override fun getName(): String? {
            return method.name
        }

        override fun getParameters(): List<LuaParameterInfo?> {
            val list = mutableListOf<LuaParameterInfo>()
            method.params.forEachIndexed { index, param ->
                list.add(LuaParameterInfo(param, index))
            }
            return list
        }

        override fun getParametersCount(): Int {
            return method.params.size
        }

        override fun getVisibility(): String {
            return ""
        }

        override fun getMethod(): PsiElement {
            return method
        }

        override fun canChangeVisibility(): Boolean {
            return false
        }

        override fun canChangeParameters(): Boolean {
            return true
        }

        override fun canChangeName(): Boolean {
            return true
        }

        override fun canChangeReturnType(): MethodDescriptor.ReadWriteOption {
            return MethodDescriptor.ReadWriteOption.None
        }
    }

    class LuaParameterTableModelItem(val parameter: LuaParameterInfo?, typeCodeFragment: PsiCodeFragment, defaultValueCodeFragment: PsiCodeFragment) :
        ParameterTableModelItemBase<LuaParameterInfo>(parameter, typeCodeFragment, defaultValueCodeFragment) {
        override fun isEllipsisType(): Boolean {
            return false
        }
    }

    class LuaPsiCodeFragment(project: Project, text: String, isPhysical: Boolean) : LuaPsiFile(
        PsiManagerEx.getInstanceEx(project).fileManager.createFileViewProvider
            (LightVirtualFile("temp.txt", LuaLanguage.INSTANCE, text), isPhysical)
    ), PsiCodeFragment {
        override fun forceResolveScope(scope: GlobalSearchScope?) {
        }

        override fun getForcedResolveScope(): GlobalSearchScope? {
            return null
        }
    }

    class LuaParamColumnInfo(name: @NlsContexts.ColumnName String) : ColumnInfo<LuaParameterTableModelItem, String>(name) {

        override fun valueOf(item: LuaParameterTableModelItem?): String {
            return when (name) {
                "参数名" -> item?.parameter?.paramName ?: ""
                "默认值" -> item?.parameter?.paramDefaultValue ?: ""
                else -> ""
            }
        }

        override fun setValue(item: LuaParameterTableModelItem?, value: String?) {
            when (name) {
                "参数名" -> item?.parameter?.paramName = value ?: ""
                "默认值" -> item?.parameter?.paramDefaultValue = value ?: ""
            }
        }
    }

    class LuaParameterTableModel(val method: LuaClassMethod) : ParameterTableModelBase<LuaParameterInfo, LuaParameterTableModelItem>(
        method, method,
        LuaParamColumnInfo("参数名"), LuaParamColumnInfo("默认值")
    ) {

        override fun createRowItem(parameterInfo: LuaParameterInfo?): LuaParameterTableModelItem? {
            var param = parameterInfo
            if (param == null) {
                param = LuaParameterInfo(null, -1)
                param.paramName = "param"
            }
            // 创建包含参数名称的代码片段，而不是空片段
            val project = method.project
            val typeFragment = LuaPsiCodeFragment(project, param.paramName, false)
            val defaultFragment = LuaPsiCodeFragment(project, "", false)
            return LuaParameterTableModelItem(param, typeFragment, defaultFragment)
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return true
        }
    }

    class LuaChangeSignatureProcessor(project: Project, val method: LuaClassMethod, val model: LuaParameterTableModel) : BaseRefactoringProcessor(project) {
        override fun createUsageViewDescriptor(usages: Array<out UsageInfo?>): UsageViewDescriptor {
            // 返回一个临时的数据
            return object : UsageViewDescriptor {
                override fun getElements(): Array<out PsiElement?> {
                    return arrayOf(method)
                }

                override fun getProcessedElementsHeader(): @NlsContexts.ListItem String? {
                    return ""
                }

                override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): @Nls String {
                    return ""
                }
            }
        }

        override fun findUsages(): Array<out UsageInfo?> {
            TODO("Not yet implemented")
        }

        override fun performRefactoring(usages: Array<out UsageInfo?>) {
            TODO("Not yet implemented")
        }

        override fun getCommandName(): @NlsContexts.Command String {
            return ""
        }
    }

    class LuaChangeSignatureDialog(project: Project, val method: LuaMethodDescriptor) : ChangeSignatureDialogBase<
            LuaParameterInfo,
            LuaClassMethodDef,
            String,
            LuaMethodDescriptor,
            LuaParameterTableModelItem, LuaParameterTableModel>(project, method, false, method.method) {
        override fun getFileType(): LanguageFileType? {
            return LuaFileType.INSTANCE
        }

        override fun createParametersInfoModel(method: LuaMethodDescriptor): LuaParameterTableModel {
            return LuaParameterTableModel(method.method)
        }

        override fun createRefactoringProcessor(): BaseRefactoringProcessor? {
            return LuaChangeSignatureProcessor(project, method.method, myParametersTableModel)
        }

        override fun createReturnTypeCodeFragment(): PsiCodeFragment? {
            return null
        }

        override fun createCallerChooser(
            title: @Nls String?,
            treeToReuse: Tree?,
            callback: Consumer<in MutableSet<LuaClassMethodDef>>?
        ): CallerChooserBase<LuaClassMethodDef?>? {
            return null
        }

        override fun validateAndCommitData(): @NlsContexts.DialogMessage String? {
            return null
        }

        override fun calculateSignature(): String? {
            if (method == null) {
                return ""
            }
            val classMethod = method.method
            val methodName = PsiTreeUtil.getChildOfType<LuaClassMethodName>(classMethod, LuaClassMethodName::class.java) ?: return ""
            var methodText = "function " + methodName?.text
            methodText += "("
            val rowCount = myParametersTableModel.rowCount
            for (i in 0 until rowCount) {
                val item = myParametersTableModel.getItem(i)
                if (item != null) {
                    val paramName = item.parameter?.name
                    methodText += "$paramName"
                }
                if (i < rowCount - 1) {
                    methodText += ", "
                }
            }
            methodText += ")"
            return methodText
        }

        override fun createVisibilityControl(): VisibilityPanelBase<String?>? {
            return ComboBoxVisibilityPanel(emptyArray<String>())
        }
    }

}

