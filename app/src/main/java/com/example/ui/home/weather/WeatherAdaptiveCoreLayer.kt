package com.example.ui.home.weather

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.core.state.WeykyUiState
import com.example.device.battery.ChargingState
import com.example.device.weather.WeatherSnapshot
import com.example.rendering.visual_state.CoreVisualState
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Weather condition types supported by the Adaptive Weather Core.
 */
enum class AdaptiveWeatherType {
  CLEAR,
  PARTLY_CLOUDY,
  CLOUDY,
  RAIN,
  THUNDERSTORM,
  UNKNOWN
}

/**
 * Resolves the real-time weather snapshot into one of the supported Adaptive Weather Core types.
 */
fun resolveAdaptiveWeatherType(weather: WeatherSnapshot?): AdaptiveWeatherType {
  if (weather == null) return AdaptiveWeatherType.UNKNOWN
  val condition = (weather.condition ?: "").trim().lowercase()
  val code = weather.weatherCode

  return when {
    condition.contains("رعد") || condition.contains("عاصف") || condition.contains("thunder") ||
      condition.contains("storm") || code in listOf(95, 96, 99) ->
      AdaptiveWeatherType.THUNDERSTORM

    condition.contains("مطر") || condition.contains("زخات") || condition.contains("رذاذ") ||
      condition.contains("rain") || condition.contains("drizzle") || condition.contains("shower") ||
      code in 51..67 || code in 80..82 ->
      AdaptiveWeatherType.RAIN

    condition.contains("غائم كلي") || condition.contains("ملبد") || condition.contains("overcast") ||
      code == 3 ->
      AdaptiveWeatherType.CLOUDY

    condition.contains("غائم") || condition.contains("غيوم") || condition.contains("cloud") ||
      code in 1..2 ->
      AdaptiveWeatherType.PARTLY_CLOUDY

    condition.contains("صاف") || condition.contains("مشمس") || condition.contains("صحو") ||
      condition.contains("clear") || condition.contains("sunny") || code == 0 ->
      AdaptiveWeatherType.CLEAR

    condition.contains("ثلج") || condition.contains("snow") || code in 71..77 || code in 85..86 ->
      AdaptiveWeatherType.RAIN

    else ->
      if (condition.isNotEmpty() || code != null) AdaptiveWeatherType.CLEAR else AdaptiveWeatherType.UNKNOWN
  }
}

/**
 * Determines whether the current local time is nighttime based on system clock
 * and weather snapshot sunrise/sunset timings.
 */
fun isNightTime(weather: WeatherSnapshot?): Boolean {
  val cal = Calendar.getInstance()
  val hour = cal.get(Calendar.HOUR_OF_DAY)
  val minute = cal.get(Calendar.MINUTE)
  val currentMinute = hour * 60 + minute

  val sunriseMin = parseTimeToMinutes(weather?.sunriseToday) ?: (6 * 60) // 06:00 AM default
  val sunsetMin = parseTimeToMinutes(weather?.sunsetToday) ?: (18 * 60 + 30) // 06:30 PM default

  return currentMinute < sunriseMin || currentMinute >= sunsetMin
}

/**
 * Parses time strings like "06:15", "18:30", "6:15 AM", "6:30 PM" into total minutes from midnight.
 */
fun parseTimeToMinutes(timeStr: String?): Int? {
  if (timeStr.isNullOrBlank()) return null
  return try {
    val clean = timeStr.trim().lowercase()
    val isPm = clean.contains("pm") || clean.contains("م")
    val isAm = clean.contains("am") || clean.contains("ص")
    val digitsOnly = clean.replace(Regex("[^0-9:]"), "")
    val parts = digitsOnly.split(":")
    if (parts.isNotEmpty()) {
      var h = parts[0].toInt()
      val m = if (parts.size > 1) parts[1].toInt() else 0
      if (isPm && h < 12) h += 12
      if (isAm && h == 12) h = 0
      h * 60 + m
    } else null
  } catch (_: Exception) {
    null
  }
}

/**
 * Verifies if the WEYKY core is strictly in IDLE mode without any functional operations.
 * Returns false immediately if voice listening, speaking aloud, music playing, charging,
 * or any non-IDLE visual state is active.
 */
