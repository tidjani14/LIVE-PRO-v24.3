package com.tvflux.pro

import androidx.compose.foundation.BorderStroke

import android.view.KeyEvent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// LIVE PRO v25.0 — Netflix / Shahid Style Side Navigation Rail & Mobile Bottom Bar
// Line-style Outlined Icons (24dp) + Cyan 20% Focus Circle + 1.08x Scale + Active Side Bar
// ============================================================================

enum class ProOverlayScreen {
    NONE,
    SEARCH,
    DETAILS,
    PROFILE,
    MY_LIST,
    DOWNLOADS,
    NOTIFICATIONS,
    ADVANCED_SETTINGS
}

data class ProNavRailEntry(
    val id: String,
    val tab: MainTab?,
    val overlay: ProOverlayScreen = ProOverlayScreen.NONE,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
    val labelAr: String,
    val badgeCount: Int = 0
)

@Composable
fun ProSideNavigationRail(
    activeTab: MainTab,
    compact: Boolean = false,
    onSelectTab: (MainTab) -> Unit,
    onExitClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeOverlay: ProOverlayScreen = LiveProGlobalActions.activeOverlayScreen,
    onSelectOverlay: (ProOverlayScreen) -> Unit = { overlay ->
        LiveProGlobalActions.activeOverlayScreen = overlay
    }
) {
    val focusManager = LocalFocusManager.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val unreadCount = SmartNotificationsManager.unreadCountState

    val entries = remember(unreadCount) {
        listOf(
            ProNavRailEntry("HOME", MainTab.HOME, ProOverlayScreen.NONE, Icons.Outlined.Home, Icons.Filled.Home, "الرئيسية"),
            ProNavRailEntry("SEARCH", null, ProOverlayScreen.SEARCH, Icons.Outlined.Search, Icons.Filled.Search, "البحث"),
            ProNavRailEntry("LIVE", MainTab.LIVE, ProOverlayScreen.NONE, Icons.Outlined.LiveTv, Icons.Filled.LiveTv, "مباشر"),
            ProNavRailEntry("BOUQUETS", MainTab.BOUQUETS, ProOverlayScreen.NONE, Icons.Outlined.Folder, Icons.Filled.Folder, "الباقات"),
            ProNavRailEntry("MOVIES", MainTab.MOVIES, ProOverlayScreen.NONE, Icons.Outlined.Movie, Icons.Filled.Movie, "أفلام"),
            ProNavRailEntry("SERIES", MainTab.SERIES, ProOverlayScreen.NONE, Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary, "مسلسلات"),
            ProNavRailEntry("MY_LIST", MainTab.FAVORITES, ProOverlayScreen.MY_LIST, Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark, "قائمتي"),
            ProNavRailEntry("DOWNLOADS", null, ProOverlayScreen.DOWNLOADS, Icons.Outlined.Download, Icons.Filled.Download, "التنزيلات"),
            ProNavRailEntry("NOTIFICATIONS", null, ProOverlayScreen.NOTIFICATIONS, Icons.Outlined.Notifications, Icons.Filled.Notifications, "الإشعارات", unreadCount),
            ProNavRailEntry("PROFILE", null, ProOverlayScreen.PROFILE, Icons.Outlined.Person, Icons.Filled.Person, "حسابي"),
            ProNavRailEntry("XTREAM", MainTab.XTREAM, ProOverlayScreen.NONE, Icons.Outlined.CloudDownload, Icons.Filled.CloudDownload, "القوائم"),
            ProNavRailEntry("SETTINGS", MainTab.SETTINGS, ProOverlayScreen.NONE, Icons.Outlined.Settings, Icons.Filled.Settings, "إعدادات")
        )
    }

    val itemFocusRequesters = remember { entries.associate { it.id to FocusRequester() } }
    val exitFocusRequester = remember { FocusRequester() }
    val exitInteraction = remember { MutableInteractionSource() }
    val exitFocused by exitInteraction.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(if (compact) ProDimens.SideRailWidthCompact.dp else ProDimens.SideRailWidth.dp)
            .background(
                Brush.verticalGradient(
                    listOf(SurfaceDark, DeepNavy, SurfaceDark)
                )
            )
            .border(1.dp, ProBorder.copy(alpha = 0.6f)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brand Crest + Active Profile Mini Avatar
        val activeProfile = ProfileStorageManager.activeProfileState
        Box(
            modifier = Modifier
                .padding(top = ProSpacing.sm, bottom = ProSpacing.xs)
                .size(if (compact) 36.dp else 44.dp)
                .clip(RoundedCornerShape(ProSpacing.md))
                .background(Brush.linearGradient(listOf(CyanPrimary, VioletSecondary)))
                .clickable {
                    LiveProGlobalActions.showWhoIsWatchingModal = true
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = activeProfile.avatarEmoji.ifBlank { "▶" },
                fontSize = if (compact) 16.sp else 20.sp
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.id }) { idx, entry ->
                val isActive = if (activeOverlay != ProOverlayScreen.NONE) {
                    entry.overlay == activeOverlay
                } else {
                    entry.overlay == ProOverlayScreen.NONE && entry.tab == activeTab
                }

                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isFocused) ProDimens.FocusScale else 1f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "navItemScale"
                )
                val ownReq = itemFocusRequesters[entry.id] ?: FocusRequester()

                val contentColor = when {
                    isFocused || isActive -> CyanPrimary
                    else -> TextSecondary
                }
                val railFocusMod = if (isActive) {
                    Modifier
                        .focusRequester(LiveProGlobalActions.sideNavRailFocusRequester)
                        .focusRequester(ownReq)
                } else {
                    Modifier.focusRequester(ownReq)
                }

                val triggerAction = {
                    SafeClickGuard.runThrottled("PRO_NAV_CLICK", 140L) {
                        if (entry.overlay != ProOverlayScreen.NONE) {
                            onSelectOverlay(entry.overlay)
                        } else if (entry.tab != null) {
                            onSelectOverlay(ProOverlayScreen.NONE)
                            onSelectTab(entry.tab)
                        }
                    }
                }

                Box(
                    modifier = railFocusMod
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isFocused -> CyanPrimary.copy(alpha = 0.14f)
                                isActive -> SurfaceElevated.copy(alpha = 0.75f)
                                else -> Color.Transparent
                            }
                        )
                        .then(
                            if (isFocused) {
                                Modifier.border(1.8.dp, CyanPrimary, RoundedCornerShape(12.dp))
                            } else Modifier
                        )
                        .onKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                when (ev.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                                    KeyEvent.KEYCODE_BUTTON_A -> {
                                        triggerAction()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (idx == 0) {
                                            runCatching { LiveProGlobalActions.topToolbarPrimaryFocusRequester.requestFocus() }
                                            true
                                        } else {
                                            val prevId = entries[idx - 1].id
                                            runCatching { itemFocusRequesters[prevId]?.requestFocus() }
                                            true
                                        }
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (idx < entries.lastIndex) {
                                            val nextId = entries[idx + 1].id
                                            runCatching { itemFocusRequesters[nextId]?.requestFocus() }
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
                        .clickable(interactionSource = interactionSource, indication = null) {
                            triggerAction()
                        }
                        .padding(vertical = 6.dp)
                ) {
                    // Active State Colored Side Bar
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(3.5.dp)
                                .height(if (compact) 20.dp else 26.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyanPrimary)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Focus State Circle (Cyan 20%) + 24dp Line/Filled Icon
                        Box(
                            modifier = Modifier
                                .size(if (compact) 32.dp else 38.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isFocused -> CyanPrimary.copy(alpha = 0.20f)
                                        isActive -> CyanPrimary.copy(alpha = 0.12f)
                                        else -> Color.Transparent
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isActive || isFocused) entry.filledIcon else entry.outlinedIcon,
                                contentDescription = entry.labelAr,
                                tint = contentColor,
                                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                            )
                            if (entry.badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(LiveRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = entry.badgeCount.coerceAtMost(9).toString(),
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        if (!compact) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = entry.labelAr,
                                color = contentColor,
                                fontSize = 10.5.sp,
                                fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Exit Button at bottom of Rail
        Box(
            modifier = Modifier
                .focusRequester(exitFocusRequester)
                .padding(ProSpacing.xs)
                .size(if (compact) 34.dp else 40.dp)
                .scale(if (exitFocused) 1.08f else 1f)
                .clip(CircleShape)
                .background(if (exitFocused) ErrorRed.copy(alpha = 0.22f) else Color.Transparent)
                .then(
                    if (exitFocused) Modifier.border(2.dp, ErrorRed, CircleShape) else Modifier
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
                                val lastId = entries.last().id
                                runCatching { itemFocusRequesters[lastId]?.requestFocus() }
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
                imageVector = Icons.Outlined.ExitToApp,
                contentDescription = "خروج",
                tint = if (exitFocused) Color.White else ErrorRed,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun ProMobileBottomNavigationBar(
    activeTab: MainTab,
    activeOverlay: ProOverlayScreen = LiveProGlobalActions.activeOverlayScreen,
    onSelectTab: (MainTab) -> Unit,
    onSelectOverlay: (ProOverlayScreen) -> Unit = { LiveProGlobalActions.activeOverlayScreen = it },
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        ProBottomBarEntry(
            label = "رئيسية",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            isSelected = activeOverlay == ProOverlayScreen.NONE && activeTab == MainTab.HOME,
            onClick = {
                onSelectOverlay(ProOverlayScreen.NONE)
                onSelectTab(MainTab.HOME)
            }
        ),
        ProBottomBarEntry(
            label = "مباشر",
            selectedIcon = Icons.Filled.LiveTv,
            unselectedIcon = Icons.Outlined.LiveTv,
            isSelected = activeOverlay == ProOverlayScreen.NONE && activeTab == MainTab.LIVE,
            onClick = {
                onSelectOverlay(ProOverlayScreen.NONE)
                onSelectTab(MainTab.LIVE)
            }
        ),
        ProBottomBarEntry(
            label = "أفلام",
            selectedIcon = Icons.Filled.Movie,
            unselectedIcon = Icons.Outlined.Movie,
            isSelected = activeOverlay == ProOverlayScreen.NONE && activeTab == MainTab.MOVIES,
            onClick = {
                onSelectOverlay(ProOverlayScreen.NONE)
                onSelectTab(MainTab.MOVIES)
            }
        ),
        ProBottomBarEntry(
            label = "مسلسلات",
            selectedIcon = Icons.Filled.VideoLibrary,
            unselectedIcon = Icons.Outlined.VideoLibrary,
            isSelected = activeOverlay == ProOverlayScreen.NONE && activeTab == MainTab.SERIES,
            onClick = {
                onSelectOverlay(ProOverlayScreen.NONE)
                onSelectTab(MainTab.SERIES)
            }
        ),
        ProBottomBarEntry(
            label = "بحث",
            selectedIcon = Icons.Filled.Search,
            unselectedIcon = Icons.Outlined.Search,
            isSelected = activeOverlay == ProOverlayScreen.SEARCH,
            onClick = {
                onSelectOverlay(ProOverlayScreen.SEARCH)
            }
        ),
        ProBottomBarEntry(
            label = "حسابي",
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            isSelected = activeOverlay == ProOverlayScreen.PROFILE,
            onClick = {
                onSelectOverlay(ProOverlayScreen.PROFILE)
            }
        )
    )

    Surface(
        color = SurfaceDark.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, ProBorder),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            entries.forEach { entry ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (entry.isSelected) CyanDark.copy(alpha = 0.24f) else Color.Transparent
                        )
                        .clickable { entry.onClick() }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (entry.isSelected) entry.selectedIcon else entry.unselectedIcon,
                        contentDescription = entry.label,
                        tint = if (entry.isSelected) CyanPrimary else TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.label,
                        color = if (entry.isSelected) CyanPrimary else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (entry.isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private data class ProBottomBarEntry(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isSelected: Boolean,
    val onClick: () -> Unit
)
