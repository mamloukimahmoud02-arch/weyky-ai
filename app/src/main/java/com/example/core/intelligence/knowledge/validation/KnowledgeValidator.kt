package com.example.core.intelligence.knowledge.validation

import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KnowledgePriorityAndWinnerPolicy
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.db.entity.KnowledgeFollowUpBranchEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicDetailEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity

/**
 * Strongly-typed validation issue categories required by Phase 2B.1.
 *
 * Explicitly distinguishes between:
 * 1. [VALIDATION_ERROR]
 * 2. [DUPLICATE_DATA]
 * 3. [MISSING_REFERENCE]
 * 4. [INVALID_SERIALIZATION]
 * 5. [UNSUPPORTED_SCHEMA_VERSION]
 */
enum class ValidationIssueCategory {
    VALIDATION_ERROR,
    DUPLICATE_DATA,
    MISSING_REFERENCE,
    INVALID_SERIALIZATION,
    UNSUPPORTED_SCHEMA_VERSION
}

data class ValidationIssue(
    val category: ValidationIssueCategory,
    val field: String,
    val message: String,
    val recordIdentifier: String? = null
)

sealed class ValidationResult {
    data object Valid : ValidationResult() {
        override val isValid: Boolean = true
        override val issues: List<ValidationIssue> = emptyList()
    }

    data class Invalid(
        override val issues: List<ValidationIssue>
    ) : ValidationResult() {
        override val isValid: Boolean = false
    }

    abstract val isValid: Boolean
    abstract val issues: List<ValidationIssue>

    fun hasCategory(category: ValidationIssueCategory): Boolean =
        issues.any { it.category == category }
}

/**
 * Validates knowledge pack metadata, topic headers, keywords, keyword tokens, topic details,
 * and follow-up branches prior to persistence in Room.
 *
 * Never uses silent fallbacks to conceal invalid or malformed data.
 */
object KnowledgeValidator {

    val SUPPORTED_SOURCE_TYPES: Set<String> = setOf(
        "SEED_BUILTIN",
        "BUNDLED_ASSET",
        "LOCAL_UPDATE"
    )

    val SUPPORTED_SCHEMA_VERSIONS: Set<Int> = setOf(1)

    val SUPPORTED_MATCH_TYPES: Set<String> = setOf(
        "SINGLE_TOKEN",
        "MULTI_WORD_PHRASE"
    )

    val SUPPORTED_LANGUAGES: Set<String> = setOf("ar", "en", "mixed")

    val SUPPORTED_BRANCH_MATCH_MODES: Set<String> = setOf(
        "SUBSTRING_CONTAINS",
        "WORD_BOUNDARY",
        "DEFAULT_FALLBACK"
    )

