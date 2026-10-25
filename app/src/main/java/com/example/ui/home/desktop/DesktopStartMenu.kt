package com.example.ui.home.desktop

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.state.WeykyUiState
import com.example.device.apps.AppMatch
import com.example.ui.home.AppIconCache
import com.example.ui.theme.JarvisHudColors
import com.example.ui.theme.WeykyThemeMode

/**
 * Modern Windows-inspired Start Menu tailored for WEYKY Landscape Mode.
 * Merges application discovery, taskbar pinning management, and settings access:
 * - Searchable installed applications list.
 * - Interactive Pin / Unpin action buttons with instant local persistence.
 * - Direct shortcut to Settings and launcher controls.
 * - Adheres strictly to the selected theme (Tactical HUD, Modern Glass, Minimal).
 */
@Composable
fun DesktopStartMenu(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  onDismiss: () -> Unit,
  onLaunchApp: (AppMatch) -> Unit,
  onTogglePinApp: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onRequestDefaultLauncher: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val themeMode = uiState.themeMode
  val isTactical = themeMode == WeykyThemeMode.TACTICAL
  val isModern = themeMode == WeykyThemeMode.MODERN
  val isMinimal = themeMode == WeykyThemeMode.MINIMAL

  var searchQuery by remember { mutableStateOf("") }

  val filteredApps = remember(searchQuery, uiState.installedApps) {
    if (searchQuery.isBlank()) {
      uiState.installedApps
    } else {
      val queryLower = searchQuery.trim().lowercase()
      uiState.installedApps.filter {
        it.label.lowercase().contains(queryLower) ||
          it.packageName.lowercase().contains(queryLower) ||
          it.normalizedLabel.lowercase().contains(queryLower)
      }
    }
  }

  val menuShape = when {
    isTactical -> CutCornerShape(10.dp)
    isModern -> RoundedCornerShape(20.dp)
    else -> RoundedCornerShape(14.dp)
  }

  val menuBg = when {
    isTactical -> Brush.verticalGradient(
      colors = listOf(
        Color(0xFF041021).copy(alpha = 0.98f),
        Color(0xFF01060D).copy(alpha = 0.99f)
      )
    )
    isModern -> Brush.verticalGradient(
      colors = listOf(
        panelBgElevated.copy(alpha = 0.92f),
        panelBg.copy(alpha = 0.96f)
      )
    )
    else -> Brush.verticalGradient(
      colors = listOf(
        panelBg.copy(alpha = 0.98f),
        panelBg.copy(alpha = 0.99f)
      )
    )
  }

  val menuBorderColor = when {
    isTactical -> borderColor.copy(alpha = 0.85f)
    isModern -> borderColor.copy(alpha = 0.45f)
    else -> borderColor.copy(alpha = 0.30f)
  }

  Surface(
    modifier = modifier
      .widthIn(min = 360.dp, max = 460.dp)
      .heightIn(min = 380.dp, max = 490.dp)
      .shadow(elevation = if (isModern) 20.dp else 12.dp, shape = menuShape)
      .clip(menuShape)
      .border(
        width = if (isTactical) 1.5.dp else 1.dp,
        color = menuBorderColor,
        shape = menuShape
      )
      .testTag("desktop_start_menu"),
    shape = menuShape,
    color = Color.Transparent
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(menuBg)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // 1. HEADER: Emblem, WEYKY title, and Close button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(borderColor.copy(alpha = 0.2f))
                .border(1.2.dp, borderColor, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "WEYKY Emblem",
                modifier = Modifier.size(28.dp)
              )
            }

            Column {
              Text(
                text = "WEYKY OS",
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isTactical) FontFamily.Monospace else FontFamily.SansSerif
              )
              Text(
                text = "Ambient Core • تجربة سطح المكتب",
                color = textSecondary,
                fontSize = 11.sp
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close Start Menu",
              tint = textMuted,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. SEARCH BAR
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = {
            Text(
              text = "بحث في التطبيقات...",
              fontSize = 12.sp,
              color = textMuted
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Search",
              tint = borderColor,
              modifier = Modifier.size(18.dp)
            )
          },
          singleLine = true,
          shape = if (isTactical) CutCornerShape(6.dp) else RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor.copy(alpha = 0.35f),
            focusedTextColor = textPrimary,
            unfocusedTextColor = textPrimary,
            cursorColor = borderColor
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("desktop_start_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. APPS LIST WITH PIN/UNPIN ACTIONS
        Text(
          text = if (searchQuery.isBlank()) "كافة التطبيقات المتاحة (${filteredApps.size})" else "نتائج البحث (${filteredApps.size})",
          color = borderColor,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.5.sp,
          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .testTag("desktop_start_apps_list"),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          if (filteredApps.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "لا توجد تطبيقات مطابقة",
                  color = textMuted,
                  fontSize = 12.sp
                )
              }
            }
          } else {
            items(filteredApps, key = { it.packageName }) { app ->
              val isPinned = uiState.pinnedAppPackageNames.contains(app.packageName)
              val iconBitmap = remember(app.packageName) { AppIconCache.getIcon(context, app.packageName) }

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(if (isTactical) CutCornerShape(4.dp) else RoundedCornerShape(8.dp))
                  .background(Color.White.copy(alpha = 0.03f))
                  .clickable {
                    onLaunchApp(app)
                    onDismiss()
                  }
                  .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  modifier = Modifier.weight(1f),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  // App Icon
                  if (iconBitmap != null) {
                    Image(
                      bitmap = iconBitmap,
                      contentDescription = app.label,
                      modifier = Modifier.size(32.dp)
                    )
                  } else {
                    Box(
                      modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(borderColor.copy(alpha = 0.2f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = app.label.take(1).uppercase(),
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }

                  // App Labels
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = app.label,
                      color = textPrimary,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = app.packageName,
                      color = textMuted,
                      fontSize = 10.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }

                // Interactive Pin/Unpin Button
                IconButton(
                  onClick = { onTogglePinApp(app.packageName) },
                  modifier = Modifier
                    .size(34.dp)
                    .testTag("pin_toggle_${app.packageName}")
                ) {
                  Icon(
                    imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "إلغاء التثبيت" else "تثبيت في شريط المهام",
                    tint = if (isPinned) borderColor else textMuted,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. FOOTER: Settings and Launcher shortcuts
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(borderColor.copy(alpha = 0.25f))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Settings button
          Row(
            modifier = Modifier
              .clip(if (isTactical) CutCornerShape(4.dp) else RoundedCornerShape(8.dp))
              .background(Color.White.copy(alpha = 0.05f))
              .clickable {
                onDismiss()
                onOpenSettings()
              }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("start_menu_settings_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Settings",
              tint = borderColor,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "الإعدادات (Settings)",
              color = textPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }

          // Default Launcher button
          Row(
            modifier = Modifier
              .clip(if (isTactical) CutCornerShape(4.dp) else RoundedCornerShape(8.dp))
              .background(Color.White.copy(alpha = 0.05f))
              .clickable {
                onDismiss()
                onRequestDefaultLauncher()
              }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("start_menu_launcher_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Home,
              contentDescription = "Default Launcher",
              tint = borderColor,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "المشغل الافتراضي",
              color = textSecondary,
              fontSize = 11.sp
            )
          }
        }
      }
    }
  }
}
