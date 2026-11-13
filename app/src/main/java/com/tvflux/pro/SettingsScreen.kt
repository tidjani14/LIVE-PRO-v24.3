package com.tvflux.pro

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// LIVE PRO v25.0 — Advanced Multi-Language, Subtitle Styling, Security & Telemetry Settings
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

            // Theme Accent & Subtitle Customization Row
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
    isVpnProSubscribed: Boolean = VpnProConfigFetcher.isVpnProSubscribed,
    vpnStatus: CloudflareTunnelStatus,
    vpnPausedTemporarily: Boolean = LiveProGlobalActions.vpnPausedTemporarily,
    compact: Boolean = false,
    lang: AppUiLanguage = AppStrings.activeLang,
    onRecheckVpnPro: () -> Unit = { LiveProGlobalActions.onRecheckVpnPro() },
    onEnableVpn: () -> Unit = {},
    onPauseVpn: () -> Unit = {},
    onDisableVpn: () -> Unit = {}
) {
    if (isVpnProSubscribed) {
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
