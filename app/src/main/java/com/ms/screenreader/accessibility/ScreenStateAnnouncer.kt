package com.ms.screenreader.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import com.ms.screenreader.settings.SettingsRepository
import com.ms.screenreader.tts.TtsManager
import java.util.Date

/**
 * Announces "Screen locked" when the display turns off, and speaks
 * time/date/year/battery percent when the device is actually unlocked
 * (not just when the display turns back on, which can happen without
 * a real unlock - e.g. a notification lighting the screen). Matches
 * Jieshuo's "Screen state: Always read battery status" + "Speak screen
 * lock state" settings.
 *
 * Registered dynamically from the service (SCREEN_ON/SCREEN_OFF/
 * USER_PRESENT can only be received via a runtime-registered
 * BroadcastReceiver - a manifest-declared receiver for these actions
 * is never called by the OS).
 */
class ScreenStateAnnouncer(
    private val context: Context,
    private val settings: SettingsRepository,
    private val tts: TtsManager
) {
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (!settings.announceScreenStateEnabled) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> tts.speak("Screen locked")
                Intent.ACTION_USER_PRESENT -> tts.speak(unlockAnnouncement())
            }
        }
    }

    fun register() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(receiver, filter)
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        context.unregisterReceiver(receiver)
        isRegistered = false
    }

    private fun unlockAnnouncement(): String {
        val now = Date()
        val time = DateFormat.getTimeFormat(context).format(now)
        val date = DateFormat.getDateFormat(context).format(now)
        val batteryPart = batteryPercent()?.let { ", battery $it percent" } ?: ""
        return "$time, $date$batteryPart"
    }

    /** Reads the last-known battery level via the sticky ACTION_BATTERY_CHANGED intent - no extra permission or ongoing receiver needed just to check this once. Null if the battery level genuinely can't be read. */
    private fun batteryPercent(): Int? {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100) / scale
    }
}