fun isCoreEligibleForWeatherEffect(uiState: WeykyUiState, isCharging: Boolean): Boolean {
  return uiState.visualState == CoreVisualState.IDLE &&
    !uiState.isVoiceListening &&
    !uiState.isSpeakingAloud &&
    !uiState.isMusicPlaying &&
    !isCharging
}

/**
 * Astronomical lunar phases (الأطوار والأحجام القمرية المعروفة).
 */
enum class LunarPhaseType(val arabicName: String) {
  NEW_MOON("المحاق"),
  WAXING_CRESCENT("الهلال المتزايد"),
  FIRST_QUARTER("التربيع الأول"),
  WAXING_GIBBOUS("الأحدب المتزايد"),
  FULL_MOON("البدر الكامل"),
  WANING_GIBBOUS("الأحدب المتناقص"),
  LAST_QUARTER("التربيع الأخير"),
  WANING_CRESCENT("الهلال المتناقص")
}

/**
 * Real-time astronomical lunar telemetry for the current date, time, and sunrise/sunset window.
 */
data class LunarAstronomy(
  val cyclePhase: Float,            // 0.0 = New Moon, 0.25 = First Quarter, 0.50 = Full Moon, 0.75 = Last Quarter
  val illuminationFraction: Float,  // 0.0 .. 1.0 illuminated fraction of the lunar disk
  val phaseType: LunarPhaseType,
  val moonriseMinutes: Int,         // Minutes from midnight (0..1439)
  val moonsetMinutes: Int,          // Minutes from midnight (0..1439)
  val isMoonInAppearanceWindow: Boolean, // True if current time is between moonrise & moonset and not New Moon
  val isMoonVisibleAtNight: Boolean      // True if it is currently night AND the moon is in its appearance window
)

/**
 * Calculates the real-time astronomical lunar phase, illumination size, and moonrise/moonset
 * appearance window (موعد ظهور القمر).
 */
fun calculateLunarAstronomy(
  weather: WeatherSnapshot?,
  calendar: Calendar = Calendar.getInstance()
): LunarAstronomy {
  // Reference astronomical New Moon epoch: Jan 6, 2000 18:14 UTC (947182440000L ms)
  val referenceNewMoonMs = 947182440000L
  val synodicMonthDays = 29.530588853
  val synodicMonthMs = (synodicMonthDays * 86400000.0)

  val diffMs = calendar.timeInMillis - referenceNewMoonMs
  val rawPhase = ((diffMs % synodicMonthMs) + synodicMonthMs) % synodicMonthMs
  val cyclePhase = (rawPhase / synodicMonthMs).toFloat().coerceIn(0f, 0.9999f)

  // Illumination fraction: 0.0 at New Moon, 0.5 at Quarter, 1.0 at Full Moon
  val illuminationFraction = ((1.0 - cos(cyclePhase * 2.0 * Math.PI)) * 0.5).toFloat().coerceIn(0f, 1f)

  val phaseType = when {
    cyclePhase < 0.03f || cyclePhase > 0.97f -> LunarPhaseType.NEW_MOON
    cyclePhase < 0.22f -> LunarPhaseType.WAXING_CRESCENT
    cyclePhase < 0.28f -> LunarPhaseType.FIRST_QUARTER
    cyclePhase < 0.47f -> LunarPhaseType.WAXING_GIBBOUS
    cyclePhase <= 0.53f -> LunarPhaseType.FULL_MOON
    cyclePhase <= 0.72f -> LunarPhaseType.WANING_GIBBOUS
    cyclePhase <= 0.78f -> LunarPhaseType.LAST_QUARTER
    else -> LunarPhaseType.WANING_CRESCENT
  }

  val sunriseMin = parseTimeToMinutes(weather?.sunriseToday) ?: (6 * 60)       // 06:00 default
  val sunsetMin = parseTimeToMinutes(weather?.sunsetToday) ?: (18 * 60 + 30)  // 18:30 default

  // Astronomically, Moonrise lags Sunrise by (cyclePhase * 24 hours):
  // - At New Moon (0.0): rises at Sunrise, sets at Sunset
  // - At First Quarter (0.25): rises at Noon, sets at Midnight
  // - At Full Moon (0.50): rises at Sunset, sets at Sunrise (visible all night)
  // - At Last Quarter (0.75): rises at Midnight, sets at Noon
  val moonriseMinutes = ((sunriseMin + (cyclePhase * 1440f).toInt()) % 1440 + 1440) % 1440
  // Average lunar transit duration above horizon is ~12 hours 25 minutes (745 mins)
  val moonsetMinutes = (moonriseMinutes + 745) % 1440

  val currentMin = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
  val isAboveHorizon = if (moonriseMinutes <= moonsetMinutes) {
    currentMin in moonriseMinutes..moonsetMinutes
  } else {
    // Wraps across midnight (e.g., rises at 19:00, sets at 07:25)
    currentMin >= moonriseMinutes || currentMin <= moonsetMinutes
  }

  val isMoonInAppearanceWindow = isAboveHorizon && phaseType != LunarPhaseType.NEW_MOON
  val isNight = currentMin < sunriseMin || currentMin >= sunsetMin

  return LunarAstronomy(
    cyclePhase = cyclePhase,
    illuminationFraction = illuminationFraction,
    phaseType = phaseType,
    moonriseMinutes = moonriseMinutes,
    moonsetMinutes = moonsetMinutes,
    isMoonInAppearanceWindow = isMoonInAppearanceWindow,
    isMoonVisibleAtNight = isNight && isMoonInAppearanceWindow
  )
}

