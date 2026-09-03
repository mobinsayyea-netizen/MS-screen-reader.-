package com.ms.screenreader.settings
import android.content.Context
import com.ms.screenreader.gestures.GestureAction
import com.ms.screenreader.gestures.GestureRegister

class SettingsRepository(context: Context) {
    private val p = context.getSharedPreferences("ms_screen_reader", Context.MODE_PRIVATE)

    var callerAnnouncerEnabled: Boolean get() = p.getBoolean("caller_announcer", true)
        set(v) = p.edit().putBoolean("caller_announcer", v).apply()
    var powerButtonEndCallEnabled: Boolean get() = p.getBoolean("power_end_call", false)
        set(v) = p.edit().putBoolean("power_end_call", v).apply()
    var volumeAnswerEnabled: Boolean get() = p.getBoolean("volume_answer", false)
        set(v) = p.edit().putBoolean("volume_answer", v).apply()

    /**
     * Whether pressing the on-screen Accessibility Button or the
     * volume-key Accessibility Shortcut should do anything at all
     * (currently: suspend/resume voice feedback). When off, both
     * inputs are no-ops - useful if the person's device already uses
     * that shortcut for something else system-wide.
     */
    var accessibilityShortcutEnabled: Boolean get() = p.getBoolean("accessibility_shortcut_enabled", true)
        set(v) = p.edit().putBoolean("accessibility_shortcut_enabled", v).apply()

    // ---- Verbosity settings ---------------------------------------------
    //
    // Controls how much extra detail describe()/move() speak alongside the
    // node's plain text - modeled after TalkBack's "Verbosity" screen.
    // Each toggle defaults to whatever TalkBack itself defaults to, so a
    // fresh install sounds close to what a TalkBack user already expects.

    /** Element type after the content, e.g. "button", "checkbox" - for the focused item. */
    var speakElementTypeEnabled: Boolean get() = p.getBoolean("speak_element_type", true)
        set(v) = p.edit().putBoolean("speak_element_type", v).apply()

    /** Announce entering/exiting a list, grid, or other container while navigating. */
    var speakContainerInfoEnabled: Boolean get() = p.getBoolean("speak_container_info", true)
        set(v) = p.edit().putBoolean("speak_container_info", v).apply()

    /** After describing an item, add a short hint like "double tap to activate". */
    var speakUsageHintsEnabled: Boolean get() = p.getBoolean("speak_usage_hints", true)
        set(v) = p.edit().putBoolean("speak_usage_hints", v).apply()

    /** Speak how many items are in a list/grid the first time you land in it. */
    var speakListItemCountEnabled: Boolean get() = p.getBoolean("speak_list_item_count", false)
        set(v) = p.edit().putBoolean("speak_list_item_count", v).apply()

    /** Speak window/screen names when switching to a different window (status bar, notifications, another app). */
    var speakWindowNamesEnabled: Boolean get() = p.getBoolean("speak_window_names", true)
        set(v) = p.edit().putBoolean("speak_window_names", v).apply()

    /** For unlabelled buttons/elements with no text or contentDescription, fall back to speaking their resource ID. */
    var speakElementIdsEnabled: Boolean get() = p.getBoolean("speak_element_ids", false)
        set(v) = p.edit().putBoolean("speak_element_ids", v).apply()

    /** Say "capital" before a capital letter when stepping character-by-character. */
    var speakCapitalLettersEnabled: Boolean get() = p.getBoolean("speak_capital_letters", true)
        set(v) = p.edit().putBoolean("speak_capital_letters", v).apply()

    /**
     * How much punctuation/symbol text gets spoken instead of silently
     * skipped. One of "none", "some" (common ones like . , ! ?), or "all".
     * Stored as a plain string rather than an enum since it's read/written
     * from exactly one screen and doesn't need type-safety elsewhere.
     */
    var punctuationLevel: String get() = p.getString("punctuation_level", "some") ?: "some"
        set(v) = p.edit().putString("punctuation_level", v).apply()

    /** Speak a run of 4+ identical symbol characters (e.g. "----") as a count instead of individually, e.g. "4 dashes". */
    var countRepeatedSymbolsEnabled: Boolean get() = p.getBoolean("count_repeated_symbols", true)
        set(v) = p.edit().putBoolean("count_repeated_symbols", v).apply()

