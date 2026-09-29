package com.zorix.chess.controller

import com.zorix.chess.coach.Coach
import com.zorix.chess.core.Move
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.core.Tactics
import com.zorix.chess.learn.RatedPuzzle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class PuzzleMode { RATED, STREAK }

enum class PuzzleOutcome { SOLVING, CORRECT_STEP, WRONG, SOLVED, FAILED }

data class PuzzleState(
    val puzzle: RatedPuzzle? = null,
    val position: Position? = null,
    val lastMove: Move? = null,
    val flipped: Boolean = false,
    val outcome: PuzzleOutcome = PuzzleOutcome.SOLVING,
    val interactive: Boolean = false,
    val hintSquare: Int? = null,
    val explanation: String? = null,
    val ratingChange: Int? = null,
    val mode: PuzzleMode = PuzzleMode.RATED,
    val streak: Int = 0,
    /** Theme filter ("fork", "pin"...), or null for all. */
    val theme: String? = null,
    val pendingPromotion: Pair<Int, Int>? = null,
    val loaded: Boolean = false,
)

/**
 * The puzzle trainer: rated puzzles chosen near the player's puzzle rating (Elo-rated like chess.com),
 * a streak mode that gets harder with every solved puzzle, and theme training.
 */
class PuzzleTrainer(
    private val scope: CoroutineScope,
    private val profile: ProfileStore,
    private val store: KeyValueStore,
    private val random: Random = Random.Default,
) {
    private val _state = MutableStateFlow(PuzzleState())
    val state: StateFlow<PuzzleState> = _state.asStateFlow()

    var language: String = "en"
    private var bank: List<RatedPuzzle> = emptyList()
    private val recent = ArrayDeque<Int>()
    private var cursor = 0
    private var failedOnce = false
    private var job: Job? = null

    val themes: List<String> get() = bank.flatMap { it.themes }.groupingBy { it }.eachCount()
        .filter { it.value >= 8 && it.key in TRAINABLE }.keys.sortedBy { TRAINABLE.indexOf(it) }

    fun load(puzzles: List<RatedPuzzle>) {
        if (bank.isNotEmpty() || puzzles.isEmpty()) return
        bank = puzzles.filter { it.moves.isNotEmpty() }
        store.getString(KEY_RECENT).orEmpty().split(',').mapNotNull { it.toIntOrNull() }.forEach { recent.addLast(it) }
        _state.update { it.copy(loaded = true) }
    }

    fun start(mode: PuzzleMode, theme: String? = null) {
        _state.update { it.copy(mode = mode, theme = theme, streak = 0) }
        next()
    }

    fun next() {
        if (bank.isEmpty()) return
        val st = _state.value
        val target = when (st.mode) {
            PuzzleMode.RATED -> profile.current.puzzleRating
            PuzzleMode.STREAK -> 450 + st.streak * 70
        }
        val pool = bank.filter { (st.theme == null || st.theme in it.themes) && it.id !in recent }
            .ifEmpty { bank.filter { st.theme == null || st.theme in it.themes } }
        val near = pool.sortedBy { kotlin.math.abs(it.rating - target) }.take(12)
        val p = near.randomOrNull(random) ?: return
        remember(p.id)
        show(p)
    }

    private fun show(p: RatedPuzzle) {
        job?.cancel()
        cursor = 0
        failedOnce = false
        val start = Position.fromFen(p.fen)
        val pre = p.pre?.let { Position.fromFen(it) }
        _state.update {
            it.copy(
                puzzle = p, position = pre ?: start, lastMove = null, flipped = start.sideToMove == Side.BLACK,
                outcome = PuzzleOutcome.SOLVING, interactive = pre == null, hintSquare = null, explanation = null, ratingChange = null,
            )
        }
        if (pre != null && p.last != null) {
            job = scope.launch {
                delay(600)
                _state.update { it.copy(position = start, lastMove = Move.fromUci(p.last), interactive = true) }
            }
        }
    }

    fun onMove(from: Int, to: Int) {
        val st = _state.value
        val pos = st.position ?: return
        if (!st.interactive) return
        if (pos.isPromotion(from, to)) {
            _state.update { it.copy(pendingPromotion = from to to) }
            return
        }
        handle(Move(from, to))
    }

    fun onPromotion(type: PieceType?) {
        val p = _state.value.pendingPromotion ?: return
        _state.update { it.copy(pendingPromotion = null) }
        if (type != null) handle(Move(p.first, p.second, type))
    }

    private fun handle(move: Move) {
        val st = _state.value
        val p = st.puzzle ?: return
        val pos = st.position ?: return
        if (!pos.isLegal(move)) return
        val expected = Move.fromUci(p.moves[cursor]) ?: return
        val after = pos.play(move)
        val last = cursor >= p.moves.size - 1
        val ok = move == expected || (last && Tactics.isCheckmate(after))
        if (!ok) {
            fail(pos, move)
            return
        }
        cursor++
        if (cursor >= p.moves.size) {
            solved(after, move)
            return
        }
        val reply = Move.fromUci(p.moves[cursor])
        _state.update { it.copy(position = after, lastMove = move, outcome = PuzzleOutcome.CORRECT_STEP, interactive = false, hintSquare = null) }
        job = scope.launch {
            delay(500)
            if (reply != null && after.isLegal(reply)) {
                cursor++
                _state.update { it.copy(position = after.play(reply), lastMove = reply, interactive = true, outcome = PuzzleOutcome.SOLVING) }
            }
        }
    }

    private fun solved(after: Position, move: Move) {
        val st = _state.value
        val p = st.puzzle ?: return
        val start = Position.fromFen(p.fen)
        val explanation = Coach.explainSolutionMove(start, Move.fromUci(p.moves[0])!!, p.moves, language, speechLanguage(language)).display
        val change = if (st.mode == PuzzleMode.RATED && !failedOnce) profile.recordPuzzle(p.rating, solved = true) else null
        _state.update {
            it.copy(position = after, lastMove = move, outcome = PuzzleOutcome.SOLVED, interactive = false, explanation = explanation,
                ratingChange = change, streak = if (it.mode == PuzzleMode.STREAK) it.streak + 1 else it.streak)
        }
    }

    private fun fail(pos: Position, move: Move) {
        val st = _state.value
        val p = st.puzzle ?: return
        val change = if (!failedOnce && st.mode == PuzzleMode.RATED) profile.recordPuzzle(p.rating, solved = false) else null
        failedOnce = true
        _state.update { it.copy(position = pos.play(move), lastMove = move, outcome = PuzzleOutcome.WRONG, interactive = false, ratingChange = change ?: it.ratingChange) }
        job = scope.launch {
            delay(800)
            if (_state.value.mode == PuzzleMode.STREAK) {
                _state.update { it.copy(outcome = PuzzleOutcome.FAILED, position = pos, lastMove = null) }
            } else {
                _state.update { it.copy(position = pos, lastMove = null, outcome = PuzzleOutcome.SOLVING, interactive = true) }
            }
        }
    }

    fun hint() {
        val st = _state.value
        val p = st.puzzle ?: return
        failedOnce = true
        _state.update { it.copy(hintSquare = Move.fromUci(p.moves.getOrNull(cursor) ?: return)?.from) }
    }

    /** Plays the rest of the solution (counts as not solved). */
    fun showSolution() {
        val st = _state.value
        val p = st.puzzle ?: return
        if (st.outcome == PuzzleOutcome.SOLVED) return
        if (!failedOnce && st.mode == PuzzleMode.RATED) {
            val change = profile.recordPuzzle(p.rating, solved = false)
            _state.update { it.copy(ratingChange = change) }
        }
        failedOnce = true
        job?.cancel()
        job = scope.launch {
            var pos = _state.value.position ?: return@launch
            _state.update { it.copy(interactive = false) }
            while (cursor < p.moves.size) {
                val m = Move.fromUci(p.moves[cursor]) ?: break
                if (!pos.isLegal(m)) break
                pos = pos.play(m)
                cursor++
                _state.update { it.copy(position = pos, lastMove = m) }
                delay(650)
            }
            val start = Position.fromFen(p.fen)
            val explanation = Coach.explainSolutionMove(start, Move.fromUci(p.moves[0])!!, p.moves, language, speechLanguage(language)).display
            _state.update { it.copy(outcome = if (it.mode == PuzzleMode.STREAK) PuzzleOutcome.FAILED else PuzzleOutcome.SOLVED, explanation = explanation) }
        }
    }

    private fun remember(id: Int) {
        recent.addLast(id)
        while (recent.size > 300) recent.removeFirst()
        store.putString(KEY_RECENT, recent.joinToString(","))
    }

    companion object {
        private const val KEY_RECENT = "puzzles.recent"
        val TRAINABLE = listOf(
            "mate", "mateIn1", "mateIn2", "fork", "pin", "skewer", "discoveredAttack", "doubleCheck", "hangingPiece",
            "capturingDefender", "deflection", "attraction", "sacrifice", "promotion", "quietMove", "endgame", "opening",
        )
    }
}
