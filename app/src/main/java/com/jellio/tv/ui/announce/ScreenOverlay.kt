package com.jellio.tv.ui.announce

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.JellioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

private const val OVERLAY_POLL_MS = 5_000L
private const val OVERLAY_FADE_MS = 400

data class ScreenOverlay(val text: String?, val imageUrl: String?, val opacity: Float, val seconds: Int, val seq: Long)

// Screen overlays sent from the Jellio dashboard
// (Controllers/OverlaysController.cs): a picture or a line of text in the
// middle of the screen for a few seconds, over every screen, never
// taking focus.
@HiltViewModel
class ScreenOverlayViewModel @Inject constructor(private val repository: JellioRepository) : ViewModel() {
    private val _current = MutableStateFlow<ScreenOverlay?>(null)
    val current: StateFlow<ScreenOverlay?> = _current.asStateFlow()
    private var after = -1L
    private val queue = ArrayDeque<ScreenOverlay>()

    // Settings' test button: what the server answers right now, and a
    // sample overlay drawn locally, to tell a delivery problem from a
    // drawing one.
    private val _diagnostic = MutableStateFlow<String?>(null)
    val diagnostic: StateFlow<String?> = _diagnostic.asStateFlow()

    fun runTest(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            _diagnostic.value = "Checking..."
            _diagnostic.value = runCatching { repository.getPendingOverlays(0) }.fold(
                onSuccess = { "Server answered: latest #${it.Latest}, ${it.Overlays.size} pending for you. This TV last saw #$after." },
                onFailure = { "Server check failed: ${it.javaClass.simpleName}: ${it.message}" },
            )
            queue.addLast(ScreenOverlay("Test overlay", null, 0.9f, 4, -System.currentTimeMillis()))
        }
    }

    suspend fun runWhileStarted() {
        while (coroutineContext.isActive) {
            val pending = runCatching { repository.getPendingOverlays(after) }.getOrNull()
            if (pending != null) {
                val first = after < 0
                after = pending.Latest
                if (!first) {
                    pending.Overlays.forEach { overlay ->
                        queue.addLast(
                            ScreenOverlay(
                                text = overlay.Text?.takeIf { it.isNotBlank() },
                                imageUrl = overlay.ImageId?.let { repository.overlayImageUrl(it) },
                                opacity = (overlay.Opacity / 100f).coerceIn(0.05f, 1f),
                                seconds = overlay.Seconds.coerceIn(1, 60),
                                seq = overlay.Seq,
                            ),
                        )
                    }
                }
            }
            while (_current.value == null && queue.isNotEmpty()) {
                val next = queue.removeFirst()
                _current.value = next
                delay(next.seconds * 1000L + OVERLAY_FADE_MS)
                _current.value = null
            }
            // Polls every 5 s, but picks up a queued test overlay within a second.
            repeat((OVERLAY_POLL_MS / 1000).toInt()) {
                if (queue.isNotEmpty() && _current.value == null) return@repeat
                delay(1000)
            }
        }
    }
}

@Composable
fun ScreenOverlayHost(viewModel: ScreenOverlayViewModel = hiltViewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runWhileStarted()
        }
    }
    val overlay by viewModel.current.collectAsState()
    val shown = overlay ?: return
    var visible by remember(shown.seq) { mutableStateOf(false) }
    LaunchedEffect(shown.seq) {
        visible = true
        delay(shown.seconds * 1000L)
        visible = false
    }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (visible) shown.opacity else 0f,
        animationSpec = tween(OVERLAY_FADE_MS),
        label = "overlayAlpha",
    )
    // Opacity goes on the picture and the text themselves, not a layer
    // around them: an offscreen alpha layer can drop out over the video
    // surface during playback.
    Box(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = overlayAlpha
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth(0.75f).fillMaxHeight(0.8f),
        ) {
            if (shown.imageUrl != null) {
                AsyncImage(
                    model = shown.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(12.dp)),
                )
            }
            if (shown.text != null) {
                Text(
                    text = shown.text,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 54.sp,
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = 24f),
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
