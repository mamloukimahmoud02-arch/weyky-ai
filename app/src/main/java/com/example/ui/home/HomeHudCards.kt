package com.example.ui.home

import android.content.res.Configuration
import android.graphics.Bitmap
import com.example.ui.home.weather.RealtimeSkyViewport
import com.example.ui.home.weather.WeatherAdaptiveCoreLayer
import com.example.ui.home.weather.calculateLunarAstronomy
import com.example.ui.home.weather.isNightTime
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.core.state.ImportantEvent
import com.example.core.state.WeykyUiState
import com.example.device.battery.ChargingState
import com.example.rendering.renderer.WeykyCoreView
import com.example.ui.adaptive.CardDensity
import com.example.ui.theme.HudTabPosition
import com.example.ui.theme.JarvisHudBadge
import com.example.ui.theme.JarvisHudCard
import com.example.ui.theme.JarvisHudColors
import com.example.ui.theme.WeykyAdaptiveCard
import com.example.ui.theme.WeykyThemeMode
import kotlinx.coroutines.delay

@Composable
fun HudVerticalDivider(color: Color, heightDp: Dp = 44.dp) {
  Box(
    modifier = Modifier
      .width(1.dp)
      .height(heightDp)
      .background(color.copy(alpha = 0.18f))
  )
}

@Composable
fun HomeTopActionBar(
  borderColor: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  onToggleAppDrawer: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left / Start: Installed Apps Button (التطبيقات / APPS)
    JarvisHudBadge(
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutSize = 8.dp,
      onClick = onToggleAppDrawer,
      modifier = Modifier
        .height(48.dp)
        .testTag("app_drawer_button")
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Apps,
          contentDescription = "Applications",
          tint = borderColor,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "التطبيقات // APPS",
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          color = textPrimary
        )
      }
    }

    // Right / End: Settings Button (الإعدادات / SETTINGS)
    JarvisHudBadge(
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutSize = 8.dp,
      onClick = onOpenSettings,
      modifier = Modifier
        .height(48.dp)
        .testTag("settings_button")
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "Settings",
          tint = borderColor,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "الإعدادات // CFG",
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          color = textPrimary
        )
      }
    }
  }
}

/**
 * Top Row Cards: Weather Card & Date Card side-by-side in one horizontal row.
 * Strictly adheres to Section 4.1 of the Master Specification:
 * - Real Open-Meteo weather data (temperature, condition, humidity, wind).
 * - Real device date (Arabic & English formatted).
 * - No redundant clock/battery/network duplication.
 * - Perfectly responsive without text clipping.
 */
