package com.zorix.chess.speech

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.zorix.chess.coach.KurdishVoice
import com.zorix.chess.coach.VoiceText
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The neural voices (sherpa-onnx + Piper), running in their own process (":voice"). If the native
 * speech engine ever fails, only this process ends; the app keeps running and [AndroidSpeech] simply
 * stops using the voices.
 *
 * - Persian: the "ganji" voice with its espeak-ng pronunciation rules.
 * - Kurdish: the same voice, fed with Kurdish phonemes ([KurdishVoice]) through a lexicon.
 * - English: the "ljspeech" voice with espeak-ng.
 *
 * One engine is loaded at a time; switching the language releases the previous one.
 */
class VoiceService : Service() {
    private val generation = AtomicInteger()
    private val synth = Executors.newSingleThreadExecutor { r -> Thread(r, "zorix-voice") }
    private lateinit var inbox: HandlerThread
    private lateinit var messenger: Messenger

    // Only touched on [synth].
    private var tts: OfflineTts? = null
    private var ttsLang: String? = null
    private var kurdishKeys: MutableSet<String>? = null
    private var kurdishDir: File? = null
    private var voiceTokens: Set<Char>? = null

    @Volatile private var track: AudioTrack? = null

    override fun onCreate() {
        super.onCreate()
        inbox = HandlerThread("zorix-voice-inbox").apply { start() }
        messenger = Messenger(Inbox(inbox.looper))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        stopAudio()
        synth.execute {
            releaseEngine()
            runCatching { track?.release() }
            track = null
        }
        synth.shutdown()
        inbox.quitSafely()
        super.onDestroy()
    }

