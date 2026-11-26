package com.zhaoyingqi.payledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7F3E4),
    onPrimaryContainer = Color(0xFF00201B),
    inversePrimary = Color(0xFF80D5C6),
    secondary = Color(0xFF4A635C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8DF),
    onSecondaryContainer = Color(0xFF06201A),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF6FAF7),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF6FAF7),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceTint = Color(0xFF00695C),
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFECF2EF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBEC9C4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FAF7),
    surfaceDim = Color(0xFFD6DBD8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F2),
    surfaceContainer = Color(0xFFEAEFED),
    surfaceContainerHigh = Color(0xFFE4EAE7),
    surfaceContainerHighest = Color(0xFFDFE4E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5C6),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFA7F3E4),
    inversePrimary = Color(0xFF00695C),
    secondary = Color(0xFFB1CCC4),
    onSecondary = Color(0xFF1D352F),
    secondaryContainer = Color(0xFF334B45),
    onSecondaryContainer = Color(0xFFCCE8DF),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF482900),
    tertiaryContainer = Color(0xFF683C00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C4),
    surfaceTint = Color(0xFF80D5C6),
    inverseSurface = Color(0xFFDDE4E1),
    inverseOnSurface = Color(0xFF2B3230),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343B38),
    surfaceDim = Color(0xFF0E1513),
    surfaceContainerLowest = Color(0xFF090F0D),
    surfaceContainerLow = Color(0xFF161D1A),
    surfaceContainer = Color(0xFF1A211E),
    surfaceContainerHigh = Color(0xFF242B28),
    surfaceContainerHighest = Color(0xFF2F3633),
)

private val BaseTypography = Typography()

private val AppTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = BaseTypography.titleLarge.copy(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = BaseTypography.titleSmall.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = BaseTypography.labelLarge.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = BaseTypography.labelMedium.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = BaseTypography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PayLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
