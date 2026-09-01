package com.jellio.tv

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import coil3.bitmapFactoryMaxParallelism
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.jellio.tv.data.network.APP_VERSION
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import com.jellio.tv.data.session.SessionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageLoaderEntryPoint {
    fun sessionManager(): SessionManager
}

@HiltAndroidApp
class JellioTvApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
        val sessionManager = entryPoint.sessionManager()

        val imageDispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        }

        val imageOkHttpClient = OkHttpClient.Builder()
            .connectionPool(com.jellio.tv.data.network.SharedHttp.connectionPool)
            .dispatcher(imageDispatcher)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val token = sessionManager.getCachedAccessToken()
                val deviceId = sessionManager.getCachedDeviceId()
                val serverHost = sessionManager.getCachedServerAddress()
                    ?.let { runCatching { java.net.URI(it).host }.getOrNull() }
                val requestBuilder = original.newBuilder()

                val isServerHost = serverHost == null || original.url.host.equals(serverHost, ignoreCase = true)
                if (!token.isNullOrEmpty() && isServerHost) {
                    requestBuilder.header("X-Emby-Token", token)
                    requestBuilder.header("Authorization", buildEmbyAuthorizationHeader(deviceId, APP_VERSION, token))
                }
                chain.proceed(requestBuilder.build())
            }
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val serverHost = sessionManager.getCachedServerAddress()
                    ?.let { runCatching { java.net.URI(it).host }.getOrNull() }
                if (serverHost != null && !req.url.host.equals(serverHost, ignoreCase = true)) {
                    chain.proceed(
                        req.newBuilder()
                            .removeHeader("X-Emby-Token")
                            .removeHeader("Authorization")
                            .build()
                    )
                } else {
                    chain.proceed(req)
                }
            }
            .apply {
                try {
                    val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    })
                    val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                    hostnameVerifier { _, _ -> true }
                } catch (_: Exception) {}
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
                add(GifDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                val activityManager = getSystemService(android.app.ActivityManager::class.java)
                val memoryInfo = android.app.ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
                val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
                val cachePercent = when {
                    totalRamMb in 1..2048 -> 0.15
                    totalRamMb in 2049..3072 -> 0.20
                    else -> 0.25
                }
                MemoryCache.Builder()
                    .maxSizePercent(context, cachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .precision(coil3.size.Precision.INEXACT)
            .allowHardware(false)
            .bitmapFactoryMaxParallelism(4)
            .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
            .build()
    }
}
