package com.zorix.chess.coach

import com.zorix.chess.core.Attacks
import com.zorix.chess.core.Move
import com.zorix.chess.core.Notation
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.core.Squares
import com.zorix.chess.core.Tactics
import com.zorix.chess.core.passTurn

/** A piece on a square, as mentioned in explanations. */
data class PieceAt(val type: PieceType, val side: Side, val square: Int)

/** Rough size of a material swing, used when no single piece describes it. */
enum class MaterialAmount { PAWN, TWO_PAWNS, MINOR, EXCHANGE, ROOK, QUEEN, DECISIVE }

/**
 * Something true about a move (or a position), found by exact board analysis.
 * Renderers ([Phrases]) turn facts into sentences in every language.
 */
sealed interface Fact {
    // ---------------------------------------------------------------- what a move does
    data object Checkmate : Fact
    data class Check(val double: Boolean, val discovered: Boolean) : Fact
    data class Captures(val piece: PieceAt, val net: Int) : Fact
    data class Trade(val piece: PieceAt) : Fact
    data class Promotes(val type: PieceType) : Fact
    data class Castles(val kingside: Boolean) : Fact
    data class Fork(val attacker: PieceAt, val targets: List<PieceAt>) : Fact
    data class Pin(val attacker: PieceAt, val pinned: PieceAt, val behind: PieceAt) : Fact
    data class Skewer(val attacker: PieceAt, val front: PieceAt, val back: PieceAt) : Fact
    data class DiscoveredAttack(val attacker: PieceAt, val target: PieceAt) : Fact
    data class AttacksUndefended(val attacker: PieceAt, val target: PieceAt) : Fact
    data class ThreatensMate(val mateSan: String) : Fact
    data class SavesPiece(val piece: PieceAt, val moved: Boolean) : Fact
    data class Develops(val piece: PieceAt) : Fact
    data object ControlsCenter : Fact
    data class PushesPassedPawn(val square: Int) : Fact
    data class RookOnOpenFile(val file: Int) : Fact
    data class Sacrifice(val piece: PieceAt) : Fact

    // ---------------------------------------------------------------- what a move neglects
    data class Hangs(val piece: PieceAt) : Fact
    data object KingWalksEarly : Fact
    data object QueenOutEarly : Fact

    // ---------------------------------------------------------------- engine consequences
    /** The opponent can mate in [moves] moves, starting with [replySan]. */
    data class AllowsMate(val moves: Int, val replySan: String) : Fact
    /** After the opponent's best reply the mover loses material. */
    data class LosesMaterial(val replySan: String, val lost: PieceAt?, val amount: MaterialAmount, val how: Fact?) : Fact
    /** The line starting with a move wins material. */
    data class WinsMaterial(val won: PieceAt?, val amount: MaterialAmount) : Fact
    /** A forced mate in [moves] moves. */
    data class ForcedMate(val moves: Int) : Fact
    /** Current dangers for the side to move. */
    data class ThreatCapture(val piece: PieceAt, val bySan: String?) : Fact
    data class ThreatMate(val mateSan: String) : Fact
}

/** Material outcome of a principal variation. */
data class LineOutcome(
    /** Material gained by [mover] in centipawns (negative = lost). */
    val material: Int,
    val gained: List<PieceAt>,
    val lost: List<PieceAt>,
    val mates: Boolean,
)

object Facts {

    private fun at(pos: Position, sq: Int): PieceAt? = pos[sq]?.let { PieceAt(it.type, it.side, sq) }

