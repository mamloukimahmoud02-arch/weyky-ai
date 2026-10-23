package com.example.core.voice.model

import java.io.File

/**
 * Metadata and catalog entry for an on-device offline voice model (STT or TTS).
 */
data class VoiceModelInfo(
  val id: String,
  val nameArabic: String,
  val nameEnglish: String,
  val type: VoiceModelType,
  val downloadUrl: String,
  val sizeBytes: Long,
  val expectedSha256: String? = null,
  val license: String,
  val descriptionArabic: String,
  val targetDirectoryName: String,
  val languageCode: String = "ar",
  val companionConfigUrl: String? = null,
  val version: String = "1.0.0",
  val isFemaleVoice: Boolean = false
)

enum class VoiceModelType {
  STT,
  TTS
}

sealed interface ModelInstallStatus {
  object NotInstalled : ModelInstallStatus
  data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : ModelInstallStatus
  data class Downloaded(val zipFile: File) : ModelInstallStatus
  data class Extracting(val progressPercent: Int) : ModelInstallStatus
  data class Installed(val modelDirectory: File) : ModelInstallStatus
  data class Error(val message: String) : ModelInstallStatus
}
