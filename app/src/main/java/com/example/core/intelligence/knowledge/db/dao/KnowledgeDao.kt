package com.example.core.intelligence.knowledge.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.entity.KnowledgeFollowUpBranchEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicDetailEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity

/**
 * Lightweight projection for checking cross-topic reference integrity without loading heavy detail text.
 */
data class ActiveTopicRelatedRefsProjection(
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "related_topic_ids_json") val relatedTopicIdsJson: String
)

/**
 * Bundle holding a keyword and its ordered tokens for atomic insertion into Room.
 */
data class KeywordWithTokensBundle(
    val keyword: KnowledgeKeywordEntity,
    val tokens: List<KnowledgeKeywordTokenEntity>
)

/**
 * Bundle holding all storage records for a single topic within a specific pack.
 */
data class TopicStorageBundle(
    val header: KnowledgeTopicHeaderEntity,
    val keywordsWithTokens: List<KeywordWithTokensBundle>,
    val detail: KnowledgeTopicDetailEntity?,
    val followUpBranches: List<KnowledgeFollowUpBranchEntity>
)

/**
 * Centralized, deterministic policy for Knowledge Pack priority encoding/decoding,
 * enabled-state detection, and active topic winner selection.
 *
 * Enforces one consistent precedence order across SQL queries, DAO winner recomputation,
 * and in-memory snapshot construction:
 * 1. Canonical Core Seed protection (`pack_id == "core_seed_pack" && is_core_topic && topic_id in PROTECTED_CORE_TOPIC_IDS`)
 * 2. Highest enabled `pack_priority DESC` (`pack_priority >= 0`)
 * 3. Highest `content_version DESC`
 * 4. Deterministic lexicographical tie-breaker `pack_id ASC`
 *
 * Disabled packs are represented via negative priority encoding `-(logicalPriority + 1)`
 * using 64-bit intermediate arithmetic so boundary values (`0`, `Int.MAX_VALUE`, `Int.MIN_VALUE`)
 * never overflow.
 */
object KnowledgePriorityAndWinnerPolicy {

    const val MIN_LOGICAL_PRIORITY: Int = 0
    const val MAX_LOGICAL_PRIORITY: Int = Int.MAX_VALUE

    fun isValidLogicalPriority(logicalPriority: Int): Boolean =
        logicalPriority >= MIN_LOGICAL_PRIORITY

    fun requireValidLogicalPriority(logicalPriority: Int, paramName: String = "priority"): Int {
        require(isValidLogicalPriority(logicalPriority)) {
            "$paramName ($logicalPriority) must be >= $MIN_LOGICAL_PRIORITY."
        }
        return logicalPriority
    }

    fun isStoredPriorityEnabled(storedPriority: Int): Boolean =
        storedPriority >= MIN_LOGICAL_PRIORITY

    fun isStoredPriorityDisabled(storedPriority: Int): Boolean =
        storedPriority < MIN_LOGICAL_PRIORITY

    /**
     * Encodes a non-negative [logicalPriority] (`0..Int.MAX_VALUE`) into its disabled negative
     * storage representation `-(logicalPriority + 1)` without 32-bit signed integer overflow.
     */
    fun encodeDisabledPriority(logicalPriority: Int): Int {
        requireValidLogicalPriority(logicalPriority, "logicalPriority")
        return (-(logicalPriority.toLong() + 1L)).toInt()
    }

    /**
     * Decodes any stored priority (enabled `>= 0` or disabled `< 0`) back into its non-negative
     * logical priority (`0..Int.MAX_VALUE`) without 32-bit signed integer overflow.
     */
    fun decodeLogicalPriority(storedPriority: Int): Int {
        return if (isStoredPriorityEnabled(storedPriority)) {
            storedPriority
        } else {
            (-(storedPriority.toLong() + 1L)).toInt()
        }
    }

