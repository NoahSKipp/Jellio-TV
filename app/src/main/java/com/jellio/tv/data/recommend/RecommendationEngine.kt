package com.jellio.tv.data.recommend

import com.jellio.tv.data.JellyfinDates
import com.jellio.tv.data.catching
import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.HomeSection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

private const val SEED_LIMIT = 4
private const val HISTORY_SAMPLE_LIMIT = 60
private const val NEXTUP_SEED_LIMIT = 2
private const val POOL_LIMIT = 100
private const val ROW_SIZE = 20

private const val WEIGHT_GENRE = 3.0
private const val WEIGHT_PERSON = 1.6
private const val WEIGHT_STUDIO = 0.7
private const val WEIGHT_ERA = 0.4
private const val WEIGHT_RUNTIME = 0.3

private const val LIKED_SEED_BOOST = 2.5
private const val MAX_PER_PERSON = 2
private const val MAX_PER_GENRE = 3
private const val MIN_RATING = 6.0
private const val MIN_GENRE_COUNT = 3
private const val RECENCY_HALF_LIFE_DAYS = 21.0
private const val TICKS_PER_HOUR = 10000000L * 3600L

data class CandidateEntry(
    val item: BaseItemDto,
    val viaPerson: Boolean = false,
    val matchedPersonIds: List<String>? = null,
)

data class ScoredCandidate(
    val item: BaseItemDto,
    var score: Double,
    var seedMatches: Int = 1,
)

object RecommendationEngine {
    private val seedCandidatesCache = ConcurrentHashMap<String, List<CandidateEntry>>()
    @Volatile
    private var topPicksBackendCache: List<BaseItemDto>? = null

    fun invalidateCache() {
        seedCandidatesCache.clear()
        topPicksBackendCache = null
    }

    fun getCachedSeedCandidates(seedId: String): List<CandidateEntry>? = seedCandidatesCache[seedId]

    fun cacheSeedCandidates(seedId: String, candidates: List<CandidateEntry>) {
        seedCandidatesCache[seedId] = candidates
    }

    fun getCachedBackendTopPicks(): List<BaseItemDto>? = topPicksBackendCache

    fun cacheBackendTopPicks(items: List<BaseItemDto>) {
        topPicksBackendCache = items
    }
}

fun titleKey(item: BaseItemDto): String =
    (item.Name ?: "").trim().lowercase() + "|" + (item.ProductionYear ?: "")

fun nameKey(name: String?): String = name.orEmpty().trim().lowercase()

fun franchiseKey(item: BaseItemDto): String {
    val col = item.CollectionName?.trim()
    if (!col.isNullOrEmpty()) return "col:${nameKey(col)}"
    val seriesId = item.SeriesId?.trim()
    if (!seriesId.isNullOrEmpty()) return "series:$seriesId"
    val base = nameKey(item.SeriesName ?: item.Name)
        .replace(Regex("""\s+(part|vol|volume|chapter)\s+[0-9ivx]+$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+[0-9]+$"""), "")
        .replace(Regex(""":\s+.*$"""), "")
        .trim()
    return base.ifEmpty { titleKey(item) }
}

data class RecommendationDataSource(
    val getRecentlyCompleted: suspend (limit: Int) -> List<BaseItemDto>,
    val getNextUp: suspend (limit: Int) -> List<BaseItemDto>,
    val getRecommendationCandidates: suspend (seed: BaseItemDto, limit: Int) -> List<CandidateEntry>,
    val getGenreItems: suspend (genre: String, limit: Int) -> List<BaseItemDto>,
    val getGenreItemsPage: (suspend (genre: String, startIndex: Int, limit: Int) -> List<BaseItemDto>)? = null,
    val getTopPicksRecommendations: (suspend (limit: Int) -> List<BaseItemDto>)? = null,
)

fun notPlayed(item: BaseItemDto): Boolean = item.UserData?.Played != true

fun notDisliked(item: BaseItemDto): Boolean = item.UserData?.Likes != false

