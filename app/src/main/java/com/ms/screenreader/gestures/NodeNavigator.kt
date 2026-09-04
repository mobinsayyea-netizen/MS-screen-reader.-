package com.ms.screenreader.gestures

import android.accessibilityservice.AccessibilityService
import android.graphics.Typeface
import android.os.Build
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.accessibility.AccessibilityNodeInfo
import com.ms.screenreader.settings.SettingsRepository

/**
 * Builds a flat, ordered list of "interesting" nodes (things worth
 * stopping on while swiping through a screen) from the current window,
 * and moves accessibility focus one step forward/backward through them.
 *
 * A node is considered interesting if it has visible text/description,
 * or is independently clickable/focusable - mirroring the basic rule
 * TalkBack-style readers use to decide what counts as a stop.
 *
 * Two different "what does this node sound like" functions live here,
 * kept deliberately separate:
 *  - [rawText] - just the plain text/contentDescription, nothing else.
 *    Used anywhere the exact text matters and has to stay stable:
 *    character/word/line stepping, clipboard copy, and remember-focus
 *    matching.
 *  - [announceableDescription] - what actually gets spoken when
 *    navigating with a swipe: [rawText] (or a resource-ID fallback for
 *    unlabelled elements) plus, depending on Verbosity settings
 *    (see SettingsRepository), the element's type, a short usage hint,
 *    and/or a container-entering/exiting/count announcement.
 */
class NodeNavigator(private val service: AccessibilityService, private val settings: SettingsRepository) {

    private var flatNodes: MutableList<AccessibilityNodeInfo> = mutableListOf()
    private var currentIndex: Int = -1

    // ---- Sub-node cursors for granularity stepping (character/word/line) ----
    // Each indexes into the *current* node's text only. Reset whenever
    // currentIndex changes (new node focused) or the active
    // ReadingGranularity changes, so switching elements or modes always
    // restarts at the beginning of that node's text instead of reusing
    // an index that made sense for different text.
    private var charIndex = 0
    private var wordIndex = 0
    private var lineIndex = 0

    // ---- Remember focus per app ----
    // Best-effort: keyed by package name, storing the raw text/
    // contentDescription of whichever node last had focus in that app.
    // Matched back by rawText on return rather than by index, since
    // refresh() rebuilds flatNodes from scratch each time and indices
    // from a previous visit won't line up with a possibly different
    // node tree the next time that app is opened. Deliberately matched
    // against rawText (not the decorated announceableDescription) so a
    // Verbosity setting toggled in between doesn't break the match.
    private val lastFocusedDescriptionByPackage = mutableMapOf<String, String>()

    // ---- Verbosity: container tracking ----
    // Which container (list/grid, or none) the last-announced node sat
    // inside, so containerTransitionAnnouncement only speaks up on an
    // actual entering/exiting transition, not on every single step
    // taken while already inside the same container.
    private var lastContainerLabel: String? = null

    /** Rebuilds the node list from the current active window. Call this on window change. */
    fun refresh() {
        recycleAll()
        val root = service.rootInActiveWindow ?: return
        collect(root, flatNodes)
        currentIndex = -1
        lastContainerLabel = null
    }

    /** Moves to the next interesting node and sets accessibility focus on it. Returns its description, or null if at the end. */
    fun moveNext(): String? = move(1)

    /** Moves to the previous interesting node and sets accessibility focus on it. Returns its description, or null if at the start. */
    fun movePrevious(): String? = move(-1)

    /** Jumps to the first interesting node on screen (used by the "to top" gesture). Returns its description, or null if the screen has no nodes. */
    fun moveToFirst(): String? {
        if (flatNodes.isEmpty()) refresh()
        if (flatNodes.isEmpty()) return null
        currentIndex = 0
        resetSubNodeCursors()
        val node = flatNodes[currentIndex]
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        return announceableDescription(node)
    }

    /** Jumps to the last interesting node on screen (used by the "to end" gesture). Returns its description, or null if the screen has no nodes. */
    fun moveToLast(): String? {
        if (flatNodes.isEmpty()) refresh()
        if (flatNodes.isEmpty()) return null
        currentIndex = flatNodes.size - 1
        resetSubNodeCursors()
        val node = flatNodes[currentIndex]
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        return announceableDescription(node)
    }

