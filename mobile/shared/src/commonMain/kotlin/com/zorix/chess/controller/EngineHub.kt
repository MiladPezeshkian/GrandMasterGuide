package com.zorix.chess.controller

import com.zorix.chess.engine.uci.SearchLimit
import com.zorix.chess.engine.uci.UciEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the single Zorix engine process and shares it between the analysis board, play mode,
 * lessons and game review. Foreground work ([run]) always pre-empts background analysis
 * ([launchBackground]), so a bot move or a lesson check never waits behind infinite analysis.
 */
class EngineHub(
    private val scope: CoroutineScope,
    private val host: EngineHost,
    private val baseOptions: () -> Map<String, String>,
) {
    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Preparing(0f))
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private var engine: UciEngine? = null
    private val startLock = Mutex()
    private val lease = Mutex()
    private var background: Job? = null

    val cpuCores: Int get() = host.cpuCores

    /** Returns a running engine, (re)starting it if needed; null if it cannot run. */
    suspend fun ensure(): UciEngine? {
        engine?.takeIf { it.isAlive }?.let { return it }
        return startLock.withLock {
            engine?.takeIf { it.isAlive }?.let { return@withLock it }
            engine?.close()
            engine = null
            start()
        }
    }

    /** Runs [block] with exclusive use of the engine, stopping background analysis first. */
    suspend fun <T> run(block: suspend (UciEngine) -> T): T? {
        background?.cancelAndJoin()
        return lease.withLock {
            val e = ensure() ?: return@withLock null
            try {
                block(e)
            } catch (c: CancellationException) {
                throw c
            } catch (ex: Exception) {
                reportError(ex)
                null
            }
        }
    }

    /** Starts pre-emptible background work (live analysis). Replaces any previous background job. */
    fun launchBackground(block: suspend (UciEngine) -> Unit): Job {
        background?.cancel()
        val job = scope.launch {
            lease.withLock {
                val e = ensure() ?: return@withLock
                try {
                    block(e)
                } catch (c: CancellationException) {
                    throw c
                } catch (ex: Exception) {
                    reportError(ex)
                }
            }
        }
        background = job
        return job
    }

    fun cancelBackground() {
        background?.cancel()
    }

    suspend fun cancelBackgroundAndJoin() {
        background?.cancelAndJoin()
    }

    /** Applies Threads / Hash changes. */
    suspend fun applyOptions() {
        run { it.setOptions(baseOptions()) }
    }

    suspend fun newGame() {
        run { it.newGame() }
    }

    fun stopSearch() {
        engine?.stopSearch()
    }

    fun close() {
        background?.cancel()
        engine?.close()
        engine = null
    }

    fun reportError(e: Exception) {
        if (engine?.isAlive != true) _status.value = EngineStatus.Failed(e.message ?: e.toString())
    }

    private suspend fun start(): UciEngine? {
        _status.value = EngineStatus.Preparing(0f)
        val launches = try {
            host.prepare { p -> _status.value = EngineStatus.Preparing(p.coerceIn(0f, 1f)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = EngineStatus.Failed(e.message ?: e.toString())
            return null
        }
        _status.value = EngineStatus.Starting
        var lastError = "No engine binary found"
        for (launch in launches) {
            val candidate = UciEngine(launch.connect)
            try {
                candidate.start(baseOptions())
                // A tiny search proves the network loaded and the CPU supports this build.
                candidate.search("startpos", SearchLimit.Depth(1))
                engine = candidate
                _status.value = EngineStatus.Ready(candidate.engineName, launch.label)
                return candidate
            } catch (e: CancellationException) {
                candidate.close()
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: e.toString()
                candidate.close()
            }
        }
        _status.value = EngineStatus.Failed(lastError)
        return null
    }
}
