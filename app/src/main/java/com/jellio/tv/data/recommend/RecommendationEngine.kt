package com.jellio.tv.data.recommend

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.i18n.Strings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.min

// "Because you watched X" recommendation rows, scored here rather than
// asked of the server. Ported from runtime/recommend.js, real
// reasoning preserved rather than re-derived: Jellyfin's own
// similarity engine (GET /Items/{id}/Similar) 404s on a Gelato server,
// because Gelato remaps item ids through its own action filter and
// that route is not one of the ones it covers.

private const val SEED_LIMIT = 4
private const val HISTORY_SAMPLE_LIMIT = 60
private const val NEXTUP_SEED_LIMIT = 2
private const val POOL_LIMIT = 100
private const val ROW_SIZE = 20

private const val WEIGHT_GENRE = 3.0
private const val WEIGHT_PERSON = 1.2
private const val WEIGHT_ERA = 0.4
private const val WEIGHT_RATING = 0.3
private const val WEIGHT_RUNTIME = 0.3

// A title the reader explicitly liked (real Jellyfin UserData.Likes)
// should pull its own "because you watched" row (and the aggregate
// genre signal below) harder toward the same genre, explicit signal
// outweighing whatever a title's own community rating or recency
// alone would have said.
private const val LIKED_SEED_BOOST = 1.35

// Diversity is enforced at selection, not by lowering the weights
// above: one dominant signal still wins repeatedly otherwise, and a
// row fills with titles sharing a lead actor that each scored fairly
// on their own.
private const val MAX_PER_PERSON = 2
private const val MAX_PER_GENRE = 3

private const val MIN_RATING = 5.0
private const val MIN_GENRE_COUNT = 3
private const val MAX_GENRE_ROWS = 1

private const val TICKS_PER_HOUR = 10000000L * 3600L

data class CandidateEntry(val item: BaseItemDto, val viaPerson: Boolean)

// Title and year, not just id: the library really can carry a handful
// of same name, same year titles that are different films, and Gelato
// hands out an aliased id for the same item on some routes, so an
// id-only exclude set can miss a duplicate a title/year one would not.
fun titleKey(item: BaseItemDto): String =
    (item.Name ?: "").trim().lowercase() + "|" + (item.ProductionYear ?: "")

// The network calls RecommendationEngine needs, passed in so tests can
// mock them without a real repository.
data class RecommendationDataSource(
    val getRecentlyCompleted: suspend (limit: Int) -> List<BaseItemDto>,
    val getNextUp: suspend (limit: Int) -> List<BaseItemDto>,
    val getRecommendationCandidates: suspend (seed: BaseItemDto, limit: Int) -> List<CandidateEntry>,
    val getGenreItems: suspend (genre: String, limit: Int) -> List<BaseItemDto>,
)

private fun notPlayed(item: BaseItemDto): Boolean = item.UserData?.Played != true

private fun markSeen(seen: MutableSet<String>, item: BaseItemDto) {
    seen.add(item.Id)
    seen.add(titleKey(item))
    item.SeriesId?.let { seen.add(it) }
}

private fun dedupe(items: List<BaseItemDto>, seen: MutableSet<String>): List<BaseItemDto> {
    val result = mutableListOf<BaseItemDto>()
    for (item in items) {
        if (seen.contains(item.Id) || seen.contains(titleKey(item)) || (item.SeriesId != null && seen.contains(item.SeriesId))) continue
        markSeen(seen, item)
        result.add(item)
    }
    return result
}

private fun primaryPerson(seed: BaseItemDto): String? =
    seed.People?.firstOrNull { it.Type == "Actor" || it.Type == "Director" }?.Name

private fun eraOf(item: BaseItemDto): Int? =
    item.ProductionYear?.let { (it / 10) * 10 }

private fun score(seed: BaseItemDto, candidate: BaseItemDto, viaPerson: Boolean): Double {
    var score = 0.0

    // Person match: candidates found through a shared person start with
    // this score already earned.
    if (viaPerson) score += WEIGHT_PERSON

    // Shared genres: up to two, same cap the JS engine uses.
    val seedGenres = seed.Genres?.toSet() ?: emptySet()
    val candidateGenres = candidate.Genres ?: emptyList()
    var genreMatches = 0
    for (genre in candidateGenres) {
        if (seedGenres.contains(genre)) {
            score += WEIGHT_GENRE
            genreMatches++
            if (genreMatches >= 2) break
        }
    }

    // Era match: same decade.
    val seedEra = eraOf(seed)
    val candidateEra = eraOf(candidate)
    if (seedEra != null && seedEra == candidateEra) score += WEIGHT_ERA

    // Rating match: closer community ratings score higher.
    val seedRating = seed.CommunityRating ?: 0.0
    val candidateRating = candidate.CommunityRating ?: 0.0
    if (seedRating > 0.0 && candidateRating > 0.0) {
        val diff = abs(seedRating - candidateRating)
        score += WEIGHT_RATING * (1.0 - min(diff / 5.0, 1.0))
    }

    // Runtime match: within an hour of each other.
    val seedTicks = seed.RunTimeTicks ?: 0L
    val candidateTicks = candidate.RunTimeTicks ?: 0L
    if (seedTicks > 0L && candidateTicks > 0L) {
        val diffHours = abs(seedTicks - candidateTicks).toDouble() / TICKS_PER_HOUR
        score += WEIGHT_RUNTIME * (1.0 - min(diffHours / 2.0, 1.0))
    }

    // Explicit like on the seed gives its candidates a multiplier.
    if (seed.UserData?.Likes == true) score *= LIKED_SEED_BOOST

    return score
}

