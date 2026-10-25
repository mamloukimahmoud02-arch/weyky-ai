package com.example.ui.home.weather

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.device.weather.WeatherSnapshot
import java.util.Calendar

/**
 * Period of the day based on the current local hour.
 */
enum class SkyTimeOfDay {
  DAWN,   // 05:30 - 07:30
  DAY,    // 07:30 - 17:30
  SUNSET, // 17:30 - 20:00
  NIGHT   // 20:00 - 05:30
}

/**
 * Sky condition category derived from WeatherSnapshot.
 */
enum class SkyConditionType {
  CLEAR,
  PARTLY_CLOUDY,
  OVERCAST,
  RAIN,
  THUNDERSTORM,
  SNOW,
  FOG
}

/**
 * Real-time dynamic living sky viewport that represents the actual sky appearance
 * matching the real-time hour (Day/Sunset/Night/Dawn), sun/moon positions,
 * realistic drifting cloud layers, and weather precipitation particles.
 */
@Composable
fun RealtimeSkyViewport(
  weather: WeatherSnapshot?,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(12.dp),
  borderColor: Color = Color(0xFF00F0FF).copy(alpha = 0.5f)
) {
  // 1. Resolve Time of Day from current local hour
  val calendar = remember { Calendar.getInstance() }
  val hour = calendar.get(Calendar.HOUR_OF_DAY)
  val minute = calendar.get(Calendar.MINUTE)
  val timeDecimal = hour + minute / 60f

  val timeOfDay = when {
    timeDecimal in 5.5f..7.5f -> SkyTimeOfDay.DAWN
    timeDecimal in 7.5f..17.5f -> SkyTimeOfDay.DAY
    timeDecimal in 17.5f..20.0f -> SkyTimeOfDay.SUNSET
    else -> SkyTimeOfDay.NIGHT
  }

  // 2. Resolve Condition Type from weather condition or weatherCode
  val condRaw = (weather?.condition ?: "").lowercase()
  val code = weather?.weatherCode ?: -1

  val conditionType = when {
    condRaw.contains("رعد") || condRaw.contains("عاصف") || code in listOf(95, 96, 99) ->
      SkyConditionType.THUNDERSTORM
    condRaw.contains("مطر") || condRaw.contains("زخات") || condRaw.contains("رذاذ") || code in 51..67 || code in 80..82 ->
      SkyConditionType.RAIN
    condRaw.contains("ثلج") || condRaw.contains("جليد") || code in 71..77 || code in 85..86 ->
      SkyConditionType.SNOW
    condRaw.contains("ضباب") || condRaw.contains("سديم") || code in 45..48 ->
      SkyConditionType.FOG
    condRaw.contains("غائم كلي") || condRaw.contains("ملبد") || code == 3 ->
      SkyConditionType.OVERCAST
    condRaw.contains("غائم") || condRaw.contains("غيوم") || code in 1..2 ->
      SkyConditionType.PARTLY_CLOUDY
    else ->
      SkyConditionType.CLEAR
  }

  // 3. Infinite Transitions for living sky movements
  val transition = rememberInfiniteTransition(label = "sky_transition")

  // Cloud drift parallax movement
  val cloudOffset1 by transition.animateFloat(
    initialValue = -0.3f,
    targetValue = 1.3f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 14000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "cloud_drift_1"
  )

  val cloudOffset2 by transition.animateFloat(
    initialValue = -0.4f,
    targetValue = 1.4f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 20000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "cloud_drift_2"
  )

  // Sun / Moon radiant glow pulsing
  val celestialGlow by transition.animateFloat(
    initialValue = 0.65f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "celestial_glow"
  )

  // Rain / Snow falling loop
  val precipitationProgress by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 900, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "rain_progress"
  )

  // Lightning flicker (subtle sheet flash)
  val lightningFlash by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 6000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "lightning_timer"
  )

  // Star twinkling opacities
  val starTwinkle by transition.animateFloat(
    initialValue = 0.3f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "star_twinkle"
  )

  Box(
    modifier = modifier
      .size(width = 54.dp, height = 54.dp)
      .clip(shape)
      .border(1.dp, borderColor, shape)
      .testTag("realtime_sky_viewport"),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // --- LAYER 1: SKY ATMOSPHERE GRADIENT ---
      val skyGradient = getAtmosphereGradient(timeOfDay, conditionType)
      drawRect(brush = skyGradient)

      // Lightning flash in thunderstorm
      if (conditionType == SkyConditionType.THUNDERSTORM) {
        val flashAlpha = if (lightningFlash in 0.88f..0.92f || lightningFlash in 0.94f..0.97f) 0.35f else 0.0f
        if (flashAlpha > 0f) {
          drawRect(color = Color.White.copy(alpha = flashAlpha))
        }
      }

      // --- LAYER 2: CELESTIAL BODIES (SUN / MOON / STARS) ---
      when (timeOfDay) {
        SkyTimeOfDay.NIGHT -> {
          // A. Twinkling Stars
          drawStars(w, h, starTwinkle)

          // B. Luminous Blue-White Moon with Electric Cyan Corona (matching reference artwork)
          val lunar = calculateLunarAstronomy(weather)
          if (lunar.isMoonInAppearanceWindow) {
            val moonCenterX = w * 0.70f
            val moonCenterY = h * 0.35f
            val moonRadius = w * (0.16f + 0.05f * lunar.illuminationFraction)
            val moonCenter = Offset(moonCenterX, moonCenterY)

            // Outer electric cyan-blue lunar corona
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(
                  Color(0x9900F0FF).copy(alpha = celestialGlow * 0.70f),
                  Color(0x5500B4D8).copy(alpha = celestialGlow * 0.45f),
                  Color(0x220284C7).copy(alpha = celestialGlow * 0.25f),
                  Color.Transparent
                ),
                center = moonCenter,
                radius = moonRadius * 2.6f
              ),
              center = moonCenter,
              radius = moonRadius * 2.6f
            )

            // Base ice-white & sapphire-blue moon sphere
            drawCircle(
              brush = Brush.radialGradient(
                0.0f to Color(0xFFE0F2FE),
                0.55f to Color(0xFFBAE6FD),
                0.85f to Color(0xFF60A5FA),
                1.0f to Color(0xFFFFFFFF),
                center = moonCenter,
                radius = moonRadius
              ),
              center = moonCenter,
              radius = moonRadius
            )

            // Subtle sapphire lunar maria patches & bright Tycho highland
            drawCircle(
              color = Color(0x771E3A8A),
              center = moonCenter + Offset(-moonRadius * 0.26f, -moonRadius * 0.14f),
              radius = moonRadius * 0.38f
            )
            drawCircle(
              color = Color(0x661E40AF),
              center = moonCenter + Offset(moonRadius * 0.08f, -moonRadius * 0.08f),
              radius = moonRadius * 0.30f
            )
            drawCircle(
              color = Color(0xCCFFFFFF),
              center = moonCenter + Offset(moonRadius * 0.32f, moonRadius * 0.34f),
              radius = moonRadius * 0.22f
            )

            // Blazing white-cyan outer rim ring
            drawCircle(
              color = Color(0xAA00E5FF),
              center = moonCenter,
              radius = moonRadius * 1.04f,
              style = Stroke(width = 2.2f)
            )
            drawCircle(
              color = Color.White,
              center = moonCenter,
              radius = moonRadius,
              style = Stroke(width = 1.2f)
            )
          }
        }

        SkyTimeOfDay.DAY -> {
          val sunCenterX = w * 0.75f
          val sunCenterY = h * 0.30f
          val sunRadius = w * 0.18f

          // Sun Corona / Radial Rays
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                Color(0x60FDE047).copy(alpha = celestialGlow * 0.65f),
                Color(0x25F59E0B),
                Color.Transparent
              ),
              center = Offset(sunCenterX, sunCenterY),
              radius = sunRadius * 2.8f
            ),
            center = Offset(sunCenterX, sunCenterY),
            radius = sunRadius * 2.8f
          )

          // Sun Core
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(Color(0xFFFFFBEB), Color(0xFFFDE047), Color(0xFFF59E0B)),
              center = Offset(sunCenterX, sunCenterY),
              radius = sunRadius
            ),
            center = Offset(sunCenterX, sunCenterY),
            radius = sunRadius
          )
        }

        SkyTimeOfDay.SUNSET -> {
          val sunCenterX = w * 0.55f
          val sunCenterY = h * 0.65f
          val sunRadius = w * 0.20f

          // Warm Twilight Corona
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                Color(0x60FF7043).copy(alpha = celestialGlow * 0.7f),
                Color(0x25D84315),
                Color.Transparent
              ),
              center = Offset(sunCenterX, sunCenterY),
              radius = sunRadius * 2.6f
            ),
            center = Offset(sunCenterX, sunCenterY),
            radius = sunRadius * 2.6f
          )

          // Setting Sun Core
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(Color(0xFFFFEE58), Color(0xFFFF7043), Color(0xFFC2185B)),
              center = Offset(sunCenterX, sunCenterY),
              radius = sunRadius
            ),
            center = Offset(sunCenterX, sunCenterY),
            radius = sunRadius
          )
        }

        SkyTimeOfDay.DAWN -> {
          val sunCenterX = w * 0.65f
          val sunCenterY = h * 0.52f
          val sunRadius = w * 0.17f

          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                Color(0x55F472B6).copy(alpha = celestialGlow * 0.6f),
                Color(0x20FDE047),
                Color.Transparent
              ),
              center = Offset(sunCenterX, sunCenterY),
              radius = sunRadius * 2.4f
            ),
            center = Offset(sunCenterX, sunCenterY),
            radius = sunRadius * 2.4f
          )

          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(Color(0xFFFFF7ED), Color(0xFFF472B6), Color(0xFFFB923C)),
              center = Offset(sunCenterX, sunCenterY),
              radius = sunRadius
            ),
            center = Offset(sunCenterX, sunCenterY),
            radius = sunRadius
          )
        }
      }

      // --- LAYER 3: VOLUMETRIC ANIMATED CLOUDS ---
      if (conditionType != SkyConditionType.CLEAR) {
        val cloudTint = getCloudColor(timeOfDay, conditionType)
        val cloudAlpha = when (conditionType) {
          SkyConditionType.OVERCAST, SkyConditionType.RAIN, SkyConditionType.THUNDERSTORM -> 0.92f
          SkyConditionType.FOG -> 0.75f
          else -> 0.82f
        }

        val nightRimColor = if (timeOfDay == SkyTimeOfDay.NIGHT) Color(0xFFFEF08A).copy(alpha = 0.85f) else null

        // Cloud Layer 1 (Lower, drifting faster)
        val c1X = w * cloudOffset1 - w * 0.2f
        drawVolumetricCloud(
          x = c1X,
          y = h * 0.54f,
          scaleW = w * 0.75f,
          scaleH = h * 0.38f,
          color = cloudTint.copy(alpha = cloudAlpha),
          rimColor = nightRimColor
        )

        // Cloud Layer 2 (Higher, offset parallax)
        val c2X = w * (1.1f - cloudOffset2)
        drawVolumetricCloud(
          x = c2X,
          y = h * 0.36f,
          scaleW = w * 0.65f,
          scaleH = h * 0.32f,
          color = cloudTint.copy(alpha = cloudAlpha * 0.85f),
          rimColor = nightRimColor
        )
      }

      // --- LAYER 4: WEATHER PRECIPITATION PARTICLES ---
      when (conditionType) {
        SkyConditionType.RAIN, SkyConditionType.THUNDERSTORM -> {
          drawRainStreaks(w, h, precipitationProgress)
        }
        SkyConditionType.SNOW -> {
          drawSnowflakes(w, h, precipitationProgress)
        }
        SkyConditionType.FOG -> {
          drawFogBands(w, h, cloudOffset1)
        }
        else -> {}
      }
    }
  }
}

