package com.zorix.chess.controller

import com.zorix.chess.engine.uci.EngineConnection
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Board colour schemes offered in the settings. */
enum class BoardThemeId { CLASSIC, WALNUT, GREEN, BLUE, GRAPHITE }

/** App appearance: the dark Zorix style (black and red) or the light sky-blue style. */
enum class AppThemeId { ZORIX, SKY }

/** User preferences. Everything is stored locally; nothing ever leaves the device. */
data class Settings(
    /** Think time for "Best move", like the slider of the desktop version (0.5 s - 30 s). */
    val thinkTimeMs: Int = DEFAULT_THINK_MS,
    val threads: Int = 2,
    val hashMb: Int = 64,
    /** Principal variations shown by live analysis (MultiPV). */
    val analysisLines: Int = 3,
    val boardTheme: BoardThemeId = BoardThemeId.CLASSIC,
    val showCoordinates: Boolean = true,
    val showLegalMoves: Boolean = true,
    val showArrows: Boolean = true,
    val animateMoves: Boolean = true,
    val haptics: Boolean = true,
    /** Coach mode: every move played on the board is rated (best / good / mistake / blunder...). */
    val coachMode: Boolean = true,
    /** App language: null follows the system, otherwise "en", "fa" or "ckb". */
    val language: String? = null,
    /** The coach reads its explanations aloud. */
    val voice: Boolean = true,
    /** Zorix explains its own moves in play mode. */
    val explainBotMoves: Boolean = true,
    val appTheme: AppThemeId = AppThemeId.ZORIX,
) {
    companion object {
        const val DEFAULT_THINK_MS = 2000
        const val MIN_THINK_MS = 500
        const val MAX_THINK_MS = 30_000
        const val THINK_STEP_MS = 500
        val HASH_CHOICES = listOf(16, 32, 64, 128, 256)
        const val MAX_LINES = 3
        val LANGUAGES = listOf("en", "fa", "ckb")

        fun defaultThreads(cores: Int): Int = (cores - 1).coerceIn(1, 4)

        fun load(store: KeyValueStore, cores: Int): Settings {
            val d = Settings(threads = defaultThreads(cores))
            fun int(key: String, def: Int) = store.getString(key)?.toIntOrNull() ?: def
            fun bool(key: String, def: Boolean) = store.getString(key)?.toBooleanStrictOrNull() ?: def
            return Settings(
                thinkTimeMs = int("thinkTimeMs", d.thinkTimeMs).coerceIn(MIN_THINK_MS, MAX_THINK_MS),
                threads = int("threads", d.threads).coerceIn(1, cores.coerceAtLeast(1)),
                hashMb = int("hashMb", d.hashMb).takeIf { it in HASH_CHOICES } ?: d.hashMb,
                analysisLines = int("analysisLines", d.analysisLines).coerceIn(1, MAX_LINES),
                boardTheme = store.getString("boardTheme")
                    ?.let { name -> BoardThemeId.entries.firstOrNull { it.name == name } } ?: d.boardTheme,
                showCoordinates = bool("showCoordinates", d.showCoordinates),
                showLegalMoves = bool("showLegalMoves", d.showLegalMoves),
                showArrows = bool("showArrows", d.showArrows),
                animateMoves = bool("animateMoves", d.animateMoves),
                haptics = bool("haptics", d.haptics),
                coachMode = bool("coachMode", d.coachMode),
                language = store.getString("language")?.takeIf { it in LANGUAGES },
                voice = bool("voice", d.voice),
                explainBotMoves = bool("explainBotMoves", d.explainBotMoves),
                appTheme = store.getString("appTheme")
                    ?.let { name -> AppThemeId.entries.firstOrNull { it.name == name } } ?: d.appTheme,
            )
        }
    }

    fun save(store: KeyValueStore) {
        store.putString("thinkTimeMs", thinkTimeMs.toString())
        store.putString("threads", threads.toString())
        store.putString("hashMb", hashMb.toString())
        store.putString("analysisLines", analysisLines.toString())
        store.putString("boardTheme", boardTheme.name)
        store.putString("showCoordinates", showCoordinates.toString())
        store.putString("showLegalMoves", showLegalMoves.toString())
        store.putString("showArrows", showArrows.toString())
        store.putString("animateMoves", animateMoves.toString())
        store.putString("haptics", haptics.toString())
        store.putString("coachMode", coachMode.toString())
        store.putString("language", language)
        store.putString("voice", voice.toString())
        store.putString("explainBotMoves", explainBotMoves.toString())
        store.putString("appTheme", appTheme.name)
    }
}

/** What the coach's voice is doing: shown as a loading indicator until the voice starts. */
enum class SpeechStatus { IDLE, PREPARING, SPEAKING }

private val idleSpeech: StateFlow<SpeechStatus> = MutableStateFlow(SpeechStatus.IDLE)

/** Text-to-speech for the coach's voice (neural voices; the system voice as a fallback). */
interface Speech {
    /** True when a voice for [lang] is installed and ready. */
    fun supports(lang: String): Boolean
    fun speak(text: String, lang: String)
    fun stop()

    /** Loads the voice for [lang] in the background so the first sentence starts without delay. */
    fun prepare(lang: String) = Unit

    /** Preparing while the audio of the last [speak] is being made, speaking while it plays. */
    val status: StateFlow<SpeechStatus> get() = idleSpeech

    object None : Speech {
        override fun supports(lang: String) = false
        override fun speak(text: String, lang: String) = Unit
        override fun stop() = Unit
    }
}

/**
 * Language of the spoken text for a display language. Every language speaks its own text; on Android
 * Kurdish is said by the Persian voice from Kurdish phonemes (see KurdishVoice).
 */
fun speechLanguage(displayLang: String): String = displayLang

/** Minimal persistent storage (SharedPreferences on Android, a map in tests). */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

class InMemoryStore : KeyValueStore {
    private val lock = SynchronizedObject()
    private val map = HashMap<String, String>()
    override fun getString(key: String): String? = synchronized(lock) { map[key] }
    override fun putString(key: String, value: String?) {
        synchronized(lock) { if (value == null) map.remove(key) else map[key] = value }
    }
}

/** One way to start the engine. */
class EngineLaunch(
    /** Short build description shown in the settings, e.g. "armv8.2 dotprod". */
    val label: String,
    val connect: () -> EngineConnection,
)

/** Platform side of the engine: installs files and says how to run Stockfish. */
interface EngineHost {
    /**
     * Makes sure the engine files are in place (copying the network on first launch) and returns
     * launch candidates, fastest first. [onProgress] receives 0..1 while files are copied.
     */
    suspend fun prepare(onProgress: (Float) -> Unit): List<EngineLaunch>

    /** Number of CPU cores available to the engine. */
    val cpuCores: Int
}
