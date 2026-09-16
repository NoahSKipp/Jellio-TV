package com.jellio.tv.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioBorder
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.nav.LibraryIconVector

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryPickerOverlay(
    libraries: List<BaseItemDto>,
    onSelect: (BaseItemDto) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    val firstEntryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(libraries) { firstEntryFocusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(JellioBgElevated)
                .border(1.dp, JellioBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text(
                text = "Select Library",
                style = MaterialTheme.typography.titleMedium,
                color = JellioText,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp, top = 4.dp),
            )
            
            if (libraries.isEmpty()) {
                Text(text = "No libraries yet.", modifier = Modifier.padding(12.dp), color = JellioTextSecondary)
            }
            
            libraries.forEachIndexed { index, library ->
                Surface(
                    onClick = { onSelect(library) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent, 
                        contentColor = JellioText, 
                        focusedContainerColor = Color.White.copy(alpha = 0.18f), 
                        focusedContentColor = JellioText
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).let {
                        if (index == 0) it.focusRequester(firstEntryFocusRequester) else it
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getLibraryIcon(library.CollectionType),
                            contentDescription = null,
                            tint = JellioText,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = library.Name ?: "Library",
                            style = MaterialTheme.typography.titleSmall,
                            color = JellioText,
                        )
                    }
                }
            }
        }
    }
}
