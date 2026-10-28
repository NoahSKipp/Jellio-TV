package com.jellio.tv.ui.live

import com.jellio.tv.data.model.LiveChannelDto
import com.jellio.tv.data.model.LiveProgramDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import java.time.Instant

// Channel ids are stable (tvg id); channel numbers are not, so nothing here
// keys or remembers a channel by number.

const val GUIDE_HOURS = 24
private const val HOUR_MS = 60L * 60 * 1000

data class LiveProgramme(val id: String?, val name: String, val start: Long, val end: Long) {
    fun progress(nowMs: Long): Float =
        if (end <= start) 0f else ((nowMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    fun airing(nowMs: Long): Boolean = start <= nowMs && end > nowMs
}

enum class LiveKind { SPORTS, TV }

data class LiveChannel(
    val id: String,
    val name: String,
    val number: String,
    val imageTag: String?,
    val favorite: Boolean,
    val now: LiveProgramme?,
    val kind: LiveKind = classifyChannel(name).first,
    val language: String? = classifyChannel(name).second,
    // Streams the user added by link (Jellio/StreamLinks).
    val logoUrl: String? = null,
    val group: String? = null,
    val userStream: Boolean = false,
)

// Same rules as Jellio web (runtime/liveTv.js): the channel name is the
// only signal for type and language; unknown stays unknown.
private val SPORTS = Regex(
    "\\b(sports?|deportes?|desporto|esporte|espn|bein|dazn|eurosport|f1|formula ?1|nba|nfl|nhl|mlb|golf|tennis|cricket|rugby|boxing|ufc|wwe|racing|motogp|supersport|tsn|sportsnet|premier league|laliga|ligue ?1|bundesliga|serie a|eleven|setanta|willow|polsat sport|movistar|match!?|nova sport|digi sport|sportklub|viaplay|ziggo|arena|sky sport|tnt sport|fox sport|cbs sports|nbc sports|astro|ptv sports|stars? sports|sportv|claro sports|tudn|nfl network|redzone|racing tv|sky racing)\\b",
    RegexOption.IGNORE_CASE,
)

private val LANGUAGES = listOf(
    "English" to "uk|usa|us|english|eng|canada|ca|australia|au|nz|ireland|ie|scotland",
    "German" to "de|germany|deutschland|deutsch|german|austria|at|schweiz|swiss|ch",
    "French" to "fr|france|french|fran[cç]ais|belgique|quebec",
    "Spanish" to "es|spain|espa[nñ]a|mexico|mx|argentina|latam|latino|colombia|chile|peru|deportes|espanol|español",
    "Portuguese" to "pt|portugal|brazil|brasil|br|desporto|esporte",
    "Italian" to "it|italy|italia|italiano",
    "Dutch" to "nl|netherlands|holland|nederland|ziggo",
    "Polish" to "pl|poland|polska|polsat",
    "Turkish" to "tr|turkey|t[uü]rkiye",
    "Arabic" to "arabic|arabia|mena|qatar|ksa|uae|egypt|alkass|ssc",
    "Greek" to "gr|greece|greek|nova sport",
    "Balkan" to "serbia|srbija|croatia|hrvatska|bosnia|slovenia|arena sport|sportklub",
).map { (label, words) -> label to Regex("\\b($words)\\b", RegexOption.IGNORE_CASE) }

fun classifyChannel(name: String): Pair<LiveKind, String?> {
    val kind = if (SPORTS.containsMatchIn(name)) LiveKind.SPORTS else LiveKind.TV
    val language = LANGUAGES.firstOrNull { it.second.containsMatchIn(name) }?.first
    return kind to language
}

data class LiveFilters(val kind: LiveKind? = null, val language: String? = null)

// Languages present, most channels first.
fun languageFacets(channels: List<LiveChannel>): List<Pair<String, Int>> =
    channels.mapNotNull { it.language }.groupingBy { it }.eachCount().toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

internal fun parseTime(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

fun LiveProgramDto.toProgramme(): LiveProgramme? {
    val start = parseTime(StartDate) ?: return null
    val end = parseTime(EndDate) ?: return null
    return LiveProgramme(Id, Name ?: EpisodeTitle ?: "", start, end)
}

fun mapChannels(items: List<LiveChannelDto>, nowMs: Long): List<LiveChannel> =
    items.filter { it.Id.isNotBlank() }.map { dto ->
        LiveChannel(
            id = dto.Id,
            name = dto.Name.orEmpty(),
            number = dto.ChannelNumber.orEmpty(),
            imageTag = dto.ImageTags?.get("Primary"),
            favorite = dto.UserData?.IsFavorite == true,
            now = dto.CurrentProgram?.toProgramme()?.takeIf { it.end > nowMs },
        )
    }

private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

// Every word has to appear in the channel name or what is on now, then the
// type and language filters when set.
fun filterChannels(channels: List<LiveChannel>, query: String, filters: LiveFilters = LiveFilters()): List<LiveChannel> {
    val words = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    return channels.filter { channel ->
        if (filters.kind != null && channel.kind != filters.kind) return@filter false
        if (filters.language != null && channel.language != filters.language) return@filter false
        if (words.isEmpty()) return@filter true
        val haystack = normalize(channel.name + " " + (channel.group ?: "") + " " + (channel.now?.name ?: ""))
        words.all { haystack.contains(it) }
    }
}

data class ArrangedChannels(val favorites: List<LiveChannel>, val others: List<LiveChannel>) {
    val all: List<LiveChannel> get() = favorites + others
}

// Favourites first, then channels with something on now, then the rest.
fun arrangeChannels(channels: List<LiveChannel>): ArrangedChannels {
    val byName = compareBy<LiveChannel, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    val (favorites, rest) = channels.partition { it.favorite }
    val (live, idle) = rest.partition { it.now != null }
    return ArrangedChannels(favorites.sortedWith(byName), live.sortedWith(byName) + idle.sortedWith(byName))
}

fun guideWindow(programs: List<LiveProgramDto>, nowMs: Long, hours: Int = GUIDE_HOURS): List<LiveProgramme> {
    val until = nowMs + hours * HOUR_MS
    return programs.mapNotNull { it.toProgramme() }
        .filter { it.end > nowMs && it.start < until }
        .sortedBy { it.start }
}

fun nowAndNext(programs: List<LiveProgramDto>, nowMs: Long): Pair<LiveProgramme?, LiveProgramme?> {
    val window = guideWindow(programs, nowMs)
    val now = window.firstOrNull { it.airing(nowMs) }
    val next = window.firstOrNull { it.start >= nowMs && it != now }
    return now to next
}

// Channel up/down through the list as shown, wrapping at either end.
fun neighbourChannel(list: List<LiveChannel>, currentId: String?, delta: Int): LiveChannel? {
    if (list.isEmpty()) return null
    val index = list.indexOfFirst { it.id == currentId }
    if (index == -1) return list.first()
    return list[Math.floorMod(index + delta, list.size)]
}

// One live stream at a time: opening a channel closes the one before it,
// and stop() always closes whatever is open, once. A stream that finishes
// opening after it was superseded is closed straight away.
class LiveLifecycle<T>(
    private val open: suspend (channelId: String) -> T,
    private val close: suspend (T) -> Unit,
) {
    private val mutex = Mutex()
    private var current: T? = null
    @Volatile private var generation = 0

    val currentStream: T? get() = current

    suspend fun start(channelId: String): T? {
        val mine = synchronized(this) { ++generation }
        closeCurrent()
        val stream = open(channelId)
        val stale = mutex.withLock {
            if (mine != generation) {
                true
            } else {
                current = stream
                false
            }
        }
        if (stale) {
            runCatching { close(stream) }
            return null
        }
        return stream
    }

    suspend fun stop() {
        synchronized(this) { generation++ }
        closeCurrent()
    }

    private suspend fun closeCurrent() {
        val stream = mutex.withLock {
            val s = current
            current = null
            s
        } ?: return
        runCatching { close(stream) }
    }
}

fun describeOpenFailure(message: String?): String {
    val text = message.orEmpty().lowercase()
    return when {
        Regex("tuner|no available|too many|limit").containsMatchIn(text) ->
            com.jellio.tv.ui.i18n.Strings.get("live_tuners_busy")
        Regex("timed? ?out|timeout").containsMatchIn(text) ->
            com.jellio.tv.ui.i18n.Strings.get("live_open_timeout")
        else -> com.jellio.tv.ui.i18n.Strings.get("live_open_failed")
    }
}

const val LINK_PREFIX = "link:"

data class LinkChannelRef(val linkId: String, val entry: Int?)

fun linkChannelId(linkId: String, entry: Int?): String = LINK_PREFIX + linkId + (entry?.let { ":$it" } ?: "")

fun parseLinkChannelId(id: String?): LinkChannelRef? {
    if (id == null || !id.startsWith(LINK_PREFIX)) return null
    val parts = id.removePrefix(LINK_PREFIX).split(":")
    if (parts[0].isEmpty()) return null
    return LinkChannelRef(parts[0], parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 })
}

private fun hostOf(url: String?): String = runCatching { java.net.URI(url ?: "").host }.getOrNull().orEmpty()

// One channel per playable stream, playlists expanded in their own order,
// classified by name and group like web (runtime/streamLinks.js).
fun flattenStreamLinks(links: List<com.jellio.tv.data.model.StreamLinkDto>): List<LiveChannel> = links.flatMap { link ->
    val linkName = link.Name.orEmpty().ifBlank { hostOf(link.SourceUrl) }
    if (link.Kind == "playlist") {
        link.Entries.mapIndexed { index, entry ->
            val name = entry.Name.orEmpty().ifBlank { linkName }
            val (kind, language) = classifyChannel(name + " " + entry.Group.orEmpty())
            LiveChannel(
                id = linkChannelId(link.Id, index),
                name = name,
                number = "",
                imageTag = null,
                favorite = false,
                now = null,
                kind = kind,
                language = language,
                logoUrl = entry.Logo?.takeIf { it.startsWith("https://") },
                group = entry.Group?.takeIf { it.isNotBlank() } ?: linkName,
                userStream = true,
            )
        }
    } else {
        val (kind, language) = classifyChannel(linkName)
        listOf(
            LiveChannel(
                id = linkChannelId(link.Id, null),
                name = linkName,
                number = "",
                imageTag = null,
                favorite = false,
                now = null,
                kind = kind,
                language = language,
                group = hostOf(link.SourceUrl),
                userStream = true,
            ),
        )
    }
}

fun streamLinkErrorKey(code: String?): String = when (code) {
    "invalid_url" -> "live_link_error_invalid"
    "blocked_address" -> "live_link_error_blocked"
    "unreachable" -> "live_link_error_unreachable"
    "timeout" -> "live_link_error_timeout"
    "no_stream_found" -> "live_link_error_none"
    "too_many_links" -> "live_link_error_too_many"
    else -> "live_link_error_failed"
}