    /** Performs a click on the currently focused node, if any. Returns true if a click was dispatched. */
    fun activateCurrent(): Boolean {
        val node = flatNodes.getOrNull(currentIndex) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Screen bounds of whichever node is currently focused, or null if
     * there isn't one. Used as a fallback tap target when a node's own
     * ACTION_CLICK doesn't actually do anything - some views (e.g. the
     * "Apps"/all-apps button in Microsoft Launcher) don't expose a
     * working click action to accessibility services even though
     * they're clearly tappable on-screen.
     */
    fun currentNodeBoundsInScreen(): android.graphics.Rect? {
        val node = flatNodes.getOrNull(currentIndex) ?: return null
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        return rect
    }

    /** The plain text of whichever node currently has accessibility focus (no type/hint/container decoration), or null if there isn't one / it has none. Used for character/word/line stepping and clipboard copy, which need stable raw text rather than the richer spoken announcement. */
    fun currentText(): String? {
        val node = flatNodes.getOrNull(currentIndex) ?: return null
        return rawText(node)
    }

    /**
     * Clears the character/word/line cursors. Call this whenever the
     * active [ReadingGranularity] changes (not just when the focused
     * node changes - that's already handled inside [move]/[moveToFirst]/
     * [moveToLast]), so a fresh mode always starts from the beginning of
     * the currently focused node's text.
     */
    fun resetSubNodeCursors() {
        charIndex = 0
        wordIndex = 0
        lineIndex = 0
    }

    /**
     * Steps one character forward or backward through the focused node's
     * text and returns what should be spoken for the character landed
     * on (a plain letter, "capital X" for an uppercase letter if that
     * Verbosity setting is on, or a spelled-out punctuation word per
     * [SettingsRepository.punctuationLevel]), or null if the node has no
     * text. Clamps at the start/end of the text rather than wrapping or
     * crossing into the next/previous node - repeat swipes at a
     * boundary keep returning the same edge character.
     */
    fun stepCharacter(forward: Boolean): String? {
        val text = currentText()?.takeIf { it.isNotEmpty() } ?: return null
        charIndex = (charIndex + if (forward) 1 else -1).coerceIn(0, text.length - 1)
        return spokenForCharacter(text[charIndex])
    }

    /** Steps one word forward or backward through the focused node's text (whitespace-delimited). Clamps at the ends. */
    fun stepWord(forward: Boolean): String? {
        val text = currentText()?.takeIf { it.isNotBlank() } ?: return null
        val words = text.trim().split(Regex("\\s+"))
        if (words.isEmpty()) return null
        wordIndex = (wordIndex + if (forward) 1 else -1).coerceIn(0, words.size - 1)
        return words[wordIndex]
    }

    /** Steps one line forward or backward through the focused node's text (newline-delimited). Clamps at the ends. */
    fun stepLine(forward: Boolean): String? {
        val text = currentText()?.takeIf { it.isNotBlank() } ?: return null
        val lines = text.split("\n")
        if (lines.isEmpty()) return null
        lineIndex = (lineIndex + if (forward) 1 else -1).coerceIn(0, lines.size - 1)
        return lines[lineIndex]
    }

    /** Moves to the next node that sits inside a list-like container (ListView/RecyclerView/GridView). Returns its description, or null if there isn't one further along. */
    fun moveNextListItem(): String? = moveToNextMatching(1)

    /** Moves to the previous node that sits inside a list-like container. Returns its description, or null if there isn't one further back. */
    fun movePreviousListItem(): String? = moveToNextMatching(-1)

    private fun moveToNextMatching(step: Int): String? {
        if (flatNodes.isEmpty()) refresh()
        if (flatNodes.isEmpty()) return null

        var idx = currentIndex
        while (true) {
            idx += step
            if (idx !in flatNodes.indices) return null
            val node = flatNodes[idx]
            if (isListItem(node)) {
                currentIndex = idx
                resetSubNodeCursors()
                node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                return announceableDescription(node)
            }
        }
    }

    /** True if the node's immediate parent looks like a list/grid container. */
    private fun isListItem(node: AccessibilityNodeInfo): Boolean {
        val parent = node.parent ?: return false
        val parentClass = parent.className?.toString() ?: return false
        return parentClass.contains("ListView") ||
            parentClass.contains("RecyclerView") ||
            parentClass.contains("GridView")
    }

    /**
     * Records [packageName]'s currently-focused-node description so a
     * later [restoreRememberedFocus] call for that package can try to
     * find it again. Call this any time focus changes for a known
     * reason (swipe navigation, natural touch-explore/focus events) -
     * a null/blank description is ignored rather than overwriting a
     * previously-remembered one with nothing useful.
     */
    fun rememberFocus(packageName: String?, description: String?) {
        if (packageName.isNullOrBlank() || description.isNullOrBlank()) return
        lastFocusedDescriptionByPackage[packageName] = description
    }