/**
 * Returns the realistic atmospheric gradient representing real-time hour and sky weather.
 */
private fun getAtmosphereGradient(timeOfDay: SkyTimeOfDay, condition: SkyConditionType): Brush {
  return when (timeOfDay) {
    SkyTimeOfDay.NIGHT -> {
      when (condition) {
        SkyConditionType.CLEAR -> Brush.verticalGradient(
          colors = listOf(Color(0xFF030712), Color(0xFF0A1128), Color(0xFF0D1B2A))
        )
        SkyConditionType.RAIN, SkyConditionType.THUNDERSTORM -> Brush.verticalGradient(
          colors = listOf(Color(0xFF05070B), Color(0xFF0F172A), Color(0xFF1E293B))
        )
        else -> Brush.verticalGradient(
          colors = listOf(Color(0xFF04060E), Color(0xFF0B132B), Color(0xFF1C2541))
        )
      }
    }

    SkyTimeOfDay.DAY -> {
      when (condition) {
        SkyConditionType.CLEAR -> Brush.verticalGradient(
          colors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFFBAE6FD))
        )
        SkyConditionType.PARTLY_CLOUDY -> Brush.verticalGradient(
          colors = listOf(Color(0xFF0369A1), Color(0xFF0EA5E9), Color(0xFFE0F2FE))
        )
        SkyConditionType.OVERCAST -> Brush.verticalGradient(
          colors = listOf(Color(0xFF475569), Color(0xFF64748B), Color(0xFF94A3B8))
        )
        SkyConditionType.RAIN, SkyConditionType.THUNDERSTORM -> Brush.verticalGradient(
          colors = listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF475569))
        )
        SkyConditionType.SNOW -> Brush.verticalGradient(
          colors = listOf(Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFFE2E8F0))
        )
        SkyConditionType.FOG -> Brush.verticalGradient(
          colors = listOf(Color(0xFF6B7280), Color(0xFF9CA3AF), Color(0xFFD1D5DB))
        )
      }
    }

    SkyTimeOfDay.SUNSET -> {
      when (condition) {
        SkyConditionType.RAIN, SkyConditionType.THUNDERSTORM -> Brush.verticalGradient(
          colors = listOf(Color(0xFF1C1917), Color(0xFF442727), Color(0xFF78350F))
        )
        else -> Brush.verticalGradient(
          colors = listOf(
            Color(0xFF311042),
            Color(0xFF7C2D12),
            Color(0xFFEA580C),
            Color(0xFFFDE047)
          )
        )
      }
    }

    SkyTimeOfDay.DAWN -> {
      Brush.verticalGradient(
        colors = listOf(
          Color(0xFF1E1B4B),
          Color(0xFF4338CA),
          Color(0xFFDB2777),
          Color(0xFFFDE047)
        )
      )
    }
  }
}

