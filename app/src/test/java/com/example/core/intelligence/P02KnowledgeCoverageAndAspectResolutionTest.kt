package com.example.core.intelligence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.intent.IntentParser
import com.example.core.intent.WeykyIntent
import com.example.core.intelligence.conversation.LocalConversationalEngine
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.importer.KnowledgePackImportStatus
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class P02KnowledgeCoverageAndAspectResolutionTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var database: WeykyKnowledgeDatabase
    private lateinit var repository: KnowledgeRepository
    private lateinit var engine: LocalConversationalEngine

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("test_weyky_p02_aspect_resolution.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }
        database = Room.databaseBuilder(
            context,
            WeykyKnowledgeDatabase::class.java,
            dbFile.name
        ).build()
        KnowledgeRepository.clearInstanceForTesting()
        repository = KnowledgeRepository(database = database)
        assertTrue(repository.initializeSeedAndWarmUpIndex())
        engine = LocalConversationalEngine(knowledgeRepository = repository)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        WeykyKnowledgeDatabase.clearInstanceForTesting()
        KnowledgeRepository.clearInstanceForTesting()
        if (::dbFile.isInitialized && dbFile.exists()) {
            dbFile.delete()
        }
    }

    // =========================================================================
    // 1. Test A — Physics Aspect Resolution: Gravity Definition vs Solar-System Role
    // =========================================================================

    @Test
    fun `1 Test A - gravity definition and solar-system role of gravity return distinct aspect-focused explanations`() = runBlocking {
        val q1 = "ما هي الجاذبية؟"
        val r1 = engine.respond(q1)
        assertEquals("physics_universe", r1.topic)
        assertEquals("gravity", r1.preparedKnowledgeContext?.matchedAspectId)
        assertTrue("Must explain mutual attraction between masses", r1.replyText.contains("تجاذب") && r1.replyText.contains("الكتل"))
        assertTrue("Must explain giving objects weight near Earth's surface", r1.replyText.contains("أوزانها") && r1.replyText.contains("الأرض"))
        assertTrue("Must explain keeping planets in orbit around the Sun", r1.replyText.contains("مداراتها") && r1.replyText.contains("الشمس"))
        assertTrue("Must clarify gravity is universal and not limited to Earth", r1.replyText.contains("ليست مقتصرة على الأرض") && r1.replyText.contains("الكون"))
        assertFalse("Must not return generic speed of light bullet", r1.replyText.contains("300,000"))

        engine.session.clear()
        val q2 = "كيف تعمل الجاذبية؟"
        val r2 = engine.respond(q2)
        assertEquals("physics_universe", r2.topic)
        assertEquals("gravity", r2.preparedKnowledgeContext?.matchedAspectId)
        assertTrue(r2.replyText.contains("تجاذب") && r2.replyText.contains("الكتلة"))
        assertFalse(r2.replyText.contains("300,000"))

        engine.session.clear()
        val q3 = "ما دور الجاذبية في النظام الشمسي؟"
        assertTrue(IntentParser.parse(q3) is WeykyIntent.ConversationalIntent)
        val r3 = engine.respond(q3)
        assertEquals("physics_universe", r3.topic)
        assertEquals("gravity_solar_system", r3.preparedKnowledgeContext?.matchedAspectId)
        assertTrue(
            "Must explain that the Sun's gravitational attraction keeps planets in their orbits",
            r3.replyText.contains("الشمس") && r3.replyText.contains("الكواكب") && r3.replyText.contains("مدارات")
        )
        assertTrue(
            "Must explain that gravity governs interactions between moons, asteroids, comets, and other celestial bodies",
            r3.replyText.contains("الأقمار") && r3.replyText.contains("الأجرام السماوية")
        )
        assertFalse(
            "A generic definition of gravity alone must fail Test A",
            r3.replyText.startsWith("الجاذبية هي ظاهرة فيزيائية كونية") || r3.replyText.contains("هي تعريف الجاذبية")
        )
        assertNotEquals(
            "Solar-system role of gravity must be distinct from the general definition of gravity",
            r1.replyText,
            r3.replyText
        )
        assertFalse(r3.replyText.contains("300,000"))
    }

    // =========================================================================
    // 2. Physics & Universe Aspect Resolution: Stars & Nuclear Fusion
    // =========================================================================

    @Test
    fun `2 star inquiries return dedicated stars and nuclear fusion explanation`() = runBlocking {
        val q1 = "كيف تعمل النجوم؟"
        val r1 = engine.respond(q1)
        assertEquals("physics_universe", r1.topic)
        assertEquals("stars", r1.preparedKnowledgeContext?.matchedAspectId)
        assertTrue("Must explain stars are massive spheres of plasma", r1.replyText.contains("كرات غازية ضخمة") && r1.replyText.contains("البلازما"))
        assertTrue("Must explain energy production via core nuclear fusion", r1.replyText.contains("الاندماج النووي") && r1.replyText.contains("نواته"))
        assertTrue("Must explain fusing hydrogen into helium", r1.replyText.contains("الهيدروجين") && r1.replyText.contains("الهيليوم"))
        assertTrue("Must explain energy reaching us as radiation, light, and heat", r1.replyText.contains("إشعاع") && r1.replyText.contains("ضوء") && r1.replyText.contains("حرارة"))
        assertTrue("Must explain balance between gravity and internal pressure", r1.replyText.contains("الجاذبية") && r1.replyText.contains("الضغط"))
        assertFalse("Must not return generic speed of light bullet", r1.replyText.contains("300,000"))

        engine.session.clear()
        val q2 = "كيف تنتج النجوم الطاقة؟"
        val r2 = engine.respond(q2)
        assertEquals("physics_universe", r2.topic)
        assertEquals("stars", r2.preparedKnowledgeContext?.matchedAspectId)
        assertTrue(r2.replyText.contains("الاندماج النووي") && r2.replyText.contains("الهيدروجين") && r2.replyText.contains("الهيليوم"))
        assertFalse(r2.replyText.contains("300,000"))

        // Verify gravity answer, solar-system gravity answer, and stars answer are all distinct
        val gravityRes = engine.respond("ما هي الجاذبية؟")
        val solarGravityRes = engine.respond("ما دور الجاذبية في النظام الشمسي؟")
        assertEquals(3, setOf(r1.replyText, gravityRes.replyText, solarGravityRes.replyText).size)
    }

    // =========================================================================
    // 3. Physics & Universe Existing Concepts Preserved (Speed of Light, Solar System, General Overview)
    // =========================================================================

    @Test
    fun `3 existing physics concepts speed of light solar system and general overview are preserved`() = runBlocking {
        val lightRes = engine.respond("ما هي سرعة الضوء؟")
        assertEquals("physics_universe", lightRes.topic)
        assertEquals("speed_of_light", lightRes.preparedKnowledgeContext?.matchedAspectId)
        assertTrue(lightRes.replyText.contains("300,000"))
        assertTrue(lightRes.replyText.contains("8 دقائق و20 ثانية"))

        engine.session.clear()
        val solarRes = engine.respond("حدثني عن المجموعة الشمسية")
        assertEquals("physics_universe", solarRes.topic)
        assertEquals("solar_system", solarRes.preparedKnowledgeContext?.matchedAspectId)
        assertTrue(solarRes.replyText.contains("8 كواكب") && solarRes.replyText.contains("المشتري") && solarRes.replyText.contains("عطارد"))

        engine.session.clear()
        val generalRes = engine.respond("حدثني عن الفضاء والكون")
        assertEquals("physics_universe", generalRes.topic)
        assertNull("General cosmic query should return general topic summary without aspect override", generalRes.preparedKnowledgeContext?.matchedAspectId)
        assertTrue(generalRes.replyText.contains("حقائق علمية كونية"))
    }

    // =========================================================================
    // 4. Test B — Battery Knowledge Retrieval: Rapid Battery Drain & High Consumption Causes
    // =========================================================================

    @Test
    fun `4 Test B - battery drain inquiries retrieve battery_care explanation and never fall back to unavailable knowledge`() = runBlocking {
        val q1 = "لماذا تنفد البطارية بسرعة؟"
        assertTrue(IntentParser.parse(q1) is WeykyIntent.ConversationalIntent)
        val r1 = engine.respond(q1)
        assertEquals("battery_care", r1.topic)
        assertEquals("battery_drain", r1.preparedKnowledgeContext?.matchedAspectId)
        assertFalse(
            "An unavailable-knowledge fallback must fail Test B",
            r1.replyText.contains("غير مسجلة في قاعدة المعرفة")
        )
        assertTrue("Must mention screen brightness and high refresh rate", r1.replyText.contains("سطوع الشاشة") && r1.replyText.contains("معدل التحديث"))
        assertTrue("Must mention background apps", r1.replyText.contains("الخلفية"))
        assertTrue("Must mention weak network signal and constant searching", r1.replyText.contains("ضعف إشارة الشبكة") && r1.replyText.contains("البحث المستمر"))
        assertTrue("Must mention continuous GPS and wireless connections", r1.replyText.contains("GPS") && r1.replyText.contains("الاتصالات اللاسلكية"))
        assertTrue("Must mention high temperature", r1.replyText.contains("الحرارة المرتفعة"))
        assertTrue("Must explicitly distinguish high consumption causes from chemical capacity degradation over time", r1.replyText.contains("أسباب الاستهلاك العالي") && r1.replyText.contains("تراجع السعة الكيميائية"))

        engine.session.clear()
        val q2 = "ما أسباب استهلاك البطارية؟"
        assertTrue(IntentParser.parse(q2) is WeykyIntent.ConversationalIntent)
        val r2 = engine.respond(q2)
        assertEquals("battery_care", r2.topic)
        assertEquals("battery_drain", r2.preparedKnowledgeContext?.matchedAspectId)
        assertEquals(r1.replyText, r2.replyText)

        // Common Arabic orthographic variant with ذ ("تنفذ") must also resolve to battery_drain
        engine.session.clear()
        val q3 = "لماذا تنفذ البطارية بسرعة؟"
        assertTrue(IntentParser.parse(q3) is WeykyIntent.ConversationalIntent)
        val r3 = engine.respond(q3)
        assertEquals("battery_care", r3.topic)
        assertEquals("battery_drain", r3.preparedKnowledgeContext?.matchedAspectId)
        assertEquals(r1.replyText, r3.replyText)
    }

    // =========================================================================
    // 5. Battery Care Aspect Resolution: Overnight Charging
    // =========================================================================

    @Test
    fun `5 overnight charging inquiry returns balanced scientific explanation on cutoff heat and optimized charging`() = runBlocking {
        val q = "هل الشحن طوال الليل يضر البطارية؟"
        assertTrue(IntentParser.parse(q) is WeykyIntent.ConversationalIntent)
        val r = engine.respond(q)
        assertEquals("battery_care", r.topic)
        assertEquals("overnight_charging", r.preparedKnowledgeContext?.matchedAspectId)
        assertTrue("Must explain modern phones stop or manage active charging at full capacity", r.replyText.contains("توقف الشحن الفعلي") && r.replyText.contains("100%"))
        assertTrue("Must explain prolonged high charge and heat accelerate chemical aging", r.replyText.contains("الحرارة") && r.replyText.contains("الشيخوخة الكيميائية"))
        assertTrue("Must mention Optimized / Adaptive Charging", r.replyText.contains("الشحن المحسّن") && r.replyText.contains("Optimized Charging"))
        assertTrue("Must advise avoiding high heat while charging", r.replyText.contains("تجنب الحرارة أثناء الشحن") && r.replyText.contains("الوسادة"))
        assertTrue("Must clarify 20-80% is an optional helpful habit and not a mandatory requirement", r.replyText.contains("اختيارية") && r.replyText.contains("وليست شرطاً إلزامياً"))
    }

    // =========================================================================
    // 6. Battery Care Aspect Resolution: Energy Saving vs Battery Lifespan
    // =========================================================================

    @Test
    fun `6 energy saving and battery lifespan inquiries return distinct practical aspect responses`() = runBlocking {
        val qEnergy = "كيف أقلل استهلاك الطاقة؟"
        assertTrue(IntentParser.parse(qEnergy) is WeykyIntent.ConversationalIntent)
        val rEnergy = engine.respond(qEnergy)
        assertEquals("battery_care", rEnergy.topic)
        assertEquals("energy_saving", rEnergy.preparedKnowledgeContext?.matchedAspectId)
        assertTrue("Must mention enabling Battery Saver", rEnergy.replyText.contains("وضع توفير الطاقة") && rEnergy.replyText.contains("Battery Saver"))
        assertTrue("Must mention lowering screen brightness and refresh rate if supported", rEnergy.replyText.contains("خفض سطوع الشاشة") && rEnergy.replyText.contains("معدل التحديث"))
        assertTrue("Must mention reviewing top battery-consuming apps and restricting background activity", rEnergy.replyText.contains("التطبيقات الأعلى استهلاكاً") && rEnergy.replyText.contains("تقييد نشاط الخلفية"))
        assertTrue("Must mention managing GPS, Bluetooth, and wireless connections", rEnergy.replyText.contains("GPS") && rEnergy.replyText.contains("البلوتوث"))
        assertTrue("Must mention Dark Mode on OLED/AMOLED and clarify benefit depends on screen type", rEnergy.replyText.contains("OLED") && rEnergy.replyText.contains("AMOLED") && rEnergy.replyText.contains("تعتمد على نوع الشاشة"))

        engine.session.clear()
        val qLifespan = "كيف أحافظ على عمر البطارية؟"
        assertTrue(IntentParser.parse(qLifespan) is WeykyIntent.ConversationalIntent)
        val rLifespan = engine.respond(qLifespan)
        assertEquals("battery_care", rLifespan.topic)
        assertEquals("battery_lifespan", rLifespan.preparedKnowledgeContext?.matchedAspectId)
        assertTrue("Must include balanced guidance on heat, deep discharge, 20%-80%, and optimized charging",
            rLifespan.replyText.contains("الحرارة") &&
                rLifespan.replyText.contains("التفريغ العميق") &&
                rLifespan.replyText.contains("20%") &&
                rLifespan.replyText.contains("80%") &&
                rLifespan.replyText.contains("الشحن المحسّن")
        )

        // All 4 battery aspects must have distinct responses
        val rDrain = engine.respond("لماذا تنفد البطارية بسرعة؟")
        val rOvernight = engine.respond("هل الشحن طوال الليل يضر البطارية؟")
        val distinctBatteryResponses = setOf(
            rDrain.replyText,
            rOvernight.replyText,
            rEnergy.replyText,
            rLifespan.replyText
        )
        assertEquals("All 4 battery_care sub-concepts must return distinct responses", 4, distinctBatteryResponses.size)
    }

    // =========================================================================
    // 7. Room Seed Synchronization & External Pack Preservation
    // =========================================================================

    @Test
    fun `7 Room seed initialization automatically updates outdated core_seed_pack checksum and keywords while preserving imported packs`() = runBlocking {
        // 1. Import an external knowledge pack alongside core_seed_pack
        val externalPackJson = """
            {
              "packFormat": "WEYKY_KNOWLEDGE_PACK_V1",
              "packId": "astronomy_addon_pack",
              "sourceType": "LOCAL_UPDATE",
              "packVersion": 1,
              "contentVersion": 1,
              "schemaVersion": 1,
              "packPriority": 10,
              "topics": [
                {
                  "topicId": "exoplanets_discovery",
                  "topicOrderIndex": 0,
                  "contentVersion": 1,
                  "keywords": ["الكواكب الخارجية", "exoplanets"],
                  "arabicSummary": "الكواكب الخارجية هي كواكب تدور حول نجوم خارج مجموعتنا الشمسية.",
                  "englishSummary": "Exoplanets are planets that orbit stars outside our solar system.",
                  "suggestedFollowUps": ["ما هي الكواكب الخارجية؟"]
                }
              ]
            }
        """.trimIndent()
        val importRes = repository.importKnowledgePackFromJsonString(externalPackJson)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importRes.status)

        // 2. Simulate an older core_seed_pack checksum in Room
        val dao = database.knowledgeDao()
        val staleCoreMetadata = KnowledgePackMetadataEntity(
            packId = SeedKnowledgeCatalog.CORE_SEED_PACK_ID,
            sourceType = SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE,
            packVersion = 1,
            schemaVersion = SeedKnowledgeCatalog.CORE_SEED_SCHEMA_VERSION,
            sha256Checksum = "0000000000000000000000000000000000000000000000000000000000000000",
            packPriority = SeedKnowledgeCatalog.CORE_SEED_PACK_PRIORITY,
            importedAt = 1700000000000L,
            topicCount = 11
        )
        dao.upsertPackMetadata(staleCoreMetadata)
        assertEquals(
            "0000000000000000000000000000000000000000000000000000000000000000",
            dao.getPackMetadataById(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)?.sha256Checksum
        )

        // 3. Re-run initializeSeedAndWarmUpIndex(): must update core_seed_pack to new P0.2.1 checksum & keywords
        // while keeping astronomy_addon_pack intact
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val updatedCoreMetadata = dao.getPackMetadataById(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)
        assertNotNull(updatedCoreMetadata)
        assertEquals(SeedKnowledgeCatalog.coreSeedChecksum, updatedCoreMetadata?.sha256Checksum)
        assertEquals(SeedKnowledgeCatalog.CORE_SEED_PACK_VERSION, updatedCoreMetadata?.packVersion)

        val externalMetadata = dao.getPackMetadataById("astronomy_addon_pack")
        assertNotNull("Imported external pack must not be deleted during core seed update", externalMetadata)

        val physicsKeywordsInDb = dao.getKeywordsByTopicAndPackOrdered(
            "physics_universe",
            SeedKnowledgeCatalog.CORE_SEED_PACK_ID
        ).map { it.originalKeyword }
        assertTrue(physicsKeywordsInDb.contains("النجوم"))
        assertTrue(physicsKeywordsInDb.contains("الاندماج النووي"))
        assertTrue(physicsKeywordsInDb.contains("دور الجاذبية في النظام الشمسي"))

        val batteryKeywordsInDb = dao.getKeywordsByTopicAndPackOrdered(
            "battery_care",
            SeedKnowledgeCatalog.CORE_SEED_PACK_ID
        ).map { it.originalKeyword }
        assertTrue(batteryKeywordsInDb.contains("تنفد البطارية"))
        assertTrue(batteryKeywordsInDb.contains("لماذا تنفد البطارية بسرعة"))
        assertTrue(batteryKeywordsInDb.contains("الشحن طوال الليل"))
        assertTrue(batteryKeywordsInDb.contains("تقليل استهلاك الطاقة"))
    }

    // =========================================================================
    // 8. P0.1 / P0.2 Regression Protection & Multi-Turn Aspect Continuity
    // =========================================================================

    @Test
    fun `8 all P0_1 and P0_2 regression cases pass end-to-end`() = runBlocking {
        // 1. "كيف تعمل النجوم؟"
        engine.session.clear()
        val starsRes = engine.respond("كيف تعمل النجوم؟")
        assertEquals("physics_universe", starsRes.topic)
        assertEquals("stars", starsRes.preparedKnowledgeContext?.matchedAspectId)
        assertTrue(starsRes.replyText.contains("الاندماج النووي") && starsRes.replyText.contains("البلازما"))

        // 2. "ما هي الجاذبية؟"
        engine.session.clear()
        val gravityRes = engine.respond("ما هي الجاذبية؟")
        assertEquals("physics_universe", gravityRes.topic)
        assertEquals("gravity", gravityRes.preparedKnowledgeContext?.matchedAspectId)
        assertTrue(gravityRes.replyText.contains("تجاذب") && gravityRes.replyText.contains("الكتل"))

        // 3. "ما دور الجاذبية في النظام الشمسي؟"
        engine.session.clear()
        val solarGravityRes = engine.respond("ما دور الجاذبية في النظام الشمسي؟")
        assertEquals("physics_universe", solarGravityRes.topic)
        assertEquals("gravity_solar_system", solarGravityRes.preparedKnowledgeContext?.matchedAspectId)
        assertNotEquals(gravityRes.replyText, solarGravityRes.replyText)

        // 4. "لماذا تنفد البطارية بسرعة؟"
        engine.session.clear()
        val drainRes = engine.respond("لماذا تنفد البطارية بسرعة؟")
        assertEquals("battery_care", drainRes.topic)
        assertEquals("battery_drain", drainRes.preparedKnowledgeContext?.matchedAspectId)
        assertFalse(drainRes.replyText.contains("غير مسجلة في قاعدة المعرفة"))

        // 5. "هل الشحن طوال الليل يضر البطارية؟"
        engine.session.clear()
        val overnightRes = engine.respond("هل الشحن طوال الليل يضر البطارية؟")
        assertEquals("battery_care", overnightRes.topic)
        assertEquals("overnight_charging", overnightRes.preparedKnowledgeContext?.matchedAspectId)

        // 6. "كيف أقلل استهلاك الطاقة؟"
        engine.session.clear()
        val energyRes = engine.respond("كيف أقلل استهلاك الطاقة؟")
        assertEquals("battery_care", energyRes.topic)
        assertEquals("energy_saving", energyRes.preparedKnowledgeContext?.matchedAspectId)

        // 7. "كيف أحافظ على عمر البطارية؟"
        engine.session.clear()
        val lifespanRes = engine.respond("كيف أحافظ على عمر البطارية؟")
        assertEquals("battery_care", lifespanRes.topic)
        assertEquals("battery_lifespan", lifespanRes.preparedKnowledgeContext?.matchedAspectId)

        // 8. "كم نسبة البطارية الآن؟" -> live telemetry BatteryIntent
        assertTrue(IntentParser.parse("كم نسبة البطارية الآن؟") is WeykyIntent.BatteryIntent)

        // 9. "ما عاصمة مصر؟"
        engine.session.clear()
        val egyptRes = engine.respond("ما عاصمة مصر؟")
        assertEquals("capitals_arab", egyptRes.topic)
        assertTrue(egyptRes.replyText.startsWith("عاصمة مصر هي القاهرة."))

        // 10. "من أنت؟"
        engine.session.clear()
        val idRes = engine.respond("من أنت؟")
        assertEquals("identity", idRes.topic)

        // 11. "ما هو الذكاء الاصطناعي؟"
        engine.session.clear()
        val aiRes = engine.respond("ما هو الذكاء الاصطناعي؟")
        assertEquals("ai_concept", aiRes.topic)

        // 12. "كيف أستخرج جواز سفر؟" -> unindexed_query honest fallback
        engine.session.clear()
        val passportRes = engine.respond("كيف أستخرج جواز سفر؟")
        assertEquals("unindexed_query", passportRes.topic)

        // 13. Standalone greeting vs "هلال"
        engine.session.clear()
        val greetRes = engine.respond("أهلاً")
        assertEquals("chitchat", greetRes.topic)

        engine.session.clear()
        val hilalRes = engine.respond("لماذا يظهر هلال القمر؟")
        assertNotEquals("chitchat", hilalRes.topic)
    }
}
