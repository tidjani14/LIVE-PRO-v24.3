package com.tvflux.pro

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

// ============================================================================
// LIVE PRO v26.0 — Professional Categorized Settings & IBO Player Pro UI Mode
// ============================================================================

enum class SubtitleSizePreset(val labelAr: String, val scaleFactor: Float) {
    SMALL("صغير", 0.85f),
    MEDIUM("متوسط (افتراضي)", 1.0f),
    LARGE("كبير (موصى به للتلفاز)", 1.25f),
    EXTRA_LARGE("كبير جداً", 1.5f)
}

enum class SubtitleColorPreset(val labelAr: String, val textColorHex: Long, val bgColorHex: Long) {
    WHITE_DARK("أبيض بخلفية داكنة", 0xFFF8FAFC, 0xB3050B14),
    YELLOW_BLACK("أصفر سينمائي", 0xFFFACC15, 0xCC000000),
    CYAN_GLASS("سماوي شفاف", 0xFF22D3EE, 0x990F172A)
}

object ProSubtitleAndPlayerSettings {
    var subtitleSizePreset by mutableStateOf(SubtitleSizePreset.LARGE)
    var subtitleColorPreset by mutableStateOf(SubtitleColorPreset.WHITE_DARK)
    var autoFetchOpenSubtitles by mutableStateOf(true)
    var sleepTimerMinutes by mutableStateOf(0) // 0 = Off, 30, 60, 90
    var httpsPinningEnabled by mutableStateOf(true)
    var apiRateLimitingEnabled by mutableStateOf(true)
}

@Composable
private fun CategorizedSettingElevatedCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color = CyanPrimary,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = SurfaceDark
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.4.dp,
                brush = Brush.linearGradient(
                    colors = listOf(accentColor.copy(alpha = 0.85f), BeinBorder)
                ),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                    modifier = Modifier.size(if (compact) 38.dp else 44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = if (compact) 15.sp else 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = if (compact) 11.5.sp else 13.sp
                        )
                    }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
            content()
        }
    }
}