fun meetsRatingThreshold(item: BaseItemDto): Boolean {
    val rating = item.CommunityRating ?: return true
    return rating >= MIN_RATING
}

fun markSeen(seen: MutableSet<String>, item: BaseItemDto) {
    seen.add(item.Id)
    seen.add(titleKey(item))
    item.SeriesId?.let { seen.add(it) }
}

fun dedupe(items: List<BaseItemDto>, seen: MutableSet<String>): List<BaseItemDto> {
    val result = mutableListOf<BaseItemDto>()
    for (item in items) {
        if (seen.contains(item.Id) || seen.contains(titleKey(item)) || (item.SeriesId != null && seen.contains(item.SeriesId))) continue
        markSeen(seen, item)
        result.add(item)
    }
    return result
}

fun primaryPerson(seed: BaseItemDto): String? =
    seed.People?.firstOrNull { it.Type == "Actor" || it.Type == "Director" }?.Name

fun eraOf(item: BaseItemDto): Int? =
    item.ProductionYear?.let { (it / 10) * 10 }

fun hasSharedStudio(seed: BaseItemDto, item: BaseItemDto): Boolean {
    val sStudios = seed.Studios?.mapNotNull { it.Name?.trim()?.lowercase()?.takeIf(String::isNotEmpty) } ?: emptyList()
    val iStudios = item.Studios?.mapNotNull { it.Name?.trim()?.lowercase()?.takeIf(String::isNotEmpty) } ?: emptyList()
    if (sStudios.isEmpty() || iStudios.isEmpty()) return false
    val sSet = sStudios.toSet()
    return iStudios.any { it in sSet }
}

fun jaccard(a: List<String>?, b: List<String>?): Double {
    val setA = a?.map { it.trim().lowercase() }?.toSet() ?: emptySet()
    val setB = b?.map { it.trim().lowercase() }?.toSet() ?: emptySet()
    if (setA.isEmpty() || setB.isEmpty()) return 0.0
    var shared = 0
    for (x in setB) {
        if (x in setA) shared++
    }
    val total = setA.size + setB.size - shared
    return if (total > 0) shared.toDouble() / total else 0.0
}

fun seedWeight(seed: BaseItemDto, nowMs: Long = System.currentTimeMillis()): Double {
    if (seed.UserData?.Likes == false) return 0.0
    var weight = if (seed.UserData?.Likes == true) LIKED_SEED_BOOST else 1.0

    val playedMs = JellyfinDates.parseEpochMs(seed.UserData?.LastPlayedDate)
    if (playedMs != null && playedMs > 0L) {
        val daysAgo = max(0.0, (nowMs - playedMs).toDouble() / 86400000.0)
        weight *= exp(-daysAgo / RECENCY_HALF_LIFE_DAYS)
    }

    val playCount = seed.UserData?.PlayCount ?: 0
    val recentEps = seed.recentEpisodeCount
    if (playCount > 1 || recentEps >= 3) {
        weight += 0.4
    }
    return weight
}

fun score(seed: BaseItemDto, entry: CandidateEntry, nowMs: Long? = null): Double =
    score(
        seed = seed,
        candidate = entry.item,
        viaPerson = entry.viaPerson,
        matchedPersonIds = entry.matchedPersonIds,
        nowMs = nowMs,
    )

