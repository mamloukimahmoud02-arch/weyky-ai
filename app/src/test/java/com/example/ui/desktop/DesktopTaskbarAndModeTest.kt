package com.example.ui.desktop

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.state.WeykyUiState
import com.example.core.state.WeykyViewModel
import com.example.ui.adaptive.AdaptiveLayoutMode
import com.example.ui.adaptive.AdaptiveOrientation
import com.example.ui.adaptive.DevicePosture
import com.example.ui.adaptive.WindowSizeResolver
import com.example.ui.theme.WeykyThemeMode
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DesktopTaskbarAndModeTest {

  private lateinit var application: Application

  @Before
  fun setup() {
    application = ApplicationProvider.getApplicationContext()
    // Clear test preferences before each test run
    val prefs = application.getSharedPreferences("weyky_Appearance_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
  }

  @Test
  fun `landscape orientation triggers landscape mode while portrait stays in portrait`() {
    val landscapeProfile = WindowSizeResolver.resolve(
      widthDp = 840.dp,
      heightDp = 420.dp,
      windowOrientation = AdaptiveOrientation.LANDSCAPE
    )
    assertEquals(AdaptiveOrientation.LANDSCAPE, landscapeProfile.orientation)

    val portraitProfile = WindowSizeResolver.resolve(
      widthDp = 412.dp,
      heightDp = 892.dp,
      windowOrientation = AdaptiveOrientation.PORTRAIT
    )
    assertEquals(AdaptiveOrientation.PORTRAIT, portraitProfile.orientation)
    assertEquals(AdaptiveLayoutMode.PHONE_PORTRAIT, portraitProfile.layoutMode)
  }

  @Test
  fun `taskbar pinning adds app without duplicates and persists to preferences`() {
    val viewModel = WeykyViewModel(application)
    val testPkg = "com.example.testapp"

    // Initial state should not contain test app
    assertFalse(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))

    // Pin the app
    viewModel.pinApp(testPkg)
    assertTrue(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))

    // Verify pinned list has no duplicates when pinned again
    val countBefore = viewModel.uiState.value.pinnedAppPackageNames.size
    viewModel.pinApp(testPkg)
    assertEquals(countBefore, viewModel.uiState.value.pinnedAppPackageNames.size)

    // Verify persistence in SharedPreferences
    val prefs = application.getSharedPreferences("weyky_Appearance_prefs", Context.MODE_PRIVATE)
    val savedPackages = prefs.getString("pinned_apps_packages", "") ?: ""
    assertTrue("Preferences must contain pinned package", savedPackages.contains(testPkg))

    // Create a new ViewModel to simulate app restart and verify recovery
    val restartedViewModel = WeykyViewModel(application)
    assertTrue("Restarted ViewModel must restore pinned packages",
      restartedViewModel.uiState.value.pinnedAppPackageNames.contains(testPkg)
    )
  }

  @Test
  fun `taskbar unpin removes app and persists to preferences`() {
    val viewModel = WeykyViewModel(application)
    val testPkg = "com.example.removeapp"

    viewModel.pinApp(testPkg)
    assertTrue(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))

    // Unpin
    viewModel.unpinApp(testPkg)
    assertFalse(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))

    // Verify preferences updated
    val prefs = application.getSharedPreferences("weyky_Appearance_prefs", Context.MODE_PRIVATE)
    val savedPackages = prefs.getString("pinned_apps_packages", "") ?: ""
    assertFalse(savedPackages.contains(testPkg))
  }

  @Test
  fun `togglePinApp switches pin state accurately`() {
    val viewModel = WeykyViewModel(application)
    val testPkg = "com.example.toggleapp"

    assertFalse(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))

    // Toggle on
    viewModel.togglePinApp(testPkg)
    assertTrue(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))

    // Toggle off
    viewModel.togglePinApp(testPkg)
    assertFalse(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))
  }

  @Test
  fun `start menu toggles open and closed properly`() {
    val viewModel = WeykyViewModel(application)

    assertFalse(viewModel.uiState.value.isStartMenuOpen)

    viewModel.toggleStartMenu(true)
    assertTrue(viewModel.uiState.value.isStartMenuOpen)

    viewModel.toggleStartMenu(false)
    assertFalse(viewModel.uiState.value.isStartMenuOpen)

    // Toggle inverse
    viewModel.toggleStartMenu()
    assertTrue(viewModel.uiState.value.isStartMenuOpen)
    viewModel.toggleStartMenu()
    assertFalse(viewModel.uiState.value.isStartMenuOpen)
  }

  @Test
  fun `pinned apps state remains intact across theme changes`() {
    val viewModel = WeykyViewModel(application)
    val testPkg = "com.example.cross_theme"
    viewModel.pinApp(testPkg)

    // Switch to Tactical HUD
    viewModel.setThemeMode(WeykyThemeMode.TACTICAL)
    assertEquals(WeykyThemeMode.TACTICAL, viewModel.uiState.value.themeMode)
    assertTrue(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))

    // Switch to Modern
    viewModel.setThemeMode(WeykyThemeMode.MODERN)
    assertEquals(WeykyThemeMode.MODERN, viewModel.uiState.value.themeMode)
    assertTrue(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))

    // Switch to Minimal
    viewModel.setThemeMode(WeykyThemeMode.MINIMAL)
    assertEquals(WeykyThemeMode.MINIMAL, viewModel.uiState.value.themeMode)
    assertTrue(viewModel.uiState.value.pinnedAppPackageNames.contains(testPkg))
  }

  @Test
  fun `launchDialer executes safely without exception`() {
    val viewModel = WeykyViewModel(application)
    viewModel.launchDialer()
    val nextIntent = org.robolectric.Shadows.shadowOf(application).nextStartedActivity
    org.junit.Assert.assertNotNull(nextIntent)
    assertEquals(android.content.Intent.ACTION_DIAL, nextIntent.action)
  }

  @Test
  fun `announceBattery updates lastResponseText and speaks status`() {
    val viewModel = WeykyViewModel(application)
    viewModel.announceBattery()
    val text = viewModel.uiState.value.lastResponseText
    org.junit.Assert.assertNotNull(text)
    assertTrue("Response must contain battery percent", text!!.contains("مستوى شحن البطارية"))
  }

  @Test
  fun `announceTime updates lastResponseText and speaks time`() {
    val viewModel = WeykyViewModel(application)
    viewModel.announceTime()
    val text = viewModel.uiState.value.lastResponseText
    org.junit.Assert.assertNotNull(text)
    assertTrue("Response must contain time prompt", text!!.contains("الوقت الآن"))
  }

  @Test
  fun `announceWeather and announceLocation vocalize information to Core`() {
    val viewModel = WeykyViewModel(application)
    viewModel.announceWeather()
    val weatherText = viewModel.uiState.value.lastResponseText
    org.junit.Assert.assertNotNull(weatherText)

    viewModel.announceLocation()
    val locText = viewModel.uiState.value.lastResponseText
    org.junit.Assert.assertNotNull(locText)
  }

  @Test
  fun `launchMusicApp executes safely without crashing`() {
    val viewModel = WeykyViewModel(application)
    viewModel.launchMusicApp()
  }
}
