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
                text = com.jellio.tv.data.i18n.Strings.get("tv_loading"),
                style = MaterialTheme.typography.bodyMedium,
                color = JellioTextSecondary,
            )
        }
    }
}
