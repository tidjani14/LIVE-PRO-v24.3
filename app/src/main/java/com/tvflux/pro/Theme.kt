package com.tvflux.pro

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
// LIVE PRO v25.0 — Luxury OLED Black Design System (الثيم الأسود الفاخر)
// ============================================================================

// ---------- 1. Luxury Dark Theme Core Color Tokens ----------
val Background          = Color(0xFF000000)   // الخلفية الرئيسية: أسود نقي (#000000)
val Surface             = Color(0xFF121212)   // الأسطح والبطاقات: رمادي داكن جداً (#121212)
val Primary             = Color(0xFF00E5FF)   // اللون الأساسي: سماوي متوهج (#00E5FF)
val OnBackground        = Color(0xFFFFFFFF)   // النصوص الأساسية: أبيض ناصع (#FFFFFF)
val OnSurfaceSecondary  = Color(0xFFB0B0B0)   // النصوص الثانوية: رمادي (#B0B0B0)

val DeepNavy            = Background          // #000000 Pure OLED Black
val ObsidianBlack       = Background          // #000000 Pure OLED Black
val SurfaceDark         = Surface             // #121212 Luxury Dark Surface
val SurfaceElevated     = Color(0xFF1C1C1E)   // #1C1C1E Elevated Luxury Card
val SurfaceGlass        = Color(0xD9121212)   // Glassmorphism Dark Surface

val CyanPrimary         = Primary             // #00E5FF Glowing Cyan
val CyanDark            = Color(0xFF00B8D4)   // Deep Glowing Cyan
val NetflixRed          = Color(0xFFE50914)   // Netflix Red Accent
val VioletSecondary     = Color(0xFFA855F7)   // Royal Violet Secondary

val SuccessGreen        = Color(0xFF10B981)   // Connected / Completed Green
val ErrorRed            = Color(0xFFEF4444)   // Error / Alert Red
val WarningAmber        = Color(0xFFF59E0B)   // Warning Amber
val LiveRed             = Color(0xFFE11D48)   // Live Broadcast Badge
val ImdbGold            = Color(0xFFF5C518)   // IMDb Official Gold

val TextPrimary         = OnBackground        // #FFFFFF Pure White
val TextSecondary       = OnSurfaceSecondary  // #B0B0B0 Secondary Gray
val TextTertiary        = Color(0xFF757575)   // Subtle Muted Gray
val TextOnAccent        = Color(0xFF000000)   // Black text on glowing cyan/white buttons

// ---------- 2. Dynamic Theme State ----------
object ProThemeState {
    var useNetflixRedAccent by mutableStateOf(false)
    var useObsidianBackground by mutableStateOf(true)

    val activePrimary: Color
        get() = if (useNetflixRedAccent) NetflixRed else CyanPrimary

    val activeBg: Color
        get() = Background
}

// ---------- 3. Pro Token Aliases ----------
val ProBg               = Background
val ProSurface          = Surface
val ProSurfaceElevated  = SurfaceElevated
val ProCard             = Color(0xFF161618)
val ProBorder           = Color(0xFF2C2C2E)

val ProPrimary          = CyanPrimary
val ProPrimaryDark      = CyanDark
val ProSecondary        = VioletSecondary
val ProAccent           = Color(0xFF00E5FF)

val ProSuccess          = SuccessGreen
val ProWarning          = WarningAmber
val ProError            = ErrorRed
val ProInfo             = Color(0xFF00E5FF)

val ProTextPrimary      = TextPrimary
val ProTextSecondary    = TextSecondary
val ProTextTertiary     = TextTertiary

val ProFocusRing        = CyanPrimary
val ProLiveBadge        = LiveRed
val Pro4KBadge          = CyanPrimary
val ProFhdBadge         = VioletSecondary
val ProHDBadge          = SuccessGreen
val ProSdBadge          = TextSecondary

// ---------- 4. Legacy Compatibility Aliases ----------
val BeinPrimaryPurple   = VioletSecondary
val BeinHighlightPurple = CyanPrimary
val BeinFocusGlow       = CyanPrimary
val BeinDarkBg          = Background
val BeinSurface         = Surface
val BeinCard            = ProCard
val BeinBorder          = ProBorder
val EmeraldLive         = SuccessGreen
val CrimsonAlert        = ErrorRed
val Gold                = CyanPrimary
val ObsidianBg          = Background
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
    val FocusBorderWidth = 2.5f
    val FocusScale = 1.08f
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

// ---------- 9. Material3 Luxury Dark Color Scheme ----------
val ProDarkColorScheme = darkColorScheme(
    primary            = Primary,
    onPrimary          = TextOnAccent,
    primaryContainer   = CyanDark,
    onPrimaryContainer = TextPrimary,
    secondary          = VioletSecondary,
    onSecondary        = Color.White,
    tertiary           = ProAccent,
    background         = Background,
    onBackground       = OnBackground,
    surface            = Surface,
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
        background = ProThemeState.activeBg,
        surface = Surface,
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
