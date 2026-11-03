package com.example.core.intelligence.knowledge.repository

import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.dao.KnowledgePriorityAndWinnerPolicy
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity

/**
 * Read-only summary of an installed knowledge pack in WEYKY (Phase 2B.4.2C.1).
 *
 * Built strictly from verified columns in [KnowledgePackMetadataEntity] and priority decoding
 * in [KnowledgePriorityAndWinnerPolicy]. Never exposes raw negative disabled priority encoding
 * and never fabricates unverified metadata fields.
 */
data class InstalledKnowledgePackSummary(
    val packId: String,
    val sourceType: String,
    val packVersion: Int,
    val schemaVersion: Int,
    val sha256Checksum: String,
    val logicalPriority: Int,
    val enabled: Boolean,
    val importedAt: Long,
    val topicCount: Int
) {
    /**
     * Convenience alias for [enabled].
     */
    val isEnabled: Boolean
        get() = enabled

    /**
     * True if and only if this pack is the canonical built-in `core_seed_pack`.
     */
    val isCoreSeedPack: Boolean
        get() = packId == SeedKnowledgeCatalog.CORE_SEED_PACK_ID &&
            sourceType == SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE
}

/**
 * Explicit read-only state of a topic within an installed knowledge pack (Phase 2B.4.2C.1).
 */
enum class InstalledPackTopicState {
    /**
     * The topic belongs to an enabled pack and is the current active winner according to
     * [KnowledgePriorityAndWinnerPolicy].
     */
    ACTIVE_WINNER,

    /**
     * The topic is a canonical protected Core Seed topic in `core_seed_pack` and is permanently
     * protected as the active winner according to [KnowledgePriorityAndWinnerPolicy].
     */
    PROTECTED_CORE,

    /**
     * The topic belongs to an enabled pack, but another competing pack currently wins this
     * `topicId` according to [KnowledgePriorityAndWinnerPolicy].
     */
    SHADOWED,

    /**
     * The topic belongs to a disabled pack (`pack_priority < 0`) and is excluded from active
     * winner selection until its pack is reactivated.
     */
    DISABLED_PACK
}

/**
 * Read-only status of a single topic belonging to an installed knowledge pack (Phase 2B.4.2C.1).
 *
 * Winner evaluation is computed from database topic headers using
 * [KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader] without loading heavy
 * `knowledge_topic_details` or `knowledge_followup_branches` records.
 */
data class InstalledPackTopicStatus(
    val topicId: String,
    val packId: String,
    val topicOrderIndex: Int,
    val contentVersion: Int,
    val logicalPriority: Int,
    val packEnabled: Boolean,
    val isCoreTopic: Boolean,
    val isActiveWinner: Boolean,
    val status: InstalledPackTopicState,
    val currentWinningPackId: String?
)

/**
 * Read-only details for a single installed knowledge pack, combining its [summary] with the
 * ordered list of [topics] belonging to the pack and their active/shadowed/disabled statuses
 * (Phase 2B.4.2C.1).
 */
data class InstalledKnowledgePackDetails(
    val summary: InstalledKnowledgePackSummary,
    val topics: List<InstalledPackTopicStatus>
) {
    val packId: String
        get() = summary.packId

    val activeWinningTopicCount: Int
        get() = topics.count { it.isActiveWinner }

    val shadowedTopicCount: Int
        get() = topics.count { it.status == InstalledPackTopicState.SHADOWED }

    val disabledTopicCount: Int
        get() = topics.count { it.status == InstalledPackTopicState.DISABLED_PACK }
}

/**
 * Read-only status of a competing version candidate for a single `topicId` across installed
 * knowledge packs (Phase 2B.4.2C.1).
 *
 * [isCurrentWinner] is determined exclusively via
 * [KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader].
 */
