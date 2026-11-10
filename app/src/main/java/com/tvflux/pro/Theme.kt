package com.tvflux.pro

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================================
// LIVE PRO v24.3 — Professional Production Theme (Modern Arabic IPTV Pro)
// اتجاه 4: احترافي إنتاجي — وضوح عالي + تباين قوي + مناسب للتلفاز والهاتف
// ============================================================================

// ---------- Core Palette ----------
val ProBg              = Color(0xFF050B14)   // Deep Navy Black
val ProSurface         = Color(0xFF0F172A)   // Slate Surface
val ProSurfaceElevated = Color(0xFF1E293B)   // Elevated Card
val ProCard            = Color(0xFF162032)   // Card Background
val ProBorder          = Color(0xFF334155)   // Subtle Border

val ProPrimary         = Color(0xFF22D3EE)   // Cyan Accent (Live / Active)
val ProPrimaryDark     = Color(0xFF0891B2)   // Darker Cyan
val ProSecondary       = Color(0xFFA855F7)   // Soft Violet
val ProAccent          = Color(0xFF8B5CF6)   // Purple Accent

val ProSuccess         = Color(0xFF10B981)   // Emerald (Connected / Live)
val ProWarning         = Color(0xFFF59E0B)   // Amber
val ProError           = Color(0xFFEF4444)   // Red
val ProInfo            = Color(0xFF3B82F6)   // Blue

val ProTextPrimary     = Color(0xFFF8FAFC)   // Almost White
val ProTextSecondary   = Color(0xFF94A3B8)   // Muted Gray
val ProTextTertiary    = Color(0xFF64748B)   // Dimmer

val ProFocusRing       = Color(0xFF67E8F9)   // Bright Cyan Focus
val ProLiveBadge       = Color(0xFFEF4444)   // Live Red
val Pro4KBadge         = Color(0xFF22D3EE)   // 4K Cyan
val ProHDBadge         = Color(0xFF10B981)   // HD Green

// Legacy aliases (to keep old code compiling without massive search-replace)
val BeinPrimaryPurple  = ProSecondary
val BeinHighlightPurple= ProPrimary
val BeinFocusGlow      = ProFocusRing
val BeinDarkBg         = ProBg
val BeinSurface        = ProSurface
val BeinCard           = ProCard
val BeinBorder         = ProBorder
val EmeraldLive        = ProSuccess
val CrimsonAlert       = ProError
val Gold               = ProPrimary
val ObsidianBg         = ProBg
val SurfaceDark        = ProSurface
val CardDark           = ProCard

// ---------- Color Scheme ----------
val ProDarkColorScheme = darkColorScheme(
    primary          = ProPrimary,
    onPrimary        = Color(0xFF003640),
    primaryContainer = ProPrimaryDark,
    onPrimaryContainer = ProTextPrimary,
    secondary        = ProSecondary,
    onSecondary      = Color.White,
    tertiary         = ProAccent,
    background       = ProBg,
    onBackground     = ProTextPrimary,
    surface          = ProSurface,
    onSurface        = ProTextPrimary,
    surfaceVariant   = ProSurfaceElevated,
    onSurfaceVariant = ProTextSecondary,
    outline          = ProBorder,
    error            = ProError,
    onError          = Color.White
)

// ---------- Typography ----------
val ProTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

// ---------- Theme Composable ----------
@Composable
fun LiveProTheme(
    darkTheme: Boolean = true, // Always dark for IPTV
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ProDarkColorScheme,
        typography = ProTypography,
        content = content
    )
}

// ---------- Design Tokens ----------
@Immutable
object ProDimens {
    val SideRailWidth = 88
    val SideRailWidthCompact = 64
    val CardRadius = 14
    val PosterRadius = 12
    val ButtonRadius = 12
    val FocusBorderWidth = 2.5f
    val ContentPadding = 16
    val SectionSpacing = 20
    val PosterWidth = 140
    val PosterHeight = 210
    val HeroHeightRatio = 0.42f
}
