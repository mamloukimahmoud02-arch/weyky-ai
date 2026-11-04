package com.example.core.intelligence.knowledge

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.conversation.LocalKnowledgeBase
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
import com.example.core.intelligence.knowledge.importer.KnowledgePackImportStatus
import com.example.core.intelligence.knowledge.importer.KnowledgePackImporter
import com.example.core.intelligence.knowledge.importer.SnapshotRefreshState
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.repository.PackMutationResult
import com.example.core.intelligence.knowledge.repository.TopicDetailLoadStatus
import com.example.core.intelligence.knowledge.validation.KnowledgeValidator
import com.example.core.intelligence.knowledge.validation.ValidationIssueCategory
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
 * Phase 2B.4.2C.3 — Knowledge Pack Reference Integrity Tests.
 *
 * Verifies all 20 required reference-integrity scenarios across:
 * - Room SQLite foreign key constraints (`ON DELETE CASCADE`) across all 6 tables.
 * - Application-level bundle ownership and token count validation in [KnowledgeValidator].
 * - Parser and importer reference integrity validation in [KnowledgePackImporter].
 * - Repository lifecycle mutations ([KnowledgeRepository.upsertPackAndRefreshSnapshot],
 *   [KnowledgeRepository.deactivatePackAndRefreshSnapshot],
 *   [KnowledgeRepository.reactivatePackAndRefreshSnapshot],
 *   [KnowledgeRepository.updatePackPriorityAndRefreshSnapshot],
 *   [KnowledgeRepository.deletePackAndRefreshSnapshot]).
 * - Winner recalculation, transactional rollback, post-commit snapshot recovery,
 *   and targeted cache invalidation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2B4C23KnowledgePackReferenceIntegrityTest {

    private lateinit var context: Context
    private lateinit var database: WeykyKnowledgeDatabase
    private lateinit var dao: KnowledgeDao
    private lateinit var repository: KnowledgeRepository
    private lateinit var importer: KnowledgePackImporter
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
    }

    @After
    fun tearDown() {
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

    private fun buildSyntheticPackMetadata(
        packId: String,
        priority: Int = 60,
        topicCount: Int = 1,
        packVersion: Int = 1,
        sourceType: String = "BUNDLED_ASSET"
    ): KnowledgePackMetadataEntity {
        return KnowledgePackMetadataEntity(
            packId = packId,
            sourceType = sourceType,
            packVersion = packVersion,
            schemaVersion = 1,
            sha256Checksum = buildSha256Hex("${packId}_v${packVersion}_p${priority}_c$topicCount"),
            packPriority = priority,
            importedAt = 1_715_000_000_000L,
            topicCount = topicCount
        )
    }

    private fun buildSyntheticTopicBundle(
        topicId: String,
        packId: String,
        topicOrderIndex: Int = 100,
        packPriority: Int = 60,
        contentVersion: Int = 1,
        sourceType: String = "BUNDLED_ASSET",
        isCoreTopic: Boolean = false,
        arabicSummary: String = "ملخص معرفي للموضوع $topicId من الحزمة $packId الإصدار $contentVersion.",
        englishSummary: String = "Knowledge summary for $topicId from pack $packId v$contentVersion.",
        keywords: List<String> = listOf("كلمه $topicId", "موضوع $topicId", "topic $topicId"),
        includeDetail: Boolean = true,
        detailedArabic: String = "تفصيل موسع للموضوع $topicId في الحزمة $packId (v$contentVersion).",
        detailedEnglish: String = "Extended detail for $topicId in pack $packId (v$contentVersion).",
        relatedTopicIds: List<String> = emptyList(),
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
                relatedTopicIdsJson = converters.fromStringList(relatedTopicIds),
                examplesJson = converters.fromStringList(listOf("مثال تطبيقي لـ $topicId")),
                lastReviewed = 1_715_000_000_000L + contentVersion,
                additionalContentJson = """{"pack":"$packId","version":$contentVersion}"""
            )
        } else {
            null
        }

        val branches = followUpTriggers.mapIndexed { index, (trigger, replyAr) ->
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

        return TopicStorageBundle(
            header = header,
            keywordsWithTokens = kwWithTokens,
            detail = detail,
            followUpBranches = branches
        )
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
          "description": "Reference integrity test pack for $packId",
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
        arabicSummary: String = "ملخص تفصيلي للموضوع $topicId للتحقق من سلامة المراجع والعلاقات.",
        englishSummary: String = "Detailed summary for $topicId to verify reference integrity and relationships.",
        keywords: List<String> = listOf("مفهوم $topicId", "concept $topicId", "علم $topicId"),
        referenceTopicIds: List<String> = emptyList(),
        includeDetail: Boolean = true,
        detailedArabic: String = "شرح علمي مفصل للموضوع $topicId يغطي الجوانب الأساسية والتطبيقات.",
        detailedEnglish: String = "Detailed scientific explanation for $topicId covering core aspects and applications.",
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
            """,
              "followUpBranches": []"""
        }

        return """
            {
              "topicId": "$topicId",
              "title": "$englishTitle",
              "arabicTitle": "$arabicTitle",
              "englishTitle": "$englishTitle",
              "category": "science",
              "topicOrderIndex": $topicOrderIndex,
              "priority": $priority,
              "contentVersion": $contentVersion,
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
    // 1. Topic referencing nonexistent pack is rejected
    // =========================================================================
    @Test
    fun test01_topicReferencingNonexistentPackIsRejectedByFkAndValidator() = runBlocking {
        val pack = buildSyntheticPackMetadata(packId = "pack_real", topicCount = 1)
        val mismatchedBundle = buildSyntheticTopicBundle(
            topicId = "topic_orphan_pack",
            packId = "nonexistent_pack"
        )

        // Application-level validation rejects topic whose packId != pack.packId
        val validation = KnowledgeValidator.validatePackBundles(pack, listOf(mismatchedBundle))
        assertFalse(validation.isValid)
        assertTrue(validation.issues.any { it.category == ValidationIssueCategory.MISSING_REFERENCE })

        // Repository mutation rejects and does not write anything
        val mutationResult = repository.upsertPackAndRefreshSnapshot(pack, listOf(mismatchedBundle))
        assertTrue(mutationResult is PackMutationResult.ValidationRejected)
        assertNull(dao.getPackMetadataById("pack_real"))
        assertNull(dao.getTopicHeaderByIdAndPack("topic_orphan_pack", "nonexistent_pack"))

        // Database-level SQLite FK constraint rejects direct DAO insertion of a topic referencing a nonexistent pack
        var fkThrown = false
        try {
            dao.upsertTopicHeader(mismatchedBundle.header)
        } catch (_: SQLiteConstraintException) {
            fkThrown = true
        }
        assertTrue("Expected SQLiteConstraintException when inserting topic for nonexistent pack", fkThrown)
    }

    // =========================================================================
    // 2. Keyword referencing nonexistent topic is rejected
    // =========================================================================
    @Test
    fun test02_keywordReferencingNonexistentTopicIsRejectedByFkAndValidator() = runBlocking {
        val pack = buildSyntheticPackMetadata(packId = "pack_kw_orphan", topicCount = 1)
        val baseBundle = buildSyntheticTopicBundle(
            topicId = "topic_real",
            packId = "pack_kw_orphan"
        )
        val firstKwBundle = baseBundle.keywordsWithTokens.first()
        val orphanKeyword = firstKwBundle.keyword.copy(topicId = "topic_nonexistent")
        val corruptedBundle = baseBundle.copy(
            keywordsWithTokens = listOf(firstKwBundle.copy(keyword = orphanKeyword))
        )

        val validation = KnowledgeValidator.validatePackBundles(pack, listOf(corruptedBundle))
        assertFalse(validation.isValid)
        assertTrue(validation.issues.any { it.category == ValidationIssueCategory.MISSING_REFERENCE })

        // Direct SQLite FK enforcement check
        dao.upsertPackMetadata(pack)
        var fkThrown = false
        try {
            dao.insertKeyword(orphanKeyword)
        } catch (_: SQLiteConstraintException) {
            fkThrown = true
        }
        assertTrue("Expected SQLiteConstraintException when inserting keyword for nonexistent topic", fkThrown)
    }

    // =========================================================================
    // 3. Keyword associated with wrong pack or wrong bundle topic is rejected
    // =========================================================================
    @Test
    fun test03_keywordAssociatedWithWrongPackOrWrongBundleTopicIsRejected() = runBlocking {
        val pack = buildSyntheticPackMetadata(packId = "pack_bundle_check", topicCount = 2)
        val bundleA = buildSyntheticTopicBundle(topicId = "topic_a", packId = "pack_bundle_check")
        val bundleB = buildSyntheticTopicBundle(topicId = "topic_b", packId = "pack_bundle_check")

        // Case A: Keyword inside bundleA claims ownership by topic_b (even though topic_b exists in the same pack!)
        val firstKwA = bundleA.keywordsWithTokens.first()
        val crossTopicKeyword = firstKwA.keyword.copy(topicId = "topic_b")
        val bundleAWithWrongTopicKw = bundleA.copy(
            keywordsWithTokens = listOf(firstKwA.copy(keyword = crossTopicKeyword)) + bundleA.keywordsWithTokens.drop(1)
        )

        val crossTopicValidation = KnowledgeValidator.validatePackBundles(
            pack = pack,
            bundles = listOf(bundleAWithWrongTopicKw, bundleB)
        )
        assertFalse("Validator must reject keyword inside bundleA referencing topic_b", crossTopicValidation.isValid)
        assertTrue(
            crossTopicValidation.issues.any {
                it.category == ValidationIssueCategory.MISSING_REFERENCE &&
                    it.field == "keyword.topicId/packId"
            }
        )

        // Case B: Keyword inside bundleA claims ownership by wrong pack
        val wrongPackKeyword = firstKwA.keyword.copy(packId = "other_pack")
        val bundleAWithWrongPackKw = bundleA.copy(
            keywordsWithTokens = listOf(firstKwA.copy(keyword = wrongPackKeyword)) + bundleA.keywordsWithTokens.drop(1)
        )
        val wrongPackValidation = KnowledgeValidator.validatePackBundles(
            pack = pack,
            bundles = listOf(bundleAWithWrongPackKw, bundleB)
        )
        assertFalse("Validator must reject keyword with wrong packId", wrongPackValidation.isValid)
        assertTrue(wrongPackValidation.issues.any { it.category == ValidationIssueCategory.MISSING_REFERENCE })
    }

    // =========================================================================
    // 4. Keyword token referencing nonexistent keyword or mismatched count is rejected
    // =========================================================================
    @Test
    fun test04_keywordTokenReferencingNonexistentKeywordOrMismatchedCountIsRejected() = runBlocking {
        val pack = buildSyntheticPackMetadata(packId = "pack_token_check", topicCount = 1)
        val bundle = buildSyntheticTopicBundle(topicId = "topic_token", packId = "pack_token_check")

        // Case A: tokenCount says 5, but fewer tokens are supplied in bundle
        val firstKw = bundle.keywordsWithTokens.first()
        val mismatchedKw = firstKw.keyword.copy(tokenCount = 5)
        val mismatchedBundle = bundle.copy(
            keywordsWithTokens = listOf(firstKw.copy(keyword = mismatchedKw)) + bundle.keywordsWithTokens.drop(1)
        )
        val validation = KnowledgeValidator.validatePackBundles(pack, listOf(mismatchedBundle))
        assertFalse("Validator must reject keyword whose tokenCount != tokens.size", validation.isValid)
        assertTrue(
            validation.issues.any {
                it.category == ValidationIssueCategory.MISSING_REFERENCE &&
                    it.field == "token.keywordId"
            }
        )

        // Case B: Direct SQLite FK check for token referencing nonexistent keywordId
        var fkThrown = false
        try {
            dao.insertKeywordTokens(
                listOf(
                    KnowledgeKeywordTokenEntity(
                        tokenId = 0L,
                        keywordId = 999_999L,
                        tokenPosition = 0,
                        strictToken = "يتيم",
                        strictStem = "يتيم",
                        relaxedToken = "يتيم",
                        relaxedStem = "يتيم"
                    )
                )
            )
        } catch (_: SQLiteConstraintException) {
            fkThrown = true
        }
        assertTrue("Expected SQLiteConstraintException for token referencing nonexistent keywordId", fkThrown)
    }

    // =========================================================================
    // 5. Detail referencing nonexistent topic or mismatched bundle owner is rejected
    // =========================================================================
    @Test
    fun test05_detailReferencingNonexistentTopicOrMismatchedBundleOwnerIsRejected() = runBlocking {
        val pack = buildSyntheticPackMetadata(packId = "pack_detail_check", topicCount = 2)
        val bundleA = buildSyntheticTopicBundle(topicId = "topic_a", packId = "pack_detail_check")
        val bundleB = buildSyntheticTopicBundle(topicId = "topic_b", packId = "pack_detail_check")

        // Detail inside bundleA claims topic_b
        val hijackedDetail = bundleA.detail!!.copy(topicId = "topic_b")
        val corruptedBundleA = bundleA.copy(detail = hijackedDetail)

        val validation = KnowledgeValidator.validatePackBundles(
            pack = pack,
            bundles = listOf(corruptedBundleA, bundleB)
        )
        assertFalse("Validator must reject detail inside bundleA referencing topic_b", validation.isValid)
        assertTrue(
            validation.issues.any {
                it.category == ValidationIssueCategory.MISSING_REFERENCE &&
                    it.field == "detail.topicId/packId"
            }
        )

        // Direct SQLite FK check for detail referencing nonexistent (topicId, packId)
        dao.upsertPackMetadata(pack)
        var fkThrown = false
        try {
            dao.upsertTopicDetail(
                bundleA.detail!!.copy(topicId = "nonexistent_topic", packId = "pack_detail_check")
            )
        } catch (_: SQLiteConstraintException) {
            fkThrown = true
        }
        assertTrue("Expected SQLiteConstraintException for detail referencing nonexistent topic", fkThrown)
    }

    // =========================================================================
    // 6. Follow-up branch referencing nonexistent owner topic is rejected
    // =========================================================================
    @Test
    fun test06_followUpBranchReferencingNonexistentOwnerTopicIsRejected() = runBlocking {
        val pack = buildSyntheticPackMetadata(packId = "pack_branch_check", topicCount = 2)
        val bundleA = buildSyntheticTopicBundle(topicId = "topic_a", packId = "pack_branch_check")
        val bundleB = buildSyntheticTopicBundle(topicId = "topic_b", packId = "pack_branch_check")

        // Follow-up branch inside bundleA claims topic_b
        val wrongOwnerBranch = bundleA.followUpBranches.first().copy(topicId = "topic_b")
        val corruptedBundleA = bundleA.copy(followUpBranches = listOf(wrongOwnerBranch))

        val validation = KnowledgeValidator.validatePackBundles(
            pack = pack,
            bundles = listOf(corruptedBundleA, bundleB)
        )
        assertFalse("Validator must reject follow-up branch inside bundleA referencing topic_b", validation.isValid)
        assertTrue(
            validation.issues.any {
                it.category == ValidationIssueCategory.MISSING_REFERENCE &&
                    it.field == "branch.topicId/packId"
            }
        )

        // Direct SQLite FK check for follow-up branch referencing nonexistent (topicId, packId)
        dao.upsertPackMetadata(pack)
        var fkThrown = false
        try {
            dao.upsertFollowUpBranches(
                listOf(bundleA.followUpBranches.first().copy(topicId = "nonexistent_topic"))
            )
        } catch (_: SQLiteConstraintException) {
            fkThrown = true
        }
        assertTrue("Expected SQLiteConstraintException for branch referencing nonexistent topic", fkThrown)
    }

    // =========================================================================
    // 7. Related/destination topic referencing nonexistent or inactive topic is rejected
    // =========================================================================
    @Test
    fun test07_relatedDestinationTopicReferencingNonexistentOrInactiveTopicIsRejected() = runBlocking {
        // Case A: Importer rejects pack whose detail.relatedTopicIds references a nonexistent topic
        val jsonMissingRef = buildPackJsonWithTopics(
            packId = "pack_bad_ref",
            topicsJson = buildTopicJsonNode(
                topicId = "topic_with_broken_ref",
                relatedTopicIds = listOf("completely_nonexistent_topic_xyz")
            )
        )
        val importResultA = importer.importFromJsonString(jsonMissingRef)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, importResultA.status)
        assertNull(dao.getPackMetadataById("pack_bad_ref"))

        // Case B: Importer rejects pack when topic-level referenceTopicIds has duplicates AND detail.relatedTopicIds has a nonexistent reference
        val jsonCombinedDupAndMissing = buildPackJsonWithTopics(
            packId = "pack_dup_and_missing",
            topicsJson = buildTopicJsonNode(
                topicId = "topic_combined",
                referenceTopicIds = listOf("ai_concept", "ai_concept"),
                relatedTopicIds = listOf("nonexistent_detail_target")
            )
        )
        val importResultB = importer.importFromJsonString(jsonCombinedDupAndMissing)
        assertFalse(importResultB.isSuccess)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, importResultB.status)
        assertTrue(importResultB.errors.any { it.contains("duplicate", ignoreCase = true) })
        assertTrue(importResultB.errors.any { it.contains("nonexistent_detail_target") })

        // Case C: Direct repository call without explicit referenceTopicIds still validates against active winners in DB
        val directMeta = buildSyntheticPackMetadata(packId = "pack_direct_bad_ref", topicCount = 1)
        val directBundle = buildSyntheticTopicBundle(
            topicId = "topic_direct_bad_ref",
            packId = "pack_direct_bad_ref",
            relatedTopicIds = listOf("nonexistent_direct_target")
        )
        val directResult = repository.upsertPackAndRefreshSnapshot(directMeta, listOf(directBundle))
        assertTrue(
            "Direct repository upsert must validate relatedTopicIds against active DB topics",
            directResult is PackMutationResult.ValidationRejected
        )

        // Case D: Inactive pack's topic cannot satisfy reference integrity for an active pack import
        val providerJson = buildPackJsonWithTopics(
            packId = "pack_inactive_provider",
            topicsJson = buildTopicJsonNode(topicId = "inactive_only_topic")
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(providerJson).status)
        assertTrue(repository.deactivatePackAndRefreshSnapshot("pack_inactive_provider").isSuccess)

        val dependentJson = buildPackJsonWithTopics(
            packId = "pack_active_consumer",
            topicsJson = buildTopicJsonNode(
                topicId = "consumer_topic",
                relatedTopicIds = listOf("inactive_only_topic")
            )
        )
        val dependentImport = importer.importFromJsonString(dependentJson)
        assertEquals(
            "Active pack referencing topic that only exists in an inactive pack must be rejected",
            KnowledgePackImportStatus.REFERENCE_INVALID,
            dependentImport.status
        )
    }

    // =========================================================================
    // 8. Invalid cross-pack ownership & broken incoming cross-pack references are rejected
    // =========================================================================
    @Test
    fun test08_invalidCrossPackOwnershipAndBrokenIncomingCrossPackReferenceAreRejected() = runBlocking {
        // First import Provider Pack with topic_provider_1 and topic_provider_2
        val providerV1Json = buildPackJsonWithTopics(
            packId = "pack_provider",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 60,
            topicsJson = listOf(
                buildTopicJsonNode(topicId = "topic_provider_1"),
                buildTopicJsonNode(topicId = "topic_provider_2")
            ).joinToString(",\n")
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(providerV1Json).status)

        // Import Consumer Pack whose topic_consumer references topic_provider_2
        val consumerJson = buildPackJsonWithTopics(
            packId = "pack_consumer",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 70,
            topicsJson = buildTopicJsonNode(
                topicId = "topic_consumer",
                relatedTopicIds = listOf("topic_provider_2")
            )
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(consumerJson).status)

        // Now attempt to update Provider Pack to v2 removing topic_provider_2 while Consumer Pack still references it
        val providerV2DroppingReferencedTopicJson = buildPackJsonWithTopics(
            packId = "pack_provider",
            packVersion = 2,
            contentVersion = 2,
            packPriority = 60,
            topicsJson = buildTopicJsonNode(topicId = "topic_provider_1", contentVersion = 2)
        )
        val updateResult = importer.importFromJsonString(providerV2DroppingReferencedTopicJson)
        assertEquals(
            "Updating pack_provider to drop topic_provider_2 must be rejected because pack_consumer references it",
            KnowledgePackImportStatus.REFERENCE_INVALID,
            updateResult.status
        )

        // Verify topic_provider_2 is still intact and active in DB
        val provider2Header = dao.getActiveTopicHeader("topic_provider_2")
        assertNotNull(provider2Header)
        assertEquals("pack_provider", provider2Header?.packId)
    }

    // =========================================================================
    // 9. Duplicate or conflicting records are rejected
    // =========================================================================
    @Test
    fun test09_duplicateOrConflictingRecordsAreRejected() = runBlocking {
        // Duplicate topic IDs in same pack
        val dupTopicJson = buildPackJsonWithTopics(
            packId = "pack_dup_topic",
            topicsJson = listOf(
                buildTopicJsonNode(topicId = "same_topic"),
                buildTopicJsonNode(topicId = "same_topic")
            ).joinToString(",\n")
        )
        val res1 = importer.importFromJsonString(dupTopicJson)
        assertFalse(res1.isSuccess)
        assertEquals(KnowledgePackImportStatus.DUPLICATE_CONFLICT, res1.status)

        // Duplicate keywords in same topic
        val dupKeywordJson = buildPackJsonWithTopics(
            packId = "pack_dup_kw",
            topicsJson = buildTopicJsonNode(
                topicId = "topic_dup_kw",
                keywords = listOf("نفس الكلمة", "نفس الكلمه")
            )
        )
        val res2 = importer.importFromJsonString(dupKeywordJson)
        assertFalse(res2.isSuccess)
        assertTrue(
            res2.status == KnowledgePackImportStatus.DUPLICATE_CONFLICT ||
                res2.status == KnowledgePackImportStatus.VALIDATION_FAILED
        )

        // Self-reference in relatedTopicIds
        val selfRefJson = buildPackJsonWithTopics(
            packId = "pack_self_ref",
            topicsJson = buildTopicJsonNode(
                topicId = "topic_self",
                relatedTopicIds = listOf("topic_self")
            )
        )
        val res3 = importer.importFromJsonString(selfRefJson)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, res3.status)
    }

    // =========================================================================
    // 10. Pack update preserves valid relationships
    // =========================================================================
    @Test
    fun test10_packUpdatePreservesValidRelationships() = runBlocking {
        val v1Json = buildPackJsonWithTopics(
            packId = "pack_updatable",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 80,
            topicsJson = listOf(
                buildTopicJsonNode(topicId = "custom_topic_1", relatedTopicIds = listOf("custom_topic_2")),
                buildTopicJsonNode(topicId = "custom_topic_2", relatedTopicIds = listOf("ai_concept"))
            ).joinToString(",\n")
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(v1Json).status)

        val v2Json = buildPackJsonWithTopics(
            packId = "pack_updatable",
            packVersion = 2,
            contentVersion = 2,
            packPriority = 85,
            topicsJson = listOf(
                buildTopicJsonNode(
                    topicId = "custom_topic_1",
                    contentVersion = 2,
                    priority = 85,
                    arabicSummary = "ملخص محدث للموضوع الأول بعد الترقية للإصدار الثاني.",
                    relatedTopicIds = listOf("custom_topic_2")
                ),
                buildTopicJsonNode(
                    topicId = "custom_topic_2",
                    contentVersion = 2,
                    priority = 85,
                    arabicSummary = "ملخص محدث للموضوع الثاني بعد الترقية للإصدار الثاني.",
                    relatedTopicIds = listOf("ai_concept")
                )
            ).joinToString(",\n")
        )
        val v2Result = importer.importFromJsonString(v2Json)
        assertEquals(KnowledgePackImportStatus.SUCCESS_UPDATED, v2Result.status)

        // Verify metadata, headers, keywords, tokens, details, and follow-up branches are consistent
        val meta = dao.getPackMetadataById("pack_updatable")
        assertNotNull(meta)
        assertEquals(2, meta?.packVersion)
        assertEquals(85, meta?.packPriority)

        val t1Winner = dao.getActiveTopicHeader("custom_topic_1")
        assertNotNull(t1Winner)
        assertEquals("ملخص محدث للموضوع الأول بعد الترقية للإصدار الثاني.", t1Winner?.arabicSummary)

        val t1Keywords = dao.getKeywordsByTopicAndPackOrdered("custom_topic_1", "pack_updatable")
        assertEquals(3, t1Keywords.size)
        for (kw in t1Keywords) {
            val tokens = dao.getTokensByKeywordIdOrdered(kw.keywordId)
            assertEquals(kw.tokenCount, tokens.size)
        }

        val t1Detail = dao.getTopicDetail("custom_topic_1", "pack_updatable")
        assertNotNull(t1Detail)
        assertTrue(t1Detail!!.relatedTopicIdsJson.contains("custom_topic_2"))
    }

    // =========================================================================
    // 11. Pack replacement removes obsolete dependent records (including removed details)
    // =========================================================================
    @Test
    fun test11_packReplacementRemovesObsoleteDependentRecordsIncludingRemovedDetails() = runBlocking {
        // v1 has topic_keep (with detail & follow-up) and topic_obsolete (with detail, keywords, tokens, follow-up)
        val v1Json = buildPackJsonWithTopics(
            packId = "pack_replace_clean",
            packVersion = 1,
            contentVersion = 1,
            packPriority = 75,
            topicsJson = listOf(
                buildTopicJsonNode(
                    topicId = "topic_keep",
                    includeDetail = true,
                    keywords = listOf("كلمة اولى", "كلمة ثانية")
                ),
                buildTopicJsonNode(
                    topicId = "topic_obsolete",
                    includeDetail = true,
                    keywords = listOf("كلمة قديمة")
                )
            ).joinToString(",\n")
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(v1Json).status)
        assertNotNull(dao.getTopicDetail("topic_keep", "pack_replace_clean"))
        assertNotNull(dao.getTopicHeaderByIdAndPack("topic_obsolete", "pack_replace_clean"))
        assertNotNull(dao.getTopicDetail("topic_obsolete", "pack_replace_clean"))
        val oldKeywordIds = dao.getKeywordsByTopicAndPackOrdered("topic_obsolete", "pack_replace_clean").map { it.keywordId }
        assertFalse(oldKeywordIds.isEmpty())

        // v2 removes topic_obsolete AND downgrades topic_keep from having a detail to header-only (includeDetail = false)
        val v2Json = buildPackJsonWithTopics(
            packId = "pack_replace_clean",
            packVersion = 2,
            contentVersion = 2,
            packPriority = 75,
            topicsJson = buildTopicJsonNode(
                topicId = "topic_keep",
                contentVersion = 2,
                priority = 75,
                includeDetail = false,
                includeFollowUpBranches = false,
                keywords = listOf("كلمة محدثة")
            )
        )
        val v2Result = importer.importFromJsonString(v2Json)
        assertEquals(KnowledgePackImportStatus.SUCCESS_UPDATED, v2Result.status)

        // Verify topic_obsolete and all its dependent children were cascaded/removed
        assertNull(dao.getTopicHeaderByIdAndPack("topic_obsolete", "pack_replace_clean"))
        assertTrue(dao.getKeywordsByTopicAndPackOrdered("topic_obsolete", "pack_replace_clean").isEmpty())
        for (kwId in oldKeywordIds) {
            assertTrue(dao.getTokensByKeywordIdOrdered(kwId).isEmpty())
        }
        assertNull(dao.getTopicDetail("topic_obsolete", "pack_replace_clean"))
        assertTrue(dao.getFollowUpBranchesOrdered("topic_obsolete", "pack_replace_clean").isEmpty())

        // Verify topic_keep's obsolete detail and obsolete follow-up branches were removed!
        assertNotNull(dao.getTopicHeaderByIdAndPack("topic_keep", "pack_replace_clean"))
        assertNull(
            "Obsolete detail row must be deleted when topic is updated with detail = null",
            dao.getTopicDetail("topic_keep", "pack_replace_clean")
        )
        assertTrue(
            "Obsolete follow-up branches must be deleted when updated with empty branches",
            dao.getFollowUpBranchesOrdered("topic_keep", "pack_replace_clean").isEmpty()
        )
        val keepKeywords = dao.getKeywordsByTopicAndPackOrdered("topic_keep", "pack_replace_clean")
        assertEquals(1, keepKeywords.size)
        assertEquals("كلمة محدثة", keepKeywords.first().originalKeyword)
    }

    // =========================================================================
    // 12. Pack disable prevents its topics from becoming active winners & protects dependents
    // =========================================================================
    @Test
    fun test12_packDisablePreventsItsTopicsFromBecomingActiveWinnersAndProtectsDependents() = runBlocking {
        // Import pack_solo with topic_solo
        val soloJson = buildPackJsonWithTopics(
            packId = "pack_solo",
            packVersion = 1,
            packPriority = 80,
            topicsJson = buildTopicJsonNode(topicId = "topic_solo")
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(soloJson).status)
        assertNotNull(dao.getActiveTopicHeader("topic_solo"))

        // Import pack_dep referencing topic_solo
        val depJson = buildPackJsonWithTopics(
            packId = "pack_dep",
            packVersion = 1,
            packPriority = 85,
            topicsJson = buildTopicJsonNode(topicId = "topic_dep", relatedTopicIds = listOf("topic_solo"))
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(depJson).status)

        // Attempting to deactivate pack_solo while pack_dep is active and references topic_solo must be rejected
        val blockedDeactivate = repository.deactivatePackAndRefreshSnapshot("pack_solo")
        assertTrue(
            "Deactivating pack_solo must be rejected while active pack_dep references topic_solo",
            blockedDeactivate is PackMutationResult.ValidationRejected
        )
        assertNotNull("topic_solo must remain active winner after rejected deactivation", dao.getActiveTopicHeader("topic_solo"))

        // First deactivate pack_dep, then deactivate pack_solo
        val depDeactivate = repository.deactivatePackAndRefreshSnapshot("pack_dep")
        assertTrue(depDeactivate is PackMutationResult.Success)
        assertNull(dao.getActiveTopicHeader("topic_dep"))

        val soloDeactivate = repository.deactivatePackAndRefreshSnapshot("pack_solo")
        assertTrue(soloDeactivate is PackMutationResult.Success)
        assertNull("Disabled pack topic must not remain active winner", dao.getActiveTopicHeader("topic_solo"))

        // Snapshot must not contain topic_solo or topic_dep
        assertNull(repository.getTopicById("topic_solo"))
        assertNull(repository.getTopicById("topic_dep"))
    }

    // =========================================================================
    // 13. Pack removal leaves no orphan records and protects dependent packs
    // =========================================================================
    @Test
    fun test13_packRemovalLeavesNoOrphanRecordsAndProtectsDependentPacks() = runBlocking {
        val providerJson = buildPackJsonWithTopics(
            packId = "pack_removable_provider",
            packVersion = 1,
            packPriority = 70,
            topicsJson = buildTopicJsonNode(topicId = "topic_removable")
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(providerJson).status)

        val consumerJson = buildPackJsonWithTopics(
            packId = "pack_removable_consumer",
            packVersion = 1,
            packPriority = 75,
            topicsJson = buildTopicJsonNode(topicId = "topic_consumer_2", relatedTopicIds = listOf("topic_removable"))
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(consumerJson).status)

        // Deleting pack_removable_provider while pack_removable_consumer depends on topic_removable must be rejected
        val blockedDelete = repository.deletePackAndRefreshSnapshot("pack_removable_provider")
        assertTrue(
            "Deleting pack_removable_provider must be rejected while pack_removable_consumer references topic_removable",
            blockedDelete is PackMutationResult.ValidationRejected
        )

        // Delete consumer first
        val consumerDelete = repository.deletePackAndRefreshSnapshot("pack_removable_consumer")
        assertTrue(consumerDelete is PackMutationResult.Success)

        // Capture keyword IDs of provider before deleting it
        val providerKeywordIds = dao.getKeywordsByTopicAndPackOrdered("topic_removable", "pack_removable_provider").map { it.keywordId }
        assertFalse(providerKeywordIds.isEmpty())

        // Now delete provider
        val providerDelete = repository.deletePackAndRefreshSnapshot("pack_removable_provider")
        assertTrue(providerDelete is PackMutationResult.Success)

        // Verify zero orphan records remain in any table
        assertNull(dao.getPackMetadataById("pack_removable_provider"))
        assertTrue(dao.getTopicHeadersByPackOrdered("pack_removable_provider").isEmpty())
        assertTrue(dao.getKeywordsByTopicAndPackOrdered("topic_removable", "pack_removable_provider").isEmpty())
        for (kwId in providerKeywordIds) {
            assertTrue(dao.getTokensByKeywordIdOrdered(kwId).isEmpty())
        }
        assertNull(dao.getTopicDetail("topic_removable", "pack_removable_provider"))
        assertTrue(dao.getFollowUpBranchesOrdered("topic_removable", "pack_removable_provider").isEmpty())
    }

    // =========================================================================
    // 14. Protected core topic integrity is preserved across all mutations
    // =========================================================================
    @Test
    fun test14_protectedCoreTopicIntegrityPreservedAcrossAllMutations() = runBlocking {
        // Protected core pack cannot be deactivated or deleted
        val deactivateCore = repository.deactivatePackAndRefreshSnapshot(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)
        assertTrue(deactivateCore is PackMutationResult.TransactionFailed)

        val deleteCore = repository.deletePackAndRefreshSnapshot(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)
        assertTrue(deleteCore is PackMutationResult.TransactionFailed)

        // External pack attempting to hijack protected core topic ("ai_concept") must be rejected
        val hijackJson = buildPackJsonWithTopics(
            packId = "pack_core_hijack",
            packVersion = 1,
            packPriority = 200,
            topicsJson = buildTopicJsonNode(
                topicId = "ai_concept",
                arabicSummary = "محاولة استبدال موضوع محمي في البذرة الأساسية."
            )
        )
        val hijackResult = importer.importFromJsonString(hijackJson)
        assertEquals(KnowledgePackImportStatus.CORE_PROTECTION_VIOLATION, hijackResult.status)

        // Protected core topic remains owned by core_seed_pack as active winner
        val coreWinner = dao.getActiveTopicHeader("ai_concept")
        assertNotNull(coreWinner)
        assertEquals(SeedKnowledgeCatalog.CORE_SEED_PACK_ID, coreWinner?.packId)
        assertTrue(coreWinner!!.isCoreTopic)
    }

    // =========================================================================
    // 15. Winner recalculation after mutations preserves a single valid active winner
    // =========================================================================
    @Test
    fun test15_winnerRecalculationAfterMutationsPreservesSingleValidActiveWinner() = runBlocking {
        val packLowJson = buildPackJsonWithTopics(
            packId = "pack_low",
            packVersion = 1,
            packPriority = 40,
            topicsJson = buildTopicJsonNode(
                topicId = "contested_ref_topic",
                priority = 40,
                arabicSummary = "نسخة منخفضة الأولوية للموضوع المتنافس."
            )
        )
        val packHighJson = buildPackJsonWithTopics(
            packId = "pack_high",
            packVersion = 1,
            packPriority = 90,
            topicsJson = buildTopicJsonNode(
                topicId = "contested_ref_topic",
                priority = 90,
                arabicSummary = "نسخة عالية الأولوية للموضوع المتنافس."
            )
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(packLowJson).status)
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(packHighJson).status)

        // Verify exactly 1 active winner in DB
        val candidates1 = dao.getAllTopicVersionsForTopicId("contested_ref_topic")
        assertEquals(2, candidates1.size)
        assertEquals(1, candidates1.count { it.isActiveWinner })
        assertEquals("pack_high", candidates1.first { it.isActiveWinner }.packId)

        // Lower pack_high priority below pack_low
        val prioUpdate = repository.updatePackPriorityAndRefreshSnapshot("pack_high", 20)
        assertTrue(prioUpdate is PackMutationResult.Success)

        val candidates2 = dao.getAllTopicVersionsForTopicId("contested_ref_topic")
        assertEquals(1, candidates2.count { it.isActiveWinner })
        assertEquals("pack_low", candidates2.first { it.isActiveWinner }.packId)

        // Deactivate pack_low -> pack_high becomes the sole active winner
        val deactLow = repository.deactivatePackAndRefreshSnapshot("pack_low")
        assertTrue(deactLow is PackMutationResult.Success)

        val candidates3 = dao.getAllTopicVersionsForTopicId("contested_ref_topic")
        assertEquals(1, candidates3.count { it.isActiveWinner })
        assertEquals("pack_high", candidates3.first { it.isActiveWinner }.packId)
    }

    // =========================================================================
    // 16. Failed validation rolls back all related writes
    // =========================================================================
    @Test
    fun test16_failedValidationRollsBackAllRelatedWrites() = runBlocking {
        val beforePackCount = dao.getAllPackMetadataOrdered().size
        val beforeActiveWinnerCount = dao.getAllActiveTopicHeadersOrdered().size

        // Pack with 1 valid topic and 1 invalid topic (broken reference)
        val invalidPackJson = buildPackJsonWithTopics(
            packId = "pack_atomic_reject",
            packVersion = 1,
            packPriority = 80,
            topicsJson = listOf(
                buildTopicJsonNode(topicId = "valid_first_topic"),
                buildTopicJsonNode(topicId = "invalid_second_topic", relatedTopicIds = listOf("nonexistent_ref_target"))
            ).joinToString(",\n")
        )
        val result = importer.importFromJsonString(invalidPackJson)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, result.status)
        assertFalse(result.databaseCommitted)

        // Zero partial writes in DB
        assertEquals(beforePackCount, dao.getAllPackMetadataOrdered().size)
        assertEquals(beforeActiveWinnerCount, dao.getAllActiveTopicHeadersOrdered().size)
        assertNull(dao.getPackMetadataById("pack_atomic_reject"))
        assertNull(dao.getTopicHeaderByIdAndPack("valid_first_topic", "pack_atomic_reject"))
        assertNull(dao.getTopicHeaderByIdAndPack("invalid_second_topic", "pack_atomic_reject"))
    }

    // =========================================================================
    // 17. Failed import preserves previous valid database state
    // =========================================================================
    @Test
    fun test17_failedImportPreservesPreviousValidDatabaseState() = runBlocking {
        val v1Json = buildPackJsonWithTopics(
            packId = "pack_preserve_v1",
            packVersion = 1,
            packPriority = 75,
            topicsJson = buildTopicJsonNode(
                topicId = "topic_stable",
                arabicSummary = "الملخص الأصلي السليم قبل محاولة التحديث الفاشلة.",
                detailedArabic = "الشرح الأصلي السليم قبل محاولة التحديث الفاشلة."
            )
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(v1Json).status)

        // Attempt v2 import with corrupted reference integrity
        val v2BadJson = buildPackJsonWithTopics(
            packId = "pack_preserve_v1",
            packVersion = 2,
            packPriority = 95,
            topicsJson = buildTopicJsonNode(
                topicId = "topic_stable",
                contentVersion = 2,
                arabicSummary = "ملخص فاسد لا يجب أن يحفظ في قاعدة البيانات.",
                relatedTopicIds = listOf("missing_target_999")
            )
        )
        val v2Result = importer.importFromJsonString(v2BadJson)
        assertEquals(KnowledgePackImportStatus.REFERENCE_INVALID, v2Result.status)

        // Verify v1 state is 100% preserved in Room and Snapshot
        val meta = dao.getPackMetadataById("pack_preserve_v1")
        assertEquals(1, meta?.packVersion)
        assertEquals(75, meta?.packPriority)

        val winner = dao.getActiveTopicHeader("topic_stable")
        assertEquals("الملخص الأصلي السليم قبل محاولة التحديث الفاشلة.", winner?.arabicSummary)

        val detail = dao.getTopicDetail("topic_stable", "pack_preserve_v1")
        assertEquals("الشرح الأصلي السليم قبل محاولة التحديث الفاشلة.", detail?.detailedArabic)
    }

    // =========================================================================
    // 18. Concurrent mutations preserve relationship consistency
    // =========================================================================
    @Test
    fun test18_concurrentMutationsPreserveRelationshipConsistency() = runBlocking {
        val jobs = (1..6).map { idx ->
            async(Dispatchers.Default) {
                val json = buildPackJsonWithTopics(
                    packId = "pack_concurrent_$idx",
                    packVersion = 1,
                    packPriority = 50 + idx,
                    topicsJson = listOf(
                        buildTopicJsonNode(
                            topicId = "shared_concurrent_topic",
                            priority = 50 + idx,
                            arabicSummary = "ملخص مشترك للموضوع المتزامن من الحزمة رقم $idx."
                        ),
                        buildTopicJsonNode(
                            topicId = "unique_concurrent_topic_$idx",
                            priority = 50 + idx,
                            relatedTopicIds = listOf("shared_concurrent_topic")
                        )
                    ).joinToString(",\n")
                )
                importer.importFromJsonString(json)
            }
        }
        val results = jobs.awaitAll()
        assertTrue(results.all { it.status == KnowledgePackImportStatus.SUCCESS_IMPORTED })

        // Verify shared_concurrent_topic has a single active winner (pack_concurrent_6 with priority 56)
        val sharedCandidates = dao.getAllTopicVersionsForTopicId("shared_concurrent_topic")
        assertEquals(6, sharedCandidates.size)
        assertEquals(1, sharedCandidates.count { it.isActiveWinner })
        assertEquals("pack_concurrent_6", sharedCandidates.first { it.isActiveWinner }.packId)

        // Verify every unique_concurrent_topic_i is an active winner and its detail + keywords + tokens are valid
        for (idx in 1..6) {
            val topicId = "unique_concurrent_topic_$idx"
            val packId = "pack_concurrent_$idx"
            val winner = dao.getActiveTopicHeader(topicId)
            assertNotNull(winner)
            assertEquals(packId, winner?.packId)

            val detail = dao.getTopicDetail(topicId, packId)
            assertNotNull(detail)
            assertTrue(detail!!.relatedTopicIdsJson.contains("shared_concurrent_topic"))
        }
    }

    // =========================================================================
    // 19. Snapshot recovery after committed mutation preserves reference integrity
    // =========================================================================
    @Test
    fun test19_snapshotRecoveryAfterCommittedMutationPreservesReferenceIntegrity() = runBlocking {
        val packJson = buildPackJsonWithTopics(
            packId = "pack_recover_ref",
            packVersion = 1,
            packPriority = 90,
            topicsJson = listOf(
                buildTopicJsonNode(topicId = "recover_topic_a", relatedTopicIds = listOf("recover_topic_b")),
                buildTopicJsonNode(topicId = "recover_topic_b", relatedTopicIds = listOf("ai_concept"))
            ).joinToString(",\n")
        )
        val importResult = importer.importFromJsonString(
            json = packJson,
            simulatedSnapshotBuildFailure = {
                throw IllegalStateException("Primary snapshot build failure")
            },
            simulatedRecoveryFailure = {
                throw IllegalStateException("Immediate recovery failure")
            }
        )
        assertEquals(KnowledgePackImportStatus.SNAPSHOT_REFRESH_FAILURE, importResult.status)
        assertEquals(SnapshotRefreshState.FAILED_PREVIOUS_SNAPSHOT_RETAINED, importResult.snapshotRefreshState)
        assertTrue(importResult.databaseCommitted)
        assertTrue(repository.isSnapshotStale)

        // Trigger recovery via normal search read path (while getInstalledPacks reads directly from Room)
        val installedPacks = repository.getInstalledPacks()
        assertTrue(installedPacks.any { it.packId == "pack_recover_ref" })

        val searchResult = repository.search("concept recover_topic_a")
        assertFalse(repository.isSnapshotStale)
        assertTrue(searchResult.orderedCandidates.any { it.topicId == "recover_topic_a" })

        // Verify recovered snapshot contains both topics and their details/follow-ups load cleanly
        assertNotNull(repository.getTopicById("recover_topic_a"))
        assertNotNull(repository.getTopicById("recover_topic_b"))

        val detailResult = repository.getOrLoadTopicDetail("recover_topic_a")
        assertEquals(TopicDetailLoadStatus.LOADED_FROM_DB, detailResult.status)
        assertNotNull(detailResult.bundle)
        assertTrue(detailResult.bundle!!.detail.relatedTopicIdsJson.contains("recover_topic_b"))
    }

    // =========================================================================
    // 20. Targeted cache invalidation preserves unrelated valid cached entries
    // =========================================================================
    @Test
    fun test20_targetedCacheInvalidationPreservesUnrelatedValidCachedEntries() = runBlocking {
        // Import baseline pack with two topics that have full details & follow-up branches
        val basePackJson = buildPackJsonWithTopics(
            packId = "pack_cache_base",
            packVersion = 1,
            packPriority = 60,
            topicsJson = listOf(
                buildTopicJsonNode(
                    topicId = "cached_untouched_topic",
                    priority = 60,
                    detailedArabic = "شرح الموضوع غير المتأثر الذي يجب أن يبقى في الذاكرة المخبأة."
                ),
                buildTopicJsonNode(
                    topicId = "cached_contested_topic",
                    priority = 60,
                    detailedArabic = "شرح النسخة الأساسية للموضوع المتنافس قبل الترقية."
                )
            ).joinToString(",\n")
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(basePackJson).status)

        // Warm caches for both topics
        assertEquals(TopicDetailLoadStatus.LOADED_FROM_DB, repository.getOrLoadTopicDetail("cached_untouched_topic").status)
        assertEquals(TopicDetailLoadStatus.LOADED_FROM_DB, repository.getOrLoadTopicDetail("cached_contested_topic").status)
        assertEquals(2, repository.cache.snapshotStats().detailStats.entryCount)

        // Import a higher-priority pack that ONLY overrides cached_contested_topic
        val overridePackJson = buildPackJsonWithTopics(
            packId = "pack_cache_override",
            packVersion = 1,
            packPriority = 90,
            topicsJson = buildTopicJsonNode(
                topicId = "cached_contested_topic",
                priority = 90,
                detailedArabic = "شرح النسخة الفائزة الجديدة بعد استيراد الحزمة الأعلى أولوية."
            )
        )
        assertEquals(KnowledgePackImportStatus.SUCCESS_IMPORTED, importer.importFromJsonString(overridePackJson).status)

        // Unrelated cached_untouched_topic must still be in detailCache (size == 1) and produce a CACHE_HIT
        assertEquals(1, repository.cache.snapshotStats().detailStats.entryCount)
        assertEquals(TopicDetailLoadStatus.CACHE_HIT, repository.getOrLoadTopicDetail("cached_untouched_topic").status)

        // Loading cached_contested_topic must fetch the new winner's detail from DB
        val updatedDetail = repository.getOrLoadTopicDetail("cached_contested_topic")
        assertEquals(TopicDetailLoadStatus.LOADED_FROM_DB, updatedDetail.status)
        assertEquals(
            "شرح النسخة الفائزة الجديدة بعد استيراد الحزمة الأعلى أولوية.",
            updatedDetail.bundle!!.detail.detailedArabic
        )
    }
}
