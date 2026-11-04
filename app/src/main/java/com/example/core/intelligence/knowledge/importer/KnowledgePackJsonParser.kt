package com.example.core.intelligence.knowledge.importer

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Result of streaming JSON parsing for a `WEYKY_KNOWLEDGE_PACK_V1` payload.
 */
sealed class KnowledgePackParseOutcome {
    data class Success(
        val pack: ParsedKnowledgePackV1,
        val parseTimeMs: Long
    ) : KnowledgePackParseOutcome()

    data class Failure(
        val status: KnowledgePackImportStatus,
        val errors: List<String>,
        val packId: String? = null,
        val packVersion: Int? = null,
        val contentVersion: Int? = null,
        val rawBytesSha256: String? = null,
        val inputByteCount: Long = 0L,
        val parseTimeMs: Long = 0L
    ) : KnowledgePackParseOutcome()
}

/**
 * Production-grade streaming JSON parser for `WEYKY_KNOWLEDGE_PACK_V1`.
 *
 * Security & Robustness Guarantees:
 * - Executes on `Dispatchers.IO`.
 * - Streams raw UTF-8 bytes through a bounded SHA-256 stream (`BoundedSha256InputStream`),
 *   computing the exact raw SHA-256 without re-serializing JSON and aborting immediately if
 *   the stream exceeds [KnowledgePackImportLimits.maxInputBytes] (`INPUT_TOO_LARGE`).
 * - Tracks exact JSON nesting depth and aborts if depth exceeds
 *   [KnowledgePackImportLimits.maxJsonNestingDepth].
 * - Rejects duplicate keys inside any JSON object (`PARSING_FAILURE`).
 * - Enforces strict JSON token types (never coerces numbers/booleans into strings or strings into numbers).
 * - Rejects unknown JSON properties (`failOnUnknown`).
 * - Enforces maximum collection counts and string character lengths during streaming.
 */
