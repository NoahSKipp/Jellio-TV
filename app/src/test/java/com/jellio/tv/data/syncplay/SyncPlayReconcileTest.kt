package com.jellio.tv.data.syncplay

import com.jellio.tv.data.model.SyncPlayGroupDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlayReconcileTest {
    private val movieNight = SyncPlayGroupDto(GroupId = "aaaa-1111", GroupName = "Movie night", Participants = listOf("noah", "mia"))
    private val other = SyncPlayGroupDto(GroupId = "bbbb-2222", GroupName = "Other", Participants = listOf("sam"))

    private fun decide(
        groups: List<SyncPlayGroupDto>,
        stored: String? = null,
        current: SyncGroup? = null,
        pending: String? = null,
        grace: Boolean = false,
    ) = decideReconcile(groups, stored, current, "noah", pending, grace)

    @Test
    fun nothingToDoOutsideAnyGroup() {
        assertEquals(ReconcileAction.None, decide(listOf(movieNight, other)))
    }

    @Test
    fun joinsStoredGroupWhenNotInItLocally() {
        assertEquals(ReconcileAction.Join(movieNight), decide(listOf(movieNight), stored = "AAAA1111"))
    }

    @Test
    fun adoptsJustCreatedGroupByName() {
        assertEquals(ReconcileAction.Join(movieNight), decide(listOf(movieNight), pending = "Movie night"))
    }

    @Test
    fun explicitLeaveGraceBlocksRejoin() {
        assertEquals(ReconcileAction.None, decide(listOf(movieNight), stored = "aaaa-1111", grace = true))
    }

    @Test
    fun staysPutWhileListedInOwnGroup() {
        val current = SyncGroup("aaaa-1111", "Movie night", listOf("noah", "mia"))
        assertEquals(ReconcileAction.None, decide(listOf(movieNight), stored = "aaaa-1111", current = current))
    }

    @Test
    fun resetsWhenGroupIsGone() {
        val current = SyncGroup("aaaa-1111", "Movie night", listOf("noah"))
        assertEquals(ReconcileAction.Reset, decide(listOf(other), stored = "aaaa-1111", current = current))
    }

    @Test
    fun rejoinsWhenDroppedFromGroupThatStillRuns() {
        val dropped = movieNight.copy(Participants = listOf("mia"))
        val current = SyncGroup("aaaa-1111", "Movie night", listOf("noah", "mia"))
        assertEquals(ReconcileAction.Join(dropped), decide(listOf(dropped, other), stored = "aaaa-1111", current = current))
    }

    @Test
    fun resetsInsteadOfRejoiningWhenWatchingElsewhere() {
        val dropped = movieNight.copy(Participants = listOf("mia"))
        val elsewhere = other.copy(Participants = listOf("sam", "noah"))
        val current = SyncGroup("aaaa-1111", "Movie night", listOf("noah", "mia"))
        assertEquals(ReconcileAction.Reset, decide(listOf(dropped, elsewhere), stored = "aaaa-1111", current = current))
    }

    @Test
    fun unknownUserNameNeverTriggersRejoin() {
        val dropped = movieNight.copy(Participants = listOf("mia"))
        val current = SyncGroup("aaaa-1111", "Movie night", listOf("mia"))
        assertEquals(ReconcileAction.None, decideReconcile(listOf(dropped), "aaaa-1111", current, "", null, false))
    }
}
