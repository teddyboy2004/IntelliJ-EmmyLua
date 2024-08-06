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
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.editor.services.StickyPanelManager
import com.tang.intellij.lua.lang.LuaLanguageLevel
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.*
import java.nio.charset.Charset
import java.util.*


// 返回类型
enum class LuaCustomReturnType {
    Type, // 单个类型
    TypeArray, // 类型数组
}

enum class LuaCustomHandleType {
    ClassName, // 参数为类名
    Require, // Require对应类
    RequireField, // RequireField
}

// 自定义返回类型设置
class LuaCustomTypeConfig {
    var TypeName: String = ""
    var FunctionName: String = ""
    var ReturnType: LuaCustomReturnType = LuaCustomReturnType.Type

    var ParamIndex: Int = 0
    var HandleType: LuaCustomHandleType = LuaCustomHandleType.ClassName
    var ExtraParam: String = ""
    var MatchBreak: Boolean = false

    private var typeNameReg: Regex? = null;
    private var funcNameReg: Regex? = null;

    fun match(typename: String, funcName: String): Boolean {
        if (TypeName.isEmpty() || FunctionName.isEmpty()) {
            return false
        }
        try {
            if (typeNameReg == null) {
                typeNameReg = Regex(TypeName, RegexOption.IGNORE_CASE)
            }
            if (funcNameReg == null) {
                funcNameReg = Regex(FunctionName, RegexOption.IGNORE_CASE)
            }
        } catch (e: Exception) {
            return false
        }
        return typeNameReg!!.matches(typename) && funcNameReg!!.matches(funcName)
    }

    override fun equals(other: Any?): Boolean {
        val cfg = other as LuaCustomTypeConfig?
        if (cfg != null) {
            return cfg.TypeName.equals(TypeName) &&
                    cfg.FunctionName.equals(FunctionName) &&
                    cfg.ParamIndex.equals(ParamIndex) &&
                    cfg.HandleType.equals(HandleType) &&
                    cfg.ExtraParam.equals(ExtraParam) &&
                    cfg.MatchBreak.equals(MatchBreak) &&
                    cfg.ReturnType.equals(ReturnType)
        }
        return super.equals(other)
    }

    fun toArray(): Array<Any> {
        return arrayOf(TypeName, FunctionName, ReturnType, ParamIndex, HandleType, ExtraParam, MatchBreak)
    }

    companion object {
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

    var isSkipModuleName = true

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

    /**
     * Lua language level
     */
    var languageLevel = LuaLanguageLevel.LUA53
    val StickyLineLevel: Vector<Int> = Vector(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
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

    companion object {

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
            val expr: LuaExpr = luaCallExpr.getExpr()
            // a.b()
            if (expr is LuaIndexExpr) {
                val typeNames = getTypeNamesByLuaIndexExpr(expr)
                expr.getName()?.let { funcName ->
                    typeNames.forEach {
                        val typeName = it
                        return getCustomType(typeName, funcName, luaCallExpr, context)
                    }
                }
            } else if (expr is LuaNameExpr) {
                return getCustomType(Constants.WORD_G, expr.text, luaCallExpr, context)
            }
            return null
        }

        private fun getCustomType(
            typeName: String,
            funcName: @NlsSafe String,
            luaCallExpr: LuaCallExpr,
            context: SearchContext
        ): ITy? {
            instance.customTypeCfg.forEach { config ->
                // 类型匹配，函数匹配
                if (config.match(typeName, funcName)) {
                    var ty: ITy? = null
                    val typeStr: String? = getTypeStr(config, luaCallExpr, context)
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
                                    val child = PsiTreeUtil.findChildOfType(luaFile, LuaDocTagClass::class.java)
                                    if (child?.type != null) {
                                        ty = child.type
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
            }
            return null
        }

        fun isCustomHandleType(luaCallExpr: LuaCallExpr, paramIndex: Int, handleType: LuaCustomHandleType): Boolean {
            val expr: LuaExpr = luaCallExpr.getExpr()
            if (expr is LuaIndexExpr) {
                val typeNames = getTypeNamesByLuaIndexExpr(expr)
                expr.getName()?.let { funcName ->
                    typeNames.forEach { typeName: String ->
                        instance.customTypeCfg.forEach {
                            if (it.HandleType == handleType && it.ParamIndex == paramIndex && it.match(typeName, funcName)) {
                                return true
                            }
                        }
                    }
                }

            }
            return false
        }

        private fun getTypeNamesByLuaIndexExpr(expr: LuaIndexExpr): Collection<String> {
            val typeNames = mutableSetOf<String>()
            val context = SearchContext.get(expr.project)
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
                typeNames.add(cls.displayName)
            } else if (cls is ITyClass) {
                val className = cls.className
                if (className.isNotBlank()) {
                    if (className.startsWith('$')) {
                        typeNames.add(className.substring(1))
                    } else if (!className.contains('@')) {
                        typeNames.add(className)
                    }
                }
                val superClass = cls.getSuperClass(context)
                if (superClass is TyClass) {
                    addTypeName(superClass, typeNames, context)
                }
            }

        }

        private fun getTypeStr(cfg: LuaCustomTypeConfig, luaCallExpr: LuaCallExpr, context: SearchContext): String? {
            val paramIndex = cfg.ParamIndex
            val type = luaCallExpr.inferParam(paramIndex, context)
            var typeStr: String? = null
            if (type is TyPrimitiveLiteral && type.primitiveKind == TyPrimitiveKind.String) {
                typeStr = type.value
            }

            // 支持RequireField需要在table中查找的情况
            if (cfg.HandleType == LuaCustomHandleType.RequireField && cfg.ExtraParam.isNotBlank()) {
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
