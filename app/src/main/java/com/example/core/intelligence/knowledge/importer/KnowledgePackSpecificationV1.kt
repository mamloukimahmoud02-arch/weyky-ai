package com.example.core.intelligence.knowledge.importer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Official specification constants, safety limits, and intermediate parsed DTOs for
 * `WEYKY_KNOWLEDGE_PACK_V1`.
 *
 * Designed to support:
 * 1. Pack Metadata (identity, versioning, priority, source type, optional SHA-256)
 * 2. Topics (summaries, titles, category, order index, priority, content version, keywords, follow-ups)
 * 3. Lazy-loaded Topic Details (detailed Arabic/English explanations, examples, related topic references)
 * 4. Follow-Up Branches (priority order, match mode, triggers, bilingual responses, actionable options)
 */
object KnowledgePackSpecificationV1 {
    const val FORMAT_V1 = "WEYKY_KNOWLEDGE_PACK_V1"
    const val SUPPORTED_SCHEMA_VERSION = 1

    const val SOURCE_TYPE_BUNDLED_ASSET = "BUNDLED_ASSET"
    const val SOURCE_TYPE_LOCAL_UPDATE = "LOCAL_UPDATE"
    const val SOURCE_TYPE_SEED_BUILTIN = "SEED_BUILTIN"

    val ALLOWED_EXTERNAL_SOURCE_TYPES = setOf(
        SOURCE_TYPE_BUNDLED_ASSET,
        SOURCE_TYPE_LOCAL_UPDATE
    )

    val ALLOWED_LANGUAGES = setOf("ar", "en", "bilingual", "ar-en", "mixed")

    val ALLOWED_MATCH_MODES = setOf(
        "SUBSTRING_CONTAINS",
        "WORD_BOUNDARY",
        "DEFAULT_FALLBACK"
    )

    val IDENTIFIER_REGEX = Regex("^[a-zA-Z0-9_.-]{1,128}$")
    val SHA256_HEX_REGEX = Regex("^[a-fA-F0-9]{64}$")

    /**
     * Computes the lowercase 64-character hex SHA-256 digest of raw bytes.
     */
    fun computeRawBytesSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes a deterministic canonical logical SHA-256 across the semantic content of the pack,
     * deliberately excluding `contentSha256`, `importedAt`, and whitespace formatting so that:
     * 1. Embedded `contentSha256` never suffers from circular self-hashing.
     * 2. Identical logical packs formatted with different JSON whitespace produce the exact same
     *    canonical logical hash.
     */
    fun computeLogicalContentSha256(pack: ParsedKnowledgePackV1): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun update(token: String) {
            val bytes = token.toByteArray(StandardCharsets.UTF_8)
            digest.update(bytes)
            digest.update(0.toByte())
        }

        update("FORMAT:${pack.packFormat}")
        update("SCHEMA:${pack.schemaVersion}")
        update("PACK_ID:${pack.packId}")
        update("PACK_NAME:${pack.packName}")
        update("PACK_VERSION:${pack.packVersion}")
        update("CONTENT_VERSION:${pack.contentVersion}")
        update("SOURCE_TYPE:${pack.sourceType}")
        update("PRIORITY:${pack.packPriority}")
        update("LANGUAGE:${pack.language}")
        update("DESC:${pack.description}")
        update("TOPIC_COUNT:${pack.topics.size}")

