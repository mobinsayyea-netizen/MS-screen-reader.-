package com.ms.screenreader.settings

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.ms.screenreader.R

/**
 * "Verbosity" - lets the person turn each piece of extra spoken detail
 * on or off independently, TalkBack-style: element type, container
 * entering/exiting, usage hints, list item counts, window names,
 * element-ID fallback for unlabelled buttons, capital-letter
 * announcement, and how much punctuation gets spelled out.
 *
 * Reachable from the Main Menu (see MainMenuActivity) next to Reading
 * Granularities - it's the settings UI half of the verbosity feature;
 * NodeNavigator.announceableDescription()/stepCharacter() are what
 * actually read these back at speaking time. Every toggle here writes
 * straight through to SettingsRepository, same pattern as
 * GranularitySettingsActivity - no separate "Save" step.
 */
class VerbositySettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.verbosity_settings_title)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.verbosity_settings_hint)
            setPadding(0, 16, 0, 24)
        })

        addToggle(root, R.string.verbosity_speak_element_type, R.string.verbosity_speak_element_type_desc,
            get = { settings.speakElementTypeEnabled }, set = { settings.speakElementTypeEnabled = it })
        addToggle(root, R.string.verbosity_speak_container_info, R.string.verbosity_speak_container_info_desc,
            get = { settings.speakContainerInfoEnabled }, set = { settings.speakContainerInfoEnabled = it })
        addToggle(root, R.string.verbosity_speak_usage_hints, R.string.verbosity_speak_usage_hints_desc,
            get = { settings.speakUsageHintsEnabled }, set = { settings.speakUsageHintsEnabled = it })
        addToggle(root, R.string.verbosity_speak_list_item_count, R.string.verbosity_speak_list_item_count_desc,
            get = { settings.speakListItemCountEnabled }, set = { settings.speakListItemCountEnabled = it })
        addToggle(root, R.string.verbosity_speak_window_names, R.string.verbosity_speak_window_names_desc,
            get = { settings.speakWindowNamesEnabled }, set = { settings.speakWindowNamesEnabled = it })
        addToggle(root, R.string.verbosity_speak_element_ids, R.string.verbosity_speak_element_ids_desc,
            get = { settings.speakElementIdsEnabled }, set = { settings.speakElementIdsEnabled = it })
        addToggle(root, R.string.verbosity_speak_capital_letters, R.string.verbosity_speak_capital_letters_desc,
            get = { settings.speakCapitalLettersEnabled }, set = { settings.speakCapitalLettersEnabled = it })
        addToggle(root, R.string.verbosity_speak_letters_with_examples, R.string.verbosity_speak_letters_with_examples_desc,
            get = { settings.speakLettersWithExamplesEnabled }, set = { settings.speakLettersWithExamplesEnabled = it })
        addToggle(root, R.string.verbosity_speak_text_formatting, R.string.verbosity_speak_text_formatting_desc,
            get = { settings.speakTextFormattingEnabled }, set = { settings.speakTextFormattingEnabled = it })
        addToggle(root, R.string.verbosity_count_repeated_symbols, R.string.verbosity_count_repeated_symbols_desc,
            get = { settings.countRepeatedSymbolsEnabled }, set = { settings.countRepeatedSymbolsEnabled = it })
        addToggle(root, R.string.verbosity_speak_notifications_screen_off, R.string.verbosity_speak_notifications_screen_off_desc,
            get = { settings.speakNotificationsWhenScreenOffEnabled }, set = { settings.speakNotificationsWhenScreenOffEnabled = it })

        addPunctuationLevelPicker(root)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    /** One on/off switch + a smaller description line under it, wired straight to a boolean setting. */
    private fun addToggle(root: LinearLayout, titleRes: Int, descRes: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
        val checkBox = SwitchCompat(this).apply {
            setText(titleRes)
            isChecked = get()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
            setOnCheckedChangeListener { _, checked -> set(checked) }
        }
        root.addView(checkBox)

        root.addView(TextView(this).apply {
            setText(descRes)
            textSize = 13f
            setPadding(48, 0, 0, 0)
        })
    }

    /** Three-way radio choice (none/some/all) for how much punctuation gets spelled out while stepping character-by-character. */
    private fun addPunctuationLevelPicker(root: LinearLayout) {
        root.addView(TextView(this).apply {
            text = getString(R.string.verbosity_punctuation_level)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        })

        val noneButton = RadioButton(this).apply { id = View_ID_NONE; text = getString(R.string.verbosity_punctuation_none) }
        val someButton = RadioButton(this).apply { id = View_ID_SOME; text = getString(R.string.verbosity_punctuation_some) }
        val allButton = RadioButton(this).apply { id = View_ID_ALL; text = getString(R.string.verbosity_punctuation_all) }

        val group = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(noneButton)
            addView(someButton)
            addView(allButton)
        }

        when (settings.punctuationLevel) {
            "none" -> noneButton.isChecked = true
            "all" -> allButton.isChecked = true
            else -> someButton.isChecked = true
        }

        group.setOnCheckedChangeListener { _, checkedId ->
            settings.punctuationLevel = when (checkedId) {
                View_ID_NONE -> "none"
                View_ID_ALL -> "all"
                else -> "some"
            }
        }

        root.addView(group)
    }

    private companion object {
        // Arbitrary fixed IDs for the three radio buttons - fine since
        // this RadioGroup is built entirely in code and never inflated
        // from XML, so there's no risk of colliding with generated
        // resource IDs elsewhere.
        const val View_ID_NONE = 1001
        const val View_ID_SOME = 1002
        const val View_ID_ALL = 1003
    }
}
