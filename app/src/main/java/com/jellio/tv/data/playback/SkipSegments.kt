package com.jellio.tv.data.playback

import com.jellio.tv.data.model.ChapterInfoDto
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.MediaSegmentDto

private const val TICKS_PER_SECOND = 10_000_000.0

data class SkipRange(val start: Double, val end: Double) {
    val isValid: Boolean get() = end > 0 && end > start
}

// Intro and end credits of one title, in seconds, whichever source they came from.
data class SkipSegments(val intro: SkipRange? = null, val credits: SkipRange? = null)

// Credits starting in the first half are bad data; they would show Skip
// Credits and Up Next right at the start (screens/player.js isValidCredits).
fun isValidCredits(range: SkipRange?, runtimeSeconds: Double): Boolean =
    range != null && range.isValid && (runtimeSeconds <= 0 || range.start >= runtimeSeconds * 0.5)

// screens/player.js mergeSkipSegments: what is already known wins, the
// candidate only fills what is missing or unusable.
fun mergeSkipSegments(current: SkipSegments?, candidate: SkipSegments?, runtimeSeconds: Double): SkipSegments? {
    if (candidate == null) return current
    val intro = current?.intro?.takeIf { it.isValid } ?: candidate.intro?.takeIf { it.isValid }
    val credits = current?.credits?.takeIf { isValidCredits(it, runtimeSeconds) }
        ?: candidate.credits?.takeIf { isValidCredits(it, runtimeSeconds) }
    return SkipSegments(intro, credits)
}

fun skipSegmentsComplete(segments: SkipSegments?, runtimeSeconds: Double): Boolean =
    segments?.intro?.isValid == true && isValidCredits(segments.credits, runtimeSeconds)

fun IntroSkipperSegmentsDto.toSkipSegments(): SkipSegments? {
    val intro = Introduction?.let { SkipRange(it.Start, it.End) }
    val credits = Credits?.let { SkipRange(it.Start, it.End) }
    return if (intro == null && credits == null) null else SkipSegments(intro, credits)
}

fun nativeSkipSegments(items: List<MediaSegmentDto>): SkipSegments? {
    fun bySeconds(type: String): SkipRange? = items.firstOrNull { it.Type == type }
        ?.let { SkipRange(it.StartTicks / TICKS_PER_SECOND, it.EndTicks / TICKS_PER_SECOND) }
    val intro = bySeconds("Intro")
    val credits = bySeconds("Outro")
    return if (intro == null && credits == null) null else SkipSegments(intro, credits)
}

// GET /Jellio/segments/{id}: the plugin's merge of Media Segments and Intro
// Skipper; the first Intro and Outro become the skip ranges.
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class JellioSegmentDto(val Type: String? = null, val StartMs: Long = 0, val EndMs: Long = 0)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class JellioSegmentsDto(val Segments: List<JellioSegmentDto> = emptyList(), val Source: String? = null)

fun JellioSegmentsDto.toSkipSegments(): SkipSegments? {
    fun bySeconds(type: String): SkipRange? = Segments.firstOrNull { it.Type == type }
        ?.let { SkipRange(it.StartMs / 1000.0, it.EndMs / 1000.0) }
    val intro = bySeconds("Intro")
    val credits = bySeconds("Outro")
    return if (intro == null && credits == null) null else SkipSegments(intro, credits)
}

private val INTRO_CHAPTER = Regex("^(intro|introduction|opening)", RegexOption.IGNORE_CASE)

// "Opening Credits" is the intro, not the end credits.
private val CREDITS_CHAPTER = Regex("^(?!.*open).*(credit|outro|ending)", RegexOption.IGNORE_CASE)

// screens/player.js chapterFallbackSegments: a chapter named like an intro
// or credits runs until the next chapter (or the end).
fun chapterSkipSegments(chapters: List<ChapterInfoDto>?, runTimeTicks: Long?): SkipSegments? {
    val list: List<ChapterInfoDto> = chapters ?: return null
    if (list.isEmpty()) return null
    fun find(pattern: Regex): SkipRange? {
        val index = list.indexOfFirst { chapter -> chapter.Name?.let { pattern.containsMatchIn(it) } == true }
        if (index == -1) return null
        val start = list[index].StartPositionTicks / TICKS_PER_SECOND
        val nextTicks = list.getOrNull(index + 1)?.StartPositionTicks ?: (runTimeTicks ?: 0L)
        val end = nextTicks / TICKS_PER_SECOND
        return if (end > start) SkipRange(start, end) else null
    }
    val intro = find(INTRO_CHAPTER)
    val credits = find(CREDITS_CHAPTER)
    return if (intro == null && credits == null) null else SkipSegments(intro, credits)
}
