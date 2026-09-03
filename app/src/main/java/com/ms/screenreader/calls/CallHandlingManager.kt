package com.ms.screenreader.calls

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.ms.screenreader.settings.SettingsRepository
import com.ms.screenreader.tts.TtsManager

/**
 * Watches phone call state (ringing / answered / ended) and:
 *  - announces incoming calls and speaks the duration of a finished
 *    call via TTS, controlled by [SettingsRepository.callerAnnouncerEnabled]
 *  - answers a ringing call on request (called from
 *    MSScreenReaderService.onKeyEvent when a volume key is pressed
 *    while ringing and [SettingsRepository.volumeAnswerEnabled] is on)
 *
 * The caller's phone number is only announced when the platform hands
 * it to the call-state listener without an extra permission grant
 * (still the case on many devices/OS versions with just
 * READ_PHONE_STATE). We deliberately do NOT request READ_CALL_LOG just
 * to guarantee the number on every device - that permission is far
 * more sensitive (full call history) than the caller-ID feature is
 * worth. When the number isn't available we just announce "Incoming
 * call" with no number.
 *
 * Power-button-ends-call is NOT implemented here: Android reserves
 * KEYCODE_POWER as a system key and never delivers it to
 * AccessibilityService.onKeyEvent, even with flagRequestFilterKeyEvents
 * set. Only the OS's own built-in accessibility setting
 * ("Accessibility > Power button ends call") can do this - a
 * third-party service has no API for it. [powerButtonEndCallSupported]
 * returns false for that reason, not because the feature is
 * unfinished - do not spend time trying to make this work.
 */
class CallHandlingManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val tts: TtsManager
) {
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    private var isRegistered = false
    private var isCurrentlyRinging = false
    private var callStartTimeMs: Long = 0L

    @Suppress("DEPRECATION")
    private val listener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> onRinging(phoneNumber)
                TelephonyManager.CALL_STATE_OFFHOOK -> onAnswered()
                TelephonyManager.CALL_STATE_IDLE -> onIdle()
            }
        }
    }

    /**
     * Starts listening for call-state changes. Call from
     * MSScreenReaderService.onServiceConnected(). Safe to call even if
     * READ_PHONE_STATE hasn't been granted yet - it just silently won't
     * receive events until the user grants it from MainActivity.
     */
    @Suppress("DEPRECATION")
    fun register() {
        if (isRegistered) return
        try {
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            isRegistered = true
        } catch (_: SecurityException) {
            // READ_PHONE_STATE not granted yet.
        }
    }

    @Suppress("DEPRECATION")
    fun unregister() {
        if (!isRegistered) return
        telephonyManager?.listen(listener, PhoneStateListener.LISTEN_NONE)
        isRegistered = false
    }

    private fun onRinging(phoneNumber: String?) {
        isCurrentlyRinging = true
        if (!settings.callerAnnouncerEnabled) return
        val announcement = if (!phoneNumber.isNullOrBlank()) {
            "Incoming call from $phoneNumber"
        } else {
            "Incoming call"
        }
        tts.speak(announcement)
    }

    private fun onAnswered() {
        if (isCurrentlyRinging) callStartTimeMs = System.currentTimeMillis()
        isCurrentlyRinging = false
    }

    private fun onIdle() {
        if (callStartTimeMs > 0L) {
            val durationSec = (System.currentTimeMillis() - callStartTimeMs) / 1000
            if (settings.callerAnnouncerEnabled) {
                tts.speak(formatDuration(durationSec))
            }
        }
        callStartTimeMs = 0L
        isCurrentlyRinging = false
    }

    private fun formatDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            "Call ended, duration $minutes minutes $seconds seconds"
        } else {
            "Call ended, duration $seconds seconds"
        }
    }

    /**
     * True while the phone is currently ringing - used by
     * MSScreenReaderService.onKeyEvent to decide whether a volume-key
     * press should be treated as "answer the call" instead of a normal
     * volume adjustment.
     */
    fun isRinging(): Boolean = isCurrentlyRinging

    /**
     * Answers the currently ringing call. Requires ANSWER_PHONE_CALLS to
     * have been granted (requested at runtime from MainActivity) and
     * Android 8.0+ (API 26) - already this app's minSdk, so no extra
     * version check is needed before calling this.
     */
    fun answerCall(): Boolean {
        return try {
            telecomManager?.acceptRingingCall()
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Always false - see class kdoc for why power-button-ends-call
     * cannot be implemented by a third-party accessibility service.
     */
    fun powerButtonEndCallSupported() = false

    /** True once the device is new enough for ANSWER_PHONE_CALLS/acceptRingingCall() to exist. */
    fun volumeAnswerSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}
