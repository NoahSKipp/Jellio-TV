package com.jellio.tv.ui.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.BuildConfig
import com.jellio.tv.data.network.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val GITHUB_OWNER = "NoahSKipp"
private const val GITHUB_REPO = "Jellio-TV"

data class UpdateUiState(
    val availableVersion: String? = null,
    val downloadUrl: String? = null,
    val downloading: Boolean = false,
)

// Real feedback live: no in-app way at all to learn a new real
// version had shipped short of checking GitHub by hand. Checked once
// per real app open (AppBootGate's own real callers below), never
// suppressed by an earlier real dismissal the way a persisted flag
// would: a reader who cancels today still hears about the same real
// update again tomorrow, same real nagging a mobile app's own update
// prompt already does.
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val gitHubApi: GitHubApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var downloadId: Long? = null
    private var receiverRegistered = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(receivedContext: Context, intent: Intent) {
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == -1L || completedId != downloadId) return
            installDownloadedApk(completedId)
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            val release = runCatching { gitHubApi.getLatestRelease(GITHUB_OWNER, GITHUB_REPO) }.getOrNull() ?: return@launch
            val latestVersion = release.tag_name.removePrefix("v")
            if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) return@launch
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@launch
            _uiState.value = UpdateUiState(availableVersion = latestVersion, downloadUrl = apkAsset.browser_download_url)
        }
    }

    fun dismiss() {
        _uiState.value = UpdateUiState()
    }

    fun download() {
        val state = _uiState.value
        val url = state.downloadUrl ?: return
        val version = state.availableVersion ?: return
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        val fileName = "jellio-tv-$version.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Jellio TV $version")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)
        _uiState.value = state.copy(downloading = true)
    }

    private fun installDownloadedApk(completedId: Long) {
        val version = _uiState.value.availableVersion ?: return
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "jellio-tv-$version.apk")
        if (!apkFile.exists()) {
            _uiState.value = _uiState.value.copy(downloading = false)
            return
        }
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        _uiState.value = UpdateUiState()
    }

    override fun onCleared() {
        super.onCleared()
        if (receiverRegistered) {
            context.unregisterReceiver(downloadReceiver)
            receiverRegistered = false
        }
    }
}

// Plain numeric segment compare (1.2.3 vs 1.10.0 lexically would get
// 1.10.0 backwards): BuildConfig.VERSION_NAME and a release tag_name
// are both always this app's own plain three-segment scheme, git-cliff
// and build.gradle.kts's own versionName never producing anything
// else for this to actually need to handle.
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
