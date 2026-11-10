package com.tvflux.pro

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
// (الوضع الكلاسيكي معتمد دائماً — تم إخفاء أزرار تبديل الأوضاع من الإعدادات)
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
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ClassicLiveProDashboard(
    deviceId: String,
    subscription: SubscriptionInfo?,
    vpnStatus: CloudflareTunnelStatus,
    networkSpeedLabel: String,
    activePlaylistExpiry: String = "",
    lastSelectedTab: MainTab = MainTab.LIVE,
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
    val hPad = if (compact) 16.dp else 36.dp
    val vPad = if (compact) 8.dp else 18.dp
    val gap = if (compact) 10.dp else 16.dp

    // State for Fullscreen Sports WebView (data3.txt)
    var showSportsWebViewModal by remember { mutableStateOf(false) }
    var lastClickedTileKey by remember(lastSelectedTab) {
        mutableStateOf(
            when (lastSelectedTab) {
                MainTab.MOVIES -> "movies"
                MainTab.SERIES -> "series"
                MainTab.XTREAM -> "playlists"
                MainTab.SETTINGS -> "settings"
                else -> "live"
            }
        )
    }

    // FocusRequesters for the exact 8 tiles in the screenshot:
    // Col 1: Live TV (tall)
    // Col 2: Movies (top), Sports (bottom)
    // Col 3: Series (top), Change Playlist (bottom)
    // Col 4 bottom: Settings, Refresh, Exit
    val liveTileFocus = remember { FocusRequester() }
    val moviesTileFocus = remember { FocusRequester() }
    val sportsTileFocus = remember { FocusRequester() }
    val seriesTileFocus = remember { FocusRequester() }
    val playlistsTileFocus = remember { FocusRequester() }
    val settingsTileFocus = remember { FocusRequester() }
    val refreshTileFocus = remember { FocusRequester() }
    val exitTileFocus = remember { FocusRequester() }

    // Because we wrap the dashboard in LayoutDirection.Ltr so it matches the screenshot 100%,
    // DPAD_LEFT always moves visually left and DPAD_RIGHT always moves visually right!
    fun moveDashboardFocus(tile: String, keyCode: Int): Boolean {
        val target = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> when (tile) {
                "live" -> moviesTileFocus
                "movies" -> seriesTileFocus
                "sports" -> playlistsTileFocus
                "series" -> settingsTileFocus
                "playlists" -> settingsTileFocus
                "settings" -> refreshTileFocus
                "refresh" -> exitTileFocus
                else -> null
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> when (tile) {
                "movies" -> liveTileFocus
                "sports" -> liveTileFocus
                "series" -> moviesTileFocus
                "playlists" -> sportsTileFocus
                "settings" -> playlistsTileFocus
                "refresh" -> settingsTileFocus
                "exit" -> refreshTileFocus
                else -> null
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> when (tile) {
                "movies" -> sportsTileFocus
                "series" -> playlistsTileFocus
                else -> null
            }
            KeyEvent.KEYCODE_DPAD_UP -> when (tile) {
                "sports" -> moviesTileFocus
                "playlists" -> seriesTileFocus
                "settings", "refresh", "exit" -> seriesTileFocus
                else -> null
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

    // Restore focus to the exact tile the user returned from (e.g. Movies -> Movies, Sports -> Sports, Series -> Series)
    LaunchedEffect(lastClickedTileKey, showSportsWebViewModal) {
        if (!showSportsWebViewModal) {
            val targetFocus = when (lastClickedTileKey) {
                "movies" -> moviesTileFocus
                "sports" -> sportsTileFocus
                "series" -> seriesTileFocus
                "playlists" -> playlistsTileFocus
                "settings" -> settingsTileFocus
                "refresh" -> refreshTileFocus
                "exit" -> exitTileFocus
                else -> liveTileFocus
            }
            for (attempt in 1..6) {
                delay(45L)
                if (runCatching { targetFocus.requestFocus() }.isSuccess) break
            }
        }
    }

    val expiryText = activePlaylistExpiry.ifBlank {
        subscription?.expireDate?.takeIf { it.isNotBlank() } ?: "26/04/2027"
    }
    val data2BannerUrl = ChannelRepository.latestData2BannerUrl.trim()
    val data3SportsUrl = ChannelRepository.latestData3SportsUrl.trim()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF040406))
        ) {
            // Subtle Golden Bokeh Sparkle Floor at the bottom (matching screenshot)
            GoldenBokehFloorBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = hPad, vertical = vPad),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Top Header: Centered Winged Golden Crest Logo + Top-Right Expiry Text ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 46.dp else 68.dp)
                ) {
                    WingedGoldenCrestLogo(
                        modifier = Modifier.align(Alignment.Center),
                        compact = compact
                    )

                    Text(
                        text = "Current playlist expires: $expiryText",
                        color = Color(0xFFE2E8F0),
                        fontSize = if (compact) 11.sp else 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = if (compact) 2.dp else 4.dp)
                    )
                }

                Spacer(Modifier.height(if (compact) 6.dp else 12.dp))

                // ── Main 4-Column Content Area (Exact proportions & alignment as screenshot) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = if (compact) 14.dp else 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    // Column 1 (Left): Tall Live TV Card
                    NovoDashboardTile(
                        label = "Live TV",
                        iconKind = "LIVE_TV",
                        isTallCard = true,
                        compact = compact,
                        modifier = Modifier
                            .weight(1.06f)
                            .fillMaxHeight()
                            .focusRequester(liveTileFocus),
                        onDirectionalKey = { key -> moveDashboardFocus("live", key) },
                        onClick = {
                            lastClickedTileKey = "live"
                            onOpenTab(MainTab.LIVE)
                        }
                    )

                    // Column 2 (Middle-Left): Movies (top) + Sports (bottom — opens data3.txt fullscreen WebView)
                    Column(
                        modifier = Modifier
                            .weight(0.94f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        NovoDashboardTile(
                            label = "Movies",
                            iconKind = "MOVIES",
                            isTallCard = false,
                            compact = compact,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(moviesTileFocus),
                            onDirectionalKey = { key -> moveDashboardFocus("movies", key) },
                            onClick = {
                                lastClickedTileKey = "movies"
                                onOpenTab(MainTab.MOVIES)
                            }
                        )
                        NovoDashboardTile(
                            label = "Sports",
                            iconKind = "SPORTS",
                            isTallCard = false,
                            compact = compact,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(sportsTileFocus),
                            onDirectionalKey = { key -> moveDashboardFocus("sports", key) },
                            onClick = {
                                lastClickedTileKey = "sports"
                                showSportsWebViewModal = true
                            }
                        )
                    }

                    // Column 3 (Middle-Right): Series (top) + Change Playlist (bottom)
                    Column(
                        modifier = Modifier
                            .weight(0.94f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        NovoDashboardTile(
                            label = "Series",
                            iconKind = "SERIES",
                            isTallCard = false,
                            compact = compact,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(seriesTileFocus),
                            onDirectionalKey = { key -> moveDashboardFocus("series", key) },
                            onClick = {
                                lastClickedTileKey = "series"
                                onOpenTab(MainTab.SERIES)
                            }
                        )
                        NovoDashboardTile(
                            label = "Change Playlist",
                            iconKind = "PLAYLIST",
                            isTallCard = false,
                            compact = compact,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(playlistsTileFocus),
                            onDirectionalKey = { key -> moveDashboardFocus("playlists", key) },
                            onClick = {
                                lastClickedTileKey = "playlists"
                                onOpenTab(MainTab.XTREAM)
                            }
                        )
                    }

                    // Column 4 (Right): Promo Banner WebView (data2.txt) on Top + 3 Icon Buttons (Settings, Refresh, Exit) on Bottom
                    Column(
                        modifier = Modifier
                            .weight(1.92f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        // Top Promo Image / WebView Box (loads direct link from data2.txt controlled by Admin)
                        Data2PromoBannerBox(
                            bannerUrl = data2BannerUrl,
                            compact = compact,
                            modifier = Modifier
                                .weight(0.75f)
                                .fillMaxWidth()
                        )

                        // Bottom 3 Icon Action Tiles: Settings (⚙️) | Refresh (↻) | Exit ([→)
                        Row(
                            modifier = Modifier
                                .weight(0.25f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            NovoActionIconTile(
                                iconKind = "SETTINGS",
                                compact = compact,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .focusRequester(settingsTileFocus),
                                onDirectionalKey = { key -> moveDashboardFocus("settings", key) },
                                onClick = {
                                    lastClickedTileKey = "settings"
                                    onOpenTab(MainTab.SETTINGS)
                                }
                            )
                            NovoActionIconTile(
                                iconKind = "REFRESH",
                                compact = compact,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .focusRequester(refreshTileFocus),
                                onDirectionalKey = { key -> moveDashboardFocus("refresh", key) },
                                onClick = {
                                    lastClickedTileKey = "refresh"
                                    onRefreshAll()
                                }
                            )
                            NovoActionIconTile(
                                iconKind = "EXIT",
                                compact = compact,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .focusRequester(exitTileFocus),
                                onDirectionalKey = { key -> moveDashboardFocus("exit", key) },
                                onClick = {
                                    lastClickedTileKey = "exit"
                                    onExit()
                                }
                            )
                        }
                    }
                }
            }

            // Bottom-Right Version Label (v3.8)
            Text(
                text = "v3.8",
                color = Color.White.copy(alpha = 0.90f),
                fontSize = if (compact) 12.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (compact) 14.dp else 22.dp, bottom = if (compact) 6.dp else 10.dp)
            )
        }
    }

    // ── Fullscreen Sports WebView Modal (يعرض صفحة برابط مباشر من ملف data3.txt تغطي كل الشاشة) ──
    if (showSportsWebViewModal) {
        SportsFullscreenWebViewModal(
            sportsUrl = data3SportsUrl.ifBlank { "https://www.ysscores.com/ar/matches-today" },
            onDismiss = { showSportsWebViewModal = false }
        )
    }
}

