package com.zorix.chess.play

import com.zorix.chess.coach.CoachQuality
import com.zorix.chess.controller.EngineHost
import com.zorix.chess.controller.EngineHub
import com.zorix.chess.controller.EngineLaunch
import com.zorix.chess.controller.EngineStatus
import com.zorix.chess.controller.Experience
import com.zorix.chess.controller.GameOutcome
import com.zorix.chess.controller.InMemoryStore
import com.zorix.chess.controller.ProfileStore
import com.zorix.chess.controller.Settings
import com.zorix.chess.core.Game
import com.zorix.chess.core.Move
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.core.Squares
import com.zorix.chess.engine.ProcessConnection
import com.zorix.chess.engine.uci.AnalysisSnapshot
import com.zorix.chess.engine.uci.PvLine
import com.zorix.chess.engine.uci.Score
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class PlayTest {

    private fun line(rank: Int, uci: String, cp: Int) =
        PvLine(rank, 10, 10, Score(centipawns = cp), null, listOf(uci), 0, 0, 0, null)

    @Test fun levelsCoverBeginnerToZorixMax() {
        assertEquals(20, Bots.all.size)
        assertEquals(250, Bots.byLevel(1).elo)
        assertEquals(3200, Bots.byLevel(20).elo)
        assertEquals(10, Bots.recommended(1600).level)
    }

    @Test fun beginnerBotsMakeHumanMistakesButStrongBotsDoNot() {
        val pos = Position.initial()
        val snap = AnalysisSnapshot(
            lines = listOf(line(1, "e2e4", 40), line(2, "d2d4", 35), line(3, "g1f3", 30), line(4, "a2a3", -20), line(5, "g2g4", -120)),
            bestMove = "e2e4",
        )
        val rng = Random(1)
        val beginner = BotBrain.plan(Bots.byLevel(1), 20)
        val weakPicks = (1..400).map { BotBrain.choose(pos, snap, beginner, rng) }.toSet()
        assertTrue("a beginner varies its moves: $weakPicks", weakPicks.size >= 4)
        val club = BotBrain.plan(Bots.byLevel(8), 20)
        val picks = (1..400).mapNotNull { BotBrain.choose(pos, snap, club, rng) }
        assertTrue(picks.count { it == Move.fromUci("g2g4") } < 20)
    }

    @Test fun ratingFollowsResults() {
        val profile = ProfileStore(InMemoryStore())
        profile.completeOnboarding("Milad", Experience.CASUAL)
        assertEquals(1000, profile.current.rating)
        val gain = profile.recordGame(level = 6, botElo = 1000, outcome = GameOutcome.WIN, assisted = false)
        assertTrue(gain > 0)
        assertEquals(3, profile.current.levelStars[6])
        val loss = profile.recordGame(level = 7, botElo = 1150, outcome = GameOutcome.LOSS, assisted = false)
        assertTrue(loss < 0)
        // A stored profile survives a restart.
        val store = InMemoryStore()
        ProfileStore(store).completeOnboarding("سارا", Experience.CLUB)
        assertEquals("سارا", ProfileStore(store).current.name)
    }

    private class Host(val path: String, val dir: String) : EngineHost {
        override suspend fun prepare(onProgress: (Float) -> Unit) = listOf(EngineLaunch("test") { ProcessConnection(listOf(path), File(dir)) })
        override val cpuCores = 2
    }

    @Test fun playsAGameAgainstZorixAndReviewsIt() {
        val path = System.getProperty("stockfish.path").orEmpty()
        assumeTrue("stockfish.path not set", path.isNotEmpty())
        val dir = System.getProperty("stockfish.dir").orEmpty()
        runBlocking {
            val scope: CoroutineScope = this
            val hub = EngineHub(scope, Host(path, dir)) { mapOf("Threads" to "1", "Hash" to "16") }
            val profile = ProfileStore(InMemoryStore()).apply { completeOnboarding("Ali", Experience.RULES) }
            val play = PlayController(scope, hub, profile, InMemoryStore(), { Settings() }, random = Random(3))
            try {
                withTimeout(20_000) { hub.ensure() }
                assertTrue(hub.status.value is EngineStatus.Ready)
                play.start(level = 3, side = Side.WHITE)
                // 1.e4: the bot must answer and the coach must rate the move.
                play.onUserMove(Squares.parse("e2"), Squares.parse("e4"))
                val answered = withTimeout(15_000) { play.state.first { it.game.plies.size == 2 && !it.botThinking } }
                assertEquals(Side.WHITE, answered.game.position.sideToMove)
                val fb = withTimeout(15_000) { play.state.first { it.lastFeedback != null } }.lastFeedback!!
                assertTrue(fb.message, fb.quality in setOf(CoachQuality.BOOK, CoachQuality.BEST, CoachQuality.EXCELLENT, CoachQuality.GOOD))
                // Hint explains a move.
                play.hint()
                val hint = withTimeout(15_000) { play.state.first { it.hint != null } }.hint!!
                assertTrue(hint.display.isNotBlank())
                play.resign()
                val result = play.state.value.result
                assertNotNull(result)
                assertEquals(GameOutcome.LOSS, result!!.outcome)
                assertTrue(profile.current.rating < 700)

                // Review a miniature: Scholar's mate.
                val review = ReviewController(scope, hub, profile)
                var game = Game.new()
                for (m in listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")) game = game.play(Move.fromUci(m)!!)
                review.start(game, Side.BLACK)
                val done = withTimeout(30_000) { review.state.first { it.review != null } }.review!!
                val blunder = done.moves.first { it.san == "Nf6" }
                assertEquals(CoachQuality.BLUNDER, blunder.quality)
                assertTrue(blunder.message, blunder.message.contains("Ali"))
                assertTrue(done.accuracy[Side.WHITE]!! > done.accuracy[Side.BLACK]!!)
                println(blunder.message)
                println("opening=${done.opening} accuracy=${done.accuracy}")
            } finally {
                hub.close()
                coroutineContext.cancelChildren()
            }
        }
    }
}
