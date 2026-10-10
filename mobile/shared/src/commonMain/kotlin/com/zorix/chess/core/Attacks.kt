package com.zorix.chess.core

/**
 * Attack maps and static exchange evaluation. Used by the coach to explain moves
 * (hanging pieces, forks, pins, threats) and by the lesson graders.
 *
 * Attacks are pseudo-legal: pins are ignored, which is the usual convention for explanations.
 */
object Attacks {

    /** Conventional piece values in centipawns (king = effectively infinite). */
    fun value(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 300
        PieceType.BISHOP -> 310
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 20_000
    }

    private val KNIGHT = arrayOf(intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2), intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2))
    private val KING = arrayOf(intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1), intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1))
    val ROOK_DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
    val BISHOP_DIRS = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))

    /** Directions a sliding piece of [type] moves in (empty for non-sliders). */
    fun slideDirs(type: PieceType): Array<IntArray> = when (type) {
        PieceType.BISHOP -> BISHOP_DIRS
        PieceType.ROOK -> ROOK_DIRS
        PieceType.QUEEN -> ROOK_DIRS + BISHOP_DIRS
        else -> emptyArray()
    }

    /** Squares attacked by the piece standing on [from] of [board]. */
    fun attacksFrom(board: List<Piece?>, from: Int): List<Int> {
        val p = board[from] ?: return emptyList()
        val f0 = Squares.file(from)
        val r0 = Squares.rank(from)
        val out = ArrayList<Int>(16)
        when (p.type) {
            PieceType.PAWN -> {
                val dr = if (p.isWhite) 1 else -1
                for (df in intArrayOf(-1, 1)) if (Squares.isValid(f0 + df, r0 + dr)) out += Squares.of(f0 + df, r0 + dr)
            }
            PieceType.KNIGHT -> for (d in KNIGHT) if (Squares.isValid(f0 + d[0], r0 + d[1])) out += Squares.of(f0 + d[0], r0 + d[1])
            PieceType.KING -> for (d in KING) if (Squares.isValid(f0 + d[0], r0 + d[1])) out += Squares.of(f0 + d[0], r0 + d[1])
            else -> for (d in slideDirs(p.type)) {
                var f = f0 + d[0]
                var r = r0 + d[1]
                while (Squares.isValid(f, r)) {
                    val sq = Squares.of(f, r)
                    out += sq
                    if (board[sq] != null) break
                    f += d[0]
                    r += d[1]
                }
            }
        }
        return out
    }

    fun attacksFrom(pos: Position, from: Int): List<Int> = attacksFrom(pos.pieces(), from)

    /** Squares of [side]'s pieces attacking [target]. */
    fun attackers(board: List<Piece?>, target: Int, side: Side): List<Int> {
        val out = ArrayList<Int>(4)
        for (sq in 0 until 64) {
            val p = board[sq] ?: continue
            if (p.side == side && target in attacksFrom(board, sq)) out += sq
        }
        return out
    }

    fun attackers(pos: Position, target: Int, side: Side): List<Int> = attackers(pos.pieces(), target, side)

    /**
     * Static exchange evaluation: material [side] gains by starting a capture sequence on [target]
     * with its least valuable attacker, both sides always recapturing with their cheapest piece
     * and allowed to stop when continuing would lose. 0 when [side] cannot capture there.
     */
    fun seeCaptureGain(board: List<Piece?>, target: Int, side: Side): Int {
        val victim = board[target] ?: return 0
        if (victim.side == side) return 0
        val b = board.toMutableList()
        val gain = IntArray(40)
        var from = cheapestAttacker(b, target, side) ?: return 0
        if (b[from]!!.type == PieceType.KING && attackers(b, target, side.opposite).isNotEmpty()) return 0
        gain[0] = value(victim.type)
        var onSquare = value(b[from]!!.type)
        b[target] = b[from]
        b[from] = null
        var current = side.opposite
        var d = 0
        while (d < 38) {
            from = cheapestAttacker(b, target, current) ?: break
            // A king may not capture into a square the other side still attacks.
            if (b[from]!!.type == PieceType.KING && attackers(b, target, current.opposite).isNotEmpty()) break
            d++
            gain[d] = onSquare - gain[d - 1]
            onSquare = value(b[from]!!.type)
            b[target] = b[from]
            b[from] = null
            current = current.opposite
        }
        while (d > 0) {
            gain[d - 1] = -maxOf(-gain[d - 1], gain[d])
            d--
        }
        return gain[0]
    }

    private fun cheapestAttacker(board: List<Piece?>, target: Int, side: Side): Int? =
        attackers(board, target, side).minByOrNull { value(board[it]!!.type) }

    /** Material the side to move wins by playing the capture [move] (SEE), negative when it loses material. */
    fun seeMove(pos: Position, move: Move): Int {
        val board = pos.pieces()
        val mover = board[move.from] ?: return 0
        val captured = board[move.to]?.let { value(it.type) }
            ?: if (mover.type == PieceType.PAWN && Squares.file(move.from) != Squares.file(move.to)) 100 else 0
        val promo = move.promotion?.let { value(it) - 100 } ?: 0
        val after = pos.play(move)
        // What the opponent then wins by capturing on the destination square.
        val reply = seeCaptureGain(after.pieces(), move.to, mover.side.opposite)
        return captured + promo - maxOf(0, reply)
    }

    /**
     * Pieces of [side] that the opponent could win right now by capturing them (positive SEE).
     * Returns square -> material the opponent would win.
     */
    fun hangingPieces(pos: Position, side: Side): Map<Int, Int> {
        val board = pos.pieces()
        val out = HashMap<Int, Int>()
        for (sq in 0 until 64) {
            val p = board[sq] ?: continue
            if (p.side != side || p.type == PieceType.KING) continue
            val gain = seeCaptureGain(board, sq, side.opposite)
            if (gain > 0) out[sq] = gain
        }
        return out
    }

    /** True when [square] is defended by a piece of [side] (x-rays ignored). */
    fun isDefended(board: List<Piece?>, square: Int, side: Side): Boolean {
        val b = board.toMutableList()
        val occupant = b[square]
        // Treat the square as holding an enemy piece so our own pieces count as defenders.
        b[square] = occupant?.let { Piece.of(side.opposite, it.type) } ?: Piece.of(side.opposite, PieceType.PAWN)
        return attackers(b, square, side).isNotEmpty()
    }

    /** Material balance in centipawns from White's point of view (kings excluded). */
    fun material(board: List<Piece?>): Int {
        var sum = 0
        for (p in board) {
            if (p == null || p.type == PieceType.KING) continue
            sum += if (p.isWhite) value(p.type) else -value(p.type)
        }
        return sum
    }

    fun material(pos: Position): Int = material(pos.pieces())

    /** The first piece behind [first] on the line from [from] through [first], if any. */
    fun behind(board: List<Piece?>, from: Int, first: Int): Int? {
        val df = sign(Squares.file(first) - Squares.file(from))
        val dr = sign(Squares.rank(first) - Squares.rank(from))
        var f = Squares.file(first) + df
        var r = Squares.rank(first) + dr
        while (Squares.isValid(f, r)) {
            val sq = Squares.of(f, r)
            if (board[sq] != null) return sq
            f += df
            r += dr
        }
        return null
    }

    private fun sign(x: Int): Int = if (x > 0) 1 else if (x < 0) -1 else 0

    /** True when a pawn of [side] on [square] has no enemy pawns in front of it on its own or adjacent files. */
    fun isPassedPawn(board: List<Piece?>, square: Int, side: Side): Boolean {
        val file = Squares.file(square)
        val rank = Squares.rank(square)
        val enemyPawn = Piece.of(side.opposite, PieceType.PAWN)
        for (f in (file - 1)..(file + 1)) {
            if (f !in 0..7) continue
            val ranks = if (side == Side.WHITE) (rank + 1)..7 else 0 until rank
            for (r in ranks) if (board[Squares.of(f, r)] === enemyPawn) return false
        }
        return true
    }

    /** Files with no pawns at all. */
    fun isOpenFile(board: List<Piece?>, file: Int): Boolean =
        (0..7).none { board[Squares.of(file, it)]?.type == PieceType.PAWN }

    /** Files without pawns of [side]. */
    fun isHalfOpenFile(board: List<Piece?>, file: Int, side: Side): Boolean =
        (0..7).none { val p = board[Squares.of(file, it)]; p?.type == PieceType.PAWN && p.side == side }
}

/** The same position with the other side to move (a "null move"), or null when the side to move is in check. */
fun Position.passTurn(): Position? {
    if (isCheck) return null
    val flipped = Position.of(pieces(), sideToMove.opposite, castling)
    return if (flipped.problem() == null) flipped else null
}
