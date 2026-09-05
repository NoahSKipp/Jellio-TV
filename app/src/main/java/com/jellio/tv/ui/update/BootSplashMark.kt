package com.jellio.tv.ui.update

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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled

// AppBootGate's own real loading wait (usually ~8s for Home's own
// prefetch to finish) used to sit behind jellio_load.webm, a plain
// looping video clip. JellioMarkOcean draws a real Compose port of
// jellio-mark-animated.svg instead: gradient triangle, clipped teal
// glow, caustic ellipses, wake-trail dots and the jellyfish silhouette
// itself morphing through the SVG's own 11 path keyframes, all driven
// off one shared clock, same as the web boot splash.
//
// "Jellio" and "Loading…" sit under the mark now too, same real
// wording and reasoning the web plugin's own splash carries.
@Composable
fun BootSplashMark(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(240.dp.scaled())) {
                JellioMarkOcean(modifier = Modifier.fillMaxSize())
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
