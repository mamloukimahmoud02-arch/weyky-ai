package com.example.device.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.R
import com.example.core.capabilities.CapabilityDetector
import com.example.core.context.ContextSnapshot
import com.example.core.context.ContextState
import com.example.core.intent.ExecutionResult
import com.example.core.intent.IntentExecutor
import com.example.core.intent.IntentParser
import com.example.core.intent.handlers.AppActionHandler
import com.example.core.intent.handlers.AudioActionHandler
import com.example.core.intent.handlers.BatteryActionHandler
import com.example.core.intent.handlers.CallActionHandler
import com.example.core.intent.handlers.ContextActionHandler
import com.example.core.intent.handlers.FlashlightActionHandler
import com.example.core.intent.handlers.MediaActionHandler
import com.example.core.intent.handlers.NetworkActionHandler
import com.example.core.intent.handlers.NotificationActionHandler
import com.example.core.intent.handlers.SettingsActionHandler
import com.example.core.intent.handlers.StorageMemoryActionHandler
import com.example.core.intent.handlers.WeatherActionHandler
import com.example.device.apps.AppResolver
import com.example.device.audio.AudioController
import com.example.device.battery.BatteryProvider
import com.example.device.calls.CallLogProvider
import com.example.device.calls.ContactLookupProvider
import com.example.device.flashlight.FlashlightController
import com.example.device.launcher.LauncherRoleManager
import com.example.device.media.MediaPlaybackController
import com.example.device.memory.MemoryProvider
import com.example.device.network.NetworkProvider
import com.example.device.storage.StorageProvider
import com.example.device.telephony.TelephonyProvider
import com.example.device.weather.LocationProvider
import com.example.device.weather.RealWeatherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating Interaction Service for WEYKY per Section 9 of the Master Specification.
 * Allows interacting with WEYKY via voice, typing, and commands over other apps (e.g. YouTube, WhatsApp)
 * without closing or disrupting the foreground activity.
 *
 * Automatically monitors default launcher state and shuts down cleanly if WEYKY is no longer default
 * or if disabled in Settings.
 */
class WeykyFloatingOverlayService : Service() {

  companion object {
    private const val TAG = "WEYKY_OVERLAY"
    const val ACTION_START = "com.example.device.overlay.ACTION_START"
    const val ACTION_STOP = "com.example.device.overlay.ACTION_STOP"
    const val ACTION_REFRESH_LAUNCHER = "com.example.device.overlay.ACTION_REFRESH_LAUNCHER"

    @Volatile
    var isOverlayRunning: Boolean = false
      private set

    fun start(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        Log.w(TAG, "Cannot start overlay: SYSTEM_ALERT_WINDOW permission not granted")
        return
      }
      val intent = Intent(context, WeykyFloatingOverlayService::class.java).apply {
        action = ACTION_START
      }
      try {
        context.startService(intent)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start overlay service", e)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, WeykyFloatingOverlayService::class.java).apply {
        action = ACTION_STOP
      }
      try {
        context.startService(intent)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to stop overlay service", e)
      }
    }
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val mainHandler = Handler(Looper.getMainLooper())
  private var windowManager: WindowManager? = null

  // Overlay Views
  private var bubbleView: View? = null
  private var bubbleParams: WindowManager.LayoutParams? = null
  private var panelView: View? = null
  private var panelParams: WindowManager.LayoutParams? = null
  private var isPanelExpanded = false

