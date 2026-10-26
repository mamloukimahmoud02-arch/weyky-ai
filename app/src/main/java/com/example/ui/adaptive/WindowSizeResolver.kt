package com.example.ui.adaptive

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

object WindowSizeResolver {

  /**
   * Pure functional resolver for device profile.
   * Enables comprehensive, deterministic automated testing across all phone,
   * tablet, split-screen, landscape, and foldable configurations.
   */
  fun resolve(
    widthDp: Dp,
    heightDp: Dp,
    isRtl: Boolean = false,
    posture: DevicePosture = DevicePosture.NORMAL,
    foldFeature: FoldFeatureInfo? = null,
    physicalScreenWidthDp: Dp? = null,
    physicalScreenHeightDp: Dp? = null,
    windowOrientation: AdaptiveOrientation? = null
  ): DeviceProfile {
    // 1. Resolve standard Android Window Size Classes
    val widthClass = when {
      widthDp < 600.dp -> WindowWidthSizeClass.COMPACT
      widthDp < 840.dp -> WindowWidthSizeClass.MEDIUM
      else -> WindowWidthSizeClass.EXPANDED
    }

    val heightClass = when {
      heightDp < 480.dp -> WindowHeightSizeClass.COMPACT
      heightDp < 900.dp -> WindowHeightSizeClass.MEDIUM
      else -> WindowHeightSizeClass.EXPANDED
    }

    // 2. Resolve orientation:
    // Respect the actual window/device orientation if provided (from Configuration),
    // or fall back to physical screen aspect ratio before checking insets-adjusted height.
    // This prevents soft keyboard appearances from falsely flipping a portrait phone into landscape!
    val orientation = windowOrientation ?: run {
      if (physicalScreenWidthDp != null && physicalScreenHeightDp != null) {
        if (physicalScreenWidthDp >= physicalScreenHeightDp) AdaptiveOrientation.LANDSCAPE else AdaptiveOrientation.PORTRAIT
      } else {
        if (widthDp >= heightDp) AdaptiveOrientation.LANDSCAPE else AdaptiveOrientation.PORTRAIT
      }
    }

    // 3. Multi-window / Split-screen detection
    // True split-screen is when the application window width is partitioned (e.g. side-by-side)
    // or when run in narrow vertical split (< 45% screen height in landscape).
    // The soft keyboard appearing in a normal full-width portrait app must NEVER be flagged as split-screen!
    val isSplitScreen = run {
      if (physicalScreenWidthDp != null && physicalScreenHeightDp != null) {
        val widthFraction = widthDp / physicalScreenWidthDp
        val heightFraction = heightDp / physicalScreenHeightDp
        widthFraction < 0.85f || (heightFraction < 0.45f && orientation == AdaptiveOrientation.LANDSCAPE)
      } else {
        // Fallback heuristic: landscape with narrow width
        (orientation == AdaptiveOrientation.LANDSCAPE && widthDp < 540.dp)
      }
    }

    val isCompactLandscape = orientation == AdaptiveOrientation.LANDSCAPE &&
      (heightDp < 500.dp || widthClass == WindowWidthSizeClass.COMPACT)

    // Smart hardware classification (Tablet vs Phone) invariant to Portrait/Landscape rotation:
    val smallestDimDp = minOf(
      physicalScreenWidthDp ?: widthDp,
      physicalScreenHeightDp ?: heightDp
    )
    val largestDimDp = maxOf(
      physicalScreenWidthDp ?: widthDp,
      physicalScreenHeightDp ?: heightDp
    )

    val isTablet = smallestDimDp >= 600.dp && largestDimDp >= 840.dp

    val deviceCategory = when {
      !isTablet -> DeviceCategory.PHONE
      smallestDimDp >= 720.dp || largestDimDp >= 1080.dp -> DeviceCategory.TABLET_LARGE
      else -> DeviceCategory.TABLET_COMPACT
    }

    val uiScaleFactor = when (deviceCategory) {
      DeviceCategory.TABLET_LARGE -> {
        val ratio = (largestDimDp.value / 960f).coerceIn(1.18f, 1.38f)
        ratio
      }
      DeviceCategory.TABLET_COMPACT -> {
        val ratio = (largestDimDp.value / 840f).coerceIn(1.12f, 1.24f)
        ratio
      }
      DeviceCategory.PHONE -> {
        if (smallestDimDp < 350.dp || (orientation == AdaptiveOrientation.LANDSCAPE && heightDp < 380.dp)) {
          0.94f
        } else {
          1.0f
        }
      }
    }

    // 4. Resolve high-level layout mode (Tablets support both Portrait and Landscape in TABLET_EXPANDED)
    val layoutMode = when {
      posture == DevicePosture.TABLETOP && heightDp >= 440.dp -> AdaptiveLayoutMode.FOLDABLE_TABLETOP
      posture == DevicePosture.BOOK && widthDp >= 580.dp -> AdaptiveLayoutMode.FOLDABLE_BOOK
      isCompactLandscape -> AdaptiveLayoutMode.PHONE_LANDSCAPE
      orientation == AdaptiveOrientation.LANDSCAPE -> {
        if (widthClass == WindowWidthSizeClass.EXPANDED || isTablet) {
          AdaptiveLayoutMode.TABLET_EXPANDED
        } else {
          AdaptiveLayoutMode.PHONE_LANDSCAPE
        }
      }
      isSplitScreen && widthDp < 420.dp -> AdaptiveLayoutMode.SPLIT_SCREEN_COMPACT
      isTablet || widthClass == WindowWidthSizeClass.EXPANDED -> AdaptiveLayoutMode.TABLET_EXPANDED
      else -> AdaptiveLayoutMode.PHONE_PORTRAIT
    }

    // 5. Content density & card styling parameters:
    // Tablets in BOTH Portrait and Landscape receive EXPANDED density so all cards scale up properly.
    val density = when {
      heightDp < 480.dp || widthDp < 360.dp -> CardDensity.COMPACT
      isTablet || (widthDp >= 840.dp && heightDp >= 650.dp) -> CardDensity.EXPANDED
      else -> CardDensity.STANDARD
    }

    val maxCardWidth = when (layoutMode) {
      AdaptiveLayoutMode.PHONE_PORTRAIT -> minOf(widthDp - 20.dp, 600.dp)
      AdaptiveLayoutMode.PHONE_LANDSCAPE -> minOf(widthDp * 0.54f, 540.dp)
      AdaptiveLayoutMode.TABLET_EXPANDED -> minOf(widthDp - 48.dp, 760.dp)
      AdaptiveLayoutMode.FOLDABLE_BOOK -> minOf((widthDp / 2f) - 16.dp, 500.dp)
      AdaptiveLayoutMode.FOLDABLE_TABLETOP -> minOf(widthDp - 24.dp, 640.dp)
      AdaptiveLayoutMode.SPLIT_SCREEN_COMPACT -> widthDp - 12.dp
    }

    val paddingH = when (density) {
      CardDensity.COMPACT -> 10.dp
      CardDensity.STANDARD -> 16.dp
      CardDensity.EXPANDED -> 24.dp
    }

    val paddingV = when (density) {
      CardDensity.COMPACT -> 4.dp
      CardDensity.STANDARD -> 6.dp
      CardDensity.EXPANDED -> 12.dp
    }

    val spacing = when (density) {
      CardDensity.COMPACT -> 6.dp
      CardDensity.STANDARD -> 10.dp
      CardDensity.EXPANDED -> 14.dp
    }

    return DeviceProfile(
      windowWidthDp = widthDp,
      windowHeightDp = heightDp,
      widthSizeClass = widthClass,
      heightSizeClass = heightClass,
      orientation = orientation,
      posture = posture,
      foldFeature = foldFeature,
      layoutMode = layoutMode,
      isCompactLandscape = isCompactLandscape,
      isTablet = isTablet,
      isSplitScreen = isSplitScreen,
      isRtl = isRtl,
      cardDensity = density,
      maxCardWidthDp = maxCardWidth,
      contentPaddingHorizontalDp = paddingH,
      contentPaddingVerticalDp = paddingV,
      cardSpacingDp = spacing,
      deviceCategory = deviceCategory,
      smallestScreenWidthDp = smallestDimDp,
      uiScaleFactor = uiScaleFactor
    )
  }
}

