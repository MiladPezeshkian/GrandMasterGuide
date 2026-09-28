package com.zorix.chess.engine.uci

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import com.zorix.chess.platform.ioDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile

/** Thrown when the engine stops or cannot be reached. */
class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A running UCI engine: a separate process on Android, an in-app thread on iOS.
 * Implementations must allow [send] from any thread.
 */
interface EngineConnection {
    /** Writes one command line; throws [EngineException] if the engine is gone. */
    fun send(line: String)

    /** Blocks until the next output line; null when the engine output ended. */
    fun readLine(): String?

    val isAlive: Boolean

    /** Exit code once the engine has ended (may wait briefly), otherwise null. */
    fun exitCode(): Int? = null

    /** Shuts the engine down without blocking the caller. */
    fun close()
}

/**
 * Talks to a UCI engine (Stockfish) over its text protocol.
 *
 * All requests are serialised: a new [search] waits until the previous one has fully
 * finished, and cancelling a search sends `stop` and drains its `bestmove`, so the
 * engine is always idle before the next command.
 */
class UciEngine(private val connect: () -> EngineConnection) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()

    @Volatile private var connection: EngineConnection? = null
    private var lines = Channel<String>(Channel.UNLIMITED)
    private val errorsLock = SynchronizedObject()
    private val errors = ArrayList<String>()
    private val appliedOptions = HashMap<String, String>()

    @Volatile var engineName: String = ""
        private set

    @Volatile private var searching = false

    val isAlive: Boolean get() = connection?.isAlive == true

    /** Launches the process, performs the UCI handshake and applies [options]. */
    suspend fun start(options: Map<String, String> = emptyMap()) = withContext(ioDispatcher) { mutex.withLock { startLocked(options) } }

    private suspend fun startLocked(options: Map<String, String>) {
        if (isAlive) return
        val c = try {
            connect()
        } catch (e: EngineException) {
            throw e
        } catch (e: Exception) {
            throw EngineException("Could not start the engine: ${e.message}", e)
        }
        connection = c
        val channel = Channel<String>(Channel.UNLIMITED)
        lines = channel
        synchronized(errorsLock) { errors.clear() }
        appliedOptions.clear()
        scope.launch {
            try {
                while (true) {
                    val line = c.readLine() ?: break
                    if (line.startsWith("info string ERROR")) {
                        synchronized(errorsLock) { errors += line.removePrefix("info string ") }
                    }
                    channel.trySend(line)
                }
            } catch (_: Exception) {
                // Output closed: the engine is gone.
            } finally {
                channel.close()
            }
        }
        send("uci")
        withTimeout(HANDSHAKE_TIMEOUT_MS) {
            while (true) {
                val line = receive()
                if (line.startsWith("id name ")) engineName = line.removePrefix("id name ").trim()
                if (line == "uciok") break
            }
        }
        for ((name, value) in options) setOptionLocked(name, value)
        syncLocked(LOAD_TIMEOUT_MS)
    }

    /** Changes engine options (Threads, Hash, ...). Safe to call while idle or between searches. */
    suspend fun setOptions(options: Map<String, String>) = withContext(ioDispatcher) { mutex.withLock { setOptionsLocked(options) } }

    private suspend fun setOptionsLocked(options: Map<String, String>) {
        ensureAlive()
        var changed = false
        for ((name, value) in options) {
            if (appliedOptions[name] != value) {
                setOptionLocked(name, value)
                changed = true
            }
        }
        if (changed) syncLocked(LOAD_TIMEOUT_MS)
    }

    /** Clears hash tables and search history. */
    suspend fun newGame() = withContext(ioDispatcher) {
        mutex.withLock {
            ensureAlive()
            send("ucinewgame")
            syncLocked(LOAD_TIMEOUT_MS)
        }
    }

    /**
     * Searches `position [positionArgs]` and returns the final result.
     * [onUpdate] receives a fresh snapshot every time a principal variation changes.
     * Cancelling the calling coroutine stops the engine; [stopSearch] finishes early but still returns the result.
     */
    suspend fun search(
        positionArgs: String,
        limit: SearchLimit,
        multiPv: Int = 1,
        options: Map<String, String> = FULL_STRENGTH,
        onUpdate: (AnalysisSnapshot) -> Unit = {},
    ): AnalysisSnapshot = withContext(ioDispatcher) { mutex.withLock { searchLocked(positionArgs, limit, multiPv, options, onUpdate) } }

    private suspend fun searchLocked(
        positionArgs: String,
        limit: SearchLimit,
        multiPv: Int,
        options: Map<String, String>,
        onUpdate: (AnalysisSnapshot) -> Unit,
    ): AnalysisSnapshot {
        ensureAlive()
        // Strength options travel with each search so callers with different needs cannot interfere.
        for ((name, value) in options) if (appliedOptions[name] != value) setOptionLocked(name, value)
        val pvCount = multiPv.coerceIn(1, MAX_MULTIPV)
        if (appliedOptions["MultiPV"] != pvCount.toString()) setOptionLocked("MultiPV", pvCount.toString())
        send("position $positionArgs")
        send("go ${limit.uci}")
        searching = true
        val collector = SearchCollector(pvCount)
        return try {
            var result: AnalysisSnapshot? = null
            while (result == null) {
                val line = receive()
                if (line.startsWith("bestmove")) {
                    result = collector.finish(line)
                } else if (line.startsWith("info") && collector.accept(line)) {
                    onUpdate(collector.snapshot())
                }
            }
            result
        } catch (e: CancellationException) {
            withContext(NonCancellable) { abortSearch() }
            throw e
        } finally {
            searching = false
        }
    }

    /** Asks a running search to finish now; [search] then returns the best move found so far. */
    fun stopSearch() {
        if (searching) runCatching { send("stop") }
    }

    private suspend fun abortSearch() {
        runCatching { send("stop") }
        val drained = withTimeoutOrNull(STOP_TIMEOUT_MS) {
            while (true) {
                val line = lines.receiveCatching().getOrNull() ?: break
                if (line.startsWith("bestmove")) break
            }
            true
        }
        if (drained == null) killProcess()
    }

    private fun setOptionLocked(name: String, value: String) {
        send("setoption name $name value $value")
        appliedOptions[name] = value
    }

    private suspend fun syncLocked(timeoutMs: Long) {
        send("isready")
        withTimeout(timeoutMs) {
            while (receive() != "readyok") Unit
        }
    }

    private fun ensureAlive() {
        if (!isAlive) throw EngineException(deathMessage())
    }

    private suspend fun receive(): String {
        val result = lines.receiveCatching()
        return result.getOrNull() ?: run {
            throw EngineException(deathMessage())
        }
    }

    private fun deathMessage(): String {
        val details = synchronized(errorsLock) { errors.joinToString("\n") }
        val code = runCatching { connection?.exitCode() }.getOrNull()
        return buildString {
            append("The engine stopped unexpectedly")
            if (code != null) append(" (exit code ").append(code).append(')')
            if (details.isNotEmpty()) append(":\n").append(details)
        }
    }

    private fun send(command: String) {
        val c = connection ?: throw EngineException("Engine is not running")
        try {
            c.send(command)
        } catch (e: Exception) {
            throw EngineException(deathMessage(), e)
        }
    }

    private fun killProcess() {
        connection?.let { runCatching { it.close() } }
    }

    /** Stops the engine; never blocks. */
    fun close() {
        val c = connection ?: return
        runCatching { c.send("stop") }
        connection = null
        scope.cancel()
        runCatching { c.close() }
    }

    private class SearchCollector(private val multiPv: Int) {
        private val lines = HashMap<Int, PvLine>()
        private var depth = 0
        private var nodes = 0L
        private var nps = 0L
        private var time = 0L

        /** Returns true when the visible analysis changed. */
        fun accept(text: String): Boolean {
            val info = UciParser.parseInfo(text) ?: return false
            info.nodes?.let { nodes = it }
            info.nps?.let { nps = it }
            info.timeMs?.let { time = it }
            val pv = info.pv
            val score = info.score
            if (pv.isNullOrEmpty() || score == null || info.depth == null) return false
            val index = info.multipv ?: 1
            if (index > multiPv) return false
            // Aspiration-window fail highs/lows are provisional; keep the last exact line when we have one.
            if (info.bound != null && lines.containsKey(index)) return false
            lines[index] = PvLine(
                multipv = index,
                depth = info.depth,
                selDepth = info.selDepth ?: info.depth,
                score = score,
                bound = info.bound,
                pv = pv,
                nodes = info.nodes ?: nodes,
                nps = info.nps ?: nps,
                timeMs = info.timeMs ?: time,
                wdl = info.wdl,
            )
            if (index == 1) depth = info.depth
            return true
        }

        fun snapshot(best: String? = null, ponder: String? = null, finished: Boolean = false) = AnalysisSnapshot(
            lines = lines.values.sortedBy { it.multipv },
            depth = depth,
            nodes = nodes,
            nps = nps,
            timeMs = time,
            bestMove = best,
            ponderMove = ponder,
            finished = finished,
        )

        fun finish(bestLine: String): AnalysisSnapshot {
            val (best, ponder) = UciParser.parseBestMove(bestLine)
            // Make sure the reported best move heads line 1 (it can differ after a "stop").
            val first = lines[1]
            if (best != null && first != null && first.pv.firstOrNull() != best) {
                lines[1] = first.copy(pv = listOf(best) + listOfNotNull(ponder))
            }
            return snapshot(best, ponder, finished = true)
        }
    }

    companion object {
        const val MAX_MULTIPV = 12

        /** Options for analysis at full strength (undoing any bot handicap). */
        val FULL_STRENGTH: Map<String, String> = mapOf("UCI_LimitStrength" to "false", "Skill Level" to "20")

        /** Options that make Stockfish play like a player of [elo] (1320..3190). */
        fun limitedStrength(elo: Int): Map<String, String> =
            mapOf("UCI_LimitStrength" to "true", "UCI_Elo" to elo.coerceIn(1320, 3190).toString(), "Skill Level" to "20")

        private const val HANDSHAKE_TIMEOUT_MS = 15_000L
        private const val LOAD_TIMEOUT_MS = 60_000L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
