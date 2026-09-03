package com.ms.screenreader.settings

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.ms.screenreader.R
import com.ms.screenreader.gestures.ReadingGranularity

/**
 * "Reading Granularities" - lets the person choose which of the six
 * ReadingGranularity values (Default, Character, Word, Line, List,
 * Copy) the swipe up-then-down / down-then-up cycling gesture actually
 * visits. Unchecking ones they never use means fewer swipes to reach
 * the one they want.
 *
 * Reachable both from Detail Settings and from the new Main Menu (see
 * MainMenuActivity) - it's the settings UI half of the granularity
 * feature; MSScreenReaderService.activeGranularities() is what
 * actually reads this list back at cycling time.
 *
 * Deliberately doesn't let every box end up unchecked in storage terms
 * (it will happily save that state if that's what's checked), but
 * MSScreenReaderService.activeGranularities() falls back to just
 * DEFAULT if the stored set is ever empty, so cycling can never get
 * stuck with nothing to land on even from this screen.
 */
class GranularitySettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.granularity_settings_title)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.granularity_settings_hint)
            setPadding(0, 16, 0, 24)
        })

        val enabled = settings.enabledGranularities.toMutableSet()

        for (granularity in ReadingGranularity.entries) {
            val checkBox = SwitchCompat(this).apply {
                text = granularity.label
                isChecked = granularity.name in enabled
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
                setOnCheckedChangeListener { _, checked ->
                    if (checked) enabled.add(granularity.name) else enabled.remove(granularity.name)
                    settings.enabledGranularities = enabled
                }
            }
            root.addView(checkBox)
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
