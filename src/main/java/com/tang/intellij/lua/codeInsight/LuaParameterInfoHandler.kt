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

package com.tang.intellij.lua.codeInsight

import com.intellij.lang.parameterInfo.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

data class ParameterInfoType(val sig: IFunSignature, val isColonStyle: Boolean, val paramOffset: Int, val extraSig: IFunSignature?)

/**
 *
 * Created by tangzx on 2016/12/25.
 */
class LuaParameterInfoHandler : ParameterInfoHandler<LuaArgs, ParameterInfoType> {
    var start: Int = 0
    var end: Int = 0

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): LuaArgs? {
        val file = context.file
        val luaArgs = PsiTreeUtil.findElementOfClassAtOffset(file, context.offset, LuaArgs::class.java, false)
        if (luaArgs != null) {
            val callExpr = luaArgs.parent as LuaCallExpr
            val isColonStyle = callExpr.isMethodColonCall
            val searchContext = SearchContext.get(context.project)
            val type = callExpr.guessParentType(searchContext)
            val list = mutableListOf<ParameterInfoType>()
            var paramOffset = -1
            var extraSig: IFunSignature? = null
            LuaSettings.handleCustomParam(callExpr) { cfg, member ->
                val memberType = member.guessType(searchContext)
                if (memberType is ITyFunction) {
                    extraSig = memberType.mainSignature
                    paramOffset = cfg.ParameterOffset
                    return@handleCustomParam false
                }
                return@handleCustomParam true
            }
            TyUnion.each(type) { ty ->
                if (ty is ITyFunction) {
                    ty.process(Processor { it ->
                        if ((it.colonCall && !isColonStyle) || it.params.isNotEmpty()) {
                            list.add(ParameterInfoType(it, isColonStyle, paramOffset, extraSig))
                        }
                        true
                    })
                }
            }
            context.itemsToShow = list.toTypedArray()
        }
        return luaArgs
    }

    override fun showParameterInfo(args: LuaArgs, context: CreateParameterInfoContext) {
        context.showHint(args, args.textRange.startOffset + 1, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): LuaArgs? {
        val file = context.file
        return PsiTreeUtil.findElementOfClassAtOffset(file, context.offset, LuaArgs::class.java, false)
    }

    override fun updateParameterInfo(args: LuaArgs, context: UpdateParameterInfoContext) {
        if (args is LuaListArgs) {
            val index = ParameterInfoUtils.getCurrentParameterIndex(args.node, context.offset, LuaTypes.COMMA)
            context.setCurrentParameter(index)
        }
    }

    override fun updateUI(o: ParameterInfoType?, context: ParameterInfoUIContext) {
        if (o == null) return

        val index = context.currentParameterIndex
        val str = buildString {
            var paramSize = 0
            o.sig.processArgs(null, o.isColonStyle) { idx, pi ->
                paramSize = addParamStr(paramSize, index, pi, idx)
                true
            }
            if (o.paramOffset >= 0 && o.extraSig != null) {
                o.extraSig.processArgs(null, true) { idx, pi ->
                    if (idx >= o.paramOffset) {
                        paramSize = addParamStr(paramSize, index, pi, idx)
                    }
                    true
                }
            }
        }
        if (str.isNotEmpty()) {
            context.setupUIComponentPresentation(
                str, start, end, false, false, false, context.defaultParameterColor
            )
        }
    }

    private fun StringBuilder.addParamStr(paramSize: Int, index: Int, pi: LuaParamInfo, idx: Int): Int {
        if (paramSize > 0) append(", ")
        if (paramSize == index) start = length
        append(pi.name)
        append(":")
        append(pi.ty.displayName)
        if (paramSize == index) end = length
        return paramSize + 1
    }
}
