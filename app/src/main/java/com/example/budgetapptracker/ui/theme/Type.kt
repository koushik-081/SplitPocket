package com.example.budgetapptracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.budgetapptracker.R

// Both fonts are single "variable" files that contain every weight,
// so each weight is requested from the same file.
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Display = FontFamily(
    variableFont(R.font.bricolage_grotesque, FontWeight.SemiBold),
    variableFont(R.font.bricolage_grotesque, FontWeight.Bold),
)

private val Body = FontFamily(
    variableFont(R.font.plus_jakarta_sans, FontWeight.Normal),
    variableFont(R.font.plus_jakarta_sans, FontWeight.Medium),
    variableFont(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    variableFont(R.font.plus_jakarta_sans, FontWeight.Bold),
)

private val Default = Typography()

val Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    displayMedium = Default.displayMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    displaySmall = Default.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    titleSmall = Default.titleSmall.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    bodyLarge = Default.bodyLarge.copy(fontFamily = Body),
    bodyMedium = Default.bodyMedium.copy(fontFamily = Body),
    bodySmall = Default.bodySmall.copy(fontFamily = Body, fontWeight = FontWeight.Medium),
    labelLarge = Default.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
    labelSmall = Default.labelSmall.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
)