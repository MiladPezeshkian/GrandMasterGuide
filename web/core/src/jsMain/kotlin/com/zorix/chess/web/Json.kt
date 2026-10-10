package com.zorix.chess.web

import com.zorix.chess.coach.CoachMessage
import com.zorix.chess.controller.EngineLine
import com.zorix.chess.controller.EngineStatus
import com.zorix.chess.controller.EngineView
import com.zorix.chess.controller.HintState
import com.zorix.chess.controller.MoveFeedback
import com.zorix.chess.controller.PendingPromotion
import com.zorix.chess.controller.Profile
import com.zorix.chess.controller.Settings
import com.zorix.chess.controller.feedbackKey
import com.zorix.chess.core.Game
import com.zorix.chess.core.Move
import com.zorix.chess.core.Position
import com.zorix.chess.core.Squares
import com.zorix.chess.engine.uci.Score
import com.zorix.chess.learn.Course
import com.zorix.chess.learn.Lesson
import com.zorix.chess.learn.Step
import com.zorix.chess.learn.StepUi
import com.zorix.chess.play.Bot
import com.zorix.chess.play.GameReview
import com.zorix.chess.play.PlayState
import com.zorix.chess.play.RetryState
import com.zorix.chess.play.ReviewState
import com.zorix.chess.play.ReviewedMove
import com.zorix.chess.controller.PuzzleState
import com.zorix.chess.controller.ChessUiState
import com.zorix.chess.core.Side
import kotlin.js.json

/*
 * Plain JavaScript objects for the page (the page never sees Kotlin classes). Squares are names
 * ("e4"), moves UCI strings ("e2e4"), sides "white"/"black", enums their lower-case names.
 */

internal fun sq(square: Int?): String? = square?.takeIf { it in 0..63 }?.let(Squares::name)

internal fun side(s: Side): String = if (s == Side.WHITE) "white" else "black"

internal fun arr(items: Iterable<Any?>): Array<Any?> = items.toList().toTypedArray()

internal fun score(s: Score?): dynamic = s?.let { json("cp" to it.centipawns, "mate" to it.mate) }

internal fun position(p: Position?): dynamic = p?.let {
    val toMove = it.sideToMove
    json(
        "fen" to it.fen(),
        "turn" to side(toMove),
        "check" to it.isAttacked(it.kingSquare(toMove), toMove.opposite),
    )
}

internal fun game(g: Game, feedback: Map<String, MoveFeedback> = emptyMap()): dynamic = json(
    "startFen" to g.start.fen(),
    "fen" to g.position.fen(),
    "turn" to side(g.position.sideToMove),
    "check" to g.position.isAttacked(g.position.kingSquare(g.position.sideToMove), g.position.sideToMove.opposite),
    "plies" to arr(g.plies.map { p ->
        json(
            "uci" to p.move.uci,
            "san" to p.san,
            "fen" to p.after.fen(),
            "side" to side(p.before.sideToMove),
            "quality" to feedback[feedbackKey(p.before.fen(), p.move)]?.quality?.name?.lowercase(),
        )
    }),
    "redo" to g.fullLine.size - g.plies.size,
    "lastMove" to g.lastMove?.uci,
    "canUndo" to g.canUndo,
    "canRedo" to g.canRedo,
    "result" to g.status.result.pgn,
    "over" to g.status.isOver,
    "reason" to g.status.reason?.name?.lowercase(),
    "pgn" to g.toPgn(),
)

internal fun line(l: EngineLine): dynamic = json(
    "rank" to l.rank, "depth" to l.depth, "score" to score(l.score),
    "uci" to arr(l.uci), "san" to arr(l.san), "text" to l.text,
)

internal fun view(v: EngineView?): dynamic = v?.let {
    json(
        "fen" to it.fen, "lines" to arr(it.lines.map(::line)), "depth" to it.depth, "nodes" to it.nodes.toDouble(),
        "nps" to it.nps.toDouble(), "timeMs" to it.timeMs.toDouble(), "best" to it.bestMove?.uci, "bestSan" to it.bestSan,
        "finished" to it.finished, "score" to score(it.score),
    )
}

internal fun engine(e: EngineStatus): dynamic = when (e) {
    is EngineStatus.Preparing -> json("kind" to "preparing", "progress" to e.progress)
    EngineStatus.Starting -> json("kind" to "starting")
    is EngineStatus.Ready -> json("kind" to "ready", "name" to e.name, "build" to e.build)
    is EngineStatus.Failed -> json("kind" to "failed", "message" to e.message)
}

internal fun hint(h: HintState): dynamic = when (h) {
    HintState.Idle -> json("kind" to "idle")
    is HintState.Thinking -> json("kind" to "thinking", "fen" to h.fen, "startedAt" to h.startedAtMs.toDouble(), "budgetMs" to h.budgetMs, "view" to view(h.view))
    is HintState.Ready -> json("kind" to "ready", "view" to view(h.view))
}

