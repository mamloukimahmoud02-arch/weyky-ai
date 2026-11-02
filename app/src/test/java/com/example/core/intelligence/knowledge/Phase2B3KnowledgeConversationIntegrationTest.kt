package com.example.core.intelligence.knowledge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.ContextDatum
import com.example.core.intelligence.EpistemicStatus
import com.example.core.intelligence.conversation.ConversationSession
import com.example.core.intelligence.conversation.ConversationalReasoner
import com.example.core.intelligence.conversation.LocalConversationalEngine
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.cache.KnowledgeCache
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.context.KnowledgeAvailabilityStatus
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KeywordWithTokensBundle
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.search.KnowledgeSearchService
import com.example.core.capabilities.CapabilityDetector
import com.example.core.context.ContextSnapshot
import com.example.core.context.ContextState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2B3KnowledgeConversationIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: WeykyKnowledgeDatabase
    private lateinit var repository: KnowledgeRepository
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
    }

    @After
    fun tearDown() {
        repository.retrievalFailureSimulator = null
        KnowledgeRepository.clearInstanceForTesting()
        database.close()
    }

    // =========================================================================
    // 1. Repository integration into conversational flow
    // =========================================================================

    @Test
    fun `1 repository is actively integrated into LocalConversationalEngine and ConversationalReasoner`() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())
        repository.resetMetricsForTesting()

        val engine = LocalConversationalEngine(knowledgeRepository = repository)
        val response = engine.respond("كم سرعة الضوء في الفضاء؟")

        assertEquals("physics_universe", response.topic)
        assertTrue(response.replyText.contains("300,000") || response.replyText.contains("الضوء"))

        val prepared = response.preparedKnowledgeContext
        assertNotNull("ConversationalResponse must carry PreparedTurnKnowledgeContext", prepared)
        assertEquals("physics_universe", prepared?.retrievedTopicId)
        assertEquals(KnowledgeAvailabilityStatus.MATCHED_USABLE_KNOWLEDGE, prepared?.availabilityStatus)
        assertTrue(prepared?.hasValidKnowledgeMatch == true)
        assertEquals(1, repository.totalTurnPreparations)
        assertEquals(
            "Exactly one search invocation must occur during a single conversational turn",
            1,
            repository.lastTurnSearchInvocations
        )
    }

    // =========================================================================
    // 2. Zero Room I/O on the Main Thread
    // =========================================================================

    @Test
    fun `2 synchronous turn preparation and conversational reasoning perform zero Room IO on Main Thread`() = runBlocking {
        // Build a strict database that WITHOUT allowMainThreadQueries would throw if touched on Main Thread
        val strictDb = Room.inMemoryDatabaseBuilder(
            context,
            WeykyKnowledgeDatabase::class.java
        ).build()

        try {
            val strictRepo = KnowledgeRepository(
                database = strictDb,
                ioDispatcher = Dispatchers.IO
            )
            // Synchronous turn preparation before and after async warm-up must never touch Room on the caller thread
            val engine = LocalConversationalEngine(knowledgeRepository = strictRepo)
            val preWarmupResponse = engine.respond("من أنت؟")
            assertEquals("identity", preWarmupResponse.topic)
            assertNotNull(preWarmupResponse.preparedKnowledgeContext)

            assertTrue(strictRepo.initializeSeedAndWarmUpIndex())

            val postWarmupResponse = engine.respond("حدثني عن صلالة")
            assertEquals("salalah_tourism", postWarmupResponse.topic)
            assertEquals(2L, postWarmupResponse.preparedKnowledgeContext?.knowledgeGeneration)
        } finally {
            strictDb.close()
        }
    }

    // =========================================================================
    // 3. Single-turn snapshot consistency
    // =========================================================================

    @Test
    fun `3 single turn uses a pinned InMemorySearchSnapshot even if repository snapshot updates mid-turn`() {
        val session = ConversationSession(knowledgeRepository = repository)
        session.updateTopic("physics_universe")
        session.updateTopic("salalah_tourism")

        // Capture turn context on generation 1
        val pinnedTurnContext = repository.prepareTurnKnowledgeContext(
            userQuery = "نعود إلى موضوع الفيزياء",
            currentTopicId = session.currentTopic,
            isArabicOverride = true
        )
        assertEquals(1L, pinnedTurnContext.knowledgeGeneration)

        // Advance repository snapshot to generation 2 before ConversationalReasoner consumes pinnedTurnContext
        val seedBundles = SeedKnowledgeCatalog.buildCoreSeedTopicStorageBundles(converters)
        val gen2Snapshot = repository.publishNewSnapshotFromBundles(seedBundles)
        assertEquals(2L, gen2Snapshot.knowledgeGeneration)
        assertEquals(2L, repository.currentGeneration)

        val reasoner = ConversationalReasoner(knowledgeRepository = repository)
        val result = reasoner.reason(
            userQuery = "نعود إلى موضوع الفيزياء",
            session = session,
            preparedContext = pinnedTurnContext
        )

        assertEquals("physics_universe", result.topicDetected)
        assertEquals(
            "Reasoner must preserve the exact snapshot generation captured at turn start",
            1L,
            result.preparedKnowledgeContext?.knowledgeGeneration
        )
        assertSame(pinnedTurnContext.snapshotUsed, result.preparedKnowledgeContext?.snapshotUsed)
    }

    // =========================================================================
    // 4. Graceful retrieval failure handling
    // =========================================================================

    @Test
    fun `4 unexpected retrieval failure is handled gracefully without crashing the conversation pipeline`() = runBlocking {
        repository.retrievalFailureSimulator = {
            RuntimeException("Simulated index retrieval failure")
        }

        val engine = LocalConversationalEngine(knowledgeRepository = repository)
        val response = engine.respond("ما هي سرعة الضوء في الفضاء؟")

        assertNotNull(response)
        assertTrue(response.replyText.isNotBlank())
        assertEquals(
            KnowledgeAvailabilityStatus.RETRIEVAL_FAILURE_OR_UNAVAILABLE,
            response.preparedKnowledgeContext?.availabilityStatus
        )
        assertFalse(response.preparedKnowledgeContext?.hasValidKnowledgeMatch ?: true)
    }

    // =========================================================================
    // 5. No duplicate repository instances
    // =========================================================================

    @Test
    fun `5 singleton KnowledgeRepository is shared across default and context-based initializations without duplicates`() {
        KnowledgeRepository.clearInstanceForTesting()

        val defaultRepo = KnowledgeRepository.getDefaultInstance()
        val contextRepo = KnowledgeRepository.getInstance(context)
        val engine = LocalConversationalEngine()
        val executor = createRealIntentExecutor(localEngine = engine, knowledgeRepo = defaultRepo)

        assertSame("getDefaultInstance and getInstance(context) must return the same singleton", defaultRepo, contextRepo)
        assertSame("LocalConversationalEngine must use the shared singleton by default", defaultRepo, engine.knowledgeRepository)
        assertSame("ConversationSession must use the shared singleton", defaultRepo, engine.session.knowledgeRepository)
        assertSame("IntentExecutor must share the exact same KnowledgeRepository", defaultRepo, executor.knowledgeRepository)
    }

    // =========================================================================
    // 6. Arabic direct knowledge query
    // =========================================================================

    @Test
    fun `6 Arabic direct knowledge queries retrieve accurate topic and response`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val r1 = engine.respond("كيف أحافظ على عمر البطارية؟")
        assertEquals("battery_care", r1.topic)
        assertTrue(r1.replyText.contains("20%") && r1.replyText.contains("80%"))

        val r2 = engine.respond("فوائد شرب الماء")
        assertEquals("hydration_health", r2.topic)
        assertTrue(r2.replyText.contains("الماء"))
    }

    // =========================================================================
    // 7. English direct knowledge query
    // =========================================================================

    @Test
    fun `7 English direct knowledge queries retrieve accurate topic and response`() = runBlocking {
        Locale.setDefault(Locale.US)
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val r1 = engine.respond("What is the speed of light?")
        assertEquals("physics_universe", r1.topic)
        assertTrue(r1.replyText.contains("300,000"))

        val r2 = engine.respond("Give me sleep tips for insomnia")
        assertEquals("sleep_wellness", r2.topic)
        assertTrue(r2.replyText.contains("sleep", ignoreCase = true))
    }

    // =========================================================================
    // 8. Arabic alternative formulations
    // =========================================================================

    @Test
    fun `8 Arabic alternative formulations resolve to identity topic accurately`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val queries = listOf(
            "ما هو ويكي؟",
            "اشرح لي ما هو ويكي.",
            "حدثني عن هويتك",
            "عرف عن نفسك"
        )

        for (q in queries) {
            engine.session.clear()
            val response = engine.respond(q)
            assertEquals("Query '$q' must match identity", "identity", response.topic)
            assertTrue(
                "Reply for '$q' must mention وايكي or Weyky",
                response.replyText.contains("وايكي") || response.replyText.contains("Weyky", ignoreCase = true)
            )
        }
    }

    // =========================================================================
    // 9. English alternative formulations
    // =========================================================================

    @Test
    fun `9 English alternative formulations resolve to identity topic accurately`() = runBlocking {
        Locale.setDefault(Locale.US)
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val queries = listOf(
            "What is WEYKY?",
            "Explain WEYKY.",
            "Tell me about your identity.",
            "Who are you?"
        )

        for (q in queries) {
            engine.session.clear()
            val response = engine.respond(q)
            assertEquals("Query '$q' must match identity", "identity", response.topic)
            assertTrue(
                "Reply for '$q' must mention Weyky",
                response.replyText.contains("Weyky", ignoreCase = true) || response.replyText.contains("وايكي")
            )
        }
    }

    // =========================================================================
    // 10. Short token preservation ("ai", "ram", "gpu", "cpu", "gps", "nfc", "usb")
    // =========================================================================

    @Test
    fun `10 short technical tokens are preserved during tokenization and conversational lookup`() = runBlocking {
        Locale.setDefault(Locale.US)
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val aiResponse = engine.respond("ai")
        assertEquals("ai_concept", aiResponse.topic)
        assertTrue(aiResponse.replyText.contains("Artificial Intelligence", ignoreCase = true))

        val preservedShortTokens = listOf("ai", "ram", "gpu", "cpu", "gps", "nfc", "usb")
        for (shortToken in preservedShortTokens) {
            val normalized = LocalKnowledgeBase.normalizeArabic(shortToken)
            assertEquals("Short technical token '$shortToken' must be preserved intact", shortToken, normalized)
            assertFalse("Short technical token '$shortToken' must not be in STOP_WORDS", shortToken in SeedKnowledgeCatalog.STOP_WORDS)
        }
    }

    // =========================================================================
    // 11. Follow-up question resolution
    // =========================================================================

    @Test
    fun `11 multi-turn follow-up questions resolve accurately using active session topic`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val turn1 = engine.respond("حدثني عن صلالة")
        assertEquals("salalah_tourism", turn1.topic)

        val turn2 = engine.respond("وكيف الطقس هناك؟")
        assertEquals("salalah_tourism", turn2.topic)
        assertTrue(turn2.replyText.contains("22") || turn2.replyText.contains("معتدل"))

        val turn3 = engine.respond("ومتى أفضل وقت لزيارتها؟")
        assertEquals("salalah_tourism", turn3.topic)
        assertTrue(turn3.replyText.contains("يوليو") || turn3.replyText.contains("أغسطس"))
    }

    // =========================================================================
    // 12. Contextual entity continuity ("Tell me about Oman." -> "What about its tourism?")
    // =========================================================================

    @Test
    fun `12 contextual entity continuity resolves Oman inquiry followed by tourism follow-up`() = runBlocking {
        Locale.setDefault(Locale.US)
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val turn1 = engine.respond("Tell me about Oman.")
        assertEquals("salalah_tourism", turn1.topic)
        assertTrue(turn1.replyText.contains("Oman", ignoreCase = true) || turn1.replyText.contains("Salalah", ignoreCase = true))

        val turn2 = engine.respond("What about its tourism?")
        assertEquals("salalah_tourism", turn2.topic)
        assertTrue(
            "Tourism follow-up must return Salalah/Oman tourism highlights",
            turn2.replyText.contains("Wadi Darbat", ignoreCase = true) ||
                turn2.replyText.contains("Mughsail", ignoreCase = true) ||
                turn2.replyText.contains("Salalah", ignoreCase = true)
        )

        // Also verify transition when Turn 1 is capital of Oman ("capitals_arab") -> Turn 2 "What about its tourism?"
        engine.session.clear()
        val capTurn1 = engine.respond("What is the capital of Oman?")
        assertEquals("capitals_arab", capTurn1.topic)
        assertTrue(capTurn1.replyText.contains("Muscat"))

        val capTurn2 = engine.respond("What about its tourism?")
        assertEquals("salalah_tourism", capTurn2.topic)
        assertTrue(capTurn2.replyText.contains("Salalah", ignoreCase = true))
    }

    // =========================================================================
    // 13. Topic continuity across turns
    // =========================================================================

    @Test
    fun `13 topic continuity across multiple turns preserves context and prevents duplicate elaboration repetition`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val t1 = engine.respond("حدثني عن المجموعة الشمسية")
        assertEquals("physics_universe", t1.topic)

        val t2 = engine.respond("وكيف تتكون الكواكب؟")
        assertEquals("physics_universe", t2.topic)
        assertTrue(t2.replyText.contains("السديم"))

        val t3 = engine.respond("وهل يمكن رؤيتها بالعين المجردة؟")
        assertEquals("physics_universe", t3.topic)
        assertTrue(t3.replyText.contains("عطارد") && t3.replyText.contains("الزهرة"))

        // Asking the exact same follow-up again triggers duplicate elaboration protection
        val t4 = engine.respond("وهل يمكن رؤيتها بالعين المجردة؟")
        assertEquals("physics_universe", t4.topic)
        assertFalse("Must not repeat identical elaboration text verbatim", t4.replyText == t3.replyText)
        assertTrue(t4.replyText.contains("استعرضنا بالفعل"))
    }

    // =========================================================================
    // 14. Topic switching without bias ("Explain battery care." -> "Now explain the universe.")
    // =========================================================================

    @Test
    fun `14 topic switching transitions cleanly to new topic without bias from previous topic`() = runBlocking {
        Locale.setDefault(Locale.US)
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val turn1 = engine.respond("Explain battery care.")
        assertEquals("battery_care", turn1.topic)
        assertEquals("battery_care", engine.session.currentTopic)

        val turn2 = engine.respond("Now explain the universe.")
        assertEquals("physics_universe", turn2.topic)
        assertEquals("physics_universe", engine.session.currentTopic)
        assertEquals("battery_care", engine.session.previousTopic)
        assertTrue(turn2.replyText.contains("light", ignoreCase = true) || turn2.replyText.contains("Solar System", ignoreCase = true))
    }

    // =========================================================================
    // 15. Missing context safety
    // =========================================================================

    @Test
    fun `15 follow-up query with no prior session context is handled safely without crashing or fabricating`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)
        assertNull(engine.session.currentTopic)

        val response = engine.respond("وماذا عن ذلك بالتفصيل؟")
        assertNotNull(response)
        assertTrue(response.replyText.isNotBlank())
        assertEquals("unindexed_query", response.topic)
        assertFalse(response.preparedKnowledgeContext?.hasValidKnowledgeMatch ?: true)
    }

    // =========================================================================
    // 16. Conditional currentTopicId passing
    // =========================================================================

    @Test
    fun `16 conditional currentTopicId passing ignores non-knowledge topics and never overrides stronger new topics`() {
        // 1. Non-knowledge topic ("chitchat" or "calculation") must not be passed as effectiveSearchContextTopicId
        val chitchatContext = repository.prepareTurnKnowledgeContext(
            userQuery = "كم سرعة الضوء؟",
            currentTopicId = "chitchat"
        )
        assertNull(
            "Non-knowledge topic 'chitchat' must not be used as effectiveSearchContextTopicId",
            chitchatContext.effectiveSearchContextTopicId
        )
        assertEquals("physics_universe", chitchatContext.retrievedTopicId)

        // 2. Active knowledge topic ("salalah_tourism") must not override a new unrelated query ("نصائح للنوم")
        val switchContext = repository.prepareTurnKnowledgeContext(
            userQuery = "نصائح للنوم العميق",
            currentTopicId = "salalah_tourism"
        )
        assertEquals("sleep_wellness", switchContext.retrievedTopicId)
        assertNull(
            "Unrelated new query without continuity signal must not receive continuity bias from old topic",
            switchContext.effectiveSearchContextTopicId
        )
    }

    // =========================================================================
    // 17. Knowledge-backed response correctness
    // =========================================================================

    @Test
    fun `17 knowledge-backed responses match canonical topic summaries and include proactive suggestions`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        for (seedTopic in SeedKnowledgeCatalog.coreTopics) {
            engine.session.clear()
            val sampleKeyword = seedTopic.keywords.first()
            val context = repository.prepareTurnKnowledgeContext(
                userQuery = sampleKeyword,
                currentTopicId = null,
                isArabicOverride = true
            )
            assertTrue("Seed topic '${seedTopic.topicId}' must match its primary keyword '$sampleKeyword'", context.hasValidKnowledgeMatch)
            assertEquals(seedTopic.topicId, context.retrievedTopicId)
            val resolvedText = context.resolveMatchedResponseText()
            assertNotNull(resolvedText)
            assertTrue(resolvedText!!.isNotBlank())
            assertTrue(context.retrievedTopic!!.suggestedFollowUps.isNotEmpty())
        }
    }

    // =========================================================================
    // 18. Missing topic details handling
    // =========================================================================

    @Test
    fun `18 matched topic with blank summary reports MATCHED_INSUFFICIENT_INFO and falls back honestly`() {
        // Construct a bundle where a custom topic has a keyword match but an empty localized summary
        val seedBundles = SeedKnowledgeCatalog.buildCoreSeedTopicStorageBundles(converters)
        val blankSummaryHeader = KnowledgeTopicHeaderEntity(
            topicId = "incomplete_topic",
            packId = SeedKnowledgeCatalog.CORE_SEED_PACK_ID,
            topicOrderIndex = 99,
            arabicSummary = "",
            englishSummary = "",
            suggestedFollowUpsJson = "[]",
            sourceType = SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE,
            packPriority = SeedKnowledgeCatalog.CORE_SEED_PACK_PRIORITY,
            contentVersion = SeedKnowledgeCatalog.CORE_SEED_CONTENT_VERSION,
            isCoreTopic = false,
            isActiveWinner = true
        )
        val kwEntity = KnowledgeKeywordEntity(
            keywordId = 9999L,
            topicId = "incomplete_topic",
            packId = SeedKnowledgeCatalog.CORE_SEED_PACK_ID,
            keywordOrder = 0,
            originalKeyword = "موضوع اختباري فارغ",
            strictNormalizedPhrase = "موضوع اختباري فارغ",
            relaxedNormalizedPhrase = "موضوع اختباري فارغ",
            relaxedCharLength = "موضوع اختباري فارغ".length,
            tokenCount = 3,
            contentTokenCount = 3,
            matchType = "MULTI_WORD_PHRASE",
            language = "ar"
        )
        val tokEntities = listOf("موضوع", "اختباري", "فارغ").mapIndexed { idx, tok ->
            KnowledgeKeywordTokenEntity(
                tokenId = 99990L + idx,
                keywordId = 9999L,
                tokenPosition = idx,
                strictToken = tok,
                strictStem = LocalKnowledgeBase.stripArabicPrefixes(tok),
                relaxedToken = tok,
                relaxedStem = LocalKnowledgeBase.stripArabicPrefixes(tok)
            )
        }
        val incompleteBundle = TopicStorageBundle(
            header = blankSummaryHeader,
            keywordsWithTokens = listOf(KeywordWithTokensBundle(kwEntity, tokEntities)),
            detail = null,
            followUpBranches = emptyList()
        )
        repository.publishNewSnapshotFromBundles(seedBundles + incompleteBundle)

        val session = ConversationSession(knowledgeRepository = repository)
        val reasoner = ConversationalReasoner(knowledgeRepository = repository)
        val result = reasoner.reason("حدثني عن موضوع اختباري فارغ", session)

        assertEquals(
            KnowledgeAvailabilityStatus.MATCHED_INSUFFICIENT_INFO,
            result.preparedKnowledgeContext?.availabilityStatus
        )
        assertFalse(result.preparedKnowledgeContext?.hasValidKnowledgeMatch ?: true)
        assertEquals("unindexed_query", result.topicDetected)
        assertTrue(result.replyText.contains("غير مسجلة"))
    }

    // =========================================================================
    // 19. Unsupported query rejection
    // =========================================================================

    @Test
    fun `19 unsupported queries and protected root queries are rejected honestly without fabrication`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val unsupportedQueries = listOf(
            "كيف أصلح محرك طائرة نفاثة في المنزل؟",
            "ما هي وصفة تحضير الكنافة النابلسية بالتفصيل؟",
            "ما هو سعر البيتكوين اللحظي الآن؟"
        )
        for (q in unsupportedQueries) {
            val response = engine.respond(q)
            assertFalse(
                "Unsupported query '$q' must not report a valid knowledge match",
                response.preparedKnowledgeContext?.hasValidKnowledgeMatch ?: true
            )
            assertTrue(
                "Unsupported query '$q' must return unindexed_query or unavailable_external_data",
                response.topic == "unindexed_query" || response.topic == "unavailable_external_data"
            )
        }

        // Protected single-word root queries from Phase 2B.2 must still not false-positive match
        val protectedRoots = listOf("باريس", "بطارية", "فيزياء", "فرنسا", "تونس")
        for (root in protectedRoots) {
            val ctx = repository.prepareTurnKnowledgeContext(root)
            assertFalse("Protected root '$root' must not match a knowledge topic", ctx.hasValidKnowledgeMatch)
            assertNull(ctx.retrievedTopicId)
        }
    }

    // =========================================================================
    // 20. Weak single-word overlap rejection
    // =========================================================================

    @Test
    fun `20 weak single-word overlap queries are rejected below threshold 80`() = runBlocking {
        Locale.setDefault(Locale.US)
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val weakQueries = listOf(
            "Tell me something",
            "What is the best thing?",
            "Give me information",
            "حدثني عن شيء مهم",
            "أريد معلومة مفيدة"
        )

        for (q in weakQueries) {
            val response = engine.respond(q)
            assertFalse(
                "Weak overlap query '$q' must not match any knowledge topic",
                response.preparedKnowledgeContext?.hasValidKnowledgeMatch ?: true
            )
            assertNull(response.preparedKnowledgeContext?.retrievedTopicId)
        }
    }

    // =========================================================================
    // 21. Capital resolution preservation
    // =========================================================================

    @Test
    fun `21 specific country capital resolution is preserved for direct and follow-up queries`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val r1 = engine.respond("ما هي عاصمة اليابان؟")
        assertEquals("capitals_arab", r1.topic)
        assertTrue(r1.replyText.contains("طوكيو"))
        assertNotNull(r1.preparedKnowledgeContext?.specificCapitalAnswer)

        val r2 = engine.respond("وماذا عن المغرب؟")
        assertEquals("capitals_arab", r2.topic)
        assertTrue(r2.replyText.contains("الرباط"))
    }

    // =========================================================================
    // 22. Follow-up elaboration preservation
    // =========================================================================

    @Test
    fun `22 follow-up elaborations are preserved across all core topics`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        // Sleep -> Coffee follow-up
        engine.respond("نصائح للنوم")
        val coffeeFollowUp = engine.respond("وما تأثير القهوة على النوم؟")
        assertEquals("sleep_wellness", coffeeFollowUp.topic)
        assertTrue(coffeeFollowUp.replyText.contains("الكافيين"))

        // Productivity -> Multitasking follow-up
        engine.session.clear()
        engine.respond("كيف أنظم وقتي؟")
        val prodFollowUp = engine.respond("أعطني نصيحة للتركيز")
        assertEquals("productivity", prodFollowUp.topic)
        assertTrue(prodFollowUp.replyText.contains("تعدد المهام") || prodFollowUp.replyText.contains("بومودورو"))
    }

    // =========================================================================
    // 23. Actionable suggestions preservation
    // =========================================================================

    @Test
    fun `23 actionable suggestions are preserved on direct matches, follow-ups, and fallbacks`() = runBlocking {
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        val direct = engine.respond("حدثني عن الذكاء الاصطناعي")
        assertTrue("Direct knowledge match must include actionableSuggestions", direct.actionableSuggestions.isNotEmpty())

        val followUp = engine.respond("اشرح لي المزيد عن ذلك")
        assertTrue("Follow-up response must include actionableSuggestions", followUp.actionableSuggestions.isNotEmpty())

        val fallback = engine.respond("كيف أصلح غسالة الأطباق الصناعية؟")
        assertTrue("Principled fallback must include actionableSuggestions", fallback.actionableSuggestions.isNotEmpty())
    }

    // =========================================================================
    // 24. Response classification preservation
    // =========================================================================

    @Test
    fun `24 epistemic classifications FACT CALCULATION ESTIMATE INFERENCE RECOMMENDATION UNAVAILABLE are preserved`() {
        val factDatum = ContextDatum.fact(300000, "KnowledgeRepository")
        val calcDatum = ContextDatum.calculation(42, "ArithmeticEvaluator")
        val estDatum = ContextDatum.estimate(120, "BatteryModel")
        val infDatum = ContextDatum.inference("Tight schedule", "ContextReasoningEngine")
        val recDatum = ContextDatum(
            value = "Avoid caffeine after 2 PM",
            status = EpistemicStatus.RECOMMENDATION,
            source = "SleepWellness"
        )
        val unavailDatum = ContextDatum.unavailable<String>("ExternalWeb", "Offline mode")

        assertEquals(EpistemicStatus.FACT, factDatum.status)
        assertTrue(factDatum.isAvailable)
        assertEquals(EpistemicStatus.CALCULATION, calcDatum.status)
        assertTrue(calcDatum.isAvailable)
        assertEquals(EpistemicStatus.ESTIMATE, estDatum.status)
        assertTrue(estDatum.isAvailable)
        assertEquals(EpistemicStatus.INFERENCE, infDatum.status)
        assertTrue(infDatum.isAvailable)
        assertEquals(EpistemicStatus.RECOMMENDATION, recDatum.status)
        assertTrue(recDatum.isAvailable)
        assertEquals(EpistemicStatus.UNAVAILABLE, unavailDatum.status)
        assertFalse(unavailDatum.isAvailable)
    }

    // =========================================================================
    // 25. Existing intent parsing preservation
    // =========================================================================

    @Test
    fun `25 existing IntentParser routing for commands and conversational queries is preserved`() {
        assertTrue(IntentParser.parse("شغل الكشاف") is WeykyIntent.FlashlightIntent)
        assertTrue(IntentParser.parse("كم نسبة البطارية؟") is WeykyIntent.BatteryIntent)
        assertTrue(IntentParser.parse("ارفع الصوت") is WeykyIntent.VolumeStepIntent)
        assertTrue(IntentParser.parse("ما حالة الطقس؟") is WeykyIntent.WeatherIntent)
        assertTrue(IntentParser.parse("كم سرعة الضوء؟") is WeykyIntent.ConversationalIntent)
        assertTrue(IntentParser.parse("What is WEYKY?") is WeykyIntent.ConversationalIntent)
    }

    // =========================================================================
    // 26. Existing device action execution & fallback session preservation
    // =========================================================================

    @Test
    fun `26 IntentExecutor preserves conversational and fallback session execution with KnowledgeRepository`() = runBlocking {
        // Test IntentExecutor when localConversationalEngine == null (fallbackSession + fallbackReasoner path)
        val fallbackExecutor = createRealIntentExecutor(
            localEngine = null,
            knowledgeRepo = repository
        )

        val r1 = fallbackExecutor.execute(IntentParser.parse("حدثني عن صلالة", fallbackExecutor.activeSession))
        assertTrue(r1 is ExecutionResult.Success)
        assertEquals("salalah_tourism", fallbackExecutor.activeSession.currentTopic)

        val r2 = fallbackExecutor.execute(IntentParser.parse("وكيف الطقس هناك؟", fallbackExecutor.activeSession))
        assertTrue(r2 is ExecutionResult.Success)
        val speech2 = (r2 as ExecutionResult.Success).speech
        assertTrue(speech2.contains("22") || speech2.contains("معتدل"))
    }

    private fun createRealIntentExecutor(
        localEngine: LocalConversationalEngine?,
        knowledgeRepo: KnowledgeRepository = repository
    ): IntentExecutor {
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
            deviceInfoProvider = null,
            knowledgeRepository = knowledgeRepo
        )
    }

    // =========================================================================
    // 27. Full regression compatibility & end-to-end turn latency verification
    // =========================================================================

    @Test
    fun `27 full regression compatibility and sub-10ms average end-to-end conversational turn latency`() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())
        val engine = LocalConversationalEngine(knowledgeRepository = repository)

        // Explicit topic return flow: Physics -> Capitals -> Return to Physics
        val t1 = engine.respond("كم سرعة الضوء؟")
        assertEquals("physics_universe", t1.topic)

        val t2 = engine.respond("ما هي عاصمة السعودية؟")
        assertEquals("capitals_arab", t2.topic)
        assertTrue(t2.replyText.contains("الرياض"))

        val t3 = engine.respond("نعود إلى موضوع الفيزياء")
        assertEquals("physics_universe", t3.topic)
        assertTrue(t3.replyText.startsWith("بالعودة إلى موضوعنا السابق: "))

        // Unsupported arithmetic parentheses rejection preserved
        val badMath = engine.respond("احسب (2 + 3) * 4")
        assertFalse("Must not return wrong calculation 14 for (2 + 3) * 4", badMath.replyText.contains("= 14"))
        assertTrue("Must disclose parentheses boundary honestly", badMath.replyText.contains("لا يدعم الأقواس"))

        // End-to-end conversational turn latency benchmark across 50 turns
        val benchmarkQueries = listOf(
            "من أنت؟",
            "What is WEYKY?",
            "كم سرعة الضوء؟",
            "حدثني عن صلالة",
            "وكيف الطقس هناك؟",
            "ما عاصمة عمان؟",
            "وماذا عن السياحة فيها؟",
            "Explain battery care.",
            "Now explain the universe.",
            "كيف أصلح محرك صاروخ؟"
        )

        val startNanos = System.nanoTime()
        var iterations = 0
        repeat(5) {
            engine.session.clear()
            for (q in benchmarkQueries) {
                val res = engine.respond(q)
                assertTrue(res.replyText.isNotBlank())
                iterations++
            }
        }
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000.0
        val avgTurnMillis = elapsedMillis / iterations
        println(
            "PHASE_2B3_TURN_BENCHMARK: totalTurns=$iterations, totalMs=${String.format(Locale.US, "%.3f", elapsedMillis)}, avgTurnMs=${String.format(Locale.US, "%.4f", avgTurnMillis)}"
        )
        assertTrue(
            "Average end-to-end conversational turn latency (${String.format(Locale.US, "%.3f", avgTurnMillis)} ms) must be < 10 ms",
            avgTurnMillis < 10.0
        )
    }

    private fun assertNotEquals(unexpected: Any?, actual: Any?) {
        assertFalse("Expected value to differ from '$unexpected'", unexpected == actual)
    }
}
