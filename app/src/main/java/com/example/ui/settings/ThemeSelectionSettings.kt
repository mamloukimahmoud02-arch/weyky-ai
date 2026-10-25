package com.example.ui.settings

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.state.WeykyUiState
import com.example.ui.theme.DayNightMode
import com.example.ui.theme.JarvisHudBadge
import com.example.ui.theme.JarvisHudCard
import com.example.ui.theme.JarvisHudColors
import com.example.ui.theme.MinimalBackgroundType
import com.example.ui.theme.MinimalDimmingPresets
import com.example.ui.theme.MinimalGradientPresets
import com.example.ui.theme.MinimalSolidColorPresets
import com.example.ui.theme.OfflineWallpaperCatalog
import com.example.ui.theme.OfflineWallpaperPreset
import com.example.ui.theme.WallpaperCategory
import com.example.ui.theme.WeykyThemeMode
import java.io.File

/**
 * Dedicated Theme Selection and Customization Component in WEYKY Settings.
 * Implements Section 2, 3, 4, 5 of Master Blueprint.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSelectionSettingsContent(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  onSelectThemeMode: (WeykyThemeMode) -> Unit,
  onSelectDayNightMode: (DayNightMode) -> Unit,
  onUpdateCoreScale: (Float) -> Unit,
  onUpdateCardBorderColor: (Long) -> Unit,
  onSelectMinimalBgType: (MinimalBackgroundType) -> Unit,
  onSelectWallpaperPreset: (String) -> Unit,
  onSelectSolidColor: (Long) -> Unit,
  onSelectGradient: (Long, Long) -> Unit,
  onUpdateDimmingAlpha: (Float) -> Unit,
  onPickDeviceImage: (Uri) -> Unit,
  onToggleFavoriteWallpaper: (String) -> Unit,
  onRestoreMinimalDefault: () -> Unit,
  onRestoreFullDefaults: () -> Unit
) {
  val context = LocalContext.current
  var showResetConfirmationDialog by remember { mutableStateOf(false) }

  // Photo Picker Launcher for Minimal Background Image
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      onPickDeviceImage(uri)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. SECTION: The Three Big Theme Cards
    Text(
      text = "اختيار نمط مظهر WEYKY (Theme Mode):",
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      color = textPrimary
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      WeykyThemeMode.entries.forEach { mode ->
        val isActive = uiState.themeMode == mode
        ThemeSelectionCard(
          mode = mode,
          isActive = isActive,
          borderColor = borderColor,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onClick = { onSelectThemeMode(mode) },
          modifier = Modifier.weight(1f)
        )
      }
    }

    // 2. CONTEXTUAL SETTINGS DEPENDING ON ACTIVE THEME
    when (uiState.themeMode) {
      WeykyThemeMode.TACTICAL -> {
        TacticalThemeSettingsSection(
          uiState = uiState,
          isLight = isLight,
          borderColor = borderColor,
          panelBg = panelBg,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onUpdateCardBorderColor = onUpdateCardBorderColor,
          onUpdateCoreScale = onUpdateCoreScale
        )
      }

      WeykyThemeMode.MODERN -> {
        ModernThemeSettingsSection(
          uiState = uiState,
          isLight = isLight,
          borderColor = borderColor,
          panelBg = panelBg,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onSelectDayNightMode = onSelectDayNightMode,
          onUpdateCoreScale = onUpdateCoreScale
        )
      }

      WeykyThemeMode.MINIMAL -> {
        MinimalThemeSettingsSection(
          uiState = uiState,
          isLight = isLight,
          borderColor = borderColor,
          panelBg = panelBg,
          panelBgElevated = panelBgElevated,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onSelectMinimalBgType = onSelectMinimalBgType,
          onSelectWallpaperPreset = onSelectWallpaperPreset,
          onSelectSolidColor = onSelectSolidColor,
          onSelectGradient = onSelectGradient,
          onUpdateDimmingAlpha = onUpdateDimmingAlpha,
          onLaunchPhotoPicker = { photoPickerLauncher.launch("image/*") },
          onToggleFavoriteWallpaper = onToggleFavoriteWallpaper,
          onRestoreMinimalDefault = onRestoreMinimalDefault,
          onRequestFullReset = { showResetConfirmationDialog = true },
          onUpdateCoreScale = onUpdateCoreScale
        )
      }
    }
  }

  // Confirmation Alert Dialog for Full Theme Reset
  if (showResetConfirmationDialog) {
    AlertDialog(
      onDismissRequest = { showResetConfirmationDialog = false },
      title = {
        Text(
          text = "استعادة كاملة للمظهر الافتراضي؟",
          fontWeight = FontWeight.Bold,
          color = textPrimary
        )
      },
      text = {
        Text(
          text = "سيتم استعادة النمط التكتيكي الافتراضي (Tactical) وخلفيته السوداء مع الحفاظ على مفضلة الخلفيات دون حذفها.",
          fontSize = 13.sp,
          color = textSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRestoreFullDefaults()
            showResetConfirmationDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = JarvisHudColors.CrimsonAlert)
        ) {
          Text("تأكيد الاستعادة", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetConfirmationDialog = false }) {
          Text("إلغاء", color = textSecondary)
        }
      },
      containerColor = if (isLight) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    )
  }
}

/**
 * Big interactive theme card for Tactical, Modern, and Minimal.
 */
