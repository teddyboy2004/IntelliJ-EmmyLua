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

package com.tang.intellij.lua.editor.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.project.LuaCustomHandleType
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import javax.swing.Icon

class CustomTypeHandleCompleteProvider(var handleType: LuaCustomHandleType) : RequirePathCompletionProvider() {

    override fun addCompletions(session: CompletionSession) {
        var resultSet = session.resultSet
        val parameters = session.parameters
        val project = parameters.position.project
        when (handleType) {
            LuaCustomHandleType.ClassName -> {
                val prefix = resultSet.prefixMatcher.prefix
                if (prefix.startsWith("\"")) {
                    resultSet = resultSet.withPrefixMatcher(prefix.substring(1))
                }
                LuaShortNamesManager.getInstance(project).processClassNames(project) {
                    addElement(session, it, resultSet, LuaIcons.CLASS)
                    true
                }

                LuaShortNamesManager.getInstance(project).processAllAlias(project) { key ->
                    addElement(session, key, resultSet, LuaIcons.Alias)
                    true
                }
            }

            LuaCustomHandleType.Require -> {
                addPaths(parameters, resultSet)
            }

            else -> {}
        }
        resultSet.stopHere()
    }

    private fun addElement(session: CompletionSession, it: String, resultSet: CompletionResultSet, icon: Icon?) {
        session.addWord(it)
        val element = LookupElementBuilder.create(it).withIcon(icon).withInsertHandler(getInsertHandler())
        resultSet.addElement(element)
    }

    override fun getInsertHandler(): InsertHandler<LookupElement> {
        return InsertHandler()
        { context, item ->
            val startOffset = context.startOffset
            val element = context.file.findElementAt(startOffset)
            val editor = context.editor
            val targetStr = "\"${item.lookupString}\""
            if (element != null && element.text != targetStr) {
                editor.document.replaceString(startOffset - 1, startOffset + targetStr.length - 2, targetStr)
            }
        }
    }
}
