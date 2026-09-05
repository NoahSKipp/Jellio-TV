package com.jellio.tv.ui.update

import android.graphics.drawable.Animatable
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.R
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled

// AppBootGate's own real loading wait (usually ~8s for Home's own
// prefetch to finish) used to sit behind jellio_load.webm, a plain
// looping video clip. Real android animated-vector port of the same
// jellyfish mark the web plugin's own boot splash now plays instead
// (ic_jellio_mark_animated.xml, real port notes on that file's own
// header): a plain ImageView.start() rather than Compose's own
// animation-graphics AnimatedImageVector API, since that API drives a
// discrete atEnd boolean transition and has no real equivalent for an
// XML animator's own repeatCount="infinite" the way this drawable's
// own swim wobble actually needs, an Animatable.start() call already
// respects natively.
//
// "Jellio" and "Loading…" sit under the mark now too, same real
// wording and reasoning the web plugin's own splash carries.
@Composable
fun BootSplashMark(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(240.dp.scaled())) {
                // Real jellio-mark-animated.svg's own real paint order
                // clips the wake dots between its own triangle+glow and
                // its own jellyfish silhouette, but this drawable draws
                // all three as one real vector with no seam to slot a
                // Compose layer into the middle of. Painted after it
                // instead (on top of the jellyfish too, not strictly
                // behind it the way the SVG has it): the dots stay
                // small and cluster low in this mark's own triangle,
                // real feedback's own actual ask (this looking alive,
                // not motionless) matters more here than a z-order
                // mismatch invisible at this drawable's own on-screen
                // size. JellioMarkOcean.kt's own header covers why this
                // half stayed web-only until now and how it's ported
                // here instead.
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            setImageResource(R.drawable.ic_jellio_mark_animated)
                            (drawable as? Animatable)?.start()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                JellioMarkOcean()
            }
            Spacer(Modifier.height(20.dp.scaled()))
            Text(
                text = "Jellio",
                style = MaterialTheme.typography.headlineSmall,
                color = JellioText,
            )
            Spacer(Modifier.height(10.dp.scaled()))
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = JellioTextSecondary,
            )
        }
    }
}
