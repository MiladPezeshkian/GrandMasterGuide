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
import android.os.Process
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.zorix.chess.coach.KurdishVoice
import com.zorix.chess.coach.VoiceText
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * The neural voices (sherpa-onnx + Piper), running in their own process (":voice"). If the native
 * speech engine ever fails, only this process ends; the app keeps running and [AndroidSpeech] simply
 * stops using the voices.
 *
 * - Persian: the "ganji" voice with its espeak-ng pronunciation rules.
 * - Kurdish: the native Sorani voice "Vekol" (Revge), which reads Sorani letters ([KurdishVoice]).
 * - English: the "ljspeech" voice with espeak-ng.
 *
 * Text is spoken sentence by sentence, each sentence whole (so its intonation stays natural): while one
 * sentence plays, the next one is already being made. The app is told when the voice starts and ends
 * ([MSG_STARTED], [MSG_DONE]) so it can show a loading indicator until then. One engine is loaded at a
 * time; switching the language releases the previous one.
 */
class VoiceService : Service() {
    private val generation = AtomicInteger()
    private val synth = Executors.newSingleThreadExecutor { r -> Thread(r, "zorix-voice") }
    private val queue = LinkedBlockingQueue<Piece>()
    private lateinit var player: Thread
    private lateinit var inbox: HandlerThread
    private lateinit var messenger: Messenger

    // Only touched on [synth].
    private var tts: OfflineTts? = null
    private var ttsLang: String? = null
    private var kurdishLetters: Set<Char>? = null

    @Volatile private var track: AudioTrack? = null
    @Volatile private var running = true

    /**
     * Audio of one sentence for the speech request [id]; the last piece of a request has [last] set (and
     * may be empty). [reply] and [token] identify the request in the app.
     */
    private class Piece(val id: Int, val samples: FloatArray, val sampleRate: Int, val reply: Messenger?, val token: Int, val last: Boolean = false)

