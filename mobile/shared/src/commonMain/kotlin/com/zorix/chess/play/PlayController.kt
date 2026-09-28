package com.zorix.chess.play

import com.zorix.chess.coach.Coach
import com.zorix.chess.coach.CoachMessage
import com.zorix.chess.controller.CoachService
import com.zorix.chess.controller.EngineHub
import com.zorix.chess.controller.GameOutcome
import com.zorix.chess.controller.KeyValueStore
import com.zorix.chess.controller.MoveFeedback
import com.zorix.chess.controller.PendingPromotion
import com.zorix.chess.controller.ProfileStore
import com.zorix.chess.controller.Settings
import com.zorix.chess.controller.Speech
import com.zorix.chess.controller.feedbackKey
import com.zorix.chess.controller.speechLanguage
import com.zorix.chess.core.EndReason
import com.zorix.chess.core.Game
import com.zorix.chess.core.GameResult
import com.zorix.chess.core.Move
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.engine.uci.SearchLimit
import com.zorix.chess.platform.epochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** How a game against Zorix ended. */
data class PlayResult(
    val outcome: GameOutcome,
    val reason: EndReason?,
    val resigned: Boolean,
    val drawAgreed: Boolean,
    val ratingBefore: Int,
    val ratingAfter: Int,
)

data class PlayState(
    val active: Boolean = false,
    val bot: Bot = Bots.byLevel(1),
    val userSide: Side = Side.WHITE,
    val game: Game = Game.new(),
    val pendingPromotion: PendingPromotion? = null,
    val botThinking: Boolean = false,
    /** Zorix's explanation of its own last move, including threats against the player. */
    val botMessage: CoachMessage? = null,
    /** Coach verdicts for the player's moves. */
    val feedback: Map<String, MoveFeedback> = emptyMap(),
    val coachBusy: Boolean = false,
    /** A suggested move (hint) with its explanation. */
    val hint: CoachMessage? = null,
    val hintBusy: Boolean = false,
    /** Hints and takebacks used (a win with help earns 2 stars instead of 3). */
    val assists: Int = 0,
    val result: PlayResult? = null,
    val drawDeclined: Boolean = false,
    val moveCounter: Int = 0,
) {
    val fen: String get() = game.position.fen()
    val userToMove: Boolean get() = game.position.sideToMove == userSide && !game.status.isOver && result == null
    val lastFeedback: MoveFeedback?
        get() = game.plies.lastOrNull { it.before.sideToMove == userSide }?.let { feedback[feedbackKey(it.before.fen(), it.move)] }
}

/**
 * Play against Zorix bots with 20 strength levels. The coach explains the player's moves,
 * Zorix explains its own moves and warns about threats, and the player's rating is updated
 * with the Elo formula after every game.
 */
