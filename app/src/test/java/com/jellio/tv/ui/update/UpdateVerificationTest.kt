package com.jellio.tv.ui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVerificationTest {
    private val hash = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"
    private val base = "https://github.com/NoahSKipp/Jellio-TV/releases/download"

    @Test
    fun onlyGitHubHostsOverHttpsAreTrusted() {
        assertTrue(isTrustedGitHubUrl("$base/0.84.2/jellio-tv-0.84.2.apk"))
        assertTrue(isTrustedGitHubUrl("https://objects.githubusercontent.com/github-production-release-asset/1/2"))
        assertTrue(isTrustedGitHubUrl("https://release-assets.githubusercontent.com/github-production-release-asset/1/2"))
        assertTrue(isTrustedGitHubUrl("https://api.github.com/repos/NoahSKipp/Jellio-TV/releases/assets/1"))
        assertTrue(isTrustedGitHubUrl("https://GitHub.com/x"))
        assertFalse(isTrustedGitHubUrl("http://github.com/x"))
        assertFalse(isTrustedGitHubUrl("https://github.com.evil.example/x"))
        assertFalse(isTrustedGitHubUrl("https://evilgithub.com/x"))
        assertFalse(isTrustedGitHubUrl("https://raw.githubusercontent.com/x"))
        assertFalse(isTrustedGitHubUrl("https://github.com@evil.example/x"))
        assertFalse(isTrustedGitHubUrl("https://user@github.com/x"))
        assertFalse(isTrustedGitHubUrl("https://github.com:8443/x"))
        assertFalse(isTrustedGitHubUrl("ftp://github.com/x"))
        assertFalse(isTrustedGitHubUrl("not a url"))
        assertFalse(isTrustedGitHubUrl(""))
    }

    @Test
    fun picksTheApkAndChecksumNamedForTheRelease() {
        val assets = listOf(
            ReleaseAsset("other.apk", "$base/0.84.2/other.apk"),
            ReleaseAsset("jellio-tv-0.84.2.md5", "$base/0.84.2/jellio-tv-0.84.2.md5"),
            ReleaseAsset("jellio-tv-0.84.2.sha256", "$base/0.84.2/jellio-tv-0.84.2.sha256"),
            ReleaseAsset("jellio-tv-0.84.2.apk", "$base/0.84.2/jellio-tv-0.84.2.apk"),
        )
        val selected = selectUpdateAssets("0.84.2", assets)!!
        assertEquals("jellio-tv-0.84.2.apk", selected.apkName)
        assertEquals("$base/0.84.2/jellio-tv-0.84.2.apk", selected.apkUrl)
        assertEquals("$base/0.84.2/jellio-tv-0.84.2.sha256", selected.sha256Url)
    }

    @Test
    fun aVPrefixedTagMatchesEitherName() {
        val assets = listOf(
            ReleaseAsset("jellio-tv-0.84.2.apk", "$base/v0.84.2/jellio-tv-0.84.2.apk"),
            ReleaseAsset("jellio-tv-0.84.2.sha256", "$base/v0.84.2/jellio-tv-0.84.2.sha256"),
        )
        assertEquals("jellio-tv-0.84.2.apk", selectUpdateAssets("v0.84.2", assets)?.apkName)
        val tagged = listOf(
            ReleaseAsset("jellio-tv-v0.84.2.apk", "$base/v0.84.2/jellio-tv-v0.84.2.apk"),
            ReleaseAsset("jellio-tv-v0.84.2.sha256", "$base/v0.84.2/jellio-tv-v0.84.2.sha256"),
        )
        assertEquals("jellio-tv-v0.84.2.apk", selectUpdateAssets("v0.84.2", tagged)?.apkName)
    }

    @Test
    fun missingChecksumOtherVersionOrForeignHostIsNotOffered() {
        val apk = ReleaseAsset("jellio-tv-0.84.2.apk", "$base/0.84.2/jellio-tv-0.84.2.apk")
        val sha = ReleaseAsset("jellio-tv-0.84.2.sha256", "$base/0.84.2/jellio-tv-0.84.2.sha256")
        assertNull(selectUpdateAssets("0.84.2", listOf(apk)))
        assertNull(selectUpdateAssets("0.84.2", listOf(sha)))
        assertNull(selectUpdateAssets("0.85.0", listOf(apk, sha)))
        assertNull(selectUpdateAssets("0.84.2", listOf(apk.copy(url = "https://evil.example/jellio-tv-0.84.2.apk"), sha)))
        assertNull(selectUpdateAssets("0.84.2", listOf(apk, sha.copy(url = "http://github.com/x.sha256"))))
        assertNull(selectUpdateAssets("0.84.2", emptyList()))
    }

    @Test
    fun parsesSha256sumOutput() {
        assertEquals(hash, parseSha256File("$hash  jellio-tv-0.84.2.apk\n", "jellio-tv-0.84.2.apk"))
        assertEquals(hash, parseSha256File("$hash *jellio-tv-0.84.2.apk", "jellio-tv-0.84.2.apk"))
        assertEquals(hash, parseSha256File("${hash.uppercase()}  jellio-tv-0.84.2.apk\r\n", "jellio-tv-0.84.2.apk"))
        assertEquals(hash, parseSha256File("﻿$hash  ./dist/jellio-tv-0.84.2.apk", "jellio-tv-0.84.2.apk"))
        assertEquals(hash, parseSha256File("  $hash\n", "jellio-tv-0.84.2.apk"))
    }

    @Test
    fun picksTheLineForTheFileAmongSeveral() {
        val other = "a".repeat(64)
        val text = "$other  jellio-tv-0.84.1.apk\n$hash  jellio-tv-0.84.2.apk\n"
        assertEquals(hash, parseSha256File(text, "jellio-tv-0.84.2.apk"))
    }

    @Test
    fun rejectsChecksumFilesThatDoNotNameTheApk() {
        assertNull(parseSha256File("$hash  something-else.apk", "jellio-tv-0.84.2.apk"))
        assertNull(parseSha256File("", "jellio-tv-0.84.2.apk"))
        assertNull(parseSha256File("<html>Not Found</html>", "jellio-tv-0.84.2.apk"))
        assertNull(parseSha256File("${hash.dropLast(1)}  jellio-tv-0.84.2.apk", "jellio-tv-0.84.2.apk"))
        assertNull(parseSha256File("${hash}00  jellio-tv-0.84.2.apk", "jellio-tv-0.84.2.apk"))
        assertNull(parseSha256File("$hash\n${"b".repeat(64)}\n", "jellio-tv-0.84.2.apk"))
    }

    @Test
    fun hashesStreamsAndBytes() {
        val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(empty, sha256Hex(ByteArray(0)))
        assertEquals(empty, sha256Hex(ByteArray(0).inputStream()))
        val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(abc, sha256Hex("abc".toByteArray()))
        val big = ByteArray(200_000) { (it % 251).toByte() }
        assertEquals(sha256Hex(big), sha256Hex(big.inputStream()))
    }

    @Test
    fun archiveMustComeFromTheSameAppSignerAndBeNewer() {
        val signer = setOf("s1")
        fun check(pkg: String? = "com.jellio.tv", code: Long = 200, signers: Set<String> = signer, history: Set<String> = signers) =
            checkUpdateArchive("com.jellio.tv", 100, signer, pkg, code, signers, history)
        assertNull(check())
        assertEquals(UpdateRejection.NOT_GENUINE, check(pkg = "com.evil.tv"))
        assertEquals(UpdateRejection.NOT_GENUINE, check(pkg = null))
        assertEquals(UpdateRejection.NOT_GENUINE, check(signers = setOf("s2")))
        assertEquals(UpdateRejection.NOT_GENUINE, check(signers = emptySet()))
        assertEquals(UpdateRejection.NOT_GENUINE, check(signers = setOf("s1", "s2"), history = setOf("s1", "s2").minus("s1")))
        assertEquals(UpdateRejection.NOT_NEWER, check(code = 100))
        assertEquals(UpdateRejection.NOT_NEWER, check(code = 50))
    }

    @Test
    fun aRotatedKeyIsAcceptedWhenItsHistoryVouchesForTheInstalledOne() {
        assertTrue(signersMatch(setOf("old"), setOf("new"), setOf("old", "new")))
        assertFalse(signersMatch(setOf("old"), setOf("new"), setOf("new")))
        assertFalse(signersMatch(emptySet(), setOf("new"), setOf("new")))
    }
}
