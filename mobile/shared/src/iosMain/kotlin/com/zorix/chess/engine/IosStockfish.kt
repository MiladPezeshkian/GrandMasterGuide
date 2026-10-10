package com.zorix.chess.engine

import com.zorix.chess.controller.EngineHost
import com.zorix.chess.controller.EngineLaunch
import com.zorix.chess.engine.uci.EngineConnection
import com.zorix.chess.engine.uci.EngineException
import com.zorix.stockfish.zorix_sf_alive
import com.zorix.stockfish.zorix_sf_read_line
import com.zorix.stockfish.zorix_sf_send
import com.zorix.stockfish.zorix_sf_start
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.Foundation.NSProcessInfo

/**
 * Stockfish linked into the app. iOS forbids starting other processes, so the engine runs on its own
 * thread and talks UCI through pipes (see iosApp/stockfish/zorix_stockfish.cpp). The network is embedded.
 */
@OptIn(ExperimentalForeignApi::class)
object IosStockfishConnection : EngineConnection {

    private val started: Boolean by lazy { zorix_sf_start() == 0 }

    override fun send(line: String) {
        if (!started || zorix_sf_send(line) != 0) throw EngineException("The engine is not running")
    }

    override fun readLine(): String? = memScoped {
        val buffer = allocArray<ByteVar>(BUFFER_SIZE)
        if (zorix_sf_read_line(buffer, BUFFER_SIZE) < 0) null else buffer.toKString()
    }

    override val isAlive: Boolean get() = started && zorix_sf_alive() != 0

    /** The engine thread lives as long as the app; closing only stops the current search. */
    override fun close() {
        runCatching { send("stop") }
    }

    private const val BUFFER_SIZE = 64 * 1024
}

class IosEngineHost : EngineHost {
    override val cpuCores: Int = NSProcessInfo.processInfo.activeProcessorCount.toInt().coerceAtLeast(1)

    override suspend fun prepare(onProgress: (Float) -> Unit): List<EngineLaunch> {
        onProgress(1f)
        return listOf(EngineLaunch("arm64 neon") { IosStockfishConnection })
    }
}
