package com.example.core.intelligence.battery

import com.example.core.intelligence.ContextDatum
import com.example.core.intelligence.ContextualSynthesis
import com.example.core.intelligence.EpistemicStatus
import com.example.core.intelligence.NextStep
import com.example.core.intelligence.SituationSnapshot
import com.example.core.intelligence.time.SystemTimeProvider
import com.example.core.intelligence.time.TimeProvider
import com.example.device.battery.BatterySnapshot
import com.example.device.battery.ChargingState
import com.example.device.battery.PowerSource
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

data class BatteryForecastTarget(
  val targetHour: Int,
  val targetMinute: Int,
  val descriptionArabic: String,
  val descriptionEnglish: String,
  val rawMatchedString: String
)

data class BatteryForecastScenario(
  val targetHour: Int,
  val targetMinute: Int,
  val targetDescription: String,
  val horizonHours: Double,
  val includesMusic: Boolean,
  val includesNavigation: Boolean = false,
  val initialBatteryPct: Int?,
  val estimatedMinPct: Int?,
  val estimatedMaxPct: Int?,
  val isCharging: Boolean = false
)

sealed interface BatteryForecastOutcome {
  val synthesis: ContextualSynthesis

  data class Success(
    override val synthesis: ContextualSynthesis,
    val scenario: BatteryForecastScenario,
    val horizonHours: Double,
    val estimatedMinPct: Int,
    val estimatedMaxPct: Int
  ) : BatteryForecastOutcome

  data class Charging(
    override val synthesis: ContextualSynthesis,
    val scenario: BatteryForecastScenario,
    val currentPct: Int
  ) : BatteryForecastOutcome

  data class TargetTimePassed(
    override val synthesis: ContextualSynthesis,
    val targetTimeDesc: String,
    val currentTimeDesc: String
  ) : BatteryForecastOutcome

  data class TelemetryUnavailable(
    override val synthesis: ContextualSynthesis,
    val reason: String
  ) : BatteryForecastOutcome

  data class NavigationFollowUpHonestLimitation(
    override val synthesis: ContextualSynthesis,
    val updatedScenario: BatteryForecastScenario
  ) : BatteryForecastOutcome
}

object BatteryTargetTimeExtractor {

  fun isBatteryForecastQuery(query: String): Boolean {
    val norm = normalizeQuery(query)
    val hasBatteryKeyword = norm.contains("بطارية") || norm.contains("بطاريه") ||
      norm.contains("البطارية") || norm.contains("البطاريه") || norm.contains("شحن") ||
      norm.contains("battery") || norm.contains("charge")

    val hasForecastOrSufficiencyKeyword = norm.contains("ستتبقى") || norm.contains("تتبقى") ||
      norm.contains("يتبقى") || norm.contains("تكفي") || norm.contains("ستكفي") ||
      norm.contains("تصمد") || norm.contains("ستصمد") || norm.contains("كم سيبقى") ||
      norm.contains("كم باقي") || norm.contains("كم متبقي") || norm.contains("remain") ||
      norm.contains("left") || norm.contains("last") || norm.contains("enough")

    val hasTimePreposition = norm.contains("حتى") || norm.contains("الي") || norm.contains("إلى") ||
      norm.contains("لغاية") || norm.contains("قبل") || norm.contains("بالمساء") ||
      norm.contains("للمساء") || norm.contains("في المساء") ||
      norm.contains("by ") || norm.contains("until ") || norm.contains("till ") || norm.contains("to ")

    return (hasBatteryKeyword && (hasForecastOrSufficiencyKeyword || hasTimePreposition)) ||
      (hasBatteryKeyword && extractTargetTime(query) != null)
  }

