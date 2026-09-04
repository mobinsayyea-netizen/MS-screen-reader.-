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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ms.screenreader.accessibility.MSScreenReaderService

/**
 * One screen listing every permission/access MS Screen Reader needs,
 * each with its live status and its own grant/open button - instead
 * of the person having to find "Call permissions" on the main screen,
 * "Notification access" somewhere else, and "Accessibility" somewhere
 * else again.
 *
 * Two different mechanisms are involved, and Android doesn't let an
 * app collapse them into one shared dialog:
 *  - READ_PHONE_STATE / ANSWER_PHONE_CALLS / READ_CONTACTS are normal
 *    runtime permissions - these three CAN be requested together in
 *    one call, showing one system dialog after another automatically.
 *  - Notification-listener access and the Accessibility Service
 *    toggle are both "special access" settings that only the person
 *    can grant from their own dedicated system Settings screen - no
 *    API lets an app grant or even request these directly. What this
 *    screen adds for those two is auto-advancing: tapping "Grant all
 *    now" walks through the runtime permissions first, then opens the
 *    next still-missing settings screen automatically each time the
 *    person comes back to this screen, so they don't have to hunt for
 *    each one themselves - just toggle it on and press back, and the
 *    next one opens by itself.
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var phoneContactsStatusText: TextView
    private lateinit var notificationStatusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var allDoneText: TextView

    /** True while stepping through "Grant all now" - makes onResume auto-open the next still-missing settings screen instead of just sitting there. */
    private var autoAdvancing = false

    private val runtimePermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_CONTACTS
    )

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshStatuses()
        MSScreenReaderService.getRunningInstance()?.retryCallHandlingRegistration()
        if (autoAdvancing) advanceToNextStep()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        phoneContactsStatusText = findViewById(R.id.phoneContactsStatusText)
        notificationStatusText = findViewById(R.id.notificationStatusText)
        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)
        allDoneText = findViewById(R.id.allDoneText)

        findViewById<Button>(R.id.grantAllButton).setOnClickListener {
            autoAdvancing = true
            advanceToNextStep()
        }
        findViewById<Button>(R.id.phoneContactsButton).setOnClickListener {
            permissionsLauncher.launch(runtimePermissions)
        }
        findViewById<Button>(R.id.notificationButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        refreshStatuses()
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
        if (autoAdvancing) advanceToNextStep()
    }

    /**
     * Moves the "Grant all now" flow forward: fires the next
     * still-missing step (runtime permissions dialog, or one of the
     * two settings screens) each time it's called, stopping once
     * nothing is left. Called right after launch and again every time
     * the person returns to this screen while a flow is in progress.
     */
    private fun advanceToNextStep() {
        when {
            !hasPhoneContactsPermissions() -> permissionsLauncher.launch(runtimePermissions)
            !hasNotificationAccess() -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            !hasAccessibilityServiceEnabled() -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            else -> autoAdvancing = false // everything granted - stop auto-advancing
        }
    }

    private fun refreshStatuses() {
        phoneContactsStatusText.text = getString(
            if (hasPhoneContactsPermissions()) R.string.permission_status_granted else R.string.permission_status_not_granted
        )
        notificationStatusText.text = getString(
            if (hasNotificationAccess()) R.string.permission_status_on else R.string.permission_status_off
        )
        accessibilityStatusText.text = getString(
            if (hasAccessibilityServiceEnabled()) R.string.permission_status_on else R.string.permission_status_off
        )
        val allGranted = hasPhoneContactsPermissions() && hasNotificationAccess() && hasAccessibilityServiceEnabled()
        allDoneText.visibility = if (allGranted) android.view.View.VISIBLE else android.view.View.GONE
        if (allGranted) allDoneText.text = getString(R.string.permissions_all_done)
    }

    private fun hasPhoneContactsPermissions(): Boolean =
        runtimePermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasNotificationAccess(): Boolean =
        packageName in NotificationManagerCompat.getEnabledListenerPackages(this)

    private fun hasAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${MSScreenReaderService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }
}