/**
 * Determines whether the current weather condition includes visible clouds around the core.
 */
fun hasCloudsInWeather(weather: WeatherSnapshot?): Boolean {
  val weatherType = resolveAdaptiveWeatherType(weather)
  val cloudCover = weather?.cloudCoverPercent ?: 0
  return when (weatherType) {
    AdaptiveWeatherType.PARTLY_CLOUDY,
    AdaptiveWeatherType.CLOUDY,
    AdaptiveWeatherType.RAIN,
    AdaptiveWeatherType.THUNDERSTORM -> true
    AdaptiveWeatherType.CLEAR -> cloudCover >= 20
    AdaptiveWeatherType.UNKNOWN -> cloudCover >= 20
  }
}

/**
 * Adaptive Weather Core Layer.
 *
 * - Daytime: Surrounds the Golden Sun Core with warm golden solar atmospheric glow.
 * - Nighttime: Surrounds the White Moon Core (when in its appearance window) with luminous
 *   silver-white lunar glow scaled to its astronomical phase size (Crescent, Quarter, Gibbous, Full Moon).
 * - Weather-Conditional Clouds: Renders domain-warped 6-octave fBm volumetric clouds ONLY when
 *   actual weather has clouds (`PARTLY_CLOUDY`, `CLOUDY`, `RAIN`, `THUNDERSTORM`, or `cloudCoverPercent >= 20`),
 *   backlit by golden sunlight during the day and silver-white moonlight at night.
 */
