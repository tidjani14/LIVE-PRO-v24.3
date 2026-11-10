package com.tvflux.pro

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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * ProHomeScreen — الشاشة الرئيسية النهائية بالشكل الاحترافي الإنتاجي
 *
 * استبدل محتوى الـ Home / Dashboard الحالي بهذا الـ Composable.
 * يعتمد على المكونات الموجودة في ProHomeComponents.kt و Theme.kt
 */
@Composable
fun ProHomeScreen(
    deviceCode: String = "DEV-••••-••••",
    isVpnConnected: Boolean = true,
    currentDate: String = "الثلاثاء ، 10-11-2026",
    // بيانات حقيقية من ViewModel / MainActivity
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
    // إضافات حالة اختيارية متوافقة مع النظام الحالي
    playlistsCount: Int = 0,
    expiryText: String = "",
    networkSpeedLabel: String = "",
    showScreenCode: Boolean = true,
    isLoading: Boolean = false,
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

    LaunchedEffect(Unit) {
        for (attempt in 1..6) {
            delay(if (attempt <= 2) 30L else 50L)
            if (runCatching { primaryFocusRequester.requestFocus() }.isSuccess) break
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ProBg)
    ) {
        // ===== Top Status Bar =====
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

        // ===== Scrollable Content =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ===== Hero Banner (professional production version) =====
            val resolvedHero = heroItem
                ?: mostWatched.firstOrNull()
                ?: latestMovies.firstOrNull()
                ?: liveSports.firstOrNull()
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

            Spacer(modifier = Modifier.height(20.dp))

            // ===== 4 Category Cards =====
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

            Spacer(modifier = Modifier.height(24.dp))

            // ===== Continue Watching =====
            if (continueWatching.isNotEmpty()) {
                ProContentRow(
                    title = "متابعة المشاهدة",
                    icon = Icons.Default.History,
                    items = continueWatching,
                    onItemClick = onPosterClick
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ===== Favorites =====
            if (favoriteItems.isNotEmpty()) {
                ProContentRow(
                    title = "قائمتي المفضلة",
                    icon = Icons.Default.Favorite,
                    items = favoriteItems,
                    onItemClick = onPosterClick,
                    onSeeAllClick = onSeeAllFavorites
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ===== Most Watched / Featured Live Channels =====
            if (mostWatched.isNotEmpty()) {
                ProContentRow(
                    title = "الأكثر مشاهدة",
                    icon = Icons.Default.Whatshot,
                    items = mostWatched,
                    onItemClick = onPosterClick,
                    onSeeAllClick = onFeaturedClick
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ===== Latest Movies =====
            if (latestMovies.isNotEmpty()) {
                ProContentRow(
                    title = "أحدث الأفلام",
                    icon = Icons.Default.Movie,
                    items = latestMovies,
                    onItemClick = onPosterClick,
                    onSeeAllClick = onSeeAllMovies
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ===== Live Sports =====
            if (liveSports.isNotEmpty()) {
                ProContentRow(
                    title = "رياضة مباشرة",
                    icon = Icons.Default.SportsSoccer,
                    items = liveSports,
                    onItemClick = onPosterClick,
                    onSeeAllClick = onSportsClick
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ===== Latest Series =====
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
        targetValue = if (playFocused) 1.06f else 1f,
        animationSpec = tween(160),
        label = "heroPlayScale"
    )
    val moreScale by animateFloatAsState(
        targetValue = if (moreFocused) 1.06f else 1f,
        animationSpec = tween(160),
        label = "heroMoreScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A)
                    )
                )
            )
            .border(1.dp, ProBorder.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
    ) {
        // Optional Hero Artwork on the end side
        if (heroItem != null && !heroItem.imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(200.dp)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                ChannelAvatar(
                    name = heroItem.title,
                    logoUrl = heroItem.imageUrl,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 14.dp,
                    contentScale = if (heroItem.isLive) ContentScale.Fit else ContentScale.Crop,
                    innerPadding = if (heroItem.isLive) 8.dp else 0.dp,
                    isPoster = !heroItem.isLive
                )
            }
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            ProBg.copy(alpha = 0.78f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(20.dp)
        ) {
            Text(
                text = heroItem?.title?.takeIf { it.isNotBlank() } ?: "مشاهدة بلا حدود",
                color = ProTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = heroItem?.subtitle?.takeIf { it.isNotBlank() }?.let { "$it • بجودة فائقة 4K • بث مستقر" }
                    ?: "بجودة فائقة 4K • بث مستقر • تحديث فوري",
                color = ProTextSecondary,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Play button (D-Pad & Touch friendly)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (playFocused) ProFocusRing else ProPrimary,
                    border = if (playFocused) {
                        ButtonDefaults.outlinedButtonBorder.copy(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(listOf(Color.White, ProFocusRing))
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
                        text = "▶ تشغيل",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp)
                    )
                }

                // More / Playlists button (D-Pad & Touch friendly)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (moreFocused) ProSurfaceElevated else ProSurface.copy(alpha = 0.75f),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = if (moreFocused) 2.dp else 1.dp,
                        brush = Brush.linearGradient(
                            if (moreFocused) listOf(ProFocusRing, ProPrimary) else listOf(ProBorder, ProBorder)
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
                        color = if (moreFocused) ProFocusRing else ProTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}
