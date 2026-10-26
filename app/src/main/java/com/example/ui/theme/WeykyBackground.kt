package com.example.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.state.WeykyUiState
import com.example.device.battery.ChargingState
import com.example.rendering.visual_state.CoreVisualState
import java.io.File

/**
 * Universal adaptive screen background supporting Tactical, Modern, and Minimal themes.
 */
@Composable
fun Modifier.weykyScreenBackground(
  uiState: WeykyUiState,
  modifier: Modifier = Modifier
): Modifier {
  val context = LocalContext.current
  val themeMode = uiState.themeMode
  val isLight = uiState.isLightMode
  val isPowerSaving = uiState.isPowerSaving

  when (themeMode) {
    WeykyThemeMode.TACTICAL -> {
      val accentColor = Color(uiState.cardBorderColorHex)
      return this
        .background(Color.Black)
        .jarvisTacticalBackground(
          backgroundColor = Color.Black,
          accentColor = accentColor
        )
    }

    WeykyThemeMode.MODERN -> {
      val bgPreset = ModernBackgroundCatalog.findById(uiState.modernBgPresetId)

      // Determine base state color for Modern dynamic lighting
      val isCharging = uiState.batterySnapshot?.chargingState == ChargingState.CHARGING ||
        uiState.batterySnapshot?.chargingState == ChargingState.FULL

      val targetStateColor = when {
        isCharging -> Color(0xFF00F59B) // Charging (Hyper Cyber Green)
        uiState.isVoiceListening -> Color(0xFFF43F5E) // Listening (Vivid Neon Rose/Magenta)
        uiState.isSpeakingAloud || uiState.visualState == CoreVisualState.RESPONDING -> Color(0xFFFFB800) // Speaking (Saturated Solar Gold)
        uiState.isMusicPlaying || uiState.visualState == CoreVisualState.MUSIC -> bgPreset.leftNebulaColors.first() // Music
        else -> bgPreset.leftNebulaColors.first() // Idle
      }

      val animatedGlowColor by animateColorAsState(
        targetValue = targetStateColor,
        animationSpec = tween(durationMillis = 650),
        label = "modern_glow_color"
      )

      val modernBgColor = if (isLight) bgPreset.lightBaseColors.first() else bgPreset.darkBaseColors.first()

      val glowAlpha = when {
        isPowerSaving -> 0.34f
        isLight -> 0.46f
        else -> 0.78f
      }

      return this
        .background(modernBgColor)
        .drawBehind {
          val w = size.width
          val h = size.height
          val maxDim = maxOf(w, h)
          val minDim = minOf(w, h)

          // 1. Rich saturated cosmic gradient base (works in both Portrait & Landscape)
          drawRect(
            brush = Brush.linearGradient(
              colors = if (isLight) bgPreset.lightBaseColors else bgPreset.darkBaseColors,
              start = Offset(0f, 0f),
              end = Offset(w, h)
            )
          )

          // 2. Left / top-left nebula from preset
          val leftC0 = bgPreset.leftNebulaColors.getOrElse(0) { Color(0xFF00E5FF) }
          val leftC1 = bgPreset.leftNebulaColors.getOrElse(1) { Color(0xFF0066FF) }
          val leftC2 = bgPreset.leftNebulaColors.getOrElse(2) { Color(0xFF1D4ED8) }
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                leftC0.copy(alpha = glowAlpha * 0.95f),
                leftC1.copy(alpha = glowAlpha * 0.62f),
                leftC2.copy(alpha = glowAlpha * 0.25f),
                Color.Transparent
              ),
              center = Offset(w * 0.14f, h * 0.36f),
              radius = maxDim * 0.72f
            ),
            center = Offset(w * 0.14f, h * 0.36f),
            radius = maxDim * 0.72f
          )

          // 3. Right / bottom-right nebula from preset
          val rightC0 = bgPreset.rightNebulaColors.getOrElse(0) { Color(0xFFD946EF) }
          val rightC1 = bgPreset.rightNebulaColors.getOrElse(1) { Color(0xFF8B5CF6) }
          val rightC2 = bgPreset.rightNebulaColors.getOrElse(2) { Color(0xFF4F46E5) }
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                rightC0.copy(alpha = glowAlpha * 0.90f),
                rightC1.copy(alpha = glowAlpha * 0.65f),
                rightC2.copy(alpha = glowAlpha * 0.28f),
                Color.Transparent
              ),
              center = Offset(w * 0.86f, h * 0.56f),
              radius = maxDim * 0.76f
            ),
            center = Offset(w * 0.86f, h * 0.56f),
            radius = maxDim * 0.76f
          )

          // 4. Diagonal atmospheric bloom from preset
          val diagC0 = bgPreset.diagonalBloomColors.getOrElse(0) { Color(0xFF6366F1) }
          val diagC1 = bgPreset.diagonalBloomColors.getOrElse(1) { Color(0xFFEC4899) }
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                diagC0.copy(alpha = glowAlpha * 0.55f),
                diagC1.copy(alpha = glowAlpha * 0.24f),
                Color.Transparent
              ),
              center = Offset(w * 0.52f, h * 0.18f),
              radius = maxDim * 0.60f
            ),
            center = Offset(w * 0.52f, h * 0.18f),
            radius = maxDim * 0.60f
          )

          // 5. Centered radiant aura directly behind the WEYKY core
          val centerX = w * 0.5f
          val centerY = h * 0.46f
          val coreRadius = minDim * 0.68f

          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(
                animatedGlowColor.copy(alpha = glowAlpha * 0.82f),
                animatedGlowColor.copy(alpha = glowAlpha * 0.36f),
                Color.Transparent
              ),
              center = Offset(centerX, centerY),
              radius = coreRadius
            ),
            center = Offset(centerX, centerY),
            radius = coreRadius
          )
        }
    }

    WeykyThemeMode.MINIMAL -> {
      val dimAlpha = uiState.minimalDimmingAlpha.coerceIn(0.0f, 0.90f)

      when (uiState.minimalBgType) {
        MinimalBackgroundType.DEFAULT -> {
          return this
            .background(Color(0xFF050B11))
            .drawBehind {
              // Exact Minimal abstract curved waves in dark slate/petrol teal & black
              val w = size.width
              val h = size.height

              // Layer 1: Sweeping dark petrol-slate wave from middle-left across screen
              val wavePath1 = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * 0.22f)
                cubicTo(
                  w * 0.35f, h * 0.32f,
                  w * 0.75f, h * 0.45f,
                  w, h * 0.52f
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
              }
              drawPath(
                path = wavePath1,
                brush = Brush.verticalGradient(
                  colors = listOf(Color(0xFF0C1F2B), Color(0xFF07121A), Color(0xFF040A0F)),
                  startY = h * 0.22f,
                  endY = h
                )
              )

              // Subtle luminous arc rim along wave 1
              val arcPath1 = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * 0.22f)
                cubicTo(
                  w * 0.35f, h * 0.32f,
                  w * 0.75f, h * 0.45f,
                  w, h * 0.52f
                )
              }
              drawPath(
                path = arcPath1,
                brush = Brush.horizontalGradient(
                  colors = listOf(Color(0x3500E5FF), Color(0x1800E5FF), Color.Transparent)
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
              )

              // Layer 2: Second intersecting dark curved wave
              val wavePath2 = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.2f, h)
                cubicTo(
                  w * 0.55f, h * 0.78f,
                  w * 0.85f, h * 0.65f,
                  w, h * 0.60f
                )
                lineTo(w, h)
                close()
              }
              drawPath(
                path = wavePath2,
                brush = Brush.linearGradient(
                  colors = listOf(Color(0xFF0F2635), Color(0xFF07121A)),
                  start = Offset(w * 0.2f, h),
                  end = Offset(w, h * 0.60f)
                )
              )

              if (dimAlpha > 0f) {
                drawRect(Color.Black.copy(alpha = dimAlpha))
              }
            }
        }

        MinimalBackgroundType.SOLID_COLOR -> {
          val solidColor = Color(uiState.minimalSolidColorHex)
          return this
            .background(solidColor)
            .drawBehind {
              if (dimAlpha > 0f) {
                drawRect(Color.Black.copy(alpha = dimAlpha))
              }
            }
        }

        MinimalBackgroundType.GRADIENT -> {
          val startColor = Color(uiState.minimalGradientStartHex)
          val endColor = Color(uiState.minimalGradientEndHex)
          return this
            .background(
              Brush.linearGradient(
                colors = listOf(startColor, endColor)
              )
            )
            .drawBehind {
              if (dimAlpha > 0f) {
                drawRect(Color.Black.copy(alpha = dimAlpha))
              }
            }
        }

        MinimalBackgroundType.WALLPAPER_PRESET -> {
          val preset = OfflineWallpaperCatalog.findById(uiState.minimalPresetId)
          if (preset.drawableResId != null) {
            val bitmap = remember(preset.drawableResId) {
              try {
                BitmapFactory.decodeResource(context.resources, preset.drawableResId)
              } catch (e: Exception) {
                null
              }
            }
            if (bitmap != null) {
              val imageBitmap = bitmap.asImageBitmap()
              return this.drawBehind {
                val srcW = imageBitmap.width.toFloat()
                val srcH = imageBitmap.height.toFloat()
                val canvasW = size.width
                val canvasH = size.height
                val scale = maxOf(canvasW / srcW, canvasH / srcH)
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val offsetX = (canvasW - scaledW) / 2f
                val offsetY = (canvasH - scaledH) / 2f

                drawImage(
                  image = imageBitmap,
                  dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                  dstSize = androidx.compose.ui.unit.IntSize(scaledW.toInt(), scaledH.toInt())
                )
                if (dimAlpha > 0f) {
                  drawRect(Color.Black.copy(alpha = dimAlpha))
                }
              }
            }
          }
          return this
            .drawBehind {
              preset.drawBackground(this)
              if (dimAlpha > 0f) {
                drawRect(Color.Black.copy(alpha = dimAlpha))
              }
            }
        }

        MinimalBackgroundType.IMAGE -> {
          // If a custom image was saved in app files, decode it safely with fallback
          val imageFile = File(context.filesDir, "minimal_wallpaper.jpg")
          val bitmap = remember(uiState.minimalImagePath, imageFile.lastModified()) {
            if (imageFile.exists() && imageFile.length() > 0) {
              try {
                BitmapFactory.decodeFile(imageFile.absolutePath)
              } catch (e: Exception) {
                null
              }
            } else null
          }

          if (bitmap != null) {
            val imageBitmap = bitmap.asImageBitmap()
            return this
              .drawBehind {
                drawImage(
                  image = imageBitmap,
                  dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                )
                if (dimAlpha > 0f) {
                  drawRect(Color.Black.copy(alpha = dimAlpha))
                }
              }
          } else {
            // Safe fallback to default Minimal background
            return this
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color(0xFF0B0F19), Color(0xFF111827), Color(0xFF080B12))
                )
              )
              .drawBehind {
                if (dimAlpha > 0f) {
                  drawRect(Color.Black.copy(alpha = dimAlpha))
                }
              }
          }
        }
      }
    }
  }
}

