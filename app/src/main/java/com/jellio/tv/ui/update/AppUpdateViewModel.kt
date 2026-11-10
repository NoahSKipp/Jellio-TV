package com.jellio.tv.ui.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.BuildConfig
import com.jellio.tv.data.network.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val GITHUB_OWNER = "NoahSKipp"
private const val GITHUB_REPO = "Jellio-TV"
private const val UPDATE_CHECK_INTERVAL_MS = 3L * 60 * 60 * 1000
private const val UPDATE_RECHECK_ON_RETURN_MS = 60L * 60 * 1000

data class UpdateUiState(
    val availableVersion: String? = null,
    val downloadUrl: String? = null,
    val checksumUrl: String? = null,
    val apkName: String? = null,
    val downloading: Boolean = false,
    // A string key, set when the last download was refused.
    val error: String? = null,
)

sealed interface ManualCheckResult {
    data object Checking : ManualCheckResult
    data object UpToDate : ManualCheckResult
    data class UpdateFound(val version: String) : ManualCheckResult
    // The release exists but its APK is still being built and attached.
    data class Preparing(val version: String) : ManualCheckResult
    data object Error : ManualCheckResult
}

private sealed interface LatestRelease {
    data class Ready(val version: String, val assets: UpdateAssets) : LatestRelease
    data class Preparing(val version: String) : LatestRelease
    data object Failed : LatestRelease
}