fun score(
    seed: BaseItemDto,
    candidate: BaseItemDto,
    viaPerson: Boolean,
    matchedPersonIds: List<String>? = null,
    nowMs: Long? = null,
): Double {
    val overlap = jaccard(seed.Genres, candidate.Genres)
    val sharedStudio = hasSharedStudio(seed, candidate)
    if (overlap <= 0.0 && !viaPerson && !sharedStudio) return 0.0

    val sWeight = seedWeight(seed, nowMs ?: System.currentTimeMillis())
    if (sWeight <= 0.0) return 0.0

    var total = WEIGHT_GENRE * overlap * sWeight
    if (viaPerson) {
        val personMultiplier = min(2, matchedPersonIds?.size ?: 1)
        val likeBonus = if (seed.UserData?.Likes == true) 1.5 else 1.0
        total += WEIGHT_PERSON * personMultiplier * likeBonus
    }
    if (sharedStudio) {
        total += WEIGHT_STUDIO
    }

    val seedYear = seed.ProductionYear
    val candYear = candidate.ProductionYear
    if (seedYear != null && candYear != null) {
        val gap = abs(seedYear - candYear)
        total += WEIGHT_ERA * (1.0 - min(1.0, gap / 20.0))
    }

    // Candidate quality score: ((candidate.CommunityRating ?: 0.0) / 10.0) * 1.5 instead of rating proximity
    val candRating = candidate.CommunityRating ?: 0.0
    if (candRating > 0.0) {
        total += ((candRating / 10.0) * 1.5)
    }

    val seedTicks = seed.RunTimeTicks ?: 0L
    val candTicks = candidate.RunTimeTicks ?: 0L
    if (seedTicks > 0L && candTicks > 0L) {
        val diffHours = abs(seedTicks - candTicks).toDouble() / TICKS_PER_HOUR
        total += WEIGHT_RUNTIME * (1.0 - min(1.0, diffHours / 2.0))
    }

    return total
}

fun isSameTitle(seed: BaseItemDto, item: BaseItemDto): Boolean {
    val seedIds = setOfNotNull(seed.Id, seed.SeriesId)
    if (item.Id in seedIds || (item.SeriesId != null && item.SeriesId in seedIds)) return true
    val seedNames = setOf(nameKey(seed.SeriesName ?: seed.Name), nameKey(seed.Name)) - ""
    return nameKey(item.SeriesName ?: item.Name) in seedNames
}

private fun pick(seed: BaseItemDto, candidates: List<CandidateEntry>, seen: MutableSet<String>, limit: Int): List<BaseItemDto> {
    val scored = candidates
        .filter {
            notPlayed(it.item) &&
            notDisliked(it.item) &&
            meetsRatingThreshold(it.item) &&
            !seen.contains(it.item.Id) &&
            !seen.contains(titleKey(it.item)) &&
            (it.item.SeriesId == null || !seen.contains(it.item.SeriesId))
        }
        .filter { !isSameTitle(seed, it.item) }
        .map { it.item to score(seed, it) }
        .filter { it.second > 0.0 }
        .sortedByDescending { it.second }

    val picked = mutableListOf<BaseItemDto>()
    val personCounts = mutableMapOf<String, Int>()
    val genreCounts = mutableMapOf<String, Int>()

    for ((item, _) in scored) {
        val lead = primaryPerson(item)
        if (lead != null && (personCounts[lead] ?: 0) >= MAX_PER_PERSON) continue

        val leadGenre = item.Genres?.firstOrNull()
        if (leadGenre != null && (genreCounts[leadGenre] ?: 0) >= MAX_PER_GENRE) continue

        picked.add(item)
        if (lead != null) personCounts[lead] = (personCounts[lead] ?: 0) + 1
        if (leadGenre != null) genreCounts[leadGenre] = (genreCounts[leadGenre] ?: 0) + 1

        if (picked.size >= limit) break
    }
    return picked
}

fun topGenres(history: List<BaseItemDto>): List<String> {
    val counts = mutableMapOf<String, Double>()
    for (item in history) {
        val weight = seedWeight(item)
        if (weight <= 0.0) continue
        item.Genres?.forEach { genre ->
            counts[genre] = (counts[genre] ?: 0.0) + weight
        }
    }
    val filtered = counts.entries.filter { it.value >= MIN_GENRE_COUNT }
    val entries = if (filtered.isNotEmpty()) filtered else counts.entries
    return entries
        .sortedByDescending { it.value }
        .map { it.key }
}

