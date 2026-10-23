package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WEYKY Tactical Sci-Fi HUD Design System (Matching Reference Blueprint `weyky.jpg`).
 *
 * Key Architectural Features from Reference Image:
 * 1. Symmetrical Octagonal Chamfered Cards (all 4 corners cut at 45°).
 * 2. Double-Layer Precision Wireframe Border:
 *    - Outer crisp cyan/accent octagon border + recessed inner hairline octagon border.
 *    - Corner chevron bracket accents in the chamfered corners.
 * 3. Internal Fine Blueprint Coordinate Grid inside every card.
 * 4. Trapezoidal Tabbed Headers (`• CONTEXT`, `• WEYKY`) on top-left or (`• EVENTS`) on bottom-right.
 * 5. Vertical Technical Side Rails along the left and right edges of the screen.
 */
object JarvisHudColors {
  val PitchBlack = Color(0xFF000000)
  val DeepVoid = Color(0xFF02060D)
  val PanelBg = Color(0xE6040D17)
  val PanelBgElevated = Color(0xF005111E)
  val PanelBgSubtle = Color(0xCC030911)

  val HologramCyan = Color(0xFF38D9F5)
  val TealAccent = Color(0xFF19A7CE)
  val TacticalAmber = Color(0xFFFFB703)
  val NeonGreen = Color(0xFF22C55E)
  val CrimsonAlert = Color(0xFFFF2A54)
  val VioletSignal = Color(0xFF9D4EDD)
  val MagentaPulse = Color(0xFFFF006E)

  val TextPrimary = Color(0xFFF1F8FC)
  val TextSecondary = Color(0xFF6C92A6)
  val TextMuted = Color(0xFF426375)
  val GridLine = Color(0x1438D9F5)

  // Light Mode Tactical Aerospace Palette
  val LightBackground = Color(0xFFF2F7FB)
  val LightPanelBg = Color(0xF2FFFFFF)
  val LightPanelBgElevated = Color(0xFAE8F1F8)
  val LightTextPrimary = Color(0xFF091E32)
  val LightTextSecondary = Color(0xFF3E617A)
  val LightTextMuted = Color(0xFF6B8A9E)

  fun panelBg(isLightMode: Boolean): Color = if (isLightMode) LightPanelBg else PanelBg
  fun panelBgElevated(isLightMode: Boolean): Color = if (isLightMode) LightPanelBgElevated else PanelBgElevated
  fun textPrimary(isLightMode: Boolean): Color = if (isLightMode) LightTextPrimary else TextPrimary
  fun textSecondary(isLightMode: Boolean): Color = if (isLightMode) LightTextSecondary else TextSecondary
  fun textMuted(isLightMode: Boolean): Color = if (isLightMode) LightTextMuted else TextMuted

  val BackgroundPresets: List<Pair<String, Long>> = listOf(
    "أسود فاحم (OLED)" to 0xFF000000L,
    "كحلي فضائي" to 0xFF020710L,
    "زيتي عسكري" to 0xFF040B07L,
    "تيتانيوم داكن" to 0xFF080B10L,
    "قرمزي ليلي" to 0xFF0E0307L,
    "نيون كوني" to 0xFF060310L,
    "أبيض ثلجي (فاتح)" to 0xFFF2F7FBL,
    "أبيض نقي (فاتح)" to 0xFFFFFFFFL,
    "بلاتينيوم سماوي (فاتح)" to 0xFFEAF2F8L,
    "تيتانيوم نهاري (فاتح)" to 0xFFF4F5F7L
  )

  val BorderPresets: List<Pair<String, Long>> = listOf(
    "سيان ويكي (الأصلي)" to 0xFF38D9F5L,
    "سيان جارفيس" to 0xFF00F0FFL,
    "أزرق كوبالت تكتيكي" to 0xFF0284C7L,
    "كهرمان تكتيكي" to 0xFFFFB703L,
    "أخضر رادار" to 0xFF22C55EL,
    "قرمزي قتالي" to 0xFFFF2A54L,
    "بنفسجي بلازما" to 0xFF9D4EDDL,
    "أبيض تيتانيوم" to 0xFFE2F8FFL
  )
}

