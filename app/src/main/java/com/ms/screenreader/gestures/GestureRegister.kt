package com.ms.screenreader.gestures

import android.accessibilityservice.AccessibilityService

/**
 * Every gesture "slot" (register) that Android's accessibility gesture
 * detector can report to us, across 1 to 4 fingers. This is the full
 * pool a per-app gesture scheme can pick from.
 *
 * Why this exists separately from [GestureManager]:
 * [GestureManager] only knows about the 12 single-finger gestures that
 * are wired to *global* actions today. This enum additionally covers:
 *  - the 4 single-finger "reversal" gestures Android supports; two are
 *    now wired to global actions (swipe up-then-down / down-then-up
 *    cycle the reading granularity - see GestureManager and
 *    ReadingGranularity), the other two (left-then-right,
 *    right-then-left) are still unused
 *  - all 2/3/4-finger swipes and taps, which Android only detects as
 *    straight up/down/left/right (no diagonal/L-shaped multi-finger
 *    gestures exist on the platform - that shape is 1-finger only)
 *
 * Each entry stores the raw Android gesture-id int (stable public API
 * constants from AccessibilityService) plus how many fingers it takes,
 * so storage/UI code can group and label them without re-deriving this
 * mapping each time.
 */
enum class GestureRegister(val fingerCount: Int, val androidGestureId: Int, val label: String) {

    // ---- 1-finger: already wired to global actions in GestureManager ----
    F1_SWIPE_UP(1, AccessibilityService.GESTURE_SWIPE_UP, "1-finger swipe up"),
    F1_SWIPE_DOWN(1, AccessibilityService.GESTURE_SWIPE_DOWN, "1-finger swipe down"),
    F1_SWIPE_LEFT(1, AccessibilityService.GESTURE_SWIPE_LEFT, "1-finger swipe left"),
    F1_SWIPE_RIGHT(1, AccessibilityService.GESTURE_SWIPE_RIGHT, "1-finger swipe right"),
    F1_SWIPE_LEFT_UP(1, AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP, "1-finger swipe left-then-up"),
    F1_SWIPE_LEFT_DOWN(1, AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN, "1-finger swipe left-then-down"),
    F1_SWIPE_RIGHT_UP(1, AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP, "1-finger swipe right-then-up"),
    F1_SWIPE_RIGHT_DOWN(1, AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN, "1-finger swipe right-then-down"),
    F1_SWIPE_UP_LEFT(1, AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT, "1-finger swipe up-then-left"),
    F1_SWIPE_UP_RIGHT(1, AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT, "1-finger swipe up-then-right"),
    F1_SWIPE_DOWN_LEFT(1, AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT, "1-finger swipe down-then-left"),
    F1_SWIPE_DOWN_RIGHT(1, AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT, "1-finger swipe down-then-right"),

    // ---- 1-finger: reversal gestures ----
    // up-then-down / down-then-up are wired to global actions (next/
    // previous reading granularity - see GestureManager). right-then-
    // left now carries TOGGLE_SPEECH (moved off up+right, which opens
    // the main menu instead). left-then-right remains unused.
    F1_SWIPE_UP_DOWN(1, AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN, "1-finger swipe up-then-down"),
    F1_SWIPE_DOWN_UP(1, AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP, "1-finger swipe down-then-up"),
    F1_SWIPE_LEFT_RIGHT(1, AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT, "1-finger swipe left-then-right"),
    F1_SWIPE_RIGHT_LEFT(1, AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT, "1-finger swipe right-then-left"),

    // ---- 2-finger: straight swipes + taps (no diagonals on this platform) ----
    F2_SWIPE_UP(2, AccessibilityService.GESTURE_2_FINGER_SWIPE_UP, "2-finger swipe up"),
    F2_SWIPE_DOWN(2, AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN, "2-finger swipe down"),
    F2_SWIPE_LEFT(2, AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT, "2-finger swipe left"),
    F2_SWIPE_RIGHT(2, AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT, "2-finger swipe right"),
    F2_TAP_SINGLE(2, AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP, "2-finger single tap"),
    F2_TAP_DOUBLE(2, AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP, "2-finger double tap"),
    F2_TAP_TRIPLE(2, AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP, "2-finger triple tap"),
    F2_TAP_DOUBLE_HOLD(2, AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD, "2-finger double-tap and hold"),
    F2_TAP_TRIPLE_HOLD(2, AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD, "2-finger triple-tap and hold"),

    // ---- 3-finger: straight swipes + taps ----
    F3_SWIPE_UP(3, AccessibilityService.GESTURE_3_FINGER_SWIPE_UP, "3-finger swipe up"),
    F3_SWIPE_DOWN(3, AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN, "3-finger swipe down"),
    F3_SWIPE_LEFT(3, AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT, "3-finger swipe left"),
    F3_SWIPE_RIGHT(3, AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT, "3-finger swipe right"),
    F3_TAP_SINGLE(3, AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP, "3-finger single tap"),
    F3_TAP_DOUBLE(3, AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP, "3-finger double tap"),
    F3_TAP_TRIPLE(3, AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP, "3-finger triple tap"),
    F3_TAP_SINGLE_HOLD(3, AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD, "3-finger single-tap and hold"),
    F3_TAP_DOUBLE_HOLD(3, AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD, "3-finger double-tap and hold"),
    F3_TAP_TRIPLE_HOLD(3, AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD, "3-finger triple-tap and hold"),

    // ---- 4-finger: straight swipes + taps (no triple/single-tap-hold on this platform) ----
    F4_SWIPE_UP(4, AccessibilityService.GESTURE_4_FINGER_SWIPE_UP, "4-finger swipe up"),
    F4_SWIPE_DOWN(4, AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN, "4-finger swipe down"),
    F4_SWIPE_LEFT(4, AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT, "4-finger swipe left"),
    F4_SWIPE_RIGHT(4, AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT, "4-finger swipe right"),
    F4_TAP_SINGLE(4, AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP, "4-finger single tap"),
    F4_TAP_DOUBLE(4, AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP, "4-finger double tap"),
    F4_TAP_TRIPLE(4, AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP, "4-finger triple tap"),
    F4_TAP_DOUBLE_HOLD(4, AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD, "4-finger double-tap and hold");

    companion object {
        private val byAndroidId: Map<Int, GestureRegister> =
            entries.associateBy { it.androidGestureId }

        /** Look up a register from the raw id Android hands us in onGesture(). */
        fun fromAndroidGestureId(gestureId: Int): GestureRegister? = byAndroidId[gestureId]

        /** All registers for a given finger count (1-4), in declaration order. */
        fun forFingerCount(count: Int): List<GestureRegister> =
            entries.filter { it.fingerCount == count }
    }
}
