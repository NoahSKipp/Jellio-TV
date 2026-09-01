package com.jellio.tv.data.model

import com.squareup.moshi.JsonClass

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