    /**
     * After [refresh] has rebuilt flatNodes for [packageName] (i.e.
     * that app just came back to the foreground), tries to find a node
     * whose raw text matches what was last remembered for it and, if
     * found, moves accessibility focus there. Returns the announceable
     * description on success so the caller can decide whether to
     * announce it, or null if nothing was remembered for this package
     * or none of its current nodes match anymore (the screen may have
     * changed since - falls back silently, normal navigation just
     * starts fresh from there).
     */
    fun restoreRememberedFocus(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        val remembered = lastFocusedDescriptionByPackage[packageName] ?: return null
        val index = flatNodes.indexOfFirst { rawText(it) == remembered }
        if (index == -1) return null
        currentIndex = index
        resetSubNodeCursors()
        val node = flatNodes[index]
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        return announceableDescription(node)
    }

    private fun move(step: Int): String? {
        if (flatNodes.isEmpty()) refresh()
        if (flatNodes.isEmpty()) return null

        var nextIndex = currentIndex + step
        if (nextIndex !in flatNodes.indices) {
            if (!settings.wrapNavigationEnabled) return null
            // Wrap: past the end goes to the first element, past the
            // start goes to the last - matches Jieshuo's "Wrap
            // navigation" behavior instead of just stopping dead at
            // the edge, which felt like navigation had "gotten stuck".
            nextIndex = ((nextIndex % flatNodes.size) + flatNodes.size) % flatNodes.size
        }

        currentIndex = nextIndex
        resetSubNodeCursors()
        val node = flatNodes[currentIndex]
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        return announceableDescription(node)
    }

