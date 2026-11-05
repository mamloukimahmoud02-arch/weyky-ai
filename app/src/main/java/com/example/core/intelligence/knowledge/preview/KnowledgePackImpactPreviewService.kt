package com.example.core.intelligence.knowledge.preview

import androidx.room.withTransaction
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KnowledgeDao
import com.example.core.intelligence.knowledge.db.dao.KnowledgePriorityAndWinnerPolicy
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity
import com.example.core.intelligence.knowledge.importer.KnowledgePackImportLimits
import com.example.core.intelligence.knowledge.importer.KnowledgePackImporter
import com.example.core.intelligence.knowledge.importer.KnowledgePackJsonParser
import com.example.core.intelligence.knowledge.importer.KnowledgePackParseOutcome
import com.example.core.intelligence.knowledge.importer.KnowledgePackSpecificationV1
import com.example.core.intelligence.knowledge.importer.ParsedKnowledgePackV1
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.validation.KnowledgeValidator
import com.example.core.intelligence.knowledge.validation.ValidationIssue
import com.example.core.intelligence.knowledge.validation.ValidationIssueCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Service for analyzing and previewing the expected consequences of Knowledge Pack operations
 * without mutating database records, generation counters, active winner flags, or in-memory caches (Phase 2B.4.2C.4).
 *
 * Guarantees:
 * 1. Strictly read-only: never issues SQL INSERT, UPDATE, or DELETE statements.
 * 2. Reuses authoritative [KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader] for all winner simulations.
 * 3. Verifies database generation consistency between analysis start and finish, detecting concurrent mutations.
 * 4. Reuses C2.3 reference integrity rules to predict broken outgoing or incoming cross-pack references.
 */
