package com.jellio.tv.data

import com.jellio.tv.data.model.ClientConfigDto
import com.jellio.tv.data.network.JellyfinApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellioConfigRepository @Inject constructor(
    private val api: JellyfinApi,
) {
    suspend fun getJellioConfig(): ClientConfigDto? = catching { api.getJellioConfig() }.getOrNull()
        ?.also { com.jellio.tv.data.playback.VideoTranscodePolicy.set(it.VideoTranscodePolicy) }

    // The admin's Video transcode policy, re-read every few minutes.
    suspend fun loadVideoTranscodePolicy(): String {
        if (!com.jellio.tv.data.playback.VideoTranscodePolicy.isFresh()) getJellioConfig()
        return com.jellio.tv.data.playback.VideoTranscodePolicy.policy
    }
}