/**
 * Rememberable, lifecycle-aware Composable providing the current [DeviceProfile].
 * Automatically updates whenever window metrics or configuration change without continuous polling.
 */
@Composable
fun rememberAdaptiveLayoutState(
  containerWidthDp: Dp? = null,
  containerHeightDp: Dp? = null
): DeviceProfile {
  val configuration = LocalConfiguration.current
  val layoutDirection = LocalLayoutDirection.current
  val isRtl = layoutDirection == LayoutDirection.Rtl

  val configOrientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
    AdaptiveOrientation.LANDSCAPE
  } else {
    AdaptiveOrientation.PORTRAIT
  }

  // Determine available dimensions (passed container or window configuration)
  val widthDp = containerWidthDp ?: configuration.screenWidthDp.dp
  val heightDp = containerHeightDp ?: configuration.screenHeightDp.dp

  // Physical screen dimensions for split-screen heuristic detection
  val physicalWidthDp = configuration.screenWidthDp.dp
  val physicalHeightDp = configuration.screenHeightDp.dp

  // Fold posture detection (pure runtime safe detection without heavy or unstable dependencies)
  val posture = remember(configuration.orientation, widthDp, heightDp) {
    detectSafePosture(configuration, widthDp, heightDp)
  }

  return remember(widthDp, heightDp, isRtl, posture, configOrientation) {
    WindowSizeResolver.resolve(
      widthDp = widthDp,
      heightDp = heightDp,
      isRtl = isRtl,
      posture = posture,
      physicalScreenWidthDp = physicalWidthDp,
      physicalScreenHeightDp = physicalHeightDp,
      windowOrientation = configOrientation
    )
  }
}

/**
 * Safe posture detection that functions without crashing on standard devices
 * while respecting tabletop or book posture when configuration or display features indicate it.
 */
private fun detectSafePosture(
  configuration: Configuration,
  widthDp: Dp,
  heightDp: Dp
): DevicePosture {
  // If running on a square or foldable ratio unfolded
  val aspectRatio = if (heightDp > 0.dp) widthDp / heightDp else 1.0f

  // Foldable unfolded square/near-square screens (e.g. Galaxy Z Fold, Pixel Fold unfolded)
  if (aspectRatio in 0.85f..1.15f && widthDp >= 580.dp) {
    return DevicePosture.BOOK
  }

  return DevicePosture.NORMAL
}
