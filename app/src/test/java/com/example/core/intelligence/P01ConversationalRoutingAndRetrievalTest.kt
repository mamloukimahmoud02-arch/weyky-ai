package com.example.core.intelligence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.conversation.ConversationSession
import com.example.core.intelligence.conversation.ConversationalReasoner
import com.example.core.intelligence.conversation.LocalConversationalEngine
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.cache.KnowledgeCache
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KeywordWithTokensBundle
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intent.IntentParser
import com.example.core.intent.WeykyIntent
import kotlinx.coroutines.Dispatchers
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
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class P01ConversationalRoutingAndRetrievalTest {

    private lateinit var context: Context
    private lateinit var database: WeykyKnowledgeDatabase
    private lateinit var repository: KnowledgeRepository
    private lateinit var reasoner: ConversationalReasoner
    private lateinit var engine: LocalConversationalEngine
    private val converters = KnowledgeTypeConverters()

    @Before
    fun setUp() {
        Locale.setDefault(Locale("ar"))
        KnowledgeRepository.clearInstanceForTesting()
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            WeykyKnowledgeDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        repository = KnowledgeRepository(
            database = database,
            cache = KnowledgeCache(),
            converters = converters,
            ioDispatcher = Dispatchers.IO
        )
        reasoner = ConversationalReasoner(knowledgeRepository = repository)
        engine = LocalConversationalEngine(knowledgeRepository = repository)
    }

    @After
    fun tearDown() {
        KnowledgeRepository.clearInstanceForTesting()
        database.close()
    }

    // =========================================================================
    // Phase 1: Greeting Detection Accuracy & Substring False-Positive Prevention
    // =========================================================================

    @Test
    fun `phase1 standalone greetings are recognized as chitchat`() {
        val validGreetings = listOf(
            "هلا",
            "أهلاً",
            "يا هلا",
            "السلام عليكم",
            "مرحباً",
            "Hi",
            "Hello"
        )

        for (greeting in validGreetings) {
            val session = ConversationSession(knowledgeRepository = repository)
            val result = reasoner.reason(greeting, session)
            assertEquals(
                "Expected '$greeting' to be recognized as chitchat greeting",
                "chitchat",
                result.topicDetected
            )
        }
    }

    @Test
    fun `phase1 words containing substring هلا are never misclassified as greetings`() {
        val nonGreetingQueries = listOf(
            "كيف أقلل استهلاك الطاقة؟",
            "ما أسباب إهلاك البطارية؟",
            "ما مؤهلات النظام؟"
        )

        for (query in nonGreetingQueries) {
            val session = ConversationSession(knowledgeRepository = repository)
            val result = reasoner.reason(query, session)
            assertNotEquals(
                "Query '$query' must NOT be misclassified as chitchat greeting due to 'هلا' substring",
                "chitchat",
                result.topicDetected
            )
            assertFalse(
                "Reply for '$query' must not return greeting text",
                result.replyText.contains("وعليكم السلام")
            )
        }
    }

    // =========================================================================
    // Phase 2: Battery Live Telemetry vs. Knowledge & Contextual Forecast Routing
    // =========================================================================

    @Test
    fun `phase2 live battery telemetry requests route to BatteryIntent`() {
        val liveBatteryQueries = listOf(
            "ما نسبة بطاريتي الآن؟",
            "كم نسبة البطارية؟",
            "ما حالة البطارية؟",
            "هل الهاتف يشحن الآن؟",
            "هل البطارية متصلة بالشاحن؟"
        )

        for (query in liveBatteryQueries) {
            val intent = IntentParser.parse(query)
            assertTrue(
                "Expected BatteryIntent for live telemetry query '$query' but was $intent",
                intent is WeykyIntent.BatteryIntent
            )
        }
    }

    @Test
    fun `phase2 explanatory and advisory battery or power queries route to ConversationalIntent`() {
        val conversationalBatteryQueries = listOf(
            "لماذا تنفد البطارية بسرعة؟",
            "ما أسباب استهلاك البطارية؟",
            "هل الشحن طوال الليل يضر البطارية؟",
            "كيف أحافظ على عمر البطارية؟",
            "كيف أقلل استهلاك الطاقة؟",
            "ما العوامل التي تؤثر في عمر البطارية؟"
        )

        for (query in conversationalBatteryQueries) {
            val intent = IntentParser.parse(query)
            assertTrue(
                "Expected ConversationalIntent for knowledge/causal query '$query' but was $intent",
                intent is WeykyIntent.ConversationalIntent
            )
        }
    }

    @Test
    fun `phase2 contextual battery sufficiency and forecast path is preserved`() {
        val forecastQuery = "هل البطارية تكفي لمشوار ساعة؟"
        val intent = IntentParser.parse(forecastQuery)
        assertTrue(
            "Expected TimeAwareBatteryForecastIntent or ContextualQueryIntent for '$forecastQuery' but was $intent",
            intent is WeykyIntent.TimeAwareBatteryForecastIntent ||
                (intent is WeykyIntent.ContextualQueryIntent && intent.domain == IntentDomain.BATTERY_SUFICIENCY)
        )

        val requirements = ContextRequirementsRegistry.resolve(forecastQuery)
        assertEquals(IntentDomain.BATTERY_SUFICIENCY, requirements.domain)
    }

    // =========================================================================
    // Phase 3 & 4: ai_concept Keyword Cleanup & Supplemental Matching Hardening
    // =========================================================================

    @Test
    fun `phase3 ai_concept keywords replace generic كيف تعمل with domain-constrained phrases`() {
        val aiEntry = SeedKnowledgeCatalog.coreTopicsById["ai_concept"]
        assertNotNull(aiEntry)
        assertFalse(
            "ai_concept must not contain generic keyword 'كيف تعمل'",
            aiEntry!!.keywords.contains("كيف تعمل")
        )
        assertTrue(
            "ai_concept must contain 'كيف يعمل الذكاء الاصطناعي'",
            aiEntry.keywords.contains("كيف يعمل الذكاء الاصطناعي")
        )
        assertTrue(
            "ai_concept must contain 'كيف يفكر الذكاء الاصطناعي'",
            aiEntry.keywords.contains("كيف يفكر الذكاء الاصطناعي")
        )
    }

    @Test
    fun `phase3 and phase4 كيف تعمل النجوم and كيف تعمل الجاذبية never select ai_concept`() = runBlocking {
        // 1. "كيف تعمل النجوم؟" must NOT select ai_concept
        val starsSearch = repository.search("كيف تعمل النجوم؟")
        assertNotEquals("ai_concept", starsSearch.matchedTopicId)
        val starsContext = repository.prepareTurnKnowledgeContext("كيف تعمل النجوم؟")
        assertNotEquals("ai_concept", starsContext.retrievedTopicId)
        val starsResponse = engine.respond("كيف تعمل النجوم؟")
        assertNotEquals("ai_concept", starsResponse.topic)

        // 2. "كيف تعمل الجاذبية؟" must NOT select ai_concept, and must select physics_universe via "الجاذبية"
        val gravitySearch = repository.search("كيف تعمل الجاذبية؟")
        assertEquals("physics_universe", gravitySearch.matchedTopicId)
        val gravityContext = repository.prepareTurnKnowledgeContext("كيف تعمل الجاذبية؟")
        assertEquals("physics_universe", gravityContext.retrievedTopicId)
        val gravityResponse = engine.respond("كيف تعمل الجاذبية؟")
        assertEquals("physics_universe", gravityResponse.topic)
    }

    @Test
    fun `phase4 supplemental and phrase scoring block generic single-verb keywords even in custom bundles`() {
        // Build a synthetic topic containing a generic phrase "كيف يعمل" (stop word "كيف" + generic verb "يعمل")
        val seedBundles = SeedKnowledgeCatalog.buildCoreSeedTopicStorageBundles(converters)
        val genericHeader = KnowledgeTopicHeaderEntity(
            topicId = "generic_verb_topic",
            packId = "test_pack",
            topicOrderIndex = 50,
            arabicSummary = "موضوع تجريبي",
            englishSummary = "Test topic",
            suggestedFollowUpsJson = "[]",
            sourceType = "IMPORTED_JSON",
            packPriority = 10,
            contentVersion = 1,
            isCoreTopic = false,
            isActiveWinner = true
        )
        val genericKw = KnowledgeKeywordEntity(
            keywordId = 5000L,
            topicId = "generic_verb_topic",
            packId = "test_pack",
            keywordOrder = 0,
            originalKeyword = "كيف يعمل",
            strictNormalizedPhrase = "كيف يعمل",
            relaxedNormalizedPhrase = "كيف يعمل",
            relaxedCharLength = 8,
            tokenCount = 2,
            contentTokenCount = 1,
            matchType = "MULTI_WORD_PHRASE",
            language = "ar"
        )
        val genericTokens = listOf(
            KnowledgeKeywordTokenEntity(
                tokenId = 5001L,
                keywordId = 5000L,
                tokenPosition = 0,
                strictToken = "كيف",
                strictStem = "كيف",
                relaxedToken = "كيف",
                relaxedStem = "كيف"
            ),
            KnowledgeKeywordTokenEntity(
                tokenId = 5002L,
                keywordId = 5000L,
                tokenPosition = 1,
                strictToken = "يعمل",
                strictStem = "يعمل",
                relaxedToken = "يعمل",
                relaxedStem = "يعمل"
            )
        )
        val customBundle = TopicStorageBundle(
            header = genericHeader,
            keywordsWithTokens = listOf(KeywordWithTokensBundle(genericKw, genericTokens)),
            detail = null,
            followUpBranches = emptyList()
        )
        val snapshotWithCustom = repository.publishNewSnapshotFromBundles(seedBundles + customBundle)

        // Querying "كيف يعمل المحرك النفاث؟" or "كيف يعمل النجوم؟" must NOT match generic_verb_topic via "يعمل"
        val starsCtx = repository.prepareTurnKnowledgeContext(
            userQuery = "كيف يعمل النجوم؟",
            snapshotOverride = snapshotWithCustom
        )
        assertNotEquals("Generic verb 'يعمل' alone must not match generic_verb_topic", "generic_verb_topic", starsCtx.retrievedTopicId)
        assertEquals("physics_universe", starsCtx.retrievedTopicId)

        val unindexedVerbCtx = repository.prepareTurnKnowledgeContext(
            userQuery = "كيف يعمل المحرك النفاث؟",
            snapshotOverride = snapshotWithCustom
        )
        assertNull("Generic verb 'يعمل' alone must not match generic_verb_topic for unindexed subject", unindexedVerbCtx.retrievedTopicId)

        // Meanwhile, distinctive single-content-token concepts like "ما هو weyky" -> "حدثني عن weyky" still match identity
        val weykyCtx = repository.prepareTurnKnowledgeContext(
            userQuery = "حدثني عن weyky",
            snapshotOverride = snapshotWithCustom
        )
        assertEquals("identity", weykyCtx.retrievedTopicId)
    }

    // =========================================================================
    // Phase 5: Reference Queries & End-to-End Table Verification
    // =========================================================================

    @Test
    fun `phase5 positive reference queries remain completely intact`() = runBlocking {
        // 1. "ما هو الذكاء الاصطناعي؟" -> ai_concept
        val aiRes = engine.respond("ما هو الذكاء الاصطناعي؟")
        assertEquals("ai_concept", aiRes.topic)
        assertTrue(aiRes.replyText.contains("الذكاء الاصطناعي"))

        // 2. "أين تقع صلالة؟" -> salalah_tourism
        engine.session.clear()
        val salalahRes = engine.respond("أين تقع صلالة؟")
        assertEquals("salalah_tourism", salalahRes.topic)
        assertTrue(salalahRes.replyText.contains("صلالة") && salalahRes.replyText.contains("ظفار"))

        // 3. "ما عاصمة مصر؟" -> capitals_arab (starts with "عاصمة مصر هي القاهرة.")
        engine.session.clear()
        val egyptRes = engine.respond("ما عاصمة مصر؟")
        assertEquals("capitals_arab", egyptRes.topic)
        assertTrue(egyptRes.replyText.startsWith("عاصمة مصر هي القاهرة."))

        // 4. "ما نسبة بطاريتي الآن؟" -> BatteryIntent
        val batteryIntent = IntentParser.parse("ما نسبة بطاريتي الآن؟")
        assertTrue(batteryIntent is WeykyIntent.BatteryIntent)
    }
}
