package com.example.budgetapptracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Solid colours for the parts that cannot be see-through: dialogs, sheets, menus, chips.
private val DarkColors = darkColorScheme(
    primary = Mango,
    onPrimary = Color(0xFF2A1700),
    primaryContainer = Color(0xFF3B2A6B),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFF4FE3B0),
    onSecondary = Color(0xFF00291B),
    secondaryContainer = Color(0xFF2E3166),
    onSecondaryContainer = Color(0xFFD8E4FF),
    tertiary = Pink,
    onTertiary = Color(0xFF3B0320),
    background = Color(0xFF181B40),
    onBackground = Color(0xFFF4F0FF),
    surface = Color(0xFF181B40),
    onSurface = Color(0xFFF4F0FF),
    surfaceVariant = Color(0xFF2C2E5A),
    onSurfaceVariant = Color(0xFFB4AED2),
    surfaceContainerLowest = Color(0xFF141736),
    surfaceContainerLow = Color(0xFF1F2148),
    surfaceContainer = Color(0xFF23254E),
    surfaceContainerHigh = Color(0xFF2C2E5A),
    surfaceContainerHighest = Color(0xFF34376A),
    outline = Color(0xFF5A5C8C),
    outlineVariant = Color(0xFF34376A),
    error = Coral,
    onError = Color(0xFF3B0A0A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A48F2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DDFF),
    onPrimaryContainer = Color(0xFF22104F),
    secondary = Color(0xFFC2415A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADFFF),
    onSecondaryContainer = Color(0xFF2A1A55),
    tertiary = Color(0xFF0E8577),
    onTertiary = Color.White,
    background = Color(0xFFF6ECF4),
    onBackground = Color(0xFF241833),
    surface = Color(0xFFF6ECF4),
    onSurface = Color(0xFF241833),
    surfaceVariant = Color(0xFFEDE3F5),
    onSurfaceVariant = Color(0xFF6E6284),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFBF6FC),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF1E8F6),
    outline = Color(0xFFBDB0CF),
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
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = AppShapes) {
        CompositionLocalProvider(
            LocalAppColors provides appColors,
            LocalContentColor provides colorScheme.onBackground,
        ) {
            // The two-colour background sits behind every screen; screens themselves are see-through.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(appColors.backgroundTop, appColors.backgroundBottom)
                        )
                    ),
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun transparentTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)
