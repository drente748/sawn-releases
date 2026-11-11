package com.sawn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sawn.app.R

/**
 * «صَوْن» — هوية «فجر الأمل» (Dawn identity)
 * هرّة دافئة: شمس الفجر على كريمي، وبرتقالي محروق كلون أساسي،
 * وكحلي اشقر داكن كلون نص، وذهبي كلون تمرير.
 */
object SawnPalette {
    // --- الفاتح ---
    val DawnBg = Color(0xFFFFF7ED)          // خلفية الفجر (خوخي كريمي)
    val Card = Color(0xFFFFFFFF)            // سطح البطاقة
    val Ink = Color(0xFF431407)             // كحلي محروق داكن (نص/عنوان)
    val InkSoft = Color(0xFF9A3412)         // سطر وصفي
    val Muted = Color(0xFFA8A29E)           // رمادي دافئ للتعليمات الثانوية

    val Primary = Color(0xFFEA580C)         // البرتقالي المحروق
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimarySoft = Color(0xFFFDE8D8)     // Container/soft peach
    val PrimaryDeep = Color(0xFFC2410C)     // Pressed/accent
    val OnPrimaryDeep = Color(0xFFFFF7ED)

    val Success = Color(0xFF16A34A)
    val SuccessSoft = Color(0xFFE7F6EC)
    val OnSuccessSoft = Color(0xFF14532D)

    val Gold = Color(0xFFFDBA74)            // ذهبي الفجر
    val GoldDeep = Color(0xFFD97706)
    val GoldContainer = Color(0xFFFEF3C7)
    val OnGoldContainer = Color(0xFF78350F)

    val Error = Color(0xFFC62828)
    val ErrorSoft = Color(0xFFFDE8E8)
    val OnErrorSoft = Color(0xFF7F1D1D)

    val Line = Color(0xFFF5D6BB)            // حدود البطاقات (خوخي فاتح)
    val LineSoft = Color(0xFFFDE0C8)        // حدود داخلية
    val Track = Color(0xFFF3E9DC)           // مسار التقدم/الشرائح غير المحددة

    // --- الليل (فحم دافئ) ---
    val NightBg = Color(0xFF1C1917)
    val NightSurface = Color(0xFF292524)
    val NightSurfaceSoft = Color(0xFF332F2B)
    val NightSurfaceHigh = Color(0xFF3F3A34)
    val NightInk = Color(0xFFFDE8D8)
    val NightInkSoft = Color(0xFFE7CDBD)
    val NightMuted = Color(0xFFA8A29E)
    val NightLine = Color(0xFF44403C)

    val NightPrimary = Color(0xFFFB923C)    // برتقالي فاتح للوضوح على الفحم
    val NightOnPrimary = Color(0xFF431407)
    val NightPrimaryContainer = Color(0xFF7C2D12)
    val NightOnPrimaryContainer = Color(0xFFFDE8D8)
}

/** Cairo — متغير (wght 200–1000)؛ خط «صَوْن» للوصول العربية واللاتينية. */
private val SawnCairo = FontFamily(
    Font(R.font.cairo, FontWeight.Normal),
    Font(R.font.cairo, FontWeight.Medium),
    Font(R.font.cairo, FontWeight.SemiBold),
    Font(R.font.cairo, FontWeight.Bold),
    Font(R.font.cairo, FontWeight.ExtraBold),
    Font(R.font.cairo, FontWeight.Black),
)

