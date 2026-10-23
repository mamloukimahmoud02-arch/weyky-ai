package com.example.device.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChargingState {
  CHARGING,
  DISCHARGING,
  FULL,
  NOT_CHARGING,
  UNKNOWN
}

enum class PowerSource {
  AC,
  USB,
  WIRELESS,
  BATTERY,
  UNKNOWN
}

data class BatterySnapshot(
  val percentage: Int,
  val chargingState: ChargingState,
  val powerSource: PowerSource,
  val temperatureCelsius: Float?,
  val voltageMillivolts: Int?,
  val healthString: String,
  val isAvailable: Boolean = true
)

class BatteryProvider(private val context: Context) {
  private val _batteryState = MutableStateFlow<BatterySnapshot?>(queryCurrentBattery())
  val batteryState: StateFlow<BatterySnapshot?> = _batteryState.asStateFlow()

  private var receiver: BroadcastReceiver? = null

  fun start() {
    if (receiver != null) return
    receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let { _batteryState.value = parseIntent(it) }
      }
    }
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val stickyIntent = context.registerReceiver(receiver, filter)
    if (stickyIntent != null) {
      _batteryState.value = parseIntent(stickyIntent)
    }
  }

  fun stop() {
    receiver?.let {
      try {
        context.unregisterReceiver(it)
      } catch (_: Exception) {}
      receiver = null
    }
  }

  fun queryCurrentBattery(): BatterySnapshot? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return if (intent != null) parseIntent(intent) else null
  }

  private fun parseIntent(intent: Intent): BatterySnapshot {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val percent = if (level >= 0 && scale > 0) ((level * 100) / scale) else -1

    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val chargingState = when (status) {
      BatteryManager.BATTERY_STATUS_CHARGING -> ChargingState.CHARGING
      BatteryManager.BATTERY_STATUS_FULL -> ChargingState.FULL
      BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargingState.DISCHARGING
      BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargingState.NOT_CHARGING
      else -> ChargingState.UNKNOWN
    }

    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
    val powerSource = when (plugged) {
      BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC
      BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.USB
      BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
      0 -> PowerSource.BATTERY
      else -> PowerSource.UNKNOWN
    }

    val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
    val tempC = if (rawTemp > 0) rawTemp / 10.0f else null

    val rawVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
    val voltage = if (rawVoltage > 0) rawVoltage else null

    val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
      BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
      BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
      BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
      BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
      BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
      BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
      else -> "Normal"
    }

    return BatterySnapshot(
      percentage = percent,
      chargingState = chargingState,
      powerSource = powerSource,
      temperatureCelsius = tempC,
      voltageMillivolts = voltage,
      healthString = health,
      isAvailable = percent >= 0
    )
  }
}
