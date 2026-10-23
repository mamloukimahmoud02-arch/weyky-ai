package com.example.device.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * Manages detection and user requests for the Android default Home / Launcher role (ROLE_HOME).
 * Strictly lifecycle-aware and event-driven: zero background services, zero continuous polling loops.
 */
class LauncherRoleManager(private val context: Context) {
  private val packageManager: PackageManager = context.packageManager

  @Volatile
  private var isDefaultOverrideForTesting: Boolean? = null

  companion object {
    private const val TAG = "WEYKY_LAUNCHER"
  }

  /**
   * Evaluates whether WEYKY is currently the active default Home / Launcher application.
   * Leverages Android RoleManager on Android 10+ (API 29+) with fallback verification via PackageManager.
   */
  fun checkLauncherStatus(): LauncherStatus {
    isDefaultOverrideForTesting?.let { override ->
      Log.d(TAG, "[LAUNCHER] STATUS: (Testing Override) isDefault=$override, canRequestRole=true")
      if (override) {
        Log.d(TAG, "[LAUNCHER] ROLE_HELD")
      } else {
        Log.d(TAG, "[LAUNCHER] ROLE_NOT_HELD")
      }
      return LauncherStatus(isDefaultLauncher = override, canRequestRole = true)
    }

    var isRoleHeld = false
    var canRequestRole = false

    // 1. Android 10+ (API 29+) RoleManager inspection
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
      if (roleManager != null) {
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
          canRequestRole = true
          isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
      }
    } else {
      canRequestRole = true
    }

    // 2. PackageManager verification for default Home intent resolution
    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
      addCategory(Intent.CATEGORY_HOME)
    }

    val defaultResolveInfo = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(
          homeIntent,
          PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )
      } else {
        @Suppress("DEPRECATION")
        packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
      }
    } catch (e: Exception) {
      Log.e(TAG, "[LAUNCHER] Error resolving default home activity via PackageManager", e)
      null
    }

    val isPackageDefault = defaultResolveInfo?.activityInfo?.packageName == context.packageName
    val isDefault = isRoleHeld || isPackageDefault

    // Diagnostic logging per specification
    if (isDefault) {
      Log.d(TAG, "[LAUNCHER] ROLE_HELD")
    } else {
      Log.d(TAG, "[LAUNCHER] ROLE_NOT_HELD")
    }
    Log.d(TAG, "[LAUNCHER] STATUS: isDefault=$isDefault, canRequestRole=$canRequestRole")

    return LauncherStatus(
      isDefaultLauncher = isDefault,
      canRequestRole = canRequestRole
    )
  }

  /**
   * Generates the Android system intent to request the Home role.
   * Does NOT alter settings silently or force the role; Android system UI presents the choice to the user.
   */
  fun createRequestRoleIntent(): Intent? {
    Log.d(TAG, "[LAUNCHER] ROLE_REQUEST_START")
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
          roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
          Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
      } else {
        Intent(Settings.ACTION_HOME_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "[LAUNCHER] Failed to create role request intent", e)
      null
    }
  }

  fun onRoleRequestResult(resultCode: Int) {
    Log.d(TAG, "[LAUNCHER] ROLE_REQUEST_RESULT: resultCode=$resultCode")
  }

  fun onHomeResumed() {
    Log.d(TAG, "[LAUNCHER] HOME_RESUMED")
  }

  fun onHomePaused() {
    Log.d(TAG, "[LAUNCHER] HOME_PAUSED")
  }

  @VisibleForTesting
  fun setOverrideForTesting(isDefault: Boolean?) {
    isDefaultOverrideForTesting = isDefault
  }
}