@Composable
fun WeatherAdaptiveCoreLayer(
  uiState: WeykyUiState,
  coreSizeDp: Dp,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val batterySnap = uiState.batterySnapshot
  val isDeviceCharging = batterySnap?.chargingState == ChargingState.CHARGING ||
    batterySnap?.chargingState == ChargingState.FULL

  val isEligible = isCoreEligibleForWeatherEffect(uiState, isDeviceCharging)
  val weather = uiState.weatherSnapshot
  val weatherType = resolveAdaptiveWeatherType(weather)
  val isNight = isNightTime(weather)
  val lunarAstronomy = remember(weather?.sunriseToday, weather?.sunsetToday, uiState.currentTimeString) {
    calculateLunarAstronomy(weather)
  }
  val showClouds = hasCloudsInWeather(weather)
  val shouldRender = isEligible

  // Cloud opacity scales naturally with weather cloudiness intensity
  val cloudIntensity = when (weatherType) {
    AdaptiveWeatherType.THUNDERSTORM, AdaptiveWeatherType.RAIN, AdaptiveWeatherType.CLOUDY -> 1.0f
    AdaptiveWeatherType.PARTLY_CLOUDY -> 0.82f
    else -> ((weather?.cloudCoverPercent ?: 0) / 100f).coerceIn(0f, 0.85f)
  }

  // Pre-compute photorealistic domain-warped fBm volumetric cloud textures (Day Golden vs Night Silver-White)
  val backCloudBitmap = remember(isNight) {
    VolumetricCloudGenerator.generateCloudLayerBitmap(
      width = 320,
      height = 220,
      seed = 1337,
      isFrontLayer = false,
      isNight = isNight
    )
  }
  val frontCloudBitmap = remember(isNight) {
    VolumetricCloudGenerator.generateCloudLayerBitmap(
      width = 340,
      height = 230,
      seed = 7919,
      isFrontLayer = true,
      isNight = isNight
    )
  }

  // Swift cutoff on functional state switch (150ms); smooth harmonic fade-in on return to IDLE (600ms)
  val weatherAlpha by animateFloatAsState(
    targetValue = if (shouldRender) 1f else 0f,
    animationSpec = tween(
      durationMillis = if (shouldRender) 600 else 150,
      easing = FastOutSlowInEasing
    ),
    label = "AdaptiveWeatherAlpha"
  )

  val cloudAlpha by animateFloatAsState(
    targetValue = if (shouldRender && showClouds) cloudIntensity else 0f,
    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
    label = "AdaptiveCloudAlpha"
  )

  val transition = rememberInfiniteTransition(label = "weather_core_motion")

  // Gentle celestial halo breathing pulse
  val solarPulse by transition.animateFloat(
    initialValue = 0.97f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "solar_pulse"
  )

  // Slow continuous drift for front cloud bank
  val cloudDriftPrimary by transition.animateFloat(
    initialValue = -0.06f,
    targetValue = 0.06f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 28000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "cloud_drift_primary"
  )

  // Counter-drift for rear atmospheric cloud strata
  val cloudDriftSecondary by transition.animateFloat(
    initialValue = 0.05f,
    targetValue = -0.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 36000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "cloud_drift_secondary"
  )

  // Rain fall cycle
  val rainProgress by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "rain_progress"
  )

  Box(
    modifier = modifier
      .size(coreSizeDp)
      .testTag("weather_adaptive_core_layer"),
    contentAlignment = Alignment.Center
  ) {
    // 1. Ambient Background Celestial Glow (Golden Sun by Day / White Moon by Night) & Rear Clouds
    if (weatherAlpha > 0.005f) {
      Canvas(
        modifier = Modifier
          .size(coreSizeDp * 1.65f)
          .testTag("weather_adaptive_core_bg_canvas")
      ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val coreRadius = (size.minDimension / (2f * 1.65f)) * 0.86f

        if (!isNight) {
          // Daytime: Warm Golden Solar Atmosphere
          drawGoldenSolarAtmosphere(
            center = center,
            coreRadius = coreRadius,
            pulse = solarPulse,
            alpha = weatherAlpha
          )
        } else if (lunarAstronomy.isMoonInAppearanceWindow) {
          // Nighttime (Moon in appearance window): Pure White-Silver Lunar Atmosphere scaled by illumination
          drawWhiteLunarAtmosphere(
            center = center,
            coreRadius = coreRadius,
            pulse = solarPulse,
            illumination = lunarAstronomy.illuminationFraction,
            alpha = weatherAlpha
          )
        }

        // Rear turbulent cloud strata behind the core (ONLY if weather has clouds)
        if (cloudAlpha > 0.005f) {
          val rearDriftPx = (size.width * cloudDriftSecondary).toInt()
          drawImage(
            image = backCloudBitmap,
            dstOffset = IntOffset(rearDriftPx, (size.height * 0.14f).toInt()),
            dstSize = IntSize(size.width.toInt(), (size.height * 0.78f).toInt()),
            alpha = 0.82f * cloudAlpha * weatherAlpha
          )
        }
      }
    }

    // 2. The Living WEYKY Core Itself (Golden Sun by Day / White Moon in its Phase by Night)
    content()

    // 3. Foreground Volumetric fBm Clouds & Rain Precipitation (ONLY when present in weather)
    if (weatherAlpha > 0.005f && (cloudAlpha > 0.005f || weatherType == AdaptiveWeatherType.RAIN || weatherType == AdaptiveWeatherType.THUNDERSTORM)) {
      Canvas(
        modifier = Modifier
          .size(coreSizeDp * 1.65f)
          .testTag("weather_adaptive_core_fg_canvas")
      ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val coreRadius = (size.minDimension / (2f * 1.65f)) * 0.86f

        if (cloudAlpha > 0.005f) {
          val frontDriftPx = (size.width * cloudDriftPrimary).toInt()
          drawImage(
            image = frontCloudBitmap,
            dstOffset = IntOffset(frontDriftPx, (size.height * 0.16f).toInt()),
            dstSize = IntSize(size.width.toInt(), (size.height * 0.82f).toInt()),
            alpha = 0.96f * cloudAlpha * weatherAlpha
          )
        }

        // Rain Precipitation Particles if Rainy / Stormy
        if (weatherType == AdaptiveWeatherType.RAIN || weatherType == AdaptiveWeatherType.THUNDERSTORM) {
          val precipMm = weather?.precipitationMm ?: 3.0f
          drawFrontRainPrecipitation(
            center = center,
            coreRadius = coreRadius,
            rainProgress = rainProgress,
            precipitationMm = precipMm,
            alpha = weatherAlpha
          )
        }
      }
    }
  }
}

