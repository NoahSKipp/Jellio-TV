package com.jellio.tv.ui.player

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.playback.SkipSegments

data class SubtitleTrackUiState(
    val streamIndex: Int,
    val label: String,
    val isTextBased: Boolean,
    val url: String?,
    val language: String? = null,
    val isExternal: Boolean = false,
    val embeddedTextOrdinal: Int = -1,
)

data class PauseOverlayInfo(
    val art: List<com.jellio.tv.ui.common.ArtCandidate>,
    val logoUrl: String?,
    val title: String,
    val rating: Double?,
    val year: String?,
    val officialRating: String?,
    val isEpisode: Boolean,
    val episodeCode: String?,
    val episodeTitle: String?,
    val overview: String?,
)

data class UpNextInfo(
    val itemId: String,
    val thumbnailUrl: String?,
    val title: String,
    val overview: String? = null,
    val seriesName: String? = null,
    val episodeName: String = "",
    val episodeCode: String? = null,
)

data class EpisodePanelEntry(
    val itemId: String,
    val title: String,
    val thumbnailUrl: String?,
    val episodeCode: String?,
    val rating: String?,
    val overview: String?,
)

data class AudioTrackUiState(
    val streamIndex: Int,
    val label: String,
    val language: String? = null,
)

// Shown while an automatic retry loads: which attempt this is and the
// stream being tried.
data class FailoverNotice(val attempt: Int, val sourceLabel: String, val slowTranscode: Boolean = false)

data class PlayerUiState(
    // The title's runtime, to spot a placeholder clip far shorter than it.
    val expectedRuntimeMs: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val errorDetail: String? = null,
    // The list of other streams could not be loaded; OK tries again.
    val failoverRetry: Boolean = false,
    val streamUrl: String? = null,
    val mediaSourceId: String? = null,
    val startPositionTicks: Long = 0,
    val resumePercent: Int? = null,
    val title: String = "",
    val subtitle: String = "",
    // Poster shown by the system media controls (Google Home, the phone's remote).
    val artworkUrl: String? = null,
    // The video's frame rate, for matching the TV's refresh rate.
    val videoFrameRate: Float? = null,
    val subtitleTracks: List<SubtitleTrackUiState> = emptyList(),
    val selectedSubtitleIndex: Int? = null,
    val audioTracks: List<AudioTrackUiState> = emptyList(),
    val selectedAudioStreamIndex: Int? = null,
    val defaultAudioStreamIndex: Int? = null,
    val directPlay: Boolean = false,
    // The server is re-encoding this stream's video (Video transcode
    // policy Avoid or Block): give up on it sooner when no picture comes.
    val reencodingVideo: Boolean = false,
    val pauseInfo: PauseOverlayInfo? = null,
    val upNextInfo: UpNextInfo? = null,
    // An episode of a series, and the one before it once known.
    val isEpisode: Boolean = false,
    val previousEpisodeId: String? = null,
    val skipSegments: SkipSegments? = null,
    // Every skip data source has answered; until then a missing credits
    // range may still arrive.
    val skipSegmentsResolved: Boolean = false,
    val sleepTimerEndTimeMs: Long? = null,
    val sourceOptions: List<MediaSourceDto> = emptyList(),
    val seriesId: String? = null,
    val seasons: List<BaseItemDto> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<EpisodePanelEntry> = emptyList(),
    val toastMessage: String? = null,
    // Set while another stream is being fetched and started, so the screen
    // can say so until it actually plays.
    val switchingTo: String? = null,
    val switchingLoaded: Boolean = false,
    val toastId: Long = 0L,
)