@Composable
fun HomeWeatherCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  density: CardDensity = CardDensity.STANDARD,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val isCompact = density == CardDensity.COMPACT
  val isExpandedTablet = density == CardDensity.EXPANDED
  val isMinimal = uiState.themeMode == WeykyThemeMode.MINIMAL
  val isModern = uiState.themeMode == WeykyThemeMode.MODERN
  val weather = uiState.weatherSnapshot
  val tempVal = weather?.temperatureCelsius
  val tempText = if (weather != null && weather.isAvailable && tempVal != null) {
    "${tempVal.toInt()}°"
  } else {
    "18°"
  }
  val condition = weather?.condition?.ifBlank { null } ?: "غائم جزئياً"
  val locationName = weather?.locationName
    ?.takeIf { it.isNotBlank() && it != "موقعي الحالي" }
    ?: "جاري تحديد المنطقة..."

  val cardHeight = when {
    isExpandedTablet -> 88.dp
    isCompact -> 66.dp
    else -> 72.dp
  }
  val iconBoxSize = when {
    isExpandedTablet -> 58.dp
    isCompact -> 42.dp
    else -> 46.dp
  }
  val viewportShape = when (uiState.themeMode) {
    WeykyThemeMode.TACTICAL -> CutCornerShape(if (isExpandedTablet) 10.dp else 8.dp)
    WeykyThemeMode.MODERN -> RoundedCornerShape(if (isExpandedTablet) 16.dp else 14.dp)
    WeykyThemeMode.MINIMAL -> RoundedCornerShape(if (isExpandedTablet) 14.dp else 12.dp)
  }

  WeykyAdaptiveCard(
    uiState = uiState,
    accentColor = borderColor,
    containerColor = panelBg,
    cutCornerSize = when {
      isExpandedTablet -> 16.dp
      isCompact -> 10.dp
      else -> 14.dp
    },
    tabTitle = null,
    tabPosition = HudTabPosition.NONE,
    onClick = { onQuerySubmit("ما حالة الطقس؟") },
    modifier = modifier
      .height(cardHeight)
      .testTag("home_weather_card")
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(
          horizontal = if (isExpandedTablet) 15.dp else 11.dp,
          vertical = if (isExpandedTablet) 12.dp else 9.dp
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (isExpandedTablet) 14.dp else 10.dp)
    ) {
      RealtimeSkyViewport(
        weather = weather,
        modifier = Modifier.size(iconBoxSize),
        shape = viewportShape,
        borderColor = if (isModern) borderColor.copy(alpha = 0.65f) else borderColor.copy(alpha = 0.5f)
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = tempText,
          fontSize = when {
            isExpandedTablet -> 20.sp
            isCompact -> 15.5.sp
            else -> 16.5.sp
          },
          fontWeight = FontWeight.Bold,
          color = textPrimary,
          letterSpacing = 0.4.sp,
          maxLines = 1
        )
        Text(
          text = condition,
          fontSize = when {
            isExpandedTablet -> 13.sp
            isCompact -> 10.sp
            else -> 11.sp
          },
          color = if (isModern) Color(0xFFA5C9F0) else textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(if (isExpandedTablet) 4.dp else 3.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Place,
            contentDescription = "Location",
            tint = if (isMinimal) Color(0xFF00D2FF) else borderColor,
            modifier = Modifier.size(if (isExpandedTablet) 14.dp else 11.dp)
          )
          Text(
            text = locationName,
            fontSize = if (isExpandedTablet) 11.5.sp else 9.5.sp,
            color = if (isModern) Color(0xFF8EB2D6) else textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
fun HomeDateCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  density: CardDensity = CardDensity.STANDARD,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val isCompact = density == CardDensity.COMPACT
  val isExpandedTablet = density == CardDensity.EXPANDED
  val isMinimal = uiState.themeMode == WeykyThemeMode.MINIMAL
  val isModern = uiState.themeMode == WeykyThemeMode.MODERN
  val primaryDate = uiState.currentDateString.ifBlank { "الخميس، 22 أكتوبر" }
  val englishDate = uiState.currentEnglishDateString.ifBlank { "THURSDAY, 22 OCT" }
  val timeSummary = uiState.currentTimeString.ifBlank { "12:00" }

  val cardHeight = when {
    isExpandedTablet -> 88.dp
    isCompact -> 66.dp
    else -> 72.dp
  }
  val iconBoxSize = when {
    isExpandedTablet -> 58.dp
    isCompact -> 42.dp
    else -> 46.dp
  }
  val iconShape = when (uiState.themeMode) {
    WeykyThemeMode.TACTICAL -> CutCornerShape(if (isExpandedTablet) 10.dp else 8.dp)
    WeykyThemeMode.MODERN -> RoundedCornerShape(if (isExpandedTablet) 16.dp else 14.dp)
    WeykyThemeMode.MINIMAL -> RoundedCornerShape(if (isExpandedTablet) 14.dp else 12.dp)
  }
  val iconBg = when (uiState.themeMode) {
    WeykyThemeMode.MODERN -> borderColor.copy(alpha = 0.18f)
    WeykyThemeMode.MINIMAL -> Color(0xFF152230)
    WeykyThemeMode.TACTICAL -> borderColor.copy(alpha = 0.14f)
  }
  val iconTint = when (uiState.themeMode) {
    WeykyThemeMode.MODERN -> borderColor
    WeykyThemeMode.MINIMAL -> Color(0xFF7A8B9E)
    WeykyThemeMode.TACTICAL -> borderColor
  }

  WeykyAdaptiveCard(
    uiState = uiState,
    accentColor = borderColor,
    containerColor = panelBg,
    cutCornerSize = when {
      isExpandedTablet -> 16.dp
      isCompact -> 10.dp
      else -> 14.dp
    },
    tabTitle = null,
    tabPosition = HudTabPosition.NONE,
    onClick = { onQuerySubmit("ما تاريخ اليوم؟") },
    modifier = modifier
      .height(cardHeight)
      .testTag("home_date_card")
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(
          horizontal = if (isExpandedTablet) 15.dp else 11.dp,
          vertical = if (isExpandedTablet) 12.dp else 9.dp
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (isExpandedTablet) 14.dp else 10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(iconBoxSize)
          .clip(iconShape)
          .background(iconBg)
          .border(
            width = 1.dp,
            color = if (isModern) Color(0xFF00D2FF).copy(alpha = 0.45f) else borderColor.copy(alpha = 0.35f),
            shape = iconShape
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.DateRange,
          contentDescription = "Date",
          tint = iconTint,
          modifier = Modifier.size(if (isExpandedTablet) 28.dp else 22.dp)
        )
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = primaryDate,
          fontSize = when {
            isExpandedTablet -> 16.sp
            isCompact -> 12.5.sp
            else -> 13.5.sp
          },
          fontWeight = FontWeight.Bold,
          color = textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = englishDate.uppercase(),
          fontSize = when {
            isExpandedTablet -> 11.sp
            isCompact -> 9.sp
            else -> 9.5.sp
          },
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.6.sp,
          color = if (isModern) Color(0xFFA5C9F0) else textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(if (isExpandedTablet) 5.dp else 4.dp)
        ) {
          Box(
            modifier = Modifier
              .size(if (isExpandedTablet) 7.5.dp else 6.dp)
              .clip(CircleShape)
              .background(if (isMinimal || isModern) Color(0xFF00D2FF) else borderColor)
          )
          Text(
            text = timeSummary,
            fontSize = if (isExpandedTablet) 11.5.sp else 9.5.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isModern) Color(0xFF8EB2D6) else textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
fun HomeTopRowCards(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  density: CardDensity = CardDensity.STANDARD,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val isExpandedTablet = density == CardDensity.EXPANDED
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .testTag("home_top_row_cards"),
      horizontalArrangement = Arrangement.spacedBy(if (isExpandedTablet) 14.dp else 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left: Date Card (Equal width 1f and equal height)
      HomeDateCard(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        density = density,
        onQuerySubmit = onQuerySubmit,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      )

      // Right: Weather Card (Equal width 1f and equal height)
      HomeWeatherCard(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        density = density,
        onQuerySubmit = onQuerySubmit,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      )
    }
  }
}

/**
 * Bottom Control Row: Settings button, flexible text input area, and installed apps button
 * in a single, perfectly unified horizontal row per Section 4.3 of the Master Specification.
 * Automatically scales up on Tablets in Portrait mode.
 */
@Composable
fun HomeBottomControlRow(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  isLight: Boolean,
  onQueryChange: (String) -> Unit,
  onQuerySubmit: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onOpenSettings: () -> Unit,
  onToggleAppDrawer: () -> Unit,
  keyboardController: SoftwareKeyboardController?,
  modifier: Modifier = Modifier
) {
  val config = LocalConfiguration.current
  val isTabletDevice = config.smallestScreenWidthDp >= 600
  val barHeight = if (isTabletDevice) 60.dp else 50.dp
  val flankIconSize = if (isTabletDevice) 26.dp else 22.dp
  val inputFontSize = if (isTabletDevice) 16.sp else 14.sp
  val hintFontSize = if (isTabletDevice) 15.sp else 13.5.sp
  val micButtonSize = if (isTabletDevice) 44.dp else 36.dp
  val micIconSize = if (isTabletDevice) 24.dp else 20.dp

  val isModern = uiState.themeMode == WeykyThemeMode.MODERN
  val isMinimal = uiState.themeMode == WeykyThemeMode.MINIMAL
  val isTactical = uiState.themeMode == WeykyThemeMode.TACTICAL

  val flankShape = when {
    isTactical -> CutCornerShape(if (isTabletDevice) 10.dp else 8.dp)
    isModern -> RoundedCornerShape(if (isTabletDevice) 18.dp else 16.dp)
    else -> RoundedCornerShape(if (isTabletDevice) 14.dp else 12.dp)
  }
  val pillShape = when {
    isTactical -> CutCornerShape(if (isTabletDevice) 12.dp else 10.dp)
    isModern -> RoundedCornerShape(if (isTabletDevice) 30.dp else 26.dp)
    else -> RoundedCornerShape(if (isTabletDevice) 16.dp else 14.dp)
  }

  val modernGlassGradient = Brush.verticalGradient(
    listOf(Color(0x550F2C4C), Color(0x330B1B30))
  )
  val modernBorderBrush = Brush.linearGradient(
    listOf(borderColor, borderColor.copy(alpha = 0.65f))
  )
  val minimalSlateBg = Color(0xFF101924)
  val minimalBorderColor = Color(0xFF1E2D3D)

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .testTag("home_bottom_bar"),
      horizontalArrangement = Arrangement.spacedBy(if (isTabletDevice) 14.dp else 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // 1. LEFT BUTTON: Installed Apps (3x3 grid)
      val appsModifier = when {
        isModern -> Modifier
          .size(barHeight)
          .shadow(6.dp, flankShape, spotColor = borderColor.copy(alpha = 0.4f), ambientColor = borderColor.copy(alpha = 0.2f))
          .clip(flankShape)
          .background(modernGlassGradient)
          .border(1.5.dp, modernBorderBrush, flankShape)
          .clickable(onClick = onToggleAppDrawer)
          .testTag("app_drawer_button")
        isMinimal -> Modifier
          .size(barHeight)
          .clip(flankShape)
          .background(minimalSlateBg)
          .border(1.dp, minimalBorderColor, flankShape)
          .clickable(onClick = onToggleAppDrawer)
          .testTag("app_drawer_button")
        else -> Modifier
          .size(barHeight)
          .clip(flankShape)
          .background(panelBgElevated)
          .border(1.5.dp, borderColor, flankShape)
          .clickable(onClick = onToggleAppDrawer)
          .testTag("app_drawer_button")
      }
      val appsIconTint = when {
        isModern -> borderColor
        isMinimal -> Color(0xFF6E8094)
        else -> borderColor
      }

      Box(
        modifier = appsModifier,
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Apps,
          contentDescription = "Applications",
          tint = appsIconTint,
          modifier = Modifier.size(flankIconSize)
        )
      }

      // 2. CENTER PILL: Input bar (... Ask WEYKY anything)
      val pillModifier = when {
        isModern -> Modifier
          .weight(1f)
          .height(barHeight)
          .shadow(6.dp, pillShape, spotColor = borderColor.copy(alpha = 0.35f), ambientColor = borderColor.copy(alpha = 0.15f))
          .clip(pillShape)
          .background(modernGlassGradient)
          .border(1.5.dp, modernBorderBrush, pillShape)
          .testTag("home_bottom_input_area")
        isMinimal -> Modifier
          .weight(1f)
          .height(barHeight)
          .clip(pillShape)
          .background(minimalSlateBg)
          .border(1.dp, minimalBorderColor, pillShape)
          .testTag("home_bottom_input_area")
        else -> Modifier
          .weight(1f)
          .height(barHeight)
          .clip(pillShape)
          .background(panelBg)
          .border(1.2.dp, borderColor, pillShape)
          .testTag("home_bottom_input_area")
      }

      Box(
        modifier = pillModifier,
        contentAlignment = Alignment.CenterStart
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isTabletDevice) 18.dp else 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Left Arrow Icon
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Send Query",
            tint = if (uiState.inputQuery.isNotBlank()) Color(0xFF00D2FF) else Color(0xFF6B7E94),
            modifier = Modifier
              .size(if (isTabletDevice) 23.dp else 19.dp)
              .clickable {
                if (uiState.inputQuery.isNotBlank()) {
                  onQuerySubmit(uiState.inputQuery)
                  keyboardController?.hide()
                } else {
                  onQuerySubmit("ماذا أفعل الآن؟")
                }
              }
          )

          Spacer(modifier = Modifier.width(if (isTabletDevice) 12.dp else 10.dp))

          // Center Input field
          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
          ) {
            if (uiState.inputQuery.isEmpty()) {
              val hint = if (uiState.isVoiceListening) "Listening... تكلم الآن" else "... Ask WEYKY anything"
              Text(
                text = hint,
                fontSize = hintFontSize,
                color = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else Color(0xFF7A8B9E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            BasicTextField(
              value = uiState.inputQuery,
              onValueChange = onQueryChange,
              singleLine = true,
              textStyle = TextStyle(
                color = textPrimary,
                fontSize = inputFontSize,
                fontWeight = FontWeight.Normal
              ),
              cursorBrush = SolidColor(Color(0xFF00D2FF)),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
              keyboardActions = KeyboardActions(onSend = {
                if (uiState.inputQuery.isNotBlank()) {
                  onQuerySubmit(uiState.inputQuery)
                  keyboardController?.hide()
                }
              }),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("ask_weyky_input")
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Right Mic button
          IconButton(
            onClick = onToggleVoice,
            modifier = Modifier
              .size(micButtonSize)
              .testTag("voice_input_button")
          ) {
            Icon(
              imageVector = Icons.Default.Mic,
              contentDescription = "Voice Input",
              tint = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else if (isModern) borderColor else Color(0xFF00D2FF),
              modifier = Modifier.size(micIconSize)
            )
          }
        }
      }

      // 3. RIGHT BUTTON: Settings (Gear icon)
      val settingsModifier = when {
        isModern -> Modifier
          .size(barHeight)
          .shadow(6.dp, flankShape, spotColor = borderColor.copy(alpha = 0.4f), ambientColor = borderColor.copy(alpha = 0.2f))
          .clip(flankShape)
          .background(modernGlassGradient)
          .border(1.5.dp, modernBorderBrush, flankShape)
          .clickable(onClick = onOpenSettings)
          .testTag("settings_button")
        isMinimal -> Modifier
          .size(barHeight)
          .clip(flankShape)
          .background(minimalSlateBg)
          .border(1.dp, minimalBorderColor, flankShape)
          .clickable(onClick = onOpenSettings)
          .testTag("settings_button")
        else -> Modifier
          .size(barHeight)
          .clip(flankShape)
          .background(panelBgElevated)
          .border(1.5.dp, borderColor, flankShape)
          .clickable(onClick = onOpenSettings)
          .testTag("settings_button")
      }
      val settingsIconTint = when {
        isModern -> borderColor
        isMinimal -> Color(0xFF00D2FF)
        else -> borderColor
      }

      Box(
        modifier = settingsModifier,
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "Settings",
          tint = settingsIconTint,
          modifier = Modifier.size(flankIconSize)
        )
      }
    }
  }
}

/**
 * WEYKY Input Card (Dedicated typing and voice bar for landscape middle column):
 * Houses voice mic button, expandable query input, and send button.
 */
@Composable
fun HomeInputCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  onQueryChange: (String) -> Unit,
  onQuerySubmit: (String) -> Unit,
  onToggleVoice: () -> Unit,
  keyboardController: SoftwareKeyboardController?,
  modifier: Modifier = Modifier
) {
  val activeInputAccent = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor
  WeykyAdaptiveCard(
    uiState = uiState,
    accentColor = activeInputAccent,
    containerColor = panelBg,
    cutCornerSize = 12.dp,
    tabPosition = HudTabPosition.NONE,
    modifier = modifier
      .fillMaxWidth()
      .height(48.dp)
      .testTag("home_bottom_bar")
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 6.dp)
        .testTag("home_bottom_input_area"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Voice Mic Button
      IconButton(
        onClick = onToggleVoice,
        modifier = Modifier
          .size(38.dp)
          .testTag("voice_input_button")
      ) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = if (uiState.isVoiceListening) "Listening... tap to cancel" else "Voice Input",
          tint = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor,
          modifier = Modifier.size(20.dp)
        )
      }

      // Text Input Field
      Box(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        if (uiState.inputQuery.isEmpty()) {
          val hint = if (uiState.isVoiceListening) "Listening... تكلم الآن" else "Ask WEYKY anything..."
          Text(
            text = hint,
            fontSize = 13.sp,
            color = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        BasicTextField(
          value = uiState.inputQuery,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = TextStyle(
            color = textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
          ),
          cursorBrush = SolidColor(borderColor),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = {
            if (uiState.inputQuery.isNotBlank()) {
              onQuerySubmit(uiState.inputQuery)
              keyboardController?.hide()
            }
          }),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("ask_weyky_input")
        )
      }

      // Submit Button
      IconButton(
        onClick = {
          if (uiState.inputQuery.isNotBlank()) {
            onQuerySubmit(uiState.inputQuery)
            keyboardController?.hide()
          } else {
            onQuerySubmit("ماذا أفعل الآن؟")
          }
        },
        modifier = Modifier
          .size(36.dp)
          .testTag("submit_query_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "Send",
          tint = borderColor,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

/**
 * Landscape Side Settings Button:
 * Dedicated tactical action button placed in the side column.
 */
@Composable
fun HomeLandscapeSettingsButton(
  borderColor: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  isRtl: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  themeMode: WeykyThemeMode = WeykyThemeMode.TACTICAL
) {
  val isModern = themeMode == WeykyThemeMode.MODERN
  val isMinimal = themeMode == WeykyThemeMode.MINIMAL

  if (themeMode == WeykyThemeMode.TACTICAL) {
    JarvisHudBadge(
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutSize = 8.dp,
      onClick = onClick,
      modifier = modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("settings_button")
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "Settings",
          tint = borderColor,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (isRtl) "الإعدادات" else "Settings",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = textPrimary,
          maxLines = 1
        )
      }
    }
  } else {
    val shape = if (isModern) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp)
    val modernGradient = Brush.verticalGradient(listOf(Color(0x550F2C4C), Color(0x330B1B30)))
    val modernBorder = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0088FF)))
    val btnMod = if (isModern) {
      modifier
        .fillMaxWidth()
        .height(48.dp)
        .shadow(6.dp, shape, spotColor = Color(0x6600D2FF), ambientColor = Color(0x3300D2FF))
        .clip(shape)
        .background(modernGradient)
        .border(1.5.dp, modernBorder, shape)
        .clickable(onClick = onClick)
        .testTag("settings_button")
    } else {
      modifier
        .fillMaxWidth()
        .height(48.dp)
        .clip(shape)
        .background(Color(0xFF101924))
        .border(1.dp, Color(0xFF1E2D3D), shape)
        .clickable(onClick = onClick)
        .testTag("settings_button")
    }
    Box(
      modifier = btnMod,
      contentAlignment = Alignment.Center
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "Settings",
          tint = Color(0xFF00D2FF),
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (isRtl) "الإعدادات" else "Settings",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = textPrimary,
          maxLines = 1
        )
      }
    }
  }
}

