package com.tvflux.pro

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

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
    onUpdatePreferences: (PlayerPreferences) -> Unit
) {
    val context = LocalContext.current
    val lang = preferences.appLanguage
    var cacheSizeLabel by remember { mutableStateOf(FluxBitmapCache.getCacheSizeMb(context)) }
    var showBouquetReorderModal by remember { mutableStateOf(false) }
    var showChannelReorderModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        item {
            Text(
                text = "⚙️ ${AppStrings.tr(lang, "إعدادات التطبيق واللغة وإدارة الباقات", "Paramètres, Langue & Gestion des Bouquets", "App Settings, Language & Bouquet Management")}",
                color = Color.White,
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // 0. UI Theme Customization (Classic LIVE PRO Classic vs Modern Royal) + Low-RAM Device Acceleration + Network Speed
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(1.8.dp, BeinHighlightPurple, RoundedCornerShape(14.dp))
                    .padding(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "🎨 ${AppStrings.tr(lang, "تخصيص مظهر الواجهة وتسريع الأجهزة الضعيفة وعرض سرعة الشبكة:", "Personnalisation de l'interface, Accélération & Vitesse réseau :", "UI Theme Customization, Low-RAM Acceleration & Network Speed:")}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 14.sp else 17.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UiLayoutStyle.values().forEach { styleOpt ->
                        BeinFocusableButton(
                            text = (if (preferences.uiLayoutStyle == styleOpt) "✓ " else "") + styleOpt.label(lang),
                            isPrimary = preferences.uiLayoutStyle == styleOpt,
                            compact = compact,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onUpdatePreferences(preferences.copy(uiLayoutStyle = styleOpt))
                            }
                        )
                    }
                }
                Text(
                    text = "🎬 ${AppStrings.tr(lang, "اختر وضع محرك المشغل المدمج (ExoPlayer + FFmpeg الشامل لكل الترميزات):", "Choisir le mode du moteur (ExoPlayer + FFmpeg Universel) :", "Select Internal Player Engine Mode (ExoPlayer + FFmpeg All-Codec):")}",
                    color = Color(0xFFE2E8F0),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 13.sp else 15.sp
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
                }
            }
        }

        // 1. Application Language Selector (Arabic / Français / English)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(1.5.dp, BeinHighlightPurple, RoundedCornerShape(14.dp))
                    .padding(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🌐 ${AppStrings.tr(lang, "لغة التطبيق (العربية / Français / English):", "Langue de l'application :", "Application Language:")}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 14.sp else 17.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppUiLanguage.values().forEach { lOpt ->
                        BeinFocusableButton(
                            text = lOpt.nativeLabel,
                            isPrimary = preferences.appLanguage == lOpt,
                            compact = compact,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onUpdatePreferences(preferences.copy(appLanguage = lOpt))
                            }
                        )
                    }
                }
            }
        }

        // 2. Hide Unwanted Bouquets & Reorder Bouquets / Channels
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(1.5.dp, BeinBorder, RoundedCornerShape(14.dp))
                    .padding(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📂 ${AppStrings.tr(lang, "إخفاء الباقات غير المهمة وترتيب الباقات والقنوات بالريموت:", "Masquer les bouquets inutiles et réorganiser :", "Hide Unwanted Bouquets & Reorder via Remote:")}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 14.sp else 17.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BeinFocusableButton(
                        text = "👁 ${AppStrings.tr(lang, "إخفاء / ترتيب الباقات", "Masquer / Trier Bouquets", "Hide / Reorder Bouquets")} (${rawBouquets.size})",
                        isPrimary = true,
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

                // Direct 1-Click Toggles for hiding/showing "متعدد جودات" and "★ المفضلة" in the Bouquets sidebar
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

        // 2.B Anti-Freeze & Internet Fluctuation Cushion Settings (إعدادات منع التقطيع ومقاومة هزات الإنترنت)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(1.5.dp, BeinHighlightPurple, RoundedCornerShape(14.dp))
                    .padding(if (compact) 10.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🛡️ ${AppStrings.tr(lang, "نظام منع التقطيع ومقاومة هزات الإنترنت (Anti-Freeze Engine):", "Système Anti-Coupure & Stabilité Réseau :", "Anti-Freeze & Network Jitter Cushion:")}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 14.sp else 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
                                    bpFocused -> Color.White
                                    selected -> BeinPrimaryPurple
                                    else -> BeinSurface
                                }
                            )
                            .border(
                                width = if (bpFocused) 2.5.dp else if (selected) 1.5.dp else 1.dp,
                                color = if (bpFocused) BeinFocusGlow else if (selected) Color.White else BeinBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .onFocusChanged { bpFocused = it.isFocused }
                            .focusable()
                            .clickable { onUpdatePreferences(preferences.copy(bufferProfile = bp)) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = (if (selected) "✓ " else "") + bp.titleAr,
                            color = if (bpFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 13.sp else 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = bp.descAr,
                            color = if (bpFocused) Color.DarkGray else Color(0xFFE2E8F0),
                            fontSize = if (compact) 11.sp else 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 3. Stream Acceleration Control (2 Buttons Only: Start Stream Acceleration / Stop)
        item {
            val isVpnActive = vpnStatus.connected && !vpnPausedTemporarily && !LiveProGlobalActions.vpnPermanentDisable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(
                        1.8.dp,
                        if (isVpnActive) EmeraldLive else Color(0xFFF59E0B),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isVpnActive) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isVpnActive) EmeraldLive else Color(0xFFF59E0B)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isVpnActive) {
                                    AppStrings.tr(lang, "⚡ تسريع البث: قيد التشغيل ✓", "⚡ Accélération du flux : Activée ✓", "⚡ Stream Acceleration: Active ✓")
                                } else {
                                    AppStrings.tr(lang, "🛑 تسريع البث: متوقف", "🛑 Accélération du flux : Arrêtée", "🛑 Stream Acceleration: Stopped")
                                },
                                color = if (isVpnActive) EmeraldLive else Color(0xFFFDE68A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 14.sp else 16.sp
                            )
                        }
                    }
                }

                // خيارات التحكم الكامل بالـ VPN: 1) تشغيل وتفعيل تلقائي  2) إيقاف مؤقت للجلسة  3) إغلاق دائم حتى يشغله المستخدم يدوياً
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BeinFocusableButton(
                        text = AppStrings.tr(
                            lang,
                            "🚀 تشغيل الـ VPN الآن",
                            "🚀 Activer VPN maintenant",
                            "🚀 Start VPN Now"
                        ),
                        isPrimary = isVpnActive,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = {
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
                        }
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
                        onClick = {
                            LiveProGlobalActions.updateVpnPrefs(
                                context = context,
                                permanentDisable = false,
                                autoStart = true,
                                mode = VpnConnectionMode.WIREGUARD_FULL,
                                askOnStartup = false
                            )
                            LiveProGlobalActions.onPauseVpnTemporarily()
                        }
                    )
                    BeinFocusableButton(
                        text = if (LiveProGlobalActions.vpnPermanentDisable) {
                            AppStrings.tr(lang, "🔒 مغلق دائماً ✓", "🔒 Désactivé définitivement ✓", "🔒 Permanently Off ✓")
                        } else {
                            AppStrings.tr(lang, "🔒 إغلاق VPN بشكل دائم", "🔒 Désactiver VPN définitivement", "🔒 Disable VPN Permanently")
                        },
                        isPrimary = LiveProGlobalActions.vpnPermanentDisable,
                        compact = compact,
                        modifier = Modifier.weight(1.15f),
                        onClick = {
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
            }
        }

        // 3.B Manual OTA App Update Check Button in Settings ("البحث عن تحديثات التطبيق يدوياً")
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(1.8.dp, EmeraldLive, RoundedCornerShape(14.dp))
                    .padding(if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                "📲 تحديثات التطبيق وترقية النسخة (الإصدار الحالي: v${ChannelRepository.CURRENT_APP_VERSION_CODE}.0)",
                                "📲 Mises à jour de l'application (Version actuelle : v${ChannelRepository.CURRENT_APP_VERSION_CODE}.0)",
                                "📲 App Updates & Upgrade (Current Version: v${ChannelRepository.CURRENT_APP_VERSION_CODE}.0)"
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 14.sp else 16.sp
                        )
                        Text(
                            text = AppStrings.tr(
                                lang,
                                "ابحث يدوياً عن أي تحديث جديد مرسل من الإدارة ليتم تنزيله وتثبيته مباشرة فوق النسخة الحالية",
                                "Rechercher manuellement une nouvelle mise à jour pour la télécharger et l'installer directement",
                                "Manually check for a new update from Admin to download and install directly"
                            ),
                            color = Color(0xFFE2E8F0),
                            fontSize = if (compact) 11.5.sp else 13.sp
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
            }
        }

        // 4. Thumbnail Cache Quota & Software Decoder Toggle
        item {
            var perfMetrics by remember { mutableStateOf<DatabasePerformanceMetrics?>(null) }
            LaunchedEffect(allChannels.size, cacheSizeLabel) {
                perfMetrics = TiviMateDiagnostics.buildSnapshot(context)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(1.5.dp, BeinHighlightPurple, RoundedCornerShape(14.dp))
                    .padding(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🖼️ إدارة ذاكرة الصور المصغرة ($cacheSizeLabel / الحد: ${preferences.thumbnailCacheLimit.titleAr})",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 13.sp else 15.sp,
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
                    BeinFocusableButton(
                        text = if (preferences.preferSoftwareDecoder) "🎞 فك الترميز: برمجي (SW)" else "🎞 فك الترميز: تلقائي (HW+SW)",
                        isPrimary = preferences.preferSoftwareDecoder,
                        compact = true,
                        modifier = Modifier.weight(1.4f),
                        onClick = {
                            onUpdatePreferences(preferences.copy(preferSoftwareDecoder = !preferences.preferSoftwareDecoder))
                        }
                    )
                }

                // SQLite & Codec Diagnostics Summary
                val m = perfMetrics
                if (m != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BeinDarkBg)
                            .border(1.dp, BeinBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📊 المكتبة المحلية: ${m.totalChannelsInDb} قناة • ${m.totalMoviesInDb} فيلم • ${m.totalSeriesInDb} مسلسل",
                            color = EmeraldLive,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 12.sp else 14.sp
                        )
                        Text(
                            text = "💾 حجم قاعدة البيانات: ${m.dbFileSizeMb} • الذاكرة العشوائية (RAM): ${m.ramUsedMb}/${m.ramMaxMb} MB • زمن البحث: ${m.lastQueryTimeMs}ms",
                            color = Color(0xFFE2E8F0),
                            fontSize = if (compact) 11.sp else 13.sp
                        )
                        Text(
                            text = "🎬 ترميزات الفيديو المدعومة بالجهاز: ${m.supportedVideoCodecs.joinToString(" ، ")}",
                            color = Color.White,
                            fontSize = if (compact) 11.sp else 13.sp
                        )
                        Text(
                            text = "🔊 ترميزات الصوت المدعومة بالجهاز: ${m.supportedAudioCodecs.joinToString(" ، ")}",
                            color = Color.LightGray,
                            fontSize = if (compact) 11.sp else 13.sp
                        )
                        Text(
                            text = "🔄 آخر مزامنة تفاضلية: ${m.lastSyncSummary}",
                            color = Color(0xFFCBD5E1),
                            fontSize = if (compact) 11.sp else 13.sp
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
                    !enabled -> Color(0xFF130D1E)
                    focused -> Color.White
                    else -> BeinPrimaryPurple
                }
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) BeinFocusGlow else Color.White.copy(alpha = 0.4f),
                shape = RoundedCornerShape(7.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (enabled && ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
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
            color = if (!enabled) Color.Gray else if (focused) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}


/**
 * Smart Diagnostic Popup Modal for:
 * 1) Expired Xtream Account (ACCOUNT_EXPIRED)
 * 2) Invalid Username/Password (AUTH_INVALID)
 * 3) Customer Internet Connection Issue (INTERNET_ERROR)
 * 4) Temporary Stream Server Outage (SERVER_OUTAGE)
 */
@Composable
internal fun SmartDiagnosticAlertModal(
    alert: DiagnosticAlertInfo,
    deviceId: String,
    compact: Boolean,
    onOpenCustomerSpace: () -> Unit,
    onRetrySync: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isExpiredAlert = alert.type == DiagnosticAlertType.ACCOUNT_EXPIRED
    val borderColor = when (alert.type) {
        DiagnosticAlertType.ACCOUNT_EXPIRED -> CrimsonAlert
        DiagnosticAlertType.AUTH_INVALID -> Color(0xFFF97316)
        DiagnosticAlertType.INTERNET_ERROR -> Color(0xFFF59E0B)
        DiagnosticAlertType.SERVER_OUTAGE -> BeinHighlightPurple
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = if (compact) 520.dp else 620.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF120724), BeinSurface, BeinDarkBg)
                    )
                )
                .border(2.dp, borderColor, RoundedCornerShape(22.dp))
                .padding(if (compact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            Text(
                text = alert.title,
                color = if (isExpiredAlert) Color(0xFFFCA5A5) else Color.White,
                fontSize = if (compact) 17.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = alert.message,
                color = Color(0xFFE2E8F0),
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BeinDarkBg)
                    .border(1.dp, BeinBorder, RoundedCornerShape(12.dp))
                    .clickable { copyDeviceCodeToClipboard(context, deviceId) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🖥️ رمز الشاشة الخاص بجهازك",
                    color = EmeraldLive,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = deviceId,
                        color = Color.White,
                        fontSize = if (compact) 14.sp else 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isExpiredAlert) {
                    BeinFocusableButton(
                        text = "📲 فتح فضاء الزبون",
                        isPrimary = true,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenCustomerSpace
                    )
                } else {
                    BeinFocusableButton(
                        text = "🔄 إعادة المحاولة الآن",
                        isPrimary = true,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = onRetrySync
                    )
                }
                BeinFocusableButton(
                    text = "حسناً، فهمت ✓",
                    isPrimary = false,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
            }
        }
    }
}


