package com.example.core.intelligence.knowledge.importer

import android.content.Context
import androidx.room.withTransaction
import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KeywordWithTokensBundle
import com.example.core.intelligence.knowledge.db.dao.KnowledgeDao
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.db.entity.KnowledgeFollowUpBranchEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicDetailEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.core.intelligence.knowledge.validation.KnowledgeValidator
import com.example.core.intelligence.knowledge.validation.ValidationIssue
import com.squareup.moshi.JsonWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Production-grade JSON Knowledge Pack Importer for `WEYKY_KNOWLEDGE_PACK_V1`.
 *
 * Responsibilities:
 * 1. Parses JSON knowledge packs from String, ByteArray, InputStream, or Android Assets on
 *    [ioDispatcher] (`Dispatchers.IO`) using [KnowledgePackJsonParser] with strict size, depth,
 *    and duplicate-key protection.
 * 2. Computes and verifies raw byte-level SHA-256 and canonical logical SHA-256 (avoiding circular
 *    self-hashing when `contentSha256` is embedded in the JSON).
 * 3. Protects `core_seed_pack` and all 11 core seed topics (`CORE_PROTECTION_VIOLATION`).
 * 4. Enforces idempotency (`ALREADY_IMPORTED`), version progression (`SUCCESS_UPDATED`), and
 *    conflict rejection (`DUPLICATE_CONFLICT` on version downgrade or same-version content drift).
 * 5. Validates intra-pack and cross-pack topic references (`REFERENCE_INVALID`), including
 *    preventing updates that would orphan references held by other active topics.
 * 6. Executes atomic database persistence via Room (`withTransaction`), followed by bulk snapshot
 *    publication (`<= 3` `SELECT` queries), targeted cache invalidation, and automatic snapshot
 *    recovery if a post-commit snapshot rebuild error occurs.
 * 7. Preserves 100% lazy loading of `KnowledgeTopicDetailEntity` and `KnowledgeFollowUpBranchEntity`
 *    (`DetailCache` is never populated during import).
 */
