package com.example.ui.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Android Window Size Class Breakpoints.
 * Compact: < 600dp (phones in portrait, split-screen)
 * Medium: 600dp .. < 840dp (tablets in portrait, foldables unfolded, small laptops)
 * Expanded: >= 840dp (tablets in landscape, desktop windows)
 */
enum class WindowWidthSizeClass {
  COMPACT,
  MEDIUM,
  EXPANDED
}

/**
 * Standard Window Height Size Class Breakpoints.
 * Compact: < 480dp (phones in landscape, split-screen horizontal)
 * Medium: 480dp .. < 900dp (standard portrait phones, tablets)
 * Expanded: >= 900dp (tall tablets, desktop screens)
 */
enum class WindowHeightSizeClass {
  COMPACT,
  MEDIUM,
  EXPANDED
}

enum class AdaptiveOrientation {
  PORTRAIT,
  LANDSCAPE
}

/**
 * Posture for foldables and dual-screen devices.
 */
enum class DevicePosture {
  NORMAL,
  BOOK,       // Vertical hinge, left and right display regions
  TABLETOP,   // Horizontal hinge, top and bottom display regions
  FLAT
}

@Immutable
data class FoldFeatureInfo(
  val isSeparating: Boolean = false,
  val boundsLeftDp: Dp = 0.dp,
  val boundsTopDp: Dp = 0.dp,
  val boundsRightDp: Dp = 0.dp,
  val boundsBottomDp: Dp = 0.dp,
  val orientation: AdaptiveOrientation = AdaptiveOrientation.PORTRAIT
)

/**
 * Content density scale for cards and text spacing.
 */
enum class CardDensity {
  COMPACT,
  STANDARD,
  EXPANDED
}

/**
 * High-level layout strategy resolved from window metrics and posture.
 */
enum class AdaptiveLayoutMode {
  PHONE_PORTRAIT,        // Single column vertical composition
  PHONE_LANDSCAPE,       // Side-by-side: Core on one side, HUD & Controls on the other
  TABLET_EXPANDED,       // Multi-pane balanced tactical HUD
  SPLIT_SCREEN_COMPACT,  // Compact vertical or horizontal split with minimal margins
  FOLDABLE_TABLETOP,     // Top half visual/status, bottom half controls
  FOLDABLE_BOOK          // Left pane core/interact, right pane telemetry/events
}

/**
 * Centralized, immutable snapshot of the adaptive environment.
 */
@Immutable
data class DeviceProfile(
  val windowWidthDp: Dp,
  val windowHeightDp: Dp,
  val widthSizeClass: WindowWidthSizeClass,
  val heightSizeClass: WindowHeightSizeClass,
  val orientation: AdaptiveOrientation,
  val posture: DevicePosture = DevicePosture.NORMAL,
  val foldFeature: FoldFeatureInfo? = null,
  val layoutMode: AdaptiveLayoutMode,
  val isCompactLandscape: Boolean,
  val isTablet: Boolean,
  val isSplitScreen: Boolean,
  val isRtl: Boolean,
  val cardDensity: CardDensity,
  val maxCardWidthDp: Dp,
  val contentPaddingHorizontalDp: Dp,
  val contentPaddingVerticalDp: Dp,
  val cardSpacingDp: Dp
) {
  /**
   * Computes an optimal core size in dp given the user's coreScale preference (default 0.44).
   * Ensures the core is never distorted, never squashed below usable limits, and never overflows.
   */
  fun calculateAdaptiveCoreSize(coreScale: Float): Dp {
    val scale = coreScale.coerceIn(0.2f, 1.0f)
    return when (layoutMode) {
      AdaptiveLayoutMode.PHONE_LANDSCAPE -> {
        // In landscape, height is the constraining dimension.
        // Core is positioned in its own column, so it can use up to 75% of height.
        val availableHeight = (windowHeightDp - 48.dp).coerceAtLeast(80.dp)
        val candidate = windowHeightDp * scale * 1.05f
        minOf(candidate, availableHeight).coerceIn(90.dp, 280.dp)
      }
      AdaptiveLayoutMode.TABLET_EXPANDED,
      AdaptiveLayoutMode.FOLDABLE_BOOK -> {
        // In tablet mode, calculate based on orientation so portrait tablets can use their centered workspace
        val paneDimension = if (orientation == AdaptiveOrientation.PORTRAIT) {
          minOf(windowWidthDp * 0.55f, windowHeightDp * 0.45f)
        } else {
          minOf(windowWidthDp * 0.45f, windowHeightDp * 0.65f)
        }
        (paneDimension * scale * 1.5f).coerceIn(160.dp, 380.dp)
      }
      AdaptiveLayoutMode.FOLDABLE_TABLETOP -> {
        // Top half viewing pane
        val topHalfHeight = (windowHeightDp / 2f) - 32.dp
        (topHalfHeight * scale * 1.2f).coerceIn(100.dp, 260.dp)
      }
      AdaptiveLayoutMode.SPLIT_SCREEN_COMPACT -> {
        val minDim = minOf(windowWidthDp, windowHeightDp)
        (minDim * scale).coerceIn(76.dp, 200.dp)
      }
      AdaptiveLayoutMode.PHONE_PORTRAIT -> {
        val screenMinDim = minOf(windowWidthDp, windowHeightDp)
        val candidate = screenMinDim * scale
        // Ensure space for HUD top card (~120dp) and bottom command bar (~140dp)
        val maxAvailable = (windowHeightDp - 260.dp).coerceAtLeast(88.dp)
        minOf(candidate, maxAvailable).coerceAtLeast(88.dp)
      }
    }
  }
}