/**
 * Draws the warm golden solar atmospheric glow behind the living core during the day.
 */
private fun DrawScope.drawGoldenSolarAtmosphere(
  center: Offset,
  coreRadius: Float,
  pulse: Float,
  alpha: Float
) {
  val outerHaloRadius = coreRadius * 1.75f * pulse

  drawCircle(
    brush = Brush.radialGradient(
      0.0f to Color(0xFFFFF9C4).copy(alpha = 0.65f * alpha),
      0.42f to Color(0xFFFDE047).copy(alpha = 0.48f * alpha),
      0.68f to Color(0xFFF59E0B).copy(alpha = 0.28f * alpha),
      0.88f to Color(0xFFB45309).copy(alpha = 0.12f * alpha),
      1.0f to Color.Transparent,
      center = center,
      radius = outerHaloRadius
    ),
    radius = outerHaloRadius,
    center = center
  )
}

/**
 * Draws the luminous electric cyan-blue & ice-white lunar atmospheric corona and surrounding
 * cosmic starfield behind the Moon core at night, matching the reference Moon artwork.
 */
private fun DrawScope.drawWhiteLunarAtmosphere(
  center: Offset,
  coreRadius: Float,
  pulse: Float,
  illumination: Float,
  alpha: Float
) {
  val intensity = (0.55f + 0.45f * illumination).coerceIn(0.45f, 1f) * alpha
  val outerHaloRadius = coreRadius * (1.62f + 0.20f * illumination) * pulse

  // Deep electric azure & cyan radial corona
  drawCircle(
    brush = Brush.radialGradient(
      0.0f to Color(0xFFFFFFFF).copy(alpha = 0.78f * intensity),
      0.52f to Color(0xFF00F0FF).copy(alpha = 0.62f * intensity),
      0.68f to Color(0xFF00B4D8).copy(alpha = 0.38f * intensity),
      0.84f to Color(0xFF0284C7).copy(alpha = 0.18f * intensity),
      1.0f to Color.Transparent,
      center = center,
      radius = outerHaloRadius
    ),
    radius = outerHaloRadius,
    center = center
  )

  // Subtle surrounding starfield dust matching the reference image
  val starAngles = floatArrayOf(
    0.25f, 0.68f, 1.15f, 1.72f, 2.18f, 2.64f, 3.05f, 3.48f,
    3.92f, 4.35f, 4.82f, 5.28f, 5.74f, 6.10f
  )
  for (i in starAngles.indices) {
    val angle = starAngles[i]
    val dist = coreRadius * (1.18f + (i % 4) * 0.14f)
    val sx = center.x + cos(angle) * dist
    val sy = center.y + sin(angle) * dist
    val starAlpha = ((0.35f + (i % 3) * 0.22f) * intensity).coerceIn(0f, 0.9f)
    val starColor = if (i % 2 == 0) Color.White else Color(0xFF7DD3FC)
    drawCircle(
      color = starColor.copy(alpha = starAlpha),
      radius = if (i % 3 == 0) 1.9f else 1.2f,
      center = Offset(sx, sy)
    )
  }
}

/**
 * Draws realistic rain precipitation streaks when rainy weather is active.
 */