/**
 * VOD Resume Playback vs Start Over Prompt Modal (استئناف المشاهدة من حيث توقفت أو البدء من البداية)
 */
@Composable
internal fun VodResumePromptModal(
    channel: TvChannel,
    progress: VodWatchProgress,
    lang: AppUiLanguage,
    compact: Boolean,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit
) {
    val resumeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(90L)
        runCatching { resumeFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 310.dp, max = if (compact) 460.dp else 540.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF140828), BeinSurface, BeinDarkBg)
                    )
                )
                .border(2.dp, EmeraldLive, RoundedCornerShape(22.dp))
                .padding(if (compact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = AppStrings.tr(
                    lang,
                    "⏯ متابعة المشاهدة من حيث توقفت",
                    "⏯ Reprendre la lecture",
                    "⏯ Resume Playback"
                ),
                color = Color.White,
                fontSize = if (compact) 17.sp else 19.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = channel.name,
                color = Color(0xFFE2E8F0),
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C241B))
                    .border(1.dp, EmeraldLive, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "⏱ ما شاهدته سابقاً: ${progress.formattedPosition} من أصل ${progress.formattedDuration} (${(progress.progressFraction * 100).toInt()}%)",
                    color = EmeraldLive,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x55000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.progressFraction.coerceIn(0.05f, 1f))
                            .background(EmeraldLive)
                    )
                }
            }

            BeinFocusableButton(
                text = AppStrings.tr(
                    lang,
                    "▶ استئناف المشاهدة من (${progress.formattedPosition})",
                    "▶ Reprendre à (${progress.formattedPosition})",
                    "▶ Resume from (${progress.formattedPosition})"
                ),
                isPrimary = true,
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(resumeFocus),
                onClick = onResume
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BeinFocusableButton(
                    text = AppStrings.tr(lang, "🔄 البدء من البداية", "🔄 Recommencer", "🔄 Start Over"),
                    isPrimary = false,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                    onClick = onStartOver
                )
                BeinFocusableButton(
                    text = AppStrings.tr(lang, "إلغاء", "Annuler", "Cancel"),
                    isPrimary = false,
                    compact = compact,
                    modifier = Modifier.weight(0.7f),
                    onClick = onDismiss
                )
            }
        }
    }
}

