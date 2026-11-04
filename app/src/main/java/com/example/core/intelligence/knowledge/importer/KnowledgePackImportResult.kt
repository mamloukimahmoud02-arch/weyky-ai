package com.example.core.intelligence.knowledge.importer

/**
 * Exhaustive status codes for `KnowledgePackImporter` operations.
 */
enum class KnowledgePackImportStatus {
    /** New external pack was validated, stored atomically, and published to the active snapshot. */
    SUCCESS_IMPORTED,

    /** Existing external pack was updated with a newer version and published to the active snapshot. */
    SUCCESS_UPDATED,

    /** Pack with identical ID, version, and content/hash is already imported (idempotent no-op). */
    ALREADY_IMPORTED,

    /** Structural or semantic validation of pack/topics/keywords/branches failed. */
    VALIDATION_FAILED,

    /** Pack `schemaVersion` or `packFormat` is unsupported by this importer. */
    SCHEMA_UNSUPPORTED,

    /** Raw or logical SHA-256 did not match `expectedSha256` or embedded `contentSha256`. */
    HASH_MISMATCH,

    /** Version conflict (downgrade or same version with different content) or duplicate IDs inside pack. */
    DUPLICATE_CONFLICT,

    /** Invalid topic reference (missing target, self-reference, duplicate ref, inactive target, or broken dependent). */
    REFERENCE_INVALID,

    /** Attempted to overwrite or shadow `core_seed_pack` or any protected core seed topic. */
    CORE_PROTECTION_VIOLATION,

    /** Database transaction failed and rolled back cleanly. */
    STORAGE_FAILURE,

    /** Database transaction committed, and snapshot refresh encountered an error (with recovery status tracked). */
    SNAPSHOT_REFRESH_FAILURE,

    /** Input stream or payload exceeded `KnowledgePackImportLimits.maxInputBytes`. */
    INPUT_TOO_LARGE,

    /** Malformed JSON, duplicate JSON keys, unexpected token type, missing required JSON field, or depth exceeded. */
    PARSING_FAILURE
}

/**
 * Tracks the exact post-import state of `InMemorySearchSnapshot`.
 */
enum class SnapshotRefreshState {
    /** Snapshot was not modified (e.g., rejected before storage or idempotent ALREADY_IMPORTED). */
    NOT_MODIFIED,

    /** Snapshot was rebuilt from Room and published atomically. */
    REFRESHED_ATOMICALLY,

    /** Initial snapshot rebuild failed after commit, but automatic recovery from Room succeeded. */
    RECOVERED_FROM_DATABASE,

    /** Both initial snapshot rebuild and recovery failed; previous valid snapshot was retained. */
    FAILED_PREVIOUS_SNAPSHOT_RETAINED
}

/**
 * Execution timing and diagnostic metrics for a knowledge pack import operation.
 */
data class KnowledgePackImportMetrics(
    val parseTimeMs: Long = 0L,
    val validationTimeMs: Long = 0L,
    val storageTransactionTimeMs: Long = 0L,
    val snapshotRebuildTimeMs: Long = 0L,
    val totalImportTimeMs: Long = 0L,
    val inputByteCount: Long = 0L,
    val detailCacheSizeAfterImport: Int = 0
)

/**
 * Structured, non-throwing result returned by `KnowledgePackImporter`.
 */
data class KnowledgePackImportResult(
    val status: KnowledgePackImportStatus,
    val packId: String? = null,
    val packVersion: Int? = null,
    val contentVersion: Int? = null,
    val importedTopicCount: Int = 0,
    val updatedTopicCount: Int = 0,
    val skippedTopicCount: Int = 0,
    val rejectedTopicCount: Int = 0,
    val activeWinnerCountInSnapshot: Int = 0,
    val rawBytesSha256: String? = null,
    val logicalContentSha256: String? = null,
    val snapshotRefreshState: SnapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val metrics: KnowledgePackImportMetrics = KnowledgePackImportMetrics(),
    val committedGeneration: Long = 0L,
    val publishedGeneration: Long = 0L,
    val databaseCommitted: Boolean = false
) {
    val isSuccess: Boolean
        get() = status == KnowledgePackImportStatus.SUCCESS_IMPORTED ||
            status == KnowledgePackImportStatus.SUCCESS_UPDATED ||
            status == KnowledgePackImportStatus.ALREADY_IMPORTED
}
