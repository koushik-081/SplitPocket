package com.example.budgetapptracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Mango,
    onPrimary = Color(0xFF2A1700),
    primaryContainer = Color(0xFF4A2F6B),
    onPrimaryContainer = Color(0xFFF3DFFF),
    secondary = Pink,
    onSecondary = Color(0xFF3B0320),
    secondaryContainer = PlumHigh,
    onSecondaryContainer = Color(0xFFFFD08A),
    tertiary = Violet,
    onTertiary = Color(0xFF1B0A4A),
    background = Plum,
    onBackground = Lilac,
    surface = Plum,
    onSurface = Lilac,
    surfaceVariant = PlumHigh,
    onSurfaceVariant = LilacDim,
    surfaceContainerLowest = Plum,
    surfaceContainerLow = PlumRaised,
    surfaceContainer = PlumRaised,
    surfaceContainerHigh = PlumHigh,
    surfaceContainerHighest = PlumHigh,
    outline = Color(0xFF56436E),
    outlineVariant = Color(0xFF34224C),
    error = Coral,
    onError = Color(0xFF3B0A0A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B2FA0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDCFA),
    onPrimaryContainer = Color(0xFF2A0F45),
    secondary = Color(0xFFB0286A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E3F9),
    onSecondaryContainer = Color(0xFF3A1A55),
    tertiary = Color(0xFF9A5B00),
    onTertiary = Color.White,
    background = Paper,
    onBackground = Aubergine,
    surface = Paper,
    onSurface = Aubergine,
    surfaceVariant = PaperHigh,
    onSurfaceVariant = AubergineDim,
    surfaceContainerLowest = PaperRaised,
    surfaceContainerLow = PaperRaised,
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = PaperHigh,
    outline = Color(0xFFC3B5D3),
    outlineVariant = Color(0xFFE2D8EC),
    error = Color(0xFFC62828),
    onError = Color.White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BudgetAppTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
