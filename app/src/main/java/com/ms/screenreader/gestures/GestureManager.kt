package com.ms.screenreader.gestures

import android.accessibilityservice.AccessibilityService

/**
 * The navigation actions our gestures can trigger. Kept separate from
 * the raw Android gesture IDs so MSScreenReaderService doesn't need to
 * know about GestureDescription internals - it just asks "what action
 * does this gesture map to" and executes it.
 */
enum class GestureAction {
    NEXT_ELEMENT,
    PREVIOUS_ELEMENT,
    ACTIVATE,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    GO_BACK,
    GO_HOME,
    OPEN_NOTIFICATIONS,
    OPEN_QUICK_SETTINGS,
    RECENT_APPS,
    GO_TO_FIRST,
    GO_TO_LAST,
    TOGGLE_SPEECH,
    NEXT_GRANULARITY,
    PREVIOUS_GRANULARITY,
    OPEN_MAIN_MENU
}

/**
 * Translates the system's built-in single-finger swipe gestures
 * (detected automatically by Android once touch-exploration mode is on -
 * see accessibility_service_config.xml's flagRequestTouchExplorationMode)
 * into GestureAction values.
 *
 * Mapping (TalkBack-style defaults):
 *   swipe right  -> next element
 *   swipe left   -> previous element
 *   double-tap / swipe down-then-... -> handled by system as click, not here
 *   swipe up     -> scroll backward / previous group
 *   swipe down   -> scroll forward / next group
 *   swipe left+up (L shape)  -> go back
 *   swipe left+down          -> go home
 *   swipe right+up           -> open notifications
 *   swipe right+down         -> open quick settings
 *   swipe down+left          -> recent apps (overview)
 *   swipe down+right         -> jump to last element on screen ("to end")
 *   swipe up+left            -> jump to first element on screen ("to top")
 *   swipe up+right           -> open the screen reader's main menu
 *   swipe right+left         -> suspend/resume voice feedback (moved
 *                               here from up+right - see below)
 *   swipe up-then-down       -> next reading granularity (Default/Character/Word/Line/List/Copy)
 *   swipe down-then-up       -> previous reading granularity
 *
 * All eight single-stroke and L-shaped (two-stroke) gestures Android's
 * touch-exploration mode detects are now wired up here, plus three of
 * the four "reversal" gestures (see GestureRegister's kdoc) - only
 * left-then-right is still unused.
 *
 * Note on the up+right reassignment: earlier versions used swipe
 * up+right for TOGGLE_SPEECH. It now opens the main menu instead (a
 * screen-reader-menu entry point was requested to live on that
 * gesture specifically), so TOGGLE_SPEECH moved to the previously
 * unused right-then-left reversal gesture to keep a quick way to
 * mute/unmute speech. The on-screen Accessibility Button still
 * triggers TOGGLE_SPEECH directly regardless of this mapping (see
 * MSScreenReaderService.onAccessibilityButtonClicked).
 *
 * The two granularity reversal gestures don't change what swipe
 * up/down *mean* by themselves - they just fire
 * GestureAction.NEXT_GRANULARITY / PREVIOUS_GRANULARITY. It's
 * MSScreenReaderService that tracks which ReadingGranularity is
 * currently active and, once it's anything other than DEFAULT,
 * reinterprets SCROLL_FORWARD/SCROLL_BACKWARD (still what swipe
 * down/up map to here) as that granularity's forward/backward step
 * instead of a container scroll.
 *
 * mapGesture() only ever returns the *hardcoded* action for a given
 * Android gesture id - it has no knowledge of the person's own
 * customizations. MSScreenReaderService.resolveAction() is what
 * actually decides what a gesture does at dispatch time: it checks
 * gesture-launches-app, then the per-app override for whichever app is
 * in the foreground, then the global override, and only falls back to
 * this hardcoded mapping if none of those are set. See
 * MSScreenReaderService's kdoc and SettingsRepository's per-app/global
 * override sections.
 */
class GestureManager {

    fun mapGesture(gestureId: Int): GestureAction? = when (gestureId) {
        AccessibilityService.GESTURE_SWIPE_RIGHT -> GestureAction.NEXT_ELEMENT
        AccessibilityService.GESTURE_SWIPE_LEFT -> GestureAction.PREVIOUS_ELEMENT
        AccessibilityService.GESTURE_SWIPE_UP -> GestureAction.SCROLL_BACKWARD
        AccessibilityService.GESTURE_SWIPE_DOWN -> GestureAction.SCROLL_FORWARD
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP -> GestureAction.GO_BACK
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN -> GestureAction.GO_HOME
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP -> GestureAction.OPEN_NOTIFICATIONS
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN -> GestureAction.OPEN_QUICK_SETTINGS
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT -> GestureAction.RECENT_APPS
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT -> GestureAction.GO_TO_LAST
        AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT -> GestureAction.GO_TO_FIRST
        AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT -> GestureAction.OPEN_MAIN_MENU
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT -> GestureAction.TOGGLE_SPEECH
        AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN -> GestureAction.NEXT_GRANULARITY
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP -> GestureAction.PREVIOUS_GRANULARITY
        else -> null
    }

    /** Legacy no-op kept for source compatibility with earlier v1.0 callers. */
    fun handleGesture(gesture: String) { /* superseded by mapGesture(gestureId: Int) */ }
}
