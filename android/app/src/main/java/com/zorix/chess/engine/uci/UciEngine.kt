package com.zorix.chess.engine.uci

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown when the engine process exits or stops answering. */
class EngineException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Talks to a UCI engine (Stockfish) running as a separate process over stdin/stdout.
 *
 * All requests are serialised: a new [search] waits until the previous one has fully
 * finished, and cancelling a search sends `stop` and drains its `bestmove`, so the
 * engine is always idle before the next command.
 */
class UciEngine(private val launch: () -> Process) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    private var lines = Channel<String>(Channel.UNLIMITED)
    private val errors = ArrayList<String>()
    private val appliedOptions = HashMap<String, String>()

    @Volatile var engineName: String = ""
        private set

    @Volatile private var searching = false

    val isAlive: Boolean get() = process?.isAlive == true

    /** Launches the process, performs the UCI handshake and applies [options]. */
    suspend fun start(options: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) { mutex.withLock { startLocked(options) } }

    private suspend fun startLocked(options: Map<String, String>) {
        if (isAlive) return
        val p = try {
            launch()
        } catch (e: IOException) {
            throw EngineException("Could not start the engine: ${e.message}", e)
        }
        process = p
        writer = p.outputStream.bufferedWriter()
        val channel = Channel<String>(Channel.UNLIMITED)
        lines = channel
        synchronized(errors) { errors.clear() }
        appliedOptions.clear()
        scope.launch {
            try {
                p.inputStream.bufferedReader().useLines { seq ->
                    seq.forEach { line ->
                        if (line.startsWith("info string ERROR")) {
                            synchronized(errors) { errors += line.removePrefix("info string ") }
                        }
                        channel.trySend(line)
                    }
                }
            } catch (_: IOException) {
                // Stream closed: the process is gone.
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
    suspend fun setOptions(options: Map<String, String>) = withContext(Dispatchers.IO) { mutex.withLock { setOptionsLocked(options) } }

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
    suspend fun newGame() = withContext(Dispatchers.IO) {
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
        onUpdate: (AnalysisSnapshot) -> Unit = {},
    ): AnalysisSnapshot = withContext(Dispatchers.IO) { mutex.withLock { searchLocked(positionArgs, limit, multiPv, onUpdate) } }

    private suspend fun searchLocked(
        positionArgs: String,
        limit: SearchLimit,
        multiPv: Int,
        onUpdate: (AnalysisSnapshot) -> Unit,
    ): AnalysisSnapshot {
        ensureAlive()
        val pvCount = multiPv.coerceIn(1, 5)
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
            // Give the process a moment to exit so the exit code is available.
            runInterruptible { process?.waitFor(500, TimeUnit.MILLISECONDS) }
            throw EngineException(deathMessage())
        }
    }

    private fun deathMessage(): String {
        val details = synchronized(errors) { errors.joinToString("\n") }
        val code = runCatching { process?.exitValue() }.getOrNull()
        return buildString {
            append("The engine stopped unexpectedly")
            if (code != null) append(" (exit code ").append(code).append(')')
            if (details.isNotEmpty()) append(":\n").append(details)
        }
    }

    private fun send(command: String) {
        val w = writer ?: throw EngineException("Engine is not running")
        try {
            synchronized(w) {
                w.write(command)
                w.write("\n")
                w.flush()
            }
        } catch (e: IOException) {
            throw EngineException(deathMessage(), e)
        }
    }

    private fun killProcess() {
        process?.let { p ->
            runCatching { p.outputStream.close() }
            p.destroy()
            if (!p.waitFor(300, TimeUnit.MILLISECONDS)) p.destroyForcibly()
        }
    }

    /** Sends `quit`; a background thread kills the process if it does not exit promptly. Never blocks. */
    override fun close() {
        val p = process ?: return
        runCatching { send("stop") }
        runCatching { send("quit") }
        process = null
        writer = null
        scope.cancel()
        Thread {
            runCatching {
                if (!p.waitFor(500, TimeUnit.MILLISECONDS)) {
                    p.destroy()
                    if (!p.waitFor(300, TimeUnit.MILLISECONDS)) p.destroyForcibly()
                }
            }
        }.apply { isDaemon = true; name = "stockfish-shutdown" }.start()
    }

    private class SearchCollector(private val multiPv: Int) {
        private val lines = sortedMapOf<Int, PvLine>()
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
            lines = lines.values.toList(),
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
        private const val HANDSHAKE_TIMEOUT_MS = 15_000L
        private const val LOAD_TIMEOUT_MS = 60_000L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
