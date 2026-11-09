package com.jellio.tv.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.RegisterGridFocusItem
import com.jellio.tv.ui.common.gridColumnFocus
import com.jellio.tv.ui.common.rememberGridFocusController
import com.jellio.tv.ui.common.rememberTvGridState
import com.jellio.tv.ui.home.PosterCard
import com.jellio.tv.ui.home.PosterWidth
import com.jellio.tv.ui.home.SkeletonPoster
import com.jellio.tv.ui.home.rememberCardOptionsHost
import com.jellio.tv.ui.i18n.Strings
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    session: Session,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberTvGridState()
    val focusController = rememberGridFocusController()
    val context = LocalContext.current

    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, Strings.get("search_voice_prompt"))
        }
    }

    val isAmazonDevice = remember {
        android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            android.os.Build.BRAND.equals("Amazon", ignoreCase = true)
    }

    val isVoiceSupported = remember(context, isAmazonDevice) {
        if (isAmazonDevice) {
            false
        } else {
            runCatching {
                android.speech.SpeechRecognizer.isRecognitionAvailable(context) &&
                    context.packageManager.queryIntentActivities(voiceIntent, 0).isNotEmpty()
            }.getOrDefault(false)
        }
    }

    var isEditingQuery by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onQueryChange(session, spoken)
            }
        }
    }

    val openItemOptions = rememberCardOptionsHost(
        onToggleWatchlist = { viewModel.toggleWatchlist(session, it) },
        onToggleWatched = { viewModel.toggleWatched(session, it) },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 32.dp, start = 48.dp, end = 48.dp),
    ) {
        Text(text = com.jellio.tv.ui.i18n.string("nav_search"), style = MaterialTheme.typography.titleLarge)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        ) {
            // 1. Microphone Voice Search button positioned first (accessible before text field)
            Surface(
                onClick = {
                    if (isVoiceSupported) {
                        try {
                            speechLauncher.launch(voiceIntent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                Strings.get("search_voice_not_supported"),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        // On Firestick / devices without Google Speech Services:
                        // Open the search field keyboard and guide the user to hold the remote mic button
                        isEditingQuery = true
                        Toast.makeText(
                            context,
                            Strings.get("search_voice_firestick_hint"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = JellioBgElevated,
                    focusedContainerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = JellioText,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ),
                ),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = com.jellio.tv.ui.i18n.string("search_voice_btn"),
                        tint = if (isVoiceSupported) JellioText else JellioTextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // 2. TV-friendly Search Field that activates keyboard only on click/select
            TvSearchField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChange(session, it) },
                label = com.jellio.tv.ui.i18n.string("search_placeholder"),
                isEditing = isEditingQuery,
                onEditingChange = { isEditingQuery = it },
                modifier = Modifier.width(480.dp),
            )

            // 3. One-click Clear button when query is populated
            if (uiState.query.isNotEmpty()) {
                Surface(
                    onClick = { viewModel.onQueryChange(session, "") },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = JellioBgElevated,
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = JellioTextSecondary,
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(12.dp),
                        ),
                    ),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = com.jellio.tv.ui.i18n.string("search_clear"),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        when {
            uiState.results.isNotEmpty() || uiState.isSearching -> {
                val placeholders = when {
                    !uiState.isSearching -> 0
                    uiState.results.isEmpty() -> 12
                    else -> 4
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val scaledPosterWidth = PosterWidth.scaled()
                    val spacing = 16.dp
                    val columnCount = maxOf(1, ((maxWidth + spacing) / (scaledPosterWidth + spacing)).toInt())
                    val results = remember(uiState.results) { uiState.results.distinctBy { it.Id } }
                    val totalCount = results.size
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columnCount),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 48.dp),
                        modifier = Modifier.fillMaxSize().focusRestorer(focusController.requesterFor(0)),
                    ) {
                        items(totalCount, key = { results[it].Id }, contentType = { "poster_card" }) { index ->
                            val item = results[index]
                            RegisterGridFocusItem(focusController, index)
                            PosterCard(
                                item = item,
                                imageUrl = imageUrl,
                                onClick = { onItemClick(item) },
                                onOptionsClick = { openItemOptions(item) },
                                canExpand = false,
                                modifier = Modifier.gridColumnFocus(
                                    controller = focusController,
                                    index = index,
                                    totalCount = totalCount,
                                    columnCount = columnCount,
                                    allowExitUp = true,
                                    allowExitLeft = true,
                                ),
                            )
                        }
                        items(placeholders, key = { "placeholder-$it" }, contentType = { "placeholder" }) {
                            SkeletonPoster()
                        }
                    }
                }
            }
            uiState.error != null -> com.jellio.tv.ui.common.ErrorState(
                message = com.jellio.tv.ui.i18n.string(
                    if (uiState.error == com.jellio.tv.ui.common.LoadFailure.Unreachable) "search_network_error" else "search_load_error",
                ),
                onRetry = { viewModel.retry(session) },
                // The search field keeps focus while typing.
                takeFocus = false,
                modifier = Modifier.fillMaxSize(),
            )
            uiState.hasSearched && uiState.suggestions != null -> {
                val answer = uiState.suggestions!!
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "intro") {
                        Text(
                            text = com.jellio.tv.ui.i18n.formatString("search_suggest_intro", answer.MatchedTitle ?: uiState.query),
                            color = JellioTextSecondary,
                            modifier = Modifier.padding(start = 52.dp, top = 8.dp, bottom = 16.dp),
                        )
                    }
                    items(answer.Rows.size, key = { "suggest:$it" }) { index ->
                        val row = answer.Rows[index]
                        val title = when (row.Kind) {
                            "similar" -> com.jellio.tv.ui.i18n.formatString("search_suggest_similar", answer.MatchedTitle ?: uiState.query)
                            "person" -> com.jellio.tv.ui.i18n.formatString("search_suggest_person", row.Name ?: "")
                            else -> com.jellio.tv.ui.i18n.formatString("search_suggest_genre", com.jellio.tv.ui.i18n.genreDisplayName(row.Name ?: ""))
                        }
                        com.jellio.tv.ui.home.PosterRow(
                            section = com.jellio.tv.ui.home.HomeSection(title = title, items = row.Items),
                            imageUrl = imageUrl,
                            onItemClick = onItemClick,
                        )
                    }
                }
            }
            uiState.hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = com.jellio.tv.ui.i18n.formatString("search_no_results_for", uiState.query), color = JellioTextSecondary)
            }
        }
    }
}