@Composable
fun ProAdvancedSettingsHeaderCard(
    currentLanguage: AppUiLanguage,
    onSelectLanguage: (AppUiLanguage) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceDark,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(CyanPrimary, VioletSecondary))
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ProSpacing.lg, vertical = ProSpacing.sm)
    ) {
        Column(
            modifier = Modifier.padding(ProSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ProSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🌐 إعدادات المنصة العالمية (PLAYER VIP v26.0)",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "تبديل فوري للغة (العربية RTL / الفرنسية / الإنجليزية) + تخصيص المظهر والترجمة والأمان",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppUiLanguage.values().forEach { langOption ->
                        val isSel = currentLanguage == langOption
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSel) CyanPrimary else SurfaceElevated,
                            modifier = Modifier.clickable { onSelectLanguage(langOption) }
                        ) {
                            Text(
                                text = when (langOption) {
                                    AppUiLanguage.AR -> "🇩🇿 العربية (RTL)"
                                    AppUiLanguage.FR -> "🇫🇷 Français"
                                    AppUiLanguage.EN -> "🇬🇧 English"
                                },
                                color = if (isSel) DeepNavy else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ProSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceElevated,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            ProThemeState.useNetflixRedAccent = !ProThemeState.useNetflixRedAccent
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎨 نمط اللون الأساسي (Accent)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (ProThemeState.useNetflixRedAccent) "Netflix Red (#E50914)" else "Cyan Pro (#22D3EE)",
                            color = ProThemeState.activePrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceElevated,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val values = SubtitleSizePreset.values()
                            val next = values[(ProSubtitleAndPlayerSettings.subtitleSizePreset.ordinal + 1) % values.size]
                            ProSubtitleAndPlayerSettings.subtitleSizePreset = next
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💬 حجم خط الترجمة (Subtitles)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = ProSubtitleAndPlayerSettings.subtitleSizePreset.labelAr,
                            color = CyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceElevated,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val values = SubtitleColorPreset.values()
                            val next = values[(ProSubtitleAndPlayerSettings.subtitleColorPreset.ordinal + 1) % values.size]
                            ProSubtitleAndPlayerSettings.subtitleColorPreset = next
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎨 لون الترجمة والخلفية", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = ProSubtitleAndPlayerSettings.subtitleColorPreset.labelAr,
                            color = Color(ProSubtitleAndPlayerSettings.subtitleColorPreset.textColorHex),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VpnProSettingsSection(
    isVpnProSubscribed: Boolean = VpnProConfigFetcher.isVpnProSubscribed || WarpConfigFetcher.ENABLE_CLOUDFLARE_WARP_FALLBACK,
    vpnStatus: CloudflareTunnelStatus,
    vpnPausedTemporarily: Boolean = LiveProGlobalActions.vpnPausedTemporarily,
    compact: Boolean = false,
    lang: AppUiLanguage = AppStrings.activeLang,
    onRecheckVpnPro: () -> Unit = { LiveProGlobalActions.onRecheckVpnPro() },
    onEnableVpn: () -> Unit = {},
    onPauseVpn: () -> Unit = {},
    onDisableVpn: () -> Unit = {}
) {
    if (isVpnProSubscribed || WarpConfigFetcher.ENABLE_CLOUDFLARE_WARP_FALLBACK) {
        val isVpnActive = (vpnStatus.connected || CloudflareWarpVpnService.isTunnelRunning) &&
            !vpnPausedTemporarily && !LiveProGlobalActions.vpnPermanentDisable
        val isRechecking = VpnProConfigFetcher.isRechecking

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BeinCard)
                .border(
                    width = 1.8.dp,
                    color = if (isVpnActive) EmeraldLive else Color(0xFF38BDF8),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isVpnActive) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isVpnActive) EmeraldLive else Color(0xFF38BDF8)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isVpnActive) {
                                AppStrings.tr(
                                    lang,
                                    "🔒 VPN PRO: نفق WireGuard نشط ومؤمّن ✓",
                                    "🔒 VPN PRO : Tunnel WireGuard actif ✓",
                                    "🔒 VPN PRO: WireGuard Tunnel Active ✓"
                                )
                            } else {
                                AppStrings.tr(
                                    lang,
                                    "🔒 VPN PRO: متوقف حالياً",
                                    "🔒 VPN PRO : Arrêté",
                                    "🔒 VPN PRO: Stopped"
                                )
                            },
                            color = if (isVpnActive) EmeraldLive else Color(0xFF7DD3FC),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 14.sp else 16.sp
                        )
                        val ep = VpnProConfigFetcher.activeEndpoint.ifBlank { "${ChannelRepository.VPN_PRO_SERVER_IP}:51820" }
                        Text(
                            text = "AES-256-GCM • WireGuard UDP ($ep)",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                BeinFocusableButton(
                    text = if (isRechecking) {
                        AppStrings.tr(lang, "⏳ جارٍ التحقق...", "⏳ Vérification...", "⏳ Checking...")
                    } else {
                        AppStrings.tr(lang, "🔄 إعادة التحقق", "🔄 Revérifier", "🔄 Re-verify")
                    },
                    isPrimary = true,
                    compact = compact,
                    onClick = onRecheckVpnPro
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BeinFocusableButton(
                    text = AppStrings.tr(
                        lang,
                        "🚀 تشغيل VPN PRO الآن",
                        "🚀 Activer VPN PRO",
                        "🚀 Start VPN PRO Now"
                    ),
                    isPrimary = isVpnActive,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                    onClick = onEnableVpn
                )
                BeinFocusableButton(
                    text = AppStrings.tr(
                        lang,
                        "⏸ إيقاف مؤقت",
                        "⏸ Pause session",
                        "⏸ Pause Session"
                    ),
                    isPrimary = !isVpnActive && !LiveProGlobalActions.vpnPermanentDisable,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                    onClick = onPauseVpn
                )
                BeinFocusableButton(
                    text = if (LiveProGlobalActions.vpnPermanentDisable) {
                        AppStrings.tr(lang, "🔒 مغلق دائماً ✓", "🔒 Désactivé ✓", "🔒 Permanently Off ✓")
                    } else {
                        AppStrings.tr(lang, "🔒 إيقاف دائم", "🔒 Désactiver", "🔒 Disable")
                    },
                    isPrimary = LiveProGlobalActions.vpnPermanentDisable,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                    onClick = onDisableVpn
                )
            }
        }
    }
}

