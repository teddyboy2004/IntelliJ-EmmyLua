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

package com.tang.intellij.lua.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.editor.services.StickyPanelManager
import com.tang.intellij.lua.lang.LuaLanguageLevel
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.ty.*
import java.nio.charset.Charset
import java.util.*


// 返回类型
enum class LuaCustomReturnType {
    Type, // 单个类型
    TypeArray, // 类型数组
}

enum class LuaCustomHandleType(val bit: Int) {
    ClassName(1 shl 0), // 参数为类名
    Require(1 shl 1), // Require对应类
    RequireField(1 shl 2), // RequireField
}

// 基本参数配置
open class LuaBaseCustomConfig {
    var TypeName: String = ""
    var FunctionName: String = ""
    private var typeNameReg: Regex? = null
    private var funcNameReg: Regex? = null
    private var inited = false

    open fun match(luaCallExpr: LuaCallExpr, context: SearchContext): Boolean {
        if (!inited) {
            createRegex()
        }
        if (typeNameReg == null || funcNameReg == null) {
            return false
        }
        val expr: LuaExpr = luaCallExpr.getExpr()
        if (expr is LuaIndexExpr) {
            expr.getName()?.let { funcName ->
                if (!funcNameReg!!.matches(funcName)) {
                    return false
                }
                val typeNames = getTypeNamesByLuaIndexExpr(expr, context)
                typeNames.forEach {
                    val typeName = it
                    if (typeNameReg!!.matches(typeName)) {
                        return true
                    }
                }
            }
        } else if (expr is LuaNameExpr) {
            if (!funcNameReg!!.matches(expr.text)) {
                return false
            }
            if (typeNameReg!!.matches(Constants.WORD_G)) {
                return true
            }
        }
        return false
    }

    fun createRegex() {
        try {
            if (typeNameReg == null) {
                typeNameReg = Regex(TypeName, RegexOption.IGNORE_CASE)
            }
            if (funcNameReg == null) {
                funcNameReg = Regex(FunctionName, RegexOption.IGNORE_CASE)
            }
        } catch (e: Exception) {
        }
        inited = true
    }

    private fun getTypeNamesByLuaIndexExpr(expr: LuaIndexExpr, context: SearchContext): Collection<String> {
        val typeNames = mutableSetOf<String>()
        val parentType = expr.guessParentType(context)
        if (parentType is TyUnion) {
            parentType.getChildTypes().forEach {
                addTypeName(it, typeNames, context)
            }
        } else {
            addTypeName(parentType, typeNames, context)
        }
        return typeNames
    }

    private fun addTypeName(cls: ITy, typeNames: MutableSet<String>, context: SearchContext) {
        if (cls is TyTable) {
            val element = cls.displayName
            if (element != Constants.WORD_TABLE) {
                typeNames.add(element)
            }
        } else if (cls is ITyClass) {
            addTypeName(cls.className, typeNames)
            addTypeName(cls.varName, typeNames)
            val superClass = cls.getSuperClass(context)
            if (superClass is TyClass) {
                addTypeName(superClass, typeNames, context)
            }
        }

    }

    private fun addTypeName(className: String, typeNames: MutableSet<String>) {
        if (className.isNotBlank()) {
            if (className.startsWith('$')) {
                typeNames.add(className.substring(1))
            } else if (!className.contains('@')) {
                typeNames.add(className)
            }
        }
    }
}

// 自定义参数配置
class LuaCustomParamConfig : LuaBaseCustomConfig() {
    var ConvertFunctionName: String = ""

    var ParameterOffset: Int = 0


    override fun equals(other: Any?): Boolean {
        val cfg = other as LuaCustomParamConfig?
        if (cfg != null) {
            return cfg.TypeName == TypeName && cfg.FunctionName == FunctionName && cfg.ConvertFunctionName == ConvertFunctionName && cfg.ParameterOffset == ParameterOffset
        }
        return super.equals(other)
    }

    fun toArray(): Array<Any> {
        return arrayOf(TypeName, FunctionName, ConvertFunctionName, ParameterOffset)
    }

    companion object {
        val ColumnNames = arrayOf("Type", "FunctionName", "ConvertFunctionName", "ParameterOffset")

        fun createFromVector(vector: Vector<Any>?): LuaCustomParamConfig {
            val cfg = LuaCustomParamConfig()
            if (vector == null) {
                return cfg
            }
            try {
                var index = 0
                cfg.TypeName = vector[index++] as String
                cfg.FunctionName = vector[index++] as String
                cfg.ConvertFunctionName = vector[index++] as String
                cfg.ParameterOffset = vector[index++] as Int
            } catch (_: Exception) {
            }
            return cfg
        }
    }
}


