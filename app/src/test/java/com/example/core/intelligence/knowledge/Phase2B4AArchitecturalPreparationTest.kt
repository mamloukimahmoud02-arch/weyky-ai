package com.example.core.intelligence.knowledge

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.conversation.LocalConversationalEngine
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.cache.KnowledgeByteSizeEstimator
import com.example.core.intelligence.knowledge.cache.KnowledgeCache
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
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.repository.PackMutationResult
import com.example.core.intelligence.knowledge.repository.TopicDetailLoadStatus
import com.example.core.intelligence.knowledge.search.InMemorySearchSnapshot
import com.example.core.intelligence.knowledge.search.KnowledgeSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import java.util.Collections
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2B4AArchitecturalPreparationTest {

  private lateinit var context: Context
  private lateinit var database: WeykyKnowledgeDatabase
  private lateinit var dao: KnowledgeDao
  private val converters = KnowledgeTypeConverters()
  private val recordedSqlQueries: MutableList<String> = Collections.synchronizedList(mutableListOf())

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    recordedSqlQueries.clear()
    // Strictly NO allowMainThreadQueries() — all DB operations run on background dispatchers.
    // Use direct synchronous executor so query callbacks are recorded immediately on the executing thread.
    database = Room.inMemoryDatabaseBuilder(
      context,
      WeykyKnowledgeDatabase::class.java
    ).setQueryCallback(
      { sqlQuery, _ ->
        recordedSqlQueries.add(sqlQuery.trim())
      },
      java.util.concurrent.Executor { it.run() }
    ).build()
    dao = database.knowledgeDao()
  }

  @After
  fun tearDown() {
    if (::database.isInitialized && database.isOpen) {
      database.close()
    }
    WeykyKnowledgeDatabase.clearInstanceForTesting()
    KnowledgeRepository.clearInstanceForTesting()
  }

  private fun countUserSelectQueries(queries: List<String>): List<String> {
    return queries.filter { sql ->
      val upper = sql.uppercase()
      upper.startsWith("SELECT ") &&
        !upper.contains("ROOM_MASTER_TABLE") &&
        !upper.contains("ROOM_TABLE_MODIFICATION_LOG") &&
        !upper.contains("SQLITE_MASTER") &&
        !upper.contains("PRAGMA")
    }
  }

  private fun buildSha256Hex(seed: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256")
      .digest(seed.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun buildSyntheticTopicBundle(
    topicId: String,
    packId: String,
    topicOrderIndex: Int = 100,
    packPriority: Int,
    contentVersion: Int = 1,
    sourceType: String = "BUNDLED_ASSET",
    isCoreTopic: Boolean = false,
    arabicSummary: String = "ملخص معرفي للموضوع $topicId من الحزمة $packId الإصدار $contentVersion.",
    englishSummary: String = "Knowledge summary for $topicId from pack $packId v$contentVersion.",
    keywords: List<String> = listOf("كلمه $topicId", "موضوع $topicId", "topic $topicId"),
    includeDetail: Boolean = true,
    detailedArabic: String = "تفصيل موسع للموضوع $topicId في الحزمة $packId (v$contentVersion).",
    detailedEnglish: String = "Extended detail for $topicId in pack $packId (v$contentVersion).",
    followUpTriggers: List<Pair<String, String>> = listOf(
      "تطبيقات $topicId" to "تطبيقات عملية للموضوع $topicId من الحزمة $packId الإصدار $contentVersion."
    )
  ): TopicStorageBundle {
    val header = KnowledgeTopicHeaderEntity(
      topicId = topicId,
      packId = packId,
      topicOrderIndex = topicOrderIndex,
      arabicSummary = arabicSummary,
      englishSummary = englishSummary,
      suggestedFollowUpsJson = converters.fromActionableOptionList(
        listOf(ActionableOption("تطبيقات $topicId", "ما تطبيقات $topicId؟"))
      ),
      sourceType = sourceType,
      packPriority = packPriority,
      contentVersion = contentVersion,
      isCoreTopic = isCoreTopic,
      isActiveWinner = false
    )

    val kwWithTokens = ArrayList<KeywordWithTokensBundle>()
    val seenStrict = HashSet<String>()

    keywords.forEachIndexed { _, rawKeyword ->
      val strictPhrase = LocalKnowledgeBase.normalizeArabicStrict(rawKeyword)
      val relaxedPhrase = LocalKnowledgeBase.normalizeArabic(rawKeyword)
      if (strictPhrase.isNotBlank() && seenStrict.add(strictPhrase)) {
        val strictTokens = strictPhrase.split(" ").filter { it.isNotBlank() }
        val relaxedTokens = relaxedPhrase.split(" ").filter { it.isNotBlank() }
        val tokenCount = minOf(strictTokens.size, relaxedTokens.size)
        if (tokenCount > 0) {
          val contentTokenCount = (0 until tokenCount).count { pos ->
            relaxedTokens[pos] !in SeedKnowledgeCatalog.STOP_WORDS
          }.coerceAtLeast(1)

          val hasArabic = rawKeyword.any { it in '\u0600'..'\u06FF' }
          val hasLatin = rawKeyword.any { it in 'a'..'z' || it in 'A'..'Z' }
          val lang = when {
            hasArabic && hasLatin -> "mixed"
            hasArabic -> "ar"
            else -> "en"
          }

          val kwEntity = KnowledgeKeywordEntity(
            keywordId = 0L,
            topicId = topicId,
            packId = packId,
            keywordOrder = kwWithTokens.size,
            originalKeyword = rawKeyword,
            strictNormalizedPhrase = strictPhrase,
            relaxedNormalizedPhrase = relaxedPhrase,
            relaxedCharLength = relaxedPhrase.length,
            tokenCount = tokenCount,
            contentTokenCount = contentTokenCount,
            matchType = if (tokenCount > 1) "MULTI_WORD_PHRASE" else "SINGLE_TOKEN",
            language = lang
          )

          val tokEntities = (0 until tokenCount).map { pos ->
            val sTok = strictTokens[pos]
            val rTok = relaxedTokens[pos]
            KnowledgeKeywordTokenEntity(
              tokenId = 0L,
              keywordId = 0L,
              tokenPosition = pos,
              strictToken = sTok,
              strictStem = LocalKnowledgeBase.stripArabicPrefixes(sTok).ifEmpty { sTok },
              relaxedToken = rTok,
              relaxedStem = LocalKnowledgeBase.stripArabicPrefixes(rTok).ifEmpty { rTok }
            )
          }

          kwWithTokens.add(KeywordWithTokensBundle(keyword = kwEntity, tokens = tokEntities))
        }
      }
    }

    val detail = if (includeDetail) {
      KnowledgeTopicDetailEntity(
        topicId = topicId,
        packId = packId,
        detailedArabic = detailedArabic,
        detailedEnglish = detailedEnglish,
        relatedTopicIdsJson = "[]",
        examplesJson = converters.fromStringList(listOf("مثال تطبيقي لـ $topicId")),
        lastReviewed = 1_715_000_000_000L + contentVersion,
        additionalContentJson = """{"pack":"$packId","version":$contentVersion}"""
      )
    } else {
      null
    }

    val branches = if (includeDetail) {
      followUpTriggers.mapIndexed { index, (trigger, replyAr) ->
        KnowledgeFollowUpBranchEntity(
          branchId = "branch_${topicId}_${index + 1}",
          topicId = topicId,
          packId = packId,
          priorityOrder = index + 1,
          matchMode = "SUBSTRING_CONTAINS",
          triggerKeywordsJson = converters.fromStringList(listOf(trigger)),
          responseArabic = replyAr,
          responseEnglish = "Practical applications for $topicId from $packId v$contentVersion.",
          actionableOptionsJson = converters.fromActionableOptionList(
            listOf(ActionableOption("المزيد عن $topicId", "اشرح المزيد عن $topicId"))
          ),
          isDefault = false
        )
      }
    } else {
      emptyList()
    }

    return TopicStorageBundle(
      header = header,
      keywordsWithTokens = kwWithTokens,
      detail = detail,
      followUpBranches = branches
    )
  }

  private fun buildSyntheticPack(
    packId: String,
    priority: Int,
    topicCount: Int,
    packVersion: Int = 1,
    topicPrefix: String = "ext_topic"
  ): Pair<KnowledgePackMetadataEntity, List<TopicStorageBundle>> {
    val pack = KnowledgePackMetadataEntity(
      packId = packId,
      sourceType = "BUNDLED_ASSET",
      packVersion = packVersion,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("${packId}_v$packVersion"),
      packPriority = priority,
      importedAt = 1_715_000_000_000L,
      topicCount = topicCount
    )
    val topics = (1..topicCount).map { idx ->
      val id = "${topicPrefix}_$idx"
      buildSyntheticTopicBundle(
        topicId = id,
        packId = packId,
        topicOrderIndex = 10 + idx,
        packPriority = priority,
        contentVersion = packVersion,
        keywords = listOf(
          "الموضوع العلمي رقم $idx",
          "علم رقم $idx",
          "scientific topic $idx",
          "concept_${idx}_key"
        )
      )
    }
    return pack to topics
  }

  // =========================================================================
  // SECTION A: SNAPSHOT PERFORMANCE & SEARCH PARITY (11, 100, 500, 1,000 TOPICS)
  // =========================================================================

  @Test
  fun sectionA_bulkSnapshotLoaderExecutesAtMostThreeSelectQueriesAndPreserves100PercentParity() = runBlocking(Dispatchers.Default) {
    val repository = KnowledgeRepository(
      database = database,
      cache = KnowledgeCache()
    )
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    // Measure 11-topic baseline first
    Thread.sleep(20)
    recordedSqlQueries.clear()
    val seedBulkStartNs = System.nanoTime()
    val seedBulkBundles = repository.loadActiveTopicStorageBundlesFromDao(dao)
    val seedBulkLoadMs = (System.nanoTime() - seedBulkStartNs) / 1_000_000.0
    Thread.sleep(25)
    val seedBulkSelects = countUserSelectQueries(recordedSqlQueries.toList())
    assertEquals(11, seedBulkBundles.size)
    assertEquals(3, seedBulkSelects.size)

    recordedSqlQueries.clear()
    val seedLegacyStartNs = System.nanoTime()
    val seedLegacyBundles = repository.loadActiveTopicStorageBundlesLegacyPerTopicForTesting(dao)
    val seedLegacyLoadMs = (System.nanoTime() - seedLegacyStartNs) / 1_000_000.0
    Thread.sleep(25)
    val seedLegacySelects = countUserSelectQueries(recordedSqlQueries.toList())
    assertEquals(1 + 2 * 11, seedLegacySelects.size)
    assertEquals(11, seedLegacyBundles.size)

    val seedSnapStartNs = System.nanoTime()
    val seedSnap = InMemorySearchSnapshot.fromTopicStorageBundles(1L, seedBulkBundles)
    val seedSnapBuildMs = (System.nanoTime() - seedSnapStartNs) / 1_000_000.0
    println(
      "BENCHMARK_METRIC N=11 | bulkQueries=${seedBulkSelects.size} | legacyQueries=${seedLegacySelects.size} | " +
        "bulkDbLoadMs=${"%.2f".format(seedBulkLoadMs)} | legacyDbLoadMs=${"%.2f".format(seedLegacyLoadMs)} | " +
        "snapshotBuildMs=${"%.2f".format(seedSnapBuildMs)} | keywords=${seedSnap.keywordCount}"
    )

    val scaleSizes = listOf(100, 500, 1000)
    for (topicScale in scaleSizes) {
      val externalCount = topicScale - 11
      val (pack, bundles) = buildSyntheticPack(
        packId = "scale_pack_$topicScale",
        priority = 10,
        topicCount = externalCount,
        topicPrefix = "scale_${topicScale}_topic"
      )
      dao.upsertPackWithTopicsTransactional(pack, bundles)

      // 1. Measure bulk loader SQL query count and latency
      Thread.sleep(25)
      recordedSqlQueries.clear()
      val bulkStartNs = System.nanoTime()
      val bulkBundles = repository.loadActiveTopicStorageBundlesFromDao(dao)
      val bulkLoadMs = (System.nanoTime() - bulkStartNs) / 1_000_000.0
      Thread.sleep(30)
      val bulkSelectQueries = countUserSelectQueries(recordedSqlQueries.toList())

      // Verify bounded query complexity <= 3 SELECT queries regardless of N
      assertEquals(topicScale, bulkBundles.size)
      assertTrue(
        "Expected <= 3 SELECT queries for N=$topicScale, but got ${bulkSelectQueries.size}: $bulkSelectQueries",
        bulkSelectQueries.size <= 3
      )
      assertEquals(3, bulkSelectQueries.size)
      // Verify zero queries touched knowledge_topic_details or knowledge_followup_branches
      assertTrue(bulkSelectQueries.none { it.contains("knowledge_topic_details", ignoreCase = true) })
      assertTrue(bulkSelectQueries.none { it.contains("knowledge_followup_branches", ignoreCase = true) })
      assertTrue(bulkBundles.all { it.detail == null && it.followUpBranches.isEmpty() })

      // 2. Measure legacy 1 + 2N per-topic loader for comparison and parity verification
      recordedSqlQueries.clear()
      val legacyStartNs = System.nanoTime()
      val legacyBundles = repository.loadActiveTopicStorageBundlesLegacyPerTopicForTesting(dao)
      val legacyLoadMs = (System.nanoTime() - legacyStartNs) / 1_000_000.0
      Thread.sleep(30)
      val legacySelectQueries = countUserSelectQueries(recordedSqlQueries.toList())

      assertEquals(1 + 2 * topicScale, legacySelectQueries.size)

      // 3. Build snapshots from both and verify 100% search parity
      val bulkSnapshotBuildStartNs = System.nanoTime()
      val bulkSnapshot = InMemorySearchSnapshot.fromTopicStorageBundles(
        knowledgeGeneration = topicScale.toLong(),
        bundles = bulkBundles
      )
      val bulkSnapshotBuildMs = (System.nanoTime() - bulkSnapshotBuildStartNs) / 1_000_000.0

      val legacySnapshot = InMemorySearchSnapshot.fromTopicStorageBundles(
        knowledgeGeneration = topicScale.toLong(),
        bundles = legacyBundles
      )

      assertEquals(legacySnapshot.topicCount, bulkSnapshot.topicCount)
      assertEquals(legacySnapshot.keywordCount, bulkSnapshot.keywordCount)
      assertEquals(legacySnapshot.reverseTokenIndex.indexedStrictTokenCount, bulkSnapshot.reverseTokenIndex.indexedStrictTokenCount)
      assertEquals(legacySnapshot.reverseTokenIndex.indexedRelaxedStemCount, bulkSnapshot.reverseTokenIndex.indexedRelaxedStemCount)
      assertEquals(legacySnapshot.activeTopicIdentitiesById, bulkSnapshot.activeTopicIdentitiesById)

      val searchService = KnowledgeSearchService(cache = KnowledgeCache())
      val parityQueries = listOf(
        "الفيزياء والكون",
        "ما هي عاصمة عمان",
        "الموضوع العلمي رقم 1",
        "الموضوع العلمي رقم ${externalCount / 2}",
        "scientific topic $externalCount",
        "كيف احسن نومي"
      )
      var totalSearchMicros = 0.0
      for (q in parityQueries) {
        val sStart = System.nanoTime()
        val bulkRes = searchService.search(q, null, bulkSnapshot)
        totalSearchMicros += (System.nanoTime() - sStart) / 1_000.0
        val legacyRes = searchService.search(q, null, legacySnapshot)
        assertEquals("Parity mismatch for query '$q' at N=$topicScale", legacyRes.matchedTopicId, bulkRes.matchedTopicId)
        assertEquals(
          "Candidate list mismatch for query '$q' at N=$topicScale",
          legacyRes.orderedCandidates.filter { it.rawScore > 0 },
          bulkRes.orderedCandidates.filter { it.rawScore > 0 }
        )
      }
      val avgSearchMicros = totalSearchMicros / parityQueries.size

      println(
        "BENCHMARK_METRIC N=$topicScale | bulkQueries=${bulkSelectQueries.size} | legacyQueries=${legacySelectQueries.size} | " +
          "bulkDbLoadMs=${"%.2f".format(bulkLoadMs)} | legacyDbLoadMs=${"%.2f".format(legacyLoadMs)} | " +
          "snapshotBuildMs=${"%.2f".format(bulkSnapshotBuildMs)} | avgSearchUs=${"%.2f".format(avgSearchMicros)} | " +
          "keywords=${bulkSnapshot.keywordCount}"
      )

      // Clean up scale pack before next iteration
      dao.deletePackAndRecalculateWinnersTransactional(pack.packId)
    }
  }

  // =========================================================================
  // SECTION B: ACTIVE WINNER INTEGRITY & TRANSACTIONAL RECOMPUTATION
  // =========================================================================

  @Test
  fun sectionB_activeWinnerRecomputationCoversUpdateRemovalDeactivationReactivationPriorityAndRollback() = runBlocking(Dispatchers.Default) {
    val cache = KnowledgeCache()
    val repository = KnowledgeRepository(
      database = database,
      cache = cache
    )
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    // 1. Insert Pack A (priority 10) with topics [alpha, beta]
    val packA = KnowledgePackMetadataEntity(
      packId = "pack_a",
      sourceType = "BUNDLED_ASSET",
      packVersion = 1,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("pack_a_v1"),
      packPriority = 10,
      importedAt = 1_715_000_000_000L,
      topicCount = 2
    )
    val alphaA = buildSyntheticTopicBundle(
      topicId = "topic_alpha",
      packId = "pack_a",
      topicOrderIndex = 20,
      packPriority = 10,
      contentVersion = 1,
      arabicSummary = "ملخص ألفا من الحزمة A",
      keywords = listOf("موضوع الفا", "alpha topic")
    )
    val betaA = buildSyntheticTopicBundle(
      topicId = "topic_beta",
      packId = "pack_a",
      topicOrderIndex = 21,
      packPriority = 10,
      contentVersion = 1,
      arabicSummary = "ملخص بيتا من الحزمة A",
      keywords = listOf("موضوع بيتا", "beta topic")
    )
    val resA = repository.upsertPackAndRefreshSnapshot(packA, listOf(alphaA, betaA))
    assertTrue(resA.isSuccess)
    assertEquals("pack_a", repository.currentSnapshot.getActiveIdentity("topic_alpha")?.packId)
    assertEquals("pack_a", repository.currentSnapshot.getActiveIdentity("topic_beta")?.packId)

    // 2. Insert Pack B (priority 20) with overlapping [alpha, beta] and new [gamma]
    val packB = KnowledgePackMetadataEntity(
      packId = "pack_b",
      sourceType = "LOCAL_UPDATE",
      packVersion = 1,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("pack_b_v1"),
      packPriority = 20,
      importedAt = 1_715_000_001_000L,
      topicCount = 3
    )
    val alphaB = buildSyntheticTopicBundle(
      topicId = "topic_alpha",
      packId = "pack_b",
      topicOrderIndex = 20,
      packPriority = 20,
      contentVersion = 1,
      sourceType = "LOCAL_UPDATE",
      arabicSummary = "ملخص ألفا من الحزمة B",
      keywords = listOf("موضوع الفا", "alpha topic")
    )
    val betaB = buildSyntheticTopicBundle(
      topicId = "topic_beta",
      packId = "pack_b",
      topicOrderIndex = 21,
      packPriority = 20,
      contentVersion = 1,
      sourceType = "LOCAL_UPDATE",
      arabicSummary = "ملخص بيتا من الحزمة B",
      keywords = listOf("موضوع بيتا", "beta topic")
    )
    val gammaB = buildSyntheticTopicBundle(
      topicId = "topic_gamma",
      packId = "pack_b",
      topicOrderIndex = 22,
      packPriority = 20,
      contentVersion = 1,
      sourceType = "LOCAL_UPDATE",
      arabicSummary = "ملخص جاما من الحزمة B",
      keywords = listOf("موضوع جاما", "gamma topic")
    )
    assertTrue(repository.upsertPackAndRefreshSnapshot(packB, listOf(alphaB, betaB, gammaB)).isSuccess)
    assertEquals("pack_b", repository.currentSnapshot.getActiveIdentity("topic_alpha")?.packId)
    assertEquals("pack_b", repository.currentSnapshot.getActiveIdentity("topic_beta")?.packId)
    assertEquals("pack_b", repository.currentSnapshot.getActiveIdentity("topic_gamma")?.packId)

    // 3. Update Pack B (v2) where 'topic_beta' and 'topic_gamma' are REMOVED from Pack B, and 'topic_alpha' is updated to v2.
    // Expected:
    // - 'topic_alpha' stays on 'pack_b' with contentVersion = 2
    // - 'topic_beta' falls back to 'pack_a' as active winner!
    // - 'topic_gamma' is completely removed since no fallback pack owns it.
    val packBV2 = packB.copy(
      packVersion = 2,
      sha256Checksum = buildSha256Hex("pack_b_v2"),
      topicCount = 1
    )
    val alphaBV2 = buildSyntheticTopicBundle(
      topicId = "topic_alpha",
      packId = "pack_b",
      topicOrderIndex = 20,
      packPriority = 20,
      contentVersion = 2,
      sourceType = "LOCAL_UPDATE",
      arabicSummary = "ملخص ألفا المحدث من الحزمة B الإصدار 2",
      keywords = listOf("موضوع الفا", "alpha topic")
    )
    val resBV2 = repository.upsertPackAndRefreshSnapshot(packBV2, listOf(alphaBV2))
    assertTrue(resBV2.isSuccess)
    val successBV2 = resBV2 as PackMutationResult.Success
    assertTrue(successBV2.affectedTopicIds.containsAll(listOf("topic_alpha", "topic_beta", "topic_gamma")))

    val snapAfterBV2 = repository.currentSnapshot
    assertEquals("pack_b", snapAfterBV2.getActiveIdentity("topic_alpha")?.packId)
    assertEquals(2, snapAfterBV2.getActiveIdentity("topic_alpha")?.contentVersion)
    assertEquals("pack_a", snapAfterBV2.getActiveIdentity("topic_beta")?.packId)
    assertEquals("ملخص بيتا من الحزمة A", snapAfterBV2.topicsById["topic_beta"]?.arabicSummary)
    assertNull(snapAfterBV2.topicsById["topic_gamma"])

    // 4. Deactivate Pack B -> 'topic_alpha' must fall back to 'pack_a'
    val deactivateRes = repository.deactivatePackAndRefreshSnapshot("pack_b")
    assertTrue(deactivateRes.isSuccess)
    assertEquals("pack_a", repository.currentSnapshot.getActiveIdentity("topic_alpha")?.packId)
    assertEquals("ملخص ألفا من الحزمة A", repository.currentSnapshot.topicsById["topic_alpha"]?.arabicSummary)

    // 5. Reactivate Pack B -> 'topic_alpha' must return to 'pack_b' (priority 20, v2)
    val reactivateRes = repository.reactivatePackAndRefreshSnapshot("pack_b")
    assertTrue(reactivateRes.isSuccess)
    assertEquals("pack_b", repository.currentSnapshot.getActiveIdentity("topic_alpha")?.packId)
    assertEquals(2, repository.currentSnapshot.getActiveIdentity("topic_alpha")?.contentVersion)

    // 6. Change Pack A priority from 10 to 30 -> 'topic_alpha' must switch winner to 'pack_a'
    val priorityRes = repository.updatePackPriorityAndRefreshSnapshot("pack_a", 30)
    assertTrue(priorityRes.isSuccess)
    assertEquals("pack_a", repository.currentSnapshot.getActiveIdentity("topic_alpha")?.packId)
    assertEquals(30, repository.currentSnapshot.getActiveIdentity("topic_alpha")?.packPriority)

    // 7. Delete winning Pack A -> 'topic_alpha' falls back to 'pack_b', and 'topic_beta' disappears
    val deleteRes = repository.deletePackAndRefreshSnapshot("pack_a")
    assertTrue(deleteRes.isSuccess)
    assertEquals("pack_b", repository.currentSnapshot.getActiveIdentity("topic_alpha")?.packId)
    assertNull(repository.currentSnapshot.topicsById["topic_beta"])

    // 8. Transaction rollback on failed update: simulate mid-transaction exception and verify zero partial changes
    val genBeforeRollback = repository.currentGeneration
    val failedPack = KnowledgePackMetadataEntity(
      packId = "pack_corrupt_tx",
      sourceType = "LOCAL_UPDATE",
      packVersion = 1,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("pack_corrupt_tx"),
      packPriority = 99,
      importedAt = 1_715_000_009_000L,
      topicCount = 1
    )
    val failedBundle = buildSyntheticTopicBundle(
      topicId = "topic_alpha",
      packId = "pack_corrupt_tx",
      topicOrderIndex = 20,
      packPriority = 99,
      contentVersion = 9,
      sourceType = "LOCAL_UPDATE",
      arabicSummary = "ملخص فاسد لا يجب أن يُحفظ"
    )
    val txFailedResult = repository.upsertPackAndRefreshSnapshot(
      pack = failedPack,
      topics = listOf(failedBundle),
      simulatedMidTransactionFailure = {
        throw IllegalStateException("Simulated mid-transaction failure")
      }
    )
    assertFalse(txFailedResult.isSuccess)
    assertTrue(txFailedResult is PackMutationResult.TransactionFailed)
    assertNull(dao.getPackMetadataById("pack_corrupt_tx"))
    assertEquals("pack_b", dao.getActiveTopicHeader("topic_alpha")?.packId)
    assertEquals(genBeforeRollback, repository.currentGeneration)
  }

  // =========================================================================
  // SECTION C: LAZY LOADING OF DETAILED CONTENT & FOLLOW-UP BRANCHES
  // =========================================================================

  @Test
  fun sectionC_lazyDetailLoadingSelectsActiveWinnerCoalescesConcurrentLoadsAndHandlesMissingOrMalformedDetails() = runBlocking(Dispatchers.Default) {
    val cache = KnowledgeCache()
    val repository = KnowledgeRepository(
      database = database,
      cache = cache
    )
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    // Insert lower-priority pack_low (priority 10) and higher-priority pack_high (priority 25) both defining 'quantum_computing'
    val packLow = KnowledgePackMetadataEntity(
      packId = "pack_low",
      sourceType = "BUNDLED_ASSET",
      packVersion = 1,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("pack_low"),
      packPriority = 10,
      importedAt = 1_715_000_000_000L,
      topicCount = 1
    )
    val quantumLow = buildSyntheticTopicBundle(
      topicId = "quantum_computing",
      packId = "pack_low",
      topicOrderIndex = 30,
      packPriority = 10,
      contentVersion = 1,
      arabicSummary = "ملخص الحوسبة الكمومية من الحزمة المنخفضة.",
      detailedArabic = "تفصيل الحزمة المنخفضة (لا ينبغي تحميله عند فوز الحزمة العليا).",
      keywords = listOf("الحوسبه الكموميه", "كيوبت", "quantum computing"),
      followUpTriggers = listOf("الكيوبت" to "رد الكيوبت من الحزمة المنخفضة.")
    )

    val packHigh = KnowledgePackMetadataEntity(
      packId = "pack_high",
      sourceType = "LOCAL_UPDATE",
      packVersion = 3,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("pack_high"),
      packPriority = 25,
      importedAt = 1_715_000_005_000L,
      topicCount = 3
    )
    val quantumHigh = buildSyntheticTopicBundle(
      topicId = "quantum_computing",
      packId = "pack_high",
      topicOrderIndex = 30,
      packPriority = 25,
      contentVersion = 3,
      sourceType = "LOCAL_UPDATE",
      arabicSummary = "الحوسبة الكمومية تعتمد على مبادئ التراكب والتشابك الكمومي لمعالجة البيانات.",
      detailedArabic = "تفصيل الحزمة العليا الإصدار 3 حول الحوسبة الكمومية والتراكب.",
      keywords = listOf("الحوسبه الكموميه", "كيوبت", "quantum computing"),
      followUpTriggers = listOf("الكيوبت" to "الكيوبت هو وحدة المعلومات الكمومية الأساسية في الحزمة العليا الإصدار 3.")
    )
    val topicWithoutDetail = buildSyntheticTopicBundle(
      topicId = "topic_no_detail",
      packId = "pack_high",
      topicOrderIndex = 31,
      packPriority = 25,
      contentVersion = 3,
      sourceType = "LOCAL_UPDATE",
      includeDetail = false,
      arabicSummary = "ملخص موضوع بدون تفاصيل إضافية.",
      keywords = listOf("موضوع بدون تفصيل")
    )
    val topicMalformedDetail = buildSyntheticTopicBundle(
      topicId = "topic_malformed_detail",
      packId = "pack_high",
      topicOrderIndex = 32,
      packPriority = 25,
      contentVersion = 3,
      sourceType = "LOCAL_UPDATE",
      includeDetail = true,
      arabicSummary = "ملخص موضوع ذو تفاصيل تالفة آمنة.",
      keywords = listOf("موضوع تالف")
    )

    assertTrue(repository.upsertPackAndRefreshSnapshot(packLow, listOf(quantumLow)).isSuccess)
    assertTrue(repository.upsertPackAndRefreshSnapshot(packHigh, listOf(quantumHigh, topicWithoutDetail, topicMalformedDetail)).isSuccess)

    // Corrupt examplesJson in SQLite for topic_malformed_detail after insertion to test runtime resilience
    val malformedEntity = dao.getActiveTopicDetail("topic_malformed_detail")!!
    dao.upsertTopicDetail(malformedEntity.copy(examplesJson = "{corrupt_not_json_array"))

    // 1. Verify DetailCache is initially empty after snapshot build (strict lazy loading)
    assertEquals(0, cache.detailCache.stats().entryCount)

    // 2. Coalesced concurrent detail loading on cache miss
    val loadsBefore = repository.totalDetailDatabaseLoads
    val concurrentResults = coroutineScope {
      (1..12).map {
        async(Dispatchers.Default) {
          repository.getOrLoadTopicDetail("quantum_computing")
        }
      }.awaitAll()
    }
    val loadsAfter = repository.totalDetailDatabaseLoads
    // Even with 12 concurrent callers, only 1 database detail load was executed
    assertEquals(1, loadsAfter - loadsBefore)

    val loadedDetailResult = concurrentResults.first()
    assertNotNull(loadedDetailResult.bundle)
    assertEquals("pack_high", loadedDetailResult.bundle?.packId)
    assertEquals(3, loadedDetailResult.bundle?.contentVersion)
    assertEquals("quantum_computing@pack_high#v3", loadedDetailResult.bundle?.compositeKey)
    assertEquals("تفصيل الحزمة العليا الإصدار 3 حول الحوسبة الكمومية والتراكب.", loadedDetailResult.bundle?.detail?.detailedArabic)

    // 3. Subsequent load is a pure cache hit with 0 DB queries
    Thread.sleep(20)
    recordedSqlQueries.clear()
    val cachedResult = repository.getOrLoadTopicDetail("quantum_computing")
    Thread.sleep(20)
    assertEquals(TopicDetailLoadStatus.CACHE_HIT, cachedResult.status)
    assertEquals(0, cachedResult.dbQueriesExecuted)
    assertEquals(0, countUserSelectQueries(recordedSqlQueries.toList()).size)

    // 4. Missing detail handling returns MISSING_DETAIL safely
    val missingResult = repository.getOrLoadTopicDetail("topic_no_detail")
    assertEquals(TopicDetailLoadStatus.MISSING_DETAIL, missingResult.status)
    assertNull(missingResult.bundle)

    // 5. Malformed detail handling returns MALFORMED_CONTENT without crashing
    val malformedResult = repository.getOrLoadTopicDetail("topic_malformed_detail")
    assertEquals(TopicDetailLoadStatus.MALFORMED_CONTENT, malformedResult.status)
    assertNull(malformedResult.bundle)

    // 6. End-to-end conversational follow-up resolution via LocalConversationalEngine
    val engine = LocalConversationalEngine(knowledgeRepository = repository)
    val turn1 = engine.respond("حدثني عن الحوسبة الكمومية")
    assertEquals("quantum_computing", turn1.topic)
    assertTrue(turn1.replyText.contains("الحوسبة الكمومية تعتمد على مبادئ التراكب"))

    // Follow-up matching lazy-loaded branch trigger "الكيوبت"
    val turn2 = engine.respond("وما هو الكيوبت؟")
    assertEquals("quantum_computing", turn2.topic)
    assertEquals("الكيوبت هو وحدة المعلومات الكمومية الأساسية في الحزمة العليا الإصدار 3.", turn2.replyText)

    // Generic follow-up "اشرح المزيد عنها" uses lazy-loaded detailedArabic
    val turn3 = engine.respond("اشرح المزيد عنها")
    assertEquals("quantum_computing", turn3.topic)
    assertTrue(turn3.replyText.contains("تفصيل الحزمة العليا الإصدار 3"))
  }

  // =========================================================================
  // SECTION D: KNOWLEDGECACHE INTEGRITY, BUDGETS, AND VERSION ISOLATION
  // =========================================================================

  @Test
  fun sectionD_knowledgeCacheEnforcesBudgetsAndInvalidatesStaleVersionsOnWinnerOrVersionChange() = runBlocking(Dispatchers.Default) {
    val cache = KnowledgeCache()
    val report = cache.snapshotStats()

    // Verify authoritative 256 KB + 512 KB + 64 KB = 832 KB budget
    assertEquals(256 * 1024, report.headerStats.maxAccountedBudgetBytes)
    assertEquals(512 * 1024, report.detailStats.maxAccountedBudgetBytes)
    assertEquals(64 * 1024, report.rawScoreStats.maxAccountedBudgetBytes)
    assertEquals(832 * 1024, report.totalAccountedBudgetBytes)

    // Verify oversized item (> 50% of partition budget) is rejected without evicting existing entries
    val smallTopic = LocalKnowledgeBase.KnowledgeTopic(
      id = "small_topic",
      keywords = listOf("عنوان"),
      arabicSummary = "ملخص صغير",
      englishSummary = "Small summary",
      suggestedFollowUps = emptyList()
    )
    assertTrue(cache.putHeader("small_topic", smallTopic))
    assertEquals(1, cache.headerCache.stats().entryCount)

    val oversizedSummary = "أ".repeat(70_000) // ~140 KB UTF-16 > 128 KB single-item limit
    val hugeTopic = LocalKnowledgeBase.KnowledgeTopic(
      id = "huge_topic",
      keywords = listOf("ضخم"),
      arabicSummary = oversizedSummary,
      englishSummary = "Huge",
      suggestedFollowUps = emptyList()
    )
    val accepted = cache.putHeader("huge_topic", hugeTopic)
    assertFalse(accepted)
    assertNotNull(cache.getHeader("small_topic"))
    assertEquals(1L, cache.headerCache.stats().oversizedRejectedCount)

    // Verify version and pack isolation on detail cache when pack updates from v1 to v2
    val repository = KnowledgeRepository(
      database = database,
      cache = cache
    )
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    val packV1 = KnowledgePackMetadataEntity(
      packId = "pack_isolated",
      sourceType = "BUNDLED_ASSET",
      packVersion = 1,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("pack_isolated_v1"),
      packPriority = 15,
      importedAt = 1_715_000_000_000L,
      topicCount = 1
    )
    val topicV1 = buildSyntheticTopicBundle(
      topicId = "astro_topic",
      packId = "pack_isolated",
      topicOrderIndex = 40,
      packPriority = 15,
      contentVersion = 1,
      detailedArabic = "نظرة تفصيلية الإصدار الأول v1"
    )
    assertTrue(repository.upsertPackAndRefreshSnapshot(packV1, listOf(topicV1)).isSuccess)

    val detailV1 = repository.getOrLoadTopicDetail("astro_topic")
    assertEquals("نظرة تفصيلية الإصدار الأول v1", detailV1.bundle?.detail?.detailedArabic)
    assertNotNull(cache.getDetailForIdentity("astro_topic", "pack_isolated", 1))

    // Upgrade pack to v2 -> v1 cache entry must be invalidated and v2 loaded cleanly
    val packV2 = packV1.copy(
      packVersion = 2,
      sha256Checksum = buildSha256Hex("pack_isolated_v2")
    )
    val topicV2 = buildSyntheticTopicBundle(
      topicId = "astro_topic",
      packId = "pack_isolated",
      topicOrderIndex = 40,
      packPriority = 15,
      contentVersion = 2,
      detailedArabic = "نظرة تفصيلية الإصدار الثاني v2 المحدث"
    )
    assertTrue(repository.upsertPackAndRefreshSnapshot(packV2, listOf(topicV2)).isSuccess)

    assertNull("Stale v1 detail entry must be evicted on pack update", cache.getDetailForIdentity("astro_topic", "pack_isolated", 1))
    val detailV2 = repository.getOrLoadTopicDetail("astro_topic")
    assertEquals("نظرة تفصيلية الإصدار الثاني v2 المحدث", detailV2.bundle?.detail?.detailedArabic)
    assertEquals(2, detailV2.bundle?.contentVersion)
  }

  // =========================================================================
  // SECTION E: CORE SEED KNOWLEDGE PROTECTION
  // =========================================================================

  @Test
  fun sectionE_protectedCoreSeedTopicsCannotBeOverriddenDeactivatedOrDeletedByExternalPacks() = runBlocking(Dispatchers.Default) {
    val repository = KnowledgeRepository(
      database = database,
      cache = KnowledgeCache()
    )
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    // 1. Attempt to upsert an external pack with priority 999 overriding 'physics_universe' -> rejected by validator
    val hijackPack = KnowledgePackMetadataEntity(
      packId = "malicious_external_pack",
      sourceType = "LOCAL_UPDATE",
      packVersion = 99,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("hijack_pack"),
      packPriority = 999,
      importedAt = 1_715_000_000_000L,
      topicCount = 1
    )
    val hijackBundle = buildSyntheticTopicBundle(
      topicId = "physics_universe",
      packId = "malicious_external_pack",
      topicOrderIndex = 7,
      packPriority = 999,
      contentVersion = 99,
      sourceType = "LOCAL_UPDATE",
      arabicSummary = "ملخص مزيف للفيزياء"
    )
    val upsertHijackResult = repository.upsertPackAndRefreshSnapshot(hijackPack, listOf(hijackBundle))
    assertFalse("External pack attempting to override protected core topic must be rejected", upsertHijackResult.isSuccess)
    val rejected = upsertHijackResult as PackMutationResult.ValidationRejected
    assertTrue(rejected.issues.any { it.message.contains("protected core seed topic") })

    // 2. Defense-in-depth: even if inserted directly into DAO bypassing repository validation with priority 999,
    // canonical core_seed_pack still wins in SQL!
    dao.upsertPackMetadata(hijackPack)
    dao.upsertTopicHeader(hijackBundle.header)
    dao.recalculateActiveWinnerForTopic("physics_universe")
    val winningPhysics = dao.getActiveTopicHeader("physics_universe")
    assertNotNull(winningPhysics)
    assertEquals(SeedKnowledgeCatalog.CORE_SEED_PACK_ID, winningPhysics?.packId)
    assertTrue(winningPhysics?.isCoreTopic == true)

    // 3. External callers cannot deactivate, delete, or alter priority of core_seed_pack
    assertFalse(repository.deactivatePackAndRefreshSnapshot(SeedKnowledgeCatalog.CORE_SEED_PACK_ID).isSuccess)
    assertFalse(repository.deletePackAndRefreshSnapshot(SeedKnowledgeCatalog.CORE_SEED_PACK_ID).isSuccess)
    assertFalse(repository.updatePackPriorityAndRefreshSnapshot(SeedKnowledgeCatalog.CORE_SEED_PACK_ID, 5).isSuccess)

    // Verify all 11 core seed topics remain active and intact
    val currentSnap = repository.currentSnapshot
    for (coreId in SeedKnowledgeCatalog.PROTECTED_CORE_TOPIC_IDS) {
      val identity = currentSnap.getActiveIdentity(coreId)
      assertNotNull("Core topic $coreId must remain active", identity)
      assertEquals(SeedKnowledgeCatalog.CORE_SEED_PACK_ID, identity?.packId)
    }
  }

  // =========================================================================
  // SECTION F: SNAPSHOT ATOMIC PUBLICATION & TURN GENERATION CONSISTENCY
  // =========================================================================

  @Test
  fun sectionF_activeConversationTurnRetainsPinnedSnapshotWhileNextTurnUsesNewGeneration() = runBlocking(Dispatchers.Default) {
    val repository = KnowledgeRepository(
      database = database,
      cache = KnowledgeCache()
    )
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    val packV1 = KnowledgePackMetadataEntity(
      packId = "pack_turn_consistency",
      sourceType = "BUNDLED_ASSET",
      packVersion = 1,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("pack_tc_v1"),
      packPriority = 20,
      importedAt = 1_715_000_000_000L,
      topicCount = 1
    )
    val topicV1 = buildSyntheticTopicBundle(
      topicId = "renewable_energy",
      packId = "pack_turn_consistency",
      topicOrderIndex = 50,
      packPriority = 20,
      contentVersion = 1,
      arabicSummary = "الطاقة المتجددة في الإصدار الأول تشمل الطاقة الشمسية وطاقة الرياح.",
      keywords = listOf("الطاقه المتجدده", "renewable energy")
    )
    assertTrue(repository.upsertPackAndRefreshSnapshot(packV1, listOf(topicV1)).isSuccess)

    // Turn 1 prepares context on generation G1
    val turn1Context = repository.prepareTurnKnowledgeContext(
      userQuery = "حدثني عن الطاقة المتجددة",
      currentTopicId = null,
      isArabicOverride = true
    )
    val gen1 = turn1Context.knowledgeGeneration

    // Before Turn 1 finishes reasoning, a background pack update publishes generation G2
    val packV2 = packV1.copy(
      packVersion = 2,
      sha256Checksum = buildSha256Hex("pack_tc_v2")
    )
    val topicV2 = buildSyntheticTopicBundle(
      topicId = "renewable_energy",
      packId = "pack_turn_consistency",
      topicOrderIndex = 50,
      packPriority = 20,
      contentVersion = 2,
      arabicSummary = "الطاقة المتجددة في الإصدار الثاني تشمل الهيدروجين الأخضر والشمس.",
      keywords = listOf("الطاقه المتجدده", "renewable energy")
    )
    assertTrue(repository.upsertPackAndRefreshSnapshot(packV2, listOf(topicV2)).isSuccess)

    // Turn 1 still sees its pinned G1 summary and v1 identity
    assertEquals(1, turn1Context.retrievedTopicIdentity?.contentVersion)
    assertEquals(
      "الطاقة المتجددة في الإصدار الأول تشمل الطاقة الشمسية وطاقة الرياح.",
      turn1Context.resolveMatchedResponseText()
    )

    // Turn 2 automatically sees generation G2 and v2 content
    val turn2Context = repository.prepareTurnKnowledgeContext(
      userQuery = "حدثني عن الطاقة المتجددة",
      currentTopicId = null,
      isArabicOverride = true
    )
    assertTrue(turn2Context.knowledgeGeneration > gen1)
    assertEquals(2, turn2Context.retrievedTopicIdentity?.contentVersion)
    assertEquals(
      "الطاقة المتجددة في الإصدار الثاني تشمل الهيدروجين الأخضر والشمس.",
      turn2Context.resolveMatchedResponseText()
    )
  }
}