/**
 * Position of the trapezoidal HUD tab attached to a [JarvisHudCard].
 */
enum class HudTabPosition {
  NONE,
  TOP_START,
  BOTTOM_END
}

/**
 * Draws the pitch-black background with the vertical technical side-rails and subtle stardust
 * exactly as shown along the left and right edges of `weyky.jpg`.
 */
fun Modifier.jarvisTacticalBackground(
  backgroundColor: Color = JarvisHudColors.PitchBlack,
  accentColor: Color = JarvisHudColors.HologramCyan
): Modifier = this
  .background(backgroundColor)
  .drawBehind {
    val w = size.width
    val h = size.height
    val railColor = accentColor.copy(alpha = 0.36f)
    val faintRailColor = accentColor.copy(alpha = 0.16f)

    // Left & Right Vertical Technical Side Rails (as seen framing the sphere in weyky.jpg)
    val edgeMargin = 4.dp.toPx()
    val stepIn = 12.dp.toPx()
    val railTop = h * 0.24f
    val railBottom = h * 0.78f
    val bevel = 14.dp.toPx()

    // Left Outer Line
    drawLine(
      color = faintRailColor,
      start = Offset(edgeMargin, h * 0.03f),
      end = Offset(edgeMargin, h * 0.97f),
      strokeWidth = 1f
    )
    // Right Outer Line
    drawLine(
      color = faintRailColor,
      start = Offset(w - edgeMargin, h * 0.03f),
      end = Offset(w - edgeMargin, h * 0.97f),
      strokeWidth = 1f
    )

    // Left Stepped Bracket Rail
    val leftRail = Path().apply {
      moveTo(edgeMargin, railTop)
      lineTo(stepIn, railTop + bevel)
      lineTo(stepIn, railBottom - bevel)
      lineTo(edgeMargin, railBottom)
    }
    drawPath(
      path = leftRail,
      color = railColor,
      style = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Left Inner Accent Line
    val leftInner = Path().apply {
      moveTo(stepIn + 5.dp.toPx(), railTop + bevel * 1.6f)
      lineTo(stepIn + 5.dp.toPx(), railBottom - bevel * 1.6f)
    }
    drawPath(
      path = leftInner,
      color = faintRailColor,
      style = Stroke(width = 1f)
    )

    // Right Stepped Bracket Rail
    val rightRail = Path().apply {
      moveTo(w - edgeMargin, railTop)
      lineTo(w - stepIn, railTop + bevel)
      lineTo(w - stepIn, railBottom - bevel)
      lineTo(w - edgeMargin, railBottom)
    }
    drawPath(
      path = rightRail,
      color = railColor,
      style = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Right Inner Accent Line
    val rightInner = Path().apply {
      moveTo(w - stepIn - 5.dp.toPx(), railTop + bevel * 1.6f)
      lineTo(w - stepIn - 5.dp.toPx(), railBottom - bevel * 1.6f)
    }
    drawPath(
      path = rightInner,
      color = faintRailColor,
      style = Stroke(width = 1f)
    )
  }

/**
 * Renders the exact octagonal double-framed HUD card from `weyky.jpg` with:
 * - Optional trapezoidal header tab on Top-Left (`• CONTEXT`, `• WEYKY`) or Bottom-Right (`• EVENTS`)
 * - 4 symmetrical 45° chamfered corners
 * - Outer + inner recessed octagonal borders & corner chevron ticks
 * - Subtle internal blueprint grid
 */
@Composable
fun JarvisHudCard(
  modifier: Modifier = Modifier,
  accentColor: Color = JarvisHudColors.HologramCyan,
  containerColor: Color = JarvisHudColors.PanelBg,
  cutCornerSize: Dp = 16.dp,
  bracketLength: Dp = 14.dp,
  showScanlines: Boolean = true,
  tabTitle: String? = null,
  tabPosition: HudTabPosition = if (tabTitle != null) HudTabPosition.TOP_START else HudTabPosition.NONE,
  onClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit
) {
  val shape = CutCornerShape(cutCornerSize)
  val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
  val isLightCard = (containerColor.red + containerColor.green + containerColor.blue) / 3f > 0.5f
  val midGradientColor = if (isLightCard) Color(0xFFF7FAFD) else Color(0xEE02070E)

  Column(modifier = modifier) {
    // Optional Top-Start Trapezoidal HUD Tab (e.g., "• CONTEXT" or "• WEYKY")
    if (tabTitle != null && tabPosition == HudTabPosition.TOP_START) {
      HudTrapezoidTab(
        title = tabTitle,
        accentColor = accentColor,
        containerColor = containerColor,
        isLightCard = isLightCard,
        isTop = true,
        modifier = Modifier.padding(start = cutCornerSize)
      )
    }

    // Main Octagonal Double-Border Card Body
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(
          Brush.verticalGradient(
            colors = listOf(
              containerColor,
              midGradientColor,
              containerColor
            )
          )
        )
        .then(clickMod)
        .drawWithContent {
          // 1. Subtle internal blueprint grid inside the card (matches weyky.jpg)
          if (showScanlines) {
            val gridStep = 14.dp.toPx()
            val gridColor = accentColor.copy(alpha = if (isLightCard) 0.08f else 0.045f)
            var gx = gridStep
            while (gx < size.width) {
              drawLine(
                color = gridColor,
                start = Offset(gx, 0f),
                end = Offset(gx, size.height),
                strokeWidth = 0.8f
              )
              gx += gridStep
            }
            var gy = gridStep
            while (gy < size.height) {
              drawLine(
                color = gridColor,
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = 0.8f
              )
              gy += gridStep
            }
          }

          drawContent()

          // 2. Double-layer octagonal border + corner chevrons
          drawOctagonalDoubleFrame(
            accentColor = accentColor,
            cutPx = cutCornerSize.toPx()
          )
        },
      content = content
    )

    // Optional Bottom-End Trapezoidal HUD Tab (e.g., "• EVENTS")
    if (tabTitle != null && tabPosition == HudTabPosition.BOTTOM_END) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(end = cutCornerSize),
        contentAlignment = Alignment.CenterEnd
      ) {
        HudTrapezoidTab(
          title = tabTitle,
          accentColor = accentColor,
          containerColor = containerColor,
          isLightCard = isLightCard,
          isTop = false
        )
      }
    }
  }
}

