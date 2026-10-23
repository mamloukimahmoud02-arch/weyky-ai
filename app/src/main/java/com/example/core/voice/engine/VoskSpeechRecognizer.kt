package com.example.core.voice.engine

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

sealed interface OfflineRecognitionState {
  object Uninitialized : OfflineRecognitionState
  object LoadingModel : OfflineRecognitionState
  object Ready : OfflineRecognitionState
  object Listening : OfflineRecognitionState
  data class Partial(val text: String) : OfflineRecognitionState
  data class Result(val text: String) : OfflineRecognitionState
  data class Error(val message: String) : OfflineRecognitionState
}

/**
 * Embedded On-Device Speech Recognizer powered by Vosk Engine.
 * Runs completely locally on Android without Google Play Services or network.
 * Uses AudioSource.VOICE_RECOGNITION directly without requesting AudioFocus,
 * preserving external media playback while evaluating hardware audio effects safely.
 */
class VoskSpeechRecognizer(
  private val context: Context,
  private val onTranscriptReady: (String) -> Unit,
  private val onPartialTranscript: (String) -> Unit = {},
  private val onListeningStateChanged: (Boolean) -> Unit = {},
  private val onVoiceRmsChanged: (Float) -> Unit = {}
) : RecognitionListener {

  companion object {
    private const val TAG = "WEYKY_VOSK_STT"
    private const val SAMPLE_RATE = 16000.0f
    private const val SAMPLE_RATE_HZ = 16000
    private const val BUFFER_SIZE_SECONDS = 0.2f
    private const val NON_SILENT_RMS_THRESHOLD = 0.0045f
    private const val ZERO_SIGNAL_RECOVERY_FRAMES = 6
  }

  data class AudioCaptureDiagnostics(
    val voskSessionId: Long,
    val audioSourceUsed: Int,
    val audioSourceName: String,
    val sampleRateHz: Int,
    val channelConfig: Int,
    val audioEncoding: Int,
    val bufferSizeBytes: Int,
    val firstSampleLatencyMs: Long,
    val firstNonSilentSignalLatencyMs: Long,
    val totalFramesRead: Int,
    val totalSamplesRead: Long,
    val nonSilentFramesCount: Int,
    val zeroSignalFramesCount: Int,
    val peakRmsObserved: Float,
    val hardwareEffectsDisabledDueToZeroSignal: Boolean,
    val switchedToMicSourceFallback: Boolean,
    val terminationReason: String
  )

  private val mainHandler = Handler(Looper.getMainLooper())
  private val sessionLock = Any()

  private var model: Model? = null
  @Volatile
  var loadedModelPath: String? = null
    private set

  @Volatile
  var lastCaptureDiagnostics: AudioCaptureDiagnostics? = null
    private set

  private var activeAudioRecord: AudioRecord? = null
  private var activeRecognizer: Recognizer? = null
  private var captureThread: Thread? = null

  private var echoCanceler: AcousticEchoCanceler? = null
  private var noiseSuppressor: NoiseSuppressor? = null
  private var autoGainControl: AutomaticGainControl? = null

  @Volatile
  var isListeningActive: Boolean = false
    private set

  @Volatile
  private var activeVoskSessionId: Long = 0L

  @Volatile
  private var hasDeliveredFinalForVoskSession: Boolean = false

  @Volatile
  private var latestPartialTextForSession: String = ""

  @Volatile
  private var sessionTapTimestampMs: Long = 0L

  private val _state = MutableStateFlow<OfflineRecognitionState>(OfflineRecognitionState.Uninitialized)
  val state: StateFlow<OfflineRecognitionState> = _state.asStateFlow()

  val isModelLoaded: Boolean
    get() = model != null

  /**
   * Checks whether the requested [modelDir] (or its resolved nested root) is already loaded in memory.
   */
  fun isLoadedForDirectory(modelDir: File): Boolean {
    synchronized(sessionLock) {
      if (model == null || loadedModelPath == null) return false
      val resolved = resolveNestedVoskModelDir(modelDir)
      return loadedModelPath == resolved.absolutePath || loadedModelPath == modelDir.absolutePath
    }
  }

  private fun resolveNestedVoskModelDir(baseDir: File, maxDepth: Int = 2): File {
    if (!baseDir.exists() || !baseDir.isDirectory) return baseDir
    val hasDirectArtifacts = File(baseDir, "am").isDirectory ||
      File(baseDir, "conf").isDirectory ||
      File(baseDir, "final.mdl").isFile ||
      File(baseDir, "model.bin").isFile
    if (hasDirectArtifacts || maxDepth <= 0) return baseDir

    val subDirs = baseDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && !f.name.startsWith("__MACOSX") }
    if (subDirs != null && subDirs.size == 1) {
      return resolveNestedVoskModelDir(subDirs[0], maxDepth - 1)
    }
    return baseDir
  }

  fun loadModel(modelDir: File): Boolean {
    synchronized(sessionLock) {
      val effectiveDir = resolveNestedVoskModelDir(modelDir)
      if (!effectiveDir.exists() || !effectiveDir.isDirectory) {
        Log.w(TAG, "Cannot load Vosk model: directory does not exist: ${effectiveDir.absolutePath}")
        _state.value = OfflineRecognitionState.Error("مجلد النموذج الصوتي Vosk غير موجود.")
        return false
      }

      if (loadedModelPath == effectiveDir.absolutePath && model != null) {
        Log.d(TAG, "Vosk model already loaded for target directory")
        _state.value = OfflineRecognitionState.Ready
        return true
      }

      // Always stop any active recording and close the old Recognizer BEFORE closing the old Model
      if (isListeningActive || activeAudioRecord != null || activeRecognizer != null) {
        releaseRecordingResourcesLocked(notifyStop = false, flushFinalResult = false)
      }

      val startMs = System.currentTimeMillis()
      return try {
        _state.value = OfflineRecognitionState.LoadingModel
        Log.i(TAG, "Loading Vosk Model from ${effectiveDir.name}...")
        val oldModel = model
        model = null
        loadedModelPath = null
        try {
          oldModel?.close()
        } catch (_: Exception) {}

        model = Model(effectiveDir.absolutePath)
        loadedModelPath = effectiveDir.absolutePath
        _state.value = OfflineRecognitionState.Ready
        val elapsedMs = System.currentTimeMillis() - startMs
        Log.i(TAG, "Vosk Model [${effectiveDir.name}] initialized in ${elapsedMs}ms")
        true
      } catch (t: Throwable) {
        Log.e(TAG, "Failed to load Vosk model from ${effectiveDir.name}: ${t.message}")
        model = null
        loadedModelPath = null
        _state.value = OfflineRecognitionState.Error("فشل تحميل نموذج Vosk: ${t.localizedMessage ?: t.javaClass.simpleName}")
        false
      }
    }
  }

  private fun audioSourceToName(source: Int): String {
    return when (source) {
      MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
      MediaRecorder.AudioSource.MIC -> "MIC"
      MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
      else -> "SOURCE_$source"
    }
  }

  private fun isHuaweiOrHonorDevice(): Boolean {
    val manufacturer = android.os.Build.MANUFACTURER.orEmpty()
    val brand = android.os.Build.BRAND.orEmpty()
    return manufacturer.equals("HUAWEI", ignoreCase = true) ||
      brand.equals("HUAWEI", ignoreCase = true) ||
      brand.equals("HONOR", ignoreCase = true)
  }

  private fun createInitializedAudioRecord(
    preferredSources: List<Int>,
    bufferSizeInBytes: Int
  ): Pair<AudioRecord, Int> {
    var lastError: String = "Unknown AudioRecord init error"
    for (source in preferredSources) {
      try {
        val candidate = AudioRecord(
          source,
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSizeInBytes
        )
        if (candidate.state == AudioRecord.STATE_INITIALIZED) {
          Log.i(
            TAG,
            "[AUDIO-DIAG] AudioRecord initialized: source=${audioSourceToName(source)}($source), " +
              "sampleRate=${candidate.sampleRate}Hz, format=PCM_16BIT_MONO, bufferBytes=$bufferSizeInBytes"
          )
          return candidate to source
        } else {
          lastError = "AudioRecord(${audioSourceToName(source)}) state=${candidate.state}"
          Log.w(TAG, "[AUDIO-DIAG] $lastError; releasing and trying fallback source")
          try {
            candidate.release()
          } catch (_: Exception) {}
        }
      } catch (t: Throwable) {
        lastError = "AudioRecord(${audioSourceToName(source)}) threw ${t.javaClass.simpleName}: ${t.message}"
        Log.w(TAG, "[AUDIO-DIAG] $lastError")
      }
    }
    throw IOException("Failed to initialize AudioRecord ($lastError). Microphone may be in use.")
  }

  fun startListening(tapTimestampMs: Long = System.currentTimeMillis()): Boolean {
    synchronized(sessionLock) {
      val currentModel = model
      if (currentModel == null) {
        Log.w(TAG, "[AUDIO-DIAG] Cannot start Vosk listening: model not loaded")
        _state.value = OfflineRecognitionState.Error("النموذج الصوتي غير محمل حالياً.")
        return false
      }

      if (isListeningActive) {
        Log.i(TAG, "[AUDIO-DIAG] Existing Vosk session active; releasing before starting new session")
        releaseRecordingResourcesLocked(notifyStop = false, flushFinalResult = false)
      }

      return try {
        sessionTapTimestampMs = tapTimestampMs
        val rawMinBufSize = AudioRecord.getMinBufferSize(
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        )
        val safeMinBufSize = if (rawMinBufSize > 0) rawMinBufSize else SAMPLE_RATE_HZ * 2
        val targetBufferSamples = (SAMPLE_RATE_HZ * BUFFER_SIZE_SECONDS).roundToInt()
        val bufferSizeInBytes = maxOf(safeMinBufSize, targetBufferSamples * 2)

        // Try VOICE_RECOGNITION first, and automatically fall back to MIC if the OEM HAL rejects VOICE_RECOGNITION
        val (record, chosenSource) = createInitializedAudioRecord(
          preferredSources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
          ),
          bufferSizeInBytes = bufferSizeInBytes
        )

        // On Huawei Kirin 710F / EMUI 10 (e.g. P40 Lite E), attaching hardware NoiseSuppressor + AGC
        // on VOICE_RECOGNITION can over-attenuate or zero out PCM frames. Skip NS/AGC on Huawei by default.
        val enableNsAndAgc = !isHuaweiOrHonorDevice()
        attachHardwareAudioEffectsSafely(record.audioSessionId, enableNsAndAgc = enableNsAndAgc)

        val recognizer = Recognizer(currentModel, SAMPLE_RATE)
        val sessionId = ++activeVoskSessionId
        hasDeliveredFinalForVoskSession = false
        latestPartialTextForSession = ""
        activeAudioRecord = record
        activeRecognizer = recognizer
        isListeningActive = true

        val thread = Thread({
          runAudioCaptureLoop(
            sessionId = sessionId,
            record = record,
            initialAudioSource = chosenSource,
            recognizer = recognizer,
            bufferSamples = targetBufferSamples,
            bufferSizeInBytes = bufferSizeInBytes,
            tapStartMs = tapTimestampMs
          )
        }, "WeykyVoskCaptureThread-$sessionId")
        captureThread = thread
        thread.start()

        _state.value = OfflineRecognitionState.Listening
        onListeningStateChanged(true)
        val startupLatencyMs = (System.currentTimeMillis() - tapTimestampMs).coerceAtLeast(0L)
        Log.i(
          TAG,
          "[AUDIO-DIAG] Vosk AudioRecord started (voskSessionId=$sessionId, source=${audioSourceToName(chosenSource)}, " +
            "sampleRate=${SAMPLE_RATE_HZ}Hz, encoding=PCM_16BIT, bufferBytes=$bufferSizeInBytes, " +
            "audioSessionId=${record.audioSessionId}, tapToStartMs=${startupLatencyMs}ms, modelPath=$loadedModelPath)"
        )
        true
      } catch (e: IOException) {
        Log.e(TAG, "[AUDIO-DIAG] IOException starting Vosk AudioRecord", e)
        releaseRecordingResourcesLocked(notifyStop = false, flushFinalResult = false)
        _state.value = OfflineRecognitionState.Error("خطأ في تشغيل الميكروفون لـ Vosk: ${e.message}")
        onListeningStateChanged(false)
        false
      } catch (t: Throwable) {
        Log.e(TAG, "[AUDIO-DIAG] Exception starting Vosk AudioRecord", t)
        releaseRecordingResourcesLocked(notifyStop = false, flushFinalResult = false)
        _state.value = OfflineRecognitionState.Error("خطأ في خدمة الصوت المحلية: ${t.localizedMessage ?: t.javaClass.simpleName}")
        onListeningStateChanged(false)
        false
      }
    }
  }

  private fun attachHardwareAudioEffectsSafely(audioSessionId: Int, enableNsAndAgc: Boolean = true) {
    releaseAudioEffectsLocked()
    if (audioSessionId <= 0) {
      Log.d(TAG, "[AUDIO-DIAG] Invalid audioSessionId=$audioSessionId for hardware effects")
      return
    }

    // 1. AcousticEchoCanceler (helps attenuate speaker playback bleed when supported by OEM DSP)
    try {
      val aecSupported = AcousticEchoCanceler.isAvailable()
      if (aecSupported) {
        echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
          enabled = true
        }
        Log.d(TAG, "[AUDIO-DIAG] AcousticEchoCanceler supported=true, created=${echoCanceler != null}, enabled=${echoCanceler?.enabled == true}")
      } else {
        Log.d(TAG, "[AUDIO-DIAG] AcousticEchoCanceler supported=false on this device")
      }
    } catch (e: Exception) {
      Log.w(TAG, "[AUDIO-DIAG] AcousticEchoCanceler initialization failed safely: ${e.message}")
    }

    if (!enableNsAndAgc) {
      Log.i(TAG, "[AUDIO-DIAG] Skipping hardware NoiseSuppressor & AutomaticGainControl on Huawei/Honor HAL to prevent PCM signal muting")
      return
    }

    // 2. NoiseSuppressor
    try {
      val nsSupported = NoiseSuppressor.isAvailable()
      if (nsSupported) {
        noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
          enabled = true
        }
        Log.d(TAG, "[AUDIO-DIAG] NoiseSuppressor supported=true, created=${noiseSuppressor != null}, enabled=${noiseSuppressor?.enabled == true}")
      } else {
        Log.d(TAG, "[AUDIO-DIAG] NoiseSuppressor supported=false on this device")
      }
    } catch (e: Exception) {
      Log.w(TAG, "[AUDIO-DIAG] NoiseSuppressor initialization failed safely: ${e.message}")
    }

    // 3. AutomaticGainControl
    try {
      val agcSupported = AutomaticGainControl.isAvailable()
      if (agcSupported) {
        autoGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
          enabled = true
        }
        Log.d(TAG, "[AUDIO-DIAG] AutomaticGainControl supported=true, created=${autoGainControl != null}, enabled=${autoGainControl?.enabled == true}")
      } else {
        Log.d(TAG, "[AUDIO-DIAG] AutomaticGainControl supported=false on this device")
      }
    } catch (e: Exception) {
      Log.w(TAG, "[AUDIO-DIAG] AutomaticGainControl initialization failed safely: ${e.message}")
    }
  }

  private fun runAudioCaptureLoop(
    sessionId: Long,
    record: AudioRecord,
    initialAudioSource: Int,
    recognizer: Recognizer,
    bufferSamples: Int,
    bufferSizeInBytes: Int,
    tapStartMs: Long
  ) {
    val buffer = ShortArray(bufferSamples.coerceAtLeast(1600))
    var currentRecord = record
    var currentAudioSource = initialAudioSource
    var firstSampleLatencyMs = -1L
    var firstNonSilentLatencyMs = -1L
    var totalFramesRead = 0
    var totalSamplesRead = 0L
    var nonSilentFramesCount = 0
    var zeroSignalFramesCount = 0
    var consecutiveAllZeroFrames = 0
    var peakRmsObserved = 0f
    var effectsDisabledMidStream = false
    var switchedToMicMidStream = false
    var terminationReason = "NORMAL_STOP"

    try {
      currentRecord.startRecording()
      if (currentRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
        // Fallback: if VOICE_RECOGNITION failed to enter RECORDSTATE_RECORDING, try AudioSource.MIC once
        if (currentAudioSource != MediaRecorder.AudioSource.MIC) {
          Log.w(TAG, "[AUDIO-DIAG] startRecording() did not enter RECORDSTATE_RECORDING on ${audioSourceToName(currentAudioSource)}; retrying with MIC")
          try {
            currentRecord.release()
          } catch (_: Exception) {}
          val (micRecord, micSource) = createInitializedAudioRecord(
            preferredSources = listOf(MediaRecorder.AudioSource.MIC),
            bufferSizeInBytes = bufferSizeInBytes
          )
          synchronized(sessionLock) {
            if (sessionId == activeVoskSessionId && isListeningActive) {
              activeAudioRecord = micRecord
            }
          }
          currentRecord = micRecord
          currentAudioSource = micSource
          switchedToMicMidStream = true
          currentRecord.startRecording()
        }

        if (currentRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
          terminationReason = "RECORDSTATE_NOT_RECORDING"
          mainHandler.post {
            if (sessionId == activeVoskSessionId) {
              onError(IOException("AudioRecord failed to enter RECORDSTATE_RECORDING (source=${audioSourceToName(currentAudioSource)})"))
            }
          }
          return
        }
      }

      while (isListeningActive && sessionId == activeVoskSessionId && !Thread.currentThread().isInterrupted) {
        val nread = currentRecord.read(buffer, 0, buffer.size)
        if (!isListeningActive || sessionId != activeVoskSessionId || Thread.currentThread().isInterrupted) {
          terminationReason = "SESSION_STOPPED_OR_INTERRUPTED"
          break
        }
        if (nread < 0) {
          terminationReason = "READ_ERROR_$nread"
          throw IOException("AudioRecord.read returned error code $nread")
        }
        if (nread == 0) continue

        totalFramesRead++
        totalSamplesRead += nread
        if (firstSampleLatencyMs < 0L) {
          firstSampleLatencyMs = (System.currentTimeMillis() - tapStartMs).coerceAtLeast(0L)
          Log.i(
            TAG,
            "[AUDIO-DIAG] First audio samples received (voskSessionId=$sessionId, tapToFirstSampleMs=${firstSampleLatencyMs}ms, " +
              "samplesRead=$nread, source=${audioSourceToName(currentAudioSource)}, sampleRate=${SAMPLE_RATE_HZ}Hz)"
          )
        }

        // Compute real-time RMS amplitude & check whether frame is completely zeroed out by OEM HAL/DSP
        var sumSq = 0.0
        var maxAbsSample = 0
        for (i in 0 until nread) {
          val rawVal = buffer[i].toInt()
          val absVal = kotlin.math.abs(rawVal)
          if (absVal > maxAbsSample) maxAbsSample = absVal
          val sample = rawVal.toDouble() / Short.MAX_VALUE
          sumSq += sample * sample
        }
        val rms = kotlin.math.sqrt(sumSq / nread).toFloat()
        if (rms > peakRmsObserved) peakRmsObserved = rms

        val isAllZeroFrame = maxAbsSample == 0
        if (isAllZeroFrame) {
          zeroSignalFramesCount++
          consecutiveAllZeroFrames++
        } else {
          consecutiveAllZeroFrames = 0
        }

        if (rms >= NON_SILENT_RMS_THRESHOLD) {
          nonSilentFramesCount++
          if (firstNonSilentLatencyMs < 0L) {
            firstNonSilentLatencyMs = (System.currentTimeMillis() - tapStartMs).coerceAtLeast(0L)
            Log.i(
              TAG,
              "[AUDIO-DIAG] First non-silent acoustic signal detected (voskSessionId=$sessionId, " +
                "tapToNonSilentMs=${firstNonSilentLatencyMs}ms, rms=${"%.4f".format(rms)}, maxPcm=$maxAbsSample)"
            )
          }
        }

        // Self-healing guard for OEM HALs (like Kirin/EMUI) that return pure 0 PCM frames when hardware effects
        // or VOICE_RECOGNITION source are blocked:
        if (consecutiveAllZeroFrames == ZERO_SIGNAL_RECOVERY_FRAMES && !effectsDisabledMidStream) {
          effectsDisabledMidStream = true
          synchronized(sessionLock) {
            releaseAudioEffectsLocked()
          }
          Log.w(
            TAG,
            "[AUDIO-DIAG] Detected $consecutiveAllZeroFrames consecutive zero-PCM frames; " +
              "released hardware AudioEffects (AEC/NS/AGC) to recover microphone signal"
          )
        } else if (
          consecutiveAllZeroFrames == ZERO_SIGNAL_RECOVERY_FRAMES * 2 &&
          !switchedToMicMidStream &&
          currentAudioSource != MediaRecorder.AudioSource.MIC
        ) {
          switchedToMicMidStream = true
          Log.w(
            TAG,
            "[AUDIO-DIAG] Still receiving pure zero-PCM frames on VOICE_RECOGNITION; " +
              "hot-swapping AudioRecord source to AudioSource.MIC"
          )
          try {
            currentRecord.stop()
          } catch (_: Exception) {}
          try {
            currentRecord.release()
          } catch (_: Exception) {}
          val (micRecord, micSource) = createInitializedAudioRecord(
            preferredSources = listOf(MediaRecorder.AudioSource.MIC),
            bufferSizeInBytes = bufferSizeInBytes
          )
          synchronized(sessionLock) {
            if (sessionId == activeVoskSessionId && isListeningActive) {
              activeAudioRecord = micRecord
            }
          }
          currentRecord = micRecord
          currentAudioSource = micSource
          currentRecord.startRecording()
          consecutiveAllZeroFrames = 0
        }

        val normalizedEnergy = (rms * 4.2f).coerceIn(0f, 1f)
        mainHandler.post {
          if (isListeningActive && sessionId == activeVoskSessionId) {
            onVoiceRmsChanged(normalizedEnergy)
          }
        }

        val isEndpoint = recognizer.acceptWaveForm(buffer, nread)
        if (isEndpoint) {
          val resultJson = recognizer.result
          val text = extractTextFromJson(resultJson, "text")
          if (text.isNotBlank()) {
            terminationReason = "ENDPOINT_UTTERANCE_DETECTED"
            deliverFinalTextOnce(sessionId, text, source = "endpoint_result")
            break
          }
        } else {
          val partialJson = recognizer.partialResult
          val partialText = extractTextFromJson(partialJson, "partial")
          if (partialText.isNotBlank()) {
            latestPartialTextForSession = partialText
            mainHandler.post {
              if (sessionId == activeVoskSessionId && !hasDeliveredFinalForVoskSession) {
                onPartialResult(partialJson)
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      terminationReason = "EXCEPTION_${e.javaClass.simpleName}"
      Log.e(TAG, "[AUDIO-DIAG] Capture loop error (voskSessionId=$sessionId): ${e.message}", e)
      if (isListeningActive && sessionId == activeVoskSessionId) {
        mainHandler.post {
          if (sessionId == activeVoskSessionId) {
            onError(e)
          }
        }
      }
    } finally {
      try {
        if (currentRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          currentRecord.stop()
        }
      } catch (_: Exception) {}

      val diag = AudioCaptureDiagnostics(
        voskSessionId = sessionId,
        audioSourceUsed = currentAudioSource,
        audioSourceName = audioSourceToName(currentAudioSource),
        sampleRateHz = SAMPLE_RATE_HZ,
        channelConfig = AudioFormat.CHANNEL_IN_MONO,
        audioEncoding = AudioFormat.ENCODING_PCM_16BIT,
        bufferSizeBytes = bufferSizeInBytes,
        firstSampleLatencyMs = firstSampleLatencyMs,
        firstNonSilentSignalLatencyMs = firstNonSilentLatencyMs,
        totalFramesRead = totalFramesRead,
        totalSamplesRead = totalSamplesRead,
        nonSilentFramesCount = nonSilentFramesCount,
        zeroSignalFramesCount = zeroSignalFramesCount,
        peakRmsObserved = peakRmsObserved,
        hardwareEffectsDisabledDueToZeroSignal = effectsDisabledMidStream,
        switchedToMicSourceFallback = switchedToMicMidStream,
        terminationReason = terminationReason
      )
      lastCaptureDiagnostics = diag
      Log.i(
        TAG,
        "[AUDIO-DIAG] Capture summary (voskSessionId=$sessionId): source=${diag.audioSourceName}, " +
          "frames=${diag.totalFramesRead}, samples=${diag.totalSamplesRead}, nonSilentFrames=${diag.nonSilentFramesCount}, " +
          "zeroFrames=${diag.zeroSignalFramesCount}, peakRms=${"%.4f".format(diag.peakRmsObserved)}, " +
          "firstSampleMs=${diag.firstSampleLatencyMs}, firstNonSilentMs=${diag.firstNonSilentSignalLatencyMs}, " +
          "switchedToMic=${diag.switchedToMicSourceFallback}, reason=${diag.terminationReason}"
      )
    }
  }

  /**
   * Stops audio capture cleanly and extracts any remaining final speech hypothesis from Vosk
   * BEFORE closing the Recognizer. Used on manual mic stop and post-speech silence completion.
   * Returns true if a non-blank final transcript was extracted and scheduled for delivery.
   */
  fun stopListening(): Boolean {
    synchronized(sessionLock) {
      if (!isListeningActive && activeAudioRecord == null && activeRecognizer == null) return false
      Log.d(TAG, "[AUDIO-DIAG] Vosk stopListening() invoked (voskSessionId=$activeVoskSessionId); flushing final hypothesis before release")
      return releaseRecordingResourcesLocked(notifyStop = true, flushFinalResult = true)
    }
  }

  /**
   * Explicitly cancels the current Vosk session without delivering any buffered transcript.
   */
  fun cancel() {
    synchronized(sessionLock) {
      activeVoskSessionId++
      hasDeliveredFinalForVoskSession = true
      latestPartialTextForSession = ""
      if (!isListeningActive && activeAudioRecord == null && activeRecognizer == null) return
      Log.d(TAG, "[AUDIO-DIAG] Vosk cancel() invoked; releasing AudioRecord & Recognizer without transcript")
      releaseRecordingResourcesLocked(notifyStop = true, flushFinalResult = false)
    }
  }

  private fun releaseRecordingResourcesLocked(
    notifyStop: Boolean,
    flushFinalResult: Boolean
  ): Boolean {
    val sessionIdAtRelease = activeVoskSessionId
    isListeningActive = false

    // 1. Stop AudioRecord first so blocking read() in captureThread unblocks immediately
    val record = activeAudioRecord
    if (record != null) {
      try {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          record.stop()
        }
      } catch (_: Exception) {}
    }

    // 2. Wait for captureThread to finish its current acceptWaveForm iteration before touching activeRecognizer
    val thread = captureThread
    captureThread = null
    if (thread != null && thread != Thread.currentThread()) {
      thread.interrupt()
      try {
        thread.join(400)
      } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }

    // 3. Now that captureThread is no longer calling acceptWaveForm, safely extract finalResult if requested
    var flushedText: String = ""
    val rec = activeRecognizer
    activeRecognizer = null
    if (rec != null) {
      if (flushFinalResult && !hasDeliveredFinalForVoskSession) {
        try {
          val finalJson = rec.finalResult
          val extracted = extractTextFromJson(finalJson, "text")
          flushedText = extracted.ifBlank { latestPartialTextForSession.trim() }
        } catch (e: Exception) {
          Log.w(TAG, "[AUDIO-DIAG] Exception reading Vosk finalResult prior to close: ${e.message}")
          flushedText = latestPartialTextForSession.trim()
        }
      }
      // 4. Close Recognizer before Model is ever closed
      try {
        rec.close()
      } catch (_: Exception) {}
    }

    // 5. Release hardware effects and AudioRecord
    releaseAudioEffectsLocked()
    activeAudioRecord = null
    if (record != null) {
      try {
        record.release()
      } catch (_: Exception) {}
      Log.d(TAG, "[AUDIO-DIAG] Vosk AudioRecord released")
    }

    latestPartialTextForSession = ""

    val deliveredNow = if (flushFinalResult && flushedText.isNotBlank() && !hasDeliveredFinalForVoskSession) {
      deliverFinalTextOnce(sessionIdAtRelease, flushedText, source = "flush_final_result")
    } else {
      false
    }

    if (_state.value is OfflineRecognitionState.Listening) {
      _state.value = if (model != null) OfflineRecognitionState.Ready else OfflineRecognitionState.Uninitialized
    }
    if (notifyStop) {
      mainHandler.post {
        onVoiceRmsChanged(0f)
        onListeningStateChanged(false)
      }
    }
    return deliveredNow
  }

  private fun deliverFinalTextOnce(sessionId: Long, rawText: String, source: String): Boolean {
    val clean = rawText.trim()
    if (clean.isBlank()) return false
    synchronized(sessionLock) {
      if (sessionId != activeVoskSessionId || hasDeliveredFinalForVoskSession) {
        return false
      }
      hasDeliveredFinalForVoskSession = true
      isListeningActive = false
    }
    Log.i(TAG, "[AUDIO-DIAG] Vosk final transcript ready (voskSessionId=$sessionId, source=$source, chars=${clean.length})")
    mainHandler.post {
      _state.value = OfflineRecognitionState.Result(clean)
      onTranscriptReady(clean)
    }
    return true
  }

  private fun releaseAudioEffectsLocked() {
    try {
      echoCanceler?.release()
    } catch (_: Exception) {}
    echoCanceler = null

    try {
      noiseSuppressor?.release()
    } catch (_: Exception) {}
    noiseSuppressor = null

    try {
      autoGainControl?.release()
    } catch (_: Exception) {}
    autoGainControl = null
  }

  fun destroy() {
    synchronized(sessionLock) {
      activeVoskSessionId++
      hasDeliveredFinalForVoskSession = true
      releaseRecordingResourcesLocked(notifyStop = true, flushFinalResult = false)
      try {
        model?.close()
      } catch (_: Exception) {}
      model = null
      loadedModelPath = null
      _state.value = OfflineRecognitionState.Uninitialized
      Log.d(TAG, "[AUDIO-DIAG] VoskSpeechRecognizer destroyed")
    }
  }

  // --- Vosk RecognitionListener callbacks ---

  override fun onPartialResult(hypothesis: String?) {
    if (hypothesis.isNullOrBlank()) return
    val text = extractTextFromJson(hypothesis, "partial")
    if (text.isNotBlank()) {
      latestPartialTextForSession = text
      Log.d(TAG, "[AUDIO-DIAG] Vosk partial received (chars=${text.length})")
      _state.value = OfflineRecognitionState.Partial(text)
      onPartialTranscript(text)
    }
  }

  override fun onResult(hypothesis: String?) {
    if (hypothesis.isNullOrBlank()) return
    val text = extractTextFromJson(hypothesis, "text")
    if (text.isNotBlank()) {
      deliverFinalTextOnce(activeVoskSessionId, text, source = "onResult")
    }
  }

  override fun onFinalResult(hypothesis: String?) {
    if (hypothesis.isNullOrBlank()) return
    val text = extractTextFromJson(hypothesis, "text")
    if (text.isNotBlank()) {
      deliverFinalTextOnce(activeVoskSessionId, text, source = "onFinalResult")
    }
  }

  override fun onError(exception: java.lang.Exception?) {
    val msg = exception?.localizedMessage ?: "Unknown Vosk recognition error"
    Log.e(TAG, "Vosk Recognition error: $msg", exception)
    _state.value = OfflineRecognitionState.Error(msg)
    onListeningStateChanged(false)
  }

  override fun onTimeout() {
    Log.w(TAG, "Vosk Speech timeout")
    stopListening()
  }

  internal fun extractTextFromJson(jsonStr: String?, key: String): String {
    if (jsonStr.isNullOrBlank()) return ""
    return try {
      val obj = JSONObject(jsonStr)
      obj.optString(key, "").trim()
    } catch (_: Exception) {
      ""
    }
  }
}