private suspend fun buildSeedRows(
    source: RecommendationDataSource,
    seeds: List<BaseItemDto>,
    titleFor: (BaseItemDto) -> String,
    exclude: MutableSet<String>,
): List<HomeSection> = coroutineScope {
    val entriesPerSeed = seeds.map { seed ->
        async {
            val id = seed.Id
            if (id.isEmpty()) return@async null
            RecommendationEngine.getCachedSeedCandidates(id) ?: catching {
                source.getRecommendationCandidates(seed, POOL_LIMIT)
            }.getOrNull()?.also {
                if (it.isNotEmpty()) RecommendationEngine.cacheSeedCandidates(id, it)
            }
        }
    }.awaitAll()

    val rows = mutableListOf<HomeSection>()
    seeds.forEachIndexed { index, seed ->
        val entries = entriesPerSeed[index] ?: return@forEachIndexed
        val items = pick(seed, entries, exclude, ROW_SIZE)
        if (items.isEmpty()) return@forEachIndexed
        items.forEach { markSeen(exclude, it) }
        rows.add(HomeSection(titleFor(seed), items))
    }
    rows
}

suspend fun buildTopPicksRow(
    source: RecommendationDataSource,
    history: List<BaseItemDto>,
    exclude: MutableSet<String>,
): HomeSection? = coroutineScope {
    val backendDeferred = async {
        RecommendationEngine.getCachedBackendTopPicks() ?: run {
            val fetched = catching { source.getTopPicksRecommendations?.invoke(ROW_SIZE) }.getOrNull().orEmpty()
            if (fetched.isNotEmpty()) {
                RecommendationEngine.cacheBackendTopPicks(fetched)
            }
            fetched
        }
    }

    val topSeeds = history
        .filter(::notDisliked)
        .sortedByDescending { seedWeight(it) }
        .take(5)

    val seedCandidatesDeferred = topSeeds.map { seed ->
        async {
            val id = seed.Id
            if (id.isEmpty()) return@async null
            RecommendationEngine.getCachedSeedCandidates(id) ?: catching {
                source.getRecommendationCandidates(seed, POOL_LIMIT)
            }.getOrNull()?.also {
                if (it.isNotEmpty()) RecommendationEngine.cacheSeedCandidates(id, it)
            }
        }
    }

    val backendItems = backendDeferred.await()
    val seedEntriesList = seedCandidatesDeferred.awaitAll()

    val combinedScores = mutableMapOf<String, ScoredCandidate>()
    val historyIds = history.map { it.Id }.toSet()

    // 1. Backend collaborative items lead Top Picks
    backendItems.forEachIndexed { index, item ->
        if (item.Id.isEmpty()) return@forEachIndexed
        if (item.Id in historyIds || item.Id in exclude || titleKey(item) in exclude) return@forEachIndexed
        if (item.SeriesId != null && item.SeriesId in exclude) return@forEachIndexed
        if (!notPlayed(item) || !notDisliked(item)) return@forEachIndexed
        if (item.CommunityRating != null && item.CommunityRating < MIN_RATING) return@forEachIndexed

        combinedScores[item.Id] = ScoredCandidate(
            item = item,
            score = 12.0 - index * 0.25,
            seedMatches = 1,
        )
    }

    // 2. Multi-seed client aggregation with co-occurrence boost
    topSeeds.forEachIndexed { idx, seed ->
        val entries = seedEntriesList[idx] ?: return@forEachIndexed
        for (entry in entries) {
            val item = entry.item
            if (item.Id.isEmpty() || item.Id == seed.Id) continue
            if (item.Id in historyIds || item.Id in exclude || titleKey(item) in exclude) continue
            if (item.SeriesId != null && item.SeriesId in exclude) continue
            if (!notPlayed(item) || !notDisliked(item)) continue
            if (item.CommunityRating != null && item.CommunityRating < MIN_RATING) continue

            val s = score(seed, entry)
            if (s <= 0.0) continue

            val existing = combinedScores[item.Id]
            if (existing == null) {
                combinedScores[item.Id] = ScoredCandidate(item = item, score = s, seedMatches = 1)
            } else {
                existing.score += s + 1.5
                existing.seedMatches += 1
            }
        }
    }

    // 3. Fallback fill with top weighted genres if still under ROW_SIZE
    if (combinedScores.size < ROW_SIZE) {
        val fallbackGenres = topGenres(history).take(2)
        for ((gIdx, genre) in fallbackGenres.withIndex()) {
            val items = catching { source.getGenreItems(genre, 12) }.getOrDefault(emptyList())
            for ((rankIdx, item) in items.withIndex()) {
                if (item.Id.isEmpty() || item.Id in combinedScores) continue
                if (item.Id in historyIds || item.Id in exclude || titleKey(item) in exclude) continue
                if (item.SeriesId != null && item.SeriesId in exclude) continue
                if (!notPlayed(item) || !notDisliked(item)) continue
                if (item.CommunityRating != null && item.CommunityRating < MIN_RATING) continue

                val qualityBonus = ((item.CommunityRating ?: 6.0) / 10.0) * 0.5
                val fallbackScore = 1.5 - gIdx * 0.3 - rankIdx * 0.05 + qualityBonus
                combinedScores[item.Id] = ScoredCandidate(item = item, score = fallbackScore, seedMatches = 1)
            }
        }
    }

    val ranked = combinedScores.values.sortedByDescending { it.score }
    val chosen = mutableListOf<BaseItemDto>()
    val seenFranchise = mutableSetOf<String>()
    val personCounts = mutableMapOf<String, Int>()

    for (cand in ranked) {
        val item = cand.item
        val fKey = franchiseKey(item)
        if (fKey.isNotEmpty() && fKey in seenFranchise) continue

        val lead = primaryPerson(item)
        if (lead != null && (personCounts[lead] ?: 0) >= MAX_PER_PERSON) continue

        if (fKey.isNotEmpty()) seenFranchise.add(fKey)
        if (lead != null) personCounts[lead] = (personCounts[lead] ?: 0) + 1
        markSeen(exclude, item)
        chosen.add(item)
        if (chosen.size >= ROW_SIZE) break
    }

    if (chosen.isEmpty()) return@coroutineScope null

    val topGenre = topGenres(history).firstOrNull()
    val fetchPage: (suspend (Int, Int) -> List<BaseItemDto>)? = if (topGenre != null) {
        source.getGenreItemsPage?.let { pager ->
            { start, size -> pager(topGenre, start, size).filter(::notPlayed) }
        }
    } else null

    HomeSection(Strings.get("home_top_picks"), chosen, fetchPage = fetchPage)
}

