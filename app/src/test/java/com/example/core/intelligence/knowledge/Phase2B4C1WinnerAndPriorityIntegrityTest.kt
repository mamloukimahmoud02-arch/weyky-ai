package com.example.core.intelligence.knowledge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.cache.KnowledgeCache
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KeywordWithTokensBundle
import com.example.core.intelligence.knowledge.db.dao.KnowledgeDao
import com.example.core.intelligence.knowledge.db.dao.KnowledgePriorityAndWinnerPolicy
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.db.entity.KnowledgeFollowUpBranchEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicDetailEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.search.InMemorySearchSnapshot
import com.example.core.intelligence.knowledge.validation.KnowledgeValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2B4C1WinnerAndPriorityIntegrityTest {

  private lateinit var context: Context
  private lateinit var database: WeykyKnowledgeDatabase
  private lateinit var dao: KnowledgeDao
  private val converters = KnowledgeTypeConverters()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(
      context,
      WeykyKnowledgeDatabase::class.java
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

  private fun buildSha256Hex(seed: String): String {
    val bytes = MessageDigest.getInstance("SHA-256")
      .digest(seed.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun buildPackMetadata(
    packId: String,
    packPriority: Int,
    packVersion: Int = 1,
    importedAt: Long = 1_715_000_000_000L,
    topicCount: Int = 1,
    sourceType: String = "BUNDLED_ASSET"
  ): KnowledgePackMetadataEntity {
    return KnowledgePackMetadataEntity(
      packId = packId,
      sourceType = sourceType,
      packVersion = packVersion,
      schemaVersion = 1,
      sha256Checksum = buildSha256Hex("$packId:$packVersion:$packPriority:$importedAt"),
      packPriority = packPriority,
      importedAt = importedAt,
      topicCount = topicCount
    )
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
    includeDetail: Boolean = true
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

    keywords.forEach { rawKeyword ->
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
            language = "ar"
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
        detailedArabic = "تفصيل موسع للموضوع $topicId في الحزمة $packId (v$contentVersion).",
        detailedEnglish = "Extended detail for $topicId in pack $packId (v$contentVersion).",
        relatedTopicIdsJson = "[]",
        examplesJson = converters.fromStringList(listOf("مثال تطبيقي لـ $topicId")),
        lastReviewed = 1_715_000_000_000L + contentVersion,
        additionalContentJson = """{"pack":"$packId","version":$contentVersion}"""
      )
    } else {
      null
    }

    val branches = if (includeDetail) {
      listOf(
        KnowledgeFollowUpBranchEntity(
          branchId = "branch_${topicId}_1",
          topicId = topicId,
          packId = packId,
          priorityOrder = 1,
          matchMode = "SUBSTRING_CONTAINS",
          triggerKeywordsJson = converters.fromStringList(listOf("تطبيقات $topicId")),
          responseArabic = "تطبيقات عملية للموضوع $topicId من الحزمة $packId الإصدار $contentVersion.",
          responseEnglish = "Practical applications for $topicId from pack $packId v$contentVersion.",
          actionableOptionsJson = converters.fromActionableOptionList(
            listOf(ActionableOption("المزيد عن $topicId", "اشرح المزيد عن $topicId"))
          ),
          isDefault = false
        )
      )
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

  // =========================================================================
  // 1. CORE SEED PROTECTION ACROSS ALL 11 PROTECTED TOPIC IDS
  // =========================================================================

  @Test
  fun test1_coreSeedWinsAgainstExternalPacksForAll11ProtectedTopicIds() = runBlocking(Dispatchers.Default) {
    val repository = KnowledgeRepository(database = database, cache = KnowledgeCache())
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    val expectedProtectedIds = setOf(
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
    assertEquals(expectedProtectedIds, SeedKnowledgeCatalog.PROTECTED_CORE_TOPIC_IDS)
    assertEquals(11, SeedKnowledgeCatalog.PROTECTED_CORE_TOPIC_IDS.size)

    // Attempt 1: Repository upsert rejects external pack trying to claim any of the 11 protected IDs
    for (protectedId in SeedKnowledgeCatalog.PROTECTED_CORE_TOPIC_IDS) {
      val hijackerPack = buildPackMetadata(
        packId = "external_hijacker_$protectedId",
        packPriority = Int.MAX_VALUE,
        packVersion = 999
      )
      val hijackerBundle = buildSyntheticTopicBundle(
        topicId = protectedId,
        packId = hijackerPack.packId,
        packPriority = Int.MAX_VALUE,
        contentVersion = 999,
        isCoreTopic = true,
        arabicSummary = "محاولة اختطاف الموضوع الأساسي $protectedId."
      )
      val result = repository.upsertPackAndRefreshSnapshot(hijackerPack, listOf(hijackerBundle))
      assertFalse("Repository must reject external hijacker for $protectedId", result.isSuccess)
    }

    // Attempt 2: Even if rows are inserted directly into DAO with Int.MAX_VALUE priority and forged isCoreTopic=true,
    // both DAO winner selection and InMemorySearchSnapshot.fromTopicStorageBundles keep core_seed_pack as winner.
    val intruderPack = buildPackMetadata(
      packId = "aaa_intruder_max_priority",
      packPriority = Int.MAX_VALUE,
      packVersion = 999
    )
    val intruderBundles = SeedKnowledgeCatalog.PROTECTED_CORE_TOPIC_IDS.mapIndexed { idx, protectedId ->
      buildSyntheticTopicBundle(
        topicId = protectedId,
        packId = intruderPack.packId,
        topicOrderIndex = idx,
        packPriority = Int.MAX_VALUE,
        contentVersion = 999,
        isCoreTopic = true,
        arabicSummary = "ملخص دخيل للموضوع الأساسي $protectedId بأعلى أولوية ممكنة."
      )
    }

    dao.upsertPackWithTopicsTransactional(intruderPack, intruderBundles)
    assertTrue(repository.reloadActiveSnapshotFromDatabase())

    val snapshot = repository.currentSnapshot
    val seedTopicsById = SeedKnowledgeCatalog.buildCoreSeedTopicStorageBundles().associateBy { it.header.topicId }
    for (protectedId in SeedKnowledgeCatalog.PROTECTED_CORE_TOPIC_IDS) {
      val dbWinner = dao.getActiveTopicHeader(protectedId)
      assertNotNull("Active DB winner must exist for $protectedId", dbWinner)
      assertEquals(SeedKnowledgeCatalog.CORE_SEED_PACK_ID, dbWinner?.packId)
      assertTrue(dbWinner?.isActiveWinner == true)

      val snapshotWinner = snapshot.getActiveIdentity(protectedId)
      assertNotNull("Snapshot winner must exist for $protectedId", snapshotWinner)
      assertEquals(SeedKnowledgeCatalog.CORE_SEED_PACK_ID, snapshotWinner?.packId)

      // Also verify InMemorySearchSnapshot.fromTopicStorageBundles when intruder bundles come first
      val seedBundle = seedTopicsById.getValue(protectedId)
      val intruderBundle = intruderBundles.first { it.header.topicId == protectedId }
      val fromBundlesSnap = InMemorySearchSnapshot.fromTopicStorageBundles(
        knowledgeGeneration = 99L,
        bundles = listOf(intruderBundle, seedBundle)
      )
      assertEquals(
        SeedKnowledgeCatalog.CORE_SEED_PACK_ID,
        fromBundlesSnap.getActiveIdentity(protectedId)?.packId
      )
    }
  }

  // =========================================================================
  // 2–9. DETERMINISTIC WINNER PRECEDENCE, TIE-BREAKING, ORDER INDEPENDENCE,
  //      AND DB vs IN-MEMORY SNAPSHOT CONSISTENCY
  // =========================================================================

  @Test
  fun test2to9_winnerPrecedenceOrderIndependenceAndDbSnapshotAgreement() = runBlocking(Dispatchers.Default) {
    val repository = KnowledgeRepository(database = database, cache = KnowledgeCache())
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    val topicId = "quantum_sensing"

    // 2 & 3: Higher pack_priority wins; lower pack_priority does not replace higher-priority winner
    val packLow = buildPackMetadata(packId = "pack_low_prio", packPriority = 10, packVersion = 50, importedAt = 2_000_000_000_000L)
    val bundleLow = buildSyntheticTopicBundle(
      topicId = topicId,
      packId = packLow.packId,
      packPriority = 10,
      contentVersion = 50
    )

    val packHigh = buildPackMetadata(packId = "pack_high_prio", packPriority = 30, packVersion = 1, importedAt = 1_000_000_000_000L)
    val bundleHigh = buildSyntheticTopicBundle(
      topicId = topicId,
      packId = packHigh.packId,
      packPriority = 30,
      contentVersion = 1
    )

    // Insert high priority first, then low priority (even with higher contentVersion/packVersion/importedAt)
    assertTrue(repository.upsertPackAndRefreshSnapshot(packHigh, listOf(bundleHigh)).isSuccess)
    assertEquals("pack_high_prio", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    assertTrue(repository.upsertPackAndRefreshSnapshot(packLow, listOf(bundleLow)).isSuccess)
    // Lower priority pack must NOT replace higher priority winner
    assertEquals("pack_high_prio", dao.getActiveTopicHeader(topicId)?.packId)
    assertEquals("pack_high_prio", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // 4 & 5: Equal pack_priority resolves by content_version DESC (and neither pack_version nor imported_at overrides content_version)
    val packEqualPrioHigherContent = buildPackMetadata(
      packId = "zzz_higher_content_version",
      packPriority = 30,
      packVersion = 1,
      importedAt = 500_000_000_000L
    )
    val bundleEqualPrioHigherContent = buildSyntheticTopicBundle(
      topicId = topicId,
      packId = packEqualPrioHigherContent.packId,
      packPriority = 30,
      contentVersion = 5
    )
    assertTrue(
      repository.upsertPackAndRefreshSnapshot(
        packEqualPrioHigherContent,
        listOf(bundleEqualPrioHigherContent)
      ).isSuccess
    )
    assertEquals("zzz_higher_content_version", dao.getActiveTopicHeader(topicId)?.packId)
    assertEquals("zzz_higher_content_version", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // 6: Exact tie on pack_priority AND content_version resolves deterministically by pack_id ASC
    val packTieAlpha = buildPackMetadata(
      packId = "alpha_tie_pack",
      packPriority = 30,
      packVersion = 1,
      importedAt = 100_000_000_000L
    )
    val bundleTieAlpha = buildSyntheticTopicBundle(
      topicId = topicId,
      packId = packTieAlpha.packId,
      packPriority = 30,
      contentVersion = 5
    )

    val packTieBeta = buildPackMetadata(
      packId = "beta_tie_pack",
      packPriority = 30,
      packVersion = 99, // Higher packVersion & newer importedAt must not override pack_id ASC on exact tie
      importedAt = 9_999_999_999_999L
    )
    val bundleTieBeta = buildSyntheticTopicBundle(
      topicId = topicId,
      packId = packTieBeta.packId,
      packPriority = 30,
      contentVersion = 5
    )

    // 7: Verify import order Beta then Alpha vs Alpha then Beta produces the exact same winner ("alpha_tie_pack")
    assertTrue(repository.upsertPackAndRefreshSnapshot(packTieBeta, listOf(bundleTieBeta)).isSuccess)
    assertTrue(repository.upsertPackAndRefreshSnapshot(packTieAlpha, listOf(bundleTieAlpha)).isSuccess)
    assertEquals("alpha_tie_pack", dao.getActiveTopicHeader(topicId)?.packId)
    assertEquals("alpha_tie_pack", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // Now test reverse order (Alpha then Beta) by deleting both and re-inserting Alpha first, then Beta
    assertTrue(repository.deletePackAndRefreshSnapshot(packTieAlpha.packId).isSuccess)
    assertTrue(repository.deletePackAndRefreshSnapshot(packTieBeta.packId).isSuccess)

    assertTrue(repository.upsertPackAndRefreshSnapshot(packTieAlpha, listOf(bundleTieAlpha)).isSuccess)
    assertTrue(repository.upsertPackAndRefreshSnapshot(packTieBeta, listOf(bundleTieBeta)).isSuccess)
    assertEquals("alpha_tie_pack", dao.getActiveTopicHeader(topicId)?.packId)
    assertEquals("alpha_tie_pack", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // Verify exact single active winner invariant in SQLite
    val allVersions = dao.getAllTopicVersionsForTopicId(topicId)
    assertEquals(5, allVersions.size)
    val activeWinners = allVersions.filter { it.isActiveWinner }
    assertEquals(1, activeWinners.size)
    assertEquals("alpha_tie_pack", activeWinners.single().packId)

    // 8: Repeated snapshot reconstruction produces the exact same winner
    repeat(5) {
      assertTrue(repository.reloadActiveSnapshotFromDatabase())
      val rebuilt = repository.currentSnapshot
      assertEquals("alpha_tie_pack", rebuilt.getActiveIdentity(topicId)?.packId)
      assertEquals(5, rebuilt.getActiveIdentity(topicId)?.contentVersion)
      assertEquals(30, rebuilt.getActiveIdentity(topicId)?.packPriority)
    }

    // 9: Database selection and InMemorySearchSnapshot.fromTopicStorageBundles agree across all bundle orders
    val candidateBundles = listOf(
      bundleLow,
      bundleHigh,
      bundleEqualPrioHigherContent,
      bundleTieBeta,
      bundleTieAlpha
    )
    val snapForward = InMemorySearchSnapshot.fromTopicStorageBundles(1L, candidateBundles)
    val snapReverse = InMemorySearchSnapshot.fromTopicStorageBundles(2L, candidateBundles.reversed())
    assertEquals(dao.getActiveTopicHeader(topicId)?.packId, snapForward.getActiveIdentity(topicId)?.packId)
    assertEquals(dao.getActiveTopicHeader(topicId)?.packId, snapReverse.getActiveIdentity(topicId)?.packId)
  }

  // =========================================================================
  // 10–14. PRIORITY INTEGRITY, DISABLED PRIORITY UPDATE, BOUNDARY VALUES,
  //        AND CORE SEED PRIORITY PROTECTION
  // =========================================================================

  @Test
  fun test10to14_priorityEncodingDisabledUpdateBoundariesAndCoreProtection() = runBlocking(Dispatchers.Default) {
    // 12 & 13: Test encoding/decoding and boundary values (0, 1, Int.MAX_VALUE, -1, Int.MIN_VALUE)
    val logicalBoundaries = listOf(0, 1, 10, 25, 100, Int.MAX_VALUE)
    for (logical in logicalBoundaries) {
      assertTrue(KnowledgePriorityAndWinnerPolicy.isValidLogicalPriority(logical))
      val encodedDisabled = KnowledgePriorityAndWinnerPolicy.encodeDisabledPriority(logical)
      assertTrue("Encoded disabled priority for $logical must be < 0, got $encodedDisabled", encodedDisabled < 0)
      assertTrue(KnowledgePriorityAndWinnerPolicy.isStoredPriorityDisabled(encodedDisabled))
      assertFalse(KnowledgePriorityAndWinnerPolicy.isStoredPriorityEnabled(encodedDisabled))
      val decoded = KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(encodedDisabled)
      assertEquals("Decoded logical priority must equal original $logical", logical, decoded)
    }

    // Specifically check 0 -> -1 and Int.MAX_VALUE -> Int.MIN_VALUE
    assertEquals(-1, KnowledgePriorityAndWinnerPolicy.encodeDisabledPriority(0))
    assertEquals(0, KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(-1))
    assertEquals(Int.MIN_VALUE, KnowledgePriorityAndWinnerPolicy.encodeDisabledPriority(Int.MAX_VALUE))
    assertEquals(Int.MAX_VALUE, KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(Int.MIN_VALUE))

    // Reject negative logical priorities (-1, Int.MIN_VALUE)
    assertFalse(KnowledgePriorityAndWinnerPolicy.isValidLogicalPriority(-1))
    assertFalse(KnowledgePriorityAndWinnerPolicy.isValidLogicalPriority(Int.MIN_VALUE))

    // Validator rejects negative priorities and mismatched topic vs pack priorities
    val invalidNegativePack = buildPackMetadata(packId = "neg_pack", packPriority = -1)
    assertFalse(KnowledgeValidator.validatePackMetadata(invalidNegativePack).isValid)

    val minValNegativePack = buildPackMetadata(packId = "min_neg_pack", packPriority = Int.MIN_VALUE)
    assertFalse(KnowledgeValidator.validatePackMetadata(minValNegativePack).isValid)

    val repository = KnowledgeRepository(database = database, cache = KnowledgeCache())
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    val topicId = "solar_desalination"
    val packA = buildPackMetadata(packId = "pack_solar_a", packPriority = 10)
    val bundleA = buildSyntheticTopicBundle(topicId = topicId, packId = packA.packId, packPriority = 10, contentVersion = 1)

    val packB = buildPackMetadata(packId = "pack_solar_b", packPriority = 20)
    val bundleB = buildSyntheticTopicBundle(topicId = topicId, packId = packB.packId, packPriority = 20, contentVersion = 1)

    assertTrue(repository.upsertPackAndRefreshSnapshot(packA, listOf(bundleA)).isSuccess)
    assertTrue(repository.upsertPackAndRefreshSnapshot(packB, listOf(bundleB)).isSuccess)
    assertEquals("pack_solar_b", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // 10: Updating an enabled pack's priority preserves enabled state and updates winners
    assertTrue(repository.updatePackPriorityAndRefreshSnapshot("pack_solar_a", 50).isSuccess)
    assertEquals(50, dao.getPackMetadataById("pack_solar_a")?.packPriority)
    assertEquals("pack_solar_a", dao.getActiveTopicHeader(topicId)?.packId)
    assertEquals("pack_solar_a", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // Boundary priority Int.MAX_VALUE on enabled pack works safely without overflow
    assertTrue(repository.updatePackPriorityAndRefreshSnapshot("pack_solar_b", Int.MAX_VALUE).isSuccess)
    assertEquals(Int.MAX_VALUE, dao.getPackMetadataById("pack_solar_b")?.packPriority)
    assertEquals("pack_solar_b", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // Deactivate pack_solar_b (which currently has priority Int.MAX_VALUE -> stored as Int.MIN_VALUE)
    assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_solar_b").isSuccess)
    assertEquals(Int.MIN_VALUE, dao.getPackMetadataById("pack_solar_b")?.packPriority)
    assertEquals("pack_solar_a", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // 11: Updating a DISABLED pack's priority MUST preserve its disabled state!
    // Change disabled pack_solar_b's logical priority from Int.MAX_VALUE to 80.
    // It must remain disabled (stored as -(80 + 1) = -81), must NOT become active winner while disabled,
    // and must take effect as priority 80 only when subsequently reactivated!
    assertTrue(repository.updatePackPriorityAndRefreshSnapshot("pack_solar_b", 80).isSuccess)
    val disabledPackBMetadata = dao.getPackMetadataById("pack_solar_b")
    assertNotNull(disabledPackBMetadata)
    assertEquals(-81, disabledPackBMetadata?.packPriority)
    assertEquals(
      80,
      KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(disabledPackBMetadata!!.packPriority)
    )

    val disabledTopicBHeader = dao.getTopicHeaderByIdAndPack(topicId, "pack_solar_b")
    assertNotNull(disabledTopicBHeader)
    assertEquals(-81, disabledTopicBHeader?.packPriority)
    assertFalse("Disabled pack topic must NOT be marked active winner", disabledTopicBHeader?.isActiveWinner == true)
    assertEquals("pack_solar_a", dao.getActiveTopicHeader(topicId)?.packId)
    assertEquals("pack_solar_a", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // Now reactivate pack_solar_b without override -> restores logical priority 80 (> pack_solar_a's 50) and wins!
    assertTrue(repository.reactivatePackAndRefreshSnapshot("pack_solar_b").isSuccess)
    assertEquals(80, dao.getPackMetadataById("pack_solar_b")?.packPriority)
    assertEquals("pack_solar_b", dao.getActiveTopicHeader(topicId)?.packId)
    assertEquals("pack_solar_b", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // Reject negative priority update via repository and DAO
    assertFalse(repository.updatePackPriorityAndRefreshSnapshot("pack_solar_b", -1).isSuccess)
    assertFalse(repository.updatePackPriorityAndRefreshSnapshot("pack_solar_b", Int.MIN_VALUE).isSuccess)
    assertEquals(80, dao.getPackMetadataById("pack_solar_b")?.packPriority)

    // 14: Priority updates, deactivation, reactivation, and deletion cannot modify protected core_seed_pack
    assertFalse(repository.updatePackPriorityAndRefreshSnapshot(SeedKnowledgeCatalog.CORE_SEED_PACK_ID, 10).isSuccess)
    assertFalse(repository.deactivatePackAndRefreshSnapshot(SeedKnowledgeCatalog.CORE_SEED_PACK_ID).isSuccess)
    assertFalse(repository.reactivatePackAndRefreshSnapshot(SeedKnowledgeCatalog.CORE_SEED_PACK_ID, 10).isSuccess)
    assertFalse(repository.deletePackAndRefreshSnapshot(SeedKnowledgeCatalog.CORE_SEED_PACK_ID).isSuccess)
    assertEquals(
      SeedKnowledgeCatalog.CORE_SEED_PACK_PRIORITY,
      dao.getPackMetadataById(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)?.packPriority
    )
  }

  // =========================================================================
  // 15–18. REGRESSION TESTS: ACTIVATION/DEACTIVATION, ATOMIC SNAPSHOT,
  //        IN-FLIGHT PINNING, AND ZERO EAGER DETAIL LOADING
  // =========================================================================

  @Test
  fun test15to18_lifecycleRegressionAtomicSnapshotPinningAndZeroDetailLoading() = runBlocking(Dispatchers.Default) {
    val cache = KnowledgeCache()
    val repository = KnowledgeRepository(database = database, cache = cache)
    assertTrue(repository.initializeSeedAndWarmUpIndex())

    // 18: Detail cache must have 0 entries after cold start & snapshot build
    assertEquals(0, cache.detailCache.stats().entryCount)

    val topicId = "vertical_farming"
    val pack1 = buildPackMetadata(packId = "pack_vf_1", packPriority = 15)
    val bundle1 = buildSyntheticTopicBundle(
      topicId = topicId,
      packId = pack1.packId,
      packPriority = 15,
      contentVersion = 1,
      arabicSummary = "الزراعة العمودية من الحزمة الأولى توفر المياه بنسبة 90 بالمئة.",
      keywords = listOf("الزراعه العموديه", "vertical farming")
    )

    val pack2 = buildPackMetadata(packId = "pack_vf_2", packPriority = 25)
    val bundle2 = buildSyntheticTopicBundle(
      topicId = topicId,
      packId = pack2.packId,
      packPriority = 25,
      contentVersion = 2,
      arabicSummary = "الزراعة العمودية من الحزمة الثانية تعتمد على الإضاءة الذكية والزراعة المائية.",
      keywords = listOf("الزراعه العموديه", "vertical farming")
    )

    val genBefore = repository.currentSnapshot.knowledgeGeneration
    assertTrue(repository.upsertPackAndRefreshSnapshot(pack1, listOf(bundle1)).isSuccess)
    val genAfterPack1 = repository.currentSnapshot.knowledgeGeneration
    assertTrue(genAfterPack1 > genBefore)
    assertEquals(0, cache.detailCache.stats().entryCount)

    // 17: Pin Turn 1 context while pack1 is winner
    val turn1Context = repository.prepareTurnKnowledgeContext(
      userQuery = "حدثني عن الزراعة العمودية",
      currentTopicId = null,
      isArabicOverride = true
    )
    assertEquals("pack_vf_1", turn1Context.retrievedTopicIdentity?.packId)

    // 16: Upsert pack2 (higher priority) -> atomic snapshot replacement
    assertTrue(repository.upsertPackAndRefreshSnapshot(pack2, listOf(bundle2)).isSuccess)
    val genAfterPack2 = repository.currentSnapshot.knowledgeGeneration
    assertTrue(genAfterPack2 > genAfterPack1)
    assertEquals("pack_vf_2", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // Turn 1 remains pinned to pack_vf_1
    assertEquals("pack_vf_1", turn1Context.retrievedTopicIdentity?.packId)
    assertEquals(
      "الزراعة العمودية من الحزمة الأولى توفر المياه بنسبة 90 بالمئة.",
      turn1Context.resolveMatchedResponseText()
    )

    // Turn 2 sees pack_vf_2
    val turn2Context = repository.prepareTurnKnowledgeContext(
      userQuery = "حدثني عن الزراعة العمودية",
      currentTopicId = null,
      isArabicOverride = true
    )
    assertEquals("pack_vf_2", turn2Context.retrievedTopicIdentity?.packId)
    assertEquals(
      "الزراعة العمودية من الحزمة الثانية تعتمد على الإضاءة الذكية والزراعة المائية.",
      turn2Context.resolveMatchedResponseText()
    )

    // 15: Deactivation falls back to pack_vf_1, reactivation restores pack_vf_2
    assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_vf_2").isSuccess)
    assertEquals("pack_vf_1", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    assertTrue(repository.reactivatePackAndRefreshSnapshot("pack_vf_2").isSuccess)
    assertEquals("pack_vf_2", repository.currentSnapshot.getActiveIdentity(topicId)?.packId)

    // 18: Ordinary snapshot reconstructions, priority updates, and summary turn preparations never eagerly load details
    assertEquals(0, cache.detailCache.stats().entryCount)
  }
}
