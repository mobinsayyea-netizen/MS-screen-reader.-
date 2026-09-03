package com.ms.screenreader.settings

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ms.screenreader.R
import com.ms.screenreader.gestures.GestureAction
import com.ms.screenreader.gestures.GestureRegister

/**
 * "Default Register Setting" - lets the person redefine what any of
 * the 43 gesture registers (see GestureRegister) does *everywhere* in
 * the app. This is the global mapping; PerAppGestureSettingsActivity
 * is for changes scoped to one app.
 *
 * Built with plain LinearLayout rows instead of a RecyclerView/ListView
 * adapter - 43 rows is small enough that this stays simple and easy to
 * follow, at the cost of being a bit more verbose than an adapter would
 * be.
 *
 * Note: choosing an action here only takes effect once gesture
 * dispatch actually consults SettingsRepository.getGlobalGestureOverride()
 * (planned as a later step - see docs/REMAINING_WORK.md). Right now
 * this screen safely stores the choice; GestureManager's hardcoded
 * mapping is still what runs for the 12 classic gestures until that
 * wiring lands.
 */
class DefaultGestureSettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

    /** First entry in every spinner - means "use the built-in default, no override". */
    private val defaultLabel = "(default)"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = getString(R.string.default_register_settings_title)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val hint = TextView(this).apply {
            text = getString(R.string.default_register_settings_hint)
            setPadding(0, 16, 0, 24)
        }
        root.addView(hint)

        val spinnerOptions = listOf(defaultLabel) + GestureAction.entries.map { it.name }

        for (fingerCount in 1..4) {
            val registers = GestureRegister.forFingerCount(fingerCount)
            if (registers.isEmpty()) continue

            val header = TextView(this).apply {
                text = "$fingerCount-finger"
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 32, 0, 8)
            }
            root.addView(header)

            for (register in registers) {
                root.addView(buildRegisterRow(register, spinnerOptions))
            }
        }

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun buildRegisterRow(register: GestureRegister, spinnerOptions: List<String>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }

        val label = TextView(this).apply {
            text = register.label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val currentOverride = settings.getGlobalGestureOverride(register)
        val initialIndex = if (currentOverride == null) 0 else spinnerOptions.indexOf(currentOverride.name)
        spinner.setSelection(if (initialIndex >= 0) initialIndex else 0, false)

        // Set the listener after setSelection above so restoring the
        // saved choice doesn't immediately re-trigger a write.
        spinner.post {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val chosen = spinnerOptions[position]
                    if (chosen == defaultLabel) {
                        settings.clearGlobalGestureOverride(register)
                    } else {
                        settings.setGlobalGestureOverride(register, GestureAction.valueOf(chosen))
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        row.addView(spinner)
        return row
    }
}
