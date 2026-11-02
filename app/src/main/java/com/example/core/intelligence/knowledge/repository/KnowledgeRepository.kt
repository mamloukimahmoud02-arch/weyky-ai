package com.example.core.intelligence.knowledge.repository

import android.content.Context
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.cache.KnowledgeCache
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.context.KnowledgeAvailabilityStatus
import com.example.core.intelligence.knowledge.context.PreparedTurnKnowledgeContext
import com.example.core.intelligence.knowledge.db.WeykyKnowledgeDatabase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KeywordWithTokensBundle
import com.example.core.intelligence.knowledge.db.dao.KnowledgeDao
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.search.InMemorySearchSnapshot
import com.example.core.intelligence.knowledge.search.KnowledgeSearchResult
import com.example.core.intelligence.knowledge.search.KnowledgeSearchService
import com.example.core.intelligence.knowledge.search.ScoredTopicCandidate
import com.example.core.intelligence.knowledge.validation.KnowledgeValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Central KnowledgeRepository for WEYKY Phase 2B.2 & Phase 2B.3.
 *
 * Responsibilities:
 * 1. Initializes an immediate zero-I/O [InMemorySearchSnapshot] from [SeedKnowledgeCatalog] at
 *    construction time so synchronous search and turn preparation are always ready without
 *    blocking the Main Thread.
 * 2. Persists `core_seed_pack` (`SEED_BUILTIN`) into [WeykyKnowledgeDatabase] idempotently and
 *    atomically on [ioDispatcher] (`Dispatchers.IO`).
 * 3. Loads active topics, keywords, and keyword tokens from Room on `Dispatchers.IO`, builds a
 *    validated [InMemorySearchSnapshot], atomically swaps the active snapshot reference, and
 *    invalidates stale cache entries when `knowledgeGeneration` advances.
 * 4. Preserves the last valid [InMemorySearchSnapshot] if any database read, validation, or
 *    snapshot build fails.
 * 5. Prepares an immutable, turn-scoped [PreparedTurnKnowledgeContext] once per conversational
 *    turn so `ConversationalReasoner` and `ConversationSession` operate on a single consistent
 *    snapshot without repeated normalizations or duplicate search invocations.
 */