@Composable
internal fun SettingsAndVpnView(
    preferences: PlayerPreferences,
    rawBouquets: List<BouquetSummary>,
    allChannels: List<TvChannel>,
    vpnStatus: CloudflareTunnelStatus,
    vpnPausedTemporarily: Boolean = LiveProGlobalActions.vpnPausedTemporarily,
    compact: Boolean,
    onReconnectVpn: () -> Unit,
    onPauseVpnTemporarily: () -> Unit = { LiveProGlobalActions.onPauseVpnTemporarily() },
    onDisableVpnPermanently: () -> Unit = { LiveProGlobalActions.onDisableVpnPermanently() },
    onOpenPlaylistSelectorModalGlobal: () -> Unit = { LiveProGlobalActions.onOpenPlaylistSelectorModalGlobal() },
    onOpenPlaylistSelectorModal: () -> Unit = onOpenPlaylistSelectorModalGlobal,
    onUpdatePreferences: (PlayerPreferences) -> Unit
) {
    val context = LocalContext.current
    val lang = preferences.appLanguage
    var cacheSizeLabel by remember { mutableStateOf(FluxBitmapCache.getCacheSizeMb(context)) }
    var showBouquetReorderModal by remember { mutableStateOf(false) }
    var showChannelReorderModal by remember { mutableStateOf(false) }
    val firstSettingFocusRequester = remember { FocusRequester() }
    var perfMetrics by remember { mutableStateOf<DatabasePerformanceMetrics?>(null) }

    LaunchedEffect(allChannels.size, cacheSizeLabel) {
        perfMetrics = TiviMateDiagnostics.buildSnapshot(context)
    }

    LaunchedEffect(Unit) {
        for (attempt in 1..6) {
            delay(if (attempt <= 2) 25L else 45L)
            if (runCatching { firstSettingFocusRequester.requestFocus() }.isSuccess) break
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)
    ) {
        // ====================================================================
        // CARD 1: 📺 إعدادات الواجهة والمظهر
        // Language selector, Simple/Advanced UI toggle, network speed display
        // ====================================================================
        item {
            CategorizedSettingElevatedCard(
                title = "📺 إعدادات الواجهة والمظهر",
                subtitle = AppStrings.tr(
                    lang,
                    "اختيار لغة التطبيق، التبديل بين الواجهة الكلاسيكية والواجهة المتقدمة، وعرض سرعة الشبكة",
                    "Langue, bascule Interface Classique / Avancée et vitesse réseau",
                    "App language, Classic UI vs Advanced UI toggle, and network speed display"
                ),
                icon = Icons.Default.Tv,
                accentColor = CyanPrimary,
                compact = compact
            ) {
                Text(
                    text = "🌐 ${AppStrings.tr(lang, "لغة التطبيق (العربية / Français / English):", "Langue de l'application :", "Application Language:")}",
                    color = Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 13.sp else 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppUiLanguage.values().forEachIndexed { idx, lOpt ->
                        BeinFocusableButton(
                            text = (if (preferences.appLanguage == lOpt) "✓ " else "") + lOpt.nativeLabel,
                            isPrimary = preferences.appLanguage == lOpt,
                            compact = compact,
                            modifier = if (idx == 0) Modifier.weight(1f).focusRequester(firstSettingFocusRequester) else Modifier.weight(1f),
                            onClick = {
                                onUpdatePreferences(preferences.copy(appLanguage = lOpt))
                            }
                        )
                    }
                }

                Text(
                    text = "✨ ${AppStrings.tr(lang, "نمط الشاشة الرئيسية (الواجهة الكلاسيكية أو الواجهة المتقدمة):", "Style de l'écran d'accueil (Interface Classique ou Avancée) :", "Home Screen Mode (Classic UI vs Advanced UI):")}",
                    color = Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 13.sp else 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BeinFocusableButton(
                        text = (if (preferences.useSimpleUi) "✓ " else "") +
                            AppStrings.tr(lang, "⚡ الواجهة الكلاسيكية السريعة", "⚡ Interface Classique", "⚡ Classic UI"),
                        isPrimary = preferences.useSimpleUi,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!preferences.useSimpleUi) {
                                onUpdatePreferences(preferences.copy(useSimpleUi = true))
                            }
                        }
                    )
                    BeinFocusableButton(
                        text = (if (!preferences.useSimpleUi) "✓ " else "") +
                            AppStrings.tr(lang, "🎬 الواجهة المتقدمة الكاملة", "🎬 Interface Avancée", "🎬 Advanced Pro UI"),
                        isPrimary = !preferences.useSimpleUi,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (preferences.useSimpleUi) {
                                onUpdatePreferences(preferences.copy(useSimpleUi = false))
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BeinFocusableButton(
                        text = if (preferences.showNetworkSpeed) {
                            "🌐 عرض سرعة الشبكة (Vitesse réseau): مفعّل ✓"
                        } else {
                            "🌐 عرض سرعة الشبكة (Vitesse réseau): مخفي"
                        },
                        isPrimary = preferences.showNetworkSpeed,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUpdatePreferences(preferences.copy(showNetworkSpeed = !preferences.showNetworkSpeed))
                        }
                    )
                    BeinFocusableButton(
                        text = "💬 حجم الترجمة: ${ProSubtitleAndPlayerSettings.subtitleSizePreset.labelAr}",
                        isPrimary = false,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val values = SubtitleSizePreset.values()
                            val next = values[(ProSubtitleAndPlayerSettings.subtitleSizePreset.ordinal + 1) % values.size]
                            ProSubtitleAndPlayerSettings.subtitleSizePreset = next
                        }
                    )
                }
            }
        }

        // ====================================================================
        // CARD 2: 🎛️ إدارة القوائم والمحتوى
        // Bouquet reordering/hiding, quick link to switch playlists
        // ====================================================================
        item {
            CategorizedSettingElevatedCard(
                title = "🎛️ إدارة القوائم والمحتوى",
                subtitle = AppStrings.tr(
                    lang,
                    "تبديل قوائم التشغيل السريع، إخفاء الباقات غير المرغوبة، وترتيب الباقات والقنوات بالريموت",
                    "Changement rapide de playlist, masquage et tri des bouquets et chaînes",
                    "Quick playlist switcher, bouquet hiding, and remote bouquet/channel reordering"
                ),
                icon = Icons.Default.PlaylistPlay,
                accentColor = Color(0xFFF59E0B),
                compact = compact
            ) {
                BeinFocusableButton(
                    text = AppStrings.tr(
                        lang,
                        "🎛️ تغيير القوائم / إدارة البلاي ليست الآن",
                        "🎛️ Changer de liste / Gérer les playlists",
                        "🎛️ Switch Playlists / Manage Lists Now"
                    ),
                    isPrimary = true,
                    compact = compact,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenPlaylistSelectorModal
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BeinFocusableButton(
                        text = "👁 ${AppStrings.tr(lang, "إخفاء / ترتيب الباقات", "Masquer / Trier Bouquets", "Hide / Reorder Bouquets")} (${rawBouquets.size})",
                        isPrimary = false,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = { showBouquetReorderModal = true }
                    )

                    BeinFocusableButton(
                        text = "📺 ${AppStrings.tr(lang, "ترتيب القنوات", "Trier les chaînes", "Reorder Channels")} (${allChannels.size})",
                        isPrimary = false,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = { showChannelReorderModal = true }
                    )

                    if (preferences.customBouquetOrder.isNotEmpty() ||
                        preferences.customChannelOrder.isNotEmpty() ||
                        preferences.hiddenBouquets.isNotEmpty()
                    ) {
                        BeinFocusableButton(
                            text = AppStrings.tr(lang, "إظهار الكل", "Tout afficher", "Reset All"),
                            isPrimary = false,
                            compact = compact,
                            onClick = {
                                onUpdatePreferences(
                                    preferences.copy(
                                        customBouquetOrder = emptyList(),
                                        hiddenBouquets = emptySet(),
                                        customChannelOrder = emptyList()
                                    )
                                )
                            }
                        )
                    }
                }

                val isMqHidden = CustomOrderHelper.isMultiQualityBouquetHidden(preferences.hiddenBouquets)
                val isFavBqHidden = CustomOrderHelper.isFavoritesBouquetHidden(preferences.hiddenBouquets)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BeinFocusableButton(
                        text = if (isMqHidden) {
                            "🚫 باقة (متعدد جودات): مخفية (اضغط للإظهار)"
                        } else {
                            "⚡ باقة (متعدد جودات): ظاهرة في الباقات ✓"
                        },
                        isPrimary = !isMqHidden,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUpdatePreferences(
                                preferences.copy(
                                    hiddenBouquets = CustomOrderHelper.toggleMultiQualityBouquetHidden(preferences.hiddenBouquets)
                                )
                            )
                        }
                    )
                    BeinFocusableButton(
                        text = if (isFavBqHidden) {
                            "🚫 باقة (★ المفضلة) بالباقات: مخفية (اضغط للإظهار)"
                        } else {
                            "★ باقة (المفضلة) بالباقات: ظاهرة ✓"
                        },
                        isPrimary = !isFavBqHidden,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUpdatePreferences(
                                preferences.copy(
                                    hiddenBouquets = CustomOrderHelper.toggleFavoritesBouquetHidden(
                                        preferences.hiddenBouquets,
                                        AppStrings.favoritesLabel(lang)
                                    )
                                )
                            )
                        }
                    )
                }
            }
        }

        // ====================================================================
        // CARD 3: 🛡️ الحماية والشبكة
        // VPN PRO section (if subscribed), Buffer Profile selector (Anti-Freeze)
        // ====================================================================
        item {
            CategorizedSettingElevatedCard(
                title = "🛡️ الحماية والشبكة",
                subtitle = AppStrings.tr(
                    lang,
                    "نفق الحماية المشفّر VPN PRO ونظام منع التقطيع ومقاومة هزات الإنترنت (Anti-Freeze)",
                    "Tunnel sécurisé VPN PRO et système Anti-Coupure (Buffer Profile)",
                    "Encrypted VPN PRO tunnel and Anti-Freeze network buffer profiles"
                ),
                icon = Icons.Default.Security,
                accentColor = EmeraldLive,
                compact = compact
            ) {
                val isVpnProSubscribed = VpnProConfigFetcher.isVpnProSubscribed || WarpConfigFetcher.ENABLE_CLOUDFLARE_WARP_FALLBACK
                if (isVpnProSubscribed || WarpConfigFetcher.ENABLE_CLOUDFLARE_WARP_FALLBACK) {
                    VpnProSettingsSection(
                        isVpnProSubscribed = isVpnProSubscribed,
                        vpnStatus = vpnStatus,
                        vpnPausedTemporarily = vpnPausedTemporarily,
                        compact = compact,
                        lang = lang,
                        onRecheckVpnPro = { LiveProGlobalActions.onRecheckVpnPro() },
                        onEnableVpn = {
                            onUpdatePreferences(
                                preferences.copy(
                                    vpnConnectionMode = VpnConnectionMode.WIREGUARD_FULL,
                                    vpnPermanentDisable = false,
                                    vpnAutoStartOnLaunch = true
                                )
                            )
                            LiveProGlobalActions.updateVpnPrefs(
                                context = context,
                                permanentDisable = false,
                                autoStart = true,
                                mode = VpnConnectionMode.WIREGUARD_FULL,
                                askOnStartup = false
                            )
                            LiveProGlobalActions.onSelectVpnConnectionMode(VpnConnectionMode.WIREGUARD_FULL)
                        },
                        onPauseVpn = {
                            LiveProGlobalActions.updateVpnPrefs(
                                context = context,
                                permanentDisable = false,
                                autoStart = true,
                                mode = VpnConnectionMode.WIREGUARD_FULL,
                                askOnStartup = false
                            )
                            LiveProGlobalActions.onPauseVpnTemporarily()
                        },
                        onDisableVpn = {
                            onUpdatePreferences(
                                preferences.copy(
                                    vpnConnectionMode = VpnConnectionMode.DISABLED,
                                    vpnPermanentDisable = true,
                                    vpnAutoStartOnLaunch = false
                                )
                            )
                            LiveProGlobalActions.updateVpnPrefs(
                                context = context,
                                permanentDisable = true,
                                autoStart = false,
                                mode = VpnConnectionMode.DISABLED,
                                askOnStartup = false
                            )
                            onDisableVpnPermanently()
                        }
                    )
                }

                Text(
                    text = "🛡️ ${AppStrings.tr(lang, "نظام منع التقطيع ومقاومة هزات الإنترنت (Anti-Freeze Engine):", "Système Anti-Coupure & Stabilité Réseau :", "Anti-Freeze & Network Jitter Cushion:")}",
                    color = Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 13.sp else 14.sp
                )

                BufferProfile.values().forEach { bp ->
                    val selected = preferences.bufferProfile == bp
                    var bpFocused by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(if (bpFocused) 1.02f else 1.0f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    bpFocused -> Color(0xFF0284C7)
                                    selected -> BeinPrimaryPurple
                                    else -> BeinSurface
                                }
                            )
                            .border(
                                width = if (bpFocused) 2.5.dp else if (selected) 1.5.dp else 1.dp,
                                color = if (bpFocused) BeinFocusGlow else if (selected) Color(0xFF38BDF8) else BeinBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .onFocusChanged { bpFocused = it.isFocused }
                            .onKeyEvent { ev ->
                                val code = ev.nativeKeyEvent.keyCode
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0 &&
                                    (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A)
                                ) {
                                    onUpdatePreferences(preferences.copy(bufferProfile = bp))
                                    true
                                } else false
                            }
                            .focusable()
                            .clickable { onUpdatePreferences(preferences.copy(bufferProfile = bp)) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = (if (selected) "✓ " else "") + bp.titleAr,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 13.sp else 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = bp.descAr,
                            color = if (bpFocused) Color(0xFFF1F5F9) else Color(0xFFCBD5E1),
                            fontSize = if (compact) 11.sp else 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ====================================================================
        // CARD 4: ⚙️ أداء المشغل
        // Player Engine selector (Exo/FFmpeg), Low-End Device mode, Thumbnail cache settings
        // ====================================================================
        item {
            CategorizedSettingElevatedCard(
                title = "⚙️ أداء المشغل",
                subtitle = AppStrings.tr(
                    lang,
                    "محرك المشغل المدمج (ExoPlayer + FFmpeg)، وضع تسريع الأجهزة الضعيفة، وإدارة كاش الصور",
                    "Moteur de lecture (ExoPlayer + FFmpeg), mode appareils faibles et cache d'images",
                    "Player Engine (Exo/FFmpeg), Low-End Device acceleration, and thumbnail cache settings"
                ),
                icon = Icons.Default.Speed,
                accentColor = VioletSecondary,
                compact = compact
            ) {
                Text(
                    text = "🎬 ${AppStrings.tr(lang, "اختر وضع محرك المشغل المدمج (ExoPlayer + FFmpeg الشامل لكل الترميزات):", "Choisir le mode du moteur (ExoPlayer + FFmpeg Universel) :", "Select Internal Player Engine Mode (ExoPlayer + FFmpeg All-Codec):")}",
                    color = Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 13.sp else 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InternalPlayerEngine.values().forEach { engOpt ->
                        BeinFocusableButton(
                            text = (if (preferences.playerEngine == engOpt) "✓ " else "") + engOpt.label(lang),
                            isPrimary = preferences.playerEngine == engOpt,
                            compact = compact,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onUpdatePreferences(preferences.copy(playerEngine = engOpt))
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BeinFocusableButton(
                        text = if (preferences.lowEndDeviceMode) {
                            "🚀 تسريع المشغل للأجهزة الضعيفة: مفعّل ✓"
                        } else {
                            "🚀 تسريع المشغل للأجهزة الضعيفة: تلقائي"
                        },
                        isPrimary = preferences.lowEndDeviceMode,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val nextLow = !preferences.lowEndDeviceMode
                            onUpdatePreferences(
                                preferences.copy(
                                    lowEndDeviceMode = nextLow,
                                    bufferProfile = if (nextLow) BufferProfile.LITE_LOW_RAM else BufferProfile.BALANCED
                                )
                            )
                        }
                    )
                    BeinFocusableButton(
                        text = if (preferences.preferSoftwareDecoder) "🎞 فك الترميز: برمجي (SW)" else "🎞 فك الترميز: تلقائي (HW+SW)",
                        isPrimary = preferences.preferSoftwareDecoder,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUpdatePreferences(preferences.copy(preferSoftwareDecoder = !preferences.preferSoftwareDecoder))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🖼️ إدارة ذاكرة الصور المصغرة ($cacheSizeLabel / الحد: ${preferences.thumbnailCacheLimit.titleAr})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 12.5.sp else 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    BeinFocusableButton(
                        text = AppStrings.tr(lang, "مسح كاش الصور", "Vider le cache", "Clear Thumbnails"),
                        isPrimary = false,
                        compact = true,
                        onClick = {
                            FluxBitmapCache.clear(context)
                            cacheSizeLabel = FluxBitmapCache.getCacheSizeMb(context)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThumbnailCacheLimitOption.values().forEach { opt ->
                        BeinFocusableButton(
                            text = opt.name.replace("MB_", "") + " MB",
                            isPrimary = preferences.thumbnailCacheLimit == opt,
                            compact = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                FluxBitmapCache.maxDiskCacheBytes = opt.maxBytes
                                FluxBitmapCache.enforceDiskQuota(context, opt.maxBytes)
                                cacheSizeLabel = FluxBitmapCache.getCacheSizeMb(context)
                                onUpdatePreferences(preferences.copy(thumbnailCacheLimit = opt))
                            }
                        )
                    }
                }
            }
        }

        // ====================================================================
        // CARD 5: 🔄 التحديثات والدعم
        // Manual app update check, SQLite diagnostics, app version
        // ====================================================================
        item {
            CategorizedSettingElevatedCard(
                title = "🔄 التحديثات والدعم",
                subtitle = AppStrings.tr(
                    lang,
                    "فحص التحديثات اليدوي، إحصائيات قاعدة البيانات SQLite والترميزات، وإصدار التطبيق",
                    "Mise à jour manuelle, diagnostics SQLite & codecs, et version de l'application",
                    "Manual app update check, SQLite & codec diagnostics, and app version"
                ),
                icon = Icons.Default.SystemUpdateAlt,
                accentColor = Color(0xFF38BDF8),
                compact = compact
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.tr(
                                lang,
                                "📲 إصدار التطبيق الحالي: PLAYER VIP v${ChannelRepository.CURRENT_APP_VERSION_CODE}.0",
                                "📲 Version actuelle : PLAYER VIP v${ChannelRepository.CURRENT_APP_VERSION_CODE}.0",
                                "📲 Current App Version: PLAYER VIP v${ChannelRepository.CURRENT_APP_VERSION_CODE}.0"
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 13.5.sp else 15.5.sp
                        )
                        Text(
                            text = AppStrings.tr(
                                lang,
                                "ابحث يدوياً عن أي تحديث جديد مرسل من الإدارة ليتم تنزيله وتثبيته مباشرة فوق النسخة الحالية",
                                "Rechercher manuellement une nouvelle mise à jour pour l'installer directement",
                                "Manually check for a new update from Admin to download and install directly"
                            ),
                            color = Color(0xFFE2E8F0),
                            fontSize = if (compact) 11.5.sp else 12.5.sp
                        )
                    }
                }

                BeinFocusableButton(
                    text = AppStrings.tr(
                        lang,
                        "🔄 البحث عن تحديثات التطبيق الآن",
                        "🔄 Rechercher des mises à jour maintenant",
                        "🔄 Check for App Updates Now"
                    ),
                    isPrimary = true,
                    compact = compact,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        LiveProGlobalActions.onCheckAppUpdateManual()
                    }
                )

                val m = perfMetrics
                if (m != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BeinDarkBg)
                            .border(1.dp, BeinBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "📊 المكتبة المحلية (SQLite): ${m.totalChannelsInDb} قناة • ${m.totalMoviesInDb} فيلم • ${m.totalSeriesInDb} مسلسل",
                            color = EmeraldLive,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 12.sp else 13.5.sp
                        )
                        Text(
                            text = "💾 حجم قاعدة البيانات: ${m.dbFileSizeMb} • الذاكرة العشوائية (RAM): ${m.ramUsedMb}/${m.ramMaxMb} MB • زمن الاستعلام: ${m.lastQueryTimeMs}ms",
                            color = Color(0xFFE2E8F0),
                            fontSize = if (compact) 11.sp else 12.5.sp
                        )
                        Text(
                            text = "🎬 ترميزات الفيديو المدعومة بالجهاز: ${m.supportedVideoCodecs.joinToString(" ، ")}",
                            color = Color.White,
                            fontSize = if (compact) 11.sp else 12.5.sp
                        )
                        Text(
                            text = "🔊 ترميزات الصوت المدعومة بالجهاز (HW + FFmpeg): ${m.supportedAudioCodecs.joinToString(" ، ")}",
                            color = Color.LightGray,
                            fontSize = if (compact) 11.sp else 12.5.sp
                        )
                        Text(
                            text = "🔄 آخر مزامنة تفاضلية: ${m.lastSyncSummary}",
                            color = Color(0xFFCBD5E1),
                            fontSize = if (compact) 11.sp else 12.5.sp
                        )
                    }
                }
            }
        }
    }

    if (showBouquetReorderModal) {
        BouquetReorderDialog(
            rawBouquets = rawBouquets,
            preferences = preferences,
            compact = compact,
            onSave = { newOrder, newHidden ->
                onUpdatePreferences(
                    preferences.copy(
                        customBouquetOrder = newOrder,
                        hiddenBouquets = newHidden
                    )
                )
            },
            onDismiss = { showBouquetReorderModal = false }
        )
    }

    if (showChannelReorderModal) {
        ChannelReorderDialog(
            allChannels = allChannels,
            preferences = preferences,
            compact = compact,
            onSaveOrder = { newChannelOrder ->
                onUpdatePreferences(
                    preferences.copy(customChannelOrder = newChannelOrder)
                )
            },
            onDismiss = { showChannelReorderModal = false }
        )
    }
}

