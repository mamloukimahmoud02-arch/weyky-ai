package com.example.device.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log

enum class VolumeAdjustment {
  RAISE,
  LOWER,
  MUTE,
  UNMUTE,
  SET_PERCENT,
  STEPS
}

data class VolumeCommandResult(
  val success: Boolean,
  val previousStep: Int,
  val currentStep: Int,
  val maxSteps: Int,
  val percent: Int,
  val message: String
)

class AudioController(private val context: Context) {
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

  /**
   * Returns whether audio is currently active (music / media).
   */
  fun isMusicActive(): Boolean {
    return audioManager?.isMusicActive == true
  }

  /**
   * Determines the active audio stream. If media/music is active, prefer STREAM_MUSIC.
   */
  fun getTargetStream(forRingtone: Boolean = false): Int {
    return if (forRingtone) AudioManager.STREAM_RING else AudioManager.STREAM_MUSIC
  }

  /**
   * Adjusts volume incrementally by 1 step (up or down).
   * Supports multiple steps (e.g. 2 times, 3 times).
   * Never jumps to 0 or max directly.
   */
  fun adjustVolumeStep(
    increase: Boolean,
    stepCount: Int = 1,
    isArabic: Boolean = false,
    forRingtone: Boolean = false
  ): VolumeCommandResult {
    val am = audioManager ?: return VolumeCommandResult(
      false, 0, 0, 0, 0,
      if (isArabic) "التحكم بالصوت غير متاح" else "Audio controls unavailable"
    )

    val stream = getTargetStream(forRingtone)
    val max = am.getStreamMaxVolume(stream)
    val prev = am.getStreamVolume(stream)

    if (max <= 0) {
      return VolumeCommandResult(
        false, prev, prev, max, 0,
        if (isArabic) "لا يمكن تعديل الصوت" else "Volume cannot be adjusted"
      )
    }

    if (increase && prev >= max) {
      val percent = ((prev.toFloat() / max) * 100).toInt()
      Log.d("WEYKY_VOLUME", "$prev/$max -> already at maximum")
      val msg = if (isArabic) "الصوت عند الحد الأقصى بالفعل ($percent%)." else "Volume is already at maximum ($percent%)."
      return VolumeCommandResult(true, prev, prev, max, percent, msg)
    }

    if (!increase && prev <= 0) {
      Log.d("WEYKY_VOLUME", "$prev/$max -> already at minimum")
      val msg = if (isArabic) "الصوت عند الحد الأدنى بالفعل (0%)." else "Volume is already at minimum."
      return VolumeCommandResult(true, prev, prev, max, 0, msg)
    }

    val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
    val safeSteps = stepCount.coerceIn(1, max)

    repeat(safeSteps) {
      am.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
    }

    val updated = am.getStreamVolume(stream)
    val percent = ((updated.toFloat() / max) * 100).toInt()

    Log.d("WEYKY_VOLUME", "$prev/$max -> $updated/$max ($percent%)")

    val message = if (isArabic) {
      "أصبح مستوى الصوت $percent% ($updated من $max)."
    } else {
      "Volume set to $percent% ($updated/$max)."
    }

    return VolumeCommandResult(true, prev, updated, max, percent, message)
  }

  /**
   * Sets volume to an exact percentage (0% to 100%).
   * Maps 0..100% proportionally to actual hardware steps and clamps safely.
   */
  fun setVolumePercent(
    targetPercent: Int,
    isArabic: Boolean = false,
    forRingtone: Boolean = false
  ): VolumeCommandResult {
    val am = audioManager ?: return VolumeCommandResult(
      false, 0, 0, 0, 0,
      if (isArabic) "التحكم بالصوت غير متاح" else "Audio controls unavailable"
    )

    val stream = getTargetStream(forRingtone)
    val max = am.getStreamMaxVolume(stream)
    val prev = am.getStreamVolume(stream)

    if (max <= 0) {
      return VolumeCommandResult(
        false, prev, prev, max, 0,
        if (isArabic) "لا يمكن تعديل الصوت" else "Volume cannot be adjusted"
      )
    }

    val clampedPercent = targetPercent.coerceIn(0, 100)
    val targetStep = Math.round((clampedPercent / 100.0f) * max).coerceIn(0, max)

    am.setStreamVolume(stream, targetStep, AudioManager.FLAG_SHOW_UI)

    val actual = am.getStreamVolume(stream)
    val actualPercent = ((actual.toFloat() / max) * 100).toInt()

    Log.d("WEYKY_VOLUME", "setPercent $targetPercent% -> step $actual/$max ($actualPercent%)")

    val message = if (isArabic) {
      "تم ضبط مستوى الصوت على $actualPercent% ($actual من $max)."
    } else {
      "Volume adjusted to $actualPercent% ($actual/$max)."
    }

    return VolumeCommandResult(true, prev, actual, max, actualPercent, message)
  }

  /**
   * Mute or unmute media audio.
   */
  fun muteVolume(
    mute: Boolean,
    isArabic: Boolean = false,
    forRingtone: Boolean = false
  ): VolumeCommandResult {
    val am = audioManager ?: return VolumeCommandResult(
      false, 0, 0, 0, 0,
      if (isArabic) "التحكم بالصوت غير متاح" else "Audio controls unavailable"
    )

    val stream = getTargetStream(forRingtone)
    val max = am.getStreamMaxVolume(stream)
    val prev = am.getStreamVolume(stream)

    val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
    am.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)

    val updated = am.getStreamVolume(stream)
    val percent = if (max > 0) ((updated.toFloat() / max) * 100).toInt() else 0

    val message = if (isArabic) {
      if (mute) "تم كتم الصوت." else "تم إلغاء كتم الصوت ($percent%)."
    } else {
      if (mute) "Audio muted." else "Audio unmuted ($percent%)."
    }

    return VolumeCommandResult(true, prev, updated, max, percent, message)
  }

  fun getCurrentVolumePercent(forRingtone: Boolean = false): Int {
    val am = audioManager ?: return 50
    val stream = getTargetStream(forRingtone)
    val current = am.getStreamVolume(stream)
    val max = am.getStreamMaxVolume(stream)
    return if (max > 0) ((current.toFloat() / max) * 100).toInt() else 0
  }
}
