package com.example.device.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class DeviceLocation(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float? = null,
  val timestampMillis: Long = System.currentTimeMillis(),
  val resolvedLocationName: String? = null
)

sealed interface LocationResult {
  data class Success(val location: DeviceLocation) : LocationResult
  object PermissionRequired : LocationResult
  object LocationDisabledOrUnavailable : LocationResult
}

class LocationProvider(private val context: Context) {
  private val locationManager =
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

  fun hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  fun getCurrentLocation(): LocationResult {
    if (!hasLocationPermission()) {
      return LocationResult.PermissionRequired
    }

    val lm = locationManager ?: return LocationResult.LocationDisabledOrUnavailable

    val providers = listOf(
      LocationManager.GPS_PROVIDER,
      LocationManager.FUSED_PROVIDER,
      LocationManager.NETWORK_PROVIDER,
      LocationManager.PASSIVE_PROVIDER
    )

    var bestLocation: Location? = null
    val now = System.currentTimeMillis()

    for (provider in providers) {
      try {
        if (lm.isProviderEnabled(provider)) {
          val loc = lm.getLastKnownLocation(provider)
          if (loc != null && isBetterLocation(loc, bestLocation, now)) {
            bestLocation = loc
          }
        }
      } catch (_: SecurityException) {
        return LocationResult.PermissionRequired
      } catch (_: Exception) {}
    }

    // If no recent or accurate cached location is found, request an active fix on API 30+
    val isStale = bestLocation == null || (now - bestLocation.time) > 15 * 60 * 1000L
    if (isStale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val activeProvider = when {
        runCatching { lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false) ->
          LocationManager.FUSED_PROVIDER
        runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
          LocationManager.GPS_PROVIDER
        runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
          LocationManager.NETWORK_PROVIDER
        else -> null
      }

      if (activeProvider != null) {
        try {
          val latch = CountDownLatch(1)
          var freshLoc: Location? = null
          val cancelSignal = CancellationSignal()
          lm.getCurrentLocation(
            activeProvider,
            cancelSignal,
            context.mainExecutor
          ) { loc ->
            freshLoc = loc
            latch.countDown()
          }
          latch.await(2500L, TimeUnit.MILLISECONDS)
          cancelSignal.cancel()
          if (freshLoc != null && isBetterLocation(freshLoc!!, bestLocation, now)) {
            bestLocation = freshLoc
          }
        } catch (_: SecurityException) {
          return LocationResult.PermissionRequired
        } catch (_: Exception) {}
      }
    }

    return if (bestLocation != null) {
      val localAreaName = resolveLocalGeocoderName(bestLocation.latitude, bestLocation.longitude)
      LocationResult.Success(
        DeviceLocation(
          latitude = bestLocation.latitude,
          longitude = bestLocation.longitude,
          accuracyMeters = if (bestLocation.hasAccuracy()) bestLocation.accuracy else null,
          timestampMillis = bestLocation.time,
          resolvedLocationName = localAreaName
        )
      )
    } else {
      LocationResult.LocationDisabledOrUnavailable
    }
  }

  /**
   * Evaluates both accuracy (meters) and freshness so a coarse cell-tower location
   * never overrides a precise village/city GPS fix.
   */
  private fun isBetterLocation(candidate: Location, currentBest: Location?, nowMillis: Long): Boolean {
    if (currentBest == null) return true

    val timeDelta = candidate.time - currentBest.time
    val isSignificantlyNewer = timeDelta > 10 * 60 * 1000L
    val isSignificantlyOlder = timeDelta < -10 * 60 * 1000L
    val isNewer = timeDelta > 0L

    if (isSignificantlyNewer) return true
    if (isSignificantlyOlder) return false

    val accuracyDelta = (candidate.accuracy - currentBest.accuracy)
    val isLessAccurate = accuracyDelta > 0f
    val isMoreAccurate = accuracyDelta < 0f
    val isSignificantlyLessAccurate = accuracyDelta > 200f

    return when {
      isMoreAccurate -> true
      isNewer && !isLessAccurate -> true
      isNewer && !isSignificantlyLessAccurate -> true
      else -> false
    }
  }

  /**
   * Resolves the exact geographical area (القرية / الحي / البلدية / المدينة + الولاية)
   * using Android's on-device Geocoder in Arabic.
   */
  @Suppress("DEPRECATION")
  private fun resolveLocalGeocoderName(lat: Double, lon: Double): String? {
    if (!Geocoder.isPresent()) return null
    return try {
      val geocoder = Geocoder(context, Locale("ar"))
      val addresses = geocoder.getFromLocation(lat, lon, 3)
      if (addresses.isNullOrEmpty()) return null

      val addr = addresses.first()
      // Extract finest locality (village / neighborhood / municipality) and wilaya/state
      val subLocality = addr.subLocality?.trim()?.takeIf { it.isNotEmpty() }
      val locality = addr.locality?.trim()?.takeIf { it.isNotEmpty() }
      val subAdmin = addr.subAdminArea?.trim()?.takeIf { it.isNotEmpty() }
      val adminArea = addr.adminArea?.trim()?.takeIf { it.isNotEmpty() }

      val primaryPlace = subLocality ?: locality ?: subAdmin
      val statePlace = adminArea ?: subAdmin

      when {
        primaryPlace != null && statePlace != null && !statePlace.contains(primaryPlace) && !primaryPlace.contains(statePlace) ->
          "$primaryPlace، $statePlace"
        primaryPlace != null -> primaryPlace
        statePlace != null -> statePlace
        else -> null
      }
    } catch (_: Exception) {
      null
    }
  }
}