/**
 * Landscape Side App Drawer Button:
 * Dedicated tactical action button placed in the side column.
 */
@Composable
fun HomeLandscapeAppDrawerButton(
  borderColor: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  isRtl: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  themeMode: WeykyThemeMode = WeykyThemeMode.TACTICAL
) {
  val isModern = themeMode == WeykyThemeMode.MODERN
  val isMinimal = themeMode == WeykyThemeMode.MINIMAL

  if (themeMode == WeykyThemeMode.TACTICAL) {
    JarvisHudBadge(
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutSize = 8.dp,
      onClick = onClick,
      modifier = modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("app_drawer_button")
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.Apps,
          contentDescription = "Applications",
          tint = borderColor,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (isRtl) "التطبيقات" else "Apps",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = textPrimary,
          maxLines = 1
        )
      }
    }
  } else {
    val shape = if (isModern) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp)
    val modernGradient = Brush.verticalGradient(listOf(Color(0x550F2C4C), Color(0x330B1B30)))
    val modernBorder = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0088FF)))
    val btnMod = if (isModern) {
      modifier
        .fillMaxWidth()
        .height(48.dp)
        .shadow(6.dp, shape, spotColor = Color(0x6600D2FF), ambientColor = Color(0x3300D2FF))
        .clip(shape)
        .background(modernGradient)
        .border(1.5.dp, modernBorder, shape)
        .clickable(onClick = onClick)
        .testTag("app_drawer_button")
    } else {
      modifier
        .fillMaxWidth()
        .height(48.dp)
        .clip(shape)
        .background(Color(0xFF101924))
        .border(1.dp, Color(0xFF1E2D3D), shape)
        .clickable(onClick = onClick)
        .testTag("app_drawer_button")
    }
    val iconTint = if (isModern) Color(0xFF00D2FF) else Color(0xFF6E8094)
    Box(
      modifier = btnMod,
      contentAlignment = Alignment.Center
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.Apps,
          contentDescription = "Applications",
          tint = iconTint,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (isRtl) "التطبيقات" else "Apps",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = textPrimary,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
fun HomeTelemetryCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  density: CardDensity = CardDensity.STANDARD,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val isCompact = density == CardDensity.COMPACT
  val timeFontSize = if (isCompact) 22.sp else 28.sp
  val vertPadding = if (isCompact) 10.dp else 14.dp
  val horizPadding = if (isCompact) 10.dp else 14.dp

  WeykyAdaptiveCard(
    uiState = uiState,
    accentColor = borderColor,
    containerColor = panelBg,
    cutCornerSize = if (isCompact) 12.dp else 16.dp,
    tabTitle = null,
    tabPosition = HudTabPosition.NONE,
    modifier = modifier
      .fillMaxWidth()
      .testTag("hud_telemetry_bar")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = horizPadding, vertical = vertPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // COLUMN 1: Time & Date (Left)
      val cleanTime = uiState.currentTimeString
        .replace(" AM", "")
        .replace(" PM", "")
        .replace(" ص", "")
        .replace(" م", "")
        .ifEmpty { "11:02" }
      Column(
        modifier = Modifier.weight(1.15f),
        horizontalAlignment = Alignment.Start
      ) {
        Text(
          text = cleanTime,
          fontSize = timeFontSize,
          fontWeight = FontWeight.Bold,
          color = textPrimary,
          letterSpacing = 0.5.sp,
          modifier = Modifier.testTag("current_time_text")
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = uiState.currentDateString.uppercase().ifEmpty { "22 AUG 2026" },
          fontSize = if (isCompact) 9.sp else 10.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Medium,
          letterSpacing = 0.8.sp,
          color = textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      HudVerticalDivider(color = borderColor, heightDp = if (isCompact) 36.dp else 44.dp)

      // COLUMN 2: Weather (Cloud Icon + Temp + WEATHER label)
      val weather = uiState.weatherSnapshot
      val tempVal = weather?.temperatureCelsius
      val tempText = if (weather != null && weather.isAvailable && tempVal != null) {
        "${tempVal.toInt()}°C"
      } else {
        "--°C"
      }
      Row(
        modifier = Modifier
          .weight(1.1f)
          .clickable { onQuerySubmit("ما حالة الطقس؟") }
          .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        RealtimeSkyViewport(
          weather = weather,
          modifier = Modifier.size(if (isCompact) 28.dp else 34.dp),
          shape = CutCornerShape(4.dp),
          borderColor = borderColor.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.Start) {
          Text(
            text = tempText,
            fontSize = if (isCompact) 14.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
          )
          Text(
            text = "WEATHER",
            fontSize = if (isCompact) 8.sp else 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
            color = textSecondary
          )
        }
      }

      HudVerticalDivider(color = borderColor, heightDp = if (isCompact) 36.dp else 44.dp)

      // COLUMN 3: Battery (Icon + Percentage + CHARGING / BATTERY)
      val b = uiState.batterySnapshot
      val battPct = if (b != null && b.isAvailable) "${b.percentage}%" else "--%"
      val isCharging = b?.chargingState == ChargingState.CHARGING || b?.chargingState == ChargingState.FULL
      Column(
        modifier = Modifier
          .weight(0.95f)
          .clickable { onQuerySubmit("حالة البطارية") },
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
          contentDescription = "Battery",
          tint = if (b != null && b.percentage <= 20) JarvisHudColors.CrimsonAlert else borderColor,
          modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = battPct,
          fontSize = if (isCompact) 12.sp else 14.sp,
          fontWeight = FontWeight.Bold,
          color = textPrimary
        )
        Text(
          text = if (isCharging) "CHARGING" else "DISCHARGING",
          fontSize = 7.sp,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp,
          color = textSecondary,
          maxLines = 1
        )
        Text(
          text = "BATTERY",
          fontSize = 7.sp,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp,
          color = textMuted,
          maxLines = 1
        )
      }

      HudVerticalDivider(color = borderColor, heightDp = if (isCompact) 36.dp else 44.dp)

      // COLUMN 4: Network (Wifi Icon + WIFI + CONNECTED / NETWORK)
      val n = uiState.networkSnapshot
      val netConnected = n?.isConnected == true
      val netTitle = when {
        n == null || !n.isConnected -> "OFFLINE"
        n.typeLabel.contains("Wi-Fi", ignoreCase = true) || n.typeLabel.contains("واي", ignoreCase = true) -> "WIFI"
        else -> n.typeLabel.uppercase()
      }
      Column(
        modifier = Modifier
          .weight(0.95f)
          .clickable { onQuerySubmit("حالة الشبكة") },
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.Wifi,
          contentDescription = "Network",
          tint = if (netConnected) borderColor else textMuted,
          modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = netTitle,
          fontSize = if (isCompact) 11.sp else 13.sp,
          fontWeight = FontWeight.Bold,
          color = textPrimary,
          maxLines = 1
        )
        Text(
          text = if (netConnected) "CONNECTED" else "DISCONNECTED",
          fontSize = 7.sp,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp,
          color = textSecondary,
          maxLines = 1
        )
        Text(
          text = "NETWORK",
          fontSize = 7.sp,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp,
          color = textMuted,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
private fun MediaArtworkImage(
  artworkBitmap: Bitmap?,
  isMusicActivelyPlaying: Boolean,
  borderColor: Color,
  sizeDp: androidx.compose.ui.unit.Dp = 46.dp,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .size(sizeDp)
      .shadow(
        elevation = if (isMusicActivelyPlaying) 8.dp else 2.dp,
        shape = RoundedCornerShape(10.dp),
        ambientColor = JarvisHudColors.MagentaPulse,
        spotColor = JarvisHudColors.MagentaPulse
      )
      .clip(RoundedCornerShape(10.dp))
      .background(JarvisHudColors.MagentaPulse.copy(alpha = 0.16f))
      .border(
        width = 1.2.dp,
        color = if (isMusicActivelyPlaying) JarvisHudColors.MagentaPulse.copy(alpha = 0.85f) else borderColor.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp)
      )
      .testTag("music_card_image"),
    contentAlignment = Alignment.Center
  ) {
    if (artworkBitmap != null) {
      Image(
        bitmap = artworkBitmap.asImageBitmap(),
        contentDescription = "Track Artwork",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
      if (isMusicActivelyPlaying) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(3.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(JarvisHudColors.NeonGreen)
            .border(1.dp, Color.Black.copy(alpha = 0.7f), CircleShape)
        )
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.radialGradient(
              listOf(
                JarvisHudColors.MagentaPulse.copy(alpha = 0.35f),
                Color(0xFF0F0B1E)
              )
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.GraphicEq,
          contentDescription = "Track Artwork",
          tint = JarvisHudColors.MagentaPulse,
          modifier = Modifier.size((sizeDp.value * 0.52f).dp)
        )
      }
    }
  }
}

@Composable
private fun MediaControlsRow(
  isMusicActivelyPlaying: Boolean,
  borderColor: Color,
  textSecondary: Color,
  onPrevious: () -> Unit,
  onToggle: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false
) {
  val buttonSize = if (compact) 44.dp else 48.dp
  val innerPlaySize = if (compact) 32.dp else 34.dp
  val iconSize = if (compact) 20.dp else 22.dp

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
  ) {
    IconButton(
      onClick = onPrevious,
      modifier = Modifier
        .size(buttonSize)
        .testTag("music_prev_button")
    ) {
      Icon(
        imageVector = Icons.Default.SkipPrevious,
        contentDescription = "Previous Track",
        tint = textSecondary,
        modifier = Modifier.size(iconSize)
      )
    }

    IconButton(
      onClick = onToggle,
      modifier = Modifier
        .size(buttonSize)
        .testTag("music_play_pause_button")
    ) {
      Box(
        modifier = Modifier
          .size(innerPlaySize)
          .clip(CircleShape)
          .background(borderColor.copy(alpha = 0.20f))
          .border(1.2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isMusicActivelyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = if (isMusicActivelyPlaying) "Pause Music" else "Play Music",
          tint = borderColor,
          modifier = Modifier.size(if (compact) 16.dp else 18.dp)
        )
      }
    }

    IconButton(
      onClick = onNext,
      modifier = Modifier
        .size(buttonSize)
        .testTag("music_next_button")
    ) {
      Icon(
        imageVector = Icons.Default.SkipNext,
        contentDescription = "Next Track",
        tint = textSecondary,
        modifier = Modifier.size(iconSize)
      )
    }
  }
}

/**
 * Custom Landscape Mini Player Layout.
 * Designed specifically for landscape mode so that all three core elements
 * (الصورة Artwork Image, العنوان Title & Artist, and الأزرار Control Buttons)
 * are prominently visible, properly spaced, and never clipped.
 */
@Composable
private fun LandscapeMusicPlayerContent(
  trackTitle: String,
  trackArtist: String,
  artworkBitmap: Bitmap?,
  isMusicActivelyPlaying: Boolean,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit
) {
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 9.dp)
  ) {
    val isWide = maxWidth >= 320.dp
    if (isWide) {
      // Panoramic Landscape: Single continuous row with generous horizontal flow
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          MediaArtworkImage(
            artworkBitmap = artworkBitmap,
            isMusicActivelyPlaying = isMusicActivelyPlaying,
            borderColor = borderColor,
            sizeDp = 48.dp
          )

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = trackTitle,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("music_card_title")
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (isMusicActivelyPlaying) JarvisHudColors.NeonGreen else textSecondary.copy(alpha = 0.5f))
              )
              Text(
                text = trackArtist.uppercase(),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("music_card_artist")
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        MediaControlsRow(
          isMusicActivelyPlaying = isMusicActivelyPlaying,
          borderColor = borderColor,
          textSecondary = textSecondary,
          onPrevious = onPreviousMediaTrack,
          onToggle = onToggleMediaPlayback,
          onNext = onNextMediaTrack,
          compact = false
        )
      }
    } else {
      // Compact Landscape (Side Column or Narrow Window):
      // Custom structured two-tier layout ensuring complete visibility:
      // Tier 1: Artwork Image + Full-width Title and Artist (never squished)
      // Tier 2: Dedicated row of Previous, Play/Pause, Next controls (easy touch)
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
      ) {
        // 1. الصورة والعنوان (Artwork Image + Title and Artist)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MediaArtworkImage(
            artworkBitmap = artworkBitmap,
            isMusicActivelyPlaying = isMusicActivelyPlaying,
            borderColor = borderColor,
            sizeDp = 44.dp
          )

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = trackTitle,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("music_card_title")
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (isMusicActivelyPlaying) JarvisHudColors.NeonGreen else textSecondary.copy(alpha = 0.5f))
              )
              Text(
                text = trackArtist.uppercase(),
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("music_card_artist")
              )
            }
          }
        }

        // Sleek subtle tactical divider
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(borderColor.copy(alpha = 0.18f))
        )

        // 2. الأزرار (Dedicated Media Transport Controls)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          IconButton(
            onClick = onPreviousMediaTrack,
            modifier = Modifier
              .size(44.dp)
              .testTag("music_prev_button")
          ) {
            Icon(
              imageVector = Icons.Default.SkipPrevious,
              contentDescription = "Previous Track",
              tint = textSecondary,
              modifier = Modifier.size(20.dp)
            )
          }

          IconButton(
            onClick = onToggleMediaPlayback,
            modifier = Modifier
              .size(44.dp)
              .testTag("music_play_pause_button")
          ) {
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(borderColor.copy(alpha = 0.22f))
                .border(1.2.dp, borderColor, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isMusicActivelyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isMusicActivelyPlaying) "Pause Music" else "Play Music",
                tint = borderColor,
                modifier = Modifier.size(17.dp)
              )
            }
          }

          IconButton(
            onClick = onNextMediaTrack,
            modifier = Modifier
              .size(44.dp)
              .testTag("music_next_button")
          ) {
            Icon(
              imageVector = Icons.Default.SkipNext,
              contentDescription = "Next Track",
              tint = textSecondary,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * Portrait Mini Player Content.
 */
@Composable
private fun PortraitMusicPlayerContent(
  trackTitle: String,
  trackArtist: String,
  artworkBitmap: Bitmap?,
  isMusicActivelyPlaying: Boolean,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.weight(1f)
    ) {
      MediaArtworkImage(
        artworkBitmap = artworkBitmap,
        isMusicActivelyPlaying = isMusicActivelyPlaying,
        borderColor = borderColor,
        sizeDp = 44.dp
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = trackTitle,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("music_card_title")
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(if (isMusicActivelyPlaying) JarvisHudColors.NeonGreen else textSecondary.copy(alpha = 0.5f))
          )
          Text(
            text = trackArtist.uppercase(),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.9.sp,
            color = textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("music_card_artist")
          )
        }
      }
    }

    MediaControlsRow(
      isMusicActivelyPlaying = isMusicActivelyPlaying,
      borderColor = borderColor,
      textSecondary = textSecondary,
      onPrevious = onPreviousMediaTrack,
      onToggle = onToggleMediaPlayback,
      onNext = onNextMediaTrack,
      compact = false
    )
  }
}

