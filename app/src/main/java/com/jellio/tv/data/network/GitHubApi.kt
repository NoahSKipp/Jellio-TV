package com.jellio.tv.data.network

import com.jellio.tv.data.model.GitHubReleaseDto
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GitHubReleaseDto
}
