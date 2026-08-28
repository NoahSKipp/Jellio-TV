package com.jellio.tv.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioBorder

// Mirrors components/libraryPicker.js's own real popover, opened from
// the top pill's own single Library button rather than one button per
// real library. A full screen dismiss scrim plus BackHandler stand in
// for that file's own outside click/Escape dismissal, the real D-pad
// equivalent.
//
// Real bug found live testing on device: nothing here ever requested
// initial D-pad focus when this overlay opened, so focus stayed
// wherever it already was on the screen underneath, still focusable
// behind this real scrim, every D-pad press moving that underlying
// screen around instead of this overlay's own three entries. Same
// real fix every other real overlay in this app already uses: request
// focus onto the first entry on open, and trap exit so focus can't
// wander back out into the screen behind the scrim.
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusProperties { exit = { FocusRequester.Cancel } },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
                .width(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(JellioBgElevated)
                .border(1.dp, JellioBorder, RoundedCornerShape(16.dp))
                .padding(8.dp),
        ) {
            if (libraries.isEmpty()) {
                Text(text = "No libraries yet.", modifier = Modifier.padding(16.dp))
            }
            libraries.forEachIndexed { index, library ->
                Surface(
                    onClick = { onSelect(library) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().let {
                        if (index == 0) it.focusRequester(firstEntryFocusRequester) else it
                    },
                ) {
                    Text(
                        text = library.Name ?: "Library",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}
