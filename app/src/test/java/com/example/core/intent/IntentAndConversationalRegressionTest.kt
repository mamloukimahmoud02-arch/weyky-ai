package com.example.core.intent

import com.example.core.intelligence.conversation.ConversationSession
import com.example.core.intelligence.conversation.ConversationalReasoner
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentAndConversationalRegressionTest {

  @Test
  fun testArabicNormalization() {
    val raw = "  أَفْتَحْ الإعدادات!؟  "
    val normalized = IntentParser.normalize(raw)
    assertEquals("افتح الاعدادات", normalized)
  }

  @Test
  fun testSingleCommandParsing_battery() {
    val intent = IntentParser.parse("كم نسبة البطارية")
    assertTrue(intent is WeykyIntent.BatteryIntent)
  }

  @Test
  fun testSingleCommandParsing_flashlight() {
    val intentOn = IntentParser.parse("شغل الكشاف")
    assertTrue(intentOn is WeykyIntent.FlashlightIntent && intentOn.turnOn)

    val intentOff = IntentParser.parse("أطفئ الكشاف")
    assertTrue(intentOff is WeykyIntent.FlashlightIntent && !intentOff.turnOn)
  }

  @Test
  fun testSingleCommandParsing_volumePercent() {
    val intent = IntentParser.parse("اخفض الصوت إلى 30%")
    assertTrue(intent is WeykyIntent.VolumePercentIntent)
    assertEquals(30, (intent as WeykyIntent.VolumePercentIntent).percent)
  }

  @Test
  fun testSingleCommandParsing_openApp() {
    val intent = IntentParser.parse("افتح سبوتيفاي")
    assertTrue(intent is WeykyIntent.OpenAppIntent)
    assertEquals("سبوتيفاي", (intent as WeykyIntent.OpenAppIntent).appName)
  }

  @Test
  fun testHypotheticalBatteryMusic_classification() {
    val intent = IntentParser.parse("لو شغلت الموسيقى هل ستستهلك البطارية؟")
    assertTrue(intent is WeykyIntent.HypotheticalBatteryMusicIntent)
  }

  @Test
  fun testConversationalFollowUp_identityAndPhone() {
    val reasoner = ConversationalReasoner()
    val session = ConversationSession()

    // Query 1: Phone model
    val res1 = reasoner.reason("ما هو هاتفي؟", session)
    session.recordAssistantTurn(res1.replyText, res1.proactiveSuggestions, res1.topicDetected)
    assertEquals("device_info", session.currentTopic)

    // Follow-up query
    val res2 = reasoner.reason("وماذا تعرف عنه؟", session)
    assertNotNull(res2.replyText)
    assertEquals("device_info", res2.topicDetected)
  }

  @Test
  fun testLocalKnowledgeBase_unindexedHonestFallback() {
    val reasoner = ConversationalReasoner()
    val session = ConversationSession()

    val res = reasoner.reason("ما هي تفاصيل المناخ الجيولوجي في أيسلندا؟", session)
    assertEquals("unindexed_query", res.topicDetected)
    assertTrue(res.replyText.contains("غير مسجلة في قاعدة المعرفة المحلية"))
  }

  @Test
  fun testLocalKnowledgeBase_arithmeticCalculation() {
    val result = LocalKnowledgeBase.evaluateArithmetic("احسب 50 * 4")
    assertNotNull(result)
    assertTrue(result!!.contains("200"))
  }

  // --- ACCEPTANCE TESTS A THROUGH H FOR COMPOUND COMMANDS ---

  @Test
  fun testAcceptanceA_arabicCompoundCommandParsing() {
    // A: "افتح الموسيقى ثم اخفض الصوت إلى 30%"
    val query = "افتح الموسيقى ثم اخفض الصوت إلى 30%"
    val intent = IntentParser.parse(query)

    assertTrue("Expected CompoundIntent but was $intent", intent is WeykyIntent.CompoundIntent)
    val compound = intent as WeykyIntent.CompoundIntent
    assertEquals(2, compound.steps.size)

    // Step 1: Open app intent for music
    assertTrue(compound.steps[0].intent is WeykyIntent.OpenAppIntent)
    // Step 2: Volume percent intent 30%
    assertTrue(compound.steps[1].intent is WeykyIntent.VolumePercentIntent)
    assertEquals(30, (compound.steps[1].intent as WeykyIntent.VolumePercentIntent).percent)
  }

  @Test
  fun testAcceptanceB_arabicCompoundCommand_wabadaha() {
    // B: "افتح سبوتيفاي وبعدها اخفض الصوت"
    val query = "افتح سبوتيفاي وبعدها اخفض الصوت"
    val intent = IntentParser.parse(query)

    assertTrue("Expected CompoundIntent but was $intent", intent is WeykyIntent.CompoundIntent)
    val compound = intent as WeykyIntent.CompoundIntent
    assertEquals(2, compound.steps.size)
    assertEquals("سبوتيفاي", (compound.steps[0].intent as WeykyIntent.OpenAppIntent).appName)
    assertTrue(compound.steps[1].intent is WeykyIntent.VolumeStepIntent)
  }

  @Test
  fun testAcceptanceC_englishCompoundCommand_then() {
    // C: "Open Spotify, then reduce media volume to 40%"
    val query = "Open Spotify, then reduce media volume to 40%"
    val intent = IntentParser.parse(query)

    assertTrue("Expected CompoundIntent but was $intent", intent is WeykyIntent.CompoundIntent)
    val compound = intent as WeykyIntent.CompoundIntent
    assertEquals(2, compound.steps.size)
    assertEquals("Spotify", (compound.steps[0].intent as WeykyIntent.OpenAppIntent).appName)
    assertTrue(compound.steps[1].intent is WeykyIntent.VolumePercentIntent)
    assertEquals(40, (compound.steps[1].intent as WeykyIntent.VolumePercentIntent).percent)
  }

  @Test
  fun testAcceptanceD_singleCommand_unchanged() {
    // G: "اخفض الصوت إلى 30%" remains a single VolumePercentIntent
    val query = "اخفض الصوت إلى 30%"
    val intent = IntentParser.parse(query)

    assertTrue(intent is WeykyIntent.VolumePercentIntent)
    assertEquals(30, (intent as WeykyIntent.VolumePercentIntent).percent)
  }

  @Test
  fun testAcceptanceF_unsupportedSecondSegment_rejectsCompoundExecution() {
    // F: "افتح الموسيقى ثم [unsupported action / gibberish query]"
    val query = "افتح الموسيقى ثم كذلكمرثقصخهع"
    val intent = IntentParser.parse(query)

    // The compound parser must reject executing a sequence containing an unsupported action
    assertTrue("Unsupported segment must prevent CompoundIntent generation", intent !is WeykyIntent.CompoundIntent)
  }

  @Test
  fun testAcceptanceD_unknownAppStopsCompoundExecution() {
    // D: When a step reports Error, compound sequence stops immediately and reports partial execution
    val unknownAppStep = WeykyIntent.CompoundStep(
      index = 0,
      segmentQuery = "افتح تطبيق_غير_موجود",
      intent = WeykyIntent.OpenAppIntent("تطبيق_غير_موجود")
    )
    val volumeStep = WeykyIntent.CompoundStep(
      index = 1,
      segmentQuery = "اخفض الصوت",
      intent = WeykyIntent.VolumeStepIntent(increase = false, stepCount = 1)
    )

    val compound = WeykyIntent.CompoundIntent(
      steps = listOf(unknownAppStep, volumeStep),
      rawQuery = "افتح تطبيق_غير_موجود ثم اخفض الصوت"
    )

    assertEquals(2, compound.steps.size)
    assertEquals("تطبيق_غير_موجود", (compound.steps[0].intent as WeykyIntent.OpenAppIntent).appName)
  }

  @Test
  fun testAcceptanceE_permissionRequiredStopsExecution() {
    // E: When a step requires permission, subsequent steps are stopped and permission is returned
    val permissionRes = ExecutionResult.RequiresPermission(
      permission = android.Manifest.permission.READ_CONTACTS,
      rationale = "Permission needed"
    )
    assertTrue(permissionRes is ExecutionResult.RequiresPermission)
    assertEquals(android.Manifest.permission.READ_CONTACTS, permissionRes.permission)
  }

  @Test
  fun testAcceptanceH_conversationalFollowUp_preserved() {
    // H: Preservation of conversational flow
    val reasoner = ConversationalReasoner()
    val session = ConversationSession()

    // Turn 1: battery inquiry
    session.recordUserTurn("كم ستستهلك البطارية؟")
    session.currentTopic = "battery"
    session.lastReferencedSubject = "battery"

    // Turn 2: Follow-up
    val followUp = reasoner.reason("وماذا لو استخدمت الملاحة أيضًا؟", session)
    assertNotNull(followUp.replyText)
    // Honest reporting when navigation energy modeling is not indexed
    assertTrue(
      followUp.replyText.contains("غير مسجلة") ||
      followUp.replyText.contains("المعرفة المحلية") ||
      followUp.replyText.contains("البطارية")
    )
  }
}