internal fun feedback(f: MoveFeedback?): dynamic = f?.let {
    json(
        "san" to it.san, "quality" to it.quality.name.lowercase(), "bestSan" to it.bestSan,
        "scoreBefore" to score(it.scoreBefore), "scoreAfter" to score(it.scoreAfter),
        "message" to it.message, "speech" to it.speech, "best" to it.bestMove?.uci, "expectedSan" to it.expectedSan,
    )
}

internal fun promotion(p: PendingPromotion?): dynamic = p?.let { json("from" to sq(it.from), "to" to sq(it.to), "side" to side(it.side)) }

internal fun promotion(p: Pair<Int, Int>?, pos: Position?): dynamic = p?.let {
    json("from" to sq(it.first), "to" to sq(it.second), "side" to pos?.let { q -> side(q.sideToMove) })
}

internal fun settings(s: Settings): dynamic = json(
    "thinkTimeMs" to s.thinkTimeMs, "threads" to s.threads, "hashMb" to s.hashMb, "analysisLines" to s.analysisLines,
    "boardTheme" to s.boardTheme.name.lowercase(), "showCoordinates" to s.showCoordinates, "showLegalMoves" to s.showLegalMoves,
    "showArrows" to s.showArrows, "animateMoves" to s.animateMoves, "haptics" to s.haptics, "coachMode" to s.coachMode,
    "language" to s.language, "voice" to s.voice, "explainBotMoves" to s.explainBotMoves, "appTheme" to s.appTheme.name.lowercase(),
)

internal fun board(s: ChessUiState): dynamic = json(
    "game" to game(s.game, s.feedback),
    "flipped" to s.flipped,
    "promotion" to promotion(s.pendingPromotion),
    "engine" to engine(s.engine),
    "hint" to hint(s.hint),
    "analysisOn" to s.analysisOn,
    "view" to view(s.currentView),
    "settings" to settings(s.settings),
    "feedback" to feedback(s.lastFeedback),
    "coachBusy" to s.coachBusy,
    "hintExplanation" to s.hintExplanation?.let { json("fen" to it.fen, "display" to it.display, "speech" to it.speech) },
    "moveCounter" to s.moveCounter,
)

internal fun message(m: CoachMessage?): dynamic = m?.let {
    json("quality" to it.quality?.name?.lowercase(), "display" to it.display, "speech" to it.speech)
}

internal fun bot(b: Bot): dynamic = json("level" to b.level, "elo" to b.elo, "tier" to b.tier.name.lowercase())

internal fun play(s: PlayState): dynamic = json(
    "active" to s.active,
    "bot" to bot(s.bot),
    "userSide" to side(s.userSide),
    "game" to game(s.game, s.feedback),
    "promotion" to promotion(s.pendingPromotion),
    "botThinking" to s.botThinking,
    "botMessage" to message(s.botMessage),
    "feedback" to feedback(s.lastFeedback),
    "coachBusy" to s.coachBusy,
    "hint" to message(s.hint),
    "hintBusy" to s.hintBusy,
    "assists" to s.assists,
    "result" to s.result?.let {
        json(
            "outcome" to it.outcome.name.lowercase(), "reason" to it.reason?.name?.lowercase(), "resigned" to it.resigned,
            "drawAgreed" to it.drawAgreed, "ratingBefore" to it.ratingBefore, "ratingAfter" to it.ratingAfter,
        )
    },
    "drawDeclined" to s.drawDeclined,
    "moveCounter" to s.moveCounter,
    "userToMove" to s.userToMove,
)

internal fun reviewed(m: ReviewedMove): dynamic = json(
    "index" to m.index, "side" to side(m.side), "san" to m.san, "uci" to m.move.uci, "quality" to m.quality.name.lowercase(),
    "scoreBefore" to score(m.scoreBefore), "scoreAfter" to score(m.scoreAfter), "bestSan" to m.bestSan, "best" to m.bestMove?.uci,
    "accuracy" to m.accuracy, "message" to m.message, "speech" to m.speech,
)

internal fun review(r: GameReview?): dynamic = r?.let {
    fun counts(s: Side) = json(*(it.counts[s] ?: emptyMap()).entries.map { (q, n) -> q.name.lowercase() to n }.toTypedArray())
    json(
        "moves" to arr(it.moves.map(::reviewed)),
        "accuracy" to json("white" to it.accuracy[Side.WHITE], "black" to it.accuracy[Side.BLACK]),
        "counts" to json("white" to counts(Side.WHITE), "black" to counts(Side.BLACK)),
        "evalCurve" to arr(it.evalCurve),
        "opening" to it.opening,
        "keyMoments" to json(
            "white" to arr(it.keyMoments(Side.WHITE).map { m -> m.index }),
            "black" to arr(it.keyMoments(Side.BLACK).map { m -> m.index }),
        ),
    )
}

internal fun retry(r: RetryState?): dynamic = r?.let {
    json(
        "index" to it.moment.index, "fen" to it.position.fen(), "turn" to side(it.position.sideToMove), "checking" to it.checking,
        "solved" to it.solved, "message" to it.message, "tried" to it.tried?.uci,
    )
}

