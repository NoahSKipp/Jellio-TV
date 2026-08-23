package com.jellio.tv.data.recommend

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.home.HomeSection
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
private const val MIN_PERSON_COUNT = 2
private const val MAX_GENRE_ROWS = 1
private const val MAX_PERSON_ROWS = 1

private const val TICKS_PER_HOUR = 10000000L * 3600L

data class CandidateEntry(val item: BaseItemDto, val viaPerson: Boolean)

// Title and year, not just id: the library really can carry a handful
// of same name, same year titles that are different films, and Gelato
// hands out an aliased id for the same item on some routes, so an
// id-only exclude set can miss a duplicate a title/year one would not.
fun titleKey(item: BaseItemDto): String =
    (item.Name ?: "").trim().lowercase() + "|" + (item.ProductionYear ?: "")

private fun jaccard(a: List<String>, b: List<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val set = a.toHashSet()
    val shared = b.count { it in set }
    return shared.toDouble() / (a.size + b.size - shared)
}

private fun notPlayed(item: BaseItemDto): Boolean = item.UserData?.Played != true

private fun score(seed: BaseItemDto, entry: CandidateEntry): Double {
    val item = entry.item
    val overlap = jaccard(seed.Genres ?: emptyList(), item.Genres ?: emptyList())
    if (overlap <= 0.0) return 0.0

    val likedBoost = if (seed.UserData?.Likes == true) LIKED_SEED_BOOST else 1.0
    var total = WEIGHT_GENRE * overlap * likedBoost
    if (entry.viaPerson) total += WEIGHT_PERSON

    val seedYear = seed.ProductionYear
    val itemYear = item.ProductionYear
    if (seedYear != null && itemYear != null) {
        val gap = abs(seedYear - itemYear)
        total += WEIGHT_ERA * (1 - min(1.0, gap / 20.0))
    }

    item.CommunityRating?.let { total += WEIGHT_RATING * (it / 10.0) }

    val seedRuntime = seed.RunTimeTicks
    val itemRuntime = item.RunTimeTicks
    if (seedRuntime != null && itemRuntime != null) {
        val gap = abs(seedRuntime - itemRuntime)
        total += WEIGHT_RUNTIME * (1 - min(1.0, gap.toDouble() / TICKS_PER_HOUR))
    }

    return total
}

// Greedy by score, then the caps. exclude carries everything already
// drawn on the page so several rows do not become the same twenty
// titles several times over.
private fun pick(seed: BaseItemDto, entries: List<CandidateEntry>, exclude: MutableSet<String>, count: Int): List<BaseItemDto> {
    val seedKey = titleKey(seed)
    val scored = entries
        .filter { entry ->
            val item = entry.item
            if (item.Id == seed.Id || titleKey(item) == seedKey) return@filter false
            if (exclude.contains(item.Id) || exclude.contains(titleKey(item))) return@filter false
            val rating = item.CommunityRating
            if (rating != null && rating < MIN_RATING) return@filter false
            if (!notPlayed(item)) return@filter false
            true
        }
        .map { entry -> entry to score(seed, entry) }
        .filter { it.second > 0.0 }
        .sortedByDescending { it.second }

    val chosen = mutableListOf<BaseItemDto>()
    val perGenre = mutableMapOf<String, Int>()
    var people = 0

    for ((entry, _) in scored) {
        if (chosen.size >= count) break
        val primary = entry.item.Genres?.firstOrNull() ?: ""
        if (entry.viaPerson && people >= MAX_PER_PERSON) continue
        if (primary.isNotEmpty() && (perGenre[primary] ?: 0) >= MAX_PER_GENRE) continue

        if (entry.viaPerson) people++
        if (primary.isNotEmpty()) perGenre[primary] = (perGenre[primary] ?: 0) + 1
        chosen.add(entry.item)
    }

    return chosen
}

private fun markSeen(exclude: MutableSet<String>, item: BaseItemDto) {
    exclude.add(item.Id)
    exclude.add(titleKey(item))
}

private fun dedupe(items: List<BaseItemDto>, exclude: MutableSet<String>): List<BaseItemDto> {
    val kept = mutableListOf<BaseItemDto>()
    items.forEach { item ->
        if (exclude.contains(item.Id) || exclude.contains(titleKey(item))) return@forEach
        exclude.add(item.Id)
        exclude.add(titleKey(item))
        kept.add(item)
    }
    return kept
}

private fun genreWeight(item: BaseItemDto): Int = when (item.UserData?.Likes) {
    true -> 2
    false -> 0
    else -> 1
}

