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

package com.tang.intellij.lua.project;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FileContentUtil;
import com.tang.intellij.lua.lang.LuaLanguageLevel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by tangzx on 2017/6/12.
 */
public class LuaSettingsPanel implements SearchableConfigurable{
    private final LuaSettings settings;
    private JPanel myPanel;
    private JTextField constructorNames;
    private JCheckBox strictDoc;
    private JCheckBox smartCloseEnd;
    private JCheckBox showWordsInFile;
    private JCheckBox enforceTypeSafety;
    private JCheckBox nilStrict;
    private JCheckBox recognizeGlobalNameAsCheckBox;
    private LuaAdditionalSourcesRootPanel additionalRoots;
    private JCheckBox enableGenericCheckBox;
    private JCheckBox captureOutputDebugString;
    private JCheckBox captureStd;
    private JComboBox<String> defaultCharset;
    private JComboBox<LuaLanguageLevel> languageLevel;
    private JTextField requireFunctionNames;
    private JTextField tooLargerFileThreshold;
    private JTextField superFieldNames;
    private LuaCustomTypeConfigPanel typePanel;
    private LuaCustomParamConfigPanel paramPanel;
    private JComboBox<Integer> stickyLineComboBox;
    private JCheckBox enableSkipModuleNameCheckBox;
    private JTextField unknownTypeGuessRegexStr;
    private JCheckBox isOptimizeProcessClass;
    private JCheckBox ignoreMetedataValue;
    private JCheckBox ignoreFunctionValue;
    private JCheckBox showMoreValueItem;
    private JTextField skipFrameworkFiles;

    public LuaSettingsPanel() {
        this.settings = LuaSettings.Companion.getInstance();
        constructorNames.setText(settings.getConstructorNamesString());
        strictDoc.setSelected(settings.isStrictDoc());
        smartCloseEnd.setSelected(settings.isSmartCloseEnd());
        showWordsInFile.setSelected(settings.isShowWordsInFile());
        enforceTypeSafety.setSelected(settings.isEnforceTypeSafety());
        nilStrict.setSelected(settings.isNilStrict());
        recognizeGlobalNameAsCheckBox.setSelected(settings.isRecognizeGlobalNameAsType());
        additionalRoots.setRoots(settings.getAdditionalSourcesRoot());
        typePanel.setRoots(settings.getCustomTypeCfg());
        paramPanel.setRoots(settings.getCustomParamCfg());
        enableGenericCheckBox.setSelected(settings.getEnableGeneric());
        enableSkipModuleNameCheckBox.setSelected(settings.isSkipModuleName());
        isOptimizeProcessClass.setSelected(settings.isOptimizeClassProcess());
        requireFunctionNames.setText(settings.getRequireLikeFunctionNamesString());
        unknownTypeGuessRegexStr.setText(settings.getUnknownTypeGuessRegexStr());
        superFieldNames.setText(settings.getSuperFieldNamesString());
        skipFrameworkFiles.setText(settings.getSkipFrameworkFilesString());
        tooLargerFileThreshold.setDocument(new IntegerDocument());
        tooLargerFileThreshold.setText(String.valueOf(settings.getTooLargerFileThreshold()));


        captureStd.setSelected(settings.getAttachDebugCaptureStd());
        captureOutputDebugString.setSelected(settings.getAttachDebugCaptureOutput());

        ignoreFunctionValue.setSelected(settings.getDebuggerIgnoreFunction());
        ignoreMetedataValue.setSelected(settings.getDebuggerIgnoreMetadata());
        showMoreValueItem.setSelected(settings.getDebuggerShowMoreValue());

        SortedMap<String, Charset> charsetSortedMap = Charset.availableCharsets();
        ComboBoxModel<String> outputCharsetModel = new DefaultComboBoxModel<>(ArrayUtil.toStringArray(charsetSortedMap.keySet()));
        defaultCharset.setModel(outputCharsetModel);
        defaultCharset.setSelectedItem(settings.getAttachDebugDefaultCharsetName());

        //language level
        ComboBoxModel<LuaLanguageLevel> lanLevelModel = new DefaultComboBoxModel<>(LuaLanguageLevel.values());
        languageLevel.setModel(lanLevelModel);
        lanLevelModel.setSelectedItem(settings.getLanguageLevel());

        // sticky line
        Vector<Integer> stickyLineLevel = settings.getStickyLineLevel();
        stickyLineComboBox.setModel(new DefaultComboBoxModel<>(stickyLineLevel));
        stickyLineComboBox.setSelectedItem(settings.getStickyScrollMaxLevel());

        skipFrameworkFiles.setText(settings.getSkipFrameworkFilesString());
    }

    @NotNull
    @Override
    public String getId() {
        return "Lua";
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Lua";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return myPanel;
    }

