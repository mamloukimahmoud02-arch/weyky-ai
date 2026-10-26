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

/**
 * Redesigned Arabic RTL Landscape Desktop Experience for WEYKY Launcher.
 * Specifically adapted for Samsung Galaxy A24 and mobile widescreen ergonomics:
 * - Upper-Right: Compact interactive information pills (Location, Weather, Date) -> speaks info on click.
 * - Upper-Left: Compact interactive music player widget with transport controls and app launch on artwork tap.
 * - Center Stage: Completely protected, spacious living WEYKY Core.
 * - Bottom: Slim, professional desktop taskbar with Arabic search box, network status, clock, and battery.
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
            start = 14.dp,
            end = 14.dp,
            top = 46.dp,
            bottom = 54.dp // Reserves clean height for the 48dp floating taskbar
          ),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Center Stage: The protected, untouched WEYKY Living Core
        BoxWithConstraints(
          modifier = Modifier
            .weight(1f, fill = true)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          val maxAvailableDim = minOf(maxHeight, maxWidth)
          val calculatedCoreSize = minOf(dynamicCoreSize, maxAvailableDim * 0.95f)
            .coerceIn(minOf(80.dp, maxAvailableDim * 0.75f), 350.dp)

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
              .widthIn(max = 660.dp)
              .heightIn(max = 84.dp)
              .padding(bottom = 2.dp)
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
          .padding(start = 14.dp, end = 14.dp, top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        // Upper-Right (Start in RTL): Compact Information Pills (Location, Weather, Date)
        DesktopCompactInfoPills(
          uiState = uiState,
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
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          AnimatedVisibility(
            visible = isMusicActivelyPlaying,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            DesktopCompactMusicWidget(
              uiState = uiState,
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
              compactLandscapeMode = true
            )
          }
        }
      }

      // 2. BOTTOM DESKTOP TASKBAR (Floating slim bar across the bottom)
      DesktopTaskbar(
        uiState = uiState,
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
          .padding(horizontal = 10.dp, vertical = 4.dp)
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
            .padding(start = 12.dp, bottom = 54.dp)
        ) {
          DesktopStartMenu(
            uiState = uiState,
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
 * Compact Desktop Music Player Widget (Upper-Left).
 * - Clicking artwork/icon launches original music app.
 * - Track title and artist with thin volume progress bar.
 * - Transport controls (Previous, circular cyan Play/Pause, Next).
 * - Reacts to all 3 themes (Tactical HUD, Modern, Minimal).
 */
@Composable
private fun DesktopCompactMusicWidget(
  uiState: WeykyUiState,
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
    isTactical -> CutCornerShape(6.dp)
    isModern -> RoundedCornerShape(16.dp)
    else -> RoundedCornerShape(10.dp)
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
      .widthIn(min = 250.dp, max = 290.dp)
      .height(56.dp)
      .shadow(elevation = if (isModern) 10.dp else 6.dp, shape = cardShape)
      .clip(cardShape)
      .background(cardBg)
      .border(
        width = if (isTactical) 1.2.dp else 1.dp,
        color = cardBorder,
        shape = cardShape
      )
      .padding(horizontal = 8.dp, vertical = 6.dp)
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
          .size(38.dp)
          .clip(if (isTactical) CutCornerShape(4.dp) else RoundedCornerShape(8.dp))
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
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

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
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
        )
        Text(
          text = trackArtist,
          color = textSecondary,
          fontSize = 9.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
        )

        Spacer(modifier = Modifier.height(3.dp))

        // Subtle cyan volume / progress bar
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = "مستوى الصوت",
            tint = Color(0xFF00F0FF).copy(alpha = 0.8f),
            modifier = Modifier.size(10.dp)
          )
          Box(
            modifier = Modifier
              .width(44.dp)
              .height(2.5.dp)
              .clip(RoundedCornerShape(1.dp))
              .background(Color(0xFF00F0FF).copy(alpha = 0.7f))
          )
        }
      }

      Spacer(modifier = Modifier.width(6.dp))

      // 3. Transport Controls: [ Previous | (Play/Pause) | Next ]
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        IconButton(
          onClick = onPreviousMediaTrack,
          modifier = Modifier
            .size(28.dp)
            .testTag("music_prev_button")
        ) {
          Icon(
            imageVector = Icons.Default.SkipPrevious,
            contentDescription = "المقطع السابق",
            tint = Color(0xFF00F0FF),
            modifier = Modifier.size(18.dp)
          )
        }

        // Circular Cyan Play/Pause button
        Box(
          modifier = Modifier
            .size(32.dp)
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
            modifier = Modifier.size(16.dp)
          )
        }

        IconButton(
          onClick = onNextMediaTrack,
          modifier = Modifier
            .size(28.dp)
            .testTag("music_next_button")
        ) {
          Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = "المقطع التالي",
            tint = Color(0xFF00F0FF),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

/**
 * Compact Top Information Pills:
 * [ 📍 موقعي الحالي ]  [ ☀️ 14° غائم ]
 * Fully interactive: Clicking any pill speaks its details through the WEYKY Core!
 * Reacts to all 3 themes (Tactical HUD, Modern, Minimal).
 */
@Composable
private fun DesktopCompactInfoPills(
  uiState: WeykyUiState,
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
    isTactical -> CutCornerShape(4.dp)
    isModern -> RoundedCornerShape(16.dp)
    else -> RoundedCornerShape(8.dp)
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
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // 1. Location Pill: [ 📍 اسم المنطقة الجغرافية ] -> Clickable: speaks location
    Row(
      modifier = Modifier
        .height(36.dp)
        .widthIn(max = 190.dp)
        .clip(pillShape)
        .background(pillBg)
        .border(
          width = if (isTactical) 1.2.dp else 1.dp,
          color = pillBorder,
          shape = pillShape
        )
        .clickable(onClick = onLocationClick)
        .padding(horizontal = 10.dp)
        .testTag("desktop_location_pill"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Default.LocationOn,
        contentDescription = "الموقع الحالي",
        tint = Color(0xFF00F0FF),
        modifier = Modifier.size(14.dp)
      )
      Text(
        text = locationText,
        color = textPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
      )
    }

    // 2. Weather Pill: [ ☀️ 14° غائم ] -> Clickable: speaks weather
    Row(
      modifier = Modifier
        .height(36.dp)
        .clip(pillShape)
        .background(pillBg)
        .border(
          width = if (isTactical) 1.2.dp else 1.dp,
          color = pillBorder,
          shape = pillShape
        )
        .clickable(onClick = onWeatherClick)
        .padding(horizontal = 10.dp)
        .testTag("desktop_weather_pill"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = if (conditionText.contains("مطر")) Icons.Default.Cloud else Icons.Default.WbSunny,
        contentDescription = "الطقس",
        tint = Color(0xFF00F0FF),
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = tempText,
        color = textPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
      )
      Text(
        text = conditionText,
        color = textSecondary,
        fontSize = 11.sp,
        fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
      )
    }
  }
}
