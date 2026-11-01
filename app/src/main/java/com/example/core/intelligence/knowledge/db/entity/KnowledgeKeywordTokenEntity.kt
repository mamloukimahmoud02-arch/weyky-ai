package com.example.core.intelligence.knowledge.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_keyword_tokens",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeKeywordEntity::class,
            parentColumns = ["keyword_id"],
            childColumns = ["keyword_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["keyword_id", "token_position"],
            unique = true,
            name = "idx_kw_tokens_keyword_pos"
        ),
        Index(value = ["strict_token"], name = "idx_kw_tokens_strict_token"),
        Index(value = ["strict_stem"], name = "idx_kw_tokens_strict_stem"),
        Index(value = ["relaxed_token"], name = "idx_kw_tokens_relaxed_token"),
        Index(value = ["relaxed_stem"], name = "idx_kw_tokens_relaxed_stem")
    ]
)
data class KnowledgeKeywordTokenEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "token_id") val tokenId: Long = 0L,
    @ColumnInfo(name = "keyword_id") val keywordId: Long,
    @ColumnInfo(name = "token_position") val tokenPosition: Int,
    @ColumnInfo(name = "strict_token") val strictToken: String,
    @ColumnInfo(name = "strict_stem") val strictStem: String,
    @ColumnInfo(name = "relaxed_token") val relaxedToken: String,
    @ColumnInfo(name = "relaxed_stem") val relaxedStem: String
)
