package com.tvflux.pro

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// LIVE PRO v25.0 — Corporate-Grade Design System (Netflix / Shahid / beIN Level)
// ============================================================================

// ---------- 1. Official Design System Color Tokens ----------
val DeepNavy        = Color(0xFF050B14)   // الخلفية الرئيسية (#050B14)
val SurfaceDark     = Color(0xFF0F172A)   // الأسطح والبطاقات (#0F172A)
val SurfaceElevated = Color(0xFF1E293B)   // الأسطح المرتفعة (#1E293B)

val CyanPrimary     = Color(0xFF22D3EE)   // اللون الأساسي Accent (#22D3EE)
val CyanDark        = Color(0xFF0891B2)   // درجة داكنة من السماوي
val VioletSecondary = Color(0xFFA855F7)   // لون ثانوي ناعم

val SuccessGreen    = Color(0xFF10B981)   // النجاح / متصل (#10B981)
val ErrorRed        = Color(0xFFEF4444)   // الخطأ (#EF4444)
val WarningAmber    = Color(0xFFF59E0B)   // تنبيه
val LiveRed         = Color(0xFFE11D48)   // شارة البث المباشر LIVE

val TextPrimary     = Color(0xFFF8FAFC)   // النص الأساسي (#F8FAFC)
val TextSecondary   = Color(0xFF94A3B8)   // النص الثانوي (#94A3B8)
val TextTertiary    = Color(0xFF64748B)   // نص فرعي خافت
val TextOnAccent    = Color(0xFF050B14)   // نص فوق الأزرار السماوية

// ---------- 2. Pro Token Aliases ----------
val ProBg              = DeepNavy
val ProSurface         = SurfaceDark
val ProSurfaceElevated = SurfaceElevated
val ProCard            = Color(0xFF162032)
val ProBorder          = Color(0xFF334155)

val ProPrimary         = CyanPrimary
val ProPrimaryDark     = CyanDark
val ProSecondary       = VioletSecondary
val ProAccent          = Color(0xFF8B5CF6)

val ProSuccess         = SuccessGreen
val ProWarning         = WarningAmber
val ProError           = ErrorRed
val ProInfo            = Color(0xFF3B82F6)

val ProTextPrimary     = TextPrimary
val ProTextSecondary   = TextSecondary
val ProTextTertiary    = TextTertiary

val ProFocusRing       = CyanPrimary
val ProLiveBadge       = LiveRed
val Pro4KBadge         = CyanPrimary
val ProFhdBadge        = VioletSecondary
val ProHDBadge         = SuccessGreen
val ProSdBadge         = TextSecondary

// ---------- 3. Legacy Compatibility Aliases (Zero Breaking Changes) ----------
val BeinPrimaryPurple   = VioletSecondary
val BeinHighlightPurple = CyanPrimary
val BeinFocusGlow       = CyanPrimary
val BeinDarkBg          = DeepNavy
val BeinSurface         = SurfaceDark
val BeinCard            = ProCard
val BeinBorder          = ProBorder
val EmeraldLive         = SuccessGreen
val CrimsonAlert        = ErrorRed
val Gold                = CyanPrimary
val ObsidianBg          = DeepNavy
val CardDark            = ProCard

// ---------- 4. Strict Spacing Scale (4, 8, 12, 16, 20, 24, 32, 40, 48, 64) ----------
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

// ---------- 5. Design Dimensions (TV & Phone Responsive) ----------
@Immutable
object ProDimens {
    val SideRailWidth = 88
    val SideRailWidthCompact = 64
    val CardRadius = 16
    val PosterRadius = 12
    val ButtonRadius = 12
    val FocusBorderWidth = 2.5f
    val ContentPadding = 16
    val SectionSpacing = 24
    val PosterWidth = 144
    val PosterHeight = 216
    val PosterTotalHeight = 268
    val CategoryCardHeight = 96
    val HeroHeight = 224
}

// ---------- 6. TV & Mobile Shapes ----------
val ProShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ---------- 7. TV-Optimized Arabic & Latin Typography ----------
val ProTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp
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

// ---------- 8. Material3 Dark Color Scheme ----------
val ProDarkColorScheme = darkColorScheme(
    primary            = CyanPrimary,
    onPrimary          = TextOnAccent,
    primaryContainer   = CyanDark,
    onPrimaryContainer = TextPrimary,
    secondary          = VioletSecondary,
    onSecondary        = Color.White,
    tertiary           = ProAccent,
    background         = DeepNavy,
    onBackground       = TextPrimary,
    surface            = SurfaceDark,
    onSurface          = TextPrimary,
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
    MaterialTheme(
        colorScheme = ProDarkColorScheme,
        typography = ProTypography,
        shapes = ProShapes,
        content = content
    )
}
