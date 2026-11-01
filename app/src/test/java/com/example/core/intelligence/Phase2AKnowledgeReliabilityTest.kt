package com.example.core.intelligence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.capabilities.CapabilityDetector
import com.example.core.context.ContextSnapshot
import com.example.core.context.ContextState
import com.example.core.intelligence.conversation.ConversationSession
import com.example.core.intelligence.conversation.ConversationalReasoner
import com.example.core.intelligence.conversation.LocalConversationalEngine
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.conversation.ParticipantRole
import com.example.core.intent.ExecutionResult
import com.example.core.intent.IntentExecutor
import com.example.core.intent.IntentParser
import com.example.core.intent.WeykyIntent
import com.example.core.intent.handlers.AppActionHandler
import com.example.core.intent.handlers.AudioActionHandler
import com.example.core.intent.handlers.BatteryActionHandler
import com.example.core.intent.handlers.CallActionHandler
import com.example.core.intent.handlers.ContextActionHandler
import com.example.core.intent.handlers.FlashlightActionHandler
import com.example.core.intent.handlers.MediaActionHandler
import com.example.core.intent.handlers.NetworkActionHandler
import com.example.core.intent.handlers.NotificationActionHandler
import com.example.core.intent.handlers.SettingsActionHandler
import com.example.core.intent.handlers.StorageMemoryActionHandler
import com.example.core.intent.handlers.WeatherActionHandler
import com.example.device.apps.AppResolver
import com.example.device.audio.AudioController
import com.example.device.battery.BatteryProvider
import com.example.device.calls.CallLogProvider
import com.example.device.calls.ContactLookupProvider
import com.example.device.flashlight.FlashlightController
import com.example.device.media.MediaPlaybackController
import com.example.device.memory.MemoryProvider
import com.example.device.network.NetworkProvider
import com.example.device.storage.StorageProvider
import com.example.device.weather.LocationProvider
import com.example.device.weather.RealWeatherProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2A & 2A.1 Regression & Verification Test Suite:
 * Covers Intent Routing, Knowledge Retrieval, Conservative Arabic Normalization,
 * Multi-Turn Conversation Continuity, Fallback Session Persistence, Topic Bias Prevention,
 * Arithmetic Safety, and Separated On-Device Latency Benchmarks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2AKnowledgeReliabilityTest {

  private val reasoner = ConversationalReasoner()

  // =========================================================================
  // 1. Direct Arabic Knowledge Questions
  // =========================================================================
  @Test
  fun test01_directArabicKnowledgeQuestions() {
    val session = ConversationSession()

    val resLight = reasoner.reason("ما هي سرعة الضوء؟", session)
    assertEquals("physics_universe", resLight.topicDetected)
    assertTrue(resLight.replyText.contains("300,000"))

    val resAi = reasoner.reason("ما هو الذكاء الاصطناعي؟", session)
    assertEquals("ai_concept", resAi.topicDetected)
    assertTrue(resAi.replyText.contains("الذكاء الاصطناعي"))

    val resSalalah = reasoner.reason("حدثني عن صلالة", session)
    assertEquals("salalah_tourism", resSalalah.topicDetected)
    assertTrue(resSalalah.replyText.contains("ظفار"))

    val resCapitals = reasoner.reason("ما هي عاصمة الأردن؟", session)
    assertEquals("capitals_arab", resCapitals.topicDetected)
    assertTrue(resCapitals.replyText.contains("عمّان"))

    val resMorocco = reasoner.reason("ما هي عاصمة المغرب؟", session)
    assertEquals("capitals_arab", resMorocco.topicDetected)
    assertTrue(resMorocco.replyText.contains("الرباط"))
  }

  // =========================================================================
  // 2. Direct English Knowledge Questions
  // =========================================================================
  @Test
  fun test02_directEnglishKnowledgeQuestions() {
    val session = ConversationSession()

    val resLight = reasoner.reason("What is the speed of light?", session)
    assertEquals("physics_universe", resLight.topicDetected)
    assertTrue(resLight.replyText.contains("300,000 km/s"))

    val resMl = reasoner.reason("Tell me about machine learning and artificial intelligence", session)
    assertEquals("ai_concept", resMl.topicDetected)
    assertTrue(resMl.replyText.contains("Artificial Intelligence"))

    val resSleep = reasoner.reason("How to overcome insomnia and improve sleep?", session)
    assertEquals("sleep_wellness", resSleep.topicDetected)
    assertTrue(resSleep.replyText.contains("18-20°C"))

    val resCapitals = reasoner.reason("What is the capital of Japan?", session)
    assertEquals("capitals_arab", resCapitals.topicDetected)
    assertTrue(resCapitals.replyText.contains("Tokyo"))
  }

  // =========================================================================
  // 3. Arabic Normalization: Tashkeel, Hamzas, Tatweel, Ta-Marbuta, Alef-Maqsura
  // =========================================================================
  @Test
  fun test03_arabicNormalizationWithTashkeelTatweelAndHamzas() {
    val raw = "  مَا هِيَ   سُرْعَةُ الضَّوْءِـــ فِي الفَضَاءِ؟!!  "
    val norm = LocalKnowledgeBase.normalizeArabic(raw)
    assertEquals("ما هي سرعه الضوء في الفضاء", norm)

    val raw2 = "أُرِيدُ نَصَائِحَ لِلْحِفَاظِ عَلَى الْبَطَّارِيَّةِ..."
    val norm2 = LocalKnowledgeBase.normalizeArabic(raw2)
    assertEquals("اريد نصايح للحفاظ علي البطاريه", norm2)

    val topic1 = LocalKnowledgeBase.findMatchingTopic(raw)
    assertNotNull(topic1)
    assertEquals("physics_universe", topic1!!.id)

    val topic2 = LocalKnowledgeBase.findMatchingTopic(raw2)
    assertNotNull(topic2)
    assertEquals("battery_care", topic2!!.id)

    val rawMixed = "كَيْفَ يَعْمَلُ   AI  مَحَلِّيّاً؟"
    val normMixed = LocalKnowledgeBase.normalizeArabic(rawMixed)
    assertEquals("كيف يعمل ai محليا", normMixed)
    val topicMixed = LocalKnowledgeBase.findMatchingTopic(rawMixed)
    assertNotNull(topicMixed)
    assertEquals("ai_concept", topicMixed!!.id)
  }

  // =========================================================================
  // 4. Word Order Variations & Prefix Resilience
  // =========================================================================
  @Test
  fun test04_wordOrderVariationsAndPrefixMatching() {
    // Reversed / separated word order for multi-word keywords
    val t1 = LocalKnowledgeBase.findMatchingTopic("كيف يمكنني للبطارية الحفاظ عليها؟")
    assertNotNull(t1)
    assertEquals("battery_care", t1!!.id)

    val t2 = LocalKnowledgeBase.findMatchingTopic("ما هي في الفراغ للضوء سرعته؟")
    // "للضوء" + "سرعه"
    val t2Alt = LocalKnowledgeBase.findMatchingTopic("كم تبلغ للضوء سرعة انتقاله؟")
    assertNotNull(t2Alt)
    assertEquals("physics_universe", t2Alt!!.id)

    val t3 = LocalKnowledgeBase.findMatchingTopic("كيف أتمكن من الوقت وتنظيمه للدراسة؟")
    assertNotNull(t3)
    assertEquals("productivity", t3!!.id)

    // Ensure short English keyword "ai" does NOT falsely match inside words like "rain", "wait", "explain", "mountain"
    val falsePositiveCheck = LocalKnowledgeBase.findMatchingTopic("Why do mountains have cold air?")
    assertNull("Short keyword 'ai' must not match inside 'mountain' or 'air'", falsePositiveCheck)
  }

  // =========================================================================
  // 5. Device Commands Must Remain Device Commands (Zero Regression)
  // =========================================================================
  @Test
  fun test05_deviceCommandsPreserved() {
    assertTrue(IntentParser.parse("افتح الإعدادات") is WeykyIntent.OpenAppIntent || IntentParser.parse("الإعدادات") is WeykyIntent.SettingsIntent)
    assertTrue(IntentParser.parse("ضبط الشاشة") is WeykyIntent.SettingsIntent)
    assertTrue(IntentParser.parse("كم نسبة البطارية؟") is WeykyIntent.BatteryIntent)
    assertTrue(IntentParser.parse("شغّل المصباح") is WeykyIntent.FlashlightIntent)
    assertTrue(IntentParser.parse("أطفئ الكشاف") is WeykyIntent.FlashlightIntent)
    assertTrue(IntentParser.parse("Turn on the light") is WeykyIntent.FlashlightIntent)
    assertTrue(IntentParser.parse("كم الرام المتبقية؟") is WeykyIntent.RamIntent)
    assertTrue(IntentParser.parse("كم الساعة الآن؟") is WeykyIntent.TimeIntent)
    assertTrue(IntentParser.parse("ما هو تاريخ اليوم؟") is WeykyIntent.TimeIntent)
    assertTrue(IntentParser.parse("ما حالة الطقس؟") is WeykyIntent.WeatherIntent)
  }

  // =========================================================================
  // 6. Knowledge Questions Containing Device-Like Words Must Route to Knowledge
  // =========================================================================
  @Test
  fun test06_knowledgeQuestionsWithDeviceWordsNotHijacked() {
    // "light" in "What is the speed of light?" must NOT trigger FlashlightIntent
    val lightIntent = IntentParser.parse("What is the speed of light?")
    assertTrue("Expected ConversationalIntent but was $lightIntent", lightIntent is WeykyIntent.ConversationalIntent)

    // Battery care advice must NOT trigger BatteryIntent status readout
    val batteryCareIntent = IntentParser.parse("نصائح للحفاظ على البطارية")
    assertTrue("Expected ConversationalIntent but was $batteryCareIntent", batteryCareIntent is WeykyIntent.ConversationalIntent)
    val batteryCareRes = reasoner.reason("نصائح للحفاظ على البطارية", ConversationSession())
    assertEquals("battery_care", batteryCareRes.topicDetected)

    // Time management must NOT trigger TimeIntent clock readout
    val timeMgmtIntent = IntentParser.parse("تنظيم الوقت")
    assertTrue("Expected ConversationalIntent but was $timeMgmtIntent", timeMgmtIntent is WeykyIntent.ConversationalIntent)
    val timeMgmtRes = reasoner.reason("تنظيم الوقت", ConversationSession())
    assertEquals("productivity", timeMgmtRes.topicDetected)

    // Salalah visit time must NOT trigger TimeIntent
    val salalahTimeIntent = IntentParser.parse("متى أفضل وقت لزيارة صلالة؟")
    assertTrue("Expected ConversationalIntent but was $salalahTimeIntent", salalahTimeIntent is WeykyIntent.ConversationalIntent)

    // Historical questions with "تاريخ" must NOT trigger TimeIntent
    val historyIntent = IntentParser.parse("ما هو تاريخ الحضارة الإسلامية؟")
    assertTrue("Expected ConversationalIntent but was $historyIntent", historyIntent is WeykyIntent.ConversationalIntent)

    // Human memory questions must NOT trigger RamIntent
    val memoryIntent = IntentParser.parse("كيف أقوي ذاكرتي للحفظ؟")
    assertTrue("Expected ConversationalIntent but was $memoryIntent", memoryIntent is WeykyIntent.ConversationalIntent)

    // Questions with "نجوم" (contains substring "جو") must NOT trigger WeatherIntent
    val starsIntent = IntentParser.parse("كم عدد النجوم والكواكب في الفضاء؟")
    assertTrue("Expected ConversationalIntent but was $starsIntent", starsIntent is WeykyIntent.ConversationalIntent)

    // English questions containing substring "hi" ("What is machine learning?") must NOT trigger HelpIntent
    val mlIntent = IntentParser.parse("What is machine learning?")
    assertTrue("Expected ConversationalIntent but was $mlIntent", mlIntent is WeykyIntent.ConversationalIntent)
  }

  // =========================================================================
  // 7. Contextual Device Queries Preserved
  // =========================================================================
  @Test
  fun test07_contextualQueriesPreserved() {
    val departIntent = IntentParser.parse("هل أخرج الآن؟")
    assertTrue(departIntent is WeykyIntent.ContextualQueryIntent)
    assertEquals(IntentDomain.OUTDOOR_DEPARTURE, (departIntent as WeykyIntent.ContextualQueryIntent).domain)

    val scheduleIntent = IntentParser.parse("هل سأصل في الموعد؟")
    assertTrue(scheduleIntent is WeykyIntent.ContextualQueryIntent)
    assertEquals(IntentDomain.APPOINTMENT_TIMING, (scheduleIntent as WeykyIntent.ContextualQueryIntent).domain)

    val batterySufficiencyIntent = IntentParser.parse("هل البطارية ستكفي؟")
    assertTrue(batterySufficiencyIntent is WeykyIntent.ContextualQueryIntent)
    assertEquals(IntentDomain.BATTERY_SUFICIENCY, (batterySufficiencyIntent as WeykyIntent.ContextualQueryIntent).domain)
  }

  // =========================================================================
  // 8. Arithmetic Evaluation (Arabic-Indic & Western Digits, Precedence, Zero Division, Non-Math Guard)
  // =========================================================================
  @Test
  fun test08_arithmeticEvaluationAndSafety() {
    // Basic multiplication
    val multIntent = IntentParser.parse("كم يساوي 25 × 4؟")
    assertTrue(multIntent is WeykyIntent.ConversationalIntent)
    val multRes = LocalKnowledgeBase.evaluateArithmetic("كم يساوي 25 × 4؟")
    assertNotNull(multRes)
    assertTrue(multRes!!.contains("100"))

    // Arabic-Indic digits
    val arabicDigitsRes = LocalKnowledgeBase.evaluateArithmetic("احسب ٢٥ × ٤")
    assertNotNull(arabicDigitsRes)
    assertTrue(arabicDigitsRes!!.contains("100"))

    // Operator precedence: 10 + 5 * 2 = 20 (not 30)
    val precedenceRes = LocalKnowledgeBase.evaluateArithmetic("احسب 10 + 5 * 2")
    assertNotNull(precedenceRes)
    assertTrue("Expected 20 with operator precedence but got: $precedenceRes", precedenceRes!!.endsWith("= 20"))

    // Division by zero safety
    val divZeroRes = LocalKnowledgeBase.evaluateArithmetic("احسب 15 ÷ 0")
    assertNotNull(divZeroRes)
    assertTrue(divZeroRes!!.contains("صفر") || divZeroRes.contains("zero"))

    // Incomplete / invalid expression returns null without crash
    assertNull(LocalKnowledgeBase.evaluateArithmetic("احسب 25 *"))
    assertNull(LocalKnowledgeBase.evaluateArithmetic("كم يساوي"))

    // Non-math knowledge query containing numbers/hyphens must NOT be hijacked as arithmetic
    assertNull(LocalKnowledgeBase.evaluateArithmetic("ما هي قاعدة 20-20-20 لراحة العين؟"))
    assertNull(LocalKnowledgeBase.evaluateArithmetic("ماذا حدث في تاريخ 2024/10؟"))
  }

  // =========================================================================
  // 9. Multi-Turn Conversation (3 to 4 Turns with Pronouns & Elaborations)
  // =========================================================================
  @Test
  fun test09_multiTurnConversationThreeToFourTurns() = runBlocking {
    val engine = LocalConversationalEngine()

    // Turn 1: Initial question on Physics/Universe
    val turn1 = engine.respond("ما هي سرعة الضوء؟")
    assertEquals("physics_universe", turn1.topic)
    assertTrue(turn1.replyText.contains("300,000"))

    // Turn 2: Follow-up elaboration ("اشرح لي ذلك")
    val turn2 = engine.respond("اشرح لي ذلك")
    assertEquals("physics_universe", turn2.topic)
    assertTrue("Follow-up should provide new elaboration, not fallback", turn2.replyText.contains("السنة الضوئية") || turn2.replyText.contains("النسبية"))
    assertFalse("Follow-up should not repeat exact initial summary", turn2.replyText == turn1.replyText)

    // Turn 3: Follow-up on formation ("كيف تتكون؟")
    val turn3 = engine.respond("كيف تتكون؟")
    assertEquals("physics_universe", turn3.topic)
    assertTrue(turn3.replyText.contains("السديم الشمسي") || turn3.replyText.contains("الجاذبية"))

    // Turn 4: Follow-up on visibility ("وهل يمكن رؤيتها؟")
    val turn4 = engine.respond("وهل يمكن رؤيتها؟")
    assertEquals("physics_universe", turn4.topic)
    assertTrue(turn4.replyText.contains("بالعين المجردة") || turn4.replyText.contains("المشتري"))
  }

  // =========================================================================
  // 10. Topic Switching & Salalah Multi-Turn Follow-Up in Same Session
  // =========================================================================
  @Test
  fun test10_topicSwitchingAndSalalahContinuity() = runBlocking {
    val engine = LocalConversationalEngine()

    // Turn 1: Salalah topic
    val t1 = engine.respond("حدثني عن صلالة")
    assertEquals("salalah_tourism", t1.topic)

    // Turn 2: Follow-up on best time to visit Salalah (previously failed due to "salalah_tourism" vs "travel" mismatch)
    val t2 = engine.respond("متى أفضل وقت لزيارتها؟")
    assertEquals("salalah_tourism", t2.topic)
    assertTrue(t2.replyText.contains("يوليو") || t2.replyText.contains("أغسطس"))

    // Turn 3: Follow-up on weather there ("كيف الطقس هناك؟")
    val t3 = engine.respond("كيف الطقس هناك؟")
    assertEquals("salalah_tourism", t3.topic)
    assertTrue(t3.replyText.contains("الخريف") || t3.replyText.contains("معتدلاً"))

    // Turn 4: Explicit switch to Sleep Wellness topic
    val t4 = engine.respond("ما هي نصائح النوم الصحي؟")
    assertEquals("sleep_wellness", t4.topic)
    assertEquals("sleep_wellness", engine.session.currentTopic)

    // Turn 5: Follow-up now resolves against the NEW topic (sleep_wellness), NOT stale Salalah context
    val t5 = engine.respond("وما تأثير القهوة عليه؟")
    assertEquals("sleep_wellness", t5.topic)
    assertTrue(t5.replyText.contains("الكافيين"))
  }

  // =========================================================================
  // 11. Unindexed Local Queries (Honest Fallback Without Fabrication)
  // =========================================================================
  @Test
  fun test11_unindexedQueriesHonestFallback() {
    val session = ConversationSession()

    // Bare country name in a historical question must NOT falsely match capitals_arab
    val resHistory = reasoner.reason("ما هو تاريخ الدولة الأموية في دمشق؟", session)
    assertEquals("unindexed_query", resHistory.topicDetected)
    assertTrue(resHistory.replyText.contains("غير مسجلة في قاعدة المعرفة المحلية"))

    // Unrelated travel question must NOT falsely match Salalah
    val resTravel = reasoner.reason("كيف أستخرج تأشيرة سفر شنغن إلى ألمانيا؟", session)
    assertEquals("unindexed_query", resTravel.topicDetected)
  }

  // =========================================================================
  // 12. External Live Data Queries & Latency Verification (< 50ms)
  // =========================================================================
  @Test
  fun test12_externalLiveQueriesAndLatencyBenchmark() {
    val session = ConversationSession()

    val resLive = reasoner.reason("كم سعر الدولار والذهب اليوم في البورصة؟", session)
    assertEquals("unavailable_external_data", resLive.topicDetected)
    assertTrue(resLive.replyText.contains("بيانات خارجية حية"))

    // Benchmark 100 consecutive knowledge & normalization queries on-device
    val startNanos = System.nanoTime()
    repeat(20) {
      reasoner.reason("مَا هِيَ سُرْعَةُ الضَّوْءِ؟", session)
      reasoner.reason("اشرح لي ذلك", session)
      reasoner.reason("حدثني عن صلالة", session)
      reasoner.reason("متى أفضل وقت لزيارتها؟", session)
      reasoner.reason("احسب 25 * 4 + 10", session)
    }
    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0
    val avgMsPerQuery = elapsedMs / 100.0
    assertTrue("Average query latency should be under 10ms, was ${avgMsPerQuery}ms", avgMsPerQuery < 10.0)
  }

  private fun createRealIntentExecutor(
    localEngine: LocalConversationalEngine? = null
  ): IntentExecutor {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val capabilityDetector = CapabilityDetector(context)
    val batteryProvider = BatteryProvider(context)
    val memoryProvider = MemoryProvider(context)
    val storageProvider = StorageProvider()
    val flashlightController = FlashlightController(context)
    val audioController = AudioController(context)
    val mediaPlaybackController = MediaPlaybackController(context)
    val appResolver = AppResolver(context)
    val contactLookup = ContactLookupProvider(context)
    val locationProvider = LocationProvider(context)
    val weatherProvider = RealWeatherProvider(locationProvider)
    val networkProvider = NetworkProvider(context)
    val callLogProvider = CallLogProvider(context)

    return IntentExecutor(
      batteryActionHandler = BatteryActionHandler(batteryProvider),
      storageMemoryActionHandler = StorageMemoryActionHandler(memoryProvider, storageProvider),
      flashlightActionHandler = FlashlightActionHandler(capabilityDetector, flashlightController),
      audioActionHandler = AudioActionHandler(audioController),
      mediaActionHandler = MediaActionHandler(mediaPlaybackController),
      appActionHandler = AppActionHandler(appResolver),
      callActionHandler = CallActionHandler(context, callLogProvider, contactLookup),
      weatherActionHandler = WeatherActionHandler(weatherProvider, locationProvider),
      networkActionHandler = NetworkActionHandler(context, networkProvider),
      notificationActionHandler = NotificationActionHandler(context, capabilityDetector),
      settingsActionHandler = SettingsActionHandler(context),
      contextActionHandler = ContextActionHandler(
        getContextSnapshot = { ContextSnapshot(state = ContextState.STATIONARY, confidence = 0.9f) }
      ),
      localConversationalEngine = localEngine,
      deviceInfoProvider = null
    )
  }

  // =========================================================================
  // 13. Phase 2A.1 Fix 1: IntentExecutor Fallback Session Continuity & Non-Duplicate Execution
  // =========================================================================
  @Test
  fun test13_intentExecutorFallbackPreservesSessionAndPreventsDuplicateRecording() = runBlocking {
    // Instantiate real IntentExecutor with localConversationalEngine = null (Fallback Mode)
    val fallbackExecutor = createRealIntentExecutor(localEngine = null)

    // Turn 1: Initial knowledge question via IntentExecutor
    val intent1 = IntentParser.parse("ما هي سرعة الضوء؟", fallbackExecutor.activeSession)
    assertTrue(intent1 is WeykyIntent.ConversationalIntent)
    val res1 = fallbackExecutor.execute(intent1)
    assertTrue(res1 is ExecutionResult.Success)
    val speech1 = (res1 as ExecutionResult.Success).speech
    assertTrue(speech1.contains("300,000"))

    // Verify activeSession retained topic and recorded exactly 1 user turn + 1 assistant turn
    assertEquals("physics_universe", fallbackExecutor.activeSession.currentTopic)
    assertEquals(2, fallbackExecutor.activeSession.turns.size)
    assertEquals(ParticipantRole.USER, fallbackExecutor.activeSession.turns[0].role)
    assertEquals("ما هي سرعة الضوء؟", fallbackExecutor.activeSession.turns[0].text)
    assertEquals(ParticipantRole.ASSISTANT, fallbackExecutor.activeSession.turns[1].role)

    // Turn 2: Dependent follow-up question via IntentExecutor fallback path
    val intent2 = IntentParser.parse("اشرح لي ذلك", fallbackExecutor.activeSession)
    val res2 = fallbackExecutor.execute(intent2)
    assertTrue(res2 is ExecutionResult.Success)
    val speech2 = (res2 as ExecutionResult.Success).speech

    // Must resolve as follow-up on physics_universe, preserving session across calls
    assertEquals("physics_universe", fallbackExecutor.activeSession.currentTopic)
    assertTrue(
      "Fallback path must resolve follow-up against previous turn's topic",
      speech2.contains("السنة الضوئية") || speech2.contains("النسبية")
    )
    assertEquals("Must have 4 turns total without duplicate recording", 4, fallbackExecutor.activeSession.turns.size)

    // Device command after knowledge query must execute once and update topic cleanly
    val batteryIntent = IntentParser.parse("كم نسبة البطارية؟", fallbackExecutor.activeSession)
    assertTrue(batteryIntent is WeykyIntent.BatteryIntent)
    val batteryRes = fallbackExecutor.execute(batteryIntent)
    assertNotNull(batteryRes)
    assertEquals("battery", fallbackExecutor.activeSession.currentTopic)
    assertEquals("physics_universe", fallbackExecutor.activeSession.previousTopic)
    // Executing a device command must not add duplicate conversational turns
    assertEquals(4, fallbackExecutor.activeSession.turns.size)

    // Explicit reset clears the fallback session cleanly
    fallbackExecutor.resetFallbackSession()
    assertNull(fallbackExecutor.activeSession.currentTopic)
    assertTrue(fallbackExecutor.activeSession.turns.isEmpty())
  }

  // =========================================================================
  // 14. Phase 2A.1 Fix 2: Mandatory Negative Tests for Arabic Prefix & Homograph Normalization
  // =========================================================================
  @Test
  fun test14_negativeArabicPrefixAndHomographNormalization() {
    // 1. Mandatory table of words that MUST NOT lose their first letter in stripArabicPrefixes:
    assertEquals("باريس", LocalKnowledgeBase.stripArabicPrefixes("باريس"))
    assertNotEquals("اريس", LocalKnowledgeBase.stripArabicPrefixes("باريس"))
    assertFalse("باريس must not match اريس", LocalKnowledgeBase.tokensMatch("باريس", "اريس"))

    assertEquals("لبنان", LocalKnowledgeBase.stripArabicPrefixes("لبنان"))
    assertNotEquals("بنان", LocalKnowledgeBase.stripArabicPrefixes("لبنان"))
    assertFalse("لبنان must not match بنان", LocalKnowledgeBase.tokensMatch("لبنان", "بنان"))

    assertEquals("بغداد", LocalKnowledgeBase.stripArabicPrefixes("بغداد"))
    assertNotEquals("غداد", LocalKnowledgeBase.stripArabicPrefixes("بغداد"))
    assertFalse("بغداد must not match غداد", LocalKnowledgeBase.tokensMatch("بغداد", "غداد"))

    assertEquals("لندن", LocalKnowledgeBase.stripArabicPrefixes("لندن"))
    assertNotEquals("ندن", LocalKnowledgeBase.stripArabicPrefixes("لندن"))
    assertFalse("لندن must not match ندن", LocalKnowledgeBase.tokensMatch("لندن", "ندن"))

    assertEquals("وليد", LocalKnowledgeBase.stripArabicPrefixes("وليد"))
    assertNotEquals("ليد", LocalKnowledgeBase.stripArabicPrefixes("وليد"))
    assertFalse("وليد must not match ليد", LocalKnowledgeBase.tokensMatch("وليد", "ليد"))

    // Querying "ما هي عاصمة بنان؟" must NOT match Lebanon ("لبنان") in resolveSpecificCapitalAnswer
    assertNull(
      "Query with 'بنان' must not falsely match 'لبنان'",
      LocalKnowledgeBase.resolveSpecificCapitalAnswer("ما هي عاصمة بنان؟", isArabic = true)
    )

    // 2. Mandatory homograph & ة/ه & medial hamza checks:
    assertFalse("سأل (asked) must not match سال (flowed)", LocalKnowledgeBase.tokensMatch("سأل", "سال"))
    assertFalse("مهمة (task) must not match مهمه (important to him)", LocalKnowledgeBase.tokensMatch("مهمة", "مهمه"))
    assertFalse("سنة (year) must not match سنه (his age/tooth)", LocalKnowledgeBase.tokensMatch("سنة", "سنه"))
    assertFalse("حالة (condition) must not match حاله (his condition)", LocalKnowledgeBase.tokensMatch("حالة", "حاله"))

    // 3. Positive verification: valid definite prefixes and clitics still match accurately
    assertTrue(LocalKnowledgeBase.tokensMatch("الفضاء", "فضاء"))
    assertTrue(LocalKnowledgeBase.tokensMatch("بالجاذبية", "الجاذبيه"))
    assertTrue(LocalKnowledgeBase.tokensMatch("والكون", "كون"))
    assertTrue(LocalKnowledgeBase.tokensMatch("للنوم", "نوم"))
    assertTrue(LocalKnowledgeBase.tokensMatch("بصلالة", "صلاله"))
  }

  // =========================================================================
  // 15. Phase 2A.1 Fix 3: Prevention of currentTopic Bias on Standalone New Questions
  // =========================================================================
  @Test
  fun test15_currentTopicBiasPreventionOnNewTopicWithSharedWords() {
    // Active topic is physics_universe, user asks an independent question about water/hydration
    val matchWater = LocalKnowledgeBase.findMatchingTopic(
      "ما هي فوائد شرب الماء للجسم؟",
      currentTopicId = "physics_universe"
    )
    assertNotNull(matchWater)
    assertEquals("hydration_health", matchWater!!.id)

    // Active topic is hydration_health (which has keyword "الماء"), user asks an independent question
    // about Salalah waterfalls/springs ("عيون الماء في صلالة")
    val matchSalalah = LocalKnowledgeBase.findMatchingTopic(
      "حدثني عن السياحة في صلالة والماء",
      currentTopicId = "hydration_health"
    )
    assertNotNull(matchSalalah)
    assertEquals("salalah_tourism", matchSalalah!!.id)

    // Active topic is productivity ("الوقت"), user asks an independent question about sleep hours
    val matchSleep = LocalKnowledgeBase.findMatchingTopic(
      "كم ساعة من النوم يحتاج الإنسان لتحسين جودة النوم وعلاج الأرق؟",
      currentTopicId = "productivity"
    )
    assertNotNull(matchSleep)
    assertEquals("sleep_wellness", matchSleep!!.id)
  }

  // =========================================================================
  // 16. Phase 2A.1 Fix 4 & 5: 5-Step Behavioral Topic Transition & Explicit Return
  // =========================================================================
  @Test
  fun test16_fiveStepBehavioralTopicTransitionAndExplicitReturn() = runBlocking {
    val engine = LocalConversationalEngine()

    // Step 1: Question about Physics
    val step1 = engine.respond("حدثني عن الفضاء والكون")
    assertEquals("physics_universe", step1.topic)
    assertEquals("physics_universe", engine.session.currentTopic)

    // Step 2: Follow-up on Physics
    val step2 = engine.respond("كيف تتكون النجوم والكواكب؟")
    assertEquals("physics_universe", step2.topic)
    assertEquals("physics_universe", engine.session.currentTopic)
    assertTrue(step2.replyText.contains("السديم") || step2.replyText.contains("الجاذبية"))

    // Step 3: Independent question about Geography (Capitals)
    val step3 = engine.respond("ما هي عاصمة مصر؟")
    assertEquals("capitals_arab", step3.topic)
    assertEquals("capitals_arab", engine.session.currentTopic)
    assertEquals("physics_universe", engine.session.previousTopic)
    assertTrue(step3.replyText.contains("القاهرة"))

    // Step 4: Follow-up on Geography
    val step4 = engine.respond("وماذا عن المغرب؟")
    assertEquals("capitals_arab", step4.topic)
    assertEquals("capitals_arab", engine.session.currentTopic)
    assertTrue(step4.replyText.contains("الرباط"))

    // Step 5: Explicit return to Physics topic
    val step5 = engine.respond("نعود إلى موضوع الفيزياء والفضاء")
    assertEquals("physics_universe", step5.topic)
    assertEquals("physics_universe", engine.session.currentTopic)
    assertEquals("capitals_arab", engine.session.previousTopic)
    assertTrue(step5.replyText.contains("بالعودة إلى موضوعنا السابق") || step5.replyText.contains("الضوء") || step5.replyText.contains("الكون"))

    // Verify all 10 turns (5 user + 5 assistant) were recorded in order without any loss or duplication
    assertEquals(10, engine.session.turns.size)
  }

  // =========================================================================
  // 17. Phase 2A.1 Fix 7: Non-Repetitive Follow-Up Elaboration Handling
  // =========================================================================
  @Test
  fun test17_repeatedFollowUpElaborationAvoidsVerbatimRepetition() = runBlocking {
    val engine = LocalConversationalEngine()

    // Turn 1: Initial question on AI
    val t1 = engine.respond("ما هو الذكاء الاصطناعي؟")
    assertEquals("ai_concept", t1.topic)

    // Turn 2: First follow-up asking for elaboration
    val t2 = engine.respond("اشرح لي ذلك بالتفصيل")
    assertEquals("ai_concept", t2.topic)
    assertTrue(t2.replyText.contains("الذكاء المحلي") || t2.replyText.contains("معالج جهازك"))

    // Turn 3: Second generic follow-up on the same topic
    val t3 = engine.respond("حدثني أكثر عن ذلك")
    assertEquals("ai_concept", t3.topic)
    assertNotEquals(
      "Second follow-up must not mechanically repeat the exact same elaboration text",
      t2.replyText,
      t3.replyText
    )
    assertTrue(
      "Must honestly state the scope of locally indexed details or ask to specify an angle",
      t3.replyText.contains("استعرضنا بالفعل") || t3.replyText.contains("تحديد زاوية")
    )
  }

  // =========================================================================
  // 18. Phase 2A.1 Fix 8: Arithmetic Parentheses Boundary & Separated Benchmarks with Warm-Up
  // =========================================================================
  @Test
  fun test18_arithmeticParenthesesGuardAndSeparatedLatencyBenchmarks() {
    // 1. Parentheses boundary check: must NOT silently strip parentheses and compute wrong answer (20 instead of 30)
    val parenRes = LocalKnowledgeBase.evaluateArithmetic("احسب (10 + 5) * 2")
    assertNotNull(parenRes)
    assertFalse("Must not return wrong calculation 20 for (10 + 5) * 2", parenRes!!.contains("= 20"))
    assertTrue("Must disclose parentheses boundary honestly", parenRes.contains("لا يدعم الأقواس"))

    // 2. Representative query set (10 queries)
    val representativeQueries = listOf(
      "مَا هِيَ سُرْعَةُ الضَّوْءِ؟",
      "ما هو الذكاء الاصطناعي؟",
      "حدثني عن السياحة في صلالة",
      "ما هي عاصمة المملكة العربية السعودية؟",
      "كيف أحسن جودة النوم وعلاج الأرق؟",
      "ما هي تقنية بومودورو لتنظيم الوقت؟",
      "كيف أحافظ على صحة البطارية؟",
      "ما هي فوائد شرب الماء؟",
      "احك لي قصة قصيرة ملهمة",
      "من أنت وما هي قدراتك؟"
    )

    // Warm-up phase (50 iterations = 500 calls) to eliminate cold JVM JIT overhead
    val warmupSession = ConversationSession()
    repeat(50) {
      for (q in representativeQueries) {
        LocalKnowledgeBase.findMatchingTopic(q, null)
        reasoner.reason(q, warmupSession)
      }
    }

    // Benchmark A: Pure Retrieval Latency (LocalKnowledgeBase.findMatchingTopic) — 100 iterations x 10 queries = 1000 calls
    val retrievalIterations = 100
    val retrievalStartNanos = System.nanoTime()
    repeat(retrievalIterations) {
      for (q in representativeQueries) {
        val topic = LocalKnowledgeBase.findMatchingTopic(q, null)
        assertNotNull(topic)
      }
    }
    val retrievalTotalMs = (System.nanoTime() - retrievalStartNanos) / 1_000_000.0
    val retrievalAvgMs = retrievalTotalMs / (retrievalIterations * representativeQueries.size)

    // Benchmark B: Full Conversational Reasoner Path (reasoner.reason) — 100 iterations x 10 queries = 1000 calls
    val reasonerIterations = 100
    val benchSession = ConversationSession()
    val reasonerStartNanos = System.nanoTime()
    repeat(reasonerIterations) {
      for (q in representativeQueries) {
        val res = reasoner.reason(q, benchSession)
        assertNotNull(res.replyText)
      }
    }
    val reasonerTotalMs = (System.nanoTime() - reasonerStartNanos) / 1_000_000.0
    val reasonerAvgMs = reasonerTotalMs / (reasonerIterations * representativeQueries.size)

    println("BENCHMARK_RESULT: Pure findMatchingTopic avg=${String.format(java.util.Locale.US, "%.4f", retrievalAvgMs)} ms (total=${String.format(java.util.Locale.US, "%.2f", retrievalTotalMs)} ms over 1000 calls)")
    println("BENCHMARK_RESULT: Full reasoner.reason avg=${String.format(java.util.Locale.US, "%.4f", reasonerAvgMs)} ms (total=${String.format(java.util.Locale.US, "%.2f", reasonerTotalMs)} ms over 1000 calls)")

    assertTrue("Pure retrieval latency should be under 5ms, was ${retrievalAvgMs}ms", retrievalAvgMs < 5.0)
    assertTrue("Full reasoner latency should be under 10ms, was ${reasonerAvgMs}ms", reasonerAvgMs < 10.0)
  }
}
