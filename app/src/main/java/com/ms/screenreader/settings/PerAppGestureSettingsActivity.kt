package com.ms.screenreader.settings

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.ms.screenreader.R
import com.ms.screenreader.gestures.GestureAction
import com.ms.screenreader.gestures.GestureRegister

/**
 * "Per-App Register Setting" - type a package name (e.g. com.whatsapp),
 * load it, then for each of the 43 gesture registers either:
 *  - pick an action that only applies while that app is in the
 *    foreground (SettingsRepository.setAppGestureOverride), or
 *  - check "Open this app instead" to make that gesture open this app
 *    from anywhere on the phone (SettingsRepository.setGestureAppLaunch
 *    - this one is global, not scoped to the loaded app, since a
 *    gesture can only launch one app at a time)
 *
 * A package-name text field remains as a manual fallback (e.g. for an
 * app without a launcher icon), but the primary way to pick an app is
 * now the "Choose installed app" button, backed by InstalledAppsPicker
 * (item #1 Step 5, docs/REMAINING_WORK.md) - a searchable dialog over
 * every launchable installed app, without needing QUERY_ALL_PACKAGES.
 * See InstalledAppsPicker's kdoc for how that's done.
 *
 * Same caveat as DefaultGestureSettingsActivity: this screen stores
 * the choice safely, but nothing reads it yet during actual gesture
 * dispatch - that wiring is a later step.
 */
class PerAppGestureSettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var packageInput: EditText
    private lateinit var registerListContainer: LinearLayout
    private val defaultLabel = "(default)"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = getString(R.string.per_app_register_settings_title)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        packageInput = EditText(this).apply {
            hint = getString(R.string.per_app_package_hint)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        root.addView(packageInput)

        val chooseAppButton = Button(this).apply {
            text = getString(R.string.choose_installed_app_button)
        }
        root.addView(chooseAppButton)

        val loadButton = Button(this).apply {
            text = getString(R.string.per_app_load_button)
        }
        root.addView(loadButton)

        val hint = TextView(this).apply {
            text = getString(R.string.per_app_register_settings_hint)
            setPadding(0, 16, 0, 16)
        }
        root.addView(hint)

        registerListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(registerListContainer)

        chooseAppButton.setOnClickListener {
            InstalledAppsPicker.show(
                this,
                getString(R.string.choose_installed_app_title),
                getString(R.string.choose_installed_app_search_hint)
            ) { app ->
                packageInput.setText(app.packageName)
                buildRegisterListFor(app.packageName)
            }
        }

        loadButton.setOnClickListener {
            val packageName = packageInput.text.toString().trim()
            if (packageName.isEmpty()) {
                Toast.makeText(this, R.string.per_app_package_hint, Toast.LENGTH_SHORT).show()
            } else {
                buildRegisterListFor(packageName)
            }
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun buildRegisterListFor(packageName: String) {
        registerListContainer.removeAllViews()
        val spinnerOptions = listOf(defaultLabel) + GestureAction.entries.map { it.name }
        val existingOverrides = settings.getAppGestureOverrides(packageName)
        val existingLaunches = settings.getAllGestureAppLaunches()

        for (fingerCount in 1..4) {
            val registers = GestureRegister.forFingerCount(fingerCount)
            if (registers.isEmpty()) continue

            val header = TextView(this).apply {
                text = "$fingerCount-finger"
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 24, 0, 8)
            }
            registerListContainer.addView(header)

            for (register in registers) {
                registerListContainer.addView(
                    buildRegisterRow(packageName, register, spinnerOptions, existingOverrides, existingLaunches)
                )
            }
        }
    }

    private fun buildRegisterRow(
        packageName: String,
        register: GestureRegister,
        spinnerOptions: List<String>,
        existingOverrides: Map<GestureRegister, GestureAction>,
        existingLaunches: Map<GestureRegister, String>
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }

        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(this).apply {
            text = register.label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        labelRow.addView(label)

        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val currentOverride = existingOverrides[register]
        val initialIndex = if (currentOverride == null) 0 else spinnerOptions.indexOf(currentOverride.name)
        spinner.setSelection(if (initialIndex >= 0) initialIndex else 0, false)
        labelRow.addView(spinner)
        row.addView(labelRow)

        val launchCheckBox = SwitchCompat(this).apply {
            text = getString(R.string.open_this_app_instead)
            isChecked = existingLaunches[register] == packageName
        }
        row.addView(launchCheckBox)

        // Listeners attached after initial state is set, same reasoning
        // as DefaultGestureSettingsActivity - avoids a spurious write
        // the moment the row is built.
        spinner.post {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val chosen = spinnerOptions[position]
                    if (chosen == defaultLabel) {
                        settings.clearAppGestureOverride(packageName, register)
                    } else {
                        settings.setAppGestureOverride(packageName, register, GestureAction.valueOf(chosen))
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        launchCheckBox.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                settings.setGestureAppLaunch(register, packageName)
            } else if (settings.getGestureAppLaunch(register) == packageName) {
                settings.clearGestureAppLaunch(register)
            }
        }

        return row
    }
}
