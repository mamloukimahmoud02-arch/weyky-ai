package com.example.core.intelligence.knowledge.search

import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.cache.KnowledgeCache
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog

/**
 * Detailed scoring record for a single candidate topic in [KnowledgeSearchResult].
 */
data class ScoredTopicCandidate(
    val topicId: String,
    val topicOrderIndex: Int,
    val rawScore: Int,
    val finalScore: Int,
    val matchedKeywordsCount: Int
)

/**
 * Output of [KnowledgeSearchService.search].
 */
data class KnowledgeSearchResult(
    val matchedTopic: LocalKnowledgeBase.KnowledgeTopic?,
    val matchedTopicId: String?,
    val rawScore: Int,
    val finalScore: Int,
    val orderedCandidates: List<ScoredTopicCandidate>,
    val candidateTopicIdsRetrievedByIndex: Set<String>,
    val knowledgeGeneration: Long
)

/**
 * High-precision search and candidate scoring service for WEYKY's local knowledge base.
 *
 * Implements the exact Arabic normalization, prefix/clitic handling, homograph/root protection,
 * phrase/token matching, multi-keyword bonus, capital inquiry rule, and continuity tie-breaker
 * extracted line-by-line from [LocalKnowledgeBase.findMatchingTopic] (`lines 217–513`).
 *
 * ### Internal Scoring & Matching Reference Table (from `LocalKnowledgeBase.kt`)
 * | Case | Condition | Exact Formula / Effect | Source Lines |
 * | :--- | :--- | :--- | :--- |
 * | Strict Query Normalization | Keep hamza, `ة`, diacritics stripped, `ى -> ي` | `normalizeArabicStrict(query)` | `238–260`, `407` |
 * | Relaxed Query Normalization | Unify `[أإآٱ] -> ا`, `ة -> ه`, `ى -> ي`, `ؤ -> و`, `ئ -> ي` | `normalizeArabic(query)` | `265–292`, `404` |
 * | Prefix Stripping | Strip `وال/بال/كال/فال/لل/ال` if remaining `>= 3` & `!in ROOT_AL_WORDS` | `stripArabicPrefixes(word)` | `328–343` |
 * | Ambiguous Homograph Guard | `relQ in AMBIGUOUS_HOMOGRAPH_WORDS \|\| relK in AMBIGUOUS_HOMOGRAPH_WORDS` | Require `strictQ == strictK` | `303–305`, `431` |
 * | Protected Root Clitic Guard | `relQ !in PROTECTED_ROOT_WORDS && strictQ !in PROTECTED_ROOT_WORDS && relK.length >= 4` | Allow `و/ب/ل/ف` clitic strip if `afterClitic == relK` | `310–323` |
 * | Branch 1A: Exact Strict Phrase | `strictQuery == strictKw` | `204 + normKwTokens.size * 30` | `429–430` |
 * | Branch 1B: Exact Relaxed Phrase | `normQuery == normKw && normKw !in AMBIGUOUS_HOMOGRAPH_WORDS` | `200 + normKwTokens.size * 30` | `431–432` |
 * | Branch 2A: Multi-Word Boundary Phrase | `containsPhraseOnWordBoundary(...) && normKwTokens.size >= 2` | `130 + normKwTokens.size * 25 + normKw.length + exactStrictBonus` (`+4` if `strictQuery.contains(strictKw)`) | `435–439` |
 * | Branch 2B: Single-Token Short Boundary | `containsPhraseOnWordBoundary(...) && normKwTokens.size == 1 && tokenLen <= 2 && normKwTokens[0] != "ai"` | `40` | `440–442` |
 * | Branch 2C: Single-Token Standard Boundary | `containsPhraseOnWordBoundary(...) && normKwTokens.size == 1 && (tokenLen > 2 \|\| normKwTokens[0] == "ai")` | `95 + tokenLen + exactStrictBonus` (`+4` if `strictQuery.contains(strictKw)`) | `440–442` |
 * | Branch 3: Order-Independent Multi-Token | `contentKwIndices.size >= 2 && contentQueryIndices.isNotEmpty() && allContentMatched` | `115 + contentKwIndices.size * 20` | `445–458` |
 * | Multi-Keyword Specificity Bonus | `topicMaxScore >= 80 && matchedKeywordsCount > 1` | `topicMaxScore += minOf((matchedKeywordsCount - 1) * 12, 24)` | `469–471` |
 * | Special Capitals Rule | `topic.id == "capitals_arab"` + mentions `"عاصمة"`/`"capital"`/`"capitals"` + mentions `COUNTRY_CAPITALS_MAP` key | `if (topicMaxScore < 165) topicMaxScore = 165` | `474–481` |
 * | Continuity Tie-Breaker | `finalScore >= 80 && hasContinuity && isSameTopic && finalScore + 10 >= maxRawScore` | `finalScore += 12` | `488–504` |
 * | Acceptance & Tie-Break | `finalScore > bestScore` (strict `>`), threshold `bestScore >= 80` | Earlier `topicOrderIndex` wins ties; `< 80` returns `null` | `506–512` |
 */
