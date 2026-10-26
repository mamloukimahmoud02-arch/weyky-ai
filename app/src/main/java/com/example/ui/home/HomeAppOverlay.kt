package com.example.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.WeykyThemeMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.device.apps.AppMatch
import com.example.ui.adaptive.DeviceProfile
import com.example.ui.theme.JarvisHudBadge
import com.example.ui.theme.JarvisHudColors
import com.example.ui.theme.jarvisTacticalBackground

/**
 * High-performance, in-memory LRU icon cache for installed Android applications.
 * Prevents repetitive PackageManager queries and icon re-decoding during LazyVerticalGrid scrolling.
 */
object AppIconCache {
  private val iconCache = LruCache<String, ImageBitmap>(160)

  fun getIcon(context: Context, packageName: String): ImageBitmap? {
    iconCache.get(packageName)?.let { return it }
    return try {
      val pm = context.packageManager
      val drawable = pm.getApplicationIcon(packageName)
      val density = context.resources.displayMetrics.density
      val targetSizePx = (48 * density).toInt().coerceAtLeast(48)
      val bitmap = drawable.toBitmap(
        width = targetSizePx,
        height = targetSizePx,
        config = Bitmap.Config.ARGB_8888
      )
      val imageBitmap = bitmap.asImageBitmap()
      iconCache.put(packageName, imageBitmap)
      imageBitmap
    } catch (_: Exception) {
      null
    }
  }
}

/**
 * Native Android App Drawer Screen for WEYKY.
 * Replaces tall vertical cards with an adaptive, native launcher-style application icon grid:
 * - Real installed launcher icon with in-memory caching.
 * - Application name cleanly centered beneath the icon.
 * - Adaptive grid columns (4-5 on phones, 6-10 on tablets/foldables) via GridCells.Adaptive.
 * - Full search and filtering preserved.
 * - Generous, accessible touch targets (>= 48dp) with debounce protection.
 */
@Composable
fun AppSurfaceOverlay(
  apps: List<AppMatch>,
  deviceProfile: DeviceProfile,
  isLightMode: Boolean,
  backgroundColor: Color,
  borderColor: Color,
  onClose: () -> Unit,
  onLaunchApp: (AppMatch) -> Unit,
  themeMode: WeykyThemeMode = WeykyThemeMode.TACTICAL,
  modifier: Modifier = Modifier
) {
  val panelBg = JarvisHudColors.panelBg(isLightMode)
  val panelBgElevated = JarvisHudColors.panelBgElevated(isLightMode)
  val textPrimary = JarvisHudColors.textPrimary(isLightMode)
  val textSecondary = JarvisHudColors.textSecondary(isLightMode)

  var searchQuery by remember { mutableStateOf("") }
  val filteredApps = remember(searchQuery, apps) {
    if (searchQuery.isBlank()) {
      apps.sortedBy { it.label.lowercase() }
    } else {
      apps.filter {
        it.label.contains(searchQuery, ignoreCase = true) ||
          it.packageName.contains(searchQuery, ignoreCase = true)
      }.sortedBy { it.label.lowercase() }
    }
  }

  // Double-tap prevention timestamp
  var lastLaunchTimeMs by remember { mutableLongStateOf(0L) }
  val safeLaunchApp: (AppMatch) -> Unit = { app ->
    val now = System.currentTimeMillis()
    if (now - lastLaunchTimeMs > 500L) {
      lastLaunchTimeMs = now
      onLaunchApp(app)
    }
  }

  val overlayBg = when (themeMode) {
    WeykyThemeMode.TACTICAL -> backgroundColor.copy(alpha = 0.98f)
    WeykyThemeMode.MODERN -> if (isLightMode) Color(0xFAF1F5F9) else Color(0xFA040816)
    WeykyThemeMode.MINIMAL -> if (isLightMode) Color(0xFAF8FAFC) else Color(0xFA0B0F19)
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("app_surface_overlay"),
    color = overlayBg
  ) {
    val bgModifier = when (themeMode) {
      WeykyThemeMode.TACTICAL -> Modifier.jarvisTacticalBackground(backgroundColor = backgroundColor, accentColor = borderColor)
      else -> Modifier
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .then(bgModifier)
        .padding(
          horizontal = deviceProfile.contentPaddingHorizontalDp,
          vertical = deviceProfile.contentPaddingVerticalDp + 8.dp
        )
    ) {
      // Tactical App Matrix Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = when (themeMode) {
              WeykyThemeMode.TACTICAL -> "SYS.MODULES // INSTALLED APPS"
              WeykyThemeMode.MODERN -> "SYS.MODULES // APPS MATRIX"
              WeykyThemeMode.MINIMAL -> "WEYKY // التطبيقات"
            },
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = borderColor
          )
          Text(
            text = "التطبيقات المثبتة (${apps.size})",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
          )
        }
        if (themeMode == WeykyThemeMode.TACTICAL) {
          JarvisHudBadge(
            accentColor = JarvisHudColors.CrimsonAlert,
            containerColor = if (isLightMode) Color(0xFFFFE4EA) else Color(0xD91A040A),
            cutSize = 8.dp
          ) {
            IconButton(
              onClick = onClose,
              modifier = Modifier
                .size(48.dp)
                .testTag("close_app_drawer_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = JarvisHudColors.CrimsonAlert
              )
            }
          }
        } else {
          val closeShape = if (themeMode == WeykyThemeMode.MODERN) CircleShape else RoundedCornerShape(12.dp)
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(closeShape)
              .background(if (isLightMode) Color(0xFFFFE4EA) else Color(0x40EF4444))
              .border(1.2.dp, JarvisHudColors.CrimsonAlert, closeShape),
            contentAlignment = Alignment.Center
          ) {
            IconButton(
              onClick = onClose,
              modifier = Modifier
                .size(48.dp)
                .testTag("close_app_drawer_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = JarvisHudColors.CrimsonAlert
              )
            }
          }
        }
      }

      // Search Box
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = {
          Text("ابحث عن تطبيق...", color = textSecondary, fontSize = 14.sp)
        },
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = null, tint = borderColor)
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(48.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Clear", tint = textSecondary)
            }
          }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = panelBg,
          unfocusedContainerColor = panelBgElevated,
          focusedBorderColor = borderColor,
          unfocusedBorderColor = borderColor.copy(alpha = 0.35f),
          focusedTextColor = textPrimary,
          unfocusedTextColor = textPrimary
        ),
        shape = when (themeMode) {
          WeykyThemeMode.TACTICAL -> CutCornerShape(12.dp)
          WeykyThemeMode.MODERN -> RoundedCornerShape(16.dp)
          WeykyThemeMode.MINIMAL -> RoundedCornerShape(12.dp)
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("app_search_field")
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Native Launcher-Style Responsive App Grid
      if (filteredApps.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.SearchOff,
              contentDescription = null,
              tint = borderColor.copy(alpha = 0.6f),
              modifier = Modifier.size(40.dp)
            )
            Text(
              text = if (searchQuery.isNotBlank()) "لا توجد نتائج تطابق \"$searchQuery\"" else "لا توجد تطبيقات مثبتة",
              fontSize = 14.sp,
              color = textSecondary,
              textAlign = TextAlign.Center
            )
            Text(
              text = "NO MATCHING APPLICATIONS",
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              color = borderColor.copy(alpha = 0.7f),
              letterSpacing = 1.sp
            )
          }
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = if (deviceProfile.isTablet) 94.dp else 74.dp),
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("apps_list"),
          verticalArrangement = Arrangement.spacedBy(if (deviceProfile.isTablet) 16.dp else 12.dp),
          horizontalArrangement = Arrangement.spacedBy(if (deviceProfile.isTablet) 10.dp else 6.dp)
        ) {
          items(filteredApps, key = { it.packageName }) { app ->
            AppGridIconItem(
              app = app,
              isTablet = deviceProfile.isTablet,
              borderColor = borderColor,
              textPrimary = textPrimary,
              onClick = { safeLaunchApp(app) }
            )
          }
        }
      }
    }
  }
}

