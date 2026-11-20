package com.weathercard.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.weathercard.app.R

@OptIn(ExperimentalTextApi::class)
private val Inter = FontFamily(
    *listOf(400, 500, 600, 700, 800).map { weight ->
        Font(R.font.inter_variable, weight = FontWeight(weight), variationSettings = FontVariation.Settings(
            FontVariation.weight(weight), FontVariation.opticalSizing(24f),
        ))
    }.toTypedArray()
)

private val Base = Typography()
val WeatherTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    displayMedium = Base.displayMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    bodySmall = Base.bodySmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelLarge = Base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    labelSmall = Base.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
)
