package com.example.device.info

import android.content.Context
import android.os.Build
import com.example.device.battery.BatteryProvider
import com.example.device.memory.MemoryProvider
import com.example.device.storage.StorageProvider
import java.util.Locale

data class DeviceSpecs(
  val manufacturer: String,
  val model: String,
  val brand: String,
  val device: String,
  val board: String,
  val hardware: String,
  val androidVersion: String,
  val sdkInt: Int,
  val ramFormatted: String?,
  val storageFormatted: String?,
  val batteryPercent: Int?
)

class DeviceInfoProvider(
  private val context: Context,
  private val memoryProvider: MemoryProvider? = null,
  private val storageProvider: StorageProvider? = null,
  private val batteryProvider: BatteryProvider? = null
) {

  fun getSpecs(): DeviceSpecs {
    val ram = memoryProvider?.queryMemory()
    val storage = storageProvider?.queryStorage()
    val battery = batteryProvider?.queryCurrentBattery()

    val ramStr = if (ram != null) "${ram.usedRamFormatted} / ${ram.totalRamFormatted}" else null
    val storageStr = if (storage != null) "${storage.freeFormatted} متاح من ${storage.totalFormatted}" else null
    val battPct = battery?.percentage

    return DeviceSpecs(
      manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
      model = Build.MODEL,
      brand = Build.BRAND,
      device = Build.DEVICE,
      board = Build.BOARD,
      hardware = Build.HARDWARE,
      androidVersion = Build.VERSION.RELEASE,
      sdkInt = Build.VERSION.SDK_INT,
      ramFormatted = ramStr,
      storageFormatted = storageStr,
      batteryPercent = battPct
    )
  }

  fun getPhoneModelSummary(isArabic: Boolean): String {
    val specs = getSpecs()
    return if (isArabic) {
      "هاتفك هو ${specs.manufacturer} ${specs.model} ويعمل بنظام Android ${specs.androidVersion} (مستوى الواجهة API ${specs.sdkInt})."
    } else {
      "Your phone is ${specs.manufacturer} ${specs.model}, running Android ${specs.androidVersion} (API level ${specs.sdkInt})."
    }
  }

  fun getDetailedTechnicalSpecs(isArabic: Boolean): String {
    val specs = getSpecs()
    return if (isArabic) {
      val sb = StringBuilder()
      sb.append("المعلومات التقنية الحقيقية لهاتفك المستخرجة من نظام Android:\n")
      sb.append("- الشركة والموديل: ${specs.manufacturer} ${specs.model}\n")
      sb.append("- إصدار النظام: Android ${specs.androidVersion} (API ${specs.sdkInt})\n")
      sb.append("- اللوحة والعتاد: ${specs.hardware}\n")
      specs.ramFormatted?.let { sb.append("- الذاكرة العشوائية (RAM): $it\n") }
      specs.storageFormatted?.let { sb.append("- مساحة التخزين: $it\n") }
      specs.batteryPercent?.let { sb.append("- البطارية الحالية: $it%\n") }
      sb.append("هذه هي المواصفات الفعلية المؤكدة من النظام.")
      sb.toString().trim()
    } else {
      val sb = StringBuilder()
      sb.append("Real hardware and OS telemetry retrieved from Android:\n")
      sb.append("- Manufacturer & Model: ${specs.manufacturer} ${specs.model}\n")
      sb.append("- OS Version: Android ${specs.androidVersion} (API ${specs.sdkInt})\n")
      sb.append("- Hardware: ${specs.hardware}\n")
      specs.ramFormatted?.let { sb.append("- Memory (RAM): $it\n") }
      specs.storageFormatted?.let { sb.append("- Storage: $it\n") }
      specs.batteryPercent?.let { sb.append("- Battery Level: $it%\n") }
      sb.append("These are verified technical specs directly from your device.")
      sb.toString().trim()
    }
  }

  fun getUserIdentityDescription(isArabic: Boolean): String {
    return if (isArabic) {
      "أنت مستخدم هذا الجهاز. لم يتم تسجيل اسم شخصي أو ملف هوية خاص بك في WEYKY بعد، لذلك لا أمتلك معلومات شخصية عن اسمك حفاظاً على خصوصيتك."
    } else {
      "You are the user of this device. No personal identity or profile has been registered in WEYKY yet, so I do not hold personal identification details, respecting your privacy."
    }
  }
}