private fun DrawScope.drawFrontRainPrecipitation(
  center: Offset,
  coreRadius: Float,
  rainProgress: Float,
  precipitationMm: Float,
  alpha: Float
) {
  val dropCount = if (precipitationMm > 6f) 30 else 18
  val dropLength = 20.dp.toPx()
  val widthSpan = coreRadius * 2.2f
  val heightSpan = coreRadius * 2.6f

  for (i in 0 until dropCount) {
    val xFraction = ((i * 0.1618f) % 1.0f)
    val dropX = center.x - widthSpan * 0.5f + xFraction * widthSpan

    val yStagger = ((i * 0.37f) % 1.0f)
    val effectiveProgress = (rainProgress + yStagger) % 1.0f
    val startY = center.y - heightSpan * 0.5f + effectiveProgress * heightSpan
    val endY = startY + dropLength

    val normY = (startY - (center.y - heightSpan * 0.5f)) / heightSpan
    val edgeFade = sin(normY * Math.PI).toFloat().coerceIn(0f, 1f)

    val dropColor = Color(0xFF00F0FF).copy(alpha = 0.55f * edgeFade * alpha)

    drawLine(
      color = dropColor,
      start = Offset(dropX, startY),
      end = Offset(dropX - dropLength * 0.12f, endY),
      strokeWidth = 1.8.dp.toPx(),
      cap = StrokeCap.Round
    )
  }
}

/**
 * True Procedural Volumetric Cloud Shader using 6-Octave Rotated Fractional Brownian Motion (fBm),
 * Domain Warping (Turbulence Curl), and Directional Ray-Marched Celestial Scattering.
 *
 * - Daytime (`isNight = false`): Molten golden-amber solar backlighting and illuminated crevices.
 * - Nighttime (`isNight = true`): Luminous silver-pearl lunar backlighting and moonlit crevices.
 * Completely free of geometric circles or bubble shapes.
 */
internal object VolumetricCloudGenerator {

  private fun hash(ix: Int, iy: Int, seed: Int): Float {
    var n = ix * 374761393 + iy * 668265263 + seed * 1442695041
    n = (n xor (n ushr 13)) * 1274126177
    n = n xor (n ushr 16)
    return (n and 0x7fffffff).toFloat() / 2147483647.0f
  }

  private fun noise2D(x: Float, y: Float, seed: Int): Float {
    val ix = floor(x).toInt()
    val iy = floor(y).toInt()
    val fx = x - ix
    val fy = y - iy

    // Quintic smoothstep interpolation for C2 continuity (zero grid lines)
    val ux = fx * fx * fx * (fx * (fx * 6f - 15f) + 10f)
    val uy = fy * fy * fy * (fy * (fy * 6f - 15f) + 10f)

    val a = hash(ix, iy, seed)
    val b = hash(ix + 1, iy, seed)
    val c = hash(ix, iy + 1, seed)
    val d = hash(ix + 1, iy + 1, seed)

    val k0 = a + (b - a) * ux
    val k1 = c + (d - c) * ux
    return k0 + (k1 - k0) * uy
  }

  /**
   * 6-octave rotated fBm to create natural anisotropic cloud wisps and fractal edges.
   */
  private fun fbm(px: Float, py: Float, seed: Int, octaves: Int = 6): Float {
    var value = 0f
    var amp = 0.52f
    var x = px
    var y = py
    var norm = 0f

    for (i in 0 until octaves) {
      val n = noise2D(x, y, seed + i * 101)
      // Mix standard and ridged turbulence for realistic cauliflower cloud tops & wispy tendrils
      val billow = 1f - abs(2f * n - 1f)
      val mixed = n * 0.65f + billow * 0.35f
      value += amp * mixed
      norm += amp

      // Rotate by ~37 degrees and scale frequency by 2.07x per octave
      val nx = (0.80f * x - 0.60f * y) * 2.07f + 13.7f
      val ny = (0.60f * x + 0.80f * y) * 2.07f + 29.3f
      x = nx
      y = ny
      amp *= 0.50f
    }
    return value / norm
  }

