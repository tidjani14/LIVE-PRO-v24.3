package com.tvflux.pro

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// LIVE PRO v27.0 — Ultra Eye-Comfortable Dark Matte Slate & Warm Soft Gold System
// Designed for zero eye strain in dark rooms + 100% symbol compatibility
// ============================================================================

// ---------- 1. Core Eye-Friendly Dark Matte & Warm Gold Color Tokens ----------
val BackgroundBlack     = Color(0xFF050709)   // Soft OLED Obsidian Black (Zero harsh glare)
val BackgroundDarkNavy  = Color(0xFF0A0E12)   // Deep Matte Slate-Obsidian
val SurfaceDark         = Color(0xFF162026)   // Comfortable Dark Matte Slate Card (#162026)
val SurfaceElevated     = Color(0xFF1D2A32)   // Soft Elevated Matte Slate
val SurfaceHighlight    = Color(0xFF24343E)   // Highlighted Matte Slate

val FocusBorder         = Color(0xFF00C2FF)   // Electric cyan focus ring
val FocusedSurface      = Color(0xFF223039)   // Elevated Matte Slate Focused Surface
val FocusSurface        = FocusedSurface
val FocusSurfaceAlt     = Color(0xFF263640)   // Alternate Elevated Matte Slate
val FocusSurfaceDeep    = Color(0xFF1A252C)   // Deep Matte Slate
val FocusPureWhite      = Color(0xFFF5F7FA)   // Soft Alabaster White
val FocusContent        = FocusPureWhite
val FocusTextBlack      = Color(0xFF080B0E)   // Deep Obsidian Text on Gold/White
val SelectedSurface     = Color(0xFF1E2C34)   // Selected Active Item Surface

val Background          = BackgroundBlack
val Surface             = SurfaceDark
val Primary             = Color(0xFFF1F5F9)   // Soft Warm White Primary
val OnPrimary           = Color(0xFF080B0E)
val OnBackground        = Color(0xFFF1F5F9)   // Soft Warm White (comfortable contrast)
val OnSurfaceSecondary  = Color(0xFFA8B6C2)   // Soft Muted Slate-Silver

val DeepNavy            = BackgroundDarkNavy
val ObsidianBlack       = BackgroundBlack
val SurfaceGlass        = Color(0xE6141D23)

val HighContrastCyan    = Color(0xFF00C2FF)   // Electric cyan accent
val CyanPrimary         = Color(0xFF00C2FF)   // Digital cyan primary
val CyanGlow            = Color(0x3800C2FF)
val CyanDark            = FocusedSurface
val NetflixRed          = Color(0xFFE50914)
val VioletSecondary     = Color(0xFF526BFF)   // Premium indigo secondary
val VioletGlow          = Color(0x28526BFF)
val GoldPremium         = Color(0xFF00C2FF)

val SuccessGreen        = Color(0xFF10B981)   // Soothing Emerald Green
val ErrorRed            = Color(0xFFEF4444)   // Soft Alert Red
val WarningAmber        = Color(0xFFF59E0B)   // Warm Amber
val LiveRed             = Color(0xFFEF4444)
val ImdbGold            = Color(0xFFE5B84B)

val TextPrimary         = Color(0xFFF1F5F9)   // Eye-friendly Soft Alabaster White
val TextSecondary       = Color(0xFFA8B6C2)   // Comfortable Muted Slate-Silver
val TextTertiary        = Color(0xFF788996)   // Subtle Slate-Gray
val TextOnAccent        = Color(0xFFFFFFFF)
val TextWhite           = TextPrimary
val TextGray            = TextSecondary
val TextDim             = TextTertiary
val TextMuted           = TextSecondary

val GlassWhite          = Color(0x14FFFFFF)
val GlassBorder         = Color(0x3300C2FF)
val FocusedGlassBorder  = Color(0xFF00C2FF)

val GoldPrimary         = GoldPremium
val GoldLight           = Color(0xFFE6C665)
val GoldDark            = Color(0xFF8C6924)
val CrimsonAccent       = LiveRed
val BackgroundCharcoal  = BackgroundDarkNavy
val SurfaceCard         = SurfaceDark
val AccentBlue          = CyanPrimary
val FocusBlue           = FocusBorder
val FocusGlow           = CyanGlow
val CardBorder          = Color(0xFF26343D)
val FocusedCardSurface  = FocusedSurface

// ---------- 2. Dynamic Theme State ----------
object ProThemeState {
    var useNetflixRedAccent by mutableStateOf(false)
    var useObsidianBackground by mutableStateOf(true)

    val activePrimary: Color
        get() = if (useNetflixRedAccent) NetflixRed else CyanPrimary

    val activeBg: Color
        get() = if (useObsidianBackground) BackgroundBlack else BackgroundDarkNavy
}

// ---------- 3. Pro Token Aliases ----------
val ProBg               = BackgroundBlack
val ProBackground       = BackgroundBlack
val ProSurface          = SurfaceDark
val ProSurfaceElevated  = SurfaceElevated
val ProSurfaceGlass     = SurfaceGlass
val ProCard             = SurfaceDark
val ProBorder           = Color(0xFF26343D)