    private inner class Inbox(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            val lang = msg.data?.getString(KEY_LANG) ?: "fa"
            when (msg.what) {
                MSG_PREPARE -> synth.execute { runCatching { engine(lang) } }
                MSG_SPEAK -> {
                    stopAudio()
                    val id = generation.get()
                    val text = msg.data?.getString(KEY_TEXT).orEmpty()
                    synth.execute { speak(text, lang, id) }
                }
                MSG_STOP -> stopAudio()
            }
        }
    }

    private fun stopAudio() {
        generation.incrementAndGet()
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    /** Runs on [synth]: one sentence at a time, each played as soon as it is ready. */
    private fun speak(text: String, lang: String, id: Int) {
        try {
            val prepared = if (lang == "fa") VoiceText.forPersianVoice(text) else text
            for (sentence in VoiceText.sentences(prepared)) {
                if (id != generation.get()) return
                val input: String
                val engine: OfflineTts
                if (lang == "ckb") {
                    val words = kurdishWords(sentence)
                    if (words.isEmpty()) continue
                    engine = kurdishEngine(words) ?: return
                    input = KurdishVoice.engineText(words)
                } else {
                    engine = engine(lang) ?: return
                    input = sentence
                }
                val audio = engine.generate(input, sid = 0, speed = SPEED)
                if (id != generation.get()) return
                val out = audioTrack(audio.sampleRate) ?: return
                if (out.playState != AudioTrack.PLAYSTATE_PLAYING) out.play()
                // Blocks while playing; stopAudio() pauses the track, which returns early.
                out.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "speech failed", e)
        }
    }

    /** The engine for [lang], loading it (and releasing another language's engine) when needed. */
    private fun engine(lang: String): OfflineTts? {
        if (ttsLang == lang) tts?.let { return it }
        releaseEngine()
        return try {
            load(lang).also {
                tts = it
                ttsLang = lang
            }
        } catch (e: Throwable) {
            Log.e(TAG, "voice for $lang unavailable", e)
            null
        }
    }

    private fun releaseEngine() {
        runCatching { tts?.release() }
        tts = null
        ttsLang = null
    }

    private fun load(lang: String): OfflineTts {
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        if (lang == "ckb") {
            // The Kurdish lexicon is written at run time, so this engine reads files, not APK assets.
            val dir = kurdishFiles()
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(dir, "model.onnx").absolutePath,
                        tokens = File(dir, "tokens.txt").absolutePath,
                        lexicon = File(dir, LEXICON).absolutePath,
                    ),
                    numThreads = threads,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            )
            return OfflineTts(null, config)
        }
        val voice = if (lang == "en") EN_DIR else FA_DIR
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$voice/model.onnx",
                    tokens = "$voice/tokens.txt",
                    dataDir = unpackEspeakData().absolutePath,
                ),
                numThreads = threads,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return OfflineTts(assets, config)
    }

    // ------------------------------------------------------------------------------ Kurdish

    /** The Kurdish words of a sentence, keeping only phonemes the voice has. */
    private fun kurdishWords(sentence: String): List<KurdishVoice.Word> {
        val known = tokenSet()
        return KurdishVoice.words(sentence)
            .map { it.copy(ipa = it.ipa.filter { c -> c in known }) }
            .filter { it.ipa.isNotEmpty() }
    }

    /** The Kurdish engine, after adding any word its lexicon does not have yet (then it is reloaded). */
    private fun kurdishEngine(words: List<KurdishVoice.Word>): OfflineTts? {
        val dir = kurdishFiles()
        val keys = kurdishKeys ?: loadKeys(dir).also { kurdishKeys = it }
        val missing = words.distinctBy(KurdishVoice::key).filter { KurdishVoice.key(it) !in keys }
        if (missing.isNotEmpty()) {
            File(dir, LEXICON).appendText(missing.joinToString("") { KurdishVoice.lexiconLine(it) + "\n" })
            missing.forEach { keys += KurdishVoice.key(it) }
            if (ttsLang == "ckb") releaseEngine()
        }
        return engine("ckb")
    }

    private fun loadKeys(dir: File): MutableSet<String> =
        File(dir, LEXICON).readLines().mapNotNullTo(HashSet()) { line -> line.substringBefore(' ').takeIf { it.isNotEmpty() } }

    /**
     * The Kurdish voice files: the Persian model and tokens copied out of the APK once, and a lexicon
     * with the phonemes of every Kurdish word of the app (in five forms: plain and before , . ? !).
     */
    private fun kurdishFiles(): File {
        kurdishDir?.let { return it }
        val dir = File(filesDir, "voice-ckb")
        val stamp = File(dir, "version")
        val words = runCatching { assets.open(CKB_WORDS).bufferedReader().use { it.readText() } }.getOrDefault("")
        val wanted = "${assetVersion()}|$LEXICON_FORMAT|${words.hashCode()}"
        if (stamp.isFile && stamp.readText().trim() == wanted && File(dir, LEXICON).isFile && File(dir, "model.onnx").isFile) {
            kurdishDir = dir
            return dir
        }
        kurdishKeys = null
        if (ttsLang == "ckb") releaseEngine()
        dir.deleteRecursively()
        dir.mkdirs()
        copyAsset("$FA_DIR/model.onnx", File(dir, "model.onnx"))
        copyAsset("$FA_DIR/tokens.txt", File(dir, "tokens.txt"))
        val known = tokenSet()
        val lines = LinkedHashMap<String, String>()
        lines[KurdishVoice.BOS_KEY] = "${KurdishVoice.BOS_KEY} ^"
        lines[KurdishVoice.EOS_KEY] = "${KurdishVoice.EOS_KEY} $"
        val seeds = words.lines() + (0..100).flatMap { KurdishVoice.numberWords(it).split(' ') } + listOf("Zorix")
        for (word in seeds) {
            val w = word.trim()
            if (w.isEmpty()) continue
            val ipa = (if (w.all { it.code < 128 }) KurdishVoice.latinIpa(w) else KurdishVoice.wordIpa(w)).filter { it in known }
            if (ipa.isEmpty()) continue
            for (p in listOf("", ",", ".", "?", "!")) {
                val entry = KurdishVoice.Word(ipa, p)
                lines.getOrPut(KurdishVoice.key(entry)) { KurdishVoice.lexiconLine(entry) }
            }
        }
        File(dir, LEXICON).writeText(lines.values.joinToString("\n", postfix = "\n"))
        stamp.writeText(wanted)
        kurdishDir = dir
        return dir
    }

    /** The phoneme symbols the voice knows (from its tokens.txt), so no unknown symbol reaches it. */
    private fun tokenSet(): Set<Char> = voiceTokens ?: assets.open("$FA_DIR/tokens.txt").bufferedReader().useLines { lines ->
        lines.mapNotNull { line -> line.trimEnd().substringBeforeLast(' ').singleOrNull() }.filter { it != ' ' }.toSet()
    }.also { voiceTokens = it }

    // ------------------------------------------------------------------------------ files

    private fun assetVersion(): String = assets.open("$FA_DIR/version").bufferedReader().use { it.readText().trim() }

    /** espeak-ng (the pronunciation rules) reads its data from files, so it is copied out of the APK once. */
    private fun unpackEspeakData(): File {
        val root = File(filesDir, "voice-fa")
        val stamp = File(root, "version")
        val wanted = assetVersion()
        val data = File(root, "espeak-ng-data")
        if (stamp.isFile && stamp.readText().trim() == wanted && data.isDirectory) return data
        root.deleteRecursively()
        copyAssets("$FA_DIR/espeak-ng-data", data)
        stamp.writeText(wanted)
        return data
    }

    private fun copyAsset(path: String, dest: File) {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        assets.open(path).use { input -> part.outputStream().use { input.copyTo(it, 1 shl 16) } }
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
    }

    private fun copyAssets(path: String, dest: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return
        }
        dest.mkdirs()
        for (child in children) copyAssets("$path/$child", File(dest, child))
    }

    private fun audioTrack(sampleRate: Int): AudioTrack? {
        track?.let { if (it.sampleRate == sampleRate && it.state == AudioTrack.STATE_INITIALIZED) return it else it.release() }
        val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val created = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(min, sampleRate / 5 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull()?.takeIf { it.state == AudioTrack.STATE_INITIALIZED }
        track = created
        return created
    }

    companion object {
        private const val TAG = "ZorixVoice"
        const val FA_DIR = "voice/fa"
        const val EN_DIR = "voice/en"
        private const val CKB_WORDS = "voice/ckb-words.txt"
        private const val LEXICON = "lexicon.txt"

        /** Bump when [KurdishVoice] changes how it writes phonemes, so stored lexicons are rebuilt. */
        private const val LEXICON_FORMAT = 1
        private const val SPEED = 0.95f

        const val MSG_PREPARE = 1
        const val MSG_SPEAK = 2
        const val MSG_STOP = 3
        const val KEY_TEXT = "text"
        const val KEY_LANG = "lang"
    }
}
