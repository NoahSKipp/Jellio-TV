package com.jellio.tv

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.ChapterInfoDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.playback.SkipRange
import com.jellio.tv.data.playback.SkipSegments
import com.jellio.tv.data.playback.chapterSkipSegments
import com.jellio.tv.data.playback.mergeSkipSegments
import com.jellio.tv.data.streams.FailoverCounts
import com.jellio.tv.data.streams.FailoverLoad
import com.jellio.tv.data.streams.FailoverOutcome
import com.jellio.tv.data.streams.FailoverPlan
import com.jellio.tv.data.streams.ListError
import com.jellio.tv.data.streams.ListOrigin
import com.jellio.tv.data.streams.failoverOutcome
import com.jellio.tv.data.streams.planFailover
import com.jellio.tv.data.syncplay.syncDriftMs
import com.jellio.tv.data.syncplay.syncTargetTicks
import com.jellio.tv.data.clearVerifiedTodayStreamIds
import com.jellio.tv.data.isReleasedToday
import com.jellio.tv.data.isUnreleasedItem
import com.jellio.tv.data.markTodayItemHasStream
import com.jellio.tv.ui.player.BadStreams
import com.jellio.tv.ui.player.SkipKind
import com.jellio.tv.ui.player.activeSkipSegment
import com.jellio.tv.ui.player.looksLikePlaceholder
import com.jellio.tv.ui.player.looksLikeSilentSlideshow
import com.jellio.tv.ui.player.normalizeLanguage
import com.jellio.tv.ui.player.orderByLanguage
import com.jellio.tv.ui.player.preferredStreamLanguage
import com.jellio.tv.ui.player.shouldShowUpNextNow
import com.jellio.tv.ui.player.stoppedTooEarly
import com.squareup.moshi.Moshi
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// The shared web/TV vectors (app/src/test/resources/vectors, a copy of
// Jellio-Plugin's tests/vectors), run against the Kotlin functions.
@Suppress("UNCHECKED_CAST")
class VectorsTest {
    private val json = Moshi.Builder().build().adapter(Any::class.java)

    // Gradle runs unit tests from the module directory; the classpath copy
    // covers runs from anywhere else.
    private fun vectorFiles(): List<File> {
        val dir = javaClass.getResource("/vectors")?.takeIf { it.protocol == "file" }?.let { File(it.toURI()) }
            ?: File("src/test/resources/vectors")
        return dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
    }

    private fun load(file: File): Map<String, Any?> = json.fromJson(file.readText()) as Map<String, Any?>

