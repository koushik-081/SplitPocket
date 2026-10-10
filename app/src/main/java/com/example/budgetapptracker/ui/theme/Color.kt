package com.example.budgetapptracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand colours, used by the logo ring and the welcome screen in both themes.
val Mango = Color(0xFFFFB03A)
val Pink = Color(0xFFFF5F9E)
val Violet = Color(0xFF9C7BFF)
val Coral = Color(0xFFFF6B6B)

// Colours the app needs beyond Material's own scheme.
// The first two hex digits of a colour are its opacity (FF = solid, 12 = 7%).
@Immutable
data class AppColors(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val panel: Color,
    val track: Color,
    val navBar: Color,
    val onAccent: Color,
    val accents: List<Color>,
)

// Dark: plum fading into deep sea-blue, with mango, mint, pink and sky accents.
val DarkAppColors = AppColors(
    backgroundTop = Color(0xFF2A1245),
    backgroundBottom = Color(0xFF06283A),
    heroStart = Color(0xFF6B34D6),
    heroEnd = Color(0xFFE0478C),
    panel = Color(0x12FFFFFF),
    track = Color(0x24FFFFFF),
    navBar = Color(0xB80A0E22),
    onAccent = Color(0xFF0A1528),
    accents = listOf(
        Color(0xFFFFB03A),
        Color(0xFF4FE3B0),
        Color(0xFFFF5F9E),
        Color(0xFF5BB8FF),
        Color(0xFFA78BFF),
        Color(0xFFFF7A59),
        Color(0xFF45D6E8),
    ),
)

// Light ("Sorbet"): peach fading into lavender, with violet, coral, teal and amber accents.
val LightAppColors = AppColors(
    backgroundTop = Color(0xFFFFE9D9),
    backgroundBottom = Color(0xFFE9DEFF),
    heroStart = Color(0xFF7C5CFF),
    heroEnd = Color(0xFFF0647A),
    panel = Color(0xC7FFFFFF),
    track = Color(0x1F241833),
    navBar = Color(0xDBFFFFFF),
    onAccent = Color.White,
    accents = listOf(
        Color(0xFF7C5CFF),
        Color(0xFFF0647A),
        Color(0xFF12A594),
        Color(0xFFF2A000),
        Color(0xFF3D8BFF),
        Color(0xFFD6459B),
        Color(0xFF5E9E2E),
    ),
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
