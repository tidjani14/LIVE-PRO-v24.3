package com.tvflux.pro
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError

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
                        if (isVpnActive) EmeraldLive else Color(0xFFE1B85A),
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
                            tint = if (isVpnActive) EmeraldLive else Color(0xFFE1B85A)
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
        DiagnosticAlertType.INTERNET_ERROR -> Color(0xFFE1B85A)
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
    isLoading: Boolean = false,
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
    var showSportsWebViewModal by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val expiryText = activePlaylistExpiry.ifBlank {
        subscription?.expireDate?.takeIf { it.isNotBlank() } ?: "غير محدود"
    }
    val isVpnPermanentOff = LiveProGlobalActions.vpnPermanentDisable
    val isVpnPaused = LiveProGlobalActions.vpnPausedTemporarily
    val isVpnConnected = (vpnStatus.connected || CloudflareWarpVpnService.isTunnelRunning) && !isVpnPermanentOff && !isVpnPaused
    val vpnDotColor = when {
        isVpnConnected -> Color(0xFF22C55E)
        isVpnPermanentOff -> Color(0xFFEF4444)
        else -> Color(0xFFE1B85A)
    }
    val vpnHeaderLabel = when {
        isVpnConnected -> "متصل"
        isVpnPermanentOff -> "متوقف نهائياً"
        else -> "غير متصل"
    }
    val data2BannerUrl = ChannelRepository.latestData2BannerUrl.trim()
    val wideLayout = !compact
    val gold = Color(0xFFD7A943)
    val softGold = Color(0xFFF5D98B)
    val textMuted = Color(0xFFB7B0A2)

    @Composable
    fun RowScope.DashboardTile(label: String, subtitle: String, kind: String, onClick: () -> Unit) {
        NovoDashboardTile(
            label = label,
            subtitle = subtitle,
            iconKind = kind,
            isTallCard = false,
            compact = compact,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onClick
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF0B0906), Color(0xFF050505))))
        ) {
            GoldenBokehFloorBackground(Modifier.fillMaxSize())
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 30.dp, vertical = if (compact) 4.dp else 7.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
            ) {
                // Premium top bar: brand, live VPN state, and copyable device ID.
                Row(
                    modifier = Modifier.fillMaxWidth().height(if (compact) 52.dp else 68.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WingedGoldenCrestLogo(compact = compact)
                        Box(Modifier.width(1.dp).height(if (compact) 30.dp else 42.dp).background(gold.copy(alpha = 0.7f)))
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("LIVE PRO", color = softGold, fontSize = if (compact) 17.sp else 26.sp, fontWeight = FontWeight.Black, letterSpacing = 1.25.sp, maxLines = 1)
                            Text("IPTV PLAYER", color = textMuted, fontSize = if (compact) 8.sp else 10.sp, letterSpacing = 2.2.sp, maxLines = 1)
                            if (compact) {
                                Text("الصلاحية: $expiryText", color = softGold, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp)) {
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xFF11100D))
                                .border(1.dp, gold.copy(alpha = 0.65f), RoundedCornerShape(30.dp))
                                .clickable { onOpenVpnControl() }
                                .padding(horizontal = if (compact) 8.dp else 13.dp, vertical = if (compact) 6.dp else 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "VPN", tint = softGold, modifier = Modifier.size(if (compact) 15.dp else 19.dp))
                            Text("VPN", color = Color.White, fontSize = if (compact) 9.sp else 12.sp, fontWeight = FontWeight.Bold)
                            Box(Modifier.size(7.dp).clip(CircleShape).background(vpnDotColor))
                            Text(vpnHeaderLabel, color = textMuted, fontSize = if (compact) 8.sp else 10.sp, maxLines = 1)
                        }
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xFF11100D))
                                .border(1.dp, Color(0xFF393124), RoundedCornerShape(30.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LIVE PRO Device ID", deviceId))
                                    android.widget.Toast.makeText(context, "تم نسخ رمز الجهاز", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 6.dp else 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = "Device ID", tint = softGold, modifier = Modifier.size(if (compact) 14.dp else 18.dp))
                            Text(if (showScreenCode) "ID: ${deviceId.take(15)}" else "ID: ••••••••", color = Color.White, fontSize = if (compact) 8.sp else 11.sp, maxLines = 1)
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", tint = textMuted, modifier = Modifier.size(if (compact) 12.dp else 15.dp))
                        }
                        if (!compact) {
                            Column(
                                modifier = Modifier.widthIn(max = 168.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    if (networkSpeedLabel.isNotBlank()) networkSpeedLabel else "0 KB/s",
                                    color = softGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "انتهاء الصلاحية: $expiryText",
                                    color = Color(0xFFF5D98B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = onToggleScreenCodeVisibility, modifier = Modifier.size(if (compact) 30.dp else 38.dp)) {
                            Icon(if (showScreenCode) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle device ID", tint = gold)
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, gold, Color.Transparent))))

                // Expiry is integrated into the top bar on phones; remove the empty status strip.

                // Six primary destinations. Three columns on TV, two on narrow phones.
                if (wideLayout) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardTile("البث المباشر", "$liveCount قناة متاحة", "LIVE_TV") { onOpenTab(MainTab.LIVE) }
                        DashboardTile("الأفلام", "$moviesCount فيلم", "MOVIES") { onOpenTab(MainTab.MOVIES) }
                        DashboardTile("المسلسلات", "$seriesCount مسلسل", "SERIES") { onOpenTab(MainTab.SERIES) }
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardTile("إدارة القوائم", "$playlistsCount قائمة", "PLAYLIST") { onOpenTab(MainTab.XTREAM) }
                        DashboardTile("المفضلة", "$favoritesCount عنصر", "FAVORITES") { onOpenTab(MainTab.FAVORITES) }
                        DashboardTile("الإعدادات", "المظهر والشبكة والتشغيل", "SETTINGS") { onOpenTab(MainTab.SETTINGS) }
                    }
                } else {
                    repeat(3) { rowIndex ->
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (rowIndex) {
                                0 -> {
                                    DashboardTile("البث المباشر", "$liveCount قناة", "LIVE_TV") { onOpenTab(MainTab.LIVE) }
                                    DashboardTile("الأفلام", "$moviesCount فيلم", "MOVIES") { onOpenTab(MainTab.MOVIES) }
                                }
                                1 -> {
                                    DashboardTile("المسلسلات", "$seriesCount مسلسل", "SERIES") { onOpenTab(MainTab.SERIES) }
                                    DashboardTile("إدارة القوائم", "$playlistsCount قائمة", "PLAYLIST") { onOpenTab(MainTab.XTREAM) }
                                }
                                else -> {
                                    DashboardTile("المفضلة", "$favoritesCount عنصر", "FAVORITES") { onOpenTab(MainTab.FAVORITES) }
                                    DashboardTile("الإعدادات", "المظهر والشبكة", "SETTINGS") { onOpenTab(MainTab.SETTINGS) }
                                }
                            }
                        }
                    }
                }

                // Secondary actions remain available without competing with the six main sections.
                Row(Modifier.fillMaxWidth().height(if (compact) 30.dp else 36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    @Composable fun RowScope.Action(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF11100D)).border(1.dp, Color(0xFF49391D), RoundedCornerShape(9.dp))
                                .clickable(onClick = click).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = label, tint = softGold, modifier = Modifier.size(if (compact) 13.dp else 16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, color = Color.White, fontSize = if (compact) 8.sp else 10.sp, maxLines = 1)
                        }
                    }
                    Action("الرياضة", Icons.Default.SportsSoccer) { showSportsWebViewModal = true }
                    Action("تفعيل", Icons.Default.VerifiedUser) { onOpenActivation() }
                    Action("تحديث", Icons.Default.Refresh) { onRefreshAll() }
                    Action("خروج", Icons.Default.ExitToApp) { onExit() }
                }
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = gold, trackColor = Color(0xFF292116))
                }
            }
        }
    }

    if (showSportsWebViewModal) {
        SportsFullscreenWebViewModal(
            sportsUrl = ChannelRepository.latestData3SportsUrl.trim(),
            onDismiss = { showSportsWebViewModal = false }
        )
    }
}

