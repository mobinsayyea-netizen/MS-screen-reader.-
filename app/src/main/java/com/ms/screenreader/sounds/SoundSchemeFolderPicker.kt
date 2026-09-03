package com.ms.screenreader.sounds

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ms.screenreader.settings.SettingsRepository

/**
 * Wraps ACTION_OPEN_DOCUMENT_TREE so the user can pick *any* folder on
 * their device (their own recordings, a folder synced from a computer,
 * a downloaded sound pack, etc.) as their earcon sound-scheme source.
 *
 * Usage from MainActivity:
 *
 *   private lateinit var folderPicker: SoundSchemeFolderPicker
 *
 *   override fun onCreate(...) {
 *       folderPicker = SoundSchemeFolderPicker(this) { chosen ->
 *           // chosen == true if the user picked a folder successfully
 *       }
 *       ...
 *       chooseFolderButton.setOnClickListener { folderPicker.launch() }
 *   }
 */
class SoundSchemeFolderPicker(
    activity: ComponentActivity,
    private val onResult: (chosen: Boolean) -> Unit
) {
    private val settings = SettingsRepository(activity)
    private val contentResolver = activity.contentResolver

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.data
        if (result.resultCode == ComponentActivity.RESULT_OK && uri != null) {
            // Persist read access so it survives app restarts / reboots.
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                settings.soundSchemeFolderUri = uri.toString()
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        } else {
            onResult(false)
        }
    }

    fun launch() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        launcher.launch(intent)
    }
}
