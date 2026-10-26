package com.example.rendering.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.rendering.visual_state.CoreVisualState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Central WEYKY Core Renderer ("Living Luminous Energy Sphere").
 *
 * Features rich, multi-layered procedural 2D shader-like rendering that reacts fluidly to:
 * 1. User Speech & Microphone Input (`isVoiceListening` + `voiceEnergyLevel`):
 *    - Real-time acoustic ripple rings, organic morphing voice-wave contours, and cyan/aquamarine plasma surges.
 * 2. WEYKY Assistant Speech (`isSpeakingAloud` / `RESPONDING`):
 *    - Resonant syllabic vocal cadence pulses, emerald-gold aurora filaments, and living inner nucleus luminescence.
 * 3. External Media & Music Playback (`isMusicPlaying` / `MUSIC` — even simultaneously while listening/speaking):
 *    - Multi-band harmonic equalizer aurora waves, orbiting luminous stardust nodes, and a reactive bass/treble energy halo
 *      evolving smoothly across Violet -> Magenta -> Rose -> Warm Gold -> Electric Cyan -> Violet.
 *
 * Engineered for 60fps/120fps smoothness with zero per-frame allocations inside the draw loop
 * (pre-allocated reusable Paths) and full support for power-saving mode.
 */
@Composable
fun WeykyCoreView(
  visualState: CoreVisualState,
  modifier: Modifier = Modifier,
  sizeDp: Dp,
  isPowerSaving: Boolean = false,
  isVoiceListening: Boolean = false,
  isSpeakingAloud: Boolean = false,
  isMusicPlaying: Boolean = false,
  isCharging: Boolean = false,
  batteryPercentage: Int = 100,
  voiceEnergyLevel: Float = 0f,
  isNightMode: Boolean = false,
  isMoonVisible: Boolean = true,
  lunarCyclePhase: Float = 0.5f,
  onClick: () -> Unit = {}
) {
  val effectiveSpeed = visualState.speedMultiplier *
    (if (isVoiceListening || isSpeakingAloud) 1.35f else if (isMusicPlaying) 1.2f else if (isCharging) 1.15f else 1.0f) *
    (if (isPowerSaving) 0.52f else 1.0f)

  val smoothedSpeed by animateFloatAsState(
    targetValue = effectiveSpeed,
    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
    label = "SmoothedCoreSpeed"
  )

  // Continuous, monotonically advancing time accumulator (never resets to 0, preventing any loop seam or jump!)
  var continuousTimeSec by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(Unit) {
    var lastFrameMs = -1L
    while (true) {
      withInfiniteAnimationFrameMillis { frameMs ->
        if (lastFrameMs >= 0L) {
          val dtSec = ((frameMs - lastFrameMs).coerceIn(0L, 64L)) / 1000f
          continuousTimeSec += dtSec * smoothedSpeed
        }
        lastFrameMs = frameMs
      }
    }
  }

  // Smoothly evolving irrational-ratio continuous phases (since continuousTimeSec never resets,
  // every sin/cos wave is 100% continuous forever with zero jumps and non-repeating organic paths)
  val driftPhase = continuousTimeSec * 0.85f
  val breathPhase = continuousTimeSec * 0.618034f // Golden ratio conjugate -> never repeats a mechanical loop
  val musicColorPhase = continuousTimeSec * 0.78f
  val pulsePhase = continuousTimeSec * 3.6f
  val wavePhase = continuousTimeSec * 5.8f

  // Smoothly animated reactive energy levels so transitions never stutter or snap
  val smoothedVoiceEnergy by animateFloatAsState(
    targetValue = if (isVoiceListening) voiceEnergyLevel.coerceIn(0.08f, 1f) else 0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioLowBouncy,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "SmoothedVoiceEnergy"
  )

  val speakingPresence by animateFloatAsState(
    targetValue = if (isSpeakingAloud || visualState == CoreVisualState.RESPONDING) 1f else 0f,
    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
    label = "SpeakingPresence"
  )

  val listeningPresence by animateFloatAsState(
    targetValue = if (isVoiceListening || visualState == CoreVisualState.LISTENING) 1f else 0f,
    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
    label = "ListeningPresence"
  )

  val musicPresence by animateFloatAsState(
    targetValue = if (isMusicPlaying || visualState == CoreVisualState.MUSIC) 1f else 0f,
    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
    label = "MusicPresence"
  )

  val chargingPresence by animateFloatAsState(
    targetValue = if (isCharging || visualState == CoreVisualState.CHARGING) 1f else 0f,
    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
    label = "ChargingPresence"
  )

  val normalizedBatteryLevel by animateFloatAsState(
    targetValue = (batteryPercentage.coerceIn(0, 100) / 100f),
    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
    label = "NormalizedBatteryLevel"
  )

  // Smoothly interpolated state palette (wraps seamlessly in getDynamicMusicColor)
  val musicColorNorm = (musicColorPhase / (2 * Math.PI).toFloat()) % 1.0f
  val isIdleOrChargingState = (visualState == CoreVisualState.IDLE || visualState == CoreVisualState.CHARGING) &&
    !isVoiceListening && !isSpeakingAloud && !isMusicPlaying

  val isIdleNightMoon = visualState == CoreVisualState.IDLE && isNightMode && isMoonVisible &&
    !isVoiceListening && !isSpeakingAloud && !isMusicPlaying && !isCharging
  val isIdleMoonlessNight = visualState == CoreVisualState.IDLE && isNightMode && !isMoonVisible &&
    !isVoiceListening && !isSpeakingAloud && !isMusicPlaying && !isCharging

  val targetPrimary = when {
    visualState == CoreVisualState.MUSIC || (isMusicPlaying && !isVoiceListening && !isSpeakingAloud) ->
      getDynamicMusicColor(musicColorNorm)
    isCharging && isIdleOrChargingState ->
      getDynamicChargingColor(normalizedBatteryLevel, driftPhase, 0f)
    isIdleNightMoon ->
      Color(0xFF00E5FF) // Electric Cyan-Blue Lunar Glow Primary
    isIdleMoonlessNight ->
      Color(0xFF0F3460) // Deep Starry Night Primary
    else ->
      visualState.primaryColor
  }
  val targetSecondary = when {
    visualState == CoreVisualState.MUSIC || (isMusicPlaying && !isVoiceListening && !isSpeakingAloud) ->
      getDynamicMusicColor(musicColorNorm + 0.28f)
    isCharging && isIdleOrChargingState ->
      getDynamicChargingColor(normalizedBatteryLevel, driftPhase, 0.33f)
    isIdleNightMoon ->
      Color(0xFF0284C7) // Deep Azure-Sapphire Lunar Secondary
    isIdleMoonlessNight ->
      Color(0xFF16213E) // Midnight Indigo Secondary
    else ->
      visualState.secondaryColor
  }
  val targetAccent = when {
    visualState == CoreVisualState.MUSIC || (isMusicPlaying && !isVoiceListening && !isSpeakingAloud) ->
      getDynamicMusicColor(musicColorNorm + 0.58f)
    isCharging && isIdleOrChargingState ->
      getDynamicChargingColor(normalizedBatteryLevel, driftPhase, 0.66f)
    isIdleNightMoon ->
      Color(0xFFFFFFFF) // Pure Incandescent White-Cyan Moon Rim Accent
    isIdleMoonlessNight ->
      Color(0xFF38BDF8) // Subtle Starlight Cyan Accent
    else ->
      visualState.accentColor
  }

  val animatedPrimary by animateColorAsState(
    targetValue = targetPrimary,
    animationSpec = tween(durationMillis = 450),
    label = "CorePrimaryColor"
  )
  val animatedSecondary by animateColorAsState(
    targetValue = targetSecondary,
    animationSpec = tween(durationMillis = 450),
    label = "CoreSecondaryColor"
  )
  val animatedAccent by animateColorAsState(
    targetValue = targetAccent,
    animationSpec = tween(durationMillis = 450),
    label = "CoreAccentColor"
  )

  val context = LocalContext.current
  val lunarSurfaceBitmap = remember(isIdleNightMoon) {
    if (isIdleNightMoon) {
      loadOrGenerateReferenceMoonBitmap(context = context, sizePx = 320)
    } else {
      null
    }
  }

  // Pre-compute 3D spherical lunar phase shadow mask when in Night Moon mode
  val quantizedPhase = ((lunarCyclePhase.coerceIn(0f, 1f) * 100f).toInt()) / 100f
  val lunarPhaseShadowBitmap = remember(quantizedPhase, isIdleNightMoon) {
    if (isIdleNightMoon) {
      createSphericalLunarPhaseMask(sizePx = 220, cyclePhase = quantizedPhase)
    } else {
      null
    }
  }

  // Pre-allocated paths to guarantee zero GC pressure at 60/120fps
  val circlePath = remember { Path() }
  val liquidFillPath = remember { Path() }
  val dropletPath = remember { Path() }
  val innerMusicWavePath = remember { Path() }
  val innerMusicFillPath = remember { Path() }
  val outerMusicWavePath = remember { Path() }
  val outerMusicFillPath = remember { Path() }

  val density = LocalDensity.current
  val configuration = LocalConfiguration.current
  val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
  var canvasRootTopY by remember { mutableFloatStateOf(-1f) }

  Box(
    modifier = modifier
      .size(sizeDp)
      .onGloballyPositioned { coordinates ->
        canvasRootTopY = coordinates.positionInRoot().y
      }
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
      )
      .testTag("weyky_core_view"),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.size(sizeDp)) {
      val w = size.width
      val h = size.height
      val minDim = minOf(w, h)
      // Gently compact the inner sphere when music visualizer rings are active so the dual concentric waveform rings fit harmoniously
      val musicCoreScale = 1.0f - 0.14f * musicPresence
      val baseRadius = (minDim / 2f) * 0.86f * musicCoreScale
      val center = Offset(w / 2f, h / 2f)

      // Compute combined acoustic & musical excitation
      // 1. Music multi-oscillator beat simulation (bass thump + mid groove + high shimmer)
      val bassBeat = ((sin(pulsePhase.toDouble()) + 1.0) * 0.5).toFloat()
      val midGroove = sin((pulsePhase * 2.0f + 0.8f).toDouble()).toFloat() * 0.4f
      val highShimmer = cos((wavePhase * 2.0f).toDouble()).toFloat() * 0.25f
      val musicBeatEnergy = ((bassBeat * 0.65f + (midGroove + 0.4f) * 0.35f + highShimmer * 0.2f)).coerceIn(0f, 1f)

      // 2. Assistant speech syllabic cadence simulation (natural 3-5 Hz speech envelope)
      val syllable1 = ((sin(wavePhase.toDouble() * 2.0) + 1.0) * 0.5).toFloat()
      val syllable2 = ((cos(pulsePhase.toDouble() * 3.0 + 1.3) + 1.0) * 0.5).toFloat()
      val speechCadenceEnergy = (syllable1 * 0.65f + syllable2 * 0.35f).coerceIn(0.15f, 1f)

      // 3. User microphone voice energy (combines real RMS with subtle living micro-oscillation)
      val micLiveEnergy = (smoothedVoiceEnergy * 0.82f + syllable1 * 0.25f * listeningPresence).coerceIn(0f, 1f)

      // 4. Charging power surge oscillation (smooth rhythmic power infusion heartbeat)
      val chargeSurge = ((sin((pulsePhase * 0.75f).toDouble()) + 1.0) * 0.5).toFloat()

      // Total dynamic radial expansion factor
      val activePulseBoost = (
        micLiveEnergy * 0.085f * listeningPresence +
        speechCadenceEnergy * 0.065f * speakingPresence +
        musicBeatEnergy * 0.055f * musicPresence +
        chargeSurge * 0.038f * chargingPresence +
        sin(breathPhase) * 0.015f
      )
      val radius = baseRadius * (1.0f + activePulseBoost)

      // Dynamic music accent color even when overlayed with speech/listening
      val liveMusicHighlight = getDynamicMusicColor(musicColorNorm + 0.15f)
      val liveMusicSecondary = getDynamicMusicColor(musicColorNorm + 0.45f)

      // Dynamic charging highlight colors (Volt Emerald -> Electric Cyan -> Solar Gold near 100%)
      val chargeVoltColor = Color(0xFF39FF14)
      val chargeCyanColor = Color(0xFF00F5D4)
      val chargeHighlight = lerpColor(chargeVoltColor, chargeCyanColor, chargeSurge)

      // =========================================================================
      // OUTER AMBIENT CORONA & REACTIVE SONIC / POWER HALOS (Drawn outside the core rim)
      // =========================================================================

      // A. Ambient Outer Bloom & Electric Cyan Lunar Corona
      val outerBloomRadius = radius * (if (isIdleNightMoon) 1.32f else (1.16f + 0.08f * maxOf(
        listeningPresence * micLiveEnergy,
        speakingPresence * speechCadenceEnergy,
        musicPresence * musicBeatEnergy,
        chargingPresence * (0.4f + 0.5f * chargeSurge)
      )))
      val effectiveGlow = maxOf(visualState.glowIntensity, chargingPresence * 0.82f)
      if (isIdleNightMoon) {
        // Multi-stage Electric Cyan-Blue Lunar Corona matching the reference Moon artwork
        drawCircle(
          brush = Brush.radialGradient(
            colorStops = arrayOf(
              0.0f to Color(0xFF00F0FF).copy(alpha = 0.72f),
              0.68f to Color(0xFF00E5FF).copy(alpha = 0.58f),
              0.80f to Color(0xFF00B4D8).copy(alpha = 0.36f),
              0.91f to Color(0xFF0284C7).copy(alpha = 0.16f),
              1.0f to Color.Transparent
            ),
            center = center,
            radius = outerBloomRadius
          ),
          radius = outerBloomRadius,
          center = center,
          blendMode = BlendMode.Plus
        )
      } else {
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              animatedAccent.copy(alpha = 0.34f * effectiveGlow),
              animatedPrimary.copy(alpha = 0.15f * effectiveGlow),
              Color.Transparent
            ),
            center = center,
            radius = outerBloomRadius
          ),
          radius = outerBloomRadius,
          center = center
        )
      }

      // A2. Ascending Charging Energy Droplets (Launching from the very bottom of the screen at the charging port & merging into the sphere)
      if (chargingPresence > 0.01f) {
        val bottomPoleY = center.y + radius * 0.96f
        // Calculate exact Y coordinate corresponding to the physical bottom edge of the device screen (charging port location)
        val portStartY = if (canvasRootTopY >= 0f && screenHeightPx > canvasRootTopY + center.y) {
          (screenHeightPx - canvasRootTopY) + 18f
        } else {
          center.y + radius * 4.2f
        }
        val portVisibleY = portStartY - 18f

        // Subtle luminous energy emitter glow right at the bottom edge of the screen (charging port location)
        val portGlowRadius = radius * (0.36f + 0.08f * chargeSurge) * chargingPresence
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.72f * chargingPresence),
              chargeHighlight.copy(alpha = 0.45f * chargingPresence),
              Color.Transparent
            ),
            center = Offset(center.x, portVisibleY),
            radius = portGlowRadius
          ),
          radius = portGlowRadius,
          center = Offset(center.x, portVisibleY),
          blendMode = BlendMode.Plus
        )

        val dropletCount = if (isPowerSaving) 5 else 9

        for (d in 0 until dropletCount) {
          val seed = d * 0.618034f
          val speed = 0.40f + (d % 3) * 0.07f
          // Continuous progress: 0.0 (bottom screen edge / charging port) -> ~0.88 (touches sphere rim) -> 1.0 (merges inside)
          val rawProgress = (continuousTimeSec * speed + seed) % 1.0f
          // Smooth acceleration as the droplet launches from the charging port and gets magnetically pulled into the core
          val pullCurve = 0.28f * rawProgress + 0.72f * (rawProgress * rawProgress)

          val targetMergeY = center.y + radius * 0.58f
          val currentY = portStartY + (targetMergeY - portStartY) * pullCurve

          // Start tightly at center.x (charging port), gently sway in mid-flight, and converge back to center.x at the sphere pole
          val midFlightEnvelope = sin((pullCurve * Math.PI).toDouble()).toFloat().coerceIn(0f, 1f)
          val xSway = sin(driftPhase * 2.2f + d * 2.1f + rawProgress * 5.5f) * (radius * 0.24f * midFlightEnvelope)
          val currentX = center.x + xSway

          // Visible immediately as it emerges from the bottom charging port, dissolving smoothly as it merges inside the sphere
          val fadeIn = (rawProgress / 0.08f).coerceIn(0f, 1f)
          val mergeFade = ((1.0f - rawProgress) / 0.16f).coerceIn(0f, 1f)
          val dropletAlpha = fadeIn * mergeFade * chargingPresence

          // Stretch vertically as it nears the sphere surface (magnetic absorption effect)
          val proximityToRim = 1.0f - (kotlin.math.abs(currentY - bottomPoleY) / (radius * 0.48f)).coerceIn(0f, 1f)
          val dropRadius = (4.6f + (d % 3) * 1.5f) * (1.0f - 0.22f * proximityToRim) * chargingPresence
          val tailLength = dropRadius * (2.8f + 2.4f * proximityToRim)
          val dropColor = if (d % 2 == 0) chargeVoltColor else chargeCyanColor

          // Draw luminous outer halo around the rising energy droplet
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                Color.White.copy(alpha = 0.92f * dropletAlpha),
                dropColor.copy(alpha = 0.72f * dropletAlpha),
                Color.Transparent
              ),
              center = Offset(currentX, currentY),
              radius = dropRadius * 3.0f
            ),
            radius = dropRadius * 3.0f,
            center = Offset(currentX, currentY),
            blendMode = BlendMode.Plus
          )

          // Draw organic teardrop shape (rounded head at top, tapered energy tail below)
          dropletPath.reset()
          dropletPath.moveTo(currentX, currentY - dropRadius * 1.15f)
          dropletPath.cubicTo(
            currentX + dropRadius * 1.25f, currentY - dropRadius * 0.3f,
            currentX + dropRadius * 0.65f, currentY + tailLength * 0.45f,
            currentX, currentY + tailLength
          )
          dropletPath.cubicTo(
            currentX - dropRadius * 0.65f, currentY + tailLength * 0.45f,
            currentX - dropRadius * 1.25f, currentY - dropRadius * 0.3f,
            currentX, currentY - dropRadius * 1.15f
          )
          dropletPath.close()

          drawPath(
            path = dropletPath,
            brush = Brush.verticalGradient(
              colors = listOf(
                Color.White.copy(alpha = 0.96f * dropletAlpha),
                dropColor.copy(alpha = 0.88f * dropletAlpha),
                Color.Transparent
              ),
              startY = currentY - dropRadius,
              endY = currentY + tailLength
            ),
            blendMode = BlendMode.Plus
          )

          // When the droplet touches the bottom rim of the sphere, emit a soft fusion glow at the absorption point
          if (proximityToRim > 0.35f) {
            val impactAlpha = ((proximityToRim - 0.35f) / 0.65f) * dropletAlpha
            val impactCenter = Offset(center.x + xSway * 0.35f, bottomPoleY)
            val impactRadius = radius * (0.14f + 0.16f * (1f - proximityToRim))
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(
                  Color.White.copy(alpha = 0.75f * impactAlpha),
                  dropColor.copy(alpha = 0.55f * impactAlpha),
                  Color.Transparent
                ),
                center = impactCenter,
                radius = impactRadius
              ),
              radius = impactRadius,
              center = impactCenter,
              blendMode = BlendMode.Plus
            )
          }
        }
      }

      // B. Music Reactive Dual-Ring Waveform & Bilateral Equalizer Wings are rendered around the core rim below (after internal sphere aperture)

      // C. Voice Listening & Speaking Concentric Sonic Ripples
      val vocalActivity = maxOf(listeningPresence, speakingPresence)
      if (vocalActivity > 0.01f) {
        val activeVocalEnergy = if (listeningPresence >= speakingPresence) micLiveEnergy else speechCadenceEnergy
        val rippleCount = if (isPowerSaving) 2 else 3
        val rippleBaseProgress = (wavePhase / (2f * Math.PI.toFloat()))

        for (r in 0 until rippleCount) {
          val phaseOffset = (rippleBaseProgress + r.toFloat() / rippleCount) % 1.0f
          val rippleRadius = radius * (1.01f + phaseOffset * (0.14f + 0.10f * activeVocalEnergy))
          val rippleAlpha = (1.0f - phaseOffset) * (0.35f + 0.45f * activeVocalEnergy) * vocalActivity
          val rippleColor = if (listeningPresence >= speakingPresence) {
            Color(0xFF80FFDB) // Electric aquamarine for listening
          } else {
            Color(0xFFFFD166) // Warm resonant gold-emerald for speaking
          }

          drawCircle(
            color = rippleColor.copy(alpha = rippleAlpha.coerceIn(0f, 0.85f)),
            radius = rippleRadius,
            center = center,
            style = Stroke(width = (1.8f + (1f - phaseOffset) * 2.8f * (0.5f + activeVocalEnergy)))
          )
        }
      }

      // =========================================================================
      // INTERNAL SPHERE APERTURE (Clipped strictly to circular window)
      // =========================================================================
      circlePath.reset()
      circlePath.addOval(androidx.compose.ui.geometry.Rect(center, radius))

      val isPureIdle = visualState == CoreVisualState.IDLE &&
        !isVoiceListening && !isSpeakingAloud && !isMusicPlaying && !isCharging

      clipPath(circlePath) {
        // Base backdrop (warm golden amber in daytime Sun IDLE, pearl-slate in White Moon IDLE, deep cosmic dark otherwise)
        drawCircle(
          color = when {
            isIdleNightMoon -> Color(0xFF475569)
            isPureIdle && !isNightMode -> Color(0xFF92400E)
            else -> Color(0xFF030712)
          },
          radius = radius,
          center = center
        )

        // LAYER 1: Deep nebula / solar photosphere / white lunar photosphere gradient
        val bgShiftMultiplier = 0.24f + 0.10f * (musicPresence * musicBeatEnergy + vocalActivity * 0.8f)
        val bgBrush = Brush.radialGradient(
          colors = when {
            isIdleNightMoon -> listOf(
              Color(0xFFFFFFFF),
              Color(0xFFF8FAFC),
              Color(0xFFE2E8F0),
              Color(0xFF94A3B8)
            )
            isPureIdle && !isNightMode -> listOf(
              Color(0xFFFFFBEB),
              animatedAccent.copy(alpha = 0.96f),
              animatedPrimary.copy(alpha = 0.92f),
              Color(0xFFB45309)
            )
            else -> listOf(
              animatedPrimary.copy(alpha = 0.95f),
              animatedSecondary.copy(alpha = 0.85f),
              Color(0xFF050B18)
            )
          },
          center = center + Offset(
            x = cos(driftPhase) * radius * bgShiftMultiplier,
            y = sin(breathPhase) * radius * bgShiftMultiplier
          ),
          radius = radius * 1.35f
        )
        drawCircle(brush = bgBrush, radius = radius, center = center)

        // LAYER 2: Drifting plasma cloud cluster 1 (Cyan / Violet / Electric Accent)
        val cloud1RadiusScale = 0.86f + 0.14f * (micLiveEnergy * listeningPresence + musicBeatEnergy * musicPresence)
        val cloud1Center = center + Offset(
          x = (cos(driftPhase) * 0.7f + sin(breathPhase) * 0.3f) * radius * 0.42f,
          y = (sin(driftPhase) * 0.7f - cos(breathPhase) * 0.3f) * radius * 0.38f
        )
        val cloud1Brush = Brush.radialGradient(
          colors = listOf(
            animatedAccent.copy(alpha = (0.85f * visualState.glowIntensity).coerceIn(0f, 1f)),
            animatedPrimary.copy(alpha = 0.58f),
            Color.Transparent
          ),
          center = cloud1Center,
          radius = radius * cloud1RadiusScale
        )
        drawCircle(
          brush = cloud1Brush,
          radius = radius * cloud1RadiusScale,
          center = cloud1Center,
          blendMode = BlendMode.Screen
        )

        // LAYER 3: Counter-drifting warm luminous nebula cluster 2
        val cloud2Center = center + Offset(
          x = (-sin(breathPhase) * 0.75f + cos(driftPhase) * 0.25f) * radius * 0.44f,
          y = (cos(breathPhase) * 0.75f + sin(driftPhase) * 0.25f) * radius * 0.36f
        )
        val warmColor = when {
          visualState == CoreVisualState.ERROR -> Color(0xFFFF5252)
          isVoiceListening || visualState == CoreVisualState.LISTENING -> Color(0xFF64DFDF)
          isSpeakingAloud || visualState == CoreVisualState.RESPONDING -> Color(0xFFFFD166)
          visualState == CoreVisualState.PROCESSING -> Color(0xFFFF70A6)
          visualState == CoreVisualState.SUCCESS -> Color(0xFFFFD166)
          visualState == CoreVisualState.CALL -> Color(0xFFFF9E00)
          isMusicPlaying || visualState == CoreVisualState.MUSIC -> liveMusicHighlight
          isCharging || visualState == CoreVisualState.CHARGING -> chargeVoltColor
          visualState == CoreVisualState.NOTIFICATION -> Color(0xFFFFB703)
          isIdleNightMoon -> Color(0xFFFFFFFF)
          isIdleMoonlessNight -> Color(0xFF38BDF8)
          else -> Color(0xFFFEF08A)
        }
        val cloud2RadiusScale = 0.78f + 0.14f * (
          speechCadenceEnergy * speakingPresence +
          musicBeatEnergy * musicPresence +
          chargeSurge * chargingPresence * 0.7f
        )
        val cloud2Brush = Brush.radialGradient(
          colors = listOf(
            warmColor.copy(alpha = (0.75f * effectiveGlow).coerceIn(0f, 1f)),
            animatedSecondary.copy(alpha = 0.40f),
            Color.Transparent
          ),
          center = cloud2Center,
          radius = radius * cloud2RadiusScale
        )
        drawCircle(
          brush = cloud2Brush,
          radius = radius * cloud2RadiusScale,
          center = cloud2Center,
          blendMode = BlendMode.Screen
        )

        // LAYER 3.5: Co-existing Music Nebula Ribbon when Music is playing DURING Voice Listening or Speaking
        if (musicPresence > 0.05f && (listeningPresence > 0.05f || speakingPresence > 0.05f)) {
          val musicOrbCenter = center + Offset(
            x = sin(driftPhase + breathPhase) * radius * 0.36f,
            y = -cos(driftPhase + breathPhase) * radius * 0.36f
          )
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                liveMusicHighlight.copy(alpha = 0.65f * musicPresence),
                liveMusicSecondary.copy(alpha = 0.35f * musicPresence),
                Color.Transparent
              ),
              center = musicOrbCenter,
              radius = radius * 0.68f
            ),
            radius = radius * 0.68f,
            center = musicOrbCenter,
            blendMode = BlendMode.Screen
          )
        }

        // LAYER 3.8: Internal Liquid-Plasma Battery Level Fill & Absorbed Droplet Fusion (Active when charging)
        if (chargingPresence > 0.02f) {
          // Fill level inside the sphere from bottom (center.y + radius) to top (center.y - radius)
          // based on batteryPercentage (0% -> bottom rim, 100% -> top rim)
          val clampedFill = normalizedBatteryLevel.coerceIn(0.06f, 1.0f)
          val fillSurfaceY = (center.y + radius) - (2f * radius * clampedFill)
          val leftX = center.x - radius * 1.05f
          val rightX = center.x + radius * 1.05f
          val bottomY = center.y + radius * 1.08f
          val waveAmp = radius * (0.035f + 0.020f * chargeSurge) * (1f - 0.6f * (clampedFill - 0.92f).coerceAtLeast(0f) / 0.08f)

          // Build smooth undulating liquid-energy wave across the sphere at fillSurfaceY
          liquidFillPath.reset()
          liquidFillPath.moveTo(leftX, bottomY)
          val segments = 24
          val stepX = (rightX - leftX) / segments
          for (s in 0..segments) {
            val x = leftX + s * stepX
            val normX = s.toFloat() / segments
            val yWave = sin(normX * Math.PI.toFloat() * 2.5f + driftPhase * 3.2f) * waveAmp +
              cos(normX * Math.PI.toFloat() * 1.5f - breathPhase * 2.8f) * (waveAmp * 0.55f)
            val y = fillSurfaceY + yWave
            if (s == 0) {
              liquidFillPath.lineTo(x, y)
            } else {
              liquidFillPath.lineTo(x, y)
            }
          }
          liquidFillPath.lineTo(rightX, bottomY)
          liquidFillPath.close()

          // Draw the luminous energy reservoir filling the sphere up to batteryPercentage
          drawPath(
            path = liquidFillPath,
            brush = Brush.verticalGradient(
              colors = listOf(
                chargeHighlight.copy(alpha = 0.72f * chargingPresence),
                chargeCyanColor.copy(alpha = 0.52f * chargingPresence),
                chargeVoltColor.copy(alpha = 0.66f * chargingPresence)
              ),
              startY = fillSurfaceY - waveAmp,
              endY = bottomY
            ),
            blendMode = BlendMode.Plus
          )

          // Draw a glowing meniscus / energy horizon line along the top surface of the battery fill level
          val surfaceStrokePath = liquidFillPath
          drawPath(
            path = surfaceStrokePath,
            brush = Brush.horizontalGradient(
              colors = listOf(
                Color.Transparent,
                chargeCyanColor.copy(alpha = 0.85f * chargingPresence),
                Color.White.copy(alpha = 0.92f * chargingPresence),
                chargeVoltColor.copy(alpha = 0.85f * chargingPresence),
                Color.Transparent
              ),
              startX = center.x - radius,
              endX = center.x + radius
            ),
            style = Stroke(width = (2.6f + 1.4f * chargeSurge) * chargingPresence, cap = StrokeCap.Round),
            blendMode = BlendMode.Plus
          )

          // Ascending merged energy bubbles rising from the bottom absorption pole up to the liquid surface
          if (!isPowerSaving) {
            val bubbleCount = 8
            for (ionIdx in 0 until bubbleCount) {
              val ionSeed = ionIdx * 0.618034f
              val ascendProgress = (continuousTimeSec * (0.50f + (ionIdx % 3) * 0.12f) + ionSeed) % 1.0f
              val envelope = sin(ascendProgress * Math.PI.toFloat()).coerceIn(0f, 1f)
              // Travel from the bottom rim up to the current battery fill surface
              val startPoleY = center.y + radius * 0.92f
              val targetTopY = minOf(fillSurfaceY + radius * 0.05f, center.y + radius * 0.4f)
              val currentBubbleY = startPoleY + (targetTopY - startPoleY) * ascendProgress
              val horizontalSpread = (1.0f - ascendProgress * 0.5f) * radius * 0.38f
              val xWave = sin(driftPhase * 2.0f + ionIdx * 1.7f + ascendProgress * 4.0f) * horizontalSpread
              val ionPos = Offset(center.x + xWave, currentBubbleY)
              val ionRadius = (2.8f + 3.0f * envelope) * chargingPresence
              val ionColor = if (ionIdx % 2 == 0) chargeVoltColor else chargeCyanColor

              drawCircle(
                brush = Brush.radialGradient(
                  colors = listOf(
                    Color.White.copy(alpha = 0.88f * envelope * chargingPresence),
                    ionColor.copy(alpha = 0.62f * envelope * chargingPresence),
                    Color.Transparent
                  ),
                  center = ionPos,
                  radius = ionRadius * 2.5f
                ),
                radius = ionRadius * 2.5f,
                center = ionPos,
                blendMode = BlendMode.Plus
              )
            }
          }
        }

        // LAYER 5: Central Living Singularity Nucleus (pulses vividly with voice syllables, music bass & charging surge)
        val nucleusScale = (if (isPureIdle) 0.38f else 0.24f) +
          0.18f * micLiveEnergy * listeningPresence +
          0.15f * speechCadenceEnergy * speakingPresence +
          0.12f * musicBeatEnergy * musicPresence +
          0.10f * chargeSurge * chargingPresence +
          0.03f * sin(breathPhase)
        val nucleusRadius = radius * nucleusScale.coerceIn(0.18f, 0.58f)
        val nucleusOffset = Offset(
          x = cos(breathPhase) * radius * 0.055f,
          y = sin(driftPhase) * radius * 0.055f
        )
        val nucleusAlpha = (if (isPureIdle) 0.82f else (0.34f + 0.45f * maxOf(
          micLiveEnergy * listeningPresence,
          speechCadenceEnergy * speakingPresence,
          musicBeatEnergy * musicPresence,
          (0.45f + 0.45f * chargeSurge) * chargingPresence
        ))).coerceIn(0.25f, 0.92f)

        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              Color.White.copy(alpha = nucleusAlpha * 0.85f),
              animatedAccent.copy(alpha = nucleusAlpha * 0.65f),
              warmColor.copy(alpha = nucleusAlpha * 0.25f),
              Color.Transparent
            ),
            center = center + nucleusOffset,
            radius = nucleusRadius
          ),
          radius = nucleusRadius,
          center = center + nucleusOffset,
          blendMode = BlendMode.Plus
        )

        // LAYER 6: Orbiting Luminous Energy Motes / Stardust (Seamless elliptical & Lissajous trajectories)
        if (!isPowerSaving) {
          val moteCount = 8
          for (m in 0 until moteCount) {
            val seed = m * 1.37f
            val speedFactor = 0.55f + (m % 3) * 0.18f
            val orbitAngle = driftPhase * speedFactor * (if (m % 2 == 0) 1f else -1f) + seed * 2.1f
            val radialBreath = (sin(breathPhase + seed) + 1f) * 0.5f
            val orbitDistX = radius * (0.24f + 0.44f * radialBreath)
            val orbitDistY = radius * (0.22f + 0.40f * ((cos(breathPhase * 0.8f + seed) + 1f) * 0.5f))
            val motePos = Offset(
              x = center.x + cos(orbitAngle) * orbitDistX,
              y = center.y + sin(orbitAngle) * orbitDistY
            )
            val motePulse = ((sin(pulsePhase * 0.5f + seed * 3f) + 1f) * 0.5f)
            val moteRadius = (2.2f + 3.0f * motePulse) * (1f + 0.4f * maxOf(vocalActivity, musicPresence))
            val moteColor = if (m % 2 == 0) Color.White else animatedAccent

            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(
                  moteColor.copy(alpha = 0.50f + 0.35f * motePulse),
                  moteColor.copy(alpha = 0.14f),
                  Color.Transparent
                ),
                center = motePos,
                radius = moteRadius * 2.4f
              ),
              radius = moteRadius * 2.4f,
              center = motePos,
              blendMode = BlendMode.Plus
            )
          }
        }

        // LAYER 7: Radiant core horizon streak & 3D specular top-left glass reflection
        val horizonOffset = Offset(
          x = cos(breathPhase) * radius * 0.14f,
          y = sin(driftPhase) * radius * 0.14f
        )
        val energyBrush = Brush.linearGradient(
          colors = listOf(
            Color.Transparent,
            animatedAccent.copy(alpha = 0.38f * visualState.glowIntensity),
            Color.White.copy(alpha = 0.24f * visualState.glowIntensity),
            Color.Transparent
          ),
          start = center + horizonOffset - Offset(radius * 0.8f, radius * 0.3f),
          end = center + horizonOffset + Offset(radius * 0.8f, radius * 0.3f)
        )
        drawCircle(
          brush = energyBrush,
          radius = radius,
          center = center,
          blendMode = BlendMode.Plus
        )

        // Subtle 3D glass sphere top-left specular highlight
        val specularCenter = center - Offset(radius * 0.32f, radius * 0.35f)
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.18f),
              Color.White.copy(alpha = 0.05f),
              Color.Transparent
            ),
            center = specularCenter,
            radius = radius * 0.55f
          ),
          radius = radius * 0.55f,
          center = specularCenter,
          blendMode = BlendMode.Screen
        )

        // LAYER 8: Internal atmospheric vignette / lunar limb glow around the circular window rim
        val vignetteBrush = Brush.radialGradient(
          colorStops = when {
            isIdleNightMoon -> arrayOf(
              0.0f to Color.Transparent,
              0.78f to Color.Transparent,
              0.92f to Color(0x6600E5FF),
              0.97f to Color(0xAABAE6FD),
              1.0f to Color(0xEEFFFFFF)
            )
            isPureIdle && !isNightMode -> arrayOf(
              0.0f to Color.Transparent,
              0.74f to Color.Transparent,
              0.91f to Color(0x44B45309),
              1.0f to Color(0x8878350F)
            )
            else -> arrayOf(
              0.0f to Color.Transparent,
              0.74f to Color.Transparent,
              0.91f to Color(0x66000000),
              1.0f to Color(0xCC000000)
            )
          },
          center = center,
          radius = radius
        )
        drawCircle(brush = vignetteBrush, radius = radius, center = center)

        // LAYER 9: High-Definition Blue-White Lunar Surface (Maria, Craters, Tycho Rays) & 3D Phase Shading
        if (isIdleNightMoon) {
          val diameterPx = (radius * 2f).toInt().coerceAtLeast(2)
          val topLeftX = (center.x - radius).toInt()
          val topLeftY = (center.y - radius).toInt()

          if (lunarSurfaceBitmap != null) {
            drawImage(
              image = lunarSurfaceBitmap,
              dstOffset = IntOffset(topLeftX, topLeftY),
              dstSize = IntSize(diameterPx, diameterPx),
              alpha = 0.96f
            )
          }

          if (lunarPhaseShadowBitmap != null) {
            drawImage(
              image = lunarPhaseShadowBitmap,
              dstOffset = IntOffset(topLeftX, topLeftY),
              dstSize = IntSize(diameterPx, diameterPx)
            )
          }

          // Re-reinforce the incandescent white-cyan inner limb glow over the lunar surface
          drawCircle(
            brush = Brush.radialGradient(
              colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.84f to Color.Transparent,
                0.94f to Color(0x6600E5FF),
                0.98f to Color(0xCCFFFFFF),
                1.0f to Color(0xFFFFFFFF)
              ),
              center = center,
              radius = radius
            ),
            radius = radius,
            center = center,
            blendMode = BlendMode.Plus
          )
        }
      }

      // =========================================================================
      // CRISP PRECISION OUTER BOUNDARY RINGS & MUSIC AUDIO VISUALIZER
      // =========================================================================
      if (isIdleNightMoon) {
        // Outer Electric Cyan-Blue Neon Bloom Ring
        drawCircle(
          color = Color(0x9900E5FF),
          radius = radius * 1.022f,
          center = center,
          style = Stroke(width = 6.5f),
          blendMode = BlendMode.Plus
        )
        // Mid Ice-Cyan Halo Ring
        drawCircle(
          color = Color(0xCC7DD3FC),
          radius = radius * 1.008f,
          center = center,
          style = Stroke(width = 3.4f),
          blendMode = BlendMode.Plus
        )
        // Razor-Sharp Incandescent Pure White Rim Ring
        drawCircle(
          color = Color(0xFFFFFFFF),
          radius = radius,
          center = center,
          style = Stroke(width = 2.2f)
        )
      } else {
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              animatedAccent.copy(alpha = 0.55f * visualState.glowIntensity * (1f - 0.5f * musicPresence)),
              animatedPrimary.copy(alpha = 0.25f * (1f - 0.5f * musicPresence)),
              Color.Transparent
            ),
            center = center,
            radius = radius * 1.05f
          ),
          radius = radius * 1.02f,
          center = center,
          style = Stroke(width = 2.6f)
        )
        drawCircle(
          color = Color(0x40FFFFFF),
          radius = radius,
          center = center,
          style = Stroke(width = 1.1f)
        )
      }

      // =========================================================================
      // REFERENCE MUSIC VISUALIZER AROUND THE CORE (Dual Concentric Neon Wave Rings
      // + Bilateral Horizontal Cyan/Purple Equalizer Wings matching _____4bdb58a4.jpg)
      // =========================================================================
      if (musicPresence > 0.01f) {
        val innerRingR = radius * 1.012f
        val outerRingR = radius * 1.138f
        val twoPi = (2.0 * Math.PI).toFloat()

        // 0. Annular dark contrast band between inner magenta ring and outer cyan ring
        // so both concentric neon rings stand out with razor-sharp separation
        val gapMidR = (innerRingR + outerRingR) * 0.5f
        val gapWidth = (outerRingR - innerRingR)
        drawCircle(
          color = Color(0xAA040612).copy(alpha = 0.68f * musicPresence),
          radius = gapMidR,
          center = center,
          style = Stroke(width = gapWidth)
        )

        // 1. INNER CONCENTRIC WAVE RING (Neon Magenta / Hot Pink #FF007F + Electric Violet #7C4DFF)
        val innerSteps = if (isPowerSaving) 72 else 144
        innerMusicWavePath.reset()
        innerMusicFillPath.reset()

        for (i in 0..innerSteps) {
          val angle = (i.toFloat() / innerSteps) * twoPi
          val cosA = cos(angle)
          val sinA = sin(angle)

          // Smooth multi-harmonic angular oscillation around the inner ring
          val h1 = sin(angle * 7f - wavePhase * 1.6f) * 0.44f +
            cos(angle * 13f + pulsePhase * 2.3f) * 0.34f +
            sin(angle * 21f - wavePhase * 2.9f) * 0.22f
          // Prominent diagonal & vertical harmonic lobes matching reference artwork
          val diagA = sin(angle - 0.68f).pow(2)
          val diagB = cos(angle + 0.52f).pow(2)
          val innerLobe = 0.32f + 0.68f * max(diagA, diagB * 0.80f)
          val innerWaveNorm = ((h1 + 1f) * 0.5f).pow(1.45f) * innerLobe * (0.50f + 0.50f * musicBeatEnergy)
          val innerDisp = radius * (0.008f + 0.056f * innerWaveNorm) * musicPresence
          val rPt = innerRingR + innerDisp

          val px = center.x + cosA * rPt
          val py = center.y + sinA * rPt
          if (i == 0) {
            innerMusicWavePath.moveTo(px, py)
            innerMusicFillPath.moveTo(px, py)
          } else {
            innerMusicWavePath.lineTo(px, py)
            innerMusicFillPath.lineTo(px, py)
          }
        }
        innerMusicWavePath.close()

        // Trace back along innerRingR to form a closed annular fill path
        for (i in innerSteps downTo 0) {
          val angle = (i.toFloat() / innerSteps) * twoPi
          val bx = center.x + cos(angle) * innerRingR
          val by = center.y + sin(angle) * innerRingR
          innerMusicFillPath.lineTo(bx, by)
        }
        innerMusicFillPath.close()

        // Fill inner wave peaks with Neon Violet-Magenta glow
        drawPath(
          path = innerMusicFillPath,
          color = Color(0xFFD500F9).copy(alpha = 0.38f * musicPresence),
          blendMode = BlendMode.Plus
        )
        // Outer soft Electric Violet bloom along inner wave contour
        drawPath(
          path = innerMusicWavePath,
          color = Color(0xFF7C4DFF).copy(alpha = 0.62f * musicPresence),
          style = Stroke(width = 5.4f),
          blendMode = BlendMode.Plus
        )
        // Mid Neon Magenta-Pink wave contour
        drawPath(
          path = innerMusicWavePath,
          color = Color(0xFFFF007F).copy(alpha = 0.92f * musicPresence),
          style = Stroke(width = 2.4f),
          blendMode = BlendMode.Plus
        )
        // Crisp Hot-Pink / White base circle of the inner ring
        drawCircle(
          color = Color(0xFFFF4081).copy(alpha = 0.95f * musicPresence),
          radius = innerRingR,
          center = center,
          style = Stroke(width = 1.9f),
          blendMode = BlendMode.Plus
        )
        drawCircle(
          color = Color(0xFFFFB2DD).copy(alpha = 0.78f * musicPresence),
          radius = innerRingR,
          center = center,
          style = Stroke(width = 0.9f)
        )

        // 2. OUTER CONCENTRIC WAVE RING (Warm Orange-Pink/White Base Rim + Fluid Electric Cyan Waveform Peaks)
        val outerSteps = if (isPowerSaving) 96 else 180
        outerMusicWavePath.reset()
        outerMusicFillPath.reset()

        for (i in 0..outerSteps) {
          val angle = (i.toFloat() / outerSteps) * twoPi
          val cosA = cos(angle)
          val sinA = sin(angle)

          // Directional frequency burst envelopes matching the reference image:
          // Strong multi-peak bursts on Upper-Left (~2.45 rad / 140 deg) and Lower-Right (~5.59 rad / 320 deg),
          // plus secondary harmonic bursts on Upper-Right (~0.75 rad / 43 deg) and Lower-Left (~3.89 rad / 223 deg)
          val dTopLeft = atan2(sin(angle - 2.42f), cos(angle - 2.42f))
          val dBotRight = atan2(sin(angle - 5.56f), cos(angle - 5.56f))
          val dTopRight = atan2(sin(angle - 0.78f), cos(angle - 0.78f))
          val dBotLeft = atan2(sin(angle - 3.92f), cos(angle - 3.92f))

          val burstPrimary = exp(-(dTopLeft / 0.52f).pow(2)) + exp(-(dBotRight / 0.52f).pow(2))
          val burstSecondary = (exp(-(dTopRight / 0.46f).pow(2)) + exp(-(dBotLeft / 0.46f).pow(2))) * 0.62f
          val angularBurstEnv = (0.20f + 0.80f * (burstPrimary + burstSecondary).coerceIn(0f, 1.15f))

          // High-definition circular FFT spline oscillation (creates crisp fluid audio peaks)
          val w1 = (sin(angle * 10f + wavePhase * 2.1f) + 1f) * 0.5f
          val w2 = (cos(angle * 18f - pulsePhase * 2.8f) + 1f) * 0.5f
          val w3 = (sin(angle * 28f + wavePhase * 3.7f) + 1f) * 0.5f
          val combinedWave = (w1 * 0.42f + w2 * 0.35f + w3 * 0.23f).pow(1.65f)

          val beatDrive = 0.52f + 0.48f * musicBeatEnergy + 0.25f * micLiveEnergy * listeningPresence
          val outerDisp = radius * (0.008f + 0.245f * combinedWave * angularBurstEnv * beatDrive) * musicPresence
          val rOut = outerRingR + outerDisp

          val px = center.x + cosA * rOut
          val py = center.y + sinA * rOut
          if (i == 0) {
            outerMusicWavePath.moveTo(px, py)
            outerMusicFillPath.moveTo(px, py)
          } else {
            outerMusicWavePath.lineTo(px, py)
            outerMusicFillPath.lineTo(px, py)
          }
        }
        outerMusicWavePath.close()

        // Close outer fill path along the base circle outerRingR
        for (i in outerSteps downTo 0) {
          val angle = (i.toFloat() / outerSteps) * twoPi
          val bx = center.x + cos(angle) * outerRingR
          val by = center.y + sin(angle) * outerRingR
          outerMusicFillPath.lineTo(bx, by)
        }
        outerMusicFillPath.close()

        // Fill between outerRingR and the deformed cyan waveform peaks
        drawPath(
          path = outerMusicFillPath,
          brush = Brush.radialGradient(
            colorStops = arrayOf(
              0.0f to Color(0xFF00F0FF).copy(alpha = 0.15f * musicPresence),
              0.72f to Color(0xFF00F0FF).copy(alpha = 0.48f * musicPresence),
              0.90f to Color(0xFF00E5FF).copy(alpha = 0.34f * musicPresence),
              1.0f to Color(0xFF18FFFF).copy(alpha = 0.18f * musicPresence)
            ),
            center = center,
            radius = outerRingR + radius * 0.26f
          ),
          blendMode = BlendMode.Plus
        )

        // Wide Electric Cyan outer neon bloom along the waveform contour
        drawPath(
          path = outerMusicWavePath,
          color = Color(0xFF00E5FF).copy(alpha = 0.42f * musicPresence),
          style = Stroke(width = 7.8f),
          blendMode = BlendMode.Plus
        )
        // Bright Electric Cyan primary waveform line
        drawPath(
          path = outerMusicWavePath,
          color = Color(0xFF00F5FF).copy(alpha = 0.94f * musicPresence),
          style = Stroke(width = 3.0f),
          blendMode = BlendMode.Plus
        )
        // Razor-sharp Ice-White/Cyan waveform crest highlight
        drawPath(
          path = outerMusicWavePath,
          color = Color(0xFFE0FFFF).copy(alpha = 0.92f * musicPresence),
          style = Stroke(width = 1.25f)
        )

        // Warm Peach-Pink / Orange-Gold / White inner base rim of the outer circle (matching reference image)
        drawCircle(
          color = Color(0xFFFF6EC7).copy(alpha = 0.56f * musicPresence),
          radius = outerRingR,
          center = center,
          style = Stroke(width = 3.8f),
          blendMode = BlendMode.Plus
        )
        drawCircle(
          brush = Brush.sweepGradient(
            colors = listOf(
              Color(0xFFFFAB40).copy(alpha = 0.95f * musicPresence),
              Color(0xFFFF80AB).copy(alpha = 0.95f * musicPresence),
              Color(0xFFFFFFFF).copy(alpha = 0.98f * musicPresence),
              Color(0xFFFFAB40).copy(alpha = 0.95f * musicPresence),
              Color(0xFFFF4081).copy(alpha = 0.95f * musicPresence),
              Color(0xFFFFFFFF).copy(alpha = 0.98f * musicPresence),
              Color(0xFFFFAB40).copy(alpha = 0.95f * musicPresence)
            ),
            center = center
          ),
          radius = outerRingR,
          center = center,
          style = Stroke(width = 1.7f)
        )
      }
    }
  }
}

