package com.zorix.chess.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTest {

    private fun perft(pos: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = pos.legalMoves
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(pos.play(it), depth - 1) }
    }

    private fun assertPerft(fen: String, vararg expected: Long) {
        val pos = Position.fromFen(fen)
        expected.forEachIndexed { i, count -> assertEquals("perft ${i + 1} of $fen", count, perft(pos, i + 1)) }
    }

    // Reference values: https://www.chessprogramming.org/Perft_Results
    @Test fun perftStartPosition() = assertPerft(Position.START_FEN, 20, 400, 8902, 197281)

    @Test fun perftKiwipete() =
        assertPerft("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039, 97862)

    @Test fun perftPosition3() = assertPerft("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238)

    @Test fun perftPosition4() =
        assertPerft("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467)

    @Test fun perftPosition5() = assertPerft("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486, 62379)

    @Test fun perftPosition6() =
        assertPerft("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 46, 2079, 89890)

    @Test fun fenRoundTrip() {
        listOf(
            Position.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        ).forEach { assertEquals(it, Position.fromFen(it).fen()) }
    }

    @Test fun promotionOffersAllFourPieces() {
        val pos = Position.fromFen("8/4P3/8/8/8/8/k7/4K3 w - - 0 1")
        val e7 = Squares.parse("e7")
        val e8 = Squares.parse("e8")
        assertTrue(pos.isPromotion(e7, e8))
        val promos = pos.legalMovesFrom(e7).filter { it.to == e8 }.map { it.promotion }.toSet()
        assertEquals(setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT), promos)

        val knight = pos.play(Move(e7, e8, PieceType.KNIGHT))
        assertEquals(Piece.of(Side.WHITE, PieceType.KNIGHT), knight[e8])
        val rook = pos.play(Move(e7, e8, PieceType.ROOK))
        assertEquals(Piece.of(Side.WHITE, PieceType.ROOK), rook[e8])
    }

    @Test fun blackCapturePromotion() {
        val pos = Position.fromFen("4k3/8/8/8/8/8/3p4/2R1K3 b - - 0 1")
        val move = Move(Squares.parse("d2"), Squares.parse("c1"), PieceType.BISHOP)
        assertTrue(pos.isLegal(move))
        assertEquals(Piece.of(Side.BLACK, PieceType.BISHOP), pos.play(move)[Squares.parse("c1")])
        // Plain d2-c1 without a piece choice is not a legal move.
        assertFalse(pos.isLegal(Move(Squares.parse("d2"), Squares.parse("c1"))))
    }

    @Test fun enPassantSquareOnlyWhenCapturable() {
        val afterE4 = Position.initial().play(Move.fromUci("e2e4")!!)
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", afterE4.fen())
        val pos = Position.fromFen("rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 3")
            .play(Move.fromUci("c2c4")!!)
        assertTrue(pos.fen().contains(" c3 "))
        val captured = pos.play(Move.fromUci("d4c3")!!)
        assertNull(captured[Squares.parse("c4")])
    }

    @Test fun castlingMovesRook() {
        val pos = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val oo = pos.play(Move.fromUci("e1g1")!!)
        assertEquals(Piece.of(Side.WHITE, PieceType.ROOK), oo[Squares.parse("f1")])
        assertEquals("r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1", oo.fen())
        val ooo = oo.play(Move.fromUci("e8c8")!!)
        assertEquals("2kr3r/8/8/8/8/8/8/R4RK1 w - - 2 2", ooo.fen())
    }

    @Test fun problemsAreDetected() {
        assertEquals(PositionProblem.MISSING_KING, Position.fromFen("8/8/8/8/8/8/8/4K3 w - - 0 1").problem())
        assertEquals(PositionProblem.PAWN_ON_BACK_RANK, Position.fromFen("P3k3/8/8/8/8/8/8/4K3 w - - 0 1").problem())
        // Black to move while in check is fine; White to move with Black in check is impossible.
        assertNull(Position.fromFen("4k3/4R3/8/8/8/8/8/4K3 b - - 0 1").problem())
        assertEquals(PositionProblem.OPPONENT_IN_CHECK, Position.fromFen("4k3/4R3/8/8/8/8/8/4K3 w - - 0 1").problem())
        assertEquals(PositionProblem.TOO_MANY_KINGS, Position.fromFen("4k3/8/8/8/8/8/8/K3K3 w - - 0 1").problem())
        assertNull(Position.initial().problem())
    }

    @Test(expected = IllegalArgumentException::class)
    fun badFenIsRejected() {
        Position.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1")
    }
}
