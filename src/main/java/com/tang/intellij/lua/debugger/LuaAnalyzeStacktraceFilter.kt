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

package com.tang.intellij.lua.debugger;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.tang.intellij.lua.editor.activity.LuaStartupActivity
import com.tang.intellij.lua.psi.LuaFileUtil

import java.util.regex.Pattern;

public class LuaAnalyzeStacktraceFilter() : Filter {
    val pattern1 = Pattern.compile("\\s*\\.(\\\\.*?\\.lua):(\\d+):") // .\xxx.lua:xxx:
    val pattern2 = Pattern.compile("\\s*(\\w+/.*?\\.lua):(\\d+):") // xxx/xxx.lua:xxx:
    var patterns = arrayOf(pattern1, pattern2)

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val project = LuaStartupActivity.curProject
        patterns.forEach {
            val matcher = it.matcher(line)
            if (matcher.find()) {
                val fileName = matcher.group(1)
                val lineNumber = Integer.parseInt(matcher.group(2))
                val file = LuaFileUtil.findFile(project, fileName)
                if (file != null) {
                    val hyperlink = OpenFileHyperlinkInfo(project, file, lineNumber - 1)
                    val textStartOffset = entireLength - line.length
                    val startPos = matcher.start(1)
                    val endPos = matcher.end(2) + 1
                    return Filter.Result(startPos + textStartOffset, endPos + textStartOffset, hyperlink)
                }
            }
        }
        return null
    }
}
