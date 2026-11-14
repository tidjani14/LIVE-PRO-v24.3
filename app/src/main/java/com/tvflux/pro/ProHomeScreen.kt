package com.tvflux.pro

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ============================================================================
// LIVE PRO v25.0 — Responsive ProHomeScreen (Zero Top Gap + Mobile Portrait & TV Landscape)
// Optimized Navigation Return (Hoisted LazyListState + Zero Re-Filtering) & Full Localization (AR/FR/EN + RTL/LTR)
// ============================================================================

@Composable
fun ProHomeScreen(
    deviceCode: String = "DEV-••••-••••",
    isVpnConnected: Boolean = true,
    currentDate: String = "",
    mostWatched: List<PosterItem> = emptyList(),
    latestMovies: List<PosterItem> = emptyList(),
    latestSeries: List<PosterItem> = emptyList(),
    liveSports: List<PosterItem> = emptyList(),
    continueWatching: List<PosterItem> = emptyList(),
    favoriteItems: List<PosterItem> = emptyList(),
    onRefresh: () -> Unit = {},
    onDeviceCodeClick: () -> Unit = {},
    onMoviesClick: () -> Unit = {},
    onSeriesClick: () -> Unit = {},
    onSportsClick: () -> Unit = {},
    onFeaturedClick: () -> Unit = {},
    onPosterClick: (PosterItem) -> Unit = {},
    onSeeAllMovies: () -> Unit = {},
    onSeeAllSeries: () -> Unit = {},
    onSeeAllFavorites: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    lang: AppUiLanguage = AppStrings.activeLang,
    homeListState: LazyListState = rememberLazyListState(),
    playlistsCount: Int = 0,
    expiryText: String = "",
    networkSpeedLabel: String = "",
    showScreenCode: Boolean = true,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    liveCount: Int = 0,
    moviesCount: Int = 0,
    seriesCount: Int = 0,
    heroItem: PosterItem? = null,
    onPlaylistsClick: (() -> Unit)? = null,
    onVpnClick: (() -> Unit)? = null,
    onToggleScreenCodeClick: (() -> Unit)? = null,
    onHeroPlayClick: (() -> Unit)? = null,
    onHeroMoreClick: (() -> Unit)? = null
) {
    AppStrings.activeLang = lang

    val configuration = LocalConfiguration.current
    val isTv = configuration.screenWidthDp >= 720 ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val sectionGap = if (isTv) ProSpacing.xl else ProSpacing.md

    val primaryFocusRequester = LiveProGlobalActions.mainContentPrimaryFocusRequester
    val activeProfile = ProfileStorageManager.activeProfileState

    // Hoisted horizontal scroll states so every row preserves its scroll position upon returning
    val quickHubRowState = rememberLazyListState()
    val continueRowState = rememberLazyListState()
    val mostWatchedRowState = rememberLazyListState()
    val sportsRowState = rememberLazyListState()
    val moviesRowState = rememberLazyListState()
    val seriesRowState = rememberLazyListState()
    val favoritesRowState = rememberLazyListState()
    val recommendedRowState = rememberLazyListState()
    val becauseWatchedRowState = rememberLazyListState()

    val filterForProfile: (List<PosterItem>) -> List<PosterItem> = remember(activeProfile) {
        { list ->
            if (!activeProfile.isKidsMode && !activeProfile.isFamilyMode) {
                list
            } else {
                list.filter { item ->
                    val combined = "${item.title} ${item.subtitle.orEmpty()}"
                    !AdultContentFilter.isAdultOr18Plus(combined, item.subtitle.orEmpty())
                }
            }
        }
    }

    val filteredMostWatched = remember(mostWatched, activeProfile) { filterForProfile(mostWatched) }
    val filteredMovies = remember(latestMovies, activeProfile) { filterForProfile(latestMovies) }
    val filteredSeries = remember(latestSeries, activeProfile) { filterForProfile(latestSeries) }
    val filteredContinue = remember(continueWatching, activeProfile) { filterForProfile(continueWatching) }
    val filteredFavorites = remember(favoriteItems, activeProfile) { filterForProfile(favoriteItems) }

    val sortedLiveSports = remember(liveSports, activeProfile) {
        filterForProfile(liveSports)
    }

    val anchorWatchedItem = remember(filteredContinue, filteredMostWatched) {
        filteredContinue.firstOrNull() ?: filteredMostWatched.firstOrNull()
    }
    val becauseYouWatchedRow = remember(anchorWatchedItem, filteredMovies, filteredSeries) {
        val anchorGroup = anchorWatchedItem?.subtitle.orEmpty()
        val pool = (filteredMovies + filteredSeries).distinctBy { it.id }
        if (anchorGroup.isNotBlank()) {
            pool.filter { it.id != anchorWatchedItem?.id && it.subtitle?.contains(anchorGroup, ignoreCase = true) == true }
                .ifEmpty { pool.take(15) }
                .take(20)
        } else {
            pool.take(16)
        }
    }
    val recommendedForYouRow = remember(filteredMovies, filteredSeries, sortedLiveSports) {
        (filteredMovies.take(10) + filteredSeries.take(10) + sortedLiveSports.take(6))
            .distinctBy { it.id }
            .take(20)
    }

    val hasAnyCatalogContent = filteredMostWatched.isNotEmpty() ||
        filteredMovies.isNotEmpty() ||
        filteredSeries.isNotEmpty() ||
        sortedLiveSports.isNotEmpty() ||
        filteredContinue.isNotEmpty() ||
        filteredFavorites.isNotEmpty()

    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasAnyCatalogContent) {
        if (isTv && !isLoading && !initialFocusDone) {
            delay(24L)
            if (runCatching { primaryFocusRequester.requestFocus() }.isSuccess) {
                initialFocusDone = true
            }
        }
    }

    val handlePosterClick: (PosterItem) -> Unit = remember(onPosterClick) {
        { item ->
            val ch = item.sourceChannel
            if (ch != null && ch.contentType != StreamContentType.LIVE_TV) {
                LiveProGlobalActions.openDetailsForChannel(ch)
            } else {
                onPosterClick(item)
            }
        }
    }

    val resolvedHero = remember(heroItem, filteredMovies, sortedLiveSports, filteredMostWatched, filteredSeries) {
        heroItem
            ?: filteredMovies.firstOrNull { !it.imageUrl.isNullOrBlank() }
            ?: sortedLiveSports.firstOrNull()
            ?: filteredMostWatched.firstOrNull()
            ?: filteredSeries.firstOrNull()
    }

    CompositionLocalProvider(LocalLayoutDirection provides lang.layoutDirection) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(DeepNavy)
        ) {
            // 1. Fixed-height Top Status Bar (56dp phone / 68dp TV) with dynamic language support
            ProTopStatusBar(
                deviceCode = deviceCode,
                isVpnConnected = isVpnConnected,
                currentDate = currentDate,
                onRefreshClick = onRefresh,
                onDeviceCodeClick = onDeviceCodeClick,
                lang = lang,
                playlistsCount = playlistsCount,
                expiryText = expiryText,
                networkSpeedLabel = networkSpeedLabel,
                showScreenCode = showScreenCode,
                isLoading = isLoading,
                onPlaylistsClick = onPlaylistsClick,
                onVpnClick = onVpnClick,
                onToggleScreenCodeClick = onToggleScreenCodeClick
            )

            // 2. Content LazyColumn with preserved homeListState
            if (isLoading && !hasAnyCatalogContent) {
                ProLoadingSkeleton(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = homeListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(sectionGap)
                ) {
                    // Hero Banner immediately under Top Bar
                    item(key = "home_hero_banner") {
                        ProHeroBanner(
                            heroItem = resolvedHero,
                            lang = lang,
                            onPlayClick = {
                                if (onHeroPlayClick != null) {
                                    onHeroPlayClick()
                                } else if (resolvedHero != null) {
                                    onPosterClick(resolvedHero)
                                } else {
                                    onFeaturedClick()
                                }
                            },
                            onDetailsClick = {
                                val ch = resolvedHero?.sourceChannel
                                if (ch != null) {
                                    LiveProGlobalActions.openDetailsForChannel(ch)
                                } else if (onHeroMoreClick != null) {
                                    onHeroMoreClick()
                                } else {
                                    onPlaylistsClick?.invoke() ?: onFeaturedClick()
                                }
                            },
                            onAddWatchlistClick = {
                                resolvedHero?.sourceChannel?.let { ch ->
                                    ProfileStorageManager.toggleWatchlist(ch)
                                }
                            }
                        )
                    }

                    // Row 1: ⚡ Quick Access Hub
                    item(key = "home_quick_hub") {
                        ProCategoryCardsRow(
                            onMoviesClick = onMoviesClick,
                            onSeriesClick = onSeriesClick,
                            onSportsClick = onSportsClick,
                            onFeaturedClick = onFeaturedClick,
                            lang = lang,
                            rowListState = quickHubRowState,
                            firstCardFocusRequester = primaryFocusRequester,
                            moviesCount = moviesCount,
                            seriesCount = seriesCount,
                            liveCount = liveCount,
                            playlistsCount = playlistsCount,
                            favoritesCount = filteredFavorites.size,
                            onPlaylistsClick = onPlaylistsClick,
                            onFavoritesClick = {
                                if (onSeeAllFavorites != null) {
                                    onSeeAllFavorites()
                                } else {
                                    LiveProGlobalActions.activeOverlayScreen = ProOverlayScreen.MY_LIST
                                }
                            }
                        )
                    }

                    if (!errorMessage.isNullOrBlank() && !hasAnyCatalogContent) {
                        item(key = "home_error_state") {
                            ProErrorState(
                                lang = lang,
                                message = errorMessage,
                                onRetryClick = onRefresh
                            )
                        }
                    } else if (!hasAnyCatalogContent) {
                        item(key = "home_empty_state") {
                            ProEmptyState(
                                lang = lang,
                                onActionClick = { onPlaylistsClick?.invoke() ?: onRefresh() }
                            )
                        }
                    } else {
                        if (filteredContinue.isNotEmpty()) {
                            item(key = "home_continue_watching") {
                                ProContentRow(
                                    title = AppStrings.tr(
                                        lang,
                                        "⏯ متابعة المشاهدة",
                                        "⏯ Continuer à regarder",
                                        "⏯ Continue Watching"
                                    ),
                                    icon = Icons.Default.History,
                                    items = filteredContinue,
                                    lang = lang,
                                    rowListState = continueRowState,
                                    onItemClick = { item -> onPosterClick(item) },
                                    onItemDetailsClick = handlePosterClick
                                )
                            }
                        }

                        if (filteredMostWatched.isNotEmpty()) {
                            item(key = "home_most_watched") {
                                ProContentRow(
                                    title = AppStrings.tr(
                                        lang,
                                        "🔥 الأكثر مشاهدة",
                                        "🔥 Les plus regardés",
                                        "🔥 Most Watched"
                                    ),
                                    icon = Icons.Default.Whatshot,
                                    items = filteredMostWatched,
                                    lang = lang,
                                    rowListState = mostWatchedRowState,
                                    onItemClick = { item -> onPosterClick(item) },
                                    onSeeAllClick = onFeaturedClick
                                )
                            }
                        }

                        if (sortedLiveSports.isNotEmpty()) {
                            item(key = "home_live_sports") {
                                ProContentRow(
                                    title = AppStrings.tr(
                                        lang,
                                        "📺 رياضة مباشرة (4K → FHD → HD)",
                                        "📺 Sports en Direct (4K → FHD → HD)",
                                        "📺 Live Sports (4K → FHD → HD)"
                                    ),
                                    icon = Icons.Default.SportsSoccer,
                                    items = sortedLiveSports,
                                    lang = lang,
                                    rowListState = sportsRowState,
                                    onItemClick = { item -> onPosterClick(item) },
                                    onSeeAllClick = onSportsClick
                                )
                            }
                        }

                        if (filteredMovies.isNotEmpty()) {
                            item(key = "home_latest_movies") {
                                ProContentRow(
                                    title = AppStrings.tr(
                                        lang,
                                        "🎬 أحدث الأفلام",
                                        "🎬 Derniers Films",
                                        "🎬 Latest Movies"
                                    ),
                                    icon = Icons.Default.Movie,
                                    items = filteredMovies,
                                    lang = lang,
                                    rowListState = moviesRowState,
                                    onItemClick = handlePosterClick,
                                    onSeeAllClick = onSeeAllMovies
                                )
                            }
                        }

                        if (filteredSeries.isNotEmpty()) {
                            item(key = "home_latest_series") {
                                ProContentRow(
                                    title = AppStrings.tr(
                                        lang,
                                        "🎞️ أحدث المسلسلات",
                                        "🎞️ Dernières Séries",
                                        "🎞️ Latest Series"
                                    ),
                                    icon = Icons.Default.LiveTv,
                                    items = filteredSeries,
                                    lang = lang,
                                    rowListState = seriesRowState,
                                    onItemClick = handlePosterClick,
                                    onSeeAllClick = onSeeAllSeries
                                )
                            }
                        }

                        if (filteredFavorites.isNotEmpty()) {
                            item(key = "home_favorites") {
                                ProContentRow(
                                    title = AppStrings.tr(
                                        lang,
                                        "⭐ مفضلتك",
                                        "⭐ Vos Favoris",
                                        "⭐ Your Favorites"
                                    ),
                                    icon = Icons.Default.Favorite,
                                    items = filteredFavorites,
                                    lang = lang,
                                    rowListState = favoritesRowState,
                                    onItemClick = { item -> onPosterClick(item) },
                                    onSeeAllClick = {
                                        if (onSeeAllFavorites != null) {
                                            onSeeAllFavorites()
                                        } else {
                                            LiveProGlobalActions.activeOverlayScreen = ProOverlayScreen.MY_LIST
                                        }
                                    }
                                )
                            }
                        }

                        if (recommendedForYouRow.isNotEmpty()) {
                            item(key = "home_recommended") {
                                ProContentRow(
                                    title = AppStrings.tr(
                                        lang,
                                        "💡 مقترح لك (${activeProfile.name})",
                                        "💡 Recommandé pour vous (${activeProfile.name})",
                                        "💡 Recommended for You (${activeProfile.name})"
                                    ),
                                    icon = Icons.Default.AutoAwesome,
                                    items = recommendedForYouRow,
                                    lang = lang,
                                    rowListState = recommendedRowState,
                                    onItemClick = handlePosterClick
                                )
                            }
                        }

                        if (becauseYouWatchedRow.isNotEmpty() && anchorWatchedItem != null) {
                            item(key = "home_because_you_watched") {
                                ProContentRow(
                                    title = AppStrings.tr(
                                        lang,
                                        "🎯 بسبب مشاهدتك لـ «${anchorWatchedItem.title}»",
                                        "🎯 Parce que vous avez regardé «${anchorWatchedItem.title}»",
                                        "🎯 Because You Watched «${anchorWatchedItem.title}»"
                                    ),
                                    icon = Icons.Default.Recommend,
                                    items = becauseYouWatchedRow,
                                    lang = lang,
                                    rowListState = becauseWatchedRowState,
                                    onItemClick = handlePosterClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProHeroBanner(
    heroItem: PosterItem? = null,
    lang: AppUiLanguage = AppStrings.activeLang,
    onPlayClick: () -> Unit = {},
    onDetailsClick: () -> Unit = {},
    onAddWatchlistClick: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isTv = configuration.screenWidthDp >= 720 ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    val heroHeightDp = when {
        isTv -> 340.dp
        isPortrait -> 220.dp
        else -> 240.dp
    }

    val titleFontSize = when {
        isTv -> 30.sp
        isPortrait -> 18.sp
        else -> 21.sp
    }
    val subFontSize = when {
        isTv -> 13.5.sp
        isPortrait -> 11.sp
        else -> 12.sp
    }
    val btnFontSize = when {
        isTv -> 14.sp
        isPortrait -> 11.2.sp
        else -> 12.sp
    }
    val btnHorizPad = if (isTv) 18.dp else 12.dp
    val btnVertPad = if (isTv) 9.dp else 6.dp

    val playInteraction = remember { MutableInteractionSource() }
    val playFocused by playInteraction.collectIsFocusedAsState()
    val detailsInteraction = remember { MutableInteractionSource() }
    val detailsFocused by detailsInteraction.collectIsFocusedAsState()
    val listInteraction = remember { MutableInteractionSource() }
    val listFocused by listInteraction.collectIsFocusedAsState()

    val playScale by animateFloatAsState(
        targetValue = if (playFocused) ProDimens.FocusScale else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "heroPlayScale"
    )
    val detailsScale by animateFloatAsState(
        targetValue = if (detailsFocused) ProDimens.FocusScale else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "heroDetailsScale"
    )
    val listScale by animateFloatAsState(
        targetValue = if (listFocused) ProDimens.FocusScale else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "heroListScale"
    )

    val defaultBannerTitle = AppStrings.tr(
        lang,
        "مشاهدة سينمائية بلا حدود",
        "Cinéma & Direct Sans Limites",
        "Unlimited Cinema & Live TV"
    )
    val cleanHeroTitle = remember(heroItem?.title, lang) {
        heroItem?.title?.let { ProVisualSanitizer.sanitizeTitle(it, defaultBannerTitle) }
            ?: defaultBannerTitle
    }
    val inWatchlist = heroItem?.id?.let { it in ProfileStorageManager.activeProfileState.watchlistKeys } == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeightDp)
            .padding(horizontal = if (isTv) ProSpacing.lg else ProSpacing.md)
            .clip(RoundedCornerShape(ProDimens.CardRadius.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        DeepNavy,
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        SurfaceDark
                    )
                )
            )
            .border(1.dp, ProBorder.copy(alpha = 0.7f), RoundedCornerShape(ProDimens.CardRadius.dp))
    ) {
        // Poster Artwork on the End side
        if (heroItem != null && !heroItem.imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(
                        when {
                            isTv -> 240.dp
                            isPortrait -> 118.dp
                            else -> 160.dp
                        }
                    )
                    .padding(if (isTv) ProSpacing.md else 8.dp),
                contentAlignment = Alignment.Center
            ) {
                ChannelAvatar(
                    name = cleanHeroTitle,
                    logoUrl = heroItem.imageUrl,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = ProDimens.PosterRadius.dp,
                    contentScale = if (heroItem.isLive) ContentScale.Fit else ContentScale.Crop,
                    innerPadding = if (heroItem.isLive) ProSpacing.sm else 0.dp,
                    isPoster = !heroItem.isLive
                )
            }
        }

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            DeepNavy.copy(alpha = 0.60f),
                            DeepNavy.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Content Column
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isPortrait) 0.74f else 0.70f)
                .padding(if (isTv) ProSpacing.xl else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 6.dp else 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (heroItem?.isLive == true) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = LiveRed
                    ) {
                        Text(
                            text = "● LIVE",
                            color = Color.White,
                            fontSize = if (isPortrait) 9.sp else 10.5.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = CyanPrimary.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, CyanPrimary)
                ) {
                    Text(
                        text = heroItem?.badge?.let {
                            AppStrings.tr(lang, "جودة $it", "Qualité $it", "$it Quality")
                        } ?: "4K UHD",
                        color = CyanPrimary,
                        fontSize = if (isPortrait) 9.sp else 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (!heroItem?.rating.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xCC0F172A)
                    ) {
                        Text(
                            text = "★ ${heroItem?.rating}",
                            color = ImdbGold,
                            fontSize = if (isPortrait) 9.sp else 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = cleanHeroTitle,
                color = TextPrimary,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = heroItem?.plot?.takeIf { it.isNotBlank() }
                    ?: heroItem?.subtitle?.takeIf { it.isNotBlank() }?.let {
                        "$it • ${AppStrings.tr(lang, "اختيار تلقائي لأعلى جودة 4K / FHD", "Sélection auto meilleure qualité 4K / FHD", "Auto-select best 4K / FHD quality")}"
                    }
                    ?: AppStrings.tr(
                        lang,
                        "أقوى القنوات المباشرة والأفلام والمسلسلات العالمية بأعلى جودة 4K UHD",
                        "Les meilleures chaînes en direct, films et séries en qualité 4K UHD",
                        "Top live channels, movies, and series in 4K UHD"
                    ),
                color = TextSecondary,
                fontSize = subFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(if (isTv) 4.dp else 2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. White Play Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = if (playFocused) BorderStroke(2.5.dp, CyanPrimary) else null,
                    shadowElevation = if (playFocused) 12.dp else 4.dp,
                    modifier = Modifier
                        .scale(playScale)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onPlayClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = playInteraction)
                        .clickable(interactionSource = playInteraction, indication = null) { onPlayClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = btnHorizPad, vertical = btnVertPad),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = DeepNavy,
                            modifier = Modifier.size(if (isTv) 20.dp else 16.dp)
                        )
                        Text(
                            text = AppStrings.tr(lang, "تشغيل", "Lecture", "Play"),
                            color = DeepNavy,
                            fontWeight = FontWeight.Black,
                            fontSize = btnFontSize,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // 2. Translucent Details Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (detailsFocused) SurfaceElevated.copy(alpha = 0.92f) else SurfaceGlass,
                    border = BorderStroke(
                        width = if (detailsFocused) 2.dp else 1.dp,
                        color = if (detailsFocused) CyanPrimary else Color.White.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .scale(detailsScale)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onDetailsClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = detailsInteraction)
                        .clickable(interactionSource = detailsInteraction, indication = null) { onDetailsClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = btnHorizPad, vertical = btnVertPad),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (detailsFocused) CyanPrimary else TextPrimary,
                            modifier = Modifier.size(if (isTv) 18.dp else 15.dp)
                        )
                        Text(
                            text = AppStrings.tr(lang, "التفاصيل", "Détails", "Details"),
                            color = if (detailsFocused) CyanPrimary else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = btnFontSize,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // 3. My List Button
                val listLabel = if (inWatchlist) {
                    AppStrings.tr(lang, "في قائمتي", "Dans ma liste", "In My List")
                } else {
                    AppStrings.tr(lang, "قائمتي", "Ma Liste", "My List")
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (listFocused) CyanDark.copy(alpha = 0.38f) else SurfaceGlass,
                    border = BorderStroke(
                        width = if (listFocused) 2.dp else 1.dp,
                        color = if (listFocused) CyanPrimary else ProBorder
                    ),
                    modifier = Modifier
                        .scale(listScale)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onAddWatchlistClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = listInteraction)
                        .clickable(interactionSource = listInteraction, indication = null) { onAddWatchlistClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (isPortrait) 8.dp else 12.dp, vertical = btnVertPad),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = listLabel,
                            tint = if (inWatchlist || listFocused) CyanPrimary else TextPrimary,
                            modifier = Modifier.size(if (isTv) 18.dp else 15.dp)
                        )
                        if (!isPortrait) {
                            Text(
                                text = listLabel,
                                color = if (inWatchlist || listFocused) CyanPrimary else TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = btnFontSize,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