        for (topic in pack.topics.sortedBy { it.topicId }) {
            update("TOPIC:${topic.topicId}")
            update("ORDER:${topic.topicOrderIndex}")
            update("TOPIC_PRIORITY:${topic.priority}")
            update("TOPIC_CV:${topic.contentVersion}")
            update("TITLE:${topic.title}")
            update("AR_TITLE:${topic.arabicTitle}")
            update("EN_TITLE:${topic.englishTitle}")
            update("CATEGORY:${topic.category}")
            update("AR_SUMMARY:${topic.arabicSummary}")
            update("EN_SUMMARY:${topic.englishSummary}")
            update("KEYWORDS:${topic.keywords.size}")
            topic.keywords.forEach { kw -> update("KW:$kw") }
            update("FOLLOW_UPS:${topic.suggestedFollowUps.size}")
            topic.suggestedFollowUps.forEach { fu -> update("FU:$fu") }
            update("REFS:${topic.referenceTopicIds.size}")
            topic.referenceTopicIds.forEach { ref -> update("REF:$ref") }

            val detail = topic.detail
            if (detail != null) {
                update("DETAIL_AR:${detail.detailedArabic}")
                update("DETAIL_EN:${detail.detailedEnglish}")
                update("DETAIL_REFS:${detail.relatedTopicIds.size}")
                detail.relatedTopicIds.forEach { ref -> update("DREF:$ref") }
                update("EXAMPLES:${detail.examples.size}")
                detail.examples.forEach { ex -> update("EX:$ex") }
                update("LAST_REVIEWED:${detail.lastReviewed}")
            } else {
                update("DETAIL:NONE")
            }

            update("BRANCHES:${topic.followUpBranches.size}")
            for (branch in topic.followUpBranches.sortedBy { it.priorityOrder }) {
                update("BRANCH:${branch.branchId}")
                update("BP:${branch.priorityOrder}")
                update("BM:${branch.matchMode}")
                update("BD:${branch.isDefault}")
                update("BAR:${branch.responseArabic}")
                update("BEN:${branch.responseEnglish}")
                update("BTRIG:${branch.triggerKeywords.size}")
                branch.triggerKeywords.forEach { tk -> update("BTK:$tk") }
                update("BOPT:${branch.actionableOptions.size}")
                branch.actionableOptions.forEach { opt ->
                    update("BO:${opt.labelArabic}|${opt.labelEnglish}|${opt.queryText}")
                }
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Centralized, configurable, and documented safety limits for `WEYKY_KNOWLEDGE_PACK_V1` parsing
 * and validation. Protects memory and storage against malformed or adversarial JSON payloads.
 */
data class KnowledgePackImportLimits(
    val maxInputBytes: Long = DEFAULT_MAX_INPUT_BYTES,
    val maxJsonNestingDepth: Int = DEFAULT_MAX_JSON_NESTING_DEPTH,
    val maxTopicsPerPack: Int = DEFAULT_MAX_TOPICS_PER_PACK,
    val maxKeywordsPerTopic: Int = DEFAULT_MAX_KEYWORDS_PER_TOPIC,
    val maxTokensPerKeyword: Int = DEFAULT_MAX_TOKENS_PER_KEYWORD,
    val maxFollowUpBranchesPerTopic: Int = DEFAULT_MAX_BRANCHES_PER_TOPIC,
    val maxTriggerKeywordsPerBranch: Int = DEFAULT_MAX_TRIGGERS_PER_BRANCH,
    val maxActionableOptionsPerBranch: Int = DEFAULT_MAX_OPTIONS_PER_BRANCH,
    val maxSuggestedFollowUpsPerTopic: Int = DEFAULT_MAX_SUGGESTED_FOLLOW_UPS,
    val maxReferenceTopicsPerTopic: Int = DEFAULT_MAX_REFERENCE_TOPICS,
    val maxExamplesPerTopic: Int = DEFAULT_MAX_EXAMPLES_PER_TOPIC,
    val maxIdentifierLength: Int = DEFAULT_MAX_IDENTIFIER_LENGTH,
    val maxTitleLength: Int = DEFAULT_MAX_TITLE_LENGTH,
    val maxCategoryLength: Int = DEFAULT_MAX_CATEGORY_LENGTH,
    val maxKeywordLength: Int = DEFAULT_MAX_KEYWORD_LENGTH,
    val maxSummaryLength: Int = DEFAULT_MAX_SUMMARY_LENGTH,
    val maxDetailedTextLength: Int = DEFAULT_MAX_DETAILED_TEXT_LENGTH,
    val maxBranchResponseLength: Int = DEFAULT_MAX_BRANCH_RESPONSE_LENGTH,
    val maxDescriptionLength: Int = DEFAULT_MAX_DESCRIPTION_LENGTH
) {
    companion object {
        const val DEFAULT_MAX_INPUT_BYTES: Long = 8L * 1024L * 1024L // 8 MB max raw JSON stream
        const val DEFAULT_MAX_JSON_NESTING_DEPTH: Int = 12
        const val DEFAULT_MAX_TOPICS_PER_PACK: Int = 2_000
        const val DEFAULT_MAX_KEYWORDS_PER_TOPIC: Int = 64
        const val DEFAULT_MAX_TOKENS_PER_KEYWORD: Int = 24
        const val DEFAULT_MAX_BRANCHES_PER_TOPIC: Int = 20
        const val DEFAULT_MAX_TRIGGERS_PER_BRANCH: Int = 32
        const val DEFAULT_MAX_OPTIONS_PER_BRANCH: Int = 10
        const val DEFAULT_MAX_SUGGESTED_FOLLOW_UPS: Int = 12
        const val DEFAULT_MAX_REFERENCE_TOPICS: Int = 32
        const val DEFAULT_MAX_EXAMPLES_PER_TOPIC: Int = 24
        const val DEFAULT_MAX_IDENTIFIER_LENGTH: Int = 128
        const val DEFAULT_MAX_TITLE_LENGTH: Int = 256
        const val DEFAULT_MAX_CATEGORY_LENGTH: Int = 128
        const val DEFAULT_MAX_KEYWORD_LENGTH: Int = 256
        const val DEFAULT_MAX_SUMMARY_LENGTH: Int = 4_000
        const val DEFAULT_MAX_DETAILED_TEXT_LENGTH: Int = 32_768
        const val DEFAULT_MAX_BRANCH_RESPONSE_LENGTH: Int = 4_000
        const val DEFAULT_MAX_DESCRIPTION_LENGTH: Int = 2_000
    }
}

/**
 * Intermediate parsed representation of a `WEYKY_KNOWLEDGE_PACK_V1` JSON document.
 */
data class ParsedKnowledgePackV1(
    val packFormat: String = KnowledgePackSpecificationV1.FORMAT_V1,
    val schemaVersion: Int,
    val packId: String,
    val packName: String,
    val packVersion: Int,
    val contentVersion: Int,
    val sourceType: String,
    val packPriority: Int,
    val description: String,
    val language: String,
    val createdAt: Long,
    val updatedAt: Long,
    val contentSha256: String?,
    val topics: List<ParsedTopicV1>,
    val rawBytesSha256: String,
    val rawByteSize: Long
)

data class ParsedTopicV1(
    val topicId: String,
    val title: String,
    val arabicTitle: String,
    val englishTitle: String,
    val category: String,
    val topicOrderIndex: Int,
    val priority: Int,
    val contentVersion: Int,
    val arabicSummary: String,
    val englishSummary: String,
    val keywords: List<String>,
    val suggestedFollowUps: List<String>,
    val referenceTopicIds: List<String>,
    val detail: ParsedTopicDetailV1?,
    val followUpBranches: List<ParsedFollowUpBranchV1>
)

data class ParsedTopicDetailV1(
    val detailedArabic: String,
    val detailedEnglish: String,
    val relatedTopicIds: List<String>,
    val examples: List<String>,
    val lastReviewed: Long
)

data class ParsedFollowUpBranchV1(
    val branchId: String,
    val priorityOrder: Int,
    val matchMode: String,
    val triggerKeywords: List<String>,
    val responseArabic: String,
    val responseEnglish: String,
    val actionableOptions: List<ParsedActionableOptionV1>,
    val isDefault: Boolean
)

data class ParsedActionableOptionV1(
    val labelArabic: String,
    val labelEnglish: String,
    val queryText: String
)