suspend fun buildRecommendationRows(source: RecommendationDataSource, exclude: MutableSet<String>): List<HomeSection> = coroutineScope {
    val historyDeferred = async { catching { source.getRecentlyCompleted(HISTORY_SAMPLE_LIMIT) }.getOrDefault(emptyList()) }
    val nextUpDeferred = async { catching { source.getNextUp(NEXTUP_SEED_LIMIT) }.getOrDefault(emptyList()) }
    val history = historyDeferred.await()
    val nextUp = nextUpDeferred.await()

    // 1. Build "Top Picks for You" FIRST before individual seed rows claim items in exclude.
    val topPicksRow = catching { buildTopPicksRow(source, history, exclude) }.getOrNull()

    // 2. Build seed rows for recently completed and next up items
    val completedSeeds = history.filter(::notDisliked).take(SEED_LIMIT)
    val completedRows = buildSeedRows(source, completedSeeds, { seed -> Strings.format("home_because_watched", seed.Name.orEmpty()) }, exclude)

    val nextUpRows = buildSeedRows(source, nextUp.filter(::notDisliked), { seed -> Strings.format("home_because_watching", (seed.SeriesName ?: seed.Name).orEmpty()) }, exclude)

    listOfNotNull(topPicksRow) + completedRows + nextUpRows
}
