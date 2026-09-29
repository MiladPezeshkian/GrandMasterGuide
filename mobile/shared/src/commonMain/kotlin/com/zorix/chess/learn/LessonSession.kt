package com.zorix.chess.learn

import com.zorix.chess.coach.Coach
import com.zorix.chess.coach.CoachMessage
import com.zorix.chess.coach.Speakable
import com.zorix.chess.controller.EngineHub
import com.zorix.chess.core.Attacks
import com.zorix.chess.core.Move
import com.zorix.chess.core.Notation
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.core.Squares
import com.zorix.chess.core.Tactics
import com.zorix.chess.engine.uci.SearchLimit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class StepStatus { ACTIVE, WRONG, SOLVED, FAILED }

/** Everything the lesson screen shows for the current step. */
data class StepUi(
    val index: Int,
    val step: Step,
    val position: Position?,
    val flipped: Boolean = false,
    val lastMove: Move? = null,
    val stars: Set<Int> = emptySet(),
    val marks: Set<Int> = emptySet(),
    val arrows: List<Move> = emptyList(),
    val hintSquare: Int? = null,
    val interactive: Boolean = true,
    val status: StepStatus = StepStatus.ACTIVE,
    /** Feedback or explanation for the last action, and the same for the voice. */
    val message: String? = null,
    val speech: String? = null,
    val moves: Int = 0,
    val busy: Boolean = false,
    /** Squares exercise: the square to find, the round and correct answers. */
    val target: Int? = null,
    val round: Int = 0,
    val correct: Int = 0,
    /** Quiz: the chosen option. */
    val chosen: Int? = null,
    val pendingPromotion: Pair<Int, Int>? = null,
)

/** Result of a finished lesson. */
data class LessonResult(val lesson: Lesson, val mistakes: Int, val stars: Int)

/**
 * Runs one lesson: shows its steps in order and checks every answer with exact chess rules
 * (or the engine for play-it-out exercises). Explanations come from the Zorix coach.
 */