@Composable
internal fun BouquetReorderDialog(
    rawBouquets: List<BouquetSummary>,
    preferences: PlayerPreferences,
    compact: Boolean,
    onSave: (List<String>, Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var orderedNames by remember(rawBouquets, preferences.customBouquetOrder) {
        val currentSorted = CustomOrderHelper.sortBouquets(
            rawBouquets,
            preferences.customBouquetOrder,
            preferences.hiddenBouquets,
            includeHidden = true
        ).map { it.name }
        mutableStateOf(currentSorted)
    }
    var hiddenSet by remember(preferences.hiddenBouquets) {
        mutableStateOf(preferences.hiddenBouquets)
    }
    val countsMap = remember(rawBouquets) { rawBouquets.associate { it.name to it.count } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 660.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(BeinSurface)
                .border(2.dp, BeinHighlightPurple, RoundedCornerShape(18.dp))
                .padding(if (compact) 12.dp else 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "📂 إخفاء الباقات غير المهمة وترتيبها بالريموت (▲ / ▼)",
                        color = Color.White,
                        fontSize = if (compact) 16.sp else 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                BeinFocusableButton(
                    text = "حفظ وإغلاق ✓",
                    isPrimary = true,
                    compact = true,
                    onClick = {
                        onSave(orderedNames, hiddenSet)
                        onDismiss()
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(orderedNames, key = { idx, name -> "${idx}_${name}" }) { idx, bName ->
                    val isHidden = when {
                        bName == MultiQualityEngine.BOUQUET_NAME -> CustomOrderHelper.isMultiQualityBouquetHidden(hiddenSet)
                        bName.startsWith("★") -> CustomOrderHelper.isFavoritesBouquetHidden(hiddenSet) || bName in hiddenSet
                        else -> bName in hiddenSet
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isHidden) Color(0xFF1A0D18) else BeinCard)
                            .border(1.dp, if (isHidden) CrimsonAlert.copy(alpha = 0.5f) else BeinBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. $bName (${countsMap[bName] ?: 0})" + if (isHidden) " [مخفية]" else "",
                            color = if (isHidden) Color.Gray else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 13.sp else 15.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            DpadSmallControlBtn(
                                label = if (isHidden) "👁 إظهار" else "🚫 إخفاء",
                                enabled = true,
                                onClick = {
                                    hiddenSet = when {
                                        bName == MultiQualityEngine.BOUQUET_NAME ->
                                            CustomOrderHelper.toggleMultiQualityBouquetHidden(hiddenSet)
                                        bName.startsWith("★") ->
                                            CustomOrderHelper.toggleFavoritesBouquetHidden(hiddenSet, bName)
                                        isHidden -> hiddenSet - bName
                                        else -> hiddenSet + bName
                                    }
                                    onSave(orderedNames, hiddenSet)
                                }
                            )
                            DpadSmallControlBtn(
                                label = "⏫ للأول",
                                enabled = idx > 0,
                                onClick = {
                                    orderedNames = CustomOrderHelper.moveItem(orderedNames, idx, 0)
                                    onSave(orderedNames, hiddenSet)
                                }
                            )
                            DpadSmallControlBtn(
                                label = "▲",
                                enabled = idx > 0,
                                onClick = {
                                    orderedNames = CustomOrderHelper.moveItem(orderedNames, idx, idx - 1)
                                    onSave(orderedNames, hiddenSet)
                                }
                            )
                            DpadSmallControlBtn(
                                label = "▼",
                                enabled = idx < orderedNames.lastIndex,
                                onClick = {
                                    orderedNames = CustomOrderHelper.moveItem(orderedNames, idx, idx + 1)
                                    onSave(orderedNames, hiddenSet)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChannelReorderDialog(
    allChannels: List<TvChannel>,
    preferences: PlayerPreferences,
    compact: Boolean,
    onSaveOrder: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val groups = remember(allChannels) {
        listOf("الكل") + allChannels.map { it.group }.distinct()
    }
    var selectedBouquet by remember { mutableStateOf(groups.firstOrNull() ?: "الكل") }
    var channelList by remember(allChannels, selectedBouquet) {
        val filtered = if (selectedBouquet == "الكل") allChannels else allChannels.filter { it.group == selectedBouquet }
        mutableStateOf(filtered)
    }

    fun persistNewList(updatedGroupChannels: List<TvChannel>) {
        channelList = updatedGroupChannels
        val updatedKeys = updatedGroupChannels.map { it.stableKey }
        val updatedSet = updatedKeys.toHashSet()
        val fullKeys = mutableListOf<String>()
        fullKeys.addAll(updatedKeys)
        for (ch in allChannels) {
            if (ch.stableKey !in updatedSet) {
                fullKeys.add(ch.stableKey)
            }
        }
        onSaveOrder(fullKeys)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 660.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(BeinSurface)
                .border(2.dp, BeinHighlightPurple, RoundedCornerShape(18.dp))
                .padding(if (compact) 12.dp else 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "📺 ترتيب القنوات بالأسهم (▲ لأعلى / ▼ لأسفل / ⏫ للأول)",
                        color = Color.White,
                        fontSize = if (compact) 15.sp else 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                BeinFocusableButton(
                    text = "تم ✓",
                    isPrimary = true,
                    compact = true,
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it }) { g ->
                    BeinFocusableButton(
                        text = g,
                        isPrimary = g == selectedBouquet,
                        compact = true,
                        onClick = { selectedBouquet = g }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(channelList, key = { idx, ch -> "${idx}_${ch.stableKey}" }) { idx, ch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BeinCard)
                            .border(1.dp, BeinBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. ${ch.name} (${ch.group})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 13.sp else 15.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            DpadSmallControlBtn(
                                label = "⏫ للأول",
                                enabled = idx > 0,
                                onClick = {
                                    persistNewList(CustomOrderHelper.moveItem(channelList, idx, 0))
                                }
                            )
                            DpadSmallControlBtn(
                                label = "▲",
                                enabled = idx > 0,
                                onClick = {
                                    persistNewList(CustomOrderHelper.moveItem(channelList, idx, idx - 1))
                                }
                            )
                            DpadSmallControlBtn(
                                label = "▼",
                                enabled = idx < channelList.lastIndex,
                                onClick = {
                                    persistNewList(CustomOrderHelper.moveItem(channelList, idx, idx + 1))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DpadSmallControlBtn(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .scale(if (focused) 1.07f else 1.0f)
            .clip(RoundedCornerShape(7.dp))
            .background(
                when {
                    !enabled -> Color(0xFF0B111E)
                    focused -> Color(0xFF0284C7)
                    else -> BeinPrimaryPurple
                }
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) BeinFocusGlow else Color(0xFF38BDF8).copy(alpha = 0.5f),
                shape = RoundedCornerShape(7.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val code = ev.nativeKeyEvent.keyCode
                if (enabled && ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0 &&
                    (code == KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                        code == KeyEvent.KEYCODE_BUTTON_A)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable(enabled = enabled)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (!enabled) Color.Gray else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
