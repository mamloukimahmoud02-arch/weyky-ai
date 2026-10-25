package com.example.ui.home.desktop

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.state.WeykyUiState
import com.example.device.apps.AppMatch
import com.example.device.battery.ChargingState
import com.example.device.network.NetworkType
import com.example.ui.home.AppIconCache
import com.example.ui.theme.JarvisHudColors
import com.example.ui.theme.WeykyThemeMode

/**
 * Arabic RTL Desktop-Inspired Taskbar for WEYKY Landscape Mode.
 * Designed with authentic desktop UX standards for Arabic:
 * - Right (Start): WEYKY Start Button + Search/Voice Input Box.
 * - Center: Pinned Applications Dock.
 * - Left (End): System Tray with Network Status, Live Clock, and Battery Info.
 *
 * Fully reactive to all three themes: Tactical HUD, Modern, and Minimal.
 */
@Composable
fun DesktopTaskbar(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  isStartMenuOpen: Boolean,
  onToggleStartMenu: () -> Unit,
  onLaunchApp: (AppMatch) -> Unit,
  onUnpinApp: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onQuerySubmit: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onNetworkClick: () -> Unit,
  onNetworkLongClick: () -> Unit,
  onBatteryClick: () -> Unit,
  onTimeClick: () -> Unit,
  keyboardController: SoftwareKeyboardController?,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val themeMode = uiState.themeMode
  val isTactical = themeMode == WeykyThemeMode.TACTICAL
  val isModern = themeMode == WeykyThemeMode.MODERN

  val taskbarShape: Shape = when {
    isTactical -> CutCornerShape(6.dp)
    isModern -> RoundedCornerShape(24.dp)
    else -> RoundedCornerShape(14.dp)
  }

  val taskbarBg = when {
    isTactical -> Brush.verticalGradient(
      colors = listOf(
        Color(0xFF030D1C).copy(alpha = 0.94f),
        Color(0xFF01060D).copy(alpha = 0.98f)
      )
    )
    isModern -> Brush.horizontalGradient(
      colors = listOf(
        Color(0x33101E36),
        Color(0x4D0C1527),
        Color(0x33101E36)
      )
    )
    else -> Brush.verticalGradient(
      colors = listOf(
        panelBg.copy(alpha = 0.90f),
        panelBg.copy(alpha = 0.96f)
      )
    )
  }

  val taskbarBorderColor = when {
    isTactical -> borderColor.copy(alpha = 0.85f)
    isModern -> borderColor.copy(alpha = 0.50f)
    else -> borderColor.copy(alpha = 0.35f)
  }

  // Pure Arabic RTL alignment: Start on right, tray on left
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .height(48.dp)
        .shadow(elevation = if (isModern) 12.dp else 8.dp, shape = taskbarShape)
        .clip(taskbarShape)
        .background(taskbarBg)
        .border(
          width = if (isTactical) 1.2.dp else 1.dp,
          color = taskbarBorderColor,
          shape = taskbarShape
        )
        .testTag("desktop_taskbar")
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // 1. RIGHT SIDE: WEYKY Start Button & Search/Voice Input Box
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          DesktopWekyButton(
            isStartMenuOpen = isStartMenuOpen,
            themeMode = themeMode,
            borderColor = borderColor,
            textPrimary = textPrimary,
            onClick = onToggleStartMenu
          )

          DesktopSlimSearchBox(
            uiState = uiState,
            themeMode = themeMode,
            borderColor = borderColor,
            panelBgElevated = panelBgElevated,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onQueryChange = onQueryChange,
            onQuerySubmit = onQuerySubmit,
            onToggleVoice = onToggleVoice,
            keyboardController = keyboardController
          )
        }

        // 2. CENTER: Pinned Applications Dock
        DesktopPinnedAppsDock(
          pinnedPackages = uiState.pinnedAppPackageNames,
          installedApps = uiState.installedApps,
          context = context,
          themeMode = themeMode,
          borderColor = borderColor,
          textPrimary = textPrimary,
          onLaunchApp = onLaunchApp,
          onUnpinApp = onUnpinApp
        )

        // 3. LEFT SIDE: System Tray (Network, Live Clock, Battery)
        DesktopSystemPill(
          uiState = uiState,
          themeMode = themeMode,
          borderColor = borderColor,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onNetworkClick = onNetworkClick,
          onNetworkLongClick = onNetworkLongClick,
          onBatteryClick = onBatteryClick,
          onTimeClick = onTimeClick
        )
      }
    }
  }
}