@Composable
private fun TvSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldSurfaceFocusRequester = remember { FocusRequester() }
    val inputFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = isEditing) {
        onEditingChange(false)
        runCatching { fieldSurfaceFocusRequester.requestFocus() }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            runCatching { inputFocusRequester.requestFocus() }
        }
    }

    if (isEditing) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = JellioText, fontSize = 16.sp),
            cursorBrush = SolidColor(JellioText),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onEditingChange(false)
                    runCatching { fieldSurfaceFocusRequester.requestFocus() }
                },
                onDone = {
                    onEditingChange(false)
                    runCatching { fieldSurfaceFocusRequester.requestFocus() }
                },
            ),
            modifier = modifier
                .focusRequester(inputFocusRequester)
                .clip(RoundedCornerShape(12.dp))
                .background(JellioBgElevated)
                .border(2.dp, JellioSecondary, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = JellioSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(text = label, color = JellioTextSecondary, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            },
        )
    } else {
        Surface(
            onClick = { onEditingChange(true) },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1.02f,
                pressedScale = 0.98f,
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBgElevated,
                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                contentColor = JellioText,
                focusedContentColor = JellioText,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    border = BorderStroke(1.dp, Color(0xFF2B314B)),
                    shape = RoundedCornerShape(12.dp),
                ),
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color.White),
                    shape = RoundedCornerShape(12.dp),
                ),
            ),
            modifier = modifier.focusRequester(fieldSurfaceFocusRequester),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = JellioTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        color = JellioTextSecondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = value,
                        color = JellioText,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