/**
 * Returns the contextual volumetric cloud tint.
 */
private fun getCloudColor(timeOfDay: SkyTimeOfDay, condition: SkyConditionType): Color {
  return when (timeOfDay) {
    SkyTimeOfDay.NIGHT -> when (condition) {
      SkyConditionType.RAIN, SkyConditionType.THUNDERSTORM -> Color(0xFF0F172A)
      else -> Color(0xFF334155)
    }
    SkyTimeOfDay.DAY -> when (condition) {
      SkyConditionType.OVERCAST, SkyConditionType.RAIN, SkyConditionType.THUNDERSTORM -> Color(0xFF64748B)
      else -> Color(0xFFF8FAFC)
    }
    SkyTimeOfDay.SUNSET -> Color(0xFFFED7AA)
    SkyTimeOfDay.DAWN -> Color(0xFFFBCFE8)
  }
}

private fun getNightSkyColor(condition: SkyConditionType): Color {
  return if (condition == SkyConditionType.RAIN || condition == SkyConditionType.THUNDERSTORM) {
    Color(0xFF0A1128)
  } else {
    Color(0xFF0A1128)
  }
}

/**
 * Draws fluffy volumetric cloud shapes composed of overlapping circular geometry.
 */
private fun DrawScope.drawVolumetricCloud(
  x: Float,
  y: Float,
  scaleW: Float,
  scaleH: Float,
  color: Color,
  rimColor: Color? = null
) {
  val cloudPath = Path().apply {
    moveTo(x, y + scaleH * 0.85f)
    cubicTo(
      x + scaleW * 0.08f, y + scaleH * 0.35f,
      x + scaleW * 0.18f, y + scaleH * 0.18f,
      x + scaleW * 0.30f, y + scaleH * 0.26f
    )
    cubicTo(
      x + scaleW * 0.40f, y - scaleH * 0.15f,
      x + scaleW * 0.58f, y - scaleH * 0.18f,
      x + scaleW * 0.68f, y + scaleH * 0.22f
    )
    cubicTo(
      x + scaleW * 0.78f, y + scaleH * 0.05f,
      x + scaleW * 0.92f, y + scaleH * 0.32f,
      x + scaleW, y + scaleH * 0.82f
    )
    cubicTo(
      x + scaleW * 0.75f, y + scaleH * 0.96f,
      x + scaleW * 0.25f, y + scaleH * 0.96f,
      x, y + scaleH * 0.85f
    )
    close()
  }

  if (rimColor != null) {
    drawPath(
      path = cloudPath,
      color = rimColor,
      style = Stroke(width = 2.2f, cap = StrokeCap.Round)
    )
  }

  drawPath(
    path = cloudPath,
    brush = Brush.verticalGradient(
      colors = listOf(
        color,
        color.copy(alpha = color.alpha * 0.88f),
        Color.Transparent
      ),
      startY = y - scaleH * 0.18f,
      endY = y + scaleH
    ),
    style = Fill
  )
}