  fun extractTargetTime(query: String): BatteryForecastTarget? {
    val norm = normalizeQuery(query)

    // 1. Named periods of the day
    if (norm.contains("المساء") || norm.contains("مساء") || norm.contains("للمساء") ||
      norm.contains("بالمساء") || norm.contains("evening") || norm.contains("tonight")
    ) {
      if (!norm.contains("صباح") && !norm.contains("morning")) {
        // If a specific hour is also mentioned with مساء (e.g. 6 مساء), let specific hour extractor take precedence
        val specificHour = extractHourWithPeriod(norm)
        if (specificHour != null) return specificHour

        return BatteryForecastTarget(
          targetHour = 18,
          targetMinute = 0,
          descriptionArabic = "المساء (6:00 م)",
          descriptionEnglish = "evening (6:00 PM)",
          rawMatchedString = "المساء"
        )
      }
    }

    if (norm.contains("الليل") || norm.contains("ليل") || norm.contains("night")) {
      return BatteryForecastTarget(
        targetHour = 21,
        targetMinute = 0,
        descriptionArabic = "الليل (9:00 م)",
        descriptionEnglish = "night (9:00 PM)",
        rawMatchedString = "الليل"
      )
    }

    if (norm.contains("الظهر") || norm.contains("ظهر") || norm.contains("noon") || norm.contains("midday")) {
      return BatteryForecastTarget(
        targetHour = 12,
        targetMinute = 0,
        descriptionArabic = "الظهر (12:00 م)",
        descriptionEnglish = "noon (12:00 PM)",
        rawMatchedString = "الظهر"
      )
    }

    if (norm.contains("العصر") || norm.contains("عصر") || norm.contains("afternoon")) {
      return BatteryForecastTarget(
        targetHour = 16,
        targetMinute = 0,
        descriptionArabic = "العصر (4:00 م)",
        descriptionEnglish = "afternoon (4:00 PM)",
        rawMatchedString = "العصر"
      )
    }

    // 2. Named hours in Arabic (e.g. "الساعة السادسة", "السادسة")
    val namedArabicHour = extractNamedArabicHour(norm)
    if (namedArabicHour != null) {
      return namedArabicHour
    }

    // 3. Numeric hours in Arabic or English (e.g. "6 PM", "18:00", "الساعة 6", "6:00")
    return extractNumericHour(norm)
  }

  private fun extractNamedArabicHour(norm: String): BatteryForecastTarget? {
    val isMorning = norm.contains("صباحا") || norm.contains("صباحاً") || norm.contains("في الصباح")
    val isAfternoonOrEvening = norm.contains("مساء") || norm.contains("عصرا") || norm.contains("عصراً") || norm.contains("ليلا") || norm.contains("ليلاً")

    val arabicHours = listOf(
      Triple(listOf("الواحده", "الواحدة", "واحد"), 1, 13),
      Triple(listOf("الثانيه", "الثانية", "اثنين"), 2, 14),
      Triple(listOf("الثالثه", "الثالثة", "ثلاث"), 3, 15),
      Triple(listOf("الرابعه", "الرابعة", "اربع"), 4, 16),
      Triple(listOf("الخامسه", "الخامسة", "خمس"), 5, 17),
      Triple(listOf("السادسه", "السادسة", "ست"), 6, 18),
      Triple(listOf("السابعه", "السابعة", "سبع"), 7, 19),
      Triple(listOf("الثامنه", "الثامنة", "ثمان"), 8, 20),
      Triple(listOf("التاسعه", "التاسعة", "تسع"), 9, 21),
      Triple(listOf("العاشره", "العاشرة", "عشر"), 10, 22),
      Triple(listOf("الحاديه عشر", "الحادية عشر", "الحادي عشر"), 11, 23),
      Triple(listOf("الثانيه عشر", "الثانية عشر"), 12, 12)
    )

    for ((aliases, morningHour, eveningHour) in arabicHours) {
      for (alias in aliases) {
        val pattern = """(?<=^|[^\p{L}])(?:(?:الساعه|الساعة)\s*)?$alias(?=$|[^\p{L}])"""
        if (norm.contains(Regex(pattern))) {
          // If explicitly morning, use morningHour; if evening or typical daytime conversational query, default to afternoon/evening hour
          val hour = if (isMorning) morningHour else eveningHour
          val periodNameAr = if (hour >= 12) "مساءً" else "صباحاً"
          val periodNameEn = if (hour >= 12) "PM" else "AM"
          val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour

          return BatteryForecastTarget(
            targetHour = hour,
            targetMinute = 0,
            descriptionArabic = "الساعة $displayHour $periodNameAr",
            descriptionEnglish = "$displayHour:00 $periodNameEn",
            rawMatchedString = alias
          )
        }
      }
    }
    return null
  }

