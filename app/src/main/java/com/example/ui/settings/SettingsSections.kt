package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.state.WeykyUiState
import com.example.core.voice.model.ArabicVoiceCatalog
import com.example.core.voice.model.ModelInstallStatus
import com.example.core.voice.model.VoiceModelInfo
import com.example.core.voice.model.VoiceModelManager
import com.example.core.voice.model.VoiceModelType
import com.example.ui.theme.JarvisHudBadge
import com.example.ui.theme.JarvisHudCard
import com.example.ui.theme.JarvisHudColors
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import com.example.ui.theme.WeykyThemeMode
import java.io.File
import java.util.Locale

@Composable
fun SettingsSubSection(
  title: String,
  subtitle: String? = null,
  accentColor: Color,
  containerColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  themeMode: WeykyThemeMode = WeykyThemeMode.TACTICAL,
  isLight: Boolean = false,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(bottom = 6.dp)
    ) {
      Box(
        modifier = Modifier
          .size(4.dp, 14.dp)
          .clip(if (themeMode == WeykyThemeMode.TACTICAL) CutCornerShape(1.dp) else RoundedCornerShape(2.dp))
          .background(accentColor)
      )
      Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = textPrimary
      )
    }
    if (subtitle != null) {
      Text(
        text = subtitle,
        fontSize = 11.sp,
        color = textSecondary,
        modifier = Modifier.padding(start = 10.dp, bottom = 8.dp)
      )
    }
    when (themeMode) {
      WeykyThemeMode.TACTICAL -> {
        JarvisHudCard(
          accentColor = accentColor.copy(alpha = 0.45f),
          containerColor = containerColor,
          cutCornerSize = 8.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp)
          ) {
            content()
          }
        }
      }
      WeykyThemeMode.MODERN -> {
        val shape = RoundedCornerShape(14.dp)
        val modernSubBg = if (isLight) Color(0xDDFFFFFF) else Color(0x350C192E)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(modernSubBg)
            .border(1.dp, accentColor.copy(alpha = 0.35f), shape)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp)
          ) {
            content()
          }
        }
      }
      WeykyThemeMode.MINIMAL -> {
        val shape = RoundedCornerShape(10.dp)
        val minimalSubBg = if (isLight) Color(0xEAFFFFFF) else Color(0x600E1722)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(minimalSubBg)
            .border(1.dp, if (isLight) Color(0x1F000000) else Color(0xFF1E2D3D), shape)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp)
          ) {
            content()
          }
        }
      }
    }
  }
}

@Composable
fun AppearanceSettingsContent(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  onUpdateCoreScale: (Float) -> Unit,
  onSetLightMode: (Boolean) -> Unit,
  onUpdateBackgroundColor: (Long) -> Unit,
  onUpdateCardBorderColor: (Long) -> Unit,
  onSelectThemeMode: (com.example.ui.theme.WeykyThemeMode) -> Unit = {},
  onSelectDayNightMode: (com.example.ui.theme.DayNightMode) -> Unit = {},
  onSelectMinimalBgType: (com.example.ui.theme.MinimalBackgroundType) -> Unit = {},
  onSelectWallpaperPreset: (String) -> Unit = {},
  onSelectSolidColor: (Long) -> Unit = {},
  onSelectGradient: (Long, Long) -> Unit = { _, _ -> },
  onUpdateDimmingAlpha: (Float) -> Unit = {},
  onPickDeviceImage: (android.net.Uri) -> Unit = {},
  onToggleFavoriteWallpaper: (String) -> Unit = {},
  onRestoreMinimalDefault: () -> Unit = {},
  onRestoreFullDefaults: () -> Unit = {},
  onSelectModernBgPreset: (String) -> Unit = {},
  onRequestDefaultLauncher: () -> Unit = {},
  isGrouped: Boolean = false
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    ThemeSelectionSettingsContent(
      uiState = uiState,
      isLight = isLight,
      borderColor = borderColor,
      panelBg = panelBg,
      panelBgElevated = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      onSelectThemeMode = onSelectThemeMode,
      onSelectDayNightMode = onSelectDayNightMode,
      onUpdateCoreScale = onUpdateCoreScale,
      onUpdateCardBorderColor = onUpdateCardBorderColor,
      onSelectMinimalBgType = onSelectMinimalBgType,
      onSelectWallpaperPreset = onSelectWallpaperPreset,
      onSelectSolidColor = onSelectSolidColor,
      onSelectGradient = onSelectGradient,
      onUpdateDimmingAlpha = onUpdateDimmingAlpha,
      onPickDeviceImage = onPickDeviceImage,
      onToggleFavoriteWallpaper = onToggleFavoriteWallpaper,
      onRestoreMinimalDefault = onRestoreMinimalDefault,
      onRestoreFullDefaults = onRestoreFullDefaults,
      onSelectModernBgPreset = onSelectModernBgPreset
    )

    // Divider before merged Launcher section
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(borderColor.copy(alpha = 0.22f))
    )

    // Merged Launcher / Home Role Section inside Appearance & HUD
    SettingsSubSection(
      title = "وضع الواجهة الرئيسية (Launcher)",
      subtitle = "تعيين WEYKY كشاشة رئيسية افتراضية للجهاز (ROLE_HOME)",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = uiState.themeMode,
      isLight = isLight
    ) {
      LauncherSettingsContent(
        uiState = uiState,
        isLight = isLight,
        borderColor = borderColor,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        onRequestDefaultLauncher = onRequestDefaultLauncher,
        themeMode = uiState.themeMode,
        isGrouped = false
      )
    }
  }
}

