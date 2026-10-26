package com.example.ui.home.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.state.WeykyUiState
import com.example.device.apps.AppMatch
import com.example.device.battery.ChargingState
import com.example.ui.adaptive.DeviceProfile
import com.example.ui.home.HomeContextAlertCard
import com.example.ui.home.HomeCoreSection
import com.example.ui.home.HomeResponseCard
import com.example.ui.theme.JarvisHudColors
import com.example.ui.theme.WeykyThemeMode

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Responsive metrics tailored for Tablet Landscape / Desktop mode.
 * Dynamically scales taskbar, HUD pills, music/notification cards, Start Menu, and Core
 * proportionally to the tablet's window dimensions.
 */
@Immutable
data class DesktopTabletMetrics(
  val scale: Float,
  val horizontalPadding: Dp,
  val topHudPadding: Dp,
  val taskbarHeight: Dp,
  val taskbarControlHeight: Dp,
  val taskbarHorizontalMargin: Dp,
  val taskbarBottomMargin: Dp,
  val reservedBottomPadding: Dp,
  val startEmblemBoxSize: Dp,
  val startEmblemIconSize: Dp,
  val startTitleSp: TextUnit,
  val searchMinWidth: Dp,
  val searchMaxWidth: Dp,
  val searchFontSp: TextUnit,
  val pinnedAppBoxSize: Dp,
  val pinnedAppIconSize: Dp,
  val systemTrayFontSp: TextUnit,
  val systemTrayIconSize: Dp,
  val topPillHeight: Dp,
  val topLocationMaxWidth: Dp,
  val topPillIconSize: Dp,
  val topPillPrimarySp: TextUnit,
  val topPillTempSp: TextUnit,
  val mediaCardMinWidth: Dp,
  val mediaCardMaxWidth: Dp,
  val mediaCardHeight: Dp,
  val mediaArtBoxSize: Dp,
  val mediaTitleSp: TextUnit,
  val mediaSubtitleSp: TextUnit,
  val mediaPlayButtonSize: Dp,
  val mediaTransportButtonSize: Dp,
  val maxCoreSize: Dp,
  val responseMaxWidth: Dp,
  val responseMaxHeight: Dp,
  val startMenuMinWidth: Dp,
  val startMenuMaxWidth: Dp,
  val startMenuMinHeight: Dp,
  val startMenuMaxHeight: Dp
)