// A release is published a few minutes before its APK is attached.
private const val PREPARING_RECHECK_MS = 10L * 60 * 1000
private const val DOWNLOAD_POLL_MS = 1_000L
private const val MAX_CHECKSUM_BYTES = 4_096L

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val gitHubApi: GitHubApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _manualCheckResult = MutableStateFlow<ManualCheckResult?>(null)
    val manualCheckResult: StateFlow<ManualCheckResult?> = _manualCheckResult.asStateFlow()

    // Polled instead of listening for the download broadcast, which would
    // need a receiver other apps can reach.
    private var downloadJob: Job? = null

    // Every hop of the checksum fetch, redirects included, stays on GitHub.
    private val checksumClient by lazy {
        com.jellio.tv.data.network.SharedHttp.plainClient.newBuilder()
            .addNetworkInterceptor { chain ->
                val url = chain.request().url.toString()
                if (!isTrustedGitHubUrl(url)) throw IOException("Update checksum left GitHub: ${chain.request().url.host}")
                chain.proceed(chain.request())
            }
            .build()
    }

    private var lastCheckAt = 0L
    // A version turned down stays quiet for the background checks; the
    // next app open still asks again.
    private var dismissedVersion: String? = null

    private suspend fun runCheck() {
        lastCheckAt = System.currentTimeMillis()
        if (_uiState.value.availableVersion != null) return
        val (latestVersion, assets) = when (val latest = fetchLatestRelease()) {
            is LatestRelease.Ready -> latest.version to latest.assets
            is LatestRelease.Preparing -> {
                lastCheckAt = System.currentTimeMillis() - UPDATE_CHECK_INTERVAL_MS + PREPARING_RECHECK_MS
                return
            }
            LatestRelease.Failed -> return
        }
        if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME) || latestVersion == dismissedVersion) return
        if (_uiState.value.availableVersion != null) return
        _uiState.value = offered(latestVersion, assets)
    }

    suspend fun checkPeriodically() {
        while (true) {
            if (System.currentTimeMillis() - lastCheckAt >= UPDATE_RECHECK_ON_RETURN_MS) runCheck()
            val untilNext = UPDATE_CHECK_INTERVAL_MS - (System.currentTimeMillis() - lastCheckAt)
            delay(untilNext.coerceAtLeast(60_000L))
            runCheck()
        }
    }

    fun checkForUpdateManually() {
        viewModelScope.launch {
            _manualCheckResult.value = ManualCheckResult.Checking
            val (latestVersion, assets) = when (val latest = fetchLatestRelease()) {
                is LatestRelease.Ready -> latest.version to latest.assets
                is LatestRelease.Preparing -> {
                    _manualCheckResult.value = if (isNewerVersion(latest.version, BuildConfig.VERSION_NAME)) {
                        ManualCheckResult.Preparing(latest.version)
                    } else {
                        ManualCheckResult.UpToDate
                    }
                    return@launch
                }
                LatestRelease.Failed -> {
                    _manualCheckResult.value = ManualCheckResult.Error
                    return@launch
                }
            }
            if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                _manualCheckResult.value = ManualCheckResult.UpToDate
                return@launch
            }
            if (_uiState.value.availableVersion != latestVersion || !_uiState.value.downloading) {
                _uiState.value = offered(latestVersion, assets)
            }
            _manualCheckResult.value = ManualCheckResult.UpdateFound(latestVersion)
        }
    }

    private suspend fun fetchLatestRelease(): LatestRelease {
        val release = try {
            gitHubApi.getLatestRelease(GITHUB_OWNER, GITHUB_REPO)
        } catch (err: kotlinx.coroutines.CancellationException) {
            throw err
        } catch (err: Exception) {
            val detail = (err as? retrofit2.HttpException)?.let { "HTTP ${it.code()}" } ?: err.javaClass.simpleName + ": " + err.message
            android.util.Log.w("JellioUpdate", "update check failed: $detail")
            return LatestRelease.Failed
        }
        val latestVersion = release.tag_name.removePrefix("v")
        val assets = selectUpdateAssets(release.tag_name, release.assets.map { ReleaseAsset(it.name, it.browser_download_url) })
            ?: return LatestRelease.Preparing(latestVersion)
        return LatestRelease.Ready(latestVersion, assets)
    }

    private fun offered(version: String, assets: UpdateAssets) = UpdateUiState(
        availableVersion = version,
        downloadUrl = assets.apkUrl,
        checksumUrl = assets.sha256Url,
        apkName = assets.apkName,
    )

    fun dismiss() {
        dismissedVersion = _uiState.value.availableVersion ?: dismissedVersion
        _uiState.value = UpdateUiState()
        _manualCheckResult.value = null
    }

    fun download() {
        val state = _uiState.value
        if (state.downloading) return
        val url = state.downloadUrl ?: return
        val checksumUrl = state.checksumUrl ?: return
        val apkName = state.apkName ?: return
        val version = state.availableVersion ?: return
        if (!isTrustedGitHubUrl(url) || !isTrustedGitHubUrl(checksumUrl)) {
            _uiState.value = state.copy(error = "update_error_download")
            return
        }
        val fileName = "jellio-tv-$version.apk"
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (apkFile.exists()) {
            apkFile.delete()
        }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Jellio TV $version")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = downloadManager.enqueue(request)
        _uiState.value = state.copy(downloading = true, error = null)
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val succeeded = awaitDownload(downloadManager, id)
            val failure = if (succeeded) {
                withContext(Dispatchers.IO) { verifyAndInstall(apkFile, apkName, checksumUrl, version) }
            } else {
                apkFile.delete()
                "update_error_download"
            }
            if (failure != null) {
                _uiState.value = _uiState.value.copy(downloading = false, error = failure)
            } else {
                _uiState.value = UpdateUiState()
                _manualCheckResult.value = null
            }
        }
    }

    private suspend fun awaitDownload(downloadManager: DownloadManager, id: Long): Boolean {
        while (true) {
            val status = downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val column = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (column < 0) null else cursor.getInt(column)
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> return true
                DownloadManager.STATUS_FAILED, null -> return false
            }
            delay(DOWNLOAD_POLL_MS)
        }
    }

    // Returns the string key of the reason the update was refused, or null
    // once the installer has been opened.
    private suspend fun verifyAndInstall(downloaded: File, apkName: String, checksumUrl: String, version: String): String? {
        // Checked and installed from app-private storage, out of reach of
        // other apps between the check and the install.
        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { it.delete() }
        val apkFile = File(updatesDir, "jellio-tv-$version.apk")
        try {
            if (!downloaded.exists()) return "update_error_download"
            downloaded.copyTo(apkFile, overwrite = true)
        } catch (err: IOException) {
            apkFile.delete()
            return "update_error_download"
        } finally {
            downloaded.delete()
        }
        val expected = fetchChecksum(checksumUrl, apkName)
        if (expected == null) {
            apkFile.delete()
            return "update_error_download"
        }
        val actual = runCatching { apkFile.inputStream().use { sha256Hex(it) } }.getOrNull()
        if (actual != expected) {
            android.util.Log.w("JellioUpdate", "checksum mismatch for $apkName")
            apkFile.delete()
            return "update_error_checksum"
        }
        when (checkArchive(apkFile)) {
            UpdateRejection.NOT_GENUINE -> {
                android.util.Log.w("JellioUpdate", "update package or signer does not match the installed app")
                apkFile.delete()
                return "update_error_not_genuine"
            }
            UpdateRejection.NOT_NEWER -> {
                apkFile.delete()
                return "update_error_not_newer"
            }
            null -> Unit
        }
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main) { context.startActivity(installIntent) }
        return null
    }

    private fun fetchChecksum(url: String, apkName: String): String? = try {
        val request = Request.Builder()
            .url(url.toHttpUrlOrNull() ?: return null)
            .header("User-Agent", "Jellio-TV/${com.jellio.tv.data.network.APP_VERSION}")
            .build()
        checksumClient.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) return null
            if (body.contentLength() > MAX_CHECKSUM_BYTES) return null
            val text = body.source().let { source ->
                source.request(MAX_CHECKSUM_BYTES + 1)
                if (source.buffer.size > MAX_CHECKSUM_BYTES) return null
                source.buffer.readUtf8()
            }
            parseSha256File(text, apkName)
        }
    } catch (err: IOException) {
        android.util.Log.w("JellioUpdate", "checksum fetch failed: ${err.message}")
        null
    }

    private fun checkArchive(apkFile: File): UpdateRejection? {
        val pm = context.packageManager
        val installed = installedPackageInfo(pm) ?: return UpdateRejection.NOT_GENUINE
        val archive = archivePackageInfo(pm, apkFile.absolutePath) ?: return UpdateRejection.NOT_GENUINE
        val (installedSigners, _) = signerDigests(installed)
        val (archiveSigners, archiveHistory) = signerDigests(archive)
        return checkUpdateArchive(
            installedPackage = context.packageName,
            installedVersionCode = PackageInfoCompat.getLongVersionCode(installed),
            installedSigners = installedSigners,
            archivePackage = archive.packageName,
            archiveVersionCode = PackageInfoCompat.getLongVersionCode(archive),
            archiveSigners = archiveSigners,
            archiveSignerHistory = archiveHistory,
        )
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(pm: PackageManager): PackageInfo? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        pm.getPackageInfo(context.packageName, flags)
    }.getOrNull()

    // Some Android 9 builds leave signingInfo out for an archive; the older
    // flag still reads its signatures there.
    @Suppress("DEPRECATION")
    private fun archivePackageInfo(pm: PackageManager, path: String): PackageInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = runCatching { pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
            if (info?.signingInfo != null) return info
        }
        return runCatching { pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES) }.getOrNull()
    }

    // Current signers and the signers the package's rotation history vouches for.
    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Pair<Set<String>, Set<String>> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { signing ->
                val current = signing.apkContentsSigners.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
                val history = if (signing.hasMultipleSigners()) {
                    current
                } else {
                    signing.signingCertificateHistory.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
                }
                return current to history
            }
        }
        val legacy = info.signatures.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
        return legacy to legacy
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
    }
}

private fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
    val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
    val length = maxOf(remoteParts.size, localParts.size)
    for (i in 0 until length) {
        val r = remoteParts.getOrElse(i) { 0 }
        val l = localParts.getOrElse(i) { 0 }
        if (r != l) return r > l
    }
    return false
}
