package com.scrbl.app.platform.lifecycle

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager

/**
 * Manages the `recording.keepScreenAwake` preference and subtle haptic/sound feedback:
 * - Per `10_ANDROID_PLATFORM_PRIVACY.md` section 4:
 *   "If enabled, keep the display awake only during active voice recording, and clear the flag
 *   immediately when recording stops, saves, cancels or fails. Do not keep the screen awake
 *   in normal browsing or playback."
 */
object PlatformFeedbackAndWindowHelper {

    fun updateKeepScreenAwake(activity: Activity?, shouldKeepAwake: Boolean) {
        val window = activity?.window ?: return
        if (shouldKeepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun performSubtleHaptic(view: View?, hapticsEnabled: Boolean) {
        if (!hapticsEnabled || view == null) return
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun performSubtleClickSound(context: Context?, soundEffectsEnabled: Boolean) {
        if (!soundEffectsEnabled || context == null) return
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.playSoundEffect(AudioManager.FX_KEY_CLICK)
        }
    }
}
