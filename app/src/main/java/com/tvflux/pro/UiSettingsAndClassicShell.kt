package com.tvflux.pro

import androidx.compose.material3.Scaffold

import androidx.compose.foundation.lazy.rememberLazyListState
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
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
            ProAdvancedSettingsHeaderCard(
                currentLanguage = lang,
                onSelectLanguage = { chosenLang ->
                    onUpdatePreferences(preferences.copy(appLanguage = chosenLang))
                },
                onOpenProfiles = { LiveProGlobalActions.showWhoIsWatchingModal = true },
                onOpenDownloads = { LiveProGlobalActions.activeOverlayScreen = ProOverlayScreen.DOWNLOADS }
            )
        }
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
                        if (isVpnActive) EmeraldLive else Color(0xFF38BDF8),
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
                            tint = if (isVpnActive) EmeraldLive else Color(0xFF38BDF8)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isVpnActive) {
                                    AppStrings.tr(lang, "⚡ تسريع البث: قيد التشغيل ✓", "⚡ Accélération du flux : Activée ✓", "⚡ Stream Acceleration: Active ✓")
                                } else {
                                    AppStrings.tr(lang, "🛑 تسريع البث: متوقف", "🛑 Accélération du flux : Arrêtée", "🛑 Stream Acceleration: Stopped")
                                },
                                color = if (isVpnActive) EmeraldLive else Color(0xFF7DD3FC),
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
        DiagnosticAlertType.INTERNET_ERROR -> Color(0xFF38BDF8)
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

/* =============================================================================
 * LIVE PRO — Netflix-Class Professional UI v2.0 (Integrated Production Edition)
 * =============================================================================
 * Features:
 * ✅ Netflix-style Home (Hero Banner + Quick Hub Row + Horizontal Carousels)
 * ✅ Eye-Comfort Dark Obsidian Palette (#0B0B0F / #14141A / #1C1C24) + Calm Cyan/Blue Focus (No Harsh Yellow)
 * ✅ Perfect D-Pad Focus (Zero lost focus, deterministic FocusRequester matrix, no nested focus traps)
 * ✅ Glassmorphism Player Controls (auto-hide)
 * ✅ Side Navigation Rail (TV) + Bottom Bar (Mobile) — auto detect
 * ✅ Real ChannelAvatar images & full connection to ExoPlayer/Media3, VPN, and Server Sync
 * ============================================================================= */

object LiveProColors {
    val Background = ProBg
    val Surface = ProSurface
    val SurfaceElevated = ProSurfaceElevated
    val SurfaceVariant = ProBorder
    val Gold = ProPrimary
    val GoldLight = ProFocusRing
    val GoldDark = ProPrimaryDark
    val TextPrimary = ProTextPrimary
    val TextSecondary = ProTextSecondary
    val TextMuted = ProTextTertiary
    val AccentRed = ProLiveBadge
    val Success = ProSuccess
    val Error = ProError
    val FocusBorder = ProFocusRing
    val LiveBadge = ProLiveBadge
}

/**
 * Focusable container optimized for Android TV remote & D-Pad across all devices.
 * - Single focus node (prevents double-focus traps)
 * - Handles DPAD_CENTER, ENTER, NUMPAD_ENTER, BUTTON_A reliably on all TV boxes
 * - Supports explicit directional FocusRequester routing (upTarget, downTarget, leftTarget, rightTarget)
 */
@Composable
fun FocusableItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scaleOnFocus: Float = 1.06f,
    borderWidth: Dp = 2.5.dp,
    cornerRadius: Dp = 10.dp,
    upTarget: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    leftTarget: FocusRequester? = null,
    rightTarget: FocusRequester? = null,
    onDirectionalKey: ((Int) -> Boolean)? = null,
    onFocused: (() -> Unit)? = null,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val currentOnClick by rememberUpdatedState(onClick)

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused?.invoke()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "focusScale"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(110),
        label = "borderAlpha"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .onKeyEvent { ev ->
                if (!enabled) return@onKeyEvent false
                val code = ev.nativeKeyEvent.keyCode
                val isSelect = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                    code == KeyEvent.KEYCODE_ENTER ||
                    code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                    code == KeyEvent.KEYCODE_BUTTON_A
                if (isSelect) {
                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                        SafeClickGuard.runThrottled("NETFLIX_FOCUS_CLICK", 170L) { currentOnClick() }
                    }
                    true
                } else if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.repeatCount == 0) {
                    if (onDirectionalKey?.invoke(code) == true) {
                        true
                    } else {
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_UP -> if (upTarget != null) { runCatching { upTarget.requestFocus() }; true } else false
                            KeyEvent.KEYCODE_DPAD_DOWN -> if (downTarget != null) { runCatching { downTarget.requestFocus() }; true } else false
                            KeyEvent.KEYCODE_DPAD_LEFT -> if (leftTarget != null) { runCatching { leftTarget.requestFocus() }; true } else false
                            KeyEvent.KEYCODE_DPAD_RIGHT -> if (rightTarget != null) { runCatching { rightTarget.requestFocus() }; true } else false
                            else -> false
                        }
                    }
                } else false
            }
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { SafeClickGuard.runThrottled("NETFLIX_FOCUS_CLICK", 170L) { currentOnClick() } }
            )
            .border(
                width = borderWidth,
                color = if (isFocused) Color(0xFFBAE6FD).copy(alpha = borderAlpha) else Color.Transparent,
                shape = RoundedCornerShape(cornerRadius)
            )
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        content(isFocused)
        if (isFocused) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                LiveProColors.Gold.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

/* =============================================================================
 * DATA MODELS FOR NETFLIX HOME UI
 * ============================================================================= */

data class ContentItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val progress: Float = 0f,
    val rating: String = "",
    val year: String = "",
    val plot: String = "",
    val isLive: Boolean = false,
    val group: String = "",
    val durationLabel: String = "",
    val sourceChannel: TvChannel? = null,
    val quickActionId: String = "",
    val badgeCount: String = ""
)

