package com.jellio.tv.data

import javax.inject.Inject
import javax.inject.Singleton

internal fun encodeAvatarId(id: String): String =
    id.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }

@Singleton
class ImageUrls @Inject constructor() {
    fun userImageUrl(serverAddress: String, userId: String, tag: String?, maxWidth: Int = 300, accessToken: String? = null): String {
        val cleanServer = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        val base = "$cleanServer/Users/$userId/Images/Primary?maxWidth=$maxWidth"
        val withTag = if (!tag.isNullOrEmpty()) "$base&tag=$tag" else base
        return if (!accessToken.isNullOrEmpty()) "$withTag&ApiKey=$accessToken&api_key=$accessToken" else withTag
    }

    fun bannerUrl(serverAddress: String, accessToken: String, userId: String): String {
        val cleanServer = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        return "$cleanServer/Jellio/profile/banner/$userId?ApiKey=$accessToken&api_key=$accessToken&t=${System.currentTimeMillis()}"
    }

    fun avatarPresetUrl(serverAddress: String, accessToken: String, id: String): String =
        "$serverAddress/Jellio/avatars/${encodeAvatarId(id)}?ApiKey=$accessToken&api_key=$accessToken"

    fun imageUrl(
        serverAddress: String,
        itemId: String,
        tag: String?,
        imageType: String = "Primary",
        maxWidth: Int = 400,
        accessToken: String? = null,
    ): String {
        if (!tag.isNullOrEmpty() && (tag.startsWith("http://") || tag.startsWith("https://"))) {
            return tag
        }
        if (itemId.startsWith("http://") || itemId.startsWith("https://")) {
            return itemId
        }
        val cleanServer = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        val base = "$cleanServer/Items/$itemId/Images/$imageType?maxWidth=$maxWidth&quality=85"
        val withTag = if (!tag.isNullOrEmpty()) "$base&tag=$tag" else base
        return if (!accessToken.isNullOrEmpty()) "$withTag&ApiKey=$accessToken&api_key=$accessToken" else withTag
    }
}
