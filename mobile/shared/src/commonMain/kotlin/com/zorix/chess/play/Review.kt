package com.zorix.chess.play

import com.zorix.chess.coach.Coach
import com.zorix.chess.coach.CoachQuality
import com.zorix.chess.coach.MoveAnalysis
import com.zorix.chess.controller.CoachService
import com.zorix.chess.controller.EngineHub
import com.zorix.chess.controller.ProfileStore
import com.zorix.chess.controller.speechLanguage
import com.zorix.chess.core.Game
import com.zorix.chess.core.Move
import com.zorix.chess.core.Notation
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.engine.uci.Score
import com.zorix.chess.engine.uci.SearchLimit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.exp

/** One reviewed half-move. Scores are from White's point of view. */
data class ReviewedMove(
    val index: Int,
    val side: Side,
    val san: String,
    val move: Move,
    val quality: CoachQuality,
    val scoreBefore: Score,
    val scoreAfter: Score,
    val bestSan: String?,
    val bestMove: Move?,
    val accuracy: Double,
    val message: String,
    val speech: String,
)

data class GameReview(
    val moves: List<ReviewedMove>,
    val accuracy: Map<Side, Double>,
    val counts: Map<Side, Map<CoachQuality, Int>>,
    /** Evaluation after each ply in pawns from White's view, clamped to +-10 (index 0 = start). */
    val evalCurve: List<Double>,
    val opening: String?,
) {
    fun keyMoments(side: Side): List<ReviewedMove> = moves.filter {
        it.side == side && it.quality in setOf(CoachQuality.BRILLIANT, CoachQuality.GREAT, CoachQuality.MISS, CoachQuality.MISTAKE, CoachQuality.BLUNDER)
    }
}

/** A "find the better move" exercise built from a mistake in the reviewed game. */
data class RetryState(
    val moment: ReviewedMove,
    val position: Position,
    val checking: Boolean = false,
    /** null = not answered yet, true = good move, false = still not good. */
    val solved: Boolean? = null,
    val message: String? = null,
    val tried: Move? = null,
)

data class ReviewState(
    val game: Game? = null,
    val player: Side? = null,
    val progress: Float = 0f,
    val review: GameReview? = null,
    /** Ply shown on the board (0 = start). */
    val ply: Int = 0,
    val retry: RetryState? = null,
)