/**
 * خلفية البوكيه الذهبي في أسفل الشاشة الرئيسية وصفحة القوائم (مطابقة للصورة المرفقة)
 */
@Composable
internal fun GoldenBokehFloorBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val floorTop = h * 0.72f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF1A1205).copy(alpha = 0.55f),
                    Color(0xFF2D1E08).copy(alpha = 0.85f),
                    Color(0xFF140D03)
                ),
                startY = floorTop,
                endY = h
            ),
            topLeft = Offset(0f, floorTop),
            size = Size(w, h - floorTop)
        )
        // Deterministic golden bokeh particles across the bottom floor
        val count = 48
        for (i in 0 until count) {
            val fx = ((i * 73 + 19) % 100) / 100f
            val fy = 0.76f + (((i * 41 + 7) % 23) / 100f)
            val radius = (3f + (i % 6) * 2.5f)
            val alpha = 0.14f + (i % 5) * 0.08f
            drawCircle(
                color = if (i % 3 == 0) Color(0xFFFDE68A).copy(alpha = alpha) else Color(0xFFD49B35).copy(alpha = alpha),
                radius = radius,
                center = Offset(w * fx, h * fy)
            )
        }
    }
}

/**
 * الشعار المجنح الذهبي (Winged Golden Crest Logo) المطابق للصورة المرفقة
 */
