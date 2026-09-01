package com.jellio.tv.data

import com.jellio.tv.data.model.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Test

class DistinctTitlesTest {
    @Test
    fun foldsAliasedCopiesOfOneTitle() {
        val heat = BaseItemDto(Id = "a", Name = "Heat", ProductionYear = 1995)
        val alias = BaseItemDto(Id = "b", Name = "Heat ", ProductionYear = 1995)
        val remake = BaseItemDto(Id = "c", Name = "Heat", ProductionYear = 2026)
        val other = BaseItemDto(Id = "d", Name = "Ronin", ProductionYear = 1998)
        assertEquals(listOf("a", "c", "d"), distinctTitles(listOf(heat, alias, heat, remake, other)).map { it.Id })
    }
}
