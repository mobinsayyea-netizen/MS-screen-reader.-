package com.ms.screenreader.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.ms.screenreader.R
import com.ms.screenreader.gestures.GestureAction

/**
 * "Detail Settings" screen (opened from MainActivity's Detail Settings
 * button). Groups every on/off toggle and gesture-scheme entry point
 * in one place, instead of scattering them across the main screen.
 *
 * What lives here:
 *  - Call Handling: caller announcement, volume-button-answer
 *  - Accessibility Shortcut: whether the on-screen button / shortcut
 *    does anything (see SettingsRepository.accessibilityShortcutEnabled
 *    kdoc for what it can and can't control)
 *  - Sound: earcon on/off (folder picker itself stays on MainActivity,
 *    since it's a one-time setup action, not a toggle)
 *  - Notifications: master on/off, and an entry point to the per-app
 *    mute list (NotificationMuteSettingsActivity, item #2 - see
 *    docs/REMAINING_WORK.md, done as of this version)
 *  - Gestures: entry points to Default Register Setting and Per-App
 *    Register Setting, plus a reset-everything button
 *  - Reading Granularities: entry point to the checklist of which
 *    granularities (Default/Character/Word/Line/List/Copy) the
 *    up-then-down / down-then-up cycling gesture visits
 *  - Focus Memory: whether returning to an app restores accessibility
 *    focus to wherever it was left, and whether that restoration is
 *    announced aloud (see SettingsRepository.rememberFocusEnabled /
 *    readRememberedFocusOnReturn)
 *  - Power Button: explanatory note only (no toggle - see
 *    CallHandlingManager's kdoc for why power-button-end-call can't
 *    be implemented on this platform)
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = SettingsRepository(this)

        bindToggle(R.id.callerAnnouncerCheckBox, settings.callerAnnouncerEnabled) {
            settings.callerAnnouncerEnabled = it
        }
        bindToggle(R.id.volumeAnswerCheckBox, settings.volumeAnswerEnabled) {
            settings.volumeAnswerEnabled = it
        }
        bindToggle(R.id.accessibilityShortcutCheckBox, settings.accessibilityShortcutEnabled) {
            settings.accessibilityShortcutEnabled = it
        }
        bindToggle(R.id.soundSchemeCheckBox, settings.soundSchemeEnabled) {
            settings.soundSchemeEnabled = it
        }
        bindToggle(R.id.vibrationCheckBox, settings.vibrationEnabled) {
            settings.vibrationEnabled = it
        }
        bindToggle(R.id.notificationReaderCheckBox, settings.notificationReaderEnabled) {
            settings.notificationReaderEnabled = it
        }

        findViewById<Button>(R.id.openNotificationMuteSettingsButton).setOnClickListener {
            startActivity(Intent(this, NotificationMuteSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.openDefaultRegisterSettingsButton).setOnClickListener {
            startActivity(Intent(this, DefaultGestureSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.openPerAppRegisterSettingsButton).setOnClickListener {
            startActivity(Intent(this, PerAppGestureSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.resetGestureCustomizationsButton).setOnClickListener {
            settings.resetAllGestureCustomizations()
            Toast.makeText(this, R.string.reset_gesture_customizations_done, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.openGranularitySettingsButton).setOnClickListener {
            startActivity(Intent(this, GranularitySettingsActivity::class.java))
        }
        findViewById<Button>(R.id.openVerbositySettingsButton).setOnClickListener {
            startActivity(Intent(this, VerbositySettingsActivity::class.java))
        }
        bindToggle(R.id.rememberFocusCheckBox, settings.rememberFocusEnabled) {
            settings.rememberFocusEnabled = it
        }
        bindToggle(R.id.readRememberedFocusCheckBox, settings.readRememberedFocusOnReturn) {
            settings.readRememberedFocusOnReturn = it
        }
        bindToggle(R.id.wrapNavigationCheckBox, settings.wrapNavigationEnabled) {
            settings.wrapNavigationEnabled = it
        }
        bindToggle(R.id.announceScreenStateCheckBox, settings.announceScreenStateEnabled) {
            settings.announceScreenStateEnabled = it
        }
        bindToggle(R.id.volumeShortcutsEnabledCheckBox, settings.volumeShortcutsEnabled) {
            settings.volumeShortcutsEnabled = it
        }
        bindActionSpinner(R.id.volumeSimultaneousActionSpinner, settings.volumeSimultaneousAction) {
            settings.volumeSimultaneousAction = it
        }
        bindActionSpinner(R.id.volumeUpLongPressActionSpinner, settings.volumeUpLongPressAction) {
            settings.volumeUpLongPressAction = it
        }
        bindActionSpinner(R.id.volumeDownLongPressActionSpinner, settings.volumeDownLongPressAction) {
            settings.volumeDownLongPressAction = it
        }
    }

    private fun bindToggle(id: Int, initial: Boolean, onChanged: (Boolean) -> Unit) {
        val switch = findViewById<SwitchCompat>(id)
        switch.isChecked = initial
        switch.setOnCheckedChangeListener { _, checked -> onChanged(checked) }
    }

    /** Fills a Spinner with every GestureAction, preselects [initial], and reports each later choice via [onChanged] - used for the three volume-key shortcut pickers. */
    private fun bindActionSpinner(id: Int, initial: GestureAction, onChanged: (GestureAction) -> Unit) {
        val spinner = findViewById<Spinner>(id)
        val options = GestureAction.entries.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(options.indexOf(initial.name).coerceAtLeast(0), false)

        // Set the listener after setSelection above so restoring the
        // saved choice doesn't immediately re-trigger a write.
        spinner.post {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onChanged(GestureAction.valueOf(options[position]))
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }
}