  private fun extractHourWithPeriod(norm: String): BatteryForecastTarget? {
    val regex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(?:مساء|مساءً|pm)""", RegexOption.IGNORE_CASE)
    val match = regex.find(norm) ?: return null
    var hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: 0
    if (hour < 12) hour += 12
    val displayHour = if (hour > 12) hour - 12 else hour
    return BatteryForecastTarget(
      targetHour = hour,
      targetMinute = minute,
      descriptionArabic = "الساعة $displayHour مساءً",
      descriptionEnglish = "$displayHour:${minute.toString().padStart(2, '0')} PM",
      rawMatchedString = match.value
    )
  }

  private fun extractNumericHour(norm: String): BatteryForecastTarget? {
    // English style: by 6 PM, until 6:00pm, 18:00
    val enRegex = Regex("""(?:by|until|till|to)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
    val enMatch = enRegex.find(norm)
    if (enMatch != null) {
      var hour = enMatch.groupValues[1].toIntOrNull() ?: 0
      val minute = enMatch.groupValues[2].toIntOrNull() ?: 0
      val amPm = enMatch.groupValues[3].lowercase(Locale.ROOT)

      if (amPm == "pm" && hour < 12) hour += 12
      else if (amPm == "am" && hour == 12) hour = 0
      else if (amPm.isEmpty() && hour in 1..11) {
        // Conversational default: if asked during day e.g. "by 6", assume 6 PM (18:00)
        hour += 12
      }

      val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
      val periodEn = if (hour >= 12) "PM" else "AM"
      val periodAr = if (hour >= 12) "مساءً" else "صباحاً"
      return BatteryForecastTarget(
        targetHour = hour,
        targetMinute = minute,
        descriptionArabic = "الساعة $displayHour $periodAr",
        descriptionEnglish = "$displayHour:${minute.toString().padStart(2, '0')} $periodEn",
        rawMatchedString = enMatch.value
      )
    }

    // Arabic style: حتى الساعة 6, حتى 6 مساء, حتى 18:00
    val arRegex = Regex("""(?:حتى|إلى|الي|لغاية|قبل)\s*(?:الساعه|الساعة)?\s*(\d{1,2})(?::(\d{2}))?\s*(صباحا|صباحاً|مساء|مساءً)?""")
    val arMatch = arRegex.find(norm)
    if (arMatch != null) {
      var hour = arMatch.groupValues[1].toIntOrNull() ?: 0
      val minute = arMatch.groupValues[2].toIntOrNull() ?: 0
      val amPm = arMatch.groupValues[3]

      val isEvening = amPm.contains("مساء")
      val isMorning = amPm.contains("صباح")

      if (isEvening && hour < 12) hour += 12
      else if (isMorning && hour == 12) hour = 0
      else if (!isMorning && !isEvening && hour in 1..11) {
        // Default to PM for daytime queries (e.g. 6 -> 18:00)
        hour += 12
      }

      val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
      val periodEn = if (hour >= 12) "PM" else "AM"
      val periodAr = if (hour >= 12) "مساءً" else "صباحاً"
      return BatteryForecastTarget(
        targetHour = hour,
        targetMinute = minute,
        descriptionArabic = "الساعة $displayHour $periodAr",
        descriptionEnglish = "$displayHour:${minute.toString().padStart(2, '0')} $periodEn",
        rawMatchedString = arMatch.value
      )
    }

    return null
  }

  private fun normalizeQuery(raw: String): String {
    return raw.trim()
      .lowercase(Locale.ROOT)
      .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
      .replace('أ', 'ا')
      .replace('إ', 'ا')
      .replace('آ', 'ا')
      .replace('ة', 'ه')
      .replace('ى', 'ي')
      .replace('ؤ', 'و')
      .replace('ئ', 'ي')
      .map { c ->
        when (c) {
          '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
          '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
          else -> c
        }
      }.joinToString("")
  }
}

/**
 * Core Time-Aware Battery Forecasting and Scenario Reasoning Engine.
 */