/** Game review, like chess.com: accuracy, move classifications, key moments and retry exercises. */
class ReviewController(
    private val scope: CoroutineScope,
    private val hub: EngineHub,
    private val profile: ProfileStore,
) {
    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    var language: String = "en"
    private var job: Job? = null

    /** Starts reviewing [game]; [player] is the side whose moves get personal explanations. */
    fun start(game: Game, player: Side?) {
        job?.cancel()
        _state.value = ReviewState(game = game, player = player, ply = game.plies.size)
        val lang = language
        val name = profile.current.name
        job = scope.launch {
            val positions = listOf(game.start) + game.plies.map { it.after }
            val results = ArrayList<Analysed>(positions.size)
            for ((i, pos) in positions.withIndex()) {
                results += analyse(pos) ?: return@launch
                _state.update { it.copy(progress = (i + 1f) / positions.size) }
            }
            _state.update { it.copy(review = build(game, results, player, lang, name)) }
        }
    }

    fun goTo(ply: Int) = _state.update { s -> s.copy(ply = ply.coerceIn(0, s.game?.plies?.size ?: 0)) }

    fun cancel() {
        job?.cancel()
        _state.value = ReviewState()
    }

    // ------------------------------------------------------------------ retry

    fun startRetry(moment: ReviewedMove) {
        val game = _state.value.game ?: return
        val pos = game.plies[moment.index].before
        _state.update { it.copy(retry = RetryState(moment, pos), ply = moment.index) }
    }

    fun endRetry() = _state.update { it.copy(retry = null) }

    fun onRetryMove(move: Move) {
        val r = _state.value.retry ?: return
        if (r.checking || r.solved == true || !r.position.isLegal(move)) return
        val lang = language
        _state.update { it.copy(retry = r.copy(checking = true, tried = move)) }
        scope.launch {
            val before = r.position
            val after = before.play(move)
            val res = hub.run { eng ->
                val best = eng.search("fen ${before.fen()}", SearchLimit.MoveTime(600), multiPv = 1)
                val reply = if (after.legalMoves.isEmpty()) null else eng.search("fen ${after.fen()}", SearchLimit.MoveTime(600), multiPv = 1)
                best to reply
            }
            if (res == null) {
                _state.update { it.copy(retry = r.copy(checking = false)) }
                return@launch
            }
            val (best, reply) = res
            val bestScore = best.best?.score ?: Score(centipawns = 0)
            val afterScore = when {
                after.legalMoves.isEmpty() && after.isCheck -> Score(mate = 1)
                after.legalMoves.isEmpty() -> Score(centipawns = 0)
                else -> reply?.best?.score?.negate() ?: Score(centipawns = 0)
            }
            val good = bestScore.winChance() - afterScore.winChance() < 0.05
            val msg = if (good) {
                Coach.explainSolutionMove(before, move, listOf(move.uci) + reply?.best?.pv.orEmpty(), lang, speechLanguage(lang)).display
            } else {
                val a = MoveAnalysis(
                    before, move, best.bestMove?.let(Move::fromUci), bestScore, best.best?.pv.orEmpty(), null,
                    afterScore, reply?.best?.pv.orEmpty(),
                )
                Coach.explainOwnMove(a, lang, profile.current.name, speechLanguage(lang)).display
            }
            _state.update { s -> s.copy(retry = s.retry?.copy(checking = false, solved = good, message = msg)) }
        }
    }

    // ------------------------------------------------------------------ analysis

    private class Analysed(val score: Score, val best: Move?, val pv: List<String>, val second: Score?, val terminal: Boolean)

    private suspend fun analyse(pos: Position): Analysed? {
        if (pos.legalMoves.isEmpty()) {
            return Analysed(if (pos.isCheck) Score(mate = 0) else Score(centipawns = 0), null, emptyList(), null, true)
        }
        return hub.run { eng ->
            val snap = eng.search("fen ${pos.fen()}", SearchLimit.MoveTime(REVIEW_MS), multiPv = 2)
            Analysed(
                snap.best?.score ?: Score(centipawns = 0),
                snap.bestMove?.let(Move::fromUci)?.takeIf { pos.isLegal(it) },
                snap.best?.pv.orEmpty(),
                snap.lines.getOrNull(1)?.score,
                false,
            )
        }
    }

    private fun build(game: Game, a: List<Analysed>, player: Side?, lang: String, name: String): GameReview {
        val moves = ArrayList<ReviewedMove>()
        val curve = ArrayList<Double>()
        for ((i, pos) in (listOf(game.start) + game.plies.map { it.after }).withIndex()) {
            val s = a[i].score
            val white = if (a[i].terminal && s.mate == 0) {
                if (pos.sideToMove == Side.WHITE) -10.0 else 10.0
            } else {
                s.forWhite(pos.sideToMove == Side.WHITE).pawns()
            }
            curve += white
        }
        var gameSoFar = Game.new(game.start)
        for ((i, ply) in game.plies.withIndex()) {
            val before = a[i]
            val after = a[i + 1]
            val mover = ply.before.sideToMove
            val afterScore = when {
                after.terminal && after.score.mate == 0 -> Score(mate = 1)
                after.terminal -> Score(centipawns = 0)
                else -> after.score.negate()
            }
            val analysis = MoveAnalysis(
                before = ply.before,
                move = ply.move,
                bestMove = before.best,
                bestScore = before.score,
                bestPv = before.pv,
                secondScore = before.second,
                afterScore = afterScore,
                replyPv = after.pv,
                bookOpening = CoachService.bookName(gameSoFar, ply.move, lang),
            )
            val explain = player == null || mover == player
            val msg = if (explain) Coach.explainOwnMove(analysis, lang, name, speechLanguage(lang)) else null
            val quality = msg?.quality ?: Coach.classify(analysis)
            val white = mover == Side.WHITE
            moves += ReviewedMove(
                index = i,
                side = mover,
                san = ply.san,
                move = ply.move,
                quality = quality,
                scoreBefore = before.score.forWhite(white),
                scoreAfter = afterScore.forWhite(white),
                bestSan = before.best?.takeIf { it != ply.move }?.let { Notation.san(ply.before, it) },
                bestMove = before.best?.takeIf { it != ply.move },
                accuracy = moveAccuracy(before.score, afterScore),
                message = msg?.display.orEmpty(),
                speech = msg?.speech.orEmpty(),
            )
            gameSoFar = gameSoFar.play(ply.move)
        }
        val accuracy = Side.entries.associateWith { side ->
            val list = moves.filter { it.side == side }.map { it.accuracy }
            if (list.isEmpty()) 0.0 else list.average()
        }
        val counts = Side.entries.associateWith { side ->
            moves.filter { it.side == side }.groupingBy { it.quality }.eachCount()
        }
        val opening = if (game.start.fen() == Position.START_FEN) {
            var best: String? = null
            for (k in 1..game.plies.size) {
                com.zorix.chess.learn.OpeningBook.name(game.plies.take(k).map { it.move })?.let { best = it.name(lang) }
            }
            best
        } else {
            null
        }
        return GameReview(moves, accuracy, counts, curve, opening)
    }

    companion object {
        private const val REVIEW_MS = 250L

        /** Lichess accuracy formula for one move, from win percentages before and after. */
        fun moveAccuracy(best: Score, after: Score): Double {
            val wb = best.winChance() * 100
            val wa = after.winChance() * 100
            val loss = (wb - wa).coerceAtLeast(0.0)
            return (103.1668 * exp(-0.04354 * loss) - 3.1669).coerceIn(0.0, 100.0)
        }
    }
}
