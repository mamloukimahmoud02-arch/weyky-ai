package com.example.device.telephony

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class SimSlotInfo(
  val slotIndex: Int,
  val carrierName: String,
  val isReady: Boolean,
  val displayName: String?
)

data class TelephonySnapshot(
  val hasTelephonyHardware: Boolean,
  val supportedSimSlots: Int,
  val activeSimCount: Int,
  val isDualSimSupported: Boolean,
  val primaryCarrierName: String,
  val primarySimState: String,
  val simSlots: List<SimSlotInfo>,
  val isSimReady: Boolean
)

class TelephonyProvider(private val context: Context) {
  private val telephonyManager =
    context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
  private val subscriptionManager =
    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

  fun queryTelephony(): TelephonySnapshot {
    val tm = telephonyManager ?: return unavailSnapshot()
    val hasHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    if (!hasHardware) return unavailSnapshot()

    // 1. Modem slots
    val maxSimSlots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      tm.supportedModemCount
    } else {
      @Suppress("DEPRECATION")
      tm.phoneCount
    }.coerceAtLeast(1)

    val simState = tm.simState
    val isReady = simState == TelephonyManager.SIM_STATE_READY

    val stateStr = when (simState) {
      TelephonyManager.SIM_STATE_READY -> "Ready"
      TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
      TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Locked"
      TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Locked"
      TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
      else -> "Unknown State"
    }

    val primaryCarrier = if (isReady) {
      val name = tm.networkOperatorName
      if (name.isNotBlank()) name else tm.simOperatorName.ifBlank { "Cellular Network" }
    } else {
      "No Service"
    }

    // 2. Active subscription inspection if READ_PHONE_STATE granted
    val slotList = mutableListOf<SimSlotInfo>()
    var activeCount = if (isReady) 1 else 0

    val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPhoneStatePermission && subscriptionManager != null) {
      try {
        val subs: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
        if (!subs.isNullOrEmpty()) {
          activeCount = subs.size
          subs.forEach { sub ->
            slotList.add(
              SimSlotInfo(
                slotIndex = sub.simSlotIndex,
                carrierName = sub.carrierName?.toString() ?: primaryCarrier,
                isReady = true,
                displayName = sub.displayName?.toString()
              )
            )
          }
        }
      } catch (_: Exception) {}
    }

    if (slotList.isEmpty()) {
      slotList.add(
        SimSlotInfo(
          slotIndex = 0,
          carrierName = primaryCarrier,
          isReady = isReady,
          displayName = null
        )
      )
    }

    return TelephonySnapshot(
      hasTelephonyHardware = true,
      supportedSimSlots = maxSimSlots,
      activeSimCount = activeCount,
      isDualSimSupported = maxSimSlots >= 2,
      primaryCarrierName = primaryCarrier,
      primarySimState = stateStr,
      simSlots = slotList,
      isSimReady = isReady
    )
  }

  private fun unavailSnapshot() = TelephonySnapshot(
    hasTelephonyHardware = false,
    supportedSimSlots = 0,
    activeSimCount = 0,
    isDualSimSupported = false,
    primaryCarrierName = "No Telephony",
    primarySimState = "Unavailable",
    simSlots = emptyList(),
    isSimReady = false
  )
}
