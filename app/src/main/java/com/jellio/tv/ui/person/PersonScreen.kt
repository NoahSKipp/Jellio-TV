package com.jellio.tv.ui.person

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.DepartmentTab
import com.jellio.tv.data.model.PersonCreditItem
import com.jellio.tv.data.model.RichPersonDetails
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.ErrorState
import com.jellio.tv.ui.common.NoOpBringIntoViewSpec
import com.jellio.tv.ui.common.rememberTvHorizontalBringIntoViewSpec
import com.jellio.tv.ui.home.RatingChip
import com.jellio.tv.ui.home.TmdbBadge
import com.jellio.tv.ui.i18n.Strings
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled

private fun renderMarkdownBiography(raw: String): AnnotatedString {
    val cleanLinks = raw.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
    val builder = AnnotatedString.Builder()
    val tokenRegex = Regex("(\\*\\*|__)(.*?)\\1|(\\*|_)(.*?)\\3")
    var lastIndex = 0

    for (match in tokenRegex.findAll(cleanLinks)) {
        val range = match.range
        if (range.first > lastIndex) {
            builder.append(cleanLinks.substring(lastIndex, range.first))
        }
        val boldContent = match.groups[2]?.value
        val italicContent = match.groups[4]?.value
        if (boldContent != null) {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(boldContent)
            builder.pop()
        } else if (italicContent != null) {
            builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            builder.append(italicContent)
            builder.pop()
        }
        lastIndex = range.last + 1
    }

    if (lastIndex < cleanLinks.length) {
        builder.append(cleanLinks.substring(lastIndex))
    }

    return builder.toAnnotatedString()
}

