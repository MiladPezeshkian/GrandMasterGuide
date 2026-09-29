package com.zorix.chess.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.zorix.chess.coach.VoiceText
import com.zorix.chess.controller.Speech
import java.io.File
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The coach's voice on Android.
 *
 * Persian (and Kurdish, read by the Persian voice) uses a neural Piper voice run by sherpa-onnx,
 * entirely on the device. English uses the system text-to-speech engine. Speech is synthesized one
 * sentence at a time on a worker thread and streamed to an [AudioTrack] by a second thread, so
 * the coach starts talking quickly even for long explanations.
 */
class AndroidSpeech(context: Context) : Speech {
    private val app = context.applicationContext
    private val generation = AtomicInteger()
    private val synth = Executors.newSingleThreadExecutor { r -> Thread(r, "zorix-voice").apply { priority = Thread.NORM_PRIORITY } }
    private val player = Executors.newSingleThreadExecutor { r -> Thread(r, "zorix-voice-out").apply { priority = Thread.MAX_PRIORITY } }

    // ---------------------------------------------------------------- neural Persian voice

    /** True when the voice files are inside the APK (they are left out if the build could not fetch them). */
    private val persianPacked: Boolean = runCatching { app.assets.list(VOICE_DIR)?.contains("model.onnx") == true }.getOrDefault(false)

    @Volatile private var persianFailed = false
    private var tts: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null

    // ---------------------------------------------------------------- system voice (English)

    @Volatile private var systemReady = false
    private val system: TextToSpeech = TextToSpeech(app) { status -> systemReady = status == TextToSpeech.SUCCESS }

    override fun supports(lang: String): Boolean = when (lang) {
        "fa" -> (persianPacked && !persianFailed) || systemSupports(Locale("fa", "IR"))
        else -> systemSupports(Locale.US)
    }

    override fun prepare(lang: String) {
        if (lang == "fa" && persianPacked && !persianFailed) synth.execute { engine() }
    }

    override fun speak(text: String, lang: String) {
        stop()
        val id = generation.get()
        if (lang == "fa" && persianPacked && !persianFailed) {
            val prepared = VoiceText.forPersianVoice(text)
            synth.execute { speakPersian(prepared, id) }
        } else if (systemReady) {
            val locale = if (lang == "fa") Locale("fa", "IR") else Locale.US
            runCatching {
                system.language = locale
                system.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zorix-$id")
            }
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        track?.let { t ->
            runCatching {
                t.pause()
                t.flush()
            }
        }
        if (systemReady) runCatching { system.stop() }
    }

    /** Releases the voices; called when the app closes. */
    fun shutdown() {
        stop()
        runCatching { system.shutdown() }
        synth.execute {
            tts?.release()
            tts = null
        }
        player.execute {
            track?.release()
            track = null
        }
        synth.shutdown()
        player.shutdown()
    }

    private fun systemSupports(locale: Locale): Boolean =
        systemReady && runCatching { system.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)

    /** Runs on [synth]: loads the voice on first use, then synthesizes sentence by sentence. */
    private fun speakPersian(text: String, id: Int) {
        if (id != generation.get()) return
        val engine = engine() ?: return
        val out = player.submit(Callable { audioTrack(engine.sampleRate()) }).get() ?: return
        for (sentence in VoiceText.sentences(text)) {
            if (id != generation.get()) return
            try {
                engine.generateWithCallback(sentence, sid = 0, speed = SPEED) { samples ->
                    if (id != generation.get()) return@generateWithCallback 0
                    val chunk = samples.copyOf()
                    player.execute {
                        if (id == generation.get()) {
                            if (out.playState != AudioTrack.PLAYSTATE_PLAYING) out.play()
                            out.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                        }
                    }
                    1
                }
            } catch (e: Throwable) {
                Log.e(TAG, "speech failed", e)
                return
            }
        }
    }

    /** Runs on [synth]: the loaded voice, loading it on first use. */
    private fun engine(): OfflineTts? {
        tts?.let { return it }
        if (persianFailed) return null
        return runCatching { load() }
            .onFailure { e ->
                Log.e(TAG, "Persian voice unavailable", e)
                persianFailed = true
            }
            .getOrNull()
            .also { tts = it }
    }

    private fun load(): OfflineTts {
        val dataDir = unpackEspeakData()
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$VOICE_DIR/model.onnx",
                    tokens = "$VOICE_DIR/tokens.txt",
                    dataDir = dataDir.absolutePath,
                ),
                numThreads = threads,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return OfflineTts(app.assets, config)
    }

    /** espeak-ng (the pronunciation rules) reads its data from files, so it is copied out of the APK once. */
    private fun unpackEspeakData(): File {
        val root = File(app.filesDir, "voice-fa")
        val stamp = File(root, "version")
        val wanted = app.assets.open("$VOICE_DIR/version").bufferedReader().use { it.readText().trim() }
        val data = File(root, "espeak-ng-data")
        if (stamp.isFile && stamp.readText().trim() == wanted && data.isDirectory) return data
        root.deleteRecursively()
        copyAssets("$VOICE_DIR/espeak-ng-data", data)
        stamp.writeText(wanted)
        return data
    }

    private fun copyAssets(path: String, dest: File) {
        val children = app.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            app.assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return
        }
        dest.mkdirs()
        for (child in children) copyAssets("$path/$child", File(dest, child))
    }

    /** Runs on [player]. */
    private fun audioTrack(sampleRate: Int): AudioTrack? {
        track?.let { if (it.sampleRate == sampleRate) return it else it.release() }
        return runCatching {
            val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
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
        }.onFailure { Log.e(TAG, "no audio output", it) }.getOrNull().also { track = it }
    }

    private companion object {
        const val TAG = "ZorixSpeech"
        const val VOICE_DIR = "voice/fa"
        const val SPEED = 1.0f
    }
}
