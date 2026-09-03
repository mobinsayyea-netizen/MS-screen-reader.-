package com.ms.screenreader.settings

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.ms.screenreader.R

/**
 * Per-app notification mute UI - item #2 (docs/REMAINING_WORK.md), the
 * missing piece for the backend that's existed since v1.4
 * (SettingsRepository.mutedNotificationPackages, NotificationReader's
 * filtering). Shows every installed launchable app (via
 * InstalledAppsPicker.loadLaunchableApps - same source and same
 * QUERY_ALL_PACKAGES-free approach as the per-app gesture screen's
 * "Choose installed app" button) with a checkbox each; checking it
 * mutes that app's notifications, unchecking restores them. No
 * separate "load"/"choose" step needed here since the whole point is
 * seeing every app at once to toggle any of them - the search box just
 * filters which rows are visible, it never changes what's stored.
 *
 * Same coverage caveat as InstalledAppsPicker: only apps with a
 * launcher icon are listed. An already-muted package that later loses
 * its launcher icon (rare) would become unreachable from this specific
 * screen, but stays muted in storage either way since nothing here
 * clears mutes it doesn't show.
 */
class NotificationMuteSettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var listContainer: LinearLayout
    private var allApps: List<InstalledApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.notification_mute_title)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.notification_mute_hint)
            setPadding(0, 8, 0, 16)
        })

        val searchBox = EditText(this).apply {
            hint = getString(R.string.choose_installed_app_search_hint)
        }
        root.addView(searchBox)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }
        root.addView(listContainer)

        allApps = InstalledAppsPicker.loadLaunchableApps(this)
        renderList(allApps)

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim()?.lowercase().orEmpty()
                val filtered = if (query.isEmpty()) {
                    allApps
                } else {
                    allApps.filter {
                        it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                    }
                }
                renderList(filtered)
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun renderList(apps: List<InstalledApp>) {
        listContainer.removeAllViews()
        val muted = settings.mutedNotificationPackages
        for (app in apps) {
            val row = SwitchCompat(this).apply {
                text = "${app.label}\n${app.packageName}"
                isChecked = app.packageName in muted
            }
            row.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    settings.muteNotificationPackage(app.packageName)
                } else {
                    settings.unmuteNotificationPackage(app.packageName)
                }
            }
            listContainer.addView(row)
        }
    }
}
