package com.ms.screenreader.gestures

/**
 * The "reading granularity" that a plain swipe up/down performs once
 * selected. Selected by cycling with the two single-finger reversal
 * gestures that GestureRegister already listed as detected-but-unused:
 * swipe up-then-down moves to the next granularity, swipe down-then-up
 * moves to the previous one (wrapping at both ends).
 *
 * While DEFAULT is active, swipe up/down keep their original meaning
 * (scroll the focused container backward/forward). Once any other
 * granularity is selected, swipe down performs that granularity's
 * "forward" step and swipe up performs its "backward" step, both
 * operating on the text of whichever node currently has accessibility
 * focus - see NodeNavigator's stepCharacter/stepWord/stepLine/
 * moveNextListItem/movePreviousListItem, and
 * MSScreenReaderService.handleGranularityStep for COPY.
 *
 * There is no on-screen indicator for which granularity is active, so
 * every change is announced by name via TTS (see
 * MSScreenReaderService.announceGranularityChange) - the user has to
 * hear it, not see it.
 *
 * Deliberately not persisted across service restarts: it resets to
 * DEFAULT whenever the accessibility service reconnects, so the user
 * always starts from a known, predictable state rather than an old
 * mode they may not remember leaving on.
 */
enum class ReadingGranularity(val label: String) {
    DEFAULT("Default"),
    CHARACTER("Character"),
    WORD("Word"),
    LINE("Line"),
    LIST("List"),
    COPY("Copy");

    companion object {
        /** Cyclic next value, wrapping from the last entry back to the first. */
        fun ReadingGranularity.next(): ReadingGranularity {
            val values = entries
            return values[(values.indexOf(this) + 1) % values.size]
        }

        /** Cyclic previous value, wrapping from the first entry back to the last. */
        fun ReadingGranularity.previous(): ReadingGranularity {
            val values = entries
            return values[(values.indexOf(this) - 1 + values.size) % values.size]
        }
    }
}