internal fun reviewState(s: ReviewState): dynamic = json(
    "game" to s.game?.let { game(it) },
    "player" to s.player?.let(::side),
    "progress" to s.progress,
    "review" to review(s.review),
    "ply" to s.ply,
    "retry" to retry(s.retry),
)

internal fun puzzle(s: PuzzleState, themes: List<String>): dynamic = json(
    "puzzle" to s.puzzle?.let { json("id" to it.id, "rating" to it.rating, "themes" to arr(it.themes)) },
    "position" to position(s.position),
    "lastMove" to s.lastMove?.uci,
    "flipped" to s.flipped,
    "outcome" to s.outcome.name.lowercase(),
    "interactive" to s.interactive,
    "hintSquare" to sq(s.hintSquare),
    "explanation" to s.explanation,
    "ratingChange" to s.ratingChange,
    "mode" to s.mode.name.lowercase(),
    "streak" to s.streak,
    "theme" to s.theme,
    "promotion" to promotion(s.pendingPromotion, s.position),
    "loaded" to s.loaded,
    "themes" to arr(themes),
)

internal fun profile(p: Profile): dynamic = json(
    "name" to p.name, "onboarded" to p.onboarded, "experience" to p.experience.name.lowercase(), "rating" to p.rating,
    "ratedGames" to p.ratedGames, "peakRating" to p.peakRating, "wins" to p.wins, "draws" to p.draws, "losses" to p.losses,
    "levelStars" to json(*p.levelStars.entries.map { (k, v) -> k.toString() to v }.toTypedArray()),
    "puzzleRating" to p.puzzleRating, "puzzlesSolved" to p.puzzlesSolved, "puzzlesTried" to p.puzzlesTried,
    "puzzleStreak" to p.puzzleStreak, "bestPuzzleStreak" to p.bestPuzzleStreak,
    "lessonStars" to json(*p.lessonStars.entries.map { (k, v) -> k to v }.toTypedArray()),
    "xp" to p.xp, "gamesPlayed" to p.gamesPlayed, "lessonsCompleted" to p.lessonsCompleted,
)

internal fun lessonInfo(l: Lesson, lang: String): dynamic = json(
    "id" to l.id, "title" to l.title[lang], "intro" to l.intro[lang], "level" to l.level, "exercises" to l.exercises, "steps" to l.steps.size,
)

internal fun course(c: Course, lang: String): dynamic = json(
    "id" to c.id,
    "title" to c.title[lang],
    "description" to c.description[lang],
    "chapters" to arr(c.chapters.map { ch -> json("id" to ch.id, "title" to ch.title[lang], "lessons" to arr(ch.lessons.map { lessonInfo(it, lang) })) }),
)

private fun stepType(step: Step): String = when (step) {
    is Step.Theory -> "theory"
    is Step.Stars -> "stars"
    is Step.Capture -> "capture"
    is Step.Puzzle -> "puzzle"
    is Step.Mate -> "mate"
    is Step.GoalStep -> "goal"
    is Step.Quiz -> "quiz"
    is Step.Squares -> "squares"
    is Step.Best -> "best"
    is Step.Line -> "line"
    is Step.Play -> "play"
}

internal fun step(ui: StepUi, lesson: Lesson, lang: String): dynamic {
    val s = ui.step
    return json(
        "lessonId" to lesson.id,
        "lessonTitle" to lesson.title[lang],
        "index" to ui.index,
        "count" to lesson.steps.size,
        "type" to stepType(s),
        "prompt" to s.prompt[lang],
        "par" to when (s) { is Step.Stars -> s.par; is Step.Capture -> s.par; else -> null },
        "squaresCount" to (s as? Step.Squares)?.count,
        "mateIn" to (s as? Step.Mate)?.n,
        "goal" to (s as? Step.GoalStep)?.goal?.name?.lowercase(),
        "playGoal" to (s as? Step.Play)?.goal?.name?.lowercase(),
        "options" to (s as? Step.Quiz)?.options?.map { it[lang] }?.let(::arr),
        "answer" to (s as? Step.Quiz)?.answer,
        "position" to position(ui.position),
        "flipped" to ui.flipped,
        "lastMove" to ui.lastMove?.uci,
        "stars" to arr(ui.stars.map(::sq)),
        "marks" to arr(ui.marks.map(::sq)),
        "arrows" to arr(ui.arrows.map { it.uci }),
        "hintSquare" to sq(ui.hintSquare),
        "interactive" to ui.interactive,
        "status" to ui.status.name.lowercase(),
        "message" to ui.message,
        "speech" to ui.speech,
        "moves" to ui.moves,
        "busy" to ui.busy,
        "target" to sq(ui.target),
        "round" to ui.round,
        "correct" to ui.correct,
        "chosen" to ui.chosen,
        "promotion" to promotion(ui.pendingPromotion, ui.position),
    )
}

internal fun parseSquare(name: String): Int = Squares.parse(name)

internal fun parseMove(uci: String): Move? = Move.fromUci(uci)
