package com.example.core.intelligence

import com.example.core.context.ContextSnapshot
import com.example.core.context.ContextState
import com.example.core.intelligence.battery.BatteryForecastCalculator
import com.example.core.intelligence.battery.BatteryForecastOutcome
import com.example.core.intelligence.battery.BatteryForecastTarget
import com.example.core.intelligence.battery.BatteryTargetTimeExtractor
import com.example.core.intelligence.conversation.ConversationSession
import com.example.core.intelligence.conversation.ConversationalReasoner
import com.example.core.intelligence.conversation.LocalConversationalEngine
import com.example.core.intelligence.time.SettableTimeProvider
import com.example.core.intent.ExecutionResult
import com.example.core.intent.IntentParser
import com.example.core.intent.WeykyIntent
import com.example.core.intent.handlers.ContextActionHandler
import com.example.device.battery.BatterySnapshot
import com.example.device.battery.ChargingState
import com.example.device.battery.PowerSource
import com.example.device.calendar.CalendarEventItem
import com.example.device.calendar.CalendarSummary
import com.example.device.calls.CallSummary
import com.example.device.media.ActiveMediaMetadata
import com.example.device.network.NetworkSnapshot
import com.example.device.network.NetworkType
import com.example.device.notifications.NotificationSummary
import com.example.device.weather.DeviceLocation
import com.example.device.weather.WeatherSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualIntelligenceReliabilityTest {

  private fun createSnapshot(
    tempC: Float? = 26f,
    condition: String? = "Clear",
    weatherAvailable: Boolean = true,
    batteryPct: Int? = 75,
    chargingState: ChargingState = ChargingState.DISCHARGING,
    batteryAvailable: Boolean = true,
    nextEvent: CalendarEventItem? = null
  ): SituationSnapshot {
    val batteryDatum = if (batteryAvailable && batteryPct != null) {
      ContextDatum.fact(
        BatterySnapshot(
          percentage = batteryPct,
          chargingState = chargingState,
          powerSource = PowerSource.BATTERY,
          temperatureCelsius = 28f,
          voltageMillivolts = 3850,
          healthString = "Good"
        ),
        "BatteryProvider"
      )
    } else {
      ContextDatum.unavailable<BatterySnapshot>("BatteryProvider", "بيانات البطارية غير متوفرة")
    }

    val weatherDatum = if (weatherAvailable && tempC != null) {
      ContextDatum.fact(
        WeatherSnapshot(
          temperatureCelsius = tempC,
          condition = condition,
          locationName = "Muscat",
          isAvailable = true
        ),
        "WeatherProvider"
      )
    } else {
      ContextDatum.unavailable<WeatherSnapshot>("WeatherProvider", "بيانات الطقس غير متوفرة")
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
          summary = "Connected"
        ),
        "NetworkProvider"
      ),
      physicalContext = ContextDatum.fact(
        ContextSnapshot(state = ContextState.STATIONARY, confidence = 1.0f),
        "ContextEngine"
      ),
      location = ContextDatum.fact(DeviceLocation(23.5880, 58.3829), "LocationProvider"),
      weather = weatherDatum,
      calendar = ContextDatum.fact(
        CalendarSummary(
          hasPermission = true,
          isAvailable = true,
          nextEvent = nextEvent
        ),
        "CalendarProvider"
      ),
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

  // ==========================================
  // SECTION A: Contextual Reasoning & Telemetry Grounding
  // ==========================================

  @Test
  fun testA1_missingTelemetryReportedHonestlyWithoutInvention() {
    val timeProvider = SettableTimeProvider().apply { setTime(10, 0, 0) }
    val engine = ContextReasoningEngine(timeProvider)
    val snapshot = createSnapshot(weatherAvailable = false, batteryAvailable = false)

    val req = ContextRequirements(
      domain = IntentDomain.OUTDOOR_DEPARTURE,
      required = setOf(ContextField.WEATHER, ContextField.BATTERY)
    )
    val synthesis = engine.synthesize(req, snapshot, "هل اخرج الان؟")

    // Must report missing weather in unavailabilities rather than inventing fake temp
    assertTrue(synthesis.unavailabilitiesHandled.any { it.contains("الطقس") || it.contains("weather") })
    // No fabricated temperature degrees in speech
    assertFalse(synthesis.synthesizedSpeech.contains("°م"))
  }

  @Test
  fun testA2_outdoorDepartureExplainsMaterialFactors() {
    val engine = ContextReasoningEngine()
    val event = CalendarEventItem(
      id = 1,
      title = "اجتماع العمل",
      description = null,
      location = "المكتب",
      startMillis = System.currentTimeMillis() + 30 * 60 * 1000,
      endMillis = System.currentTimeMillis() + 90 * 60 * 1000,
      minutesUntilStart = 30
    )
    val snapshot = createSnapshot(
      tempC = 18f,
      condition = "Rain / أمطار",
      batteryPct = 14,
      chargingState = ChargingState.DISCHARGING,
      nextEvent = event
    )

    val req = ContextRequirements(
      domain = IntentDomain.OUTDOOR_DEPARTURE,
      required = setOf(ContextField.WEATHER, ContextField.CALENDAR, ContextField.BATTERY)
    )
    val synthesis = engine.synthesize(req, snapshot, "هل اخرج الان؟")

    // Rain advice: umbrella
    assertTrue("Should mention umbrella for rain", synthesis.synthesizedSpeech.contains("مظلة"))
    // Low battery warning (<20%)
    assertTrue("Should warn about low battery", synthesis.synthesizedSpeech.contains("14%"))
    // Event proximity mentioned
    assertTrue("Should mention upcoming appointment", synthesis.synthesizedSpeech.contains("اجتماع العمل"))
    // Actionable next step is power saving
    assertNotNull(synthesis.nextStep)
    assertTrue(synthesis.nextStep!!.label.contains("توفير الطاقة"))
  }

  @Test
  fun testA3_contradictoryOrIncompleteContextDoesNotProduceUnjustifiedCertainty() {
    val engine = ContextReasoningEngine()
    val snapshot = createSnapshot(weatherAvailable = false, batteryPct = 80)

    val req = ContextRequirements(
      domain = IntentDomain.OUTDOOR_DEPARTURE,
      required = setOf(ContextField.WEATHER, ContextField.BATTERY)
    )
    val synthesis = engine.synthesize(req, snapshot, "هل اخرج الان؟")

    // Confidence should be degraded when weather telemetry is missing
    assertTrue("Confidence must not be maximum when data is missing", synthesis.confidenceScore < 0.95f)
  }

  // ==========================================
  // SECTION B: Time-Aware Battery Conversations & Stale Cache Isolation
  // ==========================================

  @Test
  fun testB1_fullMultiTurnBatteryConversation_musicThenNavigationThenUnrelatedTopic() {
    val timeProvider = SettableTimeProvider().apply { setTime(14, 0, 0) } // 2:00 PM
    val session = ConversationSession()
    val reasoner = ConversationalReasoner(timeProvider = timeProvider)
    val calculator = BatteryForecastCalculator(timeProvider)
    val snapshot = createSnapshot(batteryPct = 80)

    // 1. Turn 1: Battery estimate until 6 PM with music
    val query1 = "كم ستتبقى البطارية حتى السادسة إذا استمعت للموسيقى؟"
    val intent1 = IntentParser.parse(query1, session)
    assertTrue(intent1 is WeykyIntent.TimeAwareBatteryForecastIntent)
    val forecastIntent = intent1 as WeykyIntent.TimeAwareBatteryForecastIntent

    val outcome1 = calculator.calculateForecast(snapshot, forecastIntent.target!!, forecastIntent.includesMusic, isArabic = true)
    assertTrue(outcome1 is BatteryForecastOutcome.Success)
    val success1 = outcome1 as BatteryForecastOutcome.Success

    session.lastBatteryScenario = success1.scenario
    session.recordUserTurn(query1)
    session.recordAssistantTurn(success1.synthesis.synthesizedSpeech, newTopic = "battery")

    assertEquals("battery", session.currentTopic)
    assertEquals(4.0, session.lastBatteryScenario!!.horizonHours, 0.001)
    assertTrue(session.lastBatteryScenario!!.includesMusic)
    assertFalse(session.lastBatteryScenario!!.includesNavigation)
    // Modeled remaining range between 32% and 56% (80 - 48 to 80 - 24)
    assertEquals(32, success1.estimatedMinPct)
    assertEquals(56, success1.estimatedMaxPct)
    assertTrue(success1.synthesis.synthesizedSpeech.contains("ESTIMATE") || success1.synthesis.synthesizedSpeech.contains("تقدير"))

    // 2. Turn 2: Follow-up adding navigation
    val query2 = "وماذا لو استخدمت الملاحة أيضًا؟"
    val intent2 = IntentParser.parse(query2, session)
    assertTrue(intent2 is WeykyIntent.TimeAwareBatteryForecastIntent)
    assertTrue((intent2 as WeykyIntent.TimeAwareBatteryForecastIntent).isFollowUpNavigation)

    val resFollowUp = reasoner.reason(query2, session)
    // Preserves original scenario
    assertEquals(4.0, session.lastBatteryScenario!!.horizonHours, 0.001)
    assertTrue(session.lastBatteryScenario!!.includesNavigation)
    assertTrue(session.lastBatteryScenario!!.includesMusic)
    // Acknowledges limitation without fabricating numerical percentage
    assertTrue(resFollowUp.replyText.contains("عدم توفر نموذج استهلاك مقاس") || resFollowUp.replyText.contains("لا يمكن تقديم نسبة مئوية"))
    session.recordUserTurn(query2)
    session.recordAssistantTurn(resFollowUp.replyText, newTopic = "battery")

    // 3. Turn 3: Unrelated question switches topic to "weather"
    val query3 = "ما حالة الطقس اليوم؟"
    session.recordUserTurn(query3)
    assertEquals("weather", session.currentTopic)
    // Verification: stale battery scenario is cleared!
    assertNull("Unrelated topic must clear stale battery scenario", session.lastBatteryScenario)

    // 4. Turn 4: Ambiguous follow-up now must NOT invoke stale battery scenario
    val query4 = "وماذا عن المساء؟"
    val intent4 = IntentParser.parse(query4, session)
    // Resolves as weather intent, NOT battery forecast
    assertTrue("Subsequent query must not inherit battery scenario", intent4 is WeykyIntent.WeatherIntent)
  }

  @Test
  fun testB2_batteryBoundaryCases() {
    val timeProvider = SettableTimeProvider().apply { setTime(16, 0, 0) }
    val calculator = BatteryForecastCalculator(timeProvider)

    // Case A: Charging state
    val chargingSnap = createSnapshot(batteryPct = 50, chargingState = ChargingState.CHARGING)
    val targetFuture = BatteryForecastTarget(18, 0, "الساعة 6 مساءً", "6:00 PM", "الساعة 6")
    val resCharging = calculator.calculateForecast(chargingSnap, targetFuture, false, isArabic = true)
    assertTrue(resCharging is BatteryForecastOutcome.Charging)
    assertTrue(resCharging.synthesis.synthesizedSpeech.contains("قيد الشحن"))

    // Case B: Target time in the past today
    val timeProviderPast = SettableTimeProvider().apply { setTime(19, 0, 0) } // 7 PM
    val calcPast = BatteryForecastCalculator(timeProviderPast)
    val normalSnap = createSnapshot(batteryPct = 60)
    val resPast = calcPast.calculateForecast(normalSnap, targetFuture, false, isArabic = true)
    assertTrue(resPast is BatteryForecastOutcome.TargetTimePassed)
    assertTrue(resPast.synthesis.synthesizedSpeech.contains("قد مضت بالفعل اليوم"))

    // Case C: Missing battery telemetry
    val missingSnap = createSnapshot(batteryAvailable = false)
    val resMissing = calculator.calculateForecast(missingSnap, targetFuture, false, isArabic = true)
    assertTrue(resMissing is BatteryForecastOutcome.TelemetryUnavailable)
    assertEquals(0.0f, resMissing.synthesis.confidenceScore, 0.001f)
  }

  // ==========================================
  // SECTION C: Compound-Command Execution Regression
  // ==========================================

  @Test
  fun testC1_compoundCommand_parsedAndSequenced() {
    val query = "افتح الموسيقى ثم اخفض الصوت إلى 30%"
    val intent = IntentParser.parse(query)
    assertTrue(intent is WeykyIntent.CompoundIntent)
    val compound = intent as WeykyIntent.CompoundIntent
    assertEquals(2, compound.steps.size)
    assertTrue(compound.steps[0].intent is WeykyIntent.OpenAppIntent)
    assertTrue(compound.steps[1].intent is WeykyIntent.VolumePercentIntent)
    assertEquals(30, (compound.steps[1].intent as WeykyIntent.VolumePercentIntent).percent)
  }

  @Test
  fun testC2_compoundCommand_unsupportedSegmentRejectsExecution() {
    val query = "افتح سبوتيفاي ثم خربشات_غير_مفهومة_بتاتا"
    val intent = IntentParser.parse(query)
    assertFalse("Unsupported segment must prevent compound intent creation", intent is WeykyIntent.CompoundIntent)
  }

  // ==========================================
  // SECTION D: Dialogue State & Ambiguous Clarification
  // ==========================================

  @Test
  fun testD1_ambiguousQueryTriggersClarification() {
    val reasoner = ConversationalReasoner()
    val session = ConversationSession()

    // Short ambiguous query without context
    val result = reasoner.reason("مساعدة", session)
    assertEquals("ambiguous_clarification", result.topicDetected)
    assertTrue(result.replyText.contains("غير محدد بما يكفي") || result.replyText.contains("ambiguous"))
  }

  @Test
  fun testD2_bilingualFollowUpsConsistency() {
    val reasoner = ConversationalReasoner()

    // Arabic follow-up on phone
    val sessionAr = ConversationSession().apply {
      currentTopic = "device_info"
      lastReferencedSubject = "phone"
    }
    val resAr = reasoner.reason("وماذا تعرف عنه؟", sessionAr)
    assertEquals("device_info", resAr.topicDetected)
    assertNotNull(resAr.replyText)

    // English follow-up on phone
    val sessionEn = ConversationSession().apply {
      currentTopic = "device_info"
      lastReferencedSubject = "phone"
    }
    val resEn = reasoner.reason("what about it?", sessionEn)
    assertEquals("device_info", resEn.topicDetected)
    assertNotNull(resEn.replyText)
  }

  // ==========================================
  // SECTION E: Epistemic Integrity & Proactive Suggestions
  // ==========================================

  @Test
  fun testE1_epistemicStatusSemanticsPreserved() {
    val factDatum = ContextDatum.fact("45%", "BatteryProvider")
    assertEquals(EpistemicStatus.FACT, factDatum.status)
    assertTrue(factDatum.isAvailable)

    val calcDatum = ContextDatum.calculation(4.0, "TimeProvider")
    assertEquals(EpistemicStatus.CALCULATION, calcDatum.status)

    val estimateDatum = ContextDatum.estimate("32% - 56%", "DischargeModel")
    assertEquals(EpistemicStatus.ESTIMATE, estimateDatum.status)

    val unavailDatum = ContextDatum.unavailable<String>("WeatherProvider", "Offline")
    assertEquals(EpistemicStatus.UNAVAILABLE, unavailDatum.status)
    assertFalse(unavailDatum.isAvailable)
  }

  @Test
  fun testE2_proactiveSuggestionsGroundedInTelemetry() {
    val engine = ContextReasoningEngine()
    val snapshot = createSnapshot(batteryPct = 12, chargingState = ChargingState.DISCHARGING)

    val req = ContextRequirements(
      domain = IntentDomain.BATTERY_SUFICIENCY,
      required = setOf(ContextField.BATTERY)
    )
    val synthesis = engine.synthesize(req, snapshot, "هل البطارية ستكفي؟")

    // When battery is critically low, recommendation to charge or enable battery saver
    assertTrue(synthesis.recommendations.any { it.contains("شاحن") || it.contains("توفير الطاقة") })
  }

  @Test
  fun testE3_unindexedKnowledgeQueriesHonestFallback() {
    val reasoner = ConversationalReasoner()
    val session = ConversationSession()

    val res = reasoner.reason("ما هي نظرية الأوتار الفائقة بأبعادها الإحدى عشرة؟", session)
    assertEquals("unindexed_query", res.topicDetected)
    assertTrue(res.replyText.contains("غير مسجلة في قاعدة المعرفة المحلية"))
  }

  // ==========================================
  // SECTION F: Shared Reasoning Engine in LocalConversationalEngine
  // ==========================================

  @Test
  fun testF1_sharedContextReasoningEngineInLocalConversationalEngine() = runBlocking {
    val timeProvider = SettableTimeProvider().apply { setTime(14, 0, 0) }
    val sharedEngine = ContextReasoningEngine(timeProvider)

    val conversationalEngine = LocalConversationalEngine(
      situationSnapshotBuilder = null,
      deviceInfoProvider = null,
      contextReasoningEngine = sharedEngine,
      timeProvider = timeProvider
    )

    // Direct response on unindexed topic uses conversational reasoner
    val res = conversationalEngine.respond("ما هي عاصمة مدغشقر القديمة؟")
    assertNotNull(res.replyText)
    assertTrue(res.replyText.contains("غير مسجلة في قاعدة المعرفة المحلية"))
  }
}