    /** Plain text/contentDescription only - nothing decorated. See the class doc for why this is kept separate from [announceableDescription]. */
    private fun rawText(node: AccessibilityNodeInfo): String? {
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) return it }
        node.text?.toString()?.let { if (it.isNotBlank()) return it }
        return null
    }

    /**
     * Builds what actually gets spoken when navigating to [node]:
     * a container-entering/exiting/count announcement (if the
     * container changed since the last node), then the node's
     * [rawText] (or a resource-ID fallback for unlabelled elements, if
     * that's enabled), then its element type, then a short usage hint -
     * each of the last three gated by its own Verbosity toggle in
     * [SettingsRepository]. Returns null only if there's truly nothing
     * to say (no text, no fallback, no type, no hint, no container
     * change).
     */
    private fun announceableDescription(node: AccessibilityNodeInfo): String? {
        val parts = mutableListOf<String>()

        containerTransitionAnnouncement(node)?.let { parts.add(it) }

        val text = rawText(node) ?: elementIdFallback(node)
        text?.let { parts.add(collapseRepeatedSymbols(it)) }

        if (settings.speakTextFormattingEnabled) {
            textFormattingLabel(node)?.let { parts.add(it) }
        }

        if (settings.speakElementTypeEnabled) {
            elementTypeLabel(node)?.let { parts.add(it) }
        }

        if (settings.speakUsageHintsEnabled) {
            usageHint(node)?.let { parts.add(it) }
        }

        return parts.joinToString(", ").takeIf { it.isNotBlank() }
    }

    /** For an element with no text/contentDescription at all, falls back to a cleaned-up version of its resource ID (e.g. "submit_button" -> "submit button") - only if speakElementIdsEnabled is on, since a raw resource name is a weaker signal than real text. */
    private fun elementIdFallback(node: AccessibilityNodeInfo): String? {
        if (!settings.speakElementIdsEnabled) return null
        val id = node.viewIdResourceName ?: return null
        return id.substringAfterLast('/').replace('_', ' ').trim().takeIf { it.isNotBlank() }
    }

    /** A short spoken label for common widget types, or null for plain text/unrecognized views (announcing "text" after every label would be noisy). */
    private fun elementTypeLabel(node: AccessibilityNodeInfo): String? {
        val className = node.className?.toString() ?: return null
        return when {
            className.contains("CheckBox") -> "checkbox"
            className.contains("Switch") -> "switch"
            className.contains("RadioButton") -> "radio button"
            className.contains("ToggleButton") -> "toggle button"
            className.contains("SeekBar") -> "slider"
            className.contains("Spinner") -> "dropdown"
            className.contains("ImageButton") -> "button"
            className.contains("Button") -> "button"
            className.contains("EditText") -> "edit box"
            else -> null
        }
    }

    /** A short hint about how to interact with [node], preferring the most specific applicable action. Null for plain non-interactive elements. */
    private fun usageHint(node: AccessibilityNodeInfo): String? = when {
        node.isCheckable -> "double tap to toggle"
        node.isClickable -> "double tap to activate"
        node.isLongClickable -> "double tap and hold for more options"
        else -> null
    }

    /** Bold/italic/underline mentioned when the node's own text (not contentDescription - apps essentially never span that) actually carries that markup, e.g. a bolded word in a message. Null for plain text or when the setting is off. */
    private fun textFormattingLabel(node: AccessibilityNodeInfo): String? {
        val text = node.text
        if (text !is Spanned) return null

        val styles = text.getSpans(0, text.length, StyleSpan::class.java)
        val hasBold = styles.any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
        val hasItalic = styles.any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC }
        val hasUnderline = text.getSpans(0, text.length, UnderlineSpan::class.java).isNotEmpty()

        val labels = mutableListOf<String>()
        if (hasBold) labels.add("bold")
        if (hasItalic) labels.add("italic")
        if (hasUnderline) labels.add("underline")
        return labels.joinToString(", ").takeIf { it.isNotBlank() }
    }

    /**
     * Replaces any run of 4 or more identical, non-alphanumeric,
     * non-whitespace characters (e.g. "----", "****", "......") with a
     * spoken count instead of reading each one individually - matches
     * TalkBack's "Count repeated symbols" behavior. Runs shorter than 4,
     * and runs of letters/digits/whitespace, are left untouched.
     */
    private fun collapseRepeatedSymbols(text: String): String {
        if (!settings.countRepeatedSymbolsEnabled) return text
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            val char = text[i]
            var j = i
            while (j < text.length && text[j] == char) j++
            val runLength = j - i
            if (runLength >= 4 && !char.isLetterOrDigit() && !char.isWhitespace()) {
                result.append(runLength).append(' ').append(symbolPluralName(char))
            } else {
                result.append(text, i, j)
            }
            i = j
        }
        return result.toString()
    }

    /** Pluralized spoken name for a repeated symbol, e.g. '-' -> "dashes". Falls back to the literal character repeated if it isn't in either punctuation map. */
    private fun symbolPluralName(char: Char): String {
        val name = commonPunctuationWords[char] ?: extendedPunctuationWords[char] ?: return char.toString()
        return if (name.endsWith("h") || name.endsWith("s") || name.endsWith("x")) "${name}es" else "${name}s"
    }

    /**
     * Compares the container (list/grid, if any) [node] sits in against
     * whichever container was last announced, and returns an
     * entering/exiting phrase - with an item count on entry, if that's
     * enabled - the moment it actually changes. Returns null on
     * repeated moves within the same container (or outside any
     * container), so this only speaks up on a real transition rather
     * than on every single step.
     */
    private fun containerTransitionAnnouncement(node: AccessibilityNodeInfo): String? {
        if (!settings.speakContainerInfoEnabled) return null
        val newLabel = containerLabelFor(node)
        if (newLabel == lastContainerLabel) return null

        val announcement = when {
            newLabel != null -> {
                val count = if (settings.speakListItemCountEnabled) ", ${siblingCountFor(node)} items" else ""
                "In $newLabel$count"
            }
            lastContainerLabel != null -> "Out of $lastContainerLabel"
            else -> null
        }
        lastContainerLabel = newLabel
        return announcement
    }

    /**
     * "list"/"grid" if any ancestor up the chain looks like one, else
     * null. Deliberately walks the whole ancestor chain rather than
     * just the immediate parent - a real list/grid row is usually
     * several layout levels deep (row container > inner layout > title
     * TextView, summary TextView, etc.), and each of those pieces
     * becomes its own stop in flatNodes since they each carry their own
     * text. Checking only the immediate parent made the container label
     * flip between "list" and null as navigation moved between pieces
     * of the very same row, which caused "In list" / "Out of list" to
     * be re-announced repeatedly mid-row instead of once per real
     * list/grid entry or exit. Capped at a handful of levels so a
     * pathological layout can't turn every lookup into a long walk.
     */
    private fun containerLabelFor(node: AccessibilityNodeInfo): String? {
        var current = node.parent
        var depth = 0
        while (current != null && depth < 8) {
            val parentClass = current.className?.toString()
            when {
                parentClass?.contains("GridView") == true -> return "grid"
                parentClass?.contains("ListView") == true || parentClass?.contains("RecyclerView") == true -> return "list"
            }
            current = current.parent
            depth++
        }
        return null
    }

    /** Rough item count for the container announcement: walks up to the same list/grid ancestor [containerLabelFor] found, and counts ITS children - not just the immediate parent's, which for a node several levels inside one row would only return that row's own internal piece count (dividers etc. may still be slightly over-counted - close enough for a spoken count). */
    private fun siblingCountFor(node: AccessibilityNodeInfo): Int {
        var current = node.parent
        var depth = 0
        while (current != null && depth < 8) {
            val parentClass = current.className?.toString()
            if (parentClass?.contains("GridView") == true ||
                parentClass?.contains("ListView") == true ||
                parentClass?.contains("RecyclerView") == true
            ) {
                return current.childCount
            }
            current = current.parent
            depth++
        }
        return node.parent?.childCount ?: 0
    }

    /** What to speak for a single stepped-through character: a spelled-out punctuation word (per [SettingsRepository.punctuationLevel]) if it's punctuation, "capital X" for an uppercase letter if that setting is on, plus a short example word for letters if that setting is on (e.g. "capital h, hotel"), else the character itself. */
    private fun spokenForCharacter(char: Char): String {
        punctuationWordFor(char)?.let { return it }

        val capitalPrefix = if (settings.speakCapitalLettersEnabled && char.isUpperCase()) "capital " else ""

        if (char.isLetter() && settings.speakLettersWithExamplesEnabled) {
            val example = letterExamples[char.lowercaseChar()]
            if (example != null) return "$capitalPrefix$char, $example"
        }

        return "$capitalPrefix$char"
    }

    private val letterExamples = mapOf(
        'a' to "apple", 'b' to "bravo", 'c' to "charlie", 'd' to "delta",
        'e' to "echo", 'f' to "foxtrot", 'g' to "golf", 'h' to "hotel",
        'i' to "india", 'j' to "juliet", 'k' to "kilo", 'l' to "lima",
        'm' to "mike", 'n' to "november", 'o' to "oscar", 'p' to "papa",
        'q' to "quebec", 'r' to "romeo", 's' to "sierra", 't' to "tango",
        'u' to "uniform", 'v' to "victor", 'w' to "whiskey", 'x' to "x-ray",
        'y' to "yankee", 'z' to "zulu"
    )

    private val commonPunctuationWords = mapOf(
        '.' to "period", ',' to "comma", '!' to "exclamation mark",
        '?' to "question mark", ':' to "colon", ';' to "semicolon"
    )

    private val extendedPunctuationWords = mapOf(
        '-' to "dash", '_' to "underscore", '@' to "at", '#' to "hash",
        '$' to "dollar", '%' to "percent", '&' to "and", '*' to "asterisk",
        '/' to "slash", '\\' to "backslash", '(' to "open paren", ')' to "close paren",
        '\'' to "apostrophe", '"' to "quote"
    )

    private fun punctuationWordFor(char: Char): String? {
        when (settings.punctuationLevel) {
            "none" -> return null
            "all" -> extendedPunctuationWords[char]?.let { return it }
        }
        return commonPunctuationWords[char]
    }

    /** Depth-first walk collecting nodes worth stopping on, skipping invisible/unimportant ones. */
    private fun collect(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (!node.isVisibleToUser) return

        val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        val isActionable = node.isClickable || node.isLongClickable || node.isFocusable
        val keep = hasText || isActionable
        if (keep) {
            out.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out)
        }

        // Every getChild()/rootInActiveWindow call hands back a new
        // AccessibilityNodeInfo drawn from a per-window pool (capped at
        // ~500 on API 26-32). Nodes we don't keep in flatNodes still
        // need to go back to that pool or repeated refresh() calls
        // (basically every window change) leak them and can eventually
        // exhaust the pool. From API 33 onward recycle() is a
        // documented no-op (pooling was removed), so this only runs
        // where it still matters.
        if (!keep && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    /** Returns every node currently held in flatNodes back to the pool (API 26-32 only - see [collect]'s kdoc) before clearing the list, so a fresh refresh() doesn't leak the previous window's nodes. */
    private fun recycleAll() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            flatNodes.forEach { it.recycle() }
        }
        flatNodes.clear()
    }
}
