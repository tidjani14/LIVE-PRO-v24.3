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
// LIVE PRO v26.0 — High-Contrast 10-Foot TV UI Design System (OLED Black + Bright Cyan/White)
// ============================================================================

// ---------- 1. High-Contrast 10-Foot TV Core Color Tokens ----------
val HighContrastCyan    = Color(0xFF00E5FF)   // Bright Cyan (#00E5FF) for High-Contrast TV Focus/Active states
val FocusPureWhite      = Color(0xFFFFFFFF)   // Pure White (#FFFFFF) for High-Contrast Focus states
val FocusTextBlack      = Color(0xFF000000)   // Pure Black (#000000) text/icons on White/Cyan focused items
val SurfaceDark         = Color(0xFF121212)   // Dark Gray (#121212) unfocused/inactive surface
val BackgroundBlack     = Color(0xFF000000)   // Pure OLED Black (#000000)

val Background          = BackgroundBlack     // #000000 Pure OLED Black
val Surface             = SurfaceDark         // #121212 Dark Gray Surface
val Primary             = HighContrastCyan    // #00E5FF Bright Readable Cyan
val OnPrimary           = FocusTextBlack      // #000000 Pure Black on Primary
val OnBackground        = Color(0xFFFFFFFF)   // #FFFFFF Pure White
val OnSurfaceSecondary  = Color(0xFFB0B0B0)   // #B0B0B0 Light Gray

val DeepNavy            = BackgroundBlack     // #000000 Pure OLED Black
val ObsidianBlack       = BackgroundBlack     // #000000 Pure OLED Black
val SurfaceElevated     = Color(0xFF1A1A1A)   // #1A1A1A Subtle Dark Gray Card
val SurfaceGlass        = Color(0xE6121212)   // Subtle Dark Surface Overlay

val CyanPrimary         = HighContrastCyan    // #00E5FF Bright Cyan Accent
val CyanDark            = SurfaceDark         // #121212 Dark Gray Surface
val NetflixRed          = Color(0xFFE50914)   // High-Contrast Crimson
val VioletSecondary     = HighContrastCyan    // #00E5FF Bright Cyan (High-Contrast Active Chip Color)

val SuccessGreen        = Color(0xFF00E676)   // Bright Readable Status Green
val ErrorRed            = Color(0xFFFF1744)   // High-Contrast Alert Red
val WarningAmber        = Color(0xFFFFB300)   // High-Contrast Amber
val LiveRed             = Color(0xFFFF1744)   // Live Broadcast Badge
val ImdbGold            = Color(0xFFFFD700)   // High-Contrast Gold

val TextPrimary         = OnBackground        // #FFFFFF Pure White
val TextSecondary       = OnSurfaceSecondary  // #B0B0B0 Light Gray
val TextTertiary        = Color(0xFF8E8E93)   // Muted Gray
val TextOnAccent        = FocusTextBlack      // #000000 Pure Black text on Bright Cyan / White

// ---------- 2. Dynamic Theme State ----------
object ProThemeState {
    var useNetflixRedAccent by mutableStateOf(false)
    var useObsidianBackground by mutableStateOf(true)

    val activePrimary: Color
        get() = if (useNetflixRedAccent) NetflixRed else HighContrastCyan

    val activeBg: Color
        get() = BackgroundBlack
}

// ---------- 3. Pro Token Aliases ----------
val ProBg               = BackgroundBlack
val ProSurface          = SurfaceDark
val ProSurfaceElevated  = SurfaceElevated
val ProCard             = SurfaceDark
val ProBorder           = Color(0xFF2C2C2E)

val ProPrimary          = HighContrastCyan
val ProPrimaryDark      = SurfaceDark
val ProSecondary        = HighContrastCyan
val ProAccent           = HighContrastCyan

val ProSuccess          = SuccessGreen
val ProWarning          = WarningAmber
val ProError            = ErrorRed
val ProInfo             = HighContrastCyan

val ProTextPrimary      = TextPrimary
val ProTextSecondary    = TextSecondary
val ProTextTertiary     = TextTertiary

val ProFocusRing        = HighContrastCyan
val ProLiveBadge        = LiveRed
val Pro4KBadge          = HighContrastCyan
val ProFhdBadge         = SurfaceElevated
val ProHDBadge          = HighContrastCyan
val ProSdBadge          = TextSecondary

// ---------- 4. Legacy Compatibility Aliases (10-Foot High-Contrast Standard) ----------
val BeinPrimaryPurple   = SurfaceDark
val BeinHighlightPurple = HighContrastCyan
val BeinFocusGlow       = FocusPureWhite
val BeinDarkBg          = BackgroundBlack
val BeinSurface         = SurfaceDark
val BeinCard            = SurfaceDark
val BeinBorder          = ProBorder
val EmeraldLive         = HighContrastCyan
val CrimsonAlert        = ErrorRed
val Gold                = HighContrastCyan
val ObsidianBg          = BackgroundBlack
val CardDark            = SurfaceDark

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
    val FocusBorderWidth = 2.5f
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

// ---------- 9. Material3 High-Contrast 10-Foot TV Dark Color Scheme ----------
val ProDarkColorScheme = darkColorScheme(
    primary            = Primary,
    onPrimary          = OnPrimary,
    primaryContainer   = HighContrastCyan,
    onPrimaryContainer = FocusTextBlack,
    secondary          = HighContrastCyan,
    onSecondary        = FocusTextBlack,
    tertiary           = FocusPureWhite,
    onTertiary         = FocusTextBlack,
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
