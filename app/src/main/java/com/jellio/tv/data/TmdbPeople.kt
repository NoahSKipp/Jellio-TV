package com.jellio.tv.data

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.DepartmentTab
import com.jellio.tv.data.model.PersonCreditItem
import com.jellio.tv.data.model.RichPersonDetails
import com.jellio.tv.data.model.TmdbPersonDetails
import com.jellio.tv.data.model.TmdbPersonSearchResponse
import com.jellio.tv.data.network.SharedHttp
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// A person's TMDB details and credits, matched against the library.
@Singleton
class TmdbPeople @Inject constructor() {
    private val tmdbMoshi: Moshi by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }

    internal suspend fun searchTmdbPersonId(name: String): Int? = withContext(Dispatchers.IO) {
        catching {
            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/person?api_key=4219e299c89411838049ab0dab19ebd5&query=$encodedName"
            val request = okhttp3.Request.Builder().url(url).build()
            SharedHttp.plainClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@catching null
                val body = response.body?.string() ?: return@catching null
                val adapter = tmdbMoshi.adapter(TmdbPersonSearchResponse::class.java)
                adapter.fromJson(body)?.results?.firstOrNull()?.id
            }
        }.getOrNull()
    }

    internal suspend fun fetchTmdbPersonDetails(tmdbId: Int): TmdbPersonDetails? = withContext(Dispatchers.IO) {
        catching {
            val url = "https://api.themoviedb.org/3/person/$tmdbId?api_key=4219e299c89411838049ab0dab19ebd5&append_to_response=combined_credits,external_ids"
            val request = okhttp3.Request.Builder().url(url).build()
            SharedHttp.plainClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@catching null
                val body = response.body?.string() ?: return@catching null
                val adapter = tmdbMoshi.adapter(TmdbPersonDetails::class.java)
                adapter.fromJson(body)
            }
        }.getOrNull()
    }

    internal fun buildRichPersonDetails(
        personId: String,
        person: BaseItemDto,
        tmdb: TmdbPersonDetails,
        localFilmography: List<BaseItemDto>,
    ): RichPersonDetails {
        val localByTmdb = localFilmography
            .filter { !it.ProviderIds?.get("Tmdb").isNullOrBlank() }
            .associateBy { it.ProviderIds!!["Tmdb"]!! }

        val localByNameAndYear = localFilmography.associateBy {
            normalizeTitleForMatching(it.Name.orEmpty()) + ":" + (it.ProductionYear ?: 0)
        }

        val allCredits = mutableListOf<PersonCreditItem>()

        val castList = tmdb.combined_credits?.cast.orEmpty()
        for (cast in castList) {
            val title = cast.title ?: cast.name ?: continue
            val mediaType = if (cast.media_type == "tv") "Series" else "Movie"
            val releaseDate = cast.release_date ?: cast.first_air_date
            val year = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()

            val localItem = localByTmdb[cast.id.toString()]
                ?: (if (year != null) localByNameAndYear[normalizeTitleForMatching(title) + ":" + year] else null)

            val inLibrary = localItem != null
            val creditId = localItem?.Id ?: generateDeterministicVirtualId(if (cast.media_type == "tv") "tv" else "movie", cast.id.toString())

            val baseItem = localItem ?: BaseItemDto(
                Id = creditId,
                Name = title,
                Type = mediaType,
                ProductionYear = year,
                PremiereDate = releaseDate,
                Overview = cast.overview,
                CommunityRating = cast.vote_average,
                PrimaryImageTag = cast.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                BackdropImageTags = cast.backdrop_path?.let { listOf("https://image.tmdb.org/t/p/original$it") },
                ProviderIds = mapOf("Tmdb" to cast.id.toString()),
            )

            allCredits.add(
                PersonCreditItem(
                    id = creditId,
                    tmdbId = cast.id,
                    title = title,
                    mediaType = mediaType,
                    year = year,
                    releaseDate = releaseDate,
                    role = cast.character?.takeIf { it.isNotBlank() },
                    department = "Acting",
                    overview = cast.overview,
                    posterUrl = cast.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = cast.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" },
                    rating = cast.vote_average,
                    popularity = cast.popularity ?: 0.0,
                    voteCount = cast.vote_count ?: 0,
                    inLibrary = inLibrary,
                    baseItem = baseItem,
                )
            )
        }

        val crewList = tmdb.combined_credits?.crew.orEmpty()
        for (crew in crewList) {
            val title = crew.title ?: crew.name ?: continue
            val mediaType = if (crew.media_type == "tv") "Series" else "Movie"
            val releaseDate = crew.release_date ?: crew.first_air_date
            val year = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()

            val dept = when (crew.department?.lowercase(java.util.Locale.US)) {
                "directing" -> "Directing"
                "writing" -> "Writing"
                "production" -> "Production"
                else -> "Other"
            }

            val localItem = localByTmdb[crew.id.toString()]
                ?: (if (year != null) localByNameAndYear[normalizeTitleForMatching(title) + ":" + year] else null)

            val inLibrary = localItem != null
            val creditId = localItem?.Id ?: generateDeterministicVirtualId(if (crew.media_type == "tv") "tv" else "movie", crew.id.toString())

            val baseItem = localItem ?: BaseItemDto(
                Id = creditId,
                Name = title,
                Type = mediaType,
                ProductionYear = year,
                PremiereDate = releaseDate,
                Overview = crew.overview,
                CommunityRating = crew.vote_average,
                PrimaryImageTag = crew.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                BackdropImageTags = crew.backdrop_path?.let { listOf("https://image.tmdb.org/t/p/original$it") },
                ProviderIds = mapOf("Tmdb" to crew.id.toString()),
            )

            allCredits.add(
                PersonCreditItem(
                    id = creditId,
                    tmdbId = crew.id,
                    title = title,
                    mediaType = mediaType,
                    year = year,
                    releaseDate = releaseDate,
                    role = crew.job ?: crew.department,
                    department = dept,
                    overview = crew.overview,
                    posterUrl = crew.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = crew.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" },
                    rating = crew.vote_average,
                    popularity = crew.popularity ?: 0.0,
                    voteCount = crew.vote_count ?: 0,
                    inLibrary = inLibrary,
                    baseItem = baseItem,
                )
            )
        }

        val dedupedCredits = allCredits
            .groupBy { it.tmdbId.toString() + ":" + it.department }
            .values
            .map { list -> list.maxByOrNull { it.popularity } ?: list.first() }
            .sortedByDescending { it.releaseDate ?: "${it.year ?: 0}" }

        val knownFor = dedupedCredits
            .distinctBy { it.tmdbId }
            .sortedByDescending { (it.voteCount * (it.rating ?: 0.0)) + (it.popularity * 2.0) }
            .take(10)

        val bestBackdropUrl = knownFor.firstOrNull { !it.backdropUrl.isNullOrBlank() }?.backdropUrl
            ?: dedupedCredits.firstOrNull { !it.backdropUrl.isNullOrBlank() }?.backdropUrl

        val age = calculateAge(tmdb.birthday, tmdb.deathday)
        val profileUrl = tmdb.profile_path?.let { "https://image.tmdb.org/t/p/h632$it" }

        val uniqueTmdbCount = dedupedCredits.distinctBy { it.tmdbId }.size
        val tabs = mutableListOf<DepartmentTab>()
        tabs.add(DepartmentTab("All", com.jellio.tv.data.i18n.Strings.get("person_tab_all"), uniqueTmdbCount))

        val actingCount = dedupedCredits.count { it.department == "Acting" }
        if (actingCount > 0) {
            tabs.add(DepartmentTab("Acting", com.jellio.tv.data.i18n.Strings.get("person_tab_acting"), actingCount))
        }
        val directingCount = dedupedCredits.count { it.department == "Directing" }
        if (directingCount > 0) {
            tabs.add(DepartmentTab("Directing", com.jellio.tv.data.i18n.Strings.get("person_tab_directing"), directingCount))
        }
        val writingCount = dedupedCredits.count { it.department == "Writing" }
        if (writingCount > 0) {
            tabs.add(DepartmentTab("Writing", com.jellio.tv.data.i18n.Strings.get("person_tab_writing"), writingCount))
        }
        val productionCount = dedupedCredits.count { it.department == "Production" }
        if (productionCount > 0) {
            tabs.add(DepartmentTab("Production", com.jellio.tv.data.i18n.Strings.get("person_tab_production"), productionCount))
        }
        val otherCount = dedupedCredits.count { it.department == "Other" }
        if (otherCount > 0) {
            tabs.add(DepartmentTab("Other", com.jellio.tv.data.i18n.Strings.get("person_tab_other"), otherCount))
        }

        return RichPersonDetails(
            id = personId,
            name = tmdb.name?.takeIf { it.isNotBlank() } ?: person.Name.orEmpty(),
            biography = tmdb.biography?.takeIf { it.isNotBlank() } ?: person.Overview,
            birthday = tmdb.birthday,
            deathday = tmdb.deathday,
            placeOfBirth = tmdb.place_of_birth,
            age = age,
            knownForDepartment = tmdb.known_for_department ?: "Acting",
            profileUrl = profileUrl,
            backdropUrl = bestBackdropUrl,
            imdbId = tmdb.external_ids?.imdb_id ?: person.ProviderIds?.get("Imdb"),
            tmdbId = tmdb.id,
            totalCreditsCount = uniqueTmdbCount,
            knownFor = knownFor,
            allCredits = dedupedCredits,
            departmentTabs = tabs,
        )
    }

    internal fun buildFallbackPersonDetails(
        personId: String,
        person: BaseItemDto,
        localFilmography: List<BaseItemDto>,
    ): RichPersonDetails {
        val credits = localFilmography.map { item ->
            val tmdbId = item.ProviderIds?.get("Tmdb")?.toIntOrNull() ?: 0
            PersonCreditItem(
                id = item.Id,
                tmdbId = tmdbId,
                title = item.Name.orEmpty(),
                mediaType = item.Type ?: "Movie",
                year = item.ProductionYear,
                releaseDate = item.PremiereDate,
                role = item.Type,
                department = "Acting",
                overview = item.Overview,
                posterUrl = null,
                backdropUrl = null,
                rating = item.CommunityRating,
                popularity = 0.0,
                voteCount = 0,
                inLibrary = true,
                baseItem = item,
            )
        }
        val knownFor = credits.take(10)
        val tabs = listOf(
            DepartmentTab("All", com.jellio.tv.data.i18n.Strings.get("person_tab_all"), credits.size),
            DepartmentTab("Acting", com.jellio.tv.data.i18n.Strings.get("person_tab_acting"), credits.size),
        )
        return RichPersonDetails(
            id = personId,
            name = person.Name.orEmpty(),
            biography = person.Overview,
            birthday = null,
            deathday = null,
            placeOfBirth = null,
            age = null,
            knownForDepartment = "Acting",
            profileUrl = null,
            backdropUrl = null,
            imdbId = person.ProviderIds?.get("Imdb"),
            tmdbId = person.ProviderIds?.get("Tmdb")?.toIntOrNull(),
            totalCreditsCount = credits.size,
            knownFor = knownFor,
            allCredits = credits,
            departmentTabs = tabs,
        )
    }

    private fun normalizeTitleForMatching(title: String): String =
        title.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9]"), "")

    private fun calculateAge(birthday: String?, deathday: String?): Int? {
        if (birthday.isNullOrBlank()) return null
        return runCatching {
            val birthParts = birthday.split("-").mapNotNull { it.toIntOrNull() }
            if (birthParts.size < 3) return@runCatching null
            val birthCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                set(birthParts[0], birthParts[1] - 1, birthParts[2], 0, 0, 0)
            }
            val endCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                if (!deathday.isNullOrBlank()) {
                    val deathParts = deathday.split("-").mapNotNull { it.toIntOrNull() }
                    if (deathParts.size >= 3) {
                        set(deathParts[0], deathParts[1] - 1, deathParts[2], 0, 0, 0)
                    }
                }
            }
            var age = endCal.get(java.util.Calendar.YEAR) - birthCal.get(java.util.Calendar.YEAR)
            if (endCal.get(java.util.Calendar.DAY_OF_YEAR) < birthCal.get(java.util.Calendar.DAY_OF_YEAR)) {
                age--
            }
            age.takeIf { it in 0..130 }
        }.getOrNull()
    }
}
