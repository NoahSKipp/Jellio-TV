package com.jellio.tv.data.model

import androidx.compose.runtime.Immutable
import com.squareup.moshi.JsonClass

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbExternalIds(
    val imdb_id: String? = null,
    val wikidata_id: String? = null,
    val facebook_id: String? = null,
    val instagram_id: String? = null,
    val twitter_id: String? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbCastCredit(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val media_type: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val character: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val vote_average: Double? = null,
    val vote_count: Int? = null,
    val popularity: Double? = null,
    val order: Int? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbCrewCredit(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val media_type: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val department: String? = null,
    val job: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val vote_average: Double? = null,
    val vote_count: Int? = null,
    val popularity: Double? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbCombinedCredits(
    val cast: List<TmdbCastCredit>? = null,
    val crew: List<TmdbCrewCredit>? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbPersonDetails(
    val id: Int,
    val name: String? = null,
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    val place_of_birth: String? = null,
    val known_for_department: String? = null,
    val profile_path: String? = null,
    val external_ids: TmdbExternalIds? = null,
    val combined_credits: TmdbCombinedCredits? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbPersonSearchResult(
    val id: Int,
    val name: String? = null,
    val profile_path: String? = null,
    val known_for_department: String? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbPersonSearchResponse(
    val results: List<TmdbPersonSearchResult>? = null,
)

@Immutable
data class PersonCreditItem(
    val id: String,
    val tmdbId: Int,
    val title: String,
    val mediaType: String,
    val year: Int?,
    val releaseDate: String?,
    val role: String?,
    val department: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double?,
    val popularity: Double,
    val voteCount: Int,
    val inLibrary: Boolean,
    val baseItem: BaseItemDto,
)

@Immutable
data class DepartmentTab(
    val key: String,
    val label: String,
    val count: Int,
)

@Immutable
data class RichPersonDetails(
    val id: String,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val age: Int?,
    val knownForDepartment: String?,
    val profileUrl: String?,
    val backdropUrl: String?,
    val imdbId: String?,
    val tmdbId: Int?,
    val totalCreditsCount: Int,
    val knownFor: List<PersonCreditItem>,
    val allCredits: List<PersonCreditItem>,
    val departmentTabs: List<DepartmentTab>,
)