/**
 * System Tray on the far-left of the Arabic taskbar.
 * Layout (RTL): [ Network Icon | Live Clock | Battery % | Battery Glyph ]
 * Fully interactive: Clicking any component sends its data to Core to be vocalized.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopSystemPill(
  uiState: WeykyUiState,
  themeMode: WeykyThemeMode,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  onNetworkClick: () -> Unit,
  onNetworkLongClick: () -> Unit,
  onBatteryClick: () -> Unit,
  onTimeClick: () -> Unit
) {
  val isTactical = themeMode == WeykyThemeMode.TACTICAL
  val isModern = themeMode == WeykyThemeMode.MODERN

  val pillShape: Shape = when {
    isTactical -> CutCornerShape(4.dp)
    isModern -> RoundedCornerShape(16.dp)
    else -> RoundedCornerShape(10.dp)
  }

  val pillBorder = when {
    isTactical -> borderColor.copy(alpha = 0.50f)
    isModern -> borderColor.copy(alpha = 0.35f)
    else -> borderColor.copy(alpha = 0.25f)
  }

  Row(
    modifier = Modifier
      .height(36.dp)
      .clip(pillShape)
      .background(Color.White.copy(alpha = 0.05f))
      .border(1.dp, pillBorder, pillShape)
      .padding(horizontal = 8.dp, vertical = 2.dp)
      .testTag("desktop_system_tray"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // 1. Network Status Indicator (Replaces call button)
    val network = uiState.networkSnapshot
    val isConnected = network?.isConnected == true
    val netIcon = when {
      !isConnected -> Icons.Default.WifiOff
      network?.type == NetworkType.CELLULAR -> Icons.Default.SignalCellularAlt
      else -> Icons.Default.Wifi
    }
    val netTint = if (isConnected) Color(0xFF00F0FF) else Color(0xFFFF4D4D)

    Box(
      modifier = Modifier
        .size(28.dp)
        .clip(RoundedCornerShape(6.dp))
        .combinedClickable(
          onClick = onNetworkClick,
          onLongClick = onNetworkLongClick
        )
        .testTag("desktop_network_button"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = netIcon,
        contentDescription = "حالة اتصال الشبكة",
        tint = netTint,
        modifier = Modifier.size(16.dp)
      )
    }

    // 2. Live Clock (Interactive -> speaks time)
    val timeText = uiState.currentTimeString.ifBlank { "07:43" }
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .clickable(onClick = onTimeClick)
        .padding(horizontal = 2.dp, vertical = 2.dp)
        .testTag("desktop_clock_button"),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = timeText,
        color = textPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
      )
    }

    // 3. Battery Percentage & Icon (Interactive -> speaks battery state)
    val battery = uiState.batterySnapshot
    val percentage = battery?.percentage ?: 93
    val isCharging = battery?.chargingState == ChargingState.CHARGING || battery?.chargingState == ChargingState.FULL

    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .clickable(onClick = onBatteryClick)
        .padding(horizontal = 2.dp, vertical = 2.dp)
        .testTag("desktop_battery_button"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Text(
        text = "$percentage%",
        color = Color(0xFF00F0FF),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
      )

      Icon(
        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
        contentDescription = "البطارية",
        tint = Color(0xFF00F0FF),
        modifier = Modifier.size(15.dp)
      )
    }
  }
}

/**
 * Pinned apps dock with active cyan indicator bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopPinnedAppsDock(
  pinnedPackages: List<String>,
  installedApps: List<AppMatch>,
  context: Context,
  themeMode: WeykyThemeMode,
  borderColor: Color,
  textPrimary: Color,
  onLaunchApp: (AppMatch) -> Unit,
  onUnpinApp: (String) -> Unit
) {
  val itemShape = when (themeMode) {
    WeykyThemeMode.TACTICAL -> CutCornerShape(4.dp)
    WeykyThemeMode.MODERN -> RoundedCornerShape(12.dp)
    WeykyThemeMode.MINIMAL -> RoundedCornerShape(8.dp)
  }

  Row(
    modifier = Modifier
      .horizontalScroll(rememberScrollState())
      .testTag("desktop_pinned_apps_tray")
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    for (pkg in pinnedPackages) {
      val appMatch = installedApps.firstOrNull { it.packageName == pkg }
      val appName = appMatch?.label ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
      val iconBitmap = remember(pkg) { AppIconCache.getIcon(context, pkg) }

      var showMenu by remember { mutableStateOf(false) }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(itemShape)
            .background(Color.White.copy(alpha = 0.05f))
            .combinedClickable(
              onClick = {
                if (appMatch != null) {
                  onLaunchApp(appMatch)
                } else {
                  val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                  if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                  }
                }
              },
              onLongClick = {
                showMenu = true
              }
            )
            .padding(2.dp)
            .testTag("pinned_app_$pkg"),
          contentAlignment = Alignment.Center
        ) {
          if (iconBitmap != null) {
            Image(
              bitmap = iconBitmap,
              contentDescription = appName,
              modifier = Modifier.size(26.dp)
            )
          } else {
            Box(
              modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(borderColor.copy(alpha = 0.25f))
                .border(1.dp, borderColor.copy(alpha = 0.4f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = appName.take(1).uppercase(),
                color = textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
          ) {
            DropdownMenuItem(
              text = { Text("إزالة من شريط المهام") },
              onClick = {
                showMenu = false
                onUnpinApp(pkg)
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = null,
                  tint = Color.Red
                )
              }
            )
          }
        }

        // Active app indicator bar
        Box(
          modifier = Modifier
            .padding(top = 1.dp)
            .width(12.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(Color(0xFF00F0FF).copy(alpha = 0.7f))
        )
      }
    }
  }
}

/**
 * Slim Search / Voice Input Box.
 * Visibly and dynamically transforms across all three themes:
 * - TACTICAL: Chamfered cut corners, monospace text, crisp HUD wireframe border.
 * - MODERN: Smooth capsule shape, frosted translucent glass gradient, neon glow.
 * - MINIMAL: Clean rounded rectangle, flat calm background, minimal hairline border.
 */
