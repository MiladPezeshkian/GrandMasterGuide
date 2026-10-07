package com.zorix.chess.controller

import com.zorix.chess.coach.Coach
import com.zorix.chess.core.Game
import com.zorix.chess.core.Move
import com.zorix.chess.core.Notation
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.PositionProblem
import com.zorix.chess.core.Side
import com.zorix.chess.engine.uci.AnalysisSnapshot
import com.zorix.chess.engine.uci.Score
import com.zorix.chess.engine.uci.SearchLimit
import com.zorix.chess.platform.epochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The analysis board: the game, the promotion choice, undo/redo, Zorix hints ("Best move"),
 * live analysis and the coach. Independent of Android and iOS; the platforms only host it.
 *
 * Public functions must be called from the [scope]'s (main) thread.
 */
class ChessController(
    private val scope: CoroutineScope,
    host: EngineHost,
    private val store: KeyValueStore,
    private val clock: () -> Long = ::epochMillis,
    hub: EngineHub? = null,
    profile: ProfileStore? = null,
    private val speech: Speech = Speech.None,
) {
    private val _state = MutableStateFlow(restoreState(host.cpuCores))
    val state: StateFlow<ChessUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** The engine shared with play mode, lessons and game review. */
    val hub: EngineHub = hub ?: EngineHub(scope, host) { engineOptions(_state.value.settings, host.cpuCores) }
    val profile: ProfileStore = profile ?: ProfileStore(store)

    /** Resolved display language ("en", "fa", "ckb"); set by the UI. */
    var language: String = "en"
        set(value) {
            if (field == value) return
            field = value
            // Existing explanations were written in the previous language.
            _state.update { it.copy(feedback = emptyMap(), hintExplanation = null) }
        }

    private var hintJob: Job? = null
    private var coachJob: Job? = null
    private var analysisJob: Job? = null
    private var foreground = true
    /** The analysis board is on screen (live analysis pauses on other screens). */
    private var visible = true

    init {
        scope.launch { this@ChessController.hub.status.collect { s -> _state.update { it.copy(engine = s) } } }
        scope.launch {
            this@ChessController.hub.ensure()
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
        val game = _state.value.game
        updateGame { it.play(move) }
        rateMove(game, move)
    }

    // ------------------------------------------------------------------ coach

    /** Coach mode: compares the played move with the engine's best move, classifies and explains it. */
    private fun rateMove(gameBefore: Game, move: Move) {
        if (!_state.value.settings.coachMode) return
        val key = feedbackKey(gameBefore.position.fen(), move)
        if (_state.value.feedback.containsKey(key)) return
        val lang = language
        val name = profile.current.name
        coachJob?.cancel()
        coachJob = scope.launch {
            _state.update { it.copy(coachBusy = true) }
            try {
                val feedback = CoachService.rate(hub, gameBefore, move, lang, name) ?: return@launch
                publishFeedback(key, feedback)
                speak(feedback.speech)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hub.reportError(e)
            } finally {
                _state.update { it.copy(coachBusy = false) }
                restartAnalysis(coachJob)
            }
        }
    }

    private fun publishFeedback(key: String, feedback: MoveFeedback) {
        _state.update { it.copy(feedback = it.feedback + (key to feedback)) }
    }

    /** Reads the last coach message aloud again. */
    fun speakLastFeedback() {
        _state.value.lastFeedback?.let { speech.stop(); speakNow(it.speech) }
    }

    fun speakHintExplanation() {
        _state.value.hintExplanation?.let { speech.stop(); speakNow(it.speech) }
    }

    private fun speak(text: String) {
        if (visible && foreground) speakNow(text)
    }

    private fun speakNow(text: String) {
        val lang = speechLanguage(language)
        if (_state.value.settings.voice && text.isNotBlank() && speech.supports(lang)) speech.speak(text, lang)
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
        scope.launch {
            hub.newGame()
            // Clearing the hash interrupted the analysis that updateGame started.
            restartAnalysis()
        }
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

    /** Opens a finished game (from play mode or review) on the analysis board. */
    fun loadGame(game: Game) {
        updateGame { game }
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
                hintExplanation = null,
                analysis = it.analysis?.takeIf { view -> view.fen == game.position.fen() },
                moveCounter = it.moveCounter + 1,
            )
        }
        saveSession()
        restartAnalysis()
    }

    // ------------------------------------------------------------------ best move (hint)

    /** Asks Zorix for the best move of the side to move, then explains it. */
    fun requestBestMove() {
        val st = _state.value
        if (st.game.status.isOver) return emit(UiMessage.GAME_IS_OVER)
        if (hintJob?.isActive == true) return
        val game = st.game
        val position = game.position
        val fen = position.fen()
        val budget = st.settings.thinkTimeMs
        val lang = language
        hintJob = scope.launch {
            if (hub.status.value !is EngineStatus.Ready && hub.ensure() == null) {
                emit(UiMessage.ENGINE_NOT_READY)
                return@launch
            }
            _state.update { it.copy(hint = HintState.Thinking(fen, clock(), budget, null)) }
            try {
                var lastPublish = 0L
                val result = hub.run { eng ->
                    eng.search(game.uciPosition(), SearchLimit.MoveTime(budget.toLong()), multiPv = 1) { snap ->
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
                }
                if (result == null) {
                    emit(UiMessage.ENGINE_NOT_READY)
                    return@launch
                }
                val view = toView(position, result)
                _state.update { s -> if (s.fen == fen) s.copy(hint = HintState.Ready(view)) else s }
                val best = view.bestMove
                if (best == null) {
                    emit(UiMessage.NO_LEGAL_MOVES)
                } else {
                    val line = result.best
                    val msg = Coach.explainBestMove(
                        position, best, line?.score ?: Score(centipawns = 0), line?.pv.orEmpty(), lang, speechLanguage(lang),
                    )
                    _state.update { s -> if (s.fen == fen) s.copy(hintExplanation = HintExplanation(fen, msg.display, msg.speech)) else s }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hub.reportError(e)
            } finally {
                _state.update { s ->
                    val h = s.hint
                    if (h is HintState.Thinking && h.fen == fen) s.copy(hint = HintState.Idle) else s
                }
            }
            restartAnalysis(hintJob)
        }
    }

    /** Ends the current think early; the best move found so far is still shown. */
    fun stopThinking() {
        if (_state.value.isThinking) hub.stopSearch()
    }

    /** Plays the suggested move on the board. */
    fun playSuggestion() {
        val st = _state.value
        val hint = st.hint as? HintState.Ready ?: return
        val move = hint.view.bestMove ?: return
        if (hint.view.fen != st.fen || !st.game.position.isLegal(move)) return emit(UiMessage.SUGGESTION_OUTDATED)
        play(move)
    }

    fun dismissSuggestion() {
        hintJob?.cancel()
        _state.update { it.copy(hint = HintState.Idle, hintExplanation = null) }
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

    /** Restarts live analysis; [finishing] is the hint/coach job that is about to end (not counted as busy). */
    private fun restartAnalysis(finishing: Job? = null) {
        analysisJob?.cancel()
        val st = _state.value
        val busy = (hintJob?.isActive == true && hintJob !== finishing) || (coachJob?.isActive == true && coachJob !== finishing)
        if (!st.analysisOn || !foreground || !visible || st.game.status.isOver || busy) return
        val game = st.game
        val position = game.position
        val fen = position.fen()
        val lines = st.settings.analysisLines
        analysisJob = hub.launchBackground { eng ->
            delay(ANALYSIS_DEBOUNCE_MS)
            var lastPublish = 0L
            eng.search(game.uciPosition(), SearchLimit.Infinite, multiPv = lines) { snap ->
                val now = clock()
                if (now - lastPublish >= PUBLISH_INTERVAL_MS) {
                    lastPublish = now
                    val view = toView(position, snap)
                    _state.update { s -> if (s.analysisOn && s.fen == fen) s.copy(analysis = view) else s }
                }
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
        if (!new.voice) speech.stop()
        // Load the voice as soon as it is switched on, so the first explanation is not delayed.
        if (new.voice && !old.voice) speech.prepare(speechLanguage(language))
        if (new.threads != old.threads || new.hashMb != old.hashMb) {
            scope.launch {
                hub.applyOptions()
                restartAnalysis()
            }
        } else if (new.analysisLines != old.analysisLines) {
            restartAnalysis()
        }
    }

    /** The analysis board became visible / hidden (other tabs pause live analysis and the voice). */
    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (visible) restartAnalysis() else analysisJob?.cancel()
    }

    /** App went to the background: stop burning battery. */
    fun onBackground() {
        foreground = false
        analysisJob?.cancel()
        stopThinking()
        speech.stop()
    }

    fun onForeground() {
        if (foreground) return
        foreground = true
        restartAnalysis()
    }

    fun retryEngine() {
        scope.launch {
            hub.ensure()
            restartAnalysis()
        }
    }

    fun close() {
        coachJob?.cancel()
        hintJob?.cancel()
        analysisJob?.cancel()
        hub.close()
    }

    // ------------------------------------------------------------------ helpers

    private fun toView(position: Position, snap: AnalysisSnapshot): EngineView = engineView(position, snap)

    private fun emit(message: UiMessage) {
        _messages.tryEmit(message)
    }

    // ------------------------------------------------------------------ persistence

    private fun restoreState(cores: Int): ChessUiState {
        val settings = Settings.load(store, cores)
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
        const val MAX_PV_MOVES = 14
        private const val COACH_TIME_MS = 500L
        private const val KEY_SOLVED = "learn.solved"

        private const val KEY_START_FEN = "game.startFen"
        private const val KEY_MOVES = "game.moves"
        private const val KEY_REDO = "game.redo"
        private const val KEY_FLIPPED = "board.flipped"
        private const val KEY_ANALYSIS = "analysis.on"

        fun engineOptions(settings: Settings, cores: Int) = mapOf(
            "Threads" to settings.threads.coerceIn(1, cores.coerceAtLeast(1)).toString(),
            "Hash" to settings.hashMb.toString(),
        )
    }
}

/** Converts an engine snapshot into display lines (scores from White's point of view). */
fun engineView(position: Position, snap: AnalysisSnapshot, maxPv: Int = ChessController.MAX_PV_MOVES): EngineView {
    val whiteToMove = position.sideToMove == Side.WHITE
    val lines = snap.lines.map { line ->
        val san = Notation.variationToSan(position, line.pv, maxPv)
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
