package com.tvflux.pro

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================================================
// LIVE PRO v25.0 — Responsive UI Components (TV Landscape + Phone Portrait/Landscape)
// Full Multi-Language Support (AR / FR / EN) + Dynamic RTL/LTR + Scroll State Preservation
// ============================================================================

internal fun isDpadSelectKeyEvent(ev: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || ev.nativeKeyEvent.repeatCount != 0) return false
    val code = ev.nativeKeyEvent.keyCode
    return code == KeyEvent.KEYCODE_DPAD_CENTER ||
        code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        code == KeyEvent.KEYCODE_BUTTON_A
}

object ProVisualSanitizer {
    private val invalidFragments = setOf(
        "فن", "في", "لا", "...", "null", "undefined", "n/a", "test", "placeholder"
    )

    fun defaultFallbackTitle(
        lang: AppUiLanguage = AppStrings.activeLang,
        isLive: Boolean = true
    ): String {
        return if (isLive) {
            AppStrings.tr(lang, "قناة مباشرة", "Chaîne en direct", "Live Channel")
        } else {
            AppStrings.tr(lang, "محتوى مميز", "Contenu vedette", "Featured Content")
        }
    }

    fun sanitizeTitle(
        raw: String,
        fallback: String = defaultFallbackTitle(AppStrings.activeLang, isLive = true)
    ): String {
        val cleaned = raw
            .replace(Regex("^[\\s•\\-|_:\\.]+|[\\s•\\-|_:\\.]+$"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (cleaned.length <= 2 && cleaned.lowercase() in invalidFragments) {
            return fallback
        }
        if (cleaned.isBlank() || cleaned.lowercase() in invalidFragments) {
            return fallback
        }
        return cleaned
    }

    fun sanitizeSubtitle(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace(Regex("^[\\s•\\-|_:\\.]+|[\\s•\\-|_:\\.]+$"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (cleaned.length <= 2 || cleaned.lowercase() in invalidFragments) return null
        return cleaned
    }

    fun getVerifiedDeviceDate(lang: AppUiLanguage = AppStrings.activeLang): String {
        return runCatching {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            if (year < 2024 || year > 2035) {
                ""
            } else {
                val pattern = if (lang == AppUiLanguage.AR) "EEEE، d MMMM yyyy" else "EEE, d MMM yyyy"
                SimpleDateFormat(pattern, Locale(lang.code)).format(Date())
            }
        }.getOrDefault("")
    }
}

enum class ProQualityTier(
    val priority: Int,
    val badgeLabel: String,
    val playerLabel: String,
    val badgeColor: Color
) {
    UHD_4K(4, "4K", "4K UHD", Pro4KBadge),
    FHD_1080(3, "FHD", "FHD 1080p", ProFhdBadge),
    HD_720(2, "HD", "HD 720p", ProHDBadge),
    SD(1, "SD", "SD", ProSdBadge);
}

object SportsQualitySelector {
    private val sportsKeywords = listOf(
        "bein", "بي إن", "بي ان", "رياضة", "رياضيه", "رياضية",
        "sports", "sport", "ssc", "alkass", "الكأس", "أبوظبي الرياضية", "ad sports"
    )

    private val qualityStripRegex = Regex(
        "\\b(4k|uhd|2160p?|fhd|1080p?|1080i|hd|720p?|sd|480p?|360p?|low|hevc|h265|vip|raw)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isSportsOrBeinContent(name: String, group: String = ""): Boolean {
        val combined = "$name $group"
        return sportsKeywords.any { kw -> combined.contains(kw, ignoreCase = true) }
    }

    fun isSportsOrBeinChannel(channel: TvChannel): Boolean {
        return isSportsOrBeinContent(channel.name, channel.group)
    }

    fun detectQualityTier(name: String, group: String = ""): ProQualityTier {
        val text = "$name $group".uppercase(Locale.ROOT)
        return when {
            text.contains("4K") || text.contains("UHD") || text.contains("2160") -> ProQualityTier.UHD_4K
            text.contains("FHD") || text.contains("1080") || text.contains("HEVC") -> ProQualityTier.FHD_1080
            text.contains("HD") || text.contains("720") -> ProQualityTier.HD_720
            else -> ProQualityTier.SD
        }
    }

    fun normalizeChannelIdentity(name: String): String {
        return name
            .replace(qualityStripRegex, " ")
            .replace(Regex("[\\[\\]()•\\-|_:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    fun sortSportsChannelsByBestQuality(channels: List<TvChannel>): List<TvChannel> {
        if (channels.size <= 1) return channels
        return channels.sortedWith(
            compareByDescending<TvChannel> { detectQualityTier(it.name, it.group).priority }
                .thenBy { it.number }
        )
    }

    fun resolveBestQualityChannel(
        selected: TvChannel,
        allCandidates: List<TvChannel>
    ): TvChannel {
        if (selected.contentType != StreamContentType.LIVE_TV) return selected
        if (!isSportsOrBeinChannel(selected) || allCandidates.isEmpty()) return selected

        val selectedTier = detectQualityTier(selected.name, selected.group)
        if (selectedTier == ProQualityTier.UHD_4K) return selected

        val targetIdentity = normalizeChannelIdentity(selected.name)
        if (targetIdentity.isBlank()) return selected

        var bestMatch: TvChannel = selected
        var bestPriority: Int = selectedTier.priority

        for (candidate in allCandidates) {
            if (candidate.contentType != StreamContentType.LIVE_TV) continue
            if (candidate.playlistId != selected.playlistId) continue
            val candPriority = detectQualityTier(candidate.name, candidate.group).priority
            if (candPriority <= bestPriority) continue
            if (normalizeChannelIdentity(candidate.name) == targetIdentity) {
                bestMatch = candidate
                bestPriority = candPriority
                if (bestPriority == ProQualityTier.UHD_4K.priority) break
            }
        }
        return bestMatch
    }
}

// ============================================================================
// 3. Compact Fixed-Height ProTopStatusBar (56.dp Phone / 64.dp TV)
// Zero vertical black space + Responsive Portrait hiding + Capsule VPN Button + Full Localization
// ============================================================================
@Composable
fun ProTopStatusBar(
    deviceCode: String,
    isVpnConnected: Boolean,
    currentDate: String,
    onRefreshClick: () -> Unit,
    onDeviceCodeClick: () -> Unit,
    modifier: Modifier = Modifier,
    lang: AppUiLanguage = AppStrings.activeLang,
    playlistsCount: Int = 0,
    expiryText: String = "",
    networkSpeedLabel: String = "",
    showScreenCode: Boolean = true,
    isLoading: Boolean = false,
    onPlaylistsClick: (() -> Unit)? = null,
    onVpnClick: (() -> Unit)? = null,
    onToggleScreenCodeClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = { LiveProGlobalActions.activeOverlayScreen = ProOverlayScreen.SEARCH },
    onProfileClick: (() -> Unit)? = { LiveProGlobalActions.showWhoIsWatchingModal = true },
    onNotificationsClick: (() -> Unit)? = { LiveProGlobalActions.activeOverlayScreen = ProOverlayScreen.NOTIFICATIONS }
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        configuration.screenWidthDp >= 720
    val isPhone = !isTv

    val barHeight = if (isPhone) 56.dp else 68.dp
    val horizPadding = if (isPhone) 8.dp else 16.dp
    val vertPadding = if (isPhone) 4.dp else 8.dp

    val devInteraction = remember { MutableInteractionSource() }
    val devFocused by devInteraction.collectIsFocusedAsState()
    val eyeInteraction = remember { MutableInteractionSource() }
    val eyeFocused by eyeInteraction.collectIsFocusedAsState()
    val refreshInteraction = remember { MutableInteractionSource() }
    val refreshFocused by refreshInteraction.collectIsFocusedAsState()
    val plInteraction = remember { MutableInteractionSource() }
    val plFocused by plInteraction.collectIsFocusedAsState()
    val searchInteraction = remember { MutableInteractionSource() }
    val searchFocused by searchInteraction.collectIsFocusedAsState()
    val profileInteraction = remember { MutableInteractionSource() }
    val profileFocused by profileInteraction.collectIsFocusedAsState()
    val notifInteraction = remember { MutableInteractionSource() }
    val notifFocused by notifInteraction.collectIsFocusedAsState()

    val vpnInteraction = remember { MutableInteractionSource() }
    var vpnFocused by remember { mutableStateOf(false) }
    val vpnScale by animateFloatAsState(
        targetValue = if (vpnFocused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "vpnCapsuleScale"
    )

    val activeProfile = ProfileStorageManager.activeProfileState
    val unreadNotifCount = SmartNotificationsManager.unreadCountState

    val verifiedDate = remember(currentDate, lang) {
        val realDate = ProVisualSanitizer.getVerifiedDeviceDate(lang)
        realDate.ifBlank { currentDate.takeIf { !it.contains("10-11-2026") }.orEmpty() }
    }

    val effectiveVpnClick: () -> Unit = {
        if (onVpnClick != null) {
            onVpnClick()
        } else {
            LiveProGlobalActions.onOpenVpnControl()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(SurfaceDark.copy(alpha = 0.98f))
            .border(width = 1.dp, color = ProBorder.copy(alpha = 0.45f))
            .padding(horizontal = horizPadding, vertical = vertPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Start Section (Scrollable horizontally if narrow so it NEVER wraps vertically!)
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isPhone) 6.dp else 8.dp)
        ) {
            // 1. App Logo / Active Profile Pill
            if (onProfileClick != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (profileFocused) CyanDark.copy(alpha = 0.35f) else SurfaceElevated,
                    border = BorderStroke(
                        width = if (profileFocused) 2.dp else 1.dp,
                        color = if (profileFocused) CyanPrimary else VioletSecondary.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .height(if (isPhone) 34.dp else 38.dp)
                        .scale(if (profileFocused) 1.05f else 1f)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onProfileClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = profileInteraction)
                        .clickable(interactionSource = profileInteraction, indication = null) { onProfileClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (isPhone) 8.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(text = activeProfile.avatarEmoji, fontSize = if (isPhone) 12.sp else 14.sp)
                        Text(
                            text = if (isPortrait) APP_BRAND_NAME else activeProfile.name,
                            color = TextPrimary,
                            fontSize = if (isPhone) 11.sp else 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 2. Pill-Shape Search Button (Localized AR/FR/EN)
            if (onSearchClick != null) {
                val searchHint = if (isPortrait) {
                    AppStrings.tr(lang, "بحث...", "Rechercher...", "Search...")
                } else {
                    AppStrings.tr(lang, "ابحث عن قناة، فيلم...", "Rechercher chaîne, film...", "Search channel, movie...")
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (searchFocused) CyanDark.copy(alpha = 0.35f) else SurfaceElevated,
                    border = BorderStroke(
                        width = if (searchFocused) 2.dp else 1.dp,
                        color = if (searchFocused) CyanPrimary else ProBorder
                    ),
                    modifier = Modifier
                        .height(if (isPhone) 34.dp else 38.dp)
                        .scale(if (searchFocused) 1.05f else 1f)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onSearchClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = searchInteraction)
                        .clickable(interactionSource = searchInteraction, indication = null) { onSearchClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (isPhone) 10.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = AppStrings.tr(lang, "بحث", "Recherche", "Search"),
                            tint = if (searchFocused) CyanPrimary else TextSecondary,
                            modifier = Modifier.size(if (isPhone) 15.dp else 16.dp)
                        )
                        Text(
                            text = searchHint,
                            color = if (searchFocused) TextPrimary else TextSecondary,
                            fontSize = if (isPhone) 11.sp else 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 3. Screen Code Pill (DEV-••••-••••)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (devFocused) CyanDark.copy(alpha = 0.35f) else SurfaceElevated,
                border = BorderStroke(
                    width = if (devFocused) 2.dp else 1.dp,
                    color = if (devFocused) CyanPrimary else CyanPrimary.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .height(if (isPhone) 34.dp else 38.dp)
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
                        modifier = Modifier.padding(horizontal = if (isPhone) 8.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(if (isPhone) 14.dp else 15.dp)
                        )
                        Text(
                            text = if (showScreenCode && deviceCode.isNotBlank()) deviceCode else "DEV-••••",
                            color = TextPrimary,
                            fontSize = if (isPhone) 10.5.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (onToggleScreenCodeClick != null && !isPortrait) {
                Surface(
                    shape = CircleShape,
                    color = if (eyeFocused) CyanDark.copy(alpha = 0.35f) else SurfaceElevated,
                    border = BorderStroke(1.dp, if (eyeFocused) CyanPrimary else ProBorder),
                    modifier = Modifier
                        .size(if (isPhone) 32.dp else 34.dp)
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
                            contentDescription = AppStrings.tr(lang, "إظهار أو إخفاء الكود", "Afficher ou masquer le code", "Show or hide code"),
                            tint = if (eyeFocused) CyanPrimary else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            if (!isPhone) {
                Surface(
                    shape = CircleShape,
                    color = if (refreshFocused) CyanDark.copy(alpha = 0.35f) else SurfaceElevated,
                    border = BorderStroke(1.dp, if (refreshFocused) CyanPrimary else ProBorder),
                    modifier = Modifier
                        .size(if (isPhone) 32.dp else 34.dp)
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
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = AppStrings.tr(lang, "تحديث", "Actualiser", "Refresh"),
                            tint = if (refreshFocused || isLoading) CyanPrimary else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            if (onPlaylistsClick != null && !isPortrait) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (plFocused) CyanDark.copy(alpha = 0.35f) else SurfaceElevated,
                    border = BorderStroke(1.dp, if (plFocused) CyanPrimary else ProBorder),
                    modifier = Modifier
                        .height(if (isPhone) 34.dp else 38.dp)
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
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (plFocused) CyanPrimary else VioletSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        val plLabel = buildString {
                            append("${AppStrings.tr(lang, "القوائم", "Listes", "Lists")} ($playlistsCount)")
                            if (expiryText.isNotBlank() && isTv) append(" • $expiryText")
                        }
                        Text(
                            text = plLabel,
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // End Section: Secondary telemetry (Hidden in Portrait!) + Capsule VPN Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isPhone) 6.dp else 8.dp)
        ) {
            if (onNotificationsClick != null && !isPortrait) {
                Surface(
                    shape = CircleShape,
                    color = if (notifFocused) CyanDark.copy(alpha = 0.35f) else SurfaceElevated,
                    border = BorderStroke(1.dp, if (notifFocused) CyanPrimary else ProBorder),
                    modifier = Modifier
                        .size(if (isPhone) 32.dp else 34.dp)
                        .scale(if (notifFocused) 1.08f else 1f)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onNotificationsClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = notifInteraction)
                        .clickable(interactionSource = notifInteraction, indication = null) { onNotificationsClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = AppStrings.tr(lang, "الإشعارات", "Notifications", "Notifications"),
                            tint = if (notifFocused || unreadNotifCount > 0) CyanPrimary else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        if (unreadNotifCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(LiveRed)
                            )
                        }
                    }
                }
            }

            if (!isPhone && networkSpeedLabel.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, ProBorder),
                    modifier = Modifier.height(if (isPhone) 32.dp else 36.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⬇ $networkSpeedLabel",
                            color = SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (isTv && verifiedDate.isNotBlank()) {
                Text(
                    text = verifiedDate,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (VpnProConfigFetcher.isVpnProSubscribed || WarpConfigFetcher.ENABLE_CLOUDFLARE_WARP_FALLBACK) {
                val vpnBgColor = if (isVpnConnected) {
                    SuccessGreen.copy(alpha = 0.16f)
                } else {
                    CyanDark.copy(alpha = 0.22f)
                }
                val vpnBorderColor = when {
                    vpnFocused -> CyanPrimary
                    isVpnConnected -> SuccessGreen
                    else -> CyanPrimary.copy(alpha = 0.7f)
                }
                val vpnContentColor = if (isVpnConnected) SuccessGreen else CyanPrimary
                val vpnStatusLabel = "🔒 VPN PRO"

                Surface(
                    shape = RoundedCornerShape(50),
                    color = vpnBgColor,
                    border = BorderStroke(
                        width = if (vpnFocused) 2.4.dp else 1.3.dp,
                        color = vpnBorderColor
                    ),
                    shadowElevation = if (vpnFocused) 18.dp else 4.dp,
                    modifier = Modifier
                        .height(if (isPhone) 34.dp else 38.dp)
                        .scale(vpnScale)
                        .shadow(
                            elevation = if (vpnFocused) 18.dp else 4.dp,
                            shape = RoundedCornerShape(50),
                            ambientColor = if (isVpnConnected) SuccessGreen else CyanPrimary,
                            spotColor = if (vpnFocused) CyanPrimary else SuccessGreen
                        )
                        .onFocusChanged { focusState ->
                            vpnFocused = focusState.isFocused
                        }
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                effectiveVpnClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = vpnInteraction)
                        .clickable(interactionSource = vpnInteraction, indication = null) {
                            effectiveVpnClick()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (isPhone) 10.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = vpnStatusLabel,
                            color = if (vpnFocused) TextPrimary else vpnContentColor,
                            fontSize = if (isPhone) 11.sp else 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. ⚡ Quick Access Hub (Responsive Cards for TV & Phone + Full Localization + Stable Keys)
// ============================================================================
@Composable
fun ProCategoryCardsRow(
    onMoviesClick: () -> Unit,
    onSeriesClick: () -> Unit,
    onSportsClick: () -> Unit,
    onFeaturedClick: () -> Unit,
    modifier: Modifier = Modifier,
    lang: AppUiLanguage = AppStrings.activeLang,
    rowListState: LazyListState = rememberLazyListState(),
    firstCardFocusRequester: FocusRequester? = null,
    moviesCount: Int = 0,
    seriesCount: Int = 0,
    liveCount: Int = 0,
    playlistsCount: Int = 0,
    favoritesCount: Int = 0,
    onPlaylistsClick: (() -> Unit)? = null,
    onFavoritesClick: (() -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isTvScreen = configuration.screenWidthDp >= 720 ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    val cardHeightDp = when {
        isTvScreen -> ProDimens.QuickHubHeightTv.dp
        isPortrait -> 104.dp
        else -> ProDimens.QuickHubHeightLandscape.dp
    }
    val cardWidthDp = when {
        isTvScreen -> 186.dp
        isPortrait -> 132.dp
        else -> 156.dp
    }

    val liveSub = if (liveCount > 0) {
        AppStrings.tr(lang, "$liveCount قناة مباشرة", "$liveCount chaînes en direct", "$liveCount live channels")
    } else {
        AppStrings.tr(lang, "بث فوري بأعلى جودة", "Direct haute qualité", "High-quality live stream")
    }
    val moviesSub = if (moviesCount > 0) {
        AppStrings.tr(lang, "$moviesCount فيلم سينمائي", "$moviesCount films", "$moviesCount movies")
    } else {
        AppStrings.tr(lang, "أحدث الأفلام العالمية", "Derniers films", "Latest global movies")
    }
    val seriesSub = if (seriesCount > 0) {
        AppStrings.tr(lang, "$seriesCount مسلسل متكامل", "$seriesCount séries complètes", "$seriesCount full series")
    } else {
        AppStrings.tr(lang, "جميع المواسم والحلقات", "Toutes les saisons", "All seasons & episodes")
    }
    val sportsSub = AppStrings.tr(lang, "فرز تلقائي 4K → FHD", "Tri auto 4K → FHD", "Auto-sort 4K → FHD")
    val playlistsSub = if (playlistsCount > 0) {
        AppStrings.tr(lang, "$playlistsCount قوائم نشطة", "$playlistsCount listes actives", "$playlistsCount active lists")
    } else {
        AppStrings.tr(lang, "إدارة القوائم", "Gérer les listes", "Manage playlists")
    }
    val favSub = if (favoritesCount > 0) {
        AppStrings.tr(lang, "$favoritesCount عنصر محفوظ", "$favoritesCount favoris", "$favoritesCount saved items")
    } else {
        AppStrings.tr(lang, "مفضلتك وقوائمك", "Vos favoris", "Your favorites & lists")
    }

    val cards = remember(
        lang,
        liveSub,
        moviesSub,
        seriesSub,
        sportsSub,
        playlistsSub,
        favSub,
        onFeaturedClick,
        onMoviesClick,
        onSeriesClick,
        onSportsClick,
        onPlaylistsClick,
        onFavoritesClick
    ) {
        listOf(
            CategoryCardData(
                id = "quick_card_live",
                title = AppStrings.tr(lang, "مباشر", "Direct TV", "Live TV"),
                subtitle = liveSub,
                icon = Icons.Default.LiveTv,
                accent = CyanPrimary,
                onClick = onFeaturedClick
            ),
            CategoryCardData(
                id = "quick_card_movies",
                title = AppStrings.tr(lang, "أفلام", "Films", "Movies"),
                subtitle = moviesSub,
                icon = Icons.Default.Movie,
                accent = VioletSecondary,
                onClick = onMoviesClick
            ),
            CategoryCardData(
                id = "quick_card_series",
                title = AppStrings.tr(lang, "مسلسلات", "Séries", "Series"),
                subtitle = seriesSub,
                icon = Icons.Default.VideoLibrary,
                accent = Color(0xFF38BDF8),
                onClick = onSeriesClick
            ),
            CategoryCardData(
                id = "quick_card_sports",
                title = AppStrings.tr(lang, "رياضة", "Sports", "Sports"),
                subtitle = sportsSub,
                icon = Icons.Default.SportsSoccer,
                accent = SuccessGreen,
                onClick = onSportsClick
            ),
            CategoryCardData(
                id = "quick_card_playlists",
                title = AppStrings.tr(lang, "قوائم", "Listes", "Lists"),
                subtitle = playlistsSub,
                icon = Icons.Default.CloudDownload,
                accent = WarningAmber,
                onClick = { onPlaylistsClick?.invoke() ?: onFeaturedClick() }
            ),
            CategoryCardData(
                id = "quick_card_favorites",
                title = AppStrings.tr(lang, "مفضلة", "Favoris", "Favorites"),
                subtitle = favSub,
                icon = Icons.Default.Favorite,
                accent = LiveRed,
                onClick = {
                    onFavoritesClick?.invoke() ?: run {
                        LiveProGlobalActions.activeOverlayScreen = ProOverlayScreen.MY_LIST
                    }
                }
            )
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ProSectionHeader(
            title = AppStrings.tr(lang, "⚡ الأقسام السريعة", "⚡ Accès Rapide", "⚡ Quick Access"),
            icon = Icons.Default.Bolt
        )
        LazyRow(
            state = rowListState,
            contentPadding = PaddingValues(horizontal = if (isTvScreen) ProSpacing.lg else ProSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(if (isTvScreen) ProSpacing.md else ProSpacing.sm)
        ) {
            itemsIndexed(cards, key = { _, item -> item.id }) { index, card ->
                val cardModifier = if (index == 0 && firstCardFocusRequester != null) {
                    Modifier
                        .width(cardWidthDp)
                        .height(cardHeightDp)
                        .focusRequester(firstCardFocusRequester)
                } else {
                    Modifier
                        .width(cardWidthDp)
                        .height(cardHeightDp)
                }
                ProCategoryCard(
                    data = card,
                    isCompactPhone = !isTvScreen,
                    modifier = cardModifier
                )
            }
        }
    }
}

private data class CategoryCardData(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun ProCategoryCard(
    data: CategoryCardData,
    isCompactPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) ProDimens.FocusScale else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "categoryFocusScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .onKeyEvent { ev ->
                if (isDpadSelectKeyEvent(ev)) {
                    data.onClick()
                    true
                } else false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { data.onClick() },
        shape = RoundedCornerShape(ProDimens.CardRadius.dp),
        color = if (isFocused) SurfaceElevated else SurfaceDark,
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) CyanPrimary else ProBorder.copy(alpha = 0.6f)
        ),
        shadowElevation = if (isFocused) 16.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            data.accent.copy(alpha = if (isFocused) 0.30f else 0.16f),
                            SurfaceDark.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(if (isCompactPhone) 10.dp else 12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCompactPhone) 32.dp else 40.dp)
                        .clip(CircleShape)
                        .background(data.accent.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = data.icon,
                        contentDescription = data.title,
                        tint = if (isFocused) Color.White else data.accent,
                        modifier = Modifier.size(if (isCompactPhone) 18.dp else 22.dp)
                    )
                }
                Column {
                    Text(
                        text = data.title,
                        color = TextPrimary,
                        fontSize = if (isCompactPhone) 14.sp else 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = data.subtitle,
                        color = if (isFocused) CyanPrimary else TextSecondary,
                        fontSize = if (isCompactPhone) 10.sp else 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ============================================================================
// 5. Section Header + Responsive Poster Card (115dp Phone vs 144dp TV) + ContentRow
// ============================================================================
@Composable
fun ProSectionHeader(
    title: String,
    icon: ImageVector? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.screenWidthDp >= 720 ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val actionInteraction = remember { MutableInteractionSource() }
    val actionFocused by actionInteraction.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTv) ProSpacing.lg else ProSpacing.md,
                vertical = if (isTv) ProSpacing.sm else 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ProSpacing.sm),
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(if (isTv) 20.dp else 18.dp)
                )
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = if (isTv) 19.sp else 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actionText != null && onActionClick != null) {
            Surface(
                shape = RoundedCornerShape(ProSpacing.sm),
                color = if (actionFocused) CyanPrimary.copy(alpha = 0.2f) else Color.Transparent,
                border = if (actionFocused) BorderStroke(2.dp, CyanPrimary) else null,
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
                    color = CyanPrimary,
                    fontSize = if (isTv) 13.5.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = ProSpacing.sm, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ProPosterCard(
    title: String,
    imageUrl: String? = null,
    badge: String? = null,
    subtitle: String? = null,
    isLive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lang: AppUiLanguage = AppStrings.activeLang,
    progress: Float = 0f,
    rating: String = "",
    inWatchlist: Boolean = false,
    onQuickAddWatchlist: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.screenWidthDp >= 720 ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    val cardWidthDp = if (isTv) ProDimens.PosterWidth.dp else 116.dp
    val posterHeightDp = if (isTv) ProDimens.PosterHeight.dp else 174.dp
    val totalHeightDp = if (isTv) ProDimens.PosterTotalHeight.dp else 224.dp

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) ProDimens.FocusScale else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "posterScale"
    )

    val cleanTitle = remember(title, isLive, lang) {
        ProVisualSanitizer.sanitizeTitle(
            title,
            ProVisualSanitizer.defaultFallbackTitle(lang, isLive)
        )
    }
    val cleanSubtitle = remember(subtitle) {
        ProVisualSanitizer.sanitizeSubtitle(subtitle)
    }

    Column(
        modifier = modifier
            .width(cardWidthDp)
            .height(totalHeightDp)
            .scale(scale)
            .onKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val code = ev.nativeKeyEvent.keyCode
                    if (ev.nativeKeyEvent.repeatCount > 0 &&
                        (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
                    ) {
                        onOpenDetails?.invoke()
                        return@onKeyEvent true
                    }
                    if (code == KeyEvent.KEYCODE_INFO || code == KeyEvent.KEYCODE_MENU) {
                        onOpenDetails?.invoke()
                        return@onKeyEvent true
                    }
                }
                if (isDpadSelectKeyEvent(ev)) {
                    onClick()
                    true
                } else false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(cardWidthDp)
                .height(posterHeightDp)
                .shadow(
                    elevation = if (isFocused) 16.dp else 4.dp,
                    shape = RoundedCornerShape(ProDimens.PosterRadius.dp),
                    spotColor = if (isFocused) CyanPrimary else Color.Black
                )
                .clip(RoundedCornerShape(ProDimens.PosterRadius.dp))
                .background(SurfaceElevated)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) CyanPrimary else ProBorder.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(ProDimens.PosterRadius.dp)
                )
        ) {
            if (!imageUrl.isNullOrBlank()) {
                ChannelAvatar(
                    name = cleanTitle,
                    logoUrl = imageUrl,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = ProDimens.PosterRadius.dp,
                    contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                    innerPadding = if (isLive) ProSpacing.sm else 0.dp,
                    isPoster = !isLive
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(SurfaceElevated, SurfaceDark))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLive) Icons.Default.LiveTv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(if (isTv) 40.dp else 32.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                DeepNavy.copy(alpha = if (isFocused) 0.92f else 0.72f)
                            )
                        )
                    )
            )

            if (!badge.isNullOrBlank()) {
                val badgeColor = when (badge.uppercase(Locale.ROOT)) {
                    "4K", "4K UHD", "UHD" -> Pro4KBadge
                    "FHD", "1080P", "FHD 1080" -> ProFhdBadge
                    "HD", "720P", "HD 720" -> ProHDBadge
                    "SD" -> ProSdBadge
                    else -> CyanPrimary
                }
                Surface(
                    shape = RoundedCornerShape(bottomEnd = ProSpacing.sm),
                    color = badgeColor.copy(alpha = 0.94f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = badge,
                        color = if (badgeColor == ProFhdBadge || badgeColor == ProSdBadge) Color.White else TextOnAccent,
                        fontSize = if (isTv) 10.5.sp else 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (isLive) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = ProSpacing.sm),
                    color = LiveRed.copy(alpha = 0.94f),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = if (isTv) 9.5.sp else 8.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            } else if (rating.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = ProSpacing.sm),
                    color = Color(0xD9050B14),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (rating.startsWith("★")) rating else "★ $rating",
                        color = ImdbGold,
                        fontSize = if (isTv) 10.sp else 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (onQuickAddWatchlist != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(if (isTv) 24.dp else 22.dp)
                        .clip(CircleShape)
                        .background(
                            if (inWatchlist) CyanPrimary else SurfaceDark.copy(alpha = 0.82f)
                        )
                        .border(1.dp, if (inWatchlist) CyanPrimary else ProBorder, CircleShape)
                        .clickable { onQuickAddWatchlist() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = AppStrings.tr(lang, "قائمتي", "Ma Liste", "My List"),
                        tint = if (inWatchlist) TextOnAccent else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.24f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(NetflixRed, CyanPrimary))
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 50.dp else 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = cleanTitle,
                color = if (isFocused) TextPrimary else TextSecondary,
                fontSize = if (isTv) 12.5.sp else 11.sp,
                lineHeight = if (isTv) 16.sp else 14.sp,
                fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 3f
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (cleanSubtitle != null && isTv) {
                Text(
                    text = cleanSubtitle,
                    color = if (isFocused) CyanPrimary else TextTertiary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ProContentRow(
    title: String,
    icon: ImageVector? = null,
    items: List<PosterItem>,
    onItemClick: (PosterItem) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    lang: AppUiLanguage = AppStrings.activeLang,
    rowListState: LazyListState = rememberLazyListState(),
    onItemDetailsClick: ((PosterItem) -> Unit)? = null
) {
    if (items.isEmpty()) return
    val configuration = LocalConfiguration.current
    val isTv = configuration.screenWidthDp >= 720 ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val watchlistKeys = ProfileStorageManager.activeProfileState.watchlistKeys

    // Deduplicate by item.id so LazyRow uses stable unique key = { _, item -> item.id } without collisions
    val uniqueItems = remember(items) { items.distinctBy { it.id } }

    Column(modifier = modifier) {
        ProSectionHeader(
            title = title,
            icon = icon,
            actionText = if (onSeeAllClick != null) {
                AppStrings.tr(lang, "عرض الكل", "Voir tout", "See All")
            } else null,
            onActionClick = onSeeAllClick
        )

        LazyRow(
            state = rowListState,
            contentPadding = PaddingValues(horizontal = if (isTv) ProSpacing.lg else ProSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) ProSpacing.md else 10.dp)
        ) {
            itemsIndexed(uniqueItems, key = { _, item -> item.id }) { _, item ->
                val isWatchlisted = item.id in watchlistKeys
                ProPosterCard(
                    title = item.title,
                    imageUrl = item.imageUrl,
                    badge = item.badge,
                    subtitle = item.subtitle,
                    isLive = item.isLive,
                    lang = lang,
                    progress = item.progress,
                    rating = item.rating,
                    inWatchlist = isWatchlisted,
                    onQuickAddWatchlist = {
                        item.sourceChannel?.let { ch ->
                            ProfileStorageManager.toggleWatchlist(ch)
                        }
                    },
                    onOpenDetails = {
                        if (onItemDetailsClick != null) {
                            onItemDetailsClick(item)
                        } else {
                            item.sourceChannel?.let { ch ->
                                LiveProGlobalActions.openDetailsForChannel(ch)
                            }
                        }
                    },
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

data class PosterItem(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
    val badge: String? = null,
    val subtitle: String? = null,
    val isLive: Boolean = false,
    val streamUrl: String = "",
    val qualityTier: ProQualityTier = ProQualityTier.SD,
    val sourceChannel: TvChannel? = null,
    val progress: Float = 0f,
    val rating: String = "",
    val releaseYear: String = "",
    val plot: String = ""
)

fun TvChannel.toPosterItem(progressFraction: Float = 0f): PosterItem {
    val tier = SportsQualitySelector.detectQualityTier(this.name, this.group)
    val isSports = SportsQualitySelector.isSportsOrBeinChannel(this)
    val resolvedBadge = when {
        tier == ProQualityTier.UHD_4K -> "4K"
        tier == ProQualityTier.FHD_1080 -> "FHD"
        tier == ProQualityTier.HD_720 -> "HD"
        isSports -> tier.badgeLabel
        else -> tier.badgeLabel
    }
    return PosterItem(
        id = this.stableKey,
        title = ProVisualSanitizer.sanitizeTitle(this.name),
        imageUrl = this.logo,
        badge = resolvedBadge,
        subtitle = ProVisualSanitizer.sanitizeSubtitle(this.group),
        isLive = (this.contentType == StreamContentType.LIVE_TV),
        streamUrl = this.url,
        qualityTier = tier,
        sourceChannel = this,
        progress = progressFraction.coerceIn(0f, 1f),
        rating = this.rating.ifBlank { if (this.contentType != StreamContentType.LIVE_TV) "8.2" else "" },
        releaseYear = this.releaseYear.ifBlank { "2025" },
        plot = this.plot
    )
}

@Composable
fun rememberProShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "proShimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(
            SurfaceDark,
            SurfaceElevated,
            SurfaceDark
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
}

@Composable
fun ProLoadingSkeleton(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberProShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(vertical = ProSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ProSpacing.lg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = ProSpacing.md)
                .clip(RoundedCornerShape(ProDimens.CardRadius.dp))
                .background(shimmerBrush)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ProSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ProSpacing.sm)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(104.dp)
                        .clip(RoundedCornerShape(ProDimens.CardRadius.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
fun ProEmptyState(
    lang: AppUiLanguage = AppStrings.activeLang,
    title: String = AppStrings.tr(
        lang,
        "لا يوجد محتوى متاح حالياً",
        "Aucun contenu disponible pour le moment",
        "No content available right now"
    ),
    subtitle: String = AppStrings.tr(
        lang,
        "يمكنك تحديث القوائم أو اختيار قائمة تشغيل من إدارة القوائم.",
        "Vous pouvez actualiser les listes ou choisir une playlist depuis le gestionnaire.",
        "You can refresh your lists or select a playlist from the playlist manager."
    ),
    actionText: String = AppStrings.tr(
        lang,
        "تحديث القوائم الآن",
        "Actualiser les listes maintenant",
        "Refresh Lists Now"
    ),
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btnInteraction = remember { MutableInteractionSource() }
    val btnFocused by btnInteraction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (btnFocused) ProDimens.FocusScale else 1f,
        animationSpec = tween(200),
        label = "emptyBtnScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(ProSpacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(ProDimens.CardRadius.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, ProBorder),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(ProSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ProSpacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(ProDimens.ButtonRadius.dp),
                    color = CyanPrimary,
                    border = if (btnFocused) BorderStroke(2.5.dp, Color.White) else null,
                    modifier = Modifier
                        .scale(scale)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onActionClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = btnInteraction)
                        .clickable(interactionSource = btnInteraction, indication = null) { onActionClick() }
                ) {
                    Text(
                        text = actionText,
                        color = TextOnAccent,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = ProSpacing.xl, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProErrorState(
    lang: AppUiLanguage = AppStrings.activeLang,
    message: String = AppStrings.tr(
        lang,
        "تعذّر تحميل المحتوى، يرجى التحقق من الاتصال والمحاولة مجدداً.",
        "Impossible de charger le contenu, veuillez vérifier votre connexion et réessayer.",
        "Unable to load content, please check your connection and try again."
    ),
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btnInteraction = remember { MutableInteractionSource() }
    val btnFocused by btnInteraction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (btnFocused) ProDimens.FocusScale else 1f,
        animationSpec = tween(200),
        label = "errorBtnScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(ProSpacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(ProDimens.CardRadius.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(ProSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ProSpacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = AppStrings.tr(lang, "تنبيه في الاتصال", "Alerte de connexion", "Connection Alert"),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(ProDimens.ButtonRadius.dp),
                    color = CyanPrimary,
                    border = if (btnFocused) BorderStroke(2.5.dp, Color.White) else null,
                    modifier = Modifier
                        .scale(scale)
                        .onKeyEvent { ev ->
                            if (isDpadSelectKeyEvent(ev)) {
                                onRetryClick()
                                true
                            } else false
                        }
                        .focusable(interactionSource = btnInteraction)
                        .clickable(interactionSource = btnInteraction, indication = null) { onRetryClick() }
                ) {
                    Text(
                        text = AppStrings.tr(lang, "إعادة المحاولة", "Réessayer", "Retry"),
                        color = TextOnAccent,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = ProSpacing.xl, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
