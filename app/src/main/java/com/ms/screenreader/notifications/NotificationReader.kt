package com.ms.screenreader.notifications

import android.app.Notification
import android.content.Context
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import com.ms.screenreader.settings.SettingsRepository

/**
 * Decides whether a given notification should be read aloud.
 *
 * Filtering rules (in order):
 * 1. Master switch (`SettingsRepository.notificationReaderEnabled`) - if
 *    off, nothing is read.
 * 2. Per-app mute list - packages the user has explicitly silenced.
 * 3. Screen-off check (`SettingsRepository.speakNotificationsWhenScreenOffEnabled`)
 *    - off by default (matches TalkBack), so an unattended phone with
 *    the screen off stays quiet about incoming notifications unless the
 *    person has explicitly opted into hearing them anyway.
 * 4. Group-summary notifications are skipped (Android posts a summary
 *    *and* individual notifications for grouped notifications - reading
 *    both would double-announce the same content).
 * 5. Low-priority / ongoing foreground-service notifications (e.g. "App
 *    is running in background", media-playback controls) are skipped by
 *    default, since they're not something the user needs read aloud
 *    every time they refresh.
 */
class NotificationReader(private val context: Context) {

    private val settings = SettingsRepository(context)

    /** Simple package-name check, kept for backward source-compatibility. Does not check screen state - see the StatusBarNotification overload for the full check. */
    fun shouldRead(packageName: String): Boolean {
        if (!settings.notificationReaderEnabled) return false
        return packageName !in settings.mutedNotificationPackages
    }

    /** Full check using the actual posted notification, for finer-grained filtering. */
    fun shouldRead(sbn: StatusBarNotification): Boolean {
        if (!settings.notificationReaderEnabled) return false
        if (sbn.packageName in settings.mutedNotificationPackages) return false
        if (!settings.speakNotificationsWhenScreenOffEnabled && !isScreenInteractive()) return false

        val notification = sbn.notification ?: return true

        // Group summary: the individual child notifications already carry
        // the real content, so the summary is redundant.
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return false

        // Ongoing (foreground-service style) notifications - e.g. "Music
        // playing", "Downloading file", nav directions - tend to update
        // very frequently and aren't meant to be read every time.
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing) return false

        return true
    }

    /** True if the screen is currently on/interactive. Used to gate reading notifications aloud when the screen is off, per [SettingsRepository.speakNotificationsWhenScreenOffEnabled]. */
    private fun isScreenInteractive(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isInteractive
    }

    /** Extracts a spoken-friendly summary: "AppName: title, text". */
    fun extractSpokenText(sbn: StatusBarNotification): String? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val parts = listOfNotNull(title?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() })
        if (parts.isEmpty()) return null
        return parts.joinToString(": ")
    }
}
