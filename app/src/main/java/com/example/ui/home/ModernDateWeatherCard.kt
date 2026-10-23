package com.example.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.state.WeykyUiState
import com.example.ui.home.weather.RealtimeSkyViewport
import com.example.ui.theme.WeykyAdaptiveCard

/**
 * Modern Combined Date & Weather Card in Portrait Mode.
 * Fully represents the REAL-TIME appearance of the sky (sun/moon position, hour atmosphere,
 * dynamic drifting cloud masses, precipitation particles) instead of a silent static icon.
 */
@Composable
fun ModernCombinedDateWeatherCard(
  uiState: WeykyUiState,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val weather = uiState.weatherSnapshot
  val tempVal = weather?.temperatureCelsius
  val tempText = if (weather != null && weather.isAvailable && tempVal != null) {
    "${tempVal.toInt()}°"
  } else {
    "18°"
  }
  val condition = weather?.condition?.ifBlank { null } ?: "غائم جزئياً"
  val locationName = weather?.locationName?.takeIf { it.isNotBlank() } ?: "جاري تحديد المنطقة..."

  val primaryDate = uiState.currentDateString.ifBlank { "الخميس، 22 أكتوبر" }
  val englishDate = uiState.currentEnglishDateString.ifBlank { "THURSDAY, 22 OCT" }

  WeykyAdaptiveCard(
    uiState = uiState,
    accentColor = borderColor,
    onClick = { onQuerySubmit("ما حالة الطقس وتاريخ اليوم؟") },
    modifier = modifier
      .fillMaxWidth()
      .testTag("modern_combined_date_weather_card")
  ) {
    // Natural Arabic RTL Alignment
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // 1. DATE SECTION (Right side in Arabic RTL)
        Row(
          modifier = Modifier.weight(1.05f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Squircle Calendar Icon Container
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0x2800D2FF)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.DateRange,
              contentDescription = "التاريخ",
              tint = Color(0xFF00D2FF),
              modifier = Modifier.size(22.dp)
            )
          }

          Column(verticalArrangement = Arrangement.Center) {
            Text(
              text = primaryDate,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = englishDate.uppercase(),
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              letterSpacing = 0.8.sp,
              color = Color(0xFF8EB2D6),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // Vertical Soft Glass Divider
        Box(
          modifier = Modifier
            .padding(horizontal = 10.dp)
            .width(1.dp)
            .height(42.dp)
            .background(borderColor.copy(alpha = 0.25f))
        )

        // 2. REAL-TIME SKY WEATHER SECTION (Left side in Arabic RTL)
        Row(
          modifier = Modifier.weight(1.15f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Real-time living sky window representing actual sky appearance
          RealtimeSkyViewport(
            weather = weather,
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(12.dp),
            borderColor = borderColor.copy(alpha = 0.55f)
          )

          Column(verticalArrangement = Arrangement.Center) {
            Text(
              text = tempText,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = textPrimary,
              letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
              text = condition,
              fontSize = 10.5.sp,
              color = Color(0xFF8EB2D6),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "الموقع",
                tint = Color(0xFF00D2FF),
                modifier = Modifier.size(11.dp)
              )
              Text(
                text = locationName,
                fontSize = 9.5.sp,
                color = Color(0xFF8EB2D6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }
}
