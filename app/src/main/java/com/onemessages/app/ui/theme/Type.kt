package com.onemessages.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography with a user-controlled scale on top of the system font scale.
 * We stay on the platform font so Amharic and other scripts render with the correct
 * system font fallbacks instead of a bundled Latin-only family.
 */
fun appTypography(fontScale: Float): Typography {
    val scale = fontScale.coerceIn(0.8f, 1.6f)
    fun base(size: Float, weight: FontWeight = FontWeight.Normal, lineHeight: Float = size * 1.35f) =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = weight,
            fontSize = (size * scale).sp,
            lineHeight = (lineHeight * scale).sp,
            letterSpacing = 0.sp,
        )
    return Typography(
        displaySmall = base(36f, FontWeight.SemiBold, 42f),
        headlineLarge = base(32f, FontWeight.SemiBold, 38f),
        headlineMedium = base(28f, FontWeight.SemiBold, 34f),
        headlineSmall = base(24f, FontWeight.SemiBold, 30f),
        titleLarge = base(22f, FontWeight.SemiBold, 28f),
        titleMedium = base(17f, FontWeight.SemiBold, 24f),
        titleSmall = base(15f, FontWeight.Medium, 20f),
        bodyLarge = base(16f, FontWeight.Normal, 24f),
        bodyMedium = base(14.5f, FontWeight.Normal, 21f),
        bodySmall = base(13f, FontWeight.Normal, 18f),
        labelLarge = base(14f, FontWeight.Medium, 20f),
        labelMedium = base(12.5f, FontWeight.Medium, 17f),
        labelSmall = base(11.5f, FontWeight.Medium, 16f),
    )
}
