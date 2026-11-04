package com.example.core.intelligence.knowledge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.dao.KnowledgeDao
import com.example.core.intelligence.knowledge.importer.KnowledgePackImportStatus
import com.example.core.intelligence.knowledge.importer.KnowledgePackImporter
import com.example.core.intelligence.knowledge.importer.SnapshotRefreshState
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.repository.StaleSnapshotPublicationException
import com.example.core.intelligence.knowledge.repository.TopicDetailLoadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 2B.4.2C.2 — Importer / Repository Synchronization & Post-Commit Snapshot Recovery
 *
 * Verifies all 20 required architectural synchronization, transaction commit, post-commit
 * snapshot recovery, concurrency, monotonic generation, and targeted cache invalidation invariants:
 * - Transaction & Commit Tests (1..5)
 * - Snapshot Recovery Tests (6..10)
 * - Concurrency Tests (11..15)
 * - Cache Consistency Tests (16..20)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2B4C22ImporterRepositorySyncAndRecoveryTest {

    private lateinit var context: Context
    private lateinit var database: WeykyKnowledgeDatabase
    private lateinit var dao: KnowledgeDao
    private lateinit var repository: KnowledgeRepository
    private lateinit var importer: KnowledgePackImporter

    @Before
    fun setUp() = runBlocking {
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
    }

    @After
    fun tearDown() {
        repository.snapshotPublicationHook = null
        repository.retrievalFailureSimulator = null
        database.close()
        KnowledgeRepository.clearInstanceForTesting()
    }

    private fun buildPackJson(
        packId: String = "sync_test_pack",
        packVersion: Int = 1,
        contentVersion: Int = 1,
        packPriority: Int = 120,
        topicId: String = "quantum_optics_sync",
        arabicTitle: String = "البصريات الكمومية المتزامنة",
        englishTitle: String = "Synchronized Quantum Optics",
        arabicSummary: String = "ملخص البصريات الكمومية المتزامنة لاختبار التزامن بين المستورد والمستودع.",
        englishSummary: String = "Summary of synchronized quantum optics for testing importer and repository sync.",
        arabicDetailedBody: String = "تفصيل كامل عن البصريات الكمومية المتزامنة وتطبيقات الفوتونات المنفردة في الأنظمة الكمومية.",
        englishDetailedBody: String = "Comprehensive detail about synchronized quantum optics and single-photon applications in quantum systems.",
        primaryArKeyword: String = "البصريات الكمومية المتزامنة",
        primaryEnKeyword: String = "synchronized quantum optics",
        followUpQuestionAr: String = "كيف تعمل الفوتونات المنفردة؟",
        followUpQuestionEn: String = "How do single photons work?",
        followUpAnswerAr: String = "تعمل الفوتونات المنفردة كحاملات للمعلومات الكمومية عبر حالات الاستقطاب.",
        followUpAnswerEn: String = "Single photons act as quantum information carriers via polarization states.",
        followUpTriggerAr: String = "الفوتونات المنفردة",
        followUpTriggerEn: String = "single photons"
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
          "description": "Synchronization test pack for $packId",
          "language": "bilingual",
          "createdAt": 1710000000000,
          "updatedAt": 1710000005000,
          "topics": [
            {
              "topicId": "$topicId",
              "title": "$englishTitle",
              "arabicTitle": "$arabicTitle",
              "englishTitle": "$englishTitle",
              "category": "science",
              "topicOrderIndex": 200,
              "priority": $packPriority,
              "contentVersion": $contentVersion,
              "arabicSummary": "$arabicSummary",
              "englishSummary": "$englishSummary",
              "keywords": [
                "$primaryArKeyword",
                "$primaryEnKeyword",
                "استقطاب الفوتونات الكمومية $packId"
              ],
              "suggestedFollowUps": [
                "$followUpQuestionAr",
                "$followUpQuestionEn"
              ],
              "referenceTopicIds": ["ai_concept"],
              "detail": {
                "detailedArabic": "$arabicDetailedBody",
                "detailedEnglish": "$englishDetailedBody",
                "relatedTopicIds": ["ai_concept"],
                "examples": [
                  "مثال تطبيقي أول حول $arabicTitle",
                  "Second practical example for $englishTitle"
                ],
                "lastReviewed": 1710000005000
              },
              "followUpBranches": [
                {
                  "branchId": "branch_specific_$topicId",
                  "priorityOrder": 1,
                  "matchMode": "SUBSTRING_CONTAINS",
                  "triggerKeywords": ["$followUpTriggerAr", "$followUpTriggerEn"],
                  "responseArabic": "$followUpAnswerAr",
                  "responseEnglish": "$followUpAnswerEn",
                  "actionableOptions": [
                    {
                      "labelArabic": "تطبيقات إضافية",
                      "labelEnglish": "More applications",
                      "queryText": "$followUpQuestionAr"
                    }
                  ],
                  "isDefault": false
                }
              ]
            }
          ]
        }
        """.trimIndent()
    }

    // =========================================================================
    // Transaction & Commit Tests (1..5)
    // =========================================================================

    @Test
    fun test01_failedImportLeavesDatabaseUnchanged() = runBlocking {
        val packsBefore = dao.getAllPackMetadataOrdered()
        val activeHeadersBefore = dao.getAllActiveTopicHeadersOrdered()
        val snapshotBefore = repository.currentSnapshot

        val result = importer.importFromJsonString(
            json = buildPackJson(packId = "tx_rollback_pack", topicId = "tx_rollback_topic"),
            simulatedMidTransactionFailure = {
                throw IllegalStateException("Simulated mid-transaction failure")
            }
        )

        assertEquals(KnowledgePackImportStatus.STORAGE_FAILURE, result.status)
        assertFalse(result.databaseCommitted)
        assertEquals(SnapshotRefreshState.NOT_MODIFIED, result.snapshotRefreshState)
        assertNull(dao.getPackMetadataById("tx_rollback_pack"))
        assertEquals(packsBefore, dao.getAllPackMetadataOrdered())
        assertEquals(activeHeadersBefore, dao.getAllActiveTopicHeadersOrdered())
        assertEquals(snapshotBefore, repository.currentSnapshot)
    }

    @Test
    fun test02_successfulImportUpdatesDurableDatabase() = runBlocking {
        val json = buildPackJson(
            packId = "durable_commit_pack",
            packVersion = 2,
            contentVersion = 3,
            topicId = "durable_commit_topic"
        )
        val result = importer.importFromJsonString(json)

        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, result.status)
        assertTrue(result.databaseCommitted)
        assertEquals(SnapshotRefreshState.REFRESHED_ATOMICALLY, result.snapshotRefreshState)

        val storedPack = dao.getPackMetadataById("durable_commit_pack")
        assertNotNull(storedPack)
        assertEquals(2, storedPack!!.packVersion)
        assertEquals(1, storedPack.topicCount)

        val storedHeader = dao.getActiveTopicHeader("durable_commit_topic")
        assertNotNull(storedHeader)
        assertEquals("durable_commit_pack", storedHeader!!.packId)
        assertEquals(3, storedHeader.contentVersion)

        val storedDetail = dao.getTopicDetail("durable_commit_topic", "durable_commit_pack")
        assertNotNull(storedDetail)
    }

    @Test
    fun test03_duplicateImportRemainsIdempotent() = runBlocking {
        val json = buildPackJson(packId = "idempotent_sync_pack", topicId = "idempotent_sync_topic")
        val first = importer.importFromJsonString(json)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, first.status)
        assertTrue(first.databaseCommitted)

        val committedAfterFirst = repository.committedGeneration
        val publishedAfterFirst = repository.publishedGeneration
        val snapshotAfterFirst = repository.currentSnapshot

        val second = importer.importFromJsonString(json)
        assertEquals(KnowledgePackImportStatus.ALREADY_IMPORTED, second.status)
        assertTrue(second.databaseCommitted)
        assertEquals(SnapshotRefreshState.NOT_MODIFIED, second.snapshotRefreshState)
        assertEquals(0, second.importedTopicCount)
        assertEquals(1, second.skippedTopicCount)
        assertEquals(committedAfterFirst, repository.committedGeneration)
        assertEquals(publishedAfterFirst, repository.publishedGeneration)
        assertEquals(snapshotAfterFirst, repository.currentSnapshot)
    }

    @Test
    fun test04_conflictBehaviorUnchanged() = runBlocking {
        val v2Json = buildPackJson(
            packId = "conflict_policy_pack",
            packVersion = 2,
            contentVersion = 2,
            topicId = "conflict_policy_topic",
            englishSummary = "Initial v2 summary for conflict policy verification."
        )
        val initial = importer.importFromJsonString(v2Json)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, initial.status)

        val committedBeforeConflict = repository.committedGeneration
        val publishedBeforeConflict = repository.publishedGeneration

        // Same version with modified content -> DUPLICATE_CONFLICT
        val driftedSameVersionJson = buildPackJson(
            packId = "conflict_policy_pack",
            packVersion = 2,
            contentVersion = 2,
            topicId = "conflict_policy_topic",
            englishSummary = "Drifted v2 summary with different checksum."
        )
        val sameVersionConflict = importer.importFromJsonString(driftedSameVersionJson)
        assertEquals(KnowledgePackImportStatus.DUPLICATE_CONFLICT, sameVersionConflict.status)
        assertFalse(sameVersionConflict.databaseCommitted)

        // Older version -> DUPLICATE_CONFLICT
        val olderVersionJson = buildPackJson(
            packId = "conflict_policy_pack",
            packVersion = 1,
            contentVersion = 1,
            topicId = "conflict_policy_topic"
        )
        val downgradeConflict = importer.importFromJsonString(olderVersionJson)
        assertEquals(KnowledgePackImportStatus.DUPLICATE_CONFLICT, downgradeConflict.status)
        assertFalse(downgradeConflict.databaseCommitted)

        assertEquals(committedBeforeConflict, repository.committedGeneration)
        assertEquals(publishedBeforeConflict, repository.publishedGeneration)
    }

    @Test
    fun test05_failedTransactionDoesNotAdvanceCommittedGeneration() = runBlocking {
        val committedBefore = repository.committedGeneration
        val publishedBefore = repository.publishedGeneration

        val failedTxResult = importer.importFromJsonString(
            json = buildPackJson(packId = "failed_gen_pack", topicId = "failed_gen_topic"),
            simulatedMidTransactionFailure = {
                throw RuntimeException("Abort transaction before commit")
            }
        )

        assertEquals(KnowledgePackImportStatus.STORAGE_FAILURE, failedTxResult.status)
        assertFalse(failedTxResult.databaseCommitted)
        assertEquals(committedBefore, repository.committedGeneration)
        assertEquals(publishedBefore, repository.publishedGeneration)
        assertEquals(committedBefore, failedTxResult.committedGeneration)
        assertEquals(publishedBefore, failedTxResult.publishedGeneration)
        assertFalse(repository.isSnapshotStale)
    }

    // =========================================================================
    // Snapshot Recovery Tests (6..10)
    // =========================================================================

    @Test
    fun test06_successfulCommitPublishesNewSnapshot() = runBlocking {
        val genBefore = repository.publishedGeneration
        val result = importer.importFromJsonString(
            buildPackJson(
                packId = "publish_snap_pack",
                topicId = "publish_snap_topic",
                primaryEnKeyword = "photonic crystal waveguide"
            )
        )

        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, result.status)
        assertTrue(result.databaseCommitted)
        assertTrue(repository.publishedGeneration > genBefore)
        assertEquals(repository.committedGeneration, repository.publishedGeneration)
        assertFalse(repository.isSnapshotStale)
        assertNotNull(repository.getTopicById("publish_snap_topic"))

        val searchResult = repository.search("photonic crystal waveguide")
        assertTrue(searchResult.orderedCandidates.any { it.topicId == "publish_snap_topic" })
    }

    @Test
    fun test07_snapshotRebuildFailureIsRecoverable() = runBlocking {
        val genBefore = repository.publishedGeneration
        val result = importer.importFromJsonString(
            json = buildPackJson(
                packId = "auto_recover_pack",
                topicId = "auto_recover_topic",
                primaryEnKeyword = "superconducting nanowire detector"
            ),
            simulatedSnapshotBuildFailure = {
                throw IllegalStateException("Simulated transient snapshot build failure")
            }
        )

        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, result.status)
        assertEquals(SnapshotRefreshState.RECOVERED_FROM_DATABASE, result.snapshotRefreshState)
        assertTrue(result.databaseCommitted)
        assertTrue(repository.publishedGeneration > genBefore)
        assertEquals(repository.committedGeneration, repository.publishedGeneration)
        assertFalse(repository.isSnapshotStale)
        assertNotNull(repository.getTopicById("auto_recover_topic"))
    }

    @Test
    fun test08_subsequentReadsRecoverFromStaleSnapshot() = runBlocking {
        val genBefore = repository.publishedGeneration
        val doubleFailResult = importer.importFromJsonString(
            json = buildPackJson(
                packId = "lazy_read_recovery_pack",
                topicId = "lazy_read_recovery_topic",
                primaryArKeyword = "التداخل الكمومي الضوئي",
                primaryEnKeyword = "optical quantum interferometry"
            ),
            simulatedSnapshotBuildFailure = {
                throw IllegalStateException("Primary snapshot build failed")
            },
            simulatedRecoveryFailure = {
                throw IllegalStateException("Immediate recovery also failed")
            }
        )

        // Immediately after double failure: DB is committed, previous valid snapshot is retained,
        // and repository marks the snapshot as stale (committedGeneration > publishedGeneration).
        assertEquals(KnowledgePackImportStatus.SNAPSHOT_REFRESH_FAILURE, doubleFailResult.status)
        assertEquals(SnapshotRefreshState.FAILED_PREVIOUS_SNAPSHOT_RETAINED, doubleFailResult.snapshotRefreshState)
        assertTrue(doubleFailResult.databaseCommitted)
        assertTrue(repository.committedGeneration > genBefore)
        assertEquals(genBefore, repository.publishedGeneration)
        assertTrue(repository.isSnapshotStale)
        assertNull(repository.currentSnapshot.topicsById["lazy_read_recovery_topic"])

        // Now perform a standard search read — it must automatically recover the stale snapshot from Room!
        val searchResult = repository.search("optical quantum interferometry")
        assertFalse(repository.isSnapshotStale)
        assertEquals(repository.committedGeneration, repository.publishedGeneration)
        assertTrue(searchResult.orderedCandidates.any { it.topicId == "lazy_read_recovery_topic" })
        assertNotNull(repository.getTopicById("lazy_read_recovery_topic"))
    }

    @Test
    fun test09_failedOrStalePublicationCannotReplaceNewerSnapshot() = runBlocking {
        // Import pack v1 so we have a valid TopicStorageBundle set
        importer.importFromJsonString(
            buildPackJson(
                packId = "monotonic_guard_pack",
                packVersion = 1,
                contentVersion = 1,
                topicId = "monotonic_guard_topic"
            )
        )
        val v1Bundles = repository.loadActiveTopicStorageBundlesFromDao(dao)

        // Import pack v2 so publishedGeneration advances to a newer generation
        importer.importFromJsonString(
            buildPackJson(
                packId = "monotonic_guard_pack",
                packVersion = 2,
                contentVersion = 2,
                topicId = "monotonic_guard_topic",
                englishSummary = "Updated v2 summary in newer generation."
            )
        )
        val newestPublishedGen = repository.publishedGeneration
        val newestSnapshot = repository.currentSnapshot

        // Attempting to publish an older generation (e.g., stale candidate from an earlier build)
        // must throw StaleSnapshotPublicationException and leave newestSnapshot untouched.
        var rejectedAsStale = false
        try {
            repository.publishNewSnapshotFromBundles(
                bundles = v1Bundles,
                targetGenerationOverride = newestPublishedGen - 1L
            )
        } catch (_: StaleSnapshotPublicationException) {
            rejectedAsStale = true
        }

        assertTrue(rejectedAsStale)
        assertEquals(newestPublishedGen, repository.publishedGeneration)
        assertEquals(newestSnapshot, repository.currentSnapshot)
        assertEquals(
            "Updated v2 summary in newer generation.",
            repository.getTopicById("monotonic_guard_topic")?.englishSummary
        )
    }

    @Test
    fun test10_retryAfterUncertainPostCommitOutcomeDoesNotDuplicateImportAndRecoversStaleSnapshot() = runBlocking {
        val packJson = buildPackJson(
            packId = "uncertain_retry_pack",
            packVersion = 1,
            contentVersion = 1,
            topicId = "uncertain_retry_topic",
            primaryEnKeyword = "cavity optomechanics"
        )

        // 1st attempt: DB transaction commits, but both initial snapshot build and recovery fail.
        val firstAttempt = importer.importFromJsonString(
            json = packJson,
            simulatedSnapshotBuildFailure = {
                throw IllegalStateException("Initial snapshot build error")
            },
            simulatedRecoveryFailure = {
                throw IllegalStateException("Secondary recovery error")
            }
        )
        assertEquals(KnowledgePackImportStatus.SNAPSHOT_REFRESH_FAILURE, firstAttempt.status)
        assertTrue(firstAttempt.databaseCommitted)
        assertTrue(repository.isSnapshotStale)
        val committedGenAfterFirst = repository.committedGeneration

        // 2nd attempt (Caller retries importing the same pack after uncertain outcome):
        // Must detect that the pack is already committed in Room with matching SHA-256,
        // NOT duplicate DB writes, and recover the stale snapshot to match committedGeneration!
        val retryAttempt = importer.importFromJsonString(packJson)
        assertEquals(KnowledgePackImportStatus.ALREADY_IMPORTED, retryAttempt.status)
        assertEquals(SnapshotRefreshState.RECOVERED_FROM_DATABASE, retryAttempt.snapshotRefreshState)
        assertTrue(retryAttempt.databaseCommitted)
        assertEquals(0, retryAttempt.importedTopicCount)
        assertEquals(1, retryAttempt.skippedTopicCount)
        assertFalse(repository.isSnapshotStale)
        assertEquals(committedGenAfterFirst, repository.committedGeneration)
        assertEquals(committedGenAfterFirst, repository.publishedGeneration)
        assertNotNull(repository.getTopicById("uncertain_retry_topic"))
        assertEquals(1, dao.getTopicHeadersByPackOrdered("uncertain_retry_pack").size)
    }

    // =========================================================================
    // Concurrency Tests (11..15)
    // =========================================================================

    @Test
    fun test11_twoSimultaneousImportsPreserveBothCommittedChangesWhenCompatible() = runBlocking {
        val packA = buildPackJson(
            packId = "concurrent_pack_a",
            topicId = "concurrent_topic_a",
            primaryEnKeyword = "laser cooling trap"
        )
        val packB = buildPackJson(
            packId = "concurrent_pack_b",
            topicId = "concurrent_topic_b",
            primaryEnKeyword = "atomic fountain clock"
        )

        val importerA = repository.createKnowledgePackImporter()
        val importerB = repository.createKnowledgePackImporter()

        val results = listOf(
            async(Dispatchers.Default) { importerA.importFromJsonString(packA) },
            async(Dispatchers.Default) { importerB.importFromJsonString(packB) }
        ).awaitAll()

        assertTrue(results.all { it.status == KnowledgePackImportStatus.SUCCESS_IMPORTED })
        assertTrue(results.all { it.databaseCommitted })
        assertFalse(repository.isSnapshotStale)
        assertEquals(repository.committedGeneration, repository.publishedGeneration)

        assertNotNull(repository.getTopicById("concurrent_topic_a"))
        assertNotNull(repository.getTopicById("concurrent_topic_b"))
        assertTrue(repository.search("laser cooling trap").orderedCandidates.any { it.topicId == "concurrent_topic_a" })
        assertTrue(repository.search("atomic fountain clock").orderedCandidates.any { it.topicId == "concurrent_topic_b" })
    }

    @Test
    fun test12_conflictingConcurrentImportsFollowExistingConflictPolicy() = runBlocking {
        // Two concurrent imports for the same packId & version (v1) but different content
        val variant1 = buildPackJson(
            packId = "concurrent_conflict_pack",
            packVersion = 1,
            contentVersion = 1,
            topicId = "concurrent_conflict_topic",
            englishSummary = "Variant 1 summary for concurrent conflict test."
        )
        val variant2 = buildPackJson(
            packId = "concurrent_conflict_pack",
            packVersion = 1,
            contentVersion = 1,
            topicId = "concurrent_conflict_topic",
            englishSummary = "Variant 2 summary with different content hash."
        )

        val results = listOf(
            async(Dispatchers.Default) { importer.importFromJsonString(variant1) },
            async(Dispatchers.Default) { importer.importFromJsonString(variant2) }
        ).awaitAll()

        val statuses = results.map { it.status }.toSet()
        // Exactly one succeeds and the second is rejected with DUPLICATE_CONFLICT
        assertEquals(
            setOf(KnowledgePackImportStatus.SUCCESS_IMPORTED, KnowledgePackImportStatus.DUPLICATE_CONFLICT),
            statuses
        )
        val winnerResult = results.first { it.status == KnowledgePackImportStatus.SUCCESS_IMPORTED }
        val storedPack = dao.getPackMetadataById("concurrent_conflict_pack")
        assertNotNull(storedPack)
        assertEquals(winnerResult.logicalContentSha256, storedPack!!.sha256Checksum)
        val activeHeader = dao.getActiveTopicHeader("concurrent_conflict_topic")
        assertNotNull(activeHeader)
        assertEquals(
            activeHeader!!.englishSummary,
            repository.getTopicById("concurrent_conflict_topic")?.englishSummary
        )
    }

    @Test
    fun test13_repositoryInitializationAndImportCannotCorruptSnapshotState() = runBlocking {
        val packJson = buildPackJson(
            packId = "init_race_pack",
            topicId = "init_race_topic",
            primaryEnKeyword = "rydberg atom array"
        )

        val initJob = async(Dispatchers.Default) {
            repeat(5) {
                assertTrue(repository.initializeSeedAndWarmUpIndex())
            }
        }
        val importJob = async(Dispatchers.Default) {
            importer.importFromJsonString(packJson)
        }

        val importResult = importJob.await()
        initJob.await()

        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importResult.status)
        assertFalse(repository.isSnapshotStale)
        assertEquals(repository.committedGeneration, repository.publishedGeneration)

        // All 11 core seed topics plus the imported topic must be present and valid in the snapshot
        assertEquals(12, repository.currentSnapshot.topicCount)
        assertNotNull(repository.getTopicById("ai_concept"))
        assertNotNull(repository.getTopicById("init_race_topic"))
    }

    @Test
    fun test14_concurrentReadersNeverObservePartiallyBuiltSnapshot() = runBlocking {
        val keepReading = AtomicBoolean(true)
        val observedTopicCounts = ConcurrentLinkedQueue<Int>()
        val observedKeywordCounts = ConcurrentLinkedQueue<Int>()

        val readerJobs = (1..3).map {
            async(Dispatchers.Default) {
                while (keepReading.get()) {
                    val snap = repository.currentSnapshot
                    observedTopicCounts.add(snap.topicCount)
                    observedKeywordCounts.add(snap.keywordCount)
                    val ctx = repository.prepareTurnKnowledgeContext("What is artificial intelligence?")
                    assertTrue((ctx.snapshotUsed?.topicCount ?: 0) >= 11)
                    delay(1)
                }
            }
        }

        for (i in 1..4) {
            val res = importer.importFromJsonString(
                buildPackJson(
                    packId = "reader_safety_pack_$i",
                    topicId = "reader_safety_topic_$i",
                    primaryEnKeyword = "quantum repeater node $i"
                )
            )
            assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, res.status)
        }

        keepReading.set(false)
        readerJobs.awaitAll()

        assertTrue(observedTopicCounts.isNotEmpty())
        assertTrue(observedTopicCounts.all { it in 11..15 })
        assertTrue(observedKeywordCounts.all { it > 0 })
    }

    @Test
    fun test15_snapshotGenerationsNeverMoveBackwards() = runBlocking {
        val observedGenerations = ConcurrentLinkedQueue<Long>()
        val maxSeenByHook = AtomicLong(repository.publishedGeneration)

        repository.snapshotPublicationHook = { candidate ->
            val prev = maxSeenByHook.getAndUpdate { maxOf(it, candidate.knowledgeGeneration) }
            assertTrue(
                "Candidate generation ${candidate.knowledgeGeneration} must be >= previous $prev",
                candidate.knowledgeGeneration >= prev
            )
            observedGenerations.add(candidate.knowledgeGeneration)
        }

        for (v in 1..4) {
            val res = importer.importFromJsonString(
                buildPackJson(
                    packId = "monotonic_seq_pack",
                    packVersion = v,
                    contentVersion = v,
                    topicId = "monotonic_seq_topic",
                    englishSummary = "Summary version $v for monotonic sequence test."
                )
            )
            assertTrue(
                res.status == KnowledgePackImportStatus.SUCCESS_IMPORTED ||
                    res.status == KnowledgePackImportStatus.SUCCESS_UPDATED
            )
        }

        val genList = observedGenerations.toList()
        assertEquals(4, genList.size)
        for (i in 1 until genList.size) {
            assertTrue(
                "Generation at index $i (${genList[i]}) must be strictly greater than ${genList[i - 1]}",
                genList[i] > genList[i - 1]
            )
        }
    }

    // =========================================================================
    // Cache Consistency Tests (16..20)
    // =========================================================================

    @Test
    fun test16_changedTopicHeadersInvalidateAppropriateSearchData() = runBlocking {
        val v1Json = buildPackJson(
            packId = "cache_header_pack",
            packVersion = 1,
            contentVersion = 1,
            topicId = "cache_header_topic",
            englishSummary = "Version 1 header summary.",
            primaryEnKeyword = "alpha spectroscopy signal"
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(v1Json).status)

        // Warm headerCache & rawScoreCache for cache_header_topic
        val v1Topic = repository.getTopicById("cache_header_topic")
        assertNotNull(v1Topic)
        assertEquals("Version 1 header summary.", v1Topic!!.englishSummary)
        val v1Search = repository.search("alpha spectroscopy signal")
        assertTrue(v1Search.orderedCandidates.any { it.topicId == "cache_header_topic" })
        assertTrue(repository.cache.snapshotStats().rawScoreStats.entryCount > 0)

        // Update pack to v2 with new summary and new primary keyword
        val v2Json = buildPackJson(
            packId = "cache_header_pack",
            packVersion = 2,
            contentVersion = 2,
            topicId = "cache_header_topic",
            englishSummary = "Version 2 updated header summary.",
            primaryEnKeyword = "beta interferometry signal"
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_UPDATED, importer.importFromJsonString(v2Json).status)

        val v2Topic = repository.getTopicById("cache_header_topic")
        assertNotNull(v2Topic)
        assertEquals("Version 2 updated header summary.", v2Topic!!.englishSummary)

        val oldKeywordSearch = repository.search("alpha spectroscopy signal")
        assertFalse(oldKeywordSearch.orderedCandidates.any { it.topicId == "cache_header_topic" })

        val newKeywordSearch = repository.search("beta interferometry signal")
        assertTrue(newKeywordSearch.orderedCandidates.any { it.topicId == "cache_header_topic" })
    }

    @Test
    fun test17_changedTopicDetailsInvalidateAppropriateDetailCache() = runBlocking {
        val v1Json = buildPackJson(
            packId = "cache_detail_pack",
            packVersion = 1,
            contentVersion = 1,
            topicId = "cache_detail_topic",
            englishDetailedBody = "Detailed body v1 before pack update."
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(v1Json).status)

        // Warm detail cache on demand
        val detailLoadV1 = repository.getOrLoadTopicDetail("cache_detail_topic")
        assertEquals(TopicDetailLoadStatus.LOADED_FROM_DB, detailLoadV1.status)
        assertNotNull(detailLoadV1.bundle)
        assertEquals("Detailed body v1 before pack update.", detailLoadV1.bundle!!.detail.detailedEnglish)
        assertEquals(1, repository.cache.snapshotStats().detailStats.entryCount)

        // Update pack to v2 with new detailedBody
        val v2Json = buildPackJson(
            packId = "cache_detail_pack",
            packVersion = 2,
            contentVersion = 2,
            topicId = "cache_detail_topic",
            englishDetailedBody = "Detailed body v2 after pack update."
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_UPDATED, importer.importFromJsonString(v2Json).status)

        // Old detail entry for cache_detail_topic must have been invalidated during v2 publication
        assertEquals(0, repository.cache.snapshotStats().detailStats.entryCount)

        // Loading detail now returns v2
        val detailLoadV2 = repository.getOrLoadTopicDetail("cache_detail_topic")
        assertEquals(TopicDetailLoadStatus.LOADED_FROM_DB, detailLoadV2.status)
        assertNotNull(detailLoadV2.bundle)
        assertEquals("Detailed body v2 after pack update.", detailLoadV2.bundle!!.detail.detailedEnglish)
    }

    @Test
    fun test18_followUpChangesInvalidateAppropriateFollowUpCache() = runBlocking {
        val v1Json = buildPackJson(
            packId = "cache_followup_pack",
            packVersion = 1,
            contentVersion = 1,
            topicId = "cache_followup_topic",
            followUpAnswerEn = "Follow-up response v1 for single photons.",
            followUpTriggerEn = "single photons"
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(v1Json).status)

        val detailLoadV1 = repository.getOrLoadTopicDetail("cache_followup_topic")
        assertEquals(TopicDetailLoadStatus.LOADED_FROM_DB, detailLoadV1.status)
        assertNotNull(detailLoadV1.bundle)
        assertTrue(detailLoadV1.bundle!!.followUpBranches.any { it.responseEnglish == "Follow-up response v1 for single photons." })

        // Update follow-up response in v2
        val v2Json = buildPackJson(
            packId = "cache_followup_pack",
            packVersion = 2,
            contentVersion = 2,
            topicId = "cache_followup_topic",
            followUpAnswerEn = "Follow-up response v2 with entangled photon pairs.",
            followUpTriggerEn = "single photons"
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_UPDATED, importer.importFromJsonString(v2Json).status)

        val detailLoadV2 = repository.getOrLoadTopicDetail("cache_followup_topic")
        assertEquals(TopicDetailLoadStatus.LOADED_FROM_DB, detailLoadV2.status)
        assertNotNull(detailLoadV2.bundle)
        assertTrue(detailLoadV2.bundle!!.followUpBranches.any { it.responseEnglish == "Follow-up response v2 with entangled photon pairs." })
        assertFalse(detailLoadV2.bundle!!.followUpBranches.any { it.responseEnglish == "Follow-up response v1 for single photons." })
    }

    @Test
    fun test19_unrelatedCacheEntriesRemainReusableWhenSafe() = runBlocking {
        // Import a baseline pack that has a full detail record and warm headerCache + detailCache
        val baselinePack = buildPackJson(
            packId = "baseline_cached_pack",
            topicId = "baseline_cached_topic"
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(baselinePack).status)

        assertNotNull(repository.getTopicById("baseline_cached_topic"))
        val baselineDetailLoad = repository.getOrLoadTopicDetail("baseline_cached_topic")
        assertEquals(TopicDetailLoadStatus.LOADED_FROM_DB, baselineDetailLoad.status)
        val headerHitsBefore = repository.cache.snapshotStats().headerStats.hitCount
        val detailHitsBefore = repository.cache.snapshotStats().detailStats.hitCount
        assertEquals(1, repository.cache.snapshotStats().detailStats.entryCount)

        // Import an unrelated pack
        val unrelatedPack = buildPackJson(
            packId = "unrelated_pack",
            topicId = "unrelated_topic"
        )
        val importRes = importer.importFromJsonString(unrelatedPack)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importRes.status)

        // Verify 'baseline_cached_topic' remained in both headerCache and detailCache and produces cache hits!
        assertEquals(1, repository.cache.snapshotStats().detailStats.entryCount)
        assertNotNull(repository.getTopicById("baseline_cached_topic"))
        assertEquals(TopicDetailLoadStatus.CACHE_HIT, repository.getOrLoadTopicDetail("baseline_cached_topic").status)

        val headerHitsAfter = repository.cache.snapshotStats().headerStats.hitCount
        val detailHitsAfter = repository.cache.snapshotStats().detailStats.hitCount
        assertTrue(headerHitsAfter > headerHitsBefore)
        assertTrue(detailHitsAfter > detailHitsBefore)
    }

    @Test
    fun test20_failedImportsDoNotUnnecessarilyInvalidateValidCaches() = runBlocking {
        // Import a baseline pack with detail, then warm headerCache, detailCache, and rawScoreCache
        val baselinePack = buildPackJson(
            packId = "baseline_valid_cache_pack",
            topicId = "baseline_valid_cache_topic",
            primaryEnKeyword = "coherent optical lattice"
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(baselinePack).status)

        assertNotNull(repository.getTopicById("baseline_valid_cache_topic"))
        assertEquals(
            TopicDetailLoadStatus.LOADED_FROM_DB,
            repository.getOrLoadTopicDetail("baseline_valid_cache_topic").status
        )
        repository.search("coherent optical lattice")

        val statsBefore = repository.cache.snapshotStats()
        assertTrue(statsBefore.headerStats.entryCount > 0)
        assertEquals(1, statsBefore.detailStats.entryCount)
        assertTrue(statsBefore.rawScoreStats.entryCount > 0)

        // 1. Validation failure (core protection violation)
        val coreViolationRes = importer.importFromJsonString(
            buildPackJson(packId = "bad_core_override", topicId = "ai_concept")
        )
        assertEquals(KnowledgePackImportStatus.CORE_PROTECTION_VIOLATION, coreViolationRes.status)

        // 2. Mid-transaction storage failure
        val txFailureRes = importer.importFromJsonString(
            json = buildPackJson(packId = "tx_fail_cache_pack", topicId = "tx_fail_cache_topic"),
            simulatedMidTransactionFailure = {
                throw IllegalStateException("Simulated DB rollback")
            }
        )
        assertEquals(KnowledgePackImportStatus.STORAGE_FAILURE, txFailureRes.status)

        val statsAfterFailures = repository.cache.snapshotStats()
        assertEquals(statsBefore.headerStats.entryCount, statsAfterFailures.headerStats.entryCount)
        assertEquals(statsBefore.detailStats.entryCount, statsAfterFailures.detailStats.entryCount)
        assertEquals(statsBefore.rawScoreStats.entryCount, statsAfterFailures.rawScoreStats.entryCount)
    }
}
