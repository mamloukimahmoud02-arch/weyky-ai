package com.example.device.memory

import android.app.ActivityManager
import android.content.Context
import java.util.Locale

data class MemorySnapshot(
  val totalRamBytes: Long,
  val availableRamBytes: Long,
  val usedRamBytes: Long,
  val usagePercent: Int,
  val isLowMemory: Boolean,
  val totalRamFormatted: String,
  val usedRamFormatted: String,
  val availableRamFormatted: String,
  val isAvailable: Boolean = true
)

class MemoryProvider(private val context: Context) {
  private val activityManager =
    context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

  fun queryMemory(): MemorySnapshot? {
    val am = activityManager ?: return null
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)

    if (memInfo.totalMem <= 0) return null

    val total = memInfo.totalMem
    val avail = memInfo.availMem
    val used = (total - avail).coerceAtLeast(0)
    val percent = ((used.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)

    return MemorySnapshot(
      totalRamBytes = total,
      availableRamBytes = avail,
      usedRamBytes = used,
      usagePercent = percent,
      isLowMemory = memInfo.lowMemory,
      totalRamFormatted = formatBytes(total),
      usedRamFormatted = formatBytes(used),
      availableRamFormatted = formatBytes(avail),
      isAvailable = true
    )
  }

  private fun formatBytes(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
      String.format(Locale.US, "%.1f GB", gb)
    } else {
      val mb = bytes.toDouble() / (1024.0 * 1024.0)
      String.format(Locale.US, "%.0f MB", mb)
    }
  }
}
