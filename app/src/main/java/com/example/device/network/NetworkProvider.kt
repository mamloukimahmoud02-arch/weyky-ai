package com.example.device.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkType {
  WIFI,
  CELLULAR,
  ETHERNET,
  OFFLINE,
  OTHER
}

data class NetworkSnapshot(
  val isConnected: Boolean,
  val isValidated: Boolean,
  val type: NetworkType,
  val typeLabel: String,
  val isMetered: Boolean,
  val isVpn: Boolean,
  val summary: String,
  val isAvailable: Boolean = true
)

class NetworkProvider(private val context: Context) {
  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  private val _networkState = MutableStateFlow(queryCurrentNetwork())
  val networkState: StateFlow<NetworkSnapshot> = _networkState.asStateFlow()

  private var callback: ConnectivityManager.NetworkCallback? = null

  fun start() {
    if (callback != null || connectivityManager == null) return
    callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        _networkState.value = queryCurrentNetwork()
      }

      override fun onLost(network: Network) {
        _networkState.value = queryCurrentNetwork()
      }

      override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        _networkState.value = queryCurrentNetwork()
      }
    }
    try {
      connectivityManager.registerDefaultNetworkCallback(callback!!)
    } catch (_: Exception) {
      _networkState.value = queryCurrentNetwork()
    }
  }

  fun stop() {
    callback?.let {
      try {
        connectivityManager?.unregisterNetworkCallback(it)
      } catch (_: Exception) {}
      callback = null
    }
  }

  fun queryCurrentNetwork(): NetworkSnapshot {
    val cm = connectivityManager ?: return offlineSnapshot()
    val activeNetwork = cm.activeNetwork ?: return offlineSnapshot()
    val caps = cm.getNetworkCapabilities(activeNetwork) ?: return offlineSnapshot()

    val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

    val (type, label) = when {
      caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI to "Wi-Fi"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR to "Cellular"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET to "Ethernet"
      else -> NetworkType.OTHER to "Online"
    }

    val summary = buildString {
      append(label)
      if (isValidated) append(" (Verified)") else if (isConnected) append(" (Connected)")
      if (isMetered) append(" - Metered")
      if (isVpn) append(" - VPN")
    }

    return NetworkSnapshot(
      isConnected = isConnected,
      isValidated = isValidated,
      type = type,
      typeLabel = label,
      isMetered = isMetered,
      isVpn = isVpn,
      summary = summary,
      isAvailable = true
    )
  }

  private fun offlineSnapshot() = NetworkSnapshot(
    isConnected = false,
    isValidated = false,
    type = NetworkType.OFFLINE,
    typeLabel = "Offline",
    isMetered = false,
    isVpn = false,
    summary = "No Network Connection",
    isAvailable = false
  )
}
