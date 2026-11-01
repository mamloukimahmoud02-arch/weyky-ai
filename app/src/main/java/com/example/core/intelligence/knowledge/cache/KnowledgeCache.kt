package com.example.core.intelligence.knowledge.cache

import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.db.entity.KnowledgeFollowUpBranchEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicDetailEntity

/**
 * Value bundle stored in the Detail Cache combining a topic's extended detail record
 * and its ordered follow-up branches.
 */
data class CachedTopicDetailBundle(
    val detail: KnowledgeTopicDetailEntity,
    val followUpBranches: List<KnowledgeFollowUpBranchEntity> = emptyList()
)

/**
 * Snapshot of accounted byte metrics, hit/miss statistics, and oversized rejection counts
 * for a single LRU cache partition.
 */
data class CachePartitionStats(
    val name: String,
    val maxAccountedBudgetBytes: Int,
    val maxSingleItemBytes: Int,
    val currentAccountedBytes: Int,
    val entryCount: Int,
    val hitCount: Long,
    val missCount: Long,
    val evictionCount: Long,
    val oversizedRejectedCount: Long
)

/**
 * Aggregated accounted budget report across all three [KnowledgeCache] partitions.
 *
 * Explicitly separates:
 * - [totalAccountedBudgetBytes] / [currentTotalAccountedBytes]: Deterministic accounted bytes
 *   calculated by [KnowledgeByteSizeEstimator].
 * - Actual JVM/ART Measured Heap Footprint: Must be measured separately at runtime and is NOT
 *   claimed by accounted byte counters.
 */
data class KnowledgeCacheStats(
    val headerStats: CachePartitionStats,
    val detailStats: CachePartitionStats,
    val rawScoreStats: CachePartitionStats
) {
    val totalAccountedBudgetBytes: Int
        get() = headerStats.maxAccountedBudgetBytes +
            detailStats.maxAccountedBudgetBytes +
            rawScoreStats.maxAccountedBudgetBytes

    val currentTotalAccountedBytes: Int
        get() = headerStats.currentAccountedBytes +
            detailStats.currentAccountedBytes +
            rawScoreStats.currentAccountedBytes
}

/**
 * Deterministic byte size estimator used for [KnowledgeCache] accounting.
 */
object KnowledgeByteSizeEstimator {
    private const val OBJECT_HEADER_BYTES = 32
    private const val STRING_HEADER_BYTES = 24
    private const val MAP_ENTRY_BYTES = 24

    fun estimateStringBytes(value: String?): Int {
        if (value == null) return 0
        return STRING_HEADER_BYTES + (value.length * 2)
    }

    fun estimateTopicHeaderEntryBytes(
        key: String,
        topic: LocalKnowledgeBase.KnowledgeTopic
    ): Int {
        var bytes = OBJECT_HEADER_BYTES + estimateStringBytes(key)
        bytes += estimateStringBytes(topic.id)
        bytes += estimateStringBytes(topic.arabicSummary)
        bytes += estimateStringBytes(topic.englishSummary)
        for (kw in topic.keywords) {
            bytes += estimateStringBytes(kw)
        }
        for (option in topic.suggestedFollowUps) {
            bytes += OBJECT_HEADER_BYTES
            bytes += estimateStringBytes(option.title)
            bytes += estimateStringBytes(option.queryToExecute)
        }
        return bytes
    }

    fun estimateTopicDetailEntryBytes(
        key: String,
        bundle: CachedTopicDetailBundle
    ): Int {
        var bytes = OBJECT_HEADER_BYTES + estimateStringBytes(key)
        val detail = bundle.detail
        bytes += estimateStringBytes(detail.topicId)
        bytes += estimateStringBytes(detail.packId)
        bytes += estimateStringBytes(detail.detailedArabic)
        bytes += estimateStringBytes(detail.detailedEnglish)
        bytes += estimateStringBytes(detail.relatedTopicIdsJson)
        bytes += estimateStringBytes(detail.examplesJson)
        bytes += estimateStringBytes(detail.additionalContentJson)
        bytes += 8 // lastReviewed Long
        for (branch in bundle.followUpBranches) {
            bytes += OBJECT_HEADER_BYTES
            bytes += estimateStringBytes(branch.branchId)
            bytes += estimateStringBytes(branch.topicId)
            bytes += estimateStringBytes(branch.packId)
            bytes += estimateStringBytes(branch.matchMode)
            bytes += estimateStringBytes(branch.triggerKeywordsJson)
            bytes += estimateStringBytes(branch.responseArabic)
            bytes += estimateStringBytes(branch.responseEnglish)
            bytes += estimateStringBytes(branch.actionableOptionsJson)
            bytes += 8 // priorityOrder Int + isDefault Boolean
        }
        return bytes
    }

    fun estimateRawScoreEntryBytes(
        queryKey: String,
        scores: Map<String, Int>
    ): Int {
        var bytes = OBJECT_HEADER_BYTES + estimateStringBytes(queryKey)
        for ((topicId, _) in scores) {
            bytes += MAP_ENTRY_BYTES + estimateStringBytes(topicId) + 4
        }
        return bytes
    }
}