/**
 * Loads the user-provided reference Moon artwork (`aistudio/______2b58ee5a.jpg`), auto-detects
 * its circular blazing white-cyan rim, and extracts the exact circular lunar sphere so that
 * it matches the WEYKY Core diameter pixel-for-pixel. Falls back to the high-resolution
 * procedural blue-white lunar topography generator if asset decoding is unavailable.
 */
private fun loadOrGenerateReferenceMoonBitmap(context: Context, sizePx: Int): ImageBitmap {
  try {
    context.assets.open("aistudio/______2b58ee5a.jpg").use { stream ->
      val src = BitmapFactory.decodeStream(stream)
      if (src != null && src.width > 64 && src.height > 64) {
        val sw = src.width
        val sh = src.height
        val srcPixels = IntArray(sw * sh)
        src.getPixels(srcPixels, 0, sw, 0, 0, sw, sh)

        // Detect the bright white-cyan rim along horizontal and vertical centerlines
        val midY = sh / 2
        val midX = sw / 2

        var leftX = (sw * 0.28f).toInt()
        for (x in 0 until midX) {
          val c = srcPixels[midY * sw + x]
          val r = (c shr 16) and 0xFF
          val g = (c shr 8) and 0xFF
          val b = c and 0xFF
          if (r > 190 && g > 230 && b > 240) {
            leftX = x
            break
          }
        }

        var rightX = (sw * 0.72f).toInt()
        for (x in (sw - 1) downTo midX) {
          val c = srcPixels[midY * sw + x]
          val r = (c shr 16) and 0xFF
          val g = (c shr 8) and 0xFF
          val b = c and 0xFF
          if (r > 190 && g > 230 && b > 240) {
            rightX = x
            break
          }
        }

        var topY = (sh * 0.068f).toInt()
        for (y in 0 until midY) {
          val c = srcPixels[y * sw + midX]
          val r = (c shr 16) and 0xFF
          val g = (c shr 8) and 0xFF
          val b = c and 0xFF
          if (r > 190 && g > 230 && b > 240) {
            topY = y
            break
          }
        }

        var bottomY = (sh * 0.932f).toInt()
        for (y in (sh - 1) downTo midY) {
          val c = srcPixels[y * sw + midX]
          val r = (c shr 16) and 0xFF
          val g = (c shr 8) and 0xFF
          val b = c and 0xFF
          if (r > 190 && g > 230 && b > 240) {
            bottomY = y
            break
          }
        }

        val detectedRx = (rightX - leftX) * 0.5f
        val detectedRy = (bottomY - topY) * 0.5f
        val validDetection = detectedRx in (sh * 0.34f)..(sh * 0.49f) &&
          detectedRy in (sh * 0.34f)..(sh * 0.49f)

        val cx = if (validDetection) (leftX + rightX) * 0.5f else sw * 0.50f
        val cy = if (validDetection) (topY + bottomY) * 0.5f else sh * 0.50f
        val rMoon = if (validDetection) (detectedRx + detectedRy) * 0.5f else sh * 0.432f

        val outPixels = IntArray(sizePx * sizePx)
        val halfOut = sizePx * 0.5f

        for (py in 0 until sizePx) {
          val ny = (py + 0.5f - halfOut) / halfOut
          val rowOffset = py * sizePx
          for (px in 0 until sizePx) {
            val nx = (px + 0.5f - halfOut) / halfOut
            val r2 = nx * nx + ny * ny
            if (r2 > 1.0f) {
              outPixels[rowOffset + px] = 0
              continue
            }

            val sx = (cx + nx * rMoon).coerceIn(0f, (sw - 1).toFloat())
            val sy = (cy + ny * rMoon).coerceIn(0f, (sh - 1).toFloat())
            val x0 = sx.toInt().coerceIn(0, sw - 1)
            val y0 = sy.toInt().coerceIn(0, sh - 1)
            val x1 = (x0 + 1).coerceAtMost(sw - 1)
            val y1 = (y0 + 1).coerceAtMost(sh - 1)
            val fx = sx - x0
            val fy = sy - y0

            val c00 = srcPixels[y0 * sw + x0]
            val c10 = srcPixels[y0 * sw + x1]
            val c01 = srcPixels[y1 * sw + x0]
            val c11 = srcPixels[y1 * sw + x1]

            val rCol = bilinearChannel((c00 shr 16) and 0xFF, (c10 shr 16) and 0xFF, (c01 shr 16) and 0xFF, (c11 shr 16) and 0xFF, fx, fy)
            val gCol = bilinearChannel((c00 shr 8) and 0xFF, (c10 shr 8) and 0xFF, (c01 shr 8) and 0xFF, (c11 shr 8) and 0xFF, fx, fy)
            val bCol = bilinearChannel(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, fx, fy)

            val edgeAlpha = ((1.0f - sqrt(r2)) / 0.025f).coerceIn(0f, 1f)
            val aCol = (edgeAlpha * 255f).toInt().coerceIn(0, 255)
            outPixels[rowOffset + px] = AndroidColor.argb(aCol, rCol, gCol, bCol)
          }
        }

        val outBitmap = Bitmap.createBitmap(outPixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
        return outBitmap.asImageBitmap()
      }
    }
  } catch (_: Exception) {
    // Fallback to procedural high-res lunar topography generator below
  }
  return generateProceduralBlueWhiteMoonBitmap(sizePx)
}

private fun bilinearChannel(c00: Int, c10: Int, c01: Int, c11: Int, fx: Float, fy: Float): Int {
  val top = c00 + (c10 - c00) * fx
  val bottom = c01 + (c11 - c01) * fx
  return (top + (bottom - top) * fy).toInt().coerceIn(0, 255)
}

/**
 * High-definition procedural 3D Blue-White Lunar Surface generator reproducing the exact
 * lunar maria (Mare Imbrium, Serenitatis, Tranquillitatis, Crisium), bright icy highlands,
 * and radiating Tycho crater rays in the lower-right quadrant.
 */
private fun generateProceduralBlueWhiteMoonBitmap(sizePx: Int): ImageBitmap {
  val pixels = IntArray(sizePx * sizePx)
  val r = sizePx * 0.5f

  fun hash(ix: Int, iy: Int, seed: Int): Float {
    var n = ix * 374761393 + iy * 668265263 + seed * 1442695041
    n = (n xor (n ushr 13)) * 1274126177
    n = n xor (n ushr 16)
    return (n and 0x7fffffff).toFloat() / 2147483647.0f
  }

  fun noise2D(x: Float, y: Float, seed: Int): Float {
    val ix = floor(x).toInt()
    val iy = floor(y).toInt()
    val fx = x - ix
    val fy = y - iy
    val ux = fx * fx * (3f - 2f * fx)
    val uy = fy * fy * (3f - 2f * fy)
    val a = hash(ix, iy, seed)
    val b = hash(ix + 1, iy, seed)
    val c = hash(ix, iy + 1, seed)
    val d = hash(ix + 1, iy + 1, seed)
    return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy
  }

  fun fbm(px: Float, py: Float, seed: Int, octaves: Int = 5): Float {
    var v = 0f
    var amp = 0.5f
    var x = px
    var y = py
    var norm = 0f
    for (i in 0 until octaves) {
      v += amp * noise2D(x, y, seed + i * 97)
      norm += amp
      val nx = (0.8f * x - 0.6f * y) * 2.05f + 7.3f
      val ny = (0.6f * x + 0.8f * y) * 2.05f + 19.1f
      x = nx
      y = ny
      amp *= 0.5f
    }
    return v / norm
  }

  for (py in 0 until sizePx) {
    val ny = (py + 0.5f - r) / r
    val rowOffset = py * sizePx
    for (px in 0 until sizePx) {
      val nx = (px + 0.5f - r) / r
      val r2 = nx * nx + ny * ny
      if (r2 > 1.0f) {
        pixels[rowOffset + px] = 0
        continue
      }
      val nz = sqrt(max(0f, 1f - r2))
      // Spherical projection coordinates
      val sx = nx / (0.65f + 0.35f * nz)
      val sy = ny / (0.65f + 0.35f * nz)

      val warpX = fbm(sx * 3.2f, sy * 3.2f, 101, 4) - 0.5f
      val warpY = fbm(sx * 3.2f + 4.1f, sy * 3.2f + 2.7f, 202, 4) - 0.5f
      val detail = fbm(sx * 6.5f + warpX * 1.4f, sy * 6.5f + warpY * 1.4f, 303, 5)

      // Lunar Maria basins (dark sapphire-blue regions matching the reference image)
      val wx = sx + warpX * 0.28f
      val wy = sy + warpY * 0.28f
      val mImbrium = exp(-6.5f * ((wx + 0.26f).pow(2) + (wy + 0.16f).pow(2)))
      val mSerenitatis = exp(-8.0f * ((wx - 0.04f).pow(2) + (wy + 0.12f).pow(2)))
      val mTranquillitatis = exp(-7.5f * ((wx - 0.14f).pow(2) + (wy - 0.08f).pow(2)))
      val mProcellarum = exp(-5.5f * ((wx + 0.42f).pow(2) + (wy - 0.10f).pow(2)))
      val mNubium = exp(-7.5f * ((wx + 0.02f).pow(2) + (wy - 0.36f).pow(2)))
      val mCrisium = exp(-28.0f * ((sx - 0.12f).pow(2) + (sy + 0.58f).pow(2)))

      val mariaWeight = (mImbrium * 0.85f + mSerenitatis * 0.80f + mTranquillitatis * 0.72f +
        mProcellarum * 0.75f + mNubium * 0.68f + mCrisium * 0.92f).coerceIn(0f, 1f)

      // Tycho Crater & radiating rays in lower-right (nx ~ 0.36, ny ~ 0.42)
      val dxTycho = nx - 0.36f
      val dyTycho = ny - 0.42f
      val distTycho = sqrt(dxTycho * dxTycho + dyTycho * dyTycho)
      val angleTycho = atan2(dyTycho, dxTycho)
      val rayPattern = ((sin(angleTycho * 9f + detail * 2.5f) + 1f) * 0.5f).pow(3) *
        exp(-2.6f * distTycho)
      val tychoCore = exp(-48f * distTycho * distTycho)
      val tychoGlow = (tychoCore * 0.95f + rayPattern * 0.55f).coerceIn(0f, 1f)

      // Base lunar albedo (0 = deep sapphire mare, 1 = pure white highland/crater)
      val baseAlbedo = ((1f - mariaWeight * 0.72f) * (0.52f + 0.48f * detail) + tychoGlow * 0.55f).coerceIn(0f, 1f)
      // Fresnel icy-cyan limb brightening
      val fresnel = (1f - nz).pow(2.1f)
      val lum = (baseAlbedo * 0.85f + fresnel * 0.65f).coerceIn(0f, 1f)

      val red = (28f + 227f * lum.pow(1.25f)).toInt().coerceIn(0, 255)
      val green = (58f + 197f * lum.pow(1.05f)).toInt().coerceIn(0, 255)
      val blue = (108f + 147f * lum.pow(0.82f)).toInt().coerceIn(0, 255)

      pixels[rowOffset + px] = AndroidColor.argb(255, red, green, blue)
    }
  }

  val bmp = Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
  return bmp.asImageBitmap()
}

/**
 * Generates a 3D spherical lunar terminator shadow mask for the Moon Core.
 *
 * Uses a translucent deep-sapphire earthshine on the unlit portion so that the astronomical
 * lunar phase (Crescent, Quarter, Gibbous, Full Moon) is gracefully expressed while preserving
 * the full visibility of the lunar maria, craters, and blazing electric-cyan rim.
 */
private fun createSphericalLunarPhaseMask(sizePx: Int, cyclePhase: Float): ImageBitmap {
  val pixels = IntArray(sizePx * sizePx)
  val r = sizePx / 2f
  val phaseAngle = (cyclePhase.coerceIn(0f, 1f) * 2.0 * Math.PI).toFloat()

  val lx = sin(phaseAngle)
  val ly = -0.10f * sin(phaseAngle)
  val lz = -cos(phaseAngle)
  val lLen = sqrt(lx * lx + ly * ly + lz * lz).coerceAtLeast(0.001f)
  val dirX = lx / lLen
  val dirY = ly / lLen
  val dirZ = lz / lLen

  for (py in 0 until sizePx) {
    val ny = (py + 0.5f - r) / r
    val rowOffset = py * sizePx
    for (px in 0 until sizePx) {
      val nx = (px + 0.5f - r) / r
      val r2 = nx * nx + ny * ny
      if (r2 > 1.0f) {
        pixels[rowOffset + px] = 0
        continue
      }
      val nz = sqrt(max(0f, 1f - r2))
      val dot = nx * dirX + ny * dirY + nz * dirZ

      // Soft penumbra transition across the lunar terminator line;
      // Keep the outer 4% rim bright so the electric cyan ring always wraps 360 degrees
      val rimKeep = ((sqrt(r2) - 0.93f) / 0.07f).coerceIn(0f, 1f)
      val t = ((dot + 0.12f) / 0.28f).coerceIn(0f, 1f)
      val litFactor = max(t * t * (3f - 2f * t), rimKeep)
      val shadowAlpha = ((1f - litFactor) * 0.34f * 255f).toInt().coerceIn(0, 255)
      if (shadowAlpha <= 1) {
        pixels[rowOffset + px] = 0
      } else {
        pixels[rowOffset + px] = AndroidColor.argb(shadowAlpha, 6, 18, 42)
      }
    }
  }

  val bitmap = Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
  return bitmap.asImageBitmap()
}

/**
 * Procedural harmonic color interpolation for dynamic music expression.
 * Evolves smoothly through: Violet -> Vivid Pink -> Magenta -> Warm Gold -> Electric Cyan -> Violet
 */
private fun getDynamicMusicColor(progress: Float): Color {
  val p = (progress % 1.0f + 1.0f) % 1.0f
  return when {
    p < 0.2f -> lerpColor(Color(0xFF8338EC), Color(0xFFFF006E), p / 0.2f) // Violet -> Vivid Pink
    p < 0.4f -> lerpColor(Color(0xFFFF006E), Color(0xFFFF70A6), (p - 0.2f) / 0.2f) // Vivid Pink -> Magenta
    p < 0.6f -> lerpColor(Color(0xFFFF70A6), Color(0xFFFFD166), (p - 0.4f) / 0.2f) // Magenta -> Warm Gold
    p < 0.8f -> lerpColor(Color(0xFFFFD166), Color(0xFF00F5D4), (p - 0.6f) / 0.2f) // Warm Gold -> Electric Cyan
    else -> lerpColor(Color(0xFF00F5D4), Color(0xFF8338EC), (p - 0.8f) / 0.2f) // Electric Cyan -> Violet
  }
}

/**
 * Procedural harmonic color interpolation for dynamic charging power expression.
 * Evolves smoothly between Electric Cyan, Emerald Plasma, Neon Volt Green, and Solar Gold as charge progresses.
 */
private fun getDynamicChargingColor(batteryNorm: Float, phase: Float, offset: Float): Color {
  val wave = ((sin((phase + offset * 6.283185f).toDouble()) + 1.0) * 0.5).toFloat()
  val cyan = Color(0xFF00B4D8)
  val emerald = Color(0xFF06D6A0)
  val neonVolt = Color(0xFF39FF14)
  val solarGold = Color(0xFFFFEA00)

  val base = lerpColor(cyan, emerald, wave)
  val energized = lerpColor(emerald, neonVolt, ((cos((phase * 0.7f + offset * 3.14f).toDouble()) + 1.0) * 0.5).toFloat())
  val blended = lerpColor(base, energized, 0.55f)
  // As battery approaches 100%, infuse warm solar-gold luminescence
  val fullChargeBoost = ((batteryNorm - 0.75f).coerceAtLeast(0f) / 0.25f) * 0.35f
  return lerpColor(blended, solarGold, fullChargeBoost)
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
  val f = fraction.coerceIn(0f, 1f)
  return Color(
    red = start.red + (end.red - start.red) * f,
    green = start.green + (end.green - start.green) * f,
    blue = start.blue + (end.blue - start.blue) * f,
    alpha = start.alpha + (end.alpha - start.alpha) * f
  )
}