@Composable
private fun DesktopSlimSearchBox(
  uiState: WeykyUiState,
  themeMode: WeykyThemeMode,
  borderColor: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  onQueryChange: (String) -> Unit,
  onQuerySubmit: (String) -> Unit,
  onToggleVoice: () -> Unit,
  keyboardController: SoftwareKeyboardController?
) {
  val isTactical = themeMode == WeykyThemeMode.TACTICAL
  val isModern = themeMode == WeykyThemeMode.MODERN
  val isListening = uiState.isVoiceListening

  val boxShape: Shape = when {
    isTactical -> CutCornerShape(6.dp)
    isModern -> RoundedCornerShape(20.dp)
    else -> RoundedCornerShape(8.dp)
  }

  val boxBg = when {
    isTactical -> Color(0xFF030D1A).copy(alpha = 0.90f)
    isModern -> Color(0xFF071426).copy(alpha = 0.75f)
    else -> panelBgElevated.copy(alpha = 0.65f)
  }

  val activeBorder = when {
    isListening -> JarvisHudColors.MagentaPulse
    isTactical -> borderColor.copy(alpha = 0.85f)
    isModern -> borderColor.copy(alpha = 0.55f)
    else -> borderColor.copy(alpha = 0.35f)
  }

  Row(
    modifier = Modifier
      .height(34.dp)
      .widthIn(min = 180.dp, max = 280.dp)
      .clip(boxShape)
      .background(boxBg)
      .border(
        width = if (isTactical) 1.2.dp else 1.dp,
        color = activeBorder,
        shape = boxShape
      )
      .padding(horizontal = 6.dp)
      .testTag("home_bottom_bar"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Voice Mic button inside search pill
    IconButton(
      onClick = onToggleVoice,
      modifier = Modifier
        .size(26.dp)
        .testTag("voice_input_button")
    ) {
      Icon(
        imageVector = Icons.Default.Mic,
        contentDescription = if (isListening) "جاري الاستماع..." else "البحث الصوتي",
        tint = if (isListening) JarvisHudColors.MagentaPulse else Color(0xFF00F0FF),
        modifier = Modifier.size(16.dp)
      )
    }

    // Input text field with Arabic "...اسأل وايكي" hint
    Box(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 4.dp),
      contentAlignment = Alignment.CenterStart
    ) {
      if (uiState.inputQuery.isEmpty()) {
        val hint = if (isListening) "تكلم الآن..." else "...اسأل وايكي"
        Text(
          text = hint,
          fontSize = 12.sp,
          color = if (isListening) JarvisHudColors.MagentaPulse else Color(0xFF6E8299),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Start,
          fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
        )
      }
      BasicTextField(
        value = uiState.inputQuery,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(
          color = textPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.Normal,
          textAlign = TextAlign.Start,
          fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
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

    // Send button if text is entered
    if (uiState.inputQuery.isNotBlank()) {
      IconButton(
        onClick = {
          onQuerySubmit(uiState.inputQuery)
          keyboardController?.hide()
        },
        modifier = Modifier
          .size(26.dp)
          .testTag("query_submit_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = "إرسال",
          tint = Color(0xFF00F0FF),
          modifier = Modifier.size(15.dp)
        )
      }
    }
  }
}

/**
 * WEYKY Start Button.
 * Visibly and dynamically transforms across all three themes:
 * - TACTICAL: Chamfered cut corners, monospace bold typography, technical HUD glow.
 * - MODERN: Smooth capsule shape, glowing gradient, frosted glass style.
 * - MINIMAL: Clean rounded rectangle, calm flat styling, thin border.
 */
@Composable
private fun DesktopWekyButton(
  isStartMenuOpen: Boolean,
  themeMode: WeykyThemeMode,
  borderColor: Color,
  textPrimary: Color,
  onClick: () -> Unit
) {
  val isTactical = themeMode == WeykyThemeMode.TACTICAL
  val isModern = themeMode == WeykyThemeMode.MODERN

  val buttonShape: Shape = when {
    isTactical -> CutCornerShape(6.dp)
    isModern -> RoundedCornerShape(20.dp)
    else -> RoundedCornerShape(8.dp)
  }

  val activeBg = if (isStartMenuOpen) {
    borderColor.copy(alpha = 0.35f)
  } else {
    when {
      isTactical -> Color(0xFF030D1A).copy(alpha = 0.90f)
      isModern -> Color.White.copy(alpha = 0.08f)
      else -> Color.White.copy(alpha = 0.04f)
    }
  }

  val activeBorder = if (isStartMenuOpen) {
    Color(0xFF00F0FF)
  } else {
    when {
      isTactical -> borderColor.copy(alpha = 0.85f)
      isModern -> borderColor.copy(alpha = 0.55f)
      else -> borderColor.copy(alpha = 0.35f)
    }
  }

  Row(
    modifier = Modifier
      .height(34.dp)
      .clip(buttonShape)
      .background(activeBg)
      .border(
        width = if (isTactical) 1.2.dp else 1.dp,
        color = activeBorder,
        shape = buttonShape
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 2.dp)
      .testTag("desktop_start_button"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    // WEYKY Circular Emblem
    Box(
      modifier = Modifier
        .size(24.dp)
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            colors = listOf(
              Color(0xFF00F0FF).copy(alpha = 0.45f),
              Color(0xFF0055FF).copy(alpha = 0.20f),
              Color.Transparent
            )
          )
        )
        .border(1.dp, Color(0xFF00F0FF), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_launcher_foreground),
        contentDescription = "شعار WEYKY",
        modifier = Modifier.size(20.dp)
      )
    }

    Text(
      text = "WEYKY",
      color = textPrimary,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.8.sp,
      fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
    )
  }
}