// 自定义返回类型设置
class LuaCustomTypeConfig : LuaBaseCustomConfig() {
    var ReturnType: LuaCustomReturnType = LuaCustomReturnType.Type

    var ParamIndex: Int = 0
    var HandleType: LuaCustomHandleType = LuaCustomHandleType.ClassName
    var ExtraParam: String = ""
    var MatchBreak: Boolean = false

    private var firstValue: String? = null
    private var secondValue: String? = null


    override fun equals(other: Any?): Boolean {
        val cfg = other as LuaCustomTypeConfig?
        if (cfg != null) {
            return cfg.TypeName == TypeName && cfg.FunctionName == FunctionName && cfg.ParamIndex == ParamIndex && cfg.HandleType == HandleType && cfg.ExtraParam == ExtraParam && cfg.MatchBreak == MatchBreak && cfg.ReturnType == ReturnType
        }
        return super.equals(other)
    }

    fun toArray(): Array<Any> {
        return arrayOf(TypeName, FunctionName, ReturnType, ParamIndex, HandleType, ExtraParam, MatchBreak)
    }

    fun getClassName(typeName: String): String {
        if (HandleType != LuaCustomHandleType.ClassName) return typeName
        // 没处理就根据ExtraParam，初始化一下
        initExtraParam()

        if (!firstValue.isNullOrEmpty()) {
            return if (secondValue != null) {
                typeName.replace(firstValue!!, secondValue!!)
            } else {
                firstValue + typeName
            }
        }
        return typeName
    }

    fun getSrcName(string: String): String {
        if (HandleType != LuaCustomHandleType.ClassName) return string
        // 没处理就根据ExtraParam，初始化一下
        initExtraParam()

        if (!firstValue.isNullOrEmpty()) {
            return if (secondValue != null) {
                string.replace(secondValue!!, firstValue!!)
            } else {
                string.substring(firstValue!!.length)
            }
        }
        return string
    }

    private fun initExtraParam() {
        if (firstValue == null && ExtraParam.isNotBlank()) {
            if (ExtraParam.contains(',')) {
                val strings = ExtraParam.split(',')
                if (strings.size > 1) {
                    firstValue = strings[0]
                    secondValue = strings[1]
                }
            } else {
                firstValue = ExtraParam
            }
        }
    }

    companion object {
        val ColumnNames = arrayOf("Type", "FunctionName", "ReturnType", "ParamIndex", "HandleType", "ExtraParam", "Break")

        fun createFromVector(vector: Vector<Any>?): LuaCustomTypeConfig {
            val cfg = LuaCustomTypeConfig()
            if (vector == null) {
                return cfg
            }
            try {
                var index = 0
                cfg.TypeName = vector[index++] as String
                cfg.FunctionName = vector[index++] as String
                cfg.ReturnType = vector[index++] as LuaCustomReturnType
                cfg.ParamIndex = vector[index++] as Int
                cfg.HandleType = vector[index++] as LuaCustomHandleType
                cfg.ExtraParam = vector[index++] as String
                cfg.MatchBreak = vector[index++] as Boolean
            } catch (_: Exception) {
            }
            return cfg
        }
    }
}

/**
 *
 * Created by tangzx on 2017/6/12.
 */
@State(name = "LuaSettings", storages = [(Storage("emmy.xml"))])
class LuaSettings : PersistentStateComponent<LuaSettings> {
    //自定义require函数，参考constructorNames
    var requireLikeFunctionNames: Array<String> = arrayOf("require")

    var superFieldNames: Array<String> = arrayOf("__super")

    var constructorNames: Array<String> = arrayOf("new", "get")

    var unknownTypeGuessRegexStr = "(.*?)_"
        set(value) {
            val trim = value.trim()
            if (trim == field) {
                return
            }
            field = trim
            unknownTypeGuessRegex = null
        }
    private var unknownTypeGuessRegex: List<Regex>? = null

    //Doc文档严格模式，对不合法的注解报错
    var isStrictDoc: Boolean = false

    //在未匹配end的statement后回车会自动补全
    var isSmartCloseEnd: Boolean = true

    //在代码完成时使用参数完成模板
    var autoInsertParameters: Boolean = false

    var isShowWordsInFile: Boolean = true

    var isShowUnknownMethod: Boolean = true

    // Throw errors if specified and found types do not match
    var isEnforceTypeSafety: Boolean = false

    var isNilStrict: Boolean = false

    var isRecognizeGlobalNameAsType = true

