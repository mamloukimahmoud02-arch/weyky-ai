package com.example.core.intelligence.knowledge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.cache.KnowledgeCache
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.catalog.SeedTopicCatalogEntry
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KeywordWithTokensBundle
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.search.InMemorySearchSnapshot
import com.example.core.intelligence.knowledge.search.KnowledgeSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2B2KnowledgeSearchAndRepositoryTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var db: WeykyKnowledgeDatabase
    private lateinit var repository: KnowledgeRepository
    private lateinit var searchService: KnowledgeSearchService

    private val expectedOrderedTopicIds = listOf(
        "identity",
        "capabilities",
        "sleep_wellness",
        "productivity",
        "ai_concept",
        "capitals_arab",
        "salalah_tourism",
        "physics_universe",
        "short_story",
        "hydration_health",
        "battery_care"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("test_weyky_knowledge_2b2.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }
        // Build real file-backed Room database WITHOUT allowMainThreadQueries()
        db = Room.databaseBuilder(
            context,
            WeykyKnowledgeDatabase::class.java,
            dbFile.name
        ).build()
        repository = KnowledgeRepository(database = db)
        searchService = KnowledgeSearchService(cache = repository.cache)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        WeykyKnowledgeDatabase.clearInstanceForTesting()
        KnowledgeRepository.clearInstanceForTesting()
        if (::dbFile.isInitialized && dbFile.exists()) {
            dbFile.delete()
        }
    }

    // =========================================================================
    // 1–3. SEED KNOWLEDGE CATALOG VERIFICATION
    // =========================================================================

    @Test
    fun seedCatalogContainsExactCoreTopics() {
        assertEquals(11, SeedKnowledgeCatalog.coreTopics.size)
        val actualIds = SeedKnowledgeCatalog.coreTopics.map { it.topicId }
        assertEquals(expectedOrderedTopicIds, actualIds)

        for (id in expectedOrderedTopicIds) {
            val legacy = LocalKnowledgeBase.getTopicById(id)
            assertNotNull("Expected topic '$id' to exist in LocalKnowledgeBase", legacy)
            assertNotNull("Expected topic '$id' to exist in SeedKnowledgeCatalog", SeedKnowledgeCatalog.coreTopicsById[id])
        }
        assertEquals(37, SeedKnowledgeCatalog.COUNTRY_CAPITALS_MAP.size)
    }

    @Test
    fun seedCatalogPreservesTopicOrder() {
        for ((expectedIndex, expectedId) in expectedOrderedTopicIds.withIndex()) {
            val entry = SeedKnowledgeCatalog.coreTopics[expectedIndex]
            assertEquals(expectedId, entry.topicId)
            assertEquals(expectedIndex, entry.topicOrderIndex)
        }
    }

    @Test
    fun seedCatalogPreservesExactKeywordsAndSummaries() {
        for (entry in SeedKnowledgeCatalog.coreTopics) {
            val legacy = LocalKnowledgeBase.getTopicById(entry.topicId)
            assertNotNull(legacy)
            val converted = entry.toLegacyKnowledgeTopic()
            assertEquals("Full KnowledgeTopic equality check for ${entry.topicId}", legacy, converted)
            assertEquals(legacy!!.id, converted.id)
            assertEquals(legacy.keywords, converted.keywords)
            assertEquals(legacy.arabicSummary, converted.arabicSummary)
            assertEquals(legacy.englishSummary, converted.englishSummary)
            assertEquals(legacy.suggestedFollowUps, converted.suggestedFollowUps)
        }
    }

    // =========================================================================
    // 4–6. IN-MEMORY SEARCH SNAPSHOT VERIFICATION
    // =========================================================================

    @Test
    fun snapshotBuildsCorrectKeywordEntries() {
        val snapshot = InMemorySearchSnapshot.fromSeedCatalog(knowledgeGeneration = 1L)
        assertEquals(11, snapshot.topicCount)

        val expectedTotalKeywords = SeedKnowledgeCatalog.coreTopics.sumOf { it.keywords.size }
        assertEquals(expectedTotalKeywords, snapshot.keywordCount)

        for (entry in SeedKnowledgeCatalog.coreTopics) {
            val snapshotKws = snapshot.keywordEntriesByTopic[entry.topicId]
            assertNotNull(snapshotKws)
            assertEquals(entry.keywords.size, snapshotKws!!.size)

            for ((kwIdx, rawKw) in entry.keywords.withIndex()) {
                val kwEntry = snapshotKws[kwIdx]
                val expectedStrict = LocalKnowledgeBase.normalizeArabicStrict(rawKw)
                val expectedRelaxed = LocalKnowledgeBase.normalizeArabic(rawKw)
                val expectedTokens = expectedRelaxed.split(" ").filter { it.isNotBlank() }
                val expectedContentIndices = expectedTokens.indices
                    .filter { expectedTokens[it] !in SeedKnowledgeCatalog.STOP_WORDS }
                    .ifEmpty { expectedTokens.indices.toList() }

                assertEquals(entry.topicId, kwEntry.topicId)
                assertEquals(entry.topicOrderIndex, kwEntry.topicOrderIndex)
                assertEquals(kwIdx, kwEntry.keywordOrder)
                assertEquals(rawKw, kwEntry.originalKeyword)
                assertEquals(expectedStrict, kwEntry.strictNormalizedPhrase)
                assertEquals(expectedRelaxed, kwEntry.relaxedNormalizedPhrase)
                assertEquals(expectedRelaxed.length, kwEntry.relaxedCharLength)
                assertEquals(expectedTokens.size, kwEntry.tokenCount)
                assertEquals(expectedContentIndices.size, kwEntry.contentTokenCount)
                assertEquals(expectedContentIndices, kwEntry.contentTokenPositions)
            }
        }
    }

    @Test
    fun snapshotPreservesTokenPositions() {
        val snapshot = InMemorySearchSnapshot.fromSeedCatalog(knowledgeGeneration = 1L)
        for (kwEntry in snapshot.keywordEntries) {
            assertEquals(kwEntry.tokenCount, kwEntry.tokens.size)
            for ((idx, tok) in kwEntry.tokens.withIndex()) {
                assertEquals("Token position must match index in ${kwEntry.originalKeyword}", idx, tok.tokenPosition)
                assertEquals(kwEntry.strictTokens[idx], tok.strictToken)
                assertEquals(kwEntry.relaxedTokens[idx], tok.relaxedToken)
                assertEquals(
                    LocalKnowledgeBase.stripArabicPrefixes(tok.strictToken).ifEmpty { tok.strictToken },
                    tok.strictStem
                )
                assertEquals(
                    LocalKnowledgeBase.stripArabicPrefixes(tok.relaxedToken).ifEmpty { tok.relaxedToken },
                    tok.relaxedStem
                )
            }
        }
    }

    @Test
    fun snapshotPreservesTopicAndKeywordOrder() {
        val snapshot = InMemorySearchSnapshot.fromSeedCatalog(knowledgeGeneration = 1L)
        assertEquals(expectedOrderedTopicIds, snapshot.orderedTopicIds)
        for ((idx, id) in expectedOrderedTopicIds.withIndex()) {
            assertEquals(idx, snapshot.topicOrderIndex[id])
            assertEquals(id, snapshot.orderedTopics[idx].id)
        }
        var expectedGlobalIdx = 0
        for (topicId in expectedOrderedTopicIds) {
            val kwList = snapshot.keywordEntriesByTopic[topicId]!!
            for ((localOrder, kw) in kwList.withIndex()) {
                assertEquals(localOrder, kw.keywordOrder)
                assertEquals(expectedGlobalIdx, kw.globalKeywordIndex)
                expectedGlobalIdx++
            }
        }
    }

    // =========================================================================
    // 7–17. GOLDEN SEARCH PARITY & EXHAUSTIVE VS INDEXED SCORING
    // =========================================================================

    @Test
    fun indexedCandidateRetrievalParity() {
        val snapshot = repository.currentSnapshot
        val goldenQueries = buildGoldenQueryCorpus()
        val contexts = listOf<String?>(
            null,
            "identity",
            "sleep_wellness",
            "productivity",
            "ai_concept",
            "capitals_arab",
            "salalah_tourism",
            "physics_universe",
            "travel",
            "health_wellness"
        )

        for (contextTopicId in contexts) {
            for (query in goldenQueries) {
                val exhaustiveResult = searchService.searchExhaustive(query, contextTopicId, snapshot)
                val indexedResult = searchService.search(query, contextTopicId, snapshot)

                val legacyPositiveTopics = exhaustiveResult.orderedCandidates
                    .filter { it.rawScore > 0 }
                    .map { it.topicId }
                    .toSet()

                val indexedRetrievedTopics = indexedResult.candidateTopicIdsRetrievedByIndex

                // 1. Prove LegacyPositiveCandidates ⊆ IndexedCandidates
                assertTrue(
                    "LegacyPositiveCandidates ($legacyPositiveTopics) must be a subset of IndexedCandidates ($indexedRetrievedTopics) for query='$query', ctx='$contextTopicId'",
                    indexedRetrievedTopics.containsAll(legacyPositiveTopics)
                )

                // 2. Prove every positive candidate's rawScore, finalScore, and matchedKeywordsCount match identically
                val indexedPositiveCandidates = indexedResult.orderedCandidates.filter { it.rawScore > 0 }
                val exhaustivePositiveCandidates = exhaustiveResult.orderedCandidates.filter { it.rawScore > 0 }
                assertEquals(
                    "Positive candidate scores and order must match for query='$query', ctx='$contextTopicId'",
                    exhaustivePositiveCandidates,
                    indexedPositiveCandidates
                )

                // 3. Prove winner and finalScore match both Exhaustive and Legacy LocalKnowledgeBase
                val legacyWinner = LocalKnowledgeBase.findMatchingTopic(query, contextTopicId)
                assertEquals(
                    "Winning topicId must match legacy LocalKnowledgeBase for query='$query', ctx='$contextTopicId'",
                    legacyWinner?.id,
                    indexedResult.matchedTopicId
                )
                assertEquals(
                    "Exhaustive vs Indexed winning topicId must match for query='$query', ctx='$contextTopicId'",
                    exhaustiveResult.matchedTopicId,
                    indexedResult.matchedTopicId
                )
                assertEquals(
                    "Exhaustive vs Indexed finalScore must match for query='$query', ctx='$contextTopicId'",
                    exhaustiveResult.finalScore,
                    indexedResult.finalScore
                )
                if (legacyWinner != null) {
                    val matched = indexedResult.matchedTopic
                    assertNotNull(matched)
                    assertEquals(legacyWinner.id, matched!!.id)
                    assertEquals(legacyWinner.keywords, matched.keywords)
                    assertEquals(legacyWinner.arabicSummary, matched.arabicSummary)
                    assertEquals(legacyWinner.englishSummary, matched.englishSummary)
                    assertEquals(legacyWinner.suggestedFollowUps, matched.suggestedFollowUps)
                }
            }
        }
    }

    @Test
    fun indexedSearchWinnerParityArabic() {
        val arabicQueries = listOf(
            // Direct questions
            "من أنت؟",
            "عرفني بنفسك",
            "ماذا تستطيع أن تفعل؟",
            "أعطني نصائح للنوم",
            "كيف أتخلص من الأرق؟",
            "تنظيم الوقت والدراسة",
            "ما هي تقنية بومودورو؟",
            "حدثني عن الذكاء الاصطناعي",
            "ما هي سرعة الضوء؟",
            "كم عدد كواكب المجموعة الشمسية؟",
            "احك لي قصة قصيرة",
            "فوائد شرب الماء",
            "كيف أحافظ على صحة البطارية؟",
            "حدثني عن خريف صلالة",
            "ما هي أبرز العواصم العربية؟",
            // Hamza variations
            "ادارة الوقت والانتاجيه",
            "اطاله عمر البطاريه",
            "ابرز العواصم العربيه",
            "اريد الهام وحكمه",
            // Taa Marbuta vs Haa & Alef Maqsura vs Yaa
            "صلاله في سلطنه عمان",
            "الجاذبيه والفضاء",
            "حمايه بطاريه الهاتف",
            "متى افضل وقت للنوم",
            // Prefixes & clitics (وال، بال، كال، فال، لل، ال، و، ب، ل، ف)
            "والنوم الصحي",
            "بالتركيز وبومودورو",
            "للجاذبية والكون",
            "فصلالة جميلة",
            "وبالذكاء الاصطناعي",
            "للبطارية وشحن الهاتف",
            // Punctuation & Tashkeel
            "مَا هِيَ سُرْعَةُ الضَّوْءِ؟!...",
            "«تَنْظِيمُ الْوَقْتِ» — كَيْفَ أَبْدَأُ؟"
        )

        for (query in arabicQueries) {
            assertFullLegacyParity(query, null)
        }
    }

    @Test
    fun indexedSearchWinnerParityEnglish() {
        val englishQueries = listOf(
            "Who are you?",
            "WHAT CAN YOU DO?!",
            "Tell me about sleep and insomnia",
            "Productivity and time management tips",
            "How does Pomodoro work?",
            "What is AI?",
            "Explain artificial intelligence and machine learning",
            "World capitals and Arab capitals",
            "Visiting Salalah and Dhofar in Khareef",
            "What is the speed of light in the solar system?",
            "Gravity and planets in the universe",
            "Tell me a short story",
            "Benefits of drinking water and daily water intake",
            "Battery health and battery lifespan tips",
            "How to save battery?"
        )

        for (query in englishQueries) {
            assertFullLegacyParity(query, null)
        }
    }

    @Test
    fun strictAndRelaxedHomographParity() {
        // Test queries containing words from AMBIGUOUS_HOMOGRAPH_WORDS:
        // "سال", "مهمه", "سنه", "حاله", "عده", "مره", "قوه", "بنه", "حبه"
        val homographQueries = listOf(
            "مهمه جدا",
            "مهمة جدا",
            "سأل عن الوقت",
            "سال الماء في الوادي",
            "السنة الضوئية في الفضاء",
            "سنه حسنه",
            "حاله الطقس",
            "قوه الجاذبيه",
            "مره اخري",
            "حبه دواء"
        )

        for (query in homographQueries) {
            assertFullLegacyParity(query, null)
        }
    }

    @Test
    fun protectedRootParity() {
        // Verify PROTECTED_ROOT_WORDS (باريس، بغداد، لبنان، لندن، وليد، صلالة، فيزياء، بطارية، بومودورو...)
        // are never corrupted by single-letter clitic stripping
        val protectedRootQueries = listOf(
            "باريس",
            "بغداد",
            "لبنان",
            "لندن",
            "وليد",
            "بومودورو",
            "صلالة",
            "بطارية",
            "فيزياء",
            "كافيين",
            "مسقط",
            "تونس",
            "فرنسا"
        )

        for (query in protectedRootQueries) {
            assertFullLegacyParity(query, null)
        }
    }

    @Test
    fun shortTokenMatchingParity() {
        // Verify "ai" (2 chars) matches:
        // - Exact full query match ("ai" == "ai"): 204 + 1 * 30 = 234
        // - Contiguous phrase match inside multi-token query ("what is ai"): 95 + 2 + 4 = 101
        // Whereas other <=2 char tokens score 40 on word-boundary match (below 80 threshold)
        val exactAiResult = repository.search("ai")
        val legacyExactAi = LocalKnowledgeBase.findMatchingTopic("ai")
        assertNotNull(legacyExactAi)
        assertEquals("ai_concept", exactAiResult.matchedTopicId)
        assertEquals(legacyExactAi?.id, exactAiResult.matchedTopicId)
        assertEquals(234, exactAiResult.finalScore) // 204 + 1 * 30 = 234

        val phraseAiResult = repository.search("what is ai")
        val legacyPhraseAi = LocalKnowledgeBase.findMatchingTopic("what is ai")
        assertNotNull(legacyPhraseAi)
        assertEquals("ai_concept", phraseAiResult.matchedTopicId)
        assertEquals(legacyPhraseAi?.id, phraseAiResult.matchedTopicId)
        assertEquals(101, phraseAiResult.finalScore) // 95 + 2 + 4 = 101

        val shortNonAiQueries = listOf("من", "ما", "في", "to", "is", "ab")
        for (q in shortNonAiQueries) {
            assertFullLegacyParity(q, null)
            assertNull(repository.findMatchingTopic(q))
        }
    }

    @Test
    fun phraseMatchingParity() {
        // Verify exact strict (204 + 30*N), exact relaxed (200 + 30*N), contiguous multi-word
        // (130 + 25*N + len + bonus), and order-independent multi-token (115 + 20*M)
        val snapshot = repository.currentSnapshot

        // 1. Exact strict match: "تنظيم الوقت" (2 tokens -> 204 + 60 = 264)
        val exactStrict = searchService.search("تنظيم الوقت", null, snapshot)
        assertEquals("productivity", exactStrict.matchedTopicId)
        assertEquals(264, exactStrict.rawScore)
        assertFullLegacyParity("تنظيم الوقت", null)

        // 2. Exact relaxed match: "الذكاء الاصطناعى" ('ى' -> 'ي' in both strict & relaxed) or
        // "اداره الوقت" ('إدارة الوقت' where 'ة' -> 'ه' only in relaxed -> 200 + 60 = 260)
        val exactRelaxed = searchService.search("اداره الوقت", null, snapshot)
        assertEquals("productivity", exactRelaxed.matchedTopicId)
        assertEquals(260, exactRelaxed.rawScore)
        assertFullLegacyParity("اداره الوقت", null)

        // 3. Contiguous phrase on word boundary inside longer query
        val contiguous = searchService.search("اريد معرفة سرعة الضوء في الفضاء", null, snapshot)
        assertEquals("physics_universe", contiguous.matchedTopicId)
        assertFullLegacyParity("اريد معرفة سرعة الضوء في الفضاء", null)

        // 4. Order-independent multi-token match (reversed word order: "الوقت تنظيم")
        val reordered = searchService.search("الوقت تنظيم", null, snapshot)
        assertEquals("productivity", reordered.matchedTopicId)
        assertEquals(115 + 2 * 20, reordered.rawScore) // 155
        assertFullLegacyParity("الوقت تنظيم", null)
    }

    @Test
    fun multiKeywordBonusParity() {
        // Query matching multiple keywords in physics_universe ("سرعة الضوء", "الجاذبية", "الفضاء", "الكواكب")
        val multiKwQuery = "حدثني عن سرعة الضوء والجاذبية والكواكب في الفضاء"
        val res = repository.search(multiKwQuery)
        assertEquals("physics_universe", res.matchedTopicId)
        val candidate = res.orderedCandidates.first { it.topicId == "physics_universe" }
        assertTrue("Expected matchedKeywordsCount >= 3", candidate.matchedKeywordsCount >= 3)
        assertFullLegacyParity(multiKwQuery, null)
    }

    @Test
    fun continuityTieBreakerParity() {
        // Test continuity tie-breaker when currentTopicId is set and query has a continuity signal
        // vs when query has NO continuity signal or competing topic is >10 points stronger.
        val queriesAndContexts = listOf(
            "وماذا عن النوم والتركيز؟" to "sleep_wellness",
            "وماذا عن النوم والتركيز؟" to "productivity",
            "وماذا عن النوم والتركيز؟" to null,
            "النوم والتركيز" to "productivity", // No continuity signal -> tie broken by topicOrderIndex (sleep_wellness=2 < productivity=3)
            "حدثني اكثر عن صلالة" to "travel", // alias "travel" -> "salalah_tourism"
            "اشرح اكثر عن النوم" to "health_wellness", // alias "health_wellness" -> "sleep_wellness"
            "ما هي سرعة الضوء؟" to "salalah_tourism" // New topic clearly wins over previous topic
        )

        for ((q, ctx) in queriesAndContexts) {
            assertFullLegacyParity(q, ctx)
        }
    }

    @Test
    fun capitalTopicSpecialRuleParity() {
        // Test all 37 entries in COUNTRY_CAPITALS_MAP in both direct and contextual queries
        for ((countryKey, _) in SeedKnowledgeCatalog.COUNTRY_CAPITALS_MAP) {
            val isArabicKey = countryKey.any { it in '\u0600'..'\u06FF' }
            val query = if (isArabicKey) {
                "ما هي عاصمة $countryKey؟"
            } else {
                "What is the capital of $countryKey?"
            }
            val result = repository.search(query)
            val legacy = LocalKnowledgeBase.findMatchingTopic(query)
            assertEquals("Expected capitals_arab for query='$query'", "capitals_arab", legacy?.id)
            assertEquals("Expected capitals_arab in new search for query='$query'", "capitals_arab", result.matchedTopicId)
            assertTrue("Expected score >= 165 for query='$query'", result.finalScore >= 165)

            // Also verify SeedKnowledgeCatalog.resolveSpecificCapitalAnswer parity
            assertEquals(
                LocalKnowledgeBase.resolveSpecificCapitalAnswer(query, isArabicKey),
                SeedKnowledgeCatalog.resolveSpecificCapitalAnswer(query, isArabicKey)
            )
        }
    }

    @Test
    fun deterministicTopicTieBreakParity() {
        // 1. Exact tie between sleep_wellness ("نوم" + "النوم" -> 102 + 12 = 114, index 2)
        // and productivity ("كسل", index 3): sleep_wellness wins by higher score and earlier index
        val tieQuery = "نوم كسل"
        val legacyWinner = LocalKnowledgeBase.findMatchingTopic(tieQuery, null)
        val newResult = repository.search(tieQuery, null)

        assertEquals("sleep_wellness", legacyWinner?.id)
        assertEquals(legacyWinner?.id, newResult.matchedTopicId)

        // 2. Exact 102 == 102 tie with continuity signal ("وماذا عن نوم كسل", currentTopicId = "productivity"):
        // sleep_wellness raw = 114 (index 2), productivity raw = 102 + 12 (continuity) = 114 (index 3).
        // Because finalScore > bestScore is strict (>), sleep_wellness (index 2) wins the 114 == 114 tie!
        val tieWithContinuity = "وماذا عن نوم كسل"
        val legacyContinuityWinner = LocalKnowledgeBase.findMatchingTopic(tieWithContinuity, "productivity")
        val newContinuityResult = repository.search(tieWithContinuity, "productivity")
        assertEquals("sleep_wellness", legacyContinuityWinner?.id)
        assertEquals(legacyContinuityWinner?.id, newContinuityResult.matchedTopicId)
        assertEquals(114, newContinuityResult.finalScore)

        // 3. Continuity boost flipping winner when later topic is competitive:
        // "كسل" (productivity, index 3, raw = 102) vs "حكمة" (short_story, index 8, raw = 103).
        // With currentTopicId = "productivity" and continuity signal ("وماذا عن حكمة كسل"):
        // productivity gets 102 + 12 = 114 > short_story (103), so productivity wins!
        val continuityFlipQuery = "وماذا عن حكمة كسل"
        val legacyFlipWinner = LocalKnowledgeBase.findMatchingTopic(continuityFlipQuery, "productivity")
        val newFlipResult = repository.search(continuityFlipQuery, "productivity")
        assertEquals("productivity", legacyFlipWinner?.id)
        assertEquals(legacyFlipWinner?.id, newFlipResult.matchedTopicId)
        assertEquals(114, newFlipResult.finalScore)
    }

    // =========================================================================
    // 18–22. CACHE GENERATION INVALIDATION, ATOMIC SNAPSHOT & REPOSITORY TESTS
    // =========================================================================

    @Test
    fun rawScoreCacheGenerationInvalidation() {
        val gen1 = repository.currentGeneration
        val res1 = repository.search("سرعة الضوء")
        assertEquals("physics_universe", res1.matchedTopicId)
        assertEquals(gen1, res1.knowledgeGeneration)

        // Second search on same generation hits RawScoreCache
        val hitsBefore = repository.cache.rawScoreCache.stats().hitCount
        val res2 = repository.search("سرعة الضوء")
        val hitsAfter = repository.cache.rawScoreCache.stats().hitCount
        assertEquals(res1.finalScore, res2.finalScore)
        assertEquals(hitsBefore + 1, hitsAfter)

        // Publish a new snapshot generation -> RawScoreCache must be invalidated
        val bundles = SeedKnowledgeCatalog.buildCoreSeedTopicStorageBundles()
        val newSnapshot = repository.publishNewSnapshotFromBundles(bundles)
        assertTrue(newSnapshot.knowledgeGeneration > gen1)
        assertEquals(0, repository.cache.rawScoreCache.stats().entryCount)

        val resAfterGenUpdate = repository.search("سرعة الضوء")
        assertEquals(newSnapshot.knowledgeGeneration, resAfterGenUpdate.knowledgeGeneration)
        assertEquals("physics_universe", resAfterGenUpdate.matchedTopicId)
    }

    @Test
    fun atomicSnapshotReplacement() {
        val threadCount = 6
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)
        val seedBundles = SeedKnowledgeCatalog.buildCoreSeedTopicStorageBundles()

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    for (i in 0 until 80) {
                        if (t == 0 && i % 10 == 0) {
                            repository.publishNewSnapshotFromBundles(seedBundles)
                        } else {
                            val snap = repository.currentSnapshot
                            // Every observed snapshot must be completely built (11 topics, >0 keywords)
                            if (snap.topicCount != 11 || snap.keywordCount == 0) {
                                errorCount.incrementAndGet()
                            }
                            val r = repository.search("ما هي سرعة الضوء؟")
                            if (r.matchedTopicId != "physics_universe") {
                                errorCount.incrementAndGet()
                            }
                        }
                    }
                } catch (_: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals("Observed half-built snapshot or concurrent search failure", 0, errorCount.get())
    }

    @Test
    fun repositorySeedInitializationIdempotency() = runBlocking(Dispatchers.IO) {
        // 1st initialization: writes core_seed_pack (11 topics) to Room
        assertTrue(repository.initializeSeedAndWarmUpIndex())
        val dao = db.knowledgeDao()

        val packsAfterFirst = dao.getAllPackMetadataOrdered()
        val headersAfterFirst = dao.getTopicHeadersByPackOrdered(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)
        val keywordsCountFirst = headersAfterFirst.sumOf {
            dao.getKeywordsByTopicAndPackOrdered(it.topicId, it.packId).size
        }
        val tokensCountFirst = headersAfterFirst.sumOf {
            dao.getTokensByTopicAndPackOrdered(it.topicId, it.packId).size
        }

        assertEquals(1, packsAfterFirst.size)
        assertEquals(11, headersAfterFirst.size)
        assertEquals(SeedKnowledgeCatalog.coreTopics.sumOf { it.keywords.size }, keywordsCountFirst)
        assertTrue(tokensCountFirst >= keywordsCountFirst)

        // 2nd and 3rd initializations: must be 100% idempotent (zero row duplication)
        assertTrue(repository.initializeSeedAndWarmUpIndex())
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val packsAfterRepeat = dao.getAllPackMetadataOrdered()
        val headersAfterRepeat = dao.getTopicHeadersByPackOrdered(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)
        val keywordsCountRepeat = headersAfterRepeat.sumOf {
            dao.getKeywordsByTopicAndPackOrdered(it.topicId, it.packId).size
        }
        val tokensCountRepeat = headersAfterRepeat.sumOf {
            dao.getTokensByTopicAndPackOrdered(it.topicId, it.packId).size
        }

        assertEquals(1, packsAfterRepeat.size)
        assertEquals(11, headersAfterRepeat.size)
        assertEquals(keywordsCountFirst, keywordsCountRepeat)
        assertEquals(tokensCountFirst, tokensCountRepeat)

        // Verify search after Room warm-up still achieves 100% parity with LocalKnowledgeBase
        for (query in buildGoldenQueryCorpus()) {
            assertFullLegacyParity(query, null)
        }
    }

    @Test
    fun repositoryWarmUpDoesNotBlockMainThread() = runBlocking {
        // Calling synchronous search & getTopicById on the test's main thread BEFORE and AFTER
        // warm-up must succeed with zero Room main-thread exceptions (since allowMainThreadQueries is NOT enabled)
        val immediateTopic = repository.findMatchingTopic("من أنت؟")
        assertNotNull(immediateTopic)
        assertEquals("identity", immediateTopic!!.id)
        assertEquals("salalah_tourism", repository.getTopicById("salalah_tourism")?.id)

        // Warm up index asynchronously (switches internally to Dispatchers.IO)
        val initialized = repository.initializeSeedAndWarmUpIndex()
        assertTrue(initialized)

        // Synchronous search on Main Thread after warm-up reads InMemorySearchSnapshot with 0 Room I/O
        val postWarmUpTopic = repository.findMatchingTopic("تنظيم الوقت")
        assertNotNull(postWarmUpTopic)
        assertEquals("productivity", postWarmUpTopic!!.id)
    }

    @Test
    fun repositoryFallbackPreservesPreviousSnapshotOnFailure() = runBlocking(Dispatchers.IO) {
        assertTrue(repository.initializeSeedAndWarmUpIndex())
        val validSnapshotBefore = repository.currentSnapshot
        val validGenBefore = repository.currentGeneration

        // Simulate a failed reload (e.g., empty bundles or exception during load)
        val reloadResultEmpty = repository.reloadActiveSnapshotFromDatabase { emptyList() }
        assertFalse("Expected reload with empty bundles to return false", reloadResultEmpty)

        val reloadResultException = repository.reloadActiveSnapshotFromDatabase {
            throw IllegalStateException("Simulated database read failure")
        }
        assertFalse("Expected reload throwing exception to return false", reloadResultException)

        // Verify the previous valid snapshot is preserved intact
        assertEquals(validGenBefore, repository.currentGeneration)
        assertEquals(validSnapshotBefore, repository.currentSnapshot)
        assertEquals("physics_universe", repository.findMatchingTopic("سرعة الضوء")?.id)
    }

    @Test
    fun searchLatencyAndSnapshotAccountedFootprintBenchmark() {
        val snapshot = repository.currentSnapshot
        val queries = buildGoldenQueryCorpus().filter { it.isNotBlank() }

        // Warmup
        for (i in 0 until 20) {
            for (q in queries) {
                searchService.search(q, null, snapshot)
            }
        }

        val latenciesNanos = ArrayList<Long>(queries.size * 5)
        for (round in 0 until 5) {
            for (q in queries) {
                val t0 = System.nanoTime()
                searchService.search(q, null, snapshot)
                val elapsed = System.nanoTime() - t0
                latenciesNanos.add(elapsed)
            }
        }

        latenciesNanos.sort()
        val p50Micros = latenciesNanos[latenciesNanos.size / 2] / 1_000.0
        val p95Micros = latenciesNanos[(latenciesNanos.size * 95) / 100] / 1_000.0
        val p99Micros = latenciesNanos[(latenciesNanos.size * 99) / 100] / 1_000.0

        val cacheStats = repository.cache.snapshotStats()
        println(
            "[Phase2B2 Benchmark - Robolectric JVM] " +
                "topics=${snapshot.topicCount}, keywords=${snapshot.keywordCount}, " +
                "strictTokens=${snapshot.reverseTokenIndex.indexedStrictTokenCount}, " +
                "relaxedStems=${snapshot.reverseTokenIndex.indexedRelaxedStemCount}, " +
                "P50=${String.format(java.util.Locale.US, "%.2f", p50Micros)}us, " +
                "P95=${String.format(java.util.Locale.US, "%.2f", p95Micros)}us, " +
                "P99=${String.format(java.util.Locale.US, "%.2f", p99Micros)}us, " +
                "rawScoreCacheBytes=${cacheStats.rawScoreStats.currentAccountedBytes}/${cacheStats.rawScoreStats.maxAccountedBudgetBytes}"
        )

        assertTrue("Expected P95 warm search latency < 5,000 microseconds", p95Micros < 5_000.0)
        assertTrue(
            cacheStats.currentTotalAccountedBytes <= cacheStats.totalAccountedBudgetBytes
        )
    }

    // =========================================================================
    // Helper Verification & Golden Corpus
    // =========================================================================

    private fun assertFullLegacyParity(query: String, currentTopicId: String?) {
        val legacyTopic = LocalKnowledgeBase.findMatchingTopic(query, currentTopicId)
        val newResult = repository.search(query, currentTopicId)

        assertEquals(
            "Mismatch in winning topicId for query='$query', currentTopicId='$currentTopicId'",
            legacyTopic?.id,
            newResult.matchedTopicId
        )

        if (legacyTopic != null) {
            val matched = newResult.matchedTopic
            assertNotNull("Expected non-null matchedTopic for query='$query'", matched)
            assertEquals(legacyTopic.id, matched!!.id)
            assertEquals(legacyTopic.keywords, matched.keywords)
            assertEquals(legacyTopic.arabicSummary, matched.arabicSummary)
            assertEquals(legacyTopic.englishSummary, matched.englishSummary)
            assertEquals(legacyTopic.suggestedFollowUps, matched.suggestedFollowUps)
        } else {
            assertNull(newResult.matchedTopic)
        }
    }

    private fun buildGoldenQueryCorpus(): List<String> {
        val corpus = mutableListOf(
            "",
            "   ",
            "من انت",
            "من أنت؟",
            "عرفني بنفسك",
            "ما هو weyky",
            "who are you",
            "what can you do",
            "قدراتك ومميزاتك",
            "النوم الصحي والأرق",
            "كيف انام بسرعة",
            "insomnia and sleep tips",
            "تنظيم الوقت والتركيز",
            "إدارة الوقت والدراسة",
            "الوقت تنظيم",
            "pomodoro time management",
            "الذكاء الاصطناعي وتعلم الآلة",
            "ai",
            "machine learning and artificial intelligence",
            "عواصم الدول العربية",
            "ما هي عاصمة اليابان؟",
            "ما هي عاصمة السعودية؟",
            "what is the capital of france",
            "world capitals",
            "خريف صلالة ووادي دربات",
            "سياحه عمان وظفار",
            "salalah khareef in dhofar",
            "سرعة الضوء في المجموعة الشمسية",
            "الجاذبية والكواكب في الكون",
            "speed of light and gravity",
            "احك لي قصة قصيرة وموعظة",
            "tell me a short story",
            "فوائد شرب الماء وترطيب الجسم",
            "drinking water intake",
            "الحفاظ على البطارية وعمر البطارية",
            "اطاله عمر البطاريه وشحن الهاتف",
            "battery health and save battery",
            // Clitic & prefix edge cases
            "والنوم",
            "بالنوم",
            "للنوم",
            "فالنوم",
            "ونوم",
            "بنوم",
            "لنوم",
            "فنوم",
            "وصلالة",
            "بصلالة",
            "وللجاذبية",
            // Protected root words & homographs
            "باريس",
            "بغداد",
            "لبنان",
            "لندن",
            "وليد",
            "بومودورو",
            "مهمه",
            "مهمة",
            "سال",
            "سأل",
            "سنه",
            "السنة الضوئية",
            // Continuity & multi-topic queries
            "وماذا عن صلالة؟",
            "حدثني اكثر عن الفضاء",
            "اشرح لي بالتفصيل عن البطارية",
            "نوم كسل",
            "التركيز والنوم"
        )
        // Include all keywords of all 11 topics
        for (entry in SeedKnowledgeCatalog.coreTopics) {
            corpus.addAll(entry.keywords)
        }
        return corpus
    }
}
