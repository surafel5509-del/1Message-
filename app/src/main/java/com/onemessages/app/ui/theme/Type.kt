package com.onemessages.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography with a user-controlled scale on top of the system font scale.
 *
 * We stay on the platform font so Amharic renders with the correct system fallbacks instead of
 * a bundled Latin-only family. Headline sizes carry a slightly negative tracking, which is what
 * makes a large title look intentional rather than just big.
 */
fun appTypography(fontScale: Float): Typography {
    val scale = fontScale.coerceIn(0.8f, 1.6f)
    fun base(
        size: Float,
        weight: FontWeight = FontWeight.Normal,
        lineHeight: Float = size * 1.4f,
        tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (lineHeight * scale).sp,
        letterSpacing = tracking.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    return Typography(
        displayLarge = base(52f, FontWeight.SemiBold, 58f, (-1.0f)),
        displayMedium = base(44f, FontWeight.SemiBold, 50f, (-0.8f)),
        displaySmall = base(36f, FontWeight.SemiBold, 42f, (-0.6f)),
        headlineLarge = base(32f, FontWeight.SemiBold, 38f, (-0.5f)),
        headlineMedium = base(27f, FontWeight.SemiBold, 34f, (-0.4f)),
        headlineSmall = base(23f, FontWeight.SemiBold, 30f, (-0.3f)),
        titleLarge = base(21f, FontWeight.SemiBold, 27f, (-0.2f)),
        titleMedium = base(17f, FontWeight.SemiBold, 24f, (-0.1f)),
        titleSmall = base(15f, FontWeight.SemiBold, 20f),
        bodyLarge = base(16f, FontWeight.Normal, 24f),
        bodyMedium = base(14.5f, FontWeight.Normal, 21f),
        bodySmall = base(13f, FontWeight.Normal, 18f),
        labelLarge = base(14f, FontWeight.SemiBold, 20f, 0.1f),
        labelMedium = base(12.5f, FontWeight.Medium, 17f, 0.2f),
        labelSmall = base(11.5f, FontWeight.Medium, 16f, 0.3f),
    )
}