/**
 * Draws animated diagonal rain streak lines.
 */
private fun DrawScope.drawRainStreaks(w: Float, h: Float, progress: Float) {
  val rainColor = Color(0xC0A5F3FC)
  val streakCount = 8

  for (i in 0 until streakCount) {
    val xBase = (w / streakCount) * i + (w * 0.08f)
    val yOffset = ((progress + (i * 0.14f)) % 1f) * (h + 20f) - 10f

    val start = Offset(xBase, yOffset)
    val end = Offset(xBase - 3.5f, yOffset + 11f)

    drawLine(
      color = rainColor,
      start = start,
      end = end,
      strokeWidth = 1.2f,
      cap = StrokeCap.Round
    )
  }
}

/**
 * Draws gentle falling snowflakes.
 */
private fun DrawScope.drawSnowflakes(w: Float, h: Float, progress: Float) {
  val snowColor = Color.White.copy(alpha = 0.9f)
  val flakes = 9

  for (i in 0 until flakes) {
    val xBase = (w / flakes) * i + (w * 0.05f)
    val sway = kotlin.math.sin(progress * 6.28f + i) * 3f
    val yOffset = ((progress + (i * 0.12f)) % 1f) * (h + 10f)

    drawCircle(
      color = snowColor,
      center = Offset(xBase + sway, yOffset),
      radius = if (i % 2 == 0) 1.6f else 1.1f
    )
  }
}