    override fun onCreate() {
        super.onCreate()
        inbox = HandlerThread("zorix-voice-inbox").apply { start() }
        messenger = Messenger(Inbox(inbox.looper))
        player = Thread(::play, "zorix-voice-player").apply { start() }
        synth.execute { Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT) }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        stopAudio()
        running = false
        player.interrupt()
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
                    val reply = msg.replyTo
                    val token = msg.arg1
                    synth.execute { speak(text, lang, id, reply, token) }
                }
                MSG_STOP -> stopAudio()
            }
        }
    }

    private fun stopAudio() {
        generation.incrementAndGet()
        queue.clear()
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    /** Runs on [synth]: makes the audio sentence by sentence and hands each to the player as soon as it is ready. */
    private fun speak(text: String, lang: String, id: Int, reply: Messenger?, token: Int) {
        try {
            val engine = engine(lang)
            if (engine == null) {
                tell(reply, MSG_DONE, token)
                return
            }
            for (piece in pieces(text, lang)) {
                if (id != generation.get()) return
                var audio = engine.generate(piece, sid = 0, speed = SPEED)
                if (lang == "ckb") {
                    // A rare babbling tail only makes the audio longer than its text allows:
                    // such a render is made again and the shorter one is kept.
                    val limit = piece.length * SECONDS_PER_LETTER / SPEED + 1.2f
                    if (audio.samples.size.toFloat() / audio.sampleRate > limit && id == generation.get()) {
                        val again = engine.generate(piece, sid = 0, speed = SPEED)
                        if (again.samples.size < audio.samples.size) audio = again
                    }
                }
                if (id != generation.get()) return
                queue.put(Piece(id, audio.samples, audio.sampleRate, reply, token))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "speech failed", e)
        }
        // Always closes the request, so the app's indicator never stays on.
        queue.put(Piece(id, FloatArray(0), 0, reply, token, last = true))
    }

    private fun tell(reply: Messenger?, what: Int, token: Int) {
        runCatching { reply?.send(Message.obtain(null, what, token, 0)) }
    }

    /**
     * The sentences of [text], spoken whole. Only a very long Kurdish sentence is split at a comma
     * (the Kurdish voice can babble at the end of a very long input).
     */
    private fun pieces(text: String, lang: String): List<String> {
        val prepared = if (lang == "fa") VoiceText.forPersianVoice(text) else text
        val sentences = VoiceText.sentences(prepared)
        if (lang != "ckb") return sentences
        val letters = kurdishTokens()
        return sentences.flatMap { KurdishVoice.chunks(it, max = 200, firstMax = 220) }
            .map { KurdishVoice.speechText(it, letters) }.filter { it.isNotEmpty() }
    }

    /**
     * The player thread (audio priority, so making the next sentence never starves playback): plays the
     * sentences of the current request in order, with a short breath between them.
     */
    private fun play() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var startedId = -1
        var written = 0L
        var base = 0L
        while (running) {
            val piece = try {
                queue.take()
            } catch (e: InterruptedException) {
                return
            }
            if (piece.id != generation.get()) {
                if (piece.last) tell(piece.reply, MSG_DONE, piece.token)
                continue
            }
            if (piece.last) {
                waitUntilPlayed(piece.id, base + written)
                tell(piece.reply, MSG_DONE, piece.token)
                continue
            }
            val out = audioTrack(piece.sampleRate) ?: continue
            if (startedId != piece.id) {
                startedId = piece.id
                base = headPosition(out)
                written = 0L
                tell(piece.reply, MSG_STARTED, piece.token)
            } else {
                // A short pause between two sentences, as a speaker takes a breath.
                val pause = FloatArray(piece.sampleRate * SENTENCE_PAUSE_MS / 1000)
                written += out.write(pause, 0, pause.size, AudioTrack.WRITE_BLOCKING).coerceAtLeast(0)
            }
            if (out.playState != AudioTrack.PLAYSTATE_PLAYING) out.play()
            // Blocks while the buffer is full; stopAudio() pauses the track, which returns early.
            written += out.write(piece.samples, 0, piece.samples.size, AudioTrack.WRITE_BLOCKING).coerceAtLeast(0)
        }
    }

    private fun headPosition(out: AudioTrack): Long = runCatching { out.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrDefault(0L)

    /** Waits (at most 15 s) until the track has played up to frame [end] for request [id]. */
    private fun waitUntilPlayed(id: Int, end: Long) {
        val out = track ?: return
        val deadline = System.currentTimeMillis() + 15_000
        while (id == generation.get() && System.currentTimeMillis() < deadline) {
            if (headPosition(out) >= end || out.playState != AudioTrack.PLAYSTATE_PLAYING) return
            try {
                Thread.sleep(30)
            } catch (e: InterruptedException) {
                return
            }
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
        val vits = when (lang) {
            // Vekol reads letters (its tokens are Sorani letters), with its own rhythm setting.
            "ckb" -> OfflineTtsVitsModelConfig(model = "$CKB_DIR/model.onnx", tokens = "$CKB_DIR/tokens.txt", noiseScaleW = 0.35f)
            "en" -> OfflineTtsVitsModelConfig(model = "$EN_DIR/model.onnx", tokens = "$EN_DIR/tokens.txt", dataDir = unpackEspeakData().absolutePath)
            else -> OfflineTtsVitsModelConfig(model = "$FA_DIR/model.onnx", tokens = "$FA_DIR/tokens.txt", dataDir = unpackEspeakData().absolutePath)
        }
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(vits = vits, numThreads = threads, debug = false, provider = "cpu"),
            maxNumSentences = 1,
        )
        return OfflineTts(assets, config)
    }

    /** The letters and marks the Kurdish voice knows (from its tokens.txt). */
    private fun kurdishTokens(): Set<Char> = kurdishLetters ?: assets.open("$CKB_DIR/tokens.txt").bufferedReader().useLines { lines ->
        lines.mapNotNull { line -> line.trimEnd().substringBeforeLast(' ').singleOrNull() }.filter { it != ' ' }.toSet()
    }.also { kurdishLetters = it }

    // ------------------------------------------------------------------------------ files

    /** espeak-ng (the pronunciation rules) reads its data from files, so it is copied out of the APK once. */
    private fun unpackEspeakData(): File {
        val root = File(filesDir, "voice-fa")
        val stamp = File(root, "version")
        val wanted = assets.open("$FA_DIR/version").bufferedReader().use { it.readText().trim() }
        val data = File(root, "espeak-ng-data")
        // Files of earlier versions (the Kurdish lexicon and its model copy) are no longer used.
        File(filesDir, "voice-ckb").deleteRecursively()
        if (stamp.isFile && stamp.readText().trim() == wanted && data.isDirectory) return data
        root.deleteRecursively()
        copyAssets("$FA_DIR/espeak-ng-data", data)
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
                // About a second of audio, so a busy processor never makes the voice stutter.
                .setBufferSizeInBytes(maxOf(min, sampleRate * 4))
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
        const val CKB_DIR = "voice/ckb"
        private const val SPEED = 0.95f

        /** The Kurdish voice's normal pace (from its author): much longer audio means babble. */
        private const val SECONDS_PER_LETTER = 0.075f

        /** Pause between two sentences. */
        private const val SENTENCE_PAUSE_MS = 160

        const val MSG_PREPARE = 1
        const val MSG_SPEAK = 2
        const val MSG_STOP = 3

        /** To the app: the voice of request arg1 started / finished (or was stopped). */
        const val MSG_STARTED = 10
        const val MSG_DONE = 11
        const val KEY_TEXT = "text"
        const val KEY_LANG = "lang"
    }
}
