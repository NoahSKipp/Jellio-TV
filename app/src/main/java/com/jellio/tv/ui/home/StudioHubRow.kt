package com.jellio.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.scaled

private val TileWidth = 200.dp
private val TileHeight = 110.dp

// Mirrors components/services.js's own buildHubStrip()/buildHubTile():
// one real tile per real service groupByService(collections) actually
// found, opening ServiceScreen. The web build's own logo-with-name-
// fallback-on-error trick has no direct Compose equivalent worth the
// extra state it would take here, so this always shows both, real
// but simpler.
@Composable
fun StudioHubRow(
    services: List<String>,
    logoUrl: (String) -> String,
    onServiceClick: (String) -> Unit,
) {
    if (services.isEmpty()) return
    Column {
        RowTitle(text = "Studio Hubs")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(services, key = { it }) { service ->
                StudioHubTile(name = service, logoUrl = logoUrl(service), onClick = { onServiceClick(service) })
            }
        }
    }
}

@Composable
private fun StudioHubTile(name: String, logoUrl: String, onClick: () -> Unit) {
    val tileWidth = TileWidth.scaled()
    val tileHeight = TileHeight.scaled()
    // Real port of css/streaming-hub.css's own real
    // .jellio-hub-tile:hover { transform: translateY(-2px); background:
    // var(--jellio-card-bg); }: the lift itself is this Surface's own
    // real default focus scale (already on, never overridden the way
    // TopNavPill's own header explains that pill turning off), the
    // background swap on focus was the one real piece missing.
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
        ),
        modifier = Modifier.width(tileWidth).height(tileHeight),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = tileWidth - 24.dp, height = tileHeight - 48.dp),
            )
            Text(
                text = name,
                color = JellioText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
