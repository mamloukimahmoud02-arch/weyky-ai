package com.example.core.intelligence.battery

import com.example.core.context.ContextSnapshot
import com.example.core.context.ContextState
import com.example.core.intelligence.ContextDatum
import com.example.core.intelligence.ContextReasoningEngine
import com.example.core.intelligence.ContextRequirements
import com.example.core.intelligence.EpistemicStatus
import com.example.core.intelligence.IntentDomain
import com.example.core.intelligence.SituationSnapshot
import com.example.core.intelligence.conversation.ConversationSession
import com.example.core.intelligence.conversation.ConversationalReasoner
import com.example.core.intelligence.time.SettableTimeProvider
import com.example.core.intent.IntentParser
import com.example.core.intent.WeykyIntent
import com.example.device.battery.BatterySnapshot
import com.example.device.battery.ChargingState
import com.example.device.battery.PowerSource
import com.example.device.calendar.CalendarSummary
import com.example.device.calls.CallSummary
import com.example.device.media.ActiveMediaMetadata
import com.example.device.network.NetworkSnapshot
import com.example.device.network.NetworkType
import com.example.device.notifications.NotificationSummary
import com.example.device.weather.DeviceLocation
import com.example.device.weather.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TimeAwareBatteryReasoningTest {

  private fun createMockSnapshot(
    batteryPct: Int? = 80,
    chargingState: ChargingState = ChargingState.DISCHARGING,
    powerSource: PowerSource = PowerSource.BATTERY,
    batteryAvailable: Boolean = true
  ): SituationSnapshot {
    val batteryDatum = if (batteryAvailable && batteryPct != null) {
      ContextDatum.fact(
        BatterySnapshot(
          percentage = batteryPct,
          chargingState = chargingState,
          powerSource = powerSource,
          temperatureCelsius = 28f,
          voltageMillivolts = 3850,
          healthString = "Good"
        ),
        "BatteryProvider"
      )
    } else {
      ContextDatum.unavailable<BatterySnapshot>("BatteryProvider", "بيانات البطارية غير متوفرة")
    }

    return SituationSnapshot(
      battery = batteryDatum,
      network = ContextDatum.fact(
        NetworkSnapshot(
          isConnected = true,
          isValidated = true,
          type = NetworkType.WIFI,
          typeLabel = "Wi-Fi",
          isMetered = false,
          isVpn = false,
          summary = "Connected to Wi-Fi"
        ),
        "NetworkProvider"
      ),
      physicalContext = ContextDatum.fact(
        ContextSnapshot(state = ContextState.STATIONARY, confidence = 1.0f),
        "ContextEngine"
      ),
      location = ContextDatum.fact(DeviceLocation(23.5880, 58.3829), "LocationProvider"),
      weather = ContextDatum.fact(
        WeatherSnapshot(
          temperatureCelsius = 26f,
          condition = "Sunny",
          locationName = "Muscat",
          isAvailable = true
        ),
        "WeatherProvider"
      ),
      calendar = ContextDatum.fact(CalendarSummary(hasPermission = true, isAvailable = true, nextEvent = null), "CalendarProvider"),
      media = ContextDatum.fact(ActiveMediaMetadata(null, null, null, false), "MediaPlaybackController"),
      calls = ContextDatum.fact(
        CallSummary(
          missedCallsCount = 0,
          unreadMissedCallsCount = 0,
          latestMissedCall = null,
          latestIncomingCall = null,
          latestOutgoingCall = null,
          hasPermission = true
        ),
        "CallLogProvider"
      ),
      notifications = ContextDatum.fact(
        NotificationSummary(
          totalImportantCount = 0,
          latestImportantEvent = null,
          isAccessGranted = true
        ),
        "NotificationRepository"
      )
    )
  }

  // 1. Correct elapsed time calculation between current time and target time
  @Test
  fun test1_correctElapsedTimeBetweenCurrentAndTargetTime() {
    val timeProvider = SettableTimeProvider().apply {
      setTime(14, 0, 0) // 2:00 PM
    }
    val calculator = BatteryForecastCalculator(timeProvider)
    val snapshot = createMockSnapshot(batteryPct = 80)
    val target = BatteryForecastTarget(
      targetHour = 18,
      targetMinute = 0,
      descriptionArabic = "الساعة 6 مساءً",
      descriptionEnglish = "6:00 PM",
      rawMatchedString = "الساعة 6"
    )

    val outcome = calculator.calculateForecast(snapshot, target, includesMusic = false, isArabic = true)
    assertTrue("Outcome should be Success", outcome is BatteryForecastOutcome.Success)
    val success = outcome as BatteryForecastOutcome.Success

    // 14:00 to 18:00 is exactly 4.0 hours
    assertEquals(4.0, success.horizonHours, 0.001)
    assertEquals(4.0, success.scenario.horizonHours, 0.001)
  }

  // 2. Correct projected range using supported assumptions
  @Test
  fun test2_correctProjectedRangeUsingSupportedAssumptions() {
    val timeProvider = SettableTimeProvider().apply {
      setTime(14, 0, 0) // 2:00 PM
    }
    val calculator = BatteryForecastCalculator(timeProvider)
    val snapshot = createMockSnapshot(batteryPct = 80)
    val target = BatteryForecastTarget(18, 0, "الساعة 6 مساءً", "6:00 PM", "الساعة 6")

    // Case A: Standard usage (2% - 4% per hour) over 4 hours -> 8% to 16% drain
    // Expected: 80 - 16 = 64% min, 80 - 8 = 72% max
    val outcomeNormal = calculator.calculateForecast(snapshot, target, includesMusic = false, isArabic = true) as BatteryForecastOutcome.Success
    assertEquals(64, outcomeNormal.estimatedMinPct)
    assertEquals(72, outcomeNormal.estimatedMaxPct)

    // Case B: With music (+4% - 8% per hour -> total 6% - 12% per hour) over 4 hours -> 24% to 48% drain
    // Expected: 80 - 48 = 32% min, 80 - 24 = 56% max
    val outcomeMusic = calculator.calculateForecast(snapshot, target, includesMusic = true, isArabic = true) as BatteryForecastOutcome.Success
    assertEquals(32, outcomeMusic.estimatedMinPct)
    assertEquals(56, outcomeMusic.estimatedMaxPct)
  }

  // 3. Zero or negative projected battery values being bounded correctly to 0%
  @Test
  fun test3_zeroOrNegativeProjectedBatteryBoundedToZero() {
    val timeProvider = SettableTimeProvider().apply {
      setTime(10, 0, 0) // 10:00 AM
    }
    val calculator = BatteryForecastCalculator(timeProvider)
    // Low battery: 15% with 10 hours horizon (until 20:00)
    // Drain: 10 * 2% = 20%, 10 * 4% = 40%. Raw remaining: -25% to -5%
    val snapshot = createMockSnapshot(batteryPct = 15)
    val target = BatteryForecastTarget(20, 0, "الساعة 8 مساءً", "8:00 PM", "الساعة 8")

    val outcome = calculator.calculateForecast(snapshot, target, includesMusic = false, isArabic = true)
    assertTrue("Outcome should be Success with bounded 0", outcome is BatteryForecastOutcome.Success)
    val success = outcome as BatteryForecastOutcome.Success

    assertEquals(0, success.estimatedMinPct)
    assertEquals(0, success.estimatedMaxPct)
    assertTrue(success.synthesis.synthesizedSpeech.contains("0%"))
    assertTrue(success.synthesis.primaryDecision.contains("تنفد") || success.synthesis.primaryDecision.contains("Depletion"))
  }

  // 4. Target time that has already passed today
  @Test
  fun test4_targetTimeAlreadyPassedToday() {
    val timeProvider = SettableTimeProvider().apply {
      setTime(19, 30, 0) // 7:30 PM
    }
    val calculator = BatteryForecastCalculator(timeProvider)
    val snapshot = createMockSnapshot(batteryPct = 70)
    // User asks for 6:00 PM (18:00), which already passed today at 19:30
    val target = BatteryForecastTarget(18, 0, "الساعة 6 مساءً", "6:00 PM", "الساعة 6")

    val outcome = calculator.calculateForecast(snapshot, target, includesMusic = false, isArabic = true)
    assertTrue("Must detect that target time has already passed today", outcome is BatteryForecastOutcome.TargetTimePassed)
    val passed = outcome as BatteryForecastOutcome.TargetTimePassed

    assertTrue(passed.synthesis.synthesizedSpeech.contains("قد مضت بالفعل اليوم"))
    assertTrue(passed.synthesis.synthesizedSpeech.contains("لا يمكن إجراء تقدير زمني لوقت في الماضي"))
  }

  // 5. Missing battery telemetry
  @Test
  fun test5_missingBatteryTelemetryHandledHonestly() {
    val timeProvider = SettableTimeProvider().apply {
      setTime(12, 0, 0)
    }
    val calculator = BatteryForecastCalculator(timeProvider)
    val snapshot = createMockSnapshot(batteryAvailable = false)
    val target = BatteryForecastTarget(18, 0, "المساء (6:00 م)", "evening (6:00 PM)", "المساء")

    val outcome = calculator.calculateForecast(snapshot, target, includesMusic = false, isArabic = true)
    assertTrue("Must detect unavailable battery telemetry", outcome is BatteryForecastOutcome.TelemetryUnavailable)
    val unavailable = outcome as BatteryForecastOutcome.TelemetryUnavailable

    assertTrue(unavailable.synthesis.synthesizedSpeech.contains("غير متوفرة حالياً"))
    assertEquals(0.0f, unavailable.synthesis.confidenceScore, 0.001f)
  }

  // 6. Charging-state handling (never applies discharge formula)
  @Test
  fun test6_chargingStateTreatedSeparatelyWithoutDischargeFormula() {
    val timeProvider = SettableTimeProvider().apply {
      setTime(14, 0, 0)
    }
    val calculator = BatteryForecastCalculator(timeProvider)
    val snapshot = createMockSnapshot(
      batteryPct = 45,
      chargingState = ChargingState.CHARGING,
      powerSource = PowerSource.AC
    )
    val target = BatteryForecastTarget(18, 0, "الساعة 6 مساءً", "6:00 PM", "الساعة 6")

    val outcome = calculator.calculateForecast(snapshot, target, includesMusic = true, isArabic = true)
    assertTrue("Charging state must be recognized", outcome is BatteryForecastOutcome.Charging)
    val charging = outcome as BatteryForecastOutcome.Charging

    assertEquals(45, charging.currentPct)
    assertTrue(charging.synthesis.synthesizedSpeech.contains("قيد الشحن"))
    assertTrue(charging.synthesis.synthesizedSpeech.contains("لن تنفد البطارية"))
  }

  // 7. Preservation of ESTIMATE status and disclosure of assumptions
  @Test
  fun test7_preservationOfEstimateStatusAndDisclosureOfAssumptions() {
    val timeProvider = SettableTimeProvider().apply {
      setTime(12, 0, 0)
    }
    val calculator = BatteryForecastCalculator(timeProvider)
    val snapshot = createMockSnapshot(batteryPct = 75)
    val target = BatteryForecastTarget(18, 0, "المساء (6:00 م)", "evening (6:00 PM)", "المساء")

    val outcome = calculator.calculateForecast(snapshot, target, includesMusic = false, isArabic = true) as BatteryForecastOutcome.Success

    // Epistemic label ESTIMATE must be present in speech
    assertTrue(outcome.synthesis.synthesizedSpeech.contains("ESTIMATE") || outcome.synthesis.synthesizedSpeech.contains("تقدير"))
    // Assumptions regarding factors affecting real battery endurance must be disclosed
    assertTrue(outcome.synthesis.synthesizedSpeech.contains("سطوع الشاشة") || outcome.synthesis.synthesizedSpeech.contains("الشبكة"))
    assertTrue(outcome.synthesis.factsUsed.any { it.contains("القياس الفعلي") })
  }

  // 8. Navigation follow-up retaining previous time horizon and scenario
  @Test
  fun test8_navigationFollowUpRetainsPreviousHorizonAndScenario() {
    val timeProvider = SettableTimeProvider().apply {
      setTime(14, 0, 0)
    }
    val calculator = BatteryForecastCalculator(timeProvider)
    val snapshot = createMockSnapshot(batteryPct = 70)
    val target = BatteryForecastTarget(18, 0, "الساعة 6 مساءً", "6:00 PM", "الساعة 6")

    // Turn 1: User asks for forecast with music
    val initialForecast = calculator.calculateForecast(snapshot, target, includesMusic = true, isArabic = true) as BatteryForecastOutcome.Success
    val scenario = initialForecast.scenario
    assertEquals(4.0, scenario.horizonHours, 0.001)
    assertTrue(scenario.includesMusic)

    // Turn 2: Follow-up adding navigation
    val followUpOutcome = calculator.reasonNavigationFollowUp(scenario, isArabic = true)

    assertEquals(4.0, followUpOutcome.updatedScenario.horizonHours, 0.001)
    assertEquals(scenario.targetDescription, followUpOutcome.updatedScenario.targetDescription)
    assertTrue(followUpOutcome.updatedScenario.includesMusic)
    assertTrue(followUpOutcome.updatedScenario.includesNavigation)
    assertTrue(followUpOutcome.synthesis.synthesizedSpeech.contains("الموسيقى"))
    assertTrue(followUpOutcome.synthesis.synthesizedSpeech.contains("الملاحة"))
  }

  // 9. Unsupported navigation drain model does NOT produce a fabricated numerical result
  @Test
  fun test9_unsupportedNavigationModelDoesNotProduceFabricatedNumericalResult() {
    val calculator = BatteryForecastCalculator()
    val scenario = BatteryForecastScenario(
      targetHour = 18,
      targetMinute = 0,
      targetDescription = "الساعة 6 مساءً",
      horizonHours = 4.0,
      includesMusic = true,
      includesNavigation = false,
      initialBatteryPct = 70,
      estimatedMinPct = 30,
      estimatedMaxPct = 50
    )

    val followUpOutcome = calculator.reasonNavigationFollowUp(scenario, isArabic = true)

    // Must disclose unquantified/unavailable model rather than fabricating a fake % number
    assertTrue(followUpOutcome.synthesis.synthesizedSpeech.contains("عدم توفر نموذج استهلاك مقاس") ||
      followUpOutcome.synthesis.synthesizedSpeech.contains("لا يمكن تقديم نسبة مئوية رقمية محددة"))
    assertTrue(followUpOutcome.synthesis.unavailabilitiesHandled.isNotEmpty())
    // Practical advice to connect car charger
    assertTrue(followUpOutcome.synthesis.recommendations.any { it.contains("شاحن") })
  }

  // 10. Regression: Existing music-only battery estimate
  @Test
  fun test10_regression_existingMusicOnlyBatteryEstimate() {
    val intent = IntentParser.parse("لو شغلت الموسيقى هل ستستهلك البطارية؟")
    assertEquals(WeykyIntent.HypotheticalBatteryMusicIntent, intent)

    val engine = ContextReasoningEngine()
    val snapshot = createMockSnapshot(batteryPct = 65)
    val synthesis = engine.reasonHypotheticalBatteryMusic(snapshot, isArabic = true)

    assertTrue(synthesis.synthesizedSpeech.contains("4% إلى 8%"))
    assertTrue(synthesis.synthesizedSpeech.contains("تقدير إحصائي عام"))
    assertTrue(synthesis.synthesizedSpeech.contains("لم أقم بتشغيل الموسيقى"))
  }

  // 11. Regression: Conversational follow-ups (device info, travel, etc.)
  @Test
  fun test11_regression_existingConversationalFollowUps() {
    val reasoner = ConversationalReasoner()
    val session = ConversationSession()

    // Query 1: Device model
    val res1 = reasoner.reason("ما هو هاتفي؟", session)
    session.recordAssistantTurn(res1.replyText, res1.proactiveSuggestions, res1.topicDetected)
    assertEquals("device_info", session.currentTopic)

    // Follow-up query
    val res2 = reasoner.reason("وماذا تعرف عنه؟", session)
    assertNotNull(res2.replyText)
    assertEquals("device_info", res2.topicDetected)
  }

  // 12. Regression: Compound-command parsing and execution
  @Test
  fun test12_regression_compoundCommandParsingAndExecution() {
    val queryAr = "افتح الموسيقى ثم اخفض الصوت إلى 30%"
    val parsedAr = IntentParser.parse(queryAr)
    assertTrue("Should parse as CompoundIntent", parsedAr is WeykyIntent.CompoundIntent)
    val compoundAr = parsedAr as WeykyIntent.CompoundIntent
    assertEquals(2, compoundAr.steps.size)
    assertTrue(compoundAr.steps[0].intent is WeykyIntent.OpenAppIntent)
    assertTrue(compoundAr.steps[1].intent is WeykyIntent.VolumePercentIntent)

    val queryEn = "Open Spotify, then reduce media volume to 40%"
    val parsedEn = IntentParser.parse(queryEn)
    assertTrue("Should parse as CompoundIntent", parsedEn is WeykyIntent.CompoundIntent)
    val compoundEn = parsedEn as WeykyIntent.CompoundIntent
    assertEquals(2, compoundEn.steps.size)
    assertEquals("Spotify", (compoundEn.steps[0].intent as WeykyIntent.OpenAppIntent).appName)
    assertEquals(40, (compoundEn.steps[1].intent as WeykyIntent.VolumePercentIntent).percent)
  }

  // 13. End-to-end multi-turn conversation test:
  // User: "كم ستتبقى البطارية حتى السادسة إذا استمعت للموسيقى؟"
  // User: "وماذا لو استخدمت الملاحة أيضًا؟"
  @Test
  fun test13_endToEndMultiTurnBatteryNavigationConversation() {
    val timeProvider = SettableTimeProvider().apply {
      setTime(14, 0, 0) // 2:00 PM
    }
    val session = ConversationSession()
    val snapshot = createMockSnapshot(batteryPct = 75)
    val reasoner = ConversationalReasoner(timeProvider = timeProvider)

    // Turn 1: Time-aware query with music
    val query1 = "كم ستتبقى البطارية حتى السادسة إذا استمعت للموسيقى؟"
    val intent1 = IntentParser.parse(query1, session)
    assertTrue("Intent 1 should be TimeAwareBatteryForecastIntent", intent1 is WeykyIntent.TimeAwareBatteryForecastIntent)
    val forecastIntent = intent1 as WeykyIntent.TimeAwareBatteryForecastIntent
    assertEquals(18, forecastIntent.target?.targetHour)
    assertTrue(forecastIntent.includesMusic)

    // Execute via calculator and store scenario in session
    val calculator = BatteryForecastCalculator(timeProvider)
    val outcome1 = calculator.calculateForecast(snapshot, forecastIntent.target!!, forecastIntent.includesMusic, isArabic = true)
    assertTrue(outcome1 is BatteryForecastOutcome.Success)
    session.lastBatteryScenario = (outcome1 as BatteryForecastOutcome.Success).scenario
    session.recordUserTurn(query1)
    session.recordAssistantTurn(outcome1.synthesis.synthesizedSpeech, newTopic = "battery")

    assertEquals("battery", session.currentTopic)
    assertNotNull(session.lastBatteryScenario)
    assertEquals(4.0, session.lastBatteryScenario!!.horizonHours, 0.001)

    // Turn 2: Follow-up adding navigation
    val query2 = "وماذا لو استخدمت الملاحة أيضًا؟"
    val intent2 = IntentParser.parse(query2, session)
    assertTrue("Intent 2 should recognize navigation follow-up", intent2 is WeykyIntent.TimeAwareBatteryForecastIntent)
    val navIntent = intent2 as WeykyIntent.TimeAwareBatteryForecastIntent
    assertTrue(navIntent.isFollowUpNavigation)

    // Reasoner resolves follow-up against previous scenario
    val resFollowUp = reasoner.reason(query2, session)
    assertNotNull(resFollowUp.replyText)
    assertEquals("battery", resFollowUp.topicDetected)
    // Honest limitation disclosed
    assertTrue(resFollowUp.replyText.contains("الملاحة"))
    assertTrue(resFollowUp.replyText.contains("الموسيقى"))
    assertTrue(resFollowUp.replyText.contains("عدم توفر نموذج استهلاك مقاس") || resFollowUp.replyText.contains("لا يمكن تقديم نسبة مئوية"))
  }
}