    /**
     * Validates a complete pack payload from [TopicStorageBundle]s, automatically assigning
     * synthetic keyword/token IDs when `keywordId == 0L` prior to Room insertion and enforcing
     * core seed protection rules when [enforceCoreTopicProtection] is true.
     */
    fun validatePackBundles(
        pack: KnowledgePackMetadataEntity,
        bundles: List<TopicStorageBundle>,
        referenceTopicIds: Set<String>? = null,
        enforceCoreTopicProtection: Boolean = true
    ): ValidationResult {
        val bundleIssues = mutableListOf<ValidationIssue>()
        val allHeaders = ArrayList<KnowledgeTopicHeaderEntity>(bundles.size)
        val allKeywords = ArrayList<KnowledgeKeywordEntity>()
        val allTokens = ArrayList<KnowledgeKeywordTokenEntity>()
        val allDetails = ArrayList<KnowledgeTopicDetailEntity>()
        val allBranches = ArrayList<KnowledgeFollowUpBranchEntity>()

        var syntheticKeywordId = 1L
        var syntheticTokenId = 1L

        for (bundle in bundles) {
            val ownerTopicId = bundle.header.topicId
            val ownerPackId = bundle.header.packId
            allHeaders.add(bundle.header)
            for (kwWithTokens in bundle.keywordsWithTokens) {
                val kw = kwWithTokens.keyword
                if (kw.topicId != ownerTopicId || kw.packId != ownerPackId) {
                    bundleIssues += ValidationIssue(
                        category = ValidationIssueCategory.MISSING_REFERENCE,
                        field = "keyword.topicId/packId",
                        message = "Keyword '${kw.originalKeyword}' inside bundle ($ownerTopicId, $ownerPackId) has mismatched ownership (${kw.topicId}, ${kw.packId}).",
                        recordIdentifier = "${kw.topicId}@${kw.packId}#${kw.keywordOrder}"
                    )
                }
                if (kw.tokenCount > 0 && kwWithTokens.tokens.size != kw.tokenCount) {
                    bundleIssues += ValidationIssue(
                        category = ValidationIssueCategory.MISSING_REFERENCE,
                        field = "token.keywordId",
                        message = "Keyword '${kw.originalKeyword}' in ($ownerTopicId, $ownerPackId) declares tokenCount=${kw.tokenCount} but bundle provided ${kwWithTokens.tokens.size} token record(s).",
                        recordIdentifier = "${kw.topicId}@${kw.packId}#${kw.keywordOrder}"
                    )
                }
                val kwId = if (kw.keywordId > 0L) {
                    kw.keywordId
                } else {
                    syntheticKeywordId++
                }
                allKeywords.add(kw.copy(keywordId = kwId))
                for (tok in kwWithTokens.tokens) {
                    val tokId = if (tok.tokenId > 0L) tok.tokenId else syntheticTokenId++
                    allTokens.add(tok.copy(tokenId = tokId, keywordId = kwId))
                }
            }
            if (bundle.detail != null) {
                val det = bundle.detail
                if (det.topicId != ownerTopicId || det.packId != ownerPackId) {
                    bundleIssues += ValidationIssue(
                        category = ValidationIssueCategory.MISSING_REFERENCE,
                        field = "detail.topicId/packId",
                        message = "Detail inside bundle ($ownerTopicId, $ownerPackId) has mismatched ownership (${det.topicId}, ${det.packId}).",
                        recordIdentifier = "${det.topicId}@${det.packId}"
                    )
                }
                allDetails.add(det)
            }
            for (branch in bundle.followUpBranches) {
                if (branch.topicId != ownerTopicId || branch.packId != ownerPackId) {
                    bundleIssues += ValidationIssue(
                        category = ValidationIssueCategory.MISSING_REFERENCE,
                        field = "branch.topicId/packId",
                        message = "Follow-up branch '${branch.branchId}' inside bundle ($ownerTopicId, $ownerPackId) has mismatched ownership (${branch.topicId}, ${branch.packId}).",
                        recordIdentifier = "${branch.branchId}@${branch.packId}"
                    )
                }
                allBranches.add(branch)
            }
        }

        val dataValidation = validatePackData(
            pack = pack,
            topics = allHeaders,
            keywords = allKeywords,
            tokens = allTokens,
            details = allDetails,
            followUpBranches = allBranches,
            referenceTopicIds = referenceTopicIds,
            enforceCoreTopicProtection = enforceCoreTopicProtection
        )
        return if (bundleIssues.isEmpty()) {
            dataValidation
        } else {
            ValidationResult.Invalid(bundleIssues + dataValidation.issues)
        }
    }