/**
 * Thread-safe, pure Kotlin/JVM & Android compatible byte-budgeted Least-Recently-Used (LRU) cache
 * partition.
 *
 * Enforces:
 * 1. Accounted byte budget `<= maxAccountedBudgetBytes`.
 * 2. Oversized Single Item Guard: Any entry whose accounted size exceeds
 *    `maxSingleItemBytes` (`maxAccountedBudgetBytes / 2`) is rejected from being stored in the
 *    cache without evicting any existing entries, while `getOrLoad` still returns the value
 *    directly to the caller.
 */
class ByteBoundedLruPartition<K : Any, V : Any>(
    private val name: String,
    val maxAccountedBudgetBytes: Int,
    val maxSingleItemBytes: Int = maxAccountedBudgetBytes / 2,
    private val sizeCalculator: (K, V) -> Int
) {
    init {
        require(maxAccountedBudgetBytes > 0) { "maxAccountedBudgetBytes must be > 0" }
        require(maxSingleItemBytes in 1..maxAccountedBudgetBytes) {
            "maxSingleItemBytes must be in 1..maxAccountedBudgetBytes"
        }
    }

    private data class SizedEntry<V>(
        val value: V,
        val sizeBytes: Int
    )

    private val lock = Any()
    private val map = LinkedHashMap<K, SizedEntry<V>>(16, 0.75f, true)

    private var currentBytes: Int = 0
    private var hits: Long = 0L
    private var misses: Long = 0L
    private var evictions: Long = 0L
    private var oversizedRejections: Long = 0L

    /**
     * Reads [key] from the cache, updating its LRU recency order.
     */
    fun get(key: K): V? = synchronized(lock) {
        val entry = map[key]
        if (entry != null) {
            hits++
            entry.value
        } else {
            misses++
            null
        }
    }

    /**
     * Attempts to store [value] under [key].
     *
     * @return `true` if the entry was stored in the cache, or `false` if it was rejected by the
     * oversized single-item guard (`itemBytes > maxSingleItemBytes`). When rejected, existing
     * cached entries are preserved untouched.
     */
    fun put(key: K, value: V): Boolean = synchronized(lock) {
        val itemBytes = sizeCalculator(key, value).coerceAtLeast(1)
        if (itemBytes > maxSingleItemBytes) {
            oversizedRejections++
            return false
        }

        val previous = map.remove(key)
        if (previous != null) {
            currentBytes -= previous.sizeBytes
        }

        while (currentBytes + itemBytes > maxAccountedBudgetBytes && map.isNotEmpty()) {
            val eldestIterator = map.entries.iterator()
            if (eldestIterator.hasNext()) {
                val eldest = eldestIterator.next()
                currentBytes -= eldest.value.sizeBytes
                eldestIterator.remove()
                evictions++
            } else {
                break
            }
        }

        map[key] = SizedEntry(value = value, sizeBytes = itemBytes)
        currentBytes += itemBytes
        true
    }

    /**
     * Returns the cached value for [key] if present, or computes it via [loader], attempts to
     * cache it (unless oversized), and always returns the loaded value to the caller.
     */
    fun getOrLoad(key: K, loader: () -> V): V {
        get(key)?.let { return it }
        val loaded = loader()
        put(key, loaded)
        return loaded
    }

    /**
     * Removes [key] from the cache and deducts its accounted bytes.
     */
    fun invalidate(key: K): V? = synchronized(lock) {
        val removed = map.remove(key)
        if (removed != null) {
            currentBytes -= removed.sizeBytes
            removed.value
        } else {
            null
        }
    }

    /**
     * Clears all entries from the cache and resets accounted bytes to 0.
     */
    fun invalidateAll() = synchronized(lock) {
        map.clear()
        currentBytes = 0
    }

    /**
     * Returns a snapshot of keys in LRU-to-MRU iteration order for deterministic testing.
     */
    fun keysInLruOrder(): List<K> = synchronized(lock) {
        map.keys.toList()
    }

    fun stats(): CachePartitionStats = synchronized(lock) {
        CachePartitionStats(
            name = name,
            maxAccountedBudgetBytes = maxAccountedBudgetBytes,
            maxSingleItemBytes = maxSingleItemBytes,
            currentAccountedBytes = currentBytes,
            entryCount = map.size,
            hitCount = hits,
            missCount = misses,
            evictionCount = evictions,
            oversizedRejectedCount = oversizedRejections
        )
    }
}

/**
 * Standalone, thread-safe Byte-Budgeted KnowledgeCache for WEYKY Phase 2B.1.
 *
 * Accounted Byte Budgets:
 * - Header Cache: `256 KB` (`262,144 Bytes`), max single item `128 KB` (`131,072 Bytes`)
 * - Detail Cache: `512 KB` (`524,288 Bytes`), max single item `256 KB` (`262,144 Bytes`)
 * - Raw Score Cache: `64 KB` (`65,536 Bytes`), max single item `32 KB` (`32,768 Bytes`)
 * - Total Accounted Cache Budget: `832 KB` (`851,968 Bytes`)
 */
