package com.example.core.intelligence.knowledge.search

import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog
import com.example.core.intelligence.knowledge.catalog.SeedTopicCatalogEntry
import com.example.core.intelligence.knowledge.db.dao.KnowledgePriorityAndWinnerPolicy
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle

/**
 * Immutable pre-normalized representation of a single token at [tokenPosition] inside a
 * [SnapshotKeywordEntry].
 */
data class SnapshotKeywordToken(
    val tokenPosition: Int,
    val strictToken: String,
    val strictStem: String,
    val relaxedToken: String,
    val relaxedStem: String,
    val isStopWord: Boolean
)

/**
 * Immutable pre-normalized representation of a complete keyword phrase and its ordered tokens
 * within [InMemorySearchSnapshot].
 *
 * Retains every field required by the exact scoring formula in
 * [LocalKnowledgeBase.findMatchingTopic] (`lines 414–466`).
 */
data class SnapshotKeywordEntry(
    val globalKeywordIndex: Int,
    val topicId: String,
    val topicOrderIndex: Int,
    val keywordOrder: Int,
    val originalKeyword: String,
    val strictNormalizedPhrase: String,
    val relaxedNormalizedPhrase: String,
    val relaxedCharLength: Int,
    val tokenCount: Int,
    val contentTokenCount: Int,
    val tokens: List<SnapshotKeywordToken>,
    val strictTokens: List<String>,
    val relaxedTokens: List<String>,
    val contentTokenPositions: List<Int>
)

/**
 * Immutable reverse token index mapping pre-normalized token/stem keys to sorted arrays of
 * `topicOrderIndex` and `globalKeywordIndex` postings.
 *
 * Uses four specialized lookup maps corresponding to the four token comparison branches in
 * [LocalKnowledgeBase.matchPreNormalizedTokens] (`lines 294–326`):
 * 1. [strictTokenToTopicIndices] (`strictQToken == strictKToken`)
 * 2. [strictStemToTopicIndices] (`strictQ == strictK`)
 * 3. [relaxedTokenToTopicIndices] (`relQToken == relKToken`)
 * 4. [relaxedStemToTopicIndices] (`relQ == relK` and clitic-stripped `afterClitic == relK`)
 */
class ReverseTokenIndex internal constructor(
    private val strictTokenToTopicIndices: Map<String, IntArray>,
    private val strictStemToTopicIndices: Map<String, IntArray>,
    private val relaxedTokenToTopicIndices: Map<String, IntArray>,
    private val relaxedStemToTopicIndices: Map<String, IntArray>,
    private val strictTokenToKeywordIndices: Map<String, IntArray>,
    private val strictStemToKeywordIndices: Map<String, IntArray>,
    private val relaxedStemToKeywordIndices: Map<String, IntArray>
) {
    fun getTopicsByStrictToken(strictToken: String): IntArray? =
        strictTokenToTopicIndices[strictToken]

    fun getTopicsByStrictStem(strictStem: String): IntArray? =
        strictStemToTopicIndices[strictStem]

    fun getTopicsByRelaxedToken(relaxedToken: String): IntArray? =
        relaxedTokenToTopicIndices[relaxedToken]

    fun getTopicsByRelaxedStem(relaxedStem: String): IntArray? =
        relaxedStemToTopicIndices[relaxedStem]

    fun getKeywordsByStrictToken(strictToken: String): IntArray? =
        strictTokenToKeywordIndices[strictToken]

    fun getKeywordsByStrictStem(strictStem: String): IntArray? =
        strictStemToKeywordIndices[strictStem]

    fun getKeywordsByRelaxedStem(relaxedStem: String): IntArray? =
        relaxedStemToKeywordIndices[relaxedStem]

    val indexedStrictTokenCount: Int get() = strictTokenToTopicIndices.size
    val indexedStrictStemCount: Int get() = strictStemToTopicIndices.size
    val indexedRelaxedStemCount: Int get() = relaxedStemToTopicIndices.size
}

