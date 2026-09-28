package com.zorix.chess.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlin.math.roundToInt

/** How much chess the player knows; chosen in the welcome screen and used for the first rating. */
enum class Experience(val startRating: Int) {
    NEW(400), RULES(700), CASUAL(1000), CLUB(1400), STRONG(1800)
}

/** Result of one game against a Zorix bot, from the player's side. */
enum class GameOutcome(val score: Double) { WIN(1.0), DRAW(0.5), LOSS(0.0) }

/** The player: name for the coach, ratings and learning progress. Stored only on the device. */
data class Profile(
    val name: String = "",
    val onboarded: Boolean = false,
    val experience: Experience = Experience.RULES,
    /** Playing strength, updated with the Elo formula after every game against Zorix. */
    val rating: Int = Experience.RULES.startRating,
    val ratedGames: Int = 0,
    val peakRating: Int = Experience.RULES.startRating,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    /** Best result against each bot level: 3 = clean win, 2 = win with help, 1 = draw. */
    val levelStars: Map<Int, Int> = emptyMap(),
    val puzzleRating: Int = Experience.RULES.startRating,
    val puzzlesSolved: Int = 0,
    val puzzlesTried: Int = 0,
    val puzzleStreak: Int = 0,
    val bestPuzzleStreak: Int = 0,
    /** Stars earned per lesson id (1..3). */
    val lessonStars: Map<String, Int> = emptyMap(),
    /** Experience points: lessons, puzzles and games all add to it. */
    val xp: Int = 0,
) {
    val gamesPlayed: Int get() = wins + draws + losses
    val lessonsCompleted: Int get() = lessonStars.size

    /** Highest bot level the player may choose: beaten levels plus the next one, and anything near their rating. */
    fun unlockedLevel(levels: List<Int>, levelElo: (Int) -> Int): Int {
        val beaten = levelStars.filterValues { it >= 2 }.keys.maxOrNull() ?: 0
        val byRating = levels.lastOrNull { levelElo(it) <= rating + 150 } ?: levels.first()
        return maxOf(beaten + 1, byRating).coerceAtMost(levels.last())
    }
}

/** Elo helpers shared by games and puzzles. */
object Elo {
    fun expected(rating: Int, opponent: Int): Double = 1.0 / (1.0 + 10.0.pow((opponent - rating) / 400.0))

    /** New rating after a result; faster movement for the first games (provisional rating). */
    fun update(rating: Int, opponent: Int, score: Double, games: Int): Int {
        val k = when {
            games < 10 -> 48.0
            games < 30 -> 32.0
            else -> 20.0
        }
        return (rating + k * (score - expected(rating, opponent))).roundToInt().coerceIn(100, 3300)
    }
}

/** Loads and saves the [Profile]; the UI observes [profile]. */
class ProfileStore(private val store: KeyValueStore) {
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    val current: Profile get() = _profile.value

    fun update(transform: (Profile) -> Profile) {
        val next = transform(_profile.value)
        if (next == _profile.value) return
        _profile.value = next
        save(next)
    }

    fun completeOnboarding(name: String, experience: Experience) = update {
        it.copy(
            name = name.trim().take(24),
            onboarded = true,
            experience = experience,
            rating = if (it.ratedGames == 0) experience.startRating else it.rating,
            peakRating = maxOf(it.peakRating, experience.startRating),
            puzzleRating = if (it.puzzlesTried == 0) experience.startRating else it.puzzleRating,
        )
    }

    /** Records a finished game against a bot of [botElo]; returns the rating change. */
    fun recordGame(level: Int, botElo: Int, outcome: GameOutcome, assisted: Boolean): Int {
        var delta = 0
        update { p ->
            val newRating = Elo.update(p.rating, botElo, outcome.score, p.ratedGames)
            delta = newRating - p.rating
            val stars = when (outcome) {
                GameOutcome.WIN -> if (assisted) 2 else 3
                GameOutcome.DRAW -> 1
                GameOutcome.LOSS -> 0
            }
            p.copy(
                rating = newRating,
                ratedGames = p.ratedGames + 1,
                peakRating = maxOf(p.peakRating, newRating),
                wins = p.wins + if (outcome == GameOutcome.WIN) 1 else 0,
                draws = p.draws + if (outcome == GameOutcome.DRAW) 1 else 0,
                losses = p.losses + if (outcome == GameOutcome.LOSS) 1 else 0,
                levelStars = if (stars > (p.levelStars[level] ?: 0)) p.levelStars + (level to stars) else p.levelStars,
                xp = p.xp + when (outcome) {
                    GameOutcome.WIN -> 30
                    GameOutcome.DRAW -> 15
                    GameOutcome.LOSS -> 5
                },
            )
        }
        return delta
    }

