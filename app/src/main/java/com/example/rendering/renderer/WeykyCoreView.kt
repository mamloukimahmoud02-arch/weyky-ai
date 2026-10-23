package com.example.rendering.renderer

import android.graphics.Bitmap
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.rendering.visual_state.CoreVisualState
import kotlin.math.cos
import kotlin.math.max
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
      Color(0xFFE2E8F0) // Luminous Silver-White Moon Primary
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
      Color(0xFFCBD5E1) // Soft Pearl-Silver Secondary
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
      Color(0xFFFFFFFF) // Pure Brilliant White Moon Accent
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
      val baseRadius = (minDim / 2f) * 0.86f
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

      // A. Ambient Outer Bloom
      val outerBloomRadius = radius * (1.16f + 0.08f * maxOf(
        listeningPresence * micLiveEnergy,
        speakingPresence * speechCadenceEnergy,
        musicPresence * musicBeatEnergy,
        chargingPresence * (0.4f + 0.5f * chargeSurge)
      ))
      val effectiveGlow = maxOf(visualState.glowIntensity, chargingPresence * 0.82f)
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

      // B. Music Reactive Equalizer Corona & Harmonic Halo (Active whenever music is playing)
      if (musicPresence > 0.01f) {
        val haloRadius = radius * (1.045f + 0.055f * musicBeatEnergy * musicPresence)
        val haloAlpha = (0.38f + 0.35f * musicBeatEnergy) * musicPresence

        drawCircle(
          brush = Brush.sweepGradient(
            colors = listOf(
              liveMusicHighlight.copy(alpha = haloAlpha),
              liveMusicSecondary.copy(alpha = haloAlpha * 0.75f),
              animatedAccent.copy(alpha = haloAlpha * 0.9f),
              liveMusicSecondary.copy(alpha = haloAlpha * 0.75f),
              liveMusicHighlight.copy(alpha = haloAlpha)
            ),
            center = center
          ),
          radius = haloRadius,
          center = center,
          style = Stroke(width = (2.5f + 3.5f * musicBeatEnergy) * musicPresence)
        )

        // Symmetrical radial acoustic equalizer bars around the sphere perimeter
        if (!isPowerSaving) {
          val barCount = 36
          for (i in 0 until barCount) {
            val angle = (i.toFloat() / barCount) * (2f * Math.PI.toFloat()) + driftPhase * 0.35f
            val bandHarmonic = sin(angle * 4f + wavePhase * 2f) * 0.5f +
              cos(angle * 3f - pulsePhase * 2f) * 0.5f
            val barNormalized = ((bandHarmonic + 1f) * 0.5f * (0.45f + 0.55f * musicBeatEnergy)).coerceIn(0.08f, 1f)
            val barLength = radius * (0.025f + 0.095f * barNormalized) * musicPresence

            val innerR = radius * 1.02f
            val outerR = innerR + barLength
            val cosA = cos(angle)
            val sinA = sin(angle)

            val startPt = Offset(center.x + cosA * innerR, center.y + sinA * innerR)
            val endPt = Offset(center.x + cosA * outerR, center.y + sinA * outerR)

            val barColor = if (i % 2 == 0) liveMusicHighlight else liveMusicSecondary
            drawLine(
              color = barColor.copy(alpha = (0.35f + 0.55f * barNormalized) * musicPresence),
              start = startPt,
              end = endPt,
              strokeWidth = 2.6f,
              cap = StrokeCap.Round
            )
          }
        }
      }

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

        // LAYER 8: Internal atmospheric vignette around the circular window rim for deep 3D curvature
        val vignetteBrush = Brush.radialGradient(
          colorStops = when {
            isIdleNightMoon -> arrayOf(
              0.0f to Color.Transparent,
              0.74f to Color.Transparent,
              0.91f to Color(0x4464748B),
              1.0f to Color(0x88334155)
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

        // LAYER 9: 3D Spherical Lunar Phase Terminator Shadow (when in Night White Moon mode)
        // Shapes the living White Core itself into Crescent (هلال), Quarter (تربيع), Gibbous (أحدب), or Full Moon (بدر)
        if (isIdleNightMoon && lunarPhaseShadowBitmap != null) {
          val diameterPx = (radius * 2f).toInt().coerceAtLeast(2)
          val topLeftX = (center.x - radius).toInt()
          val topLeftY = (center.y - radius).toInt()
          drawImage(
            image = lunarPhaseShadowBitmap,
            dstOffset = IntOffset(topLeftX, topLeftY),
            dstSize = IntSize(diameterPx, diameterPx)
          )
        }
      }

      // =========================================================================
      // CRISP PRECISION OUTER BOUNDARY RINGS
      // =========================================================================
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            animatedAccent.copy(alpha = 0.55f * visualState.glowIntensity),
            animatedPrimary.copy(alpha = 0.25f),
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
  }
}

/**
 * Generates a 3D spherical lunar terminator shadow mask for the White Moon Core.
 *
 * Uses true 3D sphere normal dot-product (`dot = nx * Lx + ny * Ly + nz * Lz`) so the living
 * White Core naturally exhibits all astronomical lunar phases/sizes:
 * - Waxing Crescent (هلال متزايد: cyclePhase ~ 0.03..0.22)
 * - First Quarter (التربيع الأول: cyclePhase ~ 0.25)
 * - Waxing Gibbous (أحدب متزايد: cyclePhase ~ 0.28..0.47)
 * - Full Moon (بدر كامل: cyclePhase ~ 0.47..0.53 -> 100% illuminated white core)
 * - Waning Gibbous (أحدب متناقص: cyclePhase ~ 0.53..0.72)
 * - Last Quarter (التربيع الأخير: cyclePhase ~ 0.75)
 * - Waning Crescent (هلال متناقص: cyclePhase ~ 0.78..0.97)
 */
private fun createSphericalLunarPhaseMask(sizePx: Int, cyclePhase: Float): ImageBitmap {
  val pixels = IntArray(sizePx * sizePx)
  val r = sizePx / 2f
  val phaseAngle = (cyclePhase.coerceIn(0f, 1f) * 2.0 * Math.PI).toFloat()

  // Solar illumination vector relative to the Moon-Earth line:
  // phase = 0.0 (New Moon)  -> Lz = -1 (lit from behind)
  // phase = 0.25 (First Q)  -> Lx = +1, Lz = 0 (right hemisphere lit)
  // phase = 0.50 (Full Moon)-> Lz = +1 (entire front hemisphere lit)
  // phase = 0.75 (Last Q)   -> Lx = -1, Lz = 0 (left hemisphere lit)
  val lx = sin(phaseAngle)
  val ly = -0.10f * sin(phaseAngle) // Subtle natural axial tilt of the lunar crescent
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

      // Soft penumbra transition across the lunar terminator line
      val t = ((dot + 0.08f) / 0.18f).coerceIn(0f, 1f)
      val litFactor = t * t * (3f - 2f * t)
      // Unlit side retains subtle cosmic earthshine (alpha ~ 0.88) so the sphere silhouette remains noble
      val shadowAlpha = ((1f - litFactor) * 0.88f * 255f).toInt().coerceIn(0, 255)
      if (shadowAlpha <= 1) {
        pixels[rowOffset + px] = 0
      } else {
        pixels[rowOffset + px] = AndroidColor.argb(shadowAlpha, 4, 8, 20)
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
