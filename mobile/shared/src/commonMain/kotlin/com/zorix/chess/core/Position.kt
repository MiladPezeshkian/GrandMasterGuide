package com.zorix.chess.core

/**
 * An immutable chess position with full legal move generation
 * (castling, en passant, under-promotion) and FEN import/export.
 */
class Position private constructor(
    private val board: Array<Piece?>,
    val sideToMove: Side,
    /** Bit mask of [CASTLE_WK], [CASTLE_WQ], [CASTLE_BK], [CASTLE_BQ]. */
    val castling: Int,
    /** Square a pawn skipped over on the previous double push, or [Squares.NONE]. */
    private val epTarget: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {

    operator fun get(square: Int): Piece? = board[square]

    /** Snapshot of the 64 squares (a1 first). */
    fun pieces(): List<Piece?> = board.toList()

    fun kingSquare(side: Side): Int {
        val king = Piece.of(side, PieceType.KING)
        for (sq in 0 until 64) if (board[sq] === king) return sq
        return Squares.NONE
    }

    val isCheck: Boolean by lazy { isKingAttacked(sideToMove) }

    val legalMoves: List<Move> by lazy { generateLegalMoves() }

    fun isLegal(move: Move): Boolean = move in legalMoves

    /** Legal moves of the piece standing on [from]. */
    fun legalMovesFrom(from: Int): List<Move> = legalMoves.filter { it.from == from }

    /** True if moving from [from] to [to] requires choosing a promotion piece. */
    fun isPromotion(from: Int, to: Int): Boolean =
        legalMoves.any { it.from == from && it.to == to && it.promotion != null }

    fun isCapture(move: Move): Boolean {
        val mover = board[move.from] ?: return false
        return board[move.to] != null ||
            (mover.type == PieceType.PAWN && move.to == epTarget && Squares.file(move.from) != Squares.file(move.to))
    }

    fun isCastling(move: Move): Boolean {
        val mover = board[move.from] ?: return false
        return mover.type == PieceType.KING && kotlin.math.abs(Squares.file(move.to) - Squares.file(move.from)) == 2
    }

    /** Square attacked by any piece of [by]. */
    fun isAttacked(square: Int, by: Side): Boolean {
        val file = Squares.file(square)
        val rank = Squares.rank(square)

        // Pawns: a white pawn attacks diagonally upwards, so it sits one rank below the target.
        val pawnRank = if (by == Side.WHITE) rank - 1 else rank + 1
        val pawn = Piece.of(by, PieceType.PAWN)
        for (df in intArrayOf(-1, 1)) {
            val f = file + df
            if (Squares.isValid(f, pawnRank) && board[Squares.of(f, pawnRank)] === pawn) return true
        }

        val knight = Piece.of(by, PieceType.KNIGHT)
        for (d in KNIGHT_STEPS) {
            val f = file + d[0]
            val r = rank + d[1]
            if (Squares.isValid(f, r) && board[Squares.of(f, r)] === knight) return true
        }

        val king = Piece.of(by, PieceType.KING)
        for (d in KING_STEPS) {
            val f = file + d[0]
            val r = rank + d[1]
            if (Squares.isValid(f, r) && board[Squares.of(f, r)] === king) return true
        }

        val queen = Piece.of(by, PieceType.QUEEN)
        val rook = Piece.of(by, PieceType.ROOK)
        val bishop = Piece.of(by, PieceType.BISHOP)
        if (slidingAttack(file, rank, ROOK_DIRS, rook, queen)) return true
        if (slidingAttack(file, rank, BISHOP_DIRS, bishop, queen)) return true
        return false
    }

    private fun slidingAttack(file: Int, rank: Int, dirs: Array<IntArray>, a: Piece, b: Piece): Boolean {
        for (d in dirs) {
            var f = file + d[0]
            var r = rank + d[1]
            while (Squares.isValid(f, r)) {
                val p = board[Squares.of(f, r)]
                if (p != null) {
                    if (p === a || p === b) return true
                    break
                }
                f += d[0]
                r += d[1]
            }
        }
        return false
    }

    private fun isKingAttacked(side: Side): Boolean {
        val k = kingSquare(side)
        return k != Squares.NONE && isAttacked(k, side.opposite)
    }

    private fun generateLegalMoves(): List<Move> {
        val pseudo = ArrayList<Move>(64)
        generatePseudoLegal(pseudo)
        return pseudo.filter { !play(it, validate = false).isKingAttacked(sideToMove) }
    }

    private fun generatePseudoLegal(out: MutableList<Move>) {
        val us = sideToMove
        for (from in 0 until 64) {
            val p = board[from] ?: continue
            if (p.side != us) continue
            val file = Squares.file(from)
            val rank = Squares.rank(from)
            when (p.type) {
                PieceType.PAWN -> genPawn(from, file, rank, us, out)
                PieceType.KNIGHT -> genSteps(from, file, rank, KNIGHT_STEPS, us, out)
                PieceType.BISHOP -> genSlides(from, file, rank, BISHOP_DIRS, us, out)
                PieceType.ROOK -> genSlides(from, file, rank, ROOK_DIRS, us, out)
                PieceType.QUEEN -> {
                    genSlides(from, file, rank, ROOK_DIRS, us, out)
                    genSlides(from, file, rank, BISHOP_DIRS, us, out)
                }
                PieceType.KING -> {
                    genSteps(from, file, rank, KING_STEPS, us, out)
                    genCastling(from, us, out)
                }
            }
        }
    }

    private fun genPawn(from: Int, file: Int, rank: Int, us: Side, out: MutableList<Move>) {
        val dir = if (us == Side.WHITE) 1 else -1
        val startRank = if (us == Side.WHITE) 1 else 6
        val lastRank = if (us == Side.WHITE) 7 else 0
        val r1 = rank + dir
        if (r1 !in 0..7) return

        fun add(to: Int) {
            if (Squares.rank(to) == lastRank) {
                for (promo in PieceType.PROMOTIONS) out += Move(from, to, promo)
            } else {
                out += Move(from, to)
            }
        }

        val one = Squares.of(file, r1)
        if (board[one] == null) {
            add(one)
            if (rank == startRank) {
                val two = Squares.of(file, rank + 2 * dir)
                if (board[two] == null) out += Move(from, two)
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val f = file + df
            if (f !in 0..7) continue
            val to = Squares.of(f, r1)
            val target = board[to]
            if (target != null && target.side != us) add(to)
            else if (target == null && to == epTarget) out += Move(from, to)
        }
    }

    private fun genSteps(from: Int, file: Int, rank: Int, steps: Array<IntArray>, us: Side, out: MutableList<Move>) {
        for (d in steps) {
            val f = file + d[0]
            val r = rank + d[1]
            if (!Squares.isValid(f, r)) continue
            val to = Squares.of(f, r)
            val target = board[to]
            if (target == null || target.side != us) out += Move(from, to)
        }
    }

    private fun genSlides(from: Int, file: Int, rank: Int, dirs: Array<IntArray>, us: Side, out: MutableList<Move>) {
        for (d in dirs) {
            var f = file + d[0]
            var r = rank + d[1]
            while (Squares.isValid(f, r)) {
                val to = Squares.of(f, r)
                val target = board[to]
                if (target == null) {
                    out += Move(from, to)
                } else {
                    if (target.side != us) out += Move(from, to)
                    break
                }
                f += d[0]
                r += d[1]
            }
        }
    }

    private fun genCastling(from: Int, us: Side, out: MutableList<Move>) {
        val home = if (us == Side.WHITE) 4 else 60
        if (from != home) return
        val rook = Piece.of(us, PieceType.ROOK)
        val them = us.opposite
        val kingSide = if (us == Side.WHITE) CASTLE_WK else CASTLE_BK
        val queenSide = if (us == Side.WHITE) CASTLE_WQ else CASTLE_BQ
        if (castling and (kingSide or queenSide) == 0) return
        if (isAttacked(home, them)) return

        if (castling and kingSide != 0 && board[home + 3] === rook &&
            board[home + 1] == null && board[home + 2] == null &&
            !isAttacked(home + 1, them) && !isAttacked(home + 2, them)
        ) {
            out += Move(home, home + 2)
        }
        if (castling and queenSide != 0 && board[home - 4] === rook &&
            board[home - 1] == null && board[home - 2] == null && board[home - 3] == null &&
            !isAttacked(home - 1, them) && !isAttacked(home - 2, them)
        ) {
            out += Move(home, home - 2)
        }
    }

    /**
     * Returns the position after [move].
     * @throws IllegalArgumentException if the move is not legal here.
     */
    fun play(move: Move): Position = play(move, validate = true)

    private fun play(move: Move, validate: Boolean): Position {
        if (validate) require(isLegal(move)) { "Illegal move ${move.uci} in ${fen()}" }
        val b = board.copyOf()
        val mover = b[move.from] ?: throw IllegalArgumentException("No piece on ${Squares.name(move.from)}")
        val captured = b[move.to]
        var newEp = Squares.NONE
        var halfmove = halfmoveClock + 1

        b[move.from] = null
        b[move.to] = if (move.promotion != null) Piece.of(mover.side, move.promotion) else mover

        when (mover.type) {
            PieceType.PAWN -> {
                halfmove = 0
                val fileDelta = Squares.file(move.to) - Squares.file(move.from)
                val rankDelta = Squares.rank(move.to) - Squares.rank(move.from)
                if (fileDelta != 0 && captured == null && move.to == epTarget) {
                    // En passant: the captured pawn sits behind the target square.
                    b[Squares.of(Squares.file(move.to), Squares.rank(move.from))] = null
                }
                if (rankDelta == 2 || rankDelta == -2) {
                    newEp = Squares.of(Squares.file(move.from), Squares.rank(move.from) + rankDelta / 2)
                }
            }
            PieceType.KING -> {
                val fileDelta = Squares.file(move.to) - Squares.file(move.from)
                if (fileDelta == 2) { // O-O
                    b[move.from + 1] = b[move.from + 3]
                    b[move.from + 3] = null
                } else if (fileDelta == -2) { // O-O-O
                    b[move.from - 1] = b[move.from - 4]
                    b[move.from - 4] = null
                }
            }
            else -> Unit
        }
        if (captured != null) halfmove = 0

        val newCastling = castling and CASTLE_KEEP[move.from] and CASTLE_KEEP[move.to]
        return Position(
            board = b,
            sideToMove = sideToMove.opposite,
            castling = newCastling,
            epTarget = newEp,
            halfmoveClock = halfmove,
            fullmoveNumber = if (sideToMove == Side.BLACK) fullmoveNumber + 1 else fullmoveNumber,
        )
    }

    /** En passant square only when an en passant capture is actually legal (as Stockfish and python-chess do). */
    private val legalEpSquare: Int by lazy {
        if (epTarget == Squares.NONE) {
            Squares.NONE
        } else {
            val found = legalMoves.any {
                it.to == epTarget && board[it.from]?.type == PieceType.PAWN &&
                    Squares.file(it.from) != Squares.file(it.to)
            }
            if (found) epTarget else Squares.NONE
        }
    }

    fun fen(): String = fenText

    private val fenText: String by lazy {
        buildFen()
    }

    private fun buildFen(): String = buildString {
        append(placementFen())
        append(if (sideToMove == Side.WHITE) " w " else " b ")
        append(castlingFen())
        append(' ')
        append(if (legalEpSquare == Squares.NONE) "-" else Squares.name(legalEpSquare))
        append(' ').append(halfmoveClock)
        append(' ').append(fullmoveNumber)
    }

    /** Identity used for threefold repetition. */
    val repetitionKey: String by lazy {
        "${placementFen()} ${if (sideToMove == Side.WHITE) 'w' else 'b'} ${castlingFen()} $legalEpSquare"
    }

    private fun placementFen(): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = board[Squares.of(file, rank)]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        append(empty)
                        empty = 0
                    }
                    append(p.fenChar)
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
    }

    private fun castlingFen(): String {
        if (castling == 0) return "-"
        return buildString {
            if (castling and CASTLE_WK != 0) append('K')
            if (castling and CASTLE_WQ != 0) append('Q')
            if (castling and CASTLE_BK != 0) append('k')
            if (castling and CASTLE_BQ != 0) append('q')
        }
    }

    /** Neither side can possibly deliver mate (K v K, K+minor v K, K+B v K+B same colour). */
    fun isInsufficientMaterial(): Boolean {
        var minors = 0
        val bishopColors = HashSet<Boolean>()
        var bishops = 0
        var knights = 0
        for (sq in 0 until 64) {
            val p = board[sq] ?: continue
            when (p.type) {
                PieceType.KING -> Unit
                PieceType.KNIGHT -> { minors++; knights++ }
                PieceType.BISHOP -> { minors++; bishops++; bishopColors += Squares.isLight(sq) }
                else -> return false
            }
        }
        if (minors <= 1) return true
        return knights == 0 && bishopColors.size == 1 && bishops == minors
    }

    /** Why the engine could not safely analyse this position, or null when it is valid. */
    fun problem(): PositionProblem? {
        val whiteKings = board.count { it === Piece.of(Side.WHITE, PieceType.KING) }
        val blackKings = board.count { it === Piece.of(Side.BLACK, PieceType.KING) }
        if (whiteKings == 0 || blackKings == 0) return PositionProblem.MISSING_KING
        if (whiteKings > 1 || blackKings > 1) return PositionProblem.TOO_MANY_KINGS
        for (file in 0..7) {
            if (board[Squares.of(file, 0)]?.type == PieceType.PAWN || board[Squares.of(file, 7)]?.type == PieceType.PAWN) {
                return PositionProblem.PAWN_ON_BACK_RANK
            }
        }
        for (side in Side.entries) {
            if (board.count { it != null && it.side == side && it.type == PieceType.PAWN } > 8) return PositionProblem.TOO_MANY_PIECES
            if (board.count { it?.side == side } > 16) return PositionProblem.TOO_MANY_PIECES
        }
        if (isKingAttacked(sideToMove.opposite)) return PositionProblem.OPPONENT_IN_CHECK
        return null
    }

    override fun equals(other: Any?): Boolean = other is Position && other.fen() == fen()
    override fun hashCode(): Int = fen().hashCode()
    override fun toString(): String = fen()

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        const val CASTLE_WK = 1
        const val CASTLE_WQ = 2
        const val CASTLE_BK = 4
        const val CASTLE_BQ = 8

        private val KNIGHT_STEPS = arrayOf(
            intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2),
            intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2),
        )
        private val KING_STEPS = arrayOf(
            intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1),
            intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
        )
        private val ROOK_DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        private val BISHOP_DIRS = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))

        /** Castling rights that survive a move touching the given square. */
        private val CASTLE_KEEP = IntArray(64) { 0xF }.also {
            it[0] = 0xF and CASTLE_WQ.inv()
            it[7] = 0xF and CASTLE_WK.inv()
            it[4] = 0xF and (CASTLE_WK or CASTLE_WQ).inv()
            it[56] = 0xF and CASTLE_BQ.inv()
            it[63] = 0xF and CASTLE_BK.inv()
            it[60] = 0xF and (CASTLE_BK or CASTLE_BQ).inv()
        }

        fun initial(): Position = fromFen(START_FEN)

        /** Builds a position from explicit squares (used by the board editor). */
        fun of(
            pieces: List<Piece?>,
            sideToMove: Side,
            castling: Int,
            epSquare: Int = Squares.NONE,
            halfmoveClock: Int = 0,
            fullmoveNumber: Int = 1,
        ): Position {
            require(pieces.size == 64)
            val board = pieces.toTypedArray()
            return Position(board, sideToMove, sanitizeCastling(board, castling), epSquare, halfmoveClock, fullmoveNumber)
        }

        /**
         * Parses a FEN string. Missing trailing fields default to "w - - 0 1".
         * @throws IllegalArgumentException for malformed FEN.
         */
        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.isNotEmpty() && parts[0].isNotEmpty()) { "Empty FEN" }
            val board = arrayOfNulls<Piece>(64)
            val rows = parts[0].split('/')
            require(rows.size == 8) { "FEN must have 8 ranks" }
            for ((i, row) in rows.withIndex()) {
                val rank = 7 - i
                var file = 0
                for (c in row) {
                    if (c.isDigit()) {
                        file += c - '0'
                    } else {
                        val p = Piece.fromFenChar(c) ?: throw IllegalArgumentException("Bad piece '$c'")
                        require(file < 8) { "Rank ${rank + 1} is too long" }
                        board[Squares.of(file, rank)] = p
                        file++
                    }
                    require(file <= 8) { "Rank ${rank + 1} is too long" }
                }
                require(file == 8) { "Rank ${rank + 1} is too short" }
            }
            val side = when (parts.getOrElse(1) { "w" }) {
                "w" -> Side.WHITE
                "b" -> Side.BLACK
                else -> throw IllegalArgumentException("Bad side to move")
            }
            var castling = 0
            val castleText = parts.getOrElse(2) { "-" }
            if (castleText != "-") {
                for (c in castleText) {
                    castling = castling or when (c) {
                        'K' -> CASTLE_WK
                        'Q' -> CASTLE_WQ
                        'k' -> CASTLE_BK
                        'q' -> CASTLE_BQ
                        else -> throw IllegalArgumentException("Bad castling field")
                    }
                }
            }
            val epText = parts.getOrElse(3) { "-" }
            val ep = if (epText == "-") Squares.NONE else Squares.parse(epText)
            val half = parts.getOrElse(4) { "0" }.toIntOrNull() ?: throw IllegalArgumentException("Bad halfmove clock")
            val full = parts.getOrElse(5) { "1" }.toIntOrNull() ?: throw IllegalArgumentException("Bad move number")
            return Position(board, side, sanitizeCastling(board, castling), ep, half.coerceAtLeast(0), full.coerceAtLeast(1))
        }

        /** Drops castling rights whose king or rook is not on its original square. */
        private fun sanitizeCastling(board: Array<Piece?>, castling: Int): Int {
            var c = castling
            val wk = Piece.of(Side.WHITE, PieceType.KING)
            val bk = Piece.of(Side.BLACK, PieceType.KING)
            val wr = Piece.of(Side.WHITE, PieceType.ROOK)
            val br = Piece.of(Side.BLACK, PieceType.ROOK)
            if (board[4] !== wk) c = c and (CASTLE_WK or CASTLE_WQ).inv()
            if (board[60] !== bk) c = c and (CASTLE_BK or CASTLE_BQ).inv()
            if (board[7] !== wr) c = c and CASTLE_WK.inv()
            if (board[0] !== wr) c = c and CASTLE_WQ.inv()
            if (board[63] !== br) c = c and CASTLE_BK.inv()
            if (board[56] !== br) c = c and CASTLE_BQ.inv()
            return c
        }
    }
}
