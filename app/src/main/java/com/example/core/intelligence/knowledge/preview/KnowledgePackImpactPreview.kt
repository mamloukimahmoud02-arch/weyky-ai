package com.example.core.intelligence.knowledge.preview

import com.example.core.intelligence.knowledge.validation.ValidationIssue

/**
 * Type of pack mutation operation being previewed (Phase 2B.4.2C.4).
 */
enum class PackOperationType {
    IMPORT_OR_UPDATE,
    DEACTIVATE,
    REACTIVATE,
    UPDATE_PRIORITY,
    DELETE
}

/**
 * Overall status of the impact preview analysis.
 */
enum class ImpactPreviewStatus {
    /**
     * Operation is structurally valid, preserves reference integrity, causes no core violations,
     * and is expected to succeed cleanly without any warnings.
     */
    SAFE_TO_APPLY,

    /**
     * Operation is valid and can be applied, but produces non-blocking warnings (e.g. shadowed topics,
     * displaced winners, or existing pack re-import with matching content).
     */
    SAFE_WITH_WARNINGS,

    /**
     * Operation fails format, JSON, limits, duplicate ID/keyword, or structural validation.
     */
    BLOCKED_BY_VALIDATION,

    /**
     * Operation would break outgoing or incoming cross-pack topic references.
     */
    BLOCKED_BY_REFERENCE_INTEGRITY,

    /**
     * Operation attempts to modify, overwrite, disable, or delete protected core seed pack/topics.
     */
    BLOCKED_BY_CORE_PROTECTION,

    /**
     * Analysis could not be performed (e.g., target pack not found, database unavailable).
     */
    ANALYSIS_UNAVAILABLE,

    /**
     * Database generation changed between analysis start and completion (concurrency conflict).
     */
    STALE_PREVIEW
}

/**
 * Specific nature of change for a topic identity in the previewed operation.
 */
enum class TopicChangeType {
    /** Topic is newly introduced by the incoming pack and did not previously exist in this pack. */
    ADDED,

    /** Topic was already in this pack and is updated with a newer version or content. */
    UPDATED,

    /** Topic was in an earlier version of this pack and is omitted / removed in the incoming version. */
    REMOVED,

    /** Topic exists in this pack with identical content and version (unchanged). */
    UNCHANGED,

    /** Topic is introduced or updated, but is shadowed by a higher-priority winning pack. */
    SHADOWED,

    /** Topic belonged to a disabled pack and is restored to active competition upon pack reactivation. */
    REACTIVATED
}

/**
 * Detailed change analysis for a single topic identity.
 */
data class TopicChange(
    val topicId: String,
    val changeType: TopicChangeType,
    val previousPackId: String?,
    val incomingPackId: String?,
    val previousContentVersion: Int?,
    val incomingContentVersion: Int?,
    val previousWinnerPackId: String?,
    val predictedWinnerPackId: String?,
    val winnerWillChange: Boolean
)

/**
 * Specific nature of impact on a cross-topic or cross-pack reference.
 */
enum class ReferenceImpactType {
    /** Reference target remains an active winning topic. */
    PRESERVED,

    /** Reference target topic is preserved, but its active winning pack will change. */
    TARGET_WINNER_CHANGED,

    /** Operation removes the last active winner of the referenced topic with no alternative fallback. */
    TARGET_REMOVED_NO_FALLBACK,

    /** Referenced target topic exists only in an inactive/disabled pack. */
    TARGET_INACTIVE,

    /** Referenced topic does not exist anywhere in the database or incoming pack. */
    NONEXISTENT_TARGET,

    /** Topic references itself (forbidden). */
    SELF_REFERENCE_DISALLOWED,

    /** Topic contains duplicate reference entries. */
    DUPLICATE_REFERENCE
}

/**
 * Analysis of an outgoing or incoming topic reference.
 */
data class ReferenceImpact(
    val sourceTopicId: String,
    val sourcePackId: String,
    val referencedTopicId: String,
    val currentTargetWinnerPackId: String?,
    val predictedTargetWinnerPackId: String?,
    val impactType: ReferenceImpactType,
    val isBlocking: Boolean
)

/**
 * High-level counts and statistics for the preview.
 */
data class ImpactPreviewSummary(
    val addedTopicCount: Int = 0,
    val updatedTopicCount: Int = 0,
    val removedTopicCount: Int = 0,
    val unchangedTopicCount: Int = 0,
    val shadowedTopicCount: Int = 0,
    val reactivatedTopicCount: Int = 0,
    val winnerChangedCount: Int = 0,
    val totalAffectedTopicCount: Int = 0,
    val cascadeKeywordsCount: Int = 0,
    val cascadeTokensCount: Int = 0,
    val cascadeDetailsCount: Int = 0,
    val cascadeFollowUpsCount: Int = 0,
    val blockingIssueCount: Int = 0,
    val warningCount: Int = 0
)

/**
 * Immutable result model representing the simulated impact of a proposed Knowledge Pack operation.
 */
data class KnowledgePackImpactPreview(
    val operationType: PackOperationType,
    val packId: String,
    val currentPackVersion: Int?,
    val incomingPackVersion: Int?,
    val currentDatabaseGeneration: Long,
    val analyzedDatabaseGeneration: Long,
    val previewStatus: ImpactPreviewStatus,
    val canApply: Boolean,
    val summary: ImpactPreviewSummary,
    val topicChanges: List<TopicChange>,
    val winnerChanges: List<TopicChange>,
    val referenceImpacts: List<ReferenceImpact>,
    val validationIssues: List<ValidationIssue>,
    val warnings: List<String>
) {
    val isSafeToApply: Boolean
        get() = canApply && (previewStatus == ImpactPreviewStatus.SAFE_TO_APPLY || previewStatus == ImpactPreviewStatus.SAFE_WITH_WARNINGS)

    val isBlocked: Boolean
        get() = !canApply
}
