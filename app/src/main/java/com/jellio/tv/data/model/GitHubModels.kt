package com.jellio.tv.data.model

import com.squareup.moshi.JsonClass

// GitHub's own real REST API, GET /repos/{owner}/{repo}/releases/latest
// (AppUpdateViewModel's own real update check): field names left
// exactly as GitHub's own real JSON keys, same real convention this
// codebase's own JellyfinModels.kt already keeps for a server's own
// real field names rather than an app-local rename.
@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    val tag_name: String,
    val html_url: String,
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseAssetDto(
    val name: String,
    val browser_download_url: String,
)