/**
 * Standard launcher icon grid item:
 * - App icon at 48dp x 48dp on phones, 60dp x 60dp on tablets.
 * - App label beneath the icon, centered, max 2 lines with clean ellipsis.
 * - Accessible touch target of at least 48dp.
 */
@Composable
private fun AppGridIconItem(
  app: AppMatch,
  isTablet: Boolean = false,
  borderColor: Color,
  textPrimary: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val iconBitmap = remember(app.packageName) {
    AppIconCache.getIcon(context, app.packageName)
  }
  val boxSize = if (isTablet) 64.dp else 52.dp
  val iconSize = if (isTablet) 60.dp else 48.dp

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 4.dp, vertical = 6.dp)
      .testTag("app_item_${app.packageName}"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Top
  ) {
    // Launcher Icon
    Box(
      modifier = Modifier
        .size(boxSize),
      contentAlignment = Alignment.Center
    ) {
      if (iconBitmap != null) {
        Image(
          bitmap = iconBitmap,
          contentDescription = app.label,
          modifier = Modifier
            .size(iconSize)
            .clip(RoundedCornerShape(if (isTablet) 14.dp else 10.dp))
        )
      } else {
        // Fallback for Robolectric unit tests or apps without package icon
        JarvisHudBadge(
          accentColor = borderColor,
          containerColor = borderColor.copy(alpha = 0.14f),
          cutSize = 8.dp,
          modifier = Modifier.size(iconSize)
        ) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
              text = app.label.firstOrNull()?.uppercase() ?: "?",
              fontSize = if (isTablet) 22.sp else 18.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = borderColor
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(if (isTablet) 6.dp else 4.dp))

    // App Name directly beneath icon
    Text(
      text = app.label,
      fontSize = if (isTablet) 13.5.sp else 11.5.sp,
      fontWeight = FontWeight.Normal,
      color = textPrimary,
      textAlign = TextAlign.Center,
      maxLines = 2,
      minLines = 1,
      lineHeight = if (isTablet) 16.sp else 14.sp,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth()
    )
  }
}