@Composable
fun PowerSettingsContent(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  onTogglePowerSaving: () -> Unit,
  themeMode: WeykyThemeMode = uiState.themeMode,
  isGrouped: Boolean = false
) {
  val powerControl = @Composable {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "وضع توفير الطاقة المحسّن",
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = textPrimary
        )
        Text(
          text = "يهدئ سرعة تموجات النواة لتقليل استهلاك المعالج الرسومي وموارد الجهاز",
          fontSize = 12.sp,
          color = textSecondary
        )
      }
      Switch(
        checked = uiState.isPowerSaving,
        onCheckedChange = { onTogglePowerSaving() },
        colors = SwitchDefaults.colors(
          checkedThumbColor = JarvisHudColors.PitchBlack,
          checkedTrackColor = borderColor,
          uncheckedTrackColor = if (isLight) Color(0xFFD0DFEC) else Color(0xFF0A1E36)
        ),
        modifier = Modifier.testTag("power_saving_switch")
      )
    }
  }

  if (isGrouped) {
    SettingsSubSection(
      title = "إدارة الطاقة والموارد الرسومية",
      subtitle = "تحسين استهلاك المعالج ومعدل تحديث إطارات النواة الحية",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = themeMode,
      isLight = isLight,
      content = powerControl
    )
  } else {
    powerControl()
  }
}

@Composable
fun TelemetrySettingsContent(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  themeMode: WeykyThemeMode = uiState.themeMode,
  isGrouped: Boolean = false
) {
  val c = uiState.contextSnapshot
  val telemetryContent = @Composable {
    Text(
      text = "الحالة الحالية: ${c.state.displayName} (${(c.confidence * 100).toInt()}% ثقة)",
      fontSize = 13.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = borderColor
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "الميلان الأمامي: ${String.format(Locale.US, "%.1f°", c.pitchDegrees)} | الجانبي: ${String.format(Locale.US, "%.1f°", c.rollDegrees)}",
      fontSize = 12.sp,
      fontFamily = FontFamily.Monospace,
      color = textSecondary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "حساس التقارب: ${if (c.isProximityNear) "قريب (مغطى)" else "بعيد (مكشوف)"}",
      fontSize = 12.sp,
      fontFamily = FontFamily.Monospace,
      color = textSecondary
    )
  }

  if (isGrouped) {
    SettingsSubSection(
      title = "بيانات المستشعرات والوعي البيئي",
      subtitle = "رصد حي ومباشر للميلان الفيزيائي والتقارب ونشاط المستخدم",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = themeMode,
      isLight = isLight,
      content = telemetryContent
    )
  } else {
    telemetryContent()
  }
}

