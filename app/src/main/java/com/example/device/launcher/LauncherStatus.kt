package com.example.device.launcher

/**
 * Encapsulates the Android default launcher (ROLE_HOME) state for WEYKY.
 * @property isDefaultLauncher True if WEYKY is currently the user's default Home/Launcher application.
 * @property canRequestRole True if the device platform supports requesting the ROLE_HOME via system UI.
 */
data class LauncherStatus(
  val isDefaultLauncher: Boolean = false,
  val canRequestRole: Boolean = true
)
