package com.tvflux.pro

import android.view.KeyEvent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// Professional Production Home Components — LIVE PRO v24.3
// Supports RTL + TV Remote D-Pad Focus + Phone & TV Responsive Layouts
// ============================================================================

internal fun isDpadSelectKeyEvent(ev: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || ev.nativeKeyEvent.repeatCount != 0) return false
    val code = ev.nativeKeyEvent.keyCode
    return code == KeyEvent.KEYCODE_DPAD_CENTER ||
        code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        code == KeyEvent.KEYCODE_BUTTON_A
}

// ---------- Top Status Bar ----------
@Composable
fun ProTopStatusBar(
    deviceCode: String,
    isVpnConnected: Boolean,
    currentDate: String,
    onRefreshClick: () -> Unit,
    onDeviceCodeClick: () -> Unit,
    modifier: Modifier = Modifier,
    playlistsCount: Int = 0,
    expiryText: String = "",
    networkSpeedLabel: String = "",
    showScreenCode: Boolean = true,
    isLoading: Boolean = false,
    onPlaylistsClick: (() -> Unit)? = null,
    onVpnClick: (() -> Unit)? = null,
    onToggleScreenCodeClick: (() -> Unit)? = null
) {
    val devInteraction = remember { MutableInteractionSource() }
    val devFocused by devInteraction.collectIsFocusedAsState()
    val eyeInteraction = remember { MutableInteractionSource() }
    val eyeFocused by eyeInteraction.collectIsFocusedAsState()
    val refreshInteraction = remember { MutableInteractionSource() }
    val refreshFocused by refreshInteraction.collectIsFocusedAsState()
    val plInteraction = remember { MutableInteractionSource() }
    val plFocused by plInteraction.collectIsFocusedAsState()
    val vpnInteraction = remember { MutableInteractionSource() }
    val vpnFocused by vpnInteraction.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ProSurface.copy(alpha = 0.94f))
            .border(width = 0.5.dp, color = ProBorder.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Start side: Device Code + Eye toggle + Refresh + Optional Playlists Chip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Device Code Chip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (devFocused) ProPrimaryDark.copy(alpha = 0.35f) else ProSurfaceElevated,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = if (devFocused) 2.dp else 1.dp,
                    brush = if (devFocused) {
                        Brush.horizontalGradient(listOf(ProFocusRing, ProPrimary))
                    } else {
                        Brush.horizontalGradient(listOf(ProPrimary.copy(0.45f), ProSecondary.copy(0.45f)))
                    }
                ),
                modifier = Modifier
                    .scale(if (devFocused) 1.05f else 1f)
                    .onKeyEvent { ev ->
                        if (isDpadSelectKeyEvent(ev)) {
                            onDeviceCodeClick()
                            true
                        } else false
                    }
                    .focusable(interactionSource = devInteraction)
                    .clickable(interactionSource = devInteraction, indication = null) { onDeviceCodeClick() }
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = if (devFocused) ProFocusRing else ProPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (showScreenCode) deviceCode.ifBlank { "DEV-••••-••••" } else "DEV-••••-••••",
                            color = ProTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            if (onToggleScreenCodeClick != null) {
                Surface(
                    shape = CircleShape,
                    color = if (eyeFocused) ProPrimaryDark.copy(alpha = 0.35f) else ProSurfaceElevated,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = if (eyeFocused) 2.dp else 1.dp,
                        brush = Brush.linearGradient(
                            if (eyeFocused) listOf(ProFocusRing, ProPrimary) else listOf(ProBorder, ProBorder)
                        )
                    ),
                    modifier = Modifier
                        .size(32.dp)
                        .scale(if (eyeFocused) 1.08f else 1f)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onToggleScreenCodeClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = eyeInteraction)
                        .clickable(interactionSource = eyeInteraction, indication = null) { onToggleScreenCodeClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (showScreenCode) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "إظهار/إخفاء رمز الشاشة",
                            tint = if (eyeFocused) ProFocusRing else ProTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Refresh Button
            Surface(
                shape = CircleShape,
                color = if (refreshFocused) ProPrimaryDark.copy(alpha = 0.35f) else ProSurfaceElevated,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = if (refreshFocused) 2.dp else 1.dp,
                    brush = Brush.linearGradient(
                        if (refreshFocused) listOf(ProFocusRing, ProPrimary) else listOf(ProBorder, ProBorder)
                    )
                ),
                modifier = Modifier
                    .size(34.dp)
                    .scale(if (refreshFocused) 1.08f else 1f)
                    .onKeyEvent { ev ->
                        if (isDpadSelectKeyEvent(ev)) {
                            onRefreshClick()
                            true
                        } else false
                    }
                    .focusable(interactionSource = refreshInteraction)
                    .clickable(interactionSource = refreshInteraction, indication = null) { onRefreshClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = ProPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث",
                            tint = if (refreshFocused) ProFocusRing else ProTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Playlists / Server Switcher Chip (if callback provided)
            if (onPlaylistsClick != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (plFocused) ProPrimaryDark.copy(alpha = 0.35f) else ProSurfaceElevated,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = if (plFocused) 2.dp else 1.dp,
                        brush = Brush.linearGradient(
                            if (plFocused) listOf(ProFocusRing, ProPrimary) else listOf(ProBorder, ProBorder)
                        )
                    ),
                    modifier = Modifier
                        .scale(if (plFocused) 1.05f else 1f)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onPlaylistsClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = plInteraction)
                        .clickable(interactionSource = plInteraction, indication = null) { onPlaylistsClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (plFocused) ProFocusRing else ProSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        val plLabel = buildString {
                            append("القوائم ($playlistsCount)")
                            if (expiryText.isNotBlank()) {
                                append(" • $expiryText")
                            }
                        }
                        Text(
                            text = plLabel,
                            color = ProTextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // End side: Network Speed + Date + VPN
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (networkSpeedLabel.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ProSurfaceElevated,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(ProBorder, ProBorder))
                    )
                ) {
                    Text(
                        text = "⬇ $networkSpeedLabel",
                        color = ProSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            if (currentDate.isNotBlank()) {
                Text(
                    text = currentDate,
                    color = ProTextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            // VPN Status Chip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (vpnFocused) {
                    ProPrimaryDark.copy(alpha = 0.35f)
                } else if (isVpnConnected) {
                    ProSuccess.copy(alpha = 0.15f)
                } else {
                    ProError.copy(alpha = 0.12f)
                },
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = if (vpnFocused) 2.dp else 1.dp,
                    brush = Brush.linearGradient(
                        if (vpnFocused) {
                            listOf(ProFocusRing, ProPrimary)
                        } else {
                            listOf(
                                if (isVpnConnected) ProSuccess else ProError,
                                if (isVpnConnected) ProSuccess.copy(0.6f) else ProError.copy(0.6f)
                            )
                        }
                    )
                ),
                modifier = if (onVpnClick != null) {
                    Modifier
                        .scale(if (vpnFocused) 1.05f else 1f)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onVpnClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = vpnInteraction)
                        .clickable(interactionSource = vpnInteraction, indication = null) { onVpnClick() }
                } else Modifier
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isVpnConnected) ProSuccess else ProError)
                    )
                    Text(
                        text = if (isVpnConnected) "VPN: متصل" else "VPN: غير متصل",
                        color = if (isVpnConnected) ProSuccess else ProError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ---------- Category Quick Cards (4 main sections) ----------
@Composable
fun ProCategoryCardsRow(
    onMoviesClick: () -> Unit,
    onSeriesClick: () -> Unit,
    onSportsClick: () -> Unit,
    onFeaturedClick: () -> Unit,
    modifier: Modifier = Modifier,
    firstCardFocusRequester: FocusRequester? = null,
    moviesCount: Int = 0,
    seriesCount: Int = 0,
    liveCount: Int = 0
) {
    val moviesSub = if (moviesCount > 0) "$moviesCount فيلم • أحدث الأفلام" else "أحدث وأقوى الأفلام"
    val seriesSub = if (seriesCount > 0) "$seriesCount مسلسل • مسلسلات عالمية وعربية" else "مسلسلات عالمية وعربية"
    val featuredSub = if (liveCount > 0) "$liveCount قناة • أقوى القنوات" else "أقوى القنوات العربية"

    val cards = listOf(
        CategoryCardData("قنوات مميزة", featuredSub, Icons.Default.LiveTv, ProPrimary, onFeaturedClick),
        CategoryCardData("الأفلام", moviesSub, Icons.Default.Movie, ProSecondary, onMoviesClick),
        CategoryCardData("المسلسلات", seriesSub, Icons.Default.VideoLibrary, ProPrimaryDark, onSeriesClick),
        CategoryCardData("الرياضة", "مباريات حصرية مباشرة", Icons.Default.SportsSoccer, ProSuccess, onSportsClick)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        cards.forEachIndexed { index, card ->
            val cardModifier = if (index == 0 && firstCardFocusRequester != null) {
                Modifier
                    .weight(1f)
                    .focusRequester(firstCardFocusRequester)
            } else {
                Modifier.weight(1f)
            }
            ProCategoryCard(
                data = card,
                modifier = cardModifier
            )
        }
    }
}

private data class CategoryCardData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun ProCategoryCard(
    data: CategoryCardData,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(180),
        label = "catScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .height(92.dp)
            .onKeyEvent { ev ->
                if (isDpadSelectKeyEvent(ev)) {
                    data.onClick()
                    true
                } else false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { data.onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isFocused) ProSurfaceElevated else ProCard,
        border = if (isFocused) {
            ButtonDefaults.outlinedButtonBorder.copy(
                width = 2.5.dp,
                brush = Brush.linearGradient(listOf(ProFocusRing, data.accent))
            )
        } else {
            ButtonDefaults.outlinedButtonBorder.copy(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(ProBorder, ProBorder))
            )
        },
        shadowElevation = if (isFocused) 8.dp else 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            data.accent.copy(alpha = if (isFocused) 0.28f else 0.18f),
                            Color.Transparent
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = if (isFocused) ProFocusRing else data.accent,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = data.title,
                        color = ProTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = data.subtitle,
                        color = if (isFocused) ProTextPrimary.copy(alpha = 0.85f) else ProTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---------- Content Section Header ----------
@Composable
fun ProSectionHeader(
    title: String,
    icon: ImageVector? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val actionInteraction = remember { MutableInteractionSource() }
    val actionFocused by actionInteraction.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ProPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                color = ProTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (actionText != null && onActionClick != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (actionFocused) ProPrimary.copy(alpha = 0.20f) else Color.Transparent,
                border = if (actionFocused) {
                    ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(ProFocusRing, ProPrimary))
                    )
                } else null,
                modifier = Modifier
                    .onKeyEvent { ev ->
                        if (isDpadSelectKeyEvent(ev)) {
                            onActionClick()
                            true
                        } else false
                    }
                    .focusable(interactionSource = actionInteraction)
                    .clickable(interactionSource = actionInteraction, indication = null) { onActionClick() }
            ) {
                Text(
                    text = actionText,
                    color = if (actionFocused) ProFocusRing else ProPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

// ---------- Poster Card (Movies / Series / Live Channels) ----------
@Composable
fun ProPosterCard(
    title: String,
    imageUrl: String? = null,
    badge: String? = null,          // "4K", "HD", "NEW"
    subtitle: String? = null,       // e.g. "S01 • 12 حلقة"
    isLive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(200),
        label = "posterScale"
    )

    Column(
        modifier = modifier
            .width(140.dp)
            .scale(scale)
            .onKeyEvent { ev ->
                if (isDpadSelectKeyEvent(ev)) {
                    onClick()
                    true
                } else false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(ProSurfaceElevated)
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(listOf(ProFocusRing, ProPrimary)),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Modifier.border(
                            width = 1.dp,
                            color = ProBorder.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                )
        ) {
            // Real Poster / Channel Logo with automatic fallback
            if (!imageUrl.isNullOrBlank()) {
                ChannelAvatar(
                    name = title,
                    logoUrl = imageUrl,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 12.dp,
                    contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                    innerPadding = if (isLive) 10.dp else 0.dp,
                    isPoster = !isLive
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(ProSurfaceElevated, ProCard)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLive) Icons.Default.LiveTv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = ProTextTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Badge (top-start)
            if (badge != null) {
                val badgeColor = when (badge.uppercase()) {
                    "4K", "UHD" -> Pro4KBadge
                    "HD", "FHD" -> ProHDBadge
                    "NEW", "جديد" -> ProWarning
                    else -> ProPrimary
                }
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = badgeColor.copy(alpha = 0.92f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = badge,
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // Live indicator
            if (isLive) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    color = ProLiveBadge,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom gradient + title overlay when focused
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            color = if (isFocused) ProTextPrimary else ProTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                color = ProTextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------- Horizontal Content Row ----------
@Composable
fun ProContentRow(
    title: String,
    icon: ImageVector? = null,
    items: List<PosterItem>,
    onItemClick: (PosterItem) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ProSectionHeader(
            title = title,
            icon = icon,
            actionText = if (onSeeAllClick != null) "عرض الكل" else null,
            onActionClick = onSeeAllClick
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items, key = { idx, item -> "${idx}_${item.id}" }) { _, item ->
                ProPosterCard(
                    title = item.title,
                    imageUrl = item.imageUrl,
                    badge = item.badge,
                    subtitle = item.subtitle,
                    isLive = item.isLive,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

// Simple data class for posters (map from your TvChannel)
data class PosterItem(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
    val badge: String? = null,
    val subtitle: String? = null,
    val isLive: Boolean = false,
    val streamUrl: String = "",
    val sourceChannel: TvChannel? = null
)

/**
 * تحويل TvChannel → PosterItem (كما هو محدد في README_INTEGRATION.md)
 */
fun TvChannel.toPosterItem(): PosterItem = PosterItem(
    id = this.stableKey,
    title = this.name,
    imageUrl = this.logo,
    badge = when {
        name.contains("4K", true) || name.contains("UHD", true) || group.contains("4K", true) -> "4K"
        name.contains("FHD", true) || name.contains("1080", true) || name.contains("HD", true) -> "HD"
        rating.isNotBlank() -> "★ $rating"
        else -> null
    },
    subtitle = this.group.takeIf { it.isNotBlank() },
    isLive = (this.contentType == StreamContentType.LIVE_TV),
    streamUrl = this.url,
    sourceChannel = this
)

// ---------- Side Navigation Rail (Professional) ----------
@Composable
fun ProSideNavigationRail(
    activeTab: MainTab,
    compact: Boolean = false,
    onSelectTab: (MainTab) -> Unit,
    onExitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tabs = remember {
        listOf(
            MainTab.HOME to (Icons.Default.Home to "الرئيسية"),
            MainTab.LIVE to (Icons.Default.LiveTv to "مباشر"),
            MainTab.BOUQUETS to (Icons.Default.Folder to "الباقات"),
            MainTab.MOVIES to (Icons.Default.Movie to "أفلام"),
            MainTab.SERIES to (Icons.Default.VideoLibrary to "مسلسلات"),
            MainTab.XTREAM to (Icons.Default.CloudDownload to "القوائم"),
            MainTab.FAVORITES to (Icons.Default.Favorite to "مفضلة"),
            MainTab.SETTINGS to (Icons.Default.Settings to "إعدادات")
        )
    }
    val tabFocusRequesters = remember { tabs.associate { it.first to FocusRequester() } }
    val exitFocusRequester = remember { FocusRequester() }
    val exitInteraction = remember { MutableInteractionSource() }
    val exitFocused by exitInteraction.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(if (compact) 64.dp else 88.dp)
            .background(ProSurface)
            .border(1.dp, ProBorder),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo / Brand
        Box(
            modifier = Modifier
                .padding(top = if (compact) 10.dp else 14.dp, bottom = if (compact) 6.dp else 10.dp)
                .size(if (compact) 36.dp else 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(listOf(ProPrimary, ProSecondary))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "LIVE PRO",
                tint = Color.White,
                modifier = Modifier.size(if (compact) 22.dp else 26.dp)
            )
        }

        // Scrollable Tabs List so all tabs fit cleanly on both TV and compact phone landscape
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(tabs, key = { _, pair -> pair.first.name }) { idx, (tab, pair) ->
                val (icon, label) = pair
                val isActive = activeTab == tab
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                val ownReq = tabFocusRequesters[tab] ?: FocusRequester()

                val bgColor = when {
                    isFocused -> ProPrimary.copy(alpha = 0.26f)
                    isActive -> ProPrimary.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
                val contentColor = when {
                    isFocused -> ProFocusRing
                    isActive -> ProPrimary
                    else -> ProTextSecondary
                }
                val railFocusMod = if (isActive) {
                    Modifier
                        .focusRequester(LiveProGlobalActions.sideNavRailFocusRequester)
                        .focusRequester(ownReq)
                } else {
                    Modifier.focusRequester(ownReq)
                }

                Column(
                    modifier = railFocusMod
                        .fillMaxWidth()
                        .padding(horizontal = if (compact) 5.dp else 8.dp, vertical = 2.dp)
                        .scale(if (isFocused) 1.06f else 1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .then(
                            if (isFocused) {
                                Modifier.border(2.dp, ProFocusRing, RoundedCornerShape(12.dp))
                            } else if (isActive) {
                                Modifier.border(1.dp, ProPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            } else Modifier
                        )
                        .onKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                when (ev.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                                    KeyEvent.KEYCODE_BUTTON_A -> {
                                        SafeClickGuard.runThrottled("PRO_TAB_CLICK", 160L) { onSelectTab(tab) }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (idx == 0) {
                                            runCatching { LiveProGlobalActions.topToolbarPrimaryFocusRequester.requestFocus() }
                                            true
                                        } else {
                                            val prevTab = tabs[idx - 1].first
                                            runCatching { tabFocusRequesters[prevTab]?.requestFocus() }
                                            true
                                        }
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (idx < tabs.lastIndex) {
                                            val nextTab = tabs[idx + 1].first
                                            runCatching { tabFocusRequesters[nextTab]?.requestFocus() }
                                            true
                                        } else {
                                            runCatching { exitFocusRequester.requestFocus() }
                                            true
                                        }
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        if (isRtl) {
                                            if (runCatching { LiveProGlobalActions.mainContentPrimaryFocusRequester.requestFocus() }.isFailure) {
                                                runCatching { focusManager.moveFocus(FocusDirection.Left) }
                                            }
                                            true
                                        } else false
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        if (!isRtl) {
                                            if (runCatching { LiveProGlobalActions.mainContentPrimaryFocusRequester.requestFocus() }.isFailure) {
                                                runCatching { focusManager.moveFocus(FocusDirection.Right) }
                                            }
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .focusable(interactionSource = interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { SafeClickGuard.runThrottled("PRO_TAB_CLICK", 160L) { onSelectTab(tab) } }
                        .padding(vertical = if (compact) 7.dp else 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = contentColor,
                        modifier = Modifier.size(if (compact) 20.dp else 23.dp)
                    )
                    if (!compact) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = label,
                            color = contentColor,
                            fontSize = 11.sp,
                            fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Exit Button
        Box(
            modifier = Modifier
                .focusRequester(exitFocusRequester)
                .padding(vertical = 8.dp, horizontal = 8.dp)
                .size(if (compact) 36.dp else 42.dp)
                .scale(if (exitFocused) 1.08f else 1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (exitFocused) ProError.copy(alpha = 0.25f) else Color.Transparent)
                .then(
                    if (exitFocused) Modifier.border(2.dp, ProError, RoundedCornerShape(12.dp)) else Modifier
                )
                .onKeyEvent { ev ->
                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                        when (ev.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                            KeyEvent.KEYCODE_BUTTON_A -> {
                                onExitClick()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                val lastTab = tabs.last().first
                                runCatching { tabFocusRequesters[lastTab]?.requestFocus() }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (isRtl) {
                                    runCatching { LiveProGlobalActions.mainContentPrimaryFocusRequester.requestFocus() }
                                    true
                                } else false
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (!isRtl) {
                                    runCatching { LiveProGlobalActions.mainContentPrimaryFocusRequester.requestFocus() }
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                }
                .focusable(interactionSource = exitInteraction)
                .clickable(interactionSource = exitInteraction, indication = null) { onExitClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = "خروج",
                tint = if (exitFocused) Color.White else ProError.copy(alpha = 0.85f),
                modifier = Modifier.size(if (compact) 20.dp else 22.dp)
            )
        }
    }
}