class BatteryForecastCalculator(
  private val timeProvider: TimeProvider = SystemTimeProvider()
) {

  // Supported Generic Statistical Assumptions:
  // Baseline typical discharge: 2.0% - 4.0% per hour
  // Music playback additional discharge: 4.0% - 8.0% per hour (existing grounded codebase assumption)
  companion object {
    const val BASELINE_MIN_RATE_PER_HOUR = 2.0
    const val BASELINE_MAX_RATE_PER_HOUR = 4.0
    const val MUSIC_ADDITIONAL_MIN_RATE = 4.0
    const val MUSIC_ADDITIONAL_MAX_RATE = 8.0
  }

  fun calculateForecast(
    snapshot: SituationSnapshot,
    target: BatteryForecastTarget,
    includesMusic: Boolean,
    isArabic: Boolean
  ): BatteryForecastOutcome {
    val nowCal = timeProvider.now()
    val nowMillis = nowCal.timeInMillis
    val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)
    val currentMinute = nowCal.get(Calendar.MINUTE)
    val currentTimeDesc = formatTime(currentHour, currentMinute, isArabic)

    // Build target calendar strictly for TODAY
    val targetCal = (nowCal.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, target.targetHour)
      set(Calendar.MINUTE, target.targetMinute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val targetMillis = targetCal.timeInMillis
    val targetDesc = if (isArabic) target.descriptionArabic else target.descriptionEnglish

    // 1. Requirement 10: Handle times already passed today explicitly (never silently project to next day)
    val diffMillis = targetMillis - nowMillis
    if (diffMillis <= 0) {
      val speech = if (isArabic) {
        "$targetDesc قد مضت بالفعل اليوم (الوقت الحالي هو $currentTimeDesc). لا يمكن إجراء تقدير زمني لوقت في الماضي."
      } else {
        "$targetDesc has already passed today (current time is $currentTimeDesc). Cannot forecast battery for a past time."
      }
      val synthesis = ContextualSynthesis(
        synthesizedSpeech = speech,
        primaryDecision = if (isArabic) "الوقت المطلوب قد مضى" else "Target Time Already Passed",
        factsUsed = listOf(
          if (isArabic) "الوقت الحالي: $currentTimeDesc" else "Current time: $currentTimeDesc",
          if (isArabic) "الوقت المطلوب: $targetDesc" else "Requested target: $targetDesc"
        ),
        inferencesMade = listOf(if (isArabic) "الوقت في الماضي اليوم" else "Time is in the past"),
        confidenceScore = 1.0f
      )
      return BatteryForecastOutcome.TargetTimePassed(
        synthesis = synthesis,
        targetTimeDesc = targetDesc,
        currentTimeDesc = currentTimeDesc
      )
    }

    // 2. Requirement 9: Missing battery telemetry
    val batteryDatum = snapshot.battery
    val batterySnap = batteryDatum.value
    if (!batteryDatum.isAvailable || batterySnap == null || batterySnap.percentage < 0) {
      val speech = if (isArabic) {
        "بيانات البطارية غير متوفرة حالياً من النظام، لذا لا يمكن حساب التقدير الزمني للبطارية."
      } else {
        "Battery telemetry is currently unavailable from system APIs; cannot compute battery forecast."
      }
      val synthesis = ContextualSynthesis(
        synthesizedSpeech = speech,
        primaryDecision = if (isArabic) "بيانات البطارية غير متاحة" else "Battery Telemetry Unavailable",
        unavailabilitiesHandled = listOf(batteryDatum.unavailabilityReason ?: "Battery telemetry unavailable"),
        confidenceScore = 0.0f
      )
      return BatteryForecastOutcome.TelemetryUnavailable(
        synthesis = synthesis,
        reason = batteryDatum.unavailabilityReason ?: "Battery telemetry unavailable"
      )
    }

    val currentPct = batterySnap.percentage
    val isCharging = batterySnap.chargingState == ChargingState.CHARGING || batterySnap.powerSource != PowerSource.BATTERY
    val horizonHours = diffMillis.toDouble() / (1000.0 * 60.0 * 60.0)
    val horizonStr = formatHorizonHours(horizonHours, isArabic)

    // 3. Requirement 8: Treat charging separately (never apply discharge formula while charging)
    if (isCharging) {
      val powerSourceLabel = batterySnap.powerSource.name
      val speech = if (isArabic) {
        "القياس الفعلي المؤكد: الهاتف قيد الشحن حالياً عند $currentPct% ومتصل بمصدر طاقة ($powerSourceLabel). طالما أن الهاتف متصل بالشاحن فلن تنفد البطارية، بل ستستمر بالشحن نحو 100% قبل حلول $targetDesc."
      } else {
        "Confirmed actual measurement: Phone is currently charging at $currentPct% connected to $powerSourceLabel. While connected to power, the battery will not discharge and will continue charging toward 100% before $targetDesc."
      }
      val scenario = BatteryForecastScenario(
        targetHour = target.targetHour,
        targetMinute = target.targetMinute,
        targetDescription = targetDesc,
        horizonHours = horizonHours,
        includesMusic = includesMusic,
        includesNavigation = false,
        initialBatteryPct = currentPct,
        estimatedMinPct = currentPct,
        estimatedMaxPct = 100,
        isCharging = true
      )
      val synthesis = ContextualSynthesis(
        synthesizedSpeech = speech,
        primaryDecision = if (isArabic) "الهاتف قيد الشحن" else "Charging (Will Not Deplete)",
        factsUsed = listOf(
          if (isArabic) "القياس الفعلي: $currentPct%" else "Measured battery: $currentPct%",
          if (isArabic) "حالة الشحن: قيد الشحن ($powerSourceLabel)" else "Charging state: Charging ($powerSourceLabel)"
        ),
        confidenceScore = 1.0f
      )
      return BatteryForecastOutcome.Charging(
        synthesis = synthesis,
        scenario = scenario,
        currentPct = currentPct
      )
    }

    // 4. Requirements 3, 4, 5, 6, 7:
    // Calculate projected discharge range using supported assumptions and bound to 0..100%
    val (minRate, maxRate, rateAssumptionsAr, rateAssumptionsEn) = if (includesMusic) {
      val min = BASELINE_MIN_RATE_PER_HOUR + MUSIC_ADDITIONAL_MIN_RATE
      val max = BASELINE_MAX_RATE_PER_HOUR + MUSIC_ADDITIONAL_MAX_RATE
      val arDesc = "معدل استهلاك عام للاستخدام المعتاد (~2-4%/ساعة) مع استهلاك تشغيل الموسيقى (~4-8%/ساعة إضافية كتقدير إحصائي)"
      val enDesc = "generic typical usage (~2-4%/hr) plus music playback (~4-8%/hr additional statistical estimate)"
      Tuple4(min, max, arDesc, enDesc)
    } else {
      val arDesc = "معدل استهلاك عام متوقع للاستخدام المعتاد (~2% إلى 4% في الساعة كتقدير إحصائي)"
      val enDesc = "generic typical smartphone usage (~2% to 4% per hour as a statistical estimate)"
      Tuple4(BASELINE_MIN_RATE_PER_HOUR, BASELINE_MAX_RATE_PER_HOUR, arDesc, enDesc)
    }

    val minDrain = horizonHours * minRate
    val maxDrain = horizonHours * maxRate

    val rawProjectedMin = (currentPct - maxDrain).roundToInt()
    val rawProjectedMax = (currentPct - minDrain).roundToInt()

    // Bound to 0..100%
    val boundedMin = rawProjectedMin.coerceIn(0, 100)
    val boundedMax = rawProjectedMax.coerceIn(0, 100)

    val rateAssumptions = if (isArabic) rateAssumptionsAr else rateAssumptionsEn

    val speech = StringBuilder()
    val primaryDecision: String
    val nextStep: NextStep?

    if (rawProjectedMax <= 0) {
      // Depletion before target time
      primaryDecision = if (isArabic) "البطارية ستنفد قبل الموعد" else "Battery Depletion Predicted"
      if (isArabic) {
        speech.append("القياس الفعلي المؤكد: البطارية الآن عند $currentPct%. ")
        speech.append("التقدير الحسابي للوقت المتبقي حتى $targetDesc هو حوالي $horizonStr. ")
        speech.append("بناءً على $rateAssumptions، يُتوقع أن تنفد البطارية تماماً (0%) قبل حلول $targetDesc [تقدير إحصائي / ESTIMATE]. ")
        speech.append("يُنصح بشحن الهاتف الآن أو تفعيل وضع توفير الطاقة.")
      } else {
        speech.append("Confirmed actual measurement: Battery is currently at $currentPct%. ")
        speech.append("Calculated horizon until $targetDesc is approximately $horizonStr. ")
        speech.append("Based on $rateAssumptions, the battery is projected to deplete to 0% before $targetDesc [ESTIMATE]. ")
        speech.append("Immediate charging or enabling battery saver is recommended.")
      }
      nextStep = NextStep(
        label = if (isArabic) "تفعيل توفير الطاقة" else "Enable Battery Saver"
      )
    } else {
      primaryDecision = if (boundedMin >= 20) {
        if (isArabic) "البطارية كافية" else "Battery Sufficient"
      } else {
        if (isArabic) "البطارية حرجة" else "Battery Tight"
      }

      if (isArabic) {
        speech.append("القياس الفعلي المؤكد: نسبة البطارية الحالية هي $currentPct%. ")
        speech.append("الوقت المتبقي حتى $targetDesc هو حوالي $horizonStr. ")
        speech.append("التقدير الحسابي المتوقع للبطارية المتبقية هو نطاق تقريبي بين $boundedMin% و $boundedMax% [تقدير إحصائي / ESTIMATE]. ")
        speech.append("تنبيه: هذا تقدير إحصائي عام مبني على $rateAssumptions وليس قياساً دقيقاً خاصاً بجهازك؛ ")
        speech.append("إذ يعتمد الصمود الفعلي على سطوع الشاشة ووقت تشغيلها، قوة إشارة الشبكة وبيانات 4G/5G، والتطبيقات النشطة في الخلفية.")
      } else {
        speech.append("Confirmed actual measurement: Current battery is at $currentPct%. ")
        speech.append("Forecast horizon until $targetDesc is approximately $horizonStr. ")
        speech.append("Projected remaining battery is estimated between $boundedMin% and $boundedMax% [ESTIMATE]. ")
        speech.append("Note: This is an approximation based on $rateAssumptions, not a device-specific measurement; ")
        speech.append("actual endurance depends on screen-on time, network connectivity, and background activity.")
      }

      nextStep = if (boundedMin < 20) {
        NextStep(label = if (isArabic) "تفعيل توفير الطاقة" else "Enable Battery Saver")
      } else null
    }

    val scenario = BatteryForecastScenario(
      targetHour = target.targetHour,
      targetMinute = target.targetMinute,
      targetDescription = targetDesc,
      horizonHours = horizonHours,
      includesMusic = includesMusic,
      includesNavigation = false,
      initialBatteryPct = currentPct,
      estimatedMinPct = boundedMin,
      estimatedMaxPct = boundedMax,
      isCharging = false
    )

    val synthesis = ContextualSynthesis(
      synthesizedSpeech = speech.toString().trim(),
      primaryDecision = primaryDecision,
      nextStep = nextStep,
      factsUsed = listOf(
        if (isArabic) "القياس الفعلي المؤكد: $currentPct%" else "Actual confirmed measurement: $currentPct%",
        if (isArabic) "الأفق الزمني المحسوب: $horizonStr حتى $targetDesc" else "Calculated horizon: $horizonStr until $targetDesc",
        if (includesMusic) (if (isArabic) "النشاط المتضمن: تشغيل الموسيقى" else "Activity included: Music playback") else (if (isArabic) "النشاط: الاستخدام المعتاد" else "Activity: Typical usage")
      ),
      inferencesMade = listOf(
        if (isArabic) "الاستهلاك الإحصائي المقدر: ~$minRate% إلى ~$maxRate% في الساعة" else "Estimated statistical drain: ~$minRate% to ~$maxRate% per hr",
        if (isArabic) "النطاق التقديري المتبقي: $boundedMin% - $boundedMax%" else "Projected remaining range: $boundedMin% - $boundedMax%"
      ),
      recommendations = if (boundedMin < 20) listOf(if (isArabic) "شحن الهاتف أو توفير الطاقة" else "Charge phone or enable battery saver") else emptyList(),
      confidenceScore = 0.85f
    )

    return BatteryForecastOutcome.Success(
      synthesis = synthesis,
      scenario = scenario,
      horizonHours = horizonHours,
      estimatedMinPct = boundedMin,
      estimatedMaxPct = boundedMax
    )
  }

  /**
   * Requirement 4: Contextual Follow-Up with Navigation.
   * Resolves against the previous forecast scenario, preserves horizon and music,
   * honestly discloses that navigation drain is unmodeled, and does NOT fabricate a fake number.
   */
  fun reasonNavigationFollowUp(
    previousScenario: BatteryForecastScenario,
    isArabic: Boolean
  ): BatteryForecastOutcome.NavigationFollowUpHonestLimitation {
    val updatedScenario = previousScenario.copy(includesNavigation = true)
    val horizonStr = formatHorizonHours(previousScenario.horizonHours, isArabic)
    val targetDesc = previousScenario.targetDescription

    val speech = if (isArabic) {
      val musicClause = if (previousScenario.includesMusic) " (مع استمرار تشغيل الموسيقى)" else ""
      "بالنسبة للسيناريو حتى $targetDesc$musicClause: إضافة الملاحة (استخدام GPS وتشغيل الشاشة المستمر وعرض الخرائط اللحظي) ستزيد من سرعة استنزاف البطارية بصورة ملحوظة. " +
        "ولكن نظراً لعدم توفر نموذج استهلاك مقاس وموثوق للملاحة على هذا الجهاز في قاعدة المعرفة المحلية، لا يمكن تقديم نسبة مئوية رقمية محددة أو دقيقة للسيناريو المشترك دون افتراضات غير مثبتة [غير متاح / UNAVAILABLE للنموذج الرقمي للملاحة]. " +
        "التوصية العملية: يُنصح بشدة بتوصيل الهاتف بشاحن سيارة عند تشغيل الملاحة لضمان صمود البطارية حتى $targetDesc."
    } else {
      val musicClause = if (previousScenario.includesMusic) " (with music playback)" else ""
      "Regarding your scenario until $targetDesc$musicClause: adding navigation (GPS, continuous screen-on time, and real-time mapping) will substantially accelerate battery drain. " +
        "However, because WEYKY does not have a calibrated empirical drain model for navigation on this device, the combined scenario cannot be reliably quantified with a specific numerical percentage without ungrounded assumptions [UNAVAILABLE for navigation numerical model]. " +
        "Practical recommendation: Connecting to a car charger during navigation is strongly advised to ensure the battery lasts until $targetDesc."
    }

    val synthesis = ContextualSynthesis(
      synthesizedSpeech = speech,
      primaryDecision = if (isArabic) "إضافة الملاحة (نموذج الاستهلاك غير متاح)" else "Navigation Added (Drain Model Unavailable)",
      nextStep = NextStep(
        label = if (isArabic) "توصيل شاحن السيارة" else "Connect Car Charger"
      ),
      factsUsed = listOf(
        if (isArabic) "الأفق الزمني المحفوظ: $horizonStr حتى $targetDesc" else "Retained horizon: $horizonStr until $targetDesc",
        if (previousScenario.initialBatteryPct != null) {
          if (isArabic) "نسبة البطارية المقاسة: ${previousScenario.initialBatteryPct}%" else "Measured battery: ${previousScenario.initialBatteryPct}%"
        } else {
          if (isArabic) "بيانات البطارية الأولية" else "Initial battery data"
        },
        if (previousScenario.includesMusic) (if (isArabic) "السيناريو السابق يتضمن: تشغيل الموسيقى" else "Previous scenario includes: Music playback") else (if (isArabic) "الاستخدام المعتاد" else "Typical usage"),
        if (isArabic) "النشاط المضاف: الملاحة ونظام التموضع العالمي (GPS)" else "Added activity: Navigation & GPS"
      ),
      inferencesMade = listOf(
        if (isArabic) "الملاحة والشاشة المستمرة والـ GPS ترفع استهلاك الطاقة بصورة حادة" else "Navigation with GPS and active display significantly increases power consumption"
      ),
      recommendations = listOf(
        if (isArabic) "توصيل الهاتف بشاحن السيارة أثناء الملاحة" else "Connect to car charger during navigation"
      ),
      unavailabilitiesHandled = listOf(
        if (isArabic) "نموذج استهلاك الملاحة غير مقاس على هذا الجهاز" else "Navigation consumption rate model is not calibrated for this device"
      ),
      confidenceScore = 0.9f
    )

    return BatteryForecastOutcome.NavigationFollowUpHonestLimitation(
      synthesis = synthesis,
      updatedScenario = updatedScenario
    )
  }

  private fun formatTime(hour: Int, minute: Int, isArabic: Boolean): String {
    val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
    val period = if (isArabic) (if (hour >= 12) "مساءً" else "صباحاً") else (if (hour >= 12) "PM" else "AM")
    val minStr = minute.toString().padStart(2, '0')
    return "$displayHour:$minStr $period"
  }

  private fun formatHorizonHours(hours: Double, isArabic: Boolean): String {
    val rounded = (hours * 10.0).roundToInt() / 10.0
    return if (isArabic) {
      if (rounded == 1.0) "ساعة واحدة"
      else if (rounded == 2.0) "ساعتان"
      else "$rounded ساعة"
    } else {
      if (rounded == 1.0) "1 hour"
      else "$rounded hours"
    }
  }

  private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
