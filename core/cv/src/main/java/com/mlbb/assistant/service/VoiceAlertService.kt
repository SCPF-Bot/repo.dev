package com.mlbb.assistant.service

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAlertService(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    var isEnabled = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isReady = true
            }
        }
    }

    fun speak(message: String) {
        if (!isEnabled || !isReady) return
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "mlbb_alert_${System.currentTimeMillis()}")
    }

    fun alertBanTurn()        = speak("Your turn to ban.")
    fun alertPickTurn()       = speak("Your turn to pick.")
    fun alertCounterDetected(enemyName: String) = speak("Counter detected. $enemyName picked.")
    fun alertMissingRole(role: String)    = speak("Team needs a $role.")
    fun alertDraftComplete()  = speak("Draft complete.")
    fun alertTradingPhase()   = speak("Trading phase. You have 20 seconds.")

    // P2 fix: TTS engine binding on some MIUI/Xiaomi ROMs can leave the engine in
    // a partial state; calling stop()/shutdown() on a not-fully-bound engine throws
    // IllegalArgumentException. Wrap in runCatching so MainActivity.onDestroy()
    // never propagates a TTS crash into an Activity teardown crash.
    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }
}