data class TopicVersionCandidateStatus(
    val topicId: String,
    val packId: String,
    val sourceType: String,
    val contentVersion: Int,
    val packPriority: Int,
    val packEnabled: Boolean,
    val isCoreTopic: Boolean,
    val isCurrentWinner: Boolean
) {
    /**
     * Convenience alias for [packPriority] (which always holds the decoded non-negative logical priority).
     */
    val logicalPriority: Int
        get() = packPriority
}

/**
 * Pure, deterministic mapper functions for Phase 2B.4.2C.1 Read APIs backed by
 * [KnowledgePriorityAndWinnerPolicy].
 */
internal object KnowledgePackReadModelMapper {

    /**
     * Deterministic ordering for installed pack summaries:
     * 1. Enabled packs before disabled packs (`enabled DESC`)
     * 2. Higher decoded logical priority (`logicalPriority DESC`)
     * 3. Higher pack version (`packVersion DESC`)
     * 4. Lexicographical tie-breaker (`packId ASC`)
     */
    val PACK_SUMMARY_COMPARATOR: Comparator<InstalledKnowledgePackSummary> =
        Comparator { a, b ->
            val enabledCmp = b.enabled.compareTo(a.enabled)
            if (enabledCmp != 0) return@Comparator enabledCmp

            val priorityCmp = b.logicalPriority.compareTo(a.logicalPriority)
            if (priorityCmp != 0) return@Comparator priorityCmp

            val versionCmp = b.packVersion.compareTo(a.packVersion)
            if (versionCmp != 0) return@Comparator versionCmp

            a.packId.compareTo(b.packId)
        }

    /**
     * Deterministic ordering for competing topic version candidates that delegates to
     * [KnowledgePriorityAndWinnerPolicy.WINNER_HEADER_COMPARATOR] after separating enabled
     * from disabled candidates and normalizing disabled stored priorities to their decoded
     * logical priorities.
     */
    private val CANDIDATE_HEADER_DISPLAY_COMPARATOR: Comparator<KnowledgeTopicHeaderEntity> =
        Comparator { a, b ->
            val aEnabled = KnowledgePriorityAndWinnerPolicy.isStoredPriorityEnabled(a.packPriority)
            val bEnabled = KnowledgePriorityAndWinnerPolicy.isStoredPriorityEnabled(b.packPriority)
            if (aEnabled != bEnabled) {
                return@Comparator if (aEnabled) -1 else 1
            }
            val aNormalized = if (aEnabled) {
                a
            } else {
                a.copy(packPriority = KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(a.packPriority))
            }
            val bNormalized = if (bEnabled) {
                b
            } else {
                b.copy(packPriority = KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(b.packPriority))
            }
            KnowledgePriorityAndWinnerPolicy.WINNER_HEADER_COMPARATOR.compare(aNormalized, bNormalized)
        }

    fun toInstalledPackSummary(entity: KnowledgePackMetadataEntity): InstalledKnowledgePackSummary {
        val enabled = KnowledgePriorityAndWinnerPolicy.isStoredPriorityEnabled(entity.packPriority)
        val logicalPriority = KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(entity.packPriority)
        return InstalledKnowledgePackSummary(
            packId = entity.packId,
            sourceType = entity.sourceType,
            packVersion = entity.packVersion,
            schemaVersion = entity.schemaVersion,
            sha256Checksum = entity.sha256Checksum,
            logicalPriority = logicalPriority,
            enabled = enabled,
            importedAt = entity.importedAt,
            topicCount = entity.topicCount
        )
    }

    fun buildInstalledPackSummaries(
        entities: Iterable<KnowledgePackMetadataEntity>
    ): List<InstalledKnowledgePackSummary> {
        return entities
            .map(::toInstalledPackSummary)
            .sortedWith(PACK_SUMMARY_COMPARATOR)
    }

