package com.jellio.tv

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import dagger.hilt.android.HiltAndroidApp

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
@HiltAndroidApp
class JellioTvApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
