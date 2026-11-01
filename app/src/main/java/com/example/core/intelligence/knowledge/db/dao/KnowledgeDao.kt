package com.example.core.intelligence.knowledge.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.core.intelligence.knowledge.db.entity.KnowledgeFollowUpBranchEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicDetailEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity

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
        ORDER BY pack_priority DESC, content_version DESC, pack_id ASC
        LIMIT 1
        """
    )
    suspend fun getActiveTopicHeader(topicId: String): KnowledgeTopicHeaderEntity?

    @Query(
        """
        SELECT * FROM knowledge_topics
        WHERE topic_id = :topicId
        ORDER BY pack_priority DESC, content_version DESC, pack_id ASC
        """
    )
    suspend fun getAllTopicVersionsForTopicId(topicId: String): List<KnowledgeTopicHeaderEntity>

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
    // 3. Keywords & Keyword Tokens Operations
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
        ORDER BY h.pack_priority DESC, h.content_version DESC, h.pack_id ASC
        LIMIT 1
        """
    )
    suspend fun getActiveTopicDetail(topicId: String): KnowledgeTopicDetailEntity?

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
     * Recomputes and enforces a single active winner (`is_active_winner = 1`) for [topicId]
     * based on highest `(pack_priority DESC, content_version DESC, pack_id ASC)`.
     */
    @Transaction
    suspend fun recalculateActiveWinnerForTopic(topicId: String) {
        val versions = getAllTopicVersionsForTopicId(topicId)
        clearActiveWinnerForTopic(topicId)
        val winner = versions.firstOrNull() ?: return
        markActiveWinnerForTopic(topicId, winner.packId)
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
     * Atomically inserts/updates a complete topic storage bundle (header, keywords, tokens,
     * detail, follow-up branches) and recalculates the single active winner for that topic.
     */
    @Transaction
    suspend fun upsertTopicBundleTransactional(bundle: TopicStorageBundle) {
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
        }
        deleteFollowUpBranchesByTopicAndPack(bundle.header.topicId, bundle.header.packId)
        if (bundle.followUpBranches.isNotEmpty()) {
            upsertFollowUpBranches(bundle.followUpBranches)
        }
        recalculateActiveWinnerForTopic(bundle.header.topicId)
    }

    /**
     * Atomically inserts/updates a full knowledge pack and all its topic bundles, then ensures
     * every affected `topic_id` has a single deterministic `is_active_winner = 1`.
     */
    @Transaction
    suspend fun upsertPackWithTopicsTransactional(
        pack: KnowledgePackMetadataEntity,
        topics: List<TopicStorageBundle>
    ) {
        upsertPackMetadata(pack)
        for (bundle in topics) {
            upsertTopicBundleTransactional(bundle)
        }
    }

    /**
     * Atomically deletes a pack by [packId] (cascading to its topics, keywords, tokens, details,
     * and follow-up branches) and promotes the highest-priority remaining pack version for each
     * affected `topic_id` as the new `is_active_winner = 1`.
     */
    @Transaction
    suspend fun deletePackAndRecalculateWinnersTransactional(packId: String): Int {
        val affectedTopicIds = getTopicHeadersByPackOrdered(packId).map { it.topicId }.distinct()
        val deletedCount = deletePackByIdRaw(packId)
        for (topicId in affectedTopicIds) {
            recalculateActiveWinnerForTopic(topicId)
        }
        return deletedCount
    }
}