class KnowledgeCache(
    headerBudgetBytes: Int = DEFAULT_HEADER_BUDGET_BYTES,
    detailBudgetBytes: Int = DEFAULT_DETAIL_BUDGET_BYTES,
    rawScoreBudgetBytes: Int = DEFAULT_RAW_SCORE_BUDGET_BYTES
) {
    companion object {
        const val KB: Int = 1024
        const val DEFAULT_HEADER_BUDGET_BYTES: Int = 256 * KB     // 262,144 Bytes
        const val DEFAULT_DETAIL_BUDGET_BYTES: Int = 512 * KB     // 524,288 Bytes
        const val DEFAULT_RAW_SCORE_BUDGET_BYTES: Int = 64 * KB   // 65,536 Bytes
        const val TOTAL_DEFAULT_BUDGET_BYTES: Int =
            DEFAULT_HEADER_BUDGET_BYTES + DEFAULT_DETAIL_BUDGET_BYTES + DEFAULT_RAW_SCORE_BUDGET_BYTES // 832 KB
    }

    val headerCache = ByteBoundedLruPartition<String, LocalKnowledgeBase.KnowledgeTopic>(
        name = "HeaderCache",
        maxAccountedBudgetBytes = headerBudgetBytes,
        maxSingleItemBytes = headerBudgetBytes / 2,
        sizeCalculator = KnowledgeByteSizeEstimator::estimateTopicHeaderEntryBytes
    )

    val detailCache = ByteBoundedLruPartition<String, CachedTopicDetailBundle>(
        name = "DetailCache",
        maxAccountedBudgetBytes = detailBudgetBytes,
        maxSingleItemBytes = detailBudgetBytes / 2,
        sizeCalculator = KnowledgeByteSizeEstimator::estimateTopicDetailEntryBytes
    )

    val rawScoreCache = ByteBoundedLruPartition<String, Map<String, Int>>(
        name = "RawScoreCache",
        maxAccountedBudgetBytes = rawScoreBudgetBytes,
        maxSingleItemBytes = rawScoreBudgetBytes / 2,
        sizeCalculator = KnowledgeByteSizeEstimator::estimateRawScoreEntryBytes
    )

    // Header Cache convenience delegates
    fun getHeader(topicId: String): LocalKnowledgeBase.KnowledgeTopic? = headerCache.get(topicId)

    fun putHeader(topicId: String, topic: LocalKnowledgeBase.KnowledgeTopic): Boolean =
        headerCache.put(topicId, topic)

    fun getOrLoadHeader(
        topicId: String,
        loader: () -> LocalKnowledgeBase.KnowledgeTopic
    ): LocalKnowledgeBase.KnowledgeTopic = headerCache.getOrLoad(topicId, loader)

    fun invalidateHeader(topicId: String) {
        headerCache.invalidate(topicId)
    }

    // Detail Cache convenience delegates
    fun getDetail(topicId: String): CachedTopicDetailBundle? = detailCache.get(topicId)

    fun putDetail(topicId: String, bundle: CachedTopicDetailBundle): Boolean =
        detailCache.put(topicId, bundle)

    fun getOrLoadDetail(
        topicId: String,
        loader: () -> CachedTopicDetailBundle
    ): CachedTopicDetailBundle = detailCache.getOrLoad(topicId, loader)

    fun invalidateDetail(topicId: String) {
        detailCache.invalidate(topicId)
    }

    // Raw Score Cache convenience delegates
    fun getRawScores(normalizedQueryKey: String): Map<String, Int>? =
        rawScoreCache.get(normalizedQueryKey)

    fun putRawScores(normalizedQueryKey: String, scores: Map<String, Int>): Boolean =
        rawScoreCache.put(normalizedQueryKey, scores)

    fun getOrLoadRawScores(
        normalizedQueryKey: String,
        loader: () -> Map<String, Int>
    ): Map<String, Int> = rawScoreCache.getOrLoad(normalizedQueryKey, loader)

    fun invalidateRawScores(normalizedQueryKey: String) {
        rawScoreCache.invalidate(normalizedQueryKey)
    }

    /**
     * Invalidates all cached entries associated with [key] across header, detail, and rawScore
     * partitions.
     */
    fun invalidate(key: String) {
        headerCache.invalidate(key)
        detailCache.invalidate(key)
        rawScoreCache.invalidate(key)
    }

    /**
     * Clears all three cache partitions (`HeaderCache`, `DetailCache`, `RawScoreCache`).
     */
    fun invalidateAll() {
        headerCache.invalidateAll()
        detailCache.invalidateAll()
        rawScoreCache.invalidateAll()
    }

    fun snapshotStats(): KnowledgeCacheStats = KnowledgeCacheStats(
        headerStats = headerCache.stats(),
        detailStats = detailCache.stats(),
        rawScoreStats = rawScoreCache.stats()
    )
}
