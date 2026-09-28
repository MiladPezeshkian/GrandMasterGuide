package com.zorix.chess.play

import com.zorix.chess.core.Move
import com.zorix.chess.core.Position
import com.zorix.chess.engine.uci.AnalysisSnapshot
import com.zorix.chess.engine.uci.SearchLimit
import com.zorix.chess.engine.uci.UciEngine
import kotlin.math.exp
import kotlin.random.Random

/** Strength groups, used for names and colours in the level map. */
enum class BotTier { BEGINNER, NOVICE, INTERMEDIATE, ADVANCED, EXPERT, MASTER, GRANDMASTER, ZORIX }

/** One Zorix opponent. Levels 1..20 go from a complete beginner to full engine strength. */
data class Bot(val level: Int, val elo: Int) {
    val tier: BotTier
        get() = when {
            elo < 700 -> BotTier.BEGINNER
            elo < 1150 -> BotTier.NOVICE
            elo < 1600 -> BotTier.INTERMEDIATE
            elo < 2050 -> BotTier.ADVANCED
            elo < 2500 -> BotTier.EXPERT
            elo < 2800 -> BotTier.MASTER
            elo < 3100 -> BotTier.GRANDMASTER
            else -> BotTier.ZORIX
        }
}

object Bots {
    private val ELOS = listOf(250, 400, 550, 700, 850, 1000, 1150, 1300, 1450, 1600, 1750, 1900, 2050, 2200, 2350, 2500, 2650, 2800, 3000, 3200)

    val all: List<Bot> = ELOS.mapIndexed { i, elo -> Bot(i + 1, elo) }
    val levels: List<Int> = all.map { it.level }

    fun byLevel(level: Int): Bot = all[(level - 1).coerceIn(0, all.lastIndex)]

    /** The level closest to a player's rating (a fair game). */
    fun recommended(rating: Int): Bot = all.minBy { kotlin.math.abs(it.elo - rating) }
}

/**
 * How a bot chooses its move. Strong levels use Stockfish's own strength limit (UCI_Elo);
 * the beginner levels below 1320 Elo pick among the engine's candidate moves with a
 * temperature, so they make natural human-like mistakes instead of random nonsense.
 */
object BotBrain {

    data class Plan(
        val limit: SearchLimit,
        val multiPv: Int,
        val options: Map<String, String>,
        /** Softmax temperature in centipawns, or null to play the engine's choice. */
        val temperature: Double?,
        /** Chance of an outright random legal move (only the weakest levels). */
        val randomMove: Double,
    )

    fun plan(bot: Bot, ply: Int): Plan {
        val opening = ply < 8
        return when {
            bot.elo >= 3190 -> Plan(SearchLimit.MoveTime(1500), if (opening) 3 else 1, UciEngine.FULL_STRENGTH, if (opening) 12.0 else null, 0.0)
            bot.elo >= 1320 -> Plan(
                SearchLimit.MoveTime(300L + (bot.elo - 1300) / 3),
                1,
                UciEngine.limitedStrength(bot.elo),
                null,
                0.0,
            )
            else -> {
                val t = when {
                    bot.elo <= 250 -> 420.0
                    bot.elo <= 400 -> 300.0
                    bot.elo <= 550 -> 210.0
                    bot.elo <= 700 -> 150.0
                    bot.elo <= 850 -> 110.0
                    bot.elo <= 1000 -> 80.0
                    bot.elo <= 1150 -> 58.0
                    else -> 42.0
                }
                val random = when {
                    bot.elo <= 250 -> 0.14
                    bot.elo <= 400 -> 0.08
                    bot.elo <= 550 -> 0.04
                    bot.elo <= 700 -> 0.02
                    else -> 0.0
                }
                Plan(SearchLimit.Depth(if (bot.elo <= 700) 5 else 8), 10, UciEngine.FULL_STRENGTH, t, random)
            }
        }
    }

    /** Picks the move from a finished search according to [plan]. */
    fun choose(position: Position, snap: AnalysisSnapshot, plan: Plan, rng: Random): Move? {
        val legal = position.legalMoves
        if (legal.isEmpty()) return null
        if (plan.randomMove > 0 && rng.nextDouble() < plan.randomMove) return legal[rng.nextInt(legal.size)]
        val engineBest = snap.bestMove?.let(Move::fromUci)?.takeIf { position.isLegal(it) }
        val t = plan.temperature ?: return engineBest ?: legal.first()
        val candidates = snap.lines.mapNotNull { line ->
            val m = line.pv.firstOrNull()?.let(Move::fromUci)?.takeIf { position.isLegal(it) } ?: return@mapNotNull null
            val cp = line.score.mate?.let { if (it > 0) 3000 - 10 * it else -3000 - 10 * it } ?: line.score.centipawns ?: 0
            m to cp
        }.distinctBy { it.first }
        if (candidates.isEmpty()) return engineBest ?: legal.first()
        // Even beginners take a mate in one when they see it most of the time.
        val mateInOne = snap.lines.firstOrNull { it.score.mate == 1 }?.pv?.firstOrNull()?.let(Move::fromUci)
        if (mateInOne != null && position.isLegal(mateInOne) && rng.nextDouble() < 0.7) return mateInOne
        val best = candidates.maxOf { it.second }
        val weights = candidates.map { exp((it.second - best) / t) }
        var r = rng.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0) return candidates[i].first
        }
        return candidates.last().first
    }
}
