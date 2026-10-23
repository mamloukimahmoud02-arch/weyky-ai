package com.example.device.storage

import android.os.Environment
import android.os.StatFs
import java.io.File
import java.util.Locale

data class StorageSnapshot(
  val totalBytes: Long,
  val freeBytes: Long,
  val usedBytes: Long,
  val usagePercent: Int,
  val totalFormatted: String,
  val usedFormatted: String,
  val freeFormatted: String,
  val isAvailable: Boolean = true
)

class StorageProvider {
  fun queryStorage(): StorageSnapshot? {
    return try {
      val path: File = Environment.getDataDirectory()
      val stat = StatFs(path.path)
      val blockSize = stat.blockSizeLong
      val totalBlocks = stat.blockCountLong
      val availableBlocks = stat.availableBlocksLong

      val total = totalBlocks * blockSize
      if (total <= 0) return null

      val free = availableBlocks * blockSize
      val used = (total - free).coerceAtLeast(0)
      val percent = ((used.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)

      StorageSnapshot(
        totalBytes = total,
        freeBytes = free,
        usedBytes = used,
        usagePercent = percent,
        totalFormatted = formatBytes(total),
        usedFormatted = formatBytes(used),
        freeFormatted = formatBytes(free),
        isAvailable = true
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun formatBytes(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gb)
  }
}
