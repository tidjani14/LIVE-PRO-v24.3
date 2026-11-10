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
import androidx.compose.ui.focus.focusProperties
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
    val firstSettingFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        for (attempt in 1..6) {
            delay(if (attempt <= 2) 25L else 45L)
            if (runCatching { firstSettingFocusRequester.requestFocus() }.isSuccess) break
        }
    }

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
                    InternalPlayerEngine.values().forEachIndexed { idx, engOpt ->
                        BeinFocusableButton(
                            text = (if (preferences.playerEngine == engOpt) "✓ " else "") + engOpt.label(lang),
                            isPrimary = preferences.playerEngine == engOpt,
                            compact = compact,
                            modifier = if (idx == 0) Modifier.weight(1f).focusRequester(firstSettingFocusRequester) else Modifier.weight(1f),
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
                                    bpFocused -> Color(0xFF5B21B6)
                                    selected -> BeinPrimaryPurple
                                    else -> BeinSurface
                                }
                            )
                            .border(
                                width = if (bpFocused) 2.5.dp else if (selected) 1.5.dp else 1.dp,
                                color = if (bpFocused) BeinFocusGlow else if (selected) Color(0xFF9D4EDD) else BeinBorder,
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
                        if (isVpnActive) EmeraldLive else Color(0xFF9D4EDD),
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
                            tint = if (isVpnActive) EmeraldLive else Color(0xFF9D4EDD)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isVpnActive) {
                                    AppStrings.tr(lang, "⚡ تسريع البث: قيد التشغيل ✓", "⚡ Accélération du flux : Activée ✓", "⚡ Stream Acceleration: Active ✓")
                                } else {
                                    AppStrings.tr(lang, "🛑 تسريع البث: متوقف", "🛑 Accélération du flux : Arrêtée", "🛑 Stream Acceleration: Stopped")
                                },
                                color = if (isVpnActive) EmeraldLive else Color(0xFFC4B5FD),
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
                    !enabled -> Color(0xFF0B111E)
                    focused -> Color(0xFF5B21B6)
                    else -> BeinPrimaryPurple
                }
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) BeinFocusGlow else Color(0xFF9D4EDD).copy(alpha = 0.5f),
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
        DiagnosticAlertType.INTERNET_ERROR -> Color(0xFF9D4EDD)
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
 * Ultra-Modern Dark Cinema & Broadcast OS Home Dashboard (`ClassicLiveProDashboard`)
 * - Eye-Comfort Deep Obsidian & Midnight Slate Palette (#050811 / #0D1526 / #131F36)
 * - Calm Electric Sky-Cyan (#38BDF8), Royal Sapphire (#1D4ED8), and Emerald (#10B981) Accents (Zero Harsh Yellow!)
 * - Deterministic 2D Matrix Remote Control (D-Pad) Navigation Engine across ALL rows & columns on all devices
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
        subscription?.expireDate?.takeIf { it.isNotBlank() }
            ?: (subscription?.daysLeft?.takeIf { it > 0 }?.let { "$it يوم" } ?: "غير محدود")
    }
    val isVpnPermanentOff = LiveProGlobalActions.vpnPermanentDisable
    val isVpnPaused = LiveProGlobalActions.vpnPausedTemporarily
    val isVpnConnected = (vpnStatus.connected || CloudflareWarpVpnService.isTunnelRunning) && !isVpnPermanentOff && !isVpnPaused
    val vpnDotColor = when {
        isVpnConnected -> Color(0xFF10B981)
        isVpnPermanentOff -> Color(0xFFEF4444)
        else -> Color(0xFF9D4EDD)
    }
    val vpnHeaderLabel = when {
        isVpnConnected -> AppStrings.tr(lang, "متصل ومحمي ✓", "Connecté ✓", "Protected ✓")
        isVpnPermanentOff -> AppStrings.tr(lang, "متوقف دائماً", "Désactivé", "Disabled")
        else -> AppStrings.tr(lang, "قيد الانتظار", "En attente", "Standby")
    }
    val wideLayout = !compact
    val cyanAccent = Color(0xFF9D4EDD)
    val cyanLight = Color(0xFFC4B5FD)
    val sapphirePrimary = Color(0xFF4B1680)
    val emeraldAccent = Color(0xFF10B981)
    val indigoAccent = Color(0xFF818CF8)
    val slateMuted = Color(0xFF94A3B8)

    // =========================================================================
    // Explicit 2D Matrix FocusRequesters for 100% Deterministic D-Pad Navigation
    // =========================================================================
    // Row 0: Top Status Bar [VPN Pill | Active Server Pill | Device ID Pill | Eye Toggle]
    val topVpnFocus = remember { FocusRequester() }
    val topServerFocus = remember { FocusRequester() }
    val topCodeFocus = remember { FocusRequester() }
    val topEyeFocus = remember { FocusRequester() }

    // Row 1: Primary Media Pillars [Live TV | Movies | Series]
    val liveTileFocus = remember { FocusRequester() }
    val moviesTileFocus = remember { FocusRequester() }
    val seriesTileFocus = remember { FocusRequester() }

    // Row 2: Secondary Hub [Playlists | Refresh | Settings]
    val playlistsTileFocus = remember { FocusRequester() }
    val refreshTileFocus = remember { FocusRequester() }
    val settingsTileFocus = remember { FocusRequester() }

    // Row 3: Bottom Quick Dock [Sports | Favorites | Activation | Exit]
    val sportsDockFocus = remember { FocusRequester() }
    val favsDockFocus = remember { FocusRequester() }
    val activationDockFocus = remember { FocusRequester() }
    val exitDockFocus = remember { FocusRequester() }

    fun moveFocusSafe(target: FocusRequester): Boolean {
        return runCatching { target.requestFocus() }.isSuccess
    }

    // Automatically land remote focus on the active/last-selected card when Dashboard opens or returns!
    LaunchedEffect(lastSelectedTab, wideLayout) {
        val initialTarget = when (lastSelectedTab) {
            MainTab.LIVE, MainTab.BOUQUETS -> liveTileFocus
            MainTab.MOVIES -> moviesTileFocus
            MainTab.SERIES -> seriesTileFocus
            MainTab.XTREAM -> playlistsTileFocus
            MainTab.SETTINGS -> settingsTileFocus
            MainTab.FAVORITES -> favsDockFocus
            MainTab.ACTIVATION -> activationDockFocus
        }
        for (attempt in 1..7) {
            delay(if (attempt <= 2) 20L else 45L)
            if (moveFocusSafe(initialTarget) || moveFocusSafe(liveTileFocus)) break
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF030108), Color(0xFF10071D), Color(0xFF030108))
                    )
                )
        ) {
            GoldenBokehFloorBackground(Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (compact) 12.dp else 26.dp,
                        vertical = if (compact) 6.dp else 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
            ) {
                // =========================================================================
                // ROW 0: Unified Glassmorphic Top Header Bar (4 Focusable Controls)
                // =========================================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 48.dp else 62.dp)
                        .clip(RoundedCornerShape(if (compact) 14.dp else 18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0B1528), Color(0xFF160B24), Color(0xFF0B1528))
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            color = Color(0xFF1E3252),
                            shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
                        )
                        .padding(horizontal = if (compact) 8.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 0.0: Left — Interactive VPN Shield & Stream Accelerator Pill
                    var vpnFocused by remember { mutableStateOf(false) }
                    val downFromVpn = liveTileFocus
                    Row(
                        modifier = Modifier
                            .focusRequester(topVpnFocus)
                            .focusProperties {
                                right = topServerFocus
                                down = downFromVpn
                                left = topVpnFocus
                                up = topVpnFocus
                            }
                            .scale(if (vpnFocused) 1.04f else 1.0f)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                when {
                                    vpnFocused -> Color(0xFF5B21B6)
                                    isVpnConnected -> Color(0xFF072B22)
                                    else -> Color(0xFF111C30)
                                }
                            )
                            .border(
                                width = if (vpnFocused) 2.4.dp else 1.2.dp,
                                color = when {
                                    vpnFocused -> Color(0xFFE9D5FF)
                                    isVpnConnected -> emeraldAccent.copy(alpha = 0.8f)
                                    else -> Color(0xFF284066)
                                },
                                shape = RoundedCornerShape(99.dp)
                            )
                            .onFocusChanged { vpnFocused = it.isFocused }
                            .onKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                    when (ev.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER,
                                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                                        KeyEvent.KEYCODE_BUTTON_A -> {
                                            SafeClickGuard.runThrottled("DASH_VPN_SWITCH", 220L) {
                                                onOpenVpnControl()
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(topServerFocus)
                                        KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(downFromVpn)
                                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .clickable {
                                SafeClickGuard.runThrottled("DASH_VPN_SWITCH", 220L) {
                                    onOpenVpnControl()
                                }
                            }
                            .padding(
                                horizontal = if (compact) 8.dp else 13.dp,
                                vertical = if (compact) 5.dp else 7.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "VPN",
                            tint = if (vpnFocused) Color.White else if (isVpnConnected) emeraldAccent else cyanAccent,
                            modifier = Modifier.size(if (compact) 14.dp else 18.dp)
                        )
                        Text(
                            text = "VPN",
                            color = Color.White,
                            fontSize = if (compact) 10.sp else 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            Modifier
                                .size(if (compact) 7.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (vpnFocused) Color.White else vpnDotColor)
                        )
                        if (!compact) {
                            Text(
                                text = vpnHeaderLabel,
                                color = if (vpnFocused) Color.White else Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    // 0.1: Center — Brand Crest + Active Server & Validity Switcher Pill
                    var serverPillFocused by remember { mutableStateOf(false) }
                    val downFromServer = if (wideLayout) moviesTileFocus else liveTileFocus
                    Row(
                        modifier = Modifier
                            .focusRequester(topServerFocus)
                            .focusProperties {
                                left = topVpnFocus
                                right = topCodeFocus
                                down = downFromServer
                                up = topServerFocus
                            }
                            .scale(if (serverPillFocused) 1.03f else 1.0f)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (serverPillFocused) Color(0xFF5B21B6) else Color(0xFF0E1A2F)
                            )
                            .border(
                                width = if (serverPillFocused) 2.4.dp else 1.dp,
                                color = if (serverPillFocused) Color(0xFFE9D5FF) else Color(0xFF233B5E),
                                shape = RoundedCornerShape(99.dp)
                            )
                            .onFocusChanged { serverPillFocused = it.isFocused }
                            .onKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                    when (ev.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER,
                                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                                        KeyEvent.KEYCODE_BUTTON_A -> {
                                            SafeClickGuard.runThrottled("DASH_SERVER_SWITCH", 200L) {
                                                onOpenPlaylistSelectorModal()
                                            }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(topVpnFocus)
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(topCodeFocus)
                                        KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(downFromServer)
                                        KeyEvent.KEYCODE_DPAD_UP -> true
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .clickable {
                                SafeClickGuard.runThrottled("DASH_SERVER_SWITCH", 200L) {
                                    onOpenPlaylistSelectorModal()
                                }
                            }
                            .padding(
                                horizontal = if (compact) 8.dp else 14.dp,
                                vertical = if (compact) 3.dp else 5.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
                    ) {
                        WingedGoldenCrestLogo(compact = true)
                        if (!compact) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(Color.White.copy(alpha = 0.22f))
                            )
                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = AppStrings.tr(
                                        lang,
                                        "📡 القوائم المتصلة ($playlistsCount) ▾",
                                        "📡 Listes connectées ($playlistsCount) ▾",
                                        "📡 Active Lists ($playlistsCount) ▾"
                                    ),
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "⏳ الصلاحية: $expiryText" + (if (networkSpeedLabel.isNotBlank()) " • ⬇ $networkSpeedLabel" else ""),
                                    color = if (serverPillFocused) Color.White else cyanLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 0.2 & 0.3: Right — Device Screen ID Pill + Show/Hide Eye Toggle
                    val downFromRightTop = if (wideLayout) seriesTileFocus else moviesTileFocus
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
                    ) {
                        var codeFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .focusRequester(topCodeFocus)
                                .focusProperties {
                                    left = topServerFocus
                                    right = topEyeFocus
                                    down = downFromRightTop
                                    up = topCodeFocus
                                }
                                .scale(if (codeFocused) 1.04f else 1.0f)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (codeFocused) Color(0xFF5B21B6) else Color(0xFF111C30))
                                .border(
                                    width = if (codeFocused) 2.4.dp else 1.2.dp,
                                    color = if (codeFocused) Color(0xFFE9D5FF) else Color(0xFF284066),
                                    shape = RoundedCornerShape(99.dp)
                                )
                                .onFocusChanged { codeFocused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                        when (ev.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_CENTER,
                                            KeyEvent.KEYCODE_ENTER,
                                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                                            KeyEvent.KEYCODE_BUTTON_A -> {
                                                copyDeviceCodeToClipboard(context, deviceId)
                                                onOpenActivation()
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(topServerFocus)
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(topEyeFocus)
                                            KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(downFromRightTop)
                                            KeyEvent.KEYCODE_DPAD_UP -> true
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusable()
                                .clickable {
                                    copyDeviceCodeToClipboard(context, deviceId)
                                    onOpenActivation()
                                }
                                .padding(
                                    horizontal = if (compact) 7.dp else 12.dp,
                                    vertical = if (compact) 5.dp else 7.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Device ID",
                                tint = if (codeFocused) Color.White else cyanAccent,
                                modifier = Modifier.size(if (compact) 13.dp else 16.dp)
                            )
                            Text(
                                text = if (showScreenCode) "ID: ${deviceId.take(if (compact) 9 else 15)}" else "ID: ••••••••",
                                color = Color.White,
                                fontSize = if (compact) 9.sp else 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ID",
                                tint = if (codeFocused) Color.White else slateMuted,
                                modifier = Modifier.size(if (compact) 11.dp else 14.dp)
                            )
                        }

                        var eyeFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .focusRequester(topEyeFocus)
                                .focusProperties {
                                    left = topCodeFocus
                                    right = topEyeFocus
                                    down = downFromRightTop
                                    up = topEyeFocus
                                }
                                .size(if (compact) 30.dp else 36.dp)
                                .scale(if (eyeFocused) 1.08f else 1.0f)
                                .clip(CircleShape)
                                .background(if (eyeFocused) Color(0xFF5B21B6) else Color(0xFF111C30))
                                .border(
                                    width = if (eyeFocused) 2.4.dp else 1.2.dp,
                                    color = if (eyeFocused) Color(0xFFE9D5FF) else Color(0xFF284066),
                                    shape = CircleShape
                                )
                                .onFocusChanged { eyeFocused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                        when (ev.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_CENTER,
                                            KeyEvent.KEYCODE_ENTER,
                                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                                            KeyEvent.KEYCODE_BUTTON_A -> {
                                                onToggleScreenCodeVisibility()
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(topCodeFocus)
                                            KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(downFromRightTop)
                                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> true
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusable()
                                .clickable { onToggleScreenCodeVisibility() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (showScreenCode) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle device ID",
                                tint = if (eyeFocused) Color.White else cyanAccent,
                                modifier = Modifier.size(if (compact) 15.dp else 18.dp)
                            )
                        }
                    }
                }

                // =========================================================================
                // MAIN CONTENT GRID (ROW 1 & ROW 2) — Deterministic 2D Focus Matrix
                // =========================================================================
                if (wideLayout) {
                    // ROW 1: 3 Large Hero Media Pillars (Live TV | Movies | Series)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.18f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "البث المباشر", "TV en Direct", "Live TV"),
                            subtitle = AppStrings.tr(lang, "القنوات العالمية والباقات الرياضية", "Chaînes mondiales & bouquets sport", "Global channels & sports bouquets"),
                            badgeText = "$liveCount ${AppStrings.tr(lang, "قناة", "Chaînes", "Channels")}",
                            iconKind = "LIVE_TV",
                            accentColor = cyanAccent,
                            cardGradient = listOf(Color(0xFF0E203D), Color(0xFF091326)),
                            isTallCard = true,
                            dense = false,
                            compact = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(liveTileFocus)
                                .focusProperties {
                                    up = topVpnFocus
                                    down = playlistsTileFocus
                                    left = liveTileFocus
                                    right = moviesTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(topVpnFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(playlistsTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(moviesTileFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> true
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.LIVE) }
                        )

                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "الأفلام", "Films VOD", "Movies"),
                            subtitle = AppStrings.tr(lang, "أحدث الأفلام العربية والعالمية", "Derniers films arabes & internationaux", "Latest Arabic & international movies"),
                            badgeText = "$moviesCount ${AppStrings.tr(lang, "فيلم", "Films", "Movies")}",
                            iconKind = "MOVIES",
                            accentColor = indigoAccent,
                            cardGradient = listOf(Color(0xFF15193B), Color(0xFF0B0E24)),
                            isTallCard = true,
                            dense = false,
                            compact = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(moviesTileFocus)
                                .focusProperties {
                                    up = topServerFocus
                                    down = refreshTileFocus
                                    left = liveTileFocus
                                    right = seriesTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(topServerFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(refreshTileFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(liveTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(seriesTileFocus)
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.MOVIES) }
                        )

                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "المسلسلات", "Séries TV", "Series"),
                            subtitle = AppStrings.tr(lang, "جميع المواسم والحلقات الكاملة", "Toutes les saisons et épisodes", "All complete seasons & episodes"),
                            badgeText = "$seriesCount ${AppStrings.tr(lang, "مسلسل", "Séries", "Series")}",
                            iconKind = "SERIES",
                            accentColor = emeraldAccent,
                            cardGradient = listOf(Color(0xFF0B252A), Color(0xFF07151A)),
                            isTallCard = true,
                            dense = false,
                            compact = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(seriesTileFocus)
                                .focusProperties {
                                    up = topCodeFocus
                                    down = settingsTileFocus
                                    left = moviesTileFocus
                                    right = seriesTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(topCodeFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(settingsTileFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(moviesTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.SERIES) }
                        )
                    }

                    // ROW 2: 3 Secondary Management & System Cards (Playlists | Smart Sync | Settings)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.76f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "إدارة القوائم", "Gérer les Listes", "Playlists Manager"),
                            subtitle = AppStrings.tr(lang, "$playlistsCount قوائم • تبديل وتعديل الحسابات", "$playlistsCount listes • Changer ou modifier", "$playlistsCount lists • Switch & edit"),
                            badgeText = "$playlistsCount",
                            iconKind = "PLAYLIST",
                            accentColor = cyanAccent,
                            cardGradient = listOf(Color(0xFF0F1B30), Color(0xFF0D0718)),
                            isTallCard = false,
                            dense = true,
                            compact = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(playlistsTileFocus)
                                .focusProperties {
                                    up = liveTileFocus
                                    down = sportsDockFocus
                                    left = playlistsTileFocus
                                    right = refreshTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(liveTileFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(sportsDockFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(refreshTileFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> true
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.XTREAM) }
                        )

                        NovoDashboardTile(
                            label = if (isLoading) {
                                AppStrings.tr(lang, "جارٍ التحديث...", "Mise à jour...", "Syncing...")
                            } else {
                                AppStrings.tr(lang, "تحديث القوائم", "Actualiser", "Sync Content")
                            },
                            subtitle = AppStrings.tr(lang, "مزامنة القنوات والأفلام من المصدر", "Synchroniser depuis la source", "Refresh channels & VOD from source"),
                            badgeText = if (isLoading) "⏳" else "↻",
                            iconKind = "REFRESH",
                            accentColor = emeraldAccent,
                            cardGradient = listOf(Color(0xFF0D2126), Color(0xFF081418)),
                            isTallCard = false,
                            dense = true,
                            compact = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(refreshTileFocus)
                                .focusProperties {
                                    up = moviesTileFocus
                                    down = favsDockFocus
                                    left = playlistsTileFocus
                                    right = settingsTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(moviesTileFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(favsDockFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(playlistsTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(settingsTileFocus)
                                    else -> false
                                }
                            },
                            onClick = { onRefreshAll() }
                        )

                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "الإعدادات", "Paramètres", "Settings"),
                            subtitle = AppStrings.tr(lang, "المشغل • الـ VPN • ترتيب وإخفاء الباقات", "Lecteur • VPN • Tri des bouquets", "Player • VPN • Bouquets order"),
                            badgeText = "⚙️",
                            iconKind = "SETTINGS",
                            accentColor = Color(0xFF60A5FA),
                            cardGradient = listOf(Color(0xFF111A30), Color(0xFF0A1020)),
                            isTallCard = false,
                            dense = true,
                            compact = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(settingsTileFocus)
                                .focusProperties {
                                    up = seriesTileFocus
                                    down = activationDockFocus
                                    left = refreshTileFocus
                                    right = settingsTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(seriesTileFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(activationDockFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(refreshTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.SETTINGS) }
                        )
                    }
                } else {
                    // Compact 2-Column Layout (3 Rows x 2 Columns) with Full Deterministic D-Pad Matrix
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "البث المباشر", "TV en Direct", "Live TV"),
                            subtitle = "$liveCount قناة",
                            iconKind = "LIVE_TV",
                            accentColor = cyanAccent,
                            cardGradient = listOf(Color(0xFF0E203D), Color(0xFF091326)),
                            isTallCard = false,
                            dense = false,
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(liveTileFocus)
                                .focusProperties {
                                    up = topVpnFocus
                                    down = seriesTileFocus
                                    left = liveTileFocus
                                    right = moviesTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(topVpnFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(seriesTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(moviesTileFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> true
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.LIVE) }
                        )
                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "الأفلام", "Films", "Movies"),
                            subtitle = "$moviesCount فيلم",
                            iconKind = "MOVIES",
                            accentColor = indigoAccent,
                            cardGradient = listOf(Color(0xFF15193B), Color(0xFF0B0E24)),
                            isTallCard = false,
                            dense = false,
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(moviesTileFocus)
                                .focusProperties {
                                    up = topCodeFocus
                                    down = playlistsTileFocus
                                    left = liveTileFocus
                                    right = moviesTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(topCodeFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(playlistsTileFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(liveTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.MOVIES) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "المسلسلات", "Séries", "Series"),
                            subtitle = "$seriesCount مسلسل",
                            iconKind = "SERIES",
                            accentColor = emeraldAccent,
                            cardGradient = listOf(Color(0xFF0B252A), Color(0xFF07151A)),
                            isTallCard = false,
                            dense = false,
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(seriesTileFocus)
                                .focusProperties {
                                    up = liveTileFocus
                                    down = settingsTileFocus
                                    left = seriesTileFocus
                                    right = playlistsTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(liveTileFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(settingsTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(playlistsTileFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> true
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.SERIES) }
                        )
                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "إدارة القوائم", "Listes", "Playlists"),
                            subtitle = "$playlistsCount قوائم",
                            iconKind = "PLAYLIST",
                            accentColor = cyanAccent,
                            cardGradient = listOf(Color(0xFF0F1B30), Color(0xFF0D0718)),
                            isTallCard = false,
                            dense = false,
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(playlistsTileFocus)
                                .focusProperties {
                                    up = moviesTileFocus
                                    down = refreshTileFocus
                                    left = seriesTileFocus
                                    right = playlistsTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(moviesTileFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(refreshTileFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(seriesTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.XTREAM) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(0.85f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "الإعدادات", "Paramètres", "Settings"),
                            iconKind = "SETTINGS",
                            accentColor = Color(0xFF60A5FA),
                            cardGradient = listOf(Color(0xFF111A30), Color(0xFF0A1020)),
                            isTallCard = false,
                            dense = true,
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(settingsTileFocus)
                                .focusProperties {
                                    up = seriesTileFocus
                                    down = sportsDockFocus
                                    left = settingsTileFocus
                                    right = refreshTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(seriesTileFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(sportsDockFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusSafe(refreshTileFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> true
                                    else -> false
                                }
                            },
                            onClick = { onOpenTab(MainTab.SETTINGS) }
                        )
                        NovoDashboardTile(
                            label = AppStrings.tr(lang, "تحديث", "Actualiser", "Refresh"),
                            iconKind = "REFRESH",
                            accentColor = emeraldAccent,
                            cardGradient = listOf(Color(0xFF0D2126), Color(0xFF081418)),
                            isTallCard = false,
                            dense = true,
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(refreshTileFocus)
                                .focusProperties {
                                    up = playlistsTileFocus
                                    down = activationDockFocus
                                    left = settingsTileFocus
                                    right = refreshTileFocus
                                },
                            onDirectionalKey = { code ->
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(playlistsTileFocus)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveFocusSafe(activationDockFocus)
                                    KeyEvent.KEYCODE_DPAD_LEFT -> moveFocusSafe(settingsTileFocus)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                                    else -> false
                                }
                            },
                            onClick = { onRefreshAll() }
                        )
                    }
                }

                // =========================================================================
                // ROW 3: Bottom Quick-Action Dock (4 Sleek Focusable Pill Cards)
                // =========================================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 38.dp else 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    @Composable
                    fun RowScope.FocusableDockCard(
                        label: String,
                        icon: androidx.compose.ui.graphics.vector.ImageVector,
                        accent: Color,
                        ownFocus: FocusRequester,
                        upFocus: FocusRequester,
                        leftFocus: FocusRequester,
                        rightFocus: FocusRequester,
                        isExitBtn: Boolean = false,
                        onClick: () -> Unit
                    ) {
                        var dockFocused by remember { mutableStateOf(false) }
                        val shape = RoundedCornerShape(if (compact) 10.dp else 13.dp)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(ownFocus)
                                .focusProperties {
                                    up = upFocus
                                    down = ownFocus
                                    left = leftFocus
                                    right = rightFocus
                                }
                                .scale(if (dockFocused) 1.04f else 1.0f)
                                .clip(shape)
                                .background(
                                    when {
                                        dockFocused && isExitBtn -> Color(0xFFDC2626)
                                        dockFocused -> Color(0xFF5B21B6)
                                        isExitBtn -> Color(0xFF1F1018)
                                        else -> Color(0xFF170D26)
                                    }
                                )
                                .border(
                                    width = if (dockFocused) 2.4.dp else 1.1.dp,
                                    color = when {
                                        dockFocused && isExitBtn -> Color(0xFFFECACA)
                                        dockFocused -> Color(0xFFE9D5FF)
                                        isExitBtn -> Color(0xFF7F1D1D)
                                        else -> Color(0xFF1F314F)
                                    },
                                    shape = shape
                                )
                                .onFocusChanged { dockFocused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                                        when (ev.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_CENTER,
                                            KeyEvent.KEYCODE_ENTER,
                                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                                            KeyEvent.KEYCODE_BUTTON_A -> {
                                                SafeClickGuard.runThrottled("DOCK_ACTION_CLICK", 180L) { onClick() }
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_UP -> moveFocusSafe(upFocus)
                                            KeyEvent.KEYCODE_DPAD_LEFT -> if (leftFocus != ownFocus) moveFocusSafe(leftFocus) else true
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> if (rightFocus != ownFocus) moveFocusSafe(rightFocus) else true
                                            KeyEvent.KEYCODE_DPAD_DOWN -> true
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusable()
                                .clickable {
                                    SafeClickGuard.runThrottled("DOCK_ACTION_CLICK", 180L) { onClick() }
                                }
                                .padding(horizontal = if (compact) 6.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (dockFocused) Color.White else accent,
                                modifier = Modifier.size(if (compact) 15.dp else 19.dp)
                            )
                            Spacer(Modifier.width(if (compact) 5.dp else 8.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = if (compact) 10.5.sp else 13.5.sp,
                                fontWeight = if (dockFocused) FontWeight.ExtraBold else FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val upFromDock0 = if (wideLayout) playlistsTileFocus else settingsTileFocus
                    val upFromDock1 = if (wideLayout) refreshTileFocus else settingsTileFocus
                    val upFromDock2 = if (wideLayout) settingsTileFocus else refreshTileFocus
                    val upFromDock3 = if (wideLayout) settingsTileFocus else refreshTileFocus

                    FocusableDockCard(
                        label = AppStrings.tr(lang, "الرياضة", "Sport", "Sports"),
                        icon = Icons.Default.SportsSoccer,
                        accent = emeraldAccent,
                        ownFocus = sportsDockFocus,
                        upFocus = upFromDock0,
                        leftFocus = sportsDockFocus,
                        rightFocus = favsDockFocus,
                        onClick = { showSportsWebViewModal = true }
                    )
                    FocusableDockCard(
                        label = if (favoritesCount > 0) {
                            "${AppStrings.tr(lang, "المفضلة", "Favoris", "Favorites")} ($favoritesCount)"
                        } else {
                            AppStrings.tr(lang, "المفضلة", "Favoris", "Favorites")
                        },
                        icon = Icons.Default.Favorite,
                        accent = Color(0xFFF43F5E),
                        ownFocus = favsDockFocus,
                        upFocus = upFromDock1,
                        leftFocus = sportsDockFocus,
                        rightFocus = activationDockFocus,
                        onClick = { onOpenTab(MainTab.FAVORITES) }
                    )
                    FocusableDockCard(
                        label = AppStrings.tr(lang, "التفعيل والصلاحية", "Activation", "Activation"),
                        icon = Icons.Default.VerifiedUser,
                        accent = cyanAccent,
                        ownFocus = activationDockFocus,
                        upFocus = upFromDock2,
                        leftFocus = favsDockFocus,
                        rightFocus = exitDockFocus,
                        onClick = { onOpenActivation() }
                    )
                    FocusableDockCard(
                        label = AppStrings.tr(lang, "خروج", "Quitter", "Exit"),
                        icon = Icons.Default.ExitToApp,
                        accent = Color(0xFFF87171),
                        ownFocus = exitDockFocus,
                        upFocus = upFromDock3,
                        leftFocus = activationDockFocus,
                        rightFocus = exitDockFocus,
                        isExitBtn = true,
                        onClick = { onExit() }
                    )
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = cyanAccent,
                        trackColor = Color(0xFF160B24)
                    )
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
        // Subtle Ambient Midnight Horizon Glow (Sapphire & Cyan — Zero Yellow!)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF5B21B6).copy(alpha = 0.12f),
                    Color(0xFF4C1D95).copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.12f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.5f, h * 0.12f)
        )
        val floorTop = h * 0.72f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF081428).copy(alpha = 0.45f),
                    Color(0xFF0A1931).copy(alpha = 0.70f),
                    Color(0xFF050B16).copy(alpha = 0.88f)
                ),
                startY = floorTop,
                endY = h
            ),
            topLeft = Offset(0f, floorTop),
            size = Size(w, h - floorTop)
        )
        val count = 28
        for (i in 0 until count) {
            val fx = ((i * 73 + 19) % 100) / 100f
            val fy = 0.76f + (((i * 41 + 7) % 22) / 100f)
            val radius = (2.2f + (i % 5) * 1.8f)
            val alpha = 0.06f + (i % 4) * 0.035f
            drawCircle(
                color = if (i % 3 == 0) Color(0xFF9D4EDD).copy(alpha = alpha) else Color(0xFF3B82F6).copy(alpha = alpha),
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
    val cyanMain = Color(0xFF5B21B6)
    val cyanLight = Color(0xFF9D4EDD)
    val sapphireDark = Color(0xFF4C1D95)
    val wDp = if (compact) 118.dp else 188.dp
    val hDp = if (compact) 36.dp else 54.dp

    Box(
        modifier = modifier.size(wDp, hDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.52f
            val shieldRx = w * 0.25f
            val shieldRy = h * 0.34f

            for (layer in 0..3) {
                val yOff = layer * (h * 0.08f)
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
                drawPath(path = wingL, color = if (layer % 2 == 0) cyanMain else cyanLight.copy(alpha = 0.85f))
            }

            for (layer in 0..3) {
                val yOff = layer * (h * 0.08f)
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
                drawPath(path = wingR, color = if (layer % 2 == 0) cyanMain else cyanLight.copy(alpha = 0.85f))
            }

            val crownPath = Path().apply {
                moveTo(cx - shieldRx * 0.48f, cy - shieldRy * 0.96f)
                lineTo(cx - shieldRx * 0.62f, cy - shieldRy * 1.42f)
                lineTo(cx - shieldRx * 0.24f, cy - shieldRy * 1.12f)
                lineTo(cx, cy - shieldRy * 1.56f)
                lineTo(cx + shieldRx * 0.24f, cy - shieldRy * 1.12f)
                lineTo(cx + shieldRx * 0.62f, cy - shieldRy * 1.42f)
                lineTo(cx + shieldRx * 0.48f, cy - shieldRy * 0.96f)
                close()
            }
            drawPath(path = crownPath, color = cyanLight)

            drawRoundRect(
                color = Color(0xFF0C0615),
                topLeft = Offset(cx - shieldRx, cy - shieldRy),
                size = Size(shieldRx * 2f, shieldRy * 2f),
                cornerRadius = CornerRadius(shieldRy, shieldRy)
            )
            drawRoundRect(
                color = cyanLight,
                topLeft = Offset(cx - shieldRx, cy - shieldRy),
                size = Size(shieldRx * 2f, shieldRy * 2f),
                cornerRadius = CornerRadius(shieldRy, shieldRy),
                style = Stroke(width = 2.dp.toPx())
            )
            drawRoundRect(
                color = sapphireDark,
                topLeft = Offset(cx - shieldRx * 0.90f, cy + shieldRy * 0.82f),
                size = Size(shieldRx * 1.80f, h * 0.10f),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
        Text(
            text = "LIVE PRO",
            color = Color.White,
            fontSize = if (compact) 8.sp else 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun NovoDashboardTile(
    label: String,
    subtitle: String = "",
    badgeText: String = "",
    iconKind: String,
    accentColor: Color = Color(0xFF9D4EDD),
    cardGradient: List<Color> = listOf(Color(0xFF170D26), Color(0xFF0D0718)),
    isTallCard: Boolean,
    dense: Boolean = false,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onDirectionalKey: ((Int) -> Boolean)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 14.dp else 20.dp)
    val activeBgBrush = if (focused) {
        Brush.verticalGradient(
            listOf(
                accentColor.copy(alpha = 0.34f),
                Color(0xFF21102F),
                Color(0xFF100719)
            )
        )
    } else {
        Brush.verticalGradient(cardGradient)
    }

    Box(
        modifier = modifier
            .scale(if (focused) 1.032f else 1.0f)
            .clip(shape)
            .background(activeBgBrush)
            .border(
                width = if (focused) 2.8.dp else 1.2.dp,
                color = if (focused) Color(0xFFE9D5FF) else accentColor.copy(alpha = 0.32f),
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val keyCode = ev.nativeKeyEvent.keyCode
                val isSelect = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                    keyCode == KeyEvent.KEYCODE_BUTTON_A
                if (isSelect) {
                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                        SafeClickGuard.runThrottled("LIVEPRO_TILE_CLICK", 180L) { onClick() }
                    }
                    true
                } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                    onDirectionalKey?.invoke(keyCode) == true
                } else false
            }
            .focusable()
            .clickable { SafeClickGuard.runThrottled("LIVEPRO_TILE_CLICK", 180L) { onClick() } }
            .padding(
                horizontal = if (compact) 10.dp else 18.dp,
                vertical = if (compact) 8.dp else if (dense) 12.dp else 16.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        // Top-Right Live Count / Status Pill Badge
        if (badgeText.isNotBlank() && !compact) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (focused) accentColor.copy(alpha = 0.32f) else Color(0xFF0C0615).copy(alpha = 0.85f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (focused) Color.White.copy(alpha = 0.8f) else accentColor.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(99.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = if (focused) Color.White else accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Glowing Icon Circle Container
            val iconCircleSize = when {
                dense && compact -> 36.dp
                dense -> 52.dp
                isTallCard && compact -> 52.dp
                isTallCard -> 76.dp
                compact -> 44.dp
                else -> 68.dp
            }
            Box(
                modifier = Modifier
                    .size(iconCircleSize)
                    .clip(CircleShape)
                    .background(
                        if (focused) accentColor.copy(alpha = 0.28f) else accentColor.copy(alpha = 0.14f)
                    )
                    .border(
                        width = if (focused) 2.dp else 1.2.dp,
                        color = if (focused) Color.White else accentColor.copy(alpha = 0.65f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                NovoLineArtIcon(
                    kind = iconKind,
                    sizeDp = iconCircleSize * 0.54f,
                    tint = if (focused) Color.White else accentColor
                )
            }

            Spacer(
                Modifier.height(
                    when {
                        dense && compact -> 4.dp
                        dense -> 8.dp
                        compact -> 6.dp
                        else -> 12.dp
                    }
                )
            )

            Text(
                text = label,
                color = Color.White,
                fontSize = when {
                    dense && compact -> 13.sp
                    dense -> 17.sp
                    compact -> 15.sp
                    else -> 21.sp
                },
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
                Text(
                    text = subtitle,
                    color = if (focused) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
                    fontSize = when {
                        dense && compact -> 9.5.sp
                        dense -> 11.5.sp
                        compact -> 10.5.sp
                        else -> 12.5.sp
                    },
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bottom Focus Indicator Glow Bar
        if (focused) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.42f)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFFE9D5FF))
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
            imageVector = Icons.Default.Sync,
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
            val stroke = Stroke(width = (w * 0.065f).coerceAtLeast(3f), cap = StrokeCap.Round)
            val thinStroke = Stroke(width = (w * 0.045f).coerceAtLeast(2f), cap = StrokeCap.Round)

            when (kind) {
                "LIVE_TV" -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.06f, h * 0.16f),
                        size = Size(w * 0.68f, h * 0.48f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
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
                        color = Color(0xFF100719),
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
                }
                "MOVIES" -> {
                    val cx = w * 0.46f
                    val cy = h * 0.48f
                    val r = w * 0.35f
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
                            Color(0xFF030108),
                            Color(0xFF100719),
                            Color(0xFF030108)
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
                    color = Color(0xFF9D4EDD),
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
                    .background(if (closeFocused) Color(0xFF5B21B6) else Color(0xCC0D1729))
                    .border(1.5.dp, if (closeFocused) Color(0xFFE9D5FF) else Color(0xFF9D4EDD), CircleShape)
                    .onFocusChanged { closeFocused = it.isFocused }
                    .onKeyEvent { ev ->
                        val code = ev.nativeKeyEvent.keyCode
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)
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
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