private fun nameKey(name: String?): String = name.orEmpty().trim().lowercase()

private fun isSameTitle(seed: BaseItemDto, item: BaseItemDto): Boolean {
    val seedIds = setOfNotNull(seed.Id, seed.SeriesId)
    if (item.Id in seedIds || (item.SeriesId != null && item.SeriesId in seedIds)) return true
    val seedNames = setOf(nameKey(seed.SeriesName ?: seed.Name), nameKey(seed.Name)) - ""
    return nameKey(item.SeriesName ?: item.Name) in seedNames
}

private fun pick(seed: BaseItemDto, candidates: List<CandidateEntry>, seen: MutableSet<String>, limit: Int): List<BaseItemDto> {
    val scored = candidates
        .filter { notPlayed(it.item) && !seen.contains(it.item.Id) && (it.item.SeriesId == null || !seen.contains(it.item.SeriesId)) }
        // Never the row's own title: not the seed, its show, or another
        // version of it with the same name (a remake, a second import).
        .filter { !isSameTitle(seed, it.item) }
        .map { it.item to score(seed, it.item, it.viaPerson) }
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

private fun topGenres(history: List<BaseItemDto>): List<String> {
    val counts = mutableMapOf<String, Double>()
    for (item in history) {
        val weight = if (item.UserData?.Likes == true) LIKED_SEED_BOOST else 1.0
        item.Genres?.forEach { genre ->
            counts[genre] = (counts[genre] ?: 0.0) + weight
        }
    }
    return counts.entries
        .filter { it.value >= MIN_GENRE_COUNT }
        .sortedByDescending { it.value }
        .map { it.key }
}

private suspend fun buildSeedRows(
    source: RecommendationDataSource,
    seeds: List<BaseItemDto>,
    titleFor: (BaseItemDto) -> String,
    exclude: MutableSet<String>,
): List<HomeSection> = coroutineScope {
    // Fired together, same as home.js's Promise.allSettled: awaiting these
    // one by one was adding seconds to Home load.
    val entriesPerSeed = seeds.map { seed ->
        async { runCatching { source.getRecommendationCandidates(seed, POOL_LIMIT) }.getOrNull() }
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

private suspend fun buildTopGenreRows(source: RecommendationDataSource, history: List<BaseItemDto>, exclude: MutableSet<String>): List<HomeSection> {
    val genres = topGenres(history).take(MAX_GENRE_ROWS)
    val rows = mutableListOf<HomeSection>()
    genres.forEach { genre ->
        val items = runCatching {
            dedupe(source.getGenreItems(genre, ROW_SIZE).filter(::notPlayed), exclude)
        }.getOrDefault(emptyList())
        if (items.isNotEmpty()) rows.add(HomeSection(Strings.get("home_top_picks"), items))
    }
    return rows
}

private fun notDisliked(seed: BaseItemDto): Boolean = seed.UserData?.Likes != false

// Every personalized row this app builds without a second backend: one
// row per recently completed title ("Because you watched X"), one per
// series still in progress ("Because you're watching X"), plus the
// two real aggregate rows. exclude is Home's own shared seen set,
// added to here in the same real priority order real feedback
// established: per title rows first, the two aggregate rows after.
// Real feedback: the genre aggregate row ("Top Picks for You") sits
// first in the returned list, ahead of every per-title row.
suspend fun buildRecommendationRows(source: RecommendationDataSource, exclude: MutableSet<String>): List<HomeSection> = coroutineScope {
    val historyDeferred = async { runCatching { source.getRecentlyCompleted(HISTORY_SAMPLE_LIMIT) }.getOrDefault(emptyList()) }
    val nextUpDeferred = async { runCatching { source.getNextUp(NEXTUP_SEED_LIMIT) }.getOrDefault(emptyList()) }
    val history = historyDeferred.await()
    val nextUp = nextUpDeferred.await()

    val completedSeeds = history.filter(::notDisliked).take(SEED_LIMIT)
    val completedRows = buildSeedRows(source, completedSeeds, { seed -> Strings.format("home_because_watched", seed.Name.orEmpty()) }, exclude)

    val nextUpRows = buildSeedRows(source, nextUp.filter(::notDisliked), { seed -> Strings.format("home_because_watching", (seed.SeriesName ?: seed.Name).orEmpty()) }, exclude)

    val genreRows = buildTopGenreRows(source, history, exclude)

    genreRows + completedRows + nextUpRows
}