@Composable
fun LauncherSettingsContent(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  onRequestDefaultLauncher: () -> Unit,
  themeMode: WeykyThemeMode = uiState.themeMode,
  isGrouped: Boolean = false
) {
  val isDefault = uiState.launcherStatus.isDefaultLauncher
  val launcherContent = @Composable {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "التطبيق الرئيسي الافتراضي (ROLE_HOME)",
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = textPrimary
        )
        Text(
          text = if (isDefault) {
            "WEYKY يعمل حالياً كشاشة رئيسية افتراضية لهاتفك"
          } else {
            "يمكنك تعيين WEYKY كواجهة رئيسية عبر إعدادات Android الرسمية"
          },
          fontSize = 12.sp,
          color = if (isDefault) borderColor else textSecondary
        )
      }
      if (!isDefault) {
        val buttonShape = if (themeMode == WeykyThemeMode.TACTICAL) {
          CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp)
        } else {
          RoundedCornerShape(10.dp)
        }
        Button(
          onClick = onRequestDefaultLauncher,
          colors = ButtonDefaults.buttonColors(containerColor = borderColor),
          shape = buttonShape,
          modifier = Modifier.testTag("request_default_launcher_button")
        ) {
          Text("تعيين كافتراضي", fontSize = 12.sp, color = JarvisHudColors.PitchBlack, fontWeight = FontWeight.Bold)
        }
      } else {
        if (themeMode == WeykyThemeMode.TACTICAL) {
          JarvisHudBadge(
            accentColor = borderColor,
            containerColor = borderColor.copy(alpha = 0.2f),
            cutSize = 7.dp,
            modifier = Modifier.testTag("default_launcher_badge")
          ) {
            Text(
              text = "مفعل كافتراضي",
              fontSize = 12.sp,
              color = borderColor,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        } else {
          val badgeShape = RoundedCornerShape(8.dp)
          Box(
            modifier = Modifier
              .clip(badgeShape)
              .background(borderColor.copy(alpha = 0.15f))
              .border(1.dp, borderColor.copy(alpha = 0.4f), badgeShape)
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("default_launcher_badge")
          ) {
            Text(
              text = "مفعل كافتراضي",
              fontSize = 12.sp,
              color = borderColor,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }

  if (isGrouped) {
    SettingsSubSection(
      title = "دور المشغل الافتراضي لنظام التشغيل",
      subtitle = "تحويل الهاتف إلى تجربة WEYKY الحية كواجهة رئيسية دائمة",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = themeMode,
      isLight = isLight,
      content = launcherContent
    )
  } else {
    launcherContent()
  }
}

@Composable
fun VoiceSettingsContent(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  context: Context,
  modelZipPickerLauncher: ActivityResultLauncher<String>,
  onRequestFileInstall: (VoiceModelInfo) -> Unit,
  onDownloadModel: (VoiceModelInfo) -> Unit,
  onInstallModel: (VoiceModelInfo) -> Unit,
  onDeleteModel: (VoiceModelInfo) -> Unit,
  onSelectActiveTtsModel: (VoiceModelInfo) -> Unit,
  onSetHybridTtsEnabled: (Boolean) -> Unit,
  onSetCloudTtsVoice: (String) -> Unit,
  onTestVoice: () -> Unit,
  onTestVoiceWithKey: (String, String) -> Unit,
  themeMode: WeykyThemeMode = uiState.themeMode,
  isGrouped: Boolean = false
) {
  val voiceStatus = uiState.voiceEngineStatus

  val enginesTelemetry = @Composable {
    Text(
      text = "محرك الاستماع النشط (STT): ${voiceStatus?.sttEngine?.name ?: "جاري الفحص..."}",
      fontSize = 13.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = borderColor
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = "محرك النطق (TTS): ${voiceStatus?.activeTtsPackage ?: "غير محدد"} (دعم العربية: ${if (voiceStatus?.isArabicTtsSupported == true) "مدعوم" else "غير مدعوم"})",
      fontSize = 12.sp,
      color = textSecondary
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = "حالة الاتصال: ${if (voiceStatus?.isOnline == true) "متصل بالإنترنت" else "غير متصل (أوفلاين)"}",
      fontSize = 12.sp,
      color = if (voiceStatus?.isOnline == true) borderColor else JarvisHudColors.TacticalAmber
    )
  }

  val hybridSpeechContent = @Composable {
    JarvisHudCard(
      accentColor = if (uiState.isHybridTtsEnabled) borderColor else textSecondary,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      cutCornerSize = 8.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .testTag("hybrid_tts_settings_card")
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "المنظومة الهجينة (Hybrid Neural Speech)",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = borderColor
            )
            Text(
              text = "نطق عصبي فائق الجودة (أونلاين) + احتياطي محلي فوري (أوفلاين)",
              fontSize = 11.sp,
              color = textSecondary
            )
          }
          Switch(
            checked = uiState.isHybridTtsEnabled,
            onCheckedChange = onSetHybridTtsEnabled,
            colors = SwitchDefaults.colors(
              checkedThumbColor = borderColor,
              checkedTrackColor = borderColor.copy(alpha = 0.3f)
            ),
            modifier = Modifier.testTag("hybrid_tts_switch")
          )
        }

        if (uiState.isHybridTtsEnabled) {
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "قائمة الأصوات العربية الفصيحة المتاحة:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = borderColor
          )
          Text(
            text = "اختر الصوت المعتمد لنطق WEYKY التفاعلي واستمع لتجربته:",
            fontSize = 11.sp,
            color = textSecondary
          )
          Spacer(modifier = Modifier.height(8.dp))

          Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("arabic_voices_list")
          ) {
            ArabicVoiceCatalog.VOICES.forEach { voice ->
              val isSelected = uiState.cloudTtsVoiceName == voice.voiceKey
              JarvisHudCard(
                accentColor = if (isSelected) borderColor else borderColor.copy(alpha = 0.28f),
                containerColor = if (isSelected) JarvisHudColors.panelBgElevated(isLight) else panelBg,
                cutCornerSize = 8.dp,
                onClick = { onSetCloudTtsVoice(voice.voiceKey) },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("voice_option_${voice.voiceKey}")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Text(
                      text = voice.flag,
                      fontSize = 20.sp
                    )
                    Column {
                      Text(
                        text = "${voice.flag} ${voice.name} (${voice.country}) - ${voice.gender}",
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) borderColor else textPrimary
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = if (isSelected) "✓ الصوت المعتمد حالياً" else "انقر للاختيار والتعيين",
                        fontSize = 11.sp,
                        color = if (isSelected) JarvisHudColors.NeonGreen else textSecondary
                      )
                    }
                  }

                  IconButton(
                    onClick = {
                      onSetCloudTtsVoice(voice.voiceKey)
                      onTestVoiceWithKey(voice.voiceKey, voice.previewText)
                    },
                    modifier = Modifier
                      .size(42.dp)
                      .testTag("test_voice_${voice.voiceKey}")
                  ) {
                    Box(
                      modifier = Modifier
                        .size(32.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (isSelected) borderColor.copy(alpha = 0.16f) else Color(0x22888888))
                        .border(1.dp, if (isSelected) borderColor else Color(0x44888888), androidx.compose.foundation.shape.CircleShape),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "استماع ${voice.name}",
                        tint = if (isSelected) borderColor else textSecondary,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = onTestVoice,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("test_hybrid_voice_button"),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp),
            border = BorderStroke(1.dp, borderColor)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = borderColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("تجربة نطق الصوت الحالي الآن", fontSize = 12.sp, color = borderColor, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }

  val offlineCatalogContent = @Composable {
    val hasInstalledTts = VoiceModelManager.CATALOG.any {
      it.type == VoiceModelType.TTS &&
        uiState.voiceModelStatuses[it.id] is ModelInstallStatus.Installed
    }

    if (hasInstalledTts) {
      JarvisHudCard(
        accentColor = JarvisHudColors.NeonGreen,
        containerColor = JarvisHudColors.panelBgElevated(isLight),
        cutCornerSize = 8.dp,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = JarvisHudColors.NeonGreen,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "محرك النطق العصبي المحلي (Piper VITS) نشط ويعمل محلياً بالكامل دون الحاجة لخدمات أندرويد أو جوجل.",
            fontSize = 11.sp,
            color = textPrimary,
            fontWeight = FontWeight.Medium
          )
        }
      }
    } else {
      JarvisHudCard(
        accentColor = JarvisHudColors.TacticalAmber,
        containerColor = JarvisHudColors.panelBgElevated(isLight),
        cutCornerSize = 8.dp,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          Text(
            text = "تنبيه للأجهزة التي لا تدعم خدمات جوجل:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisHudColors.TacticalAmber
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "محرك النظام الافتراضي في الأجهزة غير المزودة بخدمات جوجل قد لا يدعم النطق العربي تلقائياً. صُمم WEYKY بمحرك عصبي مدمج (Piper ONNX)؛ يرجى تنزيل نموذج «كريم» أو «النسائي» أدناه ليعمل النطق الفصيح محلياً بالكامل وبدون إنترنت.",
            fontSize = 11.sp,
            color = textSecondary,
            lineHeight = 16.sp
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    VoiceModelManager.CATALOG.forEach { modelInfo ->
      val status = uiState.voiceModelStatuses[modelInfo.id] ?: ModelInstallStatus.NotInstalled
      val engineBadgeText = if (modelInfo.type == VoiceModelType.TTS) {
        "PIPER VITS // OFFLINE NEURAL TTS"
      } else {
        "VOSK ASR // OFFLINE SPEECH RECOGNITION"
      }
      val engineBadgeColor = if (modelInfo.type == VoiceModelType.TTS) {
        JarvisHudColors.MagentaPulse
      } else {
        borderColor
      }
      JarvisHudCard(
        accentColor = borderColor,
        containerColor = JarvisHudColors.panelBgElevated(isLight),
        cutCornerSize = 10.dp,
        bracketLength = 10.dp,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 5.dp)
          .testTag("voice_model_card_${modelInfo.id}")
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = engineBadgeText,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = engineBadgeColor
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = modelInfo.nameArabic,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
              )
              Text(
                text = "${modelInfo.sizeBytes / (1024 * 1024)} ميجابايت • ${modelInfo.license}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = textSecondary
              )
              if (modelInfo.type == VoiceModelType.TTS) {
                Text(
                  text = if (modelInfo.isFemaleVoice) "صوت نسائي (إماراتي / فصيح)" else "صوت رجالي (كريم - الأردن / فصيح)",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = borderColor
                )
              }
              val statusLabel = when (status) {
                is ModelInstallStatus.Installed -> "الحالة: مثبت وجاهز للعمل بدون إنترنت"
                is ModelInstallStatus.Downloaded -> "الحالة: تم التنزيل • جاهز للتثبيت الآن"
                is ModelInstallStatus.Downloading -> "الحالة: جاري التنزيل (${status.progressPercent}%)..."
                is ModelInstallStatus.Extracting -> "الحالة: جاري التثبيت وفك الضغط..."
                is ModelInstallStatus.Error -> "الحالة: غير مثبت"
                else -> "الحالة: غير مثبت"
              }
              Text(
                text = statusLabel,
                fontSize = 11.sp,
                color = when (status) {
                  is ModelInstallStatus.Installed -> JarvisHudColors.NeonGreen
                  is ModelInstallStatus.Downloaded -> borderColor
                  is ModelInstallStatus.Extracting -> JarvisHudColors.TacticalAmber
                  else -> textSecondary
                }
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            when (status) {
              is ModelInstallStatus.Installed -> {
                val isTts = modelInfo.type == VoiceModelType.TTS
                val isTtsActive = isTts && (uiState.activeTtsModelId == modelInfo.id || uiState.activeTtsModelId.isEmpty())

                if (isTts && !isTtsActive) {
                  Button(
                    onClick = { onSelectActiveTtsModel(modelInfo) },
                    colors = ButtonDefaults.buttonColors(containerColor = borderColor),
                    shape = CutCornerShape(6.dp),
                    modifier = Modifier
                      .weight(1f)
                      .testTag("activate_model_${modelInfo.id}")
                  ) {
                    Text("تفعيل هذا الصوت", fontSize = 11.sp, color = JarvisHudColors.PitchBlack, fontWeight = FontWeight.Bold)
                  }
                } else {
                  JarvisHudBadge(
                    accentColor = JarvisHudColors.NeonGreen,
                    containerColor = JarvisHudColors.NeonGreen.copy(alpha = 0.14f),
                    cutSize = 6.dp,
                    modifier = Modifier.weight(1f)
                  ) {
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = if (isTts) "✓ مفعل حالياً للنطق" else "✓ مثبت ومفعل",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisHudColors.NeonGreen
                      )
                    }
                  }
                }
                Button(
                  onClick = { onDeleteModel(modelInfo) },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF2A54)),
                  shape = CutCornerShape(6.dp),
                  modifier = Modifier.testTag("delete_model_${modelInfo.id}")
                ) {
                  Text("حذف", fontSize = 11.sp, color = JarvisHudColors.CrimsonAlert, fontWeight = FontWeight.Bold)
                }
              }
              is ModelInstallStatus.Downloading -> {
                Text(
                  text = "جاري التنزيل: ${status.progressPercent}% (${status.bytesDownloaded / (1024 * 1024)} / ${status.totalBytes / (1024 * 1024)} MB)",
                  fontSize = 12.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  color = borderColor
                )
              }
              is ModelInstallStatus.Extracting -> {
                Text(
                  text = "جاري التثبيت وفك الضغط...",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = JarvisHudColors.TacticalAmber
                )
              }
              is ModelInstallStatus.Downloaded -> {
                Button(
                  onClick = { onInstallModel(modelInfo) },
                  colors = ButtonDefaults.buttonColors(containerColor = JarvisHudColors.NeonGreen),
                  shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("install_model_${modelInfo.id}")
                ) {
                  Text("تثبيت النموذج الآن", fontSize = 12.sp, color = JarvisHudColors.PitchBlack, fontWeight = FontWeight.Bold)
                }
                Button(
                  onClick = { onDownloadModel(modelInfo) },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLight) Color(0xFFDCE8F4) else Color(0xFF0A2239)
                  ),
                  shape = CutCornerShape(6.dp),
                  modifier = Modifier.testTag("redownload_model_${modelInfo.id}")
                ) {
                  Text("إعادة تنزيل", fontSize = 11.sp, color = borderColor)
                }
              }
              else -> {
                Button(
                  onClick = { onDownloadModel(modelInfo) },
                  colors = ButtonDefaults.buttonColors(containerColor = borderColor),
                  shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp, topEnd = 2.dp, bottomStart = 2.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("download_model_${modelInfo.id}")
                ) {
                  Text("تنزيل", fontSize = 12.sp, color = JarvisHudColors.PitchBlack, fontWeight = FontWeight.Bold)
                }

                Button(
                  onClick = {
                    val downloadedZip = File(
                      File(context.filesDir, "voice_models"),
                      "${modelInfo.id}.zip"
                    )
                    if (downloadedZip.exists() && downloadedZip.length() > 1024L) {
                      onInstallModel(modelInfo)
                    } else {
                      onRequestFileInstall(modelInfo)
                      try {
                        modelZipPickerLauncher.launch("application/zip")
                      } catch (_: Exception) {
                        onInstallModel(modelInfo)
                      }
                    }
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisHudColors.NeonGreen
                  ),
                  shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp, topEnd = 2.dp, bottomStart = 2.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("install_model_${modelInfo.id}")
                ) {
                  Text("تثبيت", fontSize = 12.sp, color = JarvisHudColors.PitchBlack, fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          if (status is ModelInstallStatus.Error) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "تنبيه: ${status.message}",
              fontSize = 11.sp,
              color = JarvisHudColors.CrimsonAlert
            )
          }
        }
      }
    }
  }

  if (isGrouped) {
    SettingsSubSection(
      title = "محركات الصوت النشطة والاتصال",
      subtitle = "فحص حالة محرك الاستماع (STT) والنطق (TTS) والاتصال الشبكي",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = themeMode,
      isLight = isLight,
      content = enginesTelemetry
    )
    SettingsSubSection(
      title = "المنظومة الهجينة (Hybrid Neural Speech)",
      subtitle = "أصوات عصبية فصيحة سحابية فائقة الواقعية مع استماع تجريبي",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = themeMode,
      isLight = isLight,
      content = hybridSpeechContent
    )
    SettingsSubSection(
      title = "النماذج العصبية المحلية أوفلاين (Piper & Vosk)",
      subtitle = "تشغيل النطق والاستماع بالكامل على الهاتف بدون إنترنت أو خدمات خارجية",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = themeMode,
      isLight = isLight,
      content = offlineCatalogContent
    )
  } else {
    enginesTelemetry()
    Spacer(modifier = Modifier.height(10.dp))
    hybridSpeechContent()
    Spacer(modifier = Modifier.height(10.dp))
    offlineCatalogContent()
  }
}