    /** Everything notable about [move] played in [pos], most important first. */
    fun of(pos: Position, move: Move): List<Fact> {
        if (!pos.isLegal(move)) return emptyList()
        val us = pos.sideToMove
        val them = us.opposite
        val mover = pos[move.from]!!
        val after = pos.play(move)
        val board = after.pieces()
        val before = pos.pieces()
        val to = move.to
        val moved = PieceAt(after[to]!!.type, us, to)
        val out = ArrayList<Fact>()

        if (Tactics.isCheckmate(after)) return listOf(Fact.Checkmate)

        // Check, double and discovered check.
        if (after.isCheck) {
            val checkers = Attacks.attackers(board, after.kingSquare(them), us)
            out += Fact.Check(double = checkers.size >= 2, discovered = to !in checkers)
        }
        move.promotion?.let { out += Fact.Promotes(it) }
        if (pos.isCastling(move)) out += Fact.Castles(kingside = Squares.file(to) > Squares.file(move.from))

        // Captures, judged by static exchange.
        val captured = pos[to]?.let { PieceAt(it.type, them, to) }
            ?: if (mover.type == PieceType.PAWN && Squares.file(move.from) != Squares.file(to)) {
                PieceAt(PieceType.PAWN, them, Squares.of(Squares.file(to), Squares.rank(move.from)))
            } else {
                null
            }
        val see = Attacks.seeMove(pos, move)
        if (captured != null) {
            out += if (see == 0 && Attacks.value(captured.type) == Attacks.value(mover.type)) Fact.Trade(captured) else Fact.Captures(captured, see)
        }

        // Is the moved piece safe on its new square?
        val moverLoss = Attacks.seeCaptureGain(board, to, them)
        val moverSafe = moverLoss <= 0

        // Forks: the moved piece attacks two or more worthwhile targets and cannot simply be taken.
        if (moverSafe || after.isCheck) {
            val targets = Attacks.attacksFrom(board, to).mapNotNull { sq ->
                val t = board[sq] ?: return@mapNotNull null
                if (t.side != them) return@mapNotNull null
                val worth = t.type == PieceType.KING ||
                    Attacks.value(t.type) > Attacks.value(moved.type) ||
                    Attacks.seeCaptureGain(board, sq, us) > 0
                if (worth) PieceAt(t.type, them, sq) else null
            }
            val forkWorks = targets.size >= 2 && (moverSafe || Attacks.seeCaptureGain(board, to, them) <= 0)
            if (forkWorks) out += Fact.Fork(moved, targets.sortedByDescending { Attacks.value(it.type) })
        }

        // Pins and skewers along the moved piece's lines.
        if (moved.type in setOf(PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN) && moverSafe) {
            for (sq in Attacks.attacksFrom(board, to)) {
                val front = board[sq] ?: continue
                if (front.side != them) continue
                val backSq = Attacks.behind(board, to, sq) ?: continue
                val back = board[backSq] ?: continue
                if (back.side != them) continue
                val frontAt = PieceAt(front.type, them, sq)
                val backAt = PieceAt(back.type, them, backSq)
                val fv = Attacks.value(front.type)
                val bv = Attacks.value(back.type)
                if (bv > fv && front.type != PieceType.KING) {
                    out += Fact.Pin(moved, frontAt, backAt)
                } else if (fv > bv && fv > Attacks.value(moved.type) && back.type != PieceType.PAWN) {
                    out += Fact.Skewer(moved, frontAt, backAt)
                }
            }
        }

        // Discovered attacks: a line piece behind the moved piece now hits something.
        for (sq in 0 until 64) {
            val p = board[sq] ?: continue
            if (p.side != us || sq == to) continue
            if (p.type !in setOf(PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN)) continue
            val beforeHits = Attacks.attacksFrom(before, sq).toSet()
            for (hit in Attacks.attacksFrom(board, sq)) {
                if (hit in beforeHits) continue
                val t = board[hit] ?: continue
                if (t.side != them || t.type == PieceType.KING) continue
                if (Attacks.value(t.type) >= Attacks.value(p.type) || Attacks.seeCaptureGain(board, hit, us) > 0) {
                    out += Fact.DiscoveredAttack(PieceAt(p.type, us, sq), PieceAt(t.type, them, hit))
                }
            }
        }

        // New threats against undefended pieces (only when nothing bigger was found).
        if (moverSafe && out.none { it is Fact.Fork || it is Fact.Pin || it is Fact.Skewer }) {
            val threatened = Attacks.attacksFrom(board, to).mapNotNull { sq ->
                val t = board[sq] ?: return@mapNotNull null
                if (t.side != them || t.type == PieceType.KING) return@mapNotNull null
                val nowWins = Attacks.seeCaptureGain(board, sq, us)
                if (nowWins > 0) PieceAt(t.type, them, sq) to nowWins else null
            }.maxByOrNull { it.second }
            if (threatened != null && captured?.square != threatened.first.square) {
                out += Fact.AttacksUndefended(moved, threatened.first)
            }
        }

        // Mate threats: if the opponent did nothing, could we mate next move?
        if (!after.isCheck) {
            after.passTurn()?.let { passed ->
                Tactics.matingMoves(passed).firstOrNull()?.let { out += Fact.ThreatensMate(Notation.san(passed, it)) }
            }
        }

        // Saving pieces that were en prise.
        val hangingBefore = Attacks.hangingPieces(pos, us)
        val hangingAfter = Attacks.hangingPieces(after, us)
        for ((sq, _) in hangingBefore) {
            val p = before[sq]!!
            if (sq == move.from) {
                if (to !in hangingAfter) out += Fact.SavesPiece(PieceAt(p.type, us, sq), moved = true)
            } else if (sq !in hangingAfter && board[sq] === p) {
                out += Fact.SavesPiece(PieceAt(p.type, us, sq), moved = false)
            }
        }

        // Pieces left hanging by this move (the moved piece or others), when not a deliberate trade.
        for ((sq, gain) in hangingAfter) {
            if (sq in hangingBefore.keys && sq != to) continue
            val p = board[sq]!!
            if (sq == to && captured != null && see >= 0) continue
            if (gain >= 100) out += Fact.Hangs(PieceAt(p.type, us, sq))
        }
        if (see < 0 && captured == null && moverLoss > 0) {
            // Offering the moved piece: a sacrifice when it achieves something, otherwise a plain blunder.
            out.removeAll { it is Fact.Hangs && it.piece.square == to }
            out += Fact.Sacrifice(moved)
        }

        // Opening principles and plans.
        val opening = pos.fullmoveNumber <= 12
        val homeRank = if (us == Side.WHITE) 0 else 7
        if (opening && mover.type in setOf(PieceType.KNIGHT, PieceType.BISHOP) && Squares.rank(move.from) == homeRank) {
            out += Fact.Develops(moved)
        }
        if (mover.type == PieceType.PAWN && to in CENTER && pos.fullmoveNumber <= 15) out += Fact.ControlsCenter
        if (mover.type == PieceType.KNIGHT && to in EXTENDED_CENTER && opening && Squares.rank(move.from) != homeRank) out += Fact.ControlsCenter
        if (mover.type == PieceType.PAWN && move.promotion == null && Attacks.isPassedPawn(board, to, us)) {
            val relRank = if (us == Side.WHITE) Squares.rank(to) else 7 - Squares.rank(to)
            if (relRank >= 4) out += Fact.PushesPassedPawn(to)
        }
        if (mover.type == PieceType.ROOK && Squares.file(move.from) != Squares.file(to) &&
            (Attacks.isOpenFile(board, Squares.file(to)) || Attacks.isHalfOpenFile(board, Squares.file(to), us))
        ) {
            out += Fact.RookOnOpenFile(Squares.file(to))
        }
        if (mover.type == PieceType.KING && !pos.isCastling(move) && opening && pos.castling and castleMask(us) != 0 && !pos.isCheck) {
            out += Fact.KingWalksEarly
        }
        if (mover.type == PieceType.QUEEN && pos.fullmoveNumber <= 6 && captured == null && undevelopedMinors(before, us) >= 3) {
            out += Fact.QueenOutEarly
        }

        return out.distinct().sortedBy { rank(it) }
    }

