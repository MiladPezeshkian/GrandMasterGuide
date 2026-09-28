package com.zorix.chess.core

import kotlin.math.abs

/** Standard Algebraic Notation (SAN) helpers. */
object Notation {

    /** SAN for a legal [move] in [position], e.g. "Nbd7", "exd6", "e8=N+", "O-O-O#". */
    fun san(position: Position, move: Move): String {
        val piece = position[move.from] ?: return move.uci
        val body = when {
            piece.type == PieceType.KING && abs(Squares.file(move.to) - Squares.file(move.from)) == 2 ->
                if (Squares.file(move.to) > Squares.file(move.from)) "O-O" else "O-O-O"

            piece.type == PieceType.PAWN -> buildString {
                if (position.isCapture(move)) {
                    append('a' + Squares.file(move.from)).append('x')
                }
                append(Squares.name(move.to))
                if (move.promotion != null) append('=').append(move.promotion.letter)
            }

            else -> buildString {
                append(piece.type.letter)
                append(disambiguation(position, move, piece))
                if (position.isCapture(move)) append('x')
                append(Squares.name(move.to))
            }
        }
        val after = position.play(move)
        val suffix = when {
            after.isCheck && after.legalMoves.isEmpty() -> "#"
            after.isCheck -> "+"
            else -> ""
        }
        return body + suffix
    }

    private fun disambiguation(position: Position, move: Move, piece: Piece): String {
        val rivals = position.legalMoves.filter {
            it.to == move.to && it.from != move.from && position[it.from] === piece
        }
        if (rivals.isEmpty()) return ""
        val file = Squares.file(move.from)
        val rank = Squares.rank(move.from)
        val sameFile = rivals.any { Squares.file(it.from) == file }
        val sameRank = rivals.any { Squares.rank(it.from) == rank }
        return when {
            !sameFile -> ('a' + file).toString()
            !sameRank -> ('1' + rank).toString()
            else -> Squares.name(move.from)
        }
    }

    /** Converts a UCI principal variation into SAN, stopping at the first illegal move. */
    fun variationToSan(start: Position, uciMoves: List<String>, maxMoves: Int = Int.MAX_VALUE): List<String> {
        val out = ArrayList<String>()
        var pos = start
        for (text in uciMoves) {
            if (out.size >= maxMoves) break
            val move = Move.fromUci(text) ?: break
            if (!pos.isLegal(move)) break
            out += san(pos, move)
            pos = pos.play(move)
        }
        return out
    }

    /** "1. e4 e5 2. Nf3" style text for a SAN line starting at [start]. */
    fun numbered(start: Position, sans: List<String>): String = buildString {
        var number = start.fullmoveNumber
        var whiteToMove = start.sideToMove == Side.WHITE
        for ((i, san) in sans.withIndex()) {
            if (whiteToMove) {
                if (isNotEmpty()) append(' ')
                append(number).append(". ")
            } else if (i == 0) {
                append(number).append("... ")
            } else {
                append(' ')
            }
            append(san)
            if (!whiteToMove) number++
            whiteToMove = !whiteToMove
        }
    }
}
