package com.jellio.tv.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NowPlayingItemDto(
    val Id: String? = null,
    val Name: String? = null,
    val Type: String? = null,
    val SeriesId: String? = null,
    val SeriesName: String? = null,
    val Album: String? = null,
    val AlbumId: String? = null,
    val ParentIndexNumber: Int? = null,
    val IndexNumber: Int? = null,
    val ProductionYear: Int? = null,
    val RunTimeTicks: Long? = null,
    val Source: String? = null,
    val LogoUrl: String? = null,
    val MangaId: Int? = null,
    val Page: Int? = null,
    val PageCount: Int? = null,
)

@JsonClass(generateAdapter = true)
data class NowPlayingSessionDto(
    val Id: String? = null,
    val UserId: String? = null,
    val UserName: String? = null,
    val DeviceName: String? = null,
    val Client: String? = null,
    val Activity: String? = null,
    val IsPaused: Boolean = false,
    val PositionTicks: Long? = null,
    val Item: NowPlayingItemDto? = null,
)
