package com.example.core.intelligence.knowledge.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_keywords",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeTopicHeaderEntity::class,
            parentColumns = ["topic_id", "pack_id"],
            childColumns = ["topic_id", "pack_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["topic_id", "pack_id", "keyword_order"],
            unique = true,
            name = "idx_keywords_topic_pack_order"
        ),
        Index(value = ["strict_normalized_phrase"], name = "idx_keywords_strict_phrase"),
        Index(value = ["relaxed_normalized_phrase"], name = "idx_keywords_relaxed_phrase")
    ]
)
data class KnowledgeKeywordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "keyword_id") val keywordId: Long = 0L,
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "keyword_order") val keywordOrder: Int,
    @ColumnInfo(name = "original_keyword") val originalKeyword: String,
    @ColumnInfo(name = "strict_normalized_phrase") val strictNormalizedPhrase: String,
    @ColumnInfo(name = "relaxed_normalized_phrase") val relaxedNormalizedPhrase: String,
    @ColumnInfo(name = "relaxed_char_length") val relaxedCharLength: Int,
    @ColumnInfo(name = "token_count") val tokenCount: Int,
    @ColumnInfo(name = "content_token_count") val contentTokenCount: Int,
    @ColumnInfo(name = "match_type") val matchType: String,
    @ColumnInfo(name = "language") val language: String
)
