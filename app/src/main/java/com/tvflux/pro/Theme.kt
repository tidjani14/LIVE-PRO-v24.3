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
// LIVE PRO v26.0 — Premium 10-Foot TV UI Design System (Netflix / Apple TV Style)
// Dark Surfaces + Crisp Borders + Scale Focus (Zero Bright Neon Background Fills)
// ============================================================================

// ---------- 1. Core 10-Foot TV Color Tokens ----------
val FocusBorder         = Color(0xFF38BDF8)   // Soft Cyan (#38BDF8) Focus Border
val FocusedSurface      = Color(0xFF2C2C2E)   // Elevated Dark Gray (#2C2C2E) Focused Surface
val FocusSurfaceAlt     = Color(0xFF2C2C2E)   // Apple TV Elevated Dark Gray (#2C2C2E)
val FocusSurfaceDeep    = Color(0xFF1E1E24)   // Subtle Dark Gray (#1E1E24)
val FocusPureWhite      = Color(0xFFFFFFFF)   // Pure White (#FFFFFF)
val FocusTextBlack      = Color(0xFF000000)   // Pure Black (#000000)
val SurfaceDark         = Color(0xFF121212)   // Dark Gray (#121212) Unfocused/Selected Surface
val BackgroundBlack     = Color(0xFF000000)   // Pure Black (#000000)
val BackgroundDarkNavy  = Color(0xFF0A0A0F)   // Dark Navy (#0A0A0F)

val Background          = BackgroundBlack     // #000000 Pure Black
val Surface             = SurfaceDark         // #121212 Dark Gray Surface
val Primary             = Color(0xFFFFFFFF)   // #FFFFFF Clean White Primary
val OnPrimary           = Color(0xFF000000)   // #000000 Pure Black on Primary
val OnBackground        = Color(0xFFFFFFFF)   // #FFFFFF Pure White
val OnSurfaceSecondary  = Color(0xFFB0B0B0)   // #B0B0B0 Light Gray

val DeepNavy            = BackgroundDarkNavy  // #0A0A0F Dark Navy
val ObsidianBlack       = BackgroundBlack     // #000000 Pure Black
val SurfaceElevated     = Color(0xFF1E1E24)   // #1E1E24 Elevated Dark Card
val SurfaceGlass        = Color(0xE6121212)   // Dark Translucent Surface Overlay

val HighContrastCyan    = FocusBorder         // #38BDF8 Soft Cyan (Strictly for Borders/Accents, NEVER background fill)
val CyanPrimary         = FocusBorder         // #38BDF8 Soft Cyan Accent
val CyanDark            = FocusedSurface      // #2C2C2E Focused Dark Surface
val NetflixRed          = Color(0xFFE50914)   // Crimson Accent
val VioletSecondary     = Color(0xFFA78BFA)   // Soft Violet Accent

val SuccessGreen        = Color(0xFF22C55E)   // Clean Status Green
val ErrorRed            = Color(0xFFEF4444)   // Clean Alert Red
val WarningAmber        = Color(0xFFF59E0B)   // Clean Amber
val LiveRed             = Color(0xFFE50914)   // Live Broadcast Badge
val ImdbGold            = Color(0xFFFBBF24)   // Refined Gold

val TextPrimary         = Color(0xFFFFFFFF)   // #FFFFFF Pure White
val TextSecondary       = Color(0xFFB0B0B0)   // #B0B0B0 Light Gray
val TextTertiary        = Color(0xFF8E8E93)   // Muted Gray
val TextOnAccent        = Color(0xFFFFFFFF)   // #FFFFFF Pure White text on dark surfaces

// ---------- 2. Dynamic Theme State ----------
object ProThemeState {
    var useNetflixRedAccent by mutableStateOf(false)
    var useObsidianBackground by mutableStateOf(true)

    val activePrimary: Color
        get() = if (useNetflixRedAccent) NetflixRed else Primary

    val activeBg: Color
        get() = if (useObsidianBackground) BackgroundBlack else BackgroundDarkNavy
}

// ---------- 3. Pro Token Aliases ----------
val ProBg               = BackgroundBlack
val ProSurface          = SurfaceDark
val ProSurfaceElevated  = SurfaceElevated
val ProCard             = SurfaceDark
val ProBorder           = Color(0xFF2C2C2E)

val ProPrimary          = Primary
val ProPrimaryDark      = FocusedSurface
val ProSecondary        = FocusBorder
val ProAccent           = FocusBorder

val ProSuccess          = SuccessGreen
val ProWarning          = WarningAmber
val ProError            = ErrorRed
val ProInfo             = FocusBorder

val ProTextPrimary      = TextPrimary
val ProTextSecondary    = TextSecondary
val ProTextTertiary     = TextTertiary

val ProFocusRing        = FocusBorder
val ProLiveBadge        = LiveRed
val Pro4KBadge          = FocusBorder
val ProFhdBadge         = SurfaceElevated
val ProHDBadge          = FocusBorder
val ProSdBadge          = TextSecondary

// ---------- 4. Legacy Compatibility Aliases (Dark Netflix/Apple TV Standard) ----------
val BeinPrimaryPurple   = FocusedSurface
val BeinHighlightPurple = FocusBorder
val BeinFocusGlow       = FocusPureWhite
val BeinDarkBg          = BackgroundBlack
val BeinSurface         = SurfaceDark
val BeinCard            = SurfaceDark
val BeinBorder          = ProBorder
val EmeraldLive         = SuccessGreen
val CrimsonAlert        = ErrorRed
val Gold                = FocusBorder
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

// ---------- 9. Material3 Netflix/Apple TV Dark Color Scheme ----------
val ProDarkColorScheme = darkColorScheme(
    primary            = Primary,
    onPrimary          = OnPrimary,
    primaryContainer   = FocusedSurface,
    onPrimaryContainer = Color.White,
    secondary          = FocusBorder,
    onSecondary        = Color.White,
    tertiary           = FocusPureWhite,
    onTertiary         = Color.Black,
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
