package com.zorix.chess.core

/** The two players. */
enum class Side {
    WHITE, BLACK;

    val opposite: Side get() = if (this == WHITE) BLACK else WHITE
}

/** Piece kinds, with their English SAN / FEN letter. */
enum class PieceType(val letter: Char) {
    PAWN('P'), KNIGHT('N'), BISHOP('B'), ROOK('R'), QUEEN('Q'), KING('K');

    companion object {
        /** Pieces a pawn may promote to, strongest first (this is also the picker order). */
        val PROMOTIONS: List<PieceType> = listOf(QUEEN, ROOK, BISHOP, KNIGHT)

        fun fromLetter(c: Char): PieceType? = entries.firstOrNull { it.letter == c.uppercaseChar() }
    }
}

/** A coloured piece. Instances are interned, so `===` comparisons are safe. */
class Piece private constructor(val side: Side, val type: PieceType) {

    val isWhite: Boolean get() = side == Side.WHITE

    /** FEN letter: upper case for White, lower case for Black. */
    val fenChar: Char get() = if (isWhite) type.letter else type.letter.lowercaseChar()

    override fun toString(): String = fenChar.toString()

    companion object {
        private val all = Array(12) { i -> Piece(Side.entries[i / 6], PieceType.entries[i % 6]) }

        fun of(side: Side, type: PieceType): Piece = all[side.ordinal * 6 + type.ordinal]

        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromLetter(c) ?: return null
            return of(if (c.isUpperCase()) Side.WHITE else Side.BLACK, type)
        }
    }
}

/** Square helpers. Squares are 0..63 with a1 = 0, b1 = 1, ... h8 = 63. */
object Squares {
    const val NONE = -1

    fun of(file: Int, rank: Int): Int = rank * 8 + file
    fun file(square: Int): Int = square and 7
    fun rank(square: Int): Int = square shr 3
    fun isValid(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7
    fun isLight(square: Int): Boolean = (file(square) + rank(square)) % 2 == 1

    fun name(square: Int): String = "${'a' + file(square)}${'1' + rank(square)}"

    fun parse(name: String): Int {
        require(name.length == 2) { "Bad square: $name" }
        val file = name[0] - 'a'
        val rank = name[1] - '1'
        require(isValid(file, rank)) { "Bad square: $name" }
        return of(file, rank)
    }
}

/** A move in UCI terms. Castling is encoded as the king's two-square move (e1g1). */
data class Move(val from: Int, val to: Int, val promotion: PieceType? = null) {

    val uci: String
        get() = Squares.name(from) + Squares.name(to) + (promotion?.letter?.lowercaseChar() ?: "")

    override fun toString(): String = uci

    companion object {
        /** Parses "e2e4" / "e7e8q". Returns null for malformed input or "(none)"/"0000". */
        fun fromUci(text: String): Move? {
            val s = text.trim()
            if (s.length !in 4..5) return null
            return try {
                val from = Squares.parse(s.substring(0, 2))
                val to = Squares.parse(s.substring(2, 4))
                val promo = if (s.length == 5) {
                    PieceType.fromLetter(s[4])?.takeIf { it in PieceType.PROMOTIONS } ?: return null
                } else {
                    null
                }
                Move(from, to, promo)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

/** Reasons a set-up position cannot be played or analysed. */
enum class PositionProblem {
    MISSING_KING,
    TOO_MANY_KINGS,
    PAWN_ON_BACK_RANK,
    TOO_MANY_PIECES,
    OPPONENT_IN_CHECK,
}
