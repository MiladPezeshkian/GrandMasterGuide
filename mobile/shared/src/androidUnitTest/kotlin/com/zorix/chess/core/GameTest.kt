package com.zorix.chess.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {

    private fun Game.uci(vararg moves: String): Game =
        moves.fold(this) { g, m -> g.play(Move.fromUci(m) ?: error("bad move $m")) }

    @Test fun sanNotation() {
        val g = Game.new().uci("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5c6", "d7c6", "e1g1")
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6", "O-O"), g.plies.map { it.san })
    }

    @Test fun sanDisambiguationAndPromotionSuffixes() {
        val knights = Position.fromFen("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1")
        assertEquals("Nbd2", Notation.san(knights, Move.fromUci("b1d2")!!))
        assertEquals("Nfd2", Notation.san(knights, Move.fromUci("f1d2")!!))

        val rooks = Position.fromFen("4k3/8/R7/8/8/8/8/R3K3 w - - 0 1")
        assertEquals("R6a3", Notation.san(rooks, Move.fromUci("a6a3")!!))

        val promo = Position.fromFen("7k/4P3/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals("e8=Q+", Notation.san(promo, Move.fromUci("e7e8q")!!))
        assertEquals("e8=N", Notation.san(promo, Move.fromUci("e7e8n")!!))

        val mate = Position.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")
        assertEquals("Ra8#", Notation.san(mate, Move.fromUci("a1a8")!!))
    }

    @Test fun undoRedoAndSmartRedo() {
        var g = Game.new().uci("e2e4", "e7e5", "g1f3")
        g = g.undo().undo()
        assertEquals(1, g.plies.size)
        assertTrue(g.canRedo)
        // Replaying the undone move keeps the remaining redo line.
        g = g.uci("e7e5")
        assertTrue(g.canRedo)
        g = g.redo()
        assertEquals(listOf("e4", "e5", "Nf3"), g.plies.map { it.san })
        // A different move discards the redo line.
        g = g.undo().uci("b1c3")
        assertFalse(g.canRedo)
        assertEquals(3, g.goTo(99).plies.size)
        assertEquals(0, g.goTo(0).plies.size)
        assertEquals(3, g.goTo(0).goTo(3).plies.size)
    }

    @Test fun gameEndDetection() {
        val fools = Game.new().uci("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameStatus(GameResult.BLACK_WINS, EndReason.CHECKMATE), fools.status)

        val stalemate = Game.new(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"))
        assertEquals(EndReason.STALEMATE, stalemate.status.reason)

        val bare = Game.new(Position.fromFen("8/8/4k3/8/8/3NK3/8/8 w - - 0 1"))
        assertEquals(EndReason.INSUFFICIENT_MATERIAL, bare.status.reason)

        val shuffle = Game.new().uci("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(EndReason.THREEFOLD_REPETITION, shuffle.status.reason)
        assertFalse(shuffle.undo().status.isOver)
    }

    @Test fun uciPositionAndPgn() {
        val g = Game.new().uci("e2e4", "c7c5")
        assertEquals("startpos moves e2e4 c7c5", g.uciPosition())
        val pgn = g.toPgn()
        assertTrue(pgn.contains("[Result \"*\"]"))
        assertTrue(pgn.trimEnd().endsWith("1. e4 c5 *"))

        val custom = Game.new(Position.fromFen("4k3/8/8/8/8/8/8/4K2R b K - 0 30")).uci("e8d7")
        assertEquals("fen 4k3/8/8/8/8/8/8/4K2R b K - 0 30 moves e8d7", custom.uciPosition())
        assertTrue(custom.toPgn().contains("[FEN \"4k3/8/8/8/8/8/8/4K2R b K - 0 30\"]"))
        assertTrue(custom.toPgn().contains("30... Kd7"))
    }

    @Test fun restoreKeepsRedoLine() {
        val g = Game.restore(Position.START_FEN, listOf("e2e4", "e7e5"), listOf("g1f3", "b8c6"))
        assertEquals(2, g.plies.size)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6"), g.fullLine.map { it.san })
        assertEquals("Nf3", g.redo().plies.last().san)
    }

    @Test fun variationToSan() {
        val sans = Notation.variationToSan(Position.initial(), listOf("e2e4", "e7e5", "g1f3", "zzzz", "b8c6"))
        assertEquals(listOf("e4", "e5", "Nf3"), sans)
        assertEquals("1. e4 e5 2. Nf3", Notation.numbered(Position.initial(), sans))
    }
}
