package com.tvflux.pro

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ============================================================================
// PLAYER VIP v26.0 — Muted Dark OLED DetailsScreen (TMDB Live + Series Resume + Offline Downloads)
// ============================================================================

@Immutable
data class CastMemberInfo(
    val name: String,
    val role: String,
    val initials: String,
    val accentHex: Long,
    val profileUrl: String = ""
)

@Immutable
data class RichContentMetadata(
    val cleanTitle: String,
    val plot: String,
    val releaseYear: String,
    val durationLabel: String,
    val ageRating: String,
    val imdbRating: String,
    val rottenTomatoesScore: String,
    val director: String,
    val genres: List<String>,
    val cast: List<CastMemberInfo>,
    val youtubeTrailerSearchUrl: String
)

object TmdbImdbMetadataEnricher {
    fun enrich(channel: TvChannel): RichContentMetadata {
        val cleanTitle = ProVisualSanitizer.sanitizeTitle(channel.name, "عمل سينمائي مميز")
        val year = channel.releaseYear.takeIf { it.length >= 4 } ?: "2025"
        val rawRating = channel.rating.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 8.4f
        val clampedImdb = String.format(java.util.Locale.US, "%.1f", rawRating.coerceIn(6.2f, 9.6f))
        val rtScore = "${(rawRating.coerceIn(6.5f, 9.7f) * 10).toInt().coerceIn(72, 98)}%"

        val duration = when (channel.contentType) {
            StreamContentType.SERIES -> {
                val epCount = channel.episodes.size.coerceAtLeast(8)
                "$epCount حلقة • 45 دقيقة للحلقة"
            }
            StreamContentType.MOVIE -> "1h 58m • 4K HDR10+"
            StreamContentType.LIVE_TV -> "بث مباشر 24/7 • 60fps"
        }

        val isAdult = AdultContentFilter.isAdultOr18Plus(channel.name, channel.group)
        val ageRating = when {
            isAdult -> "+18"
            channel.group.contains("أطفال", true) || channel.group.contains("kids", true) -> "عائلي G"
            else -> "PG-13 (+13)"
        }

        val resolvedPlot = channel.plot.trim().takeIf { it.length > 15 }
            ?: "تدور أحداث «$cleanTitle» في إطار تشويقي ودرامي مثير ضمن تصنيف (${channel.group.ifBlank { "الأعمال العالمية" }})، حيث تتشابك الأحداث في إنتاج سينمائي فائق الجودة."

        val genres = listOfNotNull(
            channel.group.takeIf { it.isNotBlank() && it != "عام" },
            if (channel.contentType == StreamContentType.SERIES) "مسلسل درامي" else "فيلم سينمائي",
            "تشويق وإثارة",
            "4K UHD"
        ).distinct()

        // [2] Hide Fake Cast: Set hardcoded fake castList to emptyList()
        val castList = emptyList<CastMemberInfo>()

        val trailerQuery = Uri.encode("$cleanTitle $year official trailer")
        val trailerUrl = "https://www.youtube.com/results?search_query=$trailerQuery"

        return RichContentMetadata(
            cleanTitle = cleanTitle,
            plot = resolvedPlot,
            releaseYear = year,
            durationLabel = duration,
            ageRating = ageRating,
            imdbRating = clampedImdb,
            rottenTomatoesScore = rtScore,
            director = "إنتاج سينمائي عالمي • PLAYER VIP",
            genres = genres,
            cast = castList,
            youtubeTrailerSearchUrl = trailerUrl
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    channel: TvChannel,
    similarChannels: List<TvChannel> = emptyList(),
    isFavorite: Boolean = false,
    xtreamPlaylists: List<XtreamAccountConfig> = emptyList(),
    vodWatchProgressMap: Map<String, VodWatchProgress> = emptyMap(),
    onPlay: (TvChannel) -> Unit,
    onPlayEpisode: ((SeriesEpisode) -> Unit)? = null,
    onToggleFavorite: (TvChannel) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPortraitPhone = configuration.orientation == Configuration.ORIENTATION_PORTRAIT && configuration.screenWidthDp < 600

    LaunchedEffect(Unit) {
        OfflineDownloadsManager.init(context)
    }

    val meta = remember(channel) { TmdbImdbMetadataEnricher.enrich(channel) }
    val activeProfile = ProfileStorageManager.activeProfileState
    val inWatchlist = channel.stableKey in activeProfile.watchlistKeys
    val currentUserRating = activeProfile.userRatings[channel.stableKey] ?: 0

    // Real TMDB Fetch via LaunchedEffect(channel.name)
    var tmdbDetails by remember(channel.name) { mutableStateOf<TmdbMovieDetails?>(null) }
    var isLoadingTmdb by remember(channel.name) { mutableStateOf(true) }

    LaunchedEffect(channel.name) {
        isLoadingTmdb = true
        tmdbDetails = TmdbRepository.fetchMovieDetails(
            title = channel.name,
            year = channel.releaseYear,
            preferSeries = (channel.contentType == StreamContentType.SERIES)
        )
        isLoadingTmdb = false
    }

    // Dynamic Series Episodes loading if channel.contentType == SERIES and episodes are not yet loaded
    val cachedInitialEpisodes = remember(channel.stableKey, channel.seriesId, channel.episodes) {
        if (channel.episodes.isNotEmpty()) channel.episodes
        else XtreamRepository.getCachedSeriesEpisodesNow(channel).orEmpty()
    }
    var seriesEpisodes by remember(channel.stableKey, channel.episodes) {
        mutableStateOf(cachedInitialEpisodes)
    }
    var isLoadingEpisodes by remember(channel.stableKey) {
        mutableStateOf(channel.contentType == StreamContentType.SERIES && cachedInitialEpisodes.isEmpty())
    }

    LaunchedEffect(channel.stableKey, xtreamPlaylists) {
        if (channel.contentType == StreamContentType.SERIES && seriesEpisodes.isEmpty()) {
            isLoadingEpisodes = true
            val fetched = runCatching {
                XtreamRepository.fetchSeriesEpisodes(channel, xtreamPlaylists)
            }.getOrDefault(emptyList())
            if (fetched.isNotEmpty()) {
                seriesEpisodes = fetched
            }
            isLoadingEpisodes = false
        }
    }

    // Track last watched episode for this series so "Play Now" resumes that specific episode
    val lastWatchedEpisodePair = remember(seriesEpisodes, vodWatchProgressMap, channel.stableKey) {
        seriesEpisodes.mapNotNull { ep ->
            val epStableKey = "${channel.stableKey}_S${ep.seasonNumber}_E${ep.episodeNumber}"
            val prog = vodWatchProgressMap[epStableKey]
                ?: vodWatchProgressMap[ep.url.trim()]
                ?: vodWatchProgressMap.values.firstOrNull { it.resolvedKey == ep.url.trim() }
            if (prog != null && prog.positionMs >= 3_000L) (ep to prog) else null
        }.maxByOrNull { it.second.updatedAt }
    }

    val playSpecificEpisode: (SeriesEpisode) -> Unit = { ep ->
        if (onPlayEpisode != null) {
            onPlayEpisode(ep)
        } else {
            val epChannel = channel.copy(
                name = "${channel.name} - S${ep.seasonNumber}E${ep.episodeNumber} - ${ep.title}",
                url = ep.url,
                contentType = StreamContentType.SERIES,
                seriesId = 0,
                streamId = ep.id.toIntOrNull() ?: ep.episodeNumber,
                containerExtension = ep.containerExtension.ifBlank { "mp4" }
            )
            onPlay(epChannel)
        }
    }

    // [1] Fix "Play Now" for Series:
    val handlePlayNowClick: () -> Unit = {
        if (channel.contentType == StreamContentType.SERIES) {
            val targetEpisode = lastWatchedEpisodePair?.first ?: seriesEpisodes.firstOrNull()
            if (targetEpisode != null) {
                playSpecificEpisode(targetEpisode)
            } else {
                Toast.makeText(
                    context,
                    if (isLoadingEpisodes) "⏳ جارٍ تحميل حلقات المسلسل، يرجى الانتظار..." else "⚠️ لم يتم العثور على حلقات متاحة حالياً",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            onPlay(channel)
        }
    }

    var showRatingDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var pendingEpisodeForDownload by remember { mutableStateOf<SeriesEpisode?>(null) }

    val seasonsMap = remember(seriesEpisodes) {
        seriesEpisodes.groupBy { it.seasonNumber.coerceAtLeast(1) }.toSortedMap()
    }
    var selectedSeason by remember(seasonsMap, lastWatchedEpisodePair) {
        mutableStateOf(
            lastWatchedEpisodePair?.first?.seasonNumber
                ?: seasonsMap.keys.firstOrNull()
                ?: 1
        )
    }

    val playBtnFocus = remember { FocusRequester() }
    LaunchedEffect(channel.stableKey) {
        for (i in 1..5) {
            delay(35L)
            if (runCatching { playBtnFocus.requestFocus() }.isSuccess) break
        }
    }

    val displayTitle = tmdbDetails?.title?.takeIf { it.isNotBlank() } ?: meta.cleanTitle
    val displayYear = tmdbDetails?.releaseYear?.takeIf { it.isNotBlank() } ?: meta.releaseYear
    val displayRating = tmdbDetails?.rating?.takeIf { it.isNotBlank() } ?: meta.imdbRating
    val displayPoster = tmdbDetails?.posterUrl?.takeIf { it.isNotBlank() } ?: channel.logo.orEmpty()
    val displayOverview = tmdbDetails?.overview?.takeIf { it.isNotBlank() } ?: meta.plot
    val displayGenres = tmdbDetails?.genres?.takeIf { it.isNotEmpty() } ?: meta.genres

    val displayCast: List<CastMemberInfo> = remember(tmdbDetails, meta.cast) {
        val tmdbCast = tmdbDetails?.cast.orEmpty()
        if (tmdbCast.isNotEmpty()) {
            val mutedColors = listOf(0xFF0B3B36, 0xFF14B8A6, 0xFF1E293B, 0xFF334155)
            tmdbCast.mapIndexed { idx, c ->
                CastMemberInfo(
                    name = c.name,
                    role = c.character,
                    initials = c.name.trim().take(1).uppercase(),
                    accentHex = mutedColors[idx % mutedColors.size],
                    profileUrl = c.profileUrl
                )
            }
        } else {
            meta.cast
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = ProSpacing.s48)
        ) {
            // 1. Top Header: Poster + Title/Year/IMDb Badges + Overview + Clean White/Dark Gray Action Buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                SurfaceDark,
                                BackgroundBlack
                            )
                        )
                    )
                    .padding(horizontal = if (isPortraitPhone) ProSpacing.lg else ProSpacing.xxl, vertical = ProSpacing.xl)
            ) {
                if (isPortraitPhone) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ProSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceDark)
                                    .border(1.dp, ProBorder, CircleShape)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = OnBackground)
                            }
                            Text(
                                text = "تفاصيل العرض",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(164.dp)
                                .height(244.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceDark)
                                .border(1.5.dp, MutedTealBorder.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        ) {
                            ChannelAvatar(
                                name = displayTitle,
                                logoUrl = displayPoster,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 16.dp,
                                contentScale = ContentScale.Crop,
                                isPoster = true
                            )
                        }

                        DetailsMetadataAndActionsBlock(
                            channel = channel,
                            displayTitle = displayTitle,
                            displayYear = displayYear,
                            displayRating = displayRating,
                            ageRating = meta.ageRating,
                            durationLabel = meta.durationLabel,
                            displayGenres = displayGenres,
                            displayOverview = displayOverview,
                            isLoadingTmdb = isLoadingTmdb,
                            inWatchlist = inWatchlist,
                            isFavorite = isFavorite,
                            currentUserRating = currentUserRating,
                            playBtnFocus = playBtnFocus,
                            onPlay = handlePlayNowClick,
                            onDownloadClick = {
                                pendingEpisodeForDownload = null
                                showDownloadQualityDialog = true
                            },
                            onToggleWatchlist = { ProfileStorageManager.toggleWatchlist(channel) },
                            onToggleFavorite = { onToggleFavorite(channel) },
                            onOpenTrailer = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(meta.youtubeTrailerSearchUrl))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                            },
                            onOpenRating = { showRatingDialog = true },
                            onBack = onBack,
                            isPortraitPhone = true
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(ProSpacing.xxl)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(184.dp)
                                .height(276.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceDark)
                                .border(1.5.dp, MutedTealBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        ) {
                            ChannelAvatar(
                                name = displayTitle,
                                logoUrl = displayPoster,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 16.dp,
                                contentScale = ContentScale.Crop,
                                isPoster = true
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            DetailsMetadataAndActionsBlock(
                                channel = channel,
                                displayTitle = displayTitle,
                                displayYear = displayYear,
                                displayRating = displayRating,
                                ageRating = meta.ageRating,
                                durationLabel = meta.durationLabel,
                                displayGenres = displayGenres,
                                displayOverview = displayOverview,
                                isLoadingTmdb = isLoadingTmdb,
                                inWatchlist = inWatchlist,
                                isFavorite = isFavorite,
                                currentUserRating = currentUserRating,
                                playBtnFocus = playBtnFocus,
                                onPlay = handlePlayNowClick,
                                onDownloadClick = {
                                    pendingEpisodeForDownload = null
                                    showDownloadQualityDialog = true
                                },
                                onToggleWatchlist = { ProfileStorageManager.toggleWatchlist(channel) },
                                onToggleFavorite = { onToggleFavorite(channel) },
                                onOpenTrailer = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(meta.youtubeTrailerSearchUrl))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                },
                                onOpenRating = { showRatingDialog = true },
                                onBack = onBack,
                                isPortraitPhone = false
                            )
                        }
                    }
                }
            }

            // [2] Hide Fake Cast: Only show Cast section when real cast data exists
            if (displayCast.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isPortraitPhone) ProSpacing.lg else ProSpacing.xxl, vertical = ProSpacing.md)
                ) {
                    Text(
                        text = "🎭 طاقم التمثيل والنجوم (Cast)",
                        color = OnBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(ProSpacing.md))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(ProSpacing.lg),
                        contentPadding = PaddingValues(end = ProSpacing.lg)
                    ) {
                        itemsIndexed(displayCast) { _, actor ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceDark,
                                border = BorderStroke(1.dp, ProBorder),
                                modifier = Modifier.width(150.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MutedDarkTeal)
                                            .border(1.5.dp, MutedTealBorder.copy(alpha = 0.6f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (actor.profileUrl.isNotBlank()) {
                                            ChannelAvatar(
                                                name = actor.name,
                                                logoUrl = actor.profileUrl,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape),
                                                cornerRadius = 32.dp,
                                                contentScale = ContentScale.Crop,
                                                isPoster = true
                                            )
                                        } else {
                                            Text(
                                                text = actor.initials,
                                                color = OnBackground,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                    Text(
                                        text = actor.name,
                                        color = OnBackground,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = actor.role,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Series Episodes Section (قسم الحلقات للمسلسلات)
            if (channel.contentType == StreamContentType.SERIES) {
                Spacer(modifier = Modifier.height(ProSpacing.lg))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isPortraitPhone) ProSpacing.lg else ProSpacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(ProSpacing.md)
                ) {
                    Text(
                        text = "🎞️ المواسم والحلقات (${seriesEpisodes.size} حلقة)",
                        color = OnBackground,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    if (isLoadingEpisodes) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MutedTealBorder,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "جارٍ تحميل قائمة المواسم والحلقات...",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (seasonsMap.isNotEmpty()) {
                        // Season Tabs with MutedDarkTeal active background
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val seasonKeys = seasonsMap.keys.toList()
                            itemsIndexed(seasonKeys) { _, sNum ->
                                val isSelected = sNum == selectedSeason
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (isSelected) MutedDarkTeal else SurfaceDark,
                                    border = BorderStroke(1.dp, if (isSelected) MutedTealBorder else ProBorder),
                                    modifier = Modifier.clickable { selectedSeason = sNum }
                                ) {
                                    Text(
                                        text = "الموسم $sNum (${seasonsMap[sNum]?.size ?: 0})",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // Episodes List
                        val activeEpisodes = seasonsMap[selectedSeason].orEmpty()
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            activeEpisodes.take(100).forEach { ep ->
                                val epKey = "${channel.stableKey}_S${ep.seasonNumber}_E${ep.episodeNumber}"
                                val epDl = OfflineDownloadsManager.findDownloadForChannelOrUrl(epKey, ep.url)
                                val epProg = vodWatchProgressMap[epKey]
                                    ?: vodWatchProgressMap[ep.url.trim()]
                                    ?: vodWatchProgressMap.values.firstOrNull { it.resolvedKey == ep.url.trim() }

                                DetailsEpisodeRowItem(
                                    episode = ep,
                                    posterUrl = displayPoster,
                                    downloadItem = epDl,
                                    watchProgress = epProg,
                                    onPlayEpisode = { playSpecificEpisode(ep) },
                                    onDownloadEpisode = {
                                        if (epDl?.status == DownloadItemStatus.PAUSED) {
                                            OfflineDownloadsManager.resumeDownload(epDl.id)
                                        } else {
                                            pendingEpisodeForDownload = ep
                                            showDownloadQualityDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, ProBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "اضغط «تشغيل الآن» لفتح وبدء تشغيل حلقات المسلسل.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // 4. Similar Titles Carousel
            Spacer(modifier = Modifier.height(ProSpacing.xxl))
            val similarPosters = remember(similarChannels, channel.stableKey) {
                similarChannels
                    .filter { it.stableKey != channel.stableKey }
                    .take(25)
                    .map { it.toPosterItem() }
            }
            if (similarPosters.isNotEmpty()) {
                ProContentRow(
                    title = "🎬 أعمال مشابهة قد تعجبك",
                    icon = Icons.Default.AutoAwesome,
                    items = similarPosters,
                    onItemClick = { poster ->
                        poster.sourceChannel?.let { ch ->
                            LiveProGlobalActions.openDetailsForChannel(ch)
                        }
                    }
                )
            }
        }

        // Rating Dialog
        if (showRatingDialog) {
            AlertDialog(
                onDismissRequest = { showRatingDialog = false },
                containerColor = SurfaceDark,
                title = {
                    Text("قيّم «$displayTitle»", color = OnBackground, fontWeight = FontWeight.Bold)
                },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        (1..5).forEach { stars ->
                            IconButton(
                                onClick = {
                                    ProfileStorageManager.rateContent(channel.stableKey, stars)
                                    showRatingDialog = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "$stars نجوم",
                                    tint = if (stars <= currentUserRating) ImdbGold else TextSecondary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRatingDialog = false }) {
                        Text("إغلاق", color = MutedTealBorder)
                    }
                }
            )
        }

        // Download Quality Picker Dialog
        if (showDownloadQualityDialog) {
            val targetEp = pendingEpisodeForDownload
            val dialogTitle = if (targetEp != null) {
                "$displayTitle - م${targetEp.seasonNumber} ح${targetEp.episodeNumber}"
            } else {
                displayTitle
            }
            DownloadQualityPickerDialog(
                channelTitle = dialogTitle,
                onSelectQuality = { qLabel ->
                    OfflineDownloadsManager.enqueueDownload(
                        context = context,
                        channel = channel,
                        qualityLabel = qLabel,
                        episode = targetEp
                    )
                    pendingEpisodeForDownload = null
                    showDownloadQualityDialog = false
                },
                onDismiss = {
                    pendingEpisodeForDownload = null
                    showDownloadQualityDialog = false
                }
            )
        }
    }
}

@Composable
private fun DetailsMetadataAndActionsBlock(
    channel: TvChannel,
    displayTitle: String,
    displayYear: String,
    displayRating: String,
    ageRating: String,
    durationLabel: String,
    displayGenres: List<String>,
    displayOverview: String,
    isLoadingTmdb: Boolean,
    inWatchlist: Boolean,
    isFavorite: Boolean,
    currentUserRating: Int,
    playBtnFocus: FocusRequester,
    onPlay: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenTrailer: () -> Unit,
    onOpenRating: () -> Unit,
    onBack: () -> Unit,
    isPortraitPhone: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isPortraitPhone) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(ProSpacing.sm)
    ) {
        // Badges Row: IMDb Rating + Release Year + Age Rating + Quality Badge
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, ImdbGold.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "IMDb ★ $displayRating",
                        color = ImdbGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, ProBorder)
                ) {
                    Text(
                        text = displayYear,
                        color = OnBackground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, ProBorder)
                ) {
                    Text(
                        text = ageRating,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MutedDarkTeal,
                    border = BorderStroke(1.dp, MutedTealBorder.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = durationLabel,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Title
        Text(
            text = displayTitle,
            color = OnBackground,
            fontSize = if (isPortraitPhone) 24.sp else 30.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isPortraitPhone) TextAlign.Center else TextAlign.Start
        )

        // Genres Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(displayGenres) { _, g ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, ProBorder)
                ) {
                    Text(
                        text = g,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Overview (قصة العمل)
        if (isLoadingTmdb) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    color = MutedTealBorder,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "جارٍ جلب قصة العمل والتفاصيل من TMDB...",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Text(
                text = displayOverview,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isPortraitPhone) TextAlign.Center else TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // [3] Remove Flashy Colors from Action Buttons:
        // "Play Now" button is clean White (#FFFFFF) with Black text.
        // Download/Favorite/Watchlist buttons are Dark Gray (#121212) with subtle borders.
        val movieDlItem = OfflineDownloadsManager.findDownloadForChannelOrUrl(channel.stableKey, channel.url)
        val dlBtnText = when (movieDlItem?.status) {
            DownloadItemStatus.COMPLETED -> "محمّل ✓ (Offline)"
            DownloadItemStatus.DOWNLOADING -> "جارٍ التحميل ${(movieDlItem.progress * 100).toInt()}%"
            DownloadItemStatus.PAUSED -> "استئناف التحميل"
            else -> "تحميل"
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                DetailsActionButton(
                    text = "تشغيل الآن",
                    icon = Icons.Default.PlayArrow,
                    isPrimaryWhite = true,
                    modifier = Modifier.focusRequester(playBtnFocus),
                    onClick = onPlay
                )
            }

            if (channel.contentType != StreamContentType.LIVE_TV) {
                item {
                    DetailsActionButton(
                        text = dlBtnText,
                        icon = if (movieDlItem?.status == DownloadItemStatus.COMPLETED) Icons.Default.DownloadDone else Icons.Outlined.Download,
                        onClick = {
                            if (movieDlItem?.status == DownloadItemStatus.PAUSED) {
                                OfflineDownloadsManager.resumeDownload(movieDlItem.id)
                            } else {
                                onDownloadClick()
                            }
                        }
                    )
                }
            }

            item {
                DetailsActionButton(
                    text = if (inWatchlist) "✓ في قائمتي" else "+ قائمتي",
                    icon = if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                    onClick = onToggleWatchlist
                )
            }

            item {
                DetailsActionButton(
                    text = if (isFavorite) "♥ المفضلة" else "♡ المفضلة",
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    onClick = onToggleFavorite
                )
            }

            item {
                DetailsActionButton(
                    text = "التريلر",
                    icon = Icons.Default.OndemandVideo,
                    onClick = onOpenTrailer
                )
            }

            item {
                DetailsActionButton(
                    text = if (currentUserRating > 0) "تقييمك ★$currentUserRating" else "تقييم ★",
                    icon = Icons.Default.StarRate,
                    onClick = onOpenRating
                )
            }

            if (!isPortraitPhone) {
                item {
                    DetailsActionButton(
                        text = "رجوع",
                        icon = Icons.Default.ArrowBack,
                        onClick = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimaryWhite: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) ProDimens.FocusScale else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "detailsBtnScale"
    )

    val bgColor = when {
        isPrimaryWhite -> Color(0xFFFFFFFF)
        focused -> MutedDarkTeal
        else -> SurfaceDark
    }
    val contentColor = when {
        isPrimaryWhite -> Color(0xFF000000)
        focused -> Color(0xFFFFFFFF)
        else -> OnSurfaceSecondary
    }
    val borderColor = when {
        focused -> MutedTealBorder
        isPrimaryWhite -> Color(0xFFFFFFFF)
        else -> ProBorder
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (focused) 2.dp else 1.dp, borderColor),
        shadowElevation = if (focused) 8.dp else 2.dp,
        modifier = modifier
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
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = text,
                color = contentColor,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun DetailsEpisodeRowItem(
    episode: SeriesEpisode,
    posterUrl: String?,
    downloadItem: OfflineDownloadItem?,
    watchProgress: VodWatchProgress? = null,
    onPlayEpisode: () -> Unit,
    onDownloadEpisode: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (focused) MutedDarkTeal else SurfaceDark,
        border = BorderStroke(
            width = if (focused) 1.8.dp else 1.dp,
            color = if (focused) MutedTealBorder else ProBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onKeyEvent { ev ->
                if (isDpadSelectKeyEvent(ev)) {
                    onPlayEpisode()
                    true
                } else false
            }
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null) { onPlayEpisode() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BackgroundBlack)
                ) {
                    ChannelAvatar(
                        name = episode.title,
                        logoUrl = posterUrl,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 10.dp,
                        contentScale = ContentScale.Crop,
                        isPoster = true
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = "تشغيل الحلقة",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "الحلقة ${episode.episodeNumber} — ${episode.title.ifBlank { "حلقة ${episode.episodeNumber}" }}",
                        color = OnBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("الموسم ${episode.seasonNumber}")
                            if (episode.durationLabel.isNotBlank()) {
                                append(" • ${episode.durationLabel}")
                            }
                            if (watchProgress != null && watchProgress.hasResumePoint) {
                                append(" • شوهد ${(watchProgress.progressFraction * 100).toInt()}% (${watchProgress.formattedPosition})")
                            }
                            if (downloadItem != null) {
                                append(" • ${downloadItem.status.labelAr} (${(downloadItem.progress * 100).toInt()}%)")
                            }
                        },
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        modifier = Modifier.clickable { onPlayEpisode() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "تشغيل",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (watchProgress != null && watchProgress.hasResumePoint) "استئناف" else "تشغيل",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, ProBorder),
                        modifier = Modifier.clickable { onDownloadEpisode() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (downloadItem?.status == DownloadItemStatus.COMPLETED) Icons.Default.DownloadDone else Icons.Outlined.Download,
                                contentDescription = "تحميل الحلقة",
                                tint = OnSurfaceSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when (downloadItem?.status) {
                                    DownloadItemStatus.COMPLETED -> "محمّل"
                                    DownloadItemStatus.DOWNLOADING -> "${(downloadItem.progress * 100).toInt()}%"
                                    else -> "تحميل"
                                },
                                color = OnSurfaceSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            if (watchProgress != null && watchProgress.positionMs >= 3_000L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF262626))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(watchProgress.progressFraction.coerceIn(0.04f, 1f))
                            .background(MutedTealBorder)
                    )
                }
            }
        }
    }
}