data class ContentRowData(
    val id: String,
    val title: String,
    val items: List<ContentItem>
)

data class HomeUiState(
    val hero: ContentItem? = null,
    val quickActions: List<ContentItem> = emptyList(),
    val continueWatching: List<ContentItem> = emptyList(),
    val rows: List<ContentRowData> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@Composable
fun LiveProLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = LiveProColors.Gold,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
fun LiveProError(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = LiveProColors.Error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            color = LiveProColors.TextSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            FocusableItem(onClick = onRetry, cornerRadius = 8.dp) {
                Box(
                    modifier = Modifier
                        .background(LiveProColors.Gold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("إعادة المحاولة", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, compact: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontSize = if (compact) 15.sp else 19.sp,
        color = LiveProColors.TextPrimary,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier.padding(horizontal = if (compact) 16.dp else 28.dp, vertical = if (compact) 4.dp else 8.dp)
    )
}

/* =============================================================================
 * HERO BANNER (With Top Status Bar + Real Backdrop + Single-Node Focus Buttons)
 * ============================================================================= */

@Composable
fun HeroBanner(
    item: ContentItem?,
    deviceId: String,
    showScreenCode: Boolean,
    vpnConnected: Boolean,
    vpnLabel: String,
    playlistsCount: Int,
    expiryText: String,
    networkSpeedLabel: String,
    isLoading: Boolean,
    compact: Boolean,
    playButtonFocusRequester: FocusRequester,
    downTargetFocusRequester: FocusRequester? = null,
    sideRailFocusRequester: FocusRequester? = null,
    onPlay: (ContentItem) -> Unit,
    onDetails: (ContentItem) -> Unit,
    onFavorite: (ContentItem) -> Unit,
    onOpenVpnControl: () -> Unit,
    onOpenPlaylistSelector: () -> Unit,
    onOpenActivation: () -> Unit,
    onToggleScreenCode: () -> Unit,
    onRefreshAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val topVpnFocus = remember { FocusRequester() }
    val topServerFocus = remember { FocusRequester() }
    val topRefreshFocus = remember { FocusRequester() }
    val topCodeFocus = remember { FocusRequester() }
    val topEyeFocus = remember { FocusRequester() }

    val detailsBtnFocus = remember { FocusRequester() }
    val favBtnFocus = remember { FocusRequester() }

    val heroHeight = if (compact) 250.dp else 340.dp
    val effectiveHero = item ?: ContentItem(
        id = "default_hero",
        title = "منصة LIVE PRO الاحترافية",
        subtitle = "بث مباشر • أفلام • مسلسلات",
        year = "4K UHD",
        rating = "VIP",
        plot = "استمتع بمشاهدة جميع القنوات الرياضية والعالمية وأحدث الأفلام والمسلسلات بأعلى ثبات وسرعة فائقة مع دعم كامل للريموت كونترول.",
        isLive = true
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(LiveProColors.Background)
    ) {
        // 1. Rich Cinematic Backdrop + Optional Poster Art on the opposite side
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF131C31),
                            Color(0xFF0B0B0F)
                        )
                    )
                )
        )

        if (effectiveHero.posterUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(if (compact) 0.38f else 0.42f)
                    .fillMaxHeight()
                    .padding(end = if (compact) 12.dp else 28.dp, top = 46.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                ChannelAvatar(
                    name = effectiveHero.title,
                    logoUrl = effectiveHero.posterUrl,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (compact) 130.dp else 210.dp),
                    cornerRadius = 16.dp,
                    contentScale = if (effectiveHero.isLive) ContentScale.Fit else ContentScale.Crop,
                    innerPadding = if (effectiveHero.isLive) 8.dp else 0.dp,
                    isPoster = !effectiveHero.isLive
                )
            }
        }

        // Gradient vignettes for crisp text legibility
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = if (isRtl) {
                            listOf(Color.Transparent, LiveProColors.Background.copy(alpha = 0.78f), LiveProColors.Background)
                        } else {
                            listOf(LiveProColors.Background, LiveProColors.Background.copy(alpha = 0.78f), Color.Transparent)
                        }
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, LiveProColors.Background)
                    )
                )
        )

        // 2. Top Status & Quick Control Bar inside Hero Banner (VPN | Playlists & Expiry | Refresh | Screen Code | Eye)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = if (compact) 14.dp else 28.dp, vertical = if (compact) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // VPN Status Pill
                FocusableItem(
                    onClick = onOpenVpnControl,
                    modifier = Modifier.focusRequester(topVpnFocus),
                    scaleOnFocus = 1.05f,
                    cornerRadius = 50.dp,
                    downTarget = playButtonFocusRequester,
                    leftTarget = if (isRtl) topServerFocus else sideRailFocusRequester,
                    rightTarget = if (isRtl) sideRailFocusRequester else topServerFocus
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .background(
                                when {
                                    focused -> LiveProColors.GoldDark
                                    vpnConnected -> Color(0xFF064E3B).copy(alpha = 0.88f)
                                    else -> LiveProColors.SurfaceElevated.copy(alpha = 0.9f)
                                },
                                RoundedCornerShape(50.dp)
                            )
                            .border(
                                1.dp,
                                if (vpnConnected) LiveProColors.Success else LiveProColors.SurfaceVariant,
                                RoundedCornerShape(50.dp)
                            )
                            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 5.dp else 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(if (vpnConnected) LiveProColors.Success else LiveProColors.Gold, CircleShape)
                        )
                        Text(
                            text = "VPN: $vpnLabel",
                            color = LiveProColors.TextPrimary,
                            fontSize = if (compact) 10.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Active Server / Playlists Switcher Pill
                FocusableItem(
                    onClick = onOpenPlaylistSelector,
                    modifier = Modifier.focusRequester(topServerFocus),
                    scaleOnFocus = 1.05f,
                    cornerRadius = 50.dp,
                    downTarget = playButtonFocusRequester,
                    leftTarget = if (isRtl) topRefreshFocus else topVpnFocus,
                    rightTarget = if (isRtl) topVpnFocus else topRefreshFocus
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (focused) LiveProColors.GoldDark else LiveProColors.SurfaceElevated.copy(alpha = 0.9f),
                                RoundedCornerShape(50.dp)
                            )
                            .border(1.dp, LiveProColors.SurfaceVariant, RoundedCornerShape(50.dp))
                            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 5.dp else 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📡 القوائم ($playlistsCount) • الصلاحية: $expiryText",
                            color = LiveProColors.TextPrimary,
                            fontSize = if (compact) 10.sp else 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Quick Sync / Refresh Pill
                FocusableItem(
                    onClick = onRefreshAll,
                    modifier = Modifier.focusRequester(topRefreshFocus),
                    scaleOnFocus = 1.05f,
                    cornerRadius = 50.dp,
                    downTarget = playButtonFocusRequester,
                    leftTarget = if (isRtl) topCodeFocus else topServerFocus,
                    rightTarget = if (isRtl) topServerFocus else topCodeFocus
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (focused) LiveProColors.GoldDark else LiveProColors.SurfaceElevated.copy(alpha = 0.9f),
                                RoundedCornerShape(50.dp)
                            )
                            .border(1.dp, LiveProColors.SurfaceVariant, RoundedCornerShape(50.dp))
                            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 5.dp else 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = if (isLoading) "⏳ جارٍ التحديث..." else "🔄 تحديث",
                            color = LiveProColors.TextPrimary,
                            fontSize = if (compact) 10.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Screen Code (DEV-XXXX-XXXX) + Eye Visibility Toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (networkSpeedLabel.isNotBlank() && !compact) {
                    Text(
                        text = "⬇ $networkSpeedLabel",
                        color = LiveProColors.Success,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(LiveProColors.SurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                FocusableItem(
                    onClick = {
                        copyDeviceCodeToClipboard(context, deviceId)
                        onOpenActivation()
                    },
                    modifier = Modifier.focusRequester(topCodeFocus),
                    scaleOnFocus = 1.05f,
                    cornerRadius = 50.dp,
                    downTarget = playButtonFocusRequester,
                    leftTarget = if (isRtl) topEyeFocus else topRefreshFocus,
                    rightTarget = if (isRtl) topRefreshFocus else topEyeFocus
                ) { focused ->
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier
                                .background(
                                    if (focused) LiveProColors.GoldDark else LiveProColors.SurfaceElevated.copy(alpha = 0.9f),
                                    RoundedCornerShape(50.dp)
                                )
                                .border(1.dp, LiveProColors.SurfaceVariant, RoundedCornerShape(50.dp))
                                .padding(horizontal = if (compact) 9.dp else 13.dp, vertical = if (compact) 5.dp else 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Tv,
                                contentDescription = null,
                                tint = if (focused) Color.White else LiveProColors.Gold,
                                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
                            )
                            Text(
                                text = if (showScreenCode) deviceId else "DEV-••••-••••",
                                color = LiveProColors.TextPrimary,
                                fontSize = if (compact) 10.sp else 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                FocusableItem(
                    onClick = onToggleScreenCode,
                    modifier = Modifier.focusRequester(topEyeFocus),
                    scaleOnFocus = 1.08f,
                    cornerRadius = 50.dp,
                    downTarget = playButtonFocusRequester,
                    leftTarget = if (isRtl) null else topCodeFocus,
                    rightTarget = if (isRtl) topCodeFocus else null
                ) { focused ->
                    Box(
                        modifier = Modifier
                            .size(if (compact) 30.dp else 34.dp)
                            .background(
                                if (focused) LiveProColors.GoldDark else LiveProColors.SurfaceElevated.copy(alpha = 0.9f),
                                CircleShape
                            )
                            .border(1.dp, LiveProColors.SurfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showScreenCode) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "إظهار/إخفاء الرمز",
                            tint = LiveProColors.TextPrimary,
                            modifier = Modifier.size(if (compact) 15.dp else 17.dp)
                        )
                    }
                }
            }
        }

        // 3. Hero Content & Action Buttons (Single Focus Node per Button — Zero Focus Traps!)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = if (compact) 16.dp else 28.dp,
                    end = if (compact) 16.dp else 28.dp,
                    bottom = if (compact) 16.dp else 24.dp
                )
                .fillMaxWidth(if (compact) 0.72f else 0.60f)
        ) {
            if (effectiveHero.isLive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(LiveProColors.LiveBadge, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "مباشر الآن",
                        color = LiveProColors.LiveBadge,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = effectiveHero.title,
                fontSize = if (compact) 22.sp else 32.sp,
                color = LiveProColors.TextPrimary,
                fontWeight = FontWeight.Black,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )

            val meta = listOfNotNull(
                effectiveHero.year.takeIf { it.isNotBlank() },
                effectiveHero.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                effectiveHero.subtitle.takeIf { it.isNotBlank() }
            ).joinToString("  •  ")

            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meta,
                    fontSize = if (compact) 12.sp else 15.sp,
                    color = LiveProColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (effectiveHero.plot.isNotBlank() && !compact) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = effectiveHero.plot,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiveProColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )
            }

            Spacer(Modifier.height(if (compact) 12.dp else 18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Button
                FocusableItem(
                    onClick = { onPlay(effectiveHero) },
                    modifier = Modifier.focusRequester(playButtonFocusRequester),
                    scaleOnFocus = 1.06f,
                    cornerRadius = 8.dp,
                    upTarget = topVpnFocus,
                    downTarget = downTargetFocusRequester,
                    leftTarget = if (isRtl) detailsBtnFocus else sideRailFocusRequester,
                    rightTarget = if (isRtl) sideRailFocusRequester else detailsBtnFocus
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (focused) LiveProColors.Gold else LiveProColors.TextPrimary,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = if (compact) 18.dp else 26.dp, vertical = if (compact) 8.dp else 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "تشغيل",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 13.sp else 15.sp
                        )
                    }
                }

                // Details / Browse Section Button
                FocusableItem(
                    onClick = { onDetails(effectiveHero) },
                    modifier = Modifier.focusRequester(detailsBtnFocus),
                    scaleOnFocus = 1.06f,
                    cornerRadius = 8.dp,
                    upTarget = topServerFocus,
                    downTarget = downTargetFocusRequester,
                    leftTarget = if (isRtl) favBtnFocus else playButtonFocusRequester,
                    rightTarget = if (isRtl) playButtonFocusRequester else favBtnFocus
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (focused) LiveProColors.GoldDark else LiveProColors.SurfaceElevated.copy(alpha = 0.85f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.5.dp,
                                if (focused) Color.White else LiveProColors.TextSecondary.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 8.dp else 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = LiveProColors.TextPrimary,
                            modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "التفاصيل",
                            color = LiveProColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 13.sp else 15.sp
                        )
                    }
                }

                // Favorite Button
                FocusableItem(
                    onClick = { onFavorite(effectiveHero) },
                    modifier = Modifier.focusRequester(favBtnFocus),
                    scaleOnFocus = 1.1f,
                    cornerRadius = 50.dp,
                    upTarget = topCodeFocus,
                    downTarget = downTargetFocusRequester,
                    leftTarget = if (isRtl) null else detailsBtnFocus,
                    rightTarget = if (isRtl) detailsBtnFocus else null
                ) { focused ->
                    Box(
                        modifier = Modifier
                            .size(if (compact) 38.dp else 44.dp)
                            .background(
                                if (focused) LiveProColors.GoldDark else LiveProColors.SurfaceVariant.copy(alpha = 0.85f),
                                CircleShape
                            )
                            .border(
                                1.2.dp,
                                if (focused) Color.White else LiveProColors.TextSecondary.copy(alpha = 0.5f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.FavoriteBorder,
                            contentDescription = "إضافة للمفضلة",
                            tint = LiveProColors.TextPrimary,
                            modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                        )
                    }
                }
            }
        }
    }
}

/* =============================================================================
 * POSTER CARD + QUICK HUB CARD + CONTENT ROW
 * ============================================================================= */

@Composable
fun PosterCard(
    item: ContentItem,
    onClick: () -> Unit,
    showProgress: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    upTarget: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    leftTarget: FocusRequester? = null,
    rightTarget: FocusRequester? = null,
    width: Dp = if (compact) 108.dp else 136.dp,
    height: Dp = if (compact) 154.dp else 196.dp
) {
    val isQuickAction = item.quickActionId.isNotBlank()
    val cardWidth = if (isQuickAction) (if (compact) 136.dp else 168.dp) else width
    val cardHeight = if (isQuickAction) (if (compact) 78.dp else 94.dp) else height

    FocusableItem(
        onClick = onClick,
        modifier = modifier.width(cardWidth),
        scaleOnFocus = 1.07f,
        cornerRadius = 12.dp,
        upTarget = upTarget,
        downTarget = downTarget,
        leftTarget = leftTarget,
        rightTarget = rightTarget
    ) { focused ->
        if (isQuickAction) {
            // Netflix Quick Section Hub Card (Live TV, Movies, Series, Playlists, Sports, Favorites, Sync, Activation, Settings)
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            if (focused) {
                                listOf(Color(0xFF0284C7), Color(0xFF1E3A8A))
                            } else {
                                listOf(LiveProColors.SurfaceElevated, LiveProColors.Surface)
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (focused) Color.White else LiveProColors.SurfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (item.badgeCount.isNotBlank()) {
                    Text(
                        text = item.badgeCount,
                        color = if (focused) Color.White else LiveProColors.Gold,
                        fontSize = if (compact) 10.sp else 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        color = LiveProColors.TextPrimary,
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            color = if (focused) Color(0xFFE0F2FE) else LiveProColors.TextSecondary,
                            fontSize = if (compact) 10.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Column {
                Box(
                    Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LiveProColors.SurfaceVariant)
                ) {
                    if (item.posterUrl.isNotBlank()) {
                        ChannelAvatar(
                            name = item.title,
                            logoUrl = item.posterUrl,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 12.dp,
                            contentScale = if (item.isLive) ContentScale.Fit else ContentScale.Crop,
                            innerPadding = if (item.isLive) 6.dp else 0.dp,
                            isPoster = !item.isLive
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF232533), Color(0xFF151621))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (item.isLive) Icons.Default.LiveTv else Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = LiveProColors.TextMuted.copy(alpha = 0.65f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    if (showProgress && item.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { item.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter),
                            color = LiveProColors.Gold,
                            trackColor = Color.White.copy(alpha = 0.22f)
                        )
                    }

                    if (item.isLive) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(7.dp)
                                .background(LiveProColors.LiveBadge, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "LIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (item.rating.isNotBlank()) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(7.dp)
                                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "★ ${item.rating}",
                                color = LiveProColors.GoldLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = if (compact) 11.5.sp else 13.sp,
                    color = if (focused) Color.White else LiveProColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.SemiBold,
                    lineHeight = 17.sp
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = if (compact) 10.sp else 11.sp,
                        color = LiveProColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ContentRow(
    title: String,
    items: List<ContentItem>,
    onItemClick: (ContentItem) -> Unit,
    showProgress: Boolean = false,
    compact: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
    upRowFocusRequester: FocusRequester? = null,
    downRowFocusRequester: FocusRequester? = null,
    sideRailFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(modifier.padding(vertical = if (compact) 4.dp else 7.dp)) {
        SectionTitle(title = title, compact = compact)

        LazyRow(
            contentPadding = PaddingValues(horizontal = if (compact) 16.dp else 28.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            itemsIndexed(items, key = { idx, it -> "${idx}_${it.id}" }) { idx, item ->
                val cardMod = if (idx == 0 && firstItemFocusRequester != null) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier
                }
                val edgeRailTarget = if (idx == 0) sideRailFocusRequester else null
                PosterCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    showProgress = showProgress,
                    compact = compact,
                    modifier = cardMod,
                    upTarget = upRowFocusRequester,
                    downTarget = downRowFocusRequester,
                    leftTarget = if (isRtl) null else edgeRailTarget,
                    rightTarget = if (isRtl) edgeRailTarget else null
                )
            }
        }
    }
}

/* =============================================================================
 * NETFLIX HOME SCREEN (`PremiumHomeScreen`)
 * ============================================================================= */

@Composable
fun PremiumHomeScreen(
    uiState: HomeUiState,
    deviceId: String = "",
    showScreenCode: Boolean = true,
    vpnConnected: Boolean = false,
    vpnLabel: String = "متصل",
    playlistsCount: Int = 0,
    expiryText: String = "غير محدود",
    networkSpeedLabel: String = "",
    compact: Boolean = false,
    sideRailFocusRequester: FocusRequester? = null,
    onPlay: (ContentItem) -> Unit,
    onDetails: (ContentItem) -> Unit,
    onFavorite: (ContentItem) -> Unit,
    onOpenVpnControl: () -> Unit = {},
    onOpenPlaylistSelector: () -> Unit = {},
    onOpenActivation: () -> Unit = {},
    onToggleScreenCode: () -> Unit = {},
    onRefreshAll: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val heroPlayFocusRequester = remember { FocusRequester() }
    val quickRowFocusRequester = remember { FocusRequester() }
    val continueRowFocusRequester = remember { FocusRequester() }
    val rowFocusRequesters = remember(uiState.rows.size) {
        List(uiState.rows.size.coerceAtLeast(1)) { FocusRequester() }
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            for (attempt in 1..6) {
                delay(if (attempt <= 2) 25L else 45L)
                if (runCatching { quickRowFocusRequester.requestFocus() }.isSuccess ||
                    runCatching { heroPlayFocusRequester.requestFocus() }.isSuccess
                ) break
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(LiveProColors.Background)
    ) {
        when {
            uiState.isLoading && uiState.quickActions.isEmpty() && uiState.rows.isEmpty() -> LiveProLoading()
            uiState.errorMessage != null && uiState.quickActions.isEmpty() -> LiveProError(
                message = uiState.errorMessage,
                onRetry = onRetry
            )
            else -> {
                val firstBelowQuick = when {
                    uiState.continueWatching.isNotEmpty() -> continueRowFocusRequester
                    uiState.rows.isNotEmpty() -> rowFocusRequesters.firstOrNull()
                    else -> null
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = if (compact) 72.dp else 88.dp)
                ) {
                    item(key = "netflix_hero_banner") {
                        HeroBanner(
                            item = uiState.hero,
                            deviceId = deviceId,
                            showScreenCode = showScreenCode,
                            vpnConnected = vpnConnected,
                            vpnLabel = vpnLabel,
                            playlistsCount = playlistsCount,
                            expiryText = expiryText,
                            networkSpeedLabel = networkSpeedLabel,
                            isLoading = uiState.isLoading,
                            compact = compact,
                            playButtonFocusRequester = heroPlayFocusRequester,
                            downTargetFocusRequester = if (uiState.quickActions.isNotEmpty()) quickRowFocusRequester else firstBelowQuick,
                            sideRailFocusRequester = sideRailFocusRequester,
                            onPlay = onPlay,
                            onDetails = onDetails,
                            onFavorite = onFavorite,
                            onOpenVpnControl = onOpenVpnControl,
                            onOpenPlaylistSelector = onOpenPlaylistSelector,
                            onOpenActivation = onOpenActivation,
                            onToggleScreenCode = onToggleScreenCode,
                            onRefreshAll = onRefreshAll
                        )
                    }

                    if (uiState.quickActions.isNotEmpty()) {
                        item(key = "netflix_quick_hub_row") {
                            ContentRow(
                                title = "⚡ الوصول السريع والأقسام الرئيسية",
                                items = uiState.quickActions,
                                onItemClick = onPlay,
                                compact = compact,
                                firstItemFocusRequester = quickRowFocusRequester,
                                upRowFocusRequester = heroPlayFocusRequester,
                                downRowFocusRequester = firstBelowQuick,
                                sideRailFocusRequester = sideRailFocusRequester
                            )
                        }
                    }

                    if (uiState.continueWatching.isNotEmpty()) {
                        item(key = "netflix_continue_watching_row") {
                            ContentRow(
                                title = "⏯ متابعة المشاهدة",
                                items = uiState.continueWatching,
                                onItemClick = onPlay,
                                showProgress = true,
                                compact = compact,
                                firstItemFocusRequester = continueRowFocusRequester,
                                upRowFocusRequester = if (uiState.quickActions.isNotEmpty()) quickRowFocusRequester else heroPlayFocusRequester,
                                downRowFocusRequester = rowFocusRequesters.firstOrNull(),
                                sideRailFocusRequester = sideRailFocusRequester
                            )
                        }
                    }

                    itemsIndexed(uiState.rows, key = { _, row -> row.id }) { rIdx, row ->
                        val upTarget = when {
                            rIdx > 0 -> rowFocusRequesters[rIdx - 1]
                            uiState.continueWatching.isNotEmpty() -> continueRowFocusRequester
                            uiState.quickActions.isNotEmpty() -> quickRowFocusRequester
                            else -> heroPlayFocusRequester
                        }
                        val downTarget = if (rIdx + 1 < rowFocusRequesters.size) rowFocusRequesters[rIdx + 1] else null
                        ContentRow(
                            title = row.title,
                            items = row.items,
                            onItemClick = onPlay,
                            compact = compact,
                            firstItemFocusRequester = rowFocusRequesters.getOrNull(rIdx),
                            upRowFocusRequester = upTarget,
                            downRowFocusRequester = downTarget,
                            sideRailFocusRequester = sideRailFocusRequester
                        )
                    }
                }
            }
        }
    }
}

/* =============================================================================
 * NAVIGATION — Side Rail (TV) + Bottom Bar (Mobile)
 * ============================================================================= */

data class NavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val mainNavItems = listOf(
    NavItem("home", "الرئيسية", Icons.Outlined.Home, Icons.Filled.Home),
    NavItem("live", "مباشر", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    NavItem("movies", "أفلام", Icons.Outlined.Movie, Icons.Filled.Movie),
    NavItem("series", "مسلسلات", Icons.Outlined.Tv, Icons.Filled.Tv),
    NavItem("xtream", "القوائم", Icons.Default.CloudDownload, Icons.Default.CloudDownload),
    NavItem("favorites", "المفضلة", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    NavItem("settings", "الإعدادات", Icons.Outlined.Settings, Icons.Filled.Settings),
    NavItem("exit", "خروج", Icons.Default.ExitToApp, Icons.Default.ExitToApp)
)

@Composable
fun TvSideRail(
    selectedId: String,
    onNavigate: (String) -> Unit,
    railPrimaryFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val itemRequesters = remember { mainNavItems.associate { it.id to FocusRequester() } }

    LazyColumn(
        modifier = modifier
            .width(86.dp)
            .fillMaxHeight()
            .background(LiveProColors.Surface)
            .border(1.dp, LiveProColors.SurfaceVariant)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "lp_crest") {
            Box(
                Modifier
                    .size(42.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(LiveProColors.Gold, LiveProColors.GoldDark)
                        ),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "LP",
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        itemsIndexed(mainNavItems, key = { _, item -> item.id }) { idx, item ->
            val selected = item.id == selectedId
            val isExit = item.id == "exit"
            val ownReq = itemRequesters[item.id] ?: FocusRequester()
            val upReq = if (idx > 0) itemRequesters[mainNavItems[idx - 1].id] else ownReq
            val downReq = if (idx < mainNavItems.lastIndex) itemRequesters[mainNavItems[idx + 1].id] else ownReq
            val modWithPrimary = if (selected && railPrimaryFocusRequester != null) {
                Modifier
                    .focusRequester(railPrimaryFocusRequester)
                    .focusRequester(ownReq)
            } else {
                Modifier.focusRequester(ownReq)
            }

            FocusableItem(
                onClick = { onNavigate(item.id) },
                modifier = modWithPrimary
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                scaleOnFocus = 1.07f,
                cornerRadius = 12.dp,
                upTarget = upReq,
                downTarget = downReq,
                leftTarget = if (isRtl) contentFocusRequester else ownReq,
                rightTarget = if (isRtl) ownReq else contentFocusRequester
            ) { focused ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                focused && isExit -> LiveProColors.Error
                                focused -> LiveProColors.GoldDark
                                selected -> LiveProColors.Gold.copy(alpha = 0.20f)
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                        tint = when {
                            focused -> Color.White
                            isExit -> LiveProColors.Error
                            selected -> LiveProColors.Gold
                            else -> LiveProColors.TextSecondary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        color = when {
                            focused -> Color.White
                            selected -> LiveProColors.GoldLight
                            else -> LiveProColors.TextSecondary
                        },
                        fontSize = 10.5.sp,
                        fontWeight = if (selected || focused) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun MobileBottomBar(
    selectedId: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = LiveProColors.Surface,
        contentColor = LiveProColors.TextPrimary,
        tonalElevation = 0.dp
    ) {
        mainNavItems.take(6).forEach { item ->
            val selected = item.id == selectedId
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.id) },
                icon = {
                    Icon(
                        if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(item.label, fontSize = 10.5.sp, maxLines = 1)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = LiveProColors.Gold,
                    selectedTextColor = LiveProColors.Gold,
                    indicatorColor = LiveProColors.Gold.copy(alpha = 0.16f),
                    unselectedIconColor = LiveProColors.TextMuted,
                    unselectedTextColor = LiveProColors.TextMuted
                )
            )
        }
    }
}

/* =============================================================================
 * PLAYER CONTROLS OVERLAY — Glassmorphism + Auto-hide
 * ============================================================================= */

@Composable
fun PlayerControlsOverlay(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    title: String,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onQuality: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(visible, isPlaying) {
        if (visible && isPlaying) {
            delay(4200)
            visible = false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { visible = !visible }
            }
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(280)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FocusableItem(onClick = onBack, scaleOnFocus = 1.1f, cornerRadius = 50.dp) {
                        Box(
                            modifier = Modifier.size(42.dp).background(Color.White.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FocusableItem(onClick = onSeekBack, scaleOnFocus = 1.12f, cornerRadius = 50.dp) {
                        Box(
                            modifier = Modifier.size(60.dp).background(Color.White.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Replay10, contentDescription = "تأخير 10 ثوانٍ", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    FocusableItem(onClick = onPlayPause, scaleOnFocus = 1.1f, cornerRadius = 50.dp) {
                        Box(
                            modifier = Modifier.size(78.dp).background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
                                tint = Color.Black,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    FocusableItem(onClick = onSeekForward, scaleOnFocus = 1.12f, cornerRadius = 50.dp) {
                        Box(
                            modifier = Modifier.size(60.dp).background(Color.White.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Forward10, contentDescription = "تقديم 10 ثوانٍ", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                            )
                        )
                        .padding(horizontal = 28.dp, vertical = 18.dp)
                ) {
                    val progress = if (durationMs > 0L) {
                        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = progress,
                        onValueChange = { onSeek((it * durationMs).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = LiveProColors.Gold,
                            activeTrackColor = LiveProColors.Gold,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        )
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(positionMs)}  /  ${formatTime(durationMs)}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlayerChip("الجودة", Icons.Rounded.HighQuality, onQuality)
                            PlayerChip("الصوت", Icons.Rounded.Audiotrack, onAudio)
                            PlayerChip("ترجمة", Icons.Rounded.ClosedCaption, onSubtitle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    FocusableItem(onClick = onClick, scaleOnFocus = 1.08f, cornerRadius = 8.dp) {
        Row(
            modifier = Modifier
                .background(LiveProColors.SurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = (ms / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/* =============================================================================
 * MAIN SCAFFOLD — Auto TV / Mobile (`PremiumMainScaffold`)
 * ============================================================================= */

@Composable
fun PremiumMainScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    railPrimaryFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTv = configuration.screenWidthDp >= 700 ||
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    if (isTv) {
        Row(
            Modifier
                .fillMaxSize()
                .background(LiveProColors.Background)
        ) {
            TvSideRail(
                selectedId = currentRoute,
                onNavigate = onNavigate,
                railPrimaryFocusRequester = railPrimaryFocusRequester,
                contentFocusRequester = contentFocusRequester
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content(PaddingValues())
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                MobileBottomBar(
                    selectedId = currentRoute,
                    onNavigate = onNavigate
                )
            },
            containerColor = LiveProColors.Background
        ) { padding ->
            content(padding)
        }
    }
}

/* =============================================================================
 * PRODUCTION NETFLIX HOME DASHBOARD (`ClassicLiveProDashboard`)
 * Connects `PremiumMainScaffold` + `PremiumHomeScreen` to real LIVE PRO data!
 * ============================================================================= */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ClassicLiveProDashboard(
    deviceId: String,
    subscription: SubscriptionInfo?,
    vpnStatus: CloudflareTunnelStatus,
    networkSpeedLabel: String,
    activePlaylistExpiry: String = "",
    lastSelectedTab: MainTab = MainTab.HOME,
    isLoading: Boolean = false,
    showScreenCode: Boolean = true,
    onToggleScreenCodeVisibility: () -> Unit = {},
    liveCount: Int,
    moviesCount: Int,
    seriesCount: Int,
    playlistsCount: Int,
    favoritesCount: Int,
    continueWatchingCount: Int,
    allChannels: List<TvChannel> = emptyList(),
    allMovies: List<TvChannel> = emptyList(),
    allSeries: List<TvChannel> = emptyList(),
    favorites: List<TvChannel> = emptyList(),
    history: List<TvChannel> = emptyList(),
    vodWatchProgressMap: Map<String, VodWatchProgress> = emptyMap(),
    precomputedContinueWatching: List<PosterItem>? = null,
    precomputedFavorites: List<PosterItem>? = null,
    precomputedMostWatched: List<PosterItem>? = null,
    precomputedLatestMovies: List<PosterItem>? = null,
    precomputedLatestSeries: List<PosterItem>? = null,
    precomputedLiveSports: List<PosterItem>? = null,
    precomputedHeroPoster: PosterItem? = null,
    homeListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    lang: AppUiLanguage,
    compact: Boolean,
    onOpenTab: (MainTab) -> Unit,
    onPlayChannel: (TvChannel) -> Unit = {},
    onToggleFavoriteChannel: (TvChannel) -> Unit = {},
    onSwitchToModernUi: () -> Unit,
    onOpenPlaylistSelectorModal: () -> Unit = {},
    onOpenQuickAddAccount: () -> Unit = { LiveProGlobalActions.onOpenQuickAddAccount() },
    onOpenVpnControl: () -> Unit = { LiveProGlobalActions.onOpenVpnControl() },
    onRefreshAll: () -> Unit,
    onOpenActivation: () -> Unit,
    onExit: () -> Unit
) {
    AppStrings.activeLang = lang
    val context = LocalContext.current
    var showSportsWebViewModal by remember { mutableStateOf(false) }

    val expiryText = activePlaylistExpiry.ifBlank {
        subscription?.expireDate?.takeIf { it.isNotBlank() }
            ?: (subscription?.daysLeft?.takeIf { it > 0 }?.let {
                AppStrings.tr(lang, "$it يوم", "$it jours", "$it days")
            } ?: AppStrings.tr(lang, "غير محدود", "Illimité", "Unlimited"))
    }
    val isVpnPermanentOff = LiveProGlobalActions.vpnPermanentDisable
    val isVpnPaused = LiveProGlobalActions.vpnPausedTemporarily
    val isVpnConnected = (vpnStatus.connected || CloudflareWarpVpnService.isTunnelRunning) && !isVpnPermanentOff && !isVpnPaused

    val formattedDate = remember(lang) {
        ProVisualSanitizer.getVerifiedDeviceDate(lang)
    }

    // Instant O(1) read from pre-computed memory cache in MainActivity (zero UI-thread lag when pressing Back!)
    val continueWatchingPosters = precomputedContinueWatching ?: remember(history, allMovies, allSeries, vodWatchProgressMap, lang) {
        val list = ArrayList<PosterItem>()
        for (ch in (history + allMovies.take(60) + allSeries.take(60)).distinctBy { it.stableKey }) {
            val prog = vodWatchProgressMap[ch.stableKey]
                ?: (if (ch.seriesId > 0) vodWatchProgressMap["SERIES_ID_${ch.playlistId}_${ch.seriesId}"] else null)
                ?: vodWatchProgressMap[ch.url.trim()]
            if (prog != null && prog.positionMs >= 3_000L) {
                val pct = (prog.progressFraction * 100).toInt().coerceIn(1, 99)
                val resumePrefix = AppStrings.tr(lang, "متابعة", "Continuer", "Resume")
                list.add(
                    ch.toPosterItem(progressFraction = prog.progressFraction).copy(
                        badge = "$pct%",
                        subtitle = "$resumePrefix • ${ch.group}"
                    )
                )
            }
            if (list.size >= 20) break
        }
        list
    }

    val favoritePosters = precomputedFavorites ?: remember(favorites) {
        favorites.take(30).map { it.toPosterItem() }
    }

    val mostWatchedPosters = precomputedMostWatched ?: remember(history, favorites, allChannels) {
        (history + favorites.filter { it.contentType == StreamContentType.LIVE_TV } + allChannels.take(35))
            .distinctBy { it.stableKey }
            .take(30)
            .map { it.toPosterItem() }
    }

    val latestMoviesPosters = precomputedLatestMovies ?: remember(allMovies) {
        allMovies.take(35).map { it.toPosterItem() }
    }

    val latestSeriesPosters = precomputedLatestSeries ?: remember(allSeries) {
        allSeries.take(35).map { it.toPosterItem() }
    }

    val liveSportsPosters = precomputedLiveSports ?: remember(allChannels) {
        allChannels.take(25).map { it.toPosterItem() }
    }

    val heroPoster = precomputedHeroPoster ?: remember(history, mostWatchedPosters, latestMoviesPosters, liveSportsPosters) {
        history.firstOrNull()?.toPosterItem()
            ?: mostWatchedPosters.firstOrNull { !it.imageUrl.isNullOrBlank() }
            ?: latestMoviesPosters.firstOrNull { !it.imageUrl.isNullOrBlank() }
            ?: liveSportsPosters.firstOrNull()
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    CompositionLocalProvider(LocalLayoutDirection provides lang.layoutDirection) {
        LiveProTheme {
            Scaffold(
                containerColor = ProBg,
                bottomBar = {
                    if (isPortrait) {
                        ProMobileBottomNavigationBar(
                            activeTab = MainTab.HOME,
                            activeOverlay = LiveProGlobalActions.activeOverlayScreen,
                            lang = lang,
                            onSelectTab = { tab ->
                                if (tab != MainTab.HOME) {
                                    onOpenTab(tab)
                                }
                            },
                            onSelectOverlay = { overlay ->
                                LiveProGlobalActions.activeOverlayScreen = overlay
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(ProBg)
                ) {
                    if (!isPortrait) {
                        ProSideNavigationRail(
                            activeTab = MainTab.HOME,
                            compact = compact,
                            lang = lang,
                            onSelectTab = { tab ->
                                if (tab != MainTab.HOME) {
                                    onOpenTab(tab)
                                }
                            },
                            onExitClick = onExit
                        )
                    }

                    ProHomeScreen(
                        deviceCode = deviceId,
                        isVpnConnected = isVpnConnected,
                        currentDate = formattedDate,
                        mostWatched = mostWatchedPosters,
                        latestMovies = latestMoviesPosters,
                        latestSeries = latestSeriesPosters,
                        liveSports = liveSportsPosters,
                        continueWatching = continueWatchingPosters,
                        favoriteItems = favoritePosters,
                        onRefresh = onRefreshAll,
                        onDeviceCodeClick = {
                            copyDeviceCodeToClipboard(context, deviceId)
                            onOpenActivation()
                        },
                        onMoviesClick = { onOpenTab(MainTab.MOVIES) },
                        onSeriesClick = { onOpenTab(MainTab.SERIES) },
                        onSportsClick = {
                            if (ChannelRepository.latestData3SportsUrl.trim().startsWith("http", ignoreCase = true)) {
                                showSportsWebViewModal = true
                            } else {
                                onOpenTab(MainTab.LIVE)
                            }
                        },
                        onFeaturedClick = { onOpenTab(MainTab.LIVE) },
                        onPosterClick = { poster ->
                            val ch = poster.sourceChannel
                            if (ch != null) {
                                val bestCh = SportsQualitySelector.resolveBestQualityChannel(ch, allChannels)
                                onPlayChannel(bestCh)
                            } else {
                                onOpenTab(MainTab.LIVE)
                            }
                        },
                        onSeeAllMovies = { onOpenTab(MainTab.MOVIES) },
                        onSeeAllSeries = { onOpenTab(MainTab.SERIES) },
                        onSeeAllFavorites = { onOpenTab(MainTab.FAVORITES) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        lang = lang,
                        homeListState = homeListState,
                        playlistsCount = playlistsCount,
                        expiryText = expiryText,
                        networkSpeedLabel = networkSpeedLabel,
                        showScreenCode = showScreenCode,
                        isLoading = isLoading,
                        liveCount = liveCount,
                        moviesCount = moviesCount,
                        seriesCount = seriesCount,
                        heroItem = heroPoster,
                        onPlaylistsClick = onOpenPlaylistSelectorModal,
                        onVpnClick = onOpenVpnControl,
                        onToggleScreenCodeClick = onToggleScreenCodeVisibility
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
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0284C7).copy(alpha = 0.12f),
                    Color(0xFF1E3A8A).copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.12f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.5f, h * 0.12f)
        )
    }
}

@Composable
internal fun WingedGoldenCrestLogo(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val cyanMain = Color(0xFF0284C7)
    val cyanLight = Color(0xFF38BDF8)
    val sapphireDark = Color(0xFF1E3A8A)
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

            drawRoundRect(
                color = Color(0xFF081121),
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
                .background(LiveProColors.Background)
        ) {
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
                    color = LiveProColors.Gold,
                    strokeWidth = 3.2.dp,
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.Center)
                )
            }

            FocusableItem(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .focusRequester(closeFocus),
                scaleOnFocus = 1.12f,
                cornerRadius = 50.dp
            ) { focused ->
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(if (focused) LiveProColors.GoldDark else Color(0xCC0D1729), CircleShape)
                        .border(1.5.dp, if (focused) Color.White else LiveProColors.Gold, CircleShape),
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
}
