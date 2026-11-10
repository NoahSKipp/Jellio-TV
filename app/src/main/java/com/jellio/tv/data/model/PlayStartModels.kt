package com.jellio.tv.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlayStartStages(
    val DetailsMs: Int? = null,
    val PlaybackInfoMs: Int? = null,
    val PlayerReadyMs: Int? = null,
    val FirstByteMs: Int? = null,
    val FirstFrameMs: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PlayStartRecord(
    val ItemId: String,
    val MediaSourceId: String?,
    val Client: String = "tv",
    val Trigger: String,
    val From: String,
    val WarmAtPlay: Boolean?,
    val DirectPlay: Boolean?,
    val Stages: PlayStartStages,
    val TotalMs: Int,
)