@Composable
private fun ThemeSelectionCard(
  mode: WeykyThemeMode,
  isActive: Boolean,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val cardShape = RoundedCornerShape(12.dp)
  val activeBorderColor = if (isActive) borderColor else Color(0x357CA5B8)

  Column(
    modifier = modifier
      .clip(cardShape)
      .background(if (isActive) borderColor.copy(alpha = 0.12f) else Color(0x22111827))
      .border(
        width = if (isActive) 2.dp else 1.dp,
        color = activeBorderColor,
        shape = cardShape
      )
      .clickable(onClick = onClick)
      .padding(8.dp)
      .testTag("theme_card_${mode.id}"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Visual Graphic Preview Box
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(
          when (mode) {
            WeykyThemeMode.TACTICAL -> Color.Black
            WeykyThemeMode.MODERN -> Color(0xFF040816)
            WeykyThemeMode.MINIMAL -> Color(0xFF0F172A)
          }
        )
        .drawBehind {
          when (mode) {
            WeykyThemeMode.TACTICAL -> {
              // Tactical HUD wireframe preview
              val cx = size.width / 2f
              val cy = size.height / 2f
              drawCircle(
                color = Color(0xFF00ADB5),
                radius = 16.dp.toPx(),
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
              )
              drawLine(
                color = Color(0x4400ADB5),
                start = Offset(4.dp.toPx(), 4.dp.toPx()),
                end = Offset(4.dp.toPx(), size.height - 4.dp.toPx()),
                strokeWidth = 1f
              )
              drawLine(
                color = Color(0x4400ADB5),
                start = Offset(size.width - 4.dp.toPx(), 4.dp.toPx()),
                end = Offset(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                strokeWidth = 1f
              )
            }
            WeykyThemeMode.MODERN -> {
              // Modern soft radial glowing aura preview
              val cx = size.width / 2f
              val cy = size.height / 2f
              drawCircle(
                brush = Brush.radialGradient(
                  colors = listOf(Color(0xFF00D2FF), Color(0x400A84FF), Color.Transparent),
                  center = Offset(cx, cy),
                  radius = 32.dp.toPx()
                ),
                radius = 32.dp.toPx(),
                center = Offset(cx, cy)
              )
              drawCircle(
                color = Color(0xFF00D2FF),
                radius = 14.dp.toPx(),
                center = Offset(cx, cy)
              )
            }
            WeykyThemeMode.MINIMAL -> {
              // Minimal sleek gradient preview
              drawRect(
                brush = Brush.verticalGradient(
                  colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                )
              )
              drawCircle(
                color = Color(0x66FFFFFF),
                radius = 12.dp.toPx(),
                center = Offset(size.width / 2f, size.height / 2f)
              )
            }
          }
        },
      contentAlignment = Alignment.TopEnd
    ) {
      if (isActive) {
        Box(
          modifier = Modifier
            .padding(4.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(borderColor),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Active",
            tint = Color.Black,
            modifier = Modifier.size(12.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = mode.titleEn,
      fontSize = 13.sp,
      fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
      color = if (isActive) borderColor else textPrimary,
      textAlign = TextAlign.Center
    )
    Text(
      text = when (mode) {
        WeykyThemeMode.TACTICAL -> "تكتيكي HUD"
        WeykyThemeMode.MODERN -> "عصري سينمائي"
        WeykyThemeMode.MINIMAL -> "مبسط نقي"
      },
      fontSize = 10.sp,
      color = textSecondary,
      textAlign = TextAlign.Center,
      maxLines = 1
    )
  }
}

/**
 * Settings section specific to [WeykyThemeMode.TACTICAL].
 */
@Composable
private fun TacticalThemeSettingsSection(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  onUpdateCardBorderColor: (Long) -> Unit,
  onUpdateCoreScale: (Float) -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Info Banner: Fixed black background per Section 2
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0x1800ADB5))
        .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        .padding(10.dp)
    ) {
      Text(
        text = "• نمط Tactical: خلفية سوداء فاحمة ثابتة مع خطوط إطارات HUD التكتيكية. تخصيص الخلفية غير متاح في هذا النمط للحفاظ على الطابع التكتيكي.",
        fontSize = 11.5.sp,
        color = textSecondary,
        lineHeight = 16.sp
      )
    }

    // Border color presets
    Text(
      text = "لون إطارات HUD التكتيكية:",
      fontSize = 12.5.sp,
      fontWeight = FontWeight.SemiBold,
      color = textPrimary
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      JarvisHudColors.BorderPresets.forEach { (label, hexValue) ->
        val isSelected = uiState.cardBorderColorHex == hexValue
        val presetColor = Color(hexValue)
        val swatchShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp)
        Box(
          modifier = Modifier
            .clip(swatchShape)
            .background(if (isLight) Color(0xFFEAF2F8) else Color(0xCC030A14))
            .border(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) presetColor else presetColor.copy(alpha = 0.4f),
              shape = swatchShape
            )
            .clickable { onUpdateCardBorderColor(hexValue) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .background(presetColor)
            )
            Text(
              text = label,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) borderColor else textPrimary
            )
          }
        }
      }
    }

    // Core Scale Slider
    CoreScaleSettingSlider(
      coreScale = uiState.coreScale,
      borderColor = borderColor,
      textPrimary = textPrimary,
      onUpdateCoreScale = onUpdateCoreScale
    )
  }
}

