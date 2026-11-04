package uk.me.g4dpz.HamSatDroid.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speaks one announcement with text-to-speech, best effort. It takes transient audio focus that ducks other audio,
 * speaks, then gives the focus back and shuts the engine down. If TTS cannot start, or does not finish within
 * [TIMEOUT_MILLIS], it gives up quietly and still calls [onDone] so the receiver can finish. Nothing is shown to the
 * user on failure.
 */
object SpokenAnnouncer {

    private const val TIMEOUT_MILLIS = 8_000L
    private const val UTTERANCE_ID = "pass_alert"

    fun speak(context: Context, text: String, onDone: () -> Unit) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)

        var tts: TextToSpeech? = null
        var focusRequest: AudioFocusRequest? = null

        fun cleanUp() {
            if (!finished.compareAndSet(false, true)) return
            handler.post {
                try {
                    tts?.stop()
                    tts?.shutdown()
                } catch (e: IllegalArgumentException) {
                    // engine already gone
                }
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
                onDone()
            }
        }

        // Give up if TTS never calls back
        handler.postDelayed({ cleanUp() }, TIMEOUT_MILLIS)

        tts = TextToSpeech(appContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                cleanUp()
                return@TextToSpeech
            }
            engine.language = Locale.getDefault().takeIf { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                ?: Locale.ENGLISH

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            requestFocus(audioManager, attributes)?.let { focusRequest = it }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = cleanUp()

                @Deprecated("required override")
                override fun onError(utteranceId: String?) = cleanUp()
                override fun onError(utteranceId: String?, errorCode: Int) = cleanUp()
            })

            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            if (result != TextToSpeech.SUCCESS) cleanUp()
        }
    }

    private fun requestFocus(audioManager: AudioManager?, attributes: AudioAttributes): AudioFocusRequest? {
        if (audioManager == null) return null
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        audioManager.requestAudioFocus(request)
        return request
    }
}
