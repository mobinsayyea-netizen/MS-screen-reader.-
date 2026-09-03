package com.ms.screenreader.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog

/** One installed, launchable app - just enough to show and pick it. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Lists installed apps and lets the person pick one from a searchable
 * dialog, without needing the QUERY_ALL_PACKAGES permission - this is
 * item #1 Step 5 / item #2's missing piece (docs/REMAINING_WORK.md),
 * shared by the Per-App Register Setting screen and the (upcoming)
 * per-app notification mute screen so both get the same picker instead
 * of each typing a package name by hand.
 *
 * How it avoids QUERY_ALL_PACKAGES: on Android 11+ (API 30+), a
 * package's normal PackageManager queries are filtered to only the
 * apps it's declared visibility to, unless it holds
 * QUERY_ALL_PACKAGES (a Play Store restricted permission needing a
 * declared-use justification) - PerAppGestureSettingsActivity's kdoc
 * explains why that permission was avoided from the start. The
 * `<queries>` block added to AndroidManifest.xml for
 * ACTION_MAIN/CATEGORY_LAUNCHER is the platform's sanctioned
 * exception for exactly this case: declaring intent-based visibility
 * makes `queryIntentActivities()` for that intent return every
 * matching app regardless of the normal filtering, no special
 * permission or Play Store declaration needed. The tradeoff: this
 * only surfaces apps with a normal launcher icon (covers what someone
 * would actually want to gesture-launch or mute notifications from) -
 * headless system components or apps without a launcher activity
 * won't appear, which is why the package-name EditText stays as a
 * manual fallback in both screens rather than being replaced outright.
 */
object InstalledAppsPicker {

    /** All installed apps with a launcher icon, alphabetically sorted by display label. */
    fun loadLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        return resolved
            .map { resolveInfo ->
                val appInfo: ApplicationInfo = resolveInfo.activityInfo.applicationInfo
                InstalledApp(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Shows a searchable picker dialog over [context]'s installed
     * launchable apps and calls [onPicked] with the chosen one. Does
     * nothing (no dialog, no callback) if there are no launchable apps
     * to show, which shouldn't happen in practice but costs nothing to
     * guard against.
     */
    fun show(context: Context, title: String, searchHint: String, onPicked: (InstalledApp) -> Unit) {
        val apps = loadLaunchableApps(context)
        if (apps.isEmpty()) return

        val labels = apps.map { "${it.label}\n${it.packageName}" }
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, labels.toMutableList())
        var filtered = apps

        val searchBox = EditText(context).apply {
            hint = searchHint
        }
        val listView = ListView(context).apply { this.adapter = adapter }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(searchBox)
            addView(listView)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            onPicked(filtered[position])
            dialog.dismiss()
        }

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim()?.lowercase().orEmpty()
                filtered = if (query.isEmpty()) {
                    apps
                } else {
                    apps.filter { it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query) }
                }
                adapter.clear()
                adapter.addAll(filtered.map { "${it.label}\n${it.packageName}" })
                adapter.notifyDataSetChanged()
            }
        })

        dialog.show()
    }
}