class KnowledgePackJsonParser(
    private val limits: KnowledgePackImportLimits = KnowledgePackImportLimits(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun parseFromJsonString(json: String): KnowledgePackParseOutcome =
        withContext(ioDispatcher) {
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            parseFromByteArray(bytes)
        }

    suspend fun parseFromByteArray(bytes: ByteArray): KnowledgePackParseOutcome =
        withContext(ioDispatcher) {
            if (bytes.size.toLong() > limits.maxInputBytes) {
                return@withContext KnowledgePackParseOutcome.Failure(
                    status = KnowledgePackImportStatus.INPUT_TOO_LARGE,
                    errors = listOf(
                        "Input size (${bytes.size} bytes) exceeds maximum allowed limit (${limits.maxInputBytes} bytes)."
                    ),
                    inputByteCount = bytes.size.toLong(),
                    parseTimeMs = 0L
                )
            }
            ByteArrayInputStream(bytes).use { stream ->
                parseFromInputStreamSync(stream)
            }
        }

    suspend fun parseFromInputStream(inputStream: InputStream): KnowledgePackParseOutcome =
        withContext(ioDispatcher) {
            parseFromInputStreamSync(inputStream)
        }

    private fun parseFromInputStreamSync(inputStream: InputStream): KnowledgePackParseOutcome {
        val startNanos = System.nanoTime()
        val boundedStream = BoundedSha256InputStream(inputStream, limits.maxInputBytes)
        var discoveredPackId: String? = null
        var discoveredPackVersion: Int? = null
        var discoveredContentVersion: Int? = null

        return try {
            val bufferedSource = boundedStream.source().buffer()
            if (bufferedSource.exhausted()) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                return KnowledgePackParseOutcome.Failure(
                    status = KnowledgePackImportStatus.PARSING_FAILURE,
                    errors = listOf("Knowledge pack JSON input is empty."),
                    inputByteCount = boundedStream.totalBytesRead,
                    parseTimeMs = elapsedMs
                )
            }

            val reader = JsonReader.of(bufferedSource)
            reader.isLenient = false

            val depthTracker = DepthTracker(limits.maxJsonNestingDepth)
            val parsedRoot = parseRootPackObject(
                reader = reader,
                depthTracker = depthTracker,
                onHeaderDiscovered = { id, pVer, cVer ->
                    discoveredPackId = id ?: discoveredPackId
                    discoveredPackVersion = pVer ?: discoveredPackVersion
                    discoveredContentVersion = cVer ?: discoveredContentVersion
                }
            )

            // Drain any trailing whitespace so the stream reaches EOF and updates the full SHA-256
            if (reader.peek() != JsonReader.Token.END_DOCUMENT) {
                throw StrictJsonParseException(
                    status = KnowledgePackImportStatus.PARSING_FAILURE,
                    message = "Unexpected trailing token after root JSON object at ${reader.path}: ${reader.peek()}"
                )
            }
            // Ensure remaining trailing whitespace bytes in the underlying stream are hashed
            val drainBuffer = ByteArray(1024)
            while (boundedStream.read(drainBuffer) != -1) {
                // BoundedSha256InputStream updates digest and checks byte limit
            }

            val rawSha256 = boundedStream.finishSha256Hex()
            val totalBytes = boundedStream.totalBytesRead
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

            // Verify schema compatibility after parsing root header so schema errors are clearly distinguished
            if (parsedRoot.packFormat != KnowledgePackSpecificationV1.FORMAT_V1) {
                return KnowledgePackParseOutcome.Failure(
                    status = KnowledgePackImportStatus.SCHEMA_UNSUPPORTED,
                    errors = listOf(
                        "Unsupported packFormat '${parsedRoot.packFormat}'. Expected '${KnowledgePackSpecificationV1.FORMAT_V1}'."
                    ),
                    packId = parsedRoot.packId,
                    packVersion = parsedRoot.packVersion,
                    contentVersion = parsedRoot.contentVersion,
                    rawBytesSha256 = rawSha256,
                    inputByteCount = totalBytes,
                    parseTimeMs = elapsedMs
                )
            }

            if (parsedRoot.schemaVersion != KnowledgePackSpecificationV1.SUPPORTED_SCHEMA_VERSION) {
                return KnowledgePackParseOutcome.Failure(
                    status = KnowledgePackImportStatus.SCHEMA_UNSUPPORTED,
                    errors = listOf(
                        "Unsupported schemaVersion=${parsedRoot.schemaVersion}. Supported version is ${KnowledgePackSpecificationV1.SUPPORTED_SCHEMA_VERSION}."
                    ),
                    packId = parsedRoot.packId,
                    packVersion = parsedRoot.packVersion,
                    contentVersion = parsedRoot.contentVersion,
                    rawBytesSha256 = rawSha256,
                    inputByteCount = totalBytes,
                    parseTimeMs = elapsedMs
                )
            }

            KnowledgePackParseOutcome.Success(
                pack = parsedRoot.copy(
                    rawBytesSha256 = rawSha256,
                    rawByteSize = totalBytes
                ),
                parseTimeMs = elapsedMs
            )
        } catch (tooLarge: InputTooLargeException) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            KnowledgePackParseOutcome.Failure(
                status = KnowledgePackImportStatus.INPUT_TOO_LARGE,
                errors = listOf(tooLarge.message ?: "Input exceeded maximum byte limit."),
                packId = discoveredPackId,
                packVersion = discoveredPackVersion,
                contentVersion = discoveredContentVersion,
                inputByteCount = boundedStream.totalBytesRead,
                parseTimeMs = elapsedMs
            )
        } catch (strictEx: StrictJsonParseException) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            KnowledgePackParseOutcome.Failure(
                status = strictEx.status,
                errors = listOf(strictEx.message ?: "JSON parsing error."),
                packId = discoveredPackId,
                packVersion = discoveredPackVersion,
                contentVersion = discoveredContentVersion,
                inputByteCount = boundedStream.totalBytesRead,
                parseTimeMs = elapsedMs
            )
        } catch (encodingEx: JsonEncodingException) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            KnowledgePackParseOutcome.Failure(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                errors = listOf("Malformed JSON syntax: ${encodingEx.message}"),
                packId = discoveredPackId,
                packVersion = discoveredPackVersion,
                contentVersion = discoveredContentVersion,
                inputByteCount = boundedStream.totalBytesRead,
                parseTimeMs = elapsedMs
            )
        } catch (dataEx: JsonDataException) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            KnowledgePackParseOutcome.Failure(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                errors = listOf("JSON data error: ${dataEx.message}"),
                packId = discoveredPackId,
                packVersion = discoveredPackVersion,
                contentVersion = discoveredContentVersion,
                inputByteCount = boundedStream.totalBytesRead,
                parseTimeMs = elapsedMs
            )
        } catch (eofEx: EOFException) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            KnowledgePackParseOutcome.Failure(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                errors = listOf("Unexpected end of JSON input: ${eofEx.message}"),
                packId = discoveredPackId,
                packVersion = discoveredPackVersion,
                contentVersion = discoveredContentVersion,
                inputByteCount = boundedStream.totalBytesRead,
                parseTimeMs = elapsedMs
            )
        } catch (t: Throwable) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            KnowledgePackParseOutcome.Failure(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                errors = listOf("Unexpected error while parsing JSON: ${t.message ?: t::class.simpleName}"),
                packId = discoveredPackId,
                packVersion = discoveredPackVersion,
                contentVersion = discoveredContentVersion,
                inputByteCount = boundedStream.totalBytesRead,
                parseTimeMs = elapsedMs
            )
        }
    }

    private fun parseRootPackObject(
        reader: JsonReader,
        depthTracker: DepthTracker,
        onHeaderDiscovered: (String?, Int?, Int?) -> Unit
    ): ParsedKnowledgePackV1 {
        expectObjectBegin(reader, depthTracker, "root")
        val seenKeys = HashSet<String>()

        var packFormat: String? = null
        var schemaVersion: Int? = null
        var packId: String? = null
        var packName: String? = null
        var packVersion: Int? = null
        var contentVersion: Int? = null
        var sourceType: String? = null
        var packPriority: Int? = null
        var description: String? = null
        var language: String? = null
        var createdAt: Long? = null
        var updatedAt: Long? = null
        var contentSha256: String? = null
        var topics: List<ParsedTopicV1>? = null

        while (reader.hasNext()) {
            val key = nextUniqueName(reader, seenKeys, "root")
            when (key) {
                "packFormat" -> {
                    packFormat = readRequiredString(reader, "packFormat", limits.maxIdentifierLength)
                }
                "schemaVersion" -> {
                    schemaVersion = readStrictInt(reader, "schemaVersion")
                }
                "packId" -> {
                    packId = readRequiredString(reader, "packId", limits.maxIdentifierLength)
                    onHeaderDiscovered(packId, packVersion, contentVersion)
                }
                "packName" -> {
                    packName = readRequiredString(reader, "packName", limits.maxTitleLength)
                }
                "packVersion" -> {
                    packVersion = readStrictInt(reader, "packVersion")
                    onHeaderDiscovered(packId, packVersion, contentVersion)
                }
                "contentVersion" -> {
                    contentVersion = readStrictInt(reader, "contentVersion")
                    onHeaderDiscovered(packId, packVersion, contentVersion)
                }
                "sourceType" -> {
                    sourceType = readRequiredString(reader, "sourceType", limits.maxIdentifierLength)
                }
                "packPriority", "priority" -> {
                    packPriority = readStrictInt(reader, key)
                }
                "description" -> {
                    description = readAllowedBlankString(reader, "description", limits.maxDescriptionLength)
                }
                "language" -> {
                    language = readRequiredString(reader, "language", limits.maxIdentifierLength)
                }
                "createdAt" -> {
                    createdAt = readStrictLong(reader, "createdAt")
                }
                "updatedAt" -> {
                    updatedAt = readStrictLong(reader, "updatedAt")
                }
                "contentSha256", "sha256Checksum" -> {
                    contentSha256 = readNullableString(reader, key, 128)
                }
                "topics" -> {
                    topics = parseTopicsArray(reader, depthTracker)
                }
                else -> {
                    throw StrictJsonParseException(
                        status = KnowledgePackImportStatus.PARSING_FAILURE,
                        message = "Unknown JSON property '$key' at ${reader.path}."
                    )
                }
            }
        }
        expectObjectEnd(reader, depthTracker)

        val resolvedSchemaVersion = schemaVersion ?: throw missingField("schemaVersion")
        val resolvedPackFormat = packFormat ?: KnowledgePackSpecificationV1.FORMAT_V1

        // Check schemaVersion early so unsupported schemas even with missing V2+ fields report SCHEMA_UNSUPPORTED
        if (resolvedPackFormat != KnowledgePackSpecificationV1.FORMAT_V1) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.SCHEMA_UNSUPPORTED,
                message = "Unsupported packFormat '$resolvedPackFormat'. Expected '${KnowledgePackSpecificationV1.FORMAT_V1}'."
            )
        }
        if (resolvedSchemaVersion != KnowledgePackSpecificationV1.SUPPORTED_SCHEMA_VERSION) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.SCHEMA_UNSUPPORTED,
                message = "Unsupported schemaVersion=$resolvedSchemaVersion. Supported version is ${KnowledgePackSpecificationV1.SUPPORTED_SCHEMA_VERSION}."
            )
        }

        val resolvedPackId = packId ?: throw missingField("packId")
        val resolvedPackVersion = packVersion ?: throw missingField("packVersion")
        val resolvedContentVersion = contentVersion ?: resolvedPackVersion
        val resolvedPackName = packName ?: resolvedPackId
        val resolvedSourceType = sourceType ?: KnowledgePackSpecificationV1.SOURCE_TYPE_LOCAL_UPDATE
        val resolvedPriority = packPriority ?: throw missingField("packPriority")
        val resolvedTopics = topics ?: throw missingField("topics")
        val resolvedCreatedAt = createdAt ?: 1_700_000_000_000L
        val resolvedUpdatedAt = updatedAt ?: resolvedCreatedAt

        return ParsedKnowledgePackV1(
            packFormat = resolvedPackFormat,
            schemaVersion = resolvedSchemaVersion,
            packId = resolvedPackId,
            packName = resolvedPackName,
            packVersion = resolvedPackVersion,
            contentVersion = resolvedContentVersion,
            sourceType = resolvedSourceType,
            packPriority = resolvedPriority,
            description = description ?: "",
            language = language ?: "bilingual",
            createdAt = resolvedCreatedAt,
            updatedAt = resolvedUpdatedAt,
            contentSha256 = contentSha256,
            topics = resolvedTopics,
            rawBytesSha256 = "",
            rawByteSize = 0L
        )
    }

    private fun parseTopicsArray(
        reader: JsonReader,
        depthTracker: DepthTracker
    ): List<ParsedTopicV1> {
        expectArrayBegin(reader, depthTracker, "topics")
        val topics = ArrayList<ParsedTopicV1>()
        var index = 0
        while (reader.hasNext()) {
            if (index >= limits.maxTopicsPerPack) {
                throw StrictJsonParseException(
                    status = KnowledgePackImportStatus.VALIDATION_FAILED,
                    message = "Pack exceeds maximum allowed topics (${limits.maxTopicsPerPack})."
                )
            }
            topics.add(parseTopicObject(reader, depthTracker, index))
            index++
        }
        expectArrayEnd(reader, depthTracker)
        return topics
    }

    private fun parseTopicObject(
        reader: JsonReader,
        depthTracker: DepthTracker,
        defaultOrderIndex: Int
    ): ParsedTopicV1 {
        expectObjectBegin(reader, depthTracker, "topic[$defaultOrderIndex]")
        val seenKeys = HashSet<String>()

        var topicId: String? = null
        var title: String? = null
        var arabicTitle: String? = null
        var englishTitle: String? = null
        var category: String? = null
        var topicOrderIndex: Int? = null
        var priority: Int? = null
        var contentVersion: Int? = null
        var arabicSummary: String? = null
        var englishSummary: String? = null
        var keywords: List<String>? = null
        var suggestedFollowUps: List<String>? = null
        var referenceTopicIds: List<String>? = null
        var detail: ParsedTopicDetailV1? = null
        var followUpBranches: List<ParsedFollowUpBranchV1>? = null

        while (reader.hasNext()) {
            val key = nextUniqueName(reader, seenKeys, "topic[$defaultOrderIndex]")
            when (key) {
                "topicId", "id" -> {
                    topicId = readRequiredString(reader, key, limits.maxIdentifierLength)
                }
                "title" -> {
                    title = readRequiredString(reader, "title", limits.maxTitleLength)
                }
                "arabicTitle" -> {
                    arabicTitle = readRequiredString(reader, "arabicTitle", limits.maxTitleLength)
                }
                "englishTitle" -> {
                    englishTitle = readRequiredString(reader, "englishTitle", limits.maxTitleLength)
                }
                "category" -> {
                    category = readRequiredString(reader, "category", limits.maxCategoryLength)
                }
                "topicOrderIndex" -> {
                    topicOrderIndex = readStrictInt(reader, "topicOrderIndex")
                }
                "priority" -> {
                    priority = readStrictInt(reader, "priority")
                }
                "contentVersion" -> {
                    contentVersion = readStrictInt(reader, "contentVersion")
                }
                "arabicSummary" -> {
                    arabicSummary = readRequiredString(reader, "arabicSummary", limits.maxSummaryLength)
                }
                "englishSummary" -> {
                    englishSummary = readRequiredString(reader, "englishSummary", limits.maxSummaryLength)
                }
                "keywords" -> {
                    keywords = parseStringList(
                        reader = reader,
                        depthTracker = depthTracker,
                        fieldName = "keywords",
                        maxItems = limits.maxKeywordsPerTopic,
                        maxItemLength = limits.maxKeywordLength
                    )
                }
                "suggestedFollowUps" -> {
                    suggestedFollowUps = parseStringList(
                        reader = reader,
                        depthTracker = depthTracker,
                        fieldName = "suggestedFollowUps",
                        maxItems = limits.maxSuggestedFollowUpsPerTopic,
                        maxItemLength = limits.maxTitleLength
                    )
                }
                "referenceTopicIds", "relatedTopicIds" -> {
                    referenceTopicIds = parseStringList(
                        reader = reader,
                        depthTracker = depthTracker,
                        fieldName = key,
                        maxItems = limits.maxReferenceTopicsPerTopic,
                        maxItemLength = limits.maxIdentifierLength
                    )
                }
                "detail" -> {
                    detail = if (reader.peek() == JsonReader.Token.NULL) {
                        reader.nextNull<Unit>()
                        null
                    } else {
                        parseTopicDetailObject(reader, depthTracker)
                    }
                }
                "followUpBranches" -> {
                    followUpBranches = if (reader.peek() == JsonReader.Token.NULL) {
                        reader.nextNull<Unit>()
                        emptyList()
                    } else {
                        parseFollowUpBranchesArray(reader, depthTracker)
                    }
                }
                else -> {
                    throw StrictJsonParseException(
                        status = KnowledgePackImportStatus.PARSING_FAILURE,
                        message = "Unknown JSON property '$key' inside topic at ${reader.path}."
                    )
                }
            }
        }
        expectObjectEnd(reader, depthTracker)

        val resolvedTopicId = topicId ?: throw missingField("topicId (at topic[$defaultOrderIndex])")
        val resolvedArabicSummary = arabicSummary ?: throw missingField("arabicSummary (in topic '$resolvedTopicId')")
        val resolvedEnglishSummary = englishSummary ?: throw missingField("englishSummary (in topic '$resolvedTopicId')")
        val resolvedKeywords = keywords ?: throw missingField("keywords (in topic '$resolvedTopicId')")
        val resolvedFollowUps = suggestedFollowUps ?: throw missingField("suggestedFollowUps (in topic '$resolvedTopicId')")

        val resolvedTitle = title ?: englishTitle ?: arabicTitle ?: resolvedTopicId
        val resolvedArabicTitle = arabicTitle ?: title ?: resolvedTopicId
        val resolvedEnglishTitle = englishTitle ?: title ?: resolvedTopicId
        val resolvedCategory = category ?: "general"

        // Merge topic-level referenceTopicIds with detail.relatedTopicIds:
        // If either list contains internal duplicates, preserve all entries from both lists so
        // reference validation detects both duplicates and any non-existent references;
        // otherwise combine them without duplicating entries that appear in both topic and detail.
        val hasTopicLevelDuplicates = referenceTopicIds != null &&
            referenceTopicIds.size != referenceTopicIds.distinct().size
        val hasDetailLevelDuplicates = detail != null &&
            detail.relatedTopicIds.size != detail.relatedTopicIds.distinct().size

        val mergedReferenceIds = when {
            hasTopicLevelDuplicates || hasDetailLevelDuplicates -> {
                referenceTopicIds.orEmpty() + detail?.relatedTopicIds.orEmpty()
            }
            referenceTopicIds != null && detail != null && detail.relatedTopicIds.isNotEmpty() -> {
                (referenceTopicIds + detail.relatedTopicIds).distinct()
            }
            referenceTopicIds != null -> referenceTopicIds
            detail != null -> detail.relatedTopicIds
            else -> emptyList()
        }

        val normalizedDetail = when {
            detail != null -> detail.copy(relatedTopicIds = mergedReferenceIds)
            mergedReferenceIds.isNotEmpty() -> ParsedTopicDetailV1(
                detailedArabic = resolvedArabicSummary,
                detailedEnglish = resolvedEnglishSummary,
                relatedTopicIds = mergedReferenceIds,
                examples = emptyList(),
                lastReviewed = 1_700_000_000_000L
            )
            else -> null
        }

        return ParsedTopicV1(
            topicId = resolvedTopicId,
            title = resolvedTitle,
            arabicTitle = resolvedArabicTitle,
            englishTitle = resolvedEnglishTitle,
            category = resolvedCategory,
            topicOrderIndex = topicOrderIndex ?: defaultOrderIndex,
            priority = priority ?: 100,
            contentVersion = contentVersion ?: 1,
            arabicSummary = resolvedArabicSummary,
            englishSummary = resolvedEnglishSummary,
            keywords = resolvedKeywords,
            suggestedFollowUps = resolvedFollowUps,
            referenceTopicIds = mergedReferenceIds,
            detail = normalizedDetail,
            followUpBranches = followUpBranches ?: emptyList()
        )
    }

    private fun parseTopicDetailObject(
        reader: JsonReader,
        depthTracker: DepthTracker
    ): ParsedTopicDetailV1 {
        expectObjectBegin(reader, depthTracker, "detail")
        val seenKeys = HashSet<String>()

        var detailedArabic: String? = null
        var detailedEnglish: String? = null
        var relatedTopicIds: List<String>? = null
        var examples: List<String>? = null
        var lastReviewed: Long? = null

        while (reader.hasNext()) {
            val key = nextUniqueName(reader, seenKeys, "detail")
            when (key) {
                "detailedArabic" -> {
                    detailedArabic = readRequiredString(reader, "detailedArabic", limits.maxDetailedTextLength)
                }
                "detailedEnglish" -> {
                    detailedEnglish = readRequiredString(reader, "detailedEnglish", limits.maxDetailedTextLength)
                }
                "relatedTopicIds", "referenceTopicIds" -> {
                    relatedTopicIds = parseStringList(
                        reader = reader,
                        depthTracker = depthTracker,
                        fieldName = key,
                        maxItems = limits.maxReferenceTopicsPerTopic,
                        maxItemLength = limits.maxIdentifierLength
                    )
                }
                "examples" -> {
                    examples = parseStringList(
                        reader = reader,
                        depthTracker = depthTracker,
                        fieldName = "examples",
                        maxItems = limits.maxExamplesPerTopic,
                        maxItemLength = limits.maxSummaryLength
                    )
                }
                "lastReviewed" -> {
                    lastReviewed = readStrictLong(reader, "lastReviewed")
                }
                else -> {
                    throw StrictJsonParseException(
                        status = KnowledgePackImportStatus.PARSING_FAILURE,
                        message = "Unknown JSON property '$key' inside detail at ${reader.path}."
                    )
                }
            }
        }
        expectObjectEnd(reader, depthTracker)

        val resolvedDetailedArabic = detailedArabic ?: throw missingField("detail.detailedArabic")
        val resolvedDetailedEnglish = detailedEnglish ?: throw missingField("detail.detailedEnglish")

        return ParsedTopicDetailV1(
            detailedArabic = resolvedDetailedArabic,
            detailedEnglish = resolvedDetailedEnglish,
            relatedTopicIds = relatedTopicIds ?: emptyList(),
            examples = examples ?: emptyList(),
            lastReviewed = lastReviewed ?: 1_700_000_000_000L
        )
    }

    private fun parseFollowUpBranchesArray(
        reader: JsonReader,
        depthTracker: DepthTracker
    ): List<ParsedFollowUpBranchV1> {
        expectArrayBegin(reader, depthTracker, "followUpBranches")
        val branches = ArrayList<ParsedFollowUpBranchV1>()
        var index = 0
        while (reader.hasNext()) {
            if (index >= limits.maxFollowUpBranchesPerTopic) {
                throw StrictJsonParseException(
                    status = KnowledgePackImportStatus.VALIDATION_FAILED,
                    message = "Topic exceeds maximum allowed followUpBranches (${limits.maxFollowUpBranchesPerTopic})."
                )
            }
            branches.add(parseFollowUpBranchObject(reader, depthTracker, index))
            index++
        }
        expectArrayEnd(reader, depthTracker)
        return branches
    }

    private fun parseFollowUpBranchObject(
        reader: JsonReader,
        depthTracker: DepthTracker,
        index: Int
    ): ParsedFollowUpBranchV1 {
        expectObjectBegin(reader, depthTracker, "followUpBranches[$index]")
        val seenKeys = HashSet<String>()

        var branchId: String? = null
        var priorityOrder: Int? = null
        var matchMode: String? = null
        var triggerKeywords: List<String>? = null
        var responseArabic: String? = null
        var responseEnglish: String? = null
        var actionableOptions: List<ParsedActionableOptionV1>? = null
        var isDefault: Boolean? = null

        while (reader.hasNext()) {
            val key = nextUniqueName(reader, seenKeys, "followUpBranches[$index]")
            when (key) {
                "branchId" -> {
                    branchId = readRequiredString(reader, "branchId", limits.maxIdentifierLength)
                }
                "priorityOrder" -> {
                    priorityOrder = readStrictInt(reader, "priorityOrder")
                }
                "matchMode" -> {
                    matchMode = readRequiredString(reader, "matchMode", limits.maxIdentifierLength)
                }
                "triggerKeywords" -> {
                    triggerKeywords = parseStringList(
                        reader = reader,
                        depthTracker = depthTracker,
                        fieldName = "triggerKeywords",
                        maxItems = limits.maxTriggerKeywordsPerBranch,
                        maxItemLength = limits.maxKeywordLength
                    )
                }
                "responseArabic" -> {
                    responseArabic = readRequiredString(reader, "responseArabic", limits.maxBranchResponseLength)
                }
                "responseEnglish" -> {
                    responseEnglish = readRequiredString(reader, "responseEnglish", limits.maxBranchResponseLength)
                }
                "actionableOptions" -> {
                    actionableOptions = parseActionableOptionsArray(reader, depthTracker)
                }
                "isDefault" -> {
                    isDefault = readStrictBoolean(reader, "isDefault")
                }
                else -> {
                    throw StrictJsonParseException(
                        status = KnowledgePackImportStatus.PARSING_FAILURE,
                        message = "Unknown JSON property '$key' inside followUpBranch at ${reader.path}."
                    )
                }
            }
        }
        expectObjectEnd(reader, depthTracker)

        val resolvedBranchId = branchId ?: throw missingField("branchId")
        val resolvedMatchMode = matchMode ?: throw missingField("matchMode (in branch '$resolvedBranchId')")
        val resolvedResponseArabic = responseArabic ?: throw missingField("responseArabic (in branch '$resolvedBranchId')")
        val resolvedResponseEnglish = responseEnglish ?: throw missingField("responseEnglish (in branch '$resolvedBranchId')")
        val resolvedIsDefault = isDefault ?: (resolvedMatchMode == "DEFAULT_FALLBACK")

        return ParsedFollowUpBranchV1(
            branchId = resolvedBranchId,
            priorityOrder = priorityOrder ?: (index + 1),
            matchMode = resolvedMatchMode,
            triggerKeywords = triggerKeywords ?: emptyList(),
            responseArabic = resolvedResponseArabic,
            responseEnglish = resolvedResponseEnglish,
            actionableOptions = actionableOptions ?: emptyList(),
            isDefault = resolvedIsDefault
        )
    }

    private fun parseActionableOptionsArray(
        reader: JsonReader,
        depthTracker: DepthTracker
    ): List<ParsedActionableOptionV1> {
        expectArrayBegin(reader, depthTracker, "actionableOptions")
        val options = ArrayList<ParsedActionableOptionV1>()
        var index = 0
        while (reader.hasNext()) {
            if (index >= limits.maxActionableOptionsPerBranch) {
                throw StrictJsonParseException(
                    status = KnowledgePackImportStatus.VALIDATION_FAILED,
                    message = "Branch exceeds maximum allowed actionableOptions (${limits.maxActionableOptionsPerBranch})."
                )
            }
            expectObjectBegin(reader, depthTracker, "actionableOptions[$index]")
            val seenKeys = HashSet<String>()
            var labelArabic: String? = null
            var labelEnglish: String? = null
            var queryText: String? = null

            while (reader.hasNext()) {
                val key = nextUniqueName(reader, seenKeys, "actionableOptions[$index]")
                when (key) {
                    "labelArabic" -> labelArabic = readRequiredString(reader, "labelArabic", limits.maxTitleLength)
                    "labelEnglish" -> labelEnglish = readRequiredString(reader, "labelEnglish", limits.maxTitleLength)
                    "queryText" -> queryText = readRequiredString(reader, "queryText", limits.maxTitleLength)
                    else -> throw StrictJsonParseException(
                        status = KnowledgePackImportStatus.PARSING_FAILURE,
                        message = "Unknown JSON property '$key' in actionableOption at ${reader.path}."
                    )
                }
            }
            expectObjectEnd(reader, depthTracker)
            options.add(
                ParsedActionableOptionV1(
                    labelArabic = labelArabic ?: throw missingField("labelArabic"),
                    labelEnglish = labelEnglish ?: throw missingField("labelEnglish"),
                    queryText = queryText ?: throw missingField("queryText")
                )
            )
            index++
        }
        expectArrayEnd(reader, depthTracker)
        return options
    }

    private fun parseStringList(
        reader: JsonReader,
        depthTracker: DepthTracker,
        fieldName: String,
        maxItems: Int,
        maxItemLength: Int
    ): List<String> {
        expectArrayBegin(reader, depthTracker, fieldName)
        val items = ArrayList<String>()
        var count = 0
        while (reader.hasNext()) {
            if (count >= maxItems) {
                throw StrictJsonParseException(
                    status = KnowledgePackImportStatus.VALIDATION_FAILED,
                    message = "Array '$fieldName' at ${reader.path} exceeds maximum allowed items ($maxItems)."
                )
            }
            val item = readRequiredString(reader, "$fieldName[$count]", maxItemLength)
            items.add(item)
            count++
        }
        expectArrayEnd(reader, depthTracker)
        return items
    }

    private fun expectObjectBegin(reader: JsonReader, depthTracker: DepthTracker, contextName: String) {
        val token = reader.peek()
        if (token != JsonReader.Token.BEGIN_OBJECT) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Expected JSON object for '$contextName' at ${reader.path}, but found $token."
            )
        }
        depthTracker.enter(reader)
        reader.beginObject()
    }

    private fun expectObjectEnd(reader: JsonReader, depthTracker: DepthTracker) {
        reader.endObject()
        depthTracker.exit()
    }

    private fun expectArrayBegin(reader: JsonReader, depthTracker: DepthTracker, contextName: String) {
        val token = reader.peek()
        if (token != JsonReader.Token.BEGIN_ARRAY) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Expected JSON array for '$contextName' at ${reader.path}, but found $token."
            )
        }
        depthTracker.enter(reader)
        reader.beginArray()
    }

    private fun expectArrayEnd(reader: JsonReader, depthTracker: DepthTracker) {
        reader.endArray()
        depthTracker.exit()
    }

    private fun nextUniqueName(
        reader: JsonReader,
        seenKeys: MutableSet<String>,
        objectContext: String
    ): String {
        val name = reader.nextName()
        if (!seenKeys.add(name)) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Duplicate JSON key '$name' detected inside $objectContext at ${reader.path}."
            )
        }
        return name
    }

    private fun readRequiredString(
        reader: JsonReader,
        fieldName: String,
        maxLength: Int
    ): String {
        val token = reader.peek()
        if (token != JsonReader.Token.STRING) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Expected STRING for '$fieldName' at ${reader.path}, but found $token."
            )
        }
        val value = reader.nextString()
        if (value.isBlank()) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.VALIDATION_FAILED,
                message = "Field '$fieldName' at ${reader.path} must not be blank."
            )
        }
        if (value.length > maxLength) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.VALIDATION_FAILED,
                message = "Field '$fieldName' at ${reader.path} length (${value.length}) exceeds maximum ($maxLength)."
            )
        }
        return value
    }

    private fun readAllowedBlankString(
        reader: JsonReader,
        fieldName: String,
        maxLength: Int
    ): String {
        val token = reader.peek()
        if (token != JsonReader.Token.STRING) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Expected STRING for '$fieldName' at ${reader.path}, but found $token."
            )
        }
        val value = reader.nextString()
        if (value.length > maxLength) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.VALIDATION_FAILED,
                message = "Field '$fieldName' at ${reader.path} length (${value.length}) exceeds maximum ($maxLength)."
            )
        }
        return value
    }

    private fun readNullableString(
        reader: JsonReader,
        fieldName: String,
        maxLength: Int
    ): String? {
        return when (val token = reader.peek()) {
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                null
            }
            JsonReader.Token.STRING -> {
                val value = reader.nextString().trim()
                if (value.isEmpty()) {
                    null
                } else {
                    if (value.length > maxLength) {
                        throw StrictJsonParseException(
                            status = KnowledgePackImportStatus.VALIDATION_FAILED,
                            message = "Field '$fieldName' at ${reader.path} length (${value.length}) exceeds maximum ($maxLength)."
                        )
                    }
                    value
                }
            }
            else -> throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Expected STRING or NULL for '$fieldName' at ${reader.path}, but found $token."
            )
        }
    }

    private fun readStrictInt(reader: JsonReader, fieldName: String): Int {
        val token = reader.peek()
        if (token != JsonReader.Token.NUMBER) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Expected integer NUMBER for '$fieldName' at ${reader.path}, but found $token."
            )
        }
        return try {
            reader.nextInt()
        } catch (e: Throwable) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Invalid integer value for '$fieldName' at ${reader.path}: ${e.message}"
            )
        }
    }

    private fun readStrictLong(reader: JsonReader, fieldName: String): Long {
        val token = reader.peek()
        if (token != JsonReader.Token.NUMBER) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Expected long NUMBER for '$fieldName' at ${reader.path}, but found $token."
            )
        }
        return try {
            reader.nextLong()
        } catch (e: Throwable) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Invalid long value for '$fieldName' at ${reader.path}: ${e.message}"
            )
        }
    }

    private fun readStrictBoolean(reader: JsonReader, fieldName: String): Boolean {
        val token = reader.peek()
        if (token != JsonReader.Token.BOOLEAN) {
            throw StrictJsonParseException(
                status = KnowledgePackImportStatus.PARSING_FAILURE,
                message = "Expected BOOLEAN for '$fieldName' at ${reader.path}, but found $token."
            )
        }
        return reader.nextBoolean()
    }

    private fun missingField(fieldName: String): StrictJsonParseException {
        return StrictJsonParseException(
            status = KnowledgePackImportStatus.PARSING_FAILURE,
            message = "Missing required JSON field '$fieldName'."
        )
    }

    private class DepthTracker(private val maxDepth: Int) {
        private var currentDepth = 0

        fun enter(reader: JsonReader) {
            currentDepth++
            if (currentDepth > maxDepth) {
                throw StrictJsonParseException(
                    status = KnowledgePackImportStatus.PARSING_FAILURE,
                    message = "JSON nesting depth ($currentDepth) exceeds maximum allowed depth ($maxDepth) at ${reader.path}."
                )
            }
        }

        fun exit() {
            if (currentDepth > 0) {
                currentDepth--
            }
        }
    }

    private class StrictJsonParseException(
        val status: KnowledgePackImportStatus,
        override val message: String
    ) : RuntimeException(message)

    private class InputTooLargeException(override val message: String) : RuntimeException(message)

    /**
     * InputStream wrapper that enforces `maxBytes` during streaming and computes the exact
     * raw UTF-8 byte-level SHA-256 without buffering the entire payload in memory.
     */
    private class BoundedSha256InputStream(
        private val delegate: InputStream,
        private val maxBytes: Long
    ) : InputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")
        var totalBytesRead: Long = 0L
            private set

        override fun read(): Int {
            val byteVal = delegate.read()
            if (byteVal != -1) {
                totalBytesRead++
                if (totalBytesRead > maxBytes) {
                    throw InputTooLargeException(
                        "Input stream exceeded maximum allowed size of $maxBytes bytes."
                    )
                }
                digest.update(byteVal.toByte())
            }
            return byteVal
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = delegate.read(b, off, len)
            if (count > 0) {
                totalBytesRead += count
                if (totalBytesRead > maxBytes) {
                    throw InputTooLargeException(
                        "Input stream exceeded maximum allowed size of $maxBytes bytes."
                    )
                }
                digest.update(b, off, count)
            }
            return count
        }

        fun finishSha256Hex(): String {
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        override fun close() {
            delegate.close()
        }
    }
}
