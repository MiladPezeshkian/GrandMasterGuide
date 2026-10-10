package com.zorix.chess.engine

import com.zorix.chess.engine.uci.EngineConnection
import com.zorix.chess.engine.uci.EngineException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Stockfish running as a separate process (Android, JVM tests). */
class ProcessConnection(command: List<String>, workingDir: File) : EngineConnection {

    private val process: Process = try {
        ProcessBuilder(command).directory(workingDir).redirectErrorStream(true).start()
    } catch (e: IOException) {
        throw EngineException("Could not start the engine: ${e.message}", e)
    }
    private val writer = process.outputStream.bufferedWriter()
    private val reader = process.inputStream.bufferedReader()

    override fun send(line: String) {
        try {
            synchronized(writer) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        } catch (e: IOException) {
            throw EngineException("The engine is not running", e)
        }
    }

    override fun readLine(): String? = try {
        reader.readLine()
    } catch (_: IOException) {
        null
    }

    override val isAlive: Boolean get() = process.isAlive

    override fun exitCode(): Int? =
        if (process.waitFor(500, TimeUnit.MILLISECONDS)) process.exitValue() else null

    override fun close() {
        runCatching { send("quit") }
        Thread {
            runCatching {
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroy()
                    if (!process.waitFor(300, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                }
            }
        }.apply {
            isDaemon = true
            name = "stockfish-shutdown"
        }.start()
    }
}
