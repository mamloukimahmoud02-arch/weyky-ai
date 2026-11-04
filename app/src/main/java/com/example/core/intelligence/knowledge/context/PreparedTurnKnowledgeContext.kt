package com.example.core.intelligence.knowledge.context

import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.search.ActiveTopicIdentity
import com.example.core.intelligence.knowledge.search.InMemorySearchSnapshot
import com.example.core.intelligence.knowledge.search.ScoredTopicCandidate

/**
 * Explicit availability status for knowledge prepared during a single conversational turn.
 */
enum class KnowledgeAvailabilityStatus {
    /**
     * A matching topic was retrieved (`finalSearchScore >= 80`) and has usable localized summary
     * or specific factual answer content.
     */
    MATCHED_USABLE_KNOWLEDGE,

    /**
     * A topic ID was matched, but the topic lacks usable content for the requested inquiry
     * (for example, blank summary or missing requested detail).
     */
    MATCHED_INSUFFICIENT_INFO,

    /**
     * No topic met the minimum confidence threshold (`finalSearchScore < 80`).
     */
    NO_MATCHING_TOPIC,

    /**
     * Knowledge retrieval encountered an unexpected error or no valid snapshot was available;
     * handled gracefully without crashing the conversation pipeline.
     */
    RETRIEVAL_FAILURE_OR_UNAVAILABLE
}

/**
 * Lightweight, immutable turn-scoped knowledge context prepared once per conversational turn.
 *
 * Designed for WEYKY Phase 2B.3 & Phase 2B.4.2A to:
 * 1. Avoid repeated Arabic/English query normalizations within the same turn.
 * 2. Avoid repeated calls to [com.example.core.intelligence.knowledge.search.KnowledgeSearchService]
 *    across explicit-return detection, follow-up topic-switch checks, and direct knowledge lookup.
 * 3. Guarantee single-turn snapshot consistency by pinning the [snapshotUsed] captured at turn start.
 * 4. Keep heavy encyclopedic details out of RAM until explicitly needed, while pinning the exact
 *    winning [ActiveTopicIdentity] (`topicId + packId + contentVersion`) for on-demand detail lookup.
 */
data class PreparedTurnKnowledgeContext(
    val userQuery: String,
    val strictNormalizedQuery: String,
    val relaxedNormalizedQuery: String,
    val isArabic: Boolean,
    val currentConversationalTopicId: String?,
    val effectiveSearchContextTopicId: String?,
    val retrievedTopicId: String?,
    val retrievedTopic: LocalKnowledgeBase.KnowledgeTopic?,
    val rawSearchScore: Int,
    val finalSearchScore: Int,
    val rankedCandidates: List<ScoredTopicCandidate>,
    val candidateTopicIdsRetrievedByIndex: Set<String> = emptySet(),
    val unbiasedTopTopicId: String? = null,
    val unbiasedTopTopic: LocalKnowledgeBase.KnowledgeTopic? = null,
    val unbiasedRawScore: Int = 0,
    val hasContinuitySignal: Boolean,
    val specificCapitalAnswer: String?,
    val knowledgeGeneration: Long,
    val hasValidKnowledgeMatch: Boolean,
    val availabilityStatus: KnowledgeAvailabilityStatus,
    val snapshotUsed: InMemorySearchSnapshot? = null,
    val matchedAspectId: String? = null,
    val matchedAspectAnswer: String? = null
) {
    /**
     * The pinned [ActiveTopicIdentity] (`topicId + packId + contentVersion`) of [retrievedTopicId]
     * in [snapshotUsed], ensuring on-demand detailed content lookup uses the exact active winner
     * from this turn's snapshot generation.
     */
    val retrievedTopicIdentity: ActiveTopicIdentity?
        get() = retrievedTopicId?.let { snapshotUsed?.activeTopicIdentitiesById?.get(it) }

    /**
     * Looks up a topic by ID from the exact [snapshotUsed] captured at the start of this turn,
     * preserving single-turn snapshot consistency even if a background update occurs mid-turn.
     */
    fun getTopicById(topicId: String): LocalKnowledgeBase.KnowledgeTopic? {
        return snapshotUsed?.topicsById?.get(topicId)
    }

    /**
     * Looks up the active winning [ActiveTopicIdentity] (`topicId + packId + contentVersion`) for
     * [topicId] from the pinned [snapshotUsed] captured at the start of this turn.
     */
    fun getActiveTopicIdentity(topicId: String): ActiveTopicIdentity? {
        return snapshotUsed?.activeTopicIdentitiesById?.get(topicId)
    }

    /**
     * Resolves the localized response text for [retrievedTopic] when [hasValidKnowledgeMatch] is true,
     * prioritizing [matchedAspectAnswer] for sub-concept/aspect inquiries and [specificCapitalAnswer]
     * for `capitals_arab` inquiries, falling back to the general topic summary when the query is broad.
     */
    fun resolveMatchedResponseText(): String? {
        if (!hasValidKnowledgeMatch || retrievedTopic == null) return null
        if (!matchedAspectAnswer.isNullOrBlank()) {
            return matchedAspectAnswer
        }
        if (retrievedTopic.id == "capitals_arab" && !specificCapitalAnswer.isNullOrBlank()) {
            return specificCapitalAnswer
        }
        val summary = if (isArabic) retrievedTopic.arabicSummary else retrievedTopic.englishSummary
        return summary.takeIf { it.isNotBlank() }
    }
}