    fun buildInstalledPackDetails(
        packEntity: KnowledgePackMetadataEntity,
        packTopicHeaders: Iterable<KnowledgeTopicHeaderEntity>,
        allCompetingTopicHeaders: Iterable<KnowledgeTopicHeaderEntity>
    ): InstalledKnowledgePackDetails {
        val summary = toInstalledPackSummary(packEntity)
        val orderedPackHeaders = packTopicHeaders.sortedWith(
            compareBy<KnowledgeTopicHeaderEntity> { it.topicOrderIndex }
                .thenBy { it.topicId }
        )
        if (orderedPackHeaders.isEmpty()) {
            return InstalledKnowledgePackDetails(
                summary = summary,
                topics = emptyList()
            )
        }

        val competingByTopicId = allCompetingTopicHeaders.groupBy { it.topicId }
        val topicStatuses = orderedPackHeaders.map { header ->
            val effectiveHeader = if (header.packPriority != packEntity.packPriority) {
                header.copy(packPriority = packEntity.packPriority)
            } else {
                header
            }
            val rawCandidatesForTopic = competingByTopicId[header.topicId].orEmpty()
            val candidatesForTopic = if (rawCandidatesForTopic.isEmpty()) {
                listOf(effectiveHeader)
            } else {
                rawCandidatesForTopic.map { candidate ->
                    if (candidate.packId == packEntity.packId && candidate.packPriority != packEntity.packPriority) {
                        candidate.copy(packPriority = packEntity.packPriority)
                    } else {
                        candidate
                    }
                }
            }

            val winningHeader = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(candidatesForTopic)
            val packEnabled = KnowledgePriorityAndWinnerPolicy.isStoredPriorityEnabled(effectiveHeader.packPriority)
            val logicalPriority = KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(effectiveHeader.packPriority)
            val isCanonicalCore = KnowledgePriorityAndWinnerPolicy.isCanonicalCoreSeedTopic(effectiveHeader)
            val isWinner = packEnabled &&
                winningHeader != null &&
                winningHeader.packId == effectiveHeader.packId &&
                winningHeader.topicId == effectiveHeader.topicId

            val state = when {
                !packEnabled -> InstalledPackTopicState.DISABLED_PACK
                isWinner && isCanonicalCore -> InstalledPackTopicState.PROTECTED_CORE
                isWinner -> InstalledPackTopicState.ACTIVE_WINNER
                else -> InstalledPackTopicState.SHADOWED
            }

            InstalledPackTopicStatus(
                topicId = effectiveHeader.topicId,
                packId = effectiveHeader.packId,
                topicOrderIndex = effectiveHeader.topicOrderIndex,
                contentVersion = effectiveHeader.contentVersion,
                logicalPriority = logicalPriority,
                packEnabled = packEnabled,
                isCoreTopic = isCanonicalCore,
                isActiveWinner = isWinner,
                status = state,
                currentWinningPackId = winningHeader?.packId
            )
        }

        return InstalledKnowledgePackDetails(
            summary = summary,
            topics = topicStatuses
        )
    }

    fun buildTopicVersionCandidateStatuses(
        candidates: Iterable<KnowledgeTopicHeaderEntity>
    ): List<TopicVersionCandidateStatus> {
        val candidateList = candidates.toList()
        if (candidateList.isEmpty()) return emptyList()

        val winningHeader = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(candidateList)
        val sortedCandidates = candidateList.sortedWith(CANDIDATE_HEADER_DISPLAY_COMPARATOR)

        return sortedCandidates.map { header ->
            val isEnabled = KnowledgePriorityAndWinnerPolicy.isStoredPriorityEnabled(header.packPriority)
            val logicalPriority = KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(header.packPriority)
            val isCanonicalCore = KnowledgePriorityAndWinnerPolicy.isCanonicalCoreSeedTopic(header)
            val isWinner = isEnabled &&
                winningHeader != null &&
                winningHeader.packId == header.packId &&
                winningHeader.topicId == header.topicId

            TopicVersionCandidateStatus(
                topicId = header.topicId,
                packId = header.packId,
                sourceType = header.sourceType,
                contentVersion = header.contentVersion,
                packPriority = logicalPriority,
                packEnabled = isEnabled,
                isCoreTopic = isCanonicalCore,
                isCurrentWinner = isWinner
            )
        }
    }
}