val ProPrimary          = Primary
val ProPrimaryDark      = FocusedSurface
val ProSecondary        = FocusBorder
val ProAccent           = CyanPrimary
val ProCyan             = CyanPrimary
val ProBlue             = VioletSecondary
val ProGold             = GoldPremium

val ProSuccess          = SuccessGreen
val ProWarning          = WarningAmber
val ProError            = ErrorRed
val ProDanger           = ErrorRed
val ProInfo             = CyanPrimary

val ProTextPrimary      = TextPrimary
val ProTextSecondary    = TextSecondary
val ProTextTertiary     = TextTertiary
val ProTextMuted        = TextTertiary

val ProFocusRing        = FocusBorder
val ProLiveBadge        = LiveRed
val Pro4KBadge          = FocusBorder
val ProFhdBadge         = SurfaceElevated
val ProHDBadge          = CyanPrimary
val ProSdBadge          = TextSecondary

// ---------- 4. Legacy Compatibility Aliases (Eye-Comfortable Matte Slate & Gold) ----------
val BeinPrimaryPurple   = FocusedSurface
val BeinHighlightPurple = Color(0xFFBF9543)
val BeinFocusGlow       = Color(0xFFD4AF37)
val BeinDarkBg          = BackgroundBlack
val BeinSurface         = Color(0xFF121A20)
val BeinCard            = Color(0xFF172128)
val BeinBorder          = Color(0xFF26343D)
val EmeraldLive         = SuccessGreen
val CrimsonAlert        = ErrorRed
val Gold                = FocusBorder
val ObsidianBg          = BackgroundBlack
val CardDark            = SurfaceDark

// ---------- 5. Brush Gradients ----------
val MainBackgroundBrush = Brush.verticalGradient(
    colors = listOf(BackgroundBlack, BackgroundDarkNavy, Color(0xFF0C1116))
)

val HeroVignetteBrush = Brush.verticalGradient(
    0.0f to Color(0x99050709),
    0.35f to Color.Transparent,
    0.75f to Color(0xCC0A0E12),
    1.0f to BackgroundBlack
)

val HeroSideVignetteBrush = Brush.horizontalGradient(
    0.0f to BackgroundBlack,
    0.45f to Color(0x990A0E12),
    1.0f to Color.Transparent
)

val CyanVioletGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF00C2FF), Color(0xFF526BFF))
)

val CardGlassBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFF182329), Color(0xFF141D22))
)

// ---------- 6. Strict Spacing Scale ----------
@Immutable
object ProSpacing {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 20.dp
    val xxl = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp
    val s48 = 48.dp
    val s64 = 64.dp
}

// ---------- 7. Responsive Design Dimensions ----------
@Immutable
object ProDimens {
    val SideRailWidth = 88
    val SideRailWidthCompact = 64
    val CardRadius = 16
    val PosterRadius = 12
    val ButtonRadius = 12
    val FocusBorderWidth = 2.5f
    val FocusScale = 1.02f
    val ContentPadding = 16
    val SectionSpacing = 24
    val PosterWidth = 144
    val PosterHeight = 216
    val PosterTotalHeight = 276
    val HeroHeightTv = 340
    val HeroHeightLandscape = 250
    val HeroHeightPortrait = 220
    val HeroHeight = 340
    val QuickHubHeightTv = 168
    val QuickHubHeightLandscape = 136
    val CategoryCardHeight = 136
    val PlayerCenterPlayButton = 78
    val AdaptiveGridMinSize = 180
}

// ---------- 8. Shapes ----------
val ProShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ---------- 9. Typography ----------
val ProTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
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
        lineHeight = 20.sp
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
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
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

// ---------- 10. Material3 Dark Color Scheme ----------
val ProDarkColorScheme = darkColorScheme(
    primary            = CyanPrimary,
    onPrimary          = OnPrimary,
    primaryContainer   = FocusedSurface,
    onPrimaryContainer = TextPrimary,
    secondary          = FocusBorder,
    onSecondary        = OnPrimary,
    tertiary           = FocusPureWhite,
    onTertiary         = OnPrimary,
    background         = BackgroundBlack,
    onBackground       = OnBackground,
    surface            = SurfaceDark,
    onSurface          = OnBackground,
    surfaceVariant     = SurfaceElevated,
    onSurfaceVariant   = TextSecondary,
    outline            = ProBorder,
    error              = ErrorRed,
    onError            = Color.White
)

@Composable
fun LiveProTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val dynamicScheme = ProDarkColorScheme.copy(
        primary = ProThemeState.activePrimary,
        onPrimary = OnPrimary,
        background = ProThemeState.activeBg,
        surface = SurfaceDark,
        onBackground = OnBackground,
        onSurface = OnBackground
    )
    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = ProTypography,
        shapes = ProShapes,
        content = content
    )
}