    /**
     * Computes the stored priority value for [logicalPriority] while preserving the target
     * [enabled] state.
     */
    fun encodeStoredPriority(logicalPriority: Int, enabled: Boolean): Int {
        requireValidLogicalPriority(logicalPriority, "logicalPriority")
        return if (enabled) {
            logicalPriority
        } else {
            encodeDisabledPriority(logicalPriority)
        }
    }

    /**
     * Returns true if [header] is an authoritative Core Seed topic row belonging to `core_seed_pack`.
     */
    fun isCanonicalCoreSeedTopic(header: KnowledgeTopicHeaderEntity): Boolean =
        header.packId == SeedKnowledgeCatalog.CORE_SEED_PACK_ID &&
            header.isCoreTopic &&
            SeedKnowledgeCatalog.isProtectedCoreTopicId(header.topicId)

    /**
     * Comparator ordering candidate topic headers from highest precedence (winner first) to lowest:
     * 1. Canonical Core Seed topic (`true` before `false`)
     * 2. Higher `packPriority` (`DESC`)
     * 3. Higher `contentVersion` (`DESC`)
     * 4. Lexicographically smaller `packId` (`ASC`)
     */
    val WINNER_HEADER_COMPARATOR: Comparator<KnowledgeTopicHeaderEntity> =
        Comparator { a, b ->
            val aCore = isCanonicalCoreSeedTopic(a)
            val bCore = isCanonicalCoreSeedTopic(b)
            if (aCore != bCore) {
                return@Comparator if (aCore) -1 else 1
            }
            val priorityCmp = b.packPriority.compareTo(a.packPriority)
            if (priorityCmp != 0) {
                return@Comparator priorityCmp
            }
            val contentVersionCmp = b.contentVersion.compareTo(a.contentVersion)
            if (contentVersionCmp != 0) {
                return@Comparator contentVersionCmp
            }
            a.packId.compareTo(b.packId)
        }

    /**
     * Selects the single winning [KnowledgeTopicHeaderEntity] from [candidates], ignoring any
     * disabled candidate (`packPriority < 0`). Independent of input iteration order.
     */
    fun selectWinningTopicHeader(
        candidates: Iterable<KnowledgeTopicHeaderEntity>
    ): KnowledgeTopicHeaderEntity? {
        return candidates
            .asSequence()
            .filter { isStoredPriorityEnabled(it.packPriority) }
            .minWithOrNull(WINNER_HEADER_COMPARATOR)
    }

    /**
     * Filters out disabled topic bundles (`packPriority < 0`) and resolves any competing bundles
     * for the same `topicId` using [WINNER_HEADER_COMPARATOR], ensuring in-memory snapshot
     * construction always agrees with database winner selection regardless of bundle order.
     */
    fun selectWinningTopicBundles(
        bundles: Iterable<TopicStorageBundle>
    ): List<TopicStorageBundle> {
        val winningByTopicId = LinkedHashMap<String, TopicStorageBundle>()
        for (bundle in bundles) {
            if (!isStoredPriorityEnabled(bundle.header.packPriority)) continue
            val topicId = bundle.header.topicId
            val existing = winningByTopicId[topicId]
            if (existing == null ||
                WINNER_HEADER_COMPARATOR.compare(bundle.header, existing.header) < 0
            ) {
                winningByTopicId[topicId] = bundle
            }
        }
        return winningByTopicId.values.toList()
    }
}

@Dao
interface KnowledgeDao {

    // =========================================================================
    // 1. Pack Metadata Operations
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackMetadata(pack: KnowledgePackMetadataEntity)

    @Update
    suspend fun updatePackMetadata(pack: KnowledgePackMetadataEntity): Int

    @Query("SELECT * FROM knowledge_pack_metadata WHERE pack_id = :packId LIMIT 1")
    suspend fun getPackMetadataById(packId: String): KnowledgePackMetadataEntity?

    @Query(
        """
        SELECT * FROM knowledge_pack_metadata
        ORDER BY pack_priority DESC, pack_version DESC, pack_id ASC
        """
    )
    suspend fun getAllPackMetadataOrdered(): List<KnowledgePackMetadataEntity>