class KnowledgePackImporter(
    private val database: WeykyKnowledgeDatabase,
    private val repository: KnowledgeRepository,
    val limits: KnowledgePackImportLimits = KnowledgePackImportLimits(),
    private val converters: KnowledgeTypeConverters = KnowledgeTypeConverters(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val parser = KnowledgePackJsonParser(limits = limits, ioDispatcher = ioDispatcher)
    private val importMutex = Mutex()

    init {
        repository.attachDatabaseIfAbsent(database)
    }

    /**
     * Imports a `WEYKY_KNOWLEDGE_PACK_V1` JSON string on [ioDispatcher].
     */
    suspend fun importFromJsonString(
        json: String,
        expectedSha256: String? = null,
        simulatedMidTransactionFailure: (() -> Unit)? = null,
        simulatedSnapshotBuildFailure: (() -> Unit)? = null,
        simulatedRecoveryFailure: (() -> Unit)? = null
    ): KnowledgePackImportResult = withContext(ioDispatcher) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        importFromByteArray(
            bytes = bytes,
            expectedSha256 = expectedSha256,
            simulatedMidTransactionFailure = simulatedMidTransactionFailure,
            simulatedSnapshotBuildFailure = simulatedSnapshotBuildFailure,
            simulatedRecoveryFailure = simulatedRecoveryFailure
        )
    }

    /**
     * Imports a `WEYKY_KNOWLEDGE_PACK_V1` UTF-8 byte array on [ioDispatcher].
     */
    suspend fun importFromByteArray(
        bytes: ByteArray,
        expectedSha256: String? = null,
        simulatedMidTransactionFailure: (() -> Unit)? = null,
        simulatedSnapshotBuildFailure: (() -> Unit)? = null,
        simulatedRecoveryFailure: (() -> Unit)? = null
    ): KnowledgePackImportResult = withContext(ioDispatcher) {
        val startNanos = System.nanoTime()
        val parseOutcome = parser.parseFromByteArray(bytes)
        executeImportPipeline(
            parseOutcome = parseOutcome,
            expectedSha256 = expectedSha256,
            totalStartNanos = startNanos,
            simulatedMidTransactionFailure = simulatedMidTransactionFailure,
            simulatedSnapshotBuildFailure = simulatedSnapshotBuildFailure,
            simulatedRecoveryFailure = simulatedRecoveryFailure
        )
    }

    /**
     * Imports a `WEYKY_KNOWLEDGE_PACK_V1` from an [InputStream] on [ioDispatcher].
     */
    suspend fun importFromInputStream(
        inputStream: InputStream,
        expectedSha256: String? = null,
        simulatedMidTransactionFailure: (() -> Unit)? = null,
        simulatedSnapshotBuildFailure: (() -> Unit)? = null,
        simulatedRecoveryFailure: (() -> Unit)? = null
    ): KnowledgePackImportResult = withContext(ioDispatcher) {
        val startNanos = System.nanoTime()
        val parseOutcome = parser.parseFromInputStream(inputStream)
        executeImportPipeline(
            parseOutcome = parseOutcome,
            expectedSha256 = expectedSha256,
            totalStartNanos = startNanos,
            simulatedMidTransactionFailure = simulatedMidTransactionFailure,
            simulatedSnapshotBuildFailure = simulatedSnapshotBuildFailure,
            simulatedRecoveryFailure = simulatedRecoveryFailure
        )
    }

    /**
     * Imports a `WEYKY_KNOWLEDGE_PACK_V1` from an Android asset file on [ioDispatcher].
     */
    suspend fun importFromAsset(
        context: Context,
        assetPath: String,
        expectedSha256: String? = null,
        simulatedMidTransactionFailure: (() -> Unit)? = null,
        simulatedSnapshotBuildFailure: (() -> Unit)? = null,
        simulatedRecoveryFailure: (() -> Unit)? = null
    ): KnowledgePackImportResult = withContext(ioDispatcher) {
        val startNanos = System.nanoTime()
        val openResult = runCatching { context.assets.open(assetPath) }
        val stream = openResult.getOrElse { err ->
            val totalMs = (System.nanoTime() - startNanos) / 1_000_000L
            return@withContext KnowledgePackImportResult(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                committedGeneration = repository.committedGeneration,
                publishedGeneration = repository.publishedGeneration,
                databaseCommitted = false,
                errors = listOf("Failed to open asset '$assetPath': ${err.message}"),
                snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                metrics = KnowledgePackImportMetrics(
                    totalImportTimeMs = totalMs,
                    detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                )
            )
        }
        stream.use { input ->
            val parseOutcome = parser.parseFromInputStream(input)
            executeImportPipeline(
                parseOutcome = parseOutcome,
                expectedSha256 = expectedSha256,
                totalStartNanos = startNanos,
                simulatedMidTransactionFailure = simulatedMidTransactionFailure,
                simulatedSnapshotBuildFailure = simulatedSnapshotBuildFailure,
                simulatedRecoveryFailure = simulatedRecoveryFailure
            )
        }
    }

    private suspend fun executeImportPipeline(
        parseOutcome: KnowledgePackParseOutcome,
        expectedSha256: String?,
        totalStartNanos: Long,
        simulatedMidTransactionFailure: (() -> Unit)?,
        simulatedSnapshotBuildFailure: (() -> Unit)?,
        simulatedRecoveryFailure: (() -> Unit)?
    ): KnowledgePackImportResult = importMutex.withLock {
        repository.withRepositoryMutationLock {
            when (parseOutcome) {
                is KnowledgePackParseOutcome.Failure -> {
                    val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                    return@withRepositoryMutationLock KnowledgePackImportResult(
                        status = parseOutcome.status,
                        packId = parseOutcome.packId,
                        packVersion = parseOutcome.packVersion,
                        contentVersion = parseOutcome.contentVersion,
                        activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                        committedGeneration = repository.committedGeneration,
                        publishedGeneration = repository.publishedGeneration,
                        databaseCommitted = false,
                        rawBytesSha256 = parseOutcome.rawBytesSha256,
                        snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                        errors = parseOutcome.errors,
                        metrics = KnowledgePackImportMetrics(
                            parseTimeMs = parseOutcome.parseTimeMs,
                            totalImportTimeMs = totalMs,
                            inputByteCount = parseOutcome.inputByteCount,
                            detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                        )
                    )
                }
                is KnowledgePackParseOutcome.Success -> {
                    val parsedPack = parseOutcome.pack
                    val parseTimeMs = parseOutcome.parseTimeMs
                    val validationStartNanos = System.nanoTime()

                    // 1. Compute canonical logical SHA-256 (excludes contentSha256 field)
                    val rawSha256 = parsedPack.rawBytesSha256
                    val logicalSha256 = KnowledgePackSpecificationV1.computeLogicalContentSha256(parsedPack)

                    // 2. Verify expectedSha256 & embedded contentSha256
                    val hashErrors = verifyChecksums(
                        rawSha256 = rawSha256,
                        logicalSha256 = logicalSha256,
                        expectedSha256 = expectedSha256,
                        embeddedContentSha256 = parsedPack.contentSha256
                    )
                    if (hashErrors.isNotEmpty()) {
                        val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                        val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                        return@withRepositoryMutationLock KnowledgePackImportResult(
                            status = KnowledgePackImportStatus.HASH_MISMATCH,
                            packId = parsedPack.packId,
                            packVersion = parsedPack.packVersion,
                            contentVersion = parsedPack.contentVersion,
                            rejectedTopicCount = parsedPack.topics.size,
                            activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                            committedGeneration = repository.committedGeneration,
                            publishedGeneration = repository.publishedGeneration,
                            databaseCommitted = false,
                            rawBytesSha256 = rawSha256,
                            logicalContentSha256 = logicalSha256,
                            snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                            errors = hashErrors,
                            metrics = KnowledgePackImportMetrics(
                                parseTimeMs = parseTimeMs,
                                validationTimeMs = valMs,
                                totalImportTimeMs = totalMs,
                                inputByteCount = parsedPack.rawByteSize,
                                detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                            )
                        )
                    }

                    // 3. Check Core Seed Protection BEFORE any other conflict/storage logic
                    val coreProtectionErrors = checkCoreProtection(parsedPack)
                    if (coreProtectionErrors.isNotEmpty()) {
                        val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                        val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                        return@withRepositoryMutationLock KnowledgePackImportResult(
                            status = KnowledgePackImportStatus.CORE_PROTECTION_VIOLATION,
                            packId = parsedPack.packId,
                            packVersion = parsedPack.packVersion,
                            contentVersion = parsedPack.contentVersion,
                            rejectedTopicCount = parsedPack.topics.size,
                            activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                            committedGeneration = repository.committedGeneration,
                            publishedGeneration = repository.publishedGeneration,
                            databaseCommitted = false,
                            rawBytesSha256 = rawSha256,
                            logicalContentSha256 = logicalSha256,
                            snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                            errors = coreProtectionErrors,
                            metrics = KnowledgePackImportMetrics(
                                parseTimeMs = parseTimeMs,
                                validationTimeMs = valMs,
                                totalImportTimeMs = totalMs,
                                inputByteCount = parsedPack.rawByteSize,
                                detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                            )
                        )
                    }

                    // 4. Intra-pack duplicate & semantic validation
                    val duplicateTopicErrors = checkIntraPackDuplicateTopics(parsedPack)
                    if (duplicateTopicErrors.isNotEmpty()) {
                        val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                        val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                        return@withRepositoryMutationLock KnowledgePackImportResult(
                            status = KnowledgePackImportStatus.DUPLICATE_CONFLICT,
                            packId = parsedPack.packId,
                            packVersion = parsedPack.packVersion,
                            contentVersion = parsedPack.contentVersion,
                            rejectedTopicCount = parsedPack.topics.size,
                            activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                            committedGeneration = repository.committedGeneration,
                            publishedGeneration = repository.publishedGeneration,
                            databaseCommitted = false,
                            rawBytesSha256 = rawSha256,
                            logicalContentSha256 = logicalSha256,
                            snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                            errors = duplicateTopicErrors,
                            metrics = KnowledgePackImportMetrics(
                                parseTimeMs = parseTimeMs,
                                validationTimeMs = valMs,
                                totalImportTimeMs = totalMs,
                                inputByteCount = parsedPack.rawByteSize,
                                detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                            )
                        )
                    }

                    val preValidationErrors = validateParsedPackSemantics(parsedPack)
                    if (preValidationErrors.isNotEmpty()) {
                        val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                        val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                        return@withRepositoryMutationLock KnowledgePackImportResult(
                            status = KnowledgePackImportStatus.VALIDATION_FAILED,
                            packId = parsedPack.packId,
                            packVersion = parsedPack.packVersion,
                            contentVersion = parsedPack.contentVersion,
                            rejectedTopicCount = parsedPack.topics.size,
                            activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                            committedGeneration = repository.committedGeneration,
                            publishedGeneration = repository.publishedGeneration,
                            databaseCommitted = false,
                            rawBytesSha256 = rawSha256,
                            logicalContentSha256 = logicalSha256,
                            snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                            errors = preValidationErrors,
                            metrics = KnowledgePackImportMetrics(
                                parseTimeMs = parseTimeMs,
                                validationTimeMs = valMs,
                                totalImportTimeMs = totalMs,
                                inputByteCount = parsedPack.rawByteSize,
                                detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                            )
                        )
                    }

                    val dao = database.knowledgeDao()

                    // Ensure core seed is persisted in Room so active winner resolution and reference checks
                    // always see the 11 protected core seed topics in the database.
                    val seedReady = runCatching {
                        repository.ensureCoreSeedPersistedInDatabase()
                    }.getOrDefault(false)
                    if (!seedReady) {
                        val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                        val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                        return@withRepositoryMutationLock KnowledgePackImportResult(
                            status = KnowledgePackImportStatus.STORAGE_FAILURE,
                            packId = parsedPack.packId,
                            packVersion = parsedPack.packVersion,
                            contentVersion = parsedPack.contentVersion,
                            rejectedTopicCount = parsedPack.topics.size,
                            activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                            committedGeneration = repository.committedGeneration,
                            publishedGeneration = repository.publishedGeneration,
                            databaseCommitted = false,
                            rawBytesSha256 = rawSha256,
                            logicalContentSha256 = logicalSha256,
                            snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                            errors = listOf("Database failed to verify or initialize core_seed_pack prior to import."),
                            metrics = KnowledgePackImportMetrics(
                                parseTimeMs = parseTimeMs,
                                validationTimeMs = valMs,
                                totalImportTimeMs = totalMs,
                                inputByteCount = parsedPack.rawByteSize,
                                detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                            )
                        )
                    }

                    // 5. Check Existing Pack in Room for Idempotency / Version Progression / Conflict
                    val existingPack = runCatching { dao.getPackMetadataById(parsedPack.packId) }.getOrNull()
                    val existingHeaders = if (existingPack != null) {
                        runCatching { dao.getTopicHeadersByPackOrdered(parsedPack.packId) }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                    val isUpdatingExistingPack = existingPack != null

                    if (existingPack != null) {
                        val existingMaxContentVersion = existingHeaders.maxOfOrNull { it.contentVersion }
                            ?: existingPack.packVersion
                        val storedChecksum = existingPack.sha256Checksum
                        val isIdenticalChecksum = storedChecksum.equals(logicalSha256, ignoreCase = true) ||
                            storedChecksum.equals(rawSha256, ignoreCase = true)

                        when {
                            // Exact same version and identical content/checksum -> ALREADY_IMPORTED
                            // If a previous import committed the DB transaction but failed during snapshot rebuild/publish,
                            // recover the stale snapshot from the database WITHOUT duplicating the DB transaction!
                            parsedPack.packVersion == existingPack.packVersion &&
                                parsedPack.contentVersion == existingMaxContentVersion &&
                                isIdenticalChecksum -> {
                                val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                                val needsSnapshotSync = repository.isSnapshotStale ||
                                    !isCommittedPackReflectedInSnapshot(parsedPack, existingPack, existingHeaders, dao)

                                if (!needsSnapshotSync) {
                                    val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                                    return@withRepositoryMutationLock KnowledgePackImportResult(
                                        status = KnowledgePackImportStatus.ALREADY_IMPORTED,
                                        packId = parsedPack.packId,
                                        packVersion = parsedPack.packVersion,
                                        contentVersion = parsedPack.contentVersion,
                                        importedTopicCount = 0,
                                        updatedTopicCount = 0,
                                        skippedTopicCount = parsedPack.topics.size,
                                        rejectedTopicCount = 0,
                                        activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                                        committedGeneration = repository.committedGeneration,
                                        publishedGeneration = repository.publishedGeneration,
                                        databaseCommitted = true,
                                        rawBytesSha256 = rawSha256,
                                        logicalContentSha256 = logicalSha256,
                                        snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                                        warnings = listOf(
                                            "Pack '${parsedPack.packId}' (v${parsedPack.packVersion}, contentVersion=${parsedPack.contentVersion}) is already imported with matching SHA-256."
                                        ),
                                        metrics = KnowledgePackImportMetrics(
                                            parseTimeMs = parseTimeMs,
                                            validationTimeMs = valMs,
                                            totalImportTimeMs = totalMs,
                                            inputByteCount = parsedPack.rawByteSize,
                                            detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                                        )
                                    )
                                }

                                val snapshotStartNanos = System.nanoTime()
                                val affectedIdsForSync = existingHeaders.map { it.topicId }.toSet().ifEmpty {
                                    parsedPack.topics.map { it.topicId }.toSet()
                                }
                                val targetGen = maxOf(
                                    repository.committedGeneration,
                                    repository.publishedGeneration + 1L
                                )
                                val syncWarnings = mutableListOf(
                                    "Pack '${parsedPack.packId}' (v${parsedPack.packVersion}, contentVersion=${parsedPack.contentVersion}) is already committed in database with matching SHA-256; recovered stale in-memory snapshot without duplicating database writes."
                                )
                                var initialSyncFailureMessage: String? = null
                                val synced = try {
                                    simulatedSnapshotBuildFailure?.invoke()
                                    val activeBundles = repository.loadActiveTopicStorageBundlesFromDao(dao)
                                    if (activeBundles.isEmpty()) {
                                        throw IllegalStateException("Active topic bundles from database are empty during idempotent snapshot recovery.")
                                    }
                                    repository.publishNewSnapshotFromBundles(
                                        bundles = activeBundles,
                                        affectedTopicIds = affectedIdsForSync,
                                        affectedPackId = parsedPack.packId,
                                        targetGenerationOverride = targetGen
                                    )
                                    true
                                } catch (initialSyncError: Throwable) {
                                    initialSyncFailureMessage = initialSyncError.message
                                    val recoveredOnRetry = try {
                                        simulatedRecoveryFailure?.invoke()
                                        val recoveryBundles = repository.loadActiveTopicStorageBundlesFromDao(dao)
                                        if (recoveryBundles.isEmpty()) {
                                            false
                                        } else {
                                            repository.publishNewSnapshotFromBundles(
                                                bundles = recoveryBundles,
                                                affectedTopicIds = affectedIdsForSync,
                                                affectedPackId = parsedPack.packId,
                                                targetGenerationOverride = targetGen
                                            )
                                            true
                                        }
                                    } catch (_: Throwable) {
                                        false
                                    }
                                    if (recoveredOnRetry) {
                                        syncWarnings.add(
                                            "Initial snapshot recovery encountered an error (${initialSyncError.message}); secondary recovery from database succeeded."
                                        )
                                    }
                                    recoveredOnRetry
                                }

                                val snapshotMs = (System.nanoTime() - snapshotStartNanos) / 1_000_000L
                                val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                                if (synced) {
                                    return@withRepositoryMutationLock KnowledgePackImportResult(
                                        status = KnowledgePackImportStatus.ALREADY_IMPORTED,
                                        packId = parsedPack.packId,
                                        packVersion = parsedPack.packVersion,
                                        contentVersion = parsedPack.contentVersion,
                                        importedTopicCount = 0,
                                        updatedTopicCount = 0,
                                        skippedTopicCount = parsedPack.topics.size,
                                        rejectedTopicCount = 0,
                                        activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                                        committedGeneration = repository.committedGeneration,
                                        publishedGeneration = repository.publishedGeneration,
                                        databaseCommitted = true,
                                        rawBytesSha256 = rawSha256,
                                        logicalContentSha256 = logicalSha256,
                                        snapshotRefreshState = SnapshotRefreshState.RECOVERED_FROM_DATABASE,
                                        warnings = syncWarnings,
                                        metrics = KnowledgePackImportMetrics(
                                            parseTimeMs = parseTimeMs,
                                            validationTimeMs = valMs,
                                            snapshotRebuildTimeMs = snapshotMs,
                                            totalImportTimeMs = totalMs,
                                            inputByteCount = parsedPack.rawByteSize,
                                            detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                                        )
                                    )
                                } else {
                                    return@withRepositoryMutationLock KnowledgePackImportResult(
                                        status = KnowledgePackImportStatus.SNAPSHOT_REFRESH_FAILURE,
                                        packId = parsedPack.packId,
                                        packVersion = parsedPack.packVersion,
                                        contentVersion = parsedPack.contentVersion,
                                        importedTopicCount = 0,
                                        updatedTopicCount = 0,
                                        skippedTopicCount = parsedPack.topics.size,
                                        rejectedTopicCount = 0,
                                        activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                                        committedGeneration = repository.committedGeneration,
                                        publishedGeneration = repository.publishedGeneration,
                                        databaseCommitted = true,
                                        rawBytesSha256 = rawSha256,
                                        logicalContentSha256 = logicalSha256,
                                        snapshotRefreshState = SnapshotRefreshState.FAILED_PREVIOUS_SNAPSHOT_RETAINED,
                                        errors = listOf(
                                            "Pack '${parsedPack.packId}' is already committed in database, but snapshot recovery failed: ${initialSyncFailureMessage ?: "unknown error"}. Previous valid snapshot was retained."
                                        ),
                                        metrics = KnowledgePackImportMetrics(
                                            parseTimeMs = parseTimeMs,
                                            validationTimeMs = valMs,
                                            snapshotRebuildTimeMs = snapshotMs,
                                            totalImportTimeMs = totalMs,
                                            inputByteCount = parsedPack.rawByteSize,
                                            detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                                        )
                                    )
                                }
                            }

                            // Same version (packVersion + contentVersion) but different content -> DUPLICATE_CONFLICT
                            parsedPack.packVersion == existingPack.packVersion &&
                                parsedPack.contentVersion == existingMaxContentVersion &&
                                !isIdenticalChecksum -> {
                                val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                                val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                                return@withRepositoryMutationLock KnowledgePackImportResult(
                                    status = KnowledgePackImportStatus.DUPLICATE_CONFLICT,
                                    packId = parsedPack.packId,
                                    packVersion = parsedPack.packVersion,
                                    contentVersion = parsedPack.contentVersion,
                                    rejectedTopicCount = parsedPack.topics.size,
                                    activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                                    committedGeneration = repository.committedGeneration,
                                    publishedGeneration = repository.publishedGeneration,
                                    databaseCommitted = false,
                                    rawBytesSha256 = rawSha256,
                                    logicalContentSha256 = logicalSha256,
                                    snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                                    errors = listOf(
                                        "Conflict for pack '${parsedPack.packId}': version (packVersion=${parsedPack.packVersion}, contentVersion=${parsedPack.contentVersion}) is already imported with a different SHA-256 checksum."
                                    ),
                                    metrics = KnowledgePackImportMetrics(
                                        parseTimeMs = parseTimeMs,
                                        validationTimeMs = valMs,
                                        totalImportTimeMs = totalMs,
                                        inputByteCount = parsedPack.rawByteSize,
                                        detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                                    )
                                )
                            }

                            // Older packVersion or older contentVersion -> DUPLICATE_CONFLICT
                            parsedPack.packVersion < existingPack.packVersion ||
                                (parsedPack.packVersion == existingPack.packVersion &&
                                    parsedPack.contentVersion < existingMaxContentVersion) -> {
                                val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                                val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                                return@withRepositoryMutationLock KnowledgePackImportResult(
                                    status = KnowledgePackImportStatus.DUPLICATE_CONFLICT,
                                    packId = parsedPack.packId,
                                    packVersion = parsedPack.packVersion,
                                    contentVersion = parsedPack.contentVersion,
                                    rejectedTopicCount = parsedPack.topics.size,
                                    activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                                    committedGeneration = repository.committedGeneration,
                                    publishedGeneration = repository.publishedGeneration,
                                    databaseCommitted = false,
                                    rawBytesSha256 = rawSha256,
                                    logicalContentSha256 = logicalSha256,
                                    snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                                    errors = listOf(
                                        "Downgrade rejected for pack '${parsedPack.packId}': incoming version (packVersion=${parsedPack.packVersion}, contentVersion=${parsedPack.contentVersion}) is older than installed version (packVersion=${existingPack.packVersion}, contentVersion=$existingMaxContentVersion)."
                                    ),
                                    metrics = KnowledgePackImportMetrics(
                                        parseTimeMs = parseTimeMs,
                                        validationTimeMs = valMs,
                                        totalImportTimeMs = totalMs,
                                        inputByteCount = parsedPack.rawByteSize,
                                        detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                                    )
                                )
                            }
                        }
                    }

                    // 6. Validate Topic Reference Integrity (intra-pack, active snapshot, inactive packs, & removed dependents)
                    val referenceValidation = validateReferenceIntegrity(
                        parsedPack = parsedPack,
                        existingHeadersForSamePack = existingHeaders,
                        dao = dao
                    )
                    if (referenceValidation.errors.isNotEmpty()) {
                        val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                        val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                        return@withRepositoryMutationLock KnowledgePackImportResult(
                            status = KnowledgePackImportStatus.REFERENCE_INVALID,
                            packId = parsedPack.packId,
                            packVersion = parsedPack.packVersion,
                            contentVersion = parsedPack.contentVersion,
                            rejectedTopicCount = parsedPack.topics.size,
                            activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                            committedGeneration = repository.committedGeneration,
                            publishedGeneration = repository.publishedGeneration,
                            databaseCommitted = false,
                            rawBytesSha256 = rawSha256,
                            logicalContentSha256 = logicalSha256,
                            snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                            errors = referenceValidation.errors,
                            metrics = KnowledgePackImportMetrics(
                                parseTimeMs = parseTimeMs,
                                validationTimeMs = valMs,
                                totalImportTimeMs = totalMs,
                                inputByteCount = parsedPack.rawByteSize,
                                detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                            )
                        )
                    }

                    // 7. Convert Parsed DTOs to Room Entities & Validate via KnowledgeValidator
                    val packEntity = buildPackMetadataEntity(parsedPack, logicalSha256)
                    val topicBundles = try {
                        buildTopicStorageBundles(parsedPack, rawSha256, logicalSha256)
                    } catch (convError: Throwable) {
                        val valMs = (System.nanoTime() - validationStartNanos) / 1_000_000L
                        val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                        return@withRepositoryMutationLock KnowledgePackImportResult(
                            status = KnowledgePackImportStatus.VALIDATION_FAILED,
                            packId = parsedPack.packId,
                            packVersion = parsedPack.packVersion,
                            contentVersion = parsedPack.contentVersion,
                            rejectedTopicCount = parsedPack.topics.size,
                            activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                            committedGeneration = repository.committedGeneration,
                            publishedGeneration = repository.publishedGeneration,
                            databaseCommitted = false,
                            rawBytesSha256 = rawSha256,
                            logicalContentSha256 = logicalSha256,
                            snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                            errors = listOf("Failed to build storage entities: ${convError.message}"),
                            metrics = KnowledgePackImportMetrics(
                                parseTimeMs = parseTimeMs,
                                validationTimeMs = valMs,
                                totalImportTimeMs = totalMs,
                                inputByteCount = parsedPack.rawByteSize,
                                detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                            )
                        )
                    }

                    val validatorResult = KnowledgeValidator.validatePackBundles(
                        pack = packEntity,
                        bundles = topicBundles,
                        referenceTopicIds = referenceValidation.effectiveActiveTopicIdsAfterImport,
                        enforceCoreTopicProtection = true
                    )
                    val validationTimeMs = (System.nanoTime() - validationStartNanos) / 1_000_000L

                    if (!validatorResult.isValid) {
                        val mappedStatus = classifyValidatorIssues(validatorResult.issues)
                        val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                        return@withRepositoryMutationLock KnowledgePackImportResult(
                            status = mappedStatus,
                            packId = parsedPack.packId,
                            packVersion = parsedPack.packVersion,
                            contentVersion = parsedPack.contentVersion,
                            rejectedTopicCount = parsedPack.topics.size,
                            activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                            committedGeneration = repository.committedGeneration,
                            publishedGeneration = repository.publishedGeneration,
                            databaseCommitted = false,
                            rawBytesSha256 = rawSha256,
                            logicalContentSha256 = logicalSha256,
                            snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                            errors = validatorResult.issues.map {
                                "[${it.category}] ${it.field}(${it.recordIdentifier ?: "pack"}): ${it.message}"
                            },
                            metrics = KnowledgePackImportMetrics(
                                parseTimeMs = parseTimeMs,
                                validationTimeMs = validationTimeMs,
                                totalImportTimeMs = totalMs,
                                inputByteCount = parsedPack.rawByteSize,
                                detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                            )
                        )
                    }

                    // 8. Execute Atomic Database Transaction
                    val storageStartNanos = System.nanoTime()
                    val affectedTopicIds: Set<String> = try {
                        database.withTransaction {
                            val affected = dao.upsertPackWithTopicsTransactional(packEntity, topicBundles)
                            simulatedMidTransactionFailure?.invoke()
                            affected
                        }
                    } catch (txError: Throwable) {
                        val storageMs = (System.nanoTime() - storageStartNanos) / 1_000_000L
                        val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                        return@withRepositoryMutationLock KnowledgePackImportResult(
                            status = KnowledgePackImportStatus.STORAGE_FAILURE,
                            packId = parsedPack.packId,
                            packVersion = parsedPack.packVersion,
                            contentVersion = parsedPack.contentVersion,
                            rejectedTopicCount = parsedPack.topics.size,
                            activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                            committedGeneration = repository.committedGeneration,
                            publishedGeneration = repository.publishedGeneration,
                            databaseCommitted = false,
                            rawBytesSha256 = rawSha256,
                            logicalContentSha256 = logicalSha256,
                            snapshotRefreshState = SnapshotRefreshState.NOT_MODIFIED,
                            errors = listOf("Atomic database transaction failed and was rolled back: ${txError.message}"),
                            metrics = KnowledgePackImportMetrics(
                                parseTimeMs = parseTimeMs,
                                validationTimeMs = validationTimeMs,
                                storageTransactionTimeMs = storageMs,
                                totalImportTimeMs = totalMs,
                                inputByteCount = parsedPack.rawByteSize,
                                detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                            )
                        )
                    }
                    val storageTimeMs = (System.nanoTime() - storageStartNanos) / 1_000_000L
                    val committedGen = repository.recordCommittedDatabaseMutation(
                        affectedTopicIds = affectedTopicIds,
                        affectedPackId = parsedPack.packId
                    )

                    // 9. Rebuild & Publish Active Snapshot from Committed DB State (with Automatic Recovery)
                    val snapshotStartNanos = System.nanoTime()
                    val warnings = mutableListOf<String>()
                    var refreshState: SnapshotRefreshState

                    try {
                        simulatedSnapshotBuildFailure?.invoke()
                        val activeBundles = repository.loadActiveTopicStorageBundlesFromDao(dao)
                        if (activeBundles.isEmpty()) {
                            throw IllegalStateException("Active topic bundles from database are empty after commit.")
                        }
                        repository.publishNewSnapshotFromBundles(
                            bundles = activeBundles,
                            affectedTopicIds = affectedTopicIds,
                            affectedPackId = parsedPack.packId,
                            targetGenerationOverride = committedGen
                        )
                        refreshState = SnapshotRefreshState.REFRESHED_ATOMICALLY
                    } catch (initialSnapshotError: Throwable) {
                        // Attempt automatic recovery from the committed database state
                        val recovered = try {
                            simulatedRecoveryFailure?.invoke()
                            val recoveryBundles = repository.loadActiveTopicStorageBundlesFromDao(dao)
                            if (recoveryBundles.isEmpty()) {
                                false
                            } else {
                                repository.publishNewSnapshotFromBundles(
                                    bundles = recoveryBundles,
                                    affectedTopicIds = affectedTopicIds,
                                    affectedPackId = parsedPack.packId,
                                    targetGenerationOverride = committedGen
                                )
                                true
                            }
                        } catch (_: Throwable) {
                            false
                        }

                        if (recovered) {
                            refreshState = SnapshotRefreshState.RECOVERED_FROM_DATABASE
                            warnings.add(
                                "Initial snapshot build encountered an error (${initialSnapshotError.message}); automatic recovery from database succeeded."
                            )
                        } else {
                            refreshState = SnapshotRefreshState.FAILED_PREVIOUS_SNAPSHOT_RETAINED
                            val snapshotMs = (System.nanoTime() - snapshotStartNanos) / 1_000_000L
                            val totalMs = (System.nanoTime() - totalStartNanos) / 1_000_000L
                            return@withRepositoryMutationLock KnowledgePackImportResult(
                                status = KnowledgePackImportStatus.SNAPSHOT_REFRESH_FAILURE,
                                packId = parsedPack.packId,
                                packVersion = parsedPack.packVersion,
                                contentVersion = parsedPack.contentVersion,
                                importedTopicCount = if (isUpdatingExistingPack) 0 else parsedPack.topics.size,
                                updatedTopicCount = if (isUpdatingExistingPack) parsedPack.topics.size else 0,
                                activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                                committedGeneration = repository.committedGeneration,
                                publishedGeneration = repository.publishedGeneration,
                                databaseCommitted = true,
                                rawBytesSha256 = rawSha256,
                                logicalContentSha256 = logicalSha256,
                                snapshotRefreshState = refreshState,
                                errors = listOf(
                                    "Database transaction committed, but snapshot refresh and automatic recovery both failed: ${initialSnapshotError.message}. Previous valid snapshot was retained."
                                ),
                                metrics = KnowledgePackImportMetrics(
                                    parseTimeMs = parseTimeMs,
                                    validationTimeMs = validationTimeMs,
                                    storageTransactionTimeMs = storageTimeMs,
                                    snapshotRebuildTimeMs = snapshotMs,
                                    totalImportTimeMs = totalMs,
                                    inputByteCount = parsedPack.rawByteSize,
                                    detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                                )
                            )
                        }
                    }

                    val snapshotRebuildTimeMs = (System.nanoTime() - snapshotStartNanos) / 1_000_000L
                    val totalImportTimeMs = (System.nanoTime() - totalStartNanos) / 1_000_000L

                    val finalStatus = if (isUpdatingExistingPack) {
                        KnowledgePackImportStatus.SUCCESS_UPDATED
                    } else {
                        KnowledgePackImportStatus.SUCCESS_IMPORTED
                    }

                    KnowledgePackImportResult(
                        status = finalStatus,
                        packId = parsedPack.packId,
                        packVersion = parsedPack.packVersion,
                        contentVersion = parsedPack.contentVersion,
                        importedTopicCount = if (isUpdatingExistingPack) 0 else parsedPack.topics.size,
                        updatedTopicCount = if (isUpdatingExistingPack) parsedPack.topics.size else 0,
                        skippedTopicCount = 0,
                        rejectedTopicCount = 0,
                        activeWinnerCountInSnapshot = repository.currentSnapshot.topicCount,
                        committedGeneration = repository.committedGeneration,
                        publishedGeneration = repository.publishedGeneration,
                        databaseCommitted = true,
                        rawBytesSha256 = rawSha256,
                        logicalContentSha256 = logicalSha256,
                        snapshotRefreshState = refreshState,
                        warnings = warnings,
                        metrics = KnowledgePackImportMetrics(
                            parseTimeMs = parseTimeMs,
                            validationTimeMs = validationTimeMs,
                            storageTransactionTimeMs = storageTimeMs,
                            snapshotRebuildTimeMs = snapshotRebuildTimeMs,
                            totalImportTimeMs = totalImportTimeMs,
                            inputByteCount = parsedPack.rawByteSize,
                            detailCacheSizeAfterImport = repository.cache.snapshotStats().detailStats.entryCount
                        )
                    )
                }
            }
        }
    }

    internal fun verifyChecksums(
        rawSha256: String,
        logicalSha256: String,
        expectedSha256: String?,
        embeddedContentSha256: String?
    ): List<String> {
        val errors = mutableListOf<String>()

        if (!expectedSha256.isNullOrBlank()) {
            val cleanExpected = expectedSha256.trim()
            if (!KnowledgePackSpecificationV1.SHA256_HEX_REGEX.matches(cleanExpected)) {
                errors.add("Provided expectedSha256 '$cleanExpected' is not a valid 64-character hex SHA-256 digest.")
            } else if (!cleanExpected.equals(rawSha256, ignoreCase = true) &&
                !cleanExpected.equals(logicalSha256, ignoreCase = true)
            ) {
                errors.add(
                    "SHA-256 mismatch against expectedSha256: expected='$cleanExpected', actualRaw='$rawSha256', actualLogical='$logicalSha256'."
                )
            }
        }

        if (!embeddedContentSha256.isNullOrBlank()) {
            val cleanEmbedded = embeddedContentSha256.trim()
            if (!KnowledgePackSpecificationV1.SHA256_HEX_REGEX.matches(cleanEmbedded)) {
                errors.add("Embedded contentSha256 '$cleanEmbedded' is not a valid 64-character hex SHA-256 digest.")
            } else if (!cleanEmbedded.equals(logicalSha256, ignoreCase = true) &&
                !cleanEmbedded.equals(rawSha256, ignoreCase = true)
            ) {
                errors.add(
                    "Embedded contentSha256 mismatch: embedded='$cleanEmbedded', actualLogical='$logicalSha256'."
                )
            }
        }

        return errors
    }

    internal fun checkCoreProtection(pack: ParsedKnowledgePackV1): List<String> {
        val errors = mutableListOf<String>()
        if (pack.packId == SeedKnowledgeCatalog.CORE_SEED_PACK_ID) {
            errors.add("Cannot import or overwrite reserved core packId '${SeedKnowledgeCatalog.CORE_SEED_PACK_ID}'.")
        }
        if (pack.sourceType == SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE) {
            errors.add("External JSON pack '${pack.packId}' cannot use reserved sourceType '${SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE}'.")
        }
        for (topic in pack.topics) {
            if (topic.topicId in SeedKnowledgeCatalog.PROTECTED_CORE_TOPIC_IDS) {
                errors.add(
                    "External pack '${pack.packId}' attempted to define or overwrite protected core seed topic '${topic.topicId}'."
                )
            }
        }
        return errors
    }

    internal fun checkIntraPackDuplicateTopics(pack: ParsedKnowledgePackV1): List<String> {
        val errors = mutableListOf<String>()
        val seenTopicIds = HashSet<String>()
        for (topic in pack.topics) {
            if (!seenTopicIds.add(topic.topicId)) {
                errors.add("Duplicate topicId '${topic.topicId}' found within pack '${pack.packId}'.")
            }
        }
        return errors
    }

    internal fun validateParsedPackSemantics(pack: ParsedKnowledgePackV1): List<String> {
        val errors = mutableListOf<String>()

        if (!KnowledgePackSpecificationV1.IDENTIFIER_REGEX.matches(pack.packId)) {
            errors.add("Invalid packId '${pack.packId}'. Must match ${KnowledgePackSpecificationV1.IDENTIFIER_REGEX.pattern}.")
        }
        if (pack.packName.isBlank()) {
            errors.add("packName must not be blank.")
        }
        if (pack.packVersion < 1) {
            errors.add("packVersion (${pack.packVersion}) must be >= 1.")
        }
        if (pack.contentVersion < 1) {
            errors.add("contentVersion (${pack.contentVersion}) must be >= 1.")
        }
        if (pack.sourceType !in KnowledgePackSpecificationV1.ALLOWED_EXTERNAL_SOURCE_TYPES) {
            errors.add(
                "Unsupported external sourceType '${pack.sourceType}'. Allowed values: ${KnowledgePackSpecificationV1.ALLOWED_EXTERNAL_SOURCE_TYPES}."
            )
        }
        if (pack.packPriority < 0) {
            errors.add("packPriority (${pack.packPriority}) must be >= 0.")
        }
        if (pack.language !in KnowledgePackSpecificationV1.ALLOWED_LANGUAGES) {
            errors.add("Unsupported language '${pack.language}'. Allowed values: ${KnowledgePackSpecificationV1.ALLOWED_LANGUAGES}.")
        }
        if (pack.createdAt <= 0L) {
            errors.add("createdAt (${pack.createdAt}) must be > 0.")
        }
        if (pack.updatedAt < pack.createdAt) {
            errors.add("updatedAt (${pack.updatedAt}) must be >= createdAt (${pack.createdAt}).")
        }
        if (pack.topics.isEmpty()) {
            errors.add("Knowledge pack '${pack.packId}' must contain at least 1 topic.")
        }

        val seenOrderIndices = HashSet<Int>()
        for (topic in pack.topics) {
            if (!KnowledgePackSpecificationV1.IDENTIFIER_REGEX.matches(topic.topicId)) {
                errors.add("Invalid topicId '${topic.topicId}'.")
            }
            if (topic.title.isBlank() || topic.arabicTitle.isBlank() || topic.englishTitle.isBlank()) {
                errors.add("Topic '${topic.topicId}' must have non-blank title/arabicTitle/englishTitle.")
            }
            if (topic.category.isBlank()) {
                errors.add("Topic '${topic.topicId}' must have a non-blank category.")
            }
            if (topic.topicOrderIndex < 0) {
                errors.add("Topic '${topic.topicId}' topicOrderIndex (${topic.topicOrderIndex}) must be >= 0.")
            }
            if (!seenOrderIndices.add(topic.topicOrderIndex)) {
                errors.add("Duplicate topicOrderIndex=${topic.topicOrderIndex} in pack '${pack.packId}'.")
            }
            if (topic.priority < 0) {
                errors.add("Topic '${topic.topicId}' priority (${topic.priority}) must be >= 0.")
            }
            if (topic.contentVersion < 1) {
                errors.add("Topic '${topic.topicId}' contentVersion (${topic.contentVersion}) must be >= 1.")
            }
            if (topic.keywords.isEmpty()) {
                errors.add("Topic '${topic.topicId}' must have at least 1 keyword.")
            }
            if (topic.suggestedFollowUps.isEmpty()) {
                errors.add("Topic '${topic.topicId}' must have at least 1 suggestedFollowUp.")
            }

            val seenRelaxedKeywords = HashSet<String>()
            for (rawKw in topic.keywords) {
                val relaxed = LocalKnowledgeBase.normalizeArabic(rawKw)
                if (relaxed.isBlank()) {
                    errors.add("Topic '${topic.topicId}' contains keyword '$rawKw' that normalizes to blank.")
                } else {
                    if (!seenRelaxedKeywords.add(relaxed)) {
                        errors.add("Topic '${topic.topicId}' contains duplicate normalized keyword '$rawKw' ('$relaxed').")
                    }
                    val tokenCount = relaxed.split(" ").count { it.isNotBlank() }
                    if (tokenCount > limits.maxTokensPerKeyword) {
                        errors.add(
                            "Keyword '$rawKw' in topic '${topic.topicId}' has $tokenCount tokens, exceeding max (${limits.maxTokensPerKeyword})."
                        )
                    }
                }
            }

            val seenBranchIds = HashSet<String>()
            val seenBranchPriorities = HashSet<Int>()
            for (branch in topic.followUpBranches) {
                if (!KnowledgePackSpecificationV1.IDENTIFIER_REGEX.matches(branch.branchId)) {
                    errors.add("Invalid branchId '${branch.branchId}' in topic '${topic.topicId}'.")
                }
                if (!seenBranchIds.add(branch.branchId)) {
                    errors.add("Duplicate branchId '${branch.branchId}' in topic '${topic.topicId}'.")
                }
                if (branch.priorityOrder < 1) {
                    errors.add("Branch '${branch.branchId}' priorityOrder (${branch.priorityOrder}) must be >= 1.")
                }
                if (!seenBranchPriorities.add(branch.priorityOrder)) {
                    errors.add("Duplicate branch priorityOrder=${branch.priorityOrder} in topic '${topic.topicId}'.")
                }
                if (branch.matchMode !in KnowledgePackSpecificationV1.ALLOWED_MATCH_MODES) {
                    errors.add("Invalid matchMode '${branch.matchMode}' in branch '${branch.branchId}'.")
                }
                if (!branch.isDefault && branch.matchMode != "DEFAULT_FALLBACK" && branch.triggerKeywords.isEmpty()) {
                    errors.add("Non-default branch '${branch.branchId}' in topic '${topic.topicId}' must have triggerKeywords.")
                }
            }
        }

        return errors
    }

    private suspend fun isCommittedPackReflectedInSnapshot(
        parsedPack: ParsedKnowledgePackV1,
        existingPack: KnowledgePackMetadataEntity,
        existingHeaders: List<KnowledgeTopicHeaderEntity>,
        dao: KnowledgeDao
    ): Boolean {
        if (existingPack.packPriority < 0) {
            return true
        }
        val activeHeadersInDb = runCatching {
            dao.getAllActiveTopicHeadersOrdered().associateBy { it.topicId }
        }.getOrDefault(emptyMap())
        if (activeHeadersInDb.isEmpty()) {
            return false
        }
        val snap = repository.currentSnapshot
        val topicIdsToCheck = existingHeaders.map { it.topicId }.ifEmpty {
            parsedPack.topics.map { it.topicId }
        }
        for (topicId in topicIdsToCheck) {
            val dbWinner = activeHeadersInDb[topicId] ?: continue
            val snapIdentity = snap.activeTopicIdentitiesById[topicId] ?: return false
            val snapTopic = snap.topicsById[topicId] ?: return false
            if (snapIdentity.packId != dbWinner.packId ||
                snapIdentity.contentVersion != dbWinner.contentVersion ||
                snapIdentity.packPriority != dbWinner.packPriority ||
                snapTopic.arabicSummary != dbWinner.arabicSummary ||
                snapTopic.englishSummary != dbWinner.englishSummary
            ) {
                return false
            }
        }
        return true
    }

    internal data class ReferenceValidationOutcome(
        val effectiveActiveTopicIdsAfterImport: Set<String>,
        val errors: List<String>
    )

    internal suspend fun validateReferenceIntegrity(
        parsedPack: ParsedKnowledgePackV1,
        existingHeadersForSamePack: List<KnowledgeTopicHeaderEntity>,
        dao: KnowledgeDao
    ): ReferenceValidationOutcome {
        val errors = mutableListOf<String>()
        val incomingTopicIds = parsedPack.topics.map { it.topicId }.toSet()
        val currentActiveTopicIds = runCatching {
            dao.getAllActiveTopicHeadersOrdered().map { it.topicId }.toSet()
        }.getOrDefault(emptySet()).ifEmpty {
            repository.currentSnapshot.topicsById.keys
        }

        // Identify topics that belonged to an earlier version of this same pack and are being removed
        val previousPackTopicIds = existingHeadersForSamePack.map { it.topicId }.toSet()
        val removedFromThisPack = previousPackTopicIds - incomingTopicIds

        // A topic removed from this pack ceases to be active ONLY if no other active pack (packPriority >= 0) provides it
        val removedWithoutFallback = HashSet<String>()
        for (removedId in removedFromThisPack) {
            val alternativeActiveExists = dao.getAllTopicVersionsForTopicId(removedId).any { header ->
                header.packId != parsedPack.packId && header.packPriority >= 0
            }
            if (!alternativeActiveExists) {
                removedWithoutFallback.add(removedId)
            }
        }

        // Inactive-only topic IDs in the database (belonging exclusively to deactivated packs)
        val inactiveOnlyTopicIds = runCatching {
            dao.getInactiveOnlyTopicIds().toSet() - incomingTopicIds
        }.getOrDefault(emptySet())

        val effectiveActiveTopicIdsAfterImport =
            (currentActiveTopicIds - removedWithoutFallback) + incomingTopicIds

        // 1. Validate every topic's outgoing referenceTopicIds inside the incoming pack
        for (topic in parsedPack.topics) {
            val seenRefs = HashSet<String>()
            for (refId in topic.referenceTopicIds) {
                val cleanRef = refId.trim()
                if (cleanRef.isEmpty() || !KnowledgePackSpecificationV1.IDENTIFIER_REGEX.matches(cleanRef)) {
                    errors.add(
                        "Topic '${topic.topicId}' has malformed reference topicId '$refId'."
                    )
                    continue
                }
                if (cleanRef == topic.topicId) {
                    errors.add(
                        "Self-reference is not allowed: topic '${topic.topicId}' references itself."
                    )
                }
                if (!seenRefs.add(cleanRef)) {
                    errors.add(
                        "Duplicate reference topicId '$cleanRef' inside topic '${topic.topicId}'."
                    )
                }
                if (cleanRef in inactiveOnlyTopicIds) {
                    errors.add(
                        "Topic '${topic.topicId}' references inactive/deactivated topic '$cleanRef'."
                    )
                } else if (cleanRef in removedWithoutFallback) {
                    errors.add(
                        "Topic '${topic.topicId}' references topic '$cleanRef' which is being removed by this pack update."
                    )
                } else if (cleanRef !in effectiveActiveTopicIdsAfterImport) {
                    errors.add(
                        "Topic '${topic.topicId}' references unknown/non-existent topic '$cleanRef'."
                    )
                }
            }
        }

        // 2. Ensure removing topics in a pack update does NOT break references held by other active topics
        if (removedWithoutFallback.isNotEmpty()) {
            val activeProjections = runCatching {
                dao.getActiveTopicRelatedRefsProjections()
            }.getOrDefault(emptyList())

            for (proj in activeProjections) {
                if (proj.packId == parsedPack.packId) continue
                val refs = converters.toStringList(proj.relatedTopicIdsJson)
                for (ref in refs) {
                    if (ref in removedWithoutFallback) {
                        errors.add(
                            "Updating pack '${parsedPack.packId}' would remove topic '$ref', breaking an active reference from topic '${proj.topicId}' (in pack '${proj.packId}')."
                        )
                    }
                }
            }
        }

        return ReferenceValidationOutcome(
            effectiveActiveTopicIdsAfterImport = effectiveActiveTopicIdsAfterImport,
            errors = errors
        )
    }

    private fun classifyValidatorIssues(issues: List<ValidationIssue>): KnowledgePackImportStatus {
        return when {
            issues.any {
                it.message.contains("core_seed_pack", ignoreCase = true) ||
                    it.message.contains("protected core", ignoreCase = true) ||
                    it.message.contains("SEED_BUILTIN", ignoreCase = true)
            } -> KnowledgePackImportStatus.CORE_PROTECTION_VIOLATION

            issues.any {
                it.category.name == "MISSING_REFERENCE" ||
                    it.field.contains("relatedTopicIds", ignoreCase = true)
            } -> KnowledgePackImportStatus.REFERENCE_INVALID

            issues.any {
                it.category.name == "DUPLICATE_DATA" && it.field == "topicId"
            } -> KnowledgePackImportStatus.DUPLICATE_CONFLICT

            else -> KnowledgePackImportStatus.VALIDATION_FAILED
        }
    }

    internal fun buildPackMetadataEntity(
        parsedPack: ParsedKnowledgePackV1,
        logicalSha256: String
    ): KnowledgePackMetadataEntity {
        return KnowledgePackMetadataEntity(
            packId = parsedPack.packId,
            sourceType = parsedPack.sourceType,
            packVersion = parsedPack.packVersion,
            schemaVersion = parsedPack.schemaVersion,
            sha256Checksum = logicalSha256,
            packPriority = parsedPack.packPriority,
            importedAt = parsedPack.updatedAt,
            topicCount = parsedPack.topics.size
        )
    }

    internal fun buildTopicStorageBundles(
        parsedPack: ParsedKnowledgePackV1,
        rawSha256: String,
        logicalSha256: String
    ): List<TopicStorageBundle> {
        return parsedPack.topics.map { topic ->
            val followUpsJson = converters.fromActionableOptionList(
                topic.suggestedFollowUps.map { label ->
                    ActionableOption(title = label, queryToExecute = label)
                }
            )

            // Combine packPriority and topic-level priority offset if topic defines custom priority,
            // while ensuring packPriority on header matches pack.packPriority as required by KnowledgeValidator.
            val header = KnowledgeTopicHeaderEntity(
                topicId = topic.topicId,
                packId = parsedPack.packId,
                topicOrderIndex = topic.topicOrderIndex,
                arabicSummary = topic.arabicSummary,
                englishSummary = topic.englishSummary,
                suggestedFollowUpsJson = followUpsJson,
                sourceType = parsedPack.sourceType,
                packPriority = parsedPack.packPriority,
                contentVersion = topic.contentVersion,
                isCoreTopic = false,
                isActiveWinner = false
            )

            val keywordsWithTokens = topic.keywords.mapIndexed { kwOrder, rawKeyword ->
                buildKeywordWithTokensBundle(
                    topicId = topic.topicId,
                    packId = parsedPack.packId,
                    keywordOrder = kwOrder,
                    rawKeyword = rawKeyword
                )
            }

            val detailEntity = topic.detail?.let { det ->
                KnowledgeTopicDetailEntity(
                    topicId = topic.topicId,
                    packId = parsedPack.packId,
                    detailedArabic = det.detailedArabic,
                    detailedEnglish = det.detailedEnglish,
                    relatedTopicIdsJson = converters.fromStringList(det.relatedTopicIds),
                    examplesJson = converters.fromStringList(det.examples),
                    lastReviewed = det.lastReviewed,
                    additionalContentJson = serializeAdditionalMetadataJson(
                        parsedPack = parsedPack,
                        topic = topic,
                        rawSha256 = rawSha256,
                        logicalSha256 = logicalSha256
                    )
                )
            }

            val branchEntities = topic.followUpBranches
                .sortedBy { it.priorityOrder }
                .map { branch ->
                    val optionsJson = converters.fromActionableOptionList(
                        branch.actionableOptions.map { opt ->
                            ActionableOption(
                                title = opt.labelArabic,
                                queryToExecute = opt.queryText
                            )
                        }
                    )
                    KnowledgeFollowUpBranchEntity(
                        branchId = branch.branchId,
                        topicId = topic.topicId,
                        packId = parsedPack.packId,
                        priorityOrder = branch.priorityOrder,
                        matchMode = branch.matchMode,
                        triggerKeywordsJson = converters.fromStringList(branch.triggerKeywords),
                        responseArabic = branch.responseArabic,
                        responseEnglish = branch.responseEnglish,
                        actionableOptionsJson = optionsJson,
                        isDefault = branch.isDefault
                    )
                }

            TopicStorageBundle(
                header = header,
                keywordsWithTokens = keywordsWithTokens,
                detail = detailEntity,
                followUpBranches = branchEntities
            )
        }
    }

    private fun buildKeywordWithTokensBundle(
        topicId: String,
        packId: String,
        keywordOrder: Int,
        rawKeyword: String
    ): KeywordWithTokensBundle {
        val strictPhrase = LocalKnowledgeBase.normalizeArabicStrict(rawKeyword)
        val relaxedPhrase = LocalKnowledgeBase.normalizeArabic(rawKeyword)
        val strictParts = strictPhrase.split(" ").filter { it.isNotBlank() }
        val relaxedParts = relaxedPhrase.split(" ").filter { it.isNotBlank() }

        val tokenCount = relaxedParts.size
        val matchType = if (tokenCount > 1) "MULTI_WORD_PHRASE" else "SINGLE_TOKEN"
        val language = detectLanguage(rawKeyword)

        var contentTokenCount = 0
        val tokenEntities = ArrayList<KnowledgeKeywordTokenEntity>(tokenCount)

        for (pos in 0 until tokenCount) {
            val rTok = relaxedParts[pos]
            val sTok = strictParts.getOrElse(pos) { rTok }
            val rStem = LocalKnowledgeBase.stripArabicPrefixes(rTok).ifEmpty { rTok }
            val sStem = LocalKnowledgeBase.stripArabicPrefixes(sTok).ifEmpty { sTok }

            val isStopWord = rTok in SeedKnowledgeCatalog.STOP_WORDS ||
                sTok in SeedKnowledgeCatalog.STOP_WORDS
            if (!isStopWord) {
                contentTokenCount++
            }

            tokenEntities.add(
                KnowledgeKeywordTokenEntity(
                    tokenId = 0L,
                    keywordId = 0L,
                    tokenPosition = pos,
                    strictToken = sTok,
                    strictStem = sStem,
                    relaxedToken = rTok,
                    relaxedStem = rStem
                )
            )
        }

        val keywordEntity = KnowledgeKeywordEntity(
            keywordId = 0L,
            topicId = topicId,
            packId = packId,
            keywordOrder = keywordOrder,
            originalKeyword = rawKeyword,
            strictNormalizedPhrase = strictPhrase,
            relaxedNormalizedPhrase = relaxedPhrase,
            relaxedCharLength = relaxedPhrase.length,
            tokenCount = tokenCount,
            contentTokenCount = contentTokenCount,
            matchType = matchType,
            language = language
        )

        return KeywordWithTokensBundle(
            keyword = keywordEntity,
            tokens = tokenEntities
        )
    }

    private fun detectLanguage(text: String): String {
        var hasArabic = false
        var hasLatin = false
        for (ch in text) {
            if (ch in '\u0600'..'\u06FF') hasArabic = true
            if (ch in 'a'..'z' || ch in 'A'..'Z') hasLatin = true
        }
        return when {
            hasArabic && hasLatin -> "mixed"
            hasArabic -> "ar"
            else -> "en"
        }
    }

    private fun serializeAdditionalMetadataJson(
        parsedPack: ParsedKnowledgePackV1,
        topic: ParsedTopicV1,
        rawSha256: String,
        logicalSha256: String
    ): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { writer ->
            writer.beginObject()
            writer.name("packName").value(parsedPack.packName)
            writer.name("packContentVersion").value(parsedPack.contentVersion)
            writer.name("packLanguage").value(parsedPack.language)
            writer.name("packDescription").value(parsedPack.description)
            writer.name("createdAt").value(parsedPack.createdAt)
            writer.name("updatedAt").value(parsedPack.updatedAt)
            writer.name("rawBytesSha256").value(rawSha256)
            writer.name("logicalContentSha256").value(logicalSha256)
            writer.name("title").value(topic.title)
            writer.name("arabicTitle").value(topic.arabicTitle)
            writer.name("englishTitle").value(topic.englishTitle)
            writer.name("category").value(topic.category)
            writer.name("topicPriority").value(topic.priority)
            writer.endObject()
        }
        return buffer.readUtf8()
    }
}