private fun topGenres(history: List<BaseItemDto>): List<String> {
    val counts = linkedMapOf<String, Int>()
    history.forEach { item ->
        val weight = genreWeight(item)
        if (weight == 0) return@forEach
        item.Genres?.forEach { genre -> counts[genre] = (counts[genre] ?: 0) + weight }
    }
    return counts.filterValues { it >= MIN_GENRE_COUNT }.entries.sortedByDescending { it.value }.map { it.key }
}

private data class PersonCount(val id: String, val name: String, val count: Int)

private fun topPeople(history: List<BaseItemDto>): List<PersonCount> {
    val counts = linkedMapOf<String, PersonCount>()
    history.forEach { item ->
        item.People?.forEach { person ->
            val id = person.Id
            if (person.Type != "Actor" && person.Type != "Director") return@forEach
            val existing = counts[id]
            counts[id] = if (existing != null) existing.copy(count = existing.count + 1) else PersonCount(id, person.Name ?: "", 1)
        }
    }
    return counts.values.filter { it.count >= MIN_PERSON_COUNT }.sortedByDescending { it.count }
}

// Real callback dependency injection rather than a repository
// reference held directly: keeps this file pure real scoring logic,
// same real separation runtime/recommend.js itself draws against
// runtime/api.js.
class RecommendationDataSource(
    val getRecentlyCompleted: suspend (limit: Int) -> List<BaseItemDto>,
    val getNextUp: suspend (limit: Int) -> List<BaseItemDto>,
    val getRecommendationCandidates: suspend (seed: BaseItemDto, limit: Int) -> List<CandidateEntry>,
    val getGenreItems: suspend (genre: String, limit: Int) -> List<BaseItemDto>,
    val getPersonItems: suspend (personId: String, limit: Int) -> List<BaseItemDto>,
)

private suspend fun buildSeedRows(
    source: RecommendationDataSource,
    seeds: List<BaseItemDto>,
    titleFor: (BaseItemDto) -> String,
    exclude: MutableSet<String>,
): List<HomeSection> {
    val entriesPerSeed = seeds.map { seed ->
        runCatching { source.getRecommendationCandidates(seed, POOL_LIMIT) }.getOrNull()
    }

    val rows = mutableListOf<HomeSection>()
    seeds.forEachIndexed { index, seed ->
        val entries = entriesPerSeed[index] ?: return@forEachIndexed
        val items = pick(seed, entries, exclude, ROW_SIZE)
        if (items.isEmpty()) return@forEachIndexed
        items.forEach { markSeen(exclude, it) }
        rows.add(HomeSection(titleFor(seed), items))
    }
    return rows
}

private suspend fun buildTopGenreRows(source: RecommendationDataSource, history: List<BaseItemDto>, exclude: MutableSet<String>): List<HomeSection> {
    val genres = topGenres(history).take(MAX_GENRE_ROWS)
    val rows = mutableListOf<HomeSection>()
    genres.forEach { genre ->
        val items = runCatching {
            dedupe(source.getGenreItems(genre, ROW_SIZE).filter(::notPlayed), exclude)
        }.getOrDefault(emptyList())
        if (items.isNotEmpty()) rows.add(HomeSection("Top Picks for You", items))
    }
    return rows
}

private suspend fun buildTopPersonRows(source: RecommendationDataSource, history: List<BaseItemDto>, exclude: MutableSet<String>): List<HomeSection> {
    val people = topPeople(history).take(MAX_PERSON_ROWS)
    val rows = mutableListOf<HomeSection>()
    people.forEach { person ->
        val items = runCatching {
            dedupe(source.getPersonItems(person.id, ROW_SIZE).filter(::notPlayed), exclude)
        }.getOrDefault(emptyList())
        if (items.isNotEmpty()) rows.add(HomeSection("More with ${person.name}", items))
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
suspend fun buildRecommendationRows(source: RecommendationDataSource, exclude: MutableSet<String>): List<HomeSection> {
    val history = runCatching { source.getRecentlyCompleted(HISTORY_SAMPLE_LIMIT) }.getOrDefault(emptyList())
    val nextUp = runCatching { source.getNextUp(NEXTUP_SEED_LIMIT) }.getOrDefault(emptyList())

    val completedSeeds = history.filter(::notDisliked).take(SEED_LIMIT)
    val completedRows = buildSeedRows(source, completedSeeds, { seed -> "Because you watched ${seed.Name}" }, exclude)

    val nextUpRows = buildSeedRows(source, nextUp.filter(::notDisliked), { seed -> "Because you're watching ${seed.SeriesName ?: seed.Name}" }, exclude)

    val genreRows = buildTopGenreRows(source, history, exclude)
    val personRows = buildTopPersonRows(source, history, exclude)

    return genreRows + completedRows + nextUpRows + personRows
}
