package com.tvflux.pro

import androidx.compose.foundation.BorderStroke

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

// ============================================================================
// LIVE PRO v25.0 — Advanced Search Engine (Debounced 250ms + Google Voice TV)
// Pill-shape Search Bar + Last 10 Recent Searches + Multi-Filters + Categorized Results
// ============================================================================

enum class SearchResultTab(val titleAr: String) {
    ALL("الكل"),
    CHANNELS("قنوات مباشرة"),
    MOVIES("أفلام"),
    SERIES("مسلسلات"),
    PEOPLE("أشخاص وممثلين")
}

enum class SearchSortOption(val titleAr: String) {
    NEWEST("الأحدث"),
    POPULAR("الأشهر"),
    A_Z("أبجدياً A-Z"),
    DURATION("المدة / الحلقات")
}

@Composable
fun SearchScreen(
    allChannels: List<TvChannel>,
    allMovies: List<TvChannel>,
    allSeries: List<TvChannel>,
    onPlayChannel: (TvChannel) -> Unit,
    onOpenDetails: (TvChannel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var rawQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    var activeCategoryTab by remember { mutableStateOf(SearchResultTab.ALL) }
    var selectedGenre by remember { mutableStateOf("الكل") }
    var selectedYear by remember { mutableStateOf("الكل") }
    var selectedRating by remember { mutableStateOf("الكل") }
    var selectedSort by remember { mutableStateOf(SearchSortOption.POPULAR) }
    var useAdaptiveGridView by remember { mutableStateOf(true) }

    val searchInputFocus = remember { FocusRequester() }

    // Google Voice Search Launcher (Android TV & Mobile)
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotBlank()) {
                rawQuery = spoken
                debouncedQuery = spoken
                ProfileStorageManager.recordRecentSearch(spoken)
            }
        }
    }

    // 250ms Debounce for Instant Live Search
    LaunchedEffect(rawQuery) {
        delay(250L)
        debouncedQuery = rawQuery.trim()
        if (debouncedQuery.length >= 2) {
            ProfileStorageManager.recordRecentSearch(debouncedQuery)
        }
    }

    LaunchedEffect(Unit) {
        delay(40L)
        runCatching { searchInputFocus.requestFocus() }
    }

    val availableGenres = remember(allChannels, allMovies, allSeries) {
        val dynamic = (allMovies.take(150).map { it.group } +
            allSeries.take(150).map { it.group } +
            allChannels.take(150).map { it.group })
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length <= 24 }
            .distinct()
            .take(10)
        listOf("الكل", "رياضة", "أفلام", "مسلسلات", "وثائقي", "أطفال") + dynamic
    }.distinct()

    val yearsList = remember { listOf("الكل", "2026", "2025", "2024", "2023", "2020+") }
    val ratingsList = remember { listOf("الكل", "+8.5 ★", "+8.0 ★", "+7.0 ★") }

    val matchesFilters: (TvChannel) -> Boolean = remember(debouncedQuery, selectedGenre, selectedYear, selectedRating) {
        { ch ->
            val qMatch = debouncedQuery.isBlank() ||
                ch.name.contains(debouncedQuery, ignoreCase = true) ||
                ch.group.contains(debouncedQuery, ignoreCase = true) ||
                ch.plot.contains(debouncedQuery, ignoreCase = true)

            val gMatch = selectedGenre == "الكل" ||
                ch.group.contains(selectedGenre, ignoreCase = true) ||
                ch.name.contains(selectedGenre, ignoreCase = true)

            val yMatch = when (selectedYear) {
                "الكل" -> true
                "2020+" -> (ch.releaseYear.toIntOrNull() ?: 2025) >= 2020
                else -> ch.releaseYear.contains(selectedYear) || ch.name.contains(selectedYear)
            }

            val rMatch = when (selectedRating) {
                "الكل" -> true
                "+8.5 ★" -> (ch.rating.toFloatOrNull() ?: 8.5f) >= 8.5f
                "+8.0 ★" -> (ch.rating.toFloatOrNull() ?: 8.2f) >= 8.0f
                "+7.0 ★" -> (ch.rating.toFloatOrNull() ?: 7.5f) >= 7.0f
                else -> true
            }

            qMatch && gMatch && yMatch && rMatch
        }
    }

    val sortChannels: (List<TvChannel>) -> List<TvChannel> = remember(selectedSort) {
        { list ->
            when (selectedSort) {
                SearchSortOption.NEWEST -> list.sortedByDescending { it.releaseYear.ifBlank { "2025" } }
                SearchSortOption.POPULAR -> list.sortedByDescending {
                    SportsQualitySelector.detectQualityTier(it.name, it.group).priority
                }
                SearchSortOption.A_Z -> list.sortedBy { it.name.lowercase(Locale.ROOT) }
                SearchSortOption.DURATION -> list.sortedByDescending { it.episodes.size }
            }
        }
    }

    val matchedChannels = remember(allChannels, matchesFilters, sortChannels) {
        sortChannels(allChannels.filter(matchesFilters).take(80))
    }
    val matchedMovies = remember(allMovies, matchesFilters, sortChannels) {
        sortChannels(allMovies.filter(matchesFilters).take(80))
    }
    val matchedSeries = remember(allSeries, matchesFilters, sortChannels) {
        sortChannels(allSeries.filter(matchesFilters).take(80))
    }

    val matchedPeople = remember(debouncedQuery) {
        val samplePeople = listOf(
            "كريستوفر نولان (مخرج عالمي)",
            "كيليان مورفي (ممثل سينمائي)",
            "ليوناردو دي كابريو (ممثل)",
            "دينزل واشنطن (ممثل)",
            "مروان حامد (مخرج)",
            "كريم عبد العزيز (ممثل)",
            "تيم حسن (ممثل)",
            "منى زكي (ممثلة)"
        )
        if (debouncedQuery.isBlank()) samplePeople
        else samplePeople.filter { it.contains(debouncedQuery, ignoreCase = true) }.ifEmpty {
            listOf("نتائج الأشخاص لـ «$debouncedQuery»")
        }
    }

    val recentSearches = ProfileStorageManager.recentSearchesState

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(ProSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ProSpacing.md)
    ) {
        // 1A. Separate Top Row: Back Button ("الرجوع") + Title + Smart Grid Toggle ("شبكة ذكية")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, ProBorder),
                modifier = Modifier.clickable { onBack() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "الرجوع",
                        tint = TextPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "الرجوع",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "محرك البحث الذكي",
                color = TextPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, ProBorder),
                modifier = Modifier.clickable { useAdaptiveGridView = !useAdaptiveGridView }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (useAdaptiveGridView) Icons.Default.GridView else Icons.Default.ViewHeadline,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = if (useAdaptiveGridView) "شبكة ذكية" else "صفوف",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 1B. Rectangular Full-Width Search Input (RoundedCornerShape(12.dp), Search Icon at Start, Mic at End)
        OutlinedTextField(
            value = rawQuery,
            onValueChange = { rawQuery = it },
            singleLine = true,
            maxLines = 1,
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                Text(
                    text = "ابحث عن القنوات، الأفلام، أو المسلسلات...",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = "بحث", tint = CyanPrimary)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rawQuery.isNotEmpty()) {
                        IconButton(onClick = { rawQuery = ""; debouncedQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextSecondary)
                        }
                    }
                    IconButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن للبحث الصوتي...")
                                }
                                voiceLauncher.launch(intent)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "بحث صوتي",
                            tint = CyanPrimary
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    ProfileStorageManager.recordRecentSearch(rawQuery)
                    keyboardController?.hide()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = ProBorder,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchInputFocus)
        )

        // 2. Last 10 Recent Searches Row
        if (recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Text("عمليات البحث الأخيرة:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(recentSearches.take(10)) { _, item ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = SurfaceDark,
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = 1.dp,
                                brush = Brush.linearGradient(listOf(ProBorder, ProBorder))
                            ),
                            modifier = Modifier.clickable {
                                rawQuery = item
                                debouncedQuery = item
                            }
                        ) {
                            Text(
                                text = item,
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = { ProfileStorageManager.clearRecentSearches() }) {
                    Text("مسح السجل", color = ErrorRed, fontSize = 11.5.sp)
                }
            }
        }

        // 3. Multi-Filters & Sort Bar (النوع + التصنيف + السنة + التقييم + الترتيب)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(SearchResultTab.values().toList()) { _, tab ->
                val selected = activeCategoryTab == tab
                FilterChipPill(
                    text = tab.titleAr,
                    selected = selected,
                    accent = CyanPrimary,
                    onClick = { activeCategoryTab = tab }
                )
            }
            itemsIndexed(availableGenres.take(8)) { _, genre ->
                val selected = selectedGenre == genre
                FilterChipPill(
                    text = "التصنيف: $genre",
                    selected = selected,
                    accent = VioletSecondary,
                    onClick = { selectedGenre = genre }
                )
            }
            itemsIndexed(yearsList) { _, yr ->
                FilterChipPill(
                    text = "السنة: $yr",
                    selected = selectedYear == yr,
                    accent = SuccessGreen,
                    onClick = { selectedYear = yr }
                )
            }
            itemsIndexed(ratingsList) { _, rt ->
                FilterChipPill(
                    text = "التقييم: $rt",
                    selected = selectedRating == rt,
                    accent = ImdbGold,
                    onClick = { selectedRating = rt }
                )
            }
            itemsIndexed(SearchSortOption.values().toList()) { _, srt ->
                FilterChipPill(
                    text = "ترتيب: ${srt.titleAr}",
                    selected = selectedSort == srt,
                    accent = Color(0xFF38BDF8),
                    onClick = { selectedSort = srt }
                )
            }
        }

        // 4. Categorized Results (قنوات / أفلام / مسلسلات / أشخاص)
        val combinedGridItems = remember(activeCategoryTab, matchedChannels, matchedMovies, matchedSeries) {
            when (activeCategoryTab) {
                SearchResultTab.ALL -> (matchedChannels.take(24) + matchedMovies.take(24) + matchedSeries.take(24))
                SearchResultTab.CHANNELS -> matchedChannels
                SearchResultTab.MOVIES -> matchedMovies
                SearchResultTab.SERIES -> matchedSeries
                SearchResultTab.PEOPLE -> emptyList()
            }
        }

        if (activeCategoryTab == SearchResultTab.PEOPLE) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if ((LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION || LocalConfiguration.current.screenWidthDp >= 720) 180.dp else 110.dp),
                verticalArrangement = Arrangement.spacedBy(ProSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ProSpacing.md),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(matchedPeople) { _, person ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceDark,
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp,
                            brush = Brush.linearGradient(listOf(ProBorder, ProBorder))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val actorName = person.substringBefore("(").trim()
                                rawQuery = actorName
                                activeCategoryTab = SearchResultTab.ALL
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(VioletSecondary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = CyanPrimary)
                            }
                            Column {
                                Text(person, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                Text("اضغط لعرض الأعمال المرتبطة", color = CyanPrimary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else if (useAdaptiveGridView) {
            // Responsive Adaptive Grid: GridCells.Adaptive(minSize = 180.dp)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if ((LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION || LocalConfiguration.current.screenWidthDp >= 720) 180.dp else 110.dp),
                verticalArrangement = Arrangement.spacedBy(ProSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ProSpacing.md),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(combinedGridItems, key = { idx, ch -> "${idx}_${ch.stableKey}" }) { _, ch ->
                    val poster = ch.toPosterItem()
                    Box(contentAlignment = Alignment.Center) {
                        ProPosterCard(
                            title = poster.title,
                            imageUrl = poster.imageUrl,
                            badge = poster.badge,
                            subtitle = poster.subtitle,
                            isLive = poster.isLive,
                            rating = poster.rating,
                            onClick = {
                                if (ch.contentType == StreamContentType.LIVE_TV) {
                                    onPlayChannel(ch)
                                } else {
                                    onOpenDetails(ch)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ProSpacing.xl)
            ) {
                if (matchedChannels.isNotEmpty()) {
                    item(key = "search_live_row") {
                        ProContentRow(
                            title = "📺 قنوات مباشرة (${matchedChannels.size})",
                            icon = Icons.Default.LiveTv,
                            items = matchedChannels.take(30).map { it.toPosterItem() },
                            onItemClick = { p -> p.sourceChannel?.let(onPlayChannel) }
                        )
                    }
                }
                if (matchedMovies.isNotEmpty()) {
                    item(key = "search_movies_row") {
                        ProContentRow(
                            title = "🎬 أفلام (${matchedMovies.size})",
                            icon = Icons.Default.Movie,
                            items = matchedMovies.take(30).map { it.toPosterItem() },
                            onItemClick = { p -> p.sourceChannel?.let(onOpenDetails) }
                        )
                    }
                }
                if (matchedSeries.isNotEmpty()) {
                    item(key = "search_series_row") {
                        ProContentRow(
                            title = "🎞️ مسلسلات (${matchedSeries.size})",
                            icon = Icons.Default.VideoLibrary,
                            items = matchedSeries.take(30).map { it.toPosterItem() },
                            onItemClick = { p -> p.sourceChannel?.let(onOpenDetails) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipPill(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "chipScale"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = when {
            selected -> accent
            focused -> SurfaceElevated
            else -> SurfaceDark
        },
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = if (focused) 2.dp else 1.dp,
            brush = Brush.linearGradient(
                if (focused || selected) listOf(Color.White, accent) else listOf(ProBorder, ProBorder)
            )
        ),
        modifier = Modifier
            .scale(scale)
            .onKeyEvent { ev ->
                if (isDpadSelectKeyEvent(ev)) {
                    onClick()
                    true
                } else false
            }
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    ) {
        Text(
            text = text,
            color = if (selected) DeepNavy else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected || focused) FontWeight.ExtraBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
