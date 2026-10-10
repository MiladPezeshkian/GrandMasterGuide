package com.zorix.chess.learn

/** Text in every app language. */
class Text(private val values: Map<String, String>) {
    operator fun get(lang: String): String = values[lang] ?: values["en"] ?: values.values.firstOrNull().orEmpty()
    val isEmpty: Boolean get() = values.values.all { it.isBlank() }

    companion object {
        val EMPTY = Text(emptyMap())
        fun of(any: Any?): Text = when (any) {
            is String -> Text(mapOf("en" to any))
            is Map<*, *> -> Text(any.entries.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }.toMap())
            else -> EMPTY
        }
    }
}

/** What a "goal" exercise asks for; checked with exact rules. */
enum class Goal {
    CHECK, CHECKMATE, CAPTURE_CHECKER, BLOCK_CHECK, KING_ESCAPE, ESCAPE_CHECK,
    CASTLE_SHORT, CASTLE_LONG, PROMOTE, PROMOTE_KNIGHT, EN_PASSANT, WIN_MATERIAL, SAVE_PIECE;

    companion object {
        fun parse(s: String?): Goal? = entries.firstOrNull { it.name.equals(s?.replace('-', '_'), ignoreCase = true) }
    }
}

/** What a "play" exercise (against Zorix) requires. */
enum class PlayGoal { WIN, DRAW, PROMOTE }

/** One screen of a lesson. */
sealed interface Step {
    val prompt: Text

    /** Explanation card, optionally with a diagram. */
    data class Theory(override val prompt: Text, val fen: String?, val arrows: List<String>, val marks: List<String>) : Step

    /** Move a piece to collect every star (the side to move never changes). [par] = fewest moves. */
    data class Stars(override val prompt: Text, val fen: String, val stars: List<String>, val par: Int) : Step

    /** Capture every enemy piece; enemy pieces do not move. */
    data class Capture(override val prompt: Text, val fen: String, val par: Int) : Step

    /** A puzzle with a fixed solution: solver, reply, solver... The last move may be any checkmate. */
    data class Puzzle(
        override val prompt: Text,
        val fen: String,
        val moves: List<String>,
        val pre: String?,
        val last: String?,
        val rating: Int,
        val themes: List<String>,
    ) : Step

    /** Mate in [n]: every correct solution is accepted, Zorix defends as well as possible. */
    data class Mate(override val prompt: Text, val fen: String, val n: Int) : Step

    /** Make a move that satisfies [goal]. */
    data class GoalStep(override val prompt: Text, val fen: String, val goal: Goal) : Step

    /** Multiple-choice question. */
    data class Quiz(override val prompt: Text, val fen: String?, val options: List<Text>, val answer: Int, val explain: Text, val marks: List<String>) : Step

    /** Tap the named squares ([count] rounds). */
    data class Squares(override val prompt: Text, val count: Int, val blackView: Boolean) : Step

    /** Find a good move: any move of [accept] (engine-checked offline) is correct. */
    data class Best(override val prompt: Text, val fen: String, val accept: List<String>, val explain: Text) : Step

    /** Play through an opening: the player plays [side]'s moves, Zorix the others. */
    data class Line(override val prompt: Text, val moves: List<String>, val playerIsWhite: Boolean, val notes: Map<Int, Text>) : Step

    /** Play a position against Zorix until the goal is reached (or [maxMoves] player moves pass for DRAW). */
    data class Play(override val prompt: Text, val fen: String, val goal: PlayGoal, val maxMoves: Int) : Step
}

class Lesson(
    val id: String,
    val title: Text,
    val intro: Text,
    /** Difficulty 1..5. */
    val level: Int,
    val steps: List<Step>,
) {
    /** Exercises (everything but theory cards). */
    val exercises: Int get() = steps.count { it !is Step.Theory }
}

class Chapter(val id: String, val title: Text, val lessons: List<Lesson>)

class Course(val id: String, val title: Text, val description: Text, val chapters: List<Chapter>) {
    val lessons: List<Lesson> get() = chapters.flatMap { it.lessons }
}

/** A rated puzzle for the puzzle trainer. */
class RatedPuzzle(val id: Int, val fen: String, val moves: List<String>, val pre: String?, val last: String?, val rating: Int, val themes: List<String>)

object CurriculumParser {

    fun course(json: String): Course {
        val root = Json.parse(json).obj()
        return Course(
            id = root.str("id").orEmpty(),
            title = Text.of(root["title"]),
            description = Text.of(root["desc"]),
            chapters = root["chapters"].arr().map { c ->
                val co = c.obj()
                Chapter(
                    id = co.str("id").orEmpty(),
                    title = Text.of(co["title"]),
                    lessons = co["lessons"].arr().map { lesson(it.obj()) },
                )
            },
        )
    }

    fun lesson(o: Map<String, Any?>): Lesson = Lesson(
        id = o.str("id").orEmpty(),
        title = Text.of(o["title"]),
        intro = Text.of(o["intro"]),
        level = o.int("level", 1),
        steps = o["steps"].arr().mapNotNull { step(it.obj()) },
    )

    fun step(o: Map<String, Any?>): Step? {
        val prompt = Text.of(o["prompt"] ?: o["text"])
        return when (o.str("type")) {
            "theory" -> Step.Theory(prompt, o.str("fen"), o.strings("arrows"), o.strings("marks"))
            "stars" -> Step.Stars(prompt, o.str("fen") ?: return null, o.strings("stars"), o.int("par", 1))
            "capture" -> Step.Capture(prompt, o.str("fen") ?: return null, o.int("par", 1))
            "puzzle" -> Step.Puzzle(prompt, o.str("fen") ?: return null, o.strings("moves"), o.str("pre"), o.str("last"), o.int("rating", 1000), o.strings("themes"))
            "mate" -> Step.Mate(prompt, o.str("fen") ?: return null, o.int("n", 1))
            "goal" -> Step.GoalStep(prompt, o.str("fen") ?: return null, Goal.parse(o.str("goal")) ?: return null)
            "quiz" -> Step.Quiz(prompt, o.str("fen"), o["options"].arr().map { Text.of(it) }, o.int("answer"), Text.of(o["explain"]), o.strings("marks"))
            "square" -> Step.Squares(prompt, o.int("count", 10), o["black"] == true)
            "best" -> Step.Best(prompt, o.str("fen") ?: return null, o.strings("accept"), Text.of(o["explain"]))
            "line" -> Step.Line(
                prompt,
                o.strings("moves"),
                o.str("side") != "black",
                o["notes"].obj().mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to Text.of(v) } }.toMap(),
            )
            "play" -> Step.Play(
                prompt,
                o.str("fen") ?: return null,
                PlayGoal.entries.firstOrNull { it.name.equals(o.str("goal"), ignoreCase = true) } ?: PlayGoal.WIN,
                o.int("moves", 50),
            )
            else -> null
        }
    }

    fun puzzles(json: String): List<RatedPuzzle> = Json.parse(json).arr().mapIndexed { i, p ->
        val o = p.obj()
        RatedPuzzle(
            id = o.int("id", i + 1),
            fen = o.str("fen").orEmpty(),
            moves = o.strings("moves"),
            pre = o.str("pre"),
            last = o.str("last"),
            rating = o.int("rating", 1000),
            themes = o.strings("themes"),
        )
    }
}
