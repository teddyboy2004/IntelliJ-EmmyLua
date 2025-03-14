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

package com.tang.intellij.lua.debugger.remote

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.tang.intellij.lua.debugger.IRemoteConfiguration
import com.tang.intellij.lua.debugger.LogConsoleType
import com.tang.intellij.lua.debugger.LuaDebugProcess
import com.tang.intellij.lua.debugger.LuaDebuggerEditorsProvider
import com.tang.intellij.lua.debugger.remote.commands.DebugCommand
import com.tang.intellij.lua.debugger.remote.commands.GetStackCommand
import com.tang.intellij.lua.psi.LuaFileUtil
import java.net.BindException

/**

 * Created by TangZX on 2016/12/30.
 */
open class LuaMobDebugProcess(session: XDebugSession) : LuaDebugProcess(session), MobServerListener {

    private val runProfile: IRemoteConfiguration = session.runProfile as IRemoteConfiguration
    private var mobServer: MobServer? = null
    private var mobClient: MobClient? = null
    private var baseDir: String? = null

    override fun sessionInitialized() {
        super.sessionInitialized()
        try {
            mobServer = MobServer(this)
            mobServer?.start(runProfile.port)
            println("Start mobdebug server at port:${runProfile.port}", LogConsoleType.NORMAL, ConsoleViewContentType.SYSTEM_OUTPUT)
            println("Waiting for process connection...", LogConsoleType.NORMAL, ConsoleViewContentType.SYSTEM_OUTPUT)
        } catch (e:BindException) {
            error("Failed start mobdebug server at port:${runProfile.port}\n${e.message}")
        } catch (e: Exception) {
            e.message?.let { error(it) }
        }
    }

    override fun stop() {
        mobServer?.stop()
    }

    override fun run() {
        mobClient?.addCommand("RUN")
    }

    override fun startPausing() {
        mobClient?.addCommand("SUSPEND")
    }

    override fun startStepOver(context: XSuspendContext?) {
        mobClient?.addCommand("OVER")
    }

    override fun startStepInto(context: XSuspendContext?) {
        mobClient?.addCommand("STEP")
    }

    override fun startStepOut(context: XSuspendContext?) {
        mobClient?.addCommand("OUT")
    }

    private fun sendBreakpoint(sourcePosition: XSourcePosition) {
        val file = sourcePosition.file
        val fileShortUrl: String? = getShortPath(file)
        if (fileShortUrl != null) {
            LuaFileUtil.getAllAvailablePathsForMob(fileShortUrl, file).forEach{ url ->
                mobClient?.sendAddBreakpoint(url, sourcePosition.line + 1)
            }
        }
    }

    override fun registerBreakpoint(sourcePosition: XSourcePosition, breakpoint: XLineBreakpoint<*>) {
        super.registerBreakpoint(sourcePosition, breakpoint)
        sendBreakpoint(sourcePosition)
    }

    override fun unregisterBreakpoint(sourcePosition: XSourcePosition, breakpoint: XLineBreakpoint<*>) {
        super.unregisterBreakpoint(sourcePosition, breakpoint)
        val file = sourcePosition.file
        val fileShortUrl = getShortPath(file)
        LuaFileUtil.getAllAvailablePathsForMob(fileShortUrl, file).forEach{ url ->
            mobClient?.sendRemoveBreakpoint(url, sourcePosition.line + 1)
        }
    }

    override fun handleResp(client: MobClient, code: Int, data: String?) {
        when (code) {
            202 -> runCommand(GetStackCommand())
        }
    }

    override fun onDisconnect(client: MobClient) {
        mobServer?.restart()
        mobClient = null
        baseDir = null
    }

    override fun onConnect(client: MobClient) {
        mobClient = client
        client.addCommand("DELB * 0")
        client.addCommand((GetStackCommand()))
        sendAllBreakpoints()
    }

    fun findSourcePosition(chunkName: String, line: Int): XSourcePosition? {
        var position: XSourcePositionImpl? = null
        val virtualFile = LuaFileUtil.findFile(session.project, chunkName)
        if (virtualFile != null) {
            recognizeBaseDir(virtualFile, chunkName)
            position = XSourcePositionImpl.create(virtualFile, line - 1)
        }
        return position
    }

    private fun recognizeBaseDir(file: VirtualFile, chunkName: String) {
        val pathParts = file.canonicalPath?.split(Regex("[\\/]"))?.reversed() ?: return
        val chunkParts = chunkName.split(Regex("[\\/]")).reversed()
        if (pathParts.size < chunkParts.size)
            return
        var neq = 1
        for (i in 1 until chunkParts.size) {
            val chunkPart = chunkParts[i]
            val pathPart = pathParts[i]
            if (chunkPart.lowercase() != pathPart.lowercase()) {
                break
            }
            neq = i
        }
        val dir = pathParts.takeLast(pathParts.size - neq).reversed().joinToString("/")
        val myBaseDir = this.baseDir
        if (myBaseDir == null || myBaseDir.length > dir.length) {
            this.baseDir = dir
            print("Base dir: $dir\n", LogConsoleType.EMMY, ConsoleViewContentType.SYSTEM_OUTPUT)
            sendAllBreakpoints()
        }
    }

    private fun sendAllBreakpoints() {
        mobClient?.addCommand("DELB * 0")
        processBreakpoint(Processor { bp ->
            bp.sourcePosition?.let { sendBreakpoint(it) }
            true
        })
    }

    private fun getShortPath(file: VirtualFile): String {
        val myBaseDir = this.baseDir
        val path = file.canonicalPath
        if (myBaseDir != null && path != null && path.lowercase().startsWith(myBaseDir.lowercase())) {
            return path.substring(myBaseDir.length + 1)
        }
        return LuaFileUtil.getShortPath(session.project, file)
    }

    override val process: LuaMobDebugProcess
        get() = this

    fun runCommand(command: DebugCommand) {
        mobClient?.addCommand(command)
    }
}