private fun cairoStyle(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(
    fontFamily = SawnCairo,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private val SawnTypography = Typography(
    displaySmall = cairoStyle(30.sp, 1.35.em, FontWeight.Bold),
    headlineMedium = cairoStyle(26.sp, 1.4.em, FontWeight.Bold),
    headlineSmall = cairoStyle(22.sp, 1.45.em, FontWeight.Bold),
    titleLarge = cairoStyle(20.sp, 1.45.em, FontWeight.Bold),
    titleMedium = cairoStyle(17.sp, 1.5.em, FontWeight.SemiBold),
    titleSmall = cairoStyle(15.sp, 1.5.em, FontWeight.SemiBold),
    bodyLarge = cairoStyle(16.sp, 1.7.em),
    bodyMedium = cairoStyle(14.sp, 1.7.em),
    bodySmall = cairoStyle(12.sp, 1.65.em),
    labelLarge = cairoStyle(15.sp, 1.5.em, FontWeight.SemiBold),
    labelMedium = cairoStyle(12.sp, 1.5.em, FontWeight.Medium),
    labelSmall = cairoStyle(11.sp, 1.4.em, FontWeight.Medium),
)

private val DawnLightColors = lightColorScheme(
    primary = SawnPalette.Primary,
    onPrimary = SawnPalette.OnPrimary,
    primaryContainer = SawnPalette.PrimarySoft,
    onPrimaryContainer = SawnPalette.Ink,
    secondary = SawnPalette.Success,
    onSecondary = SawnPalette.OnPrimary,
    secondaryContainer = SawnPalette.SuccessSoft,
    onSecondaryContainer = SawnPalette.OnSuccessSoft,
    tertiary = SawnPalette.GoldDeep,
    onTertiary = SawnPalette.OnPrimary,
    tertiaryContainer = SawnPalette.GoldContainer,
    onTertiaryContainer = SawnPalette.OnGoldContainer,
    background = SawnPalette.DawnBg,
    onBackground = SawnPalette.Ink,
    surface = SawnPalette.Card,
    onSurface = SawnPalette.Ink,
    surfaceVariant = SawnPalette.PrimarySoft,
    onSurfaceVariant = SawnPalette.InkSoft,
    surfaceTint = SawnPalette.Primary,
    surfaceContainerLowest = SawnPalette.Card,
    surfaceContainerLow = SawnPalette.DawnBg,
    surfaceContainer = SawnPalette.PrimarySoft,
    surfaceContainerHigh = Color(0xFFFBD9C4),
    surfaceContainerHighest = Color(0xFFF9CDB5),
    outline = SawnPalette.Line,
    outlineVariant = SawnPalette.LineSoft,
    error = SawnPalette.Error,
    onError = SawnPalette.OnPrimary,
    errorContainer = SawnPalette.ErrorSoft,
    onErrorContainer = SawnPalette.OnErrorSoft,
)

private val DawnDarkColors = darkColorScheme(
    primary = SawnPalette.NightPrimary,
    onPrimary = SawnPalette.NightOnPrimary,
    primaryContainer = SawnPalette.NightPrimaryContainer,
    onPrimaryContainer = SawnPalette.NightOnPrimaryContainer,
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF052E1A),
    secondaryContainer = Color(0xFF14532D),
    onSecondaryContainer = Color(0xFFBBF7D0),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF453000),
    tertiaryContainer = Color(0xFF5C3D00),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = SawnPalette.NightBg,
    onBackground = SawnPalette.NightInk,
    surface = SawnPalette.NightSurface,
    onSurface = SawnPalette.NightInk,
    surfaceVariant = SawnPalette.NightSurfaceSoft,
    onSurfaceVariant = SawnPalette.NightInkSoft,
    surfaceTint = SawnPalette.NightPrimary,
    surfaceContainerLowest = SawnPalette.NightBg,
    surfaceContainerLow = SawnPalette.NightSurface,
    surfaceContainer = SawnPalette.NightSurfaceSoft,
    surfaceContainerHigh = SawnPalette.NightSurfaceHigh,
    surfaceContainerHighest = SawnPalette.NightSurfaceHigh,
    outline = SawnPalette.NightLine,
    outlineVariant = Color(0xFF57534E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3F0000),
    errorContainer = Color(0xFF6F1D1B),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val DawnShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun SawnTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DawnDarkColors else DawnLightColors,
        shapes = DawnShapes,
        typography = SawnTypography,
        content = content,
    )
}