class LessonSession(
    val lesson: Lesson,
    private val scope: CoroutineScope,
    private val hub: EngineHub?,
    private val lang: String,
    private val speechLang: String = lang,
    private val random: Random = Random.Default,
) {
    private val _ui = MutableStateFlow(enter(0))
    val ui: StateFlow<StepUi> = _ui.asStateFlow()

    private val _result = MutableStateFlow<LessonResult?>(null)
    val result: StateFlow<LessonResult?> = _result.asStateFlow()

    var mistakes = 0
        private set
    private var job: Job? = null
    /** Puzzle / line progress: index of the next expected move. */
    private var cursor = 0
    private var wrongTries = 0
    private var playMoves = 0
    /** Square of the piece the player moves in star / capture exercises. */
    private var mover: Int? = null

    val stepCount: Int get() = lesson.steps.size

    // ------------------------------------------------------------------ navigation

    fun next() {
        val i = _ui.value.index
        if (i + 1 >= lesson.steps.size) {
            val stars = when {
                mistakes == 0 -> 3
                mistakes <= 2 -> 2
                else -> 1
            }
            _result.value = LessonResult(lesson, mistakes, stars)
            return
        }
        job?.cancel()
        _ui.value = enter(i + 1)
    }

    fun previous() {
        val i = _ui.value.index
        if (i == 0) return
        job?.cancel()
        _ui.value = enter(i - 1)
    }

    /** Starts the current step again. */
    fun retry() {
        job?.cancel()
        _ui.value = enter(_ui.value.index)
    }

    fun close() {
        job?.cancel()
    }

    private fun enter(index: Int): StepUi {
        cursor = 0
        wrongTries = 0
        playMoves = 0
        val step = lesson.steps[index]
        mover = when (step) {
            is Step.Stars -> moverOf(Position.fromFen(step.fen))
            is Step.Capture -> moverOf(Position.fromFen(step.fen))
            else -> null
        }
        return when (step) {
            is Step.Theory -> StepUi(
                index, step, step.fen?.let(::pos), marks = sqs(step.marks),
                arrows = step.arrows.mapNotNull(Move::fromUci), interactive = false, status = StepStatus.SOLVED,
            )
            is Step.Stars -> StepUi(index, step, pos(step.fen), stars = sqs(step.stars))
            is Step.Capture -> StepUi(index, step, pos(step.fen))
            is Step.Puzzle -> {
                val p = pos(step.fen)
                val base = StepUi(index, step, p, flipped = p.sideToMove == Side.BLACK, lastMove = step.last?.let(Move::fromUci))
                if (step.pre != null && step.last != null) {
                    // Show the opponent's last move being played first.
                    job = scope.launch {
                        val pre = pos(step.pre)
                        _ui.update { it.copy(position = pre, lastMove = null, interactive = false) }
                        delay(650)
                        _ui.update { it.copy(position = p, lastMove = Move.fromUci(step.last), interactive = true) }
                    }
                }
                base
            }
            is Step.Mate -> pos(step.fen).let { StepUi(index, step, it, flipped = it.sideToMove == Side.BLACK) }
            is Step.GoalStep -> pos(step.fen).let { StepUi(index, step, it, flipped = it.sideToMove == Side.BLACK) }
            is Step.Quiz -> StepUi(index, step, step.fen?.let(::pos), marks = sqs(step.marks), interactive = false)
            is Step.Squares -> StepUi(index, step, Position.fromFen(EMPTY_FEN), flipped = step.blackView, target = random.nextInt(64), interactive = true)
            is Step.Best -> pos(step.fen).let { StepUi(index, step, it, flipped = it.sideToMove == Side.BLACK) }
            is Step.Line -> {
                val ui = StepUi(index, step, Position.initial(), flipped = !step.playerIsWhite)
                if (!step.playerIsWhite) job = scope.launch { delay(500); autoLine() }
                ui
            }
            is Step.Play -> pos(step.fen).let { StepUi(index, step, it, flipped = it.sideToMove == Side.BLACK) }
        }
    }

    // ------------------------------------------------------------------ input

    fun onMove(from: Int, to: Int) {
        val ui = _ui.value
        val p = ui.position ?: return
        if (!ui.interactive || ui.busy || ui.status == StepStatus.SOLVED) return
        if ((ui.step is Step.Stars || ui.step is Step.Capture) && mover != null && from != mover) return
        if (p.isPromotion(from, to)) {
            _ui.update { it.copy(pendingPromotion = from to to) }
            return
        }
        handle(Move(from, to))
    }

    fun onPromotion(type: PieceType?) {
        val pending = _ui.value.pendingPromotion ?: return
        _ui.update { it.copy(pendingPromotion = null) }
        if (type != null) handle(Move(pending.first, pending.second, type))
    }

    fun onSquare(square: Int) {
        val ui = _ui.value
        val step = ui.step as? Step.Squares ?: return
        if (ui.status == StepStatus.SOLVED || ui.target == null) return
        val right = square == ui.target
        if (!right) mistakes++
        val round = ui.round + 1
        val correct = ui.correct + if (right) 1 else 0
        if (round >= step.count) {
            _ui.update { it.copy(round = round, correct = correct, status = StepStatus.SOLVED, target = null, marks = setOf(square), interactive = false) }
        } else {
            var next = random.nextInt(64)
            while (next == ui.target) next = random.nextInt(64)
            _ui.update { it.copy(round = round, correct = correct, target = next, marks = setOf(square), status = if (right) StepStatus.ACTIVE else StepStatus.WRONG) }
        }
    }

    fun onOption(index: Int) {
        val ui = _ui.value
        val step = ui.step as? Step.Quiz ?: return
        if (ui.status == StepStatus.SOLVED) return
        if (index == step.answer) {
            _ui.update { it.copy(chosen = index, status = StepStatus.SOLVED, message = step.explain[lang].takeIf { t -> t.isNotBlank() }) }
        } else {
            mistakes++
            _ui.update { it.copy(chosen = index, status = StepStatus.WRONG) }
        }
    }

    /** Shows where to start (the piece to move). Counts as a mistake-free hint. */
    fun hint() {
        val ui = _ui.value
        val p = ui.position ?: return
        val move: Move? = when (val step = ui.step) {
            is Step.Puzzle -> step.moves.getOrNull(cursor)?.let(Move::fromUci)
            is Step.Mate -> Tactics.solutions(p, step.n - cursor).firstOrNull()
            is Step.Best -> step.accept.firstOrNull()?.let(Move::fromUci)
            is Step.Line -> step.moves.getOrNull(cursor)?.let(Move::fromUci)
            is Step.GoalStep -> p.legalMoves.firstOrNull { satisfies(step.goal, p, it) }
            else -> null
        }
        _ui.update { it.copy(hintSquare = move?.from) }
    }

    /** Plays the solution of the current puzzle-like step (the step then counts as a mistake). */
    fun showSolution() {
        val ui = _ui.value
        val step = ui.step
        if (ui.status == StepStatus.SOLVED) return
        val p = ui.position ?: return
        mistakes++
        val move = when (step) {
            is Step.Puzzle -> step.moves.getOrNull(cursor)?.let(Move::fromUci)
            is Step.Mate -> Tactics.solutions(p, step.n - cursor).firstOrNull()
            is Step.Best -> step.accept.firstOrNull()?.let(Move::fromUci)
            is Step.Line -> step.moves.getOrNull(cursor)?.let(Move::fromUci)
            is Step.GoalStep -> p.legalMoves.firstOrNull { satisfies(step.goal, p, it) }
            else -> null
        } ?: return
        handle(move)
    }

    private fun handle(move: Move) {
        val ui = _ui.value
        val p = ui.position ?: return
        if (!p.isLegal(move)) return
        when (val step = ui.step) {
            is Step.Stars -> stars(step, p, move)
            is Step.Capture -> capture(p, move)
            is Step.Puzzle -> puzzle(step, p, move)
            is Step.Mate -> mate(step, p, move)
            is Step.GoalStep -> goal(step, p, move)
            is Step.Best -> best(step, p, move)
            is Step.Line -> line(step, p, move)
            is Step.Play -> play(step, p, move)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ step logic

    private fun stars(step: Step.Stars, p: Position, move: Move) {
        mover = move.to
        val after = sandbox(p.play(move), p.sideToMove)
        val left = _ui.value.stars - move.to
        val moves = _ui.value.moves + 1
        if (left.isEmpty()) {
            if (moves > step.par) mistakes++
            _ui.update { it.copy(position = after, lastMove = move, stars = left, moves = moves, status = StepStatus.SOLVED, interactive = false) }
        } else {
            _ui.update { it.copy(position = after, lastMove = move, stars = left, moves = moves, status = StepStatus.ACTIVE, hintSquare = null) }
        }
    }

    private fun capture(p: Position, move: Move) {
        mover = move.to
        val us = p.sideToMove
        val after = sandbox(p.play(move), us)
        val enemies = (0 until 64).count { after[it]?.side == us.opposite }
        val moves = _ui.value.moves + 1
        val step = _ui.value.step as Step.Capture
        if (enemies == 0) {
            if (moves > step.par) mistakes++
            _ui.update { it.copy(position = after, lastMove = move, moves = moves, status = StepStatus.SOLVED, interactive = false) }
        } else {
            _ui.update { it.copy(position = after, lastMove = move, moves = moves, status = StepStatus.ACTIVE) }
        }
    }

    private fun puzzle(step: Step.Puzzle, p: Position, move: Move) {
        val expected = step.moves.getOrNull(cursor)?.let(Move::fromUci) ?: return
        val after = p.play(move)
        val lastStep = cursor >= step.moves.size - 1
        val ok = move == expected || (lastStep && Tactics.isCheckmate(after) && Tactics.isCheckmate(p.play(expected)))
        if (!ok) {
            wrong(p, move)
            return
        }
        cursor++
        if (cursor >= step.moves.size) {
            solved(after, move, Coach.explainSolutionMove(pos(step.fen), Move.fromUci(step.moves[0])!!, step.moves, lang, speechLang))
            return
        }
        // Zorix plays the defender's reply.
        val reply = Move.fromUci(step.moves[cursor])
        _ui.update { it.copy(position = after, lastMove = move, hintSquare = null, status = StepStatus.ACTIVE, interactive = false, message = null) }
        job = scope.launch {
            delay(REPLY_DELAY_MS)
            val cur = _ui.value.position ?: return@launch
            if (reply != null && cur.isLegal(reply)) {
                cursor++
                _ui.update { it.copy(position = cur.play(reply), lastMove = reply, interactive = true) }
            }
        }
    }

    private fun mate(step: Step.Mate, p: Position, move: Move) {
        val remaining = step.n - cursor
        _ui.update { it.copy(busy = true) }
        job = scope.launch {
            val ok = withContext(Dispatchers.Default) { Tactics.forcesMate(p, move, remaining) }
            val after = p.play(move)
            if (!ok) {
                _ui.update { it.copy(busy = false) }
                wrong(p, move)
                return@launch
            }
            if (Tactics.isCheckmate(after)) {
                _ui.update { it.copy(busy = false) }
                solved(after, move, Coach.explainSolutionMove(p, move, listOf(move.uci), lang, speechLang))
                return@launch
            }
            cursor++
            _ui.update { it.copy(position = after, lastMove = move, interactive = false, hintSquare = null, status = StepStatus.ACTIVE) }
            val reply = withContext(Dispatchers.Default) { Tactics.bestDefence(p, move, remaining) }
            delay(REPLY_DELAY_MS)
            if (reply != null) _ui.update { it.copy(position = after.play(reply), lastMove = reply, interactive = true, busy = false) }
        }
    }

    private fun goal(step: Step.GoalStep, p: Position, move: Move) {
        val after = p.play(move)
        if (satisfies(step.goal, p, move)) {
            solved(after, move, Coach.explainSolutionMove(p, move, listOf(move.uci), lang, speechLang))
        } else {
            wrong(p, move)
        }
    }

    private fun best(step: Step.Best, p: Position, move: Move) {
        if (move.uci in step.accept) {
            val own = step.explain[lang].takeIf { it.isNotBlank() }
            val auto = Coach.explainSolutionMove(p, move, listOf(move.uci), lang, speechLang)
            solved(p.play(move), move, if (own != null) CoachMessage(null, own, Speakable.of(step.explain[speechLang], speechLang)) else auto)
        } else {
            wrong(p, move)
        }
    }

    private fun line(step: Step.Line, p: Position, move: Move) {
        val expected = step.moves.getOrNull(cursor)?.let(Move::fromUci) ?: return
        if (move != expected) {
            wrong(p, move)
            if (wrongTries >= 2) _ui.update { it.copy(hintSquare = expected.from) }
            return
        }
        advanceLine(p, move, step)
        if (cursor < step.moves.size) job = scope.launch { delay(REPLY_DELAY_MS); autoLine() }
    }

    private fun advanceLine(p: Position, move: Move, step: Step.Line) {
        val san = Notation.san(p, move)
        val after = p.play(move)
        val note = step.notes[cursor]?.get(lang)
        cursor++
        wrongTries = 0
        val auto = Coach.explainSolutionMove(p, move, listOf(move.uci), lang, speechLang)
        val text = note ?: auto.display.takeIf { it.isNotBlank() }
        val spoken = step.notes[cursor - 1]?.get(speechLang)?.let { Speakable.of(it, speechLang) } ?: auto.speech
        val msg = listOfNotNull("${(cursor + 1) / 2}${if (p.sideToMove == Side.WHITE) "." else "..."} $san", text).joinToString(" — ")
        if (cursor >= step.moves.size) {
            _ui.update { it.copy(position = after, lastMove = move, status = StepStatus.SOLVED, interactive = false, message = msg, speech = spoken, hintSquare = null) }
        } else {
            _ui.update { it.copy(position = after, lastMove = move, message = msg, speech = spoken, hintSquare = null, status = StepStatus.ACTIVE) }
        }
    }

    /** Zorix plays its moves of the opening line. */
    private fun autoLine() {
        val ui = _ui.value
        val step = ui.step as? Step.Line ?: return
        val p = ui.position ?: return
        val playerToMove = (p.sideToMove == Side.WHITE) == step.playerIsWhite
        if (playerToMove || cursor >= step.moves.size) return
        val move = Move.fromUci(step.moves[cursor])?.takeIf { p.isLegal(it) } ?: return
        advanceLine(p, move, step)
    }

    private fun play(step: Step.Play, p: Position, move: Move) {
        val hub = hub ?: return
        val player = p.sideToMove
        val after = p.play(move)
        playMoves++
        _ui.update { it.copy(position = after, lastMove = move, interactive = false, busy = true, hintSquare = null, moves = playMoves, status = StepStatus.ACTIVE, message = null) }
        job = scope.launch {
            // Goal reached with the player's move?
            when {
                Tactics.isCheckmate(after) -> {
                    _ui.update { it.copy(busy = false) }
                    return@launch finishPlay(step.goal == PlayGoal.WIN || step.goal == PlayGoal.DRAW, after, move)
                }
                after.legalMoves.isEmpty() || after.isInsufficientMaterial() -> {
                    _ui.update { it.copy(busy = false) }
                    return@launch finishPlay(step.goal == PlayGoal.DRAW, after, move)
                }
                step.goal == PlayGoal.PROMOTE && move.promotion != null && Attacks.seeCaptureGain(after.pieces(), move.to, player.opposite) <= 0 -> {
                    _ui.update { it.copy(busy = false) }
                    return@launch finishPlay(true, after, move)
                }
            }
            val snap = hub.run { it.search("fen ${after.fen()}", SearchLimit.MoveTime(450), multiPv = 1) }
            val reply = snap?.bestMove?.let(Move::fromUci)?.takeIf { after.isLegal(it) } ?: after.legalMoves.firstOrNull()
            delay(250)
            if (reply == null) return@launch
            val next = after.play(reply)
            val score = snap?.best?.score?.negate() // player's point of view after the reply
            _ui.update { it.copy(position = next, lastMove = reply, interactive = true, busy = false) }
            when {
                Tactics.isCheckmate(next) -> finishPlay(false, next, reply)
                next.legalMoves.isEmpty() || next.isInsufficientMaterial() -> finishPlay(step.goal == PlayGoal.DRAW, next, reply)
                step.goal == PlayGoal.DRAW && score != null && score.winChance() < 0.12 -> finishPlay(false, next, reply)
                step.goal == PlayGoal.DRAW && playMoves >= step.maxMoves -> finishPlay(true, next, reply)
                step.goal != PlayGoal.DRAW && playMoves >= step.maxMoves -> finishPlay(false, next, reply)
                step.goal != PlayGoal.DRAW && score != null && score.winChance() < 0.5 -> finishPlay(false, next, reply)
            }
        }
    }

    private fun finishPlay(success: Boolean, position: Position, move: Move) {
        if (success) {
            _ui.update { it.copy(position = position, lastMove = move, status = StepStatus.SOLVED, interactive = false, busy = false) }
        } else {
            mistakes++
            _ui.update { it.copy(position = position, lastMove = move, status = StepStatus.FAILED, interactive = false, busy = false) }
        }
    }

    private fun solved(after: Position, move: Move, message: CoachMessage?) {
        _ui.update {
            it.copy(
                position = after, lastMove = move, status = StepStatus.SOLVED, interactive = false, hintSquare = null,
                message = message?.display?.takeIf { m -> m.isNotBlank() }, speech = message?.speech, busy = false,
            )
        }
    }

    private fun wrong(p: Position, move: Move) {
        mistakes++
        wrongTries++
        val shown = p.play(move)
        _ui.update { it.copy(position = shown, lastMove = move, status = StepStatus.WRONG, interactive = false) }
        job = scope.launch {
            delay(WRONG_DELAY_MS)
            _ui.update { it.copy(position = p, lastMove = null, status = StepStatus.ACTIVE, interactive = true) }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun pos(fen: String) = Position.fromFen(fen)

    /** Keeps the same side to move in move-around exercises (the other side never moves). */
    private fun sandbox(p: Position, side: Side): Position = Position.of(p.pieces(), side, 0)

    /** The one piece the player moves: the only non-pawn piece, or the only piece at all. */
    private fun moverOf(p: Position): Int? {
        val own = (0 until 64).filter { p[it]?.side == p.sideToMove }
        return own.singleOrNull { p[it]?.type != PieceType.PAWN } ?: own.singleOrNull()
    }

    private fun sqs(names: List<String>): Set<Int> = names.mapNotNull { runCatching { Squares.parse(it) }.getOrNull() }.toSet()

    companion object {
        private const val REPLY_DELAY_MS = 550L
        private const val EMPTY_FEN = "8/8/8/8/8/8/8/8 w - - 0 1"
        private const val WRONG_DELAY_MS = 750L

        /** Exact rule checks for "goal" exercises. */
        fun satisfies(goal: Goal, p: Position, move: Move): Boolean {
            if (!p.isLegal(move)) return false
            val after = p.play(move)
            val mover = p[move.from] ?: return false
            val checkers = if (p.isCheck) Attacks.attackers(p.pieces(), p.kingSquare(p.sideToMove), p.sideToMove.opposite) else emptyList()
            return when (goal) {
                Goal.CHECK -> after.isCheck
                Goal.CHECKMATE -> Tactics.isCheckmate(after)
                Goal.CAPTURE_CHECKER -> p.isCheck && move.to in checkers
                Goal.BLOCK_CHECK -> p.isCheck && mover.type != PieceType.KING && move.to !in checkers
                Goal.KING_ESCAPE -> p.isCheck && mover.type == PieceType.KING
                Goal.ESCAPE_CHECK -> p.isCheck
                Goal.CASTLE_SHORT -> p.isCastling(move) && Squares.file(move.to) == 6
                Goal.CASTLE_LONG -> p.isCastling(move) && Squares.file(move.to) == 2
                Goal.PROMOTE -> move.promotion != null
                Goal.PROMOTE_KNIGHT -> move.promotion == PieceType.KNIGHT
                Goal.EN_PASSANT -> mover.type == PieceType.PAWN && p[move.to] == null && Squares.file(move.from) != Squares.file(move.to)
                Goal.WIN_MATERIAL -> p.isCapture(move) && Attacks.seeMove(p, move) > 0
                Goal.SAVE_PIECE -> {
                    val before = Attacks.hangingPieces(p, p.sideToMove)
                    val nowHanging = Attacks.hangingPieces(after, p.sideToMove)
                    before.isNotEmpty() && nowHanging.isEmpty()
                }
            }
        }
    }
}
