package com.example.core.intelligence.knowledge.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters

@Entity(
    tableName = "knowledge_topics",
    primaryKeys = ["topic_id", "pack_id"],
    foreignKeys = [
        ForeignKey(
            entity = KnowledgePackMetadataEntity::class,
            parentColumns = ["pack_id"],
            childColumns = ["pack_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pack_id"], name = "idx_topics_pack_id"),
        Index(value = ["topic_id", "is_active_winner"], name = "idx_topics_active_lookup"),
        Index(value = ["is_active_winner", "topic_order_index"], name = "idx_topics_active_order")
    ]
)
data class KnowledgeTopicHeaderEntity(
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "topic_order_index") val topicOrderIndex: Int,
    @ColumnInfo(name = "arabic_summary") val arabicSummary: String,
    @ColumnInfo(name = "english_summary") val englishSummary: String,
    @ColumnInfo(name = "suggested_follow_ups_json") val suggestedFollowUpsJson: String,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "pack_priority") val packPriority: Int,
    @ColumnInfo(name = "content_version") val contentVersion: Int,
    @ColumnInfo(name = "is_core_topic") val isCoreTopic: Boolean,
    @ColumnInfo(name = "is_active_winner") val isActiveWinner: Boolean
) {
    /**
     * Converts this stored topic header (combined with its ordered keywords) into the
     * existing 5-field [LocalKnowledgeBase.KnowledgeTopic] model without altering the legacy model.
     */
    fun toLegacyKnowledgeTopic(
        keywords: List<String>,
        converters: KnowledgeTypeConverters = KnowledgeTypeConverters()
    ): LocalKnowledgeBase.KnowledgeTopic {
        val followUps: List<ActionableOption> =
            converters.toActionableOptionList(suggestedFollowUpsJson)
        return LocalKnowledgeBase.KnowledgeTopic(
            id = topicId,
            keywords = keywords,
            arabicSummary = arabicSummary,
            englishSummary = englishSummary,
            suggestedFollowUps = followUps
        )
    }
}
