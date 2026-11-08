package com.scrbl.app.platform.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Helper for runtime `RECORD_AUDIO` permission checks (`10_ANDROID_PLATFORM_PRIVACY.md` section 2):
 * - Requested ONLY when the user selects the microphone button on the blank Capture page.
 * - If denied, explains why microphone access is needed and allows the user to return to text capture
 *   (never silently opens text mode as a substitute).
 * - If permanently denied, provides a direct link to the app's Android system settings screen.
 */
object AudioPermissionHelper {

    const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            RECORD_AUDIO_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppSystemSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