@Composable
internal fun GoldenBokehFloorBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val floorTop = h * 0.73f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF1B150A).copy(alpha = 0.45f),
                    Color(0xFF2A1F0D).copy(alpha = 0.72f),
                    Color(0xFF161108).copy(alpha = 0.85f)
                ),
                startY = floorTop,
                endY = h
            ),
            topLeft = Offset(0f, floorTop),
            size = Size(w, h - floorTop)
        )
        val count = 42
        for (i in 0 until count) {
            val fx = ((i * 73 + 19) % 100) / 100f
            val fy = 0.77f + (((i * 41 + 7) % 22) / 100f)
            val radius = (2.8f + (i % 6) * 2.2f)
            val alpha = 0.12f + (i % 5) * 0.06f
            drawCircle(
                color = if (i % 3 == 0) Color(0xFFFDE68A).copy(alpha = alpha) else Color(0xFFD49B35).copy(alpha = alpha),
                radius = radius,
                center = Offset(w * fx, h * fy)
            )
        }
    }
}

@Composable
internal fun WingedGoldenCrestLogo(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val goldMain = Color(0xFFD4A238)
    val goldLight = Color(0xFFFDE68A)
    val goldDark = Color(0xFF8A6214)
    // Wider royal crest makes the brand prominent and uses the header space effectively.
    val wDp = if (compact) 158.dp else 226.dp
    val hDp = if (compact) 50.dp else 72.dp

    Box(
        modifier = modifier.size(wDp, hDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.50f
            val shieldRx = w * 0.23f
            val shieldRy = h * 0.30f

            for (layer in 0..3) {
                val yOff = layer * (h * 0.085f)
                val xShrink = layer * (w * 0.042f)
                val wingL = Path().apply {
                    moveTo(cx - shieldRx * 0.90f, cy - h * 0.08f + yOff * 0.55f)
                    quadraticBezierTo(
                        cx - w * 0.30f + xShrink,
                        cy - h * 0.26f + yOff,
                        w * 0.03f + xShrink,
                        cy - h * 0.14f + yOff
                    )
                    quadraticBezierTo(
                        cx - w * 0.24f + xShrink,
                        cy + h * 0.02f + yOff,
                        cx - shieldRx * 0.82f,
                        cy + h * 0.05f + yOff * 0.45f
                    )
                    close()
                }
                drawPath(path = wingL, color = if (layer % 2 == 0) goldMain else goldLight.copy(alpha = 0.85f))
            }

            for (layer in 0..3) {
                val yOff = layer * (h * 0.085f)
                val xShrink = layer * (w * 0.042f)
                val wingR = Path().apply {
                    moveTo(cx + shieldRx * 0.90f, cy - h * 0.08f + yOff * 0.55f)
                    quadraticBezierTo(
                        cx + w * 0.30f - xShrink,
                        cy - h * 0.26f + yOff,
                        w * 0.97f - xShrink,
                        cy - h * 0.14f + yOff
                    )
                    quadraticBezierTo(
                        cx + w * 0.24f - xShrink,
                        cy + h * 0.02f + yOff,
                        cx + shieldRx * 0.82f,
                        cy + h * 0.05f + yOff * 0.45f
                    )
                    close()
                }
                drawPath(path = wingR, color = if (layer % 2 == 0) goldMain else goldLight.copy(alpha = 0.85f))
            }

            val crownPath = Path().apply {
                moveTo(cx - shieldRx * 0.52f, cy - shieldRy * 0.96f)
                lineTo(cx - shieldRx * 0.66f, cy - shieldRy * 1.48f)
                lineTo(cx - shieldRx * 0.26f, cy - shieldRy * 1.16f)
                lineTo(cx, cy - shieldRy * 1.66f)
                lineTo(cx + shieldRx * 0.26f, cy - shieldRy * 1.16f)
                lineTo(cx + shieldRx * 0.66f, cy - shieldRy * 1.48f)
                lineTo(cx + shieldRx * 0.52f, cy - shieldRy * 0.96f)
                close()
            }
            drawPath(path = crownPath, color = goldLight)

            drawRoundRect(
                color = Color(0xFF101620),
                topLeft = Offset(cx - shieldRx, cy - shieldRy),
                size = Size(shieldRx * 2f, shieldRy * 2f),
                cornerRadius = CornerRadius(shieldRy, shieldRy)
            )
            drawRoundRect(
                color = goldMain,
                topLeft = Offset(cx - shieldRx, cy - shieldRy),
                size = Size(shieldRx * 2f, shieldRy * 2f),
                cornerRadius = CornerRadius(shieldRy, shieldRy),
                style = Stroke(width = 2.2.dp.toPx())
            )
            drawRoundRect(
                color = goldLight.copy(alpha = 0.72f),
                topLeft = Offset(cx - shieldRx * 0.90f, cy - shieldRy * 0.82f),
                size = Size(shieldRx * 1.80f, shieldRy * 1.64f),
                cornerRadius = CornerRadius(shieldRy * 0.82f, shieldRy * 0.82f),
                style = Stroke(width = 1.dp.toPx())
            )

            drawRoundRect(
                color = goldDark,
                topLeft = Offset(cx - shieldRx * 0.95f, cy + shieldRy * 0.82f),
                size = Size(shieldRx * 1.90f, h * 0.11f),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
        Text(
            text = "LIVE PRO",
            color = goldLight,
            fontSize = if (compact) 7.5.sp else 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp
        )
    }
}

@Composable
private fun NovoDashboardTile(
    label: String,
    subtitle: String = "",
    iconKind: String,
    isTallCard: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onDirectionalKey: ((Int) -> Boolean)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 7.dp else 9.dp)
    val goldStroke = Color(0xFFD4A238)
    val tileBg = if (focused) Color(0xFF2A2112) else Color(0xFF15120D)

    Box(
        modifier = modifier
            .scale(if (focused) 1.025f else 1.0f)
            .clip(shape)
            .background(tileBg)
            .border(
                width = if (focused) 2.2.dp else 0.9.dp,
                color = if (focused) goldStroke else Color.White.copy(alpha = 0.08f),
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
                        SafeClickGuard.runThrottled("LIVEPRO_TILE_CLICK", 200L) { onClick() }
                    }
                    true
                } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                    onDirectionalKey?.invoke(keyCode) == true
                } else false
            }
            .focusable()
            .clickable { SafeClickGuard.runThrottled("LIVEPRO_TILE_CLICK", 200L) { onClick() } }
            .padding(if (compact) 7.dp else 11.dp),
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
                    compact -> 36.dp
                    else -> 58.dp
                },
                tint = if (focused) Color(0xFFFDE68A) else goldStroke
            )
            Spacer(Modifier.height(if (isTallCard) (if (compact) 10.dp else 18.dp) else (if (compact) 4.dp else 5.dp)))
            Text(
                text = label,
                color = Color.White,
                fontSize = when {
                    isTallCard && compact -> 15.sp
                    isTallCard -> 20.sp
                    compact -> 13.sp
                    else -> 17.sp
                },
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(if (compact) 2.dp else 3.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFFC8BDA8),
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NovoActionIconTile(
    iconKind: String,
    subLabel: String = "",
    statusDotColor: Color? = null,
    isSpinning: Boolean = false,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onDirectionalKey: ((Int) -> Boolean)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 7.dp else 9.dp)
    val goldStroke = Color(0xFFD4A238)

    Box(
        modifier = modifier
            .scale(if (focused) 1.04f else 1.0f)
            .clip(shape)
            .background(if (focused) Color(0xFF2A2112) else Color(0xFF12100C))
            .border(
                width = if (focused) 2.2.dp else 1.dp,
                color = if (focused) goldStroke else Color(0xFF5B4727),
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
                        SafeClickGuard.runThrottled("LIVEPRO_ACTION_CLICK", 200L) { onClick() }
                    }
                    true
                } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                    onDirectionalKey?.invoke(keyCode) == true
                } else false
            }
            .focusable()
            .clickable { SafeClickGuard.runThrottled("LIVEPRO_ACTION_CLICK", 200L) { onClick() } }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSpinning) {
            CircularProgressIndicator(
                color = if (focused) Color(0xFFFDE68A) else goldStroke,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(if (compact) 20.dp else 26.dp)
            )
        } else if (subLabel.isNotBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (statusDotColor != null) {
                        Box(
                            modifier = Modifier
                                .size(if (compact) 6.dp else 8.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                    }
                    NovoLineArtIcon(
                        kind = iconKind,
                        sizeDp = if (compact) 16.dp else 20.dp,
                        tint = if (focused) Color(0xFFFDE68A) else goldStroke
                    )
                    Text(
                        text = "VPN",
                        color = if (focused) Color(0xFFFDE68A) else goldStroke,
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = subLabel,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = if (compact) 8.5.sp else 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        } else {
            NovoLineArtIcon(
                kind = iconKind,
                sizeDp = if (compact) 22.dp else 28.dp,
                tint = if (focused) Color(0xFFFDE68A) else goldStroke
            )
        }
    }
}

@Composable
private fun NovoLineArtIcon(
    kind: String,
    sizeDp: androidx.compose.ui.unit.Dp,
    tint: Color
) {
    when (kind) {
        "VPN" -> Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "VPN",
            tint = tint,
            modifier = Modifier.size(sizeDp)
        )
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
        "FAVORITES" -> Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Favorites",
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
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.06f, h * 0.16f),
                        size = Size(w * 0.68f, h * 0.48f),
                        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
                        style = stroke
                    )
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
                    drawRoundRect(
                        color = Color(0xFF15120D),
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
                    drawCircle(
                        color = tint,
                        radius = w * 0.065f,
                        center = Offset(w * 0.78f, h * 0.62f),
                        style = thinStroke
                    )
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
                    val cx = w * 0.44f
                    val cy = h * 0.48f
                    val r = w * 0.34f
                    drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = stroke)
                    drawCircle(color = tint, radius = r * 0.18f, center = Offset(cx, cy), style = thinStroke)
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
                    val stripPath = Path().apply {
                        moveTo(cx + r * 0.45f, cy + r * 0.85f)
                        lineTo(w * 0.92f, cy + r * 0.45f)
                        lineTo(w * 0.86f, cy + r * 0.98f)
                        lineTo(cx + r * 0.25f, cy + r * 0.98f)
                    }
                    drawPath(path = stripPath, color = tint, style = thinStroke)
                }
                "SPORTS" -> {
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
                    drawLine(color = tint, start = Offset(w * 0.62f, h * 0.68f), end = Offset(w * 0.88f, h * 0.90f), strokeWidth = thinStroke.width, cap = StrokeCap.Round)
                    drawLine(color = tint, start = Offset(w * 0.48f, h * 0.76f), end = Offset(w * 0.72f, h * 0.94f), strokeWidth = thinStroke.width, cap = StrokeCap.Round)
                    drawLine(color = tint, start = Offset(w * 0.74f, h * 0.56f), end = Offset(w * 0.94f, h * 0.74f), strokeWidth = thinStroke.width, cap = StrokeCap.Round)
                }
                "SERIES" -> {
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Data2PromoBannerBox(
    bannerUrl: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val cleanUrl = bannerUrl.trim()
    val hasValidUrl = cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true)
    var loadFailed by remember(cleanUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 7.dp else 9.dp)

    Box(
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        if (hasValidUrl && !loadFailed) {
            val isImageUrl = cleanUrl.substringBefore("?").lowercase().let { low ->
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
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request == null || request.isForMainFrame) {
                                    loadFailed = true
                                    view?.visibility = android.view.View.GONE
                                }
                            }
                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                if (request == null || request.isForMainFrame) {
                                    loadFailed = true
                                    view?.visibility = android.view.View.GONE
                                }
                            }
                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.cancel()
                                loadFailed = true
                                view?.visibility = android.view.View.GONE
                            }
                        }
                        if (isImageUrl) {
                            val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'><style>html,body{margin:0;padding:0;width:100%;height:100%;background:transparent;overflow:hidden;}img{width:100%;height:100%;object-fit:cover;display:block;}</style></head><body><img src='$cleanUrl' onerror='this.remove()'/></body></html>"
                            loadDataWithBaseURL(cleanUrl, html, "text/html", "UTF-8", null)
                        } else {
                            loadUrl(cleanUrl)
                        }
                    }
                },
                update = { wv ->
                    if (wv.tag != cleanUrl) {
                        wv.tag = cleanUrl
                        wv.visibility = android.view.View.VISIBLE
                        if (isImageUrl) {
                            val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'><style>html,body{margin:0;padding:0;width:100%;height:100%;background:transparent;overflow:hidden;}img{width:100%;height:100%;object-fit:cover;display:block;}</style></head><body><img src='$cleanUrl' onerror='this.remove()'/></body></html>"
                            wv.loadDataWithBaseURL(cleanUrl, html, "text/html", "UTF-8", null)
                        } else {
                            wv.loadUrl(cleanUrl)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SportsFullscreenWebViewModal(
    sportsUrl: String,
    onDismiss: () -> Unit
) {
    val cleanUrl = sportsUrl.trim()
    val hasAdminUrl = cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true)
    var isPageLoading by remember(cleanUrl) { mutableStateOf(hasAdminUrl) }
    var pageLoadError by remember(cleanUrl) { mutableStateOf(false) }

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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF070707),
                            Color(0xFF131A24),
                            Color(0xFF080807)
                        )
                    )
                )
        ) {
            GoldenBokehFloorBackground(modifier = Modifier.fillMaxSize())

            if (hasAdminUrl && !pageLoadError) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isPageLoading = false
                                }
                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    if (request == null || request.isForMainFrame) {
                                        isPageLoading = false
                                        pageLoadError = true
                                        view?.visibility = android.view.View.GONE
                                    }
                                }
                                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                    if (request == null || request.isForMainFrame) {
                                        isPageLoading = false
                                        pageLoadError = true
                                        view?.visibility = android.view.View.GONE
                                    }
                                }
                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    handler?.cancel()
                                    isPageLoading = false
                                    pageLoadError = true
                                    view?.visibility = android.view.View.GONE
                                }
                            }
                            loadUrl(cleanUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!hasAdminUrl) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WingedGoldenCrestLogo(compact = false)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "بانتظار إدخال رابط صفحة الرياضة من قِبل الإدارة",
                        color = Color(0xFFE2E8F0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (isPageLoading && hasAdminUrl && !pageLoadError) {
                CircularProgressIndicator(
                    color = Color(0xFFD4A238),
                    strokeWidth = 3.2.dp,
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.Center)
                )
            }

            var closeFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .focusRequester(closeFocus)
                    .size(38.dp)
                    .scale(if (closeFocused) 1.12f else 1.0f)
                    .clip(CircleShape)
                    .background(if (closeFocused) Color(0xFFD4A238) else Color(0xCC19202B))
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
