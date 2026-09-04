package com.ms.screenreader.sounds

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.documentfile.provider.DocumentFile
import com.ms.screenreader.R
import com.ms.screenreader.settings.SettingsRepository

/**
 * Lets the user assign their own short sound files (earcons) to specific
 * screen-reader events (click, long-press, scroll up/down, focus change,
 * etc.) from a folder they pick themselves - instead of any sound bundled
 * with the app.
 *
 * How it works:
 * 1. User grants access to a folder via ACTION_OPEN_DOCUMENT_TREE
 *    (see SoundSchemeFolderPicker). The chosen folder's URI is persisted
 *    with a *persistable* permission so it survives reboots.
 * 2. Inside that folder, the user puts audio files named after each
 *    SoundEvent's fileKey, e.g.:
 *        click.wav
 *        long_press.ogg
 *        scroll_up.mp3
 *        scroll_down.wav
 *        focus_change.ogg
 *    Any common audio extension is accepted (wav/ogg/mp3/m4a).
 * 3. When an event fires, MSScreenReaderService calls play(event), which
 *    looks up the matching file in that folder and plays it.
 *
 * If no folder is set, or a given event has no matching file, play() is a
 * silent no-op (falls back to nothing - TTS speech is unaffected, since
 * that's a separate pipeline in TtsManager).
 */
class SoundSchemeManager(private val context: Context) {

    private val settings = SettingsRepository(context)
    private val supportedExtensions = listOf("wav", "ogg", "mp3", "m4a")

    // Cache: event -> resolved content Uri, so we don't re-scan the folder
    // (which involves a content-provider query) on every single event.
    private val resolvedUriCache = mutableMapOf<SoundEvent, Uri?>()
    private var cachedFolderUri: String? = null

    private var activePlayer: MediaPlayer? = null

    /**
     * Haptic feedback (item #9, docs/REMAINING_WORK.md pre-v1.21 list) -
     * a short vibration alongside (or instead of, if no earcon folder is
     * set) the sound scheme, independent master toggle
     * (settings.vibrationEnabled, default on like TalkBack). Resolved
     * once and reused rather than fetched per event.
     */
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Call this once the user has picked (or changed) their sound folder,
     * so the cache doesn't serve stale lookups from a previous folder.
     */
    fun invalidateCache() {
        resolvedUriCache.clear()
        cachedFolderUri = null
    }

    /** True if the user has picked a sound-scheme folder at all. */
    fun hasFolderConfigured(): Boolean = !settings.soundSchemeFolderUri.isNullOrBlank()

    /**
     * Bundled defaults (added so the app has a working sound scheme
     * right after install, instead of staying silent until the person
     * picks their own folder every single time they reinstall). Not
     * every SoundEvent has one - only the events a matching source
     * sound was actually available for; the rest stay silent unless
     * the person configures their own folder for them.
     */
    private val bundledDefaults: Map<SoundEvent, Int> = mapOf(
        SoundEvent.CLICK to R.raw.click,
        SoundEvent.LONG_PRESS to R.raw.long_press,
        SoundEvent.SCROLL_UP to R.raw.scroll_up,
        SoundEvent.SCROLL_DOWN to R.raw.scroll_down,
        SoundEvent.WINDOW_CHANGE to R.raw.window_change,
        SoundEvent.FOCUS_CHANGE to R.raw.focus_change,
        SoundEvent.NOTIFICATION to R.raw.notification,
        SoundEvent.SPEECH_SUSPENDED to R.raw.speech_suspended,
        SoundEvent.SPEECH_RESUMED to R.raw.speech_resumed
    )

    /**
     * Plays the earcon assigned to [event] (if configured) and vibrates
     * (if enabled) - the two are independent: vibration doesn't require
     * a sound-scheme folder to be set up, and sound doesn't require
     * vibration to be on.
     *
     * Sound resolution order: the person's own folder (if they've set
     * one and it has a matching file for this event) takes priority
     * over the bundled default, so picking a custom folder always
     * overrides the built-in sound rather than fighting with it.
     */
    fun play(event: SoundEvent) {
        vibrateFor(event)

        if (!settings.soundSchemeEnabled) return

        val folderUriString = settings.soundSchemeFolderUri
        if (folderUriString != null) {
            if (folderUriString != cachedFolderUri) {
                resolvedUriCache.clear()
                cachedFolderUri = folderUriString
            }
            val uri = resolvedUriCache.getOrPut(event) { resolveFileUri(folderUriString, event) }
            if (uri != null) {
                playUri(uri)
                return
            }
        }

        bundledDefaults[event]?.let { playRawResource(it) }
    }

    private fun vibrateFor(event: SoundEvent) {
        if (!settings.vibrationEnabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createOneShot(vibrationMillisFor(event), VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // Some OEM vibrator drivers throw on odd durations/amplitudes -
            // never let haptics take down speech/sound feedback.
        }
    }

    /** Short buzz for most events, a little longer for the ones that matter more (long-press activation, a new notification, a window/screen change). */
    private fun vibrationMillisFor(event: SoundEvent): Long = when (event) {
        SoundEvent.LONG_PRESS -> 50L
        SoundEvent.WINDOW_CHANGE -> 40L
        SoundEvent.NOTIFICATION -> 60L
        else -> 25L
    }

    private fun resolveFileUri(folderUriString: String, event: SoundEvent): Uri? {
        return try {
            val folderUri = Uri.parse(folderUriString)
            val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return null
            if (!folder.exists() || !folder.isDirectory) return null

            // Look for fileKey.<ext> among supported extensions.
            for (ext in supportedExtensions) {
                val target = "${event.fileKey}.$ext"
                val match = folder.listFiles().firstOrNull {
                    it.name?.equals(target, ignoreCase = true) == true
                }
                if (match != null) return match.uri
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Same playback path as [playUri], but for a bundled res/raw sound instead of a user-picked file. */
    private fun playRawResource(resId: Int) {
        activePlayer?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        activePlayer = null

        try {
            val player = MediaPlayer.create(
                context, resId, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                0
            ) ?: return
            player.setOnCompletionListener { mp ->
                mp.release()
                if (activePlayer === mp) activePlayer = null
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (activePlayer === mp) activePlayer = null
                true
            }
            player.start()
            activePlayer = player
        } catch (e: Exception) {
            // Never let a broken bundled sound take down the rest of the pipeline.
        }
    }

    private fun playUri(uri: Uri) {
        // Stop/release any earcon still playing so overlapping events
        // don't stack sounds indefinitely.
        activePlayer?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        activePlayer = null

        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, uri)
            player.setOnCompletionListener { mp ->
                mp.release()
                if (activePlayer === mp) activePlayer = null
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (activePlayer === mp) activePlayer = null
                true
            }
            player.prepare()
            player.start()
            activePlayer = player
        } catch (e: Exception) {
            // Missing/unreadable/corrupt file - fail silently, TTS speech
            // pipeline is unaffected.
        }
    }

    /** Call from the service's onDestroy()/onInterrupt() to free resources. */
    fun release() {
        activePlayer?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        activePlayer = null
    }
}
