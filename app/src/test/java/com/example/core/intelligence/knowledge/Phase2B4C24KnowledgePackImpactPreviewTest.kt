package com.example.core.intelligence.knowledge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KeywordWithTokensBundle
import com.example.core.intelligence.knowledge.db.dao.KnowledgeDao
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.db.entity.KnowledgeFollowUpBranchEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicDetailEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity
import com.example.core.intelligence.knowledge.importer.KnowledgePackImportStatus
import com.example.core.intelligence.knowledge.importer.KnowledgePackImporter
import com.example.core.intelligence.knowledge.preview.ImpactPreviewStatus
import com.example.core.intelligence.knowledge.preview.KnowledgePackImpactPreviewService
import com.example.core.intelligence.knowledge.preview.PackOperationType
import com.example.core.intelligence.knowledge.preview.ReferenceImpactType
import com.example.core.intelligence.knowledge.preview.TopicChangeType
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.security.MessageDigest

/**
 * Phase 2B.4.2C.4 — Knowledge Pack Impact Preview Tests.
 *
 * Verifies all 24 required preview, simulation, and read-only non-mutation scenarios:
 * 1. Newly added topics identified.
 * 2. Updated topics identified.
 * 3. Removed topics identified.
 * 4. Unchanged topics identified.
 * 5. Winner changes predicted correctly.
 * 6. Shadowed topics predicted not to change winners.
 * 7. Core topic conflicts detected.
 * 8. Invalid incoming references detected.
 * 9. Deactivation identifies fallback winners.
 * 10. Deactivation identifies topics with no remaining winners.
 * 11. Deactivation detects broken incoming references.
 * 12. Reactivation predicts winner displacement correctly.
 * 13. Priority change predicts all affected winners.
 * 14. Deletion identifies cascade-dependent records.
 * 15. Deletion detects references that would become invalid.
 * 16. Deletion correctly handles available fallback versions.
 * 17. Preview results match actual mutation validation outcomes.
 * 18. Preview does not change any persisted database records.
 * 19. Preview does not alter active winner flags.
 * 20. Preview does not advance committed or published generations.
 * 21. Preview does not modify caches or snapshots.
 * 22. Preview detects stale generation conditions.
 * 23. Concurrent previews and operations execute safely.
 * 24. Preview never reports safe to apply when analysis is incomplete or failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2B4C24KnowledgePackImpactPreviewTest {

    private lateinit var context: Context
    private lateinit var database: WeykyKnowledgeDatabase
    private lateinit var dao: KnowledgeDao
    private lateinit var repository: KnowledgeRepository
    private lateinit var importer: KnowledgePackImporter
    private lateinit var previewService: KnowledgePackImpactPreviewService
    private val converters = KnowledgeTypeConverters()

    @Before
    fun setUp() = runBlocking {
        KnowledgeRepository.clearInstanceForTesting()
        WeykyKnowledgeDatabase.clearInstanceForTesting()
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            WeykyKnowledgeDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        dao = database.knowledgeDao()
        repository = KnowledgeRepository(
            database = database,
            ioDispatcher = Dispatchers.Default
        )
        assertTrue(repository.initializeSeedAndWarmUpIndex())
        importer = repository.createKnowledgePackImporter()
        previewService = repository.createKnowledgePackImpactPreviewService()
    }

    @After
    fun tearDown() {
        previewService.staleGenerationSimulator = null
        repository.snapshotPublicationHook = null
        repository.retrievalFailureSimulator = null
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        WeykyKnowledgeDatabase.clearInstanceForTesting()
        KnowledgeRepository.clearInstanceForTesting()
    }

    private fun buildSha256Hex(seed: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun buildPackJsonWithTopics(
        packId: String,
        packVersion: Int = 1,
        contentVersion: Int = 1,
        packPriority: Int = 120,
        topicsJson: String
    ): String {
        return """
        {
          "packFormat": "WEYKY_KNOWLEDGE_PACK_V1",
          "schemaVersion": 1,
          "packId": "$packId",
          "packName": "Pack $packId",
          "packVersion": $packVersion,
          "contentVersion": $contentVersion,
          "sourceType": "LOCAL_UPDATE",
          "packPriority": $packPriority,
          "description": "Preview test pack for $packId",
          "language": "bilingual",
          "createdAt": 1710000000000,
          "updatedAt": 1710000005000,
          "topics": [
            $topicsJson
          ]
        }
        """.trimIndent()
    }

    private fun buildTopicJsonNode(
        topicId: String,
        contentVersion: Int = 1,
        priority: Int = 120,
        topicOrderIndex: Int = 100 + (topicId.hashCode() and 0x0FFF),
        arabicTitle: String = "عنوان $topicId",
        englishTitle: String = "Title $topicId",
        arabicSummary: String = "ملخص تفصيلي للموضوع $topicId لاختبار محاكاة التغييرات والمعاينة.",
        englishSummary: String = "Detailed summary for $topicId to test change simulation and preview.",
        keywords: List<String> = listOf("مفهوم $topicId", "concept $topicId", "علم $topicId"),
        referenceTopicIds: List<String> = emptyList(),
        includeDetail: Boolean = true,
        detailedArabic: String = "شرح مفصل للموضوع $topicId يغطي الجوانب الأساسية والتطبيقات.",
        detailedEnglish: String = "Detailed explanation for $topicId covering core aspects and applications.",
        relatedTopicIds: List<String> = emptyList(),
        includeFollowUpBranches: Boolean = true
    ): String {
        val keywordsJson = keywords.joinToString(",") { "\"$it\"" }
        val refIdsJson = referenceTopicIds.joinToString(",") { "\"$it\"" }
        val relatedIdsJson = relatedTopicIds.joinToString(",") { "\"$it\"" }

        val refBlock = if (referenceTopicIds.isNotEmpty()) {
            """,
              "referenceTopicIds": [$refIdsJson]"""
        } else {
            ""
        }

        val detailBlock = if (includeDetail) {
            """,
              "detail": {
                "detailedArabic": "$detailedArabic",
                "detailedEnglish": "$detailedEnglish",
                "relatedTopicIds": [$relatedIdsJson],
                "examples": [
                  "مثال تطبيقي أول حول $arabicTitle",
                  "Second practical example for $englishTitle"
                ],
                "lastReviewed": 1710000005000
              }"""
        } else {
            ""
        }

        val branchesBlock = if (includeFollowUpBranches) {
            """,
              "followUpBranches": [
                {
                  "branchId": "branch_specific_$topicId",
                  "priorityOrder": 1,
                  "matchMode": "SUBSTRING_CONTAINS",
                  "triggerKeywords": ["تطبيقات $topicId", "applications of $topicId"],
                  "responseArabic": "إجابة متابعة متخصصة حول $topicId.",
                  "responseEnglish": "Specialized follow-up response about $topicId.",
                  "actionableOptions": [
                    {
                      "labelArabic": "تطبيقات إضافية",
                      "labelEnglish": "More applications",
                      "queryText": "ما تطبيقات $topicId؟"
                    }
                  ],
                  "isDefault": false
                }
              ]"""
        } else {
            ""
        }

        return """
        {
          "topicId": "$topicId",
          "contentVersion": $contentVersion,
          "priority": $priority,
          "topicOrderIndex": $topicOrderIndex,
          "arabicTitle": "$arabicTitle",
          "englishTitle": "$englishTitle",
          "arabicSummary": "$arabicSummary",
          "englishSummary": "$englishSummary",
          "keywords": [$keywordsJson],
          "suggestedFollowUps": [
            "ما تطبيقات $topicId؟",
            "How does $topicId work?"
          ]$refBlock$detailBlock$branchesBlock
        }
        """.trimIndent()
    }

    // =========================================================================
    // Test 1: previewIdentifiesNewlyAddedTopics
    // =========================================================================
    @Test
    fun test01_previewIdentifiesNewlyAddedTopics() = runBlocking {
        val topicJson = buildTopicJsonNode(topicId = "physics_optics", contentVersion = 1)
        val packJson = buildPackJsonWithTopics(packId = "pack_optics_new", packPriority = 120, topicsJson = topicJson)

        val preview = previewService.previewPackImport(packJson)

        assertEquals(PackOperationType.IMPORT_OR_UPDATE, preview.operationType)
        assertEquals(ImpactPreviewStatus.SAFE_TO_APPLY, preview.previewStatus)
        assertTrue(preview.canApply)
        assertEquals(1, preview.summary.addedTopicCount)
        assertEquals(0, preview.summary.updatedTopicCount)
        assertEquals(0, preview.summary.removedTopicCount)

        val change = preview.topicChanges.find { it.topicId == "physics_optics" }
        assertNotNull(change)
        assertEquals(TopicChangeType.ADDED, change!!.changeType)
        assertNull(change.previousContentVersion)
        assertEquals(1, change.incomingContentVersion)
        assertEquals("pack_optics_new", change.predictedWinnerPackId)
    }

    // =========================================================================
    // Test 2: previewIdentifiesUpdatedTopics
    // =========================================================================
    @Test
    fun test02_previewIdentifiesUpdatedTopics() = runBlocking {
        // Install v1 first
        val topicV1 = buildTopicJsonNode(topicId = "astronomy_mars", contentVersion = 1)
        val packV1 = buildPackJsonWithTopics(packId = "pack_mars", packVersion = 1, topicsJson = topicV1)
        val importResult = repository.importKnowledgePackFromJsonString(packV1)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importResult.status)

        // Preview v2
        val topicV2 = buildTopicJsonNode(
            topicId = "astronomy_mars",
            contentVersion = 2,
            arabicSummary = "ملخص محدث لكوكب المريخ في الإصدار الثاني."
        )
        val packV2 = buildPackJsonWithTopics(packId = "pack_mars", packVersion = 2, topicsJson = topicV2)

        val preview = previewService.previewPackImport(packV2)

        assertTrue(preview.canApply)
        assertEquals(0, preview.summary.addedTopicCount)
        assertEquals(1, preview.summary.updatedTopicCount)
        assertEquals(0, preview.summary.removedTopicCount)

        val change = preview.topicChanges.find { it.topicId == "astronomy_mars" }
        assertNotNull(change)
        assertEquals(TopicChangeType.UPDATED, change!!.changeType)
        assertEquals(1, change.previousContentVersion)
        assertEquals(2, change.incomingContentVersion)
    }

    // =========================================================================
    // Test 3: previewIdentifiesRemovedTopics
    // =========================================================================
    @Test
    fun test03_previewIdentifiesRemovedTopics() = runBlocking {
        // Install pack with 2 topics: mars and jupiter
        val mars = buildTopicJsonNode(topicId = "planet_mars", contentVersion = 1)
        val jupiter = buildTopicJsonNode(topicId = "planet_jupiter", contentVersion = 1)
        val packInitial = buildPackJsonWithTopics(packId = "pack_planets", topicsJson = "$mars, $jupiter")
        val importResult = repository.importKnowledgePackFromJsonString(packInitial)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importResult.status)

        // Preview update containing only mars (jupiter is omitted -> removed)
        val marsV2 = buildTopicJsonNode(topicId = "planet_mars", contentVersion = 2)
        val packUpdate = buildPackJsonWithTopics(packId = "pack_planets", packVersion = 2, topicsJson = marsV2)

        val preview = previewService.previewPackImport(packUpdate)

        assertTrue(preview.canApply)
        assertEquals(1, preview.summary.updatedTopicCount)
        assertEquals(1, preview.summary.removedTopicCount)

        val removedChange = preview.topicChanges.find { it.topicId == "planet_jupiter" }
        assertNotNull(removedChange)
        assertEquals(TopicChangeType.REMOVED, removedChange!!.changeType)
        assertEquals(1, removedChange.previousContentVersion)
        assertNull(removedChange.incomingContentVersion)
    }

    // =========================================================================
    // Test 4: previewIdentifiesUnchangedTopics
    // =========================================================================
    @Test
    fun test04_previewIdentifiesUnchangedTopics() = runBlocking {
        val topic = buildTopicJsonNode(topicId = "physics_thermo", contentVersion = 1)
        val pack = buildPackJsonWithTopics(packId = "pack_thermo", packVersion = 1, topicsJson = topic)
        val importResult = repository.importKnowledgePackFromJsonString(pack)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importResult.status)

        // Preview with identical content
        val preview = previewService.previewPackImport(pack)

        assertTrue(preview.canApply)
        assertEquals(1, preview.summary.unchangedTopicCount)
        assertEquals(0, preview.summary.addedTopicCount)
        assertEquals(0, preview.summary.updatedTopicCount)

        val change = preview.topicChanges.find { it.topicId == "physics_thermo" }
        assertNotNull(change)
        assertEquals(TopicChangeType.UNCHANGED, change!!.changeType)
    }

    // =========================================================================
    // Test 5: previewPredictsWinnerChange
    // =========================================================================
    @Test
    fun test05_previewPredictsWinnerChange() = runBlocking {
        // Install pack A with priority 80
        val topicA = buildTopicJsonNode(topicId = "chemistry_water", priority = 80)
        val packA = buildPackJsonWithTopics(packId = "pack_chem_a", packPriority = 80, topicsJson = topicA)
        repository.importKnowledgePackFromJsonString(packA)

        // Preview pack B with priority 150 (should displace pack A)
        val topicB = buildTopicJsonNode(topicId = "chemistry_water", priority = 150)
        val packB = buildPackJsonWithTopics(packId = "pack_chem_b", packPriority = 150, topicsJson = topicB)

        val preview = previewService.previewPackImport(packB)

        assertEquals(1, preview.summary.winnerChangedCount)
        val change = preview.topicChanges.find { it.topicId == "chemistry_water" }
        assertNotNull(change)
        assertTrue(change!!.winnerWillChange)
        assertEquals("pack_chem_a", change.previousWinnerPackId)
        assertEquals("pack_chem_b", change.predictedWinnerPackId)
    }

    // =========================================================================
    // Test 6: previewPredictsNoWinnerChangeWhenIncomingPackIsShadowed
    // =========================================================================
    @Test
    fun test06_previewPredictsNoWinnerChangeWhenIncomingPackIsShadowed() = runBlocking {
        // Install high priority pack A (priority 150)
        val topicA = buildTopicJsonNode(topicId = "geology_volcano", priority = 150)
        val packA = buildPackJsonWithTopics(packId = "pack_geo_high", packPriority = 150, topicsJson = topicA)
        repository.importKnowledgePackFromJsonString(packA)

        // Preview incoming pack B with lower priority (50) -> should be shadowed
        val topicB = buildTopicJsonNode(topicId = "geology_volcano", priority = 50)
        val packB = buildPackJsonWithTopics(packId = "pack_geo_low", packPriority = 50, topicsJson = topicB)

        val preview = previewService.previewPackImport(packB)

        assertEquals(0, preview.summary.winnerChangedCount)
        val change = preview.topicChanges.find { it.topicId == "geology_volcano" }
        assertNotNull(change)
        assertFalse(change!!.winnerWillChange)
        assertEquals("pack_geo_high", change.previousWinnerPackId)
        assertEquals("pack_geo_high", change.predictedWinnerPackId)
    }

    // =========================================================================
    // Test 7: previewDetectsCoreTopicConflict
    // =========================================================================
    @Test
    fun test07_previewDetectsCoreTopicConflict() = runBlocking {
        // Attempt to import a non-seed pack defining core topic "ai_concept"
        val coreConflictTopic = buildTopicJsonNode(
            topicId = "ai_concept",
            arabicSummary = "محاولة استبدال غير مصرح بها للموضوع الأساسي."
        )
        val maliciousPack = buildPackJsonWithTopics(
            packId = "pack_malicious",
            topicsJson = coreConflictTopic
        )

        val preview = previewService.previewPackImport(maliciousPack)

        assertEquals(ImpactPreviewStatus.BLOCKED_BY_CORE_PROTECTION, preview.previewStatus)
        assertFalse(preview.canApply)
        assertTrue(preview.validationIssues.any { it.message.contains("ai_concept") || it.field.contains("coreTopic") })
    }

    // =========================================================================
    // Test 8: previewDetectsInvalidIncomingReference
    // =========================================================================
    @Test
    fun test08_previewDetectsInvalidIncomingReference() = runBlocking {
        val topicWithDanglingRef = buildTopicJsonNode(
            topicId = "quantum_computing",
            referenceTopicIds = listOf("non_existent_foreign_topic_xyz")
        )
        val pack = buildPackJsonWithTopics(packId = "pack_quantum", topicsJson = topicWithDanglingRef)

        val preview = previewService.previewPackImport(pack)

        assertEquals(ImpactPreviewStatus.BLOCKED_BY_REFERENCE_INTEGRITY, preview.previewStatus)
        assertFalse(preview.canApply)
        assertTrue(preview.referenceImpacts.any { it.impactType == ReferenceImpactType.NONEXISTENT_TARGET })
    }

    // =========================================================================
    // Test 9: disablePreviewIdentifiesFallbackWinners
    // =========================================================================
    @Test
    fun test09_disablePreviewIdentifiesFallbackWinners() = runBlocking {
        // Install pack 1 (priority 80)
        val topic1 = buildTopicJsonNode(topicId = "math_calculus", priority = 80)
        val pack1 = buildPackJsonWithTopics(packId = "pack_math_low", packPriority = 80, topicsJson = topic1)
        repository.importKnowledgePackFromJsonString(pack1)

        // Install pack 2 (priority 120 - currently winning)
        val topic2 = buildTopicJsonNode(topicId = "math_calculus", priority = 120)
        val pack2 = buildPackJsonWithTopics(packId = "pack_math_high", packPriority = 120, topicsJson = topic2)
        repository.importKnowledgePackFromJsonString(pack2)

        // Preview deactivating pack_math_high
        val preview = previewService.previewPackDeactivation("pack_math_high")

        assertEquals(PackOperationType.DEACTIVATE, preview.operationType)
        assertTrue(preview.canApply)

        val change = preview.topicChanges.find { it.topicId == "math_calculus" }
        assertNotNull(change)
        assertEquals("pack_math_high", change!!.previousWinnerPackId)
        assertEquals("pack_math_low", change.predictedWinnerPackId)
    }

    // =========================================================================
    // Test 10: disablePreviewIdentifiesTopicsWithNoRemainingWinner
    // =========================================================================
    @Test
    fun test10_disablePreviewIdentifiesTopicsWithNoRemainingWinner() = runBlocking {
        // Install unique pack
        val topic = buildTopicJsonNode(topicId = "biology_genetics", priority = 90)
        val pack = buildPackJsonWithTopics(packId = "pack_genetics", packPriority = 90, topicsJson = topic)
        repository.importKnowledgePackFromJsonString(pack)

        // Preview deactivation (no fallback exists)
        val preview = previewService.previewPackDeactivation("pack_genetics")

        assertTrue(preview.canApply)
        val change = preview.topicChanges.find { it.topicId == "biology_genetics" }
        assertNotNull(change)
        assertEquals("pack_genetics", change!!.previousWinnerPackId)
        assertNull(change.predictedWinnerPackId)
        assertTrue(change.winnerWillChange)
    }

    // =========================================================================
    // Test 11: disablePreviewDetectsBrokenIncomingReferences
    // =========================================================================
    @Test
    fun test11_disablePreviewDetectsBrokenIncomingReferences() = runBlocking {
        // Pack A provides "astronomy_sun"
        val sunTopic = buildTopicJsonNode(topicId = "astronomy_sun")
        val packA = buildPackJsonWithTopics(packId = "pack_sun", topicsJson = sunTopic)
        repository.importKnowledgePackFromJsonString(packA)

        // Pack B provides "astronomy_earth" referencing "astronomy_sun"
        val earthTopic = buildTopicJsonNode(topicId = "astronomy_earth", referenceTopicIds = listOf("astronomy_sun"))
        val packB = buildPackJsonWithTopics(packId = "pack_earth", topicsJson = earthTopic)
        repository.importKnowledgePackFromJsonString(packB)

        // Preview deactivating pack_sun -> earth's reference will break!
        val preview = previewService.previewPackDeactivation("pack_sun")

        assertEquals(ImpactPreviewStatus.BLOCKED_BY_REFERENCE_INTEGRITY, preview.previewStatus)
        assertFalse(preview.canApply)
        assertTrue(preview.referenceImpacts.any {
            it.impactType == ReferenceImpactType.TARGET_REMOVED_NO_FALLBACK && it.isBlocking
        })
    }

    // =========================================================================
    // Test 12: reactivationPreviewPredictsWinnerDisplacementCorrectly
    // =========================================================================
    @Test
    fun test12_reactivationPreviewPredictsWinnerDisplacementCorrectly() = runBlocking {
        // Install pack A (priority 80) and pack B (priority 120)
        val topicA = buildTopicJsonNode(topicId = "tech_robotics", priority = 80)
        val packA = buildPackJsonWithTopics(packId = "pack_robot_a", packPriority = 80, topicsJson = topicA)
        repository.importKnowledgePackFromJsonString(packA)

        val topicB = buildTopicJsonNode(topicId = "tech_robotics", priority = 120)
        val packB = buildPackJsonWithTopics(packId = "pack_robot_b", packPriority = 120, topicsJson = topicB)
        repository.importKnowledgePackFromJsonString(packB)

        // Deactivate pack B so pack A wins
        repository.deactivatePackAndRefreshSnapshot("pack_robot_b")

        // Preview reactivation of pack B (should displace pack A)
        val preview = previewService.previewPackReactivation("pack_robot_b")

        assertEquals(PackOperationType.REACTIVATE, preview.operationType)
        assertTrue(preview.canApply)

        val change = preview.topicChanges.find { it.topicId == "tech_robotics" }
        assertNotNull(change)
        assertEquals(TopicChangeType.REACTIVATED, change!!.changeType)
        assertEquals("pack_robot_a", change.previousWinnerPackId)
        assertEquals("pack_robot_b", change.predictedWinnerPackId)
        assertTrue(change.winnerWillChange)
    }

    // =========================================================================
    // Test 13: priorityPreviewPredictsAllAffectedWinners
    // =========================================================================
    @Test
    fun test13_priorityPreviewPredictsAllAffectedWinners() = runBlocking {
        // Pack A priority 100 on topic "energy_solar"
        val topicA = buildTopicJsonNode(topicId = "energy_solar", priority = 100)
        val packA = buildPackJsonWithTopics(packId = "pack_energy_a", packPriority = 100, topicsJson = topicA)
        repository.importKnowledgePackFromJsonString(packA)

        // Pack B priority 70 on topic "energy_solar"
        val topicB = buildTopicJsonNode(topicId = "energy_solar", priority = 70)
        val packB = buildPackJsonWithTopics(packId = "pack_energy_b", packPriority = 70, topicsJson = topicB)
        repository.importKnowledgePackFromJsonString(packB)

        // Preview raising pack B's priority to 150
        val preview = previewService.previewPackPriorityChange("pack_energy_b", newPriority = 150)

        assertEquals(PackOperationType.UPDATE_PRIORITY, preview.operationType)
        assertTrue(preview.canApply)

        val change = preview.topicChanges.find { it.topicId == "energy_solar" }
        assertNotNull(change)
        assertEquals("pack_energy_a", change!!.previousWinnerPackId)
        assertEquals("pack_energy_b", change.predictedWinnerPackId)
        assertTrue(change.winnerWillChange)
    }

    // =========================================================================
    // Test 14: deletionPreviewIdentifiesCascadeDependentRecords
    // =========================================================================
    @Test
    fun test14_deletionPreviewIdentifiesCascadeDependentRecords() = runBlocking {
        val topic = buildTopicJsonNode(
            topicId = "history_rome",
            keywords = listOf("روما القديمة", "امبراطورية روما", "ancient rome"),
            includeDetail = true,
            includeFollowUpBranches = true
        )
        val pack = buildPackJsonWithTopics(packId = "pack_rome", topicsJson = topic)
        repository.importKnowledgePackFromJsonString(pack)

        val preview = previewService.previewPackDeletion("pack_rome")

        assertEquals(PackOperationType.DELETE, preview.operationType)
        assertTrue(preview.canApply)
        assertTrue(preview.summary.cascadeKeywordsCount > 0)
        assertTrue(preview.summary.cascadeTokensCount > 0)
        assertEquals(1, preview.summary.cascadeDetailsCount)
        assertEquals(1, preview.summary.cascadeFollowUpsCount)
    }

    // =========================================================================
    // Test 15: deletionPreviewDetectsReferencesThatWouldBecomeInvalid
    // =========================================================================
    @Test
    fun test15_deletionPreviewDetectsReferencesThatWouldBecomeInvalid() = runBlocking {
        // Pack 1: "topic_base"
        val baseTopic = buildTopicJsonNode(topicId = "topic_base")
        val pack1 = buildPackJsonWithTopics(packId = "pack_base", topicsJson = baseTopic)
        repository.importKnowledgePackFromJsonString(pack1)

        // Pack 2: "topic_dependent" references "topic_base"
        val depTopic = buildTopicJsonNode(topicId = "topic_dependent", referenceTopicIds = listOf("topic_base"))
        val pack2 = buildPackJsonWithTopics(packId = "pack_dep", topicsJson = depTopic)
        repository.importKnowledgePackFromJsonString(pack2)

        // Preview deleting pack_base
        val preview = previewService.previewPackDeletion("pack_base")

        assertEquals(ImpactPreviewStatus.BLOCKED_BY_REFERENCE_INTEGRITY, preview.previewStatus)
        assertFalse(preview.canApply)
        assertTrue(preview.referenceImpacts.any {
            it.impactType == ReferenceImpactType.TARGET_REMOVED_NO_FALLBACK && it.isBlocking
        })
    }

    // =========================================================================
    // Test 16: deletionPreviewCorrectlyHandlesAvailableFallbackVersions
    // =========================================================================
    @Test
    fun test16_deletionPreviewCorrectlyHandlesAvailableFallbackVersions() = runBlocking {
        // Pack 1 provides fallback version of "physics_atom" (priority 60)
        val topic1 = buildTopicJsonNode(topicId = "physics_atom", priority = 60)
        val pack1 = buildPackJsonWithTopics(packId = "pack_atom_fallback", packPriority = 60, topicsJson = topic1)
        repository.importKnowledgePackFromJsonString(pack1)

        // Pack 2 provides winner version of "physics_atom" (priority 120)
        val topic2 = buildTopicJsonNode(topicId = "physics_atom", priority = 120)
        val pack2 = buildPackJsonWithTopics(packId = "pack_atom_winner", packPriority = 120, topicsJson = topic2)
        repository.importKnowledgePackFromJsonString(pack2)

        // Pack 3 references "physics_atom"
        val topic3 = buildTopicJsonNode(topicId = "physics_nuclear", referenceTopicIds = listOf("physics_atom"))
        val pack3 = buildPackJsonWithTopics(packId = "pack_nuclear", topicsJson = topic3)
        repository.importKnowledgePackFromJsonString(pack3)

        // Preview deleting pack_atom_winner -> fallback exists, so deletion is safe!
        val preview = previewService.previewPackDeletion("pack_atom_winner")

        assertTrue(preview.canApply)
        assertTrue(preview.referenceImpacts.any {
            it.impactType == ReferenceImpactType.TARGET_WINNER_CHANGED && !it.isBlocking
        })
    }

    // =========================================================================
    // Test 17: previewResultsMatchActualMutationValidationOutcomes
    // =========================================================================
    @Test
    fun test17_previewResultsMatchActualMutationValidationOutcomes() = runBlocking {
        // Safe preview
        val safeTopic = buildTopicJsonNode(topicId = "math_algebra")
        val safePack = buildPackJsonWithTopics(packId = "pack_safe", topicsJson = safeTopic)
        val safePreview = previewService.previewPackImport(safePack)
        assertTrue(safePreview.canApply)

        val actualImport = repository.importKnowledgePackFromJsonString(safePack)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, actualImport.status)

        // Malicious preview (core topic violation)
        val coreConflict = buildTopicJsonNode(topicId = "ai_concept")
        val badPack = buildPackJsonWithTopics(packId = "pack_bad", topicsJson = coreConflict)
        val badPreview = previewService.previewPackImport(badPack)
        assertFalse(badPreview.canApply)

        val actualBadImport = repository.importKnowledgePackFromJsonString(badPack)
        assertEquals(KnowledgePackImportStatus.CORE_PROTECTION_VIOLATION, actualBadImport.status)
    }

    // =========================================================================
    // Test 18: previewDoesNotChangeAnyPersistedDatabaseRecords
    // =========================================================================
    @Test
    fun test18_previewDoesNotChangeAnyPersistedDatabaseRecords() = runBlocking {
        val packCountBefore = dao.getAllPackMetadataOrdered().size
        val headerCountBefore = dao.getAllActiveTopicHeadersOrdered().size

        val topic = buildTopicJsonNode(topicId = "geology_rocks")
        val pack = buildPackJsonWithTopics(packId = "pack_rocks", topicsJson = topic)

        previewService.previewPackImport(pack)

        val packCountAfter = dao.getAllPackMetadataOrdered().size
        val headerCountAfter = dao.getAllActiveTopicHeadersOrdered().size

        assertEquals(packCountBefore, packCountAfter)
        assertEquals(headerCountBefore, headerCountAfter)
        assertNull(dao.getPackMetadataById("pack_rocks"))
    }

    // =========================================================================
    // Test 19: previewDoesNotAlterActiveWinnerFlags
    // =========================================================================
    @Test
    fun test19_previewDoesNotAlterActiveWinnerFlags() = runBlocking {
        val activeHeadersBefore = dao.getAllActiveTopicHeadersOrdered()
        assertTrue(activeHeadersBefore.isNotEmpty())

        val topicHigherPriority = buildTopicJsonNode(topicId = "ai_concept", priority = 999)
        val pack = buildPackJsonWithTopics(packId = "pack_high_override", packPriority = 999, topicsJson = topicHigherPriority)

        previewService.previewPackImport(pack)

        val activeHeadersAfter = dao.getAllActiveTopicHeadersOrdered()
        assertEquals(activeHeadersBefore.size, activeHeadersAfter.size)
        assertEquals(
            activeHeadersBefore.map { it.topicId to it.packId },
            activeHeadersAfter.map { it.topicId to it.packId }
        )
    }

    // =========================================================================
    // Test 20: previewDoesNotAdvanceCommittedGenerationOrPublishedGeneration
    // =========================================================================
    @Test
    fun test20_previewDoesNotAdvanceCommittedGenerationOrPublishedGeneration() = runBlocking {
        val committedBefore = repository.committedGeneration
        val publishedBefore = repository.publishedGeneration

        val topic = buildTopicJsonNode(topicId = "space_stars")
        val pack = buildPackJsonWithTopics(packId = "pack_stars", topicsJson = topic)

        previewService.previewPackImport(pack)

        assertEquals(committedBefore, repository.committedGeneration)
        assertEquals(publishedBefore, repository.publishedGeneration)
    }

    // =========================================================================
    // Test 21: previewDoesNotModifyCachesOrSnapshots
    // =========================================================================
    @Test
    fun test21_previewDoesNotModifyCachesOrSnapshots() = runBlocking {
        val snapshotBefore = repository.currentSnapshot

        val topic = buildTopicJsonNode(topicId = "botany_plants")
        val pack = buildPackJsonWithTopics(packId = "pack_plants", topicsJson = topic)

        previewService.previewPackImport(pack)

        val snapshotAfter = repository.currentSnapshot
        assertTrue(snapshotBefore === snapshotAfter)
        assertEquals(snapshotBefore.knowledgeGeneration, snapshotAfter.knowledgeGeneration)
    }

    // =========================================================================
    // Test 22: previewDetectsStaleGenerationConditions
    // =========================================================================
    @Test
    fun test22_previewDetectsStaleGenerationConditions() = runBlocking {
        val topic = buildTopicJsonNode(topicId = "ocean_currents")
        val pack = buildPackJsonWithTopics(packId = "pack_ocean", topicsJson = topic)

        // Inject hook to simulate committedGeneration advancing during preview analysis
        previewService.staleGenerationSimulator = {
            repository.committedGeneration + 1L
        }

        val preview = previewService.previewPackImport(pack)

        assertEquals(ImpactPreviewStatus.STALE_PREVIEW, preview.previewStatus)
        assertFalse(preview.canApply)
        assertTrue(preview.validationIssues.any { it.message.contains("Database generation advanced") })
    }

    // =========================================================================
    // Test 23: previewHandlesConcurrentMutationsSafely
    // =========================================================================
    @Test
    fun test23_previewHandlesConcurrentMutationsSafely() = runBlocking {
        // Run concurrent previews and mutations across coroutines
        val jobs = (1..6).map { idx ->
            async(Dispatchers.Default) {
                if (idx % 2 == 0) {
                    val topic = buildTopicJsonNode(topicId = "concurrent_topic_$idx")
                    val pack = buildPackJsonWithTopics(packId = "pack_conc_$idx", topicsJson = topic)
                    repository.previewPackImport(pack)
                } else {
                    val topic = buildTopicJsonNode(topicId = "imported_topic_$idx")
                    val pack = buildPackJsonWithTopics(packId = "pack_imp_$idx", topicsJson = topic)
                    repository.importKnowledgePackFromJsonString(pack)
                }
            }
        }
        val results = jobs.awaitAll()
        assertEquals(6, results.size)
    }

    // =========================================================================
    // Test 24: previewNeverReportsSafeToApplyWhenAnalysisIsIncomplete
    // =========================================================================
    @Test
    fun test24_previewNeverReportsSafeToApplyWhenAnalysisIsIncomplete() = runBlocking {
        // Malformed JSON (syntax error)
        val malformedJson = "{ invalid json payload"
        val previewMalformed = previewService.previewPackImport(malformedJson)

        assertEquals(ImpactPreviewStatus.BLOCKED_BY_VALIDATION, previewMalformed.previewStatus)
        assertFalse(previewMalformed.canApply)

        // Non-existent pack deletion
        val previewMissing = previewService.previewPackDeletion("non_existent_pack_999")
        assertEquals(ImpactPreviewStatus.ANALYSIS_UNAVAILABLE, previewMissing.previewStatus)
        assertFalse(previewMissing.canApply)
    }
}