    /** Records a rated puzzle attempt; returns the rating change. */
    fun recordPuzzle(puzzleRating: Int, solved: Boolean): Int {
        var delta = 0
        update { p ->
            val newRating = Elo.update(p.puzzleRating, puzzleRating, if (solved) 1.0 else 0.0, p.puzzlesTried)
            delta = newRating - p.puzzleRating
            val streak = if (solved) p.puzzleStreak + 1 else 0
            p.copy(
                puzzleRating = newRating,
                puzzlesTried = p.puzzlesTried + 1,
                puzzlesSolved = p.puzzlesSolved + if (solved) 1 else 0,
                puzzleStreak = streak,
                bestPuzzleStreak = maxOf(p.bestPuzzleStreak, streak),
                xp = p.xp + if (solved) 8 else 1,
            )
        }
        return delta
    }

    fun recordLesson(id: String, stars: Int) = update { p ->
        val old = p.lessonStars[id] ?: 0
        if (stars <= old) p else p.copy(lessonStars = p.lessonStars + (id to stars), xp = p.xp + 10 * (stars - old))
    }

    fun rename(name: String) = update { it.copy(name = name.trim().take(24)) }

    private fun load(): Profile {
        fun int(key: String, def: Int) = store.getString("profile.$key")?.toIntOrNull() ?: def
        val exp = store.getString("profile.experience")?.let { v -> Experience.entries.firstOrNull { it.name == v } } ?: Experience.RULES
        return Profile(
            name = store.getString("profile.name").orEmpty(),
            onboarded = store.getString("profile.onboarded") == "true",
            experience = exp,
            rating = int("rating", exp.startRating),
            ratedGames = int("ratedGames", 0),
            peakRating = int("peakRating", exp.startRating),
            wins = int("wins", 0),
            draws = int("draws", 0),
            losses = int("losses", 0),
            levelStars = decodeMap(store.getString("profile.levelStars")) { it.toIntOrNull() },
            puzzleRating = int("puzzleRating", exp.startRating),
            puzzlesSolved = int("puzzlesSolved", 0),
            puzzlesTried = int("puzzlesTried", 0),
            puzzleStreak = int("puzzleStreak", 0),
            bestPuzzleStreak = int("bestPuzzleStreak", 0),
            lessonStars = decodeMap(store.getString("profile.lessonStars")) { it },
            xp = int("xp", 0),
        )
    }

    private fun save(p: Profile) {
        fun put(key: String, v: Any) = store.putString("profile.$key", v.toString())
        put("name", p.name)
        put("onboarded", p.onboarded)
        put("experience", p.experience.name)
        put("rating", p.rating)
        put("ratedGames", p.ratedGames)
        put("peakRating", p.peakRating)
        put("wins", p.wins)
        put("draws", p.draws)
        put("losses", p.losses)
        put("levelStars", p.levelStars.entries.joinToString(",") { "${it.key}=${it.value}" })
        put("puzzleRating", p.puzzleRating)
        put("puzzlesSolved", p.puzzlesSolved)
        put("puzzlesTried", p.puzzlesTried)
        put("puzzleStreak", p.puzzleStreak)
        put("bestPuzzleStreak", p.bestPuzzleStreak)
        put("lessonStars", p.lessonStars.entries.joinToString(",") { "${it.key}=${it.value}" })
        put("xp", p.xp)
    }

    private fun <K> decodeMap(text: String?, key: (String) -> K?): Map<K, Int> =
        text.orEmpty().split(',').mapNotNull { entry ->
            val k = entry.substringBefore('=', "")
            val v = entry.substringAfter('=', "").toIntOrNull()
            val kk = key(k)
            if (kk == null || v == null) null else kk to v
        }.toMap()
}