    @Query("DELETE FROM knowledge_pack_metadata WHERE pack_id = :packId")
    suspend fun deletePackByIdRaw(packId: String): Int

    @Query("UPDATE knowledge_pack_metadata SET pack_priority = :newPriority WHERE pack_id = :packId")
    suspend fun updatePackPriorityRaw(packId: String, newPriority: Int): Int

    // =========================================================================
    // 2. Topic Header Operations
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopicHeader(header: KnowledgeTopicHeaderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopicHeaders(headers: List<KnowledgeTopicHeaderEntity>)

    @Update
    suspend fun updateTopicHeader(header: KnowledgeTopicHeaderEntity): Int

    @Query(
        """
        SELECT * FROM knowledge_topics
        WHERE topic_id = :topicId AND pack_id = :packId
        LIMIT 1
        """
    )
    suspend fun getTopicHeaderByIdAndPack(topicId: String, packId: String): KnowledgeTopicHeaderEntity?

    @Query(
        """
        SELECT * FROM knowledge_topics
        WHERE topic_id = :topicId AND is_active_winner = 1
        ORDER BY
            CASE WHEN pack_id = 'core_seed_pack' AND is_core_topic = 1 THEN 1 ELSE 0 END DESC,
            pack_priority DESC,
            content_version DESC,
            pack_id ASC
        LIMIT 1
        """
    )
    suspend fun getActiveTopicHeader(topicId: String): KnowledgeTopicHeaderEntity?

    @Query(
        """
        SELECT * FROM knowledge_topics
        WHERE topic_id = :topicId
        ORDER BY
            CASE WHEN pack_id = 'core_seed_pack' AND is_core_topic = 1 THEN 1 ELSE 0 END DESC,
            pack_priority DESC,
            content_version DESC,
            pack_id ASC
        """
    )
    suspend fun getAllTopicVersionsForTopicId(topicId: String): List<KnowledgeTopicHeaderEntity>

    @Query(
        """
        SELECT * FROM knowledge_topics
        WHERE topic_id IN (:topicIds)
        ORDER BY
            topic_id ASC,
            CASE WHEN pack_id = 'core_seed_pack' AND is_core_topic = 1 THEN 1 ELSE 0 END DESC,
            pack_priority DESC,
            content_version DESC,
            pack_id ASC
        """
    )
    suspend fun getAllTopicVersionsForTopicIds(topicIds: List<String>): List<KnowledgeTopicHeaderEntity>

    @Query(
        """
        SELECT * FROM knowledge_topics
        WHERE pack_id = :packId
        ORDER BY topic_order_index ASC, topic_id ASC
        """
    )
    suspend fun getTopicHeadersByPackOrdered(packId: String): List<KnowledgeTopicHeaderEntity>

    @Query(
        """
        SELECT topic_id FROM knowledge_topics
        WHERE pack_id = :packId
        ORDER BY topic_order_index ASC, topic_id ASC
        """
    )
    suspend fun getTopicIdsByPack(packId: String): List<String>

    @Query("DELETE FROM knowledge_topics WHERE topic_id = :topicId AND pack_id = :packId")
    suspend fun deleteTopicByIdAndPack(topicId: String, packId: String): Int

    @Query("UPDATE knowledge_topics SET pack_priority = :newPriority WHERE pack_id = :packId")
    suspend fun updateTopicHeadersPriorityByPackRaw(packId: String, newPriority: Int): Int

    @Query("UPDATE knowledge_topics SET is_active_winner = 0 WHERE pack_id = :packId")
    suspend fun clearActiveWinnersByPackRaw(packId: String): Int

    @Query(
        """
        SELECT * FROM knowledge_topics
        WHERE is_active_winner = 1
        ORDER BY topic_order_index ASC, topic_id ASC
        """
    )
    suspend fun getAllActiveTopicHeadersOrdered(): List<KnowledgeTopicHeaderEntity>

    @Query("UPDATE knowledge_topics SET is_active_winner = 0 WHERE topic_id = :topicId")
    suspend fun clearActiveWinnerForTopic(topicId: String)

    @Query(
        """
        UPDATE knowledge_topics
        SET is_active_winner = 1
        WHERE topic_id = :topicId AND pack_id = :winningPackId
        """
    )
    suspend fun markActiveWinnerForTopic(topicId: String, winningPackId: String): Int

    // =========================================================================
    // 3. Keywords & Keyword Tokens Operations (Per-Topic & Bounded Bulk Queries)
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKeyword(keyword: KnowledgeKeywordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKeywords(keywords: List<KnowledgeKeywordEntity>): List<Long>

    @Query(
        """
        SELECT * FROM knowledge_keywords
        WHERE topic_id = :topicId AND pack_id = :packId
        ORDER BY keyword_order ASC
        """
    )
    suspend fun getKeywordsByTopicAndPackOrdered(
        topicId: String,
        packId: String
    ): List<KnowledgeKeywordEntity>

    /**
     * Bounded bulk query returning all keywords belonging to the currently active winning
     * topics across the entire database in deterministic order.
     */
    @Query(
        """
        SELECT k.* FROM knowledge_keywords AS k
        INNER JOIN knowledge_topics AS h
            ON k.topic_id = h.topic_id AND k.pack_id = h.pack_id
        WHERE h.is_active_winner = 1
        ORDER BY h.topic_order_index ASC, h.topic_id ASC, k.keyword_order ASC
        """
    )
    suspend fun getAllActiveKeywordsOrdered(): List<KnowledgeKeywordEntity>

    @Query("DELETE FROM knowledge_keywords WHERE topic_id = :topicId AND pack_id = :packId")
    suspend fun deleteKeywordsByTopicAndPack(topicId: String, packId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKeywordToken(token: KnowledgeKeywordTokenEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKeywordTokens(tokens: List<KnowledgeKeywordTokenEntity>): List<Long>

    @Query(
        """
        SELECT * FROM knowledge_keyword_tokens
        WHERE keyword_id = :keywordId
        ORDER BY token_position ASC
        """
    )
    suspend fun getTokensByKeywordIdOrdered(keywordId: Long): List<KnowledgeKeywordTokenEntity>

    @Query(
        """
        SELECT t.* FROM knowledge_keyword_tokens AS t
        INNER JOIN knowledge_keywords AS k ON t.keyword_id = k.keyword_id
        WHERE k.topic_id = :topicId AND k.pack_id = :packId
        ORDER BY k.keyword_order ASC, t.token_position ASC
        """
    )
    suspend fun getTokensByTopicAndPackOrdered(
        topicId: String,
        packId: String
    ): List<KnowledgeKeywordTokenEntity>

    /**
     * Bounded bulk query returning all keyword tokens belonging to the currently active winning
     * topics across the entire database in deterministic order.
     */
    @Query(
        """
        SELECT t.* FROM knowledge_keyword_tokens AS t
        INNER JOIN knowledge_keywords AS k
            ON t.keyword_id = k.keyword_id
        INNER JOIN knowledge_topics AS h
            ON k.topic_id = h.topic_id AND k.pack_id = h.pack_id
        WHERE h.is_active_winner = 1
        ORDER BY k.keyword_id ASC, t.token_position ASC
        """
    )
    suspend fun getAllActiveKeywordTokensOrdered(): List<KnowledgeKeywordTokenEntity>

    // =========================================================================
    // 4. Topic Details Operations
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopicDetail(detail: KnowledgeTopicDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopicDetails(details: List<KnowledgeTopicDetailEntity>)

    @Query(
        """
        SELECT * FROM knowledge_topic_details
        WHERE topic_id = :topicId AND pack_id = :packId
        LIMIT 1
        """
    )
    suspend fun getTopicDetail(topicId: String, packId: String): KnowledgeTopicDetailEntity?

    @Query(
        """
        SELECT d.* FROM knowledge_topic_details AS d
        INNER JOIN knowledge_topics AS h
            ON d.topic_id = h.topic_id AND d.pack_id = h.pack_id
        WHERE d.topic_id = :topicId AND h.is_active_winner = 1
        ORDER BY
            CASE WHEN h.pack_id = 'core_seed_pack' AND h.is_core_topic = 1 THEN 1 ELSE 0 END DESC,
            h.pack_priority DESC,
            h.content_version DESC,
            h.pack_id ASC
        LIMIT 1
        """
    )
    suspend fun getActiveTopicDetail(topicId: String): KnowledgeTopicDetailEntity?

    /**
     * Lightweight projection reading only `topic_id`, `pack_id`, and `related_topic_ids_json`
     * for active winning topics that declare related references, without loading heavy
     * `detailed_arabic` or `detailed_english` columns into memory.
     */
    @Query(
        """
        SELECT d.topic_id, d.pack_id, d.related_topic_ids_json
        FROM knowledge_topic_details AS d
        INNER JOIN knowledge_topics AS h
            ON d.topic_id = h.topic_id AND d.pack_id = h.pack_id
        WHERE h.is_active_winner = 1 AND d.related_topic_ids_json != '[]'
        """
    )
    suspend fun getActiveTopicRelatedRefsProjections(): List<ActiveTopicRelatedRefsProjection>

    /**
     * Returns all distinct `topic_id`s present in `knowledge_topics` that currently have no
     * active winning row (`is_active_winner = 1`), such as topics belonging only to deactivated packs.
     */
    @Query(
        """
        SELECT DISTINCT topic_id FROM knowledge_topics
        WHERE topic_id NOT IN (
            SELECT topic_id FROM knowledge_topics WHERE is_active_winner = 1
        )
        """
    )
    suspend fun getInactiveOnlyTopicIds(): List<String>

    @Query("DELETE FROM knowledge_topic_details WHERE topic_id = :topicId AND pack_id = :packId")
    suspend fun deleteTopicDetailByTopicAndPack(topicId: String, packId: String)

    // =========================================================================
    // 5. Follow-up Branches Operations
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollowUpBranch(branch: KnowledgeFollowUpBranchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollowUpBranches(branches: List<KnowledgeFollowUpBranchEntity>)

    @Query(
        """
        SELECT * FROM knowledge_followup_branches
        WHERE topic_id = :topicId AND pack_id = :packId
        ORDER BY priority_order ASC, branch_id ASC
        """
    )
    suspend fun getFollowUpBranchesOrdered(
        topicId: String,
        packId: String
    ): List<KnowledgeFollowUpBranchEntity>

    @Query(
        """
        SELECT b.* FROM knowledge_followup_branches AS b
        INNER JOIN knowledge_topics AS h
            ON b.topic_id = h.topic_id AND b.pack_id = h.pack_id
        WHERE b.topic_id = :topicId AND h.is_active_winner = 1
        ORDER BY b.priority_order ASC, b.branch_id ASC
        """
    )
    suspend fun getActiveFollowUpBranchesOrdered(topicId: String): List<KnowledgeFollowUpBranchEntity>

    @Query("DELETE FROM knowledge_followup_branches WHERE topic_id = :topicId AND pack_id = :packId")
    suspend fun deleteFollowUpBranchesByTopicAndPack(topicId: String, packId: String)

    // =========================================================================
    // 6. Transactional Composite Operations
    // =========================================================================

    /**
     * Loads all active topic metadata (headers, keywords, and tokens) in a single consistent
     * read transaction using a bounded constant number of SQL queries (`<= 3` SELECT queries
     * regardless of topic count), without loading detailed content or follow-up branches.
     */
    @Transaction
    suspend fun loadActiveSnapshotStorageBundlesTransactional(): List<TopicStorageBundle> {
        val activeHeaders = getAllActiveTopicHeadersOrdered()
        if (activeHeaders.isEmpty()) return emptyList()

        val allActiveKeywords = getAllActiveKeywordsOrdered()
        val allActiveTokens = getAllActiveKeywordTokensOrdered()

        val tokensByKeywordId = HashMap<Long, MutableList<KnowledgeKeywordTokenEntity>>(allActiveKeywords.size)
        for (token in allActiveTokens) {
            tokensByKeywordId.getOrPut(token.keywordId) { ArrayList(4) }.add(token)
        }

        val keywordsByTopicAndPack = HashMap<Pair<String, String>, MutableList<KeywordWithTokensBundle>>(activeHeaders.size)
        for (kw in allActiveKeywords) {
            val key = kw.topicId to kw.packId
            val kwTokens = tokensByKeywordId[kw.keywordId] ?: emptyList()
            keywordsByTopicAndPack.getOrPut(key) { ArrayList() }.add(
                KeywordWithTokensBundle(
                    keyword = kw,
                    tokens = kwTokens
                )
            )
        }

        val result = ArrayList<TopicStorageBundle>(activeHeaders.size)
        for (header in activeHeaders) {
            val key = header.topicId to header.packId
            val kwBundles = keywordsByTopicAndPack[key] ?: emptyList()
            result.add(
                TopicStorageBundle(
                    header = header,
                    keywordsWithTokens = kwBundles,
                    detail = null,
                    followUpBranches = emptyList()
                )
            )
        }
        return result
    }

    /**
     * Atomically loads the detailed content and ordered follow-up branches for a specific
     * `(topicId, packId)` composite identity within a consistent read transaction.
     */
    @Transaction
    suspend fun loadTopicDetailAndBranchesByIdentityTransactional(
        topicId: String,
        packId: String
    ): Pair<KnowledgeTopicDetailEntity?, List<KnowledgeFollowUpBranchEntity>> {
        val detail = getTopicDetail(topicId = topicId, packId = packId)
        val branches = getFollowUpBranchesOrdered(topicId = topicId, packId = packId)
        return detail to branches
    }

    /**
     * Recomputes and enforces a single active winner (`is_active_winner = 1`) for [topicId]
     * based on:
     * 1. Protected canonical seed ownership (`pack_id = 'core_seed_pack' AND is_core_topic = 1`)
     * 2. Highest non-negative `pack_priority DESC` (packs with `pack_priority < 0` are deactivated)
     * 3. Highest `content_version DESC`
     * 4. Lexicographical tie-breaker `pack_id ASC`
     */
    @Transaction
    suspend fun recalculateActiveWinnerForTopic(topicId: String) {
        val versions = getAllTopicVersionsForTopicId(topicId)
        clearActiveWinnerForTopic(topicId)
        val winner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(versions) ?: return
        markActiveWinnerForTopic(topicId, winner.packId)
    }

    /**
     * Centralized batch winner recomputation across all distinct [topicIds].
     */
    @Transaction
    suspend fun recalculateActiveWinnersForTopicsTransactional(topicIds: Collection<String>) {
        for (topicId in topicIds.distinct()) {
            if (topicId.isNotBlank()) {
                recalculateActiveWinnerForTopic(topicId)
            }
        }
    }

    /**
     * Atomically switches the active winner for [topicId] to [winningPackId], ensuring
     * all other versions of [topicId] have `is_active_winner = 0`.
     */
    @Transaction
    suspend fun setActiveWinnerForTopic(topicId: String, winningPackId: String) {
        clearActiveWinnerForTopic(topicId)
        markActiveWinnerForTopic(topicId, winningPackId)
    }

    /**
     * Internal helper that inserts/updates a topic bundle's rows without triggering per-topic
     * winner recomputation yet (used by batch pack updates).
     */
    @Transaction
    suspend fun upsertTopicBundleWithoutWinnerRecalc(bundle: TopicStorageBundle) {
        upsertTopicHeader(bundle.header)
        deleteKeywordsByTopicAndPack(bundle.header.topicId, bundle.header.packId)
        for (kwWithTokens in bundle.keywordsWithTokens) {
            val generatedKeywordId = insertKeyword(kwWithTokens.keyword.copy(keywordId = 0L))
            if (kwWithTokens.tokens.isNotEmpty()) {
                val boundTokens = kwWithTokens.tokens.map { token ->
                    token.copy(tokenId = 0L, keywordId = generatedKeywordId)
                }
                insertKeywordTokens(boundTokens)
            }
        }
        if (bundle.detail != null) {
            upsertTopicDetail(bundle.detail)
        } else {
            deleteTopicDetailByTopicAndPack(bundle.header.topicId, bundle.header.packId)
        }
        deleteFollowUpBranchesByTopicAndPack(bundle.header.topicId, bundle.header.packId)
        if (bundle.followUpBranches.isNotEmpty()) {
            upsertFollowUpBranches(bundle.followUpBranches)
        }
    }

    /**
     * Atomically inserts/updates a complete topic storage bundle (header, keywords, tokens,
     * detail, follow-up branches) and recalculates the single active winner for that topic.
     */
    @Transaction
    suspend fun upsertTopicBundleTransactional(bundle: TopicStorageBundle) {
        upsertTopicBundleWithoutWinnerRecalc(bundle)
        recalculateActiveWinnerForTopic(bundle.header.topicId)
    }

    /**
     * Atomically inserts or updates a full knowledge pack and its topic bundles:
     * 1. Captures previous topic IDs belonging to [pack.packId].
     * 2. Captures incoming topic IDs from [topics].
     * 3. Removes only topics that were dropped from [pack.packId] (without deleting those
     *    topic IDs from other packs).
     * 4. Upserts pack metadata and all incoming topic bundles.
     * 5. Recomputes the active winner for every topic ID in `previousTopicIds union incomingTopicIds`.
     *
     * @return the union set of all affected `topic_id`s.
     */
    @Transaction
    suspend fun upsertPackWithTopicsTransactional(
        pack: KnowledgePackMetadataEntity,
        topics: List<TopicStorageBundle>
    ): Set<String> {
        val previousTopicIds = getTopicIdsByPack(pack.packId).toSet()
        val incomingTopicIds = topics.map { it.header.topicId }.toSet()
        val removedTopicIds = previousTopicIds - incomingTopicIds
        val allAffectedTopicIds = LinkedHashSet<String>(previousTopicIds.size + incomingTopicIds.size).apply {
            addAll(previousTopicIds)
            addAll(incomingTopicIds)
        }

        val existingPack = getPackMetadataById(pack.packId)
        if (existingPack == null) {
            upsertPackMetadata(pack)
        } else {
            updatePackMetadata(pack)
        }

        for (removedTopicId in removedTopicIds) {
            deleteTopicByIdAndPack(removedTopicId, pack.packId)
        }

        for (bundle in topics) {
            val synchronizedBundle = if (bundle.header.packPriority != pack.packPriority) {
                bundle.copy(header = bundle.header.copy(packPriority = pack.packPriority))
            } else {
                bundle
            }
            upsertTopicBundleWithoutWinnerRecalc(synchronizedBundle)
        }

        recalculateActiveWinnersForTopicsTransactional(allAffectedTopicIds)
        return allAffectedTopicIds
    }

    /**
     * Atomically deactivates a non-seed knowledge pack without deleting its stored rows,
     * marking its priority negative (`-(priority + 1)`) and recomputing active winners for
     * all affected topics so fallback packs or seed topics take over immediately.
     */
    @Transaction
    suspend fun deactivatePackTransactional(packId: String): Set<String> {
        require(packId != SeedKnowledgeCatalog.CORE_SEED_PACK_ID) {
            "Protected core_seed_pack cannot be deactivated."
        }
        val existingPack = getPackMetadataById(packId) ?: return emptySet()
        val affectedTopicIds = getTopicIdsByPack(packId).toSet()
        val logicalPriority = KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(existingPack.packPriority)
        val deactivatedPriority = KnowledgePriorityAndWinnerPolicy.encodeDisabledPriority(logicalPriority)
        updatePackPriorityRaw(packId, deactivatedPriority)
        updateTopicHeadersPriorityByPackRaw(packId, deactivatedPriority)
        clearActiveWinnersByPackRaw(packId)
        recalculateActiveWinnersForTopicsTransactional(affectedTopicIds)
        return affectedTopicIds
    }

    /**
     * Atomically reactivates a previously deactivated knowledge pack, restoring its non-negative
     * priority and recomputing active winners across all affected topic IDs.
     */
    @Transaction
    suspend fun reactivatePackTransactional(
        packId: String,
        restoredPriorityOverride: Int? = null
    ): Set<String> {
        require(packId != SeedKnowledgeCatalog.CORE_SEED_PACK_ID) {
            "Protected core_seed_pack cannot be modified via reactivation."
        }
        val existingPack = getPackMetadataById(packId) ?: return emptySet()
        val restoredPriority = restoredPriorityOverride
            ?: KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(existingPack.packPriority)
        KnowledgePriorityAndWinnerPolicy.requireValidLogicalPriority(restoredPriority, "restoredPriority")
        val affectedTopicIds = getTopicIdsByPack(packId).toSet()
        updatePackPriorityRaw(packId, restoredPriority)
        updateTopicHeadersPriorityByPackRaw(packId, restoredPriority)
        recalculateActiveWinnersForTopicsTransactional(affectedTopicIds)
        return affectedTopicIds
    }

    /**
     * Atomically updates a non-seed pack's logical priority while strictly preserving its current
     * enabled/disabled state, and recomputes active winners across all affected topic IDs.
     */
    @Transaction
    suspend fun updatePackPriorityTransactional(
        packId: String,
        newPriority: Int
    ): Set<String> {
        require(packId != SeedKnowledgeCatalog.CORE_SEED_PACK_ID) {
            "Protected core_seed_pack priority cannot be changed."
        }
        KnowledgePriorityAndWinnerPolicy.requireValidLogicalPriority(newPriority, "newPriority")
        val existingPack = getPackMetadataById(packId) ?: return emptySet()
        val affectedTopicIds = getTopicIdsByPack(packId).toSet()
        val isCurrentlyEnabled = KnowledgePriorityAndWinnerPolicy.isStoredPriorityEnabled(existingPack.packPriority)
        val targetStoredPriority = KnowledgePriorityAndWinnerPolicy.encodeStoredPriority(
            logicalPriority = newPriority,
            enabled = isCurrentlyEnabled
        )
        updatePackPriorityRaw(packId, targetStoredPriority)
        updateTopicHeadersPriorityByPackRaw(packId, targetStoredPriority)
        if (!isCurrentlyEnabled) {
            clearActiveWinnersByPackRaw(packId)
        }
        recalculateActiveWinnersForTopicsTransactional(affectedTopicIds)
        return affectedTopicIds
    }

    /**
     * Atomically deletes a pack by [packId] (cascading to its topics, keywords, tokens, details,
     * and follow-up branches) and promotes the highest-priority remaining pack version for each
     * affected `topic_id` as the new `is_active_winner = 1`.
     */
    @Transaction
    suspend fun deletePackAndRecalculateWinnersTransactional(packId: String): Int {
        require(packId != SeedKnowledgeCatalog.CORE_SEED_PACK_ID) {
            "Protected core_seed_pack cannot be deleted."
        }
        val affectedTopicIds = getTopicIdsByPack(packId).distinct()
        val deletedCount = deletePackByIdRaw(packId)
        recalculateActiveWinnersForTopicsTransactional(affectedTopicIds)
        return deletedCount
    }
}
