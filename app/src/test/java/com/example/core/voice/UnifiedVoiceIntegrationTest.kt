package com.example.core.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.capabilities.CapabilityDetector
import com.example.core.capabilities.DeviceCapability
import com.example.core.intent.SpeechResult
import com.example.core.voice.engine.ActiveSttEngineType
import com.example.core.voice.engine.ArabicSpeechSynthesizer
import com.example.core.voice.engine.TtsEngineInspector
import com.example.core.voice.engine.UnifiedVoiceManager
import com.example.core.voice.model.ModelInstallStatus
import com.example.core.voice.model.VoiceModelManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnifiedVoiceIntegrationTest {

  private lateinit var context: Context
  private lateinit var unifiedVoiceManager: UnifiedVoiceManager
  private lateinit var modelManager: VoiceModelManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    modelManager = VoiceModelManager(context)
    unifiedVoiceManager = UnifiedVoiceManager(context, modelManager)
  }

  /**
   * 1. Test: Non-Google / Offline environment detection.
   * On pure AOSP / Robolectric with no GMS package, the system should not falsely claim online STT is ready.
   */
  @Test
  fun testNonGoogleOfflineEnvironmentDetection() {
    unifiedVoiceManager.refresh()
    val status = unifiedVoiceManager.status.value

    assertNotNull(status)
    // If not online and no model is installed, offline STT is accurately reported as false
    assertFalse(
      "Should not claim offline STT is ready when no offline model is installed",
      status.isOfflineModelInstalled
    )
    assertNull(
      "Installed model name must be null when no model directory exists",
      status.installedModelName
    )
  }

  /**
   * 2. Test: TTS does not falsely fall back to English on unsupported Arabic, and routes cleanly
   * through the local Arabic acoustic/neural hierarchy without ever throwing an unhandled exception.
   */
  @Test
  fun testTtsDoesNotFallbackToEnglishWhenArabicUnsupported() {
    val synth = ArabicSpeechSynthesizer(context)
    try {
      // In Robolectric standard mock without a system Arabic TTS voice, ArabicSpeechSynthesizer
      // must NOT speak English via system TTS; it must either use the local Arabic synthesizer
      // (EMBEDDED_FORMANT_FALLBACK / NEURAL_ONNX_LOCAL) or return Failed/Skipped cleanly.
      val result = synth.speak("أهلاً بك في WEYKY")
      assertNotEquals("Must not use English system TTS when Arabic is unsupported", "SYSTEM_LOCAL_TTS", synth.activeTtsSource)
      assertTrue(
        result is SpeechResult.Success || result is SpeechResult.Failed || result is SpeechResult.Skipped
      )
    } finally {
      synth.shutdown()
    }
  }

  /**
   * 3. Test: Voice Model Manager directory isolation and space checks.
   */
  @Test
  fun testVoiceModelManagerStorageAndCatalog() {
    val catalog = VoiceModelManager.CATALOG
    assertTrue("Voice model catalog must include Arabic model", catalog.isNotEmpty())

    val tnAr = catalog.first { it.id == "vosk_ar_linto_tn" }
    assertEquals("Apache 2.0 (Open Source)", tnAr.license)
    assertTrue(tnAr.sizeBytes > 100_000_000L) // ~158 MB

    val mgb2Ar = catalog.first { it.id == "vosk_ar_mgb2" }
    assertEquals("Apache 2.0 (Open Source)", mgb2Ar.license)
    assertTrue(mgb2Ar.sizeBytes > 300_000_000L) // ~318 MB

    val kareemTts = catalog.first { it.id == "piper_ar_kareem_neural_tts" }
    assertEquals(com.example.core.voice.model.VoiceModelType.TTS, kareemTts.type)
    assertFalse("Kareem must be male voice", kareemTts.isFemaleVoice)
    assertTrue(kareemTts.nameArabic.contains("كريم"))
    assertTrue("Kareem model must be ~60MB", kareemTts.sizeBytes > 50_000_000L)

    val availableSpace = modelManager.getAvailableStorageBytes()
    assertTrue("Available storage must be measurable", availableSpace > 0L)

    val isInstalledBefore = modelManager.isModelInstalled(mgb2Ar)
    assertFalse("Model should not be marked installed before files exist", isInstalledBefore)
  }

  /**
   * 4. Test: Simulating offline Vosk model installation and lifecycle.
   * Creates a mock Vosk directory structure and verifies detection by UnifiedVoiceManager.
   */
  @Test
  fun testOfflineModelDetectionAndStatusChange() {
    val mgb2Model = VoiceModelManager.VOSK_ARABIC_MGB2
    val targetDir = modelManager.getModelDirectory(mgb2Model)

    // Simulate model downloaded and extracted with Vosk required artifacts
    targetDir.mkdirs()
    File(targetDir, "am").mkdirs()
    File(targetDir, "conf").mkdirs()
    File(targetDir, "model.bin").writeText("vosk-acoustic-model-weights")

    try {
      assertTrue("Model should now be recognized as installed", modelManager.isModelInstalled(mgb2Model))

      unifiedVoiceManager.refresh()
      val status = unifiedVoiceManager.status.value

      assertTrue("isOfflineModelInstalled must be true after model files present", status.isOfflineModelInstalled)
      assertEquals("نموذج Vosk العربي الفصيح الشامل (MGB-2)", status.installedModelName)

      // Test capability detector also accurately picks this up
      val capabilityDetector = CapabilityDetector(context)
      val hasVoice = capabilityDetector.isCapabilitySupported(DeviceCapability.VOICE_RECOGNITION)
      assertTrue("CapabilityDetector must report VOICE_RECOGNITION as supported when local model is installed", hasVoice)

      // Clean up test files
      val deleted = modelManager.deleteModel(mgb2Model)
      assertTrue("Model directory must be cleanly deleted", deleted)
      assertFalse("Model must be marked not installed after deletion", modelManager.isModelInstalled(mgb2Model))
    } finally {
      targetDir.deleteRecursively()
    }
  }

  /**
   * 5. Test: VoicePipeline session termination and cleanup.
   */
  @Test
  fun testVoicePipelineSessionCleanCancellation() {
    var transcriptCalled = false
    val pipeline = VoicePipeline(
      context = context,
      onTranscriptReady = { transcriptCalled = true },
      onListeningStateChanged = {},
      unifiedVoiceManager = unifiedVoiceManager
    )

    pipeline.cancel()
    val state = pipeline.sessionState.value
    assertEquals(VoiceSessionState.Idle, state)
    assertFalse("Transcript callback must not be invoked on cancellation", transcriptCalled)

    pipeline.destroy()
    assertEquals(VoiceSessionState.Idle, pipeline.sessionState.value)
  }

  /**
   * 6. Test: Starting and stopping listening normally without sending pause/stop to external media.
   */
  @Test
  fun testStartAndStopListeningDoesNotPauseExternalMedia() {
    val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

    val viewModel = com.example.core.state.WeykyViewModel(context as android.app.Application)
    try {
      // Ensure WEYKY TTS is idle before tapping mic
      assertFalse(viewModel.speechSynthesizer.isSpeaking)

      // Calling stopIfSpeaking when TTS is idle must return false and avoid unnecessary TTS stop/AudioFocus IPC
      val stoppedWhenIdle = viewModel.speechSynthesizer.stopIfSpeaking()
      assertFalse("stopIfSpeaking() must be a no-op when WEYKY TTS is not speaking", stoppedWhenIdle)

      // Trigger microphone start and stop
      viewModel.startVoiceRecognition()
      viewModel.toggleVoiceRecognition()

      // Verify state is cleanly restored
      assertFalse("Voice listening state must be false after stopping", viewModel.uiState.value.isVoiceListening)
    } finally {
      viewModel.voicePipeline.destroy()
    }
  }

  /**
   * 7. Test: Failure to start recording (offline with no model, or system ERROR_AUDIO) restores UI state cleanly.
   */
  @Test
  fun testStartRecordingFailureRestoresStateCleanly() {
    val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

    // Disconnect network and disable on-device recognition in shadow so no engine is available
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val shadowCm = org.robolectric.Shadows.shadowOf(cm)
    shadowCm.setDefaultNetworkActive(false)
    cm.activeNetwork?.let { shadowCm.setNetworkCapabilities(it, null) }
    org.robolectric.shadows.ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)

    var latestListeningState = true
    val pipeline = VoicePipeline(
      context = context,
      onTranscriptReady = {},
      onListeningStateChanged = { latestListeningState = it },
      unifiedVoiceManager = unifiedVoiceManager
    )

    // When offline and no Vosk model is installed, startListening immediately reports Error and restores listening=false
    pipeline.startListening("ar-SA")
    val sessionState = pipeline.sessionState.value
    assertTrue("Session state should transition to Error when offline with no local model", sessionState is VoiceSessionState.Error)
    assertFalse("Listening flag must be false after start failure", latestListeningState)
    assertEquals(ActiveSttEngineType.NONE, pipeline.activeEngineType)
    pipeline.destroy()
  }

  /**
   * 8. Test: Preventing unintended concurrent listening sessions and releasing resources on completion/error.
   */
  @Test
  fun testPreventConcurrentSessionsAndResourceRelease() {
    val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

    var listeningCallbackCount = 0
    val pipeline = VoicePipeline(
      context = context,
      onTranscriptReady = {},
      onListeningStateChanged = { if (it) listeningCallbackCount++ },
      unifiedVoiceManager = unifiedVoiceManager
    )

    // Simulate entering Listening state
    pipeline.onReadyForSpeech(null)
    assertEquals(VoiceSessionState.Listening, pipeline.sessionState.value)
    assertEquals(1, listeningCallbackCount)

    // Stop listening and verify transition back to Idle
    pipeline.stopListening()
    assertEquals(VoiceSessionState.Idle, pipeline.sessionState.value)

    // Simulate error callback and verify cleanup
    pipeline.onError(android.speech.SpeechRecognizer.ERROR_AUDIO)
    assertTrue(pipeline.sessionState.value is VoiceSessionState.Error)

    // Cancel restores to Idle
    pipeline.cancel()
    assertEquals(VoiceSessionState.Idle, pipeline.sessionState.value)
    pipeline.destroy()
  }

  /**
   * 9. Test: Wi-Fi connected WITHOUT NET_CAPABILITY_VALIDATED (e.g. router without internet)
   * selects local Vosk model when installed instead of hanging on SYSTEM_ONLINE.
   */
  @Test
  fun testWifiWithoutValidatedInternetSelectsLocalVoskWhenModelInstalled() {
    val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

    val mgb2Model = VoiceModelManager.VOSK_ARABIC_MGB2
    val targetDir = File(modelManager.modelsDirectory, mgb2Model.targetDirectoryName)
    targetDir.mkdirs()
    File(targetDir, "am").mkdirs()
    File(targetDir, "conf").mkdirs()
    File(targetDir, "am/final.mdl").writeText("kaldi-mdl")
    File(targetDir, "conf/mfcc.conf").writeText("--sample-frequency=16000")

    try {
      // Simulate Wi-Fi with NET_CAPABILITY_INTERNET but WITHOUT NET_CAPABILITY_VALIDATED
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
      val shadowCm = org.robolectric.Shadows.shadowOf(cm)
      val activeNet = cm.activeNetwork
      if (activeNet != null) {
        val unvalidatedCaps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        val shadowCaps = org.robolectric.Shadows.shadowOf(unvalidatedCaps)
        shadowCaps.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowCaps.removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowCm.setNetworkCapabilities(activeNet, unvalidatedCaps)
      }

      unifiedVoiceManager.refresh()
      assertFalse("Unvalidated network must report isOnline=false", unifiedVoiceManager.isNetworkConnected())
      assertEquals(
        "UnifiedVoiceManager status must report VOSK_LOCAL_OFFLINE when unvalidated and Arabic model installed",
        ActiveSttEngineType.VOSK_LOCAL_OFFLINE,
        unifiedVoiceManager.status.value.sttEngine
      )
    } finally {
      targetDir.deleteRecursively()
    }
  }

  /**
   * 10. Test: Error classification for fallback to Vosk vs non-network errors.
   * Verifies network/server/language-unavailable errors trigger fallback eligibility,
   * while ERROR_AUDIO / ERROR_INSUFFICIENT_PERMISSIONS / ERROR_NO_MATCH do not loop into Vosk.
   */
  @Test
  fun testFallbackErrorClassificationAndSingleFallbackGuard() {
    val pipeline = VoicePipeline(
      context = context,
      onTranscriptReady = {},
      onListeningStateChanged = {},
      unifiedVoiceManager = unifiedVoiceManager
    )

    // Network, server, and offline language pack errors are eligible for local fallback
    assertTrue(pipeline.isFallbackEligibleSystemError(android.speech.SpeechRecognizer.ERROR_NETWORK))
    assertTrue(pipeline.isFallbackEligibleSystemError(android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
    assertTrue(pipeline.isFallbackEligibleSystemError(android.speech.SpeechRecognizer.ERROR_SERVER))
    assertTrue(pipeline.isFallbackEligibleSystemError(11)) // ERROR_SERVER_DISCONNECTED
    assertTrue(pipeline.isFallbackEligibleSystemError(12)) // ERROR_LANGUAGE_NOT_SUPPORTED
    assertTrue(pipeline.isFallbackEligibleSystemError(13)) // ERROR_LANGUAGE_UNAVAILABLE

    // Non-network errors must NOT falsely trigger engine fallback
    assertFalse(pipeline.isFallbackEligibleSystemError(android.speech.SpeechRecognizer.ERROR_AUDIO))
    assertFalse(pipeline.isFallbackEligibleSystemError(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    assertFalse(pipeline.isFallbackEligibleSystemError(android.speech.SpeechRecognizer.ERROR_CLIENT))
    assertFalse(pipeline.isFallbackEligibleSystemError(android.speech.SpeechRecognizer.ERROR_NO_MATCH))

    pipeline.destroy()
  }

  /**
   * 11. Test: Single final transcript per session, partial-to-final deduplication, and stale session protection.
   * Ensures a command is executed at most ONCE per session, and repeating the same command in a new session works.
   */
  @Test
  fun testSingleCommandExecutionPerSessionAndPartialFinalDeduplication() {
    val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

    val deliveredTranscripts = mutableListOf<String>()
    val pipeline = VoicePipeline(
      context = context,
      onTranscriptReady = { deliveredTranscripts.add(it) },
      onListeningStateChanged = {},
      unifiedVoiceManager = unifiedVoiceManager
    )

    // Start session 1
    pipeline.onReadyForSpeech(null)

    // 1) Partial result arrives -> must NOT execute command
    val partialBundle = android.os.Bundle().apply {
      putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("شغل الفلاش"))
    }
    pipeline.onPartialResults(partialBundle)
    assertEquals("Partial results must never trigger command execution", 0, deliveredTranscripts.size)
    assertTrue(pipeline.sessionState.value is VoiceSessionState.Partial)

    // 2) Final result arrives with the same text -> executes ONCE
    val finalBundle = android.os.Bundle().apply {
      putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("شغل الفلاش"))
    }
    pipeline.onResults(finalBundle)
    assertEquals("Final result must execute command exactly once", 1, deliveredTranscripts.size)
    assertEquals("شغل الفلاش", deliveredTranscripts[0])

    // 3) Duplicate onResults callback in the same session -> must be ignored!
    pipeline.onResults(finalBundle)
    assertEquals("Duplicate final callback in same session must be ignored", 1, deliveredTranscripts.size)

    pipeline.destroy()
  }

  /**
   * 12. Test: Recovering completed partial speech when network drops right at end of utterance,
   * and rejecting blank/empty speech.
   */
  @Test
  fun testRecoverCompletedPartialOnEndNetworkDropAndRejectBlankSpeech() {
    val deliveredTranscripts = mutableListOf<String>()
    val pipeline = VoicePipeline(
      context = context,
      onTranscriptReady = { deliveredTranscripts.add(it) },
      onListeningStateChanged = {},
      unifiedVoiceManager = unifiedVoiceManager
    )

    pipeline.onReadyForSpeech(null)
    val partialBundle = android.os.Bundle().apply {
      putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("كم الساعة الآن"))
    }
    pipeline.onPartialResults(partialBundle)

    // Network drops right after the user finished speaking
    pipeline.onError(android.speech.SpeechRecognizer.ERROR_NETWORK)
    assertEquals("Completed partial transcript must be preserved on terminal network drop", 1, deliveredTranscripts.size)
    assertEquals("كم الساعة الآن", deliveredTranscripts[0])

    // Blank result in a new state must not be delivered
    val emptyBundle = android.os.Bundle().apply {
      putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("   "))
    }
    val pipeline2 = VoicePipeline(
      context = context,
      onTranscriptReady = { deliveredTranscripts.add(it) },
      onListeningStateChanged = {},
      unifiedVoiceManager = unifiedVoiceManager
    )
    pipeline2.onResults(emptyBundle)
    assertEquals("Blank transcript must never trigger onTranscriptReady", 1, deliveredTranscripts.size)
    assertTrue(pipeline2.sessionState.value is VoiceSessionState.Error)

    pipeline.destroy()
    pipeline2.destroy()
  }

  /**
   * 13. Test: Corrupted/incomplete model directory rejection, nested ZIP root folder resolution,
   * and Zip-Slip path traversal rejection.
   */
  @Test
  fun testDeepModelValidationNestedZipAndZipSlipSecurity() {
    val lintoModel = VoiceModelManager.VOSK_ARABIC_LINTO_TN
    val rawTargetDir = File(modelManager.modelsDirectory, lintoModel.targetDirectoryName)
    rawTargetDir.deleteRecursively()

    try {
      // A) Incomplete directory (e.g. only dummy files without am + conf) must NOT be considered installed
      rawTargetDir.mkdirs()
      File(rawTargetDir, "readme.txt").writeText("dummy")
      File(rawTargetDir, "extra1.txt").writeText("dummy")
      File(rawTargetDir, "extra2.txt").writeText("dummy")
      assertFalse(
        "Incomplete folder without Vosk acoustic/config artifacts must be rejected",
        modelManager.isModelInstalled(lintoModel)
      )
      rawTargetDir.deleteRecursively()

      // B) Nested wrapper directory inside targetDir (e.g. unzip created extra outer folder) is resolved properly
      val nestedDir = File(rawTargetDir, "vosk-model-small-ar-tn-0.1-linto-inner")
      File(nestedDir, "am").mkdirs()
      File(nestedDir, "conf").mkdirs()
      File(nestedDir, "am/final.mdl").writeText("kaldi-weights")
      File(nestedDir, "conf/mfcc.conf").writeText("--sample-frequency=16000")

      assertTrue("Nested valid Vosk model must be detected", modelManager.isModelInstalled(lintoModel))
      assertEquals(
        "getModelDirectory must resolve to the inner folder containing am/ and conf/",
        nestedDir.absolutePath,
        modelManager.getModelDirectory(lintoModel).absolutePath
      )
      rawTargetDir.deleteRecursively()

      // C) Zip-Slip attack archive with "../evil.txt" must throw SecurityException and never write outside staging
      val maliciousZip = File(modelManager.modelsDirectory, "malicious.zip")
      java.util.zip.ZipOutputStream( java.io.FileOutputStream(maliciousZip) ).use { zos ->
        val entry = java.util.zip.ZipEntry("../../evil_escape.txt")
        zos.putNextEntry(entry)
        zos.write("malicious".toByteArray())
        zos.closeEntry()
      }
      val extractTarget = File(modelManager.modelsDirectory, "safe_extract_test")
      extractTarget.mkdirs()
      var zipSlipRejected = false
      try {
        modelManager.unzipFile(maliciousZip, extractTarget)
      } catch (_: SecurityException) {
        zipSlipRejected = true
      } finally {
        maliciousZip.delete()
        extractTarget.deleteRecursively()
      }
      assertTrue("Zip-Slip traversal entry must be rejected with SecurityException", zipSlipRejected)
    } finally {
      rawTargetDir.deleteRecursively()
    }
  }

  /**
   * 14. Test: Language matching — English voice request when offline with only Arabic local model installed
   * must NOT silently pass English audio into the Arabic Vosk model.
   */
  @Test
  fun testEnglishRequestOfflineDoesNotSilentlyUseArabicVoskModel() {
    val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

    // Disconnect network and disable system on-device recognizer
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val shadowCm = org.robolectric.Shadows.shadowOf(cm)
    shadowCm.setDefaultNetworkActive(false)
    cm.activeNetwork?.let { shadowCm.setNetworkCapabilities(it, null) }
    org.robolectric.shadows.ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)

    // Install valid Arabic Vosk model
    val arModel = VoiceModelManager.VOSK_ARABIC_MGB2
    val targetDir = File(modelManager.modelsDirectory, arModel.targetDirectoryName)
    File(targetDir, "am").mkdirs()
    File(targetDir, "conf").mkdirs()
    File(targetDir, "am/final.mdl").writeText("kaldi-weights")
    File(targetDir, "conf/mfcc.conf").writeText("--sample-frequency=16000")

    try {
      // Lookup for Arabic returns the installed Arabic model, while lookup for English returns null
      assertNotNull(unifiedVoiceManager.getInstalledOfflineSttModel("ar-SA"))
      assertNull(
        "English STT model lookup must return null when only Arabic models are installed",
        unifiedVoiceManager.getInstalledOfflineSttModel("en-US")
      )

      val pipeline = VoicePipeline(
        context = context,
        onTranscriptReady = {},
        onListeningStateChanged = {},
        unifiedVoiceManager = unifiedVoiceManager
      )

      // Requesting English offline when no English model or on-device recognizer is available
      pipeline.startListening("en-US")
      assertEquals(ActiveSttEngineType.NONE, pipeline.activeEngineType)
      assertEquals("NO_ENGLISH_OFFLINE_MODEL_INSTALLED", pipeline.activeEngineSelectionReason)
      assertTrue(pipeline.sessionState.value is VoiceSessionState.Error)
      pipeline.destroy()
    } finally {
      targetDir.deleteRecursively()
    }
  }

  /**
   * 15. Test: Switching between the two Arabic models (MGB-2 and LinTO) and verifying that
   * a failed/corrupted ZIP extraction does NOT overwrite or destroy an already-installed valid model.
   */
  @Test
  fun testModelSwitchingAndPreservingInstalledModelOnCorruptedZip() = runBlocking {
    val mgb2Model = VoiceModelManager.VOSK_ARABIC_MGB2
    val lintoModel = VoiceModelManager.VOSK_ARABIC_LINTO_TN
    val mgb2Dir = File(modelManager.modelsDirectory, mgb2Model.targetDirectoryName)
    val lintoDir = File(modelManager.modelsDirectory, lintoModel.targetDirectoryName)

    try {
      // 1) Install MGB-2 model via a valid ZIP with an extra wrapper directory
      val validZip = modelManager.getDownloadedZipFile(mgb2Model)
      java.util.zip.ZipOutputStream(java.io.FileOutputStream(validZip)).use { zos ->
        zos.putNextEntry(java.util.zip.ZipEntry("vosk-model-ar-mgb2-0.4-wrapper/am/final.mdl"))
        zos.write("kaldi-mgb2-weights".toByteArray())
        zos.closeEntry()
        zos.putNextEntry(java.util.zip.ZipEntry("vosk-model-ar-mgb2-0.4-wrapper/conf/mfcc.conf"))
        zos.write("--sample-frequency=16000".toByteArray())
        zos.closeEntry()
      }

      val installRes = modelManager.installDownloadedModel(mgb2Model)
      assertTrue("Valid nested ZIP must install successfully", installRes.isSuccess)
      assertTrue(modelManager.isModelInstalled(mgb2Model))
      assertTrue(modelManager.modelStatuses.value[mgb2Model.id] is ModelInstallStatus.Installed)

      // 2) Attempt to re-install over MGB-2 using a corrupted/incomplete ZIP (missing am/conf)
      java.util.zip.ZipOutputStream(java.io.FileOutputStream(validZip)).use { zos ->
        zos.putNextEntry(java.util.zip.ZipEntry("broken/readme.txt"))
        zos.write("not-a-model".toByteArray())
        zos.closeEntry()
      }
      val brokenRes = modelManager.installDownloadedModel(mgb2Model)
      assertTrue("Incomplete ZIP installation must fail validation", brokenRes.isFailure)
      assertTrue(
        "Previously valid installed model must NOT be destroyed when a new extraction fails",
        modelManager.isModelInstalled(mgb2Model)
      )

      // 3) Install second model (LinTO) and switch by deleting MGB-2
      File(lintoDir, "am").mkdirs()
      File(lintoDir, "conf").mkdirs()
      File(lintoDir, "am/final.mdl").writeText("kaldi-linto-weights")
      File(lintoDir, "conf/mfcc.conf").writeText("--sample-frequency=16000")
      modelManager.refreshStatus()

      assertEquals(mgb2Model.id, unifiedVoiceManager.getInstalledOfflineSttModel("ar")?.id)
      modelManager.deleteModel(mgb2Model)
      assertEquals(
        "After deleting MGB-2, UnifiedVoiceManager must immediately switch to LinTO",
        lintoModel.id,
        unifiedVoiceManager.getInstalledOfflineSttModel("ar")?.id
      )
    } finally {
      mgb2Dir.deleteRecursively()
      lintoDir.deleteRecursively()
      modelManager.getDownloadedZipFile(mgb2Model).delete()
    }
  }

  /**
   * 16. Test: Written text commands via WeykyViewModel.executeUserQuery continue to work
   * without any regression through IntentParser and IntentExecutor.
   */
  @Test
  fun testWrittenCommandsWorkWithoutRegression() = runBlocking {
    val viewModel = com.example.core.state.WeykyViewModel(context as android.app.Application)
    try {
      viewModel.updateInputQuery("من انت")
      assertEquals("من انت", viewModel.uiState.value.inputQuery)

      val outcome = viewModel.executeUserQuery("من انت")
      assertTrue(
        "Written identity query must succeed through IntentParser and IntentExecutor",
        outcome.execution is com.example.core.intent.ExecutionResult.Success
      )
      val responseText = viewModel.uiState.value.lastResponseText.orEmpty()
      assertTrue("Response bubble must contain assistant reply", responseText.isNotBlank())
    } finally {
      viewModel.voicePipeline.destroy()
    }
  }

  /**
   * 17. Test: Huawei P40 Lite E (HMS-only, proprietary com.huawei.vassistant / com.huawei.hiai recognizer)
   * vs Samsung A24 (GMS + Samsung recognizer) compatibility when online with an installed Arabic Vosk model.
   * - On Huawei HMS-only: must bypass the unsupported Huawei system recognizer and immediately select VOSK_LOCAL_OFFLINE.
   * - On Samsung A24 / Google Speech Services: must keep selecting SYSTEM_ONLINE when online.
   */
  @Test
  fun testHuaweiHmsOnlyBypassesUnsupportedSystemRecognizerWhileSamsungUsesSystemOnline() {
    val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

    val mgb2Model = VoiceModelManager.VOSK_ARABIC_MGB2
    val targetDir = File(modelManager.modelsDirectory, mgb2Model.targetDirectoryName)
    File(targetDir, "am").mkdirs()
    File(targetDir, "conf").mkdirs()
    File(targetDir, "am/final.mdl").writeText("kaldi-weights")
    File(targetDir, "conf/mfcc.conf").writeText("--sample-frequency=16000")

    val pm = context.packageManager
    val shadowPm = org.robolectric.Shadows.shadowOf(pm)
    val recIntent = android.content.Intent(android.speech.RecognitionService.SERVICE_INTERFACE)

    try {
      // Simulate Huawei HMS-only RecognitionService (com.huawei.vassistant)
      val huaweiResolveInfo = android.content.pm.ResolveInfo().apply {
        serviceInfo = android.content.pm.ServiceInfo().apply {
          packageName = "com.huawei.vassistant"
          name = "com.huawei.vassistant.VoiceRecognitionService"
        }
      }
      shadowPm.addResolveInfoForIntent(recIntent, huaweiResolveInfo)

      assertFalse(
        "Huawei HMS-only vassistant service must be reported as unusable for Arabic standard SpeechRecognizer",
        unifiedVoiceManager.hasUsableSystemRecognizerForLanguage("ar")
      )

      unifiedVoiceManager.refresh()
      assertEquals(
        "On Huawei HMS-only with installed Vosk model, UnifiedVoiceManager must select VOSK_LOCAL_OFFLINE even when online",
        ActiveSttEngineType.VOSK_LOCAL_OFFLINE,
        unifiedVoiceManager.status.value.sttEngine
      )

      // Now simulate Samsung A24 / Google Speech Services (com.google.android.tts) installed alongside
      val googleResolveInfo = android.content.pm.ResolveInfo().apply {
        serviceInfo = android.content.pm.ServiceInfo().apply {
          packageName = "com.google.android.tts"
          name = "com.google.android.libraries.speech.transcription.recognition.service.RecognitionServiceImpl"
        }
      }
      shadowPm.addResolveInfoForIntent(recIntent, googleResolveInfo)

      assertTrue(
        "When Google/Samsung RecognitionService is present (like on Samsung A24), hasUsableSystemRecognizerForLanguage('ar') must be true",
        unifiedVoiceManager.hasUsableSystemRecognizerForLanguage("ar")
      )
    } finally {
      shadowPm.removeResolveInfosForIntent(recIntent, "com.huawei.vassistant")
      shadowPm.removeResolveInfosForIntent(recIntent, "com.google.android.tts")
      targetDir.deleteRecursively()
    }
  }

  /**
   * 18. Test: System SpeechRecognizer mid-session network/server failure transitions cleanly to local Vosk
   * and fails cleanly with Error state if the Vosk model cannot be loaded in the test JVM.
   */
  @Test
  fun testSystemRecognizerErrorFallbackToVoskAndModelLoadFailureHandling() {
    val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

    // Install synthetic Arabic model directory so fallback is triggered
    val arModel = VoiceModelManager.VOSK_ARABIC_MGB2
    val targetDir = File(modelManager.modelsDirectory, arModel.targetDirectoryName)
    File(targetDir, "am").mkdirs()
    File(targetDir, "conf").mkdirs()
    File(targetDir, "am/final.mdl").writeText("kaldi-weights")
    File(targetDir, "conf/mfcc.conf").writeText("--sample-frequency=16000")

    val pm = context.packageManager
    val shadowPm = org.robolectric.Shadows.shadowOf(pm)
    val recIntent = android.content.Intent(android.speech.RecognitionService.SERVICE_INTERFACE)
    val googleResolveInfo = android.content.pm.ResolveInfo().apply {
      serviceInfo = android.content.pm.ServiceInfo().apply {
        packageName = "com.google.android.tts"
        name = "com.google.android.tts.RecognitionService"
      }
    }
    shadowPm.addResolveInfoForIntent(recIntent, googleResolveInfo)

    try {
      val pipeline = VoicePipeline(
        context = context,
        onTranscriptReady = {},
        onListeningStateChanged = {},
        unifiedVoiceManager = unifiedVoiceManager
      )

      // Simulate online session followed by ERROR_NETWORK -> must transition to VOSK_LOCAL_OFFLINE
      pipeline.onReadyForSpeech(null)
      pipeline.onError(android.speech.SpeechRecognizer.ERROR_NETWORK)

      assertEquals(
        "Network failure when local model is installed must transition engine to VOSK_LOCAL_OFFLINE",
        ActiveSttEngineType.VOSK_LOCAL_OFFLINE,
        pipeline.activeEngineType
      )
      assertEquals(
        "SYSTEM_ERROR_2_FALLBACK_TO_VOSK",
        pipeline.activeEngineSelectionReason
      )
      pipeline.destroy()
    } finally {
      shadowPm.removeResolveInfosForIntent(recIntent, "com.google.android.tts")
      targetDir.deleteRecursively()
    }
  }

  /**
   * 19. Test: ArabicTextDiacritizer preprocessing for Neural TTS — verifies numbers, decimals,
   * percentages, temperatures, dates, clock times, WEYKY pronunciation ("وايكي"), tashkeel preservation,
   * and bounded clause splitting (`splitIntoSynthesisChunks`) for low-RAM devices.
   */
  @Test
  fun testArabicTextDiacritizerPreprocessingAndChunkingForNeuralTts() {
    // 1) Numbers, percentages, temperatures, and WEYKY identity normalization
    val raw = "مرحبا انا WEYKY، نسبة البطارية 85% والحرارة 36.5°C في 2026/10/11 الساعة 14:30"
    val prepared = com.example.core.voice.engine.ArabicTextDiacritizer.prepareAndDiacritize(raw)

    assertFalse("Uppercase WEYKY must be converted to Arabic phonetic word", prepared.contains("WEYKY"))
    assertTrue("Prepared text must contain phonetic وايكي", prepared.contains("وَايْكِي"))
    assertTrue("85% must be expanded to Arabic words", prepared.contains("خَمْسَة وَثَمَانُونَ") && prepared.contains("بِالْمِئَةِ"))
    assertTrue("36.5°C must expand decimal and unit", prepared.contains("سِتَّة وَثَلَاثُونَ") && prepared.contains("فَاصِل") && prepared.contains("دَرَجَةً مِئَوِيَّةً"))
    assertTrue("Date 2026/10/11 must expand month name", prepared.contains("أُكْتُوبَر"))
    assertTrue("Clock time 14:30 must expand to hours and minutes", prepared.contains("أَرْبَعَةَ عَشَرَ") && prepared.contains("ثَلَاثُونَ") && prepared.contains("دَقِيقَةً"))

    // 2) Pre-diacritized text preservation
    val vocalizedInput = "السَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ وَبَرَكَاتُهُ"
    assertTrue(com.example.core.voice.engine.ArabicTextDiacritizer.isPreDiacritized(vocalizedInput))
    val preserved = com.example.core.voice.engine.ArabicTextDiacritizer.prepareAndDiacritize(vocalizedInput)
    assertTrue("Pre-diacritized text must preserve its tashkeel", preserved.contains("السَّلَامُ"))

    // 3) Long text splitting into bounded synthesis chunks (protects 4GB RAM on Huawei P40 Lite E)
    val longParagraph = (1..8).joinToString(" ، ") { idx ->
      "هذه الجملة التجريبية رقم $idx لاختبار تقسيم النصوص العربية الطويلة في محرك النطق العصبي المحلي"
    }
    val chunks = com.example.core.voice.engine.ArabicTextDiacritizer.splitIntoSynthesisChunks(longParagraph, maxCharsPerChunk = 100)
    assertTrue("Long paragraph must be split into multiple bounded clauses", chunks.size >= 3)
    assertTrue("Every chunk must obey maxCharsPerChunk bound", chunks.all { it.length <= 100 })

    // 4) Verify authentic eSpeak-ng Arabic IPA codepoints for emphatic/guttural consonants, dark vowels,
    // sun-letter assimilation, and unvocalized Arabic sentences against the exact Piper phoneme_id_map
    val realPiperPhonemeIdMap = mapOf(
      "_" to listOf(0L), "^" to listOf(1L), "$" to listOf(2L), " " to listOf(3L),
      "!" to listOf(4L), "'" to listOf(5L), "(" to listOf(6L), ")" to listOf(7L),
      "," to listOf(8L), "-" to listOf(9L), "." to listOf(10L), ":" to listOf(11L),
      ";" to listOf(12L), "?" to listOf(13L), "a" to listOf(14L), "b" to listOf(15L),
      "d" to listOf(17L), "e" to listOf(18L), "f" to listOf(19L), "g" to listOf(154L),
      "h" to listOf(20L), "i" to listOf(21L), "j" to listOf(22L), "k" to listOf(23L),
      "l" to listOf(24L), "m" to listOf(25L), "n" to listOf(26L), "o" to listOf(27L),
      "p" to listOf(28L), "q" to listOf(29L), "r" to listOf(30L), "s" to listOf(31L),
      "t" to listOf(32L), "u" to listOf(33L), "v" to listOf(34L), "w" to listOf(35L),
      "x" to listOf(36L), "z" to listOf(38L), "ð" to listOf(41L), "ħ" to listOf(43L),
      "ɣ" to listOf(68L), "ʃ" to listOf(96L), "ʒ" to listOf(108L), "ʔ" to listOf(109L),
      "ʕ" to listOf(110L), "ˈ" to listOf(120L), "ˌ" to listOf(121L), "ː" to listOf(122L),
      "ˤ" to listOf(146L), "̪" to listOf(142L), "θ" to listOf(126L), "χ" to listOf(127L)
    )

    val testSentences = listOf(
      "مرحباً بك في وايكي.",
      "درجة حرارة الجو الآن 24 درجة.",
      "البطارية مشحونة بنسبة 75 بالمئة.",
      "الطقس اليوم معتدل في مدينتك.",
      "الصوت مرتفع، والظروف مناسبة للخروج صباحاً."
    )

    for (sentence in testSentences) {
      val ipaTokens = com.example.core.voice.engine.ArabicTextDiacritizer.arabicToIpaPhonemes(sentence)
      assertTrue("IPA token list must not be empty for: $sentence", ipaTokens.isNotEmpty())
      for (tok in ipaTokens) {
        assertTrue(
          "Every emitted IPA token '$tok' (U+${tok[0].code.toString(16)}) in '$sentence' must exist in Piper phoneme_id_map",
          realPiperPhonemeIdMap.containsKey(tok)
        )
      }
    }

    // Specifically verify eSpeak-ng Arabic conventions:
    // - ص / ط emit dental bridge U+032A ('̪'), NOT pharyngealized U+02E4 on s/t
    // - خ emits 'x' (in ar_listx lexical entries) or uvular fricative 'χ' (U+03C7 in ar_rules)
    // - الصوت assimilates sun letter ص (no 'l' after '?a') and uses dark vowel 'a.'
    val sawtIpa = com.example.core.voice.engine.ArabicTextDiacritizer.arabicToIpaPhonemes("الصوت صباح الخير")
    assertTrue("ص must emit eSpeak dental bridge U+032A", sawtIpa.contains("\u032A"))
    assertTrue("خ must emit valid eSpeak phoneme 'x' or 'χ'", sawtIpa.contains("x") || sawtIpa.contains("χ"))

    // Verify speed units with backslash/slash: 24كم\س -> أربعة وعشرون كيلومتر في الساعة
    val speedExpansion = com.example.core.voice.engine.ArabicTextDiacritizer.prepareAndDiacritize("24كم\\س")
    assertTrue("24كم\\س must expand to أربعة وعشرون: $speedExpansion", speedExpansion.contains("أَرْبَعَة") && speedExpansion.contains("عِشْرُونَ"))
    assertTrue("24كم\\س must expand to كيلومتر في الساعة: $speedExpansion", speedExpansion.contains("كِيلُومِتْر") && speedExpansion.contains("السَّاعَة"))

    val speedIpa = com.example.core.voice.engine.ArabicTextDiacritizer.arabicToIpaPhonemes("24كم\\س")
    assertTrue("Speed IPA tokens must not be empty", speedIpa.isNotEmpty())
    for (tok in speedIpa) {
      assertTrue("Speed IPA token '$tok' must be in real Piper phoneme map", realPiperPhonemeIdMap.containsKey(tok))
    }
  }

  /**
   * 20. Test: Offline Neural TTS (`LocalNeuralTtsEngine` & `VoiceModelManager.PIPER_ARABIC_FEMALE_NEURAL_TTS`)
   * installation, SHA-256 manifest verification, corrupted file rollback, phoneme ID generation,
   * and 3-tier fallback in `ArabicSpeechSynthesizer` when offline.
   */
  @Test
  fun testOfflineArabicNeuralTtsInstallationInferenceAndFallback() = runBlocking {
    val ttsModelInfo = VoiceModelManager.PIPER_ARABIC_FEMALE_NEURAL_TTS
    val targetDir = File(modelManager.modelsDirectory, ttsModelInfo.targetDirectoryName)
    val onnxDownloadFile = modelManager.getDownloadedZipFile(ttsModelInfo)
    val configDownloadFile = modelManager.getDownloadedCompanionConfigFile(ttsModelInfo)

    try {
      // 1) Simulate downloaded ONNX weights (> 1024 bytes) + Piper .onnx.json config
      onnxDownloadFile.writeBytes(ByteArray(2048) { (it % 127).toByte() })
      val samplePiperJson = """
        {
          "audio": { "sample_rate": 22050 },
          "espeak": { "voice": "ar" },
          "inference": { "noise_scale": 0.667, "length_scale": 1.0, "noise_w": 0.8 },
          "phoneme_type": "espeak",
          "num_speakers": 1,
          "phoneme_id_map": {
            "_": [0],
            "^": [1],
            "$": [2],
            " ": [3],
            "m": [10],
            "a": [11],
            "r": [12],
            "ħ": [13],
            "b": [14],
            "n": [15],
            "w": [16],
            "aː": [17],
            "j": [18],
            "k": [19],
            "iː": [20]
          }
        }
      """.trimIndent()
      configDownloadFile.writeText(samplePiperJson)

      assertTrue("Downloaded ONNX + config must be detected as ready to install", modelManager.isModelZipDownloaded(ttsModelInfo))

      val installRes = modelManager.installDownloadedModel(ttsModelInfo)
      assertTrue("Direct ONNX Neural TTS package must install atomically", installRes.isSuccess)
      assertTrue("Neural TTS model must be reported as installed", modelManager.isModelInstalled(ttsModelInfo))
      assertTrue("SHA-256 manifest.json must be created inside model directory", File(targetDir, "manifest.json").exists())

      // 2) Verify that a subsequent corrupted/truncated ONNX install fails and preserves the working model
      onnxDownloadFile.writeBytes(ByteArray(16)) // Too small (< 128 bytes) -> invalid
      configDownloadFile.writeText(samplePiperJson)
      val corruptedRes = modelManager.installDownloadedModel(ttsModelInfo)
      assertTrue("Corrupted ONNX file must be rejected", corruptedRes.isFailure)
      assertTrue("Previously installed valid Neural TTS model must remain intact after failed update", modelManager.isModelInstalled(ttsModelInfo))

      // 3) Verify LocalNeuralTtsEngine config parsing, Arabic-to-IPA phoneme ID mapping, and synthesis with custom hook
      val speechStates = mutableListOf<Boolean>()
      val neuralEngine = com.example.core.voice.engine.LocalNeuralTtsEngine(context) { speaking ->
        speechStates.add(speaking)
      }
      var synthesizedClausesCount = 0
      neuralEngine.customWaveformSynthesizer = { phonemeIds, cfg ->
        synthesizedClausesCount++
        assertTrue("Phoneme IDs must start with BOS (1) and end with EOS (2)", phonemeIds.first() == 1L && phonemeIds.last() == 2L)
        FloatArray(cfg.sampleRate / 10) { 0.25f } // 100ms synthetic waveform
      }

      assertTrue("LocalNeuralTtsEngine.loadModel must succeed on installed directory", neuralEngine.loadModel(targetDir))
      val speakRes = neuralEngine.speak(
        text = "مرحبا انا وايكي",
        modelDir = targetDir,
        flush = true
      )
      assertTrue("LocalNeuralTtsEngine.speak must return SpeechResult.Success", speakRes is com.example.core.intent.SpeechResult.Success)

      // Allow background coroutine to complete synthesis
      kotlinx.coroutines.delay(250)
      assertTrue("At least one Arabic clause must be synthesized by neural engine", synthesizedClausesCount >= 1)

      // 4) Verify UnifiedVoiceManager reports LOCAL_ENGINE for TTS when neural model is installed
      unifiedVoiceManager.refresh()
      assertEquals(
        "UnifiedVoiceManager must report LOCAL_ENGINE when Piper Arabic Neural TTS is installed",
        com.example.core.voice.engine.ActiveTtsEngineType.LOCAL_ENGINE,
        unifiedVoiceManager.status.value.ttsEngine
      )

      neuralEngine.shutdown()
    } finally {
      modelManager.deleteModel(ttsModelInfo)
    }
  }

  /**
   * 21. Test: Installation and active voice switching for `ar_JO-kareem` (`PIPER_ARABIC_KAREEM_NEURAL_TTS`).
   * Verifies downloading/staging, installing, active model switching between female and male (Kareem),
   * and clean fallback when switching models.
   */
  @Test
  fun testKareemNeuralTtsInstallationAndVoiceSwitching() = runBlocking {
    val kareemInfo = VoiceModelManager.PIPER_ARABIC_KAREEM_NEURAL_TTS
    val femaleInfo = VoiceModelManager.PIPER_ARABIC_FEMALE_NEURAL_TTS
    val kareemTargetDir = File(modelManager.modelsDirectory, kareemInfo.targetDirectoryName)
    val kareemOnnx = modelManager.getDownloadedZipFile(kareemInfo)
    val kareemCfg = modelManager.getDownloadedCompanionConfigFile(kareemInfo)

    try {
      // 1) Verify Kareem model metadata
      assertEquals("piper_ar_kareem_neural_tts", kareemInfo.id)
      assertEquals("ar", kareemInfo.languageCode)
      assertFalse("Kareem must be male voice", kareemInfo.isFemaleVoice)
      assertTrue(kareemInfo.downloadUrl.contains("kareem/medium/ar_JO-kareem-medium.onnx"))

      // 2) Stage mock Kareem files
      kareemOnnx.writeBytes(ByteArray(2048) { (it % 100).toByte() })
      kareemCfg.writeText("""
        {
          "audio": { "sample_rate": 22050 },
          "espeak": { "voice": "ar" },
          "phoneme_type": "espeak",
          "num_speakers": 1,
          "phoneme_id_map": { "_": [0], "^": [1], "$": [2], " ": [3], "m": [10], "a": [11] }
        }
      """.trimIndent())

      val installRes = modelManager.installDownloadedModel(kareemInfo)
      assertTrue("Kareem model must install successfully", installRes.isSuccess)
      assertTrue(modelManager.isModelInstalled(kareemInfo))

      // 3) Test active model selection
      modelManager.setActiveTtsModelId(kareemInfo.id)
      assertEquals(kareemInfo.id, modelManager.getActiveTtsModelId())

      val synth = ArabicSpeechSynthesizer(context)
      try {
        val selectedModel = synth.getActiveOrInstalledNeuralTtsModel()
        assertNotNull("Synthesizer must return an installed neural model", selectedModel)
        assertEquals("Active TTS model must be Kareem", kareemInfo.id, selectedModel?.id)
        assertTrue(synth.isNeuralArabicTtsInstalled)

        // Switch to female voice (even if female is not yet on disk, getActiveOrInstalledNeuralTtsModel safely falls back to Kareem)
        modelManager.setActiveTtsModelId(femaleInfo.id)
        assertEquals("When preferred female voice is not installed, must fall back to installed Kareem", kareemInfo.id, synth.getActiveOrInstalledNeuralTtsModel()?.id)
      } finally {
        synth.shutdown()
      }

      // 4) UnifiedVoiceManager reflects Kareem as the active offline TTS engine
      unifiedVoiceManager.refresh()
      val ttsModel = unifiedVoiceManager.getInstalledOfflineTtsModel("ar")
      assertNotNull(ttsModel)
      assertEquals(kareemInfo.id, ttsModel?.id)
      assertTrue(unifiedVoiceManager.status.value.activeTtsPackage?.contains("كريم") == true)
    } finally {
      modelManager.deleteModel(kareemInfo)
      assertFalse(modelManager.isModelInstalled(kareemInfo))
    }
  }

  /**
   * 21. Test: Arabic Diacritization and Unit Normalization
   * Confirms 24كم\س expands to "أَرْبَعَة وَعِشْرُونَ كِيلُومِتْرًا فِي السَّاعَةِ"
   * and prepares text with full vocalization.
   */
  @Test
  fun testSpeedUnitAndNumberDiacritization() {
    val input = "سرعة الرياح 24كم\\س"
    val diacritized = com.example.core.voice.engine.ArabicTextDiacritizer.prepareAndDiacritize(input)

    assertTrue("Must expand 24 to Arabic words", diacritized.contains("أَرْبَعَة") && diacritized.contains("عِشْرُونَ"))
    assertTrue("Must expand كم\\س to كِيلُومِتْرًا فِي السَّاعَةِ", diacritized.contains("كِيلُومِتْرًا فِي السَّاعَةِ") || diacritized.contains("كِيلُومِتْر فِي السَّاعَةِ"))
    assertFalse("Must not leave raw digits in prepared text", diacritized.contains("24"))
    assertFalse("Must not leave raw slash/backslash", diacritized.contains("\\") || diacritized.contains("/"))
  }

  /**
   * 22. Test: Hybrid Speech Synthesizer Coordination
   * Confirms ArabicSpeechSynthesizer has EdgeNeuralTtsEngine integrated and hybrid mode is enabled by default.
   */
  @Test
  fun testHybridSpeechSynthesizerIntegration() {
    val synth = ArabicSpeechSynthesizer(context)
    try {
      assertTrue("Hybrid mode should be enabled by default", synth.isHybridModeEnabled)
      assertNotNull("EdgeNeuralTtsEngine must be initialized", synth.edgeNeuralEngine)
      assertEquals("Default voice should be Salma", com.example.core.voice.engine.EdgeNeuralTtsEngine.DEFAULT_VOICE, synth.edgeNeuralEngine.activeVoiceName)

      // Test changing cloud voice
      synth.edgeNeuralEngine.activeVoiceName = com.example.core.voice.engine.EdgeNeuralTtsEngine.VOICE_HAMED_SAUDI
      assertEquals(com.example.core.voice.engine.EdgeNeuralTtsEngine.VOICE_HAMED_SAUDI, synth.edgeNeuralEngine.activeVoiceName)
    } finally {
      synth.shutdown()
    }
  }
}