class PlayController(
    private val scope: CoroutineScope,
    private val hub: EngineHub,
    private val profile: ProfileStore,
    private val store: KeyValueStore,
    private val settings: () -> Settings,
    private val speech: Speech = Speech.None,
    private val random: Random = Random(epochMillis()),
) {
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<PlayState> = _state.asStateFlow()

    /** Resolved display language, set by the UI. */
    var language: String = "en"
    /** The play screen is visible (the voice only speaks then). */
    var visible: Boolean = false

    private var botJob: Job? = null
    private var coachJob: Job? = null
    private var hintJob: Job? = null

    init {
        // A restored game where it is Zorix's turn continues right away.
        if (_state.value.active && !_state.value.userToMove && _state.value.result == null) botTurn()
    }

    // ------------------------------------------------------------------ game flow

    fun start(level: Int, side: Side?) {
        cancelJobs()
        speech.stop()
        val userSide = side ?: if (random.nextBoolean()) Side.WHITE else Side.BLACK
        _state.value = PlayState(active = true, bot = Bots.byLevel(level), userSide = userSide, game = Game.new())
        save()
        scope.launch { hub.newGame() }
        if (userSide == Side.BLACK) botTurn()
    }

    fun rematch() {
        val st = _state.value
        start(st.bot.level, st.userSide)
    }

    /** Leaves the finished game screen (back to the level map). */
    fun close() {
        cancelJobs()
        speech.stop()
        _state.update { it.copy(active = false) }
        save()
    }

    fun onUserMove(from: Int, to: Int) {
        val st = _state.value
        if (!st.userToMove || st.botThinking) return
        val position = st.game.position
        if (position.isPromotion(from, to)) {
            _state.update { it.copy(pendingPromotion = PendingPromotion(from, to, st.userSide)) }
            return
        }
        val move = Move(from, to)
        if (position.isLegal(move)) playUser(move)
    }

    fun onPromotionChosen(type: PieceType) {
        val p = _state.value.pendingPromotion ?: return
        _state.update { it.copy(pendingPromotion = null) }
        val move = Move(p.from, p.to, type)
        if (_state.value.game.position.isLegal(move)) playUser(move)
    }

    fun onPromotionCancelled() = _state.update { it.copy(pendingPromotion = null) }

    private fun playUser(move: Move) {
        val before = _state.value.game
        hintJob?.cancel()
        _state.update { it.copy(game = it.game.play(move), hint = null, hintBusy = false, botMessage = null, drawDeclined = false, moveCounter = it.moveCounter + 1) }
        save()
        coach(before, move)
        if (!checkGameOver()) botTurn()
    }

    private fun coach(before: Game, move: Move) {
        if (!settings().coachMode) return
        val lang = language
        val name = profile.current.name
        coachJob = scope.launch {
            _state.update { it.copy(coachBusy = true) }
            try {
                val fb = CoachService.rate(hub, before, move, lang, name, thinkMs = 350) ?: return@launch
                _state.update { it.copy(feedback = it.feedback + (feedbackKey(before.position.fen(), move) to fb)) }
                say(fb.speech)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hub.reportError(e)
            } finally {
                _state.update { it.copy(coachBusy = false) }
            }
        }
    }

    private fun botTurn() {
        val st = _state.value
        if (st.result != null || st.game.status.isOver || st.game.position.sideToMove == st.userSide) return
        botJob?.cancel()
        val job = scope.launch {
            _state.update { it.copy(botThinking = true) }
            try {
                // Let the coach finish first so its analysis is not interrupted.
                coachJob?.join()
                val game = _state.value.game
                val position = game.position
                val plan = BotBrain.plan(_state.value.bot, game.plies.size)
                val started = epochMillis()
                val snap = hub.run { eng ->
                    eng.search(game.uciPosition(), plan.limit, multiPv = plan.multiPv, options = plan.options)
                }
                val move = snap?.let { BotBrain.choose(position, it, plan, random) } ?: position.legalMoves.randomOrNull(random) ?: return@launch
                // A short, natural pause before moving.
                val elapsed = epochMillis() - started
                if (elapsed < MIN_BOT_DELAY_MS) delay(MIN_BOT_DELAY_MS - elapsed)
                if (_state.value.game !== game) return@launch
                val explain = if (settings().explainBotMoves) {
                    Coach.explainOpponentMove(position, move, language, profile.current.name, "Zorix", speechLanguage(language))
                } else {
                    null
                }
                _state.update { it.copy(game = it.game.play(move), botMessage = explain, moveCounter = it.moveCounter + 1) }
                save()
                if (!checkGameOver() && explain != null && explain.warning) {
                    // Zorix only speaks up about its own moves when it creates a threat.
                    say(explain.speech)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hub.reportError(e)
            } finally {
                _state.update { it.copy(botThinking = false) }
            }
        }
        botJob = job
    }

    /** Takes back the player's last move (and Zorix's reply). Counts as help. */
    fun takeback() {
        val st = _state.value
        if (st.result != null || st.botThinking) return
        var g = st.game
        if (g.plies.isEmpty()) return
        g = g.undo()
        if (g.position.sideToMove != st.userSide && g.plies.isNotEmpty()) g = g.undo()
        if (g.position.sideToMove != st.userSide) return
        cancelJobs()
        _state.update { it.copy(game = Game.restore(g.start.fen(), g.plies.map { p -> p.move.uci }), assists = it.assists + 1, hint = null, botMessage = null, moveCounter = it.moveCounter + 1) }
        save()
    }

    /** Suggests the best move with an explanation. Counts as help. */
    fun hint() {
        val st = _state.value
        if (!st.userToMove || st.hintBusy) return
        val game = st.game
        val lang = language
        hintJob = scope.launch {
            _state.update { it.copy(hintBusy = true) }
            try {
                val snap = hub.run { it.search(game.uciPosition(), SearchLimit.MoveTime(1200), multiPv = 1) } ?: return@launch
                val best = snap.bestMove?.let(Move::fromUci)?.takeIf { game.position.isLegal(it) } ?: return@launch
                val line = snap.best ?: return@launch
                val msg = Coach.explainBestMove(game.position, best, line.score, line.pv, lang, speechLanguage(lang))
                if (_state.value.game === game) {
                    _state.update { it.copy(hint = msg, assists = it.assists + 1) }
                    say(msg.speech)
                }
            } finally {
                _state.update { it.copy(hintBusy = false) }
            }
        }
    }

    fun resign() {
        val st = _state.value
        if (st.result != null || !st.active) return
        finish(GameOutcome.LOSS, null, resigned = true, drawAgreed = false)
    }

    /** Offers a draw; Zorix accepts when it is not better and the game has gone on for a while. */
    fun offerDraw() {
        val st = _state.value
        if (st.result != null || !st.userToMove) return
        val game = st.game
        scope.launch {
            val snap = hub.run { it.search(game.uciPosition(), SearchLimit.MoveTime(400), multiPv = 1) }
            val score = snap?.best?.score ?: return@launch
            // Score is from the player's point of view (it is their turn).
            val playerCp = score.mate?.let { if (it > 0) 5000 else -5000 } ?: score.centipawns ?: 0
            if (game.plies.size >= 30 && playerCp >= -40) {
                finish(GameOutcome.DRAW, null, resigned = false, drawAgreed = true)
            } else {
                _state.update { it.copy(drawDeclined = true) }
            }
        }
    }

    fun speakAgain() {
        val st = _state.value
        (st.hint?.speech ?: st.lastFeedback?.speech ?: st.botMessage?.speech)?.let { speech.stop(); sayNow(it) }
    }

    private fun checkGameOver(): Boolean {
        val st = _state.value
        val status = st.game.status
        if (!status.isOver) return false
        val outcome = when (status.result) {
            GameResult.DRAW -> GameOutcome.DRAW
            GameResult.WHITE_WINS -> if (st.userSide == Side.WHITE) GameOutcome.WIN else GameOutcome.LOSS
            GameResult.BLACK_WINS -> if (st.userSide == Side.BLACK) GameOutcome.WIN else GameOutcome.LOSS
            GameResult.ONGOING -> return false
        }
        finish(outcome, status.reason, resigned = false, drawAgreed = false)
        return true
    }

    private fun finish(outcome: GameOutcome, reason: EndReason?, resigned: Boolean, drawAgreed: Boolean) {
        botJob?.cancel()
        hintJob?.cancel()
        val st = _state.value
        val before = profile.current.rating
        profile.recordGame(st.bot.level, st.bot.elo, outcome, assisted = st.assists > 0)
        val after = profile.current.rating
        _state.update { it.copy(result = PlayResult(outcome, reason, resigned, drawAgreed, before, after), botThinking = false, hint = null) }
        save()
    }

    private fun cancelJobs() {
        botJob?.cancel()
        coachJob?.cancel()
        hintJob?.cancel()
    }

    private fun say(text: String) {
        if (visible) sayNow(text)
    }

    private fun sayNow(text: String) {
        val lang = speechLanguage(language)
        if (settings().voice && text.isNotBlank() && speech.supports(lang)) speech.speak(text, lang)
    }

    // ------------------------------------------------------------------ persistence

    private fun save() {
        val st = _state.value
        store.putString(KEY_ACTIVE, st.active.toString())
        store.putString(KEY_LEVEL, st.bot.level.toString())
        store.putString(KEY_SIDE, st.userSide.name)
        store.putString(KEY_MOVES, st.game.plies.joinToString(" ") { it.move.uci })
        store.putString(KEY_ASSISTS, st.assists.toString())
        store.putString(KEY_FINISHED, (st.result != null).toString())
    }

    private fun restore(): PlayState {
        if (store.getString(KEY_ACTIVE) != "true" || store.getString(KEY_FINISHED) == "true") return PlayState()
        return runCatching {
            val level = store.getString(KEY_LEVEL)?.toIntOrNull() ?: 1
            val side = Side.valueOf(store.getString(KEY_SIDE) ?: "WHITE")
            val moves = store.getString(KEY_MOVES).orEmpty().split(' ').filter { it.isNotBlank() }
            val game = Game.restore(Position.START_FEN, moves)
            PlayState(active = true, bot = Bots.byLevel(level), userSide = side, game = game, assists = store.getString(KEY_ASSISTS)?.toIntOrNull() ?: 0)
        }.getOrElse { PlayState() }
    }

    companion object {
        private const val MIN_BOT_DELAY_MS = 650L
        private const val KEY_ACTIVE = "play.active"
        private const val KEY_LEVEL = "play.level"
        private const val KEY_SIDE = "play.side"
        private const val KEY_MOVES = "play.moves"
        private const val KEY_ASSISTS = "play.assists"
        private const val KEY_FINISHED = "play.finished"
    }
}
