package com.mlbb.assistant.service

import android.content.Context
import android.speech.tts.TextToSpeech
import timber.log.Timber
import java.util.Locale

/**
 * Thin wrapper around Android [TextToSpeech] that provides spoken alerts
 * during the draft (e.g., "Your turn to ban", "Pick confirmed").
 *
 * Lifecycle: call [init] once after creation (Hilt @Singleton, initialised in
 * [MLBBApplication.onCreate] or lazily on first use). Call [shutdown] when
 * the overlay service is stopped.
 *
 * Thread safety: [speak] is safe to call from any thread; the TTS engine
 * queues utterances internally.
 */
class VoiceAlertService(private val context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile private var isReady = false

    /** Initialises the TTS engine. Idempotent — safe to call multiple times. */
    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isReady = true
                Timber.d("VoiceAlertService: TTS engine ready")
            } else {
                Timber.w("VoiceAlertService: TTS init failed with status $status")
            }
        }
    }

    /**
     * Speaks [message] immediately, interrupting any currently playing utterance.
     * Silently no-ops if the engine is not ready.
     */
    fun speak(message: String) {
        if (!isReady) {
            Timber.w("VoiceAlertService: not ready — dropping utterance: \"$message\"")
            return
        }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "mlbb_alert_${System.nanoTime()}")
    }

    /**
     * Queues [message] without interrupting the current utterance.
     * Silently no-ops if the engine is not ready.
     */
    fun speakQueued(message: String) {
        if (!isReady) return
        tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "mlbb_queued_${System.nanoTime()}")
    }

    /** Releases TTS resources. Must be called when the overlay service is destroyed. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Timber.d("VoiceAlertService: TTS engine shut down")
    }
}
