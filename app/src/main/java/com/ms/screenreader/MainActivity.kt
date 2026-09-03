package com.ms.screenreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ms.screenreader.accessibility.MSScreenReaderService
import com.ms.screenreader.settings.SettingsRepository
import com.ms.screenreader.sounds.SoundSchemeFolderPicker

class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var folderPicker: SoundSchemeFolderPicker
    private lateinit var statusText: TextView
    private lateinit var notificationAccessStatusText: TextView
    private lateinit var callPermissionsStatusText: TextView
    private lateinit var serviceEnabledSwitch: SwitchCompat
    private lateinit var serviceEnabledStatusText: TextView

    /** Requests READ_PHONE_STATE + ANSWER_PHONE_CALLS together - both are needed for caller announcement and volume-button answer. */
    private val callPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateCallPermissionsStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsRepository(this)
        statusText = findViewById(R.id.soundFolderStatusText)
        notificationAccessStatusText = findViewById(R.id.notificationAccessStatusText)
        callPermissionsStatusText = findViewById(R.id.callPermissionsStatusText)
        serviceEnabledSwitch = findViewById(R.id.serviceEnabledSwitch)
        serviceEnabledStatusText = findViewById(R.id.serviceEnabledStatusText)

        folderPicker = SoundSchemeFolderPicker(this) { chosen ->
            updateStatusText()
        }

        findViewById<Button>(R.id.chooseSoundFolderButton).setOnClickListener {
            folderPicker.launch()
        }

        findViewById<Button>(R.id.notificationAccessButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.grantCallPermissionsButton).setOnClickListener {
            callPermissionsLauncher.launch(
                arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)
            )
        }

        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.openDetailSettingsButton).setOnClickListener {
            startActivity(Intent(this, com.ms.screenreader.settings.SettingsActivity::class.java))
        }

        updateStatusText()
        updateNotificationAccessStatus()
        updateCallPermissionsStatus()
        updateServiceEnabledStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
        updateNotificationAccessStatus()
        updateCallPermissionsStatus()
        updateServiceEnabledStatus()
    }

    private fun updateStatusText() {
        statusText.text = if (settings.soundSchemeFolderUri.isNullOrBlank()) {
            getString(R.string.no_sound_folder_selected)
        } else {
            getString(R.string.sound_folder_selected)
        }
    }

    /**
     * Checks whether the user has granted this app notification-listener
     * access (a separate system permission from the accessibility
     * service). There's no direct API to ask "is my service enabled" -
     * the standard approach is checking the enabled-listener-packages set.
     */
    private fun updateNotificationAccessStatus() {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        val isEnabled = packageName in enabledPackages
        notificationAccessStatusText.text = if (isEnabled) {
            getString(R.string.notification_access_enabled)
        } else {
            getString(R.string.notification_access_disabled)
        }
    }

    /**
     * Reflects the real system state of the accessibility service as an
     * on/off switch named after the app itself (requested: "screen
     * reader ka naam on/off switch"). Reads
     * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES directly rather than
     * relying on MSScreenReaderService.getRunningInstance(), since that
     * static reference is only non-null while this app's own process is
     * alive with the service attached - the Settings string is the
     * source of truth regardless of process state.
     */
    private fun updateServiceEnabledStatus() {
        val enabled = isAccessibilityServiceEnabled()
        serviceEnabledSwitch.setOnCheckedChangeListener(null)
        serviceEnabledSwitch.isChecked = enabled
        serviceEnabledSwitch.setOnCheckedChangeListener { switchView, checked ->
            if (!switchView.isPressed) return@setOnCheckedChangeListener
            if (!checked) {
                val service = MSScreenReaderService.getRunningInstance()
                if (service != null) {
                    service.disableServiceCompletely()
                } else {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            updateServiceEnabledStatus()
        }
        serviceEnabledStatusText.text = if (enabled) {
            getString(R.string.service_status_on)
        } else {
            getString(R.string.service_status_off)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${MSScreenReaderService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }

    private fun updateCallPermissionsStatus() {
        val hasPhoneState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val hasAnswerCalls = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED

        callPermissionsStatusText.text = if (hasPhoneState && hasAnswerCalls) {
            getString(R.string.call_permissions_granted)
        } else {
            getString(R.string.call_permissions_not_granted)
        }
    }
}