@Composable
internal fun WingedGoldenCrestLogo(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val goldMain = Color(0xFFD4A238)
    val goldLight = Color(0xFFFDE68A)
    val goldDark = Color(0xFF8A6214)
    val wDp = if (compact) 110.dp else 156.dp
    val hDp = if (compact) 44.dp else 64.dp

    Box(
        modifier = modifier.size(wDp, hDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.48f
            val r = h * 0.28f

            // Left Wing (3 layered golden feather sweeps)
            for (layer in 0..3) {
                val yOff = layer * (h * 0.09f)
                val xShrink = layer * (w * 0.045f)
                val wingL = Path().apply {
                    moveTo(cx - r * 0.85f, cy - h * 0.08f + yOff * 0.6f)
                    quadraticBezierTo(
                        cx - w * 0.28f + xShrink,
                        cy - h * 0.26f + yOff,
                        w * 0.04f + xShrink,
                        cy - h * 0.14f + yOff
                    )
                    quadraticBezierTo(
                        cx - w * 0.22f + xShrink,
                        cy + h * 0.02f + yOff,
                        cx - r * 0.75f,
                        cy + h * 0.04f + yOff * 0.5f
                    )
                    close()
                }
                drawPath(path = wingL, color = if (layer % 2 == 0) goldMain else goldLight.copy(alpha = 0.85f))
            }

            // Right Wing (3 layered golden feather sweeps)
            for (layer in 0..3) {
                val yOff = layer * (h * 0.09f)
                val xShrink = layer * (w * 0.045f)
                val wingR = Path().apply {
                    moveTo(cx + r * 0.85f, cy - h * 0.08f + yOff * 0.6f)
                    quadraticBezierTo(
                        cx + w * 0.28f - xShrink,
                        cy - h * 0.26f + yOff,
                        w * 0.96f - xShrink,
                        cy - h * 0.14f + yOff
                    )
                    quadraticBezierTo(
                        cx + w * 0.22f - xShrink,
                        cy + h * 0.02f + yOff,
                        cx + r * 0.75f,
                        cy + h * 0.04f + yOff * 0.5f
                    )
                    close()
                }
                drawPath(path = wingR, color = if (layer % 2 == 0) goldMain else goldLight.copy(alpha = 0.85f))
            }

            // Top Crown above the central shield
            val crownPath = Path().apply {
                moveTo(cx - r * 0.55f, cy - r * 0.95f)
                lineTo(cx - r * 0.70f, cy - r * 1.45f)
                lineTo(cx - r * 0.28f, cy - r * 1.15f)
                lineTo(cx, cy - r * 1.62f)
                lineTo(cx + r * 0.28f, cy - r * 1.15f)
                lineTo(cx + r * 0.70f, cy - r * 1.45f)
                lineTo(cx + r * 0.55f, cy - r * 0.95f)
                close()
            }
            drawPath(path = crownPath, color = goldLight)

            // Central dark circle with double golden ring
            drawCircle(color = Color(0xFF0A0A0E), radius = r * 1.05f, center = Offset(cx, cy))
            drawCircle(color = goldMain, radius = r * 1.05f, center = Offset(cx, cy), style = Stroke(width = 2.2.dp.toPx()))
            drawCircle(color = goldLight.copy(alpha = 0.75f), radius = r * 0.86f, center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))

            // Bottom Golden Ribbon
            drawRoundRect(
                color = goldDark,
                topLeft = Offset(cx - r * 1.35f, cy + r * 0.78f),
                size = Size(r * 2.70f, h * 0.13f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = goldMain,
                topLeft = Offset(cx - r * 1.15f, cy + r * 0.74f),
                size = Size(r * 2.30f, h * 0.13f),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
        Text(
            text = "NOVO",
            color = goldLight,
            fontSize = if (compact) 7.5.sp else 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp
        )
    }
}

/**
 * بطاقة القسم في الواجهة الرئيسية بنفس اللون الداكن (#1D242B) والأيقونة الذهبية المرسومة وحجم الخط في الصورة المرفقة
 */
@Composable
private fun NovoDashboardTile(
    label: String,
    iconKind: String,
    isTallCard: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onDirectionalKey: ((Int) -> Boolean)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)
    val goldStroke = Color(0xFFD4A238)
    val tileBg = if (focused) Color(0xFF26303A) else Color(0xFF1D242B)

    Box(
        modifier = modifier
            .scale(if (focused) 1.025f else 1.0f)
            .clip(shape)
            .background(tileBg)
            .border(
                width = if (focused) 2.2.dp else 0.8.dp,
                color = if (focused) goldStroke else Color.White.copy(alpha = 0.06f),
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val keyCode = ev.nativeKeyEvent.keyCode
                val isSelect = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (isSelect) {
                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                        SafeClickGuard.runThrottled("NOVO_TILE_CLICK", 200L) { onClick() }
                    }
                    true
                } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                    onDirectionalKey?.invoke(keyCode) == true
                } else false
            }
            .focusable()
            .clickable { SafeClickGuard.runThrottled("NOVO_TILE_CLICK", 200L) { onClick() } }
            .padding(if (compact) 8.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            NovoLineArtIcon(
                kind = iconKind,
                sizeDp = when {
                    isTallCard && compact -> 64.dp
                    isTallCard -> 96.dp
                    compact -> 38.dp
                    else -> 54.dp
                },
                tint = if (focused) Color(0xFFFDE68A) else goldStroke
            )
            Spacer(Modifier.height(if (isTallCard) (if (compact) 16.dp else 28.dp) else (if (compact) 8.dp else 14.dp)))
            Text(
                text = label,
                color = Color.White,
                fontSize = when {
                    isTallCard && compact -> 15.sp
                    isTallCard -> 20.sp
                    compact -> 12.sp
                    else -> 15.sp
                },
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * أزرار الأيقونات الثلاثة تحت البانر (Settings ⚙️ | Refresh ↻ | Exit [→) بنفس الشكل في الصورة
 */
@Composable
private fun NovoActionIconTile(
    iconKind: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onDirectionalKey: ((Int) -> Boolean)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)
    val goldStroke = Color(0xFFD4A238)

    Box(
        modifier = modifier
            .scale(if (focused) 1.04f else 1.0f)
            .clip(shape)
            .background(if (focused) Color(0xFF221E15) else Color(0xCC121417))
            .border(
                width = if (focused) 2.2.dp else 1.dp,
                color = if (focused) goldStroke else Color(0xFF3E3422),
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val keyCode = ev.nativeKeyEvent.keyCode
                val isSelect = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (isSelect) {
                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                        SafeClickGuard.runThrottled("NOVO_ACTION_CLICK", 200L) { onClick() }
                    }
                    true
                } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                    onDirectionalKey?.invoke(keyCode) == true
                } else false
            }
            .focusable()
            .clickable { SafeClickGuard.runThrottled("NOVO_ACTION_CLICK", 200L) { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        NovoLineArtIcon(
            kind = iconKind,
            sizeDp = if (compact) 22.dp else 30.dp,
            tint = if (focused) Color(0xFFFDE68A) else goldStroke
        )
    }
}

/**
 * الأيقونات الذهبية المرسومة بالخطوط (Line-Art) المطابقة للصورة المرفقة بالضبط:
 * - LIVE_TV: شاشة تلفاز مع ريموت كونترول على يمينها
 * - MOVIES: بكرة سينما مع شريط فلم
 * - SPORTS: كرة قدم مع خطوط حركة
 * - SERIES: شاشة مع مثلث تشغيل
 * - PLAYLIST: 3 طبقات سيرفرات متراصة
 * - SETTINGS / REFRESH / EXIT
 */
@Composable
private fun NovoLineArtIcon(
    kind: String,
    sizeDp: androidx.compose.ui.unit.Dp,
    tint: Color
) {
    when (kind) {
        "SETTINGS" -> Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = tint,
            modifier = Modifier.size(sizeDp)
        )
        "REFRESH" -> Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh",
            tint = tint,
            modifier = Modifier.size(sizeDp)
        )
        "EXIT" -> Icon(
            imageVector = Icons.Default.ExitToApp,
            contentDescription = "Exit",
            tint = tint,
            modifier = Modifier.size(sizeDp)
        )
        else -> Canvas(modifier = Modifier.size(sizeDp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = (w * 0.055f).coerceAtLeast(3f), cap = StrokeCap.Round)
            val thinStroke = Stroke(width = (w * 0.038f).coerceAtLeast(2f), cap = StrokeCap.Round)

            when (kind) {
                "LIVE_TV" -> {
                    // Widescreen TV monitor on left/center
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.06f, h * 0.16f),
                        size = Size(w * 0.68f, h * 0.48f),
                        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
                        style = stroke
                    )
                    // TV stand neck & base
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.34f, h * 0.64f),
                        end = Offset(w * 0.30f, h * 0.78f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.46f, h * 0.64f),
                        end = Offset(w * 0.50f, h * 0.78f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.22f, h * 0.78f),
                        end = Offset(w * 0.58f, h * 0.78f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                    // Remote Control overlapping on the right
                    drawRoundRect(
                        color = Color(0xFF1D242B),
                        topLeft = Offset(w * 0.64f, h * 0.34f),
                        size = Size(w * 0.28f, h * 0.52f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                    )
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.64f, h * 0.34f),
                        size = Size(w * 0.28f, h * 0.52f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                        style = stroke
                    )
                    // Remote D-Pad circle in middle of remote
                    drawCircle(
                        color = tint,
                        radius = w * 0.065f,
                        center = Offset(w * 0.78f, h * 0.62f),
                        style = thinStroke
                    )
                    // Remote small button dots
                    for (r in 0..1) {
                        for (c in 0..1) {
                            drawCircle(
                                color = tint,
                                radius = w * 0.018f,
                                center = Offset(w * (0.72f + c * 0.12f), h * (0.43f + r * 0.07f))
                            )
                        }
                    }
                }
                "MOVIES" -> {
                    // Film reel outer circle
                    val cx = w * 0.44f
                    val cy = h * 0.48f
                    val r = w * 0.34f
                    drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = stroke)
                    drawCircle(color = tint, radius = r * 0.18f, center = Offset(cx, cy), style = thinStroke)
                    // 4 reel holes around center
                    val offsets = listOf(
                        Offset(0f, -r * 0.55f),
                        Offset(0f, r * 0.55f),
                        Offset(-r * 0.55f, 0f),
                        Offset(r * 0.55f, 0f)
                    )
                    for (off in offsets) {
                        drawCircle(
                            color = tint,
                            radius = r * 0.18f,
                            center = Offset(cx + off.x, cy + off.y),
                            style = thinStroke
                        )
                    }
                    // Trailing film strip at bottom-right
                    val stripPath = Path().apply {
                        moveTo(cx + r * 0.45f, cy + r * 0.85f)
                        lineTo(w * 0.92f, cy + r * 0.45f)
                        lineTo(w * 0.86f, cy + r * 0.98f)
                        lineTo(cx + r * 0.25f, cy + r * 0.98f)
                    }
                    drawPath(path = stripPath, color = tint, style = thinStroke)
                }
                "SPORTS" -> {
                    // Soccer ball circle + pentagon lines + 3 speed motion lines
                    val cx = w * 0.44f
                    val cy = h * 0.44f
                    val r = w * 0.30f
                    drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = stroke)
                    drawCircle(color = tint, radius = r * 0.42f, center = Offset(cx, cy), style = thinStroke)
                    for (angleDeg in listOf(0, 72, 144, 216, 288)) {
                        val rad = Math.toRadians(angleDeg.toDouble())
                        val sx = cx + (r * 0.42f * Math.cos(rad)).toFloat()
                        val sy = cy + (r * 0.42f * Math.sin(rad)).toFloat()
                        val ex = cx + (r * 0.98f * Math.cos(rad)).toFloat()
                        val ey = cy + (r * 0.98f * Math.sin(rad)).toFloat()
                        drawLine(color = tint, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = thinStroke.width)
                    }
                    // Motion lines toward bottom-right
                    drawLine(color = tint, start = Offset(w * 0.62f, h * 0.68f), end = Offset(w * 0.88f, h * 0.90f), strokeWidth = thinStroke.width, cap = StrokeCap.Round)
                    drawLine(color = tint, start = Offset(w * 0.48f, h * 0.76f), end = Offset(w * 0.72f, h * 0.94f), strokeWidth = thinStroke.width, cap = StrokeCap.Round)
                    drawLine(color = tint, start = Offset(w * 0.74f, h * 0.56f), end = Offset(w * 0.94f, h * 0.74f), strokeWidth = thinStroke.width, cap = StrokeCap.Round)
                }
                "SERIES" -> {
                    // Monitor screen with bottom line + play triangle in center
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.12f, h * 0.18f),
                        size = Size(w * 0.76f, h * 0.56f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                        style = stroke
                    )
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.14f, h * 0.62f),
                        end = Offset(w * 0.86f, h * 0.62f),
                        strokeWidth = thinStroke.width
                    )
                    val playPath = Path().apply {
                        moveTo(w * 0.43f, h * 0.31f)
                        lineTo(w * 0.61f, h * 0.41f)
                        lineTo(w * 0.43f, h * 0.51f)
                        close()
                    }
                    drawPath(path = playPath, color = tint, style = thinStroke)
                }
                "PLAYLIST" -> {
                    // 3 stacked server rack tiers with left circle & right dots
                    val tierH = h * 0.21f
                    val gapY = h * 0.06f
                    for (t in 0..2) {
                        val topY = h * 0.14f + t * (tierH + gapY)
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(w * 0.12f, topY),
                            size = Size(w * 0.76f, tierH),
                            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
                            style = stroke
                        )
                        drawCircle(
                            color = tint,
                            radius = tierH * 0.22f,
                            center = Offset(w * 0.26f, topY + tierH * 0.5f),
                            style = thinStroke
                        )
                        for (d in 0..3) {
                            drawCircle(
                                color = tint,
                                radius = w * 0.016f,
                                center = Offset(w * (0.54f + d * 0.07f), topY + tierH * 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * صندوق البانر في يمين الواجهة الرئيسية (يعرض الصورة/الرابط المباشر من ملف data2.txt عبر WebView، مع تصميم ذهبي احترافي كخلفية/احتياطي)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Data2PromoBannerBox(
    bannerUrl: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)
    val goldMain = Color(0xFFD4A238)
    val goldLight = Color(0xFFFDE68A)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF281C08), Color(0xFF0E0B06), Color(0xFF060608))
                )
            )
            .border(1.dp, Color(0xFF3E3422), shape),
        contentAlignment = Alignment.Center
    ) {
        // Built-in luxury PLAYER NOVO / LIVE PRO golden artwork (visible immediately or if data2.txt is empty/loading)
        GoldenBokehFloorBackground(modifier = Modifier.fillMaxSize())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 8.dp else 14.dp)
        ) {
            Text(
                text = "PLAYER",
                color = Color(0xFFE2E8F0),
                fontSize = if (compact) 18.sp else 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp
            )
            Text(
                text = "NOVO",
                color = goldLight,
                fontSize = if (compact) 26.sp else 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Text(
                text = "تجهز لكأس العالم",
                color = Color.White,
                fontSize = if (compact) 12.sp else 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(goldMain)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "لا تفوت الفرصة!",
                    color = Color.Black,
                    fontSize = if (compact) 9.5.sp else 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // WebView overlay loading direct image/page URL from data2.txt when configured by Admin
        if (bannerUrl.startsWith("http://", ignoreCase = true) || bannerUrl.startsWith("https://", ignoreCase = true)) {
            val isImageUrl = bannerUrl.substringBefore("?").lowercase().let { low ->
                low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".webp") || low.endsWith(".gif") || low.endsWith(".svg")
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isFocusable = false
                        isFocusableInTouchMode = false
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                        }
                        if (isImageUrl) {
                            val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'><style>html,body{margin:0;padding:0;width:100%;height:100%;background:transparent;overflow:hidden;}img{width:100%;height:100%;object-fit:cover;display:block;}</style></head><body><img src='$bannerUrl' onerror='this.remove()'/></body></html>"
                            loadDataWithBaseURL(bannerUrl, html, "text/html", "UTF-8", null)
                        } else {
                            loadUrl(bannerUrl)
                        }
                    }
                },
                update = { wv ->
                    if (wv.tag != bannerUrl) {
                        wv.tag = bannerUrl
                        if (isImageUrl) {
                            val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'><style>html,body{margin:0;padding:0;width:100%;height:100%;background:transparent;overflow:hidden;}img{width:100%;height:100%;object-fit:cover;display:block;}</style></head><body><img src='$bannerUrl' onerror='this.remove()'/></body></html>"
                            wv.loadDataWithBaseURL(bannerUrl, html, "text/html", "UTF-8", null)
                        } else {
                            wv.loadUrl(bannerUrl)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * نافذة Sports بكامل الشاشة (تفتح الرابط المباشر من ملف data3.txt الذي يتحكم به الأدمن وتغطي 100% من الشاشة)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SportsFullscreenWebViewModal(
    sportsUrl: String,
    onDismiss: () -> Unit
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { closeFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                        }
                        loadUrl(sportsUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Discreet Close / Back Button in top-right corner for Remote & Touch
            var closeFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .focusRequester(closeFocus)
                    .size(38.dp)
                    .scale(if (closeFocused) 1.12f else 1.0f)
                    .clip(CircleShape)
                    .background(if (closeFocused) Color(0xFFD4A238) else Color(0xCC121417))
                    .border(1.5.dp, if (closeFocused) Color.White else Color(0xFFD4A238), CircleShape)
                    .onFocusChanged { closeFocused = it.isFocused }
                    .onKeyEvent { ev ->
                        val code = ev.nativeKeyEvent.keyCode
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)
                        ) {
                            onDismiss()
                            true
                        } else false
                    }
                    .focusable()
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = if (closeFocused) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
