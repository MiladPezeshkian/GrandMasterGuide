package com.zorix.chess.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One half-move of the game record. */
data class Ply(val move: Move, val san: String, val before: Position, val after: Position)

enum class GameResult(val pgn: String) { ONGOING("*"), WHITE_WINS("1-0"), BLACK_WINS("0-1"), DRAW("1/2-1/2") }

enum class EndReason { CHECKMATE, STALEMATE, INSUFFICIENT_MATERIAL, FIFTY_MOVES, THREEFOLD_REPETITION }

data class GameStatus(val result: GameResult, val reason: EndReason? = null) {
    val isOver: Boolean get() = result != GameResult.ONGOING
}

/**
 * Immutable game record with undo / redo.
 * `plies` are the moves played to reach [position]; `redo` holds undone plies (top = last element).
 */
class Game private constructor(
    val start: Position,
    val plies: List<Ply>,
    private val redoStack: List<Ply>,
) {
    val position: Position get() = plies.lastOrNull()?.after ?: start
    val lastMove: Move? get() = plies.lastOrNull()?.move
    val canUndo: Boolean get() = plies.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** All plies of the line, including undone ones, for the move list. */
    val fullLine: List<Ply> get() = plies + redoStack.asReversed()

    val status: GameStatus by lazy { computeStatus() }

    fun play(move: Move): Game {
        val pos = position
        require(pos.isLegal(move)) { "Illegal move ${move.uci}" }
        val ply = Ply(move, Notation.san(pos, move), pos, pos.play(move))
        // Replaying the move that was just undone keeps the rest of the redo line.
        val newRedo = if (redoStack.lastOrNull()?.move == move) redoStack.dropLast(1) else emptyList()
        return Game(start, plies + ply, newRedo)
    }

    fun undo(): Game = if (plies.isEmpty()) this else Game(start, plies.dropLast(1), redoStack + plies.last())

    fun redo(): Game = if (redoStack.isEmpty()) this else Game(start, plies + redoStack.last(), redoStack.dropLast(1))

    /** Jumps to the position after [plyCount] plies of [fullLine] (0 = start). */
    fun goTo(plyCount: Int): Game {
        var g = this
        val target = plyCount.coerceIn(0, fullLine.size)
        while (g.plies.size > target) g = g.undo()
        while (g.plies.size < target && g.canRedo) g = g.redo()
        return g
    }

    private fun computeStatus(): GameStatus {
        val pos = position
        if (pos.legalMoves.isEmpty()) {
            return if (pos.isCheck) {
                GameStatus(if (pos.sideToMove == Side.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS, EndReason.CHECKMATE)
            } else {
                GameStatus(GameResult.DRAW, EndReason.STALEMATE)
            }
        }
        if (pos.isInsufficientMaterial()) return GameStatus(GameResult.DRAW, EndReason.INSUFFICIENT_MATERIAL)
        if (pos.halfmoveClock >= 100) return GameStatus(GameResult.DRAW, EndReason.FIFTY_MOVES)
        val key = pos.repetitionKey
        val seen = 1 + (listOf(start) + plies.map { it.after }).dropLast(1).count { it.repetitionKey == key }
        if (seen >= 3) return GameStatus(GameResult.DRAW, EndReason.THREEFOLD_REPETITION)
        return GameStatus(GameResult.ONGOING)
    }

    /** UCI "position" command arguments for the current position. */
    fun uciPosition(): String = buildString {
        if (start.fen() == Position.START_FEN) append("startpos") else append("fen ").append(start.fen())
        if (plies.isNotEmpty()) {
            append(" moves")
            plies.forEach { append(' ').append(it.move.uci) }
        }
    }

    /** Exports the played line (not the undone moves) as PGN. */
    fun toPgn(event: String = "Zorix Chess analysis", date: Date = Date()): String = buildString {
        val result = status.result.pgn
        val tags = linkedMapOf(
            "Event" to event,
            "Site" to "Zorix Chess",
            "Date" to SimpleDateFormat("yyyy.MM.dd", Locale.US).format(date),
            "Round" to "-",
            "White" to "?",
            "Black" to "?",
            "Result" to result,
        )
        if (start.fen() != Position.START_FEN) {
            tags["SetUp"] = "1"
            tags["FEN"] = start.fen()
        }
        tags.forEach { (k, v) -> append('[').append(k).append(" \"").append(v.replace("\"", "'")).append("\"]\n") }
        append('\n')
        val moves = Notation.numbered(start, plies.map { it.san })
        append(wrap(if (moves.isEmpty()) result else "$moves $result"))
        append('\n')
    }

    private fun wrap(text: String, width: Int = 80): String {
        val out = StringBuilder()
        var lineLen = 0
        for (word in text.split(' ')) {
            if (lineLen > 0 && lineLen + 1 + word.length > width) {
                out.append('\n')
                lineLen = 0
            } else if (lineLen > 0) {
                out.append(' ')
                lineLen++
            }
            out.append(word)
            lineLen += word.length
        }
        return out.toString()
    }

    companion object {
        fun new(start: Position = Position.initial()): Game = Game(start, emptyList(), emptyList())

        /** Rebuilds a game from a start FEN and UCI moves (used to restore a saved session). */
        fun restore(startFen: String, moves: List<String>, redo: List<String> = emptyList()): Game {
            var game = new(Position.fromFen(startFen))
            val all = moves + redo
            for (text in all) {
                val move = Move.fromUci(text) ?: break
                if (!game.position.isLegal(move)) break
                game = game.play(move)
            }
            // Step back so the undone moves are available through redo again.
            repeat((game.plies.size - moves.size).coerceAtLeast(0)) { game = game.undo() }
            return game
        }
    }
}
