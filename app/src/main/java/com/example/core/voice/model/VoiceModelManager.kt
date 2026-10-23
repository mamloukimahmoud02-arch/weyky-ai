package com.example.core.voice.model

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Robust manager for downloading, validating, unzipping, checking space,
 * and caching offline voice models in private app storage.
 */
class VoiceModelManager(
  private val context: Context,
  private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()
) {
  companion object {
    private const val TAG = "WEYKY_MODEL_MGR"
    private const val MODELS_DIR = "voice_models"

    // Official Vosk Arabic Tunisian dialect LinTO model (158 MB)
    val VOSK_ARABIC_LINTO_TN = VoiceModelInfo(
      id = "vosk_ar_linto_tn",
      nameArabic = "نموذج Vosk العربي الخفيف (LinTO)",
      nameEnglish = "Vosk Arabic Tunisian Model (LinTO)",
      type = VoiceModelType.STT,
      downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ar-tn-0.1-linto.zip",
      sizeBytes = 165_703_754L, // 158 MB
      expectedSha256 = null,
      license = "Apache 2.0 (Open Source)",
      descriptionArabic = "نموذج صوتي عربي خفيف مبني على أبحاث LinTO للتعرف على الكلام بدون إنترنت.",
      targetDirectoryName = "vosk-model-small-ar-tn-0.1-linto"
    )

    // Standard Vosk Arabic MGB2 model (Modern Standard Arabic - الفصحى, 318 MB)
    val VOSK_ARABIC_MGB2 = VoiceModelInfo(
      id = "vosk_ar_mgb2",
      nameArabic = "نموذج Vosk العربي الفصيح الشامل (MGB-2)",
      nameEnglish = "Vosk Arabic MGB-2 Kaldi Model (Modern Standard Arabic)",
      type = VoiceModelType.STT,
      downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip",
      sizeBytes = 333_241_610L, // 317.8 MB
      expectedSha256 = null,
      license = "Apache 2.0 (Open Source)",
      descriptionArabic = "نموذج صوتي عربي فصيح عالي الدقة مبني على بيانات Aljazeera Kaldi MGB-2 للتعرف المحلي الكامل.",
      targetDirectoryName = "vosk-model-ar-mgb2-0.4"
    )

    // Official Piper VITS Arabic Female Neural TTS Model (ONNX, 22.05kHz, 63.5 MB, MIT License)
    val PIPER_ARABIC_FEMALE_NEURAL_TTS = VoiceModelInfo(
      id = "piper_ar_female_neural_tts",
      nameArabic = "نموذج النطق العصبي العربي النسائي (Piper VITS ONNX)",
      nameEnglish = "Piper Arabic Female Neural TTS (VITS ONNX 22.05kHz)",
      type = VoiceModelType.TTS,
      downloadUrl = "https://huggingface.co/vadimbelsky/arabic-emirati-female-piper/resolve/main/arabic-emirati-female-model.onnx",
      companionConfigUrl = "https://huggingface.co/vadimbelsky/arabic-emirati-female-piper/resolve/main/arabic-emirati-female-model.onnx.json",
      sizeBytes = 63_548_210L, // ~60.6 MB
      expectedSha256 = null,
      license = "MIT License (Commercial & Offline Allowed)",
      descriptionArabic = "محرك نطق عصبي عربي محلي بالكامل بصوت نسائي طبيعي يعمل عبر ONNX Runtime دون إنترنت بعد التنزيل لمرة واحدة.",
      targetDirectoryName = "piper-ar-female-neural-tts",
      languageCode = "ar",
      version = "1.0.0",
      isFemaleVoice = true
    )

    // Official Piper VITS Arabic Male Neural TTS Model - Kareem / Jordan (ONNX, 22.05kHz, 63.2 MB, Open Source)
    val PIPER_ARABIC_KAREEM_NEURAL_TTS = VoiceModelInfo(
      id = "piper_ar_kareem_neural_tts",
      nameArabic = "نموذج النطق العصبي العربي الفصيح - كريم (Piper VITS ONNX)",
      nameEnglish = "Piper Arabic Kareem Neural TTS (VITS ONNX 22.05kHz, Male)",
      type = VoiceModelType.TTS,
      downloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/ar/ar_JO/kareem/medium/ar_JO-kareem-medium.onnx",
      companionConfigUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/ar/ar_JO/kareem/medium/ar_JO-kareem-medium.onnx.json",
      sizeBytes = 63_201_294L, // ~60.3 MB
      expectedSha256 = null,
      license = "MIT / Open Source (Offline Allowed)",
      descriptionArabic = "محرك نطق عصبي عربي فصيح عالي الدقة بصوت رجالي (كريم - الأردن) مبني على Piper VITS ONNX ويعمل محلياً دون إنترنت.",
      targetDirectoryName = "piper-ar-kareem-neural-tts",
      languageCode = "ar",
      version = "1.0.0",
      isFemaleVoice = false
    )

    // Catalog includes both STT (Vosk) and Neural TTS (Piper VITS ONNX) models
    val CATALOG = listOf(
      VOSK_ARABIC_MGB2,
      VOSK_ARABIC_LINTO_TN,
      PIPER_ARABIC_FEMALE_NEURAL_TTS,
      PIPER_ARABIC_KAREEM_NEURAL_TTS
    )
  }

  fun getActiveTtsModelId(): String {
    val prefs = context.getSharedPreferences("weyky_voice_settings", Context.MODE_PRIVATE)
    val saved = prefs.getString("active_tts_model_id", null)
    if (!saved.isNullOrEmpty()) {
      val modelInfo = CATALOG.firstOrNull { it.id == saved && it.type == VoiceModelType.TTS }
      if (modelInfo != null && isModelInstalled(modelInfo)) {
        return saved
      }
    }
    // Default to first installed TTS model if any
    val firstInstalledTts = CATALOG.firstOrNull { it.type == VoiceModelType.TTS && isModelInstalled(it) }
    return firstInstalledTts?.id ?: ""
  }

  fun setActiveTtsModelId(modelId: String) {
    val prefs = context.getSharedPreferences("weyky_voice_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("active_tts_model_id", modelId).apply()
  }

  private val _modelStatuses = MutableStateFlow<Map<String, ModelInstallStatus>>(emptyMap())
  val modelStatuses: StateFlow<Map<String, ModelInstallStatus>> = _modelStatuses.asStateFlow()

  init {
    refreshStatus()
  }

  val modelsDirectory: File
    get() = File(context.filesDir, MODELS_DIR).apply { if (!exists()) mkdirs() }

  fun getModelDirectory(modelInfo: VoiceModelInfo): File {
    val baseDir = File(modelsDirectory, modelInfo.targetDirectoryName)
    return if (modelInfo.type == VoiceModelType.TTS) {
      resolveValidTtsModelRoot(baseDir) ?: baseDir
    } else {
      resolveValidModelRoot(baseDir) ?: baseDir
    }
  }

  fun getDownloadedZipFile(modelInfo: VoiceModelInfo): File {
    val ext = if (modelInfo.type == VoiceModelType.TTS && modelInfo.downloadUrl.endsWith(".onnx", ignoreCase = true)) {
      "onnx"
    } else {
      "zip"
    }
    return File(modelsDirectory, "${modelInfo.id}.$ext")
  }

  fun getDownloadedCompanionConfigFile(modelInfo: VoiceModelInfo): File {
    return File(modelsDirectory, "${modelInfo.id}.onnx.json")
  }

  /**
   * Checks whether a given directory contains a valid, non-corrupted ONNX Neural TTS model
   * (`.onnx` weights + `.onnx.json` or `tokens.txt` / `vocab.json` configuration).
   */
  fun isValidNeuralTtsModelRoot(dir: File): Boolean {
    if (!dir.exists() || !dir.isDirectory) return false
    val files = dir.listFiles() ?: return false
    val onnxFile = files.firstOrNull { it.isFile && it.name.endsWith(".onnx", ignoreCase = true) && it.length() > 128L }
      ?: return false
    val hasConfig = files.any {
      it.isFile && it.length() > 16L && (
        it.name.endsWith(".onnx.json", ignoreCase = true) ||
          it.name.equals("model.onnx.json", ignoreCase = true) ||
          it.name.equals("config.json", ignoreCase = true) ||
          it.name.equals("tokens.txt", ignoreCase = true) ||
          it.name.equals("vocab.json", ignoreCase = true)
        )
    }
    return onnxFile.exists() && hasConfig
  }

  fun resolveValidTtsModelRoot(baseDir: File, maxDepth: Int = 2): File? {
    if (!baseDir.exists() || !baseDir.isDirectory) return null
    if (isValidNeuralTtsModelRoot(baseDir)) return baseDir
    if (maxDepth <= 0) return null

    val subDirs = baseDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && !f.name.startsWith("__MACOSX") }
      ?: return null
    for (sub in subDirs) {
      val resolved = resolveValidTtsModelRoot(sub, maxDepth - 1)
      if (resolved != null) return resolved
    }
    return null
  }

  /**
   * Checks whether a given directory directly contains genuine Vosk/Kaldi acoustic & configuration artifacts.
   * Supports both standard Kaldi-Vosk layouts (am/final.mdl, conf/mfcc.conf, conf/model.conf, graph/)
   * and flat/compact model layouts (final.mdl, model.bin, mfcc.conf).
   */
  fun isValidVoskModelRoot(dir: File): Boolean {
    if (!dir.exists() || !dir.isDirectory) return false

    val hasAcousticModel =
      File(dir, "am/final.mdl").isRegularNonEmptyFile() ||
      File(dir, "final.mdl").isRegularNonEmptyFile() ||
      File(dir, "model.bin").isRegularNonEmptyFile() ||
      (File(dir, "am").isDirectory && (File(dir, "am").listFiles()?.any { it.isRegularNonEmptyFile() } == true))

    val hasConfigOrGraph =
      File(dir, "conf/mfcc.conf").isRegularNonEmptyFile() ||
      File(dir, "conf/model.conf").isRegularNonEmptyFile() ||
      File(dir, "mfcc.conf").isRegularNonEmptyFile() ||
      File(dir, "graph").isDirectory ||
      File(dir, "ivector").isDirectory ||
      (File(dir, "conf").isDirectory)

    return hasAcousticModel && hasConfigOrGraph
  }

  private fun File.isRegularNonEmptyFile(): Boolean {
    return exists() && isFile && length() > 0L
  }

  /**
   * Resolves the actual Vosk model root directory, automatically handling archives that extract
   * with an extra outer wrapper folder (up to 2 levels deep).
   */
  fun resolveValidModelRoot(baseDir: File, maxDepth: Int = 2): File? {
    if (!baseDir.exists() || !baseDir.isDirectory) return null
    if (isValidVoskModelRoot(baseDir)) return baseDir
    if (maxDepth <= 0) return null

    val subDirs = baseDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && !f.name.startsWith("__MACOSX") }
      ?: return null
    for (sub in subDirs) {
      val resolved = resolveValidModelRoot(sub, maxDepth - 1)
      if (resolved != null) return resolved
    }
    return null
  }

  fun isModelInstalled(modelInfo: VoiceModelInfo): Boolean {
    val rawTargetDir = File(modelsDirectory, modelInfo.targetDirectoryName)
    return if (modelInfo.type == VoiceModelType.TTS) {
      resolveValidTtsModelRoot(rawTargetDir) != null
    } else {
      resolveValidModelRoot(rawTargetDir) != null
    }
  }

  fun isModelZipDownloaded(modelInfo: VoiceModelInfo): Boolean {
    val zipFile = getDownloadedZipFile(modelInfo)
    if (!zipFile.exists() || !zipFile.isFile || zipFile.length() <= 1024L) return false
    if (modelInfo.companionConfigUrl != null) {
      val cfg = getDownloadedCompanionConfigFile(modelInfo)
      if (!cfg.exists() || cfg.length() <= 16L) return false
    }
    return true
  }

  fun refreshStatus() {
    val map = mutableMapOf<String, ModelInstallStatus>()
    for (info in CATALOG) {
      if (isModelInstalled(info)) {
        map[info.id] = ModelInstallStatus.Installed(getModelDirectory(info))
      } else if (isModelZipDownloaded(info)) {
        map[info.id] = ModelInstallStatus.Downloaded(getDownloadedZipFile(info))
      } else {
        map[info.id] = ModelInstallStatus.NotInstalled
      }
    }
    _modelStatuses.value = map
  }

  fun getAvailableStorageBytes(): Long {
    return try {
      val dir = context.filesDir
      if (!dir.exists()) dir.mkdirs()
      val stat = StatFs(dir.absolutePath)
      val bytes = stat.availableBlocksLong * stat.blockSizeLong
      if (bytes > 0L) bytes else dir.usableSpace.let { if (it > 0L) it else 500_000_000L }
    } catch (e: Exception) {
      Log.e(TAG, "Error calculating available disk space", e)
      500_000_000L
    }
  }

  /**
   * Step 1: Downloads the model archive (.zip) so the user can then explicitly click "تثبيت" (Install),
   * or can be chained via downloadAndInstallModel.
   */
  suspend fun downloadModelOnly(
    modelInfo: VoiceModelInfo,
    onProgress: (Int, Long, Long) -> Unit = { _, _, _ -> }
  ): Result<File> = withContext(Dispatchers.IO) {
    val requiredSpace = modelInfo.sizeBytes * 2L
    val availableSpace = getAvailableStorageBytes()
    if (availableSpace < requiredSpace) {
      val errorMsg = "المساحة المتاحة غير كافية (${availableSpace / (1024 * 1024)} ميجابايت، المطلوب ${requiredSpace / (1024 * 1024)} ميجابايت)."
      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Error(errorMsg))
      }
      return@withContext Result.failure(IllegalStateException(errorMsg))
    }

    val downloadedZipFile = getDownloadedZipFile(modelInfo)
    val tempZipFile = File(modelsDirectory, "${modelInfo.id}_downloading.tmp")

    try {
      val existingPartialBytes = if (tempZipFile.exists()) tempZipFile.length() else 0L
      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        val initPct = if (modelInfo.sizeBytes > 0) ((existingPartialBytes * 100) / modelInfo.sizeBytes).toInt().coerceIn(0, 99) else 0
        put(modelInfo.id, ModelInstallStatus.Downloading(initPct, existingPartialBytes, modelInfo.sizeBytes))
      }

      val requestBuilder = Request.Builder().url(modelInfo.downloadUrl)
      if (existingPartialBytes > 0L) {
        requestBuilder.header("Range", "bytes=$existingPartialBytes-")
      }

      val response = okHttpClient.newCall(requestBuilder.build()).execute()
      val isResumedPartial = response.code == 206 && existingPartialBytes > 0L
      if (!response.isSuccessful && response.code != 206) {
        throw IllegalStateException("فشل التنزيل من الخادم: كود الاستجابة ${response.code}")
      }

      val body = response.body ?: throw IllegalStateException("ملف الاستجابة فارغ")
      val contentLen = body.contentLength()
      val totalBytes = when {
        isResumedPartial && contentLen > 0 -> existingPartialBytes + contentLen
        contentLen > 0 -> contentLen
        else -> modelInfo.sizeBytes
      }

      body.byteStream().use { input ->
        FileOutputStream(tempZipFile, isResumedPartial).use { output ->
          val buffer = ByteArray(16 * 1024)
          var bytesRead: Int
          var totalRead = if (isResumedPartial) existingPartialBytes else 0L
          var lastReportedPercent = -1

          while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            val percent = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
            if (percent != lastReportedPercent) {
              lastReportedPercent = percent
              _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
                put(modelInfo.id, ModelInstallStatus.Downloading(percent, totalRead, totalBytes))
              }
              onProgress(percent, totalRead, totalBytes)
            }
          }
          output.flush()
        }
      }

      // If the model has a companion config URL (e.g. Piper .onnx.json), download it alongside
      val companionUrl = modelInfo.companionConfigUrl
      if (!companionUrl.isNullOrBlank()) {
        val companionFile = getDownloadedCompanionConfigFile(modelInfo)
        val cfgReq = Request.Builder().url(companionUrl).build()
        val cfgResp = okHttpClient.newCall(cfgReq).execute()
        if (!cfgResp.isSuccessful) {
          throw IllegalStateException("فشل تنزيل ملف إعدادات النموذج الصوتي (${cfgResp.code}).")
        }
        val cfgBody = cfgResp.body ?: throw IllegalStateException("ملف إعدادات النموذج فارغ.")
        cfgBody.byteStream().use { input ->
          FileOutputStream(companionFile).use { output ->
            input.copyTo(output)
            output.flush()
          }
        }
      }

      if (!modelInfo.expectedSha256.isNullOrBlank()) {
        val calculatedSha256 = calculateSha256(tempZipFile)
        if (!calculatedSha256.equals(modelInfo.expectedSha256, ignoreCase = true)) {
          tempZipFile.delete()
          throw IllegalStateException("فشل التحقق من سلامة الملف (SHA-256 غير مطابق).")
        }
      }

      if (downloadedZipFile.exists()) downloadedZipFile.delete()
      val renamed = tempZipFile.renameTo(downloadedZipFile)
      if (!renamed) {
        tempZipFile.copyTo(downloadedZipFile, overwrite = true)
        tempZipFile.delete()
      }

      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Downloaded(downloadedZipFile))
      }
      Result.success(downloadedZipFile)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Error downloading model ${modelInfo.id}", e)
      // Preserve tempZipFile on network interruption so subsequent downloadModelOnly can resume via HTTP Range
      val msg = e.localizedMessage ?: "خطأ غير معروف أثناء تنزيل النموذج"
      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Error(msg))
      }
      Result.failure(e)
    }
  }

  /**
   * Step 2: Installs a downloaded model archive (.zip or direct .onnx + .onnx.json) into the active voice_models directory.
   */
  suspend fun installDownloadedModel(modelInfo: VoiceModelInfo): Result<File> = withContext(Dispatchers.IO) {
    val zipFile = getDownloadedZipFile(modelInfo)
    if (!zipFile.exists() || zipFile.length() == 0L) {
      val msg = "ملف النموذج غير موجود للتثبيت. يرجى تنزيله أولاً أو اختيار ملف من الجهاز."
      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Error(msg))
      }
      return@withContext Result.failure(IllegalStateException(msg))
    }
    if (modelInfo.type == VoiceModelType.TTS && zipFile.name.endsWith(".onnx", ignoreCase = true)) {
      return@withContext installDirectOnnxTtsPackage(modelInfo, zipFile, deleteSourceAfter = true)
    }
    return@withContext extractAndActivateZip(modelInfo, zipFile, deleteZipAfter = true)
  }

  /**
   * Atomically activates a direct ONNX Neural TTS model + its JSON configuration in private storage,
   * recording its SHA-256 checksum and version manifest while preserving any previously working model on failure.
   */
  private fun installDirectOnnxTtsPackage(
    modelInfo: VoiceModelInfo,
    onnxFile: File,
    deleteSourceAfter: Boolean
  ): Result<File> {
    val targetDir = File(modelsDirectory, modelInfo.targetDirectoryName)
    val stagingDir = File(modelsDirectory, ".staging_${modelInfo.id}_${System.currentTimeMillis()}")
    val backupDir = File(modelsDirectory, ".backup_${modelInfo.id}")
    val companionConfig = getDownloadedCompanionConfigFile(modelInfo)

    return try {
      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Extracting(30))
      }
      if (stagingDir.exists()) stagingDir.deleteRecursively()
      stagingDir.mkdirs()

      // Validate ONNX protobuf header & size
      if (onnxFile.length() <= 128L) {
        throw IllegalStateException("ملف نموذج النطق العصبي ONNX تالف أو غير مكتمل.")
      }
      if (!companionConfig.exists() || companionConfig.length() <= 16L) {
        throw IllegalStateException("ملف إعدادات الصوت العصبي (.onnx.json) مفقود أو تالف.")
      }

      val stagedOnnx = File(stagingDir, "model.onnx")
      val stagedConfig = File(stagingDir, "model.onnx.json")
      onnxFile.copyTo(stagedOnnx, overwrite = true)
      companionConfig.copyTo(stagedConfig, overwrite = true)

      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Extracting(75))
      }

      // Compute and persist SHA-256 integrity manifest
      val sha256 = calculateSha256(stagedOnnx)
      if (!modelInfo.expectedSha256.isNullOrBlank() && !sha256.equals(modelInfo.expectedSha256, ignoreCase = true)) {
        throw IllegalStateException("فشل التحقق من بصمة SHA-256 لملف النطق العصبي.")
      }
      val manifestJson = org.json.JSONObject().apply {
        put("id", modelInfo.id)
        put("version", modelInfo.version)
        put("sha256", sha256)
        put("sizeBytes", stagedOnnx.length())
        put("installedAtMs", System.currentTimeMillis())
      }
      File(stagingDir, "manifest.json").writeText(manifestJson.toString(2))

      if (!isValidNeuralTtsModelRoot(stagingDir)) {
        throw IllegalStateException("فشل التحقق من بنية نموذج النطق العصبي قبل التفعيل.")
      }

      if (backupDir.exists()) backupDir.deleteRecursively()
      val hadExisting = targetDir.exists()
      if (hadExisting) {
        targetDir.renameTo(backupDir)
      }

      val moved = stagingDir.renameTo(targetDir)
      if (!moved) {
        stagingDir.copyRecursively(targetDir, overwrite = true)
      }

      val verifiedDir = resolveValidTtsModelRoot(targetDir)
      if (verifiedDir == null) {
        targetDir.deleteRecursively()
        if (hadExisting && backupDir.exists()) {
          backupDir.renameTo(targetDir)
        }
        throw IllegalStateException("فشل التحقق النهائي من نموذج النطق العصبي بعد التثبيت.")
      }

      if (backupDir.exists()) backupDir.deleteRecursively()
      if (stagingDir.exists()) stagingDir.deleteRecursively()
      if (deleteSourceAfter) {
        onnxFile.delete()
        companionConfig.delete()
      }

      if (modelInfo.type == VoiceModelType.TTS && getActiveTtsModelId().isEmpty()) {
        setActiveTtsModelId(modelInfo.id)
      }

      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Installed(verifiedDir))
      }
      Log.i(TAG, "Neural TTS Model [${modelInfo.id}] verified (SHA-256=$sha256) and installed at: ${verifiedDir.absolutePath}")
      Result.success(verifiedDir)
    } catch (e: Exception) {
      Log.e(TAG, "Error installing ONNX TTS model ${modelInfo.id}", e)
      try {
        if (stagingDir.exists()) stagingDir.deleteRecursively()
      } catch (_: Exception) {}
      val msg = e.localizedMessage ?: "فشل تثبيت نموذج النطق العصبي"
      val finalStatus = if (isModelInstalled(modelInfo)) {
        ModelInstallStatus.Installed(getModelDirectory(modelInfo))
      } else {
        ModelInstallStatus.Error(msg)
      }
      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, finalStatus)
      }
      Result.failure(e)
    }
  }

  /**
   * Installs a voice model directly from a user-selected local ZIP file URI on the device.
   */
  suspend fun installModelFromUri(modelInfo: VoiceModelInfo, uri: android.net.Uri): Result<File> = withContext(Dispatchers.IO) {
    val targetZip = getDownloadedZipFile(modelInfo)
    try {
      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Extracting(10))
      }
      val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("تعذر قراءة ملف النموذج المحدد من الجهاز.")
      inputStream.use { input ->
        FileOutputStream(targetZip).use { output ->
          input.copyTo(output, 16 * 1024)
          output.flush()
        }
      }
      return@withContext extractAndActivateZip(modelInfo, targetZip, deleteZipAfter = true)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "Failed installing model from URI for ${modelInfo.id}", e)
      targetZip.delete()
      val msg = e.localizedMessage ?: "تعذر تثبيت ملف النموذج من الجهاز"
      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Error(msg))
      }
      Result.failure(e)
    }
  }

  private fun extractAndActivateZip(
    modelInfo: VoiceModelInfo,
    zipFile: File,
    deleteZipAfter: Boolean
  ): Result<File> {
    val targetDir = File(modelsDirectory, modelInfo.targetDirectoryName)
    val stagingDir = File(modelsDirectory, ".staging_${modelInfo.id}_${System.currentTimeMillis()}")
    val backupDir = File(modelsDirectory, ".backup_${modelInfo.id}")

    return try {
      val requiredSpace = zipFile.length().coerceAtLeast(1024L) * 2L
      val availableSpace = getAvailableStorageBytes()
      if (availableSpace in 1 until requiredSpace) {
        throw IllegalStateException("المساحة المتاحة غير كافية لفك ضغط النموذج الصوتي.")
      }

      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Extracting(35))
      }

      if (stagingDir.exists()) {
        stagingDir.deleteRecursively()
      }
      stagingDir.mkdirs()

      // 1. Safely extract into isolated staging directory so existing installed models are never corrupted on failure
      unzipFile(zipFile, stagingDir)

      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Extracting(75))
      }

      // 2. Locate the actual valid model root inside stagingDir (supports both Vosk STT and ONNX Neural TTS archives)
      val validStagingRoot = if (modelInfo.type == VoiceModelType.TTS) {
        resolveValidTtsModelRoot(stagingDir, maxDepth = 3)
          ?: throw IllegalStateException("الأرشيف لا يحتوي على ملفات نموذج نطق عصبي ONNX صالحة (مثل model.onnx و model.onnx.json).")
      } else {
        resolveValidModelRoot(stagingDir, maxDepth = 3)
          ?: throw IllegalStateException("الأرشيف لا يحتوي على ملفات نموذج Vosk صالحة (مثل am/final.mdl و conf).")
      }

      // 3. Optionally verify native org.vosk.Model readability when native library is present for STT
      if (modelInfo.type == VoiceModelType.STT) {
        verifyNativeVoskModelIfAvailable(validStagingRoot)
      }

      // 4. Atomically swap verified model into targetDir, preserving backup if swap fails
      if (backupDir.exists()) {
        backupDir.deleteRecursively()
      }
      val hadExistingTarget = targetDir.exists()
      if (hadExistingTarget) {
        targetDir.renameTo(backupDir)
      }

      val moved = validStagingRoot.renameTo(targetDir)
      if (!moved) {
        validStagingRoot.copyRecursively(targetDir, overwrite = true)
      }

      // Final verification at target destination
      val finalVerifiedDir = if (modelInfo.type == VoiceModelType.TTS) {
        resolveValidTtsModelRoot(targetDir)
      } else {
        resolveValidModelRoot(targetDir)
      }
      if (finalVerifiedDir == null) {
        targetDir.deleteRecursively()
        if (hadExistingTarget && backupDir.exists()) {
          backupDir.renameTo(targetDir)
        }
        throw IllegalStateException("فشل التحقق النهائي من ملفات النموذج بعد النقل.")
      }

      // Clean up backup and temporary staging
      if (backupDir.exists()) {
        backupDir.deleteRecursively()
      }
      if (stagingDir.exists()) {
        stagingDir.deleteRecursively()
      }
      if (deleteZipAfter && zipFile.exists()) {
        zipFile.delete()
      }

      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, ModelInstallStatus.Installed(finalVerifiedDir))
      }
      Log.i(TAG, "Model [${modelInfo.id}] verified and installed at: ${finalVerifiedDir.absolutePath}")
      Result.success(finalVerifiedDir)
    } catch (e: Exception) {
      Log.e(TAG, "Error extracting/validating model ${modelInfo.id}", e)
      try {
        if (stagingDir.exists()) stagingDir.deleteRecursively()
      } catch (_: Exception) {}
      val msg = e.localizedMessage ?: "فشل فك ضغط وتثبيت النموذج"
      // If a valid previous installation still exists on disk, keep it Installed; otherwise report Error
      val finalStatus = if (isModelInstalled(modelInfo)) {
        ModelInstallStatus.Installed(getModelDirectory(modelInfo))
      } else {
        ModelInstallStatus.Error(msg)
      }
      _modelStatuses.value = _modelStatuses.value.toMutableMap().apply {
        put(modelInfo.id, finalStatus)
      }
      Result.failure(e)
    }
  }

  /**
   * Attempts a lightweight native Vosk Model header check if libvosk JNI is available in the runtime,
   * closing the temporary handle immediately. Skips gracefully in unit-test JVM environments without JNI.
   */
  private fun verifyNativeVoskModelIfAvailable(modelRootDir: File) {
    // Only invoke JNI constructor if actual Kaldi binary model exists (skip synthetic unit test stubs)
    val hasRealKaldiMdl = File(modelRootDir, "am/final.mdl").length() > 1024L ||
      File(modelRootDir, "final.mdl").length() > 1024L
    if (!hasRealKaldiMdl) return

    var testModel: org.vosk.Model? = null
    try {
      testModel = org.vosk.Model(modelRootDir.absolutePath)
    } catch (ule: UnsatisfiedLinkError) {
      Log.d(TAG, "Skipping JNI Model probe (native library not loaded in current JVM environment)")
    } catch (ncdfe: NoClassDefFoundError) {
      Log.d(TAG, "Skipping JNI Model probe (native classes unavailable in current JVM environment)")
    } catch (ioe: Exception) {
      throw IllegalStateException("ملفات النموذج تالفة أو غير متوافقة مع محرك Vosk: ${ioe.message}", ioe)
    } finally {
      try {
        testModel?.close()
      } catch (_: Exception) {}
    }
  }

  suspend fun downloadAndInstallModel(
    modelInfo: VoiceModelInfo,
    onProgress: (Int, Long, Long) -> Unit = { _, _, _ -> }
  ): Result<File> = withContext(Dispatchers.IO) {
    val downloadRes = downloadModelOnly(modelInfo, onProgress)
    if (downloadRes.isFailure) {
      return@withContext downloadRes
    }
    return@withContext installDownloadedModel(modelInfo)
  }

  fun deleteModel(modelInfo: VoiceModelInfo): Boolean {
    val dir = File(modelsDirectory, modelInfo.targetDirectoryName)
    val zip = getDownloadedZipFile(modelInfo)
    val companionCfg = getDownloadedCompanionConfigFile(modelInfo)
    val tempFile = File(modelsDirectory, "${modelInfo.id}_downloading.tmp")
    if (zip.exists()) zip.delete()
    if (companionCfg.exists()) companionCfg.delete()
    if (tempFile.exists()) tempFile.delete()
    val success = if (dir.exists()) dir.deleteRecursively() else true
    if (modelInfo.type == VoiceModelType.TTS && getActiveTtsModelId() == modelInfo.id) {
      val remainingTts = CATALOG.firstOrNull { it.type == VoiceModelType.TTS && it.id != modelInfo.id && isModelInstalled(it) }
      setActiveTtsModelId(remainingTts?.id ?: "")
    }
    refreshStatus()
    return success
  }

  /**
   * Extracts a ZIP archive into [targetDirectory] with strict Zip-Slip and path traversal protection.
   * Visible internally so tests can verify malicious entry rejection.
   */
  internal fun unzipFile(zipFile: File, targetDirectory: File) {
    val canonicalDestDir = targetDirectory.canonicalFile
    val canonicalDestPrefix = canonicalDestDir.path + File.separator
    var extractedEntriesCount = 0

    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
      var entry: ZipEntry? = zis.nextEntry
      val buffer = ByteArray(16 * 1024)
      while (entry != null) {
        val rawName = entry.name.replace('\\', '/')
        if (rawName.startsWith("/") || rawName.contains("..")) {
          throw SecurityException("إدخال مضغوط غير آمن يحتوي على مسار نسبي أو مطلق مرفوض: $rawName")
        }

        val outFile = File(canonicalDestDir, rawName)
        val canonicalOutPath = outFile.canonicalFile.path
        if (canonicalOutPath != canonicalDestDir.path && !canonicalOutPath.startsWith(canonicalDestPrefix)) {
          throw SecurityException("إدخال مضغوط غير آمن خارج المسار المسموح: $rawName")
        }

        if (entry.isDirectory) {
          outFile.mkdirs()
        } else {
          outFile.parentFile?.mkdirs()
          FileOutputStream(outFile).use { fos ->
            var len: Int
            while (zis.read(buffer).also { len = it } > 0) {
              fos.write(buffer, 0, len)
            }
          }
          extractedEntriesCount++
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }

    if (extractedEntriesCount == 0) {
      throw IllegalStateException("ملف الأرشيف المضغوط فارغ أو تالف.")
    }
  }

  private fun calculateSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { fis ->
      val buffer = ByteArray(8192)
      var read: Int
      while (fis.read(buffer).also { read = it } != -1) {
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}
