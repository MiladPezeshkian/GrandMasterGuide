package com.zorix.chess.engine.uci

import com.zorix.chess.core.Game
import com.zorix.chess.core.Move
import com.zorix.chess.core.Position
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Integration tests against a real Stockfish binary. They run only when the JVM is started with
 * `-Dstockfish.path=/path/to/stockfish -Dstockfish.dir=/folder/with/the/nnue` and are skipped otherwise.
 */
class UciEngineTest {

    private lateinit var engine: UciEngine

    @Before fun setUp() {
        val path = System.getProperty("stockfish.path").orEmpty()
        assumeTrue("stockfish.path not set", path.isNotEmpty() && File(path).canExecute())
        val dir = System.getProperty("stockfish.dir").orEmpty().ifEmpty { File(path).parent }
        engine = UciEngine { ProcessBuilder(path).directory(File(dir)).redirectErrorStream(true).start() }
        runBlocking { engine.start(mapOf("Threads" to "2", "Hash" to "32")) }
    }

    @After fun tearDown() {
        if (::engine.isInitialized) engine.close()
    }

    @Test fun handshakeReportsStockfish() {
        assertTrue(engine.engineName, engine.engineName.startsWith("Stockfish"))
    }

    @Test fun findsMateInOne() = runBlocking {
        val result = engine.search("fen 6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", SearchLimit.Depth(10))
        assertEquals("a1a8", result.bestMove)
        assertEquals(1, result.best!!.score.mate)
        assertTrue(result.finished)
    }

    @Test fun suggestsUnderPromotionWhenItIsBest() = runBlocking {
        // Promoting to a queen stalemates; only a rook (or queen elsewhere) wins cleanly.
        val game = Game.new(Position.fromFen("8/5P1k/8/6K1/8/8/8/8 w - - 0 1"))
        val result = engine.search(game.uciPosition(), SearchLimit.Depth(18))
        val move = Move.fromUci(result.bestMove!!)!!
        assertTrue(game.position.isLegal(move))
        assertTrue("f8=Q stalemates", result.bestMove != "f7f8q")
    }

    @Test fun multiPvAndTimedSearch() = runBlocking {
        val updates = mutableListOf<AnalysisSnapshot>()
        val result = engine.search("startpos moves e2e4", SearchLimit.MoveTime(700), multiPv = 3) { updates += it }
        assertEquals(3, result.lines.size)
        assertEquals(listOf(1, 2, 3), result.lines.map { it.multipv })
        assertTrue(updates.isNotEmpty())
        assertTrue(result.depth > 5)
        assertEquals(result.bestMove, result.lines.first().pv.first())
    }

    @Test fun cancellingInfiniteSearchLeavesEngineUsable() = runBlocking {
        val job = async { engine.search("startpos", SearchLimit.Infinite, multiPv = 2) }
        delay(400)
        job.cancel()
        // The next search must start cleanly after the cancelled one drained its bestmove.
        val result = withTimeout(10_000) { engine.search("startpos moves d2d4", SearchLimit.Depth(8)) }
        assertNotNull(result.bestMove)
        val legal = Position.initial().play(Move.fromUci("d2d4")!!).legalMoves.map { it.uci }
        assertTrue(result.bestMove in legal)
    }

    @Test fun stopSearchReturnsBestMoveSoFar() = runBlocking {
        val job = async { engine.search("startpos", SearchLimit.Infinite) }
        delay(500)
        engine.stopSearch()
        val result = withTimeout(5_000) { job.await() }
        assertNotNull(result.bestMove)
        assertTrue(result.finished)
    }

    @Test fun optionsCanChangeBetweenSearches() = runBlocking {
        engine.setOptions(mapOf("Hash" to "64", "Threads" to "1"))
        engine.newGame()
        val result = engine.search("startpos", SearchLimit.Depth(6))
        assertNotNull(result.bestMove)
    }
}