    /** Dangers the side to move must deal with in [pos]: mate threats and pieces en prise. */
    fun threats(pos: Position): List<Fact> {
        val us = pos.sideToMove
        val out = ArrayList<Fact>()
        pos.passTurn()?.let { passed ->
            val mate = Tactics.matingMoves(passed).firstOrNull()
            mate?.let { out += Fact.ThreatMate(Notation.san(passed, it)) }
            val board = pos.pieces()
            Attacks.hangingPieces(pos, us).entries.sortedByDescending { it.value }
                .firstOrNull { mate == null || it.key != mate.to }?.let { (sq, _) ->
                val p = board[sq]!!
                val capture = passed.legalMoves
                    .filter { it.to == sq }
                    .minByOrNull { Attacks.value(passed[it.from]!!.type) }
                out += Fact.ThreatCapture(PieceAt(p.type, us, sq), capture?.let { Notation.san(passed, it) })
            }
        }
        return out
    }

    /**
     * Plays [pv] (UCI moves) from [start] and reports the material result for [mover],
     * settling an unfinished exchange at the end with static exchange evaluation.
     */
    fun outcome(start: Position, pv: List<String>, mover: Side, maxPlies: Int = 8): LineOutcome {
        var pos = start
        val gained = ArrayList<PieceAt>()
        val lost = ArrayList<PieceAt>()
        var plies = 0
        var lastCaptureSquare: Int? = null
        for (text in pv) {
            val m = Move.fromUci(text) ?: break
            if (!pos.isLegal(m)) break
            val wasCapture = pos.isCapture(m)
            if (plies >= maxPlies && !wasCapture && lastCaptureSquare == null) break
            if (plies >= maxPlies + 4) break
            val victim = pos[m.to]?.let { PieceAt(it.type, it.side, m.to) }
                ?: if (wasCapture) PieceAt(PieceType.PAWN, pos.sideToMove.opposite, m.to) else null
            if (victim != null) {
                if (victim.side == mover) lost += victim else gained += victim
            }
            m.promotion?.let {
                val promoted = PieceAt(it, pos.sideToMove, m.to)
                if (pos.sideToMove == mover) gained += promoted else lost += promoted
            }
            pos = pos.play(m)
            plies++
            lastCaptureSquare = if (wasCapture) m.to else null
            if (pos.legalMoves.isEmpty()) break
        }
        var material = Attacks.material(pos) - Attacks.material(start)
        if (mover == Side.BLACK) material = -material
        // An exchange still in progress: whoever is to move can continue capturing on that square.
        lastCaptureSquare?.let { sq ->
            val more = Attacks.seeCaptureGain(pos.pieces(), sq, pos.sideToMove)
            if (more > 0) material += if (pos.sideToMove == mover) more else -more
        }
        return LineOutcome(material, gained, lost, Tactics.isCheckmate(pos))
    }

