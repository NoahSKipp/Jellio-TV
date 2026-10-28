package com.jellio.tv.ui.live

import com.jellio.tv.data.model.LiveChannelDto
import com.jellio.tv.data.model.LiveProgramDto
import com.jellio.tv.data.model.UserItemDataDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LiveTvModelTest {
    private val now = Instant.parse("2026-10-27T20:00:00Z").toEpochMilli()
    private fun iso(minutes: Long) = Instant.ofEpochMilli(now + minutes * 60_000).toString()

    @Test
    fun mapsChannelsWithWhatIsOnNow() {
        val list = mapChannels(
            listOf(
                LiveChannelDto(
                    Id = "dlhd.1",
                    Name = "Sky Sports F1",
                    ImageTags = mapOf("Primary" to "tag"),
                    UserData = UserItemDataDto(IsFavorite = true),
                    CurrentProgram = LiveProgramDto(Name = "Qualifying", StartDate = iso(-30), EndDate = iso(30)),
                ),
                LiveChannelDto(Id = "dlhd.2", Name = "Old", CurrentProgram = LiveProgramDto(Name = "Over", StartDate = iso(-90), EndDate = iso(-10))),
            ),
            now,
        )
        assertEquals("tag", list[0].imageTag)
        assertTrue(list[0].favorite)
        assertEquals(0.5f, list[0].now!!.progress(now), 0.001f)
        assertNull(list[1].now)
    }

    @Test
    fun searchMatchesEveryWordIgnoringAccents() {
        val list = mapChannels(
            listOf(
                LiveChannelDto(Id = "a", Name = "ESPN Deportes", CurrentProgram = LiveProgramDto(Name = "Fútbol", StartDate = iso(-1), EndDate = iso(60))),
                LiveChannelDto(Id = "b", Name = "Sky Sports", CurrentProgram = LiveProgramDto(Name = "Cricket", StartDate = iso(-1), EndDate = iso(60))),
            ),
            now,
        )
        assertEquals(listOf("a"), filterChannels(list, "espn FUTBOL").map { it.id })
        assertEquals(2, filterChannels(list, " ").size)
    }

    @Test
    fun favouritesThenLiveThenIdle() {
        val list = mapChannels(
            listOf(
                LiveChannelDto(Id = "idle", Name = "A"),
                LiveChannelDto(Id = "live", Name = "Z", CurrentProgram = LiveProgramDto(Name = "x", StartDate = iso(-1), EndDate = iso(5))),
                LiveChannelDto(Id = "fav", Name = "M", UserData = UserItemDataDto(IsFavorite = true)),
            ),
            now,
        )
        assertEquals(listOf("fav", "live", "idle"), arrangeChannels(list).all.map { it.id })
    }

    @Test
    fun guideKeepsTheNext24HoursInOrder() {
        val programs = listOf(
            LiveProgramDto(Id = "later", Name = "Later", StartDate = iso(120), EndDate = iso(180)),
            LiveProgramDto(Id = "past", Name = "Past", StartDate = iso(-120), EndDate = iso(-60)),
            LiveProgramDto(Id = "now", Name = "Now", StartDate = iso(-10), EndDate = iso(50)),
            LiveProgramDto(Id = "late", Name = "Late", StartDate = iso(25 * 60), EndDate = iso(26 * 60)),
            LiveProgramDto(Id = "bad", Name = "No dates"),
        )
        assertEquals(listOf("now", "later"), guideWindow(programs, now).map { it.id })
        val (current, next) = nowAndNext(programs, now)
        assertEquals("now", current?.id)
        assertEquals("later", next?.id)
    }

    @Test
    fun channelUpDownWraps() {
        val list = listOf("a", "b", "c").map { LiveChannel(it, it, "", null, false, null) }
        assertEquals("c", neighbourChannel(list, "a", -1)?.id)
        assertEquals("a", neighbourChannel(list, "c", 1)?.id)
        assertEquals("a", neighbourChannel(list, "missing", 1)?.id)
        assertNull(neighbourChannel(emptyList(), "a", 1))
    }

    @Test
    fun lifecycleClosesThePreviousStreamAndStopsOnce() = runBlocking {
        val closed = mutableListOf<String>()
        var n = 0
        val life = LiveLifecycle<String>({ id -> "$id-${++n}" }, { closed.add(it) })
        life.start("a")
        life.start("b")
        assertEquals(listOf("a-1"), closed)
        life.stop()
        life.stop()
        assertEquals(listOf("a-1", "b-2"), closed)
        assertNull(life.currentStream)
    }

    @Test
    fun aStreamOpenedAfterLeavingIsClosedAtOnce() = runBlocking {
        val closed = mutableListOf<String>()
        val gate = CompletableDeferred<String>()
        val life = LiveLifecycle<String>({ gate.await() }, { closed.add(it) })
        val opening = async { life.start("a") }
        yield()
        life.stop()
        gate.complete("late")
        assertNull(opening.await())
        assertEquals(listOf("late"), closed)
    }

    @Test
    fun namesGiveTypeAndLanguage() {
        assertEquals(LiveKind.SPORTS to "English", classifyChannel("Sky Sports F1 UK"))
        assertEquals(LiveKind.TV to "German", classifyChannel("RTL Germany"))
        assertEquals(LiveKind.SPORTS to "Spanish", classifyChannel("ESPN Deportes"))
        assertEquals(LiveKind.TV to null, classifyChannel("Channel 4"))
    }

    @Test
    fun filtersCombineWithSearchAndFacetsCount() {
        val list = mapChannels(
            listOf("Sky Sports UK", "BBC One UK", "RTL Germany", "DAZN 1 Germany").mapIndexed { i, n -> LiveChannelDto(Id = "c$i", Name = n) },
            now,
        )
        assertEquals(listOf("c0", "c3"), filterChannels(list, "", LiveFilters(kind = LiveKind.SPORTS)).map { it.id })
        assertEquals(listOf("c3"), filterChannels(list, "dazn", LiveFilters(LiveKind.SPORTS, "German")).map { it.id })
        assertEquals(listOf("English" to 2, "German" to 2), languageFacets(list))
    }
}