/**
 * Settings section specific to [WeykyThemeMode.MODERN].
 */
@Composable
private fun ModernThemeSettingsSection(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  onSelectDayNightMode: (DayNightMode) -> Unit,
  onUpdateCoreScale: (Float) -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Info Banner: Dynamic lighting per Section 3
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0x1800D2FF))
        .border(1.dp, Color(0x3300D2FF), RoundedCornerShape(8.dp))
        .padding(10.dp)
    ) {
      Text(
        text = "• نمط Modern: إضاءة سينمائية واسعة متمركزة خلف النواة تنتقل بسلاسة مع تغير الحالات (خمول، استماع، تحدث، موسيقى، شحن) وبطاقات زجاجية عصرية.",
        fontSize = 11.5.sp,
        color = textSecondary,
        lineHeight = 16.sp
      )
    }

    // Day/Night Mode Selector per Section 3
    Text(
      text = "الوضع النهاري والليلي (Day / Night Mode):",
      fontSize = 12.5.sp,
      fontWeight = FontWeight.SemiBold,
      color = textPrimary
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      DayNightMode.entries.forEach { mode ->
        val isSelected = uiState.dayNightMode == mode
        val modeShape = RoundedCornerShape(8.dp)

        Box(
          modifier = Modifier
            .weight(1f)
            .clip(modeShape)
            .background(if (isSelected) borderColor.copy(alpha = 0.16f) else panelBg)
            .border(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) borderColor else textSecondary.copy(alpha = 0.35f),
              shape = modeShape
            )
            .clickable { onSelectDayNightMode(mode) }
            .padding(vertical = 10.dp, horizontal = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = when (mode) {
                DayNightMode.AUTO -> Icons.Default.AutoAwesome
                DayNightMode.DARK -> Icons.Default.DarkMode
                DayNightMode.LIGHT -> Icons.Default.LightMode
              },
              contentDescription = mode.titleAr,
              tint = if (isSelected) borderColor else textSecondary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = when (mode) {
                DayNightMode.AUTO -> "تلقائي"
                DayNightMode.DARK -> "ليلي"
                DayNightMode.LIGHT -> "نهاري"
              },
              fontSize = 12.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) textPrimary else textSecondary
            )
          }
        }
      }
    }

    // Core Scale Slider
    CoreScaleSettingSlider(
      coreScale = uiState.coreScale,
      borderColor = borderColor,
      textPrimary = textPrimary,
      onUpdateCoreScale = onUpdateCoreScale
    )
  }
}

