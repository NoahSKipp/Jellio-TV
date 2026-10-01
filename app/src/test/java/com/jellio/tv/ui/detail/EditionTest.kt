package com.jellio.tv.ui.detail

import com.jellio.tv.data.model.MediaSourceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionTest {

    @Test
    fun rawEditionTagIdentifiesMonochromeAndCutTags() {
        assertEquals("Noir / B&W", EditionParser.rawEditionTag(MediaSourceDto(Name = "Logan Noir 2017 1080p")))
        assertEquals("Noir / B&W", EditionParser.rawEditionTag(MediaSourceDto(Name = "Mad Max Fury Road Black & Chrome")))
        assertEquals("Noir / B&W", EditionParser.rawEditionTag(MediaSourceDto(Name = "Godzilla Minus One Minus Color 2023")))
        assertEquals("Noir / B&W", EditionParser.rawEditionTag(MediaSourceDto(Name = "Zack Snyder's Justice League Justice Is Gray")))
        assertEquals("Noir / B&W", EditionParser.rawEditionTag(MediaSourceDto(Name = "Parasite B&W Edition 2019")))
        assertEquals("Noir / B&W", EditionParser.rawEditionTag(MediaSourceDto(Name = "In Black and White Remaster")))

        assertEquals("Extended", EditionParser.rawEditionTag(MediaSourceDto(Name = "Gladiator Extended Edition 2000")))
        assertEquals("Final Cut", EditionParser.rawEditionTag(MediaSourceDto(Name = "Blade Runner Final Cut 1982")))
        assertEquals("Director's Cut", EditionParser.rawEditionTag(MediaSourceDto(Name = "Apocalypse Now Director's Cut 1979")))
        assertEquals("Director's Cut", EditionParser.rawEditionTag(MediaSourceDto(Name = "Watchmen DC 2009")))
        assertEquals("Theatrical", EditionParser.rawEditionTag(MediaSourceDto(Name = "Avatar Theatrical Cut 2009")))
        assertEquals("Unrated", EditionParser.rawEditionTag(MediaSourceDto(Name = "The Wolf of Wall Street Unrated")))
        assertEquals("Redux", EditionParser.rawEditionTag(MediaSourceDto(Name = "Apocalypse Now Redux 2001")))
        assertEquals("IMAX", EditionParser.rawEditionTag(MediaSourceDto(Name = "Dune IMAX Enhanced 2021")))
        assertNull(EditionParser.rawEditionTag(MediaSourceDto(Name = "Inception 2010 1080p BluRay")))
    }

    @Test
    fun activationThresholdReturnsEmptyWhenFewerThanTwoEditions() {
        val singleStandard = listOf(
            MediaSourceDto(Id = "1", Name = "Inception 2010 1080p"),
            MediaSourceDto(Id = "2", Name = "Inception 2010 4K"),
        )
        assertTrue(EditionParser.getAvailableEditions(singleStandard).isEmpty())

        val singleEditionOnly = listOf(
            MediaSourceDto(Id = "1", Name = "Logan Noir 1080p"),
            MediaSourceDto(Id = "2", Name = "Logan Noir 4K"),
        )
        assertTrue(EditionParser.getAvailableEditions(singleEditionOnly).isEmpty())
    }

    @Test
    fun monochromePairingDefaultsNonMonochromeStreamsToColor() {
        val sources = listOf(
            MediaSourceDto(Id = "1", Name = "Logan 2017 4K HDR"),
            MediaSourceDto(Id = "2", Name = "Logan Noir 2017 1080p BluRay"),
        )
        val editions = EditionParser.getAvailableEditions(sources)
        assertEquals(listOf("Color", "Noir / B&W"), editions)
        assertEquals("Color", EditionParser.detectSourceEdition(sources[0], sources))
        assertEquals("Noir / B&W", EditionParser.detectSourceEdition(sources[1], sources))
    }

    @Test
    fun cutsPairingDefaultsUnlabelledStreamsToTheatricalWhenAlternateCutExists() {
        val sources = listOf(
            MediaSourceDto(Id = "1", Name = "Gladiator 2000 1080p"),
            MediaSourceDto(Id = "2", Name = "Gladiator Extended Edition 2000 4K"),
        )
        val editions = EditionParser.getAvailableEditions(sources)
        assertEquals(listOf("Theatrical", "Extended"), editions)
        assertEquals("Theatrical", EditionParser.detectSourceEdition(sources[0], sources))
        assertEquals("Extended", EditionParser.detectSourceEdition(sources[1], sources))
    }

    @Test
    fun multipleCutsCorrectlyDetectsTheatricalExtendedAndDirectorsCut() {
        val sources = listOf(
            MediaSourceDto(Id = "1", Name = "Kingdom of Heaven Theatrical 2005"),
            MediaSourceDto(Id = "2", Name = "Kingdom of Heaven Director's Cut 2005"),
            MediaSourceDto(Id = "3", Name = "Kingdom of Heaven Extended 2005"),
        )
        val editions = EditionParser.getAvailableEditions(sources)
        assertEquals(listOf("Theatrical", "Extended", "Director's Cut"), editions)
        assertEquals("Theatrical", EditionParser.detectSourceEdition(sources[0], sources))
        assertEquals("Director's Cut", EditionParser.detectSourceEdition(sources[1], sources))
        assertEquals("Extended", EditionParser.detectSourceEdition(sources[2], sources))
    }

    @Test
    fun groupSourcesByEditionGroupsAccurately() {
        val sources = listOf(
            MediaSourceDto(Id = "1", Name = "Blade Runner 1982 1080p"),
            MediaSourceDto(Id = "2", Name = "Blade Runner Final Cut 1982 4K"),
            MediaSourceDto(Id = "3", Name = "Blade Runner Director's Cut 1992 1080p"),
        )
        val groups = EditionParser.groupSourcesByEdition(sources)
        assertEquals(3, groups.size)
        assertEquals("Theatrical", groups[0].name)
        assertTrue(groups[0].isDefault)
        assertEquals(1, groups[0].sources.size)
        assertEquals("1", groups[0].sources[0].Id)

        assertEquals("Director's Cut", groups[1].name)
        assertEquals(1, groups[1].sources.size)
        assertEquals("3", groups[1].sources[0].Id)

        assertEquals("Final Cut", groups[2].name)
        assertEquals(1, groups[2].sources.size)
        assertEquals("2", groups[2].sources[0].Id)
    }
}
