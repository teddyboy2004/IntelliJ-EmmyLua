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

package com.tang.intellij.lua.editor

import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.util.Processor

/**
 * Goto Class
 * Created by TangZX on 2016/12/12.
 */
class LuaSymbolSearchEverywhereContributor(event: AnActionEvent) : SymbolSearchEverywhereContributor(event) {

    val regex = Regex(":(\\w)")
    override fun filterControlSymbols(pattern: String): String {
        val replace = pattern.replace(regex, ".$1")
        return super.filterControlSymbols(replace)
    }

    override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in Any>) {
        val replace = pattern.replace(regex, ".$1")
        super.fetchElements(replace, progressIndicator, consumer)
    }

    override fun fetchWeightedElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in FoundItemDescriptor<Any>>) {
        val replace = pattern.replace(regex, ".$1")
        super.fetchWeightedElements(replace, progressIndicator, consumer)
    }

    override fun getSortWeight(): Int {
        return super.getSortWeight() - 100
    }

    class Factory : SearchEverywhereContributorFactory<Any> {
        override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any> {
            return PSIPresentationBgRendererWrapper.wrapIfNecessary(LuaSymbolSearchEverywhereContributor(initEvent))
        }
    }

    class LuaSymbolSearchEverywhereAction : SearchEverywhereBaseAction(), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return

            val dumb: Boolean = DumbService.isDumb(project)
            if (!dumb || LuaSymbolSearchEverywhereContributor(e).isDumbAware) {
                val tabID = LuaSymbolSearchEverywhereContributor::class.java.simpleName
                showInSearchEverywherePopup(tabID, e, true, true)
            }
        }
    }
}