@Composable
fun MiniEqualizerBars(
  isAnimated: Boolean,
  color: Color = Color(0xFF00D2FF),
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
  val b1 by infiniteTransition.animateFloat(
    initialValue = 4f, targetValue = 13f,
    animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
    label = "b1"
  )
  val b2 by infiniteTransition.animateFloat(
    initialValue = 12f, targetValue = 4f,
    animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
    label = "b2"
  )
  val b3 by infiniteTransition.animateFloat(
    initialValue = 5f, targetValue = 15f,
    animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
    label = "b3"
  )
  val b4 by infiniteTransition.animateFloat(
    initialValue = 10f, targetValue = 4f,
    animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
    label = "b4"
  )

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.width(2.5.dp).height((if (isAnimated) b1 else 6f).dp).clip(CircleShape).background(color))
    Box(Modifier.width(2.5.dp).height((if (isAnimated) b2 else 10f).dp).clip(CircleShape).background(color))
    Box(Modifier.width(2.5.dp).height((if (isAnimated) b3 else 14f).dp).clip(CircleShape).background(color))
    Box(Modifier.width(2.5.dp).height((if (isAnimated) b4 else 8f).dp).clip(CircleShape).background(color))
  }
}

@Composable
fun ModernMusicBadge(
  isModern: Boolean,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(if (isModern) Color(0x3300D2FF) else Color(0xFF152230))
      .border(0.8.dp, if (isModern) Color(0x6600D2FF) else Color(0xFF26384A), RoundedCornerShape(10.dp))
      .padding(horizontal = 7.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(
      text = "MUSIC",
      fontSize = 9.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      letterSpacing = 1.sp,
      color = Color(0xFF00D2FF)
    )
    if (isModern) {
      Box(
        modifier = Modifier
          .size(5.dp)
          .clip(CircleShape)
          .background(Color(0xFF00D2FF))
      )
    } else {
      Box(
        modifier = Modifier
          .size(5.dp)
          .border(1.dp, Color(0xFF00D2FF), CircleShape)
      )
    }
  }
}

