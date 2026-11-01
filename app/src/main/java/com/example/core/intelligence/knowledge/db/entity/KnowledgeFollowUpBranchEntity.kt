package com.example.core.intelligence.knowledge.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "knowledge_followup_branches",
    primaryKeys = ["branch_id", "pack_id"],
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
            value = ["topic_id", "pack_id", "priority_order"],
            unique = true,
            name = "idx_followup_topic_pack_priority"
        )
    ]
)
data class KnowledgeFollowUpBranchEntity(
    @ColumnInfo(name = "branch_id") val branchId: String,
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "priority_order") val priorityOrder: Int,
    @ColumnInfo(name = "match_mode") val matchMode: String,
    @ColumnInfo(name = "trigger_keywords_json") val triggerKeywordsJson: String,
    @ColumnInfo(name = "response_arabic") val responseArabic: String,
    @ColumnInfo(name = "response_english") val responseEnglish: String,
    @ColumnInfo(name = "actionable_options_json") val actionableOptionsJson: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean
)
