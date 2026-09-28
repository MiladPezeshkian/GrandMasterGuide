package com.zorix.chess.engine.uci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UciParserTest {

    @Test fun parsesFullInfoLine() {
        val info = UciParser.parseInfo(
            "info depth 22 seldepth 31 multipv 2 score cp -35 upperbound wdl 20 900 80 nodes 1234567 " +
                "nps 987654 hashfull 120 tbhits 0 time 1500 pv e7e5 g1f3 b8c6",
        )!!
        assertEquals(22, info.depth)
        assertEquals(31, info.selDepth)
        assertEquals(2, info.multipv)
        assertEquals(Score(centipawns = -35), info.score)
        assertEquals(Bound.UPPER, info.bound)
        assertEquals(Wdl(20, 900, 80), info.wdl)
        assertEquals(1234567L, info.nodes)
        assertEquals(987654L, info.nps)
        assertEquals(1500L, info.timeMs)
        assertEquals(listOf("e7e5", "g1f3", "b8c6"), info.pv)
    }

    @Test fun parsesMateAndStrings() {
        assertEquals(Score(mate = -3), UciParser.parseInfo("info depth 9 score mate -3 pv h7h6")!!.score)
        assertEquals("NNUE evaluation using nn.nnue", UciParser.parseInfo("info string NNUE evaluation using nn.nnue")!!.string)
        assertNull(UciParser.parseInfo("bestmove e2e4"))
    }

    @Test fun parsesBestMove() {
        assertEquals("e2e4" to "e7e5", UciParser.parseBestMove("bestmove e2e4 ponder e7e5"))
        assertEquals("e7e8q" to null, UciParser.parseBestMove("bestmove e7e8q"))
        assertEquals(null to null, UciParser.parseBestMove("bestmove (none)"))
    }

    @Test fun scoreFormatting() {
        assertEquals("+1.25", Score(centipawns = 125).format())
        assertEquals("-0.40", Score(centipawns = -40).format())
        assertEquals("0.00", Score(centipawns = 0).format())
        assertEquals("#3", Score(mate = 3).format())
        assertEquals(Score(centipawns = -50), Score(centipawns = 50).forWhite(whiteToMove = false))
        assertEquals(10.0, Score(mate = 1).pawns(), 0.0)
    }
}
