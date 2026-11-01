package com.example.core.intelligence.knowledge.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "knowledge_topic_details",
    primaryKeys = ["topic_id", "pack_id"],
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeTopicHeaderEntity::class,
            parentColumns = ["topic_id", "pack_id"],
            childColumns = ["topic_id", "pack_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["topic_id", "pack_id"], unique = true, name = "idx_details_topic_pack")
    ]
)
data class KnowledgeTopicDetailEntity(
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "detailed_arabic") val detailedArabic: String,
    @ColumnInfo(name = "detailed_english") val detailedEnglish: String,
    @ColumnInfo(name = "related_topic_ids_json") val relatedTopicIdsJson: String,
    @ColumnInfo(name = "examples_json") val examplesJson: String,
    @ColumnInfo(name = "last_reviewed") val lastReviewed: Long,
    @ColumnInfo(name = "additional_content_json") val additionalContentJson: String? = null
)
