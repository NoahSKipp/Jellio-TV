package com.jellio.tv

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
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

// Hilt entry point. No component actually needs it yet at this
// scaffold stage; wired now so the real auth/session runtime and API
// client land as real @Inject-ed singletons later rather than a
// retrofit onto an app that never expected DI.
//
// Real feedback live: an animated GIF avatar (native Jellyfin already
// supports uploading one, AvatarPickerOverlay's own header already
// documents that) rendered as its own first frame only, frozen, on
// this app's own SidebarNav/ProfileScreen AsyncImage calls. Coil's own
// default decoder set never included GIF support at all, on any
// platform, without this component registered explicitly.
//
// Real bug found live, on a real screenshot: StudioHubRow's own real
// service logos (Services.kt's own logoUrl(), the same real .svg
// FrontendController.cs already serves the web build's own logos from)
// rendered as nothing at all, just this tile's own real name label
// underneath - Coil's own default decoder set has no SVG support
// either, on any platform, same real class of gap as the GIF one
// above, silently failing rather than throwing since the real network
// fetch itself still succeeds.
@HiltAndroidApp
class JellioTvApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
        val sessionManager = entryPoint.sessionManager()

        val imageOkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val token = runBlocking { sessionManager.accessToken() }
                val request = if (!token.isNullOrEmpty()) {
                    original.newBuilder()
                        .header("X-Emby-Token", token)
                        .header("Authorization", "MediaBrowser Token=\"$token\"")
                        .build()
                } else {
                    original
                }
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
