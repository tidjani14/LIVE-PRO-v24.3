package com.tvflux.pro

import android.content.res.Configuration
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ============================================================================
// LIVE PRO v25.0 — Netflix / Shahid VIP Style Home Screen
// Hero Banner (340dp TV / 250dp Landscape / 220dp Portrait) + Ordered Carousels:
// 1. Quick Access Hub
// 2. Continue Watching (with progress bar)
// 3. Most Watched
// 4. Live Sports (Auto-sorted 4K → FHD → HD → SD)
// 5. Latest Movies
// 6. Latest Series
// 7. Your Favorites & My List
// 8. Recommended For You & Because You Watched
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
    val primaryFocusRequester = LiveProGlobalActions.mainContentPrimaryFocusRequester
    val activeProfile = ProfileStorageManager.activeProfileState

    // Filter content if Kids Mode or Family Mode is enabled on the active profile
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

    // Sort liveSports automatically by highest quality tier: 4K/UHD → FHD/1080 → HD/720 → SD
    val sortedLiveSports = remember(liveSports, activeProfile) {
        filterForProfile(liveSports).sortedByDescending { it.qualityTier.priority }
    }

    // Smart Recommendations ("مقترح لك") & "Because You Watched" ("بسبب مشاهدتك لـ")
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

    LaunchedEffect(isLoading, hasAnyCatalogContent) {
        if (!isLoading) {
            for (attempt in 1..6) {
                delay(if (attempt <= 2) 30L else 50L)
                if (runCatching { primaryFocusRequester.requestFocus() }.isSuccess) break
            }
        }
    }

    val handlePosterClick: (PosterItem) -> Unit = { item ->
        val ch = item.sourceChannel
        if (ch != null && ch.contentType != StreamContentType.LIVE_TV) {
            // Open rich Netflix/IMDb Details Screen for Movies & Series, or play directly
            LiveProGlobalActions.openDetailsForChannel(ch)
        } else {
            onPosterClick(item)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        // 1. Top Status Bar (Profile + Search Pill + Device Code + Notifications + VPN + Network Speed)
        ProTopStatusBar(
            deviceCode = deviceCode,
            isVpnConnected = isVpnConnected,
            currentDate = currentDate,
            onRefreshClick = onRefresh,
            onDeviceCodeClick = onDeviceCodeClick,
            playlistsCount = playlistsCount,
            expiryText = expiryText,
            networkSpeedLabel = networkSpeedLabel,
            showScreenCode = showScreenCode,
            isLoading = isLoading,
            onPlaylistsClick = onPlaylistsClick,
            onVpnClick = onVpnClick,
            onToggleScreenCodeClick = onToggleScreenCodeClick
        )

        // 2. Main Body with Skeleton / Error / Empty / Netflix Carousels
        if (isLoading && !hasAnyCatalogContent) {
            ProLoadingSkeleton(modifier = Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = ProSpacing.s48)
            ) {
                Spacer(modifier = Modifier.height(ProSpacing.md))

                // Hero Banner (340dp TV / 250dp Phone Landscape / 220dp Phone Portrait)
                val resolvedHero = heroItem
                    ?: filteredMovies.firstOrNull { !it.imageUrl.isNullOrBlank() }
                    ?: sortedLiveSports.firstOrNull()
                    ?: filteredMostWatched.firstOrNull()
                    ?: filteredSeries.firstOrNull()

                ProHeroBanner(
                    heroItem = resolvedHero,
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

                Spacer(modifier = Modifier.height(ProSpacing.xl))

                // Row 1: ⚡ Quick Access Hub (مباشر، أفلام، مسلسلات، رياضة، قوائم، مفضلة)
                ProCategoryCardsRow(
                    onMoviesClick = onMoviesClick,
                    onSeriesClick = onSeriesClick,
                    onSportsClick = onSportsClick,
                    onFeaturedClick = onFeaturedClick,
                    firstCardFocusRequester = primaryFocusRequester,
                    moviesCount = moviesCount,
                    seriesCount = seriesCount,
                    liveCount = liveCount,
                    playlistsCount = playlistsCount,
                    favoritesCount = filteredFavorites.size,
                    onPlaylistsClick = onPlaylistsClick,
                    onFavoritesClick = {
                        LiveProGlobalActions.activeOverlayScreen = ProOverlayScreen.MY_LIST
                    }
                )

                Spacer(modifier = Modifier.height(ProSpacing.xl))

                if (!errorMessage.isNullOrBlank() && !hasAnyCatalogContent) {
                    ProErrorState(
                        message = errorMessage,
                        onRetryClick = onRefresh
                    )
                } else if (!hasAnyCatalogContent) {
                    ProEmptyState(
                        onActionClick = { onPlaylistsClick?.invoke() ?: onRefresh() }
                    )
                } else {
                    // Row 2: ⏯ متابعة المشاهدة (Continue Watching with progress bar under each poster)
                    if (filteredContinue.isNotEmpty()) {
                        ProContentRow(
                            title = "⏯ متابعة المشاهدة",
                            icon = Icons.Default.History,
                            items = filteredContinue,
                            onItemClick = { item -> onPosterClick(item) },
                            onItemDetailsClick = handlePosterClick
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xl))
                    }

                    // Row 3: 🔥 الأكثر مشاهدة (Most Watched)
                    if (filteredMostWatched.isNotEmpty()) {
                        ProContentRow(
                            title = "🔥 الأكثر مشاهدة",
                            icon = Icons.Default.Whatshot,
                            items = filteredMostWatched,
                            onItemClick = { item -> onPosterClick(item) },
                            onSeeAllClick = onFeaturedClick
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xl))
                    }

                    // Row 4: 📺 رياضة مباشرة (Auto-sorted: 4K → FHD → HD → SD)
                    if (sortedLiveSports.isNotEmpty()) {
                        ProContentRow(
                            title = "📺 رياضة مباشرة • مفروزة تلقائياً (4K → FHD → HD → SD)",
                            icon = Icons.Default.SportsSoccer,
                            items = sortedLiveSports,
                            onItemClick = { item -> onPosterClick(item) },
                            onSeeAllClick = onSportsClick
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xl))
                    }

                    // Row 5: 🎬 أحدث الأفلام (Latest Movies)
                    if (filteredMovies.isNotEmpty()) {
                        ProContentRow(
                            title = "🎬 أحدث الأفلام",
                            icon = Icons.Default.Movie,
                            items = filteredMovies,
                            onItemClick = handlePosterClick,
                            onSeeAllClick = onSeeAllMovies
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xl))
                    }

                    // Row 6: 🎞️ أحدث المسلسلات (Latest Series)
                    if (filteredSeries.isNotEmpty()) {
                        ProContentRow(
                            title = "🎞️ أحدث المسلسلات",
                            icon = Icons.Default.LiveTv,
                            items = filteredSeries,
                            onItemClick = handlePosterClick,
                            onSeeAllClick = onSeeAllSeries
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xl))
                    }

                    // Row 7: ⭐ مفضلتك (Your Favorites)
                    if (filteredFavorites.isNotEmpty()) {
                        ProContentRow(
                            title = "⭐ مفضلتك",
                            icon = Icons.Default.Favorite,
                            items = filteredFavorites,
                            onItemClick = { item -> onPosterClick(item) },
                            onSeeAllClick = {
                                LiveProGlobalActions.activeOverlayScreen = ProOverlayScreen.MY_LIST
                            }
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xl))
                    }

                    // Row 8: 💡 مقترح لك (Recommendations based on Watch History)
                    if (recommendedForYouRow.isNotEmpty()) {
                        ProContentRow(
                            title = "💡 مقترح لك (${activeProfile.name})",
                            icon = Icons.Default.AutoAwesome,
                            items = recommendedForYouRow,
                            onItemClick = handlePosterClick
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xl))
                    }

                    // Row 9: 🎯 بسبب مشاهدتك لـ X (Because You Watched)
                    if (becauseYouWatchedRow.isNotEmpty() && anchorWatchedItem != null) {
                        ProContentRow(
                            title = "🎯 بسبب مشاهدتك لـ «${anchorWatchedItem.title}»",
                            icon = Icons.Default.Recommend,
                            items = becauseYouWatchedRow,
                            onItemClick = handlePosterClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProHeroBanner(
    heroItem: PosterItem? = null,
    onPlayClick: () -> Unit = {},
    onDetailsClick: () -> Unit = {},
    onAddWatchlistClick: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.screenWidthDp >= 720 ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // Responsive Hero Height: TV = 340dp, Phone Landscape = 250dp, Phone Portrait = 220dp
    val heroHeightDp = when {
        isTv -> ProDimens.HeroHeightTv.dp
        isPortrait -> ProDimens.HeroHeightPortrait.dp
        else -> ProDimens.HeroHeightLandscape.dp
    }

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

    val cleanHeroTitle = remember(heroItem?.title) {
        heroItem?.title?.let { ProVisualSanitizer.sanitizeTitle(it, "مشاهدة سينمائية بلا حدود") }
            ?: "مشاهدة سينمائية بلا حدود"
    }
    val inWatchlist = heroItem?.id?.let { it in ProfileStorageManager.activeProfileState.watchlistKeys } == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeightDp)
            .padding(horizontal = ProSpacing.lg)
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
        // Cinematic Backdrop + High-Res Poster on the End side
        if (heroItem != null && !heroItem.imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(if (isTv) 260.dp else 185.dp)
                    .padding(ProSpacing.md),
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

        // Cinematic Multi-Layer Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            DeepNavy.copy(alpha = 0.55f),
                            DeepNavy.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isPortrait) 0.92f else 0.72f)
                .padding(if (isTv) ProSpacing.xxl else ProSpacing.lg)
        ) {
            // Top Metadata Chips (LIVE / Quality / IMDb Rating / Year)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = ProSpacing.sm)
            ) {
                if (heroItem?.isLive == true) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = LiveRed
                    ) {
                        Text(
                            text = "● بث مباشر LIVE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CyanPrimary.copy(alpha = 0.2f),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(CyanPrimary, CyanPrimary))
                    )
                ) {
                    Text(
                        text = heroItem?.badge?.let { "جودة فائقة $it" } ?: "4K UHD • HDR10+",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (!heroItem?.rating.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xCC0F172A)
                    ) {
                        Text(
                            text = "IMDb ★ ${heroItem?.rating}",
                            color = ImdbGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                text = cleanHeroTitle,
                color = TextPrimary,
                fontSize = if (isTv) 32.sp else 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = heroItem?.plot?.takeIf { it.isNotBlank() }
                    ?: heroItem?.subtitle?.takeIf { it.isNotBlank() }?.let { "$it • اختيار تلقائي لأعلى جودة 4K / FHD • صوت محيطي Dolby" }
                    ?: "استمتع بأقوى القنوات المباشرة والأفلام والمسلسلات العالمية بأعلى جودة 4K UHD وبدون تقطيع",
                color = TextSecondary,
                fontSize = if (isTv) 14.sp else 12.sp,
                maxLines = if (isTv) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(ProSpacing.md))

            // Netflix Hero Action Buttons: Large White Play Button + Translucent Details Button + My List Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(ProSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Large White Play Button (Netflix Style)
                Surface(
                    shape = RoundedCornerShape(ProDimens.ButtonRadius.dp),
                    color = Color.White,
                    border = if (playFocused) {
                        ButtonDefaults.outlinedButtonBorder.copy(
                            width = 3.dp,
                            brush = Brush.linearGradient(listOf(CyanPrimary, VioletSecondary))
                        )
                    } else null,
                    shadowElevation = if (playFocused) 14.dp else 4.dp,
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
                        modifier = Modifier.padding(horizontal = ProSpacing.xl, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = DeepNavy,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "تشغيل الآن",
                            color = DeepNavy,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.5.sp
                        )
                    }
                }

                // 2. Translucent Glass Details Button (زر Details شفاف)
                Surface(
                    shape = RoundedCornerShape(ProDimens.ButtonRadius.dp),
                    color = if (detailsFocused) SurfaceElevated.copy(alpha = 0.92f) else SurfaceGlass,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = if (detailsFocused) 2.5.dp else 1.dp,
                        brush = Brush.linearGradient(
                            if (detailsFocused) listOf(CyanPrimary, CyanPrimary)
                            else listOf(Color.White.copy(alpha = 0.35f), ProBorder)
                        )
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
                        modifier = Modifier.padding(horizontal = ProSpacing.lg, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (detailsFocused) CyanPrimary else TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = "التفاصيل والمعلومات",
                            color = if (detailsFocused) CyanPrimary else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // 3. Quick My List (+ قائمتي) Button
                Surface(
                    shape = RoundedCornerShape(ProDimens.ButtonRadius.dp),
                    color = if (listFocused) CyanDark.copy(alpha = 0.38f) else SurfaceGlass,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = if (listFocused) 2.5.dp else 1.dp,
                        brush = Brush.linearGradient(
                            if (listFocused) listOf(CyanPrimary, CyanPrimary) else listOf(ProBorder, ProBorder)
                        )
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
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                            tint = if (inWatchlist || listFocused) CyanPrimary else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (inWatchlist) "في قائمتي" else "قائمتي",
                            color = if (inWatchlist || listFocused) CyanPrimary else TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }
}