    private fun Map<String, Any?>.str(key: String): String? = this[key] as String?
    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as Double?)?.toLong()
    private fun Map<String, Any?>.double(key: String): Double = this[key] as Double
    private fun Map<String, Any?>.map(key: String): Map<String, Any?>? = this[key] as Map<String, Any?>?
    private fun Map<String, Any?>.list(key: String): List<Any?> = this[key] as List<Any?>
    private fun Map<String, Any?>.strings(key: String): List<String> = list(key) as List<String>

    // Moshi reads every number as a Double; compare actual values the same way.
    private fun canonical(value: Any?): Any? = when (value) {
        is Number -> value.toDouble()
        is Map<*, *> -> value.entries.associate { it.key.toString() to canonical(it.value) }
        is List<*> -> value.map { canonical(it) }
        else -> value
    }

    private fun source(m: Map<String, Any?>) = MediaSourceDto(Id = m.str("id"), Name = m.str("name"))

    private fun range(m: Map<String, Any?>?): SkipRange? = m?.let { SkipRange(it.double("start"), it.double("end")) }

    private fun segments(m: Map<String, Any?>?): SkipSegments? = m?.let { SkipSegments(range(it.map("intro")), range(it.map("credits"))) }

    private fun rangeOut(r: SkipRange?): Map<String, Double>? = r?.takeIf { it.isValid }?.let { mapOf("start" to it.start, "end" to it.end) }

    private fun segmentsOut(s: SkipSegments?): Map<String, Any?>? = s?.let { mapOf("intro" to rangeOut(it.intro), "credits" to rangeOut(it.credits)) }

    private fun item(m: Map<String, Any?>): BaseItemDto {
        val userData = m.map("UserData")
        return BaseItemDto(
            Id = m.str("Id")!!,
            Type = m.str("Type"),
            PremiereDate = m.str("PremiereDate"),
            EndDate = m.str("EndDate"),
            LocationType = m.str("LocationType"),
            RunTimeTicks = m.long("RunTimeTicks"),
            UserData = userData?.let {
                UserItemDataDto(Played = it["Played"] == true, PlaybackPositionTicks = it.long("PlaybackPositionTicks") ?: 0L)
            },
        )
    }

    private fun badKind(input: Map<String, Any?>): String? {
        val store = HashMap<String, Long>()
        for (op in input.list("ops") as List<Map<String, Any?>>) {
            val itemId = op.str("itemId")!!
            val src = source(op.map("source")!!)
            val at = op.long("atMs")!!
            if (op.str("op") == "mark") {
                val soft = op["soft"] == true
                val until = at + if (soft) BadStreams.SOFT_LIFETIME_MS else BadStreams.LIFETIME_MS
                BadStreams.markKeys(itemId, src, soft).forEach { key -> store[key] = maxOf(store[key] ?: 0L, until) }
            } else {
                BadStreams.keys(itemId, src).forEach { store.remove(it) }
            }
        }
        val query = input.map("query")!!
        val keys = BadStreams.keys(query.str("itemId")!!, source(query.map("source")!!))
        return BadStreams.kindOf(keys.map { store[it] ?: 0L }, query.long("atMs")!!)?.name?.lowercase()
    }

    private fun plan(input: Map<String, Any?>): Map<String, Any?> {
        val sources = input.list("sources") as List<Map<String, Any?>>
        val language = input.str("language")
        val byLanguage = { list: List<Map<String, Any?>> -> orderByLanguage(list, language) { it.strings("languages") } }
        val result = planFailover(
            sources,
            input.strings("tried"),
            id = { it.str("id") },
            isBad = { s -> s.str("bad")?.let { com.jellio.tv.data.streams.BadKind.valueOf(it.uppercase()) } },
            byLanguage = byLanguage,
            needsReencode = if (input["avoidReencode"] == true) { s -> s["reencode"] == true } else null,
            blockReencode = input["blockReencode"] == true,
        )
        val c = result.counts
        return mapOf(
            "ordered" to result.ordered.map { it.str("id") },
            "counts" to mapOf(
                "total" to c.total, "tried" to c.tried, "badHard" to c.badHard, "badSoft" to c.badSoft,
                "afterLanguage" to c.afterLanguage, "afterTranscode" to c.afterTranscode,
            ),
        )
    }

    private fun outcome(input: Map<String, Any?>): Map<String, Any?> {
        val loaded = FailoverLoad<String>(
            emptyList(),
            ListOrigin.valueOf(input.str("origin")!!.uppercase()),
            input.str("errorKind")?.let { ListError(it, 0, recovered = false) },
        )
        val plan = FailoverPlan(input.strings("ordered"), FailoverCounts(0, 0, 0, 0, 0, 0))
        return when (val result = failoverOutcome(loaded, plan) { it }) {
            is FailoverOutcome.Navigate -> mapOf("action" to "navigate", "sourceId" to result.sourceId)
            is FailoverOutcome.ListFailed -> mapOf("action" to "list-failed", "kind" to result.kind)
            FailoverOutcome.NoneLeft -> mapOf("action" to "none-left")
        }
    }

    private fun drift(input: Map<String, Any?>): Map<String, Any?> {
        val command = input.str("command")!!
        val positionTicks = input.long("positionMs")?.let { it * 10_000L }
        val sinceTicks = input.long("sinceCommandMs")!! * 10_000L
        val estimate = { ticks: Long -> ticks + sinceTicks }
        val target = syncTargetTicks(command, positionTicks, estimate)
        return mapOf(
            "targetMs" to target?.let { it / 10_000L },
            "driftMs" to syncDriftMs(input.long("currentMs")!!, command, positionTicks, estimate),
        )
    }

    private fun run(fn: String, input: Map<String, Any?>): Any? = when (fn) {
        "normalize" -> normalizeLanguage(input.str("code")).orEmpty()
        "preferred" -> preferredStreamLanguage(input.str("audioPreference"), input.str("displayLanguage")).orEmpty()
        "order" -> orderByLanguage(input.list("sources") as List<Map<String, Any?>>, input.str("language")) { it.strings("languages") }
            .map { it.str("id") }

        "releaseState" -> {
            clearVerifiedTodayStreamIds()
            (input["verifiedToday"] as List<String>?)?.forEach { markTodayItemHasStream(it, true) }
            val now = Instant.parse(input.str("now")).toEpochMilli()
            val dto = item(input.map("item")!!)
            mapOf("unreleased" to isUnreleasedItem(dto, now), "releasedToday" to isReleasedToday(dto, now))
                .also { clearVerifiedTodayStreamIds() }
        }

        "looksLikePlaceholder" -> looksLikePlaceholder(input.long("actualMs")!!, input.long("expectedMs")!!)
        "silentSlideshow" -> looksLikeSilentSlideshow(
            input.double("frames").toInt(),
            input.double("seconds"),
            input["noAudio"] as Boolean?,
            input.long("expectedMs")!!,
        )
        "stoppedTooEarly" -> stoppedTooEarly(input.long("positionMs")!!, input.long("expectedMs")!!)

        "badKind" -> badKind(input)
        "planFailover" -> plan(input)
        "failoverOutcome" -> outcome(input)

        "activeSegment" -> activeSkipSegment(segments(input.map("segments")), input.double("positionSeconds"))?.let {
            mapOf("kind" to if (it.kind == SkipKind.INTRO) "intro" else "credits", "targetSeconds" to it.targetSeconds)
        }
        "upNextNow" -> shouldShowUpNextNow(segments(input.map("segments")), input.double("positionSeconds"), input.double("durationSeconds"))
        "merge" -> segmentsOut(mergeSkipSegments(segments(input.map("current")), segments(input.map("candidate")), input.double("runtimeSeconds")))
        "chapters" -> {
            val chapters = (input.list("chapters") as List<Map<String, Any?>>).map {
                ChapterInfoDto(Name = it.str("name"), StartPositionTicks = (it.double("startSeconds") * 10_000_000).toLong())
            }
            segmentsOut(chapterSkipSegments(chapters, (input.double("runtimeSeconds") * 10_000_000).toLong()))
        }

        "drift" -> drift(input)
        else -> throw IllegalArgumentException("no TV runner for $fn")
    }

    @Test
    fun vectorFilesCarryAFamilyAVersionAndCases() {
        val files = vectorFiles()
        assertTrue("no vector files", files.isNotEmpty())
        files.forEach { file ->
            val data = load(file)
            assertTrue("${file.name} family", data["family"] is String)
            val version = data["version"] as? Double
            assertTrue("${file.name} version", version != null && version >= 1 && version % 1.0 == 0.0)
            assertTrue("${file.name} cases", (data["cases"] as? List<*>)?.isNotEmpty() == true)
        }
    }

    @Test
    fun everyVectorPasses() {
        val failures = mutableListOf<String>()
        var count = 0
        vectorFiles().forEach { file ->
            val data = load(file)
            (data["cases"] as List<Map<String, Any?>>).forEach { case ->
                count++
                val name = "${data["family"]}: ${case["name"]}"
                try {
                    val actual = canonical(run(case.str("fn")!!, case.map("input")!!))
                    if (actual != canonical(case["expected"])) failures += "$name\n  expected ${case["expected"]}\n  actual   $actual"
                } catch (e: Exception) {
                    failures += "$name\n  threw $e"
                }
            }
        }
        if (failures.isNotEmpty()) fail("${failures.size} of $count vectors failed:\n" + failures.joinToString("\n"))
        assertEquals(0, failures.size)
    }
}