    /**
     * Validates a complete pack payload including all relational integrity constraints across
     * tables 1 through 6, plus optional core seed topic protection.
     */
    fun validatePackData(
        pack: KnowledgePackMetadataEntity,
        topics: List<KnowledgeTopicHeaderEntity>,
        keywords: List<KnowledgeKeywordEntity>,
        tokens: List<KnowledgeKeywordTokenEntity>,
        details: List<KnowledgeTopicDetailEntity>,
        followUpBranches: List<KnowledgeFollowUpBranchEntity>,
        referenceTopicIds: Set<String>? = null,
        enforceCoreTopicProtection: Boolean = true
    ): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        // 1. Validate Pack Metadata
        issues += validatePackMetadataInternal(pack)
        if (topics.size != pack.topicCount) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "pack.topicCount",
                message = "Pack topicCount (${pack.topicCount}) does not match actual topics size (${topics.size}).",
                recordIdentifier = pack.packId
            )
        }

        val isCanonicalCoreSeedPack = pack.packId == SeedKnowledgeCatalog.CORE_SEED_PACK_ID
        if (enforceCoreTopicProtection) {
            if (isCanonicalCoreSeedPack && pack.sourceType != SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.VALIDATION_ERROR,
                    field = "pack.sourceType",
                    message = "Canonical core seed pack '${pack.packId}' must have sourceType '${SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE}'.",
                    recordIdentifier = pack.packId
                )
            }
            if (!isCanonicalCoreSeedPack && pack.sourceType == SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.VALIDATION_ERROR,
                    field = "pack.sourceType",
                    message = "External pack '${pack.packId}' cannot claim SEED_BUILTIN ownership.",
                    recordIdentifier = pack.packId
                )
            }
        }

        // 2. Validate Topic Headers & check duplicates / pack relations / core protection
        val seenTopicIdsInPack = mutableSetOf<String>()
        val validTopicKeys = mutableSetOf<Pair<String, String>>()

        for (topic in topics) {
            issues += validateTopicHeaderInternal(topic)
            if (pack.packId.isNotBlank() && topic.packId != pack.packId) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.MISSING_REFERENCE,
                    field = "topic.packId",
                    message = "Topic '${topic.topicId}' references packId '${topic.packId}' which does not match pack '${pack.packId}'.",
                    recordIdentifier = "${topic.topicId}@${topic.packId}"
                )
            }
            if (topic.packPriority != pack.packPriority) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.VALIDATION_ERROR,
                    field = "topic.packPriority",
                    message = "Topic '${topic.topicId}' packPriority (${topic.packPriority}) does not match pack '${pack.packId}' priority (${pack.packPriority}).",
                    recordIdentifier = "${topic.topicId}@${topic.packId}"
                )
            }
            if (enforceCoreTopicProtection && !isCanonicalCoreSeedPack) {
                if (topic.isCoreTopic) {
                    issues += ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "topic.isCoreTopic",
                        message = "External pack '${pack.packId}' cannot mark topic '${topic.topicId}' as isCoreTopic=true.",
                        recordIdentifier = "${topic.topicId}@${topic.packId}"
                    )
                }
                if (topic.sourceType == SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE) {
                    issues += ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "topic.sourceType",
                        message = "External pack '${pack.packId}' cannot use SEED_BUILTIN sourceType on topic '${topic.topicId}'.",
                        recordIdentifier = "${topic.topicId}@${topic.packId}"
                    )
                }
                if (SeedKnowledgeCatalog.isProtectedCoreTopicId(topic.topicId)) {
                    issues += ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "topic.topicId",
                        message = "External pack '${pack.packId}' cannot override protected core seed topic '${topic.topicId}'.",
                        recordIdentifier = "${topic.topicId}@${topic.packId}"
                    )
                }
            }
            if (topic.topicId.isNotBlank()) {
                if (!seenTopicIdsInPack.add(topic.topicId)) {
                    issues += ValidationIssue(
                        category = ValidationIssueCategory.DUPLICATE_DATA,
                        field = "topic.topicId",
                        message = "Duplicate topicId '${topic.topicId}' within pack '${topic.packId}'.",
                        recordIdentifier = "${topic.topicId}@${topic.packId}"
                    )
                }
                if (topic.packId.isNotBlank()) {
                    validTopicKeys.add(topic.topicId to topic.packId)
                }
            }
        }

        // Combined reference set for relatedTopicIds validation when provided
        val knownReferenceTopicIds: Set<String>? = referenceTopicIds?.let {
            it + seenTopicIdsInPack
        }

        // 3. Validate Keywords & check duplicates / topic relations
        val seenKeywordIds = mutableSetOf<Long>()
        val seenKeywordOrdersPerTopic = mutableMapOf<Pair<String, String>, MutableSet<Int>>()
        val keywordsById = mutableMapOf<Long, KnowledgeKeywordEntity>()

        for (kw in keywords) {
            issues += validateKeywordInternal(kw)
            val topicKey = kw.topicId to kw.packId
            if (kw.topicId.isNotBlank() && kw.packId.isNotBlank() && topicKey !in validTopicKeys) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.MISSING_REFERENCE,
                    field = "keyword.topicId/packId",
                    message = "Keyword '${kw.originalKeyword}' references non-existent topic (${kw.topicId}, ${kw.packId}).",
                    recordIdentifier = "keywordId=${kw.keywordId}"
                )
            }
            if (kw.keywordId > 0L) {
                if (!seenKeywordIds.add(kw.keywordId)) {
                    issues += ValidationIssue(
                        category = ValidationIssueCategory.DUPLICATE_DATA,
                        field = "keyword.keywordId",
                        message = "Duplicate keywordId ${kw.keywordId}.",
                        recordIdentifier = "keywordId=${kw.keywordId}"
                    )
                } else {
                    keywordsById[kw.keywordId] = kw
                }
            }
            val ordersSet = seenKeywordOrdersPerTopic.getOrPut(topicKey) { mutableSetOf() }
            if (!ordersSet.add(kw.keywordOrder)) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.DUPLICATE_DATA,
                    field = "keyword.keywordOrder",
                    message = "Duplicate keywordOrder ${kw.keywordOrder} in topic (${kw.topicId}, ${kw.packId}).",
                    recordIdentifier = "${kw.topicId}@${kw.packId}#${kw.keywordOrder}"
                )
            }
        }

        // 4. Validate Keyword Tokens & check duplicates / keyword relations
        val seenTokenIds = mutableSetOf<Long>()
        val seenPositionsPerKeyword = mutableMapOf<Long, MutableSet<Int>>()

        for (token in tokens) {
            val parentKeyword = keywordsById[token.keywordId]
            issues += validateKeywordTokenInternal(token, parentKeyword?.tokenCount)
            if (token.keywordId <= 0L || (keywordsById.isNotEmpty() && parentKeyword == null)) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.MISSING_REFERENCE,
                    field = "token.keywordId",
                    message = "Token '${token.strictToken}' references missing keywordId ${token.keywordId}.",
                    recordIdentifier = "tokenId=${token.tokenId}"
                )
            }
            if (token.tokenId > 0L && !seenTokenIds.add(token.tokenId)) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.DUPLICATE_DATA,
                    field = "token.tokenId",
                    message = "Duplicate tokenId ${token.tokenId}.",
                    recordIdentifier = "tokenId=${token.tokenId}"
                )
            }
            val posSet = seenPositionsPerKeyword.getOrPut(token.keywordId) { mutableSetOf() }
            if (!posSet.add(token.tokenPosition)) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.DUPLICATE_DATA,
                    field = "token.tokenPosition",
                    message = "Duplicate tokenPosition ${token.tokenPosition} for keywordId ${token.keywordId}.",
                    recordIdentifier = "keywordId=${token.keywordId}[${token.tokenPosition}]"
                )
            }
        }

        // 5. Validate Topic Details & check relations / relatedTopicIds
        val seenDetailKeys = mutableSetOf<Pair<String, String>>()
        for (detail in details) {
            issues += validateTopicDetailInternal(detail, knownReferenceTopicIds)
            val detailKey = detail.topicId to detail.packId
            if (detail.topicId.isNotBlank() && detail.packId.isNotBlank() && detailKey !in validTopicKeys) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.MISSING_REFERENCE,
                    field = "detail.topicId/packId",
                    message = "Detail references non-existent topic (${detail.topicId}, ${detail.packId}).",
                    recordIdentifier = "${detail.topicId}@${detail.packId}"
                )
            }
            if (!seenDetailKeys.add(detailKey)) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.DUPLICATE_DATA,
                    field = "detail.topicId/packId",
                    message = "Duplicate detail record for (${detail.topicId}, ${detail.packId}).",
                    recordIdentifier = "${detail.topicId}@${detail.packId}"
                )
            }
        }

        // 6. Validate Follow-Up Branches & check relations / priorityOrder duplicates
        val seenBranchKeys = mutableSetOf<Pair<String, String>>()
        val seenPriorityPerTopic = mutableMapOf<Pair<String, String>, MutableSet<Int>>()
        for (branch in followUpBranches) {
            issues += validateFollowUpBranchInternal(branch)
            val topicKey = branch.topicId to branch.packId
            if (branch.topicId.isNotBlank() && branch.packId.isNotBlank() && topicKey !in validTopicKeys) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.MISSING_REFERENCE,
                    field = "branch.topicId/packId",
                    message = "Follow-up branch '${branch.branchId}' references non-existent topic (${branch.topicId}, ${branch.packId}).",
                    recordIdentifier = "${branch.branchId}@${branch.packId}"
                )
            }
            val branchKey = branch.branchId to branch.packId
            if (branch.branchId.isNotBlank() && branch.packId.isNotBlank() && !seenBranchKeys.add(branchKey)) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.DUPLICATE_DATA,
                    field = "branch.branchId/packId",
                    message = "Duplicate follow-up branchId '${branch.branchId}' in pack '${branch.packId}'.",
                    recordIdentifier = "${branch.branchId}@${branch.packId}"
                )
            }
            val prioritySet = seenPriorityPerTopic.getOrPut(topicKey) { mutableSetOf() }
            if (!prioritySet.add(branch.priorityOrder)) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.DUPLICATE_DATA,
                    field = "branch.priorityOrder",
                    message = "Duplicate priorityOrder ${branch.priorityOrder} in topic (${branch.topicId}, ${branch.packId}).",
                    recordIdentifier = "${branch.branchId}@${branch.packId}"
                )
            }
        }

        return if (issues.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(issues)
    }

    fun validatePackMetadata(pack: KnowledgePackMetadataEntity): ValidationResult {
        val issues = validatePackMetadataInternal(pack)
        return if (issues.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(issues)
    }

    fun validateTopicHeader(header: KnowledgeTopicHeaderEntity): ValidationResult {
        val issues = validateTopicHeaderInternal(header)
        return if (issues.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(issues)
    }

    fun validateKeyword(keyword: KnowledgeKeywordEntity): ValidationResult {
        val issues = validateKeywordInternal(keyword)
        return if (issues.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(issues)
    }

    fun validateKeywordToken(
        token: KnowledgeKeywordTokenEntity,
        parentTokenCount: Int? = null
    ): ValidationResult {
        val issues = validateKeywordTokenInternal(token, parentTokenCount)
        return if (issues.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(issues)
    }

    fun validateTopicDetail(
        detail: KnowledgeTopicDetailEntity,
        referenceTopicIds: Set<String>? = null
    ): ValidationResult {
        val issues = validateTopicDetailInternal(detail, referenceTopicIds)
        return if (issues.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(issues)
    }

    fun validateFollowUpBranch(branch: KnowledgeFollowUpBranchEntity): ValidationResult {
        val issues = validateFollowUpBranchInternal(branch)
        return if (issues.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(issues)
    }

    // =========================================================================
    // Internal Validation Helpers
    // =========================================================================

    private fun validatePackMetadataInternal(pack: KnowledgePackMetadataEntity): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (pack.packId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "pack.packId",
                message = "packId must not be blank."
            )
        }
        if (pack.sourceType !in SUPPORTED_SOURCE_TYPES) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "pack.sourceType",
                message = "Unsupported sourceType '${pack.sourceType}'. Allowed: $SUPPORTED_SOURCE_TYPES.",
                recordIdentifier = pack.packId
            )
        }
        if (pack.packVersion <= 0) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "pack.packVersion",
                message = "packVersion (${pack.packVersion}) must be >= 1.",
                recordIdentifier = pack.packId
            )
        }
        if (pack.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.UNSUPPORTED_SCHEMA_VERSION,
                field = "pack.schemaVersion",
                message = "Unsupported schemaVersion (${pack.schemaVersion}). Supported: $SUPPORTED_SCHEMA_VERSIONS.",
                recordIdentifier = pack.packId
            )
        }
        if (pack.sha256Checksum.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "pack.sha256Checksum",
                message = "sha256Checksum must not be blank.",
                recordIdentifier = pack.packId
            )
        }
        if (!KnowledgePriorityAndWinnerPolicy.isValidLogicalPriority(pack.packPriority)) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "pack.packPriority",
                message = "packPriority (${pack.packPriority}) must be >= ${KnowledgePriorityAndWinnerPolicy.MIN_LOGICAL_PRIORITY}.",
                recordIdentifier = pack.packId
            )
        }
        if (pack.topicCount < 0) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "pack.topicCount",
                message = "topicCount (${pack.topicCount}) must be >= 0.",
                recordIdentifier = pack.packId
            )
        }
        return issues
    }

    private fun validateTopicHeaderInternal(header: KnowledgeTopicHeaderEntity): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val idTag = "${header.topicId}@${header.packId}"
        if (header.topicId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "topic.topicId",
                message = "topicId must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (header.packId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "topic.packId",
                message = "packId must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (header.topicOrderIndex < 0) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "topic.topicOrderIndex",
                message = "topicOrderIndex (${header.topicOrderIndex}) must be >= 0.",
                recordIdentifier = idTag
            )
        }
        if (header.arabicSummary.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "topic.arabicSummary",
                message = "arabicSummary must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (header.englishSummary.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "topic.englishSummary",
                message = "englishSummary must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (header.sourceType !in SUPPORTED_SOURCE_TYPES) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "topic.sourceType",
                message = "Invalid sourceType '${header.sourceType}'.",
                recordIdentifier = idTag
            )
        }
        if (!KnowledgePriorityAndWinnerPolicy.isValidLogicalPriority(header.packPriority)) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "topic.packPriority",
                message = "packPriority (${header.packPriority}) must be >= ${KnowledgePriorityAndWinnerPolicy.MIN_LOGICAL_PRIORITY}.",
                recordIdentifier = idTag
            )
        }
        if (header.contentVersion <= 0) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "topic.contentVersion",
                message = "contentVersion (${header.contentVersion}) must be >= 1.",
                recordIdentifier = idTag
            )
        }
        if (!KnowledgeTypeConverters.isValidActionableOptionListJson(header.suggestedFollowUpsJson)) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.INVALID_SERIALIZATION,
                field = "topic.suggestedFollowUpsJson",
                message = "suggestedFollowUpsJson is not a valid ActionableOption JSON array.",
                recordIdentifier = idTag
            )
        }
        return issues
    }

    private fun validateKeywordInternal(keyword: KnowledgeKeywordEntity): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val idTag = "${keyword.topicId}@${keyword.packId}#${keyword.keywordOrder}"
        if (keyword.topicId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.topicId",
                message = "keyword.topicId must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (keyword.packId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.packId",
                message = "keyword.packId must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (keyword.keywordOrder < 0) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.keywordOrder",
                message = "keywordOrder (${keyword.keywordOrder}) must be >= 0.",
                recordIdentifier = idTag
            )
        }
        if (keyword.originalKeyword.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.originalKeyword",
                message = "originalKeyword must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (keyword.strictNormalizedPhrase.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.strictNormalizedPhrase",
                message = "strictNormalizedPhrase must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (keyword.relaxedNormalizedPhrase.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.relaxedNormalizedPhrase",
                message = "relaxedNormalizedPhrase must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (keyword.relaxedCharLength <= 0 ||
            (keyword.relaxedNormalizedPhrase.isNotBlank() &&
                keyword.relaxedCharLength != keyword.relaxedNormalizedPhrase.length)
        ) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.relaxedCharLength",
                message = "relaxedCharLength (${keyword.relaxedCharLength}) must be > 0 and match relaxedNormalizedPhrase.length (${keyword.relaxedNormalizedPhrase.length}).",
                recordIdentifier = idTag
            )
        }
        if (keyword.tokenCount <= 0) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.tokenCount",
                message = "tokenCount (${keyword.tokenCount}) must be >= 1.",
                recordIdentifier = idTag
            )
        }
        if (keyword.contentTokenCount <= 0 || keyword.contentTokenCount > keyword.tokenCount) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.contentTokenCount",
                message = "contentTokenCount (${keyword.contentTokenCount}) must be in 1..tokenCount (${keyword.tokenCount}).",
                recordIdentifier = idTag
            )
        }
        if (keyword.matchType !in SUPPORTED_MATCH_TYPES) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.matchType",
                message = "Unsupported matchType '${keyword.matchType}'. Allowed: $SUPPORTED_MATCH_TYPES.",
                recordIdentifier = idTag
            )
        }
        if (keyword.language !in SUPPORTED_LANGUAGES) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "keyword.language",
                message = "Unsupported language '${keyword.language}'. Allowed: $SUPPORTED_LANGUAGES.",
                recordIdentifier = idTag
            )
        }
        return issues
    }

    private fun validateKeywordTokenInternal(
        token: KnowledgeKeywordTokenEntity,
        parentTokenCount: Int?
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val idTag = "keywordId=${token.keywordId}[${token.tokenPosition}]"
        if (token.tokenPosition < 0 || (parentTokenCount != null && token.tokenPosition >= parentTokenCount)) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "token.tokenPosition",
                message = "tokenPosition (${token.tokenPosition}) is out of bounds (parentTokenCount=$parentTokenCount).",
                recordIdentifier = idTag
            )
        }
        if (token.strictToken.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "token.strictToken",
                message = "strictToken must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (token.strictStem.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "token.strictStem",
                message = "strictStem must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (token.relaxedToken.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "token.relaxedToken",
                message = "relaxedToken must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (token.relaxedStem.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "token.relaxedStem",
                message = "relaxedStem must not be blank.",
                recordIdentifier = idTag
            )
        }
        return issues
    }

    private fun validateTopicDetailInternal(
        detail: KnowledgeTopicDetailEntity,
        referenceTopicIds: Set<String>?
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val idTag = "${detail.topicId}@${detail.packId}"
        if (detail.topicId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "detail.topicId",
                message = "detail.topicId must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (detail.packId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "detail.packId",
                message = "detail.packId must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (detail.detailedArabic.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "detail.detailedArabic",
                message = "detailedArabic must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (detail.detailedEnglish.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "detail.detailedEnglish",
                message = "detailedEnglish must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (!KnowledgeTypeConverters.isValidStringListJson(detail.relatedTopicIdsJson)) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.INVALID_SERIALIZATION,
                field = "detail.relatedTopicIdsJson",
                message = "relatedTopicIdsJson is not a valid JSON array of non-blank strings.",
                recordIdentifier = idTag
            )
        } else {
            val converters = KnowledgeTypeConverters()
            val relatedIds = converters.toStringList(detail.relatedTopicIdsJson)
            val seenRelatedIds = HashSet<String>(relatedIds.size)
            for (relId in relatedIds) {
                if (relId == detail.topicId) {
                    issues += ValidationIssue(
                        category = ValidationIssueCategory.MISSING_REFERENCE,
                        field = "detail.relatedTopicIdsJson",
                        message = "Topic '${detail.topicId}' cannot reference itself in relatedTopicIds.",
                        recordIdentifier = idTag
                    )
                }
                if (!seenRelatedIds.add(relId)) {
                    issues += ValidationIssue(
                        category = ValidationIssueCategory.DUPLICATE_DATA,
                        field = "detail.relatedTopicIdsJson",
                        message = "Duplicate relatedTopicId '$relId' in topic '${detail.topicId}'.",
                        recordIdentifier = idTag
                    )
                }
                if (referenceTopicIds != null && relId !in referenceTopicIds) {
                    issues += ValidationIssue(
                        category = ValidationIssueCategory.MISSING_REFERENCE,
                        field = "detail.relatedTopicIdsJson",
                        message = "relatedTopicId '$relId' does not exist in reference topic IDs.",
                        recordIdentifier = idTag
                    )
                }
            }
        }
        if (!KnowledgeTypeConverters.isValidStringListJson(detail.examplesJson)) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.INVALID_SERIALIZATION,
                field = "detail.examplesJson",
                message = "examplesJson is not a valid JSON array of strings.",
                recordIdentifier = idTag
            )
        }
        if (!KnowledgeTypeConverters.isValidOptionalJsonObject(detail.additionalContentJson)) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.INVALID_SERIALIZATION,
                field = "detail.additionalContentJson",
                message = "additionalContentJson must be null or a valid JSON object.",
                recordIdentifier = idTag
            )
        }
        return issues
    }

    private fun validateFollowUpBranchInternal(
        branch: KnowledgeFollowUpBranchEntity
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val idTag = "${branch.branchId}@${branch.packId}"
        if (branch.branchId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "branch.branchId",
                message = "branchId must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (branch.topicId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "branch.topicId",
                message = "branch.topicId must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (branch.packId.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "branch.packId",
                message = "branch.packId must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (branch.priorityOrder <= 0) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "branch.priorityOrder",
                message = "priorityOrder (${branch.priorityOrder}) must be >= 1.",
                recordIdentifier = idTag
            )
        }
        if (branch.matchMode !in SUPPORTED_BRANCH_MATCH_MODES) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "branch.matchMode",
                message = "Unsupported matchMode '${branch.matchMode}'. Allowed: $SUPPORTED_BRANCH_MATCH_MODES.",
                recordIdentifier = idTag
            )
        }
        if (!KnowledgeTypeConverters.isValidStringListJson(branch.triggerKeywordsJson)) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.INVALID_SERIALIZATION,
                field = "branch.triggerKeywordsJson",
                message = "triggerKeywordsJson is not a valid JSON array of non-blank strings.",
                recordIdentifier = idTag
            )
        } else {
            val triggers = KnowledgeTypeConverters().toStringList(branch.triggerKeywordsJson)
            if (!branch.isDefault && triggers.isEmpty()) {
                issues += ValidationIssue(
                    category = ValidationIssueCategory.VALIDATION_ERROR,
                    field = "branch.triggerKeywordsJson",
                    message = "Non-default follow-up branch must have at least one trigger keyword.",
                    recordIdentifier = idTag
                )
            }
        }
        if (branch.responseArabic.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "branch.responseArabic",
                message = "responseArabic must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (branch.responseEnglish.isBlank()) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "branch.responseEnglish",
                message = "responseEnglish must not be blank.",
                recordIdentifier = idTag
            )
        }
        if (!KnowledgeTypeConverters.isValidActionableOptionListJson(branch.actionableOptionsJson)) {
            issues += ValidationIssue(
                category = ValidationIssueCategory.INVALID_SERIALIZATION,
                field = "branch.actionableOptionsJson",
                message = "actionableOptionsJson is not a valid ActionableOption JSON array.",
                recordIdentifier = idTag
            )
        }
        return issues
    }
}