  // Unified Intent Execution Pipeline
  private lateinit var intentExecutor: IntentExecutor

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    initUnifiedPipeline()
  }

  private fun initUnifiedPipeline() {
    val batteryProvider = BatteryProvider(this)
    val memoryProvider = MemoryProvider(this)
    val storageProvider = StorageProvider()
    val flashlightController = FlashlightController(this)
    val audioController = AudioController(this)
    val mediaPlaybackController = MediaPlaybackController(this)
    val appResolver = AppResolver(this)
    val contactLookup = ContactLookupProvider(this)
    val locationProvider = LocationProvider(this)
    val weatherProvider = RealWeatherProvider(locationProvider)
    val networkProvider = NetworkProvider(this)
    val callLogProvider = CallLogProvider(this)
    val capabilityDetector = CapabilityDetector(this)

    val batteryActionHandler = BatteryActionHandler(batteryProvider)
    val storageMemoryActionHandler = StorageMemoryActionHandler(memoryProvider, storageProvider)
    val flashlightActionHandler = FlashlightActionHandler(capabilityDetector, flashlightController)
    val audioActionHandler = AudioActionHandler(audioController)
    val mediaActionHandler = MediaActionHandler(mediaPlaybackController)
    val appActionHandler = AppActionHandler(appResolver)
    val callActionHandler = CallActionHandler(this, callLogProvider, contactLookup)
    val weatherActionHandler = WeatherActionHandler(weatherProvider, locationProvider)
    val networkActionHandler = NetworkActionHandler(this, networkProvider)
    val notificationActionHandler = NotificationActionHandler(this, capabilityDetector)
    val settingsActionHandler = SettingsActionHandler(this)
    val contextActionHandler = ContextActionHandler(
      getContextSnapshot = {
        ContextSnapshot(
          state = ContextState.STATIONARY,
          confidence = 0.9f
        )
      }
    )

    intentExecutor = IntentExecutor(
      batteryActionHandler = batteryActionHandler,
      storageMemoryActionHandler = storageMemoryActionHandler,
      flashlightActionHandler = flashlightActionHandler,
      audioActionHandler = audioActionHandler,
      mediaActionHandler = mediaActionHandler,
      appActionHandler = appActionHandler,
      callActionHandler = callActionHandler,
      weatherActionHandler = weatherActionHandler,
      networkActionHandler = networkActionHandler,
      notificationActionHandler = notificationActionHandler,
      settingsActionHandler = settingsActionHandler,
      contextActionHandler = contextActionHandler
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_REFRESH_LAUNCHER -> {
        verifyLauncherStatus()
        return START_STICKY
      }
      else -> {
        startOverlay()
      }
    }
    return START_STICKY
  }

  private fun startOverlay() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
      Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted, stopping overlay service")
      stopSelf()
      return
    }

    if (bubbleView == null) {
      createBubbleView()
      createPanelView()
      isOverlayRunning = true
      Log.i(TAG, "Floating overlay initialized successfully")
    }
  }

  private fun verifyLauncherStatus() {
    val roleManager = LauncherRoleManager(this)
    val status = roleManager.checkLauncherStatus()
    if (!status.isDefaultLauncher) {
      Log.i(TAG, "WEYKY is no longer default home launcher -> stopping floating overlay per policy")
      stopSelf()
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun createBubbleView() {
    val wm = windowManager ?: return

    val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }

    val dp56 = (56 * resources.displayMetrics.density).toInt()

    bubbleParams = WindowManager.LayoutParams(
      dp56,
      dp56,
      layoutType,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = resources.displayMetrics.widthPixels - dp56 - 24
      y = resources.displayMetrics.heightPixels / 3
    }

    // Programmatically construct tactile HUD floating bubble
    val frame = FrameLayout(this).apply {
      val padding = (6 * resources.displayMetrics.density).toInt()
      setPadding(padding, padding, padding, padding)
      background = createBubbleDrawable()
      isClickable = true
    }

    val iconView = ImageView(this).apply {
      setImageResource(R.mipmap.ic_launcher)
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
        Gravity.CENTER
      )
    }
    frame.addView(iconView)

    // Touch listener with smooth drag & tap discrimination
    var initialX = 0
    var initialY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f
    var isMoving = false

    frame.setOnTouchListener { _, event ->
      val params = bubbleParams ?: return@setOnTouchListener false
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          initialX = params.x
          initialY = params.y
          initialTouchX = event.rawX
          initialTouchY = event.rawY
          isMoving = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = (event.rawX - initialTouchX).toInt()
          val dy = (event.rawY - initialTouchY).toInt()
          if (abs(dx) > 10 || abs(dy) > 10) {
            isMoving = true
            params.x = initialX + dx
            params.y = initialY + dy
            try {
              wm.updateViewLayout(frame, params)
            } catch (e: Exception) {
              Log.w(TAG, "Error updating bubble position", e)
            }
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          if (!isMoving) {
            toggleExpandedPanel()
          }
          true
        }
        else -> false
      }
    }

    bubbleView = frame
    try {
      wm.addView(bubbleView, bubbleParams)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to add floating bubble to WindowManager", e)
    }
  }

  private fun createPanelView() {
    val wm = windowManager ?: return

    val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }

    val widthDp = (320 * resources.displayMetrics.density).toInt()
    panelParams = WindowManager.LayoutParams(
      widthDp,
      WindowManager.LayoutParams.WRAP_CONTENT,
      layoutType,
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.CENTER
    }

    // Programmatically construct tactical HUD floating command panel
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      val pad = (16 * resources.displayMetrics.density).toInt()
      setPadding(pad, pad, pad, pad)
      background = createPanelDrawable()
      visibility = View.GONE
    }

    // Top Header: Title + Home Button + Close Button
    val headerRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }

    val titleView = TextView(this).apply {
      text = "WEYKY // OVERLAY"
      textSize = 14f
      setTextColor(0xFF00F0FF.toInt())
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    headerRow.addView(titleView)

    // Return to Home Launcher Button
    val homeBtn = TextView(this).apply {
      text = "الرئيسية ⌂"
      textSize = 12f
      setTextColor(0xFF80DEEA.toInt())
      setPadding(12, 6, 12, 6)
      setOnClickListener {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
          addCategory(Intent.CATEGORY_HOME)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        toggleExpandedPanel(expand = false)
      }
    }
    headerRow.addView(homeBtn)

    // Close / Collapse Button
    val closeBtn = TextView(this).apply {
      text = "✕"
      textSize = 16f
      setTextColor(0xFF78909C.toInt())
      setPadding(12, 6, 12, 6)
      setOnClickListener {
        toggleExpandedPanel(expand = false)
      }
    }
    headerRow.addView(closeBtn)

    container.addView(headerRow)

    // Result display text
    val resultText = TextView(this).apply {
      text = "جاهز لتلقي الأوامر... (تكلم أو اكتب)"
      textSize = 13f
      setTextColor(0xFFCFD8DC.toInt())
      setPadding(0, 16, 0, 16)
    }
    container.addView(resultText)

    // Input field & send button
    val inputRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }

    val inputField = EditText(this).apply {
      hint = "اكتب أمراً (مثلاً: كم نسبة البطارية؟)..."
      setHintTextColor(0xFF546E7A.toInt())
      setTextColor(0xFFFFFFFF.toInt())
      textSize = 13f
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    inputRow.addView(inputField)

    val sendBtn = TextView(this).apply {
      text = "إرسال ➜"
      textSize = 13f
      setTextColor(0xFF00F0FF.toInt())
      setPadding(12, 8, 12, 8)
      setOnClickListener {
        val query = inputField.text.toString().trim()
        if (query.isNotEmpty()) {
          inputField.setText("")
          executeOverlayCommand(query, resultText)
        }
      }
    }
    inputRow.addView(sendBtn)

    container.addView(inputRow)

    panelView = container
    try {
      wm.addView(panelView, panelParams)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to add panel view to WindowManager", e)
    }
  }

  private fun toggleExpandedPanel(expand: Boolean = !isPanelExpanded) {
    isPanelExpanded = expand
    panelView?.visibility = if (isPanelExpanded) View.VISIBLE else View.GONE
    bubbleView?.alpha = if (isPanelExpanded) 0.5f else 1.0f
  }

  private fun executeOverlayCommand(query: String, resultView: TextView) {
    resultView.text = "جاري تنفيذ: \"$query\"..."
    resultView.setTextColor(0xFF80DEEA.toInt())

    serviceScope.launch {
      val intent = IntentParser.parse(query)
      val executionResult = intentExecutor.execute(intent)

      val displayText = when (executionResult) {
        is ExecutionResult.Success -> "✓ ${executionResult.actionTaken ?: executionResult.speech}"
        is ExecutionResult.Fallback -> "ℹ️ ${executionResult.explanation}"
        is ExecutionResult.RequiresPermission -> "⚠️ يتطلب صلاحية: ${executionResult.permission}"
        is ExecutionResult.Error -> "✗ ${executionResult.friendlyMessage}"
        is ExecutionResult.Unavailable -> "⚠️ ${executionResult.reason}"
        is ExecutionResult.NeedsConfirmation -> "❓ ${executionResult.prompt}"
        is ExecutionResult.NotSupported -> "🚫 ${executionResult.reason}"
      }

      resultView.text = displayText
      resultView.setTextColor(
        if (executionResult is ExecutionResult.Success) 0xFF00F0FF.toInt() else 0xFFFFB74D.toInt()
      )
    }
  }

  private fun createBubbleDrawable(): android.graphics.drawable.GradientDrawable {
    return android.graphics.drawable.GradientDrawable().apply {
      shape = android.graphics.drawable.GradientDrawable.OVAL
      setColor(0xEE050E18.toInt())
      setStroke((1.5f * resources.displayMetrics.density).toInt(), 0xFF00F0FF.toInt())
    }
  }

  private fun createPanelDrawable(): android.graphics.drawable.GradientDrawable {
    return android.graphics.drawable.GradientDrawable().apply {
      shape = android.graphics.drawable.GradientDrawable.RECTANGLE
      cornerRadius = 16f * resources.displayMetrics.density
      setColor(0xF5050E18.toInt())
      setStroke((1.5f * resources.displayMetrics.density).toInt(), 0xFF00F0FF.toInt())
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    isOverlayRunning = false
    serviceScope.cancel()
    val wm = windowManager
    bubbleView?.let {
      try {
        wm?.removeView(it)
      } catch (e: Exception) {
        Log.w(TAG, "Error removing bubble view", e)
      }
      bubbleView = null
    }
    panelView?.let {
      try {
        wm?.removeView(it)
      } catch (e: Exception) {
        Log.w(TAG, "Error removing panel view", e)
      }
      panelView = null
    }
    Log.i(TAG, "Floating overlay destroyed cleanly")
  }
}
