@file:OptIn(ExperimentalJsExport::class)

package com.zorix.chess.web

import com.zorix.chess.coach.KurdishVoice
import com.zorix.chess.coach.Speakable
import com.zorix.chess.coach.VoiceText
import com.zorix.chess.controller.AppController
import com.zorix.chess.controller.speechLanguage
import com.zorix.chess.controller.AppThemeId
import com.zorix.chess.controller.BoardThemeId
import com.zorix.chess.controller.ChessController
import com.zorix.chess.controller.Experience
import com.zorix.chess.controller.PuzzleMode
import com.zorix.chess.controller.Settings
import com.zorix.chess.core.Game
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.learn.LessonSession
import com.zorix.chess.play.Bots
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.js.Promise
import kotlin.js.json

/**
 * The app's controllers for the website: analysis board with the coach, games against the 20 Zorix
 * levels, game review, rated puzzles and the 322 lessons, with the same rules, explanations and
 * texts (en / fa / ckb) as the app.
 *
 * The page subscribes once with [subscribe] and receives every state change as a plain object on a
 * named channel: "board", "play", "review", "puzzles", "lesson", "lessonResult", "profile",
 * "courses", "speech", "message", "contentError".
 */
@JsExport
class ZorixCore(
    createEngine: (onLine: (String?) -> Unit) -> JsEngine,
    storage: JsStorage,
    voice: JsVoice,
    readFile: (String) -> Promise<String>,
    cores: Int,
) {
    private val scope = MainScope()
    private val speech = WebSpeech(voice)
    private val app = AppController(scope, WebEngineHost(createEngine, cores), WebStore(storage), speech) { path ->
        readFile(path).await().encodeToByteArray()
    }
    private var listener: ((String, dynamic) -> Unit)? = null
    private var lesson: LessonSession? = null
    private var lessonJob: Job? = null

    /** Receives every state change: [channel] names the part of the app, [state] is a plain object. */
    fun subscribe(listener: (channel: String, state: dynamic) -> Unit) {
        this.listener = listener
        scope.launch { app.board.state.collectLatest { emit("board", board(it)) } }
        scope.launch { app.play.state.collectLatest { emit("play", play(it)) } }
        scope.launch { app.review.state.collectLatest { emit("review", reviewState(it)) } }
        scope.launch { app.puzzles.state.collectLatest { emit("puzzles", puzzle(it, app.puzzles.themes)) } }
        scope.launch { app.profile.profile.collectLatest { emit("profile", profile(it)) } }
        scope.launch { app.courses.collectLatest { emitCourses() } }
        scope.launch { app.speechStatus.collectLatest { emit("speech", it.name.lowercase()) } }
        scope.launch { app.board.messages.collect { emit("message", it.name.lowercase()) } }
        scope.launch { app.contentError.collectLatest { emit("contentError", it) } }
    }

    private fun emit(channel: String, state: dynamic) {
        listener?.invoke(channel, state)
    }

    private fun emitCourses() {
        val lang = app.language
        emit("courses", arr(app.courses.value.map { c ->
            val p = app.progress(c)
            val o = course(c, lang)
            o["done"] = p.done
            o["total"] = p.total
            o["stars"] = p.stars
            o
        }))
    }

    // ------------------------------------------------------------------ app

    /** The display language: "en", "fa" or "ckb" (the coach speaks and writes in it). */
    fun setLanguage(lang: String) {
        app.setLanguage(lang)
        emitCourses()
        lesson?.let { s -> emit("lesson", step(s.ui.value, s.lesson, app.language)) }
    }

    /** Which screen is visible: only that one may use the engine in the background or talk. */
    fun setScreen(screen: String) {
        app.stopSpeaking()
        app.board.setVisible(screen == "analysis")
        app.play.visible = screen == "play"
    }

    fun onBackground() = app.onBackground()
    fun onForeground() = app.onForeground()

    fun speak(text: String) = app.speak(text)

    /** Reads a display text (lesson, explanation) aloud, with chess notation turned into words. */
    fun speakDisplay(text: String) = app.speak(Speakable.of(text, speechLanguage(app.language)))
    fun stopSpeaking() = app.stopSpeaking()

    /** Whether new coach texts will be read aloud (voice on and available for the language). */
    fun voiceReady(): Boolean = app.voiceReady(app.board.state.value.settings)

    /**
     * What the Kurdish voice (Vekol, which reads Sorani letters) says for [text], piece by piece, as in
     * the app: sentences (a very long one split at its commas) in the letters the voice knows ([letters]).
     */
    fun kurdishPieces(text: String, letters: String): Array<String> {
        val known = letters.toSet()
        return VoiceText.sentences(text).flatMap { KurdishVoice.chunks(it, max = 200, firstMax = 220) }
            .map { KurdishVoice.speechText(it, known) }.filter { it.isNotEmpty() }.toTypedArray()
    }

    fun voiceStarted(token: Int) = speech.started(token)
    fun voiceDone(token: Int) = speech.done(token)

    fun close() {
        lesson?.close()
        app.close()
        scope.cancel()
    }

    // ------------------------------------------------------------------ profile

    fun completeOnboarding(name: String, experience: String) {
        val exp = Experience.entries.firstOrNull { it.name.equals(experience, ignoreCase = true) } ?: Experience.RULES
        app.profile.completeOnboarding(name, exp)
    }

    fun rename(name: String) = app.profile.rename(name)

    // ------------------------------------------------------------------ settings

    /** Changes one setting; [value] is the text form ("true", "2000", "sky"...). */
    fun setSetting(key: String, value: String) {
        app.board.updateSettings { s -> applySetting(s, key, value) }
    }

    private fun applySetting(s: Settings, key: String, value: String): Settings = when (key) {
        "thinkTimeMs" -> s.copy(thinkTimeMs = value.toIntOrNull()?.coerceIn(Settings.MIN_THINK_MS, Settings.MAX_THINK_MS) ?: s.thinkTimeMs)
        "hashMb" -> s.copy(hashMb = value.toIntOrNull()?.takeIf { it in Settings.HASH_CHOICES } ?: s.hashMb)
        "analysisLines" -> s.copy(analysisLines = value.toIntOrNull()?.coerceIn(1, Settings.MAX_LINES) ?: s.analysisLines)
        "boardTheme" -> s.copy(boardTheme = BoardThemeId.entries.firstOrNull { it.name.equals(value, true) } ?: s.boardTheme)
        "appTheme" -> s.copy(appTheme = AppThemeId.entries.firstOrNull { it.name.equals(value, true) } ?: s.appTheme)
        "showCoordinates" -> s.copy(showCoordinates = value == "true")
        "showLegalMoves" -> s.copy(showLegalMoves = value == "true")
        "showArrows" -> s.copy(showArrows = value == "true")
        "animateMoves" -> s.copy(animateMoves = value == "true")
        "coachMode" -> s.copy(coachMode = value == "true")
        "voice" -> s.copy(voice = value == "true")
        "explainBotMoves" -> s.copy(explainBotMoves = value == "true")
        "language" -> s.copy(language = value.takeIf { it in Settings.LANGUAGES })
        else -> s
    }

    // ------------------------------------------------------------------ analysis board

    fun boardMove(from: String, to: String) = app.board.onUserMove(parseSquare(from), parseSquare(to))
    fun boardPromote(piece: String) = pieceOf(piece)?.let(app.board::onPromotionChosen) ?: app.board.onPromotionCancelled()
    fun boardUndo() = app.board.undo()
    fun boardRedo() = app.board.redo()
    fun boardGoTo(plies: Int) = app.board.goTo(plies)
    fun boardNew() = app.board.newGame()
    fun boardFlip() = app.board.flipBoard()

    /** Loads a FEN; returns null when it worked, otherwise "malformed" or the position problem. */
    fun boardLoadFen(fen: String): String? = when (val e = app.board.loadFen(fen)) {
        null -> null
        ChessController.FenError.Malformed -> "malformed"
        is ChessController.FenError.Invalid -> e.problem.name.lowercase()
    }

    /** A game (start FEN and UCI moves) on the analysis board, e.g. an online game to study. */
    fun boardLoadGame(startFen: String, moves: Array<String>) = app.board.loadGame(Game.restore(startFen, moves.toList()))

    fun boardPgn(): String = app.board.pgn()
    fun boardFen(): String = app.board.fen()
    fun boardBestMove() = app.board.requestBestMove()
    fun boardStop() = app.board.stopThinking()
    fun boardPlaySuggestion() = app.board.playSuggestion()
    fun boardDismissSuggestion() = app.board.dismissSuggestion()
    fun boardAnalysis(on: Boolean) = app.board.setAnalysis(on)
    fun boardPlayLine(index: Int) = app.board.playLine(index)
    fun boardSpeakFeedback() = app.board.speakLastFeedback()
    fun boardSpeakHint() = app.board.speakHintExplanation()

    /** Puts a built position (puzzle builder) on the analysis board. */
    fun boardSetPosition(fen: String): Boolean {
        val p = runCatching { Position.fromFen(fen) }.getOrNull() ?: return false
        if (p.problem() != null) return false
        app.board.setPosition(p)
        return true
    }

    /** Why a built position cannot be analysed ("opponent_in_check", "missing_king"...), or null. */
    fun positionProblem(fen: String): String? {
        val p = runCatching { Position.fromFen(fen) }.getOrNull() ?: return "malformed"
        return p.problem()?.name?.lowercase()
    }

    // ------------------------------------------------------------------ rules (for the board's move hints)

    /** Squares the piece on [from] may move to in [fen] (the app's rules; positions without kings work too). */
    fun legalTargets(fen: String, from: String): Array<String> {
        val p = runCatching { Position.fromFen(fen) }.getOrNull() ?: return emptyArray()
        val f = parseSquare(from)
        return p.legalMovesFrom(f).mapNotNull { sq(it.to) }.distinct().toTypedArray()
    }

    /** Whether moving [from] to [to] in [fen] promotes a pawn (the board then asks for the piece). */
    fun isPromotion(fen: String, from: String, to: String): Boolean {
        val p = runCatching { Position.fromFen(fen) }.getOrNull() ?: return false
        return p.isPromotion(parseSquare(from), parseSquare(to))
    }

    // ------------------------------------------------------------------ play against Zorix

    /** The 20 levels: level, Elo, tier. */
    fun bots(): Array<dynamic> = Bots.all.map(::bot).toTypedArray()

    fun recommendedLevel(): Int = Bots.recommended(app.profile.current.rating).level

    /** [side]: "white", "black" or "random". */
    fun playStart(level: Int, side: String) = app.play.start(level, sideOf(side))
    fun playRematch() = app.play.rematch()
    fun playClose() = app.play.close()
    fun playMove(from: String, to: String) = app.play.onUserMove(parseSquare(from), parseSquare(to))
    fun playPromote(piece: String) = pieceOf(piece)?.let(app.play::onPromotionChosen) ?: app.play.onPromotionCancelled()
    fun playTakeback() = app.play.takeback()
    fun playHint() = app.play.hint()
    fun playResign() = app.play.resign()
    fun playOfferDraw() = app.play.offerDraw()
    fun playSpeakAgain() = app.play.speakAgain()

    // ------------------------------------------------------------------ game review

    /** Reviews the current game against Zorix. */
    fun reviewPlayGame() {
        val st = app.play.state.value
        app.review.start(st.game, st.userSide)
    }

    /** Reviews a game given as start FEN and UCI moves (an online game); [player] "white", "black" or "". */
    fun reviewGame(startFen: String, moves: Array<String>, player: String) =
        app.review.start(Game.restore(startFen, moves.toList()), sideOf(player))

    fun reviewGoTo(ply: Int) = app.review.goTo(ply)
    fun reviewCancel() = app.review.cancel()

    fun reviewRetry(index: Int) {
        val moment = app.review.state.value.review?.moves?.getOrNull(index) ?: return
        app.review.startRetry(moment)
    }

    fun reviewEndRetry() = app.review.endRetry()
    fun reviewRetryMove(uci: String) = parseMove(uci)?.let(app.review::onRetryMove)

    // ------------------------------------------------------------------ puzzles

    /** [mode]: "rated" or "streak"; [theme] e.g. "fork", or null for all. */
    fun puzzleStart(mode: String, theme: String?) = app.puzzles.start(if (mode == "streak") PuzzleMode.STREAK else PuzzleMode.RATED, theme)
    fun puzzleNext() = app.puzzles.next()
    fun puzzleMove(from: String, to: String) = app.puzzles.onMove(parseSquare(from), parseSquare(to))
    fun puzzlePromote(piece: String) = app.puzzles.onPromotion(pieceOf(piece))
    fun puzzleHint() = app.puzzles.hint()
    fun puzzleSolution() = app.puzzles.showSolution()

    // ------------------------------------------------------------------ lessons

    fun lessonStart(courseId: String, lessonId: String): Boolean {
        val l = app.courses.value.firstOrNull { it.id == courseId }?.lessons?.firstOrNull { it.id == lessonId } ?: return false
        lessonClose()
        val s = app.startLesson(l)
        lesson = s
        lessonJob = scope.launch {
            launch { s.ui.collectLatest { emit("lesson", step(it, l, app.language)) } }
            launch {
                s.result.collectLatest { r ->
                    if (r != null) {
                        app.completeLesson(r)
                        emitCourses()
                    }
                    emit("lessonResult", r?.let { json("lessonId" to it.lesson.id, "mistakes" to it.mistakes, "stars" to it.stars) })
                }
            }
        }
        return true
    }

    fun lessonClose() {
        lessonJob?.cancel()
        lesson?.close()
        lesson = null
        emit("lesson", null)
    }

    fun lessonNext() = lesson?.next()
    fun lessonPrevious() = lesson?.previous()
    fun lessonRetry() = lesson?.retry()
    fun lessonMove(from: String, to: String) = lesson?.onMove(parseSquare(from), parseSquare(to))
    fun lessonPromote(piece: String) = lesson?.onPromotion(pieceOf(piece))
    fun lessonSquare(square: String) = lesson?.onSquare(parseSquare(square))
    fun lessonOption(index: Int) = lesson?.onOption(index)
    fun lessonHint() = lesson?.hint()
    fun lessonSolution() = lesson?.showSolution()

    // ------------------------------------------------------------------ helpers

    private fun pieceOf(piece: String): PieceType? = when (piece.lowercase()) {
        "q", "queen" -> PieceType.QUEEN
        "r", "rook" -> PieceType.ROOK
        "b", "bishop" -> PieceType.BISHOP
        "n", "knight" -> PieceType.KNIGHT
        else -> null
    }

    private fun sideOf(s: String): Side? = when (s) {
        "white" -> Side.WHITE
        "black" -> Side.BLACK
        else -> null
    }
}
