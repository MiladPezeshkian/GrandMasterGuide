package com.zorix.chess.controller

import com.zorix.chess.core.Piece
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.core.Squares
import com.zorix.chess.engine.ProcessConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ChessControllerTest {

    /** Host without an engine binary: the engine fails, the board keeps working. */
    private object NoEngineHost : EngineHost {
        override suspend fun prepare(onProgress: (Float) -> Unit): List<EngineLaunch> = emptyList()
        override val cpuCores: Int = 4
    }

    /** Host running the Stockfish binary given with -Dstockfish.path (see UciEngineTest). */
    private class RealEngineHost(private val path: String, private val dir: String) : EngineHost {
        override suspend fun prepare(onProgress: (Float) -> Unit) =
            listOf(EngineLaunch("test") { ProcessConnection(listOf(path), File(dir)) })
        override val cpuCores: Int = 2
    }

    private fun withController(host: EngineHost = NoEngineHost, store: KeyValueStore = InMemoryStore(), block: suspend CoroutineScope.(ChessController) -> Unit) =
        runBlocking {
            val controller = ChessController(this, host, store)
            try {
                block(controller)
            } finally {
                controller.close()
                coroutineContext.cancelChildren()
            }
        }

    private fun sq(name: String) = Squares.parse(name)

    @Test fun pawnReachingLastRankAsksForPieceInsteadOfAutoQueen() = withController { c ->
        c.setPosition(Position.fromFen("8/4P3/8/8/8/8/k7/4K3 w - - 0 1"))
        c.onUserMove(sq("e7"), sq("e8"))

        val pending = c.state.value.pendingPromotion
        assertEquals(PendingPromotion(sq("e7"), sq("e8"), Side.WHITE), pending)
        // Nothing has been played yet: the pawn is still on e7.
        assertEquals(Piece.of(Side.WHITE, PieceType.PAWN), c.state.value.game.position[sq("e7")])

        c.onPromotionChosen(PieceType.KNIGHT)
        val pos = c.state.value.game.position
        assertEquals(Piece.of(Side.WHITE, PieceType.KNIGHT), pos[sq("e8")])
        assertNull(c.state.value.pendingPromotion)
        assertEquals("e8=N", c.state.value.game.plies.last().san)
    }

    @Test fun everyPromotionPieceWorksForBlackToo() = withController { c ->
        for (type in PieceType.PROMOTIONS) {
            c.setPosition(Position.fromFen("4k3/8/8/8/8/8/3p4/2R1K3 b - - 0 1"))
            c.onUserMove(sq("d2"), sq("c1"))
            assertNotNull(c.state.value.pendingPromotion)
            c.onPromotionChosen(type)
            assertEquals(Piece.of(Side.BLACK, type), c.state.value.game.position[sq("c1")])
        }
    }

    @Test fun cancellingPromotionLeavesPawnInPlace() = withController { c ->
        c.setPosition(Position.fromFen("8/4P3/8/8/8/8/k7/4K3 w - - 0 1"))
        c.onUserMove(sq("e7"), sq("e8"))
        c.onPromotionCancelled()
        assertNull(c.state.value.pendingPromotion)
        assertEquals(0, c.state.value.game.plies.size)
    }

    @Test fun illegalMoveIsRejected() = withController { c ->
        c.onUserMove(sq("e2"), sq("e5"))
        assertEquals(0, c.state.value.game.plies.size)
    }

    @Test fun sessionIsRestored() {
        val store = InMemoryStore()
        withController(store = store) { c ->
            c.onUserMove(sq("e2"), sq("e4"))
            c.onUserMove(sq("e7"), sq("e5"))
            c.onUserMove(sq("g1"), sq("f3"))
            c.undo()
            c.flipBoard()
        }
        withController(store = store) { c ->
            val st = c.state.value
            assertEquals(listOf("e4", "e5"), st.game.plies.map { it.san })
            assertTrue(st.game.canRedo)
            assertTrue(st.flipped)
        }
    }

    @Test fun fenLoadingValidates() = withController { c ->
        assertEquals(ChessController.FenError.Malformed, c.loadFen("not a fen"))
        assertTrue(c.loadFen("8/8/8/8/8/8/8/4K3 w - - 0 1") is ChessController.FenError.Invalid)
        assertNull(c.loadFen("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"))
        assertEquals("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", c.fen())
    }

    @Test fun bestMoveAndLiveAnalysisWithStockfish() {
        val path = System.getProperty("stockfish.path").orEmpty()
        assumeTrue("stockfish.path not set", path.isNotEmpty())
        val dir = System.getProperty("stockfish.dir").orEmpty()
        withController(RealEngineHost(path, dir)) { c ->
            withTimeout(20_000) { c.state.first { it.engine is EngineStatus.Ready } }
            c.updateSettings { it.copy(thinkTimeMs = 600) }

            // Mate in one for White: the hint must find it and "Play" must play it.
            c.setPosition(Position.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"))
            c.requestBestMove()
            val ready = withTimeout(10_000) { c.state.first { it.hint is HintState.Ready } }.hint as HintState.Ready
            assertEquals("a1a8", ready.view.bestMove?.uci)
            assertEquals("Ra8#", ready.view.bestSan)
            c.playSuggestion()
            assertTrue(c.state.value.game.status.isOver)

            // Live analysis shows the configured number of lines for the current position.
            c.newGame()
            c.setAnalysis(true)
            val analysed = withTimeout(10_000) {
                c.state.first { s -> s.analysis?.let { it.lines.size == 3 && it.depth >= 8 } == true }
            }
            assertEquals(analysed.fen, analysed.analysis!!.fen)
            c.onUserMove(sq("d2"), sq("d4"))
            val next = withTimeout(10_000) { c.state.first { s -> s.analysis?.fen == s.fen && s.analysis.depth >= 5 } }
            assertEquals(Side.BLACK, next.analysis!!.sideToMove)

            // A hint pre-empts the running analysis and analysis resumes afterwards.
            c.requestBestMove()
            withTimeout(10_000) { c.state.first { it.hint is HintState.Ready } }
            withTimeout(10_000) { c.state.first { s -> s.analysis?.fen == s.fen && s.analysis.depth >= 5 } }
            c.setAnalysis(false)
        }
    }
    @Test fun coachRatesMovesWithStockfish() {
        val path = System.getProperty("stockfish.path").orEmpty()
        assumeTrue("stockfish.path not set", path.isNotEmpty())
        val dir = System.getProperty("stockfish.dir").orEmpty()
        withController(RealEngineHost(path, dir)) { c ->
            withTimeout(20_000) { c.state.first { it.engine is EngineStatus.Ready } }
            c.updateSettings { it.copy(coachMode = true) }

            // Hanging the queen next to the enemy king is a blunder, and the coach names a better move.
            c.setPosition(Position.fromFen("r3k3/8/8/8/8/8/8/3QK3 w - - 0 1"))
            c.onUserMove(sq("d1"), sq("a4"))
            val bad = withTimeout(15_000) { c.state.first { it.lastFeedback != null } }.lastFeedback!!
            assertTrue("got ${bad.quality}", bad.quality >= MoveQuality.MISTAKE)
            assertNotNull(bad.bestSan)

            // Delivering mate is always the best move.
            c.setPosition(Position.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"))
            c.onUserMove(sq("a1"), sq("a8"))
            val mate = withTimeout(15_000) { c.state.first { it.lastFeedback != null } }.lastFeedback!!
            assertEquals(MoveQuality.BEST, mate.quality)
        }
    }
}
