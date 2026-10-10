package com.zorix.chess.controller

import com.zorix.chess.learn.Course
import com.zorix.chess.learn.CurriculumParser
import com.zorix.chess.learn.Json
import com.zorix.chess.learn.Lesson
import com.zorix.chess.learn.LessonResult
import com.zorix.chess.learn.LessonSession
import com.zorix.chess.learn.arr
import com.zorix.chess.learn.obj
import com.zorix.chess.play.PlayController
import com.zorix.chess.play.ReviewController
import com.zorix.chess.platform.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the player is in the curriculum. */
data class LearnProgress(val done: Int, val total: Int, val stars: Int)

/**
 * Everything the app needs, created once per process (Android ViewModel / iOS app object):
 * the player profile, the shared engine, the analysis board, play mode, game review,
 * the puzzle trainer and the curriculum.
 */
class AppController(
    private val scope: CoroutineScope,
    host: EngineHost,
    private val store: KeyValueStore,
    val speech: Speech = Speech.None,
    /** Reads a bundled file (Compose resources); injected so tests can use plain files. */
    private val readFile: suspend (String) -> ByteArray,
) {
    val profile = ProfileStore(store)
    val board = ChessController(scope, host, store, profile = profile, speech = speech)
    val hub: EngineHub get() = board.hub
    val play = PlayController(scope, hub, profile, store, { board.state.value.settings }, speech)
    val review = ReviewController(scope, hub, profile)
    val puzzles = PuzzleTrainer(scope, profile, store)

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _contentError = MutableStateFlow<String?>(null)
    val contentError: StateFlow<String?> = _contentError.asStateFlow()

    var language: String = "en"
        private set

    /** The coach's voice: preparing (a loading indicator is shown), speaking or idle. */
    val speechStatus: StateFlow<SpeechStatus> get() = speech.status

    init {
        loadContent()
    }

    fun setLanguage(lang: String) {
        language = lang
        board.language = lang
        play.language = lang
        review.language = lang
        puzzles.language = lang
        if (board.state.value.settings.voice) speech.prepare(speechLanguage(lang))
    }

    private fun loadContent() {
        scope.launch {
            try {
                val loaded = withContext(ioDispatcher) {
                    val index = Json.parse(readFile("files/learn/index.json").decodeToString()).obj()["courses"].arr().mapNotNull { it as? String }
                    val courses = index.map { id -> CurriculumParser.course(readFile("files/learn/$id.json").decodeToString()) }
                    val bank = CurriculumParser.puzzles(readFile("files/puzzles.json").decodeToString())
                    courses to bank
                }
                _courses.value = loaded.first
                puzzles.load(loaded.second)
            } catch (e: Exception) {
                _contentError.value = e.message ?: e.toString()
            }
        }
    }

    fun startLesson(lesson: Lesson): LessonSession = LessonSession(lesson, scope, hub, language, speechLanguage(language))

    fun completeLesson(result: LessonResult) {
        profile.recordLesson(result.lesson.id, result.stars)
    }

    fun progress(course: Course): LearnProgress {
        val stars = profile.current.lessonStars
        val lessons = course.lessons
        return LearnProgress(lessons.count { it.id in stars }, lessons.size, lessons.sumOf { stars[it.id] ?: 0 })
    }

    /** The next lesson to study: the first unfinished one, following the course order. */
    fun nextLesson(): Pair<Course, Lesson>? {
        val done = profile.current.lessonStars
        for (c in _courses.value) {
            c.lessons.firstOrNull { it.id !in done }?.let { return c to it }
        }
        return null
    }

    fun speak(text: String) {
        val lang = speechLanguage(language)
        if (board.state.value.settings.voice && text.isNotBlank() && speech.supports(lang)) {
            speech.stop()
            speech.speak(text, lang)
        }
    }

    /** Whether new coach texts will be read aloud with these [settings] in the current language. */
    fun voiceReady(settings: Settings): Boolean = settings.voice && speech.supports(speechLanguage(language))

    fun stopSpeaking() = speech.stop()

    fun onBackground() {
        board.onBackground()
        speech.stop()
    }

    fun onForeground() = board.onForeground()

    fun close() {
        speech.stop()
        board.close()
    }
}
