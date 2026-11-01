package com.example.core.intelligence.knowledge

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.knowledge.cache.CachedTopicDetailBundle
import com.example.core.intelligence.knowledge.cache.KnowledgeByteSizeEstimator
import com.example.core.intelligence.knowledge.cache.KnowledgeCache
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KeywordWithTokensBundle
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.db.entity.KnowledgeFollowUpBranchEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicDetailEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity
import com.example.core.intelligence.knowledge.validation.KnowledgeValidator
import com.example.core.intelligence.knowledge.validation.ValidationIssueCategory
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
class Phase2B1DataFoundationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var db: WeykyKnowledgeDatabase
    private val converters = KnowledgeTypeConverters()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("test_weyky_knowledge_2b1.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }
        // Build real file-backed Room database WITHOUT allowMainThreadQueries()
        db = Room.databaseBuilder(
            context,
            WeykyKnowledgeDatabase::class.java,
            dbFile.name
        ).build()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        WeykyKnowledgeDatabase.clearInstanceForTesting()
        if (::dbFile.isInitialized && dbFile.exists()) {
            dbFile.delete()
        }
    }

    // =========================================================================
    // 1. ROOM DATABASE, 6 TABLES, PKs, FKs, UNIQUE CONSTRAINTS, CASCADE, REOPEN
    // =========================================================================

    @Test
    fun `room creates version 1 database with all six required tables`() = runBlocking(Dispatchers.IO) {
        // Trigger database open on background thread
        val dao = db.knowledgeDao()
        assertEquals(emptyList<KnowledgePackMetadataEntity>(), dao.getAllPackMetadataOrdered())

        val sqliteDb = db.openHelper.readableDatabase
        assertEquals(1, sqliteDb.version)

        val tables = mutableSetOf<String>()
        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }

        val expectedSixTables = setOf(
            "knowledge_pack_metadata",
            "knowledge_topics",
            "knowledge_keywords",
            "knowledge_keyword_tokens",
            "knowledge_topic_details",
            "knowledge_followup_branches"
        )
        for (tableName in expectedSixTables) {
            assertTrue("Expected table '$tableName' to exist in SQLite", tableName in tables)
        }
    }

    @Test
    fun `room enforces foreign keys and rejects orphan records across tables`() = runBlocking(Dispatchers.IO) {
        val dao = db.knowledgeDao()

        // 1. Attempting to insert a Topic Header without a parent Pack must fail FK constraint
        val orphanHeader = sampleHeader(topicId = "physics_universe", packId = "missing_pack")
        var headerFkFailed = false
        try {
            dao.upsertTopicHeader(orphanHeader)
        } catch (_: SQLiteConstraintException) {
            headerFkFailed = true
        }
        assertTrue("Expected FK failure when inserting topic header without parent pack", headerFkFailed)

        // 2. Attempting to insert a Keyword without a parent Topic Header must fail FK constraint
        val orphanKeyword = sampleKeyword(topicId = "missing_topic", packId = "missing_pack", order = 0)
        var keywordFkFailed = false
        try {
            dao.insertKeyword(orphanKeyword)
        } catch (_: SQLiteConstraintException) {
            keywordFkFailed = true
        }
        assertTrue("Expected FK failure when inserting keyword without parent topic header", keywordFkFailed)

        // 3. Attempting to insert a Keyword Token without a parent Keyword must fail FK constraint
        val orphanToken = sampleToken(keywordId = 99999L, position = 0)
        var tokenFkFailed = false
        try {
            dao.insertKeywordToken(orphanToken)
        } catch (_: SQLiteConstraintException) {
            tokenFkFailed = true
        }
        assertTrue("Expected FK failure when inserting token without parent keyword", tokenFkFailed)
    }

    @Test
    fun `room enforces unique constraints on keywordOrder, tokenPosition, and branch priorityOrder`() =
        runBlocking(Dispatchers.IO) {
            val dao = db.knowledgeDao()
            val pack = samplePack(packId = "core_seed_pack")
            val header = sampleHeader(topicId = "physics_universe", packId = "core_seed_pack")
            dao.upsertPackMetadata(pack)
            dao.upsertTopicHeader(header)

            // Insert keyword at order 0
            val kwId = dao.insertKeyword(
                sampleKeyword(topicId = "physics_universe", packId = "core_seed_pack", order = 0)
            )
            assertTrue(kwId > 0L)

            // Duplicate keywordOrder 0 in the same (topicId, packId) must fail unique constraint
            var duplicateKeywordOrderFailed = false
            try {
                dao.insertKeyword(
                    sampleKeyword(topicId = "physics_universe", packId = "core_seed_pack", order = 0)
                )
            } catch (_: SQLiteConstraintException) {
                duplicateKeywordOrderFailed = true
            }
            assertTrue("Duplicate keywordOrder in same topic+pack must fail", duplicateKeywordOrderFailed)

            // Insert token at position 0
            dao.insertKeywordToken(sampleToken(keywordId = kwId, position = 0))

            // Duplicate tokenPosition 0 for same keywordId must fail unique constraint
            var duplicateTokenPosFailed = false
            try {
                dao.insertKeywordToken(sampleToken(keywordId = kwId, position = 0))
            } catch (_: SQLiteConstraintException) {
                duplicateTokenPosFailed = true
            }
            assertTrue("Duplicate tokenPosition in same keyword must fail", duplicateTokenPosFailed)
        }

    @Test
    fun `room supports insert read update delete and converts header to legacy KnowledgeTopic`() =
        runBlocking(Dispatchers.IO) {
            val dao = db.knowledgeDao()
            val pack = samplePack(packId = "core_seed_pack")
            val header = sampleHeader(
                topicId = "sleep_wellness",
                packId = "core_seed_pack",
                orderIndex = 2,
                arabicSummary = "ملخص النوم الصحي",
                englishSummary = "Healthy sleep summary"
            )
            val kw0 = sampleKeyword(
                topicId = "sleep_wellness",
                packId = "core_seed_pack",
                order = 0,
                original = "نصائح للنوم",
                strictPhrase = "نصائح للنوم",
                relaxedPhrase = "نصائح للنوم",
                tokenCount = 2,
                contentTokenCount = 2
            )
            val kw1 = sampleKeyword(
                topicId = "sleep_wellness",
                packId = "core_seed_pack",
                order = 1,
                original = "النوم العميق",
                strictPhrase = "النوم العميق",
                relaxedPhrase = "النوم العميق",
                tokenCount = 2,
                contentTokenCount = 2
            )
            val tok0 = sampleToken(keywordId = 0L, position = 0, strictToken = "نصائح", strictStem = "نصائح")
            val tok1 = sampleToken(keywordId = 0L, position = 1, strictToken = "للنوم", strictStem = "نوم")
            val detail = sampleDetail(topicId = "sleep_wellness", packId = "core_seed_pack")
            val branch2 = sampleBranch(
                branchId = "sleep_default",
                topicId = "sleep_wellness",
                packId = "core_seed_pack",
                priorityOrder = 2,
                isDefault = true
            )
            val branch1 = sampleBranch(
                branchId = "sleep_caffeine",
                topicId = "sleep_wellness",
                packId = "core_seed_pack",
                priorityOrder = 1,
                isDefault = false
            )

            // Insert via transactional bundle (intentionally pass branches out of order to verify ORDER BY)
            dao.upsertPackWithTopicsTransactional(
                pack = pack,
                topics = listOf(
                    TopicStorageBundle(
                        header = header,
                        keywordsWithTokens = listOf(
                            KeywordWithTokensBundle(kw1, listOf(tok1)),
                            KeywordWithTokensBundle(kw0, listOf(tok0, tok1))
                        ),
                        detail = detail,
                        followUpBranches = listOf(branch2, branch1)
                    )
                )
            )

            // Read back by composite key & active winner
            val loadedHeader = dao.getTopicHeaderByIdAndPack("sleep_wellness", "core_seed_pack")
            assertNotNull(loadedHeader)
            assertEquals("ملخص النوم الصحي", loadedHeader!!.arabicSummary)
            assertTrue(loadedHeader.isActiveWinner)

            val activeHeader = dao.getActiveTopicHeader("sleep_wellness")
            assertNotNull(activeHeader)
            assertEquals("core_seed_pack", activeHeader!!.packId)

            // Verify explicit ORDER BY on keywords (order 0 then order 1 despite insertion order)
            val loadedKeywords = dao.getKeywordsByTopicAndPackOrdered("sleep_wellness", "core_seed_pack")
            assertEquals(2, loadedKeywords.size)
            assertEquals("نصائح للنوم", loadedKeywords[0].originalKeyword)
            assertEquals("النوم العميق", loadedKeywords[1].originalKeyword)

            // Verify explicit ORDER BY on tokens
            val loadedTokensForKw0 = dao.getTokensByKeywordIdOrdered(loadedKeywords[0].keywordId)
            assertEquals(2, loadedTokensForKw0.size)
            assertEquals("نصائح", loadedTokensForKw0[0].strictToken)
            assertEquals("للنوم", loadedTokensForKw0[1].strictToken)

            // Verify explicit ORDER BY on follow-up branches (priorityOrder 1 then 2)
            val loadedBranches = dao.getFollowUpBranchesOrdered("sleep_wellness", "core_seed_pack")
            assertEquals(2, loadedBranches.size)
            assertEquals("sleep_caffeine", loadedBranches[0].branchId)
            assertEquals("sleep_default", loadedBranches[1].branchId)

            // Verify detail read
            val loadedDetail = dao.getTopicDetail("sleep_wellness", "core_seed_pack")
            assertNotNull(loadedDetail)
            assertEquals(detail.detailedArabic, loadedDetail!!.detailedArabic)

            // Verify conversion to legacy 5-field LocalKnowledgeBase.KnowledgeTopic
            val legacyTopic = loadedHeader.toLegacyKnowledgeTopic(
                keywords = loadedKeywords.map { it.originalKeyword },
                converters = converters
            )
            assertEquals("sleep_wellness", legacyTopic.id)
            assertEquals(listOf("نصائح للنوم", "النوم العميق"), legacyTopic.keywords)
            assertEquals("ملخص النوم الصحي", legacyTopic.arabicSummary)
            assertEquals(1, legacyTopic.suggestedFollowUps.size)
            assertEquals("تأثير القهوة", legacyTopic.suggestedFollowUps[0].title)

            // Update header and verify
            val updatedHeader = loadedHeader.copy(arabicSummary = "ملخص محدث للنوم")
            dao.updateTopicHeader(updatedHeader)
            assertEquals(
                "ملخص محدث للنوم",
                dao.getTopicHeaderByIdAndPack("sleep_wellness", "core_seed_pack")?.arabicSummary
            )
        }

    @Test
    fun `room supports multiple inactive versions for same topicId and cascades on pack delete`() =
        runBlocking(Dispatchers.IO) {
            val dao = db.knowledgeDao()

            val bundledPack = samplePack(
                packId = "bundled_science_v1",
                sourceType = "BUNDLED_ASSET",
                priority = 10
            )
            val localUpdatePack = samplePack(
                packId = "local_update_science_v2",
                sourceType = "LOCAL_UPDATE",
                priority = 20
            )
            val thirdPack = samplePack(
                packId = "seed_pack_v0",
                sourceType = "SEED_BUILTIN",
                priority = 0
            )

            // Insert 3 versions of the same topic ("physics_universe") across 3 packs.
            // Because two of them will have is_active_winner = false (0), this also proves there is
            // NO broken unique index on (topic_id, is_active_winner).
            dao.upsertPackWithTopicsTransactional(
                pack = thirdPack,
                topics = listOf(
                    TopicStorageBundle(
                        header = sampleHeader(
                            topicId = "physics_universe",
                            packId = "seed_pack_v0",
                            arabicSummary = "نسخة النواة 0",
                            sourceType = "SEED_BUILTIN",
                            priority = 0,
                            contentVersion = 1
                        ),
                        keywordsWithTokens = listOf(
                            KeywordWithTokensBundle(
                                sampleKeyword("physics_universe", "seed_pack_v0", 0),
                                listOf(sampleToken(0L, 0))
                            )
                        ),
                        detail = sampleDetail("physics_universe", "seed_pack_v0"),
                        followUpBranches = listOf(
                            sampleBranch("b_seed", "physics_universe", "seed_pack_v0", 1, true)
                        )
                    )
                )
            )

            dao.upsertPackWithTopicsTransactional(
                pack = bundledPack,
                topics = listOf(
                    TopicStorageBundle(
                        header = sampleHeader(
                            topicId = "physics_universe",
                            packId = "bundled_science_v1",
                            arabicSummary = "نسخة الحزمة المدمجة 10",
                            sourceType = "BUNDLED_ASSET",
                            priority = 10,
                            contentVersion = 2
                        ),
                        keywordsWithTokens = listOf(
                            KeywordWithTokensBundle(
                                sampleKeyword("physics_universe", "bundled_science_v1", 0),
                                listOf(sampleToken(0L, 0))
                            )
                        ),
                        detail = sampleDetail("physics_universe", "bundled_science_v1"),
                        followUpBranches = listOf(
                            sampleBranch("b_bundled", "physics_universe", "bundled_science_v1", 1, true)
                        )
                    )
                )
            )

            dao.upsertPackWithTopicsTransactional(
                pack = localUpdatePack,
                topics = listOf(
                    TopicStorageBundle(
                        header = sampleHeader(
                            topicId = "physics_universe",
                            packId = "local_update_science_v2",
                            arabicSummary = "نسخة التحديث المحلي 20",
                            sourceType = "LOCAL_UPDATE",
                            priority = 20,
                            contentVersion = 3
                        ),
                        keywordsWithTokens = listOf(
                            KeywordWithTokensBundle(
                                sampleKeyword("physics_universe", "local_update_science_v2", 0),
                                listOf(sampleToken(0L, 0))
                            )
                        ),
                        detail = sampleDetail("physics_universe", "local_update_science_v2"),
                        followUpBranches = listOf(
                            sampleBranch("b_local", "physics_universe", "local_update_science_v2", 1, true)
                        )
                    )
                )
            )

            // Verify all 3 versions coexist and exactly 1 is active winner (local_update_science_v2)
            val allVersions = dao.getAllTopicVersionsForTopicId("physics_universe")
            assertEquals(3, allVersions.size)
            assertEquals(1, allVersions.count { it.isActiveWinner })
            assertEquals("local_update_science_v2", dao.getActiveTopicHeader("physics_universe")?.packId)

            // Delete the local update pack: ON DELETE CASCADE must remove its topic/keywords/tokens/details/branches
            // and promote bundled_science_v1 as the new active winner!
            dao.deletePackAndRecalculateWinnersTransactional("local_update_science_v2")

            assertNull(dao.getTopicHeaderByIdAndPack("physics_universe", "local_update_science_v2"))
            assertTrue(dao.getKeywordsByTopicAndPackOrdered("physics_universe", "local_update_science_v2").isEmpty())
            assertTrue(dao.getTokensByTopicAndPackOrdered("physics_universe", "local_update_science_v2").isEmpty())
            assertNull(dao.getTopicDetail("physics_universe", "local_update_science_v2"))
            assertTrue(dao.getFollowUpBranchesOrdered("physics_universe", "local_update_science_v2").isEmpty())

            // Verify bundled_science_v1 is now the active winner
            val newWinner = dao.getActiveTopicHeader("physics_universe")
            assertNotNull(newWinner)
            assertEquals("bundled_science_v1", newWinner!!.packId)
            assertEquals("نسخة الحزمة المدمجة 10", newWinner.arabicSummary)
        }

    @Test
    fun `room persists data across database close and reopen`() = runBlocking(Dispatchers.IO) {
        val pack = samplePack(packId = "persistent_pack_v1")
        val header = sampleHeader(topicId = "salalah_tourism", packId = "persistent_pack_v1")
        db.knowledgeDao().upsertPackMetadata(pack)
        db.knowledgeDao().upsertTopicHeader(header)

        // Close database
        db.close()

        // Reopen the exact same database file on disk
        db = Room.databaseBuilder(
            context,
            WeykyKnowledgeDatabase::class.java,
            dbFile.name
        ).build()

        val reopenedPack = db.knowledgeDao().getPackMetadataById("persistent_pack_v1")
        val reopenedHeader = db.knowledgeDao().getTopicHeaderByIdAndPack("salalah_tourism", "persistent_pack_v1")
        assertNotNull(reopenedPack)
        assertNotNull(reopenedHeader)
        assertEquals("salalah_tourism", reopenedHeader!!.topicId)
    }

    // =========================================================================
    // 2. TYPE CONVERTERS TESTS
    // =========================================================================

    @Test
    fun `type converters round-trip ActionableOption list, empty list, null, and malformed JSON safely`() {
        val options = listOf(
            ActionableOption("فحص الطقس", "ما حالة الطقس؟"),
            ActionableOption("حساب سريع", "احسب 15 * 3")
        )
        val json = converters.fromActionableOptionList(options)
        assertTrue(KnowledgeTypeConverters.isValidActionableOptionListJson(json))

        val restored = converters.toActionableOptionList(json)
        assertEquals(options, restored)

        // Empty list & null handling
        assertEquals("[]", converters.fromActionableOptionList(emptyList()))
        assertEquals("[]", converters.fromActionableOptionList(null))
        assertEquals(emptyList<ActionableOption>(), converters.toActionableOptionList("[]"))
        assertEquals(emptyList<ActionableOption>(), converters.toActionableOptionList(null))
        assertEquals(emptyList<ActionableOption>(), converters.toActionableOptionList("   "))

        // Malformed JSON handling (never crashes, returns emptyList safely, validator flags as invalid)
        val corruptedJson = "[{\"title\": \"ناقص\", \"broken\": 123"
        assertFalse(KnowledgeTypeConverters.isValidActionableOptionListJson(corruptedJson))
        assertEquals(emptyList<ActionableOption>(), converters.toActionableOptionList(corruptedJson))

        // String list & optional map handling
        val tags = listOf("تتكون", "تتشكل", "form")
        val tagsJson = converters.fromStringList(tags)
        assertEquals(tags, converters.toStringList(tagsJson))
        assertEquals(emptyList<String>(), converters.toStringList("{not_an_array}"))
        assertNull(converters.fromStringMap(null))
        assertNull(converters.toStringMap(null))
    }

    // =========================================================================
    // 3. KNOWLEDGE VALIDATOR TESTS
    // =========================================================================

    @Test
    fun `validator accepts valid complete pack data and distinguishes all invalid categories`() {
        val validPack = samplePack(packId = "core_seed_pack", topicCount = 1)
        val validHeader = sampleHeader(topicId = "physics_universe", packId = "core_seed_pack")
        val validKeyword = sampleKeyword(
            topicId = "physics_universe",
            packId = "core_seed_pack",
            order = 0,
            original = "الفيزياء الكونية",
            strictPhrase = "الفيزياء الكونيه",
            relaxedPhrase = "الفيزياء الكونيه",
            tokenCount = 2,
            contentTokenCount = 2
        ).copy(keywordId = 10L)
        val validToken0 = sampleToken(keywordId = 10L, position = 0, strictToken = "الفيزياء", strictStem = "فيزياء")
        val validToken1 = sampleToken(keywordId = 10L, position = 1, strictToken = "الكونيه", strictStem = "كونيه")
        val validDetail = sampleDetail(
            topicId = "physics_universe",
            packId = "core_seed_pack",
            relatedJson = "[\"ai_concept\"]"
        )
        val validBranch = sampleBranch(
            branchId = "physics_stars",
            topicId = "physics_universe",
            packId = "core_seed_pack",
            priorityOrder = 1,
            isDefault = false
        )

        // 1. Valid data passes cleanly
        val validResult = KnowledgeValidator.validatePackData(
            pack = validPack,
            topics = listOf(validHeader),
            keywords = listOf(validKeyword),
            tokens = listOf(validToken0, validToken1),
            details = listOf(validDetail),
            followUpBranches = listOf(validBranch),
            referenceTopicIds = setOf("ai_concept", "sleep_wellness")
        )
        assertTrue("Expected valid pack to pass validation", validResult.isValid)

        // 2. Blank topicId and blank packId -> VALIDATION_ERROR
        val blankIdResult = KnowledgeValidator.validateTopicHeader(
            validHeader.copy(topicId = "   ", packId = "")
        )
        assertFalse(blankIdResult.isValid)
        assertTrue(blankIdResult.hasCategory(ValidationIssueCategory.VALIDATION_ERROR))

        // 3. Blank originalKeyword or blank normalized phrase -> VALIDATION_ERROR
        val blankKwResult = KnowledgeValidator.validateKeyword(
            validKeyword.copy(originalKeyword = "", strictNormalizedPhrase = "  ")
        )
        assertFalse(blankKwResult.isValid)
        assertTrue(blankKwResult.hasCategory(ValidationIssueCategory.VALIDATION_ERROR))

        // 4. Duplicate keywordOrder & duplicate tokenPosition -> DUPLICATE_DATA
        val duplicateOrderAndPosResult = KnowledgeValidator.validatePackData(
            pack = validPack,
            topics = listOf(validHeader),
            keywords = listOf(
                validKeyword.copy(keywordId = 10L, keywordOrder = 0),
                validKeyword.copy(keywordId = 11L, keywordOrder = 0)
            ),
            tokens = listOf(
                validToken0.copy(tokenId = 1L, keywordId = 10L, tokenPosition = 0),
                validToken1.copy(tokenId = 2L, keywordId = 10L, tokenPosition = 0)
            ),
            details = emptyList(),
            followUpBranches = emptyList()
        )
        assertFalse(duplicateOrderAndPosResult.isValid)
        assertTrue(duplicateOrderAndPosResult.hasCategory(ValidationIssueCategory.DUPLICATE_DATA))

        // 5. Invalid JSON in suggestedFollowUpsJson / triggerKeywordsJson -> INVALID_SERIALIZATION
        val badJsonResult = KnowledgeValidator.validateFollowUpBranch(
            validBranch.copy(
                triggerKeywordsJson = "{corrupt_json",
                actionableOptionsJson = "[{\"title\":\"only_title\"}]"
            )
        )
        assertFalse(badJsonResult.isValid)
        assertTrue(badJsonResult.hasCategory(ValidationIssueCategory.INVALID_SERIALIZATION))

        // 6. Broken relation (keyword referencing unknown topic, or unknown relatedTopicId) -> MISSING_REFERENCE
        val brokenRefResult = KnowledgeValidator.validatePackData(
            pack = validPack,
            topics = listOf(validHeader),
            keywords = listOf(validKeyword.copy(topicId = "non_existent_topic")),
            tokens = listOf(validToken0),
            details = listOf(validDetail.copy(relatedTopicIdsJson = "[\"unknown_related_topic\"]")),
            followUpBranches = emptyList(),
            referenceTopicIds = setOf("ai_concept")
        )
        assertFalse(brokenRefResult.isValid)
        assertTrue(brokenRefResult.hasCategory(ValidationIssueCategory.MISSING_REFERENCE))

        // 7. Unsupported schemaVersion -> UNSUPPORTED_SCHEMA_VERSION
        val badSchemaResult = KnowledgeValidator.validatePackMetadata(
            validPack.copy(schemaVersion = 99)
        )
        assertFalse(badSchemaResult.isValid)
        assertTrue(badSchemaResult.hasCategory(ValidationIssueCategory.UNSUPPORTED_SCHEMA_VERSION))
    }

    // =========================================================================
    // 4. BYTE-BUDGETED KNOWLEDGE CACHE TESTS
    // =========================================================================

    @Test
    fun `cache enforces LRU eviction order and respects Header, Detail, and RawScore byte budgets`() {
        // Default budgets check: 256KB + 512KB + 64KB = 832KB
        val defaultCache = KnowledgeCache()
        assertEquals(256 * 1024, defaultCache.headerCache.maxAccountedBudgetBytes)
        assertEquals(512 * 1024, defaultCache.detailCache.maxAccountedBudgetBytes)
        assertEquals(64 * 1024, defaultCache.rawScoreCache.maxAccountedBudgetBytes)
        assertEquals(832 * 1024, defaultCache.snapshotStats().totalAccountedBudgetBytes)

        // Test deterministic LRU eviction with a small custom budget where each entry fits <= budget/2
        val sampleTopicA = sampleHeader("t_a", "pack").toLegacyKnowledgeTopic(listOf("kw_a"))
        val singleHeaderSize = KnowledgeByteSizeEstimator.estimateTopicHeaderEntryBytes("t_a", sampleTopicA)

        // Allow ~2 entries max in headerCache, ~2 entries max in detailCache, ~2 entries max in rawScoreCache
        val sampleBundleA = CachedTopicDetailBundle(sampleDetail("t_a", "pack"))
        val singleDetailSize = KnowledgeByteSizeEstimator.estimateTopicDetailEntryBytes("t_a", sampleBundleA)

        val sampleScoresA = mapOf("physics_universe" to 180, "ai_concept" to 95)
        val singleScoreSize = KnowledgeByteSizeEstimator.estimateRawScoreEntryBytes("q_a", sampleScoresA)

        val boundedCache = KnowledgeCache(
            headerBudgetBytes = (singleHeaderSize * 2) + 64,
            detailBudgetBytes = (singleDetailSize * 2) + 64,
            rawScoreBudgetBytes = (singleScoreSize * 2) + 32
        )

        // Insert t_a, then t_b into HeaderCache
        assertTrue(boundedCache.putHeader("t_a", sampleTopicA))
        assertTrue(boundedCache.putHeader("t_b", sampleTopicA.copy(id = "t_b")))
        assertEquals(listOf("t_a", "t_b"), boundedCache.headerCache.keysInLruOrder())

        // Access t_a so t_b becomes the Least Recently Used (eldest)
        assertNotNull(boundedCache.getHeader("t_a"))
        assertEquals(listOf("t_b", "t_a"), boundedCache.headerCache.keysInLruOrder())

        // Insert t_c -> must evict t_b (LRU) while keeping t_a
        assertTrue(boundedCache.putHeader("t_c", sampleTopicA.copy(id = "t_c")))
        assertNull("Expected LRU entry t_b to be evicted", boundedCache.getHeader("t_b"))
        assertNotNull("Expected MRU entry t_a to remain in cache", boundedCache.getHeader("t_a"))
        assertNotNull("Expected new entry t_c to be in cache", boundedCache.getHeader("t_c"))
        assertTrue(
            boundedCache.headerCache.stats().currentAccountedBytes <=
                boundedCache.headerCache.maxAccountedBudgetBytes
        )

        // Verify DetailCache budget enforcement
        assertTrue(boundedCache.putDetail("t_a", sampleBundleA))
        assertTrue(boundedCache.putDetail("t_b", sampleBundleA))
        assertTrue(boundedCache.putDetail("t_c", sampleBundleA))
        assertNull(boundedCache.getDetail("t_a"))
        assertTrue(
            boundedCache.detailCache.stats().currentAccountedBytes <=
                boundedCache.detailCache.maxAccountedBudgetBytes
        )

        // Verify RawScoreCache budget enforcement
        assertTrue(boundedCache.putRawScores("q_a", sampleScoresA))
        assertTrue(boundedCache.putRawScores("q_b", sampleScoresA))
        assertTrue(boundedCache.putRawScores("q_c", sampleScoresA))
        assertNull(boundedCache.getRawScores("q_a"))
        assertTrue(
            boundedCache.rawScoreCache.stats().currentAccountedBytes <=
                boundedCache.rawScoreCache.maxAccountedBudgetBytes
        )
    }

    @Test
    fun `cache rejects oversized single item exceeding half budget without evicting existing items`() {
        val cache = KnowledgeCache() // DetailCache budget = 512 KB, maxSingleItem = 256 KB
        val normalBundle1 = CachedTopicDetailBundle(sampleDetail("topic_1", "pack"))
        val normalBundle2 = CachedTopicDetailBundle(sampleDetail("topic_2", "pack"))

        assertTrue(cache.putDetail("topic_1", normalBundle1))
        assertTrue(cache.putDetail("topic_2", normalBundle2))
        val bytesBeforeOversized = cache.detailCache.stats().currentAccountedBytes
        assertEquals(2, cache.detailCache.stats().entryCount)

        // Create an oversized detail bundle (> 256 KB; 140,000 UTF-16 chars = ~280 KB)
        val hugeText = "أ".repeat(140_000)
        val oversizedBundle = CachedTopicDetailBundle(
            sampleDetail("huge_topic", "pack").copy(detailedArabic = hugeText)
        )

        // 1. Direct putDetail must return false and increment oversizedRejectedCount
        val stored = cache.putDetail("huge_topic", oversizedBundle)
        assertFalse("Oversized item (> 50% of budget) must be rejected from caching", stored)
        assertNull(cache.getDetail("huge_topic"))
        assertEquals(1L, cache.detailCache.stats().oversizedRejectedCount)

        // 2. Existing cached items MUST NOT be evicted!
        assertEquals(2, cache.detailCache.stats().entryCount)
        assertEquals(bytesBeforeOversized, cache.detailCache.stats().currentAccountedBytes)
        assertNotNull(cache.getDetail("topic_1"))
        assertNotNull(cache.getDetail("topic_2"))

        // 3. getOrLoadDetail must still return the oversized item to the caller for direct use
        val directResult = cache.getOrLoadDetail("huge_topic_2") { oversizedBundle }
        assertEquals(hugeText, directResult.detail.detailedArabic)
        assertNull("Oversized item returned via getOrLoad must not remain in cache", cache.getDetail("huge_topic_2"))
        assertEquals(2L, cache.detailCache.stats().oversizedRejectedCount)
    }

    @Test
    fun `cache supports invalidate, invalidateAll, and concurrent multi-threaded read write`() {
        val cache = KnowledgeCache()
        val topic = sampleHeader("physics_universe", "pack").toLegacyKnowledgeTopic(listOf("فيزياء"))
        val detail = CachedTopicDetailBundle(sampleDetail("physics_universe", "pack"))
        val scores = mapOf("physics_universe" to 195)

        cache.putHeader("physics_universe", topic)
        cache.putDetail("physics_universe", detail)
        cache.putRawScores("physics_universe", scores)

        // Single key invalidate
        cache.invalidate("physics_universe")
        assertNull(cache.getHeader("physics_universe"))
        assertNull(cache.getDetail("physics_universe"))
        assertNull(cache.getRawScores("physics_universe"))
        assertEquals(0, cache.snapshotStats().currentTotalAccountedBytes)

        // Repopulate and test invalidateAll
        cache.putHeader("t1", topic)
        cache.putDetail("t2", detail)
        cache.putRawScores("q1", scores)
        assertTrue(cache.snapshotStats().currentTotalAccountedBytes > 0)

        cache.invalidateAll()
        assertEquals(0, cache.snapshotStats().currentTotalAccountedBytes)
        assertEquals(0, cache.headerCache.stats().entryCount)
        assertEquals(0, cache.detailCache.stats().entryCount)
        assertEquals(0, cache.rawScoreCache.stats().entryCount)

        // Concurrent read/write stress test across 8 threads
        val threadCount = 8
        val operationsPerThread = 150
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val failureCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    for (i in 0 until operationsPerThread) {
                        val key = "topic_${(t + i) % 25}"
                        cache.putHeader(key, topic.copy(id = key))
                        cache.getHeader(key)
                        cache.putDetail(key, detail)
                        cache.getDetail(key)
                        cache.putRawScores(key, mapOf(key to i))
                        cache.getRawScores(key)
                        if (i % 20 == 0) {
                            cache.invalidate(key)
                        }
                    }
                } catch (_: Throwable) {
                    failureCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue("Concurrent cache tasks timed out", doneLatch.await(15, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertEquals("Expected zero exceptions during concurrent read/write", 0, failureCount.get())
        val finalStats = cache.snapshotStats()
        assertTrue(finalStats.headerStats.currentAccountedBytes in 0..finalStats.headerStats.maxAccountedBudgetBytes)
        assertTrue(finalStats.detailStats.currentAccountedBytes in 0..finalStats.detailStats.maxAccountedBudgetBytes)
        assertTrue(finalStats.rawScoreStats.currentAccountedBytes in 0..finalStats.rawScoreStats.maxAccountedBudgetBytes)
    }

    // =========================================================================
    // Helper Fixture Builders
    // =========================================================================

    private fun samplePack(
        packId: String,
        sourceType: String = "SEED_BUILTIN",
        priority: Int = 0,
        topicCount: Int = 1
    ) = KnowledgePackMetadataEntity(
        packId = packId,
        sourceType = sourceType,
        packVersion = 1,
        schemaVersion = 1,
        sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        packPriority = priority,
        importedAt = 1700000000000L,
        topicCount = topicCount
    )

    private fun sampleHeader(
        topicId: String,
        packId: String,
        orderIndex: Int = 0,
        arabicSummary: String = "ملخص عربي للموضوع",
        englishSummary: String = "English topic summary",
        sourceType: String = "SEED_BUILTIN",
        priority: Int = 0,
        contentVersion: Int = 1
    ) = KnowledgeTopicHeaderEntity(
        topicId = topicId,
        packId = packId,
        topicOrderIndex = orderIndex,
        arabicSummary = arabicSummary,
        englishSummary = englishSummary,
        suggestedFollowUpsJson = converters.fromActionableOptionList(
            listOf(ActionableOption("تأثير القهوة", "تأثير القهوة على النوم"))
        ),
        sourceType = sourceType,
        packPriority = priority,
        contentVersion = contentVersion,
        isCoreTopic = (sourceType == "SEED_BUILTIN"),
        isActiveWinner = true
    )

    private fun sampleKeyword(
        topicId: String,
        packId: String,
        order: Int,
        original: String = "الفيزياء",
        strictPhrase: String = "الفيزياء",
        relaxedPhrase: String = "الفيزياء",
        tokenCount: Int = 1,
        contentTokenCount: Int = 1
    ) = KnowledgeKeywordEntity(
        keywordId = 0L,
        topicId = topicId,
        packId = packId,
        keywordOrder = order,
        originalKeyword = original,
        strictNormalizedPhrase = strictPhrase,
        relaxedNormalizedPhrase = relaxedPhrase,
        relaxedCharLength = relaxedPhrase.length,
        tokenCount = tokenCount,
        contentTokenCount = contentTokenCount,
        matchType = if (tokenCount > 1) "MULTI_WORD_PHRASE" else "SINGLE_TOKEN",
        language = "ar"
    )

    private fun sampleToken(
        keywordId: Long,
        position: Int,
        strictToken: String = "الفيزياء",
        strictStem: String = "فيزياء",
        relaxedToken: String = "الفيزياء",
        relaxedStem: String = "فيزياء"
    ) = KnowledgeKeywordTokenEntity(
        tokenId = 0L,
        keywordId = keywordId,
        tokenPosition = position,
        strictToken = strictToken,
        strictStem = strictStem,
        relaxedToken = relaxedToken,
        relaxedStem = relaxedStem
    )

    private fun sampleDetail(
        topicId: String,
        packId: String,
        relatedJson: String = "[\"ai_concept\"]"
    ) = KnowledgeTopicDetailEntity(
        topicId = topicId,
        packId = packId,
        detailedArabic = "تفاصيل علمية موسعة باللغة العربية حول هذا الموضوع.",
        detailedEnglish = "Extended scientific details in English about this topic.",
        relatedTopicIdsJson = relatedJson,
        examplesJson = "[\"مثال 1\", \"مثال 2\"]",
        lastReviewed = 1700000000000L,
        additionalContentJson = "{\"difficulty\":\"intermediate\"}"
    )

    private fun sampleBranch(
        branchId: String,
        topicId: String,
        packId: String,
        priorityOrder: Int,
        isDefault: Boolean
    ) = KnowledgeFollowUpBranchEntity(
        branchId = branchId,
        topicId = topicId,
        packId = packId,
        priorityOrder = priorityOrder,
        matchMode = if (isDefault) "DEFAULT_FALLBACK" else "SUBSTRING_CONTAINS",
        triggerKeywordsJson = if (isDefault) "[]" else "[\"تتكون\", \"تتشكل\"]",
        responseArabic = "إجابة متابعة تفصيلية بالعربية.",
        responseEnglish = "Detailed follow-up response in English.",
        actionableOptionsJson = converters.fromActionableOptionList(
            listOf(ActionableOption("سؤال مقترح", "ما هو الذكاء الاصطناعي؟"))
        ),
        isDefault = isDefault
    )
}
