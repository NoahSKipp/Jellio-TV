package com.jellio.tv.data

import com.jellio.tv.data.model.AddedByEntryDto
import com.jellio.tv.data.model.AddedByResponseDto
import com.jellio.tv.data.model.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendsRulesTest {
    @Test
    fun failuresFromStatusCodes() {
        assertEquals(FriendFailure.Private, friendFailureOf(403, "private"))
        assertEquals(FriendFailure.Closed, friendFailureOf(403, "closed"))
        assertEquals(FriendFailure.Failed, friendFailureOf(403, null))
        assertEquals(FriendFailure.Cooldown, friendFailureOf(429, "none"))
        assertEquals(FriendFailure.FriendLimit, friendFailureOf(409, "none"))
        assertEquals(FriendFailure.Gone, friendFailureOf(404, null))
        assertEquals(FriendFailure.Failed, friendFailureOf(500, null))
    }

    @Test
    fun statusIsReadFromErrorBodies() {
        assertEquals("private", statusFromBody("{\"Status\":\"private\"}"))
        assertEquals("none", statusFromBody("{\"Status\": \"None\", \"Error\":\"FriendLimit\"}"))
        assertNull(statusFromBody("not json"))
        assertNull(statusFromBody(null))
    }

    @Test
    fun addedByIsKeyedPlainAndLimited() {
        val entries = (1..5).map { AddedByEntryDto(UserId = "u$it", UserName = "U$it") }
        val map = addedByMap(AddedByResponseDto(mapOf("AB-CD" to entries, "ee" to emptyList())))
        assertEquals(setOf("abcd"), map.keys)
        assertEquals(listOf("u1", "u2", "u3"), map.getValue("abcd").map { it.UserId })
        val items = listOf(BaseItemDto(Id = "abcd"), BaseItemDto(Id = "zz"))
        val out = items.withAddedBy(map)
        assertEquals(3, out[0].AddedBy?.size)
        assertNull(out[1].AddedBy)
    }
}
