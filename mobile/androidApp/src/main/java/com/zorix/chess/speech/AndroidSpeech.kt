package com.zorix.chess.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.zorix.chess.controller.Speech
import com.zorix.chess.controller.SpeechStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The coach's voice on Android.
 *
 * Persian, Kurdish and English use neural Piper voices run by sherpa-onnx in [VoiceService], a
 * separate process, so a problem in the native speech engine can never close the app. The phone's
 * own text-to-speech is only a fallback for an APK built without the voices.
 */
class AndroidSpeech(context: Context) : Speech {
    private val app = context.applicationContext

    /** True when the voice files are inside the APK (they are left out if the build could not fetch them). */
    private val persianPacked: Boolean = packed(VoiceService.FA_DIR)
    private val englishPacked: Boolean = packed(VoiceService.EN_DIR)
    private val kurdishPacked: Boolean = packed(VoiceService.CKB_DIR)

    private fun packed(dir: String) = runCatching { app.assets.list(dir)?.contains("model.onnx") == true }.getOrDefault(false)

    private var service: Messenger? = null
    private var binding = false
    private var crashes = 0
    private var pending: Message? = null

    private val _status = MutableStateFlow(SpeechStatus.IDLE)
    override val status: StateFlow<SpeechStatus> = _status.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    /** Identifies the latest speech request; replies about older ones are ignored. */
    private var token = 0

    /** Receives "started" and "done" from the voice service (on the main thread). */
    private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.arg1 != token) return
            when (msg.what) {
                VoiceService.MSG_STARTED -> if (_status.value == SpeechStatus.PREPARING) _status.value = SpeechStatus.SPEAKING
                VoiceService.MSG_DONE -> _status.value = SpeechStatus.IDLE
            }
        }
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let(::Messenger)
            pending?.let { send(it) }
            pending = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // The voice process ended unexpectedly; after two failures the voice is switched off.
            service = null
            _status.value = SpeechStatus.IDLE
            if (++crashes >= 2) unbind()
        }
    }

    // ---------------------------------------------------------------- system voice (fallback)

    @Volatile private var systemReady = false
    private val system: TextToSpeech = TextToSpeech(app) { status -> systemReady = status == TextToSpeech.SUCCESS }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == "zorix-$token") _status.value = SpeechStatus.SPEAKING
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == "zorix-$token") _status.value = SpeechStatus.IDLE
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "zorix-$token") _status.value = SpeechStatus.IDLE
            }
        })
    }

    private val voicesWork: Boolean get() = crashes < 2

    /** True when [lang] is spoken by a neural voice. */
    private fun neural(lang: String): Boolean = voicesWork && when (lang) {
        "fa" -> persianPacked
        "ckb" -> kurdishPacked
        "en" -> englishPacked
        else -> false
    }

    override fun supports(lang: String): Boolean = when (lang) {
        "fa" -> neural("fa") || systemSupports(Locale("fa", "IR"))
        "ckb" -> neural("ckb")
        else -> neural("en") || systemSupports(Locale.US)
    }

    override fun prepare(lang: String) {
        if (neural(lang)) {
            val msg = Message.obtain(null, VoiceService.MSG_PREPARE)
            msg.data = Bundle().apply { putString(VoiceService.KEY_LANG, lang) }
            send(msg)
        }
    }

    override fun speak(text: String, lang: String) {
        stop()
        val id = ++token
        if (neural(lang)) {
            _status.value = SpeechStatus.PREPARING
            // Never leave the indicator on if the voice process cannot answer.
            main.postDelayed({ if (token == id && _status.value == SpeechStatus.PREPARING) _status.value = SpeechStatus.IDLE }, 20_000)
            val msg = Message.obtain(null, VoiceService.MSG_SPEAK, id, 0)
            msg.replyTo = replies
            msg.data = Bundle().apply {
                putString(VoiceService.KEY_TEXT, text)
                putString(VoiceService.KEY_LANG, lang)
            }
            send(msg)
        } else if (systemReady && lang != "ckb") {
            _status.value = SpeechStatus.PREPARING
            runCatching {
                system.language = if (lang == "fa") Locale("fa", "IR") else Locale.US
                system.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zorix-$id")
            }.onFailure { _status.value = SpeechStatus.IDLE }
        }
    }

    override fun stop() {
        token++
        _status.value = SpeechStatus.IDLE
        service?.let { s -> runCatching { s.send(Message.obtain(null, VoiceService.MSG_STOP)) } }
        pending = null
        if (systemReady) runCatching { system.stop() }
    }

    /** Releases the voices; called when the app closes. */
    fun shutdown() {
        stop()
        unbind()
        runCatching { system.shutdown() }
    }

    private fun send(msg: Message) {
        val s = service
        if (s != null) {
            if (runCatching { s.send(msg) }.isSuccess) return
            service = null
        }
        pending = msg
        bind()
    }

    private fun bind() {
        if (binding || !voicesWork || !(persianPacked || englishPacked || kurdishPacked)) return
        binding = runCatching {
            app.bindService(Intent(app, VoiceService::class.java), connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
    }

    private fun unbind() {
        if (binding) runCatching { app.unbindService(connection) }
        binding = false
        service = null
        pending = null
    }

    private fun systemSupports(locale: Locale): Boolean =
        systemReady && runCatching { system.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
}
