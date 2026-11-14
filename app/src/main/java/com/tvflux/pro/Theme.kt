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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// PLAYER VIP v26.0 — Muted Dark Teal & Pure OLED Black Design System
// ============================================================================

// ---------- 1. Muted Dark Palette Core Color Tokens ----------
val MutedDarkTeal       = Color(0xFF0B3B36)   // Dark Teal for active UI backgrounds
val MutedTealBorder     = Color(0xFF14B8A6)   // Subdued Teal for borders/progress bars
val SurfaceDark         = Color(0xFF121212)   // Pure dark surface
val BackgroundBlack     = Color(0xFF000000)   // OLED Black

val Background          = BackgroundBlack     // #000000 Pure OLED Black
val Surface             = SurfaceDark         // #121212 Pure Dark Surface
val Primary             = MutedTealBorder     // #14B8A6 Subdued Muted Teal
val OnBackground        = Color(0xFFFFFFFF)   // #FFFFFF Pure White
val OnSurfaceSecondary  = Color(0xFFB0B0B0)   // #B0B0B0 Light Gray

val DeepNavy            = BackgroundBlack     // #000000 Pure OLED Black
val ObsidianBlack       = BackgroundBlack     // #000000 Pure OLED Black
val SurfaceElevated     = Color(0xFF1A1A1A)   // #1A1A1A Subtle Dark Gray Card
val SurfaceGlass        = Color(0xE6121212)   // Subtle Dark Surface Overlay

val CyanPrimary         = MutedTealBorder     // Subdued Teal Accent (#14B8A6)
val CyanDark            = MutedDarkTeal       // Dark Teal Active Background (#0B3B36)
val NetflixRed          = Color(0xFFB91C1C)   // Subdued Crimson
val VioletSecondary     = MutedDarkTeal       // Muted Dark Teal (No Bright Purple)

val SuccessGreen        = MutedTealBorder     // Subdued Teal (No Neon Green)
val ErrorRed            = Color(0xFFDC2626)   // Subdued Alert Red
val WarningAmber        = Color(0xFFD97706)   // Subdued Amber
val LiveRed             = Color(0xFFBE123C)   // Subdued Live Broadcast Badge
val ImdbGold            = Color(0xFFD4AF37)   // Classic Muted Gold

val TextPrimary         = OnBackground        // #FFFFFF Pure White
val TextSecondary       = OnSurfaceSecondary  // #B0B0B0 Light Gray
val TextTertiary        = Color(0xFF757575)   // Subtle Muted Gray
val TextOnAccent        = Color(0xFFFFFFFF)   // Clean White text on MutedDarkTeal

// ---------- 2. Dynamic Theme State ----------
object ProThemeState {
    var useNetflixRedAccent by mutableStateOf(false)
    var useObsidianBackground by mutableStateOf(true)

    val activePrimary: Color
        get() = if (useNetflixRedAccent) NetflixRed else MutedTealBorder

    val activeBg: Color
        get() = BackgroundBlack
}

// ---------- 3. Pro Token Aliases ----------
val ProBg               = BackgroundBlack
val ProSurface          = SurfaceDark
val ProSurfaceElevated  = SurfaceElevated
val ProCard             = Color(0xFF161616)
val ProBorder           = Color(0xFF262626)

val ProPrimary          = MutedTealBorder
val ProPrimaryDark      = MutedDarkTeal
val ProSecondary        = MutedDarkTeal
val ProAccent           = MutedTealBorder

val ProSuccess          = MutedTealBorder
val ProWarning          = WarningAmber
val ProError            = ErrorRed
val ProInfo             = MutedTealBorder

val ProTextPrimary      = TextPrimary
val ProTextSecondary    = TextSecondary
val ProTextTertiary     = TextTertiary

val ProFocusRing        = MutedTealBorder
val ProLiveBadge        = LiveRed
val Pro4KBadge          = MutedTealBorder
val ProFhdBadge         = MutedDarkTeal
val ProHDBadge          = MutedTealBorder
val ProSdBadge          = TextSecondary

// ---------- 4. Legacy Compatibility Aliases (Zero Neon / Zero Bright Purple) ----------
val BeinPrimaryPurple   = MutedDarkTeal
val BeinHighlightPurple = MutedTealBorder
val BeinFocusGlow       = MutedTealBorder
val BeinDarkBg          = BackgroundBlack
val BeinSurface         = SurfaceDark
val BeinCard            = ProCard
val BeinBorder          = ProBorder
val EmeraldLive         = MutedTealBorder
val CrimsonAlert        = ErrorRed
val Gold                = MutedTealBorder
val ObsidianBg          = BackgroundBlack
val CardDark            = ProCard

// ---------- 5. Strict Spacing Scale ----------
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

// ---------- 6. Responsive Design Dimensions ----------
@Immutable
object ProDimens {
    val SideRailWidth = 88
    val SideRailWidthCompact = 64
    val CardRadius = 16
    val PosterRadius = 12
    val ButtonRadius = 12
    val FocusBorderWidth = 2.0f
    val FocusScale = 1.04f
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

// ---------- 7. Shapes ----------
val ProShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ---------- 8. Typography ----------
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

// ---------- 9. Material3 Muted Dark Color Scheme ----------
val ProDarkColorScheme = darkColorScheme(
    primary            = MutedTealBorder,
    onPrimary          = Color.White,
    primaryContainer   = MutedDarkTeal,
    onPrimaryContainer = TextPrimary,
    secondary          = MutedDarkTeal,
    onSecondary        = Color.White,
    tertiary           = MutedTealBorder,
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
        background = BackgroundBlack,
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
