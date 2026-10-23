package com.example.device.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent

/**
 * Handles physical headset button events (ACTION_MEDIA_BUTTON) when supported by Android.
 * Designed per Section 13 of the WEYKY Master Implementation Specification:
 * - Does not break normal music controls (play, pause, next, previous).
 * - Only handles hook / assistant trigger if enabled by user.
 * - Does not start listening merely on headset connection.
 * - Fails gracefully if device or headset intercepts the button event.
 */
class HeadsetButtonReceiver : BroadcastReceiver() {

  companion object {
    private const val TAG = "WEYKY_HEADSET"

    @Volatile
    var isHeadsetTriggerEnabled: Boolean = false

    var onHeadsetButtonPressedListener: (() -> Unit)? = null
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    if (intent == null || context == null) return
    if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

    val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
    if (keyEvent.action != KeyEvent.ACTION_DOWN) return

    Log.d(TAG, "Media button received: keyCode=${keyEvent.keyCode}, enabled=$isHeadsetTriggerEnabled")

    if (!isHeadsetTriggerEnabled) {
      // Feature is disabled in settings; pass through without interference
      return
    }

    when (keyEvent.keyCode) {
      KeyEvent.KEYCODE_HEADSETHOOK,
      KeyEvent.KEYCODE_VOICE_ASSIST -> {
        Log.i(TAG, "Physical headset hook detected -> trigger voice interaction")
        onHeadsetButtonPressedListener?.invoke()
      }
      else -> {
        // Let standard playback keys (PLAY, PAUSE, NEXT, PREV) pass to media players
      }
    }
  }
}
