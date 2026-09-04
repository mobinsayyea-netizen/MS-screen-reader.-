package com.ms.screenreader.menu

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ms.screenreader.R
import com.ms.screenreader.accessibility.MSScreenReaderService
import com.ms.screenreader.settings.DefaultGestureSettingsActivity
import com.ms.screenreader.settings.GranularitySettingsActivity
import com.ms.screenreader.settings.PerAppGestureSettingsActivity
import com.ms.screenreader.settings.SettingsActivity
import com.ms.screenreader.settings.VerbositySettingsActivity

/**
 * The screen reader's "main menu" - opened by swiping up-then-right
 * from anywhere (see GestureManager: GESTURE_SWIPE_UP_AND_RIGHT ->
 * GestureAction.OPEN_MAIN_MENU), the requested screen-reader-menu
 * shortcut entry point.
 *
 * Shown as a small dialog-themed Activity (see
 * Theme.MSScreenReader.Dialog) rather than a full-screen one, so it
 * reads as a quick in-and-out menu sitting on top of whatever app the
 * person was using, similar in spirit to TalkBack's global context
 * menu - though this is a plain Activity with buttons rather than a
 * true system overlay, which is enough for a keyboard/switch/touch-
 * accessible menu without the added complexity (and extra permission)
 * of a TYPE_ACCESSIBILITY_OVERLAY window.
 *
 * Every item here already exists elsewhere in the app (Detail
 * Settings, the two gesture-register screens, Reading Granularities) -
 * this just gives them a second, faster way in via a single gesture
 * instead of MainActivity -> Detail Settings -> the right button.
 * "Suspend/Resume voice feedback" and "Disable screen reader
 * completely" are the two actions that aren't settings screens; both
 * reach the running accessibility service via
 * MSScreenReaderService.getRunningInstance() since this Activity has
 * no bound-service connection of its own. Disable-completely (item #5,
 * docs/REMAINING_WORK.md) is the one menu item that's irreversible
 * from inside the app - see confirmDisableService()'s kdoc.
 */
class MainMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.main_menu_title)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            // The window's own title (see AndroidManifest's android:label for
            // this Activity) already announces "MS Screen Reader Menu" once
            // when this screen opens. Without this, touch-explore/swipe
            // navigation would also stop on this heading and announce the
            // same text a second time right after - sounding like a
            // stutter/repeat rather than a single clean announcement.
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })

        addMenuButton(root, R.string.main_menu_toggle_speech) {
            val service = MSScreenReaderService.getRunningInstance()
            if (service != null) {
                service.requestToggleSpeech()
            } else {
                Toast.makeText(this, R.string.main_menu_service_not_running, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
        addMenuButton(root, R.string.main_menu_reading_granularities) {
            startActivity(Intent(this, GranularitySettingsActivity::class.java))
            finish()
        }
        addMenuButton(root, R.string.main_menu_verbosity) {
            startActivity(Intent(this, VerbositySettingsActivity::class.java))
            finish()
        }
        addMenuButton(root, R.string.open_default_register_settings) {
            startActivity(Intent(this, DefaultGestureSettingsActivity::class.java))
            finish()
        }
        addMenuButton(root, R.string.open_per_app_register_settings) {
            startActivity(Intent(this, PerAppGestureSettingsActivity::class.java))
            finish()
        }
        addMenuButton(root, R.string.open_detail_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
        addMenuButton(root, R.string.main_menu_disable_service) {
            confirmDisableService()
        }
        addMenuButton(root, R.string.main_menu_cancel) {
            finish()
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    /**
     * Confirms before calling MSScreenReaderService.disableServiceCompletely()
     * (item #5, docs/REMAINING_WORK.md) - unlike every other action in
     * this menu, this one is one-way from inside the app: once the
     * service disables itself there's no in-app button left to turn it
     * back on, only system Settings or the OS's own volume-key /
     * Accessibility Button shortcut (if configured to target this
     * service). The dialog says so explicitly rather than just warning
     * "are you sure".
     */
    private fun confirmDisableService() {
        val service = MSScreenReaderService.getRunningInstance()
        if (service == null) {
            Toast.makeText(this, R.string.main_menu_service_not_running, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.main_menu_disable_service)
            .setMessage(R.string.main_menu_disable_service_confirm)
            .setPositiveButton(R.string.main_menu_disable_service_confirm_yes) { _, _ ->
                service.disableServiceCompletely()
                finish()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun addMenuButton(root: LinearLayout, textRes: Int, onClick: () -> Unit) {
        val button = Button(this).apply {
            setText(textRes)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            setOnClickListener { onClick() }
        }
        root.addView(button)
    }
}