/**
 * Lightweight, immutable identity record of the winning active version for a topic in
 * [InMemorySearchSnapshot].
 *
 * Ensures that on-demand detailed content and follow-up branch lookups are strictly
 * bound to the exact `(topicId, packId, contentVersion)` that won active selection,
 * preventing cross-pack or stale-version leakage.
 */
data class ActiveTopicIdentity(
    val topicId: String,
    val packId: String,
    val contentVersion: Int,
    val packPriority: Int,
    val sourceType: String,
    val isCoreTopic: Boolean
) {
    val compositeDetailKey: String
        get() = "$topicId@$packId#v$contentVersion"
}

/**
 * Immutable in-memory search snapshot used for zero-I/O synchronous topic search.
 *
 * Contains only lightweight search metadata, topic headers ([LocalKnowledgeBase.KnowledgeTopic]),
 * active winning identities ([ActiveTopicIdentity]), pre-normalized keyword entries
 * ([SnapshotKeywordEntry]), and the [ReverseTokenIndex].
 * Never holds heavy encyclopedic full-text details or follow-up branch trees.
 */
class InMemorySearchSnapshot private constructor(
    val knowledgeGeneration: Long,
    val topicsById: Map<String, LocalKnowledgeBase.KnowledgeTopic>,
    val activeTopicIdentitiesById: Map<String, ActiveTopicIdentity>,
    val orderedTopicIds: List<String>,
    val orderedTopics: List<LocalKnowledgeBase.KnowledgeTopic>,
    val topicOrderIndex: Map<String, Int>,
    val keywordEntries: List<SnapshotKeywordEntry>,
    val keywordEntriesByTopic: Map<String, List<SnapshotKeywordEntry>>,
    val reverseTokenIndex: ReverseTokenIndex
) {
    val topicCount: Int get() = orderedTopics.size
    val keywordCount: Int get() = keywordEntries.size

    fun getActiveIdentity(topicId: String): ActiveTopicIdentity? =
        activeTopicIdentitiesById[topicId]

    companion object {

        /**
         * Builds an [InMemorySearchSnapshot] directly from the 11 built-in seed topics in
         * [SeedKnowledgeCatalog] with zero disk or database I/O.
         */
        fun fromSeedCatalog(
            knowledgeGeneration: Long = 1L,
            seedEntries: List<SeedTopicCatalogEntry> = SeedKnowledgeCatalog.coreTopics
        ): InMemorySearchSnapshot {
            val sortedEntries = seedEntries.sortedWith(
                compareBy<SeedTopicCatalogEntry> { it.topicOrderIndex }.thenBy { it.topicId }
            )

            val orderedTopics = ArrayList<LocalKnowledgeBase.KnowledgeTopic>(sortedEntries.size)
            val orderedIds = ArrayList<String>(sortedEntries.size)
            val topicsMap = LinkedHashMap<String, LocalKnowledgeBase.KnowledgeTopic>(sortedEntries.size)
            val identitiesMap = LinkedHashMap<String, ActiveTopicIdentity>(sortedEntries.size)
            val orderIndexMap = LinkedHashMap<String, Int>(sortedEntries.size)
            val allKeywordEntries = ArrayList<SnapshotKeywordEntry>()
            val keywordsByTopic = LinkedHashMap<String, List<SnapshotKeywordEntry>>(sortedEntries.size)

            for ((denseTopicIdx, entry) in sortedEntries.withIndex()) {
                val legacyTopic = entry.toLegacyKnowledgeTopic()
                orderedTopics.add(legacyTopic)
                orderedIds.add(entry.topicId)
                topicsMap[entry.topicId] = legacyTopic
                identitiesMap[entry.topicId] = ActiveTopicIdentity(
                    topicId = entry.topicId,
                    packId = SeedKnowledgeCatalog.CORE_SEED_PACK_ID,
                    contentVersion = SeedKnowledgeCatalog.CORE_SEED_CONTENT_VERSION,
                    packPriority = SeedKnowledgeCatalog.CORE_SEED_PACK_PRIORITY,
                    sourceType = SeedKnowledgeCatalog.CORE_SEED_SOURCE_TYPE,
                    isCoreTopic = true
                )
                orderIndexMap[entry.topicId] = denseTopicIdx

                val topicKwEntries = ArrayList<SnapshotKeywordEntry>(entry.keywords.size)
                for ((kwOrder, rawKw) in entry.keywords.withIndex()) {
                    val snapshotKw = buildSnapshotKeywordEntryFromRaw(
                        globalKeywordIndex = allKeywordEntries.size,
                        topicId = entry.topicId,
                        denseTopicIdx = denseTopicIdx,
                        keywordOrder = kwOrder,
                        rawKeyword = rawKw
                    ) ?: continue
                    allKeywordEntries.add(snapshotKw)
                    topicKwEntries.add(snapshotKw)
                }
                keywordsByTopic[entry.topicId] = topicKwEntries.toList()
            }

            val reverseIndex = buildReverseTokenIndex(allKeywordEntries)

            return InMemorySearchSnapshot(
                knowledgeGeneration = knowledgeGeneration,
                topicsById = topicsMap.toMap(),
                activeTopicIdentitiesById = identitiesMap.toMap(),
                orderedTopicIds = orderedIds.toList(),
                orderedTopics = orderedTopics.toList(),
                topicOrderIndex = orderIndexMap.toMap(),
                keywordEntries = allKeywordEntries.toList(),
                keywordEntriesByTopic = keywordsByTopic.toMap(),
                reverseTokenIndex = reverseIndex
            )
        }

        /**
         * Builds an [InMemorySearchSnapshot] from active [TopicStorageBundle] records loaded
         * from Room on `Dispatchers.IO`.
         */
        fun fromTopicStorageBundles(
            knowledgeGeneration: Long,
            bundles: List<TopicStorageBundle>
        ): InMemorySearchSnapshot {
            require(knowledgeGeneration > 0L) { "knowledgeGeneration must be > 0" }
            require(bundles.isNotEmpty()) { "Cannot build InMemorySearchSnapshot from empty bundles" }

            val winningBundles = KnowledgePriorityAndWinnerPolicy.selectWinningTopicBundles(bundles)
            require(winningBundles.isNotEmpty()) {
                "Cannot build InMemorySearchSnapshot when no enabled winning topic bundles exist"
            }

            val sortedBundles = winningBundles.sortedWith(
                compareBy<TopicStorageBundle> { it.header.topicOrderIndex }
                    .thenBy { it.header.topicId }
            )

            val orderedTopics = ArrayList<LocalKnowledgeBase.KnowledgeTopic>(sortedBundles.size)
            val orderedIds = ArrayList<String>(sortedBundles.size)
            val topicsMap = LinkedHashMap<String, LocalKnowledgeBase.KnowledgeTopic>(sortedBundles.size)
            val identitiesMap = LinkedHashMap<String, ActiveTopicIdentity>(sortedBundles.size)
            val orderIndexMap = LinkedHashMap<String, Int>(sortedBundles.size)
            val allKeywordEntries = ArrayList<SnapshotKeywordEntry>()
            val keywordsByTopic = LinkedHashMap<String, List<SnapshotKeywordEntry>>(sortedBundles.size)

            for ((denseTopicIdx, bundle) in sortedBundles.withIndex()) {
                val sortedKws = bundle.keywordsWithTokens.sortedBy { it.keyword.keywordOrder }
                val originalKeywords = sortedKws.map { it.keyword.originalKeyword }
                val legacyTopic = bundle.header.toLegacyKnowledgeTopic(originalKeywords)

                val topicId = bundle.header.topicId
                orderedTopics.add(legacyTopic)
                orderedIds.add(topicId)
                topicsMap[topicId] = legacyTopic
                identitiesMap[topicId] = ActiveTopicIdentity(
                    topicId = topicId,
                    packId = bundle.header.packId,
                    contentVersion = bundle.header.contentVersion,
                    packPriority = bundle.header.packPriority,
                    sourceType = bundle.header.sourceType,
                    isCoreTopic = bundle.header.isCoreTopic
                )
                orderIndexMap[topicId] = denseTopicIdx

                val topicKwEntries = ArrayList<SnapshotKeywordEntry>(sortedKws.size)
                for (kwBundle in sortedKws) {
                    val kwEntity = kwBundle.keyword
                    val sortedTokenEntities = kwBundle.tokens.sortedBy { it.tokenPosition }

                    val snapshotKw = if (sortedTokenEntities.isNotEmpty() &&
                        sortedTokenEntities.size == kwEntity.tokenCount
                    ) {
                        val snapshotTokens = sortedTokenEntities.map { tok ->
                            SnapshotKeywordToken(
                                tokenPosition = tok.tokenPosition,
                                strictToken = tok.strictToken,
                                strictStem = tok.strictStem,
                                relaxedToken = tok.relaxedToken,
                                relaxedStem = tok.relaxedStem,
                                isStopWord = tok.relaxedToken in SeedKnowledgeCatalog.STOP_WORDS
                            )
                        }
                        val strictTokens = snapshotTokens.map { it.strictToken }
                        val relaxedTokens = snapshotTokens.map { it.relaxedToken }
                        val contentPositions = snapshotTokens
                            .filter { !it.isStopWord }
                            .map { it.tokenPosition }
                            .ifEmpty { snapshotTokens.map { it.tokenPosition } }

                        SnapshotKeywordEntry(
                            globalKeywordIndex = allKeywordEntries.size,
                            topicId = topicId,
                            topicOrderIndex = denseTopicIdx,
                            keywordOrder = kwEntity.keywordOrder,
                            originalKeyword = kwEntity.originalKeyword,
                            strictNormalizedPhrase = kwEntity.strictNormalizedPhrase,
                            relaxedNormalizedPhrase = kwEntity.relaxedNormalizedPhrase,
                            relaxedCharLength = kwEntity.relaxedCharLength,
                            tokenCount = kwEntity.tokenCount,
                            contentTokenCount = contentPositions.size,
                            tokens = snapshotTokens,
                            strictTokens = strictTokens,
                            relaxedTokens = relaxedTokens,
                            contentTokenPositions = contentPositions
                        )
                    } else {
                        buildSnapshotKeywordEntryFromRaw(
                            globalKeywordIndex = allKeywordEntries.size,
                            topicId = topicId,
                            denseTopicIdx = denseTopicIdx,
                            keywordOrder = kwEntity.keywordOrder,
                            rawKeyword = kwEntity.originalKeyword
                        ) ?: continue
                    }

                    allKeywordEntries.add(snapshotKw)
                    topicKwEntries.add(snapshotKw)
                }

                keywordsByTopic[topicId] = topicKwEntries.toList()
            }

            val reverseIndex = buildReverseTokenIndex(allKeywordEntries)

            return InMemorySearchSnapshot(
                knowledgeGeneration = knowledgeGeneration,
                topicsById = topicsMap.toMap(),
                activeTopicIdentitiesById = identitiesMap.toMap(),
                orderedTopicIds = orderedIds.toList(),
                orderedTopics = orderedTopics.toList(),
                topicOrderIndex = orderIndexMap.toMap(),
                keywordEntries = allKeywordEntries.toList(),
                keywordEntriesByTopic = keywordsByTopic.toMap(),
                reverseTokenIndex = reverseIndex
            )
        }

        private fun buildSnapshotKeywordEntryFromRaw(
            globalKeywordIndex: Int,
            topicId: String,
            denseTopicIdx: Int,
            keywordOrder: Int,
            rawKeyword: String
        ): SnapshotKeywordEntry? {
            val relaxedPhrase = LocalKnowledgeBase.normalizeArabic(rawKeyword)
            if (relaxedPhrase.isBlank()) return null
            val strictPhrase = LocalKnowledgeBase.normalizeArabicStrict(rawKeyword)
            val strictTokens = strictPhrase.split(" ").filter { it.isNotBlank() }
            val relaxedTokens = relaxedPhrase.split(" ").filter { it.isNotBlank() }
            if (relaxedTokens.isEmpty()) return null

            val tokens = relaxedTokens.indices.map { pos ->
                val sTok = strictTokens.getOrElse(pos) { relaxedTokens[pos] }
                val rTok = relaxedTokens[pos]
                val sStem = LocalKnowledgeBase.stripArabicPrefixes(sTok)
                val rStem = LocalKnowledgeBase.stripArabicPrefixes(rTok)
                SnapshotKeywordToken(
                    tokenPosition = pos,
                    strictToken = sTok,
                    strictStem = sStem.ifEmpty { sTok },
                    relaxedToken = rTok,
                    relaxedStem = rStem.ifEmpty { rTok },
                    isStopWord = rTok in SeedKnowledgeCatalog.STOP_WORDS
                )
            }

            val contentPositions = tokens
                .filter { !it.isStopWord }
                .map { it.tokenPosition }
                .ifEmpty { tokens.map { it.tokenPosition } }

            return SnapshotKeywordEntry(
                globalKeywordIndex = globalKeywordIndex,
                topicId = topicId,
                topicOrderIndex = denseTopicIdx,
                keywordOrder = keywordOrder,
                originalKeyword = rawKeyword,
                strictNormalizedPhrase = strictPhrase,
                relaxedNormalizedPhrase = relaxedPhrase,
                relaxedCharLength = relaxedPhrase.length,
                tokenCount = relaxedTokens.size,
                contentTokenCount = contentPositions.size,
                tokens = tokens,
                strictTokens = strictTokens,
                relaxedTokens = relaxedTokens,
                contentTokenPositions = contentPositions
            )
        }

        private fun buildReverseTokenIndex(
            keywordEntries: List<SnapshotKeywordEntry>
        ): ReverseTokenIndex {
            val strictTokTopics = HashMap<String, MutableSet<Int>>()
            val strictStemTopics = HashMap<String, MutableSet<Int>>()
            val relaxedTokTopics = HashMap<String, MutableSet<Int>>()
            val relaxedStemTopics = HashMap<String, MutableSet<Int>>()

            val strictTokKeywords = HashMap<String, MutableSet<Int>>()
            val strictStemKeywords = HashMap<String, MutableSet<Int>>()
            val relaxedStemKeywords = HashMap<String, MutableSet<Int>>()

            for (kw in keywordEntries) {
                val tIdx = kw.topicOrderIndex
                val kIdx = kw.globalKeywordIndex
                for (tok in kw.tokens) {
                    strictTokTopics.getOrPut(tok.strictToken) { LinkedHashSet() }.add(tIdx)
                    strictStemTopics.getOrPut(tok.strictStem) { LinkedHashSet() }.add(tIdx)
                    relaxedTokTopics.getOrPut(tok.relaxedToken) { LinkedHashSet() }.add(tIdx)
                    relaxedStemTopics.getOrPut(tok.relaxedStem) { LinkedHashSet() }.add(tIdx)

                    strictTokKeywords.getOrPut(tok.strictToken) { LinkedHashSet() }.add(kIdx)
                    strictStemKeywords.getOrPut(tok.strictStem) { LinkedHashSet() }.add(kIdx)
                    relaxedStemKeywords.getOrPut(tok.relaxedStem) { LinkedHashSet() }.add(kIdx)
                }
            }

            fun Map<String, MutableSet<Int>>.toSortedIntArrayMap(): Map<String, IntArray> =
                mapValues { (_, set) -> set.sorted().toIntArray() }

            return ReverseTokenIndex(
                strictTokenToTopicIndices = strictTokTopics.toSortedIntArrayMap(),
                strictStemToTopicIndices = strictStemTopics.toSortedIntArrayMap(),
                relaxedTokenToTopicIndices = relaxedTokTopics.toSortedIntArrayMap(),
                relaxedStemToTopicIndices = relaxedStemTopics.toSortedIntArrayMap(),
                strictTokenToKeywordIndices = strictTokKeywords.toSortedIntArrayMap(),
                strictStemToKeywordIndices = strictStemKeywords.toSortedIntArrayMap(),
                relaxedStemToKeywordIndices = relaxedStemKeywords.toSortedIntArrayMap()
            )
        }
    }
}
