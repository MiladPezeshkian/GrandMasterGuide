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
import com.zorix.chess.coach.VoiceText
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The neural Persian voice (sherpa-onnx + Piper), running in its own process (":voice").
 * If the native speech engine ever fails, only this process ends; the app keeps running
 * and [AndroidSpeech] simply stops using the voice.
 */
class VoiceService : Service() {
    private val generation = AtomicInteger()
    private val synth = Executors.newSingleThreadExecutor { r -> Thread(r, "zorix-voice") }
    private lateinit var inbox: HandlerThread
    private lateinit var messenger: Messenger

    private var tts: OfflineTts? = null
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
            runCatching { tts?.release() }
            tts = null
            runCatching { track?.release() }
            track = null
        }
        synth.shutdown()
        inbox.quitSafely()
        super.onDestroy()
    }

    private inner class Inbox(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_PREPARE -> synth.execute { engine() }
                MSG_SPEAK -> {
                    stopAudio()
                    val id = generation.get()
                    val text = msg.data?.getString(KEY_TEXT).orEmpty()
                    synth.execute { speak(VoiceText.forPersianVoice(text), id) }
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
    private fun speak(text: String, id: Int) {
        try {
            val engine = engine() ?: return
            for (sentence in VoiceText.sentences(text)) {
                if (id != generation.get()) return
                val audio = engine.generate(sentence, sid = 0, speed = SPEED)
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

    private fun engine(): OfflineTts? {
        tts?.let { return it }
        return try {
            load().also { tts = it }
        } catch (e: Throwable) {
            Log.e(TAG, "Persian voice unavailable", e)
            null
        }
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
        return OfflineTts(assets, config)
    }

    /** espeak-ng (the pronunciation rules) reads its data from files, so it is copied out of the APK once. */
    private fun unpackEspeakData(): File {
        val root = File(filesDir, "voice-fa")
        val stamp = File(root, "version")
        val wanted = assets.open("$VOICE_DIR/version").bufferedReader().use { it.readText().trim() }
        val data = File(root, "espeak-ng-data")
        if (stamp.isFile && stamp.readText().trim() == wanted && data.isDirectory) return data
        root.deleteRecursively()
        copyAssets("$VOICE_DIR/espeak-ng-data", data)
        stamp.writeText(wanted)
        return data
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
        const val VOICE_DIR = "voice/fa"
        private const val SPEED = 0.95f

        const val MSG_PREPARE = 1
        const val MSG_SPEAK = 2
        const val MSG_STOP = 3
        const val KEY_TEXT = "text"
    }
}