    /** Mention text formatting (bold/italic/underline) present on the focused node's text, when the app actually marks it up that way. */
    var speakTextFormattingEnabled: Boolean get() = p.getBoolean("speak_text_formatting", true)
        set(v) = p.edit().putBoolean("speak_text_formatting", v).apply()

    /** Whether notifications should still be read aloud while the screen is off. Off by default, same as TalkBack, since an unattended phone announcing messages aloud can be undesirable (overheard, or just unnecessary battery/attention cost). */
    var speakNotificationsWhenScreenOffEnabled: Boolean get() = p.getBoolean("speak_notifications_screen_off", false)
        set(v) = p.edit().putBoolean("speak_notifications_screen_off", v).apply()

    /** When stepping character-by-character onto a letter, also speak a short example word for it (e.g. "h, hotel") to disambiguate similar-sounding letters. */
    var speakLettersWithExamplesEnabled: Boolean get() = p.getBoolean("speak_letters_with_examples", true)
        set(v) = p.edit().putBoolean("speak_letters_with_examples", v).apply()

    // ---- Reading granularity ------------------------------------------
    //
    // Which of the six ReadingGranularity values (Default, Character,
    // Word, Line, List, Copy) the up-then-down / down-then-up cycling
    // gestures step through. Lets the person hide granularities they
    // never use so cycling to the one they want takes fewer swipes.
    // Stored as a StringSet of enum names; defaults to "all of them"
    // so a fresh install behaves exactly like before this setting
    // existed. If the stored set ever ends up empty (e.g. the person
    // unchecked everything), callers should fall back to just DEFAULT
    // rather than getting stuck with no granularities to cycle through.

    var enabledGranularities: Set<String>
        get() = p.getStringSet("enabled_granularities", com.ms.screenreader.gestures.ReadingGranularity.entries.map { it.name }.toSet())
            ?: emptySet()
        set(v) = p.edit().putStringSet("enabled_granularities", v).apply()

    // ---- Remember focus -------------------------------------------------
    //
    // When leaving an app and coming back to it later (e.g. typing a
    // message in WhatsApp, going back to the home screen, then
    // reopening WhatsApp), restore accessibility focus to whichever
    // element it was last on in that app instead of resetting to
    // nothing / the first element. See NodeNavigator's
    // rememberFocus/restoreRememberedFocus.

    /** Master on/off switch for the remember-focus-per-app behavior. */
    var rememberFocusEnabled: Boolean get() = p.getBoolean("remember_focus_enabled", true)
        set(v) = p.edit().putBoolean("remember_focus_enabled", v).apply()

    /** Whether restoring a remembered focus should also announce it via TTS, or restore silently. */
    var readRememberedFocusOnReturn: Boolean get() = p.getBoolean("read_remembered_focus", true)
        set(v) = p.edit().putBoolean("read_remembered_focus", v).apply()

    /**
     * Content-URI (as string) of the folder the user picked via
     * ACTION_OPEN_DOCUMENT_TREE to hold their custom earcon sound files.
     * Null/empty means no sound scheme folder has been chosen yet.
     */
    var soundSchemeFolderUri: String?
        get() = p.getString("sound_scheme_folder_uri", null)
        set(v) = p.edit().putString("sound_scheme_folder_uri", v).apply()

    /** Master on/off switch for earcon sounds (independent of TTS speech). */
    var soundSchemeEnabled: Boolean get() = p.getBoolean("sound_scheme_enabled", true)
        set(v) = p.edit().putBoolean("sound_scheme_enabled", v).apply()

    /**
     * Master on/off switch for haptic feedback (short vibration on
     * focus/click/scroll/etc, alongside or instead of earcon sounds).
     * Default true, matching TalkBack's own default.
     */
    var vibrationEnabled: Boolean get() = p.getBoolean("vibration_enabled", true)
        set(v) = p.edit().putBoolean("vibration_enabled", v).apply()

    /** Master on/off switch for reading notifications aloud. */
    var notificationReaderEnabled: Boolean get() = p.getBoolean("notification_reader_enabled", true)
        set(v) = p.edit().putBoolean("notification_reader_enabled", v).apply()