  private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }

  /**
   * Evaluates volumetric cloud density at normalized coordinates (u, v) in [0, 1].
   */
  private fun sampleCloudDensity(u: Float, v: Float, seed: Int, isFrontLayer: Boolean): Float {
    // Aspect-scaled coordinates
    val x = (u - 0.5f) * 3.8f
    val y = (v - 0.5f) * 2.6f

    // 1. Domain Warping (Turbulence Curl)
    val qx = fbm(x * 1.3f, y * 1.7f, seed, 4) - 0.5f
    val qy = fbm(x * 1.3f + 5.2f, y * 1.7f + 1.3f, seed + 17, 4) - 0.5f

    val rx = fbm(x * 2.1f + 2.4f * qx + 1.7f, y * 2.5f + 2.4f * qy + 9.2f, seed + 31, 5) - 0.5f
    val ry = fbm(x * 2.1f + 2.4f * qx + 8.3f, y * 2.5f + 2.4f * qy + 2.8f, seed + 47, 5) - 0.5f

    val f = fbm(x * 2.2f + 1.9f * rx, y * 3.0f + 1.9f * ry, seed + 73, 6)
    val fineWisp = fbm(x * 5.8f + 1.2f * rx, y * 7.2f + 1.2f * ry, seed + 113, 4)

    // 2. Sweeping Cloud Bank Silhouette
    val ridgeLineY = if (isFrontLayer) {
      0.38f + 0.14f * (u - 0.35f) - 0.10f * exp(-12f * (u - 0.30f) * (u - 0.30f))
    } else {
      0.34f + 0.08f * sin(u * 4.5f)
    }

    // Distance below the jagged fractal ridge
    val warpedRidgeY = ridgeLineY + qy * 0.18f + ry * 0.11f - (f - 0.5f) * 0.34f
    val verticalEnvelope = smoothstep(warpedRidgeY - 0.12f, warpedRidgeY + 0.14f, v)

    // Horizontal stratocumulus banding with crevices where light peeks through
    val strataWave = sin(v * 22f + qy * 5.5f + u * 2.5f)
    val creviceCarve = smoothstep(-0.85f, 0.35f, strataWave) * 0.32f + 0.68f

    // Soft organic horizontal & bottom fade (warped by noise so it never forms a box or circle)
    val edgeNoise = (f - 0.5f) * 0.18f
    val leftFade = smoothstep(0.01f, 0.22f, u + edgeNoise)
    val rightFade = smoothstep(0.01f, 0.22f, (1f - u) + edgeNoise)
    val bottomFade = smoothstep(0.01f, 0.24f, (1f - v) + edgeNoise * 0.8f)
    val topFade = smoothstep(0.02f, 0.18f, v + edgeNoise * 0.5f)

    val rawDensity = (f * 0.72f + fineWisp * 0.28f) * verticalEnvelope * creviceCarve
    val masked = smoothstep(0.30f, 0.68f, rawDensity) * leftFade * rightFade * bottomFade * topFade
    return masked.coerceIn(0f, 1f)
  }

  /**
   * Generates an ARGB_8888 ImageBitmap of volumetric clouds illuminated by the Golden Sun (day)
   * or White Moon (night).
   */
  fun generateCloudLayerBitmap(
    width: Int,
    height: Int,
    seed: Int,
    isFrontLayer: Boolean,
    isNight: Boolean = false
  ): ImageBitmap {
    val densities = FloatArray(width * height)
    for (py in 0 until height) {
      val v = py.toFloat() / height.toFloat()
      val rowOffset = py * width
      for (px in 0 until width) {
        val u = px.toFloat() / width.toFloat()
        densities[rowOffset + px] = sampleCloudDensity(u, v, seed, isFrontLayer)
      }
    }

    // Core center in normalized cloud texture space
    val sunU = 0.50f
    val sunV = 0.36f

    val pixels = IntArray(width * height)

    for (py in 0 until height) {
      val v = py.toFloat() / height.toFloat()
      val rowOffset = py * width
      for (px in 0 until width) {
        val d = densities[rowOffset + px]
        if (d <= 0.008f) {
          pixels[rowOffset + px] = 0
          continue
        }

        val u = px.toFloat() / width.toFloat()
        val dx = sunU - u
        val dy = sunV - v
        val distToSun = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val dirX = dx / distToSun
        val dirY = dy / distToSun

        // Ray-march 4 steps toward the Core to compute optical depth (shadowing)
        var opticalDepth = 0f
        val stepSize = 0.032f
        for (s in 1..4) {
          val su = (u + dirX * stepSize * s).coerceIn(0f, 0.999f)
          val sv = (v + dirY * stepSize * s).coerceIn(0f, 0.999f)
          val sx = (su * width).toInt().coerceIn(0, width - 1)
          val sy = (sv * height).toInt().coerceIn(0, height - 1)
          opticalDepth += densities[sy * width + sx]
        }

        // Beer-Lambert light transmittance + powder sugar forward-scattering effect
        val transmittance = exp(-1.15f * opticalDepth)
        val sunProximity = exp(-4.2f * max(0f, distToSun - 0.10f).pow(2))

        // 1. Ragged Rim Lighting along the cloud edges facing the celestial core
        val edgeThinness = (1f - d * 0.65f).coerceIn(0f, 1f)
        val rimIntensity = (transmittance * sunProximity * edgeThinness * 1.45f).coerceIn(0f, 1f)

        // 2. Crevice Glow where light filters between lower horizontal cloud bands
        val horizontalGlowZone = exp(-3.5f * (u - 0.52f) * (u - 0.52f))
        val creviceIntensity = (transmittance.pow(0.65f) * (1f - d).pow(1.2f) * horizontalGlowZone * 1.15f).coerceIn(0f, 1f)

        val totalLight = max(rimIntensity, creviceIntensity * 0.78f).coerceIn(0f, 1f)

        val r: Float
        val g: Float
        val b: Float

        if (isNight) {
          // Nighttime: Deep Midnight Sapphire -> Electric Cyan-Blue Crevice -> Ice-White Lunar Rim
          if (totalLight < 0.30f) {
            val t = totalLight / 0.30f
            r = 8f + (18f - 8f) * t
            g = 14f + (36f - 14f) * t
            b = 28f + (68f - 28f) * t
          } else if (totalLight < 0.62f) {
            val t = (totalLight - 0.30f) / 0.32f
            r = 18f + (14f - 18f) * t
            g = 36f + (165f - 36f) * t
            b = 68f + (233f - 68f) * t
          } else if (totalLight < 0.85f) {
            val t = (totalLight - 0.62f) / 0.23f
            r = 14f + (186f - 14f) * t
            g = 165f + (230f - 165f) * t
            b = 233f + (253f - 233f) * t
          } else {
            val t = (totalLight - 0.85f) / 0.15f
            r = 186f + (255f - 186f) * t
            g = 230f + (255f - 230f) * t
            b = 253f + (255f - 253f) * t
          }
        } else {
          // Daytime: Deep Charcoal Shadow -> Warm Amber Crevice -> Blazing Golden Solar Rim
          if (totalLight < 0.30f) {
            val t = totalLight / 0.30f
            r = 9f + (32f - 9f) * t
            g = 13f + (38f - 13f) * t
            b = 22f + (52f - 22f) * t
          } else if (totalLight < 0.62f) {
            val t = (totalLight - 0.30f) / 0.32f
            r = 32f + (217f - 32f) * t
            g = 38f + (119f - 38f) * t
            b = 52f + (6f - 52f) * t
          } else if (totalLight < 0.85f) {
            val t = (totalLight - 0.62f) / 0.23f
            r = 217f + (253f - 217f) * t
            g = 119f + (224f - 119f) * t
            b = 6f + (71f - 6f) * t
          } else {
            val t = (totalLight - 0.85f) / 0.15f
            r = 253f + (255f - 253f) * t
            g = 224f + (253f - 224f) * t
            b = 71f + (231f - 71f) * t
          }
        }

        val alpha = (smoothstep(0.01f, 0.28f, d) * (if (isFrontLayer) 0.96f else 0.82f)).coerceIn(0f, 1f)
        val aInt = (alpha * 255f).toInt().coerceIn(0, 255)
        val rInt = r.toInt().coerceIn(0, 255)
        val gInt = g.toInt().coerceIn(0, 255)
        val bInt = b.toInt().coerceIn(0, 255)

        pixels[rowOffset + px] = AndroidColor.argb(aInt, rInt, gInt, bInt)
      }
    }

    val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    return bitmap.asImageBitmap()
  }
}
