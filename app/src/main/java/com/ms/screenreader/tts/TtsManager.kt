package com.ms.screenreader.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Thin wrapper around Android's TextToSpeech engine.
 * Buffers a single pending utterance if speak() is called before the
 * engine finishes initializing, so early accessibility events right
 * after the service starts aren't silently dropped.
 *
 * Speech volume is independent from music volume: TTS output is routed
 * to Android's dedicated STREAM_ACCESSIBILITY audio stream (see
 * setAudioAttributes() in onInit) instead of the default STREAM_MUSIC.
 * This is the same mechanism TalkBack uses (confirmed both in
 * TalkBack's own AOSP source and in Android's official accessibility-
 * service guide, which explicitly recommends it for screen readers) -
 * requires two things working together, both API 26+ (this app's
 * minSdk is already 26):
 *  1. accessibility_service_config.xml declares
 *     flagEnableAccessibilityVolume.
 *  2. The audio actually played by the service uses
 *     AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY (set here for TTS,
 *     and already set in SoundSchemeManager for earcons).
 * With both in place, Android automatically treats the accessibility
 * stream as "the currently active/most relevant stream" while this
 * service is speaking or playing an earcon - so a volume-key press at
 * that moment adjusts STREAM_ACCESSIBILITY, and the system volume
 * panel shows it as a separate "Accessibility volume" slider. The
 * instant nothing is speaking/earcon-ing, volume keys go back to
 * controlling whatever else is relevant (music, ringer, etc.) as
 * normal - there's no separate "focus a slider first" step needed, and
 * no code here decides *when* volume keys apply to which stream; the
 * system does that automatically based on what's making sound.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context, this)
    private var isReady = false
    private var pendingText: String? = null

    /** Whether speech is currently suspended (toggled by the "suspend voice feedback" gesture). */
    var isMuted: Boolean = false
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            isReady = true
            pendingText?.let { speak(it) }
            pendingText = null
        }
    }

    fun speak(text: String) {
        if (text.isBlank() || isMuted) return
        if (!isReady) {
            pendingText = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ms_reader")
    }

    /**
     * Flips speech on/off. When muting, also stops whatever is currently
     * being spoken so the suspend takes effect immediately. Returns the
     * new muted state so the caller can announce it (best done via sound
     * feedback rather than speech, since speech may now be off).
     */
    fun toggleMute(): Boolean {
        isMuted = !isMuted
        if (isMuted) stop()
        return isMuted
    }

    fun stop() {
        pendingText = null
        if (isReady) tts.stop()
    }

    fun shutdown() {
        isReady = false
        tts.shutdown()
    }
}
