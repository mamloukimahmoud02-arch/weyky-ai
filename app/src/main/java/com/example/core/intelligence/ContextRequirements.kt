package com.example.core.intelligence

/**
 * Domain of the user query for targeted context scoping.
 */
enum class IntentDomain {
  OUTDOOR_DEPARTURE,     // "هل اخرج الان؟"
  APPOINTMENT_TIMING,    // "هل سأصل في الموعد؟"
  BATTERY_SUFICIENCY,    // "هل البطارية ستكفي؟"
  SITUATION_OVERVIEW,    // "ماذا افعل الان؟"
  WEATHER_ONLY,          // "الطقس"
  DIRECT_COMMAND,        // volume, flashlight, launch app, etc.
  GENERAL
}

enum class ContextField {
  BATTERY,
  NETWORK,
  PHYSICAL_CONTEXT,
  LOCATION,
  WEATHER,
  CALENDAR,
  MEDIA,
  CALLS,
  NOTIFICATIONS
}

data class ContextRequirements(
  val domain: IntentDomain,
  val required: Set<ContextField>,
  val optional: Set<ContextField> = emptySet(),
  val conditional: Set<ContextField> = emptySet()
) {
  val allNeeded: Set<ContextField> get() = required + optional + conditional
}

object ContextRequirementsRegistry {
  fun resolve(rawInput: String): ContextRequirements {
    val input = rawInput.trim().lowercase()
    val norm = com.example.core.intelligence.conversation.LocalKnowledgeBase.normalizeArabic(rawInput)
    val words = norm.split(" ").filter { it.isNotBlank() }

    // 1. Outdoor departure decision: "هل اخرج الان؟" / "اخرج ولا لا" / "should I go out?" / "can I leave now?"
    if (input.contains("اخرج") || input.contains("أخرج") || input.contains("اطلع") || input.contains("مغادرة") ||
      input.contains("go out") || input.contains("leave now") || input.contains("should i leave")
    ) {
      return ContextRequirements(
        domain = IntentDomain.OUTDOOR_DEPARTURE,
        required = setOf(ContextField.WEATHER, ContextField.CALENDAR, ContextField.BATTERY),
        optional = setOf(ContextField.PHYSICAL_CONTEXT, ContextField.LOCATION),
        conditional = setOf(ContextField.NETWORK)
      )
    }

    // 2. Appointment timing / Arrival probability: "هل سأصل في الموعد؟" / "هل بلحق الموعد" / "will I make it on time?"
    val isSleepScheduleQuery = norm.contains("نوم") || norm.contains("النوم") || norm.contains("sleep")
    if (!isSleepScheduleQuery &&
      (input.contains("موعد") || input.contains("اجتماع") || input.contains("سأصل") || input.contains("ساصل") || input.contains("بأصل") ||
       input.contains("الحق") || input.contains("ألحق") || input.contains("تأخير") || input.contains("make it on time") || input.contains("reach in time"))
    ) {
      return ContextRequirements(
        domain = IntentDomain.APPOINTMENT_TIMING,
        required = setOf(ContextField.CALENDAR, ContextField.PHYSICAL_CONTEXT),
        optional = setOf(ContextField.LOCATION, ContextField.BATTERY),
        conditional = setOf(ContextField.WEATHER)
      )
    }

    // 3. Battery sufficiency & time-aware forecast: "هل البطارية ستكفي؟" / "كم ستتبقى من البطارية حتى المساء؟"
    if ((input.contains("بطارية") || input.contains("البطارية") || input.contains("بطاريه") || input.contains("battery")) &&
      (input.contains("تكفي") || input.contains("ستكفي") || input.contains("تصمد") || input.contains("ستصمد") ||
       input.contains("ستتبقى") || input.contains("تتبقى") || input.contains("يتبقى") || input.contains("سيبقى") ||
       input.contains("باقي") || input.contains("متبقي") || input.contains("last") || input.contains("enough") ||
       input.contains("remain") || input.contains("left") || input.contains("until") || input.contains("حتى"))
    ) {
      return ContextRequirements(
        domain = IntentDomain.BATTERY_SUFICIENCY,
        required = setOf(ContextField.BATTERY, ContextField.CALENDAR),
        optional = setOf(ContextField.PHYSICAL_CONTEXT, ContextField.NETWORK),
        conditional = setOf(ContextField.MEDIA)
      )
    }

    // 4. Broad situational overview / "What should I do now?": "ماذا أفعل الآن؟" / "ما الوضع الآن" / "what should I do now?"
    if (input.contains("ماذا افعل") || input.contains("ماذا أفعل") || input.contains("شو اعمل") || input.contains("ايش اسوي") ||
      input.contains("ما الوضع") || input.contains("ملخص اليوم") || input.contains("what should i do") || input.contains("what's next")
    ) {
      return ContextRequirements(
        domain = IntentDomain.SITUATION_OVERVIEW,
        required = setOf(ContextField.CALENDAR, ContextField.CALLS, ContextField.NOTIFICATIONS, ContextField.BATTERY),
        optional = setOf(ContextField.WEATHER, ContextField.PHYSICAL_CONTEXT),
        conditional = setOf(ContextField.MEDIA)
      )
    }

    // 5. Weather inquiry: "ما حالة الطقس؟" / "الطقس" / "what's the weather?"
    val isRemotePlaceWeather = norm.contains("صلاله") || norm.contains("ظفار") || norm.contains(" هناك") || norm.endsWith("هناك") || norm.contains("salalah")
    if (!isRemotePlaceWeather &&
      (norm.contains("طقس") || words.contains("جو") || words.contains("الجو") || norm.contains("امطار") ||
       input.contains("weather") || input.contains("temperature")) &&
      !norm.contains("حراره الجسم") && !norm.contains("حراره الهاتف")
    ) {
      return ContextRequirements(
        domain = IntentDomain.WEATHER_ONLY,
        required = setOf(ContextField.WEATHER),
        optional = setOf(ContextField.PHYSICAL_CONTEXT),
        conditional = emptySet()
      )
    }

    // Default: General
    return ContextRequirements(
      domain = IntentDomain.GENERAL,
      required = emptySet(),
      optional = emptySet(),
      conditional = emptySet()
    )
  }
}
