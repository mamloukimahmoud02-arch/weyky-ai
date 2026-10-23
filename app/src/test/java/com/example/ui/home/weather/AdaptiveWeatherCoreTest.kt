package com.example.ui.home.weather

import com.example.core.state.WeykyUiState
import com.example.device.battery.BatterySnapshot
import com.example.device.battery.ChargingState
import com.example.device.weather.WeatherSnapshot
import com.example.rendering.visual_state.CoreVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWeatherCoreTest {

  @Test
  fun `core is strictly eligible for weather effects in IDLE state only`() {
    val baseIdleState = WeykyUiState(
      visualState = CoreVisualState.IDLE,
      isVoiceListening = false,
      isSpeakingAloud = false,
      isMusicPlaying = false
    )
    assertTrue(isCoreEligibleForWeatherEffect(baseIdleState, isCharging = false))
  }

  @Test
  fun `core immediately disables weather effects when entering any functional state`() {
    val baseIdleState = WeykyUiState(
      visualState = CoreVisualState.IDLE,
      isVoiceListening = false,
      isSpeakingAloud = false,
      isMusicPlaying = false
    )

    // Voice listening active
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(isVoiceListening = true), isCharging = false))

    // Assistant speaking aloud
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(isSpeakingAloud = true), isCharging = false))

    // Music playback active
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(isMusicPlaying = true), isCharging = false))

    // Device charging active
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState, isCharging = true))

    // Non-IDLE visual states
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(visualState = CoreVisualState.LISTENING), isCharging = false))
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(visualState = CoreVisualState.PROCESSING), isCharging = false))
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(visualState = CoreVisualState.RESPONDING), isCharging = false))
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(visualState = CoreVisualState.SUCCESS), isCharging = false))
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(visualState = CoreVisualState.ERROR), isCharging = false))
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(visualState = CoreVisualState.CALL), isCharging = false))
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(visualState = CoreVisualState.MUSIC), isCharging = false))
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(visualState = CoreVisualState.CHARGING), isCharging = false))
    assertFalse(isCoreEligibleForWeatherEffect(baseIdleState.copy(visualState = CoreVisualState.NOTIFICATION), isCharging = false))
  }

  @Test
  fun `weather condition resolver maps Arabic and standard weather conditions accurately`() {
    // Null weather snapshot
    assertEquals(AdaptiveWeatherType.UNKNOWN, resolveAdaptiveWeatherType(null))

    // Clear
    val clearSnapshot = WeatherSnapshot(temperatureCelsius = 24f, condition = "صافٍ مشمس", weatherCode = 0)
    assertEquals(AdaptiveWeatherType.CLEAR, resolveAdaptiveWeatherType(clearSnapshot))

    // Partly cloudy
    val partlyCloudySnapshot = WeatherSnapshot(temperatureCelsius = 21f, condition = "غائم جزئياً", weatherCode = 2)
    assertEquals(AdaptiveWeatherType.PARTLY_CLOUDY, resolveAdaptiveWeatherType(partlyCloudySnapshot))

    // Overcast / Cloudy
    val cloudySnapshot = WeatherSnapshot(temperatureCelsius = 19f, condition = "غائم كلياً ملبد", weatherCode = 3)
    assertEquals(AdaptiveWeatherType.CLOUDY, resolveAdaptiveWeatherType(cloudySnapshot))

    // Rain
    val rainSnapshot = WeatherSnapshot(temperatureCelsius = 17f, condition = "أمطار وزخات متفرقة", weatherCode = 61, precipitationMm = 4.2f)
    assertEquals(AdaptiveWeatherType.RAIN, resolveAdaptiveWeatherType(rainSnapshot))

    // Thunderstorm
    val thunderSnapshot = WeatherSnapshot(temperatureCelsius = 16f, condition = "عاصفة رعدية ممطرة", weatherCode = 95)
    assertEquals(AdaptiveWeatherType.THUNDERSTORM, resolveAdaptiveWeatherType(thunderSnapshot))

    // Blank or unknown
    val blankSnapshot = WeatherSnapshot(temperatureCelsius = null, condition = "", weatherCode = null)
    assertEquals(AdaptiveWeatherType.UNKNOWN, resolveAdaptiveWeatherType(blankSnapshot))
  }

  @Test
  fun `cached weather snapshot is preserved and reused smoothly`() {
    val cachedWeather = WeatherSnapshot(
      temperatureCelsius = 22.5f,
      condition = "صحو صافٍ",
      weatherCode = 0
    )
    val stateWithWeather = WeykyUiState(
      weatherSnapshot = cachedWeather,
      visualState = CoreVisualState.IDLE
    )

    // Verify it resolves without needing extra network calls
    assertEquals(AdaptiveWeatherType.CLEAR, resolveAdaptiveWeatherType(stateWithWeather.weatherSnapshot))
    assertTrue(isCoreEligibleForWeatherEffect(stateWithWeather, isCharging = false))
  }

  @Test
  fun `parseTimeToMinutes accurately parses 24h and 12h formats`() {
    assertEquals(6 * 60 + 30, parseTimeToMinutes("06:30"))
    assertEquals(18 * 60 + 45, parseTimeToMinutes("18:45"))
    assertEquals(14 * 60 + 15, parseTimeToMinutes("2:15 PM"))
    assertEquals(7 * 60, parseTimeToMinutes("7:00 AM"))
    assertEquals(null, parseTimeToMinutes(""))
    assertEquals(null, parseTimeToMinutes(null))
  }

  @Test
  fun `hasCloudsInWeather returns false for clear sky and true for cloudy or rainy conditions`() {
    val clearSky = WeatherSnapshot(temperatureCelsius = 25f, condition = "صافٍ مشمس", weatherCode = 0, cloudCoverPercent = 5)
    assertFalse(hasCloudsInWeather(clearSky))

    val partlyCloudy = WeatherSnapshot(temperatureCelsius = 22f, condition = "غائم جزئياً", weatherCode = 2, cloudCoverPercent = 45)
    assertTrue(hasCloudsInWeather(partlyCloudy))

    val overcast = WeatherSnapshot(temperatureCelsius = 19f, condition = "غائم كلياً", weatherCode = 3, cloudCoverPercent = 90)
    assertTrue(hasCloudsInWeather(overcast))

    val rainy = WeatherSnapshot(temperatureCelsius = 16f, condition = "ممطر", weatherCode = 61, cloudCoverPercent = 85)
    assertTrue(hasCloudsInWeather(rainy))
  }

  @Test
  fun `calculateLunarAstronomy resolves all known lunar phases and appearance window`() {
    val referenceNewMoonMs = 947182440000L
    val synodicMonthMs = (29.530588853 * 86400000.0).toLong()
    val weather = WeatherSnapshot(
      temperatureCelsius = 20f,
      condition = "صافٍ",
      sunriseToday = "06:00",
      sunsetToday = "18:30"
    )

    // 1. Full Moon (cyclePhase = 0.5) at 23:00 night -> visible white full moon
    val fullMoonCal = java.util.Calendar.getInstance().apply {
      timeInMillis = referenceNewMoonMs + (synodicMonthMs / 2)
      set(java.util.Calendar.HOUR_OF_DAY, 23)
      set(java.util.Calendar.MINUTE, 0)
    }
    val fullMoonAstro = calculateLunarAstronomy(weather, fullMoonCal)
    assertEquals(LunarPhaseType.FULL_MOON, fullMoonAstro.phaseType)
    assertTrue(fullMoonAstro.illuminationFraction > 0.95f)
    assertTrue(fullMoonAstro.isMoonInAppearanceWindow)
    assertTrue(fullMoonAstro.isMoonVisibleAtNight)

    // 2. First Quarter (cyclePhase = 0.25) at 21:00 night -> visible half moon
    val firstQuarterCal = java.util.Calendar.getInstance().apply {
      timeInMillis = referenceNewMoonMs + (synodicMonthMs / 4)
      set(java.util.Calendar.HOUR_OF_DAY, 21)
      set(java.util.Calendar.MINUTE, 0)
    }
    val fqAstro = calculateLunarAstronomy(weather, firstQuarterCal)
    assertEquals(LunarPhaseType.FIRST_QUARTER, fqAstro.phaseType)
    assertTrue(fqAstro.illuminationFraction in 0.40f..0.60f)
    assertTrue(fqAstro.isMoonInAppearanceWindow)

    // 3. New Moon (cyclePhase = 0.0) -> not visible at night
    val newMoonCal = java.util.Calendar.getInstance().apply {
      timeInMillis = referenceNewMoonMs
      set(java.util.Calendar.HOUR_OF_DAY, 23)
      set(java.util.Calendar.MINUTE, 0)
    }
    val newMoonAstro = calculateLunarAstronomy(weather, newMoonCal)
    assertEquals(LunarPhaseType.NEW_MOON, newMoonAstro.phaseType)
    assertFalse(newMoonAstro.isMoonInAppearanceWindow)
    assertFalse(newMoonAstro.isMoonVisibleAtNight)
  }
}
