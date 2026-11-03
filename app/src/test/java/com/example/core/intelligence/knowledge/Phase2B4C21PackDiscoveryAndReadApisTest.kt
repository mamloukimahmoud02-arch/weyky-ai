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
import com.example.core.intelligence.knowledge.repository.InstalledPackTopicState
import com.example.core.intelligence.knowledge.repository.KnowledgePackReadModelMapper
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.repository.PackMutationResult
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
import java.security.MessageDigest
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2B4C21PackDiscoveryAndReadApisTest {

    private lateinit var context: Context
    private lateinit var database: WeykyKnowledgeDatabase
    private lateinit var dao: KnowledgeDao
    private lateinit var repository: KnowledgeRepository
    private val converters = KnowledgeTypeConverters()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            WeykyKnowledgeDatabase::class.java
        ).build()
        dao = database.knowledgeDao()
        repository = KnowledgeRepository(
            database = database,
            cache = KnowledgeCache(),
            converters = converters,
            ioDispatcher = Dispatchers.Unconfined
        )
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
        importedAt: Long = 1_716_000_000_000L,
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
                lastReviewed = 1_716_000_000_000L + contentVersion,
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
    // Pack Discovery Tests (1–5)
    // =========================================================================

    @Test
    fun test01_getInstalledPacks_returnsAllInstalledPacksWithAccurateMetadata() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val packA = buildPackMetadata(
            packId = "science_pack_a",
            packPriority = 80,
            packVersion = 2,
            importedAt = 1_716_100_000_000L,
            topicCount = 2,
            sourceType = "BUNDLED_ASSET"
        )
        val topicsA = listOf(
            buildSyntheticTopicBundle("astro_stars", "science_pack_a", topicOrderIndex = 0, packPriority = 80, contentVersion = 2),
            buildSyntheticTopicBundle("astro_galaxies", "science_pack_a", topicOrderIndex = 1, packPriority = 80, contentVersion = 2)
        )
        val resA = repository.upsertPackAndRefreshSnapshot(packA, topicsA)
        assertTrue(resA.isSuccess)

        val packB = buildPackMetadata(
            packId = "history_pack_b",
            packPriority = 40,
            packVersion = 3,
            importedAt = 1_716_200_000_000L,
            topicCount = 1,
            sourceType = "LOCAL_UPDATE"
        )
        val topicsB = listOf(
            buildSyntheticTopicBundle("oman_maritime", "history_pack_b", topicOrderIndex = 0, packPriority = 40, contentVersion = 3)
        )
        val resB = repository.upsertPackAndRefreshSnapshot(packB, topicsB)
        assertTrue(resB.isSuccess)

        val installed = repository.getInstalledPacks()
        assertEquals(3, installed.size)

        val byId = installed.associateBy { it.packId }
        val seedSummary = byId[SeedKnowledgeCatalog.CORE_SEED_PACK_ID]
        assertNotNull(seedSummary)
        assertEquals(SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE, seedSummary!!.sourceType)
        assertEquals(SeedKnowledgeCatalog.CORE_SEED_PACK_VERSION, seedSummary.packVersion)
        assertEquals(SeedKnowledgeCatalog.CORE_SEED_SCHEMA_VERSION, seedSummary.schemaVersion)
        assertEquals(SeedKnowledgeCatalog.CORE_SEED_PACK_PRIORITY, seedSummary.logicalPriority)
        assertTrue(seedSummary.enabled)
        assertTrue(seedSummary.isCoreSeedPack)
        assertEquals(SeedKnowledgeCatalog.coreTopics.size, seedSummary.topicCount)

        val summaryA = byId["science_pack_a"]
        assertNotNull(summaryA)
        assertEquals("BUNDLED_ASSET", summaryA!!.sourceType)
        assertEquals(2, summaryA.packVersion)
        assertEquals(1, summaryA.schemaVersion)
        assertEquals(packA.sha256Checksum, summaryA.sha256Checksum)
        assertEquals(80, summaryA.logicalPriority)
        assertTrue(summaryA.enabled)
        assertFalse(summaryA.isCoreSeedPack)
        assertEquals(1_716_100_000_000L, summaryA.importedAt)
        assertEquals(2, summaryA.topicCount)

        val summaryB = byId["history_pack_b"]
        assertNotNull(summaryB)
        assertEquals("LOCAL_UPDATE", summaryB!!.sourceType)
        assertEquals(3, summaryB.packVersion)
        assertEquals(40, summaryB.logicalPriority)
        assertTrue(summaryB.enabled)
        assertEquals(1, summaryB.topicCount)
    }

    @Test
    fun test02_getInstalledPacks_returnsEmptyList_whenDatabaseIsEmptyOrUnattached() = runBlocking {
        // 1. Fresh database before any seed initialization or import
        val fromEmptyDb = repository.getInstalledPacks()
        assertTrue(fromEmptyDb.isEmpty())

        // 2. Repository without attached database
        val unattachedRepo = KnowledgeRepository(database = null, ioDispatcher = Dispatchers.Unconfined)
        val fromUnattached = unattachedRepo.getInstalledPacks()
        assertTrue(fromUnattached.isEmpty())
    }

    @Test
    fun test03_getInstalledPacks_ordersDeterministically_enabledBeforeDisabled_thenPriorityVersionPackId() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        // Insert multiple packs in arbitrary order with ties in priority and version
        val packsToInsert = listOf(
            buildPackMetadata("pack_tie_b", packPriority = 50, packVersion = 2),
            buildPackMetadata("pack_tie_a", packPriority = 50, packVersion = 2),
            buildPackMetadata("pack_tie_v1", packPriority = 50, packVersion = 1),
            buildPackMetadata("pack_high_disabled", packPriority = 200, packVersion = 1),
            buildPackMetadata("pack_low_disabled", packPriority = 30, packVersion = 1),
            buildPackMetadata("pack_top_enabled", packPriority = 150, packVersion = 1)
        )
        for ((idx, pack) in packsToInsert.withIndex()) {
            val bundle = buildSyntheticTopicBundle(
                topicId = "topic_order_$idx",
                packId = pack.packId,
                packPriority = pack.packPriority
            )
            assertTrue(repository.upsertPackAndRefreshSnapshot(pack, listOf(bundle)).isSuccess)
        }

        // Deactivate the two disabled test packs
        assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_high_disabled").isSuccess)
        assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_low_disabled").isSuccess)

        val installed = repository.getInstalledPacks()
        assertEquals(
            listOf(
                "pack_top_enabled",                     // enabled, priority 150
                "pack_tie_a",                           // enabled, priority 50, v2, packId ASC
                "pack_tie_b",                           // enabled, priority 50, v2, packId ASC
                "pack_tie_v1",                          // enabled, priority 50, v1
                SeedKnowledgeCatalog.CORE_SEED_PACK_ID, // enabled, priority 0 (CORE_SEED_PACK_PRIORITY)
                "pack_high_disabled",                   // disabled, decoded logicalPriority 200
                "pack_low_disabled"                     // disabled, decoded logicalPriority 30
            ),
            installed.map { it.packId }
        )
    }

    @Test
    fun test04_getInstalledPacks_decodesLogicalPriorityForEnabledPacks_includingZeroAndMaxInt() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val packZero = buildPackMetadata("pack_zero_prio", packPriority = 0, packVersion = 1)
        val packMax = buildPackMetadata("pack_max_prio", packPriority = Int.MAX_VALUE, packVersion = 1)

        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packZero,
                listOf(buildSyntheticTopicBundle("t_zero", "pack_zero_prio", packPriority = 0))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packMax,
                listOf(buildSyntheticTopicBundle("t_max", "pack_max_prio", packPriority = Int.MAX_VALUE))
            ).isSuccess
        )

        val installedById = repository.getInstalledPacks().associateBy { it.packId }
        val zeroSummary = installedById["pack_zero_prio"]!!
        assertTrue(zeroSummary.enabled)
        assertEquals(0, zeroSummary.logicalPriority)

        val maxSummary = installedById["pack_max_prio"]!!
        assertTrue(maxSummary.enabled)
        assertEquals(Int.MAX_VALUE, maxSummary.logicalPriority)
    }

    @Test
    fun test05_getInstalledPacks_decodesLogicalPriorityForDisabledPacks_withoutReactivatingThem() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val packZero = buildPackMetadata("pack_disabled_zero", packPriority = 0)
        val packMid = buildPackMetadata("pack_disabled_mid", packPriority = 75)
        val packMax = buildPackMetadata("pack_disabled_max", packPriority = Int.MAX_VALUE)

        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packZero,
                listOf(buildSyntheticTopicBundle("td_zero", "pack_disabled_zero", packPriority = 0))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packMid,
                listOf(buildSyntheticTopicBundle("td_mid", "pack_disabled_mid", packPriority = 75))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packMax,
                listOf(buildSyntheticTopicBundle("td_max", "pack_disabled_max", packPriority = Int.MAX_VALUE))
            ).isSuccess
        )

        assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_disabled_zero").isSuccess)
        assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_disabled_mid").isSuccess)
        assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_disabled_max").isSuccess)

        val installedById = repository.getInstalledPacks().associateBy { it.packId }

        val zeroSummary = installedById["pack_disabled_zero"]!!
        assertFalse(zeroSummary.enabled)
        assertEquals(0, zeroSummary.logicalPriority)

        val midSummary = installedById["pack_disabled_mid"]!!
        assertFalse(midSummary.enabled)
        assertEquals(75, midSummary.logicalPriority)

        val maxSummary = installedById["pack_disabled_max"]!!
        assertFalse(maxSummary.enabled)
        assertEquals(Int.MAX_VALUE, maxSummary.logicalPriority)

        // Verify stored priorities in DB remain negative (disabled) and were NOT reactivated
        assertEquals(-1, dao.getPackMetadataById("pack_disabled_zero")!!.packPriority)
        assertEquals(-76, dao.getPackMetadataById("pack_disabled_mid")!!.packPriority)
        assertEquals(Int.MIN_VALUE, dao.getPackMetadataById("pack_disabled_max")!!.packPriority)
    }

    // =========================================================================
    // Pack Details Tests (6–10)
    // =========================================================================

    @Test
    fun test06_getInstalledPackDetails_returnsPackSummaryAndTopics_whenPackExists() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val pack = buildPackMetadata("marine_pack", packPriority = 60, packVersion = 2, topicCount = 2)
        val topics = listOf(
            buildSyntheticTopicBundle("coral_reefs", "marine_pack", topicOrderIndex = 0, packPriority = 60, contentVersion = 4),
            buildSyntheticTopicBundle("deep_ocean", "marine_pack", topicOrderIndex = 1, packPriority = 60, contentVersion = 4)
        )
        assertTrue(repository.upsertPackAndRefreshSnapshot(pack, topics).isSuccess)

        val details = repository.getInstalledPackDetails("marine_pack")
        assertNotNull(details)
        assertEquals("marine_pack", details!!.packId)
        assertEquals(60, details.summary.logicalPriority)
        assertTrue(details.summary.enabled)
        assertEquals(2, details.summary.packVersion)
        assertEquals(2, details.topics.size)
        assertEquals(2, details.activeWinningTopicCount)
        assertEquals(0, details.shadowedTopicCount)
        assertEquals(0, details.disabledTopicCount)
    }

    @Test
    fun test07_getInstalledPackDetails_returnsNull_whenPackDoesNotExistOrBlank() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        assertNull(repository.getInstalledPackDetails("non_existent_pack"))
        assertNull(repository.getInstalledPackDetails(""))
        assertNull(repository.getInstalledPackDetails("   "))
    }

    @Test
    fun test08_getInstalledPackDetails_returnsOnlyTopicsBelongingToPack_inDeterministicOrder() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val packA = buildPackMetadata("pack_alpha", packPriority = 50, topicCount = 3)
        val topicsA = listOf(
            buildSyntheticTopicBundle("topic_third", "pack_alpha", topicOrderIndex = 20, packPriority = 50),
            buildSyntheticTopicBundle("topic_first", "pack_alpha", topicOrderIndex = 5, packPriority = 50),
            buildSyntheticTopicBundle("topic_second", "pack_alpha", topicOrderIndex = 10, packPriority = 50)
        )
        val packB = buildPackMetadata("pack_beta", packPriority = 70, topicCount = 1)
        val topicsB = listOf(
            buildSyntheticTopicBundle("other_pack_topic", "pack_beta", topicOrderIndex = 0, packPriority = 70)
        )

        assertTrue(repository.upsertPackAndRefreshSnapshot(packA, topicsA).isSuccess)
        assertTrue(repository.upsertPackAndRefreshSnapshot(packB, topicsB).isSuccess)

        val detailsA = repository.getInstalledPackDetails("pack_alpha")
        assertNotNull(detailsA)
        assertEquals(
            listOf("topic_first", "topic_second", "topic_third"),
            detailsA!!.topics.map { it.topicId }
        )
        assertTrue(detailsA.topics.all { it.packId == "pack_alpha" })
    }

    @Test
    fun test09_getInstalledPackDetails_distinguishesActiveWinner_shadowed_disabledPack_andProtectedCore() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        // Pack Low (priority 40): has "shared_science" (will be shadowed by Pack High) and "unique_low" (wins)
        val packLow = buildPackMetadata("pack_low", packPriority = 40, topicCount = 2)
        val topicsLow = listOf(
            buildSyntheticTopicBundle("shared_science", "pack_low", topicOrderIndex = 0, packPriority = 40, contentVersion = 1),
            buildSyntheticTopicBundle("unique_low", "pack_low", topicOrderIndex = 1, packPriority = 40, contentVersion = 1)
        )
        assertTrue(repository.upsertPackAndRefreshSnapshot(packLow, topicsLow).isSuccess)

        // Pack High (priority 90): overrides "shared_science"
        val packHigh = buildPackMetadata("pack_high", packPriority = 90, topicCount = 1)
        val topicsHigh = listOf(
            buildSyntheticTopicBundle("shared_science", "pack_high", topicOrderIndex = 0, packPriority = 90, contentVersion = 2)
        )
        assertTrue(repository.upsertPackAndRefreshSnapshot(packHigh, topicsHigh).isSuccess)

        // 1. Inspect pack_low while enabled: "shared_science" is SHADOWED, "unique_low" is ACTIVE_WINNER
        val lowDetails = repository.getInstalledPackDetails("pack_low")!!
        assertEquals(1, lowDetails.activeWinningTopicCount)
        assertEquals(1, lowDetails.shadowedTopicCount)
        val lowTopicsById = lowDetails.topics.associateBy { it.topicId }

        val shadowedTopic = lowTopicsById["shared_science"]!!
        assertFalse(shadowedTopic.isActiveWinner)
        assertTrue(shadowedTopic.packEnabled)
        assertEquals(InstalledPackTopicState.SHADOWED, shadowedTopic.status)
        assertEquals("pack_high", shadowedTopic.currentWinningPackId)

        val winningTopic = lowTopicsById["unique_low"]!!
        assertTrue(winningTopic.isActiveWinner)
        assertTrue(winningTopic.packEnabled)
        assertEquals(InstalledPackTopicState.ACTIVE_WINNER, winningTopic.status)
        assertEquals("pack_low", winningTopic.currentWinningPackId)

        // 2. Deactivate pack_low and inspect again: both topics report DISABLED_PACK with decoded logicalPriority = 40
        assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_low").isSuccess)
        val disabledDetails = repository.getInstalledPackDetails("pack_low")!!
        assertFalse(disabledDetails.summary.enabled)
        assertEquals(40, disabledDetails.summary.logicalPriority)
        assertEquals(0, disabledDetails.activeWinningTopicCount)
        assertEquals(2, disabledDetails.disabledTopicCount)
        assertTrue(
            disabledDetails.topics.all {
                !it.isActiveWinner &&
                    !it.packEnabled &&
                    it.logicalPriority == 40 &&
                    it.status == InstalledPackTopicState.DISABLED_PACK
            }
        )

        // 3. Inspect core_seed_pack: all 11 topics report PROTECTED_CORE and isActiveWinner = true
        val seedDetails = repository.getInstalledPackDetails(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)!!
        assertEquals(SeedKnowledgeCatalog.coreTopics.size, seedDetails.topics.size)
        assertTrue(
            seedDetails.topics.all {
                it.isCoreTopic &&
                    it.isActiveWinner &&
                    it.status == InstalledPackTopicState.PROTECTED_CORE &&
                    it.currentWinningPackId == SeedKnowledgeCatalog.CORE_SEED_PACK_ID
            }
        )
    }

    @Test
    fun test10_getInstalledPackDetails_doesNotLoadTopicDetailsOrPopulateDetailCache() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val pack = buildPackMetadata("lazy_check_pack", packPriority = 75, topicCount = 3)
        val topics = (0 until 3).map { idx ->
            buildSyntheticTopicBundle(
                topicId = "lazy_topic_$idx",
                packId = "lazy_check_pack",
                topicOrderIndex = idx,
                packPriority = 75,
                includeDetail = true
            )
        }
        assertTrue(repository.upsertPackAndRefreshSnapshot(pack, topics).isSuccess)
        repository.resetMetricsForTesting()
        assertEquals(0, repository.cache.snapshotStats().detailStats.entryCount)
        assertEquals(0, repository.totalDetailDatabaseLoads)

        val details = repository.getInstalledPackDetails("lazy_check_pack")
        assertNotNull(details)
        assertEquals(3, details!!.topics.size)

        // DetailCache and detailDatabaseLoadCounter must remain at 0
        assertEquals(0, repository.cache.snapshotStats().detailStats.entryCount)
        assertEquals(0, repository.totalDetailDatabaseLoads)
    }

    // =========================================================================
    // Topic Version Candidates Tests (11–16)
    // =========================================================================

    @Test
    fun test11_getTopicVersionCandidates_returnsAllCompetingVersionsAcrossPacks() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val pack1 = buildPackMetadata("pack_v1", packPriority = 30)
        val pack2 = buildPackMetadata("pack_v2", packPriority = 60)
        val pack3 = buildPackMetadata("pack_v3", packPriority = 90)

        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                pack1,
                listOf(buildSyntheticTopicBundle("renewable_energy", "pack_v1", packPriority = 30, contentVersion = 1))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                pack2,
                listOf(buildSyntheticTopicBundle("renewable_energy", "pack_v2", packPriority = 60, contentVersion = 2))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                pack3,
                listOf(buildSyntheticTopicBundle("renewable_energy", "pack_v3", packPriority = 90, contentVersion = 3))
            ).isSuccess
        )

        val candidates = repository.getTopicVersionCandidates("renewable_energy")
        assertEquals(3, candidates.size)
        assertEquals(listOf("pack_v3", "pack_v2", "pack_v1"), candidates.map { it.packId })
    }

    @Test
    fun test12_getTopicVersionCandidates_selectsWinnerStrictlyViaC1Policy() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val packLow = buildPackMetadata("pack_low", packPriority = 25)
        val packHigh = buildPackMetadata("pack_high", packPriority = 85)

        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packLow,
                listOf(buildSyntheticTopicBundle("quantum_computing", "pack_low", packPriority = 25, contentVersion = 10))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packHigh,
                listOf(buildSyntheticTopicBundle("quantum_computing", "pack_high", packPriority = 85, contentVersion = 1))
            ).isSuccess
        )

        val candidates = repository.getTopicVersionCandidates("quantum_computing")
        assertEquals(2, candidates.size)
        assertEquals(1, candidates.count { it.isCurrentWinner })

        val winner = candidates.first()
        assertTrue(winner.isCurrentWinner)
        assertEquals("pack_high", winner.packId)
        assertEquals(85, winner.packPriority)
        assertEquals(1, winner.contentVersion)

        val runnerUp = candidates[1]
        assertFalse(runnerUp.isCurrentWinner)
        assertEquals("pack_low", runnerUp.packId)
        assertEquals(25, runnerUp.packPriority)
    }

    @Test
    fun test13_getTopicVersionCandidates_excludesDisabledPacksFromWinning() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val packHigh = buildPackMetadata("pack_high_disabled", packPriority = 500)
        val packFallback = buildPackMetadata("pack_fallback_enabled", packPriority = 20)

        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packHigh,
                listOf(buildSyntheticTopicBundle("ai_ethics", "pack_high_disabled", packPriority = 500, contentVersion = 5))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packFallback,
                listOf(buildSyntheticTopicBundle("ai_ethics", "pack_fallback_enabled", packPriority = 20, contentVersion = 1))
            ).isSuccess
        )

        assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_high_disabled").isSuccess)

        val candidates = repository.getTopicVersionCandidates("ai_ethics")
        assertEquals(2, candidates.size)

        // Enabled fallback pack must be first and marked as winner
        val first = candidates[0]
        assertEquals("pack_fallback_enabled", first.packId)
        assertTrue(first.packEnabled)
        assertTrue(first.isCurrentWinner)
        assertEquals(20, first.packPriority)

        // Disabled high-priority pack must still be returned with packEnabled = false and decoded priority 500
        val second = candidates[1]
        assertEquals("pack_high_disabled", second.packId)
        assertFalse(second.packEnabled)
        assertFalse(second.isCurrentWinner)
        assertEquals(500, second.packPriority)
    }

    @Test
    fun test14_getTopicVersionCandidates_preservesCoreSeedProtection_evenAgainstHigherPriorityExternalCandidate() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        // Insert a rogue high-priority external candidate for protected core topic "battery_care" directly via DAO
        val roguePack = buildPackMetadata("rogue_external_pack", packPriority = Int.MAX_VALUE)
        val rogueBundle = buildSyntheticTopicBundle(
            topicId = "battery_care",
            packId = "rogue_external_pack",
            packPriority = Int.MAX_VALUE,
            contentVersion = 999,
            isCoreTopic = true
        )
        dao.upsertPackWithTopicsTransactional(roguePack, listOf(rogueBundle))

        val candidates = repository.getTopicVersionCandidates("battery_care")
        assertEquals(2, candidates.size)
        assertEquals(1, candidates.count { it.isCurrentWinner })

        val winner = candidates.first()
        assertEquals(SeedKnowledgeCatalog.CORE_SEED_PACK_ID, winner.packId)
        assertTrue(winner.isCoreTopic)
        assertTrue(winner.isCurrentWinner)

        val rogueCandidate = candidates[1]
        assertEquals("rogue_external_pack", rogueCandidate.packId)
        assertFalse(rogueCandidate.isCoreTopic)
        assertFalse(rogueCandidate.isCurrentWinner)
    }

    @Test
    fun test15_getTopicVersionCandidates_resolvesPriorityAndContentVersionTiesDeterministicallyViaC1() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        // 3 packs with identical packPriority = 100:
        // - pack_gamma: contentVersion = 3 (wins by higher contentVersion)
        // - pack_alpha: contentVersion = 2 (runner-up by packId ASC tie-breaker over pack_beta)
        // - pack_beta:  contentVersion = 2
        val packBeta = buildPackMetadata("pack_beta", packPriority = 100)
        val packAlpha = buildPackMetadata("pack_alpha", packPriority = 100)
        val packGamma = buildPackMetadata("pack_gamma", packPriority = 100)

        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packBeta,
                listOf(buildSyntheticTopicBundle("tie_topic", "pack_beta", packPriority = 100, contentVersion = 2))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packAlpha,
                listOf(buildSyntheticTopicBundle("tie_topic", "pack_alpha", packPriority = 100, contentVersion = 2))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packGamma,
                listOf(buildSyntheticTopicBundle("tie_topic", "pack_gamma", packPriority = 100, contentVersion = 3))
            ).isSuccess
        )

        val candidates = repository.getTopicVersionCandidates("tie_topic")
        assertEquals(
            listOf("pack_gamma", "pack_alpha", "pack_beta"),
            candidates.map { it.packId }
        )
        assertTrue(candidates[0].isCurrentWinner)
        assertFalse(candidates[1].isCurrentWinner)
        assertFalse(candidates[2].isCurrentWinner)
    }

    @Test
    fun test16_getTopicVersionCandidates_isDeterministicAndIndependentOfDatabaseRowOrder() = runBlocking {
        val rawHeaders = listOf(
            buildSyntheticTopicBundle("perm_topic", "pack_c", packPriority = 50, contentVersion = 1).header,
            buildSyntheticTopicBundle("perm_topic", "pack_a", packPriority = 80, contentVersion = 2).header,
            buildSyntheticTopicBundle("perm_topic", "pack_b", packPriority = 80, contentVersion = 2).header,
            buildSyntheticTopicBundle(
                "perm_topic",
                "pack_disabled_top",
                packPriority = KnowledgePriorityAndWinnerPolicy.encodeDisabledPriority(900),
                contentVersion = 9
            ).header,
            buildSyntheticTopicBundle(
                "perm_topic",
                "pack_disabled_low",
                packPriority = KnowledgePriorityAndWinnerPolicy.encodeDisabledPriority(10),
                contentVersion = 1
            ).header
        )

        val baseline = KnowledgePackReadModelMapper.buildTopicVersionCandidateStatuses(rawHeaders)
        assertEquals(
            listOf("pack_a", "pack_b", "pack_c", "pack_disabled_top", "pack_disabled_low"),
            baseline.map { it.packId }
        )
        assertEquals("pack_a", baseline.single { it.isCurrentWinner }.packId)

        val rng = Random(20260421L)
        repeat(30) {
            val shuffled = rawHeaders.shuffled(rng)
            val result = KnowledgePackReadModelMapper.buildTopicVersionCandidateStatuses(shuffled)
            assertEquals(baseline, result)
        }
    }

    // =========================================================================
    // Read-Only Integrity & Source-of-Truth Consistency Tests (17–21)
    // =========================================================================

    @Test
    fun test17_18_19_20_readApis_areStrictlyReadOnly_andNeverMutateDatabaseSnapshotOrCaches() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val packActive = buildPackMetadata("pack_active_ro", packPriority = 70, packVersion = 2, topicCount = 1)
        val packDisabled = buildPackMetadata("pack_disabled_ro", packPriority = 120, packVersion = 1, topicCount = 1)

        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packActive,
                listOf(buildSyntheticTopicBundle("ro_topic", "pack_active_ro", packPriority = 70, contentVersion = 2))
            ).isSuccess
        )
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packDisabled,
                listOf(buildSyntheticTopicBundle("ro_topic", "pack_disabled_ro", packPriority = 120, contentVersion = 3))
            ).isSuccess
        )
        assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_disabled_ro").isSuccess)

        val packsBefore = dao.getAllPackMetadataOrdered()
        val activeHeadersBefore = dao.getTopicHeadersByPackOrdered("pack_active_ro")
        val disabledHeadersBefore = dao.getTopicHeadersByPackOrdered("pack_disabled_ro")
        val snapshotBefore = repository.currentSnapshot
        val generationBefore = repository.currentGeneration
        val detailCacheCountBefore = repository.cache.snapshotStats().detailStats.entryCount

        // Invoke all three Read APIs repeatedly
        val packsRead = repository.getInstalledPacks()
        val activeDetailsRead = repository.getInstalledPackDetails("pack_active_ro")
        val disabledDetailsRead = repository.getInstalledPackDetails("pack_disabled_ro")
        val missingDetailsRead = repository.getInstalledPackDetails("does_not_exist")
        val candidatesRead = repository.getTopicVersionCandidates("ro_topic")

        assertEquals(3, packsRead.size)
        assertNotNull(activeDetailsRead)
        assertNotNull(disabledDetailsRead)
        assertNull(missingDetailsRead)
        assertEquals(2, candidatesRead.size)

        // 17. Pack metadata rows are identical
        assertEquals(packsBefore, dao.getAllPackMetadataOrdered())

        // 18 & 19. Enabled states and stored priorities in DB are untouched
        assertEquals(70, dao.getPackMetadataById("pack_active_ro")!!.packPriority)
        assertEquals(
            KnowledgePriorityAndWinnerPolicy.encodeDisabledPriority(120),
            dao.getPackMetadataById("pack_disabled_ro")!!.packPriority
        )
        assertEquals(activeHeadersBefore, dao.getTopicHeadersByPackOrdered("pack_active_ro"))
        assertEquals(disabledHeadersBefore, dao.getTopicHeadersByPackOrdered("pack_disabled_ro"))

        // 20. Snapshot instance, generation counter, and DetailCache are untouched
        assertSame(snapshotBefore, repository.currentSnapshot)
        assertEquals(generationBefore, repository.currentGeneration)
        assertEquals(detailCacheCountBefore, repository.cache.snapshotStats().detailStats.entryCount)
    }

    @Test
    fun test21_readApis_reflectAuthoritativeDatabaseWinner_evenWhenPublishedSnapshotIsStale() = runBlocking {
        assertTrue(repository.initializeSeedAndWarmUpIndex())

        val packInitial = buildPackMetadata("pack_initial", packPriority = 40)
        assertTrue(
            repository.upsertPackAndRefreshSnapshot(
                packInitial,
                listOf(buildSyntheticTopicBundle("space_station", "pack_initial", packPriority = 40, contentVersion = 1))
            ).isSuccess
        )
        assertEquals("pack_initial", repository.currentSnapshot.getActiveIdentity("space_station")?.packId)

        // Upsert a higher-priority pack where DB transaction commits, but snapshot rebuild fails
        val packOverride = buildPackMetadata("pack_override", packPriority = 95)
        val mutationResult = repository.upsertPackAndRefreshSnapshot(
            pack = packOverride,
            topics = listOf(buildSyntheticTopicBundle("space_station", "pack_override", packPriority = 95, contentVersion = 2)),
            simulatedSnapshotBuildFailure = {
                throw IllegalStateException("Simulated post-commit snapshot failure")
            }
        )
        assertTrue(mutationResult is PackMutationResult.SnapshotRebuildFailed)

        // Published snapshot is still stale ("pack_initial"), but DB has committed "pack_override"
        assertEquals("pack_initial", repository.currentSnapshot.getActiveIdentity("space_station")?.packId)

        // Read APIs MUST report the authoritative DB state ("pack_override" as winner, "pack_initial" as SHADOWED)
        val candidates = repository.getTopicVersionCandidates("space_station")
        assertEquals(2, candidates.size)
        assertEquals("pack_override", candidates.first { it.isCurrentWinner }.packId)

        val initialDetails = repository.getInstalledPackDetails("pack_initial")!!
        val initialTopicStatus = initialDetails.topics.single()
        assertFalse(initialTopicStatus.isActiveWinner)
        assertEquals(InstalledPackTopicState.SHADOWED, initialTopicStatus.status)
        assertEquals("pack_override", initialTopicStatus.currentWinningPackId)

        val overrideDetails = repository.getInstalledPackDetails("pack_override")!!
        val overrideTopicStatus = overrideDetails.topics.single()
        assertTrue(overrideTopicStatus.isActiveWinner)
        assertEquals(InstalledPackTopicState.ACTIVE_WINNER, overrideTopicStatus.status)
        assertEquals("pack_override", overrideTopicStatus.currentWinningPackId)
    }
}