class KnowledgeRepository(
    database: WeykyKnowledgeDatabase? = null,
    val cache: KnowledgeCache = KnowledgeCache(),
    private val converters: KnowledgeTypeConverters = KnowledgeTypeConverters(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val databaseRef = AtomicReference<WeykyKnowledgeDatabase?>(database)
    private val generationCounter = AtomicLong(1L)

    private val snapshotRef = AtomicReference<InMemorySearchSnapshot>(
        InMemorySearchSnapshot.fromSeedCatalog(knowledgeGeneration = generationCounter.get())
    )

    private val searchService = KnowledgeSearchService(
        cache = cache,
        snapshotProvider = { snapshotRef.get() }
    )
    private val initializationMutex = Mutex()

    private val searchInvocationCounter = AtomicInteger(0)
    private val turnPreparationCounter = AtomicInteger(0)

    @Volatile
    var lastPreparedContext: PreparedTurnKnowledgeContext? = null
        private set

    @Volatile
    var lastTurnSearchInvocations: Int = 0
        private set

    @Volatile
    internal var retrievalFailureSimulator: (() -> Throwable?)? = null

    /**
     * Returns the current immutable [InMemorySearchSnapshot] atomically without any I/O.
     */
    val currentSnapshot: InMemorySearchSnapshot
        get() = snapshotRef.get()

    /**
     * Returns the current knowledge generation counter.
     */
    val currentGeneration: Long
        get() = snapshotRef.get().knowledgeGeneration

    val totalSearchInvocations: Int
        get() = searchInvocationCounter.get()

    val totalTurnPreparations: Int
        get() = turnPreparationCounter.get()

    fun resetMetricsForTesting() {
        searchInvocationCounter.set(0)
        turnPreparationCounter.set(0)
        lastTurnSearchInvocations = 0
        lastPreparedContext = null
    }

    internal fun attachDatabaseIfAbsent(db: WeykyKnowledgeDatabase) {
        databaseRef.compareAndSet(null, db)
    }

    /**
     * Synchronous, zero-I/O topic lookup by ID from the active in-memory snapshot (with header
     * cache integration).
     */
    fun getTopicById(topicId: String): LocalKnowledgeBase.KnowledgeTopic? {
        cache.getHeader(topicId)?.let { return it }
        val topic = snapshotRef.get().topicsById[topicId] ?: return null
        cache.putHeader(topicId, topic)
        return topic
    }

    /**
     * Synchronous, zero-I/O search against the active [InMemorySearchSnapshot].
     * Never touches Room or SQLite.
     */
    fun search(
        query: String,
        currentTopicId: String? = null,
        snapshotOverride: InMemorySearchSnapshot? = null
    ): KnowledgeSearchResult {
        searchInvocationCounter.incrementAndGet()
        val snap = snapshotOverride ?: snapshotRef.get()
        return searchService.search(
            query = query,
            currentTopicId = currentTopicId,
            snapshot = snap
        )
    }

    /**
     * Synchronous convenience method returning the winning [LocalKnowledgeBase.KnowledgeTopic]
     * with the exact signature of [LocalKnowledgeBase.findMatchingTopic].
     */
    fun findMatchingTopic(
        query: String,
        currentTopicId: String? = null
    ): LocalKnowledgeBase.KnowledgeTopic? {
        return search(query = query, currentTopicId = currentTopicId).matchedTopic
    }

    /**
     * Exhaustive scoring across all topics in the current snapshot (for parity testing).
     */
    fun searchExhaustive(
        query: String,
        currentTopicId: String? = null
    ): KnowledgeSearchResult {
        return searchService.searchExhaustive(
            query = query,
            currentTopicId = currentTopicId,
            snapshot = snapshotRef.get()
        )
    }

    /**
     * Prepares a turn-scoped [PreparedTurnKnowledgeContext] once per conversational turn.
     *
     * Guarantees:
     * - Captures a single [InMemorySearchSnapshot] reference at the start of the turn so
     *   all reasoning steps within the turn read from a consistent snapshot generation.
     * - Computes strict (`normalizeArabicStrict`) and relaxed (`normalizeArabic`) normalizations once.
     * - Invokes [KnowledgeSearchService.search] at most once per turn.
     * - Applies conditional context passing so an old conversational topic never biases
     *   an unrelated new query when no continuity signal is present.
     * - Handles unexpected retrieval errors gracefully without crashing the conversation pipeline.
     */
    fun prepareTurnKnowledgeContext(
        userQuery: String,
        currentTopicId: String? = null,
        isArabicOverride: Boolean? = null,
        snapshotOverride: InMemorySearchSnapshot? = null
    ): PreparedTurnKnowledgeContext {
        turnPreparationCounter.incrementAndGet()
        val startInvocations = searchInvocationCounter.get()
        val cleanQuery = userQuery.trim()
        val hasArabicChars = cleanQuery.any { it in '\u0600'..'\u06FF' }
        val isArabic = isArabicOverride ?: (hasArabicChars || Locale.getDefault().language.startsWith("ar"))

        return try {
            retrievalFailureSimulator?.invoke()?.let { throw it }

            val snapshot = snapshotOverride ?: snapshotRef.get()
            val strictNormalized = LocalKnowledgeBase.normalizeArabicStrict(cleanQuery)
            val relaxedNormalized = LocalKnowledgeBase.normalizeArabic(cleanQuery)

            if (relaxedNormalized.isBlank() || snapshot.topicCount == 0) {
                val emptyContext = PreparedTurnKnowledgeContext(
                    userQuery = cleanQuery,
                    strictNormalizedQuery = strictNormalized,
                    relaxedNormalizedQuery = relaxedNormalized,
                    isArabic = isArabic,
                    currentConversationalTopicId = currentTopicId,
                    effectiveSearchContextTopicId = null,
                    retrievedTopicId = null,
                    retrievedTopic = null,
                    rawSearchScore = 0,
                    finalSearchScore = 0,
                    rankedCandidates = emptyList(),
                    candidateTopicIdsRetrievedByIndex = emptySet(),
                    unbiasedTopTopicId = null,
                    unbiasedTopTopic = null,
                    unbiasedRawScore = 0,
                    hasContinuitySignal = false,
                    specificCapitalAnswer = null,
                    knowledgeGeneration = snapshot.knowledgeGeneration,
                    hasValidKnowledgeMatch = false,
                    availabilityStatus = if (snapshot.topicCount == 0) {
                        KnowledgeAvailabilityStatus.RETRIEVAL_FAILURE_OR_UNAVAILABLE
                    } else {
                        KnowledgeAvailabilityStatus.NO_MATCHING_TOPIC
                    },
                    snapshotUsed = snapshot
                )
                lastTurnSearchInvocations = searchInvocationCounter.get() - startInvocations
                lastPreparedContext = emptyContext
                return emptyContext
            }

            val canonicalCurrentTopicId = canonicalizeKnowledgeTopicId(currentTopicId)
                ?.takeIf { snapshot.topicsById.containsKey(it) }

            val hasContinuity = detectContinuitySignal(
                relaxedNormalizedQuery = relaxedNormalized,
                canonicalCurrentTopicId = canonicalCurrentTopicId
            )

            // Execute a single indexed search against the captured snapshot
            val searchResult = search(
                query = cleanQuery,
                currentTopicId = canonicalCurrentTopicId,
                snapshotOverride = snapshot
            )

            // Determine the best unbiased candidate (ranked purely by rawScore DESC, topicOrderIndex ASC)
            val unbiasedTopCandidate = searchResult.orderedCandidates
                .sortedWith(
                    compareByDescending<ScoredTopicCandidate> { it.rawScore }
                        .thenBy { it.topicOrderIndex }
                )
                .firstOrNull { it.rawScore >= KnowledgeSearchService.ACCEPTANCE_THRESHOLD }

            // Conditional context resolution:
            // If there is no continuity signal and an unrelated topic has a strictly stronger rawScore >= 80
            // than the previous active topic, do NOT let the previous topic's continuity bonus override it.
            val primaryCandidateFromIndex: ScoredTopicCandidate?
            val effectiveContextUsed: String?
            val topBiasedCandidate = searchResult.orderedCandidates.firstOrNull()
                ?.takeIf { it.finalScore >= KnowledgeSearchService.ACCEPTANCE_THRESHOLD }

            if (!hasContinuity &&
                unbiasedTopCandidate != null &&
                topBiasedCandidate != null &&
                unbiasedTopCandidate.topicId != topBiasedCandidate.topicId &&
                unbiasedTopCandidate.rawScore > topBiasedCandidate.rawScore
            ) {
                primaryCandidateFromIndex = unbiasedTopCandidate.copy(
                    finalScore = unbiasedTopCandidate.rawScore
                )
                effectiveContextUsed = null
            } else {
                primaryCandidateFromIndex = topBiasedCandidate
                effectiveContextUsed = if (topBiasedCandidate != null && topBiasedCandidate.finalScore > topBiasedCandidate.rawScore) {
                    canonicalCurrentTopicId
                } else {
                    canonicalCurrentTopicId?.takeIf { hasContinuity }
                }
            }

            // Supplemental conversational concept & alternative formulation resolution
            // (activates when primary search has no match or when evaluating alternative topic formulations)
            val supplementalCandidate = if (primaryCandidateFromIndex == null) {
                resolveSupplementalIndexedConceptMatch(
                    strictNormalizedQuery = strictNormalized,
                    relaxedNormalizedQuery = relaxedNormalized,
                    canonicalCurrentTopicId = canonicalCurrentTopicId,
                    hasContinuitySignal = hasContinuity,
                    snapshot = snapshot
                )
            } else {
                null
            }

            val winningCandidate = primaryCandidateFromIndex ?: supplementalCandidate
            val winningTopicId = winningCandidate?.topicId
            val winningTopic = winningTopicId?.let { snapshot.topicsById[it] }

            val resolvedUnbiasedCandidate = unbiasedTopCandidate ?: supplementalCandidate?.takeIf {
                it.finalScore == it.rawScore && it.rawScore >= KnowledgeSearchService.ACCEPTANCE_THRESHOLD
            }
            val resolvedUnbiasedTopicId = resolvedUnbiasedCandidate?.topicId
            val resolvedUnbiasedTopic = resolvedUnbiasedTopicId?.let { snapshot.topicsById[it] }

            val specificCapitalAnswer = if (winningTopicId == "capitals_arab") {
                LocalKnowledgeBase.resolveSpecificCapitalAnswer(cleanQuery, isArabic)
            } else {
                null
            }

            val rankedCandidates = if (supplementalCandidate != null &&
                searchResult.orderedCandidates.none { it.topicId == supplementalCandidate.topicId }
            ) {
                listOf(supplementalCandidate) + searchResult.orderedCandidates
            } else {
                searchResult.orderedCandidates
            }

            val availabilityStatus = when {
                winningTopic == null -> KnowledgeAvailabilityStatus.NO_MATCHING_TOPIC
                else -> {
                    val localizedSummary = if (isArabic) winningTopic.arabicSummary else winningTopic.englishSummary
                    if (localizedSummary.isNotBlank() || !specificCapitalAnswer.isNullOrBlank()) {
                        KnowledgeAvailabilityStatus.MATCHED_USABLE_KNOWLEDGE
                    } else {
                        KnowledgeAvailabilityStatus.MATCHED_INSUFFICIENT_INFO
                    }
                }
            }

            val hasValidMatch = availabilityStatus == KnowledgeAvailabilityStatus.MATCHED_USABLE_KNOWLEDGE

            val prepared = PreparedTurnKnowledgeContext(
                userQuery = cleanQuery,
                strictNormalizedQuery = strictNormalized,
                relaxedNormalizedQuery = relaxedNormalized,
                isArabic = isArabic,
                currentConversationalTopicId = currentTopicId,
                effectiveSearchContextTopicId = effectiveContextUsed,
                retrievedTopicId = if (hasValidMatch) winningTopicId else null,
                retrievedTopic = if (hasValidMatch) winningTopic else null,
                rawSearchScore = winningCandidate?.rawScore ?: 0,
                finalSearchScore = winningCandidate?.finalScore ?: 0,
                rankedCandidates = rankedCandidates,
                candidateTopicIdsRetrievedByIndex = searchResult.candidateTopicIdsRetrievedByIndex,
                unbiasedTopTopicId = resolvedUnbiasedTopicId,
                unbiasedTopTopic = resolvedUnbiasedTopic,
                unbiasedRawScore = resolvedUnbiasedCandidate?.rawScore ?: 0,
                hasContinuitySignal = hasContinuity,
                specificCapitalAnswer = specificCapitalAnswer,
                knowledgeGeneration = snapshot.knowledgeGeneration,
                hasValidKnowledgeMatch = hasValidMatch,
                availabilityStatus = availabilityStatus,
                snapshotUsed = snapshot
            )
            lastTurnSearchInvocations = searchInvocationCounter.get() - startInvocations
            lastPreparedContext = prepared
            prepared
        } catch (_: Throwable) {
            val fallbackGen = runCatching { snapshotRef.get().knowledgeGeneration }.getOrDefault(1L)
            val failedContext = PreparedTurnKnowledgeContext(
                userQuery = cleanQuery,
                strictNormalizedQuery = LocalKnowledgeBase.normalizeArabicStrict(cleanQuery),
                relaxedNormalizedQuery = LocalKnowledgeBase.normalizeArabic(cleanQuery),
                isArabic = isArabic,
                currentConversationalTopicId = currentTopicId,
                effectiveSearchContextTopicId = null,
                retrievedTopicId = null,
                retrievedTopic = null,
                rawSearchScore = 0,
                finalSearchScore = 0,
                rankedCandidates = emptyList(),
                candidateTopicIdsRetrievedByIndex = emptySet(),
                unbiasedTopTopicId = null,
                unbiasedTopTopic = null,
                unbiasedRawScore = 0,
                hasContinuitySignal = false,
                specificCapitalAnswer = null,
                knowledgeGeneration = fallbackGen,
                hasValidKnowledgeMatch = false,
                availabilityStatus = KnowledgeAvailabilityStatus.RETRIEVAL_FAILURE_OR_UNAVAILABLE,
                snapshotUsed = null
            )
            lastTurnSearchInvocations = searchInvocationCounter.get() - startInvocations
            lastPreparedContext = failedContext
            failedContext
        }
    }

    private fun canonicalizeKnowledgeTopicId(topicId: String?): String? {
        if (topicId.isNullOrBlank()) return null
        return when (topicId) {
            "travel" -> "salalah_tourism"
            "health_wellness" -> "sleep_wellness"
            else -> topicId
        }
    }

    private fun detectContinuitySignal(
        relaxedNormalizedQuery: String,
        canonicalCurrentTopicId: String?
    ): Boolean {
        if (LocalKnowledgeBase.hasContinuitySignal(relaxedNormalizedQuery)) {
            return true
        }
        val queryTokens = relaxedNormalizedQuery.split(" ").filter { it.isNotBlank() }
        val hasBackReferencePronoun = relaxedNormalizedQuery.contains("هناك") ||
            relaxedNormalizedQuery.contains("فيها") ||
            relaxedNormalizedQuery.contains("عنه") ||
            relaxedNormalizedQuery.contains("عنها") ||
            relaxedNormalizedQuery.contains("ذلك") ||
            relaxedNormalizedQuery.contains("زيارتها") ||
            relaxedNormalizedQuery.contains("رويتها") ||
            relaxedNormalizedQuery.contains("رؤيتها") ||
            relaxedNormalizedQuery.contains("نراها") ||
            relaxedNormalizedQuery.contains("تتكون") ||
            relaxedNormalizedQuery.contains("تتشكل") ||
            queryTokens.contains("there") ||
            queryTokens.contains("that") ||
            queryTokens.contains("its") ||
            queryTokens.contains("their") ||
            relaxedNormalizedQuery.contains("about it") ||
            relaxedNormalizedQuery.contains("see them")

        if (hasBackReferencePronoun) return true

        val followUpStarters = listOf(
            "وماذا", "وكيف", "ولماذا", "طيب", "وما", "هل هناك", "وهل",
            "المزيد", "اكثر", "وضح",
            "tell me more", "and how", "what about", "elaborate", "can you give more details"
        )
        if (followUpStarters.any { relaxedNormalizedQuery.startsWith(it) }) {
            return true
        }

        if (canonicalCurrentTopicId == "salalah_tourism" &&
            (relaxedNormalizedQuery.contains("طقس") || relaxedNormalizedQuery.contains("جو") ||
                relaxedNormalizedQuery.contains("مطر") || relaxedNormalizedQuery.contains("ملابس") ||
                relaxedNormalizedQuery.contains("متي") || relaxedNormalizedQuery.contains("وقت") ||
                relaxedNormalizedQuery.contains("شهر") || relaxedNormalizedQuery.contains("weather") ||
                relaxedNormalizedQuery.contains("when"))
        ) {
            return true
        }
        if (canonicalCurrentTopicId == "sleep_wellness" &&
            (relaxedNormalizedQuery.contains("قهوه") || relaxedNormalizedQuery.contains("كافيين") ||
                relaxedNormalizedQuery.contains("شاي") || relaxedNormalizedQuery.contains("coffee") ||
                relaxedNormalizedQuery.contains("caffeine"))
        ) {
            return true
        }
        return false
    }

    /**
     * Resolves alternative natural-language formulations and contextual follow-ups against
     * topics present in [snapshot] when the primary keyword scorer yields `< 80`.
     */
    private fun resolveSupplementalIndexedConceptMatch(
        strictNormalizedQuery: String,
        relaxedNormalizedQuery: String,
        canonicalCurrentTopicId: String?,
        hasContinuitySignal: Boolean,
        snapshot: InMemorySearchSnapshot
    ): ScoredTopicCandidate? {
        val relaxedTokens = relaxedNormalizedQuery.split(" ").filter { it.isNotBlank() }
        if (relaxedTokens.isEmpty()) return null

        // 1. Check single-content-token multi-word keywords in the reverse token index
        // (e.g., "ما هو weyky", "ما هي weyky", "حدثني عن weyky" -> sole content token "weyky")
        for (qTok in relaxedTokens) {
            if (qTok.length < 4 || qTok in KnowledgeSearchService.AMBIGUOUS_HOMOGRAPH_WORDS) {
                continue
            }
            val qStem = LocalKnowledgeBase.stripArabicPrefixes(qTok)
            val topicIndices = LinkedHashSet<Int>()
            snapshot.reverseTokenIndex.getTopicsByRelaxedToken(qTok)?.forEach { topicIndices.add(it) }
            snapshot.reverseTokenIndex.getTopicsByRelaxedStem(qStem)?.forEach { topicIndices.add(it) }

            for (tIdx in topicIndices) {
                val topicId = snapshot.orderedTopicIds.getOrNull(tIdx) ?: continue
                val kwEntries = snapshot.keywordEntriesByTopic[topicId].orEmpty()
                val matchedSingleContentKw = kwEntries.any { kw ->
                    kw.tokenCount >= 2 &&
                        kw.contentTokenCount == 1 &&
                        kw.tokens.any { tok ->
                            !tok.isStopWord && (tok.relaxedToken == qTok || tok.relaxedStem == qStem)
                        }
                }
                if (matchedSingleContentKw) {
                    return ScoredTopicCandidate(
                        topicId = topicId,
                        topicOrderIndex = tIdx,
                        rawScore = 158,
                        finalScore = 158,
                        matchedKeywordsCount = 1
                    )
                }
            }
        }

        // 2. Assistant identity formulations ("Tell me about your identity", "What is your identity", "حدثني عن هويتك")
        val isUserIdentity = relaxedNormalizedQuery == "من انا" || relaxedNormalizedQuery == "من اكون" ||
            relaxedNormalizedQuery == "مين انا" || relaxedNormalizedQuery == "who am i" ||
            relaxedNormalizedQuery == "what is my name"
        if (!isUserIdentity && snapshot.topicsById.containsKey("identity")) {
            val asksAssistantIdentity = relaxedNormalizedQuery.contains("your identity") ||
                relaxedNormalizedQuery.contains("about your identity") ||
                relaxedNormalizedQuery.contains("introduce yourself") ||
                relaxedNormalizedQuery.contains("هويتك") ||
                relaxedNormalizedQuery.contains("عن نفسك") ||
                (relaxedTokens.contains("identity") && (relaxedTokens.contains("your") || relaxedTokens.contains("weyky")))
            if (asksAssistantIdentity) {
                val tIdx = snapshot.topicOrderIndex["identity"] ?: 0
                return ScoredTopicCandidate(
                    topicId = "identity",
                    topicOrderIndex = tIdx,
                    rawScore = 150,
                    finalScore = 150,
                    matchedKeywordsCount = 1
                )
            }
        }

        // 3. Battery care concept formulations ("Explain battery care.", "Tell me about battery care")
        if (snapshot.topicsById.containsKey("battery_care") && relaxedTokens.size >= 2) {
            val asksBatteryCare = relaxedNormalizedQuery.contains("battery care") ||
                relaxedNormalizedQuery.contains("care for battery") ||
                relaxedNormalizedQuery.contains("caring for battery") ||
                relaxedNormalizedQuery.contains("protect battery") ||
                relaxedNormalizedQuery.contains("preserve battery") ||
                relaxedNormalizedQuery.contains("العنايه بالبطاريه") ||
                relaxedNormalizedQuery.contains("عنايه البطاريه") ||
                relaxedNormalizedQuery.contains("حمايه البطاريه")
            if (asksBatteryCare) {
                val tIdx = snapshot.topicOrderIndex["battery_care"] ?: 0
                return ScoredTopicCandidate(
                    topicId = "battery_care",
                    topicOrderIndex = tIdx,
                    rawScore = 152,
                    finalScore = 152,
                    matchedKeywordsCount = 1
                )
            }
        }

        // 4. Universe & cosmos concept formulations ("Now explain the universe.", "Tell me about the universe")
        if (snapshot.topicsById.containsKey("physics_universe") && relaxedTokens.size >= 2) {
            val hasUniverseConcept = relaxedTokens.contains("universe") ||
                relaxedTokens.contains("cosmos") ||
                relaxedTokens.contains("astronomy") ||
                relaxedTokens.contains("الكون") ||
                relaxedTokens.contains("الفلك")
            val hasInquiryCue = relaxedTokens.contains("explain") ||
                relaxedTokens.contains("about") ||
                relaxedTokens.contains("what") ||
                relaxedTokens.contains("how") ||
                relaxedTokens.contains("tell") ||
                relaxedTokens.contains("اشرح") ||
                relaxedTokens.contains("حدثني") ||
                relaxedTokens.contains("معلومات") ||
                relaxedTokens.contains("حقائق")
            if (hasUniverseConcept && hasInquiryCue) {
                val tIdx = snapshot.topicOrderIndex["physics_universe"] ?: 0
                return ScoredTopicCandidate(
                    topicId = "physics_universe",
                    topicOrderIndex = tIdx,
                    rawScore = 150,
                    finalScore = 150,
                    matchedKeywordsCount = 1
                )
            }
        }

        // 5. Oman & Salalah tourism concept formulations and contextual follow-ups
        // ("Tell me about Oman." -> "What about its tourism?", "حدثني عن سلطنة عمان" -> "وماذا عن السياحة فيها؟")
        if (snapshot.topicsById.containsKey("salalah_tourism")) {
            val mentionsOmanWithoutCapital = (relaxedTokens.contains("oman") ||
                relaxedNormalizedQuery.contains("سلطنه عمان") ||
                relaxedNormalizedQuery.contains("عن عمان") ||
                strictNormalizedQuery.contains("سلطنة عمان")) &&
                !relaxedNormalizedQuery.contains("capital") &&
                !relaxedNormalizedQuery.contains("عاصمه")

            val isContextualTourismFollowUp = (canonicalCurrentTopicId == "salalah_tourism" ||
                canonicalCurrentTopicId == "capitals_arab") &&
                hasContinuitySignal &&
                (relaxedTokens.contains("tourism") ||
                    relaxedTokens.contains("tourist") ||
                    relaxedNormalizedQuery.contains("سياحه") ||
                    relaxedNormalizedQuery.contains("السياحه") ||
                    relaxedNormalizedQuery.contains("سياحتها"))

            if (mentionsOmanWithoutCapital || isContextualTourismFollowUp) {
                val tIdx = snapshot.topicOrderIndex["salalah_tourism"] ?: 0
                val bonus = if (isContextualTourismFollowUp) 12 else 0
                return ScoredTopicCandidate(
                    topicId = "salalah_tourism",
                    topicOrderIndex = tIdx,
                    rawScore = 148,
                    finalScore = 148 + bonus,
                    matchedKeywordsCount = 1
                )
            }
        }

        return null
    }

    /**
     * Idempotently initializes `core_seed_pack` (`SEED_BUILTIN`) inside Room on [ioDispatcher]
     * and refreshes the active [InMemorySearchSnapshot] from Room's active topics.
     */
    suspend fun initializeSeedAndWarmUpIndex(): Boolean = withContext(ioDispatcher) {
        initializationMutex.withLock {
            runCatching {
                val db = databaseRef.get()
                    ?: throw IllegalStateException("WeykyKnowledgeDatabase is not attached to KnowledgeRepository.")
                val dao = db.knowledgeDao()
                ensureCoreSeedPersistedIdempotently(dao)
                val loadedBundles = loadActiveTopicStorageBundlesFromDao(dao)
                if (loadedBundles.isEmpty()) {
                    throw IllegalStateException("No active topic bundles found in Room after seed initialization.")
                }
                publishNewSnapshotFromBundles(loadedBundles)
                true
            }.getOrElse {
                false
            }
        }
    }

    /**
     * Reloads active topics from Room on [ioDispatcher], builds a new [InMemorySearchSnapshot],
     * and publishes it atomically. If loading or building fails, retains the previous valid snapshot.
     */
    suspend fun reloadActiveSnapshotFromDatabase(
        loaderOverride: (suspend (KnowledgeDao) -> List<TopicStorageBundle>)? = null
    ): Boolean = withContext(ioDispatcher) {
        initializationMutex.withLock {
            runCatching {
                val db = databaseRef.get()
                    ?: throw IllegalStateException("WeykyKnowledgeDatabase is not attached to KnowledgeRepository.")
                val dao = db.knowledgeDao()
                val bundles = if (loaderOverride != null) {
                    loaderOverride(dao)
                } else {
                    loadActiveTopicStorageBundlesFromDao(dao)
                }
                if (bundles.isEmpty()) {
                    throw IllegalStateException("Active topic bundles from database are empty.")
                }
                publishNewSnapshotFromBundles(bundles)
                true
            }.getOrElse {
                false
            }
        }
    }

    /**
     * Builds and atomically publishes a new [InMemorySearchSnapshot] outside the active reference,
     * then invalidates stale cache entries.
     */
    fun publishNewSnapshotFromBundles(bundles: List<TopicStorageBundle>): InMemorySearchSnapshot {
        require(bundles.isNotEmpty()) { "Cannot publish snapshot from empty bundles" }
        val nextGen = generationCounter.incrementAndGet()
        val candidateSnapshot = InMemorySearchSnapshot.fromTopicStorageBundles(
            knowledgeGeneration = nextGen,
            bundles = bundles
        )
        check(candidateSnapshot.topicCount > 0 && candidateSnapshot.keywordCount > 0) {
            "Constructed snapshot is invalid (topicCount=${candidateSnapshot.topicCount}, keywordCount=${candidateSnapshot.keywordCount})"
        }
        snapshotRef.set(candidateSnapshot)
        cache.invalidateAll()
        return candidateSnapshot
    }

    private suspend fun ensureCoreSeedPersistedIdempotently(dao: KnowledgeDao) {
        val expectedPack = SeedKnowledgeCatalog.buildCoreSeedPackMetadata()
        val existingPack = dao.getPackMetadataById(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)
        val existingHeaders = dao.getTopicHeadersByPackOrdered(SeedKnowledgeCatalog.CORE_SEED_PACK_ID)

        val isAlreadyUpToDate = existingPack != null &&
            existingPack.sourceType == SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE &&
            existingPack.packVersion == SeedKnowledgeCatalog.CORE_SEED_PACK_VERSION &&
            existingPack.schemaVersion == SeedKnowledgeCatalog.CORE_SEED_SCHEMA_VERSION &&
            existingPack.sha256Checksum == expectedPack.sha256Checksum &&
            existingPack.topicCount == SeedKnowledgeCatalog.coreTopics.size &&
            existingHeaders.size == SeedKnowledgeCatalog.coreTopics.size &&
            verifyCoreTopicsMatchSeed(existingHeaders)

        if (isAlreadyUpToDate) {
            return
        }

        val seedBundles = SeedKnowledgeCatalog.buildCoreSeedTopicStorageBundles(converters)

        val allHeaders = seedBundles.map { it.header }
        val allKeywords = ArrayList<com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity>()
        val allTokens = ArrayList<com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity>()
        var syntheticKeywordId = 1L
        var syntheticTokenId = 1L
        for (bundle in seedBundles) {
            for (kwWithTokens in bundle.keywordsWithTokens) {
                val kwId = syntheticKeywordId++
                allKeywords.add(kwWithTokens.keyword.copy(keywordId = kwId))
                for (tok in kwWithTokens.tokens) {
                    allTokens.add(tok.copy(tokenId = syntheticTokenId++, keywordId = kwId))
                }
            }
        }
        val validation = KnowledgeValidator.validatePackData(
            pack = expectedPack,
            topics = allHeaders,
            keywords = allKeywords,
            tokens = allTokens,
            details = emptyList(),
            followUpBranches = emptyList()
        )
        check(validation.isValid) {
            "Core seed catalog failed KnowledgeValidator: ${validation.issues}"
        }

        dao.upsertPackWithTopicsTransactional(
            pack = expectedPack,
            topics = seedBundles
        )
    }

    private fun verifyCoreTopicsMatchSeed(
        existingHeaders: List<com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity>
    ): Boolean {
        val byId = existingHeaders.associateBy { it.topicId }
        for (seedEntry in SeedKnowledgeCatalog.coreTopics) {
            val stored = byId[seedEntry.topicId] ?: return false
            if (stored.topicOrderIndex != seedEntry.topicOrderIndex ||
                stored.arabicSummary != seedEntry.arabicSummary ||
                stored.englishSummary != seedEntry.englishSummary ||
                stored.sourceType != SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE ||
                !stored.isCoreTopic
            ) {
                return false
            }
        }
        return true
    }

    private suspend fun loadActiveTopicStorageBundlesFromDao(
        dao: KnowledgeDao
    ): List<TopicStorageBundle> {
        val activeHeaders = dao.getAllActiveTopicHeadersOrdered()
        if (activeHeaders.isEmpty()) return emptyList()

        val result = ArrayList<TopicStorageBundle>(activeHeaders.size)
        for (header in activeHeaders) {
            val keywords = dao.getKeywordsByTopicAndPackOrdered(header.topicId, header.packId)
            val allTokensForTopic = dao.getTokensByTopicAndPackOrdered(header.topicId, header.packId)
            val tokensByKeywordId = allTokensForTopic.groupBy { it.keywordId }

            val kwWithTokens = keywords.map { kw ->
                KeywordWithTokensBundle(
                    keyword = kw,
                    tokens = tokensByKeywordId[kw.keywordId].orEmpty()
                )
            }

            result.add(
                TopicStorageBundle(
                    header = header,
                    keywordsWithTokens = kwWithTokens,
                    detail = null,
                    followUpBranches = emptyList()
                )
            )
        }
        return result
    }

    companion object {
        @Volatile
        private var INSTANCE: KnowledgeRepository? = null

        /**
         * Returns the process-wide singleton [KnowledgeRepository], creating it with the built-in
         * zero-I/O seed snapshot if not yet initialized with a [Context].
         */
        fun getDefaultInstance(): KnowledgeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KnowledgeRepository(database = null).also { INSTANCE = it }
            }
        }

        /**
         * Returns the process-wide singleton [KnowledgeRepository] backed by
         * [WeykyKnowledgeDatabase.getInstance] using `context.applicationContext`.
         */
        fun getInstance(context: Context): KnowledgeRepository {
            val db = WeykyKnowledgeDatabase.getInstance(context.applicationContext)
            val existing = INSTANCE
            if (existing != null) {
                existing.attachDatabaseIfAbsent(db)
                return existing
            }
            return synchronized(this) {
                val current = INSTANCE
                if (current != null) {
                    current.attachDatabaseIfAbsent(db)
                    current
                } else {
                    KnowledgeRepository(database = db).also { INSTANCE = it }
                }
            }
        }

        internal fun clearInstanceForTesting() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}