/**
 * Trapezoidal angled tab (`• CONTEXT`, `• WEYKY`, `• EVENTS`) attached to top-left or bottom-right of HUD cards.
 */
@Composable
private fun HudTrapezoidTab(
  title: String,
  accentColor: Color,
  containerColor: Color,
  isLightCard: Boolean = false,
  isTop: Boolean,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .height(24.dp)
      .drawWithContent {
        val w = size.width
        val h = size.height
        val slant = 13.dp.toPx()

        val fillPath = Path().apply {
          if (isTop) {
            moveTo(0f, h)
            lineTo(slant, 0f)
            lineTo(w - slant, 0f)
            lineTo(w, h)
            close()
          } else {
            moveTo(0f, 0f)
            lineTo(slant, h)
            lineTo(w - slant, h)
            lineTo(w, 0f)
            close()
          }
        }

        drawPath(
          path = fillPath,
          color = containerColor
        )

        val borderPath = Path().apply {
          if (isTop) {
            moveTo(0f, h)
            lineTo(slant, 0f)
            lineTo(w - slant, 0f)
            lineTo(w, h)
          } else {
            moveTo(0f, 0f)
            lineTo(slant, h)
            lineTo(w - slant, h)
            lineTo(w, 0f)
          }
        }

        drawPath(
          path = borderPath,
          color = accentColor.copy(alpha = 0.85f),
          style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawContent()
      }
      .padding(horizontal = 20.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(
        modifier = Modifier
          .size(5.dp)
          .clip(CircleShape)
          .background(accentColor)
      )
      Text(
        text = title.uppercase(),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.0.sp,
        color = if (isLightCard) JarvisHudColors.LightTextPrimary else JarvisHudColors.TextPrimary
      )
    }
  }
}

