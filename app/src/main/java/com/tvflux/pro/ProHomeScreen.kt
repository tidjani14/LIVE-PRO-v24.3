package com.tvflux.pro

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ============================================================================
// LIVE PRO v25.0 — Corporate-Grade ProHomeScreen
// Supports Auto Sports Best Quality (4K→FHD→HD→SD), Skeletons, RTL & TV D-Pad
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

    // Sort liveSports automatically by highest quality tier: 4K/UHD → FHD/1080 → HD/720 → SD
    val sortedLiveSports = remember(liveSports) {
        liveSports.sortedByDescending { it.qualityTier.priority }
    }

    val hasAnyCatalogContent = mostWatched.isNotEmpty() ||
        latestMovies.isNotEmpty() ||
        latestSeries.isNotEmpty() ||
        sortedLiveSports.isNotEmpty() ||
        continueWatching.isNotEmpty() ||
        favoriteItems.isNotEmpty()

    LaunchedEffect(isLoading, hasAnyCatalogContent) {
        if (!isLoading) {
            for (attempt in 1..6) {
                delay(if (attempt <= 2) 30L else 50L)
                if (runCatching { primaryFocusRequester.requestFocus() }.isSuccess) break
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        // 1. Top Status Bar (Real Device Date + VPN + Network Speed + Device Code)
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

        // 2. Main Body with Corporate Skeleton / Error / Empty / Content States
        if (isLoading && !hasAnyCatalogContent) {
            ProLoadingSkeleton(modifier = Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = ProSpacing.s32)
            ) {
                Spacer(modifier = Modifier.height(ProSpacing.lg))

                // Hero Banner
                val resolvedHero = heroItem
                    ?: sortedLiveSports.firstOrNull()
                    ?: mostWatched.firstOrNull()
                    ?: latestMovies.firstOrNull()
                    ?: latestSeries.firstOrNull()

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
                    onMoreClick = {
                        if (onHeroMoreClick != null) {
                            onHeroMoreClick()
                        } else {
                            onPlaylistsClick?.invoke() ?: onFeaturedClick()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(ProSpacing.xxl))

                // 4 Unified Category Cards (قنوات مميزة - الرياضة - الأفلام - المسلسلات)
                ProCategoryCardsRow(
                    onMoviesClick = onMoviesClick,
                    onSeriesClick = onSeriesClick,
                    onSportsClick = onSportsClick,
                    onFeaturedClick = onFeaturedClick,
                    firstCardFocusRequester = primaryFocusRequester,
                    moviesCount = moviesCount,
                    seriesCount = seriesCount,
                    liveCount = liveCount
                )

                Spacer(modifier = Modifier.height(ProSpacing.xxl))

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
                    // Continue Watching Row
                    if (continueWatching.isNotEmpty()) {
                        ProContentRow(
                            title = "متابعة المشاهدة",
                            icon = Icons.Default.History,
                            items = continueWatching,
                            onItemClick = onPosterClick
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xxl))
                    }

                    // Live Sports & beIN Row (Auto-sorted: 4K → FHD → HD → SD)
                    if (sortedLiveSports.isNotEmpty()) {
                        ProContentRow(
                            title = "رياضة مباشرة • أعلى جودة تلقائياً",
                            icon = Icons.Default.SportsSoccer,
                            items = sortedLiveSports,
                            onItemClick = onPosterClick,
                            onSeeAllClick = onSportsClick
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xxl))
                    }

                    // Favorites Row
                    if (favoriteItems.isNotEmpty()) {
                        ProContentRow(
                            title = "قائمتي المفضلة",
                            icon = Icons.Default.Favorite,
                            items = favoriteItems,
                            onItemClick = onPosterClick,
                            onSeeAllClick = onSeeAllFavorites
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xxl))
                    }

                    // Most Watched Row
                    if (mostWatched.isNotEmpty()) {
                        ProContentRow(
                            title = "الأكثر مشاهدة",
                            icon = Icons.Default.Whatshot,
                            items = mostWatched,
                            onItemClick = onPosterClick,
                            onSeeAllClick = onFeaturedClick
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xxl))
                    }

                    // Latest Movies Row
                    if (latestMovies.isNotEmpty()) {
                        ProContentRow(
                            title = "أحدث الأفلام",
                            icon = Icons.Default.Movie,
                            items = latestMovies,
                            onItemClick = onPosterClick,
                            onSeeAllClick = onSeeAllMovies
                        )
                        Spacer(modifier = Modifier.height(ProSpacing.xxl))
                    }

                    // Latest Series Row
                    if (latestSeries.isNotEmpty()) {
                        ProContentRow(
                            title = "أحدث المسلسلات",
                            icon = Icons.Default.LiveTv,
                            items = latestSeries,
                            onItemClick = onPosterClick,
                            onSeeAllClick = onSeeAllSeries
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
    onMoreClick: () -> Unit = {}
) {
    val playInteraction = remember { MutableInteractionSource() }
    val playFocused by playInteraction.collectIsFocusedAsState()
    val moreInteraction = remember { MutableInteractionSource() }
    val moreFocused by moreInteraction.collectIsFocusedAsState()

    val playScale by animateFloatAsState(
        targetValue = if (playFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "heroPlayScale"
    )
    val moreScale by animateFloatAsState(
        targetValue = if (moreFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "heroMoreScale"
    )

    val cleanHeroTitle = remember(heroItem?.title) {
        heroItem?.title?.let { ProVisualSanitizer.sanitizeTitle(it, "مشاهدة بلا حدود") } ?: "مشاهدة بلا حدود"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ProDimens.HeroHeight.dp)
            .padding(horizontal = ProSpacing.lg)
            .clip(RoundedCornerShape(ProDimens.CardRadius.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        SurfaceDark,
                        Color(0xFF1E1B4B),
                        SurfaceDark
                    )
                )
            )
            .border(1.dp, ProBorder.copy(alpha = 0.65f), RoundedCornerShape(ProDimens.CardRadius.dp))
    ) {
        if (heroItem != null && !heroItem.imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(200.dp)
                    .padding(ProSpacing.lg),
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            DeepNavy.copy(alpha = 0.84f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(ProSpacing.xl)
        ) {
            if (heroItem?.badge != null) {
                Surface(
                    shape = RoundedCornerShape(ProSpacing.xs),
                    color = CyanPrimary.copy(alpha = 0.2f),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(CyanPrimary, CyanPrimary))
                    ),
                    modifier = Modifier.padding(bottom = ProSpacing.sm)
                ) {
                    Text(
                        text = "جودة تلقائية ${heroItem.badge}",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = ProSpacing.sm, vertical = ProSpacing.xs)
                    )
                }
            }

            Text(
                text = cleanHeroTitle,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(ProSpacing.xs))
            Text(
                text = heroItem?.subtitle?.takeIf { it.isNotBlank() }?.let { "$it • اختيار تلقائي لأعلى جودة 4K / FHD" }
                    ?: "بجودة فائقة 4K • بث مستقر • انتقال فوري للقنوات",
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(ProSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(ProSpacing.md)) {
                Surface(
                    shape = RoundedCornerShape(ProDimens.ButtonRadius.dp),
                    color = CyanPrimary,
                    border = if (playFocused) {
                        ButtonDefaults.outlinedButtonBorder.copy(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(listOf(Color.White, CyanPrimary))
                        )
                    } else null,
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
                    Text(
                        text = "▶ تشغيل بأعلى جودة",
                        color = TextOnAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = ProSpacing.xl, vertical = ProSpacing.sm)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(ProDimens.ButtonRadius.dp),
                    color = if (moreFocused) SurfaceElevated else SurfaceDark.copy(alpha = 0.85f),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = if (moreFocused) 2.5.dp else 1.dp,
                        brush = Brush.linearGradient(
                            if (moreFocused) listOf(CyanPrimary, CyanPrimary) else listOf(ProBorder, ProBorder)
                        )
                    ),
                    modifier = Modifier
                        .scale(moreScale)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onMoreClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = moreInteraction)
                        .clickable(interactionSource = moreInteraction, indication = null) { onMoreClick() }
                ) {
                    Text(
                        text = "إدارة القوائم",
                        color = if (moreFocused) CyanPrimary else TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = ProSpacing.lg, vertical = ProSpacing.sm)
                    )
                }
            }
        }
    }
}