@Composable
fun rememberDesktopTabletMetrics(
  deviceProfile: DeviceProfile
): DesktopTabletMetrics {
  val windowWidthDp = deviceProfile.windowWidthDp
  val windowHeightDp = deviceProfile.windowHeightDp
  val deviceCategory = deviceProfile.deviceCategory
  val isTablet = deviceProfile.isTablet

  return remember(windowWidthDp, windowHeightDp, deviceCategory, isTablet) {
    // Smart scaling decision based on detected DeviceCategory (Tablet vs Phone) + exact screen dimensions
    val scale = when (deviceCategory) {
      com.example.ui.adaptive.DeviceCategory.TABLET_LARGE -> {
        val widthRatio = (windowWidthDp.value / 880f).coerceIn(1.22f, 1.44f)
        val heightRatio = (windowHeightDp.value / 560f).coerceIn(1.20f, 1.42f)
        ((widthRatio * 0.55f) + (heightRatio * 0.45f)).coerceIn(1.22f, 1.42f)
      }
      com.example.ui.adaptive.DeviceCategory.TABLET_COMPACT -> {
        val widthRatio = (windowWidthDp.value / 800f).coerceIn(1.14f, 1.30f)
        val heightRatio = (windowHeightDp.value / 520f).coerceIn(1.12f, 1.28f)
        ((widthRatio * 0.55f) + (heightRatio * 0.45f)).coerceIn(1.15f, 1.28f)
      }
      com.example.ui.adaptive.DeviceCategory.PHONE -> {
        val widthRatio = (windowWidthDp.value / 780f).coerceIn(0.96f, 1.12f)
        val heightRatio = (windowHeightDp.value / 400f).coerceIn(0.95f, 1.10f)
        ((widthRatio * 0.50f) + (heightRatio * 0.50f)).coerceIn(0.98f, 1.10f)
      }
    }

    val taskbarHeight = if (isTablet) {
      (50f * scale).dp.coerceIn(58.dp, 68.dp)
    } else {
      (48f * scale).dp.coerceIn(48.dp, 54.dp)
    }
    val taskbarControlHeight = if (isTablet) {
      (36f * scale).dp.coerceIn(42.dp, 50.dp)
    } else {
      (34f * scale).dp.coerceIn(34.dp, 38.dp)
    }
    val taskbarHorizontalMargin = (12f * scale).dp.coerceIn(10.dp, 24.dp)
    val taskbarBottomMargin = (5f * scale).dp.coerceIn(4.dp, 12.dp)
    val reservedBottomPadding = taskbarHeight + taskbarBottomMargin + 6.dp
    val maxMenuHeight = (windowHeightDp - reservedBottomPadding - 14.dp).coerceIn(260.dp, 640.dp)

    DesktopTabletMetrics(
      scale = scale,
      horizontalPadding = (14f * scale).dp.coerceIn(14.dp, 28.dp),
      topHudPadding = (8f * scale).dp.coerceIn(8.dp, 18.dp),
      taskbarHeight = taskbarHeight,
      taskbarControlHeight = taskbarControlHeight,
      taskbarHorizontalMargin = taskbarHorizontalMargin,
      taskbarBottomMargin = taskbarBottomMargin,
      reservedBottomPadding = reservedBottomPadding,
      startEmblemBoxSize = (24f * scale).dp.coerceIn(24.dp, 36.dp),
      startEmblemIconSize = (20f * scale).dp.coerceIn(20.dp, 30.dp),
      startTitleSp = (12f * scale).sp,
      searchMinWidth = (windowWidthDp * 0.21f).coerceIn(if (isTablet) 210.dp else 150.dp, 290.dp),
      searchMaxWidth = (windowWidthDp * 0.29f).coerceIn(if (isTablet) 270.dp else 215.dp, 390.dp),
      searchFontSp = (12f * scale).sp,
      pinnedAppBoxSize = (34f * scale).dp.coerceIn(34.dp, 48.dp),
      pinnedAppIconSize = (26f * scale).dp.coerceIn(26.dp, 36.dp),
      systemTrayFontSp = (11.2f * scale).sp,
      systemTrayIconSize = (15f * scale).dp.coerceIn(15.dp, 22.dp),
      topPillHeight = (36f * scale).dp.coerceIn(if (isTablet) 44.dp else 36.dp, 52.dp),
      topLocationMaxWidth = (windowWidthDp * 0.27f).coerceIn(if (isTablet) 240.dp else 190.dp, 350.dp),
      topPillIconSize = (15f * scale).dp.coerceIn(15.dp, 22.dp),
      topPillPrimarySp = (11.2f * scale).sp,
      topPillTempSp = (13.2f * scale).sp,
      mediaCardMinWidth = (windowWidthDp * 0.31f).coerceIn(if (isTablet) 295.dp else 250.dp, 360.dp),
      mediaCardMaxWidth = (windowWidthDp * 0.37f).coerceIn(if (isTablet) 335.dp else 295.dp, 420.dp),
      mediaCardHeight = (56f * scale).dp.coerceIn(if (isTablet) 66.dp else 56.dp, 76.dp),
      mediaArtBoxSize = (38f * scale).dp.coerceIn(if (isTablet) 46.dp else 38.dp, 54.dp),
      mediaTitleSp = (11.2f * scale).sp,
      mediaSubtitleSp = (9.2f * scale).sp,
      mediaPlayButtonSize = (32f * scale).dp.coerceIn(32.dp, 44.dp),
      mediaTransportButtonSize = (28f * scale).dp.coerceIn(28.dp, 40.dp),
      maxCoreSize = (windowHeightDp * 0.74f).coerceIn(if (isTablet) 260.dp else 180.dp, 460.dp),
      responseMaxWidth = (windowWidthDp * 0.62f).coerceIn(480.dp, 840.dp),
      responseMaxHeight = (windowHeightDp * 0.24f).coerceIn(84.dp, 180.dp),
      startMenuMinWidth = (windowWidthDp * 0.40f).coerceIn(360.dp, 500.dp),
      startMenuMaxWidth = (windowWidthDp * 0.48f).coerceIn(420.dp, 600.dp),
      startMenuMinHeight = minOf(if (isTablet) 380.dp else 280.dp, maxMenuHeight),
      startMenuMaxHeight = maxMenuHeight
    )
  }
}