    /** Words for a material swing, naming the piece when one piece explains it. */
    fun describe(amount: Int, pieces: List<PieceAt>): Pair<PieceAt?, MaterialAmount>? {
        if (amount < 80) return null
        val single = pieces.filter { it.type != PieceType.PAWN || amount < 150 }
            .firstOrNull { kotlin.math.abs(Attacks.value(it.type) - amount) <= 120 }
        val bucket = when {
            amount >= 1200 -> MaterialAmount.DECISIVE
            amount >= 800 -> MaterialAmount.QUEEN
            amount >= 450 -> MaterialAmount.ROOK
            amount in 150..260 && pieces.any { it.type == PieceType.ROOK } -> MaterialAmount.EXCHANGE
            amount >= 250 -> MaterialAmount.MINOR
            amount >= 170 -> MaterialAmount.TWO_PAWNS
            else -> MaterialAmount.PAWN
        }
        return single to bucket
    }

    private fun castleMask(side: Side) = if (side == Side.WHITE) Position.CASTLE_WK or Position.CASTLE_WQ else Position.CASTLE_BK or Position.CASTLE_BQ

    private fun undevelopedMinors(board: List<com.zorix.chess.core.Piece?>, side: Side): Int {
        val rank = if (side == Side.WHITE) 0 else 7
        return (0..7).count {
            val p = board[Squares.of(it, rank)]
            p != null && p.side == side && (p.type == PieceType.KNIGHT || p.type == PieceType.BISHOP)
        }
    }

    private val CENTER = setOf(Squares.parse("d4"), Squares.parse("e4"), Squares.parse("d5"), Squares.parse("e5"))
    private val EXTENDED_CENTER = setOf(
        Squares.parse("c3"), Squares.parse("f3"), Squares.parse("c6"), Squares.parse("f6"),
        Squares.parse("d4"), Squares.parse("e4"), Squares.parse("d5"), Squares.parse("e5"),
    )

    /** Importance order used to pick what to say first. */
    fun rank(f: Fact): Int = when (f) {
        Fact.Checkmate -> 0
        is Fact.ForcedMate -> 1
        is Fact.AllowsMate -> 2
        is Fact.Promotes -> 3
        is Fact.Fork -> 4
        is Fact.Skewer -> 5
        is Fact.Pin -> 6
        is Fact.DiscoveredAttack -> 7
        is Fact.Sacrifice -> 8
        is Fact.Captures -> if (f.net > 0) 9 else 16
        is Fact.ThreatensMate -> 10
        is Fact.WinsMaterial -> 11
        is Fact.LosesMaterial -> 12
        is Fact.Hangs -> 13
        is Fact.Check -> 14
        is Fact.AttacksUndefended -> 15
        is Fact.SavesPiece -> 17
        is Fact.Castles -> 18
        is Fact.Trade -> 19
        is Fact.Develops -> 20
        Fact.ControlsCenter -> 21
        is Fact.PushesPassedPawn -> 22
        is Fact.RookOnOpenFile -> 23
        Fact.KingWalksEarly -> 24
        Fact.QueenOutEarly -> 25
        is Fact.ThreatMate -> 26
        is Fact.ThreatCapture -> 27
    }
}