class KnowledgeSearchService(
    private val cache: KnowledgeCache? = null,
    private val snapshotProvider: () -> InMemorySearchSnapshot = {
        InMemorySearchSnapshot.fromSeedCatalog()
    }
) {

    companion object {
        /**
         * Exact `ROOT_AL_WORDS` from [LocalKnowledgeBase] (`lines 217–222`).
         */
        val ROOT_AL_WORDS: Set<String> = setOf(
            "الله", "اللهم", "المانيا", "ألمانيا", "البانيا", "ألبانيا",
            "الماس", "ألم", "الم", "الوان", "ألوان", "الهام", "إلهام",
            "اليوم", "الان", "الآن", "الف", "ألف", "اليه", "إليه",
            "اليها", "إليها", "اليهم", "إليهم", "الات", "آلات", "اله", "إله"
        )

        /**
         * Exact `PROTECTED_ROOT_WORDS` from [LocalKnowledgeBase] (`lines 224–229`).
         */
        val PROTECTED_ROOT_WORDS: Set<String> = setOf(
            "باريس", "بغداد", "لبنان", "لندن", "وليد", "برلين", "بروكسل",
            "مدريد", "فرنسا", "تونس", "مسقط", "صلاله", "صلالة", "بومودورو",
            "بطاريه", "بطارية", "بكتيريا", "برمجه", "برمجة", "فيزياء",
            "فلسفه", "فلسفة", "فيتامين", "كافيين", "فواكه", "واشنطن", "وطن"
        )

        /**
         * Exact `AMBIGUOUS_HOMOGRAPH_WORDS` from [LocalKnowledgeBase] (`lines 231–233`).
         */
        val AMBIGUOUS_HOMOGRAPH_WORDS: Set<String> = setOf(
            "سال", "مهمه", "سنه", "حاله", "عده", "مره", "قوه", "بنه", "حبه"
        )

        /**
         * Generic non-distinctive verbs/predicates that must not by themselves (when paired only
         * with stop words) confer a high multi-word phrase match or supplemental single-token
         * concept match on queries about a different subject (e.g. "كيف تعمل النجوم؟" or
         * "كيف تعمل الجاذبية؟").
         */
        val GENERIC_NON_DISTINCTIVE_PREDICATE_TOKENS: Set<String> = setOf(
            "تعمل", "يعمل", "اعمل", "نعمل", "عمل",
            "تفكر", "يفكر", "افكر", "نفكر",
            "تشتغل", "يشتغل",
            "تكون", "يكون", "تتكون", "يتكون",
            "تحدث", "يحدث",
            "توجد", "يوجد",
            "تؤثر", "يؤثر", "توثر", "يوثر",
            "تغير", "يتغير",
            "تحتاج", "يحتاج",
            "تستخدم", "يستخدم",
            "يمكن", "ممكن",
            "work", "works", "working",
            "think", "thinks", "thinking",
            "happen", "happens",
            "affect", "affects",
            "cause", "causes"
        )

        fun isDistinctiveSemanticToken(
            relaxedToken: String,
            relaxedStem: String = LocalKnowledgeBase.stripArabicPrefixes(relaxedToken)
        ): Boolean {
            if (relaxedToken.isBlank() || relaxedToken in SeedKnowledgeCatalog.STOP_WORDS) return false
            if (relaxedToken in GENERIC_NON_DISTINCTIVE_PREDICATE_TOKENS ||
                relaxedStem in GENERIC_NON_DISTINCTIVE_PREDICATE_TOKENS
            ) {
                return false
            }
            return true
        }

        const val ACCEPTANCE_THRESHOLD: Int = 80

        /**
         * Exact token comparison from [LocalKnowledgeBase.matchPreNormalizedTokens] (`lines 294–326`).
         */
        fun matchPreNormalizedTokens(
            strictQToken: String,
            relQToken: String,
            strictKToken: String,
            relKToken: String
        ): Boolean {
            if (strictQToken == strictKToken) return true
            val strictQ = LocalKnowledgeBase.stripArabicPrefixes(strictQToken)
            val strictK = LocalKnowledgeBase.stripArabicPrefixes(strictKToken)
            if (strictQ.isNotEmpty() && strictQ == strictK) return true

            val relQ = LocalKnowledgeBase.stripArabicPrefixes(relQToken)
            val relK = LocalKnowledgeBase.stripArabicPrefixes(relKToken)
            if (relQ.isEmpty() || relK.isEmpty()) return false

            if (relQ in AMBIGUOUS_HOMOGRAPH_WORDS || relK in AMBIGUOUS_HOMOGRAPH_WORDS) {
                return strictQ == strictK
            }

            if (relQ.length >= 3 && relQ == relK) return true

            if (relQ !in PROTECTED_ROOT_WORDS && strictQ !in PROTECTED_ROOT_WORDS && relK.length >= 4) {
                val firstChar = relQ[0]
                if (relQ.length == relK.length + 1 &&
                    (firstChar == 'و' || firstChar == 'ب' || firstChar == 'ل' || firstChar == 'ف')
                ) {
                    val afterClitic = relQ.substring(1)
                    if (afterClitic == relK && afterClitic !in AMBIGUOUS_HOMOGRAPH_WORDS) {
                        return true
                    }
                }
            }

            return false
        }

        /**
         * Exact contiguous word-boundary phrase check from
         * [LocalKnowledgeBase.containsPhraseOnWordBoundary] (`lines 345–373`).
         */
        fun containsPhraseOnWordBoundary(
            strictQueryTokens: List<String>,
            normQueryTokens: List<String>,
            strictKwTokens: List<String>,
            normKwTokens: List<String>
        ): Boolean {
            if (strictKwTokens.isEmpty() || strictQueryTokens.size < strictKwTokens.size) return false
            if (strictQueryTokens.size != normQueryTokens.size || strictKwTokens.size != normKwTokens.size) {
                return false
            }

            for (i in 0..(strictQueryTokens.size - strictKwTokens.size)) {
                var allMatch = true
                for (j in strictKwTokens.indices) {
                    if (!matchPreNormalizedTokens(
                            strictQueryTokens[i + j],
                            normQueryTokens[i + j],
                            strictKwTokens[j],
                            normKwTokens[j]
                        )
                    ) {
                        allMatch = false
                        break
                    }
                }
                if (allMatch) return true
            }
            return false
        }
    }

    /**
     * Pre-parsed query representation so normalization and tokenization are performed once per query.
     */
    private data class PreparedQuery(
        val rawQuery: String,
        val strictQuery: String,
        val normQuery: String,
        val strictQueryTokens: List<String>,
        val normQueryTokens: List<String>,
        val contentQueryIndices: List<Int>
    )

    private fun prepareQuery(query: String): PreparedQuery? {
        val normQuery = LocalKnowledgeBase.normalizeArabic(query)
        if (normQuery.isBlank()) return null

        val strictQuery = LocalKnowledgeBase.normalizeArabicStrict(query)
        val strictQueryTokens = strictQuery.split(" ").filter { it.isNotBlank() }
        val normQueryTokens = normQuery.split(" ").filter { it.isNotBlank() }
        val contentQueryIndices = normQueryTokens.indices.filter {
            normQueryTokens[it] !in SeedKnowledgeCatalog.STOP_WORDS
        }

        return PreparedQuery(
            rawQuery = query,
            strictQuery = strictQuery,
            normQuery = normQuery,
            strictQueryTokens = strictQueryTokens,
            normQueryTokens = normQueryTokens,
            contentQueryIndices = contentQueryIndices
        )
    }

    /**
     * Executes indexed candidate retrieval and exact legacy scoring against [snapshot].
     */
    fun search(
        query: String,
        currentTopicId: String? = null,
        snapshot: InMemorySearchSnapshot = snapshotProvider()
    ): KnowledgeSearchResult {
        val prepared = prepareQuery(query)
            ?: return KnowledgeSearchResult(
                matchedTopic = null,
                matchedTopicId = null,
                rawScore = 0,
                finalScore = 0,
                orderedCandidates = emptyList(),
                candidateTopicIdsRetrievedByIndex = emptySet(),
                knowledgeGeneration = snapshot.knowledgeGeneration
            )

        val candidateTopicIndices = retrieveCandidateTopicIndices(prepared, currentTopicId, snapshot)
        return evaluateCandidates(
            prepared = prepared,
            currentTopicId = currentTopicId,
            snapshot = snapshot,
            candidateTopicIndices = candidateTopicIndices,
            useRawScoreCache = true
        )
    }

    /**
     * Executes exhaustive scoring across ALL topics in [snapshot] using the exact same
     * single-topic scoring function as [search], enabling Golden Parity verification between
     * Exhaustive Scoring and Indexed Candidate Scoring.
     */
    fun searchExhaustive(
        query: String,
        currentTopicId: String? = null,
        snapshot: InMemorySearchSnapshot = snapshotProvider()
    ): KnowledgeSearchResult {
        val prepared = prepareQuery(query)
            ?: return KnowledgeSearchResult(
                matchedTopic = null,
                matchedTopicId = null,
                rawScore = 0,
                finalScore = 0,
                orderedCandidates = emptyList(),
                candidateTopicIdsRetrievedByIndex = emptySet(),
                knowledgeGeneration = snapshot.knowledgeGeneration
            )

        val allTopicIndices = IntArray(snapshot.topicCount) { it }
        return evaluateCandidates(
            prepared = prepared,
            currentTopicId = currentTopicId,
            snapshot = snapshot,
            candidateTopicIndices = allTopicIndices,
            useRawScoreCache = false
        )
    }

    /**
     * Conservative candidate retrieval via [ReverseTokenIndex].
     *
     * Generates all lookup keys that can satisfy [matchPreNormalizedTokens] (`lines 294–326`):
     * 1. `strictToken`
     * 2. `strictStem = stripArabicPrefixes(strictToken)`
     * 3. `relaxedToken`
     * 4. `relaxedStem = stripArabicPrefixes(relaxedToken)`
     * 5. Clitic-stripped `afterClitic = relaxedStem.substring(1)` when `relaxedStem.length >= 5`,
     *    `firstChar in {'و','ب','ل','ف'}`, `relaxedStem !in PROTECTED_ROOT_WORDS`,
     *    `strictStem !in PROTECTED_ROOT_WORDS`, and `afterClitic !in AMBIGUOUS_HOMOGRAPH_WORDS`.
     * 6. Special capital rule trigger (`"عاصمة"`, `"capital"`, `"capitals"` -> includes `"capitals_arab"`).
     * 7. Active topic continuity candidate (`currentTopicId` and its canonical aliases).
     */
    internal fun retrieveCandidateTopicIndices(
        query: String,
        currentTopicId: String?,
        snapshot: InMemorySearchSnapshot
    ): IntArray {
        val prepared = prepareQuery(query) ?: return IntArray(0)
        return retrieveCandidateTopicIndices(prepared, currentTopicId, snapshot)
    }

    private fun retrieveCandidateTopicIndices(
        prepared: PreparedQuery,
        currentTopicId: String?,
        snapshot: InMemorySearchSnapshot
    ): IntArray {
        val topicCount = snapshot.topicCount
        if (topicCount == 0) return IntArray(0)

        val candidateMask = BooleanArray(topicCount)
        var candidateCount = 0

        fun markCandidate(topicIdx: Int) {
            if (topicIdx in 0 until topicCount && !candidateMask[topicIdx]) {
                candidateMask[topicIdx] = true
                candidateCount++
            }
        }

        fun markAll(indices: IntArray?) {
            if (indices == null) return
            for (idx in indices) {
                markCandidate(idx)
            }
        }

        val reverseIndex = snapshot.reverseTokenIndex
        val tokenLen = prepared.normQueryTokens.size

        for (i in 0 until tokenLen) {
            val strictQToken = prepared.strictQueryTokens.getOrElse(i) { prepared.normQueryTokens[i] }
            val relQToken = prepared.normQueryTokens[i]

            val strictQStem = LocalKnowledgeBase.stripArabicPrefixes(strictQToken)
            val relQStem = LocalKnowledgeBase.stripArabicPrefixes(relQToken)

            // 1. Direct strict token match
            markAll(reverseIndex.getTopicsByStrictToken(strictQToken))

            // 2. Strict stem match (after definite-article prefix stripping)
            if (strictQStem.isNotEmpty()) {
                markAll(reverseIndex.getTopicsByStrictStem(strictQStem))
                markAll(reverseIndex.getTopicsByStrictToken(strictQStem))
            }

            // 3. Relaxed stem / token match (only when not blocked by homograph or length rules)
            if (relQStem.isNotEmpty() && relQStem !in AMBIGUOUS_HOMOGRAPH_WORDS && relQStem.length >= 3) {
                markAll(reverseIndex.getTopicsByRelaxedStem(relQStem))
                markAll(reverseIndex.getTopicsByRelaxedToken(relQStem))
            }
            if (relQToken !in AMBIGUOUS_HOMOGRAPH_WORDS && (relQToken.length >= 3 || tokenLen == 1)) {
                markAll(reverseIndex.getTopicsByRelaxedToken(relQToken))
            }

            // 4. Single-letter clitic variant (و، ب، ل، ف) under exact legacy guards (lines 315-323)
            if (relQStem !in PROTECTED_ROOT_WORDS &&
                strictQStem !in PROTECTED_ROOT_WORDS &&
                relQStem.length >= 5
            ) {
                val firstChar = relQStem[0]
                if (firstChar == 'و' || firstChar == 'ب' || firstChar == 'ل' || firstChar == 'ف') {
                    val afterClitic = relQStem.substring(1)
                    if (afterClitic.length >= 4 && afterClitic !in AMBIGUOUS_HOMOGRAPH_WORDS) {
                        markAll(reverseIndex.getTopicsByRelaxedStem(afterClitic))
                        markAll(reverseIndex.getTopicsByRelaxedToken(afterClitic))
                    }
                }
            }
        }

        // 5. Special check for "capitals_arab" when query mentions "عاصمة" / "capital" / "capitals"
        val capitalsIdx = snapshot.topicOrderIndex["capitals_arab"]
        if (capitalsIdx != null && !candidateMask[capitalsIdx]) {
            val mentionsCapitalWord = prepared.strictQueryTokens.any {
                LocalKnowledgeBase.tokensMatch(it, "عاصمة") || it == "capital" || it == "capitals"
            }
            if (mentionsCapitalWord) {
                markCandidate(capitalsIdx)
            }
        }

        // 6. Include currentTopicId (and canonical alias) if present
        if (currentTopicId != null) {
            snapshot.topicOrderIndex[currentTopicId]?.let { markCandidate(it) }
            when (currentTopicId) {
                "travel" -> snapshot.topicOrderIndex["salalah_tourism"]?.let { markCandidate(it) }
                "health_wellness" -> snapshot.topicOrderIndex["sleep_wellness"]?.let { markCandidate(it) }
            }
        }

        val result = IntArray(candidateCount)
        var pos = 0
        for (idx in 0 until topicCount) {
            if (candidateMask[idx]) {
                result[pos++] = idx
            }
        }
        return result
    }

    private fun evaluateCandidates(
        prepared: PreparedQuery,
        currentTopicId: String?,
        snapshot: InMemorySearchSnapshot,
        candidateTopicIndices: IntArray,
        useRawScoreCache: Boolean
    ): KnowledgeSearchResult {
        val candidateIdSet = LinkedHashSet<String>(candidateTopicIndices.size)
        for (tIdx in candidateTopicIndices) {
            snapshot.orderedTopicIds.getOrNull(tIdx)?.let { candidateIdSet.add(it) }
        }

        val cacheKey = buildRawScoreCacheKey(
            knowledgeGeneration = snapshot.knowledgeGeneration,
            strictQuery = prepared.strictQuery,
            normQuery = prepared.normQuery
        )

        val cachedRawScores: Map<String, Int>? = if (useRawScoreCache && cache != null) {
            cache.getRawScores(cacheKey)
        } else {
            null
        }

        val rawScoresByTopicIdx = IntArray(snapshot.topicCount)
        val matchedKwCountByTopicIdx = IntArray(snapshot.topicCount)

        if (cachedRawScores != null) {
            for (topicIdx in candidateTopicIndices) {
                val topicId = snapshot.orderedTopicIds[topicIdx]
                val packed = cachedRawScores[topicId] ?: 0
                rawScoresByTopicIdx[topicIdx] = packed ushr 8
                matchedKwCountByTopicIdx[topicIdx] = packed and 0xFF
            }
        } else {
            val computedPositiveRawScores = LinkedHashMap<String, Int>()
            for (topicIdx in candidateTopicIndices) {
                val topicId = snapshot.orderedTopicIds[topicIdx]
                val kwEntries = snapshot.keywordEntriesByTopic[topicId].orEmpty()
                val (rawScore, matchedKwCount) = scoreSingleTopicRaw(
                    prepared = prepared,
                    topicId = topicId,
                    kwEntries = kwEntries
                )
                rawScoresByTopicIdx[topicIdx] = rawScore
                matchedKwCountByTopicIdx[topicIdx] = matchedKwCount
                if (rawScore > 0) {
                    val packed = (rawScore shl 8) or (matchedKwCount.coerceIn(0, 255))
                    computedPositiveRawScores[topicId] = packed
                }
            }
            if (useRawScoreCache && cache != null) {
                cache.putRawScores(cacheKey, computedPositiveRawScores)
            }
        }

        // Apply continuity tie-breaker using the exact rules of LocalKnowledgeBase (lines 488-510)
        val hasContinuity = currentTopicId != null && LocalKnowledgeBase.hasContinuitySignal(prepared.rawQuery)
        var maxRawScore = 0
        for (topicIdx in candidateTopicIndices) {
            val rScore = rawScoresByTopicIdx[topicIdx]
            if (rScore > maxRawScore) {
                maxRawScore = rScore
            }
        }

        var bestTopic: LocalKnowledgeBase.KnowledgeTopic? = null
        var bestTopicIdx = -1
        var bestRawScore = 0
        var bestScore = 0
        val scoredCandidates = ArrayList<ScoredTopicCandidate>(candidateTopicIndices.size)

        // Iterate in deterministic topicOrderIndex order (candidateTopicIndices is already sorted ASC)
        for (topicIdx in candidateTopicIndices) {
            val topic = snapshot.orderedTopics[topicIdx]
            val rawScore = rawScoresByTopicIdx[topicIdx]
            var finalScore = rawScore

            if (finalScore >= ACCEPTANCE_THRESHOLD && hasContinuity && currentTopicId != null) {
                val isSameTopic = topic.id == currentTopicId ||
                    (currentTopicId == "travel" && topic.id == "salalah_tourism") ||
                    (currentTopicId == "health_wellness" && topic.id == "sleep_wellness")
                if (isSameTopic && finalScore + 10 >= maxRawScore) {
                    finalScore += 12
                }
            }

            if (rawScore > 0 || finalScore > 0) {
                scoredCandidates.add(
                    ScoredTopicCandidate(
                        topicId = topic.id,
                        topicOrderIndex = topicIdx,
                        rawScore = rawScore,
                        finalScore = finalScore,
                        matchedKeywordsCount = matchedKwCountByTopicIdx[topicIdx]
                    )
                )
            }

            // Strict `>` preserves earlier topicOrderIndex on exact score ties (line 506)
            if (finalScore > bestScore) {
                bestScore = finalScore
                bestRawScore = rawScore
                bestTopic = topic
                bestTopicIdx = topicIdx
            }
        }

        val orderedCandidates = scoredCandidates.sortedWith(
            compareByDescending<ScoredTopicCandidate> { it.finalScore }
                .thenBy { it.topicOrderIndex }
        )

        val winningTopic = if (bestScore >= ACCEPTANCE_THRESHOLD) bestTopic else null
        return KnowledgeSearchResult(
            matchedTopic = winningTopic,
            matchedTopicId = winningTopic?.id,
            rawScore = if (winningTopic != null) bestRawScore else 0,
            finalScore = if (winningTopic != null) bestScore else 0,
            orderedCandidates = orderedCandidates,
            candidateTopicIdsRetrievedByIndex = candidateIdSet,
            knowledgeGeneration = snapshot.knowledgeGeneration
        )
    }

    /**
     * Exact single-topic raw score calculation extracted line-by-line from
     * [LocalKnowledgeBase.findMatchingTopic] (`lines 415–484`).
     */
    private fun scoreSingleTopicRaw(
        prepared: PreparedQuery,
        topicId: String,
        kwEntries: List<SnapshotKeywordEntry>
    ): Pair<Int, Int> {
        var topicMaxScore = 0
        var matchedKeywordsCount = 0

        val strictQuery = prepared.strictQuery
        val normQuery = prepared.normQuery
        val strictQueryTokens = prepared.strictQueryTokens
        val normQueryTokens = prepared.normQueryTokens
        val contentQueryIndices = prepared.contentQueryIndices

        for (kw in kwEntries) {
            val normKw = kw.relaxedNormalizedPhrase
            if (normKw.isBlank()) continue
            val strictKw = kw.strictNormalizedPhrase
            val strictKwTokens = kw.strictTokens
            val normKwTokens = kw.relaxedTokens
            val contentKwIndices = kw.contentTokenPositions

            var kwScore = 0

            // 1. Exact full query match (lines 429-433)
            if (strictQuery == strictKw) {
                kwScore = 204 + normKwTokens.size * 30
            } else if (normQuery == normKw && normKw !in AMBIGUOUS_HOMOGRAPH_WORDS) {
                kwScore = 200 + normKwTokens.size * 30
            }
            // 2. Contiguous phrase match on word boundaries (lines 435-443)
            else if (containsPhraseOnWordBoundary(strictQueryTokens, normQueryTokens, strictKwTokens, normKwTokens)) {
                val exactStrictBonus = if (strictQuery.contains(strictKw)) 4 else 0
                kwScore = if (normKwTokens.size >= 2) {
                    val hasDistinctiveContentToken = contentKwIndices.any { kIdx ->
                        val tok = normKwTokens.getOrElse(kIdx) { "" }
                        isDistinctiveSemanticToken(tok)
                    }
                    if (hasDistinctiveContentToken) {
                        130 + normKwTokens.size * 25 + normKw.length + exactStrictBonus
                    } else {
                        0
                    }
                } else {
                    val tokenLen = LocalKnowledgeBase.stripArabicPrefixes(normKwTokens[0]).length
                    if (tokenLen <= 2 && normKwTokens[0] != "ai") 40 else 95 + tokenLen + exactStrictBonus
                }
            }
            // 3. Order-independent multi-token match (lines 445-458)
            else if (contentKwIndices.size >= 2 && contentQueryIndices.isNotEmpty()) {
                val hasDistinctiveContentToken = contentKwIndices.any { kIdx ->
                    val tok = normKwTokens.getOrElse(kIdx) { "" }
                    isDistinctiveSemanticToken(tok)
                }
                val allContentMatched = hasDistinctiveContentToken && contentKwIndices.all { kIdx ->
                    val sKwTok = strictKwTokens.getOrElse(kIdx) { normKwTokens[kIdx] }
                    val nKwTok = normKwTokens[kIdx]
                    contentQueryIndices.any { qIdx ->
                        val sQTok = strictQueryTokens.getOrElse(qIdx) { normQueryTokens[qIdx] }
                        val nQTok = normQueryTokens[qIdx]
                        matchPreNormalizedTokens(sQTok, nQTok, sKwTok, nKwTok)
                    }
                }
                if (allContentMatched) {
                    kwScore = 115 + contentKwIndices.size * 20
                }
            }

            if (kwScore > 0) {
                matchedKeywordsCount++
                if (kwScore > topicMaxScore) {
                    topicMaxScore = kwScore
                }
            }
        }

        // Multi-keyword specificity bonus (lines 469-471)
        if (topicMaxScore >= ACCEPTANCE_THRESHOLD && matchedKeywordsCount > 1) {
            topicMaxScore += minOf((matchedKeywordsCount - 1) * 12, 24)
        }

        // Special check for capital city inquiries mentioning any indexed country + "عاصمة"/"capital" (lines 474-481)
        if (topicId == "capitals_arab" &&
            strictQueryTokens.any {
                LocalKnowledgeBase.tokensMatch(it, "عاصمة") || it == "capital" || it == "capitals"
            }
        ) {
            val mentionsCountry = SeedKnowledgeCatalog.COUNTRY_CAPITALS_MAP.keys.any { countryKey ->
                strictQueryTokens.any { qTok -> LocalKnowledgeBase.tokensMatch(qTok, countryKey) }
            }
            if (mentionsCountry && topicMaxScore < 165) {
                topicMaxScore = 165
            }
        }

        return topicMaxScore to matchedKeywordsCount
    }

    internal fun buildRawScoreCacheKey(
        knowledgeGeneration: Long,
        strictQuery: String,
        normQuery: String
    ): String = "gen=$knowledgeGeneration|s=$strictQuery|r=$normQuery"
}