/**
 * Renders a compact octagonal HUD badge / chip with 4 symmetrical cut corners and double-frame styling.
 */
@Composable
fun JarvisHudBadge(
  modifier: Modifier = Modifier,
  accentColor: Color = JarvisHudColors.HologramCyan,
  containerColor: Color = Color(0xD9040E1A),
  cutSize: Dp = 7.dp,
  onClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit
) {
  val shape = CutCornerShape(cutSize)
  val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

  Box(
    modifier = modifier
      .clip(shape)
      .background(containerColor)
      .then(clickMod)
      .drawWithContent {
        drawContent()
        drawOctagonalDoubleFrame(
          accentColor = accentColor,
          cutPx = cutSize.toPx(),
          drawInnerFrame = false,
          outerStroke = 1.4f
        )
      },
    content = content
  )
}

/**
 * Draws the signature double-layer octagonal border with recessed inner octagon and corner chevron ticks
 * exactly matching `weyky.jpg`.
 */
private fun DrawScope.drawOctagonalDoubleFrame(
  accentColor: Color,
  cutPx: Float,
  drawInnerFrame: Boolean = true,
  outerStroke: Float = 1.7f
) {
  val w = size.width
  val h = size.height
  val c = cutPx.coerceAtMost(minOf(w, h) * 0.35f)

  // 1. Outer Octagonal Frame
  val outerPath = Path().apply {
    moveTo(c, 0f)
    lineTo(w - c, 0f)
    lineTo(w, c)
    lineTo(w, h - c)
    lineTo(w - c, h)
    lineTo(c, h)
    lineTo(0f, h - c)
    lineTo(0f, c)
    close()
  }
  drawPath(
    path = outerPath,
    color = accentColor.copy(alpha = 0.78f),
    style = Stroke(width = outerStroke, join = StrokeJoin.Miter)
  )

  // 2. Inner Recessed Octagonal Frame (creates the double-walled sci-fi casing in weyky.jpg)
  if (drawInnerFrame && w > 60f && h > 40f) {
    val inset = 4.dp.toPx()
    val ic = (c - inset * 0.6f).coerceAtLeast(2f)
    val innerPath = Path().apply {
      moveTo(inset + ic, inset)
      lineTo(w - inset - ic, inset)
      lineTo(w - inset, inset + ic)
      lineTo(w - inset, h - inset - ic)
      lineTo(w - inset - ic, h - inset)
      lineTo(inset + ic, h - inset)
      lineTo(inset, h - inset - ic)
      lineTo(inset, inset + ic)
      close()
    }
    drawPath(
      path = innerPath,
      color = accentColor.copy(alpha = 0.28f),
      style = Stroke(width = 0.9f, join = StrokeJoin.Miter)
    )
  }

  // 3. Bright Corner Chamfer Chevrons (reinforces the 4 angled corners)
  val arm = (c * 0.55f).coerceAtLeast(4f)
  val cornerBrackets = Path().apply {
    // Top-Left chamfer
    moveTo(0f, c + arm)
    lineTo(0f, c)
    lineTo(c, 0f)
    lineTo(c + arm, 0f)

    // Top-Right chamfer
    moveTo(w - c - arm, 0f)
    lineTo(w - c, 0f)
    lineTo(w, c)
    lineTo(w, c + arm)

    // Bottom-Right chamfer
    moveTo(w, h - c - arm)
    lineTo(w, h - c)
    lineTo(w - c, h)
    lineTo(w - c - arm, h)

    // Bottom-Left chamfer
    moveTo(c + arm, h)
    lineTo(c, h)
    lineTo(0f, h - c)
    lineTo(0f, h - c - arm)
  }
  drawPath(
    path = cornerBrackets,
    color = accentColor.copy(alpha = 0.98f),
    style = Stroke(width = outerStroke + 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  )
}
