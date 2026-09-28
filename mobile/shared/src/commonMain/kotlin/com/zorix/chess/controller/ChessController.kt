package com.zorix.chess.controller

import com.zorix.chess.core.Game
import com.zorix.chess.core.Move
import com.zorix.chess.core.Notation
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.PositionProblem
import com.zorix.chess.core.Side
import com.zorix.chess.core.Tactics
import com.zorix.chess.engine.uci.Score
import com.zorix.chess.engine.uci.AnalysisSnapshot
import com.zorix.chess.engine.uci.SearchLimit
import com.zorix.chess.engine.uci.UciEngine
import com.zorix.chess.platform.epochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * All app logic, independent of Android: the game, the promotion choice, undo/redo,
 * Stockfish hints ("Best move") and live analysis. The Android ViewModel only hosts it.
 *
 * Public functions must be called from the [scope]'s (main) thread.
 */
class ChessController(
    private val scope: CoroutineScope,
    private val host: EngineHost,
    private val store: KeyValueStore,
    private val clock: () -> Long = ::epochMillis,
) {
    private val _state = MutableStateFlow(restoreState())
    val state: StateFlow<ChessUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var engine: UciEngine? = null
    private val engineLock = Mutex()
    private var hintJob: Job? = null
    private var analysisJob: Job? = null
    private var optionsJob: Job? = null
    private var coachJob: Job? = null
    private var foreground = true

    init {
        scope.launch {
            ensureEngine()
            restartAnalysis()
        }
    }

    // ------------------------------------------------------------------ moves

    /** Called by the board for a tap-tap or drag-and-drop move. */
    fun onUserMove(from: Int, to: Int) {
        val st = _state.value
        if (st.game.status.isOver) {
            emit(UiMessage.GAME_IS_OVER)
            return
        }
        val position = st.game.position
        if (position.isPromotion(from, to)) {
            // The pawn reached the last rank: ask which piece to promote to (Queen, Rook, Bishop, Knight).
            val side = position[from]?.side ?: return
            _state.update { it.copy(pendingPromotion = PendingPromotion(from, to, side)) }
            return
        }
        val move = Move(from, to)
        if (!position.isLegal(move)) {
            emit(UiMessage.ILLEGAL_MOVE)
            return
        }
        play(move)
    }

    fun onPromotionChosen(type: PieceType) {
        val pending = _state.value.pendingPromotion ?: return
        _state.update { it.copy(pendingPromotion = null) }
        val move = Move(pending.from, pending.to, type)
        if (_state.value.game.position.isLegal(move)) play(move) else emit(UiMessage.ILLEGAL_MOVE)
    }

    fun onPromotionCancelled() {
        _state.update { it.copy(pendingPromotion = null) }
    }

    private fun play(move: Move) {
        val before = _state.value.game.position
        updateGame { it.play(move) }
        rateMove(before, move)
    }

    // ------------------------------------------------------------------ coach

    /** Coach mode: compares the played move with the engine's best move and classifies it. */
    private fun rateMove(before: Position, move: Move) {
        if (!_state.value.settings.coachMode) return
        val key = feedbackKey(before.fen(), move)
        if (_state.value.feedback.containsKey(key)) return
        val san = Notation.san(before, move)
        val after = before.play(move)
        coachJob?.cancel()
        coachJob = scope.launch {
            _state.update { it.copy(coachBusy = true) }
            try {
                val whiteMoved = before.sideToMove == Side.WHITE
                if (Tactics.isCheckmate(after)) {
                    val mate = Score(mate = if (whiteMoved) 1 else -1)
                    publishFeedback(key, MoveFeedback(san, MoveQuality.BEST, san, mate, mate))
                    return@launch
                }
                analysisJob?.cancelAndJoin()
                hintJob?.join()
                optionsJob?.join()
                val eng = ensureEngine() ?: return@launch
                val best = eng.search("fen ${before.fen()}", SearchLimit.MoveTime(COACH_TIME_MS), multiPv = 1)
                val bestScore = best.best?.score ?: return@launch // mover's point of view
                val afterScore = if (after.legalMoves.isEmpty()) {
                    Score(centipawns = 0) // stalemate
                } else {
                    eng.search("fen ${after.fen()}", SearchLimit.MoveTime(COACH_TIME_MS), multiPv = 1).best?.score?.negate()
                        ?: return@launch
                }
                val loss = (bestScore.winChance() - afterScore.winChance()).coerceAtLeast(0.0)
                val bestMove = best.bestMove?.let(Move::fromUci)?.takeIf { before.isLegal(it) }
                val quality = when {
                    bestMove == move -> MoveQuality.BEST
                    loss < 0.02 -> MoveQuality.EXCELLENT
                    loss < 0.05 -> MoveQuality.GOOD
                    loss < 0.10 -> MoveQuality.INACCURACY
                    loss < 0.20 -> MoveQuality.MISTAKE
                    else -> MoveQuality.BLUNDER
                }
                publishFeedback(
                    key,
                    MoveFeedback(
                        san = san,
                        quality = quality,
                        bestSan = bestMove?.let { Notation.san(before, it) },
                        scoreBefore = bestScore.forWhite(whiteMoved),
                        scoreAfter = afterScore.forWhite(whiteMoved),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onEngineError(e)
            } finally {
                _state.update { it.copy(coachBusy = false) }
                restartAnalysis(afterCoach = true)
            }
        }
    }

    private fun publishFeedback(key: String, feedback: MoveFeedback) {
        _state.update { it.copy(feedback = it.feedback + (key to feedback)) }
    }

    fun undo() {
        if (!_state.value.game.canUndo) return emit(UiMessage.NOTHING_TO_UNDO)
        updateGame { it.undo() }
    }

    fun redo() {
        if (!_state.value.game.canRedo) return emit(UiMessage.NOTHING_TO_REDO)
        updateGame { it.redo() }
    }

    /** Jump to the position after [plyCount] half-moves of the move list. */
    fun goTo(plyCount: Int) {
        if (plyCount == _state.value.game.plies.size) return
        updateGame { it.goTo(plyCount) }
    }

    fun newGame() {
        updateGame { Game.new() }
        scope.launch { runCatching { engine?.takeIf { it.isAlive }?.newGame() } }
    }

    fun flipBoard() {
        _state.update { it.copy(flipped = !it.flipped) }
        store.putString(KEY_FLIPPED, _state.value.flipped.toString())
    }

    /** Loads a FEN typed or pasted by the user. Returns the problem, or null on success. */
    fun loadFen(text: String): FenError? {
        val position = try {
            Position.fromFen(text)
        } catch (_: IllegalArgumentException) {
            return FenError.Malformed
        }
        position.problem()?.let { return FenError.Invalid(it) }
        setPosition(position)
        return null
    }

    /** Starts a fresh game from a set-up position (board editor / FEN). */
    fun setPosition(position: Position) {
        updateGame { Game.new(position) }
        emit(UiMessage.POSITION_LOADED)
    }

    fun pgn(): String = _state.value.game.toPgn()

    /** Ids of solved puzzles (Learn section), stored with the settings. */
    fun solvedPuzzles(): Set<Int> =
        store.getString(KEY_SOLVED).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    fun markPuzzleSolved(id: Int) {
        store.putString(KEY_SOLVED, (solvedPuzzles() + id).sorted().joinToString(","))
    }

    fun fen(): String = _state.value.fen

    private fun updateGame(transform: (Game) -> Game) {
        val before = _state.value
        val game = transform(before.game)
        if (game === before.game) return
        hintJob?.cancel()
        _state.update {
            it.copy(
                game = game,
                pendingPromotion = null,
                hint = HintState.Idle,
                analysis = it.analysis?.takeIf { view -> view.fen == game.position.fen() },
                moveCounter = it.moveCounter + 1,
            )
        }
        saveSession()
        restartAnalysis()
    }

    // ------------------------------------------------------------------ best move (hint)

    /** Asks Stockfish for the best move of the side to move ("Suggest for White/Black" on desktop). */
    fun requestBestMove() {
        val st = _state.value
        if (st.game.status.isOver) return emit(UiMessage.GAME_IS_OVER)
        if (hintJob?.isActive == true) return
        val game = st.game
        val position = game.position
        val fen = position.fen()
        val budget = st.settings.thinkTimeMs
        hintJob = scope.launch {
            analysisJob?.cancelAndJoin()
            optionsJob?.join()
            val eng = ensureEngine()
            if (eng == null) {
                emit(UiMessage.ENGINE_NOT_READY)
                return@launch
            }
            _state.update { it.copy(hint = HintState.Thinking(fen, clock(), budget, null)) }
            try {
                var lastPublish = 0L
                val result = eng.search(game.uciPosition(), SearchLimit.MoveTime(budget.toLong()), multiPv = 1) { snap ->
                    val now = clock()
                    if (now - lastPublish >= PUBLISH_INTERVAL_MS) {
                        lastPublish = now
                        val view = toView(position, snap)
                        _state.update { s ->
                            val h = s.hint
                            if (h is HintState.Thinking && h.fen == fen) s.copy(hint = h.copy(view = view)) else s
                        }
                    }
                }
                val view = toView(position, result)
                _state.update { s -> if (s.fen == fen) s.copy(hint = HintState.Ready(view)) else s }
                if (view.bestMove == null) emit(UiMessage.NO_LEGAL_MOVES)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onEngineError(e)
            } finally {
                _state.update { s ->
                    val h = s.hint
                    if (h is HintState.Thinking && h.fen == fen) s.copy(hint = HintState.Idle) else s
                }
            }
            restartAnalysis(afterHint = true)
        }
    }

    /** Ends the current think early; the best move found so far is still shown. */
    fun stopThinking() {
        if (_state.value.isThinking) engine?.stopSearch()
    }

    /** Plays the suggested move on the board ("Apply Suggestion" on desktop). */
    fun playSuggestion() {
        val st = _state.value
        val hint = st.hint as? HintState.Ready ?: return
        val move = hint.view.bestMove ?: return
        if (hint.view.fen != st.fen || !st.game.position.isLegal(move)) return emit(UiMessage.SUGGESTION_OUTDATED)
        play(move)
    }

    fun dismissSuggestion() {
        hintJob?.cancel()
        _state.update { it.copy(hint = HintState.Idle) }
    }

    // ------------------------------------------------------------------ live analysis

    fun setAnalysis(enabled: Boolean) {
        _state.update { it.copy(analysisOn = enabled, analysis = if (enabled) it.analysis else null) }
        store.putString(KEY_ANALYSIS, enabled.toString())
        restartAnalysis()
    }

    /** Plays the first move of analysis line [index]. */
    fun playLine(index: Int) {
        val st = _state.value
        val view = st.currentView ?: return
        val move = view.lines.getOrNull(index)?.firstMove ?: return
        if (st.game.position.isLegal(move)) play(move)
    }

    private fun restartAnalysis(afterHint: Boolean = false, afterCoach: Boolean = false) {
        analysisJob?.cancel()
        val st = _state.value
        val hintRunning = !afterHint && hintJob?.isActive == true
        val coachRunning = !afterCoach && coachJob?.isActive == true
        if (!st.analysisOn || !foreground || st.game.status.isOver || hintRunning || coachRunning) return
        val game = st.game
        val position = game.position
        val fen = position.fen()
        val lines = st.settings.analysisLines
        analysisJob = scope.launch {
            delay(ANALYSIS_DEBOUNCE_MS)
            optionsJob?.join()
            val eng = ensureEngine() ?: return@launch
            try {
                var lastPublish = 0L
                eng.search(game.uciPosition(), SearchLimit.Infinite, multiPv = lines) { snap ->
                    val now = clock()
                    if (now - lastPublish >= PUBLISH_INTERVAL_MS) {
                        lastPublish = now
                        val view = toView(position, snap)
                        _state.update { s -> if (s.analysisOn && s.fen == fen) s.copy(analysis = view) else s }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onEngineError(e)
            }
        }
    }

    // ------------------------------------------------------------------ settings & lifecycle

    fun updateSettings(transform: (Settings) -> Settings) {
        val old = _state.value.settings
        val new = transform(old)
        if (new == old) return
        _state.update { it.copy(settings = new) }
        new.save(store)
        if (new.threads != old.threads || new.hashMb != old.hashMb) {
            optionsJob = scope.launch {
                analysisJob?.cancelAndJoin()
                hintJob?.cancelAndJoin()
                runCatching { engine?.takeIf { it.isAlive }?.setOptions(engineOptions(new)) }
                restartAnalysis()
            }
        } else if (new.analysisLines != old.analysisLines) {
            restartAnalysis()
        }
    }

    /** App went to the background: stop burning battery. */
    fun onBackground() {
        foreground = false
        analysisJob?.cancel()
        stopThinking()
    }

    fun onForeground() {
        if (foreground) return
        foreground = true
        restartAnalysis()
    }

    fun retryEngine() {
        scope.launch {
            ensureEngine()
            restartAnalysis()
        }
    }

    fun close() {
        coachJob?.cancel()
        hintJob?.cancel()
        analysisJob?.cancel()
        engine?.close()
        engine = null
    }

    // ------------------------------------------------------------------ engine plumbing

    private fun engineOptions(settings: Settings) = mapOf(
        "Threads" to settings.threads.coerceIn(1, host.cpuCores.coerceAtLeast(1)).toString(),
        "Hash" to settings.hashMb.toString(),
    )

    /** Returns a running engine, (re)starting it if needed; null if it cannot run. */
    private suspend fun ensureEngine(): UciEngine? {
        engine?.takeIf { it.isAlive }?.let { return it }
        return engineLock.withLock {
            engine?.takeIf { it.isAlive }?.let { return@withLock it }
            engine?.close()
            engine = null
            startEngine()
        }
    }

    private suspend fun startEngine(): UciEngine? {
        _state.update { it.copy(engine = EngineStatus.Preparing(0f)) }
        val launches = try {
            host.prepare { p -> _state.update { it.copy(engine = EngineStatus.Preparing(p.coerceIn(0f, 1f))) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(engine = EngineStatus.Failed(e.message ?: e.toString())) }
            return null
        }
        _state.update { it.copy(engine = EngineStatus.Starting) }
        var lastError = "No engine binary found"
        for (launch in launches) {
            val candidate = UciEngine(launch.connect)
            try {
                candidate.start(engineOptions(_state.value.settings))
                // A tiny search proves the network loaded and the CPU supports this build.
                candidate.search("startpos", SearchLimit.Depth(1))
                engine = candidate
                _state.update { it.copy(engine = EngineStatus.Ready(candidate.engineName, launch.label)) }
                return candidate
            } catch (e: CancellationException) {
                candidate.close()
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: e.toString()
                candidate.close()
            }
        }
        _state.update { it.copy(engine = EngineStatus.Failed(lastError)) }
        return null
    }

    private fun onEngineError(e: Exception) {
        val alive = engine?.isAlive == true
        if (!alive) {
            _state.update { it.copy(engine = EngineStatus.Failed(e.message ?: e.toString())) }
        }
    }

    private fun toView(position: Position, snap: AnalysisSnapshot): EngineView {
        val whiteToMove = position.sideToMove == Side.WHITE
        val lines = snap.lines.map { line ->
            val san = Notation.variationToSan(position, line.pv, MAX_PV_MOVES)
            EngineLine(
                rank = line.multipv,
                depth = line.depth,
                score = line.score.forWhite(whiteToMove),
                uci = line.pv,
                san = san,
                text = Notation.numbered(position, san),
            )
        }
        val best = (snap.bestMove ?: snap.lines.firstOrNull()?.pv?.firstOrNull())
            ?.let(Move::fromUci)
            ?.takeIf { position.isLegal(it) }
        return EngineView(
            fen = position.fen(),
            sideToMove = position.sideToMove,
            lines = lines,
            depth = snap.depth,
            nodes = snap.nodes,
            nps = snap.nps,
            timeMs = snap.timeMs,
            bestMove = best,
            bestSan = best?.let { Notation.san(position, it) },
            finished = snap.finished,
        )
    }

    private fun emit(message: UiMessage) {
        _messages.tryEmit(message)
    }

    // ------------------------------------------------------------------ persistence

    private fun restoreState(): ChessUiState {
        val settings = Settings.load(store, host.cpuCores)
        val game = runCatching {
            val start = store.getString(KEY_START_FEN) ?: Position.START_FEN
            val moves = store.getString(KEY_MOVES).orEmpty().split(' ').filter { it.isNotBlank() }
            val redo = store.getString(KEY_REDO).orEmpty().split(' ').filter { it.isNotBlank() }
            Game.restore(start, moves, redo)
        }.getOrElse { Game.new() }
        return ChessUiState(
            game = game,
            flipped = store.getString(KEY_FLIPPED)?.toBooleanStrictOrNull() ?: false,
            analysisOn = store.getString(KEY_ANALYSIS)?.toBooleanStrictOrNull() ?: false,
            settings = settings,
        )
    }

    private fun saveSession() {
        val game = _state.value.game
        store.putString(KEY_START_FEN, game.start.fen())
        store.putString(KEY_MOVES, game.plies.joinToString(" ") { it.move.uci })
        store.putString(KEY_REDO, game.fullLine.drop(game.plies.size).joinToString(" ") { it.move.uci })
    }

    sealed interface FenError {
        data object Malformed : FenError
        data class Invalid(val problem: PositionProblem) : FenError
    }

    companion object {
        private const val PUBLISH_INTERVAL_MS = 120L
        private const val ANALYSIS_DEBOUNCE_MS = 150L
        private const val MAX_PV_MOVES = 14
        private const val COACH_TIME_MS = 450L
        private const val KEY_SOLVED = "learn.solved"

        private const val KEY_START_FEN = "game.startFen"
        private const val KEY_MOVES = "game.moves"
        private const val KEY_REDO = "game.redo"
        private const val KEY_FLIPPED = "board.flipped"
        private const val KEY_ANALYSIS = "analysis.on"
    }
}
