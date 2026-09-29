package com.zorix.chess.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.speech.tts.TextToSpeech
import com.zorix.chess.controller.Speech
import java.util.Locale

/**
 * The coach's voice on Android.
 *
 * Persian (and Kurdish, read by the Persian voice) uses a neural Piper voice run by sherpa-onnx in
 * [VoiceService], a separate process, so a problem in the native speech engine can never close the
 * app. English uses the system text-to-speech engine.
 */
class AndroidSpeech(context: Context) : Speech {
    private val app = context.applicationContext

    /** True when the voice files are inside the APK (they are left out if the build could not fetch them). */
    private val persianPacked: Boolean =
        runCatching { app.assets.list(VoiceService.VOICE_DIR)?.contains("model.onnx") == true }.getOrDefault(false)

    private var service: Messenger? = null
    private var binding = false
    private var crashes = 0
    private var pending: Message? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let(::Messenger)
            pending?.let { send(it) }
            pending = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // The voice process ended unexpectedly; after two failures the voice is switched off.
            service = null
            if (++crashes >= 2) unbind()
        }
    }

    // ---------------------------------------------------------------- system voice (English)

    @Volatile private var systemReady = false
    private val system: TextToSpeech = TextToSpeech(app) { status -> systemReady = status == TextToSpeech.SUCCESS }

    private val persianAvailable: Boolean get() = persianPacked && crashes < 2

    override fun supports(lang: String): Boolean = when (lang) {
        "fa" -> persianAvailable || systemSupports(Locale("fa", "IR"))
        else -> systemSupports(Locale.US)
    }

    override fun prepare(lang: String) {
        if (lang == "fa" && persianAvailable) send(Message.obtain(null, VoiceService.MSG_PREPARE))
    }

    override fun speak(text: String, lang: String) {
        stop()
        if (lang == "fa" && persianAvailable) {
            val msg = Message.obtain(null, VoiceService.MSG_SPEAK)
            msg.data = Bundle().apply { putString(VoiceService.KEY_TEXT, text) }
            send(msg)
        } else if (systemReady) {
            runCatching {
                system.language = if (lang == "fa") Locale("fa", "IR") else Locale.US
                system.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zorix")
            }
        }
    }

    override fun stop() {
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
        if (binding || !persianAvailable) return
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
