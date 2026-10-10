package com.zorix.chess.speech

import com.zorix.chess.coach.VoiceText
import com.zorix.chess.controller.Speech
import com.zorix.sherpa.SherpaOnnxCreateOfflineTts
import com.zorix.sherpa.SherpaOnnxDestroyOfflineTtsGeneratedAudio
import com.zorix.sherpa.SherpaOnnxGenerationConfig
import com.zorix.sherpa.SherpaOnnxOfflineTtsConfig
import com.zorix.sherpa.SherpaOnnxOfflineTtsGenerateWithConfig
import com.zorix.sherpa.SherpaOnnxOfflineTtsSampleRate
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import platform.AVFAudio.*
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy
import platform.posix.memset

/**
 * The coach's voice on iOS.
 *
 * Persian (and Kurdish, read by the Persian voice) uses a neural Piper voice run by sherpa-onnx on
 * the device; English uses the system voice. Speech is synthesized one sentence at a time on a
 * serial queue and each sentence is queued on an audio player as soon as it is ready.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSpeech : Speech {
    private val generation = atomic(0)
    private val queue = dispatch_queue_create("com.zorix.voice", null)
    private val system = AVSpeechSynthesizer()

    /** Folder with the Persian voice inside the app bundle, or null when the build left it out. */
    private val voiceDir: String? = NSBundle.mainBundle.resourcePath
        ?.let { "$it/voice/fa" }
        ?.takeIf { NSFileManager.defaultManager.fileExistsAtPath("$it/model.onnx") }

    private var failed = false

    /** The speech engine, loaded on first use on [queue]. */
    private val engine by lazy { load() }

    private val audio = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private var format: AVAudioFormat? = null
    private var sessionReady = false

    override fun supports(lang: String): Boolean = when (lang) {
        "fa" -> (voiceDir != null && !failed) || AVSpeechSynthesisVoice.voiceWithLanguage("fa-IR") != null
        "ckb" -> voiceDir != null && !failed
        else -> true
    }

    override fun prepare(lang: String) {
        if ((lang == "fa" || lang == "ckb") && voiceDir != null && !failed) dispatch_async(queue) { engine }
    }

    override fun speak(text: String, lang: String) {
        stop()
        activateSession()
        val id = generation.value
        // Kurdish is read by the Persian voice (its letters mapped to the closest Persian ones).
        if ((lang == "fa" || lang == "ckb") && voiceDir != null && !failed) {
            val prepared = VoiceText.forPersianVoice(text)
            dispatch_async(queue) { speakPersian(prepared, id) }
        } else {
            val utterance = AVSpeechUtterance(string = text)
            utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(if (lang == "fa") "fa-IR" else "en-US")
            system.speakUtterance(utterance)
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        if (format != null) player.stop()
        system.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    /** Runs on [queue]. */
    private fun speakPersian(text: String, id: Int) {
        val tts = engine
        if (tts == null) {
            failed = true
            return
        }
        val fmt = audioFormat(SherpaOnnxOfflineTtsSampleRate(tts).toDouble()) ?: return
        for (sentence in VoiceText.sentences(text)) {
            if (id != generation.value) return
            val buffer = memScoped {
                val config = alloc<SherpaOnnxGenerationConfig>()
                memset(config.ptr, 0, sizeOf<SherpaOnnxGenerationConfig>().convert())
                config.silence_scale = 0.2f
                config.speed = SPEED
                config.sid = 0
                val generated = SherpaOnnxOfflineTtsGenerateWithConfig(tts, sentence, config.ptr, null, null)
                    ?: return@memScoped null
                try {
                    val n = generated.pointed.n
                    val samples = generated.pointed.samples
                    if (n <= 0 || samples == null) return@memScoped null
                    val pcm = AVAudioPCMBuffer(pCMFormat = fmt, frameCapacity = n.convert())
                    val channel = pcm.floatChannelData?.get(0) ?: return@memScoped null
                    memcpy(channel, samples, (n.toLong() * 4).convert())
                    pcm.frameLength = n.convert()
                    pcm
                } finally {
                    SherpaOnnxDestroyOfflineTtsGeneratedAudio(generated)
                }
            } ?: continue
            if (id != generation.value) return
            if (!audio.running) {
                audio.prepare()
                if (!audio.startAndReturnError(null)) return
            }
            player.scheduleBuffer(buffer, completionHandler = null)
            player.play()
        }
    }

    private fun load() = voiceDir?.let { dir ->
        memScoped {
            val config = alloc<SherpaOnnxOfflineTtsConfig>()
            memset(config.ptr, 0, sizeOf<SherpaOnnxOfflineTtsConfig>().convert())
            config.model.vits.model = "$dir/model.onnx".cstr.ptr
            config.model.vits.tokens = "$dir/tokens.txt".cstr.ptr
            config.model.vits.data_dir = "$dir/espeak-ng-data".cstr.ptr
            config.model.vits.noise_scale = 0.667f
            config.model.vits.noise_scale_w = 0.8f
            config.model.vits.length_scale = 1.0f
            config.model.num_threads = 2
            config.model.debug = 0
            config.model.provider = "cpu".cstr.ptr
            config.max_num_sentences = 1
            config.silence_scale = 0.2f
            SherpaOnnxCreateOfflineTts(config.ptr)
        }
    }

    /** Runs on [queue]: connects the player for [sampleRate] and starts the audio engine. */
    private fun audioFormat(sampleRate: Double): AVAudioFormat? {
        format?.let { if (it.sampleRate == sampleRate) return it }
        val fmt = AVAudioFormat(standardFormatWithSampleRate = sampleRate, channels = 1u)
        if (format == null) audio.attachNode(player)
        audio.connect(player, to = audio.mainMixerNode, format = fmt)
        audio.prepare()
        if (!audio.startAndReturnError(null)) return null
        format = fmt
        return fmt
    }

    private fun activateSession() {
        if (sessionReady) return
        sessionReady = true
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayback,
            mode = AVAudioSessionModeSpokenAudio,
            options = AVAudioSessionCategoryOptionMixWithOthers,
            error = null,
        )
        session.setActive(true, error = null)
    }

    private companion object {
        const val SPEED = 1.0f
    }
}
