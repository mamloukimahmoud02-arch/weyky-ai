package com.example.core.intelligence.knowledge.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_pack_metadata",
    indices = [
        Index(value = ["source_type", "pack_priority"], name = "idx_packs_source_priority")
    ]
)
data class KnowledgePackMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "pack_version") val packVersion: Int,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "sha256_checksum") val sha256Checksum: String,
    @ColumnInfo(name = "pack_priority") val packPriority: Int,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "topic_count") val topicCount: Int
)