/**
 * Redesigned Arabic RTL Landscape Desktop Experience for WEYKY Launcher.
 * Tailored for Tablets and widescreen ergonomics:
 * - Upper-Right: Proportional interactive information pills (Location, Weather) -> speaks info on click.
 * - Upper-Left: Proportional interactive music player widget & compact notification card.
 * - Center Stage: Completely protected, spacious living WEYKY Core scaled for tablets.
 * - Bottom: Tablet-scaled desktop taskbar with Arabic search box, pinned apps dock, network status, clock, and battery.
 */
@Composable
fun DesktopLandscapeHomeScreen(
  uiState: WeykyUiState,
  deviceProfile: DeviceProfile,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  isLight: Boolean,
  onQuerySubmit: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onRequestPermission: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onConfirmOption: (String) -> Unit,
  onToggleAppDrawer: () -> Unit,
  onLaunchApp: (AppMatch) -> Unit,
  onToggleStartMenu: (Boolean?) -> Unit,
  onPinApp: (String) -> Unit,
  onUnpinApp: (String) -> Unit,
  onCallClick: () -> Unit,
  onRequestDefaultLauncher: () -> Unit,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  onLaunchMusicApp: () -> Unit = {},
  onNetworkClick: () -> Unit = {},
  onNetworkLongClick: () -> Unit = {},
  onBatteryClick: () -> Unit = {},
  onTimeClick: () -> Unit = {},
  onDateClick: () -> Unit = {},
  onWeatherClick: () -> Unit = {},
  onLocationClick: () -> Unit = {},
  keyboardController: SoftwareKeyboardController?,
  modifier: Modifier = Modifier
) {
  val metrics = rememberDesktopTabletMetrics(deviceProfile = deviceProfile)
  val dynamicCoreSize = deviceProfile.calculateAdaptiveCoreSize(uiState.coreScale)
  val hasResponse = !uiState.lastResponseText.isNullOrBlank()

  val battery = uiState.batterySnapshot
  val isLowBattery = battery != null && battery.isAvailable && battery.percentage <= 20 &&
    battery.chargingState != ChargingState.CHARGING &&
    battery.chargingState != ChargingState.FULL
  val isBatteryFull = battery != null && battery.isAvailable && battery.percentage == 100 &&
    battery.chargingState == ChargingState.FULL
  val hasMissedCall = (uiState.missedCallsSummary?.missedCallsCount ?: 0) > 0
  val hasUrgentNotif = (uiState.notificationSummary?.totalImportantCount ?: 0) > 0
  val hasFallbackEvent = uiState.importantEvents.isNotEmpty()
  val hasAlert = isLowBattery || isBatteryFull || hasMissedCall || hasUrgentNotif || hasFallbackEvent

  val isMusicActivelyPlaying = uiState.isMusicPlaying || (uiState.activeMediaMetadata?.isPlaying == true)

  // Arabic RTL Desktop Workspace Layout
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .testTag("desktop_landscape_root")
    ) {
      // 1A. CENTER WORKSPACE (Spacious Central Core + Response Bubble above Taskbar)
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(
            start = metrics.horizontalPadding,
            end = metrics.horizontalPadding,
            top = metrics.topHudPadding + metrics.topPillHeight + 4.dp,
            bottom = metrics.reservedBottomPadding
          ),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Center Stage: The protected, untouched WEYKY Living Core scaled for tablets
        BoxWithConstraints(
          modifier = Modifier
            .weight(1f, fill = true)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          val maxAvailableDim = minOf(maxHeight, maxWidth)
          val calculatedCoreSize = minOf(dynamicCoreSize, maxAvailableDim * 0.95f)
            .coerceIn(minOf(110.dp, maxAvailableDim * 0.80f), metrics.maxCoreSize)

          HomeCoreSection(
            uiState = uiState,
            coreSizeDp = calculatedCoreSize,
            onToggleVoice = onToggleVoice
          )
        }

        // Assistant Response Card (if active, floating neatly above taskbar)
        if (hasResponse) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = metrics.responseMaxWidth)
              .heightIn(max = metrics.responseMaxHeight)
              .padding(bottom = 4.dp)
              .verticalScroll(rememberScrollState())
          ) {
            HomeResponseCard(
              uiState = uiState,
              borderColor = borderColor,
              panelBg = panelBg,
              panelBgElevated = panelBgElevated,
              textPrimary = textPrimary,
              onQuerySubmit = onQuerySubmit,
              onConfirmOption = onConfirmOption,
              onRequestPermission = onRequestPermission
            )
          }
        }
      }

      // 1B. TOP HUD ROW: In RTL: Info Widgets on Right (Start), Music Player & Compact Alerts on Left (End)
      Row(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .padding(
            start = metrics.horizontalPadding,
            end = metrics.horizontalPadding,
            top = metrics.topHudPadding
          ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        // Upper-Right (Start in RTL): Proportional Information Pills (Location, Weather)
        DesktopCompactInfoPills(
          uiState = uiState,
          metrics = metrics,
          themeMode = uiState.themeMode,
          borderColor = borderColor,
          panelBg = panelBgElevated,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onLocationClick = onLocationClick,
          onWeatherClick = onWeatherClick,
          onDateClick = onDateClick
        )

        // Upper-Left (End in RTL):
        // - Music player appears ONLY when music is playing.
        // - Notifications card is compact (same size as music player):
        //   appears in the music player's spot when music is not playing, or directly below it when music is playing.
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          AnimatedVisibility(
            visible = isMusicActivelyPlaying,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            DesktopCompactMusicWidget(
              uiState = uiState,
              metrics = metrics,
              themeMode = uiState.themeMode,
              borderColor = borderColor,
              panelBg = panelBgElevated,
              textPrimary = textPrimary,
              textSecondary = textSecondary,
              onToggleMediaPlayback = onToggleMediaPlayback,
              onPreviousMediaTrack = onPreviousMediaTrack,
              onNextMediaTrack = onNextMediaTrack,
              onLaunchMusicApp = onLaunchMusicApp
            )
          }

          if (hasAlert) {
            HomeContextAlertCard(
              uiState = uiState,
              borderColor = borderColor,
              onQuerySubmit = onQuerySubmit,
              compactLandscapeMode = true,
              landscapeMinWidth = metrics.mediaCardMinWidth,
              landscapeMaxWidth = metrics.mediaCardMaxWidth,
              landscapeHeight = metrics.mediaCardHeight,
              landscapeIconBoxSize = metrics.mediaArtBoxSize,
              landscapeTitleSp = metrics.mediaTitleSp,
              landscapeSubtitleSp = metrics.mediaSubtitleSp
            )
          }
        }
      }

      // 2. BOTTOM DESKTOP TASKBAR (Floating tablet-proportioned bar across the bottom)
      DesktopTaskbar(
        uiState = uiState,
        metrics = metrics,
        isLight = isLight,
        borderColor = borderColor,
        panelBg = panelBg,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        isStartMenuOpen = uiState.isStartMenuOpen,
        onToggleStartMenu = { onToggleStartMenu(null) },
        onLaunchApp = onLaunchApp,
        onUnpinApp = onUnpinApp,
        onQueryChange = onQueryChange,
        onQuerySubmit = onQuerySubmit,
        onToggleVoice = onToggleVoice,
        onNetworkClick = onNetworkClick,
        onNetworkLongClick = onNetworkLongClick,
        onBatteryClick = onBatteryClick,
        onTimeClick = onTimeClick,
        keyboardController = keyboardController,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(
            horizontal = metrics.taskbarHorizontalMargin,
            vertical = metrics.taskbarBottomMargin
          )
      )

      // 3. START MENU OVERLAY (Opens above the taskbar at Start edge / Right in RTL)
      if (uiState.isStartMenuOpen) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
            ) {
              onToggleStartMenu(false)
            }
        )

        AnimatedVisibility(
          visible = uiState.isStartMenuOpen,
          enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
          exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(
              start = metrics.taskbarHorizontalMargin,
              bottom = metrics.reservedBottomPadding
            )
        ) {
          DesktopStartMenu(
            uiState = uiState,
            metrics = metrics,
            isLight = isLight,
            borderColor = borderColor,
            panelBg = panelBg,
            panelBgElevated = panelBgElevated,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted,
            onDismiss = { onToggleStartMenu(false) },
            onLaunchApp = onLaunchApp,
            onTogglePinApp = onPinApp,
            onOpenSettings = onOpenSettings,
            onRequestDefaultLauncher = onRequestDefaultLauncher
          )
        }
      }
    }
  }
}

