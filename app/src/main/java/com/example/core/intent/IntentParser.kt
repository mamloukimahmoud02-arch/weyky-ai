package com.example.core.intent

import com.example.device.audio.VolumeAdjustment
import java.util.Locale

object IntentParser {

  fun normalize(raw: String): String {
    return raw.trim()
      .lowercase(Locale.ROOT)
      .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // Strip all tashkeel & shaddah
      .replace('أ', 'ا')
      .replace('إ', 'ا')
      .replace('آ', 'ا')
      .replace('ة', 'ه')
      .replace('ى', 'ي')
      .replace('ؤ', 'و')
      .replace('ئ', 'ي')
      .replace(Regex("[\\?\\!\\.\\,\\؟\\:]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  fun extractContactTarget(rawInput: String): String {
    var query = rawInput.trim()
    val wakePrefixes = listOf("hey weyky ", "يا ويكي ", "يا weyky ", "ويكي ", "weyky ", "هاي ويكي ", "اهلا ويكي ")
    for (wp in wakePrefixes) {
      if (query.startsWith(wp, ignoreCase = true)) {
        query = query.substring(wp.length).trim()
        break
      }
    }
    val callPrefixes = listOf(
      "اتصل بـ ", "اتصل ب ", "اتصل على ", "اتصل ب", "اتصل ",
      "إتصل بـ ", "إتصل ب ", "إتصل على ", "إتصل ب", "إتصل ",
      "كلم ", "خاطب ", "رن على ", "هاتف ", "مكالمة ",
      "call ", "dial ", "phone "
    )
    for (prefix in callPrefixes) {
      if (query.startsWith(prefix, ignoreCase = true)) {
        query = query.substring(prefix.length).trim()
        break
      }
    }
    if (query.startsWith("بـ") && query.length > 2) {
      query = query.substring(2).trim()
    } else if (query.startsWith("ب") && query.length > 2 && !query.startsWith("باسم") && !query.startsWith("بدر")) {
      val withoutB = query.substring(1).trim()
      if (withoutB.isNotEmpty()) {
        query = withoutB
      }
    }
    return query.trim()
  }

  /**
   * Checks if input is directly a recognized prominent app name.
   */
  private fun isDirectAppNameQuery(input: String): Boolean {
    val directNames = setOf(
      "واتساب", "الواتساب", "واتس اب", "واتس", "whatsapp", "whats app",
      "يوتيوب", "اليوتيوب", "يوتوب", "youtube", "you tube",
      "تيليجرام", "تليجرام", "تلكرام", "التيليجرام", "telegram",
      "فيسبوك", "الفيسبوك", "فيس بوك", "facebook",
      "انستجرام", "انستغرام", "انستا", "instagram",
      "تيك توك", "تيكتوك", "tiktok",
      "سناب شات", "سناب", "snapchat",
      "كروم", "متصفح", "chrome",
      "جيميل", "gmail",
      "سبوتيفاي", "spotify",
      "نتفلكس", "نتفليكس", "netflix",
      "الكاميرا", "كاميرا", "camera",
      "المعرض", "معرض", "الاستوديو", "الصور", "gallery", "photos",
      "الخرائط", "maps",
      "الحاسبه", "الحاسبة", "حاسبه", "حاسبة", "calculator",
      "الساعه", "الساعة", "المنبه", "منبه", "clock",
      "شات جي بي تي", "تشات جي بي تي", "جي بي تي", "chatgpt", "chat gpt"
    )
    return directNames.contains(input)
  }

  /**
   * Extracts application name from an open command.
   * e.g. "افتح واتساب" -> "واتساب", "شغل يوتيوب" -> "يوتيوب", "Open WhatsApp" -> "WhatsApp"
   */
  fun extractAppName(rawInput: String): String {
    var query = rawInput.trim()
    val wakePrefixes = listOf("hey weyky ", "يا ويكي ", "يا weyky ", "ويكي ", "weyky ", "هاي ويكي ", "اهلا ويكي ")
    for (wp in wakePrefixes) {
      if (query.startsWith(wp, ignoreCase = true)) {
        query = query.substring(wp.length).trim()
        break
      }
    }

    val openPrefixes = listOf(
      "افتح تطبيق ", "افتح برنامج ", "افتح لي ", "افتح ",
      "إفتح تطبيق ", "إفتح ",
      "شغل تطبيق ", "شغل برنامج ", "شغل لي ", "شغل ",
      "شغّل تطبيق ", "شغّل ",
      "ادخل على ", "انتقل الى ",
      "open app ", "open application ", "open ",
      "launch app ", "launch application ", "launch ",
      "start app ", "start application ", "start "
    )
    for (prefix in openPrefixes) {
      if (query.startsWith(prefix, ignoreCase = true)) {
        query = query.substring(prefix.length).trim()
        break
      }
    }
    return query.trim()
  }

  /**
   * Extracts volume percentage if specified in the query.
   */
  private fun extractVolumePercent(input: String): Int? {
    val converted = input.map { char ->
      when (char) {
        '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
        '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
        else -> char
      }
    }.joinToString("")

    if (converted.contains("%") || converted.contains("٪") || converted.contains("بالمئه") ||
        converted.contains("بالمئة") || converted.contains("في الميه") || converted.contains("percent")) {
      val regex = Regex("""(\d{1,3})""")
      val match = regex.find(converted)
      if (match != null) {
        val num = match.groupValues[1].toIntOrNull()
        if (num != null && num in 0..100) {
          return num
        }
      }
    }

    val hasSetVerb = converted.contains("اجعل") || converted.contains("اضبط") || converted.contains("خلي") || converted.contains("set")
    if (hasSetVerb) {
      val regex = Regex("""(\d{1,3})""")
      val match = regex.find(converted)
      if (match != null) {
        val num = match.groupValues[1].toIntOrNull()
        if (num != null && num in 0..100) {
          return num
        }
      }
    }
    return null
  }

  /**
   * Extracts relative step counts from query.
   */
  private fun extractStepCount(input: String): Int {
    return when {
      input.contains("مرتين") || input.contains("خطوتين") || input.contains("درجتين") || input.contains("twice") || input.contains("2 times") || input.contains("2 steps") -> 2
      input.contains("3 مرات") || input.contains("ثلاث مرات") || input.contains("3 درجات") || input.contains("3 times") || input.contains("3 steps") -> 3
      input.contains("4 مرات") || input.contains("اربع مرات") || input.contains("4 times") -> 4
      else -> 1
    }
  }

  private val COMPOUND_DELIMITERS_REGEX = Regex(
    "(?:\\s+ثم\\s+بعد\\s+ذلك\\s+|\\s+ثم\\s+|\\s+وبعدها\\s+|\\s+وبعد\\s+ذلك\\s+|\\s*,\\s*then\\s+|\\s+and\\s+then\\s+|\\s+then\\s+)",
    RegexOption.IGNORE_CASE
  )

  fun hasCompoundDelimiter(rawInput: String): Boolean {
    // Avoid false positives in short greetings or wake phrases
    if (rawInput.trim().length < 6) return false
    return COMPOUND_DELIMITERS_REGEX.containsMatchIn(rawInput)
  }

  fun tryParseCompound(
    rawInput: String,
    session: com.example.core.intelligence.conversation.ConversationSession? = null
  ): WeykyIntent? {
    val segments = rawInput.split(COMPOUND_DELIMITERS_REGEX)
      .map { it.trim() }
      .filter { it.isNotBlank() }

    if (segments.size < 2) return null

    val steps = mutableListOf<WeykyIntent.CompoundStep>()
    for ((index, seg) in segments.withIndex()) {
      // Parse child segment with allowCompound = false to strictly prevent recursion
      val childIntent = parse(seg, session, allowCompound = false)

      // Validate child intent: UnknownIntent or ConversationalIntent without recognized action should reject compound execution
      if (childIntent is WeykyIntent.UnknownIntent) {
        return null
      }
      if (childIntent is WeykyIntent.ConversationalIntent) {
        // If a segment falls through to ConversationalIntent without actionable capability, do not treat as executable compound command
        return null
      }

      steps.add(WeykyIntent.CompoundStep(index, seg, childIntent))
    }

    return WeykyIntent.CompoundIntent(steps = steps, rawQuery = rawInput)
  }

  private fun hasWord(input: String, vararg targetWords: String): Boolean {
    val tokens = input.split(" ").filter { it.isNotBlank() }
    return targetWords.any { target -> tokens.contains(target) }
  }

  private fun isClockOrDateQuery(input: String): Boolean {
    // Exclude time management, sleep biological clock, travel best time, and historical knowledge queries
    if (input.contains("تنظيم الوقت") || input.contains("اداره الوقت") || input.contains("time management") ||
      input.contains("افضل وقت") || input.contains("وقت لزياره") || input.contains("وقت للنوم") ||
      input.contains("موعد النوم") || input.contains("ساعات النوم") || input.contains("كم ساعه انام") ||
      input.contains("انام في اليوم") || input.contains("الساعه البيولوجيه") || input.contains("لضبط الساعه") ||
      input.contains("في الثانيه") || input.contains("في الدقيقه") || input.contains("ضوء الشمس")
    ) {
      return false
    }

    // Check if "تاريخ" refers to history of a subject rather than today's calendar date
    if (input.contains("تاريخ")) {
      val isCurrentDateQuery = input == "تاريخ" || input == "التاريخ" ||
        input.contains("تاريخ اليوم") || input.contains("كم التاريخ") ||
        input.contains("ما هو التاريخ") || input.contains("ما تاريخ") ||
        input.contains("التاريخ الان") || input.contains("التاريخ اليوم")
      if (!isCurrentDateQuery) return false
    }

    return input == "الوقت" || input == "وقت" || input == "الساعه" || input == "ساعه" ||
      input == "التاريخ" || input == "تاريخ" || input == "اليوم" ||
      input == "time" || input == "clock" || input == "date" || input == "today" ||
      input.contains("كم الساعه") || input.contains("ما الوقت") || input.contains("كم الوقت") ||
      input.contains("الوقت الان") || input.contains("الساعه الان") || input.contains("الساعه كم") ||
      input.contains("تاريخ اليوم") || input.contains("كم التاريخ") || input.contains("ما هو التاريخ") ||
      input.contains("اي يوم اليوم") || input.contains("ما هو اليوم") ||
      input.contains("what time") || input.contains("current time") || input.contains("tell me the time") ||
      input.contains("what is the date") || input.contains("today's date") || input.contains("what day is")
  }

  fun parse(
    rawInput: String,
    session: com.example.core.intelligence.conversation.ConversationSession? = null,
    allowCompound: Boolean = true
  ): WeykyIntent {
    var input = normalize(rawInput)
    if (input.isBlank()) return WeykyIntent.UnknownIntent("")

    // 0. Wake phrase detection
    if (input == "hey weyky" || input == "يا ويكي" || input == "ويكي" || input == "مرحبا ويكي" || input == "weyky" || input == "هاي ويكي" || input == "يا weyky" || input == "اهلا ويكي") {
      return WeykyIntent.WakeIntent
    }

    // Strip leading wake phrase
    val wakePrefixes = listOf("hey weyky ", "يا ويكي ", "يا weyky ", "ويكي ", "weyky ", "هاي ويكي ", "مرحبا ويكي ", "اهلا ويكي ")
    for (wp in wakePrefixes) {
      if (input.startsWith(wp)) {
        input = input.substring(wp.length).trim()
        break
      }
    }

    // Check for compound command splitting if enabled
    if (allowCompound && hasCompoundDelimiter(rawInput)) {
      val compound = tryParseCompound(rawInput, session)
      if (compound != null) {
        return compound
      }
    }

    // 0.5 Arithmetic & Calculation Queries -> Route directly to ConversationalEngine / LocalKnowledgeBase
    if (com.example.core.intelligence.conversation.LocalKnowledgeBase.evaluateArithmetic(rawInput) != null) {
      return WeykyIntent.ConversationalIntent(rawInput)
    }

    // 1. Semantic classification & disambiguation: Questions vs Commands vs Hypotheticals
    val classified = MessageIntentClassifier.classify(rawInput, session)
    when (classified.primarySubject) {
      "battery_music_impact" -> return WeykyIntent.HypotheticalBatteryMusicIntent
      "schedule_delay_impact" -> return WeykyIntent.HypotheticalScheduleDelayIntent(classified.extractedDelayMinutes ?: 10)
      "song_title_provided" -> {
        val title = classified.extractedSongTitle ?: rawInput
        return WeykyIntent.PlaySpecificSongIntent(title)
      }
      "request_song_title" -> return WeykyIntent.RequestSongTitleIntent
      "play_specific_song" -> {
        val title = classified.extractedSongTitle ?: rawInput
        return WeykyIntent.PlaySpecificSongIntent(title)
      }
      "user_identity" -> return WeykyIntent.UserIdentityIntent
      "phone_model" -> return WeykyIntent.PhoneModelIntent
      "device_detailed_specs" -> return WeykyIntent.DeviceInfoDetailedIntent
    }

    val mentionsBatteryOrCharging = input.contains("battery") ||
      hasWord(input, "charge", "charging", "charged", "charger") ||
      input.contains("بطاريه") || input.contains("البطاريه") || input.contains("بطاريتي") ||
      input.contains("شحن") || input.contains("يشحن") || input.contains("تشحن") ||
      input.contains("الشاحن") || input.contains("مشحون")

    val mentionsPowerConsumption = input.contains("استهلاك الطاقه") || input.contains("توفير الطاقه") ||
      input.contains("حفظ الطاقه") || input.contains("power consumption") || input.contains("save power")

    val isBatterySufficiencyOrForecastQuestion = input.contains("تكفي") || input.contains("ستكفي") ||
      input.contains("تصمد") || input.contains("ستصمد") || input.contains("ستتبقي") ||
      input.contains("تتبقي") || input.contains("يتبقي") || input.contains("سيبقي") ||
      input.contains("كم باقي") || input.contains("كم متبقي") ||
      hasWord(input, "enough", "remain", "left", "last")

    val isCausalOrAdvisoryBatteryQuery = !isBatterySufficiencyOrForecastQuestion && (
      input.contains("لماذا") || input.contains("ليش") ||
      input.contains("ما سبب") || input.contains("اسباب") || input.contains("ما العوامل") ||
      input.contains("عوامل") || input.contains("تؤثر") || input.contains("توثر") ||
      input.contains("تاثير") || input.contains("يضر") || input.contains("تضر") ||
      input.contains("ضرر") || input.contains("اضرار") || input.contains("خطر") ||
      input.contains("تنفد") || input.contains("ينفد") || input.contains("نفاد") ||
      input.contains("تفرغ") || input.contains("استنزاف") || input.contains("بسرعه") ||
      input.contains("استهلاك") || input.contains("اهلاك") || input.contains("اقلل") ||
      input.contains("تقليل") || input.contains("توفير") || input.contains("اطاله") ||
      input.contains("حفاظ") || input.contains("الحفاظ") || input.contains("احافظ") ||
      input.contains("حمايه") || input.contains("نصائح") || input.contains("نصايح") ||
      input.contains("نصيحه") || input.contains("طرق") || input.contains("كيف") ||
      input.contains("عمر البطاريه") || input.contains("صحه البطاريه") ||
      input.contains("طوال الليل") || input.contains("اثناء النوم") ||
      input.contains("سخونه") || input.contains("حراره البطاريه") ||
      hasWord(input, "why", "causes", "factors", "drain", "draining", "overnight", "damage", "harm", "affect", "lifespan", "tips", "care", "preserve", "protect", "reduce") ||
      input.contains("how to") || input.contains("how do") || input.contains("how can") ||
      input.contains("bad for") || input.contains("battery health") || input.contains("save battery")
    )

    val isBatteryKnowledgeQuery = mentionsPowerConsumption ||
      (mentionsBatteryOrCharging && isCausalOrAdvisoryBatteryQuery)

    // Contextual Follow-up: Additional Navigation Scenario on previous battery forecast
    if (session?.lastBatteryScenario != null &&
      (input.contains("ملاحة") || input.contains("الملاحة") || input.contains("ملاحه") || input.contains("خرائط") || input.contains("navigation") || input.contains("gps")) &&
      (input.contains("لو") || input.contains("ماذا") || input.contains("ايضا") || input.contains("ايضاً") || input.contains("also") || input.contains("what about"))
    ) {
      return WeykyIntent.TimeAwareBatteryForecastIntent(
        rawQuery = rawInput,
        target = null,
        includesMusic = session.lastBatteryScenario!!.includesMusic,
        isFollowUpNavigation = true
      )
    }

    // Time-Aware Battery Forecast: "كم ستتبقى من البطارية حتى المساء؟" / "هل ستكفي البطارية حتى الساعة السادسة؟"
    if (!isBatteryKnowledgeQuery && com.example.core.intelligence.battery.BatteryTargetTimeExtractor.isBatteryForecastQuery(input)) {
      val target = com.example.core.intelligence.battery.BatteryTargetTimeExtractor.extractTargetTime(input)
      if (target != null) {
        val includesMusic = input.contains("موسيقى") || input.contains("موسيقي") ||
          input.contains("اغنية") || input.contains("أغنية") ||
          input.contains("music") || input.contains("song")
        return WeykyIntent.TimeAwareBatteryForecastIntent(
          rawQuery = rawInput,
          target = target,
          includesMusic = includesMusic,
          isFollowUpNavigation = false
        )
      }
    }

    // Contextual Weather follow-ups when previous topic was weather or direct weather questions
    val isWeatherContext = session?.currentTopic == "weather" || session?.lastReferencedSubject == "weather"
    val isRemotePlaceWeather = input.contains("صلاله") || input.contains("ظفار") || input.contains("salalah") ||
      ((input.contains("هناك") || input.contains("فيها") || hasWord(input, "there")) &&
       (session?.currentTopic == "salalah_tourism" || session?.currentTopic == "travel"))

    if (!isRemotePlaceWeather) {
      // "ماذا عن المساء؟" / "طقس المساء"
      if (input.contains("المساء") || input.contains("الليله") || input.contains("مساء") || input.contains("في الليل") || input.contains("evening") || input.contains("tonight")) {
        if (input.contains("طقس") || hasWord(input, "جو", "الجو") || input.contains("حراره") || input.contains("مطر") || isWeatherContext) {
          return WeykyIntent.WeatherIntent(WeatherQueryType.HOURLY_EVENING, rawInput)
        }
      }

      // "هل ستمطر خلال الساعتين القادمتين؟" / "هل في مطر قريبا"
      if ((input.contains("تمطر") || input.contains("ستمطر") || input.contains("مطر") || hasWord(input, "rain", "raining", "rainy")) &&
        (input.contains("ساعتين") || input.contains("قادمه") || input.contains("قريبا") || input.contains("الان") || input.contains("اليوم") || input.contains("soon") || input.contains("hours"))
      ) {
        return WeykyIntent.WeatherIntent(WeatherQueryType.RAIN_SOON, rawInput)
      }

      // "كيف ستكون الرياح غدا؟" / "سرعه الرياح غدا"
      if ((input.contains("رياح") || input.contains("الرياح") || input.contains("هواء") || hasWord(input, "wind", "windy")) &&
        (input.contains("غدا") || input.contains("بكره") || input.contains("اليوم") || input.contains("كم") || input.contains("tomorrow") || isWeatherContext)
      ) {
        return WeykyIntent.WeatherIntent(WeatherQueryType.WIND_TOMORROW, rawInput)
      }

      // "قارن بين طقس اليوم وغدا"
      if (input.contains("قارن") || input.contains("مقارنه") || (input.contains("اليوم") && (input.contains("غدا") || input.contains("وغدا") || input.contains("بكره")))) {
        if (input.contains("طقس") || hasWord(input, "جو", "الجو") || isWeatherContext) {
          return WeykyIntent.WeatherIntent(WeatherQueryType.COMPARE_TOMORROW, rawInput)
        }
      }

      // "متى اقرب احتمال للمطر هذا الاسبوع؟"
      if ((input.contains("مطر") || input.contains("للمطر") || input.contains("الامطار") || hasWord(input, "rain", "raining", "rainy")) &&
        (input.contains("اسبوع") || input.contains("الاسبوع") || input.contains("متي") || input.contains("week") || input.contains("اقرب") || input.contains("قادم"))
      ) {
        return WeykyIntent.WeatherIntent(WeatherQueryType.WEEK_RAIN, rawInput)
      }

      // "هل الجو مناسب للخروج الان؟"
      if ((hasWord(input, "جو", "الجو") || input.contains("الطقس") || isWeatherContext) &&
        (input.contains("مناسب للخروج") || input.contains("اخرج") || input.contains("للخروج") || input.contains("طلعه"))
      ) {
        return WeykyIntent.WeatherIntent(WeatherQueryType.OUTDOOR_SUITABILITY, rawInput)
      }
    }

    val isForRingtone = input.contains("رنين") || input.contains("نغمه") || input.contains("مكالمات") || input.contains("ringtone") || input.contains("ring")

    // Contextual Intelligence Domain Queries (e.g. "هل اخرج الان؟" with outdoor departure reasoning)
    if (!isBatteryKnowledgeQuery) {
      val req = com.example.core.intelligence.ContextRequirementsRegistry.resolve(input)
      if (req.domain != com.example.core.intelligence.IntentDomain.GENERAL &&
        req.domain != com.example.core.intelligence.IntentDomain.WEATHER_ONLY &&
        req.domain != com.example.core.intelligence.IntentDomain.DIRECT_COMMAND
      ) {
        return WeykyIntent.ContextualQueryIntent(rawQuery = rawInput, domain = req.domain)
      }
    }

    val hasLiveBatteryTelemetrySignal = input == "battery" || input == "battery status" ||
      input == "battery level" || input == "بطاريه" || input == "البطاريه" ||
      input == "بطاريتي" || input == "حاله البطاريه" || input == "نسبه البطاريه" ||
      input == "مستوي البطاريه" || input == "شحن البطاريه" || input == "حاله الشحن" ||
      input.contains("نسبه") || input.startsWith("كم ") || input == "كم البطاريه" ||
      input.contains("كم بطاريه") || input.contains("كم بطاريتي") || input.contains("كم الشحن") ||
      input.contains("مستوي البطاريه") || input.contains("حاله البطاريه") ||
      input.contains("حاله الشحن") || input.contains("وضع البطاريه") ||
      input.contains("بطاريتي الان") || input.contains("ما بطاريتي") ||
      input.contains("فحص البطاريه") || input.contains("افحص البطاريه") ||
      input.contains("اقرا البطاريه") || input.contains("اعرض البطاريه") ||
      input.contains("المتبقي") || input.contains("باقي") || input.contains("متبقي") ||
      input.contains("يشحن") || input.contains("تشحن") ||
      input.contains("متصل بالشاحن") || input.contains("متصله بالشاحن") ||
      input.contains("موصول بالشاحن") || input.contains("موصوله بالشاحن") ||
      input.contains("قيد الشحن") || input.contains("مشحون") || input.contains("مشحونه") ||
      input.contains("في الشاحن") || input.contains("علي الشاحن") ||
      input.contains("percent") || input.contains("percentage") ||
      input.contains("battery level") || input.contains("battery status") ||
      input.contains("how much battery") || input.contains("what is my battery") ||
      input.contains("what's my battery") || input.contains("check battery") ||
      input.contains("battery remaining") || input.contains("battery left") ||
      input.contains("is it charging") || input.contains("is my phone charging") ||
      input.contains("is the phone charging") || input.contains("plugged in") ||
      input.contains("connected to charger")

    val isLiveBatteryTelemetryRequest = mentionsBatteryOrCharging &&
      !isCausalOrAdvisoryBatteryQuery &&
      hasLiveBatteryTelemetrySignal

    val isHumanMemoryQuery = input.contains("ذاكرتي") || input.contains("بشريه") || input.contains("البشريه") ||
      input.contains("تقويه") || input.contains("تنشيط") || input.contains("تحسين") ||
      input.contains("نسيان") || input.contains("النسيان") || input.contains("حفظ") ||
      input.contains("الدماغ") || input.contains("العقل") || input.contains("انسان") || input.contains("الانسان") ||
      input.contains("شرب الماء") || input.contains("فوائد الماء") ||
      input.contains("human memory") || input.contains("my memory") || input.contains("improve memory") || input.contains("brain")

    val isEnglishFlashlightCommand = hasWord(input, "light") &&
      (input.contains("turn on") || input.contains("turn off") || input.contains("switch on") ||
       input.contains("switch off") || input.contains("toggle") || input.contains("enable") || input.contains("disable")) &&
      !input.contains("speed of light") && !input.contains("blue light")

    val isHistoricalOrScienceLightQuery = input.contains("سرعه الضوء") || input.contains("speed of light") ||
      input.contains("من اخترع") || input.contains("تاريخ") || input.contains("اختراع") ||
      input.contains("اديسون") || input.contains("كهربائي") || input.contains("who invented")

    val isBiologicalOrKnowledgeDeviceQuery = input.contains("الجهاز الهضمي") || input.contains("الجهاز العصبي") ||
      input.contains("الجهاز التنفسي") || input.contains("الجهاز المناعي") || input.contains("جهاز المناعه") ||
      input.contains("الذكاء الاصطناعي") || input.contains("نوم") || input.contains("النوم") ||
      mentionsBatteryOrCharging || mentionsPowerConsumption || isBatteryKnowledgeQuery

    return when {
      // 1. Settings, Wifi, Bluetooth commands
      (input.contains("wifi") || input.contains("wi-fi") || input.contains("واي فاي") || input.contains("الواي فاي")) &&
      !input.contains("ما هو") && !input.contains("كيف يعمل") -> {
        WeykyIntent.WifiIntent(openSettings = true)
      }
      (input.contains("bluetooth") || input.contains("بلوتوث")) &&
      !isBatteryKnowledgeQuery && !input.contains("ما هو") && !input.contains("كيف يعمل") -> {
        WeykyIntent.BluetoothIntent
      }
      (input.contains("setting") || input.contains("اعدادات") || input.contains("الإعدادات") || (input.contains("ضبط") && !input.contains("صوت") && !input.contains("ساعه البيولوجيه") && !input.contains("الساعه البيولوجيه"))) && !input.startsWith("افتح ") && !input.startsWith("open ") -> {
        when {
          input.contains("battery") || input.contains("بطاريه") || input.contains("البطاريه") -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.BATTERY)
          input.contains("display") || input.contains("screen") || input.contains("شاشه") || input.contains("الشاشه") -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.DISPLAY)
          input.contains("sound") || input.contains("audio") || input.contains("صوت") -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.SOUND)
          input.contains("app") || input.contains("تطبيقات") -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.APPS)
          else -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.GENERAL)
        }
      }

      // 2. Calls: Missed, Outgoing, Incoming, or Specific dial
      input.contains("مكالمه صادره") || input.contains("اتصال صادر") || input.contains("اخر من اتصلت") || input.contains("من اتصلت به") || input.contains("last outgoing call") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.OUTGOING)
      }
      input.contains("مكالمه وارده") || input.contains("اتصال وارد") || input.contains("اخر مكالمه مستلمه") || input.contains("last incoming call") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.INCOMING)
      }
      input.contains("من اتصل بي") || input.contains("مين اتصل") || input.contains("من رن علي") || input.contains("who called") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.INCOMING)
      }
      input.contains("سجل المكالمات") || input.contains("اخر المكالمات") || input.contains("المكالمات الاخيره") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.ALL)
      }
      input.contains("missed call") || input.contains("مكالمات فائته") || input.contains("مكالمه فائته") ||
      input.contains("المكالمات الفائته") || input.contains("فائته") || input.contains("فائتة") ||
      input.contains("غير مردود") || input.contains("لم ارد") || input.contains("المكالمات") ||
      input.contains("مكالمات") || input.contains("مكالمه") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.MISSED, showMissed = true)
      }

      // Direct Dial
      input.startsWith("call ") || input.startsWith("dial ") ||
      input.startsWith("اتصل") || input.startsWith("إتصل") || input.startsWith("كلم ") ||
      input.startsWith("خاطب ") || input.startsWith("رن على ") || input.startsWith("هاتف ") || input.startsWith("phone ") -> {
        val target = extractContactTarget(rawInput)
        WeykyIntent.CallIntent(target = target.ifBlank { null }, queryType = CallQueryType.DIAL_TARGET, showMissed = false)
      }

      // 3. Media Next / Previous Track
      input.contains("الاغنيه التاليه") || input.contains("المقطع التالي") || input.contains("التالي") ||
      input.contains("تخطي الاغنيه") || input.contains("غير الاغنيه") || input.contains("next track") || input.contains("next song") || input.contains("skip track") ||
      input == "next" -> {
        WeykyIntent.MediaIntent(WeykyIntent.MediaAction.NEXT)
      }
      input.contains("الاغنيه السابقه") || input.contains("المقطع السابق") || input.contains("السابق") ||
      input.contains("previous track") || input.contains("previous song") || input == "previous" -> {
        WeykyIntent.MediaIntent(WeykyIntent.MediaAction.PREVIOUS)
      }

      // 4. Volume / Audio with Incremental Steps & Percentage
      hasWord(input, "volume", "audio", "sound") || input.contains("صوت") || input.contains("الصوت") -> {
        val percent = extractVolumePercent(input)
        val stepCount = extractStepCount(input)

        when {
          percent != null -> WeykyIntent.VolumePercentIntent(percent, forRingtone = isForRingtone)
          (input.contains("mute") && !input.contains("unmute")) || input.contains("كتم") || input.contains("اكتم") ->
            WeykyIntent.VolumeMuteIntent(mute = true, forRingtone = isForRingtone)
          input.contains("unmute") || input.contains("الغاء كتم") || input.contains("فك الكتم") ->
            WeykyIntent.VolumeMuteIntent(mute = false, forRingtone = isForRingtone)
          input.contains("up") || input.contains("raise") || input.contains("increase") || input.contains("louder") || input.contains("more") ||
          input.contains("ارفع") || input.contains("رفع") || input.contains("علي") || input.contains("اعلي") || input.contains("زياده") || input.contains("زود") ->
            WeykyIntent.VolumeStepIntent(increase = true, stepCount = stepCount, forRingtone = isForRingtone)
          input.contains("down") || input.contains("lower") || input.contains("decrease") || input.contains("quieter") || input.contains("less") ||
          input.contains("اخفض") || input.contains("خفض") || input.contains("وطي") || input.contains("قلل") || input.contains("نقص") || input.contains("اقل") ->
            WeykyIntent.VolumeStepIntent(increase = false, stepCount = stepCount, forRingtone = isForRingtone)
          else -> WeykyIntent.VolumeStepIntent(increase = true, stepCount = 1, forRingtone = isForRingtone)
        }
      }

      // 5. Open Application Commands
      (input.startsWith("افتح ") || input.startsWith("إفتح ") || input.startsWith("شغل ") ||
       input.startsWith("شغّل ") || input.startsWith("ادخل ") || input.startsWith("انتقل ") ||
       input.startsWith("تطبيق ") || input.startsWith("open ") || input.startsWith("launch ") ||
       input.startsWith("start ") || isDirectAppNameQuery(input)) &&
      !input.contains("كشاف") && !input.contains("فلاش") && !input.contains("مصباح") && !input.contains("ضوء") && !input.contains("نور") &&
      !input.contains("torch") && !input.contains("flashlight") &&
      // If user specifically requests "افتح الموسيقى" or "افتح تطبيق الموسيقى", treat as opening the music app
      (!input.contains("موسيقي") && !input.contains("موسيقى") && !input.contains("اغنيه") && !input.contains("أغنية") && !input.contains("music") ||
       input.startsWith("افتح ") || input.startsWith("إفتح ") || input.startsWith("open ")) -> {
        val appName = extractAppName(rawInput)
        if (appName.isNotBlank()) {
          WeykyIntent.OpenAppIntent(appName = appName)
        } else {
          WeykyIntent.UnknownIntent(rawInput)
        }
      }

      // 6. Media / Music Play/Pause/Toggle
      (input.contains("music") || input.contains("موسيقي") || input.contains("موسيقى") || input.contains("اغنيه") || input.contains("أغنية") ||
       input == "اوقف" || input == "أوقف" || input == "ايقاف" || input == "وقف" || input == "استمر" || input == "كمل" || input == "pause" || input == "play") &&
      (input.contains("pause") || input.contains("stop") || input.contains("play") || input.contains("resume") ||
       input.contains("شغل") || input.contains("تشغيل") || input.contains("اوقف") || input.contains("أوقف") || input.contains("ايقاف") || input.contains("وقف") || input.contains("استمر") || input.contains("كمل")) -> {
        when {
          input.contains("pause") || input.contains("stop") || input.contains("اوقف") || input.contains("أوقف") || input.contains("ايقاف") || input.contains("إيقاف") || input.contains("وقف") ->
            WeykyIntent.MediaIntent(WeykyIntent.MediaAction.PAUSE)
          input.contains("play") || input.contains("resume") || input.contains("شغل") || input.contains("تشغيل") || input.contains("استمر") ->
            WeykyIntent.MediaIntent(WeykyIntent.MediaAction.PLAY)
          else -> WeykyIntent.MediaIntent(WeykyIntent.MediaAction.TOGGLE)
        }
      }

      // 7. Battery (live telemetry readout & battery settings vs explanatory/advisory battery knowledge)
      mentionsBatteryOrCharging && (input.contains("setting") || input.contains("اعدادات") || input.contains("ضبط")) -> {
        WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.BATTERY)
      }
      isLiveBatteryTelemetryRequest -> {
        WeykyIntent.BatteryIntent
      }

      // 8. Storage
      input.contains("storage") || hasWord(input, "disk") || input.contains("free space") || input.contains("space left") ||
      input.contains("تخزين") || (input.contains("مساحه") && !input.contains("مساحه المثلث") && !input.contains("مساحه الدائره")) -> {
        WeykyIntent.StorageIntent
      }

      // 9. RAM (device RAM vs human memory knowledge)
      (hasWord(input, "ram") || input.contains("memory") || input.contains("ذاكره") || input.contains("الرام")) &&
      !isHumanMemoryQuery -> {
        WeykyIntent.RamIntent
      }

      // 10. Flashlight (hardware torch vs speed of light / science / history)
      (input.contains("flashlight") || input.contains("torch") || isEnglishFlashlightCommand ||
       input.contains("كشاف") || input.contains("فلاش") || input.contains("مصباح")) &&
      !isHistoricalOrScienceLightQuery -> {
        val turnOff = hasWord(input, "off", "stop", "disable") ||
          input.contains("اطفئ") || input.contains("أطفئ") || input.contains("ايقاف") || input.contains("طفي") || input.contains("اغلق")
        val turnOn = hasWord(input, "on", "enable", "start") ||
          input.contains("شغل") || input.contains("تشغيل") || input.contains("افتح") || input.contains("اضئ") || input.contains("ولع")
        if (turnOff) WeykyIntent.FlashlightIntent(turnOn = false)
        else if (turnOn) WeykyIntent.FlashlightIntent(turnOn = true)
        else WeykyIntent.FlashlightIntent(turnOn = true)
      }

      // 11. Notifications
      input.contains("notification") || input.contains("اشعار") || input.contains("تنبيه") -> {
        val openSettings = input.contains("setting") || input.contains("permission") || input.contains("access") ||
          input.contains("اعدادات") || input.contains("صلاحيه") || input.contains("ضبط")
        WeykyIntent.NotificationIntent(openSettings = openSettings)
      }

      // 12. Weather General (local weather vs remote travel weather or body/phone heat)
      !isRemotePlaceWeather &&
      !input.contains("حراره الجسم") && !input.contains("حراره الهاتف") && !isBatteryKnowledgeQuery &&
      (input.contains("weather") || input.contains("forecast") || hasWord(input, "rain", "raining", "rainy", "sunny", "outside") ||
       input.contains("طقس") || input.contains("الطقس") || hasWord(input, "جو", "الجو") || input.contains("امطار") || input.contains("حراره")) -> {
        WeykyIntent.WeatherIntent(WeatherQueryType.CURRENT, rawInput)
      }

      // 13. Time and Date (current clock/date vs time management or history)
      isClockOrDateQuery(input) -> {
        WeykyIntent.TimeIntent
      }

      // 14. Context
      hasWord(input, "context") || input.contains("where am i") || input.contains("in pocket") || input.contains("on table") ||
      input.contains("سياق") || ((input.contains("وضع") || input.contains("حاله") || input.contains("اين")) && (input.contains("هاتف") || input.contains("جيب") || input.contains("طاوله"))) -> {
        WeykyIntent.ContextIntent
      }

      // 15. Device specs
      !isBiologicalOrKnowledgeDeviceQuery &&
      (hasWord(input, "device", "phone", "status", "specs") || input.contains("device info") || input.contains("phone info") ||
       input.contains("جهاز") || input.contains("هاتف") || input.contains("مواصفات")) -> {
        WeykyIntent.DeviceInfoIntent
      }

      // 16. Help (command list requests)
      input == "help" || input == "commands" || input == "اوامر" || input == "الاوامر" || input.contains("قائمه الاوامر") -> {
        WeykyIntent.HelpIntent
      }

      else -> WeykyIntent.ConversationalIntent(rawInput)
    }
  }
}
