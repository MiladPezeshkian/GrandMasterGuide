package com.zorix.chess.core

/** Exact, engine-free checks for short forced mates (used to grade puzzle answers offline). */
object Tactics {

    fun isCheckmate(position: Position): Boolean = position.isCheck && position.legalMoves.isEmpty()

    /** Moves that mate immediately. */
    fun matingMoves(position: Position): List<Move> =
        position.legalMoves.filter { isCheckmate(position.play(it)) }

    /**
     * True if [move] forces mate within [depth] of the mover's moves (depth 1 = mate in one).
     * Every defence is considered, so any correct solution is accepted, not only the "book" one.
     */
    fun forcesMate(position: Position, move: Move, depth: Int): Boolean {
        if (!position.isLegal(move)) return false
        val after = position.play(move)
        if (isCheckmate(after)) return true
        if (depth <= 1 || after.legalMoves.isEmpty()) return false
        return after.legalMoves.all { reply ->
            val next = after.play(reply)
            next.legalMoves.any { forcesMate(next, it, depth - 1) }
        }
    }

    /** First moves that force mate in [depth]. */
    fun solutions(position: Position, depth: Int): List<Move> =
        position.legalMoves.filter { forcesMate(position, it, depth) }

    /** The toughest defence after [move]: the reply that leaves the fewest winning continuations. */
    fun bestDefence(position: Position, move: Move, depth: Int): Move? {
        val after = position.play(move)
        return after.legalMoves.minByOrNull { reply ->
            val next = after.play(reply)
            next.legalMoves.count { forcesMate(next, it, depth - 1) }
        }
    }
}