/**
 * Authentic Classic LIVE PRO Classic Dashboard Layout (الشكل الكلاسيكي مثل LIVE PRO Classic)
 */
@Composable
internal fun ClassicLiveProDashboard(
    deviceId: String,
    subscription: SubscriptionInfo?,
    vpnStatus: CloudflareTunnelStatus,
    networkSpeedLabel: String,
    showScreenCode: Boolean = true,
    onToggleScreenCodeVisibility: () -> Unit = {},
    liveCount: Int,
    moviesCount: Int,
    seriesCount: Int,
    playlistsCount: Int,
    favoritesCount: Int,
    continueWatchingCount: Int,
    lang: AppUiLanguage,
    compact: Boolean,
    onOpenTab: (MainTab) -> Unit,
    onSwitchToModernUi: () -> Unit,
    onOpenPlaylistSelectorModal: () -> Unit = {},
    onOpenQuickAddAccount: () -> Unit = { LiveProGlobalActions.onOpenQuickAddAccount() },
    onOpenVpnControl: () -> Unit = { LiveProGlobalActions.onOpenVpnControl() },
    onRefreshAll: () -> Unit,
    onOpenActivation: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val maskedDeviceId = if (showScreenCode) deviceId else "DEV-••••-••••"
    val hPad = if (compact) 10.dp else 22.dp
    val vPad = if (compact) 6.dp else 12.dp
    val gap = if (compact) 8.dp else 14.dp
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Live Clock & Date in Top Bar (matching reference image: 21:47 + Arabic date)
    var currentClockText by remember { mutableStateOf("") }
    var currentDateText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
        val dateFmt = java.text.SimpleDateFormat("EEEE dd MMMM", java.util.Locale("ar"))
        while (true) {
            val now = java.util.Date()
            currentClockText = timeFmt.format(now)
            currentDateText = runCatching { dateFmt.format(now) }.getOrDefault("")
            delay(15_000L)
        }
    }

    // FocusRequesters for the 2x3 Matte Obsidian & Gold Grid:
    // Row 1 (RTL right-to-left: الأفلام -> البث المباشر -> المسلسلات)
    val moviesTileFocus = remember { FocusRequester() }
    val liveTileFocus = remember { FocusRequester() }
    val seriesTileFocus = remember { FocusRequester() }
    // Row 2 (RTL right-to-left: إدارة القوائم -> المفضلة -> الإعدادات)
    val playlistsTileFocus = remember { FocusRequester() }
    val favoritesTileFocus = remember { FocusRequester() }
    val settingsTileFocus = remember { FocusRequester() }

    // Top Header Bar FocusRequesters
    val topCodeEyeFocus = remember { FocusRequester() }
    val topVpnFocus = remember { FocusRequester() }
    val topRefreshFocus = remember { FocusRequester() }
    val topSettingsFocus = remember { FocusRequester() }
    val topExitFocus = remember { FocusRequester() }

    fun moveDashboardFocus(tile: String, keyCode: Int): Boolean {
        // Visual order in RTL:
        // Row 1: Right = movies, Center = live, Left = series
        // Row 2: Right = playlists, Center = favorites, Left = settings
        val target = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isRtl) {
                when (tile) {
                    "series" -> liveTileFocus
                    "live" -> moviesTileFocus
                    "settings" -> favoritesTileFocus
                    "favorites" -> playlistsTileFocus
                    else -> null
                }
            } else {
                when (tile) {
                    "movies" -> liveTileFocus
                    "live" -> seriesTileFocus
                    "playlists" -> favoritesTileFocus
                    "favorites" -> settingsTileFocus
                    else -> null
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isRtl) {
                when (tile) {
                    "movies" -> liveTileFocus
                    "live" -> seriesTileFocus
                    "playlists" -> favoritesTileFocus
                    "favorites" -> settingsTileFocus
                    else -> null
                }
            } else {
                when (tile) {
                    "series" -> liveTileFocus
                    "live" -> moviesTileFocus
                    "settings" -> favoritesTileFocus
                    "favorites" -> playlistsTileFocus
                    else -> null
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> when (tile) {
                "movies" -> playlistsTileFocus
                "live" -> favoritesTileFocus
                "series" -> settingsTileFocus
                else -> null
            }
            KeyEvent.KEYCODE_DPAD_UP -> when (tile) {
                "movies" -> topCodeEyeFocus
                "live" -> topVpnFocus
                "series" -> topRefreshFocus
                "playlists" -> moviesTileFocus
                "favorites" -> liveTileFocus
                "settings" -> seriesTileFocus
                else -> topVpnFocus
            }
            else -> null
        }
        if (target != null) {
            runCatching { target.requestFocus() }
            return true
        }
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN
    }

    LaunchedEffect(Unit) {
        delay(90L)
        runCatching { liveTileFocus.requestFocus() }
    }

    val goldPrimary = Color(0xFFD4AF37)
    val goldBright = Color(0xFFF59E0B)
    val goldLight = Color(0xFFFDE68A)
    val obsidianBg = Color(0xFF060608)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0907), obsidianBg, Color(0xFF040406))
                )
            )
            .padding(horizontal = hPad, vertical = vPad),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 12.dp)
    ) {
        // ── 1. Top Royal Obsidian & Gold Command Bar (Exact Match to Reference Image) ──
        val topChain = remember {
            listOf(
                topCodeEyeFocus,
                topVpnFocus,
                topRefreshFocus,
                topSettingsFocus,
                topExitFocus
            )
        }

        fun handleTopBarDirectional(current: FocusRequester, keyCode: Int, downTarget: FocusRequester): Boolean {
            val idx = topChain.indexOf(current)
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    runCatching { downTarget.requestFocus() }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val nextIdx = if (isRtl) idx + 1 else idx - 1
                    if (nextIdx in topChain.indices) {
                        runCatching { topChain[nextIdx].requestFocus() }
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val prevIdx = if (isRtl) idx - 1 else idx + 1
                    if (prevIdx in topChain.indices) {
                        runCatching { topChain[prevIdx].requestFocus() }
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP -> true
                else -> false
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 42.dp else 54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF14110A), Color(0xFF0C0C10), Color(0xFF14110A))
                    )
                )
                .border(1.4.dp, goldPrimary.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                .padding(horizontal = if (compact) 10.dp else 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Right/Start: Crown + LIVE PRO — IPTV PLAYER
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
            ) {
                Text(
                    text = "👑",
                    fontSize = if (compact) 20.sp else 26.sp
                )
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "LIVE PRO",
                        color = goldLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 14.sp else 18.sp,
                        letterSpacing = 1.sp
                    )
                    if (!compact) {
                        Text(
                            text = "— IPTV PLAYER —",
                            color = goldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 1.1.sp
                        )
                    }
                }
            }

            // Gold vertical divider
            Box(
                modifier = Modifier
                    .padding(horizontal = if (compact) 6.dp else 10.dp)
                    .width(1.dp)
                    .height(if (compact) 22.dp else 28.dp)
                    .background(goldPrimary.copy(alpha = 0.45f))
            )

            // Screen ID Capsule: [ ID: DEV-EC84-FBFC 🖥️ ]
            var idFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .focusRequester(topCodeEyeFocus)
                    .height(if (compact) 30.dp else 36.dp)
                    .scale(if (idFocused) 1.05f else 1.0f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (idFocused) goldBright else Color(0xFF12100B))
                    .border(
                        width = if (idFocused) 2.2.dp else 1.2.dp,
                        color = if (idFocused) Color.White else goldPrimary.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(99.dp)
                    )
                    .onFocusChanged { idFocused = it.isFocused }
                    .onKeyEvent { ev ->
                        val code = ev.nativeKeyEvent.keyCode
                        val isSelect = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                            code == KeyEvent.KEYCODE_ENTER ||
                            code == KeyEvent.KEYCODE_NUMPAD_ENTER
                        if (isSelect) {
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                onToggleScreenCodeVisibility()
                            }
                            true
                        } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                            handleTopBarDirectional(topCodeEyeFocus, code, moviesTileFocus)
                        } else false
                    }
                    .focusable()
                    .clickable { onToggleScreenCodeVisibility() }
                    .padding(horizontal = if (compact) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "ID: $maskedDeviceId",
                        color = if (idFocused) Color.Black else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = if (compact) 11.sp else 13.sp,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = if (showScreenCode) Icons.Default.Tv else Icons.Default.VisibilityOff,
                    contentDescription = "ID",
                    tint = if (idFocused) Color.Black else goldPrimary,
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp)
                )
            }

            // Gold vertical divider
            Box(
                modifier = Modifier
                    .padding(horizontal = if (compact) 6.dp else 10.dp)
                    .width(1.dp)
                    .height(if (compact) 22.dp else 28.dp)
                    .background(goldPrimary.copy(alpha = 0.45f))
            )

            // Interactive VPN Switch Pill: [ 🛡️ VPN (●━━) | ● متصل ]
            val isVpnActive = vpnStatus.connected && !LiveProGlobalActions.vpnPausedTemporarily && !LiveProGlobalActions.vpnPermanentDisable
            var vpnFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .focusRequester(topVpnFocus)
                    .height(if (compact) 30.dp else 36.dp)
                    .scale(if (vpnFocused) 1.05f else 1.0f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (vpnFocused) goldBright else Color(0xFF0E1310))
                    .border(
                        width = if (vpnFocused) 2.2.dp else 1.2.dp,
                        color = if (vpnFocused) Color.White else if (isVpnActive) EmeraldLive.copy(alpha = 0.8f) else goldPrimary.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(99.dp)
                    )
                    .onFocusChanged { vpnFocused = it.isFocused }
                    .onKeyEvent { ev ->
                        val code = ev.nativeKeyEvent.keyCode
                        val isSelect = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                            code == KeyEvent.KEYCODE_ENTER ||
                            code == KeyEvent.KEYCODE_NUMPAD_ENTER
                        if (isSelect) {
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                onOpenVpnControl()
                            }
                            true
                        } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                            handleTopBarDirectional(topVpnFocus, code, liveTileFocus)
                        } else false
                    }
                    .focusable()
                    .clickable { onOpenVpnControl() }
                    .padding(horizontal = if (compact) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isVpnActive) "● متصل" else if (LiveProGlobalActions.vpnPermanentDisable) "● مغلق دائماً" else "● متوقف",
                    color = if (vpnFocused) Color.Black else if (isVpnActive) EmeraldLive else goldLight,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 10.5.sp else 12.5.sp
                )
                // Visual Toggle Switch Track
                Box(
                    modifier = Modifier
                        .width(if (compact) 30.dp else 36.dp)
                        .height(if (compact) 16.dp else 19.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (isVpnActive) EmeraldLive.copy(alpha = 0.32f) else Color(0xFF2C2C34))
                        .border(1.dp, if (isVpnActive) EmeraldLive else Color.Gray, RoundedCornerShape(99.dp))
                        .padding(horizontal = 2.dp),
                    contentAlignment = if (isVpnActive) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 12.dp else 14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
                Text(
                    text = "VPN 🛡️",
                    color = if (vpnFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 11.sp else 12.5.sp
                )
            }

            // Gold vertical divider
            Box(
                modifier = Modifier
                    .padding(horizontal = if (compact) 6.dp else 10.dp)
                    .width(1.dp)
                    .height(if (compact) 22.dp else 28.dp)
                    .background(goldPrimary.copy(alpha = 0.45f))
            )

            // Three Circular Gold-Bordered Action Icons: Refresh (↻), Settings (⚙️), Exit (⏻)
            @Composable
            fun GoldCircleTopIcon(
                icon: androidx.compose.ui.graphics.vector.ImageVector,
                contentDesc: String,
                focusReq: FocusRequester,
                downTarget: FocusRequester,
                onClick: () -> Unit
            ) {
                var f by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .focusRequester(focusReq)
                        .size(if (compact) 30.dp else 36.dp)
                        .scale(if (f) 1.12f else 1.0f)
                        .clip(CircleShape)
                        .background(if (f) goldBright else Color(0xFF14110A))
                        .border(if (f) 2.2.dp else 1.2.dp, if (f) Color.White else goldPrimary.copy(alpha = 0.8f), CircleShape)
                        .onFocusChanged { f = it.isFocused }
                        .onKeyEvent { ev ->
                            val code = ev.nativeKeyEvent.keyCode
                            val isSelect = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                                code == KeyEvent.KEYCODE_ENTER ||
                                code == KeyEvent.KEYCODE_NUMPAD_ENTER
                            if (isSelect) {
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                    onClick()
                                }
                                true
                            } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                handleTopBarDirectional(focusReq, code, downTarget)
                            } else false
                        }
                        .focusable()
                        .clickable { onClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDesc,
                        tint = if (f) Color.Black else goldLight,
                        modifier = Modifier.size(if (compact) 16.dp else 19.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoldCircleTopIcon(
                    icon = Icons.Default.Refresh,
                    contentDesc = "تحديث",
                    focusReq = topRefreshFocus,
                    downTarget = seriesTileFocus,
                    onClick = onRefreshAll
                )
                GoldCircleTopIcon(
                    icon = Icons.Default.Settings,
                    contentDesc = "الإعدادات",
                    focusReq = topSettingsFocus,
                    downTarget = seriesTileFocus,
                    onClick = { onOpenTab(MainTab.SETTINGS) }
                )
                GoldCircleTopIcon(
                    icon = Icons.Default.PowerSettingsNew,
                    contentDesc = "خروج",
                    focusReq = topExitFocus,
                    downTarget = seriesTileFocus,
                    onClick = onExit
                )
            }

            if (currentClockText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = if (compact) 6.dp else 10.dp)
                        .width(1.dp)
                        .height(if (compact) 22.dp else 28.dp)
                        .background(goldPrimary.copy(alpha = 0.45f))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentClockText,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 12.sp else 15.sp
                    )
                    if (!compact && currentDateText.isNotBlank()) {
                        Text(
                            text = currentDateText,
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp
                        )
                    }
                }
            }
        }

        // ── 2. Row 1: 3 Big Matte Obsidian & Gold Cards in the Same Row (الأفلام | البث المباشر | المسلسلات) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.12f),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            // Right Card in RTL: الأفلام
            ObsidianGoldDashboardCard(
                title = AppStrings.tr(lang, "الأفلام", "FILMS", "MOVIES"),
                subtitle = "🎞️ $moviesCount " + AppStrings.tr(lang, "فيلم", "Films", "Movies"),
                actionBadgeText = "▶ " + AppStrings.tr(lang, "مشاهدة الآن", "Regarder", "Watch Now"),
                iconEmoji = "🎬",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(moviesTileFocus),
                compact = compact,
                emphasized = false,
                onDirectionalKey = { key -> moveDashboardFocus("movies", key) },
                onClick = { onOpenTab(MainTab.MOVIES) }
            )

            // Center Hero Card: البث المباشر (Highlighted with Golden Spotlight Aura like the reference image)
            ObsidianGoldDashboardCard(
                title = AppStrings.tr(lang, "البث المباشر", "TV EN DIRECT", "LIVE TV"),
                subtitle = "((●)) $liveCount " + AppStrings.tr(lang, "قناة متاحة", "Chaînes", "Channels"),
                actionBadgeText = "▶ " + AppStrings.tr(lang, "مشاهدة الآن", "Regarder", "Watch Now"),
                iconEmoji = "📺",
                modifier = Modifier
                    .weight(1.06f)
                    .fillMaxHeight()
                    .focusRequester(liveTileFocus),
                compact = compact,
                emphasized = true,
                onDirectionalKey = { key -> moveDashboardFocus("live", key) },
                onClick = { onOpenTab(MainTab.LIVE) }
            )

            // Left Card in RTL: المسلسلات
            ObsidianGoldDashboardCard(
                title = AppStrings.tr(lang, "المسلسلات", "SÉRIES", "SERIES"),
                subtitle = "📺 $seriesCount " + AppStrings.tr(lang, "مسلسل كامل", "Séries", "Series"),
                actionBadgeText = "▶ " + AppStrings.tr(lang, "عرض الآن", "Parcourir", "Browse Now"),
                iconEmoji = "🎞️",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(seriesTileFocus),
                compact = compact,
                emphasized = false,
                onDirectionalKey = { key -> moveDashboardFocus("series", key) },
                onClick = { onOpenTab(MainTab.SERIES) }
            )
        }

        // ── 3. Row 2: 3 Matte Obsidian & Gold Cards in the Same Row (إدارة القوائم | المفضلة | الإعدادات) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.88f),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            // Right Card in RTL: إدارة القوائم
            ObsidianGoldDashboardCard(
                title = AppStrings.tr(lang, "إدارة القوائم", "GÉRER LISTES", "MANAGE LISTS"),
                subtitle = "☰ $playlistsCount " + AppStrings.tr(lang, "قائمة مضافة", "Listes", "Lists"),
                actionBadgeText = "▶ " + AppStrings.tr(lang, "إدارة", "Gérer", "Manage"),
                iconEmoji = "📁",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(playlistsTileFocus),
                compact = compact,
                emphasized = false,
                onDirectionalKey = { key -> moveDashboardFocus("playlists", key) },
                onClick = { onOpenTab(MainTab.XTREAM) }
            )

            // Center Card: المفضلة
            ObsidianGoldDashboardCard(
                title = AppStrings.tr(lang, "المفضلة", "FAVORIS", "FAVORITES"),
                subtitle = "❤️ $favoritesCount / $continueWatchingCount",
                actionBadgeText = "▶ " + AppStrings.tr(lang, "عرض", "Voir", "View"),
                iconEmoji = "⭐",
                modifier = Modifier
                    .weight(1.06f)
                    .fillMaxHeight()
                    .focusRequester(favoritesTileFocus),
                compact = compact,
                emphasized = false,
                onDirectionalKey = { key -> moveDashboardFocus("favorites", key) },
                onClick = { onOpenTab(MainTab.FAVORITES) }
            )

            // Left Card in RTL: الإعدادات
            ObsidianGoldDashboardCard(
                title = AppStrings.tr(lang, "الإعدادات", "PARAMÈTRES", "SETTINGS"),
                subtitle = AppStrings.tr(lang, "المظهر - الشبكة - السرعة", "Thème - Réseau - Vitesse", "Theme - Network - Speed"),
                actionBadgeText = "▶ " + AppStrings.tr(lang, "الدخول", "Entrer", "Enter"),
                iconEmoji = "⚙️",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(settingsTileFocus),
                compact = compact,
                emphasized = false,
                onDirectionalKey = { key -> moveDashboardFocus("settings", key) },
                onClick = { onOpenTab(MainTab.SETTINGS) }
            )
        }

        // ── 4. Sleek Golden Footer Bar (──── محتوى متنوع بجودة عالية ──── | ⓘ حول التطبيق) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 24.dp else 30.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B0A08))
                .border(1.dp, goldPrimary.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val speedTag = if (networkSpeedLabel.isNotBlank()) " • ⬇ $networkSpeedLabel" else ""
            Text(
                text = "────  " + AppStrings.tr(lang, "محتوى متنوع بجودة عالية", "Contenu varié en haute qualité", "High Quality Diverse Content") + "  ────",
                color = goldLight.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 10.sp else 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "ⓘ " + AppStrings.tr(lang, "حول التطبيق", "À propos", "About") + speedTag,
                color = goldPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 9.5.sp else 11.5.sp,
                modifier = Modifier.clickable {
                    Toast.makeText(
                        context,
                        "LIVE PRO v${ChannelRepository.CURRENT_APP_VERSION_CODE}.0 • ID: $deviceId",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
}

@Composable
internal fun ObsidianGoldDashboardCard(
    title: String,
    subtitle: String,
    actionBadgeText: String,
    iconEmoji: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    emphasized: Boolean = false,
    onDirectionalKey: ((Int) -> Boolean)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 18.dp else 22.dp)
    val goldBorder = Color(0xFFF59E0B)
    val goldSoft = Color(0xFFD4AF37)
    val isHighlighted = focused || emphasized

    val bgBrush = when {
        focused -> Brush.verticalGradient(
            listOf(Color(0xFF4A3410), Color(0xFF1F170A), Color(0xFF0D0B08))
        )
        emphasized -> Brush.verticalGradient(
            listOf(Color(0xFF33240D), Color(0xFF17130B), Color(0xFF0B0A08))
        )
        else -> Brush.verticalGradient(
            listOf(Color(0xFF1D1D22), Color(0xFF121216), Color(0xFF0A0A0D))
        )
    }

    val borderWidth = when {
        focused -> 2.8.dp
        emphasized -> 2.dp
        else -> 1.2.dp
    }
    val borderColor = when {
        focused -> Color(0xFFFFD700)
        emphasized -> goldBorder.copy(alpha = 0.85f)
        else -> goldSoft.copy(alpha = 0.42f)
    }

    val iconSize = when {
        compact && emphasized -> 30.sp
        compact -> 26.sp
        emphasized -> 40.sp
        else -> 34.sp
    }
    val titleSize = when {
        compact && emphasized -> 15.5.sp
        compact -> 14.sp
        emphasized -> 21.sp
        else -> 18.5.sp
    }
    val subSize = when {
        compact -> 11.sp
        emphasized -> 13.5.sp
        else -> 12.5.sp
    }

    Box(
        modifier = modifier
            .scale(if (focused) 1.035f else 1.0f)
            .clip(shape)
            .background(bgBrush)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val keyCode = ev.nativeKeyEvent.keyCode
                val isSelect = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (isSelect) {
                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                        SafeClickGuard.runThrottled("CLASSIC_TILE_CLICK", 200L) { onClick() }
                    }
                    true
                } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                    onDirectionalKey?.invoke(keyCode) == true
                } else false
            }
            .focusable()
            .clickable { SafeClickGuard.runThrottled("CLASSIC_TILE_CLICK", 200L) { onClick() } }
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 8.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Icon Container (Matte Dark Glass with Gold Frame)
            Box(
                modifier = Modifier
                    .size(if (compact) 42.dp else if (emphasized) 60.dp else 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D0C0A))
                    .border(
                        width = 1.2.dp,
                        color = if (isHighlighted) goldBorder.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconEmoji, fontSize = iconSize, textAlign = TextAlign.Center)
            }

            // Middle Title & Count Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = if (focused) Color(0xFFFDE68A) else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = titleSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Bold,
                    fontSize = subSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bottom Golden-Framed Action Pill (▶ مشاهدة الآن / ▶ عرض الآن / ▶ إدارة / ▶ عرض / ▶ الدخول)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (focused) goldBorder else Color(0xFF171209))
                    .border(
                        width = 1.3.dp,
                        color = if (focused) Color.White else goldBorder.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(99.dp)
                    )
                    .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 3.dp else 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionBadgeText,
                    color = if (focused) Color(0xFF08080C) else Color(0xFFFDE68A),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 10.sp else 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}