    var additionalSourcesRoot = arrayOf<String>()

    var customTypeCfg = arrayOf<LuaCustomTypeConfig>()

    var customParamCfg = arrayOf<LuaCustomParamConfig>()

    var isSkipModuleName = true // 跳过moduleName，这样能避免一些卡死和报错

    var isOptimizeClassProcess = true // 优化处理Class和Alias


    /**
     * 使用泛型
     */
    var enableGeneric: Boolean = false

    /**
     * (KB)
     */
    var tooLargerFileThreshold = 1024

    var attachDebugDefaultCharsetName = "UTF-8"

    var attachDebugCaptureStd = true

    var attachDebugCaptureOutput = true

    var debuggerIgnoreMetadata = true

    var debuggerIgnoreFunction = false

    var debuggerShowMoreValue = false

    var debugerSkipFrameWorkFiles = arrayOf("class", "common/Promise")

    /**
     * Lua language level
     */
    var languageLevel = LuaLanguageLevel.LUA53
    val StickyLineLevel: Vector<Int> = Vector(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
    private var _stickyScrollMaxLevel = 5
    var stickyScrollMaxLevel
        get() = _stickyScrollMaxLevel
        set(value) {
            if (value != _stickyScrollMaxLevel) {
                _stickyScrollMaxLevel = value
                StickyPanelManager.instance?.recalculateAndRepaintLines(true)
            }
        }

    override fun getState(): LuaSettings {
        return this
    }

    override fun loadState(luaSettings: LuaSettings) {
        XmlSerializerUtil.copyBean(luaSettings, this)
    }

    var constructorNamesString: String
        get() {
            return constructorNames.joinToString(";")
        }
        set(value) {
            constructorNames = value.split(";").map { it.trim() }.toTypedArray()
        }

    val attachDebugDefaultCharset: Charset
        get() {
            return Charset.forName(attachDebugDefaultCharsetName) ?: Charset.forName("UTF-8")
        }

    var superFieldNamesString: String
        get() {
            return superFieldNames.joinToString(";")
        }
        set(value) {
            superFieldNames = value.split(";").map { it.trim() }.toTypedArray()
        }

    var requireLikeFunctionNamesString: String
        get() {
            return requireLikeFunctionNames.joinToString(";")
        }
        set(value) {
            requireLikeFunctionNames = value.split(";").map { it.trim() }.toTypedArray()
        }

    var skipFrameworkFilesString: String
        get() {
            return debugerSkipFrameWorkFiles.joinToString(";")
        }
        set(value) {
            debugerSkipFrameWorkFiles = value.split(";").map { it.trim() }.toTypedArray()
        }

    companion object {
        val ALL_LUA_CUSTOM_HANDLE_TYPE = LuaCustomHandleType.ClassName.bit or LuaCustomHandleType.RequireField.bit or LuaCustomHandleType.Require.bit

        val instance: LuaSettings
            get() = ApplicationManager.getApplication().getService(LuaSettings::class.java)

        fun isConstructorName(name: String): Boolean {
            return instance.constructorNames.contains(name)
        }

        fun isRequireLikeFunctionName(name: String): Boolean {
            return instance.requireLikeFunctionNames.contains(name) || name == Constants.WORD_REQUIRE
        }

        fun isSuperFieldName(name: String?): Boolean {
            if (name.isNullOrEmpty()) {
                return false
            }
            return instance.superFieldNames.contains(name)
        }

        fun getUnknownTypeName(callName: String?): String? {
            if (callName == null) {
                return null
            }
            if (instance.unknownTypeGuessRegex == null && instance.unknownTypeGuessRegexStr.isNotEmpty()) {
                val regexes = mutableListOf<Regex>()
                instance.unknownTypeGuessRegex = regexes
                instance.unknownTypeGuessRegexStr.split(";").reversed().forEach {
                    if (it.isBlank()) {
                        return@forEach
                    }
                    try {
                        regexes.add(it.trim().toRegex(RegexOption.IGNORE_CASE))
                    } catch (_: Exception) {
                    }
                }
            }
            instance.unknownTypeGuessRegex?.forEach {
                val matchResult = it.find(callName)
                if (matchResult != null && matchResult.groupValues.size > 1) {
                    return matchResult.groupValues[1]
                }
            }
            return callName
        }

        fun getCustomType(luaCallExpr: LuaCallExpr, context: SearchContext): ITy? {
            if (context.isDumb) {
                return null
            }
            if (instance.customTypeCfg.isEmpty()) {
                return null
            }
            return getCustomTypeInner(luaCallExpr, context)
        }

        private fun getCustomTypeInner(luaCallExpr: LuaCallExpr, context: SearchContext): ITy? {
            getCustomHandleType(luaCallExpr, -1, ALL_LUA_CUSTOM_HANDLE_TYPE)?.let { config: LuaCustomTypeConfig ->
                var ty: ITy? = null
                val typeStr: String? = getCustomHandleString(config, luaCallExpr, context)
                if (typeStr != null) {
                    when (config.HandleType) {
                        LuaCustomHandleType.ClassName -> {
                            val tagClass = LuaClassIndex.find(typeStr, context)
                            if (tagClass != null) {
                                ty = tagClass.type
                            }
                        }

                        LuaCustomHandleType.Require, LuaCustomHandleType.RequireField -> {
                            val luaFile = resolveRequireFile(typeStr, context.project)
                            if (luaFile != null) {
                                when (val element = luaFile.guessFileElement()) {
                                    is LuaDocTagClass -> ty = element.type
                                    is LuaTypeGuessable -> {
                                        ty = element.guessType(context)
                                    }
                                }
                            }
                        }
                    }
                }
                if (ty != null) {
                    return when (config.ReturnType) {
                        LuaCustomReturnType.Type -> ty
                        LuaCustomReturnType.TypeArray -> TyArray(ty)
                    }
                } else if (config.MatchBreak) {
                    return Ty.UNKNOWN
                }
            }
            return null
        }

        fun isCustomHandleType(luaCallExpr: LuaCallExpr, paramIndex: Int, handleType: LuaCustomHandleType): Boolean {
            return getCustomHandleType(luaCallExpr, paramIndex, handleType.bit) != null
        }

        fun getCustomHandleType(luaCallExpr: LuaCallExpr, paramIndex: Int, handleType: Int): LuaCustomTypeConfig? {
            val context = SearchContext.get(luaCallExpr.project)
            instance.customTypeCfg.forEach { config ->
                // 类型匹配，函数匹配
                if ((config.HandleType.bit and handleType) != 0 && (config.ParamIndex == paramIndex || paramIndex == -1) && config.match(luaCallExpr, context)) {
                    return config
                }
            }
            return null
        }

        fun getCustomParam(luaCallExpr: LuaCallExpr): LuaCustomParamConfig? {
            val context = SearchContext.get(luaCallExpr.project)
            instance.customParamCfg.forEach { config ->
                if (config.match(luaCallExpr, context)) {
                    return config
                }
            }
            return null
        }

        fun handleCustomParam(callExpr: LuaCallExpr, action: (cfg: LuaCustomParamConfig, member: LuaClassMember) -> Boolean) {
            val project = callExpr.project
            val context = SearchContext.get(project)
            getCustomParam(callExpr)?.let { config ->
                val guessType = callExpr.guessType(context)
                var find = false
                var handleFun = fun(ty: ITy) {
                    val fieldName = config.ConvertFunctionName
                    if (ty is TyClass) {
                        LuaClassMemberIndex.process(ty.className, fieldName, context, {
                            find = !action(config, it)
                            !find
                        }, false)
                    }
                    if (!find) {
                        ty.visitSuper(context) {
                            if (it != null) {
                                LuaClassMemberIndex.process(it.className, fieldName, context, {
                                    find = !action(config, it)
                                    !find
                                }, false)
                                !find
                            }
                            !find
                        }
                    }
                    return
                }
                TyUnion.each(guessType, handleFun)
            }
        }

        fun getCustomHandleString(cfg: LuaCustomTypeConfig, luaCallExpr: LuaCallExpr, context: SearchContext): String? {
            val paramIndex = cfg.ParamIndex
            val type = luaCallExpr.inferParam(paramIndex, context)
            var typeStr: String? = null
            if (type is TyPrimitiveLiteral && type.primitiveKind == TyPrimitiveKind.String) {
                typeStr = type.value
            }

            if (typeStr != null && cfg.HandleType == LuaCustomHandleType.ClassName) {
                typeStr = cfg.getClassName(typeStr)
            }
            // 支持RequireField需要在table中查找的情况
            else if (cfg.HandleType == LuaCustomHandleType.RequireField && cfg.ExtraParam.isNotBlank()) {
                type.each {
                    if (it is TyTable) {
                        it.findMember(cfg.ExtraParam, context)?.also { member ->
                            val guessType = member.guessType(context)
                            if (guessType is TyPrimitiveLiteral && guessType.primitiveKind == TyPrimitiveKind.String) {
                                typeStr = guessType.value
                                return@each
                            }
                        }
                    }
                }
            }

            return typeStr
        }

    }
}