/**
 * Draws misty horizontal vapor bands for foggy weather.
 */
private fun DrawScope.drawFogBands(w: Float, h: Float, drift: Float) {
  val fogColor = Color.White.copy(alpha = 0.28f)
  val y1 = h * 0.45f
  val y2 = h * 0.70f

  drawOval(
    color = fogColor,
    topLeft = Offset(w * drift - w * 0.2f, y1),
    size = Size(w * 0.9f, h * 0.18f)
  )
  drawOval(
    color = fogColor.copy(alpha = 0.22f),
    topLeft = Offset(w * (1f - drift), y2),
    size = Size(w * 0.85f, h * 0.16f)
  )
}

/**
 * Draws twinkling stars scattered across the night sky.
 */
private fun DrawScope.drawStars(w: Float, h: Float, twinkle: Float) {
  val starPoints = listOf(
    Offset(w * 0.15f, h * 0.18f) to 1.0f,
    Offset(w * 0.35f, h * 0.12f) to 0.7f,
    Offset(w * 0.48f, h * 0.28f) to 0.85f,
    Offset(w * 0.22f, h * 0.42f) to 0.6f,
    Offset(w * 0.82f, h * 0.58f) to 0.75f,
    Offset(w * 0.65f, h * 0.15f) to 0.9f,
    Offset(w * 0.90f, h * 0.22f) to 0.65f
  )

  for ((pos, weight) in starPoints) {
    val alpha = (twinkle * weight).coerceIn(0.1f, 1.0f)
    drawCircle(
      color = Color.White.copy(alpha = alpha),
      center = pos,
      radius = if (weight > 0.8f) 1.3f else 0.9f
    )
  }
}