class KnowledgePackImpactPreviewService(
    private val database: WeykyKnowledgeDatabase,
    private val repository: KnowledgeRepository,
    private val importer: KnowledgePackImporter = repository.createKnowledgePackImporter(),
    private val converters: KnowledgeTypeConverters = KnowledgeTypeConverters(),
    private val limits: KnowledgePackImportLimits = KnowledgePackImportLimits(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val parser = KnowledgePackJsonParser(limits)

    @Volatile
    internal var staleGenerationSimulator: (() -> Long)? = null

    /**
     * Previews the impact of importing or updating a knowledge pack from a raw JSON string.
     */
    suspend fun previewPackImport(
        json: String,
        expectedSha256: String? = null
    ): KnowledgePackImpactPreview = withContext(ioDispatcher) {
        val parseOutcome = parser.parseFromJsonString(json)
        when (parseOutcome) {
            is KnowledgePackParseOutcome.Failure -> {
                val currentGen = repository.committedGeneration
                KnowledgePackImpactPreview(
                    operationType = PackOperationType.IMPORT_OR_UPDATE,
                    packId = parseOutcome.packId ?: "unknown",
                    currentPackVersion = parseOutcome.packVersion,
                    incomingPackVersion = parseOutcome.packVersion,
                    currentDatabaseGeneration = currentGen,
                    analyzedDatabaseGeneration = currentGen,
                    previewStatus = ImpactPreviewStatus.BLOCKED_BY_VALIDATION,
                    canApply = false,
                    summary = ImpactPreviewSummary(blockingIssueCount = parseOutcome.errors.size),
                    topicChanges = emptyList(),
                    winnerChanges = emptyList(),
                    referenceImpacts = emptyList(),
                    validationIssues = parseOutcome.errors.map {
                        ValidationIssue(
                            category = ValidationIssueCategory.VALIDATION_ERROR,
                            field = "json",
                            message = it,
                            recordIdentifier = parseOutcome.packId
                        )
                    },
                    warnings = emptyList()
                )
            }
            is KnowledgePackParseOutcome.Success -> {
                previewPackImport(
                    parsedPack = parseOutcome.pack,
                    expectedSha256 = expectedSha256
                )
            }
        }
    }

    /**
     * Previews the impact of importing or updating a knowledge pack from an [InputStream].
     */
    suspend fun previewPackImport(
        inputStream: InputStream,
        expectedSha256: String? = null
    ): KnowledgePackImpactPreview = withContext(ioDispatcher) {
        val parseOutcome = parser.parseFromInputStream(inputStream)
        when (parseOutcome) {
            is KnowledgePackParseOutcome.Failure -> {
                val currentGen = repository.committedGeneration
                KnowledgePackImpactPreview(
                    operationType = PackOperationType.IMPORT_OR_UPDATE,
                    packId = parseOutcome.packId ?: "unknown",
                    currentPackVersion = parseOutcome.packVersion,
                    incomingPackVersion = parseOutcome.packVersion,
                    currentDatabaseGeneration = currentGen,
                    analyzedDatabaseGeneration = currentGen,
                    previewStatus = ImpactPreviewStatus.BLOCKED_BY_VALIDATION,
                    canApply = false,
                    summary = ImpactPreviewSummary(blockingIssueCount = parseOutcome.errors.size),
                    topicChanges = emptyList(),
                    winnerChanges = emptyList(),
                    referenceImpacts = emptyList(),
                    validationIssues = parseOutcome.errors.map {
                        ValidationIssue(
                            category = ValidationIssueCategory.VALIDATION_ERROR,
                            field = "inputStream",
                            message = it,
                            recordIdentifier = parseOutcome.packId
                        )
                    },
                    warnings = emptyList()
                )
            }
            is KnowledgePackParseOutcome.Success -> {
                previewPackImport(
                    parsedPack = parseOutcome.pack,
                    expectedSha256 = expectedSha256
                )
            }
        }
    }

    /**
     * Previews the impact of importing or updating a knowledge pack from a parsed DTO.
     */
    suspend fun previewPackImport(
        parsedPack: ParsedKnowledgePackV1,
        expectedSha256: String? = null
    ): KnowledgePackImpactPreview = withContext(ioDispatcher) {
        val genBefore = repository.committedGeneration
        val dao = database.knowledgeDao()

        val validationIssues = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<String>()

        // 1. Checksums & Hash verification
        val rawSha256 = parsedPack.rawBytesSha256
        val logicalSha256 = KnowledgePackSpecificationV1.computeLogicalContentSha256(parsedPack)
        val hashErrors = importer.verifyChecksums(
            rawSha256 = rawSha256,
            logicalSha256 = logicalSha256,
            expectedSha256 = expectedSha256,
            embeddedContentSha256 = parsedPack.contentSha256
        )
        if (hashErrors.isNotEmpty()) {
            for (err in hashErrors) {
                validationIssues += ValidationIssue(
                    category = ValidationIssueCategory.VALIDATION_ERROR,
                    field = "contentSha256",
                    message = err,
                    recordIdentifier = parsedPack.packId
                )
            }
        }

        // 2. Core Seed Protection check
        val coreErrors = importer.checkCoreProtection(parsedPack)
        val isCoreViolation = coreErrors.isNotEmpty()
        if (isCoreViolation) {
            for (err in coreErrors) {
                validationIssues += ValidationIssue(
                    category = ValidationIssueCategory.VALIDATION_ERROR,
                    field = "coreProtection",
                    message = err,
                    recordIdentifier = parsedPack.packId
                )
            }
        }

        // 3. Intra-pack duplicate topics & semantics
        val dupTopicErrors = importer.checkIntraPackDuplicateTopics(parsedPack)
        for (err in dupTopicErrors) {
            validationIssues += ValidationIssue(
                category = ValidationIssueCategory.DUPLICATE_DATA,
                field = "topics",
                message = err,
                recordIdentifier = parsedPack.packId
            )
        }

        val semanticErrors = importer.validateParsedPackSemantics(parsedPack)
        for (err in semanticErrors) {
            validationIssues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "semantics",
                message = err,
                recordIdentifier = parsedPack.packId
            )
        }

        // 4. Read-only authoritative database inspection
        val (existingPack, existingHeaders, referenceOutcome, activeProjections) = database.withTransaction {
            val pack = dao.getPackMetadataById(parsedPack.packId)
            val headers = if (pack != null) dao.getTopicHeadersByPackOrdered(parsedPack.packId) else emptyList()
            val refOutcome = importer.validateReferenceIntegrity(
                parsedPack = parsedPack,
                existingHeadersForSamePack = headers,
                dao = dao
            )
            val projections = dao.getActiveTopicRelatedRefsProjections()
            Quadruple(pack, headers, refOutcome, projections)
        }

        for (err in referenceOutcome.errors) {
            validationIssues += ValidationIssue(
                category = ValidationIssueCategory.MISSING_REFERENCE,
                field = "referenceTopicIds",
                message = err,
                recordIdentifier = parsedPack.packId
            )
        }

        // 5. Existing pack version progression / conflict checks
        var isAlreadyImportedIdentical = false
        if (existingPack != null) {
            val existingMaxContentVersion = existingHeaders.maxOfOrNull { it.contentVersion } ?: existingPack.packVersion
            val storedChecksum = existingPack.sha256Checksum
            val isIdenticalChecksum = storedChecksum.equals(logicalSha256, ignoreCase = true) ||
                storedChecksum.equals(rawSha256, ignoreCase = true)

            when {
                parsedPack.packVersion == existingPack.packVersion &&
                    parsedPack.contentVersion == existingMaxContentVersion &&
                    isIdenticalChecksum -> {
                    isAlreadyImportedIdentical = true
                    warnings += "Pack '${parsedPack.packId}' (v${parsedPack.packVersion}) is already imported with matching SHA-256."
                }
                parsedPack.packVersion == existingPack.packVersion &&
                    parsedPack.contentVersion == existingMaxContentVersion &&
                    !isIdenticalChecksum -> {
                    validationIssues += ValidationIssue(
                        category = ValidationIssueCategory.DUPLICATE_DATA,
                        field = "packVersion",
                        message = "Conflict for pack '${parsedPack.packId}': version (${parsedPack.packVersion}) already exists with different checksum.",
                        recordIdentifier = parsedPack.packId
                    )
                }
                parsedPack.packVersion < existingPack.packVersion ||
                    (parsedPack.packVersion == existingPack.packVersion && parsedPack.contentVersion < existingMaxContentVersion) -> {
                    validationIssues += ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "packVersion",
                        message = "Downgrade rejected for pack '${parsedPack.packId}': incoming version (${parsedPack.packVersion}) is older than installed version (${existingPack.packVersion}).",
                        recordIdentifier = parsedPack.packId
                    )
                }
            }
        }

        // 6. Entity creation and bundle validation
        val packEntity = importer.buildPackMetadataEntity(parsedPack, logicalSha256)
        val topicBundles = try {
            importer.buildTopicStorageBundles(parsedPack, rawSha256, logicalSha256)
        } catch (convErr: Throwable) {
            validationIssues += ValidationIssue(
                category = ValidationIssueCategory.VALIDATION_ERROR,
                field = "conversion",
                message = "Failed to construct topic bundles: ${convErr.message}",
                recordIdentifier = parsedPack.packId
            )
            emptyList()
        }

        if (topicBundles.isNotEmpty()) {
            val bundleValidation = KnowledgeValidator.validatePackBundles(
                pack = packEntity,
                bundles = topicBundles,
                referenceTopicIds = referenceOutcome.effectiveActiveTopicIdsAfterImport,
                enforceCoreTopicProtection = true
            )
            validationIssues += bundleValidation.issues
        }

        // 7. Winner Simulation
        val previousTopicIds = existingHeaders.map { it.topicId }.toSet()
        val incomingTopicIds = parsedPack.topics.map { it.topicId }.toSet()
        val allAffectedTopicIds = LinkedHashSet<String>(previousTopicIds.size + incomingTopicIds.size).apply {
            addAll(previousTopicIds)
            addAll(incomingTopicIds)
        }

        val topicChanges = mutableListOf<TopicChange>()
        val winnerChanges = mutableListOf<TopicChange>()
        val predictedWinnersByTopicId = mutableMapOf<String, KnowledgeTopicHeaderEntity?>()
        val currentWinnersByTopicId = mutableMapOf<String, KnowledgeTopicHeaderEntity?>()

        for (topicId in allAffectedTopicIds) {
            val existingCandidates = dao.getAllTopicVersionsForTopicId(topicId)
            val currentWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(existingCandidates)
            currentWinnersByTopicId[topicId] = currentWinner

            val simulatedCandidates = if (topicId in incomingTopicIds) {
                val incomingBundle = topicBundles.firstOrNull { it.header.topicId == topicId }
                val incomingHeader = incomingBundle?.header ?: KnowledgeTopicHeaderEntity(
                    topicId = topicId,
                    packId = parsedPack.packId,
                    topicOrderIndex = 100,
                    arabicSummary = "",
                    englishSummary = "",
                    suggestedFollowUpsJson = "[]",
                    sourceType = parsedPack.sourceType,
                    packPriority = parsedPack.packPriority,
                    contentVersion = parsedPack.contentVersion,
                    isCoreTopic = false,
                    isActiveWinner = false
                )
                existingCandidates.filterNot { it.packId == parsedPack.packId } + incomingHeader
            } else {
                existingCandidates.filterNot { it.packId == parsedPack.packId }
            }

            val predictedWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(simulatedCandidates)
            predictedWinnersByTopicId[topicId] = predictedWinner

            val previousHeaderForThisPack = existingHeaders.firstOrNull { it.topicId == topicId }
            val incomingTopicDto = parsedPack.topics.firstOrNull { it.topicId == topicId }

            val changeType = when {
                topicId !in previousTopicIds && topicId in incomingTopicIds -> {
                    if (predictedWinner?.packId == parsedPack.packId) {
                        TopicChangeType.ADDED
                    } else {
                        TopicChangeType.SHADOWED
                    }
                }
                topicId in previousTopicIds && topicId !in incomingTopicIds -> {
                    TopicChangeType.REMOVED
                }
                else -> {
                    if (isAlreadyImportedIdentical) {
                        TopicChangeType.UNCHANGED
                    } else if (previousHeaderForThisPack != null && incomingTopicDto != null &&
                        previousHeaderForThisPack.contentVersion == incomingTopicDto.contentVersion &&
                        previousHeaderForThisPack.arabicSummary == incomingTopicDto.arabicSummary &&
                        previousHeaderForThisPack.englishSummary == incomingTopicDto.englishSummary
                    ) {
                        TopicChangeType.UNCHANGED
                    } else {
                        if (predictedWinner?.packId == parsedPack.packId) {
                            TopicChangeType.UPDATED
                        } else {
                            TopicChangeType.SHADOWED
                        }
                    }
                }
            }

            val winnerWillChange = currentWinner != null && (
                (currentWinner.packId != predictedWinner?.packId) ||
                (currentWinner.contentVersion != predictedWinner?.contentVersion)
            )

            val tc = TopicChange(
                topicId = topicId,
                changeType = changeType,
                previousPackId = previousHeaderForThisPack?.packId,
                incomingPackId = if (topicId in incomingTopicIds) parsedPack.packId else null,
                previousContentVersion = previousHeaderForThisPack?.contentVersion,
                incomingContentVersion = incomingTopicDto?.contentVersion,
                previousWinnerPackId = currentWinner?.packId,
                predictedWinnerPackId = predictedWinner?.packId,
                winnerWillChange = winnerWillChange
            )
            topicChanges += tc
            if (winnerWillChange) {
                winnerChanges += tc
            }
        }

        // 8. Reference Impact Analysis
        val referenceImpacts = mutableListOf<ReferenceImpact>()

        // Outgoing references from incoming topics
        for (topic in parsedPack.topics) {
            val allDeclaredRefs = (topic.referenceTopicIds + (topic.detail?.relatedTopicIds ?: emptyList())).distinct()
            for (refId in allDeclaredRefs) {
                val cleanRef = refId.trim()
                if (cleanRef.isEmpty()) continue
                val isSelf = cleanRef == topic.topicId
                val currentTargetWinner = dao.getActiveTopicHeader(cleanRef)
                val predictedTargetWinner = predictedWinnersByTopicId[cleanRef]
                    ?: currentTargetWinner // unaffected topics retain current winner

                val (impactType, isBlocking) = when {
                    isSelf -> ReferenceImpactType.SELF_REFERENCE_DISALLOWED to true
                    cleanRef !in referenceOutcome.effectiveActiveTopicIdsAfterImport -> {
                        val inactive = dao.getInactiveOnlyTopicIds()
                        if (cleanRef in inactive) {
                            ReferenceImpactType.TARGET_INACTIVE to true
                        } else {
                            ReferenceImpactType.NONEXISTENT_TARGET to true
                        }
                    }
                    predictedTargetWinner == null -> ReferenceImpactType.TARGET_REMOVED_NO_FALLBACK to true
                    currentTargetWinner?.packId != predictedTargetWinner.packId -> ReferenceImpactType.TARGET_WINNER_CHANGED to false
                    else -> ReferenceImpactType.PRESERVED to false
                }

                referenceImpacts += ReferenceImpact(
                    sourceTopicId = topic.topicId,
                    sourcePackId = parsedPack.packId,
                    referencedTopicId = cleanRef,
                    currentTargetWinnerPackId = currentTargetWinner?.packId,
                    predictedTargetWinnerPackId = predictedTargetWinner?.packId,
                    impactType = impactType,
                    isBlocking = isBlocking
                )
            }
        }

        // Incoming references from other active packs (if any topic loses active status)
        for (proj in activeProjections) {
            if (proj.packId == parsedPack.packId) continue
            val refs = converters.toStringList(proj.relatedTopicIdsJson)
            for (refId in refs) {
                if (refId in allAffectedTopicIds) {
                    val currentTargetWinner = currentWinnersByTopicId[refId]
                    val predictedTargetWinner = predictedWinnersByTopicId[refId]
                    if (predictedTargetWinner == null) {
                        referenceImpacts += ReferenceImpact(
                            sourceTopicId = proj.topicId,
                            sourcePackId = proj.packId,
                            referencedTopicId = refId,
                            currentTargetWinnerPackId = currentTargetWinner?.packId,
                            predictedTargetWinnerPackId = null,
                            impactType = ReferenceImpactType.TARGET_REMOVED_NO_FALLBACK,
                            isBlocking = true
                        )
                    } else if (currentTargetWinner?.packId != predictedTargetWinner.packId) {
                        referenceImpacts += ReferenceImpact(
                            sourceTopicId = proj.topicId,
                            sourcePackId = proj.packId,
                            referencedTopicId = refId,
                            currentTargetWinnerPackId = currentTargetWinner?.packId,
                            predictedTargetWinnerPackId = predictedTargetWinner.packId,
                            impactType = ReferenceImpactType.TARGET_WINNER_CHANGED,
                            isBlocking = false
                        )
                    }
                }
            }
        }

        // 9. Concurrency & Stale Generation check
        val currentGen = staleGenerationSimulator?.invoke() ?: repository.committedGeneration
        if (genBefore != currentGen) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.IMPORT_OR_UPDATE,
                packId = parsedPack.packId,
                currentPackVersion = existingPack?.packVersion,
                incomingPackVersion = parsedPack.packVersion,
                currentDatabaseGeneration = currentGen,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.STALE_PREVIEW,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "generation",
                        message = "Database generation advanced from $genBefore to $currentGen during preview analysis.",
                        recordIdentifier = parsedPack.packId
                    )
                ),
                warnings = emptyList()
            )
        }

        // 10. Status determination
        val hasBlockingRefImpact = referenceImpacts.any { it.isBlocking }
        val hasRefValidationIssues = validationIssues.any {
            it.category == ValidationIssueCategory.MISSING_REFERENCE ||
                it.field.contains("referenceTopicIds", ignoreCase = true) ||
                it.field.contains("relatedTopicIds", ignoreCase = true)
        }

        val status = when {
            isCoreViolation -> ImpactPreviewStatus.BLOCKED_BY_CORE_PROTECTION
            hasBlockingRefImpact || hasRefValidationIssues -> ImpactPreviewStatus.BLOCKED_BY_REFERENCE_INTEGRITY
            validationIssues.isNotEmpty() -> ImpactPreviewStatus.BLOCKED_BY_VALIDATION
            warnings.isNotEmpty() || topicChanges.any { it.changeType == TopicChangeType.SHADOWED } || winnerChanges.isNotEmpty() -> ImpactPreviewStatus.SAFE_WITH_WARNINGS
            else -> ImpactPreviewStatus.SAFE_TO_APPLY
        }

        val canApply = status == ImpactPreviewStatus.SAFE_TO_APPLY || status == ImpactPreviewStatus.SAFE_WITH_WARNINGS

        val summary = ImpactPreviewSummary(
            addedTopicCount = topicChanges.count { it.changeType == TopicChangeType.ADDED },
            updatedTopicCount = topicChanges.count { it.changeType == TopicChangeType.UPDATED },
            removedTopicCount = topicChanges.count { it.changeType == TopicChangeType.REMOVED },
            unchangedTopicCount = topicChanges.count { it.changeType == TopicChangeType.UNCHANGED },
            shadowedTopicCount = topicChanges.count { it.changeType == TopicChangeType.SHADOWED },
            winnerChangedCount = winnerChanges.size,
            totalAffectedTopicCount = allAffectedTopicIds.size,
            blockingIssueCount = validationIssues.size + referenceImpacts.count { it.isBlocking },
            warningCount = warnings.size
        )

        KnowledgePackImpactPreview(
            operationType = PackOperationType.IMPORT_OR_UPDATE,
            packId = parsedPack.packId,
            currentPackVersion = existingPack?.packVersion,
            incomingPackVersion = parsedPack.packVersion,
            currentDatabaseGeneration = currentGen,
            analyzedDatabaseGeneration = genBefore,
            previewStatus = status,
            canApply = canApply,
            summary = summary,
            topicChanges = topicChanges,
            winnerChanges = winnerChanges,
            referenceImpacts = referenceImpacts,
            validationIssues = validationIssues,
            warnings = warnings
        )
    }

    /**
     * Previews the impact of deactivating an installed knowledge pack.
     */
    suspend fun previewPackDeactivation(packId: String): KnowledgePackImpactPreview = withContext(ioDispatcher) {
        val cleanPackId = packId.trim()
        val genBefore = repository.committedGeneration
        val dao = database.knowledgeDao()

        if (cleanPackId == SeedKnowledgeCatalog.CORE_SEED_PACK_ID) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.DEACTIVATE,
                packId = cleanPackId,
                currentPackVersion = 1,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.BLOCKED_BY_CORE_PROTECTION,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "packId",
                        message = "Protected core_seed_pack cannot be deactivated.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val existingPack = dao.getPackMetadataById(cleanPackId)
        if (existingPack == null) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.DEACTIVATE,
                packId = cleanPackId,
                currentPackVersion = null,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.ANALYSIS_UNAVAILABLE,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "packId",
                        message = "Knowledge pack '$cleanPackId' is not installed.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val warnings = mutableListOf<String>()
        val isAlreadyDeactivated = KnowledgePriorityAndWinnerPolicy.isStoredPriorityDisabled(existingPack.packPriority)
        if (isAlreadyDeactivated) {
            warnings += "Pack '$cleanPackId' is already deactivated."
        }

        val packTopics = dao.getTopicHeadersByPackOrdered(cleanPackId)
        val logicalPriority = KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(existingPack.packPriority)
        val deactivatedStoredPriority = KnowledgePriorityAndWinnerPolicy.encodeDisabledPriority(logicalPriority)

        val topicChanges = mutableListOf<TopicChange>()
        val winnerChanges = mutableListOf<TopicChange>()
        val predictedWinnersByTopicId = mutableMapOf<String, KnowledgeTopicHeaderEntity?>()
        val currentWinnersByTopicId = mutableMapOf<String, KnowledgeTopicHeaderEntity?>()

        for (topic in packTopics) {
            val topicId = topic.topicId
            val candidates = dao.getAllTopicVersionsForTopicId(topicId)
            val currentWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(candidates)
            currentWinnersByTopicId[topicId] = currentWinner

            val simulated = candidates.map {
                if (it.packId == cleanPackId) it.copy(packPriority = deactivatedStoredPriority) else it
            }
            val predictedWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(simulated)
            predictedWinnersByTopicId[topicId] = predictedWinner

            val winnerWillChange = (currentWinner?.packId != predictedWinner?.packId)
            val changeType = if (currentWinner?.packId == cleanPackId) TopicChangeType.REMOVED else TopicChangeType.UNCHANGED

            val tc = TopicChange(
                topicId = topicId,
                changeType = changeType,
                previousPackId = cleanPackId,
                incomingPackId = null,
                previousContentVersion = topic.contentVersion,
                incomingContentVersion = null,
                previousWinnerPackId = currentWinner?.packId,
                predictedWinnerPackId = predictedWinner?.packId,
                winnerWillChange = winnerWillChange
            )
            topicChanges += tc
            if (winnerWillChange) {
                winnerChanges += tc
            }
        }

        // Reference impacts: active incoming references from other packs
        val referenceImpacts = mutableListOf<ReferenceImpact>()
        val activeProjections = dao.getActiveTopicRelatedRefsProjections()
        val packTopicIds = packTopics.map { it.topicId }.toSet()

        for (proj in activeProjections) {
            if (proj.packId == cleanPackId) continue
            val refs = converters.toStringList(proj.relatedTopicIdsJson)
            for (refId in refs) {
                if (refId in packTopicIds) {
                    val currentTargetWinner = currentWinnersByTopicId[refId]
                    val predictedTargetWinner = predictedWinnersByTopicId[refId]
                    if (predictedTargetWinner == null) {
                        referenceImpacts += ReferenceImpact(
                            sourceTopicId = proj.topicId,
                            sourcePackId = proj.packId,
                            referencedTopicId = refId,
                            currentTargetWinnerPackId = currentTargetWinner?.packId,
                            predictedTargetWinnerPackId = null,
                            impactType = ReferenceImpactType.TARGET_REMOVED_NO_FALLBACK,
                            isBlocking = true
                        )
                    } else if (currentTargetWinner?.packId != predictedTargetWinner.packId) {
                        referenceImpacts += ReferenceImpact(
                            sourceTopicId = proj.topicId,
                            sourcePackId = proj.packId,
                            referencedTopicId = refId,
                            currentTargetWinnerPackId = currentTargetWinner?.packId,
                            predictedTargetWinnerPackId = predictedTargetWinner.packId,
                            impactType = ReferenceImpactType.TARGET_WINNER_CHANGED,
                            isBlocking = false
                        )
                    }
                }
            }
        }

        // Concurrency check
        val currentGen = staleGenerationSimulator?.invoke() ?: repository.committedGeneration
        if (genBefore != currentGen) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.DEACTIVATE,
                packId = cleanPackId,
                currentPackVersion = existingPack.packVersion,
                incomingPackVersion = null,
                currentDatabaseGeneration = currentGen,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.STALE_PREVIEW,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "generation",
                        message = "Database generation advanced during deactivation preview.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val hasBlocking = referenceImpacts.any { it.isBlocking }
        val status = when {
            hasBlocking -> ImpactPreviewStatus.BLOCKED_BY_REFERENCE_INTEGRITY
            warnings.isNotEmpty() || winnerChanges.isNotEmpty() -> ImpactPreviewStatus.SAFE_WITH_WARNINGS
            else -> ImpactPreviewStatus.SAFE_TO_APPLY
        }

        val summary = ImpactPreviewSummary(
            removedTopicCount = topicChanges.count { it.changeType == TopicChangeType.REMOVED },
            unchangedTopicCount = topicChanges.count { it.changeType == TopicChangeType.UNCHANGED },
            winnerChangedCount = winnerChanges.size,
            totalAffectedTopicCount = topicChanges.size,
            blockingIssueCount = referenceImpacts.count { it.isBlocking },
            warningCount = warnings.size
        )

        KnowledgePackImpactPreview(
            operationType = PackOperationType.DEACTIVATE,
            packId = cleanPackId,
            currentPackVersion = existingPack.packVersion,
            incomingPackVersion = null,
            currentDatabaseGeneration = currentGen,
            analyzedDatabaseGeneration = genBefore,
            previewStatus = status,
            canApply = status == ImpactPreviewStatus.SAFE_TO_APPLY || status == ImpactPreviewStatus.SAFE_WITH_WARNINGS,
            summary = summary,
            topicChanges = topicChanges,
            winnerChanges = winnerChanges,
            referenceImpacts = referenceImpacts,
            validationIssues = referenceImpacts.filter { it.isBlocking }.map {
                ValidationIssue(
                    category = ValidationIssueCategory.MISSING_REFERENCE,
                    field = "detail.relatedTopicIdsJson",
                    message = "Deactivating pack '$cleanPackId' would orphan referenced topic '${it.referencedTopicId}' from pack '${it.sourcePackId}'.",
                    recordIdentifier = "${it.sourceTopicId}@${it.sourcePackId}"
                )
            },
            warnings = warnings
        )
    }

    /**
     * Previews the impact of reactivating a deactivated knowledge pack.
     */
    suspend fun previewPackReactivation(
        packId: String,
        restoredPriorityOverride: Int? = null
    ): KnowledgePackImpactPreview = withContext(ioDispatcher) {
        val cleanPackId = packId.trim()
        val genBefore = repository.committedGeneration
        val dao = database.knowledgeDao()

        if (cleanPackId == SeedKnowledgeCatalog.CORE_SEED_PACK_ID) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.REACTIVATE,
                packId = cleanPackId,
                currentPackVersion = 1,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.BLOCKED_BY_CORE_PROTECTION,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "packId",
                        message = "Protected core_seed_pack cannot be reactivated.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val existingPack = dao.getPackMetadataById(cleanPackId)
        if (existingPack == null) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.REACTIVATE,
                packId = cleanPackId,
                currentPackVersion = null,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.ANALYSIS_UNAVAILABLE,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "packId",
                        message = "Knowledge pack '$cleanPackId' is not installed.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val restoredPriority = restoredPriorityOverride
            ?: KnowledgePriorityAndWinnerPolicy.decodeLogicalPriority(existingPack.packPriority)
        if (!KnowledgePriorityAndWinnerPolicy.isValidLogicalPriority(restoredPriority)) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.REACTIVATE,
                packId = cleanPackId,
                currentPackVersion = existingPack.packVersion,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.BLOCKED_BY_VALIDATION,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "restoredPriority",
                        message = "Invalid restored priority ($restoredPriority).",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val packTopics = dao.getTopicHeadersByPackOrdered(cleanPackId)
        val topicChanges = mutableListOf<TopicChange>()
        val winnerChanges = mutableListOf<TopicChange>()

        for (topic in packTopics) {
            val topicId = topic.topicId
            val candidates = dao.getAllTopicVersionsForTopicId(topicId)
            val currentWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(candidates)

            val simulated = candidates.map {
                if (it.packId == cleanPackId) it.copy(packPriority = restoredPriority) else it
            }
            val predictedWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(simulated)
            val winnerWillChange = (currentWinner?.packId != predictedWinner?.packId)

            val tc = TopicChange(
                topicId = topicId,
                changeType = TopicChangeType.REACTIVATED,
                previousPackId = cleanPackId,
                incomingPackId = cleanPackId,
                previousContentVersion = topic.contentVersion,
                incomingContentVersion = topic.contentVersion,
                previousWinnerPackId = currentWinner?.packId,
                predictedWinnerPackId = predictedWinner?.packId,
                winnerWillChange = winnerWillChange
            )
            topicChanges += tc
            if (winnerWillChange) {
                winnerChanges += tc
            }
        }

        val currentGen = staleGenerationSimulator?.invoke() ?: repository.committedGeneration
        if (genBefore != currentGen) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.REACTIVATE,
                packId = cleanPackId,
                currentPackVersion = existingPack.packVersion,
                incomingPackVersion = null,
                currentDatabaseGeneration = currentGen,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.STALE_PREVIEW,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "generation",
                        message = "Database generation advanced during reactivation preview.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val warnings = if (winnerChanges.isNotEmpty()) {
            listOf("Reactivating pack '$cleanPackId' will displace ${winnerChanges.size} active topic winner(s).")
        } else {
            emptyList()
        }

        val summary = ImpactPreviewSummary(
            reactivatedTopicCount = topicChanges.size,
            winnerChangedCount = winnerChanges.size,
            totalAffectedTopicCount = topicChanges.size,
            warningCount = warnings.size
        )

        KnowledgePackImpactPreview(
            operationType = PackOperationType.REACTIVATE,
            packId = cleanPackId,
            currentPackVersion = existingPack.packVersion,
            incomingPackVersion = null,
            currentDatabaseGeneration = currentGen,
            analyzedDatabaseGeneration = genBefore,
            previewStatus = if (warnings.isNotEmpty()) ImpactPreviewStatus.SAFE_WITH_WARNINGS else ImpactPreviewStatus.SAFE_TO_APPLY,
            canApply = true,
            summary = summary,
            topicChanges = topicChanges,
            winnerChanges = winnerChanges,
            referenceImpacts = emptyList(),
            validationIssues = emptyList(),
            warnings = warnings
        )
    }

    /**
     * Previews the impact of updating a knowledge pack's logical priority.
     */
    suspend fun previewPackPriorityChange(
        packId: String,
        newPriority: Int
    ): KnowledgePackImpactPreview = withContext(ioDispatcher) {
        val cleanPackId = packId.trim()
        val genBefore = repository.committedGeneration
        val dao = database.knowledgeDao()

        if (cleanPackId == SeedKnowledgeCatalog.CORE_SEED_PACK_ID) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.UPDATE_PRIORITY,
                packId = cleanPackId,
                currentPackVersion = 1,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.BLOCKED_BY_CORE_PROTECTION,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "packId",
                        message = "Protected core_seed_pack priority cannot be modified.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        if (!KnowledgePriorityAndWinnerPolicy.isValidLogicalPriority(newPriority)) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.UPDATE_PRIORITY,
                packId = cleanPackId,
                currentPackVersion = null,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.BLOCKED_BY_VALIDATION,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "newPriority",
                        message = "Invalid priority ($newPriority). Must be non-negative.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val existingPack = dao.getPackMetadataById(cleanPackId)
        if (existingPack == null) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.UPDATE_PRIORITY,
                packId = cleanPackId,
                currentPackVersion = null,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.ANALYSIS_UNAVAILABLE,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "packId",
                        message = "Knowledge pack '$cleanPackId' is not installed.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val isCurrentlyEnabled = KnowledgePriorityAndWinnerPolicy.isStoredPriorityEnabled(existingPack.packPriority)
        val targetStoredPriority = KnowledgePriorityAndWinnerPolicy.encodeStoredPriority(
            logicalPriority = newPriority,
            enabled = isCurrentlyEnabled
        )

        val packTopics = dao.getTopicHeadersByPackOrdered(cleanPackId)
        val topicChanges = mutableListOf<TopicChange>()
        val winnerChanges = mutableListOf<TopicChange>()

        for (topic in packTopics) {
            val topicId = topic.topicId
            val candidates = dao.getAllTopicVersionsForTopicId(topicId)
            val currentWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(candidates)

            val simulated = candidates.map {
                if (it.packId == cleanPackId) it.copy(packPriority = targetStoredPriority) else it
            }
            val predictedWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(simulated)
            val winnerWillChange = (currentWinner?.packId != predictedWinner?.packId)

            val tc = TopicChange(
                topicId = topicId,
                changeType = TopicChangeType.UPDATED,
                previousPackId = cleanPackId,
                incomingPackId = cleanPackId,
                previousContentVersion = topic.contentVersion,
                incomingContentVersion = topic.contentVersion,
                previousWinnerPackId = currentWinner?.packId,
                predictedWinnerPackId = predictedWinner?.packId,
                winnerWillChange = winnerWillChange
            )
            topicChanges += tc
            if (winnerWillChange) {
                winnerChanges += tc
            }
        }

        val currentGen = staleGenerationSimulator?.invoke() ?: repository.committedGeneration
        if (genBefore != currentGen) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.UPDATE_PRIORITY,
                packId = cleanPackId,
                currentPackVersion = existingPack.packVersion,
                incomingPackVersion = null,
                currentDatabaseGeneration = currentGen,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.STALE_PREVIEW,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "generation",
                        message = "Database generation advanced during priority change preview.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val warnings = if (winnerChanges.isNotEmpty()) {
            listOf("Changing priority of pack '$cleanPackId' will change active winners for ${winnerChanges.size} topic(s).")
        } else {
            emptyList()
        }

        val summary = ImpactPreviewSummary(
            updatedTopicCount = topicChanges.size,
            winnerChangedCount = winnerChanges.size,
            totalAffectedTopicCount = topicChanges.size,
            warningCount = warnings.size
        )

        KnowledgePackImpactPreview(
            operationType = PackOperationType.UPDATE_PRIORITY,
            packId = cleanPackId,
            currentPackVersion = existingPack.packVersion,
            incomingPackVersion = null,
            currentDatabaseGeneration = currentGen,
            analyzedDatabaseGeneration = genBefore,
            previewStatus = if (warnings.isNotEmpty()) ImpactPreviewStatus.SAFE_WITH_WARNINGS else ImpactPreviewStatus.SAFE_TO_APPLY,
            canApply = true,
            summary = summary,
            topicChanges = topicChanges,
            winnerChanges = winnerChanges,
            referenceImpacts = emptyList(),
            validationIssues = emptyList(),
            warnings = warnings
        )
    }

    /**
     * Previews the impact of permanently deleting an installed knowledge pack.
     */
    suspend fun previewPackDeletion(packId: String): KnowledgePackImpactPreview = withContext(ioDispatcher) {
        val cleanPackId = packId.trim()
        val genBefore = repository.committedGeneration
        val dao = database.knowledgeDao()

        if (cleanPackId == SeedKnowledgeCatalog.CORE_SEED_PACK_ID) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.DELETE,
                packId = cleanPackId,
                currentPackVersion = 1,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.BLOCKED_BY_CORE_PROTECTION,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "packId",
                        message = "Protected core_seed_pack cannot be deleted.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val existingPack = dao.getPackMetadataById(cleanPackId)
        if (existingPack == null) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.DELETE,
                packId = cleanPackId,
                currentPackVersion = null,
                incomingPackVersion = null,
                currentDatabaseGeneration = genBefore,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.ANALYSIS_UNAVAILABLE,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "packId",
                        message = "Knowledge pack '$cleanPackId' is not installed.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val packTopics = dao.getTopicHeadersByPackOrdered(cleanPackId)
        val packTopicIds = packTopics.map { it.topicId }.toSet()

        // Cascade counts
        var cascadeKwCount = 0
        var cascadeTokenCount = 0
        var cascadeDetailCount = 0
        var cascadeFollowUpCount = 0

        for (topic in packTopics) {
            val kws = dao.getKeywordsByTopicAndPackOrdered(topic.topicId, cleanPackId)
            cascadeKwCount += kws.size
            for (kw in kws) {
                cascadeTokenCount += kw.tokenCount
            }
            if (dao.getTopicDetail(topic.topicId, cleanPackId) != null) {
                cascadeDetailCount++
            }
            cascadeFollowUpCount += dao.getFollowUpBranchesOrdered(topic.topicId, cleanPackId).size
        }

        val topicChanges = mutableListOf<TopicChange>()
        val winnerChanges = mutableListOf<TopicChange>()
        val predictedWinnersByTopicId = mutableMapOf<String, KnowledgeTopicHeaderEntity?>()
        val currentWinnersByTopicId = mutableMapOf<String, KnowledgeTopicHeaderEntity?>()

        for (topic in packTopics) {
            val topicId = topic.topicId
            val candidates = dao.getAllTopicVersionsForTopicId(topicId)
            val currentWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(candidates)
            currentWinnersByTopicId[topicId] = currentWinner

            val simulated = candidates.filterNot { it.packId == cleanPackId }
            val predictedWinner = KnowledgePriorityAndWinnerPolicy.selectWinningTopicHeader(simulated)
            predictedWinnersByTopicId[topicId] = predictedWinner

            val winnerWillChange = (currentWinner?.packId != predictedWinner?.packId)

            val tc = TopicChange(
                topicId = topicId,
                changeType = TopicChangeType.REMOVED,
                previousPackId = cleanPackId,
                incomingPackId = null,
                previousContentVersion = topic.contentVersion,
                incomingContentVersion = null,
                previousWinnerPackId = currentWinner?.packId,
                predictedWinnerPackId = predictedWinner?.packId,
                winnerWillChange = winnerWillChange
            )
            topicChanges += tc
            if (winnerWillChange) {
                winnerChanges += tc
            }
        }

        // Reference impacts: check incoming references from other active packs
        val referenceImpacts = mutableListOf<ReferenceImpact>()
        val activeProjections = dao.getActiveTopicRelatedRefsProjections()

        for (proj in activeProjections) {
            if (proj.packId == cleanPackId) continue
            val refs = converters.toStringList(proj.relatedTopicIdsJson)
            for (refId in refs) {
                if (refId in packTopicIds) {
                    val currentTargetWinner = currentWinnersByTopicId[refId]
                    val predictedTargetWinner = predictedWinnersByTopicId[refId]
                    if (predictedTargetWinner == null) {
                        referenceImpacts += ReferenceImpact(
                            sourceTopicId = proj.topicId,
                            sourcePackId = proj.packId,
                            referencedTopicId = refId,
                            currentTargetWinnerPackId = currentTargetWinner?.packId,
                            predictedTargetWinnerPackId = null,
                            impactType = ReferenceImpactType.TARGET_REMOVED_NO_FALLBACK,
                            isBlocking = true
                        )
                    } else if (currentTargetWinner?.packId != predictedTargetWinner.packId) {
                        referenceImpacts += ReferenceImpact(
                            sourceTopicId = proj.topicId,
                            sourcePackId = proj.packId,
                            referencedTopicId = refId,
                            currentTargetWinnerPackId = currentTargetWinner?.packId,
                            predictedTargetWinnerPackId = predictedTargetWinner.packId,
                            impactType = ReferenceImpactType.TARGET_WINNER_CHANGED,
                            isBlocking = false
                        )
                    }
                }
            }
        }

        val currentGen = staleGenerationSimulator?.invoke() ?: repository.committedGeneration
        if (genBefore != currentGen) {
            return@withContext KnowledgePackImpactPreview(
                operationType = PackOperationType.DELETE,
                packId = cleanPackId,
                currentPackVersion = existingPack.packVersion,
                incomingPackVersion = null,
                currentDatabaseGeneration = currentGen,
                analyzedDatabaseGeneration = genBefore,
                previewStatus = ImpactPreviewStatus.STALE_PREVIEW,
                canApply = false,
                summary = ImpactPreviewSummary(blockingIssueCount = 1),
                topicChanges = emptyList(),
                winnerChanges = emptyList(),
                referenceImpacts = emptyList(),
                validationIssues = listOf(
                    ValidationIssue(
                        category = ValidationIssueCategory.VALIDATION_ERROR,
                        field = "generation",
                        message = "Database generation advanced during deletion preview.",
                        recordIdentifier = cleanPackId
                    )
                ),
                warnings = emptyList()
            )
        }

        val hasBlocking = referenceImpacts.any { it.isBlocking }
        val warnings = mutableListOf<String>()
        if (winnerChanges.isNotEmpty()) {
            warnings += "Deleting pack '$cleanPackId' will change winners for ${winnerChanges.size} topic(s)."
        }

        val status = when {
            hasBlocking -> ImpactPreviewStatus.BLOCKED_BY_REFERENCE_INTEGRITY
            warnings.isNotEmpty() -> ImpactPreviewStatus.SAFE_WITH_WARNINGS
            else -> ImpactPreviewStatus.SAFE_TO_APPLY
        }

        val summary = ImpactPreviewSummary(
            removedTopicCount = topicChanges.size,
            winnerChangedCount = winnerChanges.size,
            totalAffectedTopicCount = topicChanges.size,
            cascadeKeywordsCount = cascadeKwCount,
            cascadeTokensCount = cascadeTokenCount,
            cascadeDetailsCount = cascadeDetailCount,
            cascadeFollowUpsCount = cascadeFollowUpCount,
            blockingIssueCount = referenceImpacts.count { it.isBlocking },
            warningCount = warnings.size
        )

        KnowledgePackImpactPreview(
            operationType = PackOperationType.DELETE,
            packId = cleanPackId,
            currentPackVersion = existingPack.packVersion,
            incomingPackVersion = null,
            currentDatabaseGeneration = currentGen,
            analyzedDatabaseGeneration = genBefore,
            previewStatus = status,
            canApply = status == ImpactPreviewStatus.SAFE_TO_APPLY || status == ImpactPreviewStatus.SAFE_WITH_WARNINGS,
            summary = summary,
            topicChanges = topicChanges,
            winnerChanges = winnerChanges,
            referenceImpacts = referenceImpacts,
            validationIssues = referenceImpacts.filter { it.isBlocking }.map {
                ValidationIssue(
                    category = ValidationIssueCategory.MISSING_REFERENCE,
                    field = "detail.relatedTopicIdsJson",
                    message = "Deleting pack '$cleanPackId' would orphan referenced topic '${it.referencedTopicId}' from pack '${it.sourcePackId}'.",
                    recordIdentifier = "${it.sourceTopicId}@${it.sourcePackId}"
                )
            },
            warnings = warnings
        )
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
