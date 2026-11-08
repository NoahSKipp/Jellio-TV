package com.jellio.tv.ui.update

import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

// Hosts a release asset is served from: the download link on github.com
// redirects to GitHub's asset storage.
private val GITHUB_ASSET_HOSTS = setOf(
    "github.com",
    "api.github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
)

private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

internal fun isTrustedGitHubUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    if (uri.rawUserInfo != null) return false
    if (uri.port != -1 && uri.port != 443) return false
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
    return host in GITHUB_ASSET_HOSTS
}

internal data class ReleaseAsset(val name: String, val url: String)

internal data class UpdateAssets(val apkName: String, val apkUrl: String, val sha256Url: String)

// The APK named for this release's version, and its checksum, both on GitHub.
// Null while either is missing (the release is published before they are
// attached) or served from anywhere else.
internal fun selectUpdateAssets(tag: String, assets: List<ReleaseAsset>): UpdateAssets? {
    val bases = listOf(tag, tag.removePrefix("v")).distinct().map { "jellio-tv-$it" }
    for (base in bases) {
        val apk = assets.firstOrNull { it.name == "$base.apk" } ?: continue
        val sha = assets.firstOrNull { it.name == "$base.sha256" } ?: continue
        if (isTrustedGitHubUrl(apk.url) && isTrustedGitHubUrl(sha.url)) {
            return UpdateAssets(apk.name, apk.url, sha.url)
        }
    }
    return null
}

// Reads sha256sum output ("<hash>  <file>", "<hash> *<file>") or a bare hash.
// With several lines, the one naming the file wins.
internal fun parseSha256File(text: String, fileName: String): String? {
    val entries = text.removePrefix("﻿").lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            val hash = parts[0]
            if (!SHA256_HEX.matches(hash)) return@mapNotNull null
            val name = parts.getOrNull(1)?.trim()?.removePrefix("*")?.substringAfterLast('/')
            hash.lowercase() to name
        }
        .toList()
    entries.firstOrNull { it.second == fileName }?.let { return it.first }
    val unnamed = entries.filter { it.second.isNullOrEmpty() }
    if (entries.size == 1 && unnamed.size == 1) return unnamed[0].first
    return null
}

internal fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().toHex()
}

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

// The installed app's current signers must sign the update as well, or the
// update's rotation history must vouch for all of them.
internal fun signersMatch(installed: Set<String>, archiveCurrent: Set<String>, archiveHistory: Set<String>): Boolean {
    if (installed.isEmpty() || archiveCurrent.isEmpty()) return false
    return archiveCurrent == installed || archiveHistory.containsAll(installed)
}

internal enum class UpdateRejection { NOT_GENUINE, NOT_NEWER }

internal fun checkUpdateArchive(
    installedPackage: String,
    installedVersionCode: Long,
    installedSigners: Set<String>,
    archivePackage: String?,
    archiveVersionCode: Long,
    archiveSigners: Set<String>,
    archiveSignerHistory: Set<String>,
): UpdateRejection? {
    if (archivePackage != installedPackage) return UpdateRejection.NOT_GENUINE
    if (!signersMatch(installedSigners, archiveSigners, archiveSignerHistory)) return UpdateRejection.NOT_GENUINE
    if (archiveVersionCode <= installedVersionCode) return UpdateRejection.NOT_NEWER
    return null
}
