package com.zorix.chess.controller

import com.zorix.chess.coach.CoachQuality
import com.zorix.chess.core.Game
import com.zorix.chess.core.Move
import com.zorix.chess.core.Notation
import com.zorix.chess.core.Position
import com.zorix.chess.engine.ProcessConnection
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import org.junit.Assert.assertTrue

/** The coach's verdicts with the real engine: a thrown-away queen must never be called a good move. */
class CoachAccuracyTest {
    private class Host(val path: String, val dir: String) : EngineHost {
        override suspend fun prepare(onProgress: (Float) -> Unit) = listOf(EngineLaunch("test") { ProcessConnection(listOf(path), File(dir)) })
        override val cpuCores = 2
    }

    private val bad = setOf(CoachQuality.MISTAKE, CoachQuality.BLUNDER)

    @Test fun ratesMovesLikeAStrongPlayer() {
        val path = System.getProperty("stockfish.path").orEmpty()
        assumeTrue("stockfish.path not set", path.isNotEmpty())
        val dir = System.getProperty("stockfish.dir").orEmpty()
        runBlocking {
            val hub = EngineHub(this, Host(path, dir)) { mapOf("Threads" to "1", "Hash" to "16") }
            try {
                withTimeout(20_000) { hub.ensure() }
                suspend fun rate(fen: String?, moves: List<String>, uci: String): CoachQuality {
                    var game = if (fen == null) Game.new() else Game.new(Position.fromFen(fen))
                    for (m in moves) game = game.play(Move.fromUci(m)!!)
                    val move = Move.fromUci(uci)!!
                    val fb = CoachService.rate(hub, game, move, "en", "Test")!!
                    println("${Notation.san(game.position, move)} -> ${fb.quality}: ${fb.message}")
                    return fb.quality
                }
                // A queen given for a pawn in a normal position.
                val q1 = rate(null, listOf("e2e4", "e7e5", "d1h5", "b8c6"), "h5f7")
                assertTrue("Qxf7+ was $q1", q1 in bad)
                // A queen left to a pawn while already winning (the black king has an escape square, so
                // there is no back-rank mate): still a blunder, not a "good" move.
                val q2 = rate("6k1/5pp1/4p2p/8/8/8/5PPP/R2Q2K1 w - - 0 1", emptyList(), "d1d5")
                assertTrue("Qd5 was $q2", q2 in bad)
                // The same mistake by Black.
                val q3 = rate("r2q2k1/5ppp/8/8/8/4P2P/5PP1/6K1 b - - 0 1", emptyList(), "d8d4")
                assertTrue("...Qd4 was $q3", q3 in bad)
                // A knight given away for nothing while far ahead.
                val q4 = rate("r5k1/2n2pp1/7p/8/8/7P/5PP1/1Q2R1K1 b - - 0 1", emptyList(), "c7d5")
                println("...Nd5 -> $q4")
                // A real queen sacrifice that mates must stay good.
                val q5 = rate("6k1/5ppp/8/8/8/8/5PPP/3QR1K1 w - - 0 1", emptyList(), "d1d8")
                assertTrue("Qd8+ was $q5", q5 !in bad)
                // A quiet good move must stay good.
                val q6 = rate(null, listOf("e2e4", "e7e5"), "g1f3")
                assertTrue("Nf3 was $q6", q6 !in bad)
            } finally {
                hub.close()
            }
        }
    }
}