@Composable
fun PermissionsSettingsContent(
  isNotificationAccessGranted: Boolean,
  isLight: Boolean,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  context: Context,
  themeMode: WeykyThemeMode = WeykyThemeMode.TACTICAL,
  isGrouped: Boolean = false,
  uiState: WeykyUiState = WeykyUiState(),
  onToggleNotificationApp: (String) -> Unit = {},
  onSetAllNotificationApps: (Boolean) -> Unit = {}
) {
  var appSearchQuery by rememberSaveable { mutableStateOf("") }
  var showAllAppsExpanded by rememberSaveable { mutableStateOf(false) }

  val buttonShape = if (themeMode == WeykyThemeMode.TACTICAL) {
    CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp)
  } else {
    RoundedCornerShape(10.dp)
  }

  val permissionsContent = @Composable {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "الوصول إلى الإشعارات",
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = textPrimary
        )
        Text(
          text = if (isNotificationAccessGranted) "مفعل (يمكن لـ WEYKY قراءة الإشعارات)" else "غير مفعل (اضغط للفتح)",
          fontSize = 12.sp,
          color = if (isNotificationAccessGranted) borderColor else JarvisHudColors.TacticalAmber
        )
      }
      Button(
        onClick = {
          try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
          } catch (_: Exception) {}
        },
        colors = ButtonDefaults.buttonColors(containerColor = if (isLight) Color(0xFFDCE8F4) else Color(0xFF071A30)),
        shape = buttonShape,
        modifier = Modifier.testTag("open_notification_settings_button")
      ) {
        Text("الإعدادات", fontSize = 12.sp, color = borderColor)
      }
    }
  }

  val notificationAppsFilterContent = @Composable {
    val allApps = uiState.installedApps
    val disabledPkgs = uiState.disabledNotificationPackages
    val enabledCount = (allApps.size - disabledPkgs.count { pkg -> allApps.any { it.packageName == pkg } }).coerceAtLeast(0)

    val filteredApps = remember(appSearchQuery, allApps) {
      if (appSearchQuery.isBlank()) {
        allApps
      } else {
        val q = appSearchQuery.trim().lowercase(Locale.ROOT)
        allApps.filter {
          it.label.lowercase(Locale.ROOT).contains(q) ||
            it.packageName.lowercase(Locale.ROOT).contains(q) ||
            it.normalizedLabel.lowercase(Locale.ROOT).contains(q)
        }
      }
    }

    val displayedApps = if (showAllAppsExpanded || appSearchQuery.isNotBlank()) {
      filteredApps
    } else {
      filteredApps.take(8)
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("notification_apps_filter_section"),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Info note emphasizing zero impact on Android OS
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(borderColor.copy(alpha = 0.10f))
          .border(1.dp, borderColor.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
          .padding(10.dp)
      ) {
        Text(
          text = "اختر التطبيقات التي ستظهر إشعاراتها في بطاقة الإشعارات داخل WEYKY فقط. إيقاف أي تطبيق هنا يخفي تنبيهاته من بطاقة WEYKY دون أي تأثير على إشعارات نظام أندرويد.",
          fontSize = 11.5.sp,
          color = textSecondary,
          lineHeight = 16.sp
        )
      }

      // Summary Row + Select All / Deselect All
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "التطبيقات المفعّلة: $enabledCount من ${allApps.size}",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = borderColor
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = { onSetAllNotificationApps(true) },
            shape = buttonShape,
            border = BorderStroke(1.dp, borderColor.copy(alpha = 0.6f)),
            modifier = Modifier
              .height(34.dp)
              .testTag("enable_all_notif_apps_button")
          ) {
            Text("تفعيل الكل", fontSize = 11.sp, color = borderColor, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = { onSetAllNotificationApps(false) },
            shape = buttonShape,
            border = BorderStroke(1.dp, textSecondary.copy(alpha = 0.45f)),
            modifier = Modifier
              .height(34.dp)
              .testTag("disable_all_notif_apps_button")
          ) {
            Text("إيقاف الكل", fontSize = 11.sp, color = textSecondary)
          }
        }
      }

      // Search Field
      OutlinedTextField(
        value = appSearchQuery,
        onValueChange = { appSearchQuery = it },
        placeholder = {
          Text(
            text = "ابحث عن تطبيق بالاسم...",
            fontSize = 12.sp,
            color = textSecondary.copy(alpha = 0.7f)
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
        trailingIcon = {
          if (appSearchQuery.isNotEmpty()) {
            IconButton(onClick = { appSearchQuery = "" }) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear",
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = borderColor,
          unfocusedBorderColor = borderColor.copy(alpha = 0.35f),
          focusedTextColor = textPrimary,
          unfocusedTextColor = textPrimary,
          cursorColor = borderColor
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("notification_apps_search_input")
      )

      if (allApps.isEmpty()) {
        Text(
          text = "لا توجد تطبيقات مثبتة متاحة للعرض حالياً.",
          fontSize = 12.sp,
          color = textSecondary,
          modifier = Modifier.padding(vertical = 6.dp)
        )
      } else if (filteredApps.isEmpty()) {
        Text(
          text = "لا توجد تطبيقات مطابقة لبحثك.",
          fontSize = 12.sp,
          color = textSecondary,
          modifier = Modifier.padding(vertical = 6.dp)
        )
      } else {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          displayedApps.forEach { app ->
            val isAllowed = !disabledPkgs.contains(app.packageName)
            val itemShape = if (themeMode == WeykyThemeMode.TACTICAL) {
              CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp)
            } else {
              RoundedCornerShape(10.dp)
            }

            val iconBitmap = remember(app.packageName) {
              try {
                val drawable = context.packageManager.getApplicationIcon(app.packageName)
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                  drawable.bitmap.asImageBitmap()
                } else {
                  drawable.toBitmap(width = 72, height = 72).asImageBitmap()
                }
              } catch (_: Exception) {
                null
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(itemShape)
                .background(
                  if (isAllowed) borderColor.copy(alpha = if (isLight) 0.10f else 0.12f)
                  else Color(0x18000000)
                )
                .border(
                  width = 1.dp,
                  color = if (isAllowed) borderColor.copy(alpha = 0.45f) else textSecondary.copy(alpha = 0.22f),
                  shape = itemShape
                )
                .clickable { onToggleNotificationApp(app.packageName) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("notif_app_toggle_${app.packageName}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
              ) {
                if (iconBitmap != null) {
                  Image(
                    bitmap = iconBitmap,
                    contentDescription = app.label,
                    modifier = Modifier
                      .size(34.dp)
                      .clip(RoundedCornerShape(8.dp))
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .size(34.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(borderColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Notifications,
                      contentDescription = app.label,
                      tint = borderColor,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = app.label,
                    fontSize = 13.sp,
                    fontWeight = if (isAllowed) FontWeight.Bold else FontWeight.Medium,
                    color = if (isAllowed) textPrimary else textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = if (isAllowed) "مسموح بظهور إشعاراته في بطاقة WEYKY" else "مخفي من بطاقة الإشعارات (داخل التطبيق فقط)",
                    fontSize = 10.5.sp,
                    color = if (isAllowed) borderColor else textSecondary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }

              Switch(
                checked = isAllowed,
                onCheckedChange = { onToggleNotificationApp(app.packageName) },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = JarvisHudColors.PitchBlack,
                  checkedTrackColor = borderColor,
                  uncheckedTrackColor = if (isLight) Color(0xFFD0DFEC) else Color(0xFF0A1E36)
                )
              )
            }
          }

          if (filteredApps.size > 8 && appSearchQuery.isBlank()) {
            OutlinedButton(
              onClick = { showAllAppsExpanded = !showAllAppsExpanded },
              shape = buttonShape,
              border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("toggle_expand_notif_apps_button")
            ) {
              Text(
                text = if (showAllAppsExpanded) "طي القائمة (عرض أقل)" else "عرض كافة التطبيقات (${filteredApps.size})",
                fontSize = 12.sp,
                color = borderColor,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }
  }

  if (isGrouped) {
    SettingsSubSection(
      title = "صلاحيات الوصول والوعي بالإشعارات",
      subtitle = "السماح للنواة بتحليل إشعارات التطبيقات والتفاعل معها صوتياً",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = themeMode,
      isLight = isLight,
      content = permissionsContent
    )
    SettingsSubSection(
      title = "اختيار تطبيقات بطاقة الإشعارات",
      subtitle = "تحديد التطبيقات التي تظهر إشعاراتها في بطاقة الإشعارات داخل WEYKY دون المساس بإشعارات النظام",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = themeMode,
      isLight = isLight,
      content = notificationAppsFilterContent
    )
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      permissionsContent()
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(borderColor.copy(alpha = 0.22f))
      )
      Text(
        text = "اختيار تطبيقات بطاقة الإشعارات:",
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        color = textPrimary
      )
      notificationAppsFilterContent()
    }
  }
}

@Composable
fun AboutSettingsContent(
  isLight: Boolean,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  themeMode: WeykyThemeMode = WeykyThemeMode.TACTICAL,
  isGrouped: Boolean = false
) {
  val aboutContent = @Composable {
    val coreTag = when (themeMode) {
      WeykyThemeMode.TACTICAL -> "WEYKY 1.1 // TACTICAL CORE"
      WeykyThemeMode.MODERN -> "WEYKY 1.1 // MODERN LUXURY"
      WeykyThemeMode.MINIMAL -> "WEYKY 1.1 // MINIMAL SLATE"
    }
    Text(
      text = coreTag,
      fontSize = 15.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = textPrimary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "\"One living intelligent core for the phone.\"",
      fontSize = 13.sp,
      fontFamily = FontFamily.Monospace,
      color = borderColor
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "واجهة محيطة ذكية حقيقية تعتمد على بيانات حساسات ونظام Android الفعلية ومحركات الصوت العصبية.",
      fontSize = 12.sp,
      color = textSecondary,
      lineHeight = 18.sp
    )
  }

  if (isGrouped) {
    SettingsSubSection(
      title = "معلومات الإصدار والنواة الذكية",
      subtitle = "مواصفات النواة الحية والبيئة المحيطة",
      accentColor = borderColor,
      containerColor = JarvisHudColors.panelBgElevated(isLight),
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      themeMode = themeMode,
      isLight = isLight,
      content = aboutContent
    )
  } else {
    aboutContent()
  }
}
