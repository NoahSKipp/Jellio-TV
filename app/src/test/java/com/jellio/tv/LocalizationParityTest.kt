package com.jellio.tv

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.pickAnimeRows
import com.jellio.tv.data.model.pickMovieRows
import com.jellio.tv.data.model.pickTimeOfDayCopy
import com.jellio.tv.data.prefs.AppLanguage
import com.jellio.tv.data.prefs.DisplayPreferences
import com.jellio.tv.ui.changelog.CHANGELOG_RELEASES
import com.jellio.tv.data.episodeReleaseLabel
import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.data.i18n.StringsDe
import com.jellio.tv.data.i18n.StringsEn
import com.jellio.tv.data.i18n.StringsEs
import com.jellio.tv.data.i18n.StringsFr
import com.jellio.tv.data.i18n.StringsIt
import com.jellio.tv.data.i18n.StringsPl
import com.jellio.tv.data.i18n.StringsPt
import com.jellio.tv.data.i18n.badgeDisplayDescription
import com.jellio.tv.data.i18n.badgeDisplayName
import com.jellio.tv.data.i18n.catalogDisplayName
import com.jellio.tv.data.i18n.formatDurationMinutes
import com.jellio.tv.data.i18n.genreDisplayName
import com.jellio.tv.data.i18n.libraryDisplayName
import com.jellio.tv.data.i18n.seasonEpisodeTag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LocalizationParityTest {

    @After
    fun tearDown() {
        DisplayPreferences.setDisplayLanguageForTest(AppLanguage.ENGLISH)
    }

    @Test
    fun allSevenLanguageMapsHaveExactKeyParity() {
        val enKeys = StringsEn.MAP.keys
        assertTrue("Expected at least 750 translation keys", enKeys.size >= 750)

        val tables = mapOf(
            "de" to StringsDe.MAP,
            "es" to StringsEs.MAP,
            "fr" to StringsFr.MAP,
            "it" to StringsIt.MAP,
            "pl" to StringsPl.MAP,
            "pt" to StringsPt.MAP,
        )

        for ((lang, map) in tables) {
            val missing = enKeys - map.keys
            val extra = map.keys - enKeys
            assertTrue("Missing keys in $lang: $missing", missing.isEmpty())
            assertTrue("Extra keys in $lang: $extra", extra.isEmpty())
        }
    }

    @Test
    fun noEmDashesInAnyStringTableOrChangelog() {
        val allMaps = listOf(
            "en" to StringsEn.MAP,
            "de" to StringsDe.MAP,
            "es" to StringsEs.MAP,
            "fr" to StringsFr.MAP,
            "it" to StringsIt.MAP,
            "pl" to StringsPl.MAP,
            "pt" to StringsPt.MAP,
        )
        for ((lang, map) in allMaps) {
            for ((key, value) in map) {
                assertFalse("Em dash found in $lang[$key]: $value", value.contains('\u2014'))
            }
        }
        for (release in CHANGELOG_RELEASES) {
            assertFalse("Em dash in release title ${release.version}", release.title.contains('\u2014'))
            assertFalse("Em dash in release subtitle ${release.version}", release.subtitle.contains('\u2014'))
            for (feature in release.features) {
                assertFalse("Em dash in feature title ${feature.title}", feature.title.contains('\u2014'))
                assertFalse("Em dash in feature desc ${feature.title}", feature.description.contains('\u2014'))
            }
        }
    }

    @Test
    fun stringsFormatReplacesMultipleNamedPlaceholdersInOrder() {
        DisplayPreferences.setDisplayLanguageForTest(AppLanguage.GERMAN)
        val genreOnService = Strings.format("service_genre_on", "Komödie", "Netflix")
        assertTrue("Expected both Komödie and Netflix in '$genreOnService'", genreOnService.contains("Komödie") && genreOnService.contains("Netflix"))

        val removedNotice = Strings.format("live_link_removed_notice", "ZDF", 7)
        assertTrue("Expected both ZDF and 7 in '$removedNotice'", removedNotice.contains("ZDF") && removedNotice.contains("7"))
    }

    @Test
    fun germanDisplayLanguageTranslatesNavLibrariesCatalogsBadgesAndDates() {
        DisplayPreferences.setDisplayLanguageForTest(AppLanguage.GERMAN)

        // Navigation / Libraries
        assertEquals("Filme", libraryDisplayName("Movies", "movies"))
        assertEquals("Serien", libraryDisplayName("Shows", "tvshows"))
        assertEquals("Serien", libraryDisplayName("TV Shows", "tvshows"))
        assertEquals("Anime", libraryDisplayName("Anime", "tvshows"))
        assertEquals("Bücher", libraryDisplayName("Books", "books"))

        // Catalogs & Studio Hubs
        assertEquals("Filme im Trend", catalogDisplayName("Trending Movies", "movies"))
        assertEquals("Serien im Trend", catalogDisplayName("Trending", "tvshows"))
        assertEquals("Beliebte Filme", catalogDisplayName("Popular", "movies"))
        assertEquals("Filme auf Netflix", catalogDisplayName("Netflix", "movies", "Netflix"))
        assertEquals("Serien auf Disney+", catalogDisplayName("Disney+", "tvshows", "Disney+"))

        // Genres
        assertEquals("Komödie", genreDisplayName("Comedy"))
        assertEquals("Abenteuer", genreDisplayName("Adventure"))

        // Achievement Badges (both by ID and by English fallback name/description)
        assertEquals("Filmliebhaber", badgeDisplayName("movie_buff_bronze", "Movie Buff"))
        assertEquals("Filmliebhaber", badgeDisplayName(null, "Movie Buff"))
        assertEquals("Beende 10 Filme.", badgeDisplayDescription("movie_buff_bronze", "Finish 10 movies."))
        assertEquals("Beende 10 Filme.", badgeDisplayDescription(null, "Finish 10 movies."))

        // Season / Episode & Duration tags
        assertEquals("S2 F5", seasonEpisodeTag(2, 5))
        assertEquals("F5", seasonEpisodeTag(null, 5))
        assertEquals("2 Std. 15 Min.", formatDurationMinutes(135))

        // Release Date badges in German
        val nowCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val epTomorrow = BaseItemDto(
            Id = "ep-1",
            Type = "Episode",
            PremiereDate = "2026-11-11T00:00:00.000Z",
        )
        val epIn5Days = BaseItemDto(
            Id = "ep-2",
            Type = "Episode",
            PremiereDate = "2026-11-15T00:00:00.000Z",
        )
        assertEquals("Morgen", episodeReleaseLabel(epTomorrow, nowMs = nowCal.timeInMillis))
        assertEquals("In 5 Tagen", episodeReleaseLabel(epIn5Days, nowMs = nowCal.timeInMillis))

        // Editorial time-of-day and movie/anime row titles in German
        val morningCopy = pickTimeOfDayCopy(9)
        assertTrue("Morning kicker should not be empty", morningCopy.kicker.isNotEmpty())
        val movieRow = pickMovieRows(9)
        assertTrue("Movie row title should not be empty", movieRow.title.isNotEmpty())
        val animeRow = pickAnimeRows(9)
        assertTrue("Anime row title should not be empty", animeRow.title.isNotEmpty())
    }
}