    /**
     * Set of package names the user has chosen to silence (their
     * notifications will never be read aloud). Stored as a
     * StringSet under the hood.
     */
    var mutedNotificationPackages: Set<String>
        get() = p.getStringSet("muted_notification_packages", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("muted_notification_packages", v).apply()

    fun muteNotificationPackage(packageName: String) {
        mutedNotificationPackages = mutedNotificationPackages + packageName
    }

    fun unmuteNotificationPackage(packageName: String) {
        mutedNotificationPackages = mutedNotificationPackages - packageName
    }

    // ---- Per-app gesture scheme -------------------------------------
    //
    // Lets a specific app (e.g. WhatsApp, Gemini) override what a
    // gesture register (see GestureRegister - covers 1 to 4 fingers)
    // does *while that app is in the foreground*. Everywhere else the
    // gesture keeps its normal/global meaning.
    //
    // Stored as one StringSet, each entry encoded as
    // "packageName::REGISTER_NAME::ACTION_NAME" - avoids pulling in a
    // JSON library for what's a flat 3-field record. Package names and
    // enum names can't contain "::", so this is safe to split on.

    private val overrideDelimiter = "::"

    /**
     * Raw encoded overrides. Prefer [getAppGestureOverrides],
     * [setAppGestureOverride], and [clearAppGestureOverride] instead of
     * touching this directly.
     */
    private var rawAppGestureOverrides: Set<String>
        get() = p.getStringSet("app_gesture_overrides", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("app_gesture_overrides", v).apply()

    /** All per-app overrides as packageName -> (register -> action). */
    fun getAllAppGestureOverrides(): Map<String, Map<GestureRegister, GestureAction>> {
        val result = mutableMapOf<String, MutableMap<GestureRegister, GestureAction>>()
        for (entry in rawAppGestureOverrides) {
            val parts = entry.split(overrideDelimiter)
            if (parts.size != 3) continue
            val (packageName, registerName, actionName) = parts
            val register = runCatching { GestureRegister.valueOf(registerName) }.getOrNull() ?: continue
            val action = runCatching { GestureAction.valueOf(actionName) }.getOrNull() ?: continue
            result.getOrPut(packageName) { mutableMapOf() }[register] = action
        }
        return result
    }

    /** Overrides for one app only (empty map if it has none). */
    fun getAppGestureOverrides(packageName: String): Map<GestureRegister, GestureAction> =
        getAllAppGestureOverrides()[packageName] ?: emptyMap()

    /** Assign what [register] should do while [packageName] is in the foreground. */
    fun setAppGestureOverride(packageName: String, register: GestureRegister, action: GestureAction) {
        val withoutOldEntry = rawAppGestureOverrides.filterNot {
            it.startsWith("$packageName$overrideDelimiter${register.name}$overrideDelimiter")
        }
        rawAppGestureOverrides = (withoutOldEntry + "$packageName$overrideDelimiter${register.name}$overrideDelimiter${action.name}").toSet()
    }

    /** Remove a single app+register override (that gesture goes back to its global behavior in that app). */
    fun clearAppGestureOverride(packageName: String, register: GestureRegister) {
        rawAppGestureOverrides = rawAppGestureOverrides.filterNot {
            it.startsWith("$packageName$overrideDelimiter${register.name}$overrideDelimiter")
        }.toSet()
    }

    /** Remove every override for one app (that app goes back to fully global gesture behavior). */
    fun clearAllAppGestureOverrides(packageName: String) {
        rawAppGestureOverrides = rawAppGestureOverrides.filterNot {
            it.startsWith("$packageName$overrideDelimiter")
        }.toSet()
    }

    // ---- Gesture-launches-app (any register, from anywhere) ---------
    //
    // Different from the per-app override above: that one only changes
    // behavior *while a specific app is already open*. This one lets a
    // gesture register open a chosen app *from anywhere on the phone* -
    // e.g. "3-finger double tap -> open WhatsApp", no matter what's on
    // screen when it's performed. One register can only launch one app
    // at a time (it's a single slot), so setting it again replaces the
    // previous app.
    //
    // Stored the same delimiter-encoded way as above:
    // "REGISTER_NAME::packageName".

    private var rawGestureAppLaunches: Set<String>
        get() = p.getStringSet("gesture_app_launches", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("gesture_app_launches", v).apply()

    /** Every register that currently opens an app, mapped to which app. */
    fun getAllGestureAppLaunches(): Map<GestureRegister, String> {
        val result = mutableMapOf<GestureRegister, String>()
        for (entry in rawGestureAppLaunches) {
            val parts = entry.split(overrideDelimiter, limit = 2)
            if (parts.size != 2) continue
            val (registerName, packageName) = parts
            val register = runCatching { GestureRegister.valueOf(registerName) }.getOrNull() ?: continue
            result[register] = packageName
        }
        return result
    }

    /** Which app (if any) [register] currently opens, from anywhere. */
    fun getGestureAppLaunch(register: GestureRegister): String? =
        getAllGestureAppLaunches()[register]

    /** Make [register] open [packageName] from anywhere, on any screen. */
    fun setGestureAppLaunch(register: GestureRegister, packageName: String) {
        val withoutOldEntry = rawGestureAppLaunches.filterNot {
            it.startsWith("${register.name}$overrideDelimiter")
        }
        rawGestureAppLaunches = (withoutOldEntry + "${register.name}$overrideDelimiter$packageName").toSet()
    }

    /** Free up [register] so it goes back to its normal (non-app-launching) behavior. */
    fun clearGestureAppLaunch(register: GestureRegister) {
        rawGestureAppLaunches = rawGestureAppLaunches.filterNot {
            it.startsWith("${register.name}$overrideDelimiter")
        }.toSet()
    }

    // ---- Default (global) register mapping ---------------------------
    //
    // "Default Register Setting" - lets the person change what a
    // gesture register does *everywhere* (as opposed to the per-app
    // override above, which only applies inside one chosen app).
    // GestureManager still ships the original hardcoded mapping for
    // the 12 classic single-finger gestures as a fallback; whatever is
    // stored here takes priority over that once it's read at dispatch
    // time.
    //
    // Same delimiter-encoding as the other two tables above:
    // "REGISTER_NAME::ACTION_NAME".

    private var rawGlobalGestureOverrides: Set<String>
        get() = p.getStringSet("global_gesture_overrides", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("global_gesture_overrides", v).apply()

    /** Every register the person has redefined globally, mapped to its new action. */
    fun getAllGlobalGestureOverrides(): Map<GestureRegister, GestureAction> {
        val result = mutableMapOf<GestureRegister, GestureAction>()
        for (entry in rawGlobalGestureOverrides) {
            val parts = entry.split(overrideDelimiter)
            if (parts.size != 2) continue
            val (registerName, actionName) = parts
            val register = runCatching { GestureRegister.valueOf(registerName) }.getOrNull() ?: continue
            val action = runCatching { GestureAction.valueOf(actionName) }.getOrNull() ?: continue
            result[register] = action
        }
        return result
    }

    /** The globally-redefined action for [register], or null if it still uses its built-in default. */
    fun getGlobalGestureOverride(register: GestureRegister): GestureAction? =
        getAllGlobalGestureOverrides()[register]

    /** Redefine what [register] does everywhere in the app (until cleared). */
    fun setGlobalGestureOverride(register: GestureRegister, action: GestureAction) {
        val withoutOldEntry = rawGlobalGestureOverrides.filterNot {
            it.startsWith("${register.name}$overrideDelimiter")
        }
        rawGlobalGestureOverrides = (withoutOldEntry + "${register.name}$overrideDelimiter${action.name}").toSet()
    }

    /** Put [register] back to its original built-in behavior. */
    fun clearGlobalGestureOverride(register: GestureRegister) {
        rawGlobalGestureOverrides = rawGlobalGestureOverrides.filterNot {
            it.startsWith("${register.name}$overrideDelimiter")
        }.toSet()
    }

    /**
     * Wipes every gesture customization the person has made - global
     * overrides, per-app overrides, and gesture-launches-app - back to
     * a clean slate. Does not touch unrelated settings (call handling,
     * sound scheme, notification muting, etc).
     */
    fun resetAllGestureCustomizations() {
        rawGlobalGestureOverrides = emptySet()
        rawAppGestureOverrides = emptySet()
        rawGestureAppLaunches = emptySet()
    }
}