@Composable
fun HomeMusicCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  modifier: Modifier = Modifier,
  isLandscape: Boolean = false
) {
  val mediaMeta = uiState.activeMediaMetadata
  val isMusicActivelyPlaying = uiState.isMusicPlaying || (mediaMeta?.isPlaying == true)
  val isModern = uiState.themeMode == WeykyThemeMode.MODERN

  val trackTitle = mediaMeta?.title?.takeIf { it.isNotBlank() } ?: "تشغيل الموسيقى"
  val trackArtist = mediaMeta?.artist?.takeIf { it.isNotBlank() } ?: "مشغل الصوت"
  val artworkBitmap = mediaMeta?.artworkBitmap

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 4.dp)
  ) {
    WeykyAdaptiveCard(
      uiState = uiState,
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutCornerSize = 14.dp,
      tabPosition = HudTabPosition.NONE,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("music_top_hud_card")
    ) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = Modifier.fillMaxWidth()) {
          // Top-Right MUSIC pill badge
          ModernMusicBadge(
            isModern = isModern,
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(top = 8.dp, end = 12.dp)
          )

          // Main Horizontal Music Player Content
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            // 1. LEFT: Transport Controls (Previous, Large Play, Next)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              IconButton(
                onClick = onPreviousMediaTrack,
                modifier = Modifier
                  .size(40.dp)
                  .testTag("music_prev_button")
              ) {
                Icon(
                  imageVector = Icons.Default.SkipPrevious,
                  contentDescription = "Previous Track",
                  tint = if (isModern) Color(0xFFD1E4FA) else Color(0xFF7A8B9E),
                  modifier = Modifier.size(20.dp)
                )
              }

              IconButton(
                onClick = onToggleMediaPlayback,
                modifier = Modifier
                  .size(46.dp)
                  .testTag("music_play_pause_button")
              ) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isModern) Color(0x3300D2FF) else Color(0x2200D2FF))
                    .border(
                      width = if (isModern) 2.dp else 1.5.dp,
                      color = Color(0xFF00D2FF),
                      shape = CircleShape
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = if (isMusicActivelyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isMusicActivelyPlaying) "Pause Music" else "Play Music",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }

              IconButton(
                onClick = onNextMediaTrack,
                modifier = Modifier
                  .size(40.dp)
                  .testTag("music_next_button")
              ) {
                Icon(
                  imageVector = Icons.Default.SkipNext,
                  contentDescription = "Next Track",
                  tint = if (isModern) Color(0xFFD1E4FA) else Color(0xFF7A8B9E),
                  modifier = Modifier.size(20.dp)
                )
              }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 2. MIDDLE: Song Title, Artist & Equalizer bars
            Column(
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
              horizontalAlignment = Alignment.End,
              verticalArrangement = Arrangement.Center
            ) {
              Text(
                text = trackTitle,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("music_card_title")
              )
              Spacer(modifier = Modifier.height(2.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = trackArtist,
                  fontSize = 11.sp,
                  color = if (isModern) Color(0xFF8EB2D6) else Color(0xFF7A8B9E),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.testTag("music_card_artist")
                )
                MiniEqualizerBars(
                  isAnimated = isMusicActivelyPlaying,
                  color = Color(0xFF00D2FF)
                )
              }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. RIGHT: Album Art Thumbnail
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                  Brush.linearGradient(
                    colors = listOf(
                      Color(0xFFFF2A85),
                      Color(0xFF8B2CF5),
                      Color(0xFF0077FF)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(100f, 100f)
                  )
                )
                .clickable(onClick = onToggleMediaPlayback)
                .testTag("music_card_image"),
              contentAlignment = Alignment.Center
            ) {
              if (artworkBitmap != null) {
                Image(
                  bitmap = artworkBitmap.asImageBitmap(),
                  contentDescription = "Track Artwork",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                Icon(
                  imageVector = Icons.Default.PlayArrow,
                  contentDescription = "Play Track",
                  tint = Color.White,
                  modifier = Modifier.size(22.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun HomeCoreSection(
  uiState: WeykyUiState,
  coreSizeDp: Dp,
  onToggleVoice: () -> Unit,
  modifier: Modifier = Modifier
) {
  val batterySnap = uiState.batterySnapshot
  val isDeviceCharging = batterySnap?.chargingState == ChargingState.CHARGING ||
    batterySnap?.chargingState == ChargingState.FULL
  val currentBatteryPct = if (batterySnap != null && batterySnap.isAvailable) {
    batterySnap.percentage.coerceIn(0, 100)
  } else {
    100
  }
  val weather = uiState.weatherSnapshot
  val isNight = isNightTime(weather)
  val lunarAstronomy = remember(weather?.sunriseToday, weather?.sunsetToday, uiState.currentTimeString) {
    calculateLunarAstronomy(weather)
  }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    BoxWithConstraints(
      contentAlignment = Alignment.Center
    ) {
      val maxAvailableDim = minOf(maxWidth, maxHeight)
      val adaptiveSize = if (maxAvailableDim > 0.dp && maxAvailableDim < coreSizeDp) {
        maxAvailableDim * 0.94f
      } else {
        coreSizeDp
      }

      WeatherAdaptiveCoreLayer(
        uiState = uiState,
        coreSizeDp = adaptiveSize.coerceAtLeast(48.dp)
      ) {
        WeykyCoreView(
          visualState = uiState.visualState,
          sizeDp = adaptiveSize.coerceAtLeast(48.dp),
          isPowerSaving = uiState.isPowerSaving,
          isVoiceListening = uiState.isVoiceListening,
          isSpeakingAloud = uiState.isSpeakingAloud,
          isMusicPlaying = uiState.isMusicPlaying || (uiState.activeMediaMetadata?.isPlaying == true),
          isCharging = isDeviceCharging,
          batteryPercentage = currentBatteryPct,
          voiceEnergyLevel = uiState.voiceEnergyLevel,
          isNightMode = isNight,
          isMoonVisible = lunarAstronomy.isMoonInAppearanceWindow,
          lunarCyclePhase = lunarAstronomy.cyclePhase,
          onClick = onToggleVoice
        )
      }
    }
  }
}

@Composable
fun HomeResponseCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  onQuerySubmit: (String) -> Unit,
  onConfirmOption: (String) -> Unit,
  onRequestPermission: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = !uiState.lastResponseText.isNullOrBlank(),
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier
  ) {
    WeykyAdaptiveCard(
      uiState = uiState,
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutCornerSize = 14.dp,
      tabTitle = "RESPONSE",
      tabPosition = HudTabPosition.TOP_START,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("response_bubble")
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = uiState.lastResponseText ?: "",
          fontSize = 14.sp,
          color = textPrimary,
          lineHeight = 20.sp
        )

        // Proactive Conversational Suggestions & Next Steps
        if (uiState.proactiveSuggestions.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            uiState.proactiveSuggestions.forEach { suggestion ->
              JarvisHudBadge(
                accentColor = borderColor,
                containerColor = panelBg,
                cutSize = 7.dp,
                onClick = { onQuerySubmit(suggestion.queryToExecute) }
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                  Text(
                    text = suggestion.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(12.dp)
                  )
                }
              }
            }
          }
        }

        // Multiple Options Selection (Contacts or Apps)
        if (uiState.pendingConfirmationOptions.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            uiState.pendingConfirmationOptions.forEach { option ->
              JarvisHudBadge(
                accentColor = borderColor,
                containerColor = panelBg,
                cutSize = 8.dp,
                onClick = { onConfirmOption(option) },
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(text = option, fontSize = 13.sp, color = textPrimary)
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }

        // Permission Request Recovery Action
        if (uiState.pendingPermission != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Button(
            onClick = { onRequestPermission(uiState.pendingPermission.first) },
            colors = ButtonDefaults.buttonColors(containerColor = borderColor),
            shape = CutCornerShape(8.dp),
            modifier = Modifier
              .align(Alignment.End)
              .testTag("grant_permission_button")
          ) {
            Text("منح الصلاحية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisHudColors.PitchBlack)
          }
        } else if (uiState.pendingRecoveryActionTitle != null && uiState.pendingRecoveryAction != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Button(
            onClick = { uiState.pendingRecoveryAction.invoke() },
            colors = ButtonDefaults.buttonColors(containerColor = borderColor),
            shape = CutCornerShape(8.dp),
            modifier = Modifier
              .align(Alignment.End)
              .testTag("recovery_action_button")
          ) {
            Text(uiState.pendingRecoveryActionTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisHudColors.PitchBlack)
          }
        }
      }
    }
  }
}

@Composable
fun HomeCommandInputCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  isLight: Boolean,
  onQueryChange: (String) -> Unit,
  onQuerySubmit: (String) -> Unit,
  onToggleVoice: () -> Unit,
  keyboardController: SoftwareKeyboardController?,
  modifier: Modifier = Modifier
) {
  val activeInputAccent = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor

  WeykyAdaptiveCard(
    uiState = uiState,
    accentColor = activeInputAccent,
    containerColor = panelBg,
    cutCornerSize = 16.dp,
    tabTitle = "WEYKY",
    tabPosition = HudTabPosition.TOP_START,
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Left Microphone Button (48dp touch target)
      IconButton(
        onClick = onToggleVoice,
        modifier = Modifier
          .size(48.dp)
          .testTag("voice_input_button")
      ) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = if (uiState.isVoiceListening) "Listening... tap to cancel" else "Voice Input",
          tint = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor,
          modifier = Modifier.size(24.dp)
        )
      }

      // Center Seamless Input Field ("Ask Weyky...")
      Box(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        if (uiState.inputQuery.isEmpty()) {
          val hint = if (uiState.isVoiceListening) "Listening... Speak now" else "Ask Weyky..."
          Text(
            text = hint,
            fontSize = 16.sp,
            color = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else textSecondary
          )
        }
        BasicTextField(
          value = uiState.inputQuery,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = TextStyle(
            color = textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
          ),
          cursorBrush = SolidColor(borderColor),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = {
            if (uiState.inputQuery.isNotBlank()) {
              onQuerySubmit(uiState.inputQuery)
              keyboardController?.hide()
            }
          }),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("ask_weyky_input")
        )
      }

      // Right Circular Arrow Action Button
      IconButton(
        onClick = {
          if (uiState.inputQuery.isNotBlank()) {
            onQuerySubmit(uiState.inputQuery)
            keyboardController?.hide()
          } else {
            onQuerySubmit("ماذا أفعل الآن؟")
          }
        },
        modifier = Modifier
          .size(48.dp)
          .testTag("submit_query_button")
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (isLight) borderColor.copy(alpha = 0.12f) else Color(0x33071828))
            .border(1.2.dp, borderColor.copy(alpha = 0.65f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Send",
            tint = textSecondary,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun HomeContextAlertCard(
  uiState: WeykyUiState,
  borderColor: Color,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier,
  compactLandscapeMode: Boolean = false,
  landscapeMinWidth: Dp = 275.dp,
  landscapeMaxWidth: Dp = 320.dp,
  landscapeHeight: Dp = 64.dp,
  landscapeIconBoxSize: Dp = 44.dp,
  landscapeTitleSp: androidx.compose.ui.unit.TextUnit = 13.sp,
  landscapeSubtitleSp: androidx.compose.ui.unit.TextUnit = 10.5.sp
) {
  val battery = uiState.batterySnapshot
  val isLowBattery = battery != null && battery.isAvailable && battery.percentage <= 20 &&
    battery.chargingState != ChargingState.CHARGING && battery.chargingState != ChargingState.FULL
  val isBatteryFull = battery != null && battery.isAvailable && battery.percentage == 100 &&
    battery.chargingState == ChargingState.FULL

  val missedCallsSummary = uiState.missedCallsSummary
  val latestMissedCall = missedCallsSummary?.latestMissedCall
  val missedCount = missedCallsSummary?.missedCallsCount ?: 0

  val notifSummary = uiState.notificationSummary
  val latestUrgentNotif = notifSummary?.latestImportantEvent
  val urgentNotifCount = notifSummary?.totalImportantCount ?: 0

  val fallbackEvent = uiState.importantEvents.firstOrNull()

  val activeEventSignature: String? = when {
    isLowBattery -> "batt_low_${battery?.percentage}"
    isBatteryFull -> "batt_full_100"
    latestMissedCall != null && missedCount > 0 ->
      "missed_${latestMissedCall.id}_${latestMissedCall.dateMillis}_$missedCount"
    latestUrgentNotif != null && urgentNotifCount > 0 ->
      "notif_${latestUrgentNotif.eventId}_${latestUrgentNotif.timestampMillis}"
    fallbackEvent != null ->
      "event_${fallbackEvent.id}_${fallbackEvent.title}_${fallbackEvent.subtitle}"
    else -> null
  }

  var dismissedSignature by remember { mutableStateOf<String?>(null) }

  val isCardVisible = activeEventSignature != null && activeEventSignature != dismissedSignature

  AnimatedVisibility(
    visible = isCardVisible,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier
  ) {
    val mainTitle: String
    val subLabel: String
    val rightTopText: String
    val rightBottomLabel: String
    val iconColor: Color
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector
    val onCardClick: () -> Unit

    when {
      isLowBattery -> {
        mainTitle = "طاقة منخفضة (${battery?.percentage}%)"
        subLabel = "LOW BATTERY ALERT • يُرجى توصيل الشاحن"
        rightTopText = "${battery?.percentage}%"
        rightBottomLabel = "POWER"
        iconColor = JarvisHudColors.CrimsonAlert
        iconVector = Icons.Default.BatteryChargingFull
        onCardClick = {
          dismissedSignature = activeEventSignature
          onQuerySubmit("حالة البطارية")
        }
      }

      isBatteryFull -> {
        mainTitle = "اكتمل الشحن (100%)"
        subLabel = "BATTERY CHARGED • يمكنك فصل الشاحن الآن"
        rightTopText = "100%"
        rightBottomLabel = "FULL"
        iconColor = JarvisHudColors.NeonGreen
        iconVector = Icons.Default.BatteryChargingFull
        onCardClick = {
          dismissedSignature = activeEventSignature
          onQuerySubmit("حالة البطارية")
        }
      }

      latestMissedCall != null && missedCount > 0 -> {
        val callerOrNumber = latestMissedCall.number.ifBlank {
          latestMissedCall.callerName ?: "مكالمة فائتة"
        }
        val callerSub = if (!latestMissedCall.callerName.isNullOrBlank() && latestMissedCall.callerName != latestMissedCall.number) {
          "MISSED CALL • ${latestMissedCall.callerName.uppercase()}"
        } else if (missedCount > 1) {
          "MISSED CALL ($missedCount)"
        } else {
          "MISSED CALL"
        }
        mainTitle = callerOrNumber
        subLabel = callerSub
        rightTopText = latestMissedCall.formattedTime
          .replace(" AM", "")
          .replace(" PM", "")
          .replace(" ص", "")
          .replace(" م", "")
          .ifBlank { uiState.currentDateString.ifBlank { "TODAY" } }
        rightBottomLabel = "TIME"
        iconColor = JarvisHudColors.NeonGreen
        iconVector = Icons.Default.PhoneCallback
        onCardClick = {
          dismissedSignature = activeEventSignature
          onQuerySubmit("المكالمات الفائتة")
        }
      }

      latestUrgentNotif != null && urgentNotifCount > 0 -> {
        val appTag = latestUrgentNotif.appLabel?.uppercase() ?: "ALERT"
        val countSuffix = if (urgentNotifCount > 1) " (+${urgentNotifCount - 1})" else ""
        mainTitle = latestUrgentNotif.title.ifBlank { appTag } + countSuffix
        subLabel = "$appTag • ${latestUrgentNotif.text.ifBlank { "NEW MESSAGE" }}".uppercase()

        val timeFormatted = remember(latestUrgentNotif.timestampMillis) {
          java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(java.util.Date(latestUrgentNotif.timestampMillis))
        }
        rightTopText = timeFormatted
        rightBottomLabel = "NOW"
        iconColor = when (latestUrgentNotif.importanceCategory) {
          com.example.device.notifications.NotificationImportanceCategory.SECURITY_SYSTEM_ALERT -> JarvisHudColors.CrimsonAlert
          com.example.device.notifications.NotificationImportanceCategory.REMINDER_EVENT -> JarvisHudColors.TacticalAmber
          else -> JarvisHudColors.NeonGreen
        }
        iconVector = when (latestUrgentNotif.importanceCategory) {
          com.example.device.notifications.NotificationImportanceCategory.MISSED_CALL -> Icons.AutoMirrored.Filled.PhoneMissed
          com.example.device.notifications.NotificationImportanceCategory.SECURITY_SYSTEM_ALERT -> Icons.Default.Warning
          else -> Icons.Default.Notifications
        }
        onCardClick = {
          dismissedSignature = activeEventSignature
          onQuerySubmit("اقرأ الإشعارات")
        }
      }

      else -> {
        val ev = fallbackEvent
        mainTitle = ev?.title ?: ""
        subLabel = ev?.subtitle?.uppercase() ?: ""
        rightTopText = ev?.actionTitle ?: uiState.currentTimeString
          .replace(" AM", "")
          .replace(" PM", "")
          .replace(" ص", "")
          .replace(" م", "")
        rightBottomLabel = "TIME"
        iconColor = JarvisHudColors.NeonGreen
        iconVector = if (ev?.icon == "phone_missed") Icons.Default.PhoneCallback else Icons.Default.Notifications
        onCardClick = {
          dismissedSignature = activeEventSignature
          ev?.onAction?.invoke() ?: onQuerySubmit("المكالمات الفائتة والإشعارات")
        }
      }
    }

    val testTagId = fallbackEvent?.let { "event_banner_${it.id}" } ?: "events_bottom_card"
    val isLight = uiState.isLightMode
    val panelBg = JarvisHudColors.panelBg(isLight)
    val textPrimary = JarvisHudColors.textPrimary(isLight)
    val textSecondary = JarvisHudColors.textSecondary(isLight)

    if (compactLandscapeMode) {
      val isTactical = uiState.themeMode == WeykyThemeMode.TACTICAL
      val isModern = uiState.themeMode == WeykyThemeMode.MODERN
      val cardShape = when {
        isTactical -> CutCornerShape(8.dp)
        isModern -> RoundedCornerShape(18.dp)
        else -> RoundedCornerShape(12.dp)
      }
      val cardBg = when {
        isTactical -> Color(0xFF030D1A).copy(alpha = 0.90f)
        isModern -> Color(0xFF071426).copy(alpha = 0.82f)
        else -> panelBg.copy(alpha = 0.85f)
      }
      val cardBorder = when {
        isTactical -> borderColor.copy(alpha = 0.80f)
        isModern -> borderColor.copy(alpha = 0.55f)
        else -> borderColor.copy(alpha = 0.35f)
      }

      Box(
        modifier = modifier
          .widthIn(min = landscapeMinWidth, max = landscapeMaxWidth)
          .height(landscapeHeight)
          .shadow(elevation = if (isModern) 12.dp else 8.dp, shape = cardShape)
          .clip(cardShape)
          .background(cardBg)
          .border(
            width = if (isTactical) 1.3.dp else 1.dp,
            color = cardBorder,
            shape = cardShape
          )
          .clickable(onClick = onCardClick)
          .padding(horizontal = 10.dp, vertical = 7.dp)
          .testTag(testTagId)
      ) {
        Row(
          modifier = Modifier.fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(landscapeIconBoxSize)
                .clip(if (isTactical) CutCornerShape(5.dp) else RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.16f))
                .border(
                  width = 1.2.dp,
                  color = iconColor.copy(alpha = 0.85f),
                  shape = if (isTactical) CutCornerShape(5.dp) else RoundedCornerShape(10.dp)
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = iconVector,
                contentDescription = mainTitle,
                tint = iconColor,
                modifier = Modifier.size(landscapeIconBoxSize * 0.48f)
              )
            }

            Column(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
              verticalArrangement = Arrangement.Center
            ) {
              Text(
                text = mainTitle,
                fontSize = landscapeTitleSp,
                fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = subLabel,
                fontSize = landscapeSubtitleSp,
                fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Column(
              horizontalAlignment = Alignment.End,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(start = 2.dp)
            ) {
              Text(
                text = rightTopText,
                fontSize = landscapeSubtitleSp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                textAlign = TextAlign.End
              )
              Text(
                text = rightBottomLabel,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                color = textSecondary,
                textAlign = TextAlign.End
              )
            }

            IconButton(
              onClick = { dismissedSignature = activeEventSignature },
              modifier = Modifier
                .size(30.dp)
                .testTag("dismiss_alert_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss Alert",
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    } else {
      WeykyAdaptiveCard(
        uiState = uiState,
        accentColor = borderColor,
        containerColor = panelBg,
        cutCornerSize = 16.dp,
        tabTitle = "ALERTS",
        tabPosition = HudTabPosition.BOTTOM_END,
        onClick = onCardClick,
        modifier = modifier
          .fillMaxWidth()
          .testTag(testTagId)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = iconColor, spotColor = iconColor)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.14f))
                .border(1.5.dp, iconColor.copy(alpha = 0.85f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = iconVector,
                contentDescription = mainTitle,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = mainTitle,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = subLabel,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Column(
              horizontalAlignment = Alignment.End,
              modifier = Modifier.padding(start = 4.dp)
            ) {
              Text(
                text = rightTopText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                textAlign = TextAlign.End
              )
              Text(
                text = rightBottomLabel,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp,
                color = textSecondary,
                textAlign = TextAlign.End
              )
            }

            IconButton(
              onClick = { dismissedSignature = activeEventSignature },
              modifier = Modifier
                .size(32.dp)
                .testTag("dismiss_alert_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss Alert",
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Backward compatibility alias for EventsBottomHudCard.
 */
@Composable
fun EventsBottomHudCard(
  uiState: WeykyUiState,
  borderColor: Color,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  HomeContextAlertCard(
    uiState = uiState,
    borderColor = borderColor,
    onQuerySubmit = onQuerySubmit,
    modifier = modifier
  )
}