/**
 * Settings section specific to [WeykyThemeMode.MINIMAL].
 * Implements Section 4: Full Background Customization (Device Image, Offline Wallpapers, Solid, Gradient, Dimming, Favorites, Restore).
 */
@Composable
private fun MinimalThemeSettingsSection(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  onSelectMinimalBgType: (MinimalBackgroundType) -> Unit,
  onSelectWallpaperPreset: (String) -> Unit,
  onSelectSolidColor: (Long) -> Unit,
  onSelectGradient: (Long, Long) -> Unit,
  onUpdateDimmingAlpha: (Float) -> Unit,
  onLaunchPhotoPicker: () -> Unit,
  onToggleFavoriteWallpaper: (String) -> Unit,
  onRestoreMinimalDefault: () -> Unit,
  onRequestFullReset: () -> Unit,
  onUpdateCoreScale: (Float) -> Unit
) {
  val context = LocalContext.current
  var selectedWallpaperCategory by rememberSaveable { mutableStateOf(WallpaperCategory.COSMIC.id) }
  var showFavoritesOnly by rememberSaveable { mutableStateOf(false) }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Text(
      text = "تخصيص خلفية النمط المبسط (Minimal):",
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      color = textPrimary
    )

    // Sub-Tabs for Minimal Background Type
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      MinimalBackgroundType.entries.forEach { bgType ->
        val isSelected = uiState.minimalBgType == bgType
        val tabShape = RoundedCornerShape(8.dp)

        Box(
          modifier = Modifier
            .clip(tabShape)
            .background(if (isSelected) borderColor.copy(alpha = 0.18f) else panelBg)
            .border(
              width = if (isSelected) 1.5.dp else 1.dp,
              color = if (isSelected) borderColor else textSecondary.copy(alpha = 0.3f),
              shape = tabShape
            )
            .clickable { onSelectMinimalBgType(bgType) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = when (bgType) {
                MinimalBackgroundType.DEFAULT -> Icons.Default.Wallpaper
                MinimalBackgroundType.IMAGE -> Icons.Default.AddPhotoAlternate
                MinimalBackgroundType.WALLPAPER_PRESET -> Icons.Default.ColorLens
                MinimalBackgroundType.SOLID_COLOR -> Icons.Default.ColorLens
                MinimalBackgroundType.GRADIENT -> Icons.Default.Gradient
              },
              contentDescription = null,
              tint = if (isSelected) borderColor else textSecondary,
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = bgType.titleAr,
              fontSize = 11.5.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) textPrimary else textSecondary
            )
          }
        }
      }
    }

    // 1. DEVICE IMAGE PICKER CONTENT
    if (uiState.minimalBgType == MinimalBackgroundType.IMAGE) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(panelBg)
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "اختيار صورة من معرض الجهاز:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
          )
          Button(
            onClick = onLaunchPhotoPicker,
            colors = ButtonDefaults.buttonColors(containerColor = borderColor)
          ) {
            Icon(
              imageVector = Icons.Default.AddPhotoAlternate,
              contentDescription = null,
              tint = Color.Black,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("تحديد صورة", color = Color.Black, fontSize = 12.sp)
          }
        }

        // Live preview of the chosen image
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
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(130.dp)
              .clip(RoundedCornerShape(8.dp))
              .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
          ) {
            Image(
              bitmap = bitmap.asImageBitmap(),
              contentDescription = "Selected Wallpaper",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
            Box(
              modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "معاينة مباشرة (محفوظة محليًا)",
                fontSize = 10.sp,
                color = Color.White
              )
            }
          }
        } else {
          Text(
            text = "لم يتم تحديد صورة بعد. اضغط على 'تحديد صورة' لاختيار صورة من جهازك.",
            fontSize = 11.sp,
            color = textSecondary
          )
        }
      }
    }

    // 2. OFFLINE WALLPAPERS CATALOG CONTENT
    if (uiState.minimalBgType == MinimalBackgroundType.WALLPAPER_PRESET) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Category Selector & Favorites Filter
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Favorites toggle chip
          val favSelected = showFavoritesOnly
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(if (favSelected) Color(0xFFFF2A54).copy(alpha = 0.2f) else panelBg)
              .border(1.dp, if (favSelected) Color(0xFFFF2A54) else textSecondary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
              .clickable { showFavoritesOnly = true }
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF2A54), modifier = Modifier.size(13.dp))
              Text("المفضلة (${uiState.favoriteWallpaperIds.size})", fontSize = 11.sp, color = textPrimary)
            }
          }

          // Category Chips
          WallpaperCategory.entries.forEach { cat ->
            val catSelected = !showFavoritesOnly && selectedWallpaperCategory == cat.id
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (catSelected) borderColor.copy(alpha = 0.2f) else panelBg)
                .border(1.dp, if (catSelected) borderColor else textSecondary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
              .clickable {
                showFavoritesOnly = false
                selectedWallpaperCategory = cat.id
              }
              .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = cat.titleAr,
                fontSize = 11.sp,
                fontWeight = if (catSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (catSelected) borderColor else textSecondary
              )
            }
          }
        }

        // Wallpaper Cards Grid (FlowRow)
        val filteredPresets = if (showFavoritesOnly) {
          OfflineWallpaperCatalog.presets.filter { uiState.favoriteWallpaperIds.contains(it.id) }
        } else {
          OfflineWallpaperCatalog.presets.filter { it.category.id == selectedWallpaperCategory }
        }

        if (filteredPresets.isEmpty()) {
          Text(
            text = if (showFavoritesOnly) "لا توجد خلفيات مضافة للمفضلة حاليًا." else "لا توجد خلفيات في هذا القسم.",
            fontSize = 11.5.sp,
            color = textSecondary,
            modifier = Modifier.padding(vertical = 12.dp)
          )
        } else {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            filteredPresets.forEach { preset ->
              val isCurrent = uiState.minimalPresetId == preset.id
              val isFav = uiState.favoriteWallpaperIds.contains(preset.id)

              Column(
                modifier = Modifier
                  .width(110.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(panelBg)
                  .border(
                    width = if (isCurrent) 2.dp else 1.dp,
                    color = if (isCurrent) borderColor else Color(0x337CA5B8),
                    shape = RoundedCornerShape(8.dp)
                  )
                  .clickable { onSelectWallpaperPreset(preset.id) }
                  .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                // Preview Thumbnail (Image or Procedural)
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                ) {
                  if (preset.drawableResId != null) {
                    Image(
                      painter = painterResource(id = preset.drawableResId),
                      contentDescription = preset.titleAr,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()
                    )
                  } else {
                    Box(
                      modifier = Modifier
                        .fillMaxSize()
                        .drawBehind { preset.drawBackground(this) }
                    )
                  }
                  // Favorite Heart Button
                  IconButton(
                    onClick = { onToggleFavoriteWallpaper(preset.id) },
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .size(26.dp)
                  ) {
                    Icon(
                      imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                      contentDescription = "Favorite",
                      tint = if (isFav) Color(0xFFFF2A54) else Color.White.copy(alpha = 0.8f),
                      modifier = Modifier.size(16.dp)
                    )
                  }
                  if (isCurrent) {
                    Box(
                      modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(borderColor),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(10.dp))
                    }
                  }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = preset.titleAr,
                  fontSize = 10.5.sp,
                  fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                  color = if (isCurrent) borderColor else textPrimary,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
    }

    // 3. SOLID COLOR CONTENT
    if (uiState.minimalBgType == MinimalBackgroundType.SOLID_COLOR) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "اختر لون الخلفية الثابت:",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = textPrimary
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          MinimalSolidColorPresets.options.forEach { (label, hex) ->
            val isSelected = uiState.minimalSolidColorHex == hex
            val color = Color(hex)
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(
                  width = if (isSelected) 2.dp else 1.dp,
                  color = if (isSelected) borderColor else Color(0x44FFFFFF),
                  shape = RoundedCornerShape(8.dp)
                )
                .clickable { onSelectSolidColor(hex) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = label,
                  fontSize = 11.sp,
                  color = if ((color.red + color.green + color.blue) / 3f > 0.5f) Color.Black else Color.White,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (isSelected) {
                  Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = borderColor, modifier = Modifier.size(12.dp))
                }
              }
            }
          }
        }
      }
    }

    // 4. CUSTOM GRADIENT CONTENT
    if (uiState.minimalBgType == MinimalBackgroundType.GRADIENT) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "اختر تركيبة التدرج اللوني:",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = textPrimary
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          MinimalGradientPresets.pairs.forEach { (title, startHex, endHex) ->
            val isSelected = uiState.minimalGradientStartHex == startHex && uiState.minimalGradientEndHex == endHex
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                  Brush.linearGradient(
                    colors = listOf(Color(startHex), Color(endHex))
                  )
                )
                .border(
                  width = if (isSelected) 2.dp else 1.dp,
                  color = if (isSelected) borderColor else Color(0x33FFFFFF),
                  shape = RoundedCornerShape(8.dp)
                )
                .clickable { onSelectGradient(startHex, endHex) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = title,
                  fontSize = 11.sp,
                  color = Color.White,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (isSelected) {
                  Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = borderColor, modifier = Modifier.size(12.dp))
                }
              }
            }
          }
        }
      }
    }

    // 5. BACKGROUND DIMMING SCRIM SETTINGS (Section 4)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(panelBg)
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = "درجة تعتيم الخلفية (Dimming Scrim): ${(uiState.minimalDimmingAlpha * 100).toInt()}%",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = textPrimary
      )

      // Preset Buttons: Light, Medium, Dark
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(
          "خفيف (20%)" to MinimalDimmingPresets.LIGHT,
          "متوسط (45%)" to MinimalDimmingPresets.MEDIUM,
          "داكن (70%)" to MinimalDimmingPresets.DARK
        ).forEach { (label, alpha) ->
          val isSelected = kotlin.math.abs(uiState.minimalDimmingAlpha - alpha) < 0.05f
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSelected) borderColor.copy(alpha = 0.2f) else panelBgElevated)
              .border(1.dp, if (isSelected) borderColor else textSecondary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
              .clickable { onUpdateDimmingAlpha(alpha) }
              .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) borderColor else textSecondary
            )
          }
        }
      }

      // Fine adjustment slider
      Slider(
        value = uiState.minimalDimmingAlpha,
        onValueChange = onUpdateDimmingAlpha,
        valueRange = 0.0f..0.90f,
        colors = SliderDefaults.colors(
          thumbColor = borderColor,
          activeTrackColor = borderColor,
          inactiveTrackColor = textSecondary.copy(alpha = 0.3f)
        )
      )
    }

    // 6. RESTORE BUTTONS (Section 4)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Restore Minimal Default Background Only
      OutlinedButton(
        onClick = onRestoreMinimalDefault,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary),
        border = BorderStroke(1.dp, textSecondary.copy(alpha = 0.4f))
      ) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("استعادة خلفية Minimal", fontSize = 11.sp, maxLines = 1)
      }

      // Restore Full System Defaults with Confirmation
      Button(
        onClick = onRequestFullReset,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(containerColor = panelBgElevated)
      ) {
        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = JarvisHudColors.CrimsonAlert, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("استعادة كامل المظهر", fontSize = 11.sp, color = textPrimary, maxLines = 1)
      }
    }

    // Core Scale Slider
    CoreScaleSettingSlider(
      coreScale = uiState.coreScale,
      borderColor = borderColor,
      textPrimary = textPrimary,
      onUpdateCoreScale = onUpdateCoreScale
    )
  }
}

/**
 * Shared Core Scale Slider with default 0.44 constraint preserved.
 */
@Composable
private fun CoreScaleSettingSlider(
  coreScale: Float,
  borderColor: Color,
  textPrimary: Color,
  onUpdateCoreScale: (Float) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 4.dp)
  ) {
    Text(
      text = "حجم النواة في الشاشة (Core Scale): ${(coreScale * 100).toInt()}% (الافتراضي: 44%)",
      fontSize = 12.5.sp,
      fontFamily = FontFamily.Monospace,
      color = textPrimary
    )
    Slider(
      value = coreScale,
      onValueChange = onUpdateCoreScale,
      valueRange = 0.30f..0.60f,
      colors = SliderDefaults.colors(
        thumbColor = borderColor,
        activeTrackColor = borderColor,
        inactiveTrackColor = borderColor.copy(alpha = 0.25f)
      ),
      modifier = Modifier.testTag("core_scale_slider")
    )
  }
}
