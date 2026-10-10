package com.zorix.chess.web

import com.zorix.chess.controller.EngineHost
import com.zorix.chess.controller.EngineLaunch
import com.zorix.chess.controller.KeyValueStore
import com.zorix.chess.controller.Speech
import com.zorix.chess.controller.SpeechStatus
import com.zorix.chess.engine.uci.EngineConnection
import com.zorix.chess.engine.uci.EngineException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A Stockfish Web Worker, as created by the page: lines go in with [send], answers come back through a callback. */
external interface JsEngine {
    fun send(line: String)
    fun close()
}

/** The page's key-value storage (the player's saved progress). */
external interface JsStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String?)
}

/**
 * The page's voice. [speak] reports back through [ZorixCore.voiceStarted] and [ZorixCore.voiceDone]
 * with the same token, so the coach can show "Zorix is thinking…" until the voice starts.
 */
external interface JsVoice {
    fun supports(lang: String): Boolean
    fun speak(text: String, lang: String, token: Int)
    fun stop()
    fun prepare(lang: String)
}

/** One engine process: the Web Worker. */
internal class WorkerConnection(create: (onLine: (String?) -> Unit) -> JsEngine) : EngineConnection {
    private val lines = Channel<String>(Channel.UNLIMITED)
    private var alive = true
    private val worker: JsEngine = create { line ->
        if (line == null) {
            alive = false
            lines.close()
        } else {
            lines.trySend(line)
        }
    }

    override fun send(line: String) {
        if (!alive) throw EngineException("The engine is not running")
        worker.send(line)
    }

    override fun readLine(): String? = throw UnsupportedOperationException("The browser engine is read with nextLine()")

    override suspend fun nextLine(): String? = lines.receiveCatching().getOrNull()

    override val isAlive: Boolean get() = alive

    override fun close() {
        if (!alive) return
        alive = false
        runCatching { worker.close() }
        lines.close()
    }
}

internal class WebEngineHost(
    private val create: (onLine: (String?) -> Unit) -> JsEngine,
    override val cpuCores: Int,
) : EngineHost {
    override suspend fun prepare(onProgress: (Float) -> Unit): List<EngineLaunch> {
        onProgress(1f)
        return listOf(EngineLaunch("WebAssembly") { WorkerConnection(create) })
    }
}

internal class WebStore(private val storage: JsStorage) : KeyValueStore {
    override fun getString(key: String): String? = storage.getItem(key)
    override fun putString(key: String, value: String?) = storage.setItem(key, value)
}

internal class WebSpeech(private val voice: JsVoice) : Speech {
    private val _status = MutableStateFlow(SpeechStatus.IDLE)
    override val status: StateFlow<SpeechStatus> = _status.asStateFlow()
    private var token = 0

    override fun supports(lang: String): Boolean = voice.supports(lang)

    override fun speak(text: String, lang: String) {
        token++
        _status.value = SpeechStatus.PREPARING
        voice.speak(text, lang, token)
    }

    override fun stop() {
        token++
        _status.value = SpeechStatus.IDLE
        voice.stop()
    }

    override fun prepare(lang: String) = voice.prepare(lang)

    fun started(t: Int) {
        if (t == token && _status.value == SpeechStatus.PREPARING) _status.value = SpeechStatus.SPEAKING
    }

    fun done(t: Int) {
        if (t == token) _status.value = SpeechStatus.IDLE
    }
}
