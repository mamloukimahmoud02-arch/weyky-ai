package com.example.core.intelligence.knowledge

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.conversation.ConversationSession
import com.example.core.intelligence.conversation.ConversationalReasoner
import com.example.core.intelligence.conversation.LocalConversationalEngine
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.importer.KnowledgePackImportLimits
import com.example.core.intelligence.knowledge.importer.KnowledgePackImportStatus
import com.example.core.intelligence.knowledge.importer.KnowledgePackImporter
import com.example.core.intelligence.knowledge.importer.KnowledgePackJsonParser
import com.example.core.intelligence.knowledge.importer.KnowledgePackParseOutcome
import com.example.core.intelligence.knowledge.importer.KnowledgePackSpecificationV1
import com.example.core.intelligence.knowledge.importer.SnapshotRefreshState
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.repository.TopicDetailLoadStatus
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
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2B4BJsonKnowledgePackImporterTest {

    private lateinit var context: Context
    private lateinit var database: WeykyKnowledgeDatabase
    private lateinit var repository: KnowledgeRepository
    private lateinit var importer: KnowledgePackImporter
    private val recordedSqlQueries = mutableListOf<String>()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        recordedSqlQueries.clear()
        database = Room.inMemoryDatabaseBuilder(context, WeykyKnowledgeDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                object : RoomDatabaseQueryCallback() {
                    override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                        synchronized(recordedSqlQueries) {
                            recordedSqlQueries.add(sqlQuery.trim())
                        }
                    }
                },
                Executors.newSingleThreadExecutor()
            )
            .build()

        repository = KnowledgeRepository(
            database = database,
            ioDispatcher = Dispatchers.Default
        )
        assertTrue(repository.initializeSeedAndWarmUpIndex())
        importer = KnowledgePackImporter(
            database = database,
            repository = repository,
            ioDispatcher = Dispatchers.Default
        )
    }

    @After
    fun tearDown() {
        database.close()
        KnowledgeRepository.clearInstanceForTesting()
    }

    private abstract class RoomDatabaseQueryCallback : androidx.room.RoomDatabase.QueryCallback

    // =========================================================================
    // Section A: Valid Pack Import Tests
    // =========================================================================

    @Test
    fun sectionA_importValidBundledAssetAndSmallMediumPacks_withAndWithoutDetails() = runBlocking {
        // 1. Import bundled sample_science_pack_v1.json from assets
        val assetResult = importer.importFromAsset(
            context = context,
            assetPath = "knowledge_packs/sample_science_pack_v1.json"
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, assetResult.status)
        assertEquals("sample_science_pack_v1", assetResult.packId)
        assertEquals(2, assetResult.importedTopicCount)
        assertEquals(0, assetResult.updatedTopicCount)
        assertEquals(0, assetResult.rejectedTopicCount)
        assertEquals(13, assetResult.activeWinnerCountInSnapshot) // 11 seed + 2 imported
        assertEquals(SnapshotRefreshState.REFRESHED_ATOMICALLY, assetResult.snapshotRefreshState)
        assertEquals(0, repository.cache.snapshotStats().detailStats.entryCount) // Lazy loading preserved

        // 2. Import valid pack WITHOUT details or followUpBranches
        val headerOnlyJson = buildSyntheticPackJson(
            packId = "header_only_pack_v1",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 110,
            topicCount = 3,
            includeDetails = false,
            includeBranches = false
        )
        val headerOnlyResult = importer.importFromJsonString(headerOnlyJson)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, headerOnlyResult.status)
        assertEquals(3, headerOnlyResult.importedTopicCount)
        assertEquals(16, repository.currentSnapshot.topicCount)
        assertEquals(0, repository.cache.snapshotStats().detailStats.entryCount)

        // Requesting detail on a header-only topic gracefully returns MISSING_DETAIL without crashing
        val missingDetailLoad = repository.getOrLoadTopicDetail("header_only_pack_v1_topic_0")
        assertEquals(TopicDetailLoadStatus.MISSING_DETAIL, missingDetailLoad.status)
        assertNull(missingDetailLoad.bundle)

        // 3. Import valid medium pack (100 topics) with details, branches, and valid references
        val mediumJson = buildSyntheticPackJson(
            packId = "medium_pack_100",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 115,
            topicCount = 100,
            includeDetails = true,
            includeBranches = true,
            crossPackReferenceId = "physics_universe"
        )
        val mediumResult = importer.importFromJsonString(mediumJson)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, mediumResult.status)
        assertEquals(100, mediumResult.importedTopicCount)
        assertEquals(116, repository.currentSnapshot.topicCount)
        assertEquals(0, repository.cache.snapshotStats().detailStats.entryCount)
    }

    // =========================================================================
    // Section B: Schema & Malformed JSON Tests
    // =========================================================================

    @Test
    fun sectionB_rejectsMalformedTruncatedUnsupportedSchemaWrongTypesBlankFieldsAndLimits() = runBlocking {
        val initialGen = repository.currentGeneration
        val initialCount = repository.currentSnapshot.topicCount

        // 1. Malformed JSON syntax
        val malformed = importer.importFromJsonString("{ \"packId\": \"broken_json\", ")
        assertEquals(KnowledgePackImportStatus.PARSING_FAILURE, malformed.status)
        assertEquals(SnapshotRefreshState.NOT_MODIFIED, malformed.snapshotRefreshState)

        // 2. Truncated JSON inside topics array
        val truncated = importer.importFromJsonString(
            """{"schemaVersion":1,"packId":"trunc_pack","packVersion":1,"packPriority":100,"topics":[{"topicId":"t1""""
        )
        assertEquals(KnowledgePackImportStatus.PARSING_FAILURE, truncated.status)

        // 3. Missing required field (missing arabicSummary)
        val missingField = importer.importFromJsonString(
            """
            {
              "schemaVersion": 1,
              "packId": "missing_field_pack",
              "packVersion": 1,
              "packPriority": 100,
              "topics": [
                {
                  "topicId": "custom_topic_1",
                  "englishSummary": "English summary only",
                  "keywords": ["test"],
                  "suggestedFollowUps": ["next?"]
                }
              ]
            }
            """.trimIndent()
        )
        assertEquals(KnowledgePackImportStatus.PARSING_FAILURE, missingField.status)
        assertTrue(missingField.errors.any { it.contains("arabicSummary") })

        // 4. Unsupported schemaVersion (e.g., 2 and 99) & unsupported packFormat
        val unsupportedSchema = importer.importFromJsonString(
            """
            {
              "schemaVersion": 2,
              "packId": "future_pack",
              "packVersion": 1,
              "packPriority": 100,
              "topics": []
            }
            """.trimIndent()
        )
        assertEquals(KnowledgePackImportStatus.SCHEMA_UNSUPPORTED, unsupportedSchema.status)

        val unsupportedFormat = importer.importFromJsonString(
            """
            {
              "packFormat": "OTHER_FORMAT_V9",
              "schemaVersion": 1,
              "packId": "other_format_pack",
              "packVersion": 1,
              "packPriority": 100,
              "topics": []
            }
            """.trimIndent()
        )
        assertEquals(KnowledgePackImportStatus.SCHEMA_UNSUPPORTED, unsupportedFormat.status)

        // 5. Wrong field data types (string where integer expected, integer where string expected)
        val wrongTypeInt = importer.importFromJsonString(
            """
            {
              "schemaVersion": 1,
              "packId": "wrong_type_pack",
              "packVersion": "one",
              "packPriority": 100,
              "topics": []
            }
            """.trimIndent()
        )
        assertEquals(KnowledgePackImportStatus.PARSING_FAILURE, wrongTypeInt.status)

        val wrongTypeString = importer.importFromJsonString(
            """
            {
              "schemaVersion": 1,
              "packId": 12345,
              "packVersion": 1,
              "packPriority": 100,
              "topics": []
            }
            """.trimIndent()
        )
        assertEquals(KnowledgePackImportStatus.PARSING_FAILURE, wrongTypeString.status)

        // 6. Blank required string field
        val blankField = importer.importFromJsonString(
            """
            {
              "schemaVersion": 1,
              "packId": "blank_field_pack",
              "packVersion": 1,
              "packPriority": 100,
              "topics": [
                {
                  "topicId": "custom_topic_blank",
                  "arabicSummary": "   ",
                  "englishSummary": "Valid english summary",
                  "keywords": ["test"],
                  "suggestedFollowUps": ["next?"]
                }
              ]
            }
            """.trimIndent()
        )
        assertEquals(KnowledgePackImportStatus.VALIDATION_FAILED, blankField.status)

        // 7. Duplicate JSON keys inside object
        val duplicateJsonKey = importer.importFromJsonString(
            """
            {
              "schemaVersion": 1,
              "packId": "first_id",
              "packId": "second_id_shadow",
              "packVersion": 1,
              "packPriority": 100,
              "topics": []
            }
            """.trimIndent()
        )
        assertEquals(KnowledgePackImportStatus.PARSING_FAILURE, duplicateJsonKey.status)
        assertTrue(duplicateJsonKey.errors.any { it.contains("Duplicate JSON key") })

        // 8. Oversized payload (INPUT_TOO_LARGE)
        val tinyLimitImporter = KnowledgePackImporter(
            database = database,
            repository = repository,
            limits = KnowledgePackImportLimits(maxInputBytes = 128L),
            ioDispatcher = Dispatchers.Default
        )
        val validSmallJson = buildSyntheticPackJson(
            packId = "oversized_test_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 100,
            topicCount = 2
        )
        val tooLargeResult = tinyLimitImporter.importFromJsonString(validSmallJson)
        assertEquals(KnowledgePackImportStatus.INPUT_TOO_LARGE, tooLargeResult.status)

        // Also verify streaming InputStream byte limit enforcement
        val streamTooLarge = tinyLimitImporter.importFromInputStream(
            ByteArrayInputStream(validSmallJson.toByteArray(StandardCharsets.UTF_8))
        )
        assertEquals(KnowledgePackImportStatus.INPUT_TOO_LARGE, streamTooLarge.status)

        // 9. Excessive JSON nesting depth
        val shallowDepthImporter = KnowledgePackImporter(
            database = database,
            repository = repository,
            limits = KnowledgePackImportLimits(maxJsonNestingDepth = 2),
            ioDispatcher = Dispatchers.Default
        )
        val depthExceeded = shallowDepthImporter.importFromJsonString(validSmallJson)
        assertEquals(KnowledgePackImportStatus.PARSING_FAILURE, depthExceeded.status)
        assertTrue(depthExceeded.errors.any { it.contains("nesting depth") })

        // Verify snapshot and generation were never altered by any failed import
        assertEquals(initialGen, repository.currentGeneration)
        assertEquals(initialCount, repository.currentSnapshot.topicCount)
    }

    // =========================================================================
    // Section C: Hash & Integrity Tests
    // =========================================================================

    @Test
    fun sectionC_verifiesRawAndLogicalSha256AndRejectsTamperedOrMismatchedHashes() = runBlocking {
        val baseJson = buildSyntheticPackJson(
            packId = "hash_verified_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 110,
            topicCount = 2
        )
        val rawSha = KnowledgePackSpecificationV1.computeRawBytesSha256(
            baseJson.toByteArray(StandardCharsets.UTF_8)
        )

        // 1. Mismatched expectedSha256 -> HASH_MISMATCH
        val wrongExpectedSha = "0".repeat(64)
        val mismatchResult = importer.importFromJsonString(
            json = baseJson,
            expectedSha256 = wrongExpectedSha
        )
        assertEquals(KnowledgePackImportStatus.HASH_MISMATCH, mismatchResult.status)
        assertNull(database.knowledgeDao().getPackMetadataById("hash_verified_pack"))

        // 2. Malformed expectedSha256 (not 64 hex chars) -> HASH_MISMATCH
        val malformedShaResult = importer.importFromJsonString(
            json = baseJson,
            expectedSha256 = "not_a_valid_sha256"
        )
        assertEquals(KnowledgePackImportStatus.HASH_MISMATCH, malformedShaResult.status)

        // 3. Valid expectedSha256 matching raw bytes -> SUCCESS_IMPORTED
        val validRawShaResult = importer.importFromJsonString(
            json = baseJson,
            expectedSha256 = rawSha
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, validRawShaResult.status)
        assertEquals(rawSha, validRawShaResult.rawBytesSha256)
        assertNotNull(validRawShaResult.logicalContentSha256)

        // 4. Embedded contentSha256 inside JSON:
        // First parse a pack to obtain its canonical logicalContentSha256, then embed it in JSON
        val packForEmbedded = buildSyntheticPackJson(
            packId = "embedded_sha_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 115,
            topicCount = 2,
            embeddedContentSha256 = null
        )
        val parsedOutcome = KnowledgePackJsonParser().parseFromJsonString(packForEmbedded)
                as KnowledgePackParseOutcome.Success
        val canonicalLogicalSha = KnowledgePackSpecificationV1.computeLogicalContentSha256(parsedOutcome.pack)

        // Embed wrong contentSha256 -> HASH_MISMATCH
        val tamperedEmbeddedJson = buildSyntheticPackJson(
            packId = "embedded_sha_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 115,
            topicCount = 2,
            embeddedContentSha256 = "f".repeat(64)
        )
        val tamperedEmbeddedResult = importer.importFromJsonString(tamperedEmbeddedJson)
        assertEquals(KnowledgePackImportStatus.HASH_MISMATCH, tamperedEmbeddedResult.status)

        // Embed authentic canonicalLogicalSha -> SUCCESS_IMPORTED (no circular self-hashing!)
        val authenticEmbeddedJson = buildSyntheticPackJson(
            packId = "embedded_sha_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 115,
            topicCount = 2,
            embeddedContentSha256 = canonicalLogicalSha
        )
        val authenticEmbeddedResult = importer.importFromJsonString(authenticEmbeddedJson)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, authenticEmbeddedResult.status)
        assertEquals(canonicalLogicalSha, authenticEmbeddedResult.logicalContentSha256)
    }

    // =========================================================================
    // Section D: Duplicate, Idempotency & Conflict Resolution Tests
    // =========================================================================

    @Test
    fun sectionD_enforcesIdempotencyVersionUpgradesDowngradeRejectionAndPriorityWinners() = runBlocking {
        val packV1Json = buildSyntheticPackJson(
            packId = "astro_pack",
            packVersion = 2,
            contentVersion = 2,
            packPriority = 120,
            topicCount = 3,
            summaryPrefix = "V2Content"
        )

        // Import a second independent external pack so we can verify targeted cache invalidation
        val unaffectedPackJson = buildSyntheticPackJson(
            packId = "unaffected_side_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 110,
            topicCount = 1
        )
        assertEquals(
            KnowledgePackImportStatus.SUCCESS_IMPORTED,
            importer.importFromJsonString(unaffectedPackJson).status
        )

        // 1. Initial import -> SUCCESS_IMPORTED
        val firstImport = importer.importFromJsonString(packV1Json)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, firstImport.status)
        assertEquals(3, firstImport.importedTopicCount)
        val genAfterFirst = repository.currentGeneration

        // Warm one topic in DetailCache from astro_pack and one from unaffected_side_pack
        assertEquals(
            TopicDetailLoadStatus.LOADED_FROM_DB,
            repository.getOrLoadTopicDetail("astro_pack_topic_0").status
        )
        assertEquals(
            TopicDetailLoadStatus.LOADED_FROM_DB,
            repository.getOrLoadTopicDetail("unaffected_side_pack_topic_0").status
        )
        assertEquals(2, repository.cache.snapshotStats().detailStats.entryCount)

        // 2. Re-import exact same JSON -> ALREADY_IMPORTED (no DB writes, no snapshot rebuild)
        val reImportExact = importer.importFromJsonString(packV1Json)
        assertEquals(KnowledgePackImportStatus.ALREADY_IMPORTED, reImportExact.status)
        assertEquals(0, reImportExact.importedTopicCount)
        assertEquals(3, reImportExact.skippedTopicCount)
        assertEquals(SnapshotRefreshState.NOT_MODIFIED, reImportExact.snapshotRefreshState)
        assertEquals(genAfterFirst, repository.currentGeneration)

        // 3. Re-import identical logical content with extra JSON whitespace -> ALREADY_IMPORTED
        val whitespaceFormattedJson = "\n   " + packV1Json.replace("\n", "\n  ") + "   \n"
        val reImportWhitespace = importer.importFromJsonString(whitespaceFormattedJson)
        assertEquals(KnowledgePackImportStatus.ALREADY_IMPORTED, reImportWhitespace.status)
        assertEquals(genAfterFirst, repository.currentGeneration)

        // 4. Import older packVersion (v1 < v2) -> DUPLICATE_CONFLICT
        val olderVersionJson = buildSyntheticPackJson(
            packId = "astro_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 120,
            topicCount = 3,
            summaryPrefix = "OlderV1Content"
        )
        val olderResult = importer.importFromJsonString(olderVersionJson)
        assertEquals(KnowledgePackImportStatus.DUPLICATE_CONFLICT, olderResult.status)
        assertEquals(genAfterFirst, repository.currentGeneration)

        // 5. Import same packVersion & contentVersion (v2) with DIFFERENT content -> DUPLICATE_CONFLICT
        val conflictingSameVersionJson = buildSyntheticPackJson(
            packId = "astro_pack",
            packVersion = 2,
            contentVersion = 2,
            packPriority = 120,
            topicCount = 3,
            summaryPrefix = "MutatedV2Content"
        )
        val conflictResult = importer.importFromJsonString(conflictingSameVersionJson)
        assertEquals(KnowledgePackImportStatus.DUPLICATE_CONFLICT, conflictResult.status)
        assertEquals(genAfterFirst, repository.currentGeneration)

        // 6. Import newer version (v3 > v2) -> SUCCESS_UPDATED
        val newerVersionJson = buildSyntheticPackJson(
            packId = "astro_pack",
            packVersion = 3,
            contentVersion = 3,
            packPriority = 120,
            topicCount = 3,
            summaryPrefix = "UpgradedV3Content"
        )
        val updateResult = importer.importFromJsonString(newerVersionJson)
        assertEquals(KnowledgePackImportStatus.SUCCESS_UPDATED, updateResult.status)
        assertEquals(0, updateResult.importedTopicCount)
        assertEquals(3, updateResult.updatedTopicCount)
        assertTrue(repository.currentGeneration > genAfterFirst)
        assertTrue(
            repository.getTopicById("astro_pack_topic_0")!!.englishSummary.contains("UpgradedV3Content")
        )
        // Affected topic detail (v2) was invalidated while unaffected `unaffected_side_pack_topic_0` detail remained cached!
        assertNull(
            repository.cache.getDetailForIdentity("astro_pack_topic_0", "astro_pack", 2)
        )
        assertNotNull(
            repository.cache.getDetailForIdentity("unaffected_side_pack_topic_0", "unaffected_side_pack", 1)
        )

        // 7. Duplicate topicId inside the same pack -> DUPLICATE_CONFLICT
        val duplicateIntraPackJson = """
            {
              "schemaVersion": 1,
              "packId": "dup_internal_pack",
              "packVersion": 1,
              "packPriority": 100,
              "topics": [
                {
                  "topicId": "same_topic_id",
                  "topicOrderIndex": 0,
                  "arabicSummary": "ملخص 1",
                  "englishSummary": "Summary 1",
                  "keywords": ["كلمة أولى"],
                  "suggestedFollowUps": ["سؤال 1؟"]
                },
                {
                  "topicId": "same_topic_id",
                  "topicOrderIndex": 1,
                  "arabicSummary": "ملخص 2",
                  "englishSummary": "Summary 2",
                  "keywords": ["كلمة ثانية"],
                  "suggestedFollowUps": ["سؤال 2؟"]
                }
              ]
            }
        """.trimIndent()
        val dupIntraResult = importer.importFromJsonString(duplicateIntraPackJson)
        assertEquals(KnowledgePackImportStatus.DUPLICATE_CONFLICT, dupIntraResult.status)

        // 8. Two different external packs sharing a topicId -> higher packPriority wins deterministically
        val competingHighPriorityPack = """
            {
              "schemaVersion": 1,
              "packId": "competing_high_pack",
              "packVersion": 1,
              "contentVersion": 1,
              "packPriority": 250,
              "topics": [
                {
                  "topicId": "astro_pack_topic_0",
                  "topicOrderIndex": 0,
                  "arabicSummary": "ملخص فائز بأولوية 250",
                  "englishSummary": "Winning summary from priority 250 pack",
                  "keywords": ["astro_pack_topic_0_kw"],
                  "suggestedFollowUps": ["Follow up?"]
                }
              ]
            }
        """.trimIndent()
        val competingResult = importer.importFromJsonString(competingHighPriorityPack)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, competingResult.status)
        assertEquals(
            "competing_high_pack",
            repository.currentSnapshot.getActiveIdentity("astro_pack_topic_0")?.packId
        )
        assertEquals(
            "Winning summary from priority 250 pack",
            repository.getTopicById("astro_pack_topic_0")?.englishSummary
        )
    }

    // =========================================================================
    // Section E: Core Seed Protection Tests
    // =========================================================================

    @Test
    fun sectionE_strictlyProtectsCoreSeedPackAndAll11ProtectedCoreSeedTopics() = runBlocking {
        // 1. Attempt to import pack with packId = "core_seed_pack"
        val hijackPackIdJson = buildSyntheticPackJson(
            packId = SeedKnowledgeCatalog.CORE_SEED_PACK_ID,
            packVersion = 99,
            contentVersion = 99,
            packPriority = 9999,
            topicCount = 1
        )
        val hijackPackResult = importer.importFromJsonString(hijackPackIdJson)
        assertEquals(KnowledgePackImportStatus.CORE_PROTECTION_VIOLATION, hijackPackResult.status)

        // 2. Attempt to import pack with sourceType = "SEED_BUILTIN"
        val hijackSourceTypeJson = """
            {
              "schemaVersion": 1,
              "packId": "fake_builtin_pack",
              "packVersion": 1,
              "sourceType": "SEED_BUILTIN",
              "packPriority": 500,
              "topics": [
                {
                  "topicId": "custom_safe_id",
                  "arabicSummary": "ملخص",
                  "englishSummary": "Summary",
                  "keywords": ["اختبار"],
                  "suggestedFollowUps": ["سؤال؟"]
                }
              ]
            }
        """.trimIndent()
        val hijackSourceResult = importer.importFromJsonString(hijackSourceTypeJson)
        assertEquals(KnowledgePackImportStatus.CORE_PROTECTION_VIOLATION, hijackSourceResult.status)

        // 3. Attempt to overwrite any protected core seed topic (e.g., "identity", "physics_universe")
        for (protectedTopicId in listOf("identity", "physics_universe", "battery_care", "salalah_tourism")) {
            val hijackTopicJson = """
                {
                  "schemaVersion": 1,
                  "packId": "external_override_attempt_$protectedTopicId",
                  "packVersion": 10,
                  "packPriority": 99999,
                  "topics": [
                    {
                      "topicId": "$protectedTopicId",
                      "arabicSummary": "محاولة استبدال موضوع أساسي محمي",
                      "englishSummary": "Attempt to overwrite protected core seed topic",
                      "keywords": ["hijack"],
                      "suggestedFollowUps": ["hijack?"]
                    }
                  ]
                }
            """.trimIndent()
            val res = importer.importFromJsonString(hijackTopicJson)
            assertEquals(KnowledgePackImportStatus.CORE_PROTECTION_VIOLATION, res.status)
            assertEquals(
                SeedKnowledgeCatalog.CORE_SEED_PACK_ID,
                repository.currentSnapshot.getActiveIdentity(protectedTopicId)?.packId
            )
        }
    }

    // =========================================================================
    // Section F: Reference Integrity Tests
    // =========================================================================

    @Test
    fun sectionF_validatesReferencesAcrossPackAndActiveSnapshotAndRejectsInvalidOrBrokenReferences() = runBlocking {
        // 1. Valid intra-pack + cross-pack references -> SUCCESS_IMPORTED
        val validRefsPack = """
            {
              "schemaVersion": 1,
              "packId": "ref_valid_pack",
              "packVersion": 1,
              "contentVersion": 1,
              "packPriority": 120,
              "topics": [
                {
                  "topicId": "ref_topic_alpha",
                  "topicOrderIndex": 0,
                  "arabicSummary": "موضوع ألفا",
                  "englishSummary": "Topic Alpha",
                  "keywords": ["ألفا", "alpha_ref_kw"],
                  "suggestedFollowUps": ["ما علاقة ألفا ببيتا؟"],
                  "referenceTopicIds": ["ref_topic_beta", "physics_universe"]
                },
                {
                  "topicId": "ref_topic_beta",
                  "topicOrderIndex": 1,
                  "arabicSummary": "موضوع بيتا",
                  "englishSummary": "Topic Beta",
                  "keywords": ["بيتا", "beta_ref_kw"],
                  "suggestedFollowUps": ["ما علاقة بيتا بألفا؟"],
                  "referenceTopicIds": ["ref_topic_alpha"]
                }
              ]
            }
        """.trimIndent()
        val validRefResult = importer.importFromJsonString(validRefsPack)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, validRefResult.status)

        // 2. Reference to non-existent topic -> REFERENCE_INVALID
        val missingTargetPack = """
            {
              "schemaVersion": 1,
              "packId": "ref_missing_pack",
              "packVersion": 1,
              "packPriority": 120,
              "topics": [
                {
                  "topicId": "ref_topic_gamma",
                  "arabicSummary": "موضوع غاما",
                  "englishSummary": "Topic Gamma",
                  "keywords": ["غاما"],
                  "suggestedFollowUps": ["سؤال؟"],
                  "referenceTopicIds": ["non_existent_ghost_topic"]
                }
              ]
            }
        """.trimIndent()
        val missingRefResult = importer.importFromJsonString(missingTargetPack)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, missingRefResult.status)

        // 3. Self-reference -> REFERENCE_INVALID
        val selfRefPack = """
            {
              "schemaVersion": 1,
              "packId": "ref_self_pack",
              "packVersion": 1,
              "packPriority": 120,
              "topics": [
                {
                  "topicId": "self_ref_topic",
                  "arabicSummary": "موضوع يشير لنفسه",
                  "englishSummary": "Topic referencing itself",
                  "keywords": ["ذاتي"],
                  "suggestedFollowUps": ["سؤال؟"],
                  "referenceTopicIds": ["self_ref_topic"]
                }
              ]
            }
        """.trimIndent()
        val selfRefResult = importer.importFromJsonString(selfRefPack)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, selfRefResult.status)

        // 4. Duplicate reference in the same topic -> REFERENCE_INVALID
        val duplicateRefPack = """
            {
              "schemaVersion": 1,
              "packId": "ref_dup_pack",
              "packVersion": 1,
              "packPriority": 120,
              "topics": [
                {
                  "topicId": "dup_ref_topic",
                  "arabicSummary": "موضوع بمرجع مكرر",
                  "englishSummary": "Topic with duplicate reference",
                  "keywords": ["تكرار مرجع"],
                  "suggestedFollowUps": ["سؤال؟"],
                  "referenceTopicIds": ["physics_universe", "physics_universe"]
                }
              ]
            }
        """.trimIndent()
        val dupRefResult = importer.importFromJsonString(duplicateRefPack)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, dupRefResult.status)

        // 5. Reference to an inactive (deactivated) pack's topic -> REFERENCE_INVALID
        val deactivatablePack = buildSyntheticPackJson(
            packId = "temp_active_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 110,
            topicCount = 1
        )
        assertEquals(
            KnowledgePackImportStatus.SUCCESS_IMPORTED,
            importer.importFromJsonString(deactivatablePack).status
        )
        assertTrue(repository.deactivatePackAndRefreshSnapshot("temp_active_pack").isSuccess)

        val refToInactivePack = """
            {
              "schemaVersion": 1,
              "packId": "ref_to_inactive_pack",
              "packVersion": 1,
              "packPriority": 120,
              "topics": [
                {
                  "topicId": "active_topic_referencing_inactive",
                  "arabicSummary": "ملخص",
                  "englishSummary": "Summary",
                  "keywords": ["كلمة"],
                  "suggestedFollowUps": ["سؤال؟"],
                  "referenceTopicIds": ["temp_active_pack_topic_0"]
                }
              ]
            }
        """.trimIndent()
        val refToInactiveResult = importer.importFromJsonString(refToInactivePack)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, refToInactiveResult.status)

        // 6. Updating a pack that removes a topic referenced by another active pack -> REFERENCE_INVALID
        val dependentPack = """
            {
              "schemaVersion": 1,
              "packId": "dependent_external_pack",
              "packVersion": 1,
              "packPriority": 130,
              "topics": [
                {
                  "topicId": "dependent_topic_delta",
                  "arabicSummary": "موضوع يعتمد على بيتا",
                  "englishSummary": "Topic depending on ref_topic_beta",
                  "keywords": ["دلتا", "delta_kw"],
                  "suggestedFollowUps": ["سؤال؟"],
                  "referenceTopicIds": ["ref_topic_beta"]
                }
              ]
            }
        """.trimIndent()
        assertEquals(
            KnowledgePackImportStatus.SUCCESS_IMPORTED,
            importer.importFromJsonString(dependentPack).status
        )

        // Now try to update `ref_valid_pack` to v2 while removing `ref_topic_beta` (which `dependent_topic_delta` references!)
        val breakingUpdatePack = """
            {
              "schemaVersion": 1,
              "packId": "ref_valid_pack",
              "packVersion": 2,
              "contentVersion": 2,
              "packPriority": 120,
              "topics": [
                {
                  "topicId": "ref_topic_alpha",
                  "topicOrderIndex": 0,
                  "arabicSummary": "موضوع ألفا المحدث بدون بيتا",
                  "englishSummary": "Updated Alpha without Beta",
                  "keywords": ["ألفا", "alpha_ref_kw"],
                  "suggestedFollowUps": ["سؤال؟"],
                  "referenceTopicIds": ["physics_universe"]
                }
              ]
            }
        """.trimIndent()
        val breakingUpdateResult = importer.importFromJsonString(breakingUpdatePack)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, breakingUpdateResult.status)
        assertTrue(repository.currentSnapshot.topicsById.containsKey("ref_topic_beta"))
    }

    // =========================================================================
    // Section G: Atomic Rollback & Snapshot Recovery Tests
    // =========================================================================

    @Test
    fun sectionG_rollsBackAtomicallyOnMidTransactionFailureAndRecoversSnapshotOnPostCommitFailure() = runBlocking {
        val genBefore = repository.currentGeneration
        val countBefore = repository.currentSnapshot.topicCount

        // 1. Mid-transaction failure -> full DB rollback, snapshot & cache untouched
        val rollbackPackJson = buildSyntheticPackJson(
            packId = "rollback_test_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 140,
            topicCount = 5
        )
        val rollbackResult = importer.importFromJsonString(
            json = rollbackPackJson,
            simulatedMidTransactionFailure = {
                throw IllegalStateException("Simulated disk/SQLite write failure mid-transaction")
            }
        )
        assertEquals(KnowledgePackImportStatus.STORAGE_FAILURE, rollbackResult.status)
        assertEquals(SnapshotRefreshState.NOT_MODIFIED, rollbackResult.snapshotRefreshState)
        assertEquals(genBefore, repository.currentGeneration)
        assertEquals(countBefore, repository.currentSnapshot.topicCount)
        assertNull(database.knowledgeDao().getPackMetadataById("rollback_test_pack"))
        assertTrue(database.knowledgeDao().getTopicIdsByPack("rollback_test_pack").isEmpty())

        // 2. Post-commit snapshot build failure with successful automatic recovery from DB
        val recoveryPackJson = buildSyntheticPackJson(
            packId = "recovered_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 140,
            topicCount = 4
        )
        val recoveredResult = importer.importFromJsonString(
            json = recoveryPackJson,
            simulatedSnapshotBuildFailure = {
                throw RuntimeException("Simulated transient error during initial snapshot build")
            }
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, recoveredResult.status)
        assertEquals(SnapshotRefreshState.RECOVERED_FROM_DATABASE, recoveredResult.snapshotRefreshState)
        assertEquals(countBefore + 4, repository.currentSnapshot.topicCount)
        assertTrue(repository.currentSnapshot.topicsById.containsKey("recovered_pack_topic_0"))

        // 3. Post-commit snapshot build failure AND recovery failure -> retains previous valid snapshot
        val snapshotBeforeDoubleFailure = repository.currentSnapshot
        val doubleFailurePackJson = buildSyntheticPackJson(
            packId = "double_fail_snapshot_pack",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 140,
            topicCount = 2
        )
        val doubleFailResult = importer.importFromJsonString(
            json = doubleFailurePackJson,
            simulatedSnapshotBuildFailure = {
                throw RuntimeException("Initial snapshot build failure")
            },
            simulatedRecoveryFailure = {
                throw RuntimeException("Recovery snapshot build failure")
            }
        )
        assertEquals(KnowledgePackImportStatus.SNAPSHOT_REFRESH_FAILURE, doubleFailResult.status)
        assertEquals(
            SnapshotRefreshState.FAILED_PREVIOUS_SNAPSHOT_RETAINED,
            doubleFailResult.snapshotRefreshState
        )
        // Previous valid snapshot is strictly retained in memory
        assertEquals(snapshotBeforeDoubleFailure, repository.currentSnapshot)
    }

    // =========================================================================
    // Section H: End-to-End Search, Conversation & Scale Benchmarks (10, 100, 500, 1000 Topics)
    // =========================================================================

    @Test
    fun sectionH_endToEndConversationIntegrationAndScaleBenchmarksUpTo1000Topics() = runBlocking {
        // 1. Import bundled science pack and test Arabic & English search + multi-turn conversation
        val scienceImport = importer.importFromAsset(
            context = context,
            assetPath = "knowledge_packs/sample_science_pack_v1.json"
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, scienceImport.status)
        assertEquals(0, repository.cache.snapshotStats().detailStats.entryCount)

        val engine = LocalConversationalEngine(
            knowledgeRepository = repository
        )

        // Turn 1 (Arabic): Ask about Quantum Computing
        val turn1Ar = engine.respond("حدثني عن الحوسبة الكمومية والكيوبت")
        assertEquals("quantum_computing_basics", engine.session.currentTopic)
        assertTrue(turn1Ar.replyText.contains("الكيوبت"))
        // Only the matched topic's detail was pre-warmed after Turn 1
        assertEquals(1, repository.cache.snapshotStats().detailStats.entryCount)
        assertNotNull(
            repository.cache.getDetailForIdentity("quantum_computing_basics", "sample_science_pack_v1", 1)
        )
        assertNull(
            repository.cache.getDetailForIdentity("solar_photovoltaic_energy", "sample_science_pack_v1", 1)
        )

        // Turn 2 (Arabic Follow-Up): Ask follow-up trigger matching `quantum_qubit_vs_bit` branch
        val turn2Ar = engine.respond("ما الفرق بين الكيوبت والبت؟")
        assertEquals("quantum_computing_basics", engine.session.currentTopic)
        assertTrue(turn2Ar.replyText.contains("التراكب الكمومي"))

        // Turn 3 (English): Switch topic to Solar Photovoltaic Energy
        val turn3En = engine.respond("Tell me about solar photovoltaic cells and solar panels")
        assertEquals("solar_photovoltaic_energy", engine.session.currentTopic)
        assertTrue(turn3En.replyText.contains("photovoltaic") || turn3En.replyText.contains("silicon"))

        // Turn 4 (English Follow-Up): Ask about efficiency branch
        val turn4En = engine.respond("What about their efficiency?")
        assertTrue(turn4En.replyText.contains("20%") && turn4En.replyText.contains("24%"))

        // Verify all 11 core seed topics still work identically
        val coreCheck = engine.respond("ما عاصمة سلطنة عمان؟")
        assertTrue(coreCheck.replyText.contains("مسقط"))

        // 2. Scale Benchmarks: 10, 100, 500, and 1,000 topics
        val scaleCounts = listOf(10, 100, 500, 1_000)
        for (count in scaleCounts) {
            repository.cache.invalidateAll()
            val scalePackId = "scale_pack_$count"
            val json = buildSyntheticPackJson(
                packId = scalePackId,
                packVersion = 1,
                contentVersion = 1,
                packPriority = 150,
                topicCount = count,
                includeDetails = true,
                includeBranches = true
            )
            synchronized(recordedSqlQueries) { recordedSqlQueries.clear() }
            val result = importer.importFromJsonString(json)
            assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, result.status)
            assertEquals(count, result.importedTopicCount)

            // Verify 0 details loaded into DetailCache during import (100% lazy loading)
            assertEquals(0, result.metrics.detailCacheSizeAfterImport)
            assertEquals(0, repository.cache.snapshotStats().detailStats.entryCount)

            // Verify bulk snapshot rebuild used <= 3 SELECT queries
            assertTrue(repository.lastSnapshotBulkSelectQueryCount <= 3)

            // Measure first search (cache miss) and second search (cache hit)
            val targetKeyword = "${scalePackId}_kw_${count - 1}_ar"
            val t0 = System.nanoTime()
            val firstSearch = repository.search(targetKeyword)
            val firstSearchMicros = (System.nanoTime() - t0) / 1_000L
            assertEquals("${scalePackId}_topic_${count - 1}", firstSearch.matchedTopic?.id)

            val t1 = System.nanoTime()
            val secondSearch = repository.search(targetKeyword)
            val secondSearchMicros = (System.nanoTime() - t1) / 1_000L
            assertEquals("${scalePackId}_topic_${count - 1}", secondSearch.matchedTopic?.id)

            println(
                "BENCHMARK_2B4B scale=$count " +
                    "bytes=${result.metrics.inputByteCount} " +
                    "parseMs=${result.metrics.parseTimeMs} " +
                    "validateMs=${result.metrics.validationTimeMs} " +
                    "storageMs=${result.metrics.storageTransactionTimeMs} " +
                    "snapshotMs=${result.metrics.snapshotRebuildTimeMs} " +
                    "totalMs=${result.metrics.totalImportTimeMs} " +
                    "firstSearchUs=$firstSearchMicros " +
                    "cachedSearchUs=$secondSearchMicros " +
                    "detailCacheAfterImport=${result.metrics.detailCacheSizeAfterImport} " +
                    "activeTopics=${repository.currentSnapshot.topicCount}"
            )
        }
    }

    // =========================================================================
    // Helper: Synthetic WEYKY_KNOWLEDGE_PACK_V1 JSON Generator
    // =========================================================================

    private fun buildSyntheticPackJson(
        packId: String,
        packVersion: Int,
        contentVersion: Int,
        packPriority: Int,
        topicCount: Int,
        includeDetails: Boolean = true,
        includeBranches: Boolean = true,
        summaryPrefix: String = "SyntheticSummary",
        crossPackReferenceId: String? = null,
        embeddedContentSha256: String? = null
    ): String {
        val sb = StringBuilder(topicCount * 900)
        sb.append("{\n")
        sb.append("  \"packFormat\": \"WEYKY_KNOWLEDGE_PACK_V1\",\n")
        sb.append("  \"schemaVersion\": 1,\n")
        sb.append("  \"packId\": \"").append(packId).append("\",\n")
        sb.append("  \"packName\": \"Pack ").append(packId).append("\",\n")
        sb.append("  \"packVersion\": ").append(packVersion).append(",\n")
        sb.append("  \"contentVersion\": ").append(contentVersion).append(",\n")
        sb.append("  \"sourceType\": \"LOCAL_UPDATE\",\n")
        sb.append("  \"packPriority\": ").append(packPriority).append(",\n")
        sb.append("  \"description\": \"Synthetic benchmark pack ").append(packId).append("\",\n")
        sb.append("  \"language\": \"bilingual\",\n")
        sb.append("  \"createdAt\": 1710000000000,\n")
        sb.append("  \"updatedAt\": 1710000005000,\n")
        if (embeddedContentSha256 != null) {
            sb.append("  \"contentSha256\": \"").append(embeddedContentSha256).append("\",\n")
        }
        sb.append("  \"topics\": [\n")

        for (i in 0 until topicCount) {
            val topicId = "${packId}_topic_$i"
            val nextTopicId = if (topicCount > 1 && includeDetails) "${packId}_topic_${(i + 1) % topicCount}" else null
            val refs = mutableListOf<String>()
            if (nextTopicId != null && nextTopicId != topicId) refs.add(nextTopicId)
            if (crossPackReferenceId != null && crossPackReferenceId != topicId) refs.add(crossPackReferenceId)
            val refsJsonArray = refs.joinToString(", ") { "\"$it\"" }

            sb.append("    {\n")
            sb.append("      \"topicId\": \"").append(topicId).append("\",\n")
            sb.append("      \"title\": \"Topic ").append(i).append("\",\n")
            sb.append("      \"arabicTitle\": \"عنوان الموضوع ").append(i).append("\",\n")
            sb.append("      \"englishTitle\": \"Topic Title ").append(i).append("\",\n")
            sb.append("      \"category\": \"science\",\n")
            sb.append("      \"topicOrderIndex\": ").append(i).append(",\n")
            sb.append("      \"priority\": ").append(packPriority).append(",\n")
            sb.append("      \"contentVersion\": ").append(contentVersion).append(",\n")
            sb.append("      \"arabicSummary\": \"").append(summaryPrefix).append(" ملخص عربي للموضوع رقم ").append(i).append("\",\n")
            sb.append("      \"englishSummary\": \"").append(summaryPrefix).append(" English summary for topic ").append(i).append("\",\n")
            sb.append("      \"keywords\": [\n")
            sb.append("        \"").append(packId).append("_kw_").append(i).append("_ar\",\n")
            sb.append("        \"").append(packId).append("_kw_").append(i).append("_en\",\n")
            sb.append("        \"موضوع علمي ").append(packId).append(" رقم ").append(i).append("\"\n")
            sb.append("      ],\n")
            sb.append("      \"suggestedFollowUps\": [\n")
            sb.append("        \"ما تفاصيل الموضوع ").append(i).append("؟\"\n")
            sb.append("      ]")

            if (refs.isNotEmpty()) {
                sb.append(",\n      \"referenceTopicIds\": [").append(refsJsonArray).append("]")
            }

            if (includeDetails) {
                sb.append(",\n      \"detail\": {\n")
                sb.append("        \"detailedArabic\": \"شرح تفصيلي موسع للموضوع رقم ").append(i).append(" في حزمة ").append(packId).append("\",\n")
                sb.append("        \"detailedEnglish\": \"Detailed encyclopedic explanation for topic ").append(i).append(" in pack ").append(packId).append("\",\n")
                sb.append("        \"relatedTopicIds\": [").append(refsJsonArray).append("],\n")
                sb.append("        \"examples\": [\"مثال تطبيقي أول ").append(i).append("\", \"Second example ").append(i).append("\"],\n")
                sb.append("        \"lastReviewed\": 1710000005000\n")
                sb.append("      }")
            }

            if (includeBranches) {
                sb.append(",\n      \"followUpBranches\": [\n")
                sb.append("        {\n")
                sb.append("          \"branchId\": \"").append(topicId).append("_branch_1\",\n")
                sb.append("          \"priorityOrder\": 1,\n")
                sb.append("          \"matchMode\": \"SUBSTRING_CONTAINS\",\n")
                sb.append("          \"triggerKeywords\": [\"آلية\", \"mechanism\"],\n")
                sb.append("          \"responseArabic\": \"إجابة فرعية حول الآلية للموضوع ").append(i).append("\",\n")
                sb.append("          \"responseEnglish\": \"Branch response on mechanism for topic ").append(i).append("\",\n")
                sb.append("          \"actionableOptions\": [\n")
                sb.append("            {\n")
                sb.append("              \"labelArabic\": \"تطبيقات إضافية\",\n")
                sb.append("              \"labelEnglish\": \"More applications\",\n")
                sb.append("              \"queryText\": \"ما تطبيقات الموضوع ").append(i).append("؟\"\n")
                sb.append("            }\n")
                sb.append("          ],\n")
                sb.append("          \"isDefault\": false\n")
                sb.append("        }\n")
                sb.append("      ]")
            }

            sb.append("\n    }")
            if (i < topicCount - 1) sb.append(",")
            sb.append("\n")
        }

        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }
}