/**
 * Theme-aware unified card container.
 * In Tactical: Uses chamfered JarvisHudCard with double wireframe & coordinates grid.
 * In Modern: Uses frosted glass card with medium rounded corners and smooth subtle border.
 * In Minimal: Uses clean minimalist rounded card with subtle hairline edge.
 */
@Composable
fun WeykyAdaptiveCard(
  uiState: WeykyUiState,
  modifier: Modifier = Modifier,
  accentColor: Color = Color(uiState.cardBorderColorHex),
  containerColor: Color = JarvisHudColors.panelBg(uiState.isLightMode),
  cutCornerSize: Dp = 14.dp,
  tabTitle: String? = null,
  tabPosition: HudTabPosition = if (tabTitle != null) HudTabPosition.TOP_START else HudTabPosition.NONE,
  onClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit
) {
  val themeMode = uiState.themeMode
  val isLight = uiState.isLightMode

  when (themeMode) {
    WeykyThemeMode.TACTICAL -> {
      JarvisHudCard(
        modifier = modifier,
        accentColor = accentColor,
        containerColor = containerColor,
        cutCornerSize = cutCornerSize,
        tabTitle = tabTitle,
        tabPosition = tabPosition,
        onClick = onClick,
        content = content
      )
    }

    WeykyThemeMode.MODERN -> {
      val shape = RoundedCornerShape(22.dp)
      val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
      val glassBg = if (isLight) Color(0xEEFFFFFF) else Color(0x400C192E)
      val secondaryBorder = Color(
        red = (accentColor.red * 0.65f + 0.22f).coerceIn(0f, 1f),
        green = (accentColor.green * 0.55f + 0.12f).coerceIn(0f, 1f),
        blue = (accentColor.blue * 0.75f + 0.25f).coerceIn(0f, 1f),
        alpha = 0.88f
      )
      val gradientBorder = Brush.linearGradient(
        colors = listOf(
          accentColor,
          secondaryBorder,
          accentColor.copy(alpha = 0.75f)
        )
      )

      Box(
        modifier = modifier
          .clip(shape)
          .background(glassBg)
          .border(width = 1.3.dp, brush = gradientBorder, shape = shape)
          .then(clickModifier),
        content = content
      )
    }

    WeykyThemeMode.MINIMAL -> {
      val shape = RoundedCornerShape(18.dp)
      val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
      val minimalBg = if (isLight) Color(0xF2FFFFFF) else Color(0xEE0E1722)
      val minimalBorder = if (isLight) Color(0x1F000000) else Color(0xFF1E2D3D)

      Box(
        modifier = modifier
          .clip(shape)
          .background(minimalBg)
          .border(width = 1.dp, color = minimalBorder, shape = shape)
          .then(clickModifier),
        content = content
      )
    }
  }
}