@Composable
fun PersonScreen(
    session: Session,
    personId: String,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val heroFocusRequester = remember { FocusRequester() }
    var heroHasFocus by remember { mutableStateOf(false) }

    val blockScrollWhileHeroFocused = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (heroHasFocus) available else Offset.Zero
        }
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(personId) {
        viewModel.load(session, personId)
    }

    LaunchedEffect(uiState.details) {
        if (uiState.details != null) {
            listState.scrollToItem(0)
            runCatching { heroFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(heroHasFocus) {
        if (heroHasFocus) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (index != 0 || offset != 0) listState.scrollToItem(0)
                }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = Strings.get("status_loading"), color = JellioTextSecondary)
                }
            }
            uiState.error != null -> {
                ErrorState(
                    message = Strings.get("person_load_error"),
                    failure = uiState.error,
                    onRetry = { viewModel.retry(session, personId) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            uiState.details != null -> {
                val details = uiState.details!!
                val horizontalPadding = 48.dp
                val spacing = 16.dp
                val availableWidth = maxWidth - (horizontalPadding * 2)
                val columns = maxOf(1, ((availableWidth + spacing) / (160.dp + spacing)).toInt())
                val chunkedCredits = remember(uiState.filmography, columns) { uiState.filmography.chunked(columns) }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(blockScrollWhileHeroFocused)
                        .focusRestorer(),
                    contentPadding = PaddingValues(bottom = 64.dp),
                ) {
                    // 1. Hero Header Section
                    item {
                        Box(modifier = Modifier.onFocusChanged { heroHasFocus = it.hasFocus }) {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides NoOpBringIntoViewSpec) {
                                PersonHeroHeader(
                                    details = details,
                                    fallbackImageUrl = imageUrl(details.id, null, "Primary", 400),
                                    onBack = onBack,
                                    backFocusRequester = heroFocusRequester,
                                )
                            }
                        }
                    }

                    // 2. Known For Section (Top 10 works)
                    if (details.knownFor.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 28.dp)) {
                                Text(
                                    text = Strings.get("person_known_for"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = JellioText,
                                    modifier = Modifier.padding(start = 48.dp, bottom = 14.dp),
                                )
                                CompositionLocalProvider(
                                    androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides rememberTvHorizontalBringIntoViewSpec(),
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 48.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        items(details.knownFor, key = { "known_" + it.id + "_" + it.department }) { credit ->
                                            PersonCreditCard(
                                                credit = credit,
                                                imageUrl = imageUrl,
                                                onClick = { onItemClick(credit.id) },
                                                modifier = Modifier.width(160.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Filmography Heading & Department Filter Tabs
                    if (details.allCredits.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 32.dp, bottom = 12.dp)) {
                                Text(
                                    text = Strings.get("tv_filmography"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = JellioText,
                                    modifier = Modifier.padding(start = 48.dp, bottom = 14.dp),
                                )
                                CompositionLocalProvider(
                                    androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides rememberTvHorizontalBringIntoViewSpec(),
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 48.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(details.departmentTabs, key = { it.key }) { tab ->
                                            DepartmentTabPill(
                                                tab = tab,
                                                selected = uiState.selectedDepartment.equals(tab.key, ignoreCase = true),
                                                onSelect = { viewModel.selectDepartment(tab.key) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Filtered Filmography Cards in Rows
                        items(chunkedCredits.size, key = { "row_" + uiState.selectedDepartment + "_$it" }) { rowIndex ->
                            val rowCredits = chunkedCredits[rowIndex]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                for (credit in rowCredits) {
                                    PersonCreditCard(
                                        credit = credit,
                                        imageUrl = imageUrl,
                                        onClick = { onItemClick(credit.id) },
                                        modifier = Modifier.width(160.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonHeroHeader(
    details: RichPersonDetails,
    fallbackImageUrl: String,
    onBack: () -> Unit,
    backFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isBioExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth().heightIn(min = 380.dp.scaled())) {
        // The backdrop is someone else's title art, so it only sets the mood:
        // blurred and dimmed behind the text, a little clearer on the far right.
        if (!details.backdropUrl.isNullOrBlank()) {
            val canBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            AsyncImage(
                model = details.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().then(if (canBlur) Modifier.blur(24.dp) else Modifier),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(JellioBg.copy(alpha = if (canBlur) 0.45f else 0.62f)),
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to JellioBg.copy(alpha = 0.92f),
                        0.55f to JellioBg.copy(alpha = 0.72f),
                        1f to JellioBg.copy(alpha = 0.25f),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to JellioBg.copy(alpha = 0.35f),
                        0.45f to Color.Transparent,
                        0.8f to JellioBg.copy(alpha = 0.75f),
                        1f to JellioBg,
                    ),
                ),
        )

        // Top Back Button
        Surface(
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.22f),
                focusedContentColor = Color.White,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier
                .padding(start = 48.dp, top = 20.dp)
                .focusRequester(backFocusRequester),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Strings.get("nav_back"),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = Strings.get("nav_back"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Hero Content Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // High-resolution Portrait Photo
            val photoModel = details.profileUrl ?: fallbackImageUrl
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(JellioBgElevated)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (photoModel.isNotBlank()) {
                    AsyncImage(
                        model = photoModel,
                        contentDescription = details.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = JellioTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            // Person Details & Metadata
            Column(
                modifier = Modifier
                    .padding(start = 28.dp)
                    .widthIn(max = 760.dp),
            ) {
                // Name
                Text(
                    text = details.name,
                    style = MaterialTheme.typography.headlineLarge.copy(shadow = HeroTextShadow),
                    fontWeight = FontWeight.Bold,
                    color = JellioText,
                )

                // Stats / Chips Row
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Department Badge
                    MetadataBadge(text = details.knownForDepartment ?: "Acting", accent = true)

                    // Born and Age Badge
                    if (!details.birthday.isNullOrBlank()) {
                        val ageText = if (details.age != null) " (${Strings.format("person_age", details.age)})" else ""
                        MetadataBadge(text = "${Strings.get("person_born")} ${details.birthday}$ageText")
                    }

                    // Place of Birth Badge
                    if (!details.placeOfBirth.isNullOrBlank()) {
                        MetadataBadge(text = details.placeOfBirth)
                    }

                    // Total Credits Badge
                    MetadataBadge(text = Strings.format("person_credits_count", details.totalCreditsCount))

                    // IMDb Badge
                    if (!details.imdbId.isNullOrBlank()) {
                        ImdbBadge()
                    }

                    // TMDB Badge
                    TmdbBadge(width = 28.dp, height = 12.dp)
                }

                // Biography Text & Read More Toggle
                if (!details.biography.isNullOrBlank()) {
                    val bioText = details.biography
                    val annotatedBio = remember(bioText) { renderMarkdownBiography(bioText) }
                    val isLong = bioText.length > 200

                    Text(
                        text = annotatedBio,
                        color = JellioText.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = HeroTextShadow),
                        maxLines = if (isBioExpanded) 20 else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    if (isLong) {
                        Surface(
                            onClick = { isBioExpanded = !isBioExpanded },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = JellioTextSecondary,
                                focusedContainerColor = Color.White.copy(alpha = 0.22f),
                                focusedContentColor = Color.White,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(
                                text = Strings.get(if (isBioExpanded) "person_read_less" else "person_read_more"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(text: String, accent: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                if (accent) Color.White.copy(alpha = 0.22f) else JellioBg.copy(alpha = 0.55f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (accent) Color.White else JellioText.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ImdbBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFF5C518), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "IMDb",
            color = Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun DepartmentTabPill(
    tab: DepartmentTab,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSelect,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f, pressedScale = 0.97f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
            contentColor = if (selected) Color.White else JellioTextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.30f),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
        ),
        modifier = modifier.onFocusChanged {
            if (it.isFocused) {
                onSelect()
            }
        },
    ) {
        Text(
            text = "${tab.label} (${tab.count})",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PersonCreditCard(
    credit: PersonCreditItem,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageModel = credit.posterUrl
        ?: if (credit.inLibrary) imageUrl(credit.id, credit.baseItem.PrimaryImageTag, "Primary", 400) else null

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated,
            contentColor = JellioText,
            focusedContainerColor = JellioBgElevated,
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier,
    ) {
        Column {
            // Poster Artwork with Badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color.White.copy(alpha = 0.04f)),
            ) {
                if (!imageModel.isNullOrBlank()) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = credit.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // TMDB Rating Badge
                if (credit.rating != null && credit.rating > 0.0) {
                    RatingChip(
                        rating = credit.rating,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }

            // Title and Role Details
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = credit.title,
                    color = JellioText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )

                val yearStr = credit.year?.toString()
                val roleStr = credit.role?.takeIf { it.isNotBlank() }
                val subtitle = when {
                    credit.department == "Acting" && roleStr != null && yearStr != null -> "$yearStr (as $roleStr)"
                    roleStr != null && yearStr != null -> "$yearStr ($roleStr)"
                    yearStr != null -> yearStr
                    roleStr != null -> roleStr
                    else -> credit.mediaType
                }

                Text(
                    text = subtitle,
                    color = JellioTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private val HeroTextShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 8f)
