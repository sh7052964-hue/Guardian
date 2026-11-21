package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ParentLightColors = lightColorScheme(
    primary = SapphireTrust,
    onPrimary = Color.White,
    primaryContainer = SapphireContainerLight,
    onPrimaryContainer = RoyalDeepNavy,
    secondary = EmeraldShield,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = EmeraldDeep,
    tertiary = AlertAmber,
    onTertiary = Color.White,
    tertiaryContainer = AlertAmberBg,
    onTertiaryContainer = Color(0xFF78350F),
    background = CrispCanvasLight,
    onBackground = InkPrimaryLight,
    surface = PureSurfaceLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = SoftContainerLight,
    onSurfaceVariant = InkSecondaryLight,
    outline = Color(0xFFCBD5E1),
    error = DangerCrimson,
    onError = Color.White,
    errorContainer = DangerCrimsonBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val ParentDarkColors = darkColorScheme(
    primary = SapphireLight,
    onPrimary = RoyalDeepNavy,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = SapphireContainerLight,
    secondary = EmeraldBright,
    onSecondary = EmeraldDeep,
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = EmeraldContainerLight,
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = AlertAmberBg,
    background = RoyalDeepNavy,
    onBackground = Color(0xFFF1F5F9),
    surface = RoyalCardDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = RoyalSurfaceVariantDark,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = SlateBorderDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = DangerCrimsonBg
)

private val ChildLightColors = lightColorScheme(
    primary = ChildSkyPrimary,
    onPrimary = Color.White,
    primaryContainer = ChildSkyContainer,
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = ChildTealAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = AlertAmber,
    onTertiary = Color.White,
    tertiaryContainer = AlertAmberBg,
    onTertiaryContainer = Color(0xFF78350F),
    background = Color(0xFFF0F9FF),
    onBackground = InkPrimaryLight,
    surface = PureSurfaceLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = Color(0xFFE0F2FE),
    onSurfaceVariant = InkSecondaryLight,
    outline = Color(0xFFBAE6FD),
    error = DangerCrimson,
    onError = Color.White,
    errorContainer = DangerCrimsonBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val ChildDarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF075985),
    onPrimaryContainer = ChildSkyContainer,
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = AlertAmberBg,
    background = Color(0xFF081424),
    onBackground = Color(0xFFF0F9FF),
    surface = Color(0xFF0F2137),
    onSurface = Color(0xFFF0F9FF),
    surfaceVariant = Color(0xFF16304D),
    onSurfaceVariant = Color(0xFF93C5FD),
    outline = Color(0xFF1E40AF),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = DangerCrimsonBg
)

val GuardianShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun Guardian360Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isChildMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isChildMode && darkTheme -> ChildDarkColors
        isChildMode && !darkTheme -> ChildLightColors
        !isChildMode && darkTheme -> ParentDarkColors
        else -> ParentLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = GuardianShapes,
        content = content
    )
}