    @Override
    public boolean isModified() {
        return !StringUtil.equals(settings.getConstructorNamesString(), constructorNames.getText()) ||
                !StringUtil.equals(settings.getRequireLikeFunctionNamesString(), requireFunctionNames.getText()) ||
                !StringUtil.equals(settings.getUnknownTypeGuessRegexStr(), unknownTypeGuessRegexStr.getText()) ||
                !StringUtil.equals(settings.getSuperFieldNamesString(), superFieldNames.getText()) ||
                !StringUtil.equals(settings.getSkipFrameworkFilesString(), skipFrameworkFiles.getText()) ||
                settings.getTooLargerFileThreshold() != getTooLargerFileThreshold() ||
                settings.isStrictDoc() != strictDoc.isSelected() ||
                settings.isSmartCloseEnd() != smartCloseEnd.isSelected() ||
                settings.isShowWordsInFile() != showWordsInFile.isSelected() ||
                settings.isEnforceTypeSafety() != enforceTypeSafety.isSelected() ||
                settings.isNilStrict() != nilStrict.isSelected() ||
                settings.isRecognizeGlobalNameAsType() != recognizeGlobalNameAsCheckBox.isSelected() ||
                settings.getEnableGeneric() != enableGenericCheckBox.isSelected() ||
                settings.isSkipModuleName() != enableSkipModuleNameCheckBox.isSelected() ||
                settings.isOptimizeClassProcess() != isOptimizeProcessClass.isSelected() ||
                settings.getAttachDebugCaptureOutput() != captureOutputDebugString.isSelected() ||
                settings.getDebuggerIgnoreFunction() != ignoreFunctionValue.isSelected() ||
                settings.getDebuggerIgnoreMetadata() != ignoreMetedataValue.isSelected() ||
                settings.getDebuggerShowMoreValue() != showMoreValueItem.isSelected() ||
                settings.getAttachDebugCaptureStd() != captureStd.isSelected() ||
                settings.getAttachDebugDefaultCharsetName() != defaultCharset.getSelectedItem() ||
                settings.getLanguageLevel() != languageLevel.getSelectedItem() ||
                !Objects.equals(stickyLineComboBox.getSelectedItem(), settings.getStickyScrollMaxLevel()) ||
                !Arrays.equals(settings.getAdditionalSourcesRoot(), additionalRoots.getRoots(), String::compareTo) ||
                !Arrays.equals(settings.getCustomTypeCfg(), typePanel.getRoots())||
                !Arrays.equals(settings.getCustomParamCfg(), paramPanel.getRoots());
    }

    @Override
    public void apply() {
        settings.setConstructorNamesString(constructorNames.getText());
        constructorNames.setText(settings.getConstructorNamesString());
        settings.setRequireLikeFunctionNamesString(requireFunctionNames.getText());
        requireFunctionNames.setText(settings.getRequireLikeFunctionNamesString());
        settings.setUnknownTypeGuessRegexStr(unknownTypeGuessRegexStr.getText());
        unknownTypeGuessRegexStr.setText(settings.getUnknownTypeGuessRegexStr());
        settings.setSuperFieldNamesString(superFieldNames.getText());
        settings.setSkipFrameworkFilesString(skipFrameworkFiles.getText());
        superFieldNames.setText(settings.getSuperFieldNamesString());
        settings.setTooLargerFileThreshold(getTooLargerFileThreshold());
        settings.setStrictDoc(strictDoc.isSelected());
        settings.setSmartCloseEnd(smartCloseEnd.isSelected());
        settings.setShowWordsInFile(showWordsInFile.isSelected());
        settings.setEnforceTypeSafety(enforceTypeSafety.isSelected());
        settings.setNilStrict(nilStrict.isSelected());
        settings.setRecognizeGlobalNameAsType(recognizeGlobalNameAsCheckBox.isSelected());
        settings.setAdditionalSourcesRoot(additionalRoots.getRoots());
        settings.setCustomTypeCfg(typePanel.getRoots());
        settings.setCustomParamCfg(paramPanel.getRoots());
        settings.setEnableGeneric(enableGenericCheckBox.isSelected());
        settings.setSkipModuleName(enableSkipModuleNameCheckBox.isSelected());
        settings.setOptimizeClassProcess(isOptimizeProcessClass.isSelected());
        settings.setAttachDebugCaptureOutput(captureOutputDebugString.isSelected());
        settings.setDebuggerIgnoreMetadata(ignoreMetedataValue.isSelected());
        settings.setDebuggerShowMoreValue(showMoreValueItem.isSelected());
        settings.setDebuggerIgnoreFunction(ignoreFunctionValue.isSelected());
        settings.setAttachDebugCaptureStd(captureStd.isSelected());
        settings.setAttachDebugDefaultCharsetName((String) Objects.requireNonNull(defaultCharset.getSelectedItem()));
        settings.setStickyScrollMaxLevel((Integer) stickyLineComboBox.getSelectedItem());
        LuaLanguageLevel selectedLevel = (LuaLanguageLevel) Objects.requireNonNull(languageLevel.getSelectedItem());
        if (selectedLevel != settings.getLanguageLevel()) {
            settings.setLanguageLevel(selectedLevel);
            StdLibraryProvider.Companion.reload();

            FileContentUtil.reparseOpenedFiles();
        } else {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                DaemonCodeAnalyzer.getInstance(project).restart();
            }
        }
    }

    private int getTooLargerFileThreshold() {
        int value;
        try {
            value = Integer.parseInt(tooLargerFileThreshold.getText());
        } catch (NumberFormatException e) {
            value = settings.getTooLargerFileThreshold();
        }
        return value;
    }

    static class IntegerDocument extends PlainDocument {
        public void insertString(int offset, String s, AttributeSet attributeSet) throws BadLocationException {
            try {
                Integer.parseInt(s);
            } catch (Exception ex) {
                return;
            }
            super.insertString(offset, s, attributeSet);
        }
    }
}