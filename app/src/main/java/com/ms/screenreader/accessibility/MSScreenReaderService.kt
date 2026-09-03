package com.ms.screenreader.accessibility

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.ms.screenreader.calls.CallHandlingManager
import com.ms.screenreader.gestures.GestureAction
import com.ms.screenreader.gestures.GestureManager
import com.ms.screenreader.gestures.GestureRegister
import com.ms.screenreader.gestures.NodeNavigator
import com.ms.screenreader.gestures.ReadingGranularity
import com.ms.screenreader.menu.MainMenuActivity
import com.ms.screenreader.settings.SettingsRepository
import com.ms.screenreader.sounds.SoundEvent
import com.ms.screenreader.sounds.SoundSchemeManager
import com.ms.screenreader.tts.TtsManager

/**
 * Core navigation pipeline:
 * - v1.1: speaks focused/clicked elements via TTS.
 * - v1.2: plays user-configured earcons per event (SoundSchemeManager).
 * - v1.3: single-finger swipe gestures for TalkBack-style linear
 *   navigation - swipe right/left moves to the next/previous element,
 *   swipe down/up scrolls forward/backward. Only 3 of the 8 possible
 *   L-shaped (two-stroke) gestures were wired up.
 * - v1.4: notification reading with filtering (see NotificationReader).
 * - v1.5: all 8 L-shaped swipe gestures wired up - back, home,
 *   notifications, quick settings, recent apps, jump to first/last
 *   element on screen, and suspend/resume voice feedback.
 * - This version: call handling (CallHandlingManager) - announces
 *   incoming calls and finished-call duration via TTS, and answers a
 *   ringing call on a volume-key press (onKeyEvent below) when the
 *   user has that turned on. Power-button-ends-call is intentionally
 *   NOT implemented - see CallHandlingManager's kdoc for why.
 * - v1.8/v1.9: per-app gesture scheme groundwork - GestureRegister
 *   (all 43 possible 1-4 finger gestures) and SettingsRepository
 *   storage for per-app overrides and gesture-launches-app, data only.
 * - v1.10: foreground app tracking (currentForegroundPackage, updated
 *   from TYPE_WINDOW_STATE_CHANGED) - Step 2 of the per-app gesture
 *   scheme. Nothing reads this yet.
 * - v1.12: reading granularity (ReadingGranularity) - the two
 *   previously-unused swipe up-then-down / down-then-up reversal
 *   gestures cycle through Default/Character/Word/Line/List/Copy.
 *   Once a non-Default granularity is active, plain swipe down/up stop
 *   scrolling and instead step through the focused node's text a
 *   character/word/line at a time, jump between list-item nodes, or
 *   copy/append the focused text to the clipboard - see
 *   handleGranularityStep(). Every mode change is spoken by name since
 *   there's no visual indicator of which one is active.
 * - v1.13: gesture dispatch honors customizations for single-finger
 *   gestures. resolveAction() checks, in order:
 *     gesture-launches-app (works from anywhere) -> per-app override
 *     for whichever app is in the foreground -> global override ->
 *     GestureManager's hardcoded default. This is what made the
 *     "Default/Per-App Register Setting" screens actually do something
 *     instead of just storing choices nobody read yet.
 * - This version: **multi-finger gesture detection** (Step 3 of the
 *   per-app gesture scheme, docs/REMAINING_WORK.md item #1) - added
 *   onGesture(AccessibilityGestureEvent), API 33+ only, which is how
 *   Android reports 2/3/4-finger swipes and taps. Shares the same
 *   dispatchGesture() as the older single-finger onGesture(Int)
 *   overload, so all 43 GestureRegister entries (not just the 16
 *   single-finger ones) now actually fire when their gesture is
 *   performed - provided the person has assigned them something via
 *   Default/Per-App Register Setting or gesture-launches-app, since
 *   multi-finger gestures have no hardcoded default action. See
 *   dispatchGesture()'s kdoc for how the two onGesture overloads avoid
 *   double-firing on API 33+, and its own kdoc for why API 26-32
 *   simply can't reach multi-finger gestures at all.
 *   resolveAction() checks, in order:
 *     gesture-launches-app (works from anywhere) -> per-app override
 *     for whichever app is in the foreground -> global override ->
 *     GestureManager's hardcoded default. This is what makes the
 *     "Default/Per-App Register Setting" screens actually do something
 *     instead of just storing choices nobody reads yet.
 *   - **Main menu**: swipe up+right now opens MainMenuActivity (moved
 *     off TOGGLE_SPEECH, which relocated to the swipe right-then-left
 *     reversal gesture - see GestureManager's kdoc for why).
 *   - **Reading granularity now respects SettingsRepository.enabledGranularities**
 *     - cycling only visits the modes the person has left checked in
 *       the new Reading Granularities settings screen, always falling
 *       back to just DEFAULT if they somehow unchecked everything.
 *   - **Remember focus per app**: leaving an app and coming back to it
 *     restores accessibility focus to wherever it was left (e.g. typing
 *     a message in WhatsApp, going back to the home screen, then
 *     reopening WhatsApp lands back on the same element) instead of
 *     resetting. Governed by SettingsRepository.rememberFocusEnabled /
 *     readRememberedFocusOnReturn - see rememberCurrentFocus() and the
 *     TYPE_WINDOW_STATE_CHANGED handling in onAccessibilityEvent().
 *
 * Still pending: per-app scheme UI's installed-apps picker (currently
 * manual package-name entry), volume-key quick on/off toggle for the
 * service itself, real-device testing of all gestures and call
 * handling (including whether the two onGesture overloads behave on a
 * real device the way their kdoc above assumes). See
 * docs/REMAINING_WORK.md.
 */
class MSScreenReaderService : AccessibilityService() {

    companion object {
        /**
         * Lets MainMenuActivity reach the running service to trigger
         * actions like suspend/resume voice feedback without a bound
         * service connection - simplest option for a menu that's only
         * ever launched while the service is already connected (it's
         * the one that opens the menu). Nulled out in onDestroy so a
         * dead service isn't held onto by mistake.
         */
        private var instance: MSScreenReaderService? = null

        /** The running service instance, or null if the accessibility service isn't connected right now. */
        fun getRunningInstance(): MSScreenReaderService? = instance
    }

    private lateinit var tts: TtsManager
    private lateinit var soundScheme: SoundSchemeManager
    private lateinit var gestureManager: GestureManager
    private lateinit var nodeNavigator: NodeNavigator
    private lateinit var settings: SettingsRepository
    private lateinit var callHandling: CallHandlingManager

    private var lastSpoken: String? = null
    private var lastEventTimeMs: Long = 0L
    private var lastScrollY = -1

    /**
     * Which granularity plain swipe up/down currently perform. Cycled
     * by the swipe-up-then-down / swipe-down-then-up reversal gestures
     * (GestureAction.NEXT_GRANULARITY / PREVIOUS_GRANULARITY - see
     * GestureManager's kdoc). Not persisted: always starts fresh at
     * DEFAULT when the service (re)connects, see ReadingGranularity's
     * kdoc for why.
     */
    private var granularity: ReadingGranularity = ReadingGranularity.DEFAULT

    /**
     * Accumulates text across repeated COPY-granularity "append" swipes
     * (swipe up while in COPY mode) so a user can build up a multi-element
     * selection before it lands on the clipboard. Reset every time a
     * fresh "copy" (swipe down in COPY mode) starts a new clipboard
     * entry rather than appending to the previous one.
     */
    private val clipboardAccumulator = StringBuilder()

    /**
     * Package name of whichever app is currently in the foreground,
     * updated from TYPE_WINDOW_STATE_CHANGED events. This is Step 2 of
     * the per-app gesture scheme (see docs/REMAINING_WORK.md item #1) -
     * tracking alone, nothing reads this yet to change gesture
     * behavior. That lookup (per-app override / gesture-launches-app)
     * gets wired in on top of this in a later step.
     *
     * Not reliable on every launcher/OS skin the instant the service
     * connects (there's no window-state event until something changes),
     * so treat null as "unknown yet" rather than "no app open".
     */
    private var currentForegroundPackage: String? = null

    /** Package name of the app currently in the foreground, or null if not known yet. */
    fun getCurrentForegroundPackage(): String? = currentForegroundPackage

    private val handledEventTypes = setOf(
        AccessibilityEvent.TYPE_VIEW_FOCUSED,
        AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
        AccessibilityEvent.TYPE_VIEW_CLICKED,
        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
        AccessibilityEvent.TYPE_VIEW_SELECTED,
        AccessibilityEvent.TYPE_VIEW_SCROLLED,
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
    )

    override fun onServiceConnected() {
        super.onServiceConnected()

        // The OS can call onServiceConnected() more than once on the
        // same living instance (e.g. the service is restarted without
        // the process dying). Without this cleanup, re-running the
        // block below would silently overwrite tts/soundScheme/
        // callHandling with fresh instances while the old ones leaked:
        // the old TextToSpeech engine binding never released, the old
        // MediaPlayer never released, and the old PhoneStateListener
        // never unregistered from TelephonyManager (still firing on an
        // orphaned CallHandlingManager with no reference left to stop
        // it).
        if (::tts.isInitialized) tts.shutdown()
        if (::soundScheme.isInitialized) soundScheme.release()
        if (::callHandling.isInitialized) callHandling.unregister()

        tts = TtsManager(this)
        soundScheme = SoundSchemeManager(this)
        gestureManager = GestureManager()
        settings = SettingsRepository(this)
        nodeNavigator = NodeNavigator(this, settings)
        callHandling = CallHandlingManager(this, settings, tts)
        callHandling.register()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Window/content changed under us - the flattened node list from
        // NodeNavigator is now stale, so drop it and rebuild lazily on
        // the next gesture.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (::nodeNavigator.isInitialized) nodeNavigator.refresh()
            // Track which app just came to the foreground (Step 2 of the
            // per-app gesture scheme). Ignore our own overlay/settings
            // window changes with no package, and same-package repeats
            // (e.g. a dialog opening inside the same app) don't need
            // re-tracking but overwriting with the same value is harmless.
            event.packageName?.toString()?.let { pkg ->
                if (pkg.isNotBlank()) {
                    currentForegroundPackage = pkg
                    tryRestoreRememberedFocus(pkg)
                }
            }
        }

        if (event.eventType !in handledEventTypes) return

        playEarconFor(event)
        speakFor(event)
    }

    /**
     * If remember-focus is on and we have a remembered element for
     * [packageName] (see NodeNavigator.rememberFocus), tries to move
     * accessibility focus back onto it now that this app's window has
     * just come to the foreground and its node list has been rebuilt.
     * Silently does nothing if the setting is off, nothing was
     * remembered, or the remembered element isn't found this time
     * (e.g. the screen has changed since).
     */
    private fun tryRestoreRememberedFocus(packageName: String) {
        if (!::settings.isInitialized || !settings.rememberFocusEnabled) return
        if (!::nodeNavigator.isInitialized) return
        val restored = nodeNavigator.restoreRememberedFocus(packageName) ?: return
        if (::soundScheme.isInitialized) soundScheme.play(SoundEvent.FOCUS_CHANGE)
        if (settings.readRememberedFocusOnReturn) announce(restored)
    }

    /**
     * Handles the system's built-in single-finger swipe gestures, detected
     * automatically once touch exploration is active (see
     * accessibility_service_config.xml). Deprecated on newer APIs in favor
     * of onGesture(AccessibilityGestureEvent), but this overload is kept
     * for broad compatibility down to minSdk 26.
     */
    @Suppress("DEPRECATION")
    override fun onGesture(gestureId: Int): Boolean {
        if (dispatchGesture(gestureId)) return true
        return super.onGesture(gestureId)
    }

    /**
     * Handles 2/3/4-finger gestures - Step 3 of the per-app gesture
     * scheme (docs/REMAINING_WORK.md item #1). Only exists from API 33
     * (Tiramisu) onward, since AccessibilityGestureEvent itself was
     * added then; on API 26-32 these registers simply aren't reachable
     * from an actual finger gesture (a platform limitation, not
     * something fixable in app code) - the deprecated onGesture(Int)
     * below still covers single-finger gestures on every supported
     * API level.
     *
     * Deliberately reuses the same dispatchGesture() as the
     * single-finger path below rather than duplicating its logic. On
     * API 33+, the two overloads never double-fire for the same
     * gesture: when this one returns true, super.onGesture() (and
     * therefore the framework's default onGesture(int) forwarding) is
     * never reached; when it returns false, falling through to
     * super.onGesture(gestureEvent) invokes onGesture(Int) as a
     * fallback with the same id, which safely finds nothing new to do
     * and also returns false.
     *
     * Multi-finger gestures have no hardcoded default action in
     * GestureManager - unlike single-finger swipes, a raw 2/3/4-finger
     * swipe means nothing until the person assigns it something via
     * the Default/Per-App Register Setting screens or
     * gesture-launches-app. That's why dispatchGesture() finding no
     * override for one of these registers is the normal, expected
     * outcome, not a bug.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (dispatchGesture(gestureEvent.gestureId)) return true
        return super.onGesture(gestureEvent)
    }

    /**
     * Shared by both onGesture overloads. Resolves and performs
     * whatever [gestureId] should do, in priority order:
     * gesture-launches-app (works from anywhere, any finger count) ->
     * per-app override for the foreground app -> global override ->
     * (single-finger gestures only) GestureManager's hardcoded
     * default. Returns false if nothing is assigned to this gesture at
     * all, so the caller can let the system handle it normally
     * instead.
     */
    private fun dispatchGesture(gestureId: Int): Boolean {
        if (!::gestureManager.isInitialized || !::settings.isInitialized) return false

        val register = GestureRegister.fromAndroidGestureId(gestureId)
        if (register != null) {
            val appToLaunch = settings.getGestureAppLaunch(register)
            if (appToLaunch != null) {
                launchApp(appToLaunch)
                return true
            }
        }

        val action = resolveAction(register, gestureId) ?: return false
        performAction(action)
        return true
    }

    /**
     * Decides what a single-finger gesture should do, in priority
     * order: per-app override for whichever app is currently in the
     * foreground, then a global override, then GestureManager's
     * hardcoded default. Gesture-launches-app is checked separately in
     * onGesture() before this, since it isn't a GestureAction at all -
     * it opens an app directly.
     */
    private fun resolveAction(register: GestureRegister?, gestureId: Int): GestureAction? {
        if (register != null) {
            currentForegroundPackage?.let { pkg ->
                settings.getAppGestureOverrides(pkg)[register]?.let { return it }
            }
            settings.getGlobalGestureOverride(register)?.let { return it }
        }
        return gestureManager.mapGesture(gestureId)
    }

    /** Launches [packageName]'s default launch activity, if it's installed. Silently no-ops otherwise (e.g. the app was uninstalled since the gesture was set up). */
    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * Intercepts hardware key events (enabled via
     * flagRequestFilterKeyEvents in accessibility_service_config.xml).
     * Used only to answer a ringing call on a volume-key press when the
     * user has turned that on in settings - every other key passes
     * through untouched so normal volume control still works.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolumeKey &&
            event.action == KeyEvent.ACTION_DOWN &&
            ::settings.isInitialized && settings.volumeAnswerEnabled &&
            ::callHandling.isInitialized && callHandling.isRinging()
        ) {
            callHandling.answerCall()
            return true // consume it - don't also change ringer volume
        }
        return super.onKeyEvent(event)
    }

    /**
     * Called when the user taps the on-screen "Accessibility Button"
     * (enabled via the flagRequestAccessibilityButton flag inside
     * android:accessibilityFlags in accessibility_service_config.xml -
     * appears as an extra icon on
     * the navigation bar, or reachable via the floating accessibility
     * menu on gesture-nav devices). Reuses the same suspend/resume
     * logic as the swipe-up+right gesture, giving a second physical
     * way to quickly mute/unmute speech - handy on devices where the
     * volume-key "Accessibility Shortcut" is already used for
     * something else, or where the person prefers a tap over a swipe.
     */
    override fun onAccessibilityButtonClicked() {
        if (::settings.isInitialized && !settings.accessibilityShortcutEnabled) return
        performAction(GestureAction.TOGGLE_SPEECH)
    }

    /**
     * Suspends/resumes voice feedback, same as the swipe right-then-left
     * gesture or the on-screen Accessibility Button. Exposed as a public
     * entry point so MainMenuActivity (a separate Activity, not part of
     * this service) can trigger it via [getRunningInstance] without
     * needing a bound-service connection.
     */
    fun requestToggleSpeech() {
        performAction(GestureAction.TOGGLE_SPEECH)
    }

    /**
     * Fully disables this accessibility service - not just voice
     * suspend/resume (TOGGLE_SPEECH above), but the same thing as the
     * person switching it off from system Settings > Accessibility.
     * This is item #5's missing piece (docs/REMAINING_WORK.md):
     * the OS's own volume-key "Accessibility Shortcut" and the
     * on-screen Accessibility Button already exist as OS-level
     * on/off toggles for the service, but there was no way to turn
     * the whole service off *from inside* the app itself until now.
     *
     * Uses AccessibilityService.disableSelf() (public API since 24,
     * well within our minSdk 26) - the platform's own sanctioned way
     * for a service to switch itself off; nothing home-grown here.
     *
     * One-way from in-app: once disabled, this class stops running
     * entirely (onDestroy fires), so there's no in-app method to turn
     * it back on again - the person has to either go back into
     * Settings > Accessibility, or use the OS's volume-key shortcut /
     * on-screen Accessibility Button if they've set this service as
     * that shortcut's target (both are OS-level and work independently
     * of whether the service process is currently running). The
     * confirmation dialog before calling this (see MainMenuActivity)
     * exists specifically because of this one-wayness.
     */
    fun disableServiceCompletely() {
        disableSelf()
    }

    private fun performAction(action: GestureAction) {
        when (action) {
            GestureAction.NEXT_ELEMENT -> {
                val description = nodeNavigator.moveNext()
                soundScheme.play(SoundEvent.FOCUS_CHANGE)
                description?.let { announce(it); rememberCurrentFocus(it) }
            }
            GestureAction.PREVIOUS_ELEMENT -> {
                val description = nodeNavigator.movePrevious()
                soundScheme.play(SoundEvent.FOCUS_CHANGE)
                description?.let { announce(it); rememberCurrentFocus(it) }
            }
            GestureAction.ACTIVATE -> {
                val activated = nodeNavigator.activateCurrent()
                if (activated) soundScheme.play(SoundEvent.CLICK)
            }
            GestureAction.SCROLL_FORWARD -> handleGranularityStep(forward = true)
            GestureAction.SCROLL_BACKWARD -> handleGranularityStep(forward = false)
            GestureAction.GO_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GestureAction.GO_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GestureAction.OPEN_NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.OPEN_QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.RECENT_APPS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GestureAction.GO_TO_FIRST -> {
                val description = nodeNavigator.moveToFirst()
                soundScheme.play(SoundEvent.FOCUS_CHANGE)
                description?.let { announce(it); rememberCurrentFocus(it) }
            }
            GestureAction.GO_TO_LAST -> {
                val description = nodeNavigator.moveToLast()
                soundScheme.play(SoundEvent.FOCUS_CHANGE)
                description?.let { announce(it); rememberCurrentFocus(it) }
            }
            GestureAction.TOGGLE_SPEECH -> {
                if (::tts.isInitialized) {
                    val nowMuted = tts.toggleMute()
                    // Speech itself may just have been turned off, so rely on
                    // the earcon (and haptics, via SoundSchemeManager) rather
                    // than a spoken announcement to confirm the new state.
                    if (::soundScheme.isInitialized) {
                        soundScheme.play(if (nowMuted) SoundEvent.SPEECH_SUSPENDED else SoundEvent.SPEECH_RESUMED)
                    }
                }
            }
            GestureAction.NEXT_GRANULARITY -> cycleGranularity(forward = true)
            GestureAction.PREVIOUS_GRANULARITY -> cycleGranularity(forward = false)
            GestureAction.OPEN_MAIN_MENU -> openMainMenu()
        }
    }

    /** Records the currently focused node's description against the current foreground package, for remember-focus-per-app to use later (see NodeNavigator.rememberFocus). */
    private fun rememberCurrentFocus(description: String) {
        if (!::settings.isInitialized || !settings.rememberFocusEnabled) return
        nodeNavigator.rememberFocus(currentForegroundPackage, description)
    }

    /**
     * The subset of ReadingGranularity values the person has left
     * checked in the Reading Granularities settings screen (see
     * SettingsRepository.enabledGranularities). Falls back to just
     * DEFAULT if that set is somehow empty, so cycling never gets
     * stuck with nothing to land on.
     */
    private fun activeGranularities(): List<ReadingGranularity> {
        val enabledNames = if (::settings.isInitialized) settings.enabledGranularities else emptySet()
        val filtered = ReadingGranularity.entries.filter { it.name in enabledNames }
        return filtered.ifEmpty { listOf(ReadingGranularity.DEFAULT) }
    }

    /** Moves to the next/previous granularity within the person's enabled subset (wrapping), and announces the new mode. */
    private fun cycleGranularity(forward: Boolean) {
        val active = activeGranularities()
        val currentPosition = active.indexOf(granularity).let { if (it == -1) 0 else it }
        val nextPosition = if (forward) {
            (currentPosition + 1) % active.size
        } else {
            (currentPosition - 1 + active.size) % active.size
        }
        changeGranularity(active[nextPosition])
    }

    /** Opens the screen reader's main menu (swipe up+right). A normal Activity launched with NEW_TASK since accessibility services aren't themselves Activities. */
    private fun openMainMenu() {
        val intent = Intent(this, MainMenuActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * Applies swipe down (forward=true) / swipe up (forward=false)
     * according to whichever [ReadingGranularity] is currently active.
     * DEFAULT keeps the original v1.3 behavior (scroll the focused
     * container); every other mode steps through the focused node's
     * text (or, for LIST, jumps between list-item nodes; for COPY,
     * copies/appends to the clipboard) instead of scrolling.
     */
    private fun handleGranularityStep(forward: Boolean) {
        when (granularity) {
            ReadingGranularity.DEFAULT -> {
                val action = if (forward) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val scrolled = scrollFocusedContainer(action)
                if (scrolled) soundScheme.play(if (forward) SoundEvent.SCROLL_DOWN else SoundEvent.SCROLL_UP)
            }
            ReadingGranularity.CHARACTER -> {
                nodeNavigator.stepCharacter(forward)?.let { announce(it) }
            }
            ReadingGranularity.WORD -> {
                nodeNavigator.stepWord(forward)?.let { announce(it) }
            }
            ReadingGranularity.LINE -> {
                nodeNavigator.stepLine(forward)?.let { announce(it) }
            }
            ReadingGranularity.LIST -> {
                val description = if (forward) nodeNavigator.moveNextListItem() else nodeNavigator.movePreviousListItem()
                soundScheme.play(SoundEvent.FOCUS_CHANGE)
                description?.let { announce(it); rememberCurrentFocus(it) }
            }
            ReadingGranularity.COPY -> {
                if (forward) copyCurrentToClipboard() else appendCurrentToClipboard()
            }
        }
    }

    /** Switches the active granularity, resets its sub-node cursors, and announces the new mode by name (there's no on-screen indicator). */
    private fun changeGranularity(next: ReadingGranularity) {
        granularity = next
        nodeNavigator.resetSubNodeCursors()
        soundScheme.play(SoundEvent.GRANULARITY_CHANGE)
        announce(granularity.label)
    }

    /** COPY granularity, swipe down: replaces the clipboard with the focused node's text and starts a fresh accumulator for any following "append" swipes. */
    private fun copyCurrentToClipboard() {
        val text = nodeNavigator.currentText() ?: return
        clipboardAccumulator.clear()
        clipboardAccumulator.append(text)
        writeClipboard(clipboardAccumulator.toString())
        soundScheme.play(SoundEvent.COPIED)
        announce("Copied")
    }

    /** COPY granularity, swipe up: appends the focused node's text to whatever's already been copied/appended this session. */
    private fun appendCurrentToClipboard() {
        val text = nodeNavigator.currentText() ?: return
        if (clipboardAccumulator.isNotEmpty()) clipboardAccumulator.append("\n")
        clipboardAccumulator.append(text)
        writeClipboard(clipboardAccumulator.toString())
        soundScheme.play(SoundEvent.APPENDED)
        announce("Appended")
    }

    private fun writeClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MS Screen Reader", text))
    }

    /**
     * Finds a scrollable node in the current window (starting from the
     * root, first scrollable container found) and performs the given
     * scroll action on it. Returns true if a scrollable node was found
     * and the action was dispatched.
     */
    private fun scrollFocusedContainer(action: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findFirstScrollable(root) ?: return false
        return scrollable.performAction(action)
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstScrollable(child)
            if (found != null) return found
        }
        return null
    }

    /** Speaks arbitrary text immediately (used for gesture-driven navigation, not just events). */
    private fun announce(text: String) {
        if (!::tts.isInitialized || text.isBlank()) return
        lastSpoken = text
        lastEventTimeMs = System.currentTimeMillis()
        tts.speak(text)
    }

    private fun playEarconFor(event: AccessibilityEvent) {
        if (!::soundScheme.isInitialized) return

        val soundEvent = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> SoundEvent.CLICK
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> SoundEvent.LONG_PRESS
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> SoundEvent.FOCUS_CHANGE
            AccessibilityEvent.TYPE_VIEW_SELECTED -> SoundEvent.SELECTION
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> SoundEvent.WINDOW_CHANGE
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> SoundEvent.TEXT_CHANGED
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> resolveScrollDirection(event)
            else -> null
        } ?: return

        soundScheme.play(soundEvent)
    }

    private fun resolveScrollDirection(event: AccessibilityEvent): SoundEvent {
        val scrollY = event.scrollY
        val direction = if (lastScrollY >= 0 && scrollY < lastScrollY) {
            SoundEvent.SCROLL_UP
        } else {
            SoundEvent.SCROLL_DOWN
        }
        lastScrollY = scrollY
        return direction
    }

    private fun speakFor(event: AccessibilityEvent) {
        if (!::tts.isInitialized) return

        // Verbosity: "Speak window names" gates only the window-change
        // announcement itself - view focus/click/etc. from inside that
        // window still speak normally regardless of this setting.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            ::settings.isInitialized && !settings.speakWindowNamesEnabled
        ) return

        // System navigation-bar buttons (Back/Home/Recents) fire both a
        // focus/hover event and a click event within milliseconds of each
        // other, often with slightly different text between the two (and
        // between gesture-nav vs 3-button-nav on the same phone) - which
        // slips past the identical-text dedupe below and sounds like a
        // double announcement. For these specific buttons we speak only
        // once, using our own fixed label, and only on the click event
        // (the more reliable signal that the button was actually
        // activated) - the plain focus/hover event for them is skipped.
        val navBarLabel = navigationBarButtonLabel(event)
        if (navBarLabel != null) {
            if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
            val now = System.currentTimeMillis()
            if (navBarLabel == lastSpoken && now - lastEventTimeMs < 400) return
            lastSpoken = navBarLabel
            lastEventTimeMs = now
            tts.speak(navBarLabel)
            return
        }

        val description = extractDescription(event)
        if (description.isNullOrBlank()) return

        val now = System.currentTimeMillis()
        if (description == lastSpoken && now - lastEventTimeMs < 400) return

        lastSpoken = description
        lastEventTimeMs = now
        tts.speak(description)

        // Natural focus/hover events (touch-explore, not our own swipe
        // navigation) are just as valid a "this is where the user left
        // off" signal as a swipe - remember them too, so remember-focus
        // works whether the person swipes or drags a finger around.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
        ) {
            rememberCurrentFocus(description)
        }
    }

    /**
     * Nav-bar double-announce fix: returns a fixed, clean label ("Back",
     * "Home", "Recent apps") if [event] came from one of the system
     * navigation bar's own buttons, or null for everything else
     * (including every other systemui element, like quick settings
     * tiles or the notification shade, which should speak normally).
     * Matching is by resource ID rather than text/contentDescription,
     * since those can vary between gesture-navigation and 3-button-
     * navigation modes on the same phone - the ID is stable either way.
     */
    private fun navigationBarButtonLabel(event: AccessibilityEvent): String? {
        if (event.packageName?.toString() != "com.android.systemui") return null
        val source = event.source ?: return null
        val resourceId = try {
            source.viewIdResourceName
        } finally {
            source.recycle()
        } ?: return null
        return when {
            resourceId.endsWith(":id/back") -> "Back"
            resourceId.endsWith(":id/home") -> "Home"
            resourceId.endsWith(":id/home_handle") -> "Home"
            resourceId.endsWith(":id/recent_apps") -> "Recent apps"
            else -> null
        }
    }

    private fun extractDescription(event: AccessibilityEvent): String? {
        event.contentDescription?.toString()?.let { if (it.isNotBlank()) return it }

        val eventText = event.text?.filter { it.isNotBlank() }?.joinToString(", ")
        if (!eventText.isNullOrBlank()) return eventText

        val source: AccessibilityNodeInfo? = event.source
        try {
            source?.let { node ->
                node.contentDescription?.toString()?.let { if (it.isNotBlank()) return it }
                node.text?.toString()?.let { if (it.isNotBlank()) return it }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    try {
                        child.contentDescription?.toString()?.let { if (it.isNotBlank()) return it }
                        child.text?.toString()?.let { if (it.isNotBlank()) return it }
                    } finally {
                        child.recycle()
                    }
                }
            }
        } finally {
            source?.recycle()
        }
        return null
    }

    override fun onInterrupt() {
        if (::tts.isInitialized) tts.stop()
        if (::soundScheme.isInitialized) soundScheme.release()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        if (::soundScheme.isInitialized) soundScheme.release()
        if (::callHandling.isInitialized) callHandling.unregister()
        if (instance === this) instance = null
        super.onDestroy()
    }
}