/**
 * Tablet-Proportional Desktop Music Player Widget (Upper-Left).
 * - Clicking artwork/icon launches original music app.
 * - Track title and artist with thin volume progress bar.
 * - Transport controls (Previous, circular cyan Play/Pause, Next).
 * - Reacts to all 3 themes (Tactical HUD, Modern, Minimal).
 */
@Composable
private fun DesktopCompactMusicWidget(
  uiState: WeykyUiState,
  metrics: DesktopTabletMetrics,
  themeMode: WeykyThemeMode,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  onToggleMediaPlayback: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onLaunchMusicApp: () -> Unit
) {
  val isTactical = themeMode == WeykyThemeMode.TACTICAL
  val isModern = themeMode == WeykyThemeMode.MODERN

  val mediaMeta = uiState.activeMediaMetadata
  val isMusicActivelyPlaying = uiState.isMusicPlaying || (mediaMeta?.isPlaying == true)

  val trackTitle = mediaMeta?.title?.takeIf { it.isNotBlank() } ?: "تشغيل الموسيقى"
  val trackArtist = mediaMeta?.artist?.takeIf { it.isNotBlank() } ?: "مشغل الصوت"
  val artworkBitmap = mediaMeta?.artworkBitmap

  val cardShape: Shape = when {
    isTactical -> CutCornerShape(8.dp)
    isModern -> RoundedCornerShape(18.dp)
    else -> RoundedCornerShape(12.dp)
  }

  val cardBg = when {
    isTactical -> Color(0xFF030D1A).copy(alpha = 0.90f)
    isModern -> Color(0xFF071426).copy(alpha = 0.80f)
    else -> panelBg.copy(alpha = 0.75f)
  }

  val cardBorder = when {
    isTactical -> borderColor.copy(alpha = 0.80f)
    isModern -> borderColor.copy(alpha = 0.50f)
    else -> borderColor.copy(alpha = 0.35f)
  }

  Box(
    modifier = Modifier
      .widthIn(min = metrics.mediaCardMinWidth, max = metrics.mediaCardMaxWidth)
      .height(metrics.mediaCardHeight)
      .shadow(elevation = if (isModern) 12.dp else 8.dp, shape = cardShape)
      .clip(cardShape)
      .background(cardBg)
      .border(
        width = if (isTactical) 1.3.dp else 1.dp,
        color = cardBorder,
        shape = cardShape
      )
      .padding(horizontal = 10.dp, vertical = 7.dp)
      .testTag("music_top_hud_card")
  ) {
    Row(
      modifier = Modifier.fillMaxSize(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // 1. Album Artwork / Icon (Clickable -> opens original music app!)
      Box(
        modifier = Modifier
          .size(metrics.mediaArtBoxSize)
          .clip(if (isTactical) CutCornerShape(5.dp) else RoundedCornerShape(10.dp))
          .clickable(onClick = onLaunchMusicApp)
          .testTag("music_album_art_button"),
        contentAlignment = Alignment.Center
      ) {
        if (artworkBitmap != null) {
          Image(
            bitmap = artworkBitmap.asImageBitmap(),
            contentDescription = "غلاف الألبوم - فتح التطبيق الأصلي",
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                Brush.linearGradient(
                  colors = listOf(
                    Color(0xFF8A2387),
                    Color(0xFFE94057),
                    Color(0xFF00D2FF)
                  )
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.MusicNote,
              contentDescription = "فتح تطبيق الموسيقى الأصلي",
              tint = Color.White,
              modifier = Modifier.size(metrics.mediaArtBoxSize * 0.52f)
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      // 2. Track Title, Artist, and Thin Volume Line
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = trackTitle,
          color = textPrimary,
          fontSize = metrics.mediaTitleSp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
        )
        Text(
          text = trackArtist,
          color = textSecondary,
          fontSize = metrics.mediaSubtitleSp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtle cyan volume / progress bar
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = "مستوى الصوت",
            tint = Color(0xFF00F0FF).copy(alpha = 0.8f),
            modifier = Modifier.size(12.dp)
          )
          Box(
            modifier = Modifier
              .width(56.dp)
              .height(3.dp)
              .clip(RoundedCornerShape(1.5.dp))
              .background(Color(0xFF00F0FF).copy(alpha = 0.7f))
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // 3. Transport Controls: [ Previous | (Play/Pause) | Next ]
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        IconButton(
          onClick = onPreviousMediaTrack,
          modifier = Modifier
            .size(metrics.mediaTransportButtonSize)
            .testTag("music_prev_button")
        ) {
          Icon(
            imageVector = Icons.Default.SkipPrevious,
            contentDescription = "المقطع السابق",
            tint = Color(0xFF00F0FF),
            modifier = Modifier.size(metrics.mediaTransportButtonSize * 0.62f)
          )
        }

        // Circular Cyan Play/Pause button
        Box(
          modifier = Modifier
            .size(metrics.mediaPlayButtonSize)
            .clip(CircleShape)
            .background(Color(0xFF00F0FF).copy(alpha = 0.15f))
            .border(1.5.dp, Color(0xFF00F0FF), CircleShape)
            .clickable(onClick = onToggleMediaPlayback)
            .testTag("music_play_pause_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isMusicActivelyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isMusicActivelyPlaying) "إيقاف مؤقت" else "تشغيل",
            tint = Color(0xFF00F0FF),
            modifier = Modifier.size(metrics.mediaPlayButtonSize * 0.52f)
          )
        }

        IconButton(
          onClick = onNextMediaTrack,
          modifier = Modifier
            .size(metrics.mediaTransportButtonSize)
            .testTag("music_next_button")
        ) {
          Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = "المقطع التالي",
            tint = Color(0xFF00F0FF),
            modifier = Modifier.size(metrics.mediaTransportButtonSize * 0.62f)
          )
        }
      }
    }
  }
}

/**
 * Tablet-Proportional Top Information Pills:
 * [ 📍 موقعي الحالي ]  [ ☀️ 14° غائم ]
 * Fully interactive: Clicking any pill speaks its details through the WEYKY Core!
 * Reacts to all 3 themes (Tactical HUD, Modern, Minimal).
 */
@Composable
private fun DesktopCompactInfoPills(
  uiState: WeykyUiState,
  metrics: DesktopTabletMetrics,
  themeMode: WeykyThemeMode,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  onLocationClick: () -> Unit,
  onWeatherClick: () -> Unit,
  onDateClick: () -> Unit
) {
  val isTactical = themeMode == WeykyThemeMode.TACTICAL
  val isModern = themeMode == WeykyThemeMode.MODERN

  val pillShape: Shape = when {
    isTactical -> CutCornerShape(6.dp)
    isModern -> RoundedCornerShape(18.dp)
    else -> RoundedCornerShape(10.dp)
  }

  val pillBg = when {
    isTactical -> Color(0xFF030D1A).copy(alpha = 0.90f)
    isModern -> Color(0xFF071426).copy(alpha = 0.80f)
    else -> panelBg.copy(alpha = 0.75f)
  }

  val pillBorder = when {
    isTactical -> borderColor.copy(alpha = 0.80f)
    isModern -> borderColor.copy(alpha = 0.50f)
    else -> borderColor.copy(alpha = 0.35f)
  }

  val weather = uiState.weatherSnapshot
  val tempText = if (weather != null && weather.isAvailable && weather.temperatureCelsius != null) {
    "${weather.temperatureCelsius.toInt()}°"
  } else {
    "14°"
  }
  val conditionText = weather?.condition ?: "غائم"
  val locationText = weather?.locationName
    ?.takeIf { it.isNotBlank() && it != "موقعي الحالي" }
    ?: "جاري تحديد المنطقة..."

  Row(
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // 1. Location Pill: [ 📍 اسم المنطقة الجغرافية ] -> Clickable: speaks location
    Row(
      modifier = Modifier
        .height(metrics.topPillHeight)
        .widthIn(max = metrics.topLocationMaxWidth)
        .clip(pillShape)
        .background(pillBg)
        .border(
          width = if (isTactical) 1.3.dp else 1.dp,
          color = pillBorder,
          shape = pillShape
        )
        .clickable(onClick = onLocationClick)
        .padding(horizontal = 14.dp)
        .testTag("desktop_location_pill"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = Icons.Default.LocationOn,
        contentDescription = "الموقع الحالي",
        tint = Color(0xFF00F0FF),
        modifier = Modifier.size(metrics.topPillIconSize)
      )
      Text(
        text = locationText,
        color = textPrimary,
        fontSize = metrics.topPillPrimarySp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
      )
    }

    // 2. Weather Pill: [ ☀️ 14° غائم ] -> Clickable: speaks weather
    Row(
      modifier = Modifier
        .height(metrics.topPillHeight)
        .clip(pillShape)
        .background(pillBg)
        .border(
          width = if (isTactical) 1.3.dp else 1.dp,
          color = pillBorder,
          shape = pillShape
        )
        .clickable(onClick = onWeatherClick)
        .padding(horizontal = 14.dp)
        .testTag("desktop_weather_pill"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = if (conditionText.contains("مطر")) Icons.Default.Cloud else Icons.Default.WbSunny,
        contentDescription = "الطقس",
        tint = Color(0xFF00F0FF),
        modifier = Modifier.size(metrics.topPillIconSize)
      )
      Text(
        text = tempText,
        color = textPrimary,
        fontSize = metrics.topPillTempSp,
        fontWeight = FontWeight.Bold,
        fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
      )
      Text(
        text = conditionText,
        color = textSecondary,
        fontSize = metrics.topPillPrimarySp,
        fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
      )
    }
  }
}
