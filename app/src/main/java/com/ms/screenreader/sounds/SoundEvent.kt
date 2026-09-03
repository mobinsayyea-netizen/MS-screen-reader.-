package com.ms.screenreader.sounds

/**
 * Every UI event that can have a custom earcon (short feedback sound)
 * assigned from the user's own sound-scheme folder.
 *
 * `fileKey` is the base filename (without extension) the user should
 * name their sound file as, e.g. "focus_change.ogg" or "focus_change.wav".
 * Extension is not fixed - SoundSchemeManager will look for any of the
 * supported extensions.
 */
enum class SoundEvent(val fileKey: String, val displayName: String) {
    FOCUS_CHANGE("focus_change", "Focus change"),
    CLICK("click", "Click"),
    LONG_PRESS("long_press", "Long press"),
    SCROLL_UP("scroll_up", "Scroll up / reached top"),
    SCROLL_DOWN("scroll_down", "Scroll down / reached bottom"),
    WINDOW_CHANGE("window_change", "Screen / window change"),
    SELECTION("selection", "Selection changed"),
    TEXT_CHANGED("text_changed", "Text changed"),
    NOTIFICATION("notification", "New notification"),
    SPEECH_SUSPENDED("speech_suspended", "Voice feedback suspended"),
    SPEECH_RESUMED("speech_resumed", "Voice feedback resumed"),
    GRANULARITY_CHANGE("granularity_change", "Reading granularity changed"),
    COPIED("copied", "Text copied"),
    APPENDED("appended", "Text appended to clipboard")
}
