package dev.conduit.desktop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ConduitTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 24.sp, fontWeight = FontWeight(800), letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 18.sp, fontWeight = FontWeight(800), letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp,
    ),
)

val MonoFontFamily = FontFamily.Monospace
