package com.tvflux.pro

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
                        text = "🌐 إعدادات المنصة العالمية (LIVE PRO v25 Netflix Edition)",
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
