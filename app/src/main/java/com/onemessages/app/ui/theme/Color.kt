package com.onemessages.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.onemessages.app.data.preferences.AccentChoice

/** Brand reference colours. The accent palettes below are derived from these. */
object Brand {
    val ElectricViolet = Color(0xFF6D4AFF)
    val ElectricVioletLight = Color(0xFFB9A6FF)
    val SkyBlue = Color(0xFF2E9BFF)
    val SkyBlueLight = Color(0xFF9BD1FF)
    val Midnight = Color(0xFF0B0B12)
    val DeepCharcoal = Color(0xFF12121B)
    val CharcoalLayer = Color(0xFF1B1B26)
    val OffWhite = Color(0xFFF8F7FC)
    val SurfaceLight = Color(0xFFFFFFFF)
    val Mint = Color(0xFF2ED3A3)
    val Coral = Color(0xFFFF6B6B)
    val Amber = Color(0xFFF2A93B)
    val Rose = Color(0xFFF062A5)
    val SuccessGreen = Color(0xFF2E9E6B)
    val ErrorRed = Color(0xFFC0392B)
}

data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

fun AccentChoice.palette(dark: Boolean): AccentPalette = when (this) {
    AccentChoice.VIOLET -> if (dark) {
        AccentPalette(
            primary = Brand.ElectricVioletLight,
            onPrimary = Color(0xFF241255),
            primaryContainer = Color(0xFF3A2A6B),
            onPrimaryContainer = Color(0xFFE7DEFF),
            secondary = Brand.SkyBlueLight,
            onSecondary = Color(0xFF06233D),
            secondaryContainer = Color(0xFF1C3A55),
            onSecondaryContainer = Color(0xFFD6ECFF),
        )
    } else {
        AccentPalette(
            primary = Brand.ElectricViolet,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE9E2FF),
            onPrimaryContainer = Color(0xFF241255),
            secondary = Brand.SkyBlue,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDDEFFF),
            onSecondaryContainer = Color(0xFF06233D),
        )
    }

    AccentChoice.SKY -> if (dark) {
        AccentPalette(
            primary = Brand.SkyBlueLight,
            onPrimary = Color(0xFF06233D),
            primaryContainer = Color(0xFF1C3A55),
            onPrimaryContainer = Color(0xFFD6ECFF),
            secondary = Brand.ElectricVioletLight,
            onSecondary = Color(0xFF241255),
            secondaryContainer = Color(0xFF3A2A6B),
            onSecondaryContainer = Color(0xFFE7DEFF),
        )
    } else {
        AccentPalette(
            primary = Brand.SkyBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDDEFFF),
            onPrimaryContainer = Color(0xFF06233D),
            secondary = Brand.ElectricViolet,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE9E2FF),
            onSecondaryContainer = Color(0xFF241255),
        )
    }

    AccentChoice.MINT -> if (dark) {
        AccentPalette(
            primary = Color(0xFF7FE9C9),
            onPrimary = Color(0xFF00332A),
            primaryContainer = Color(0xFF0E4A3C),
            onPrimaryContainer = Color(0xFFC6F6E7),
            secondary = Brand.SkyBlueLight,
            onSecondary = Color(0xFF06233D),
            secondaryContainer = Color(0xFF1C3A55),
            onSecondaryContainer = Color(0xFFD6ECFF),
        )
    } else {
        AccentPalette(
            primary = Color(0xFF0E8F6D),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC6F6E7),
            onPrimaryContainer = Color(0xFF00332A),
            secondary = Brand.SkyBlue,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDDEFFF),
            onSecondaryContainer = Color(0xFF06233D),
        )
    }

    AccentChoice.CORAL -> if (dark) {
        AccentPalette(
            primary = Color(0xFFFFAFA8),
            onPrimary = Color(0xFF4A0F0B),
            primaryContainer = Color(0xFF5E1F19),
            onPrimaryContainer = Color(0xFFFFDAD6),
            secondary = Color(0xFFFFC48A),
            onSecondary = Color(0xFF422300),
            secondaryContainer = Color(0xFF5B3208),
            onSecondaryContainer = Color(0xFFFFDDBB),
        )
    } else {
        AccentPalette(
            primary = Color(0xFFC0433A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDAD6),
            onPrimaryContainer = Color(0xFF4A0F0B),
            secondary = Color(0xFF9A5B12),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFDDBB),
            onSecondaryContainer = Color(0xFF422300),
        )
    }

    AccentChoice.AMBER -> if (dark) {
        AccentPalette(
            primary = Color(0xFFFFD08A),
            onPrimary = Color(0xFF40280A),
            primaryContainer = Color(0xFF5A3D14),
            onPrimaryContainer = Color(0xFFFFE7C4),
            secondary = Color(0xFF9BD1FF),
            onSecondary = Color(0xFF06233D),
            secondaryContainer = Color(0xFF1C3A55),
            onSecondaryContainer = Color(0xFFD6ECFF),
        )
    } else {
        AccentPalette(
            primary = Color(0xFF9A6A00),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE7C4),
            onPrimaryContainer = Color(0xFF40280A),
            secondary = Brand.SkyBlue,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDDEFFF),
            onSecondaryContainer = Color(0xFF06233D),
        )
    }

    AccentChoice.ROSE -> if (dark) {
        AccentPalette(
            primary = Color(0xFFFFAFD2),
            onPrimary = Color(0xFF4A0F2B),
            primaryContainer = Color(0xFF5E1F3E),
            onPrimaryContainer = Color(0xFFFFD9E8),
            secondary = Color(0xFFB9A6FF),
            onSecondary = Color(0xFF241255),
            secondaryContainer = Color(0xFF3A2A6B),
            onSecondaryContainer = Color(0xFFE7DEFF),
        )
    } else {
        AccentPalette(
            primary = Color(0xFFB8306B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD9E8),
            onPrimaryContainer = Color(0xFF4A0F2B),
            secondary = Brand.ElectricViolet,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE9E2FF),
            onSecondaryContainer = Color(0xFF241255),
        )
    }
}

fun appColorScheme(accent: AccentChoice, dark: Boolean): ColorScheme {
    val palette = accent.palette(dark)
    return if (dark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondary = palette.secondary,
            onSecondary = palette.onSecondary,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = Color(0xFF9FD8B9),
            onTertiary = Color(0xFF06301D),
            background = Brand.Midnight,
            onBackground = Color(0xFFEDEBF5),
            surface = Brand.DeepCharcoal,
            onSurface = Color(0xFFEDEBF5),
            surfaceVariant = Color(0xFF232230),
            onSurfaceVariant = Color(0xFFC9C5D6),
            surfaceContainer = Color(0xFF16161F),
            surfaceContainerHigh = Brand.CharcoalLayer,
            surfaceContainerHighest = Color(0xFF242433),
            surfaceContainerLow = Color(0xFF131320),
            surfaceContainerLowest = Color(0xFF08080E),
            outline = Color(0xFF8B879B),
            outlineVariant = Color(0xFF3A3848),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            inverseSurface = Color(0xFFEDEBF5),
            inverseOnSurface = Color(0xFF1B1B26),
            inversePrimary = Brand.ElectricViolet,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondary = palette.secondary,
            onSecondary = palette.onSecondary,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = Color(0xFF0E7A5F),
            onTertiary = Color.White,
            background = Brand.OffWhite,
            onBackground = Color(0xFF15131C),
            surface = Brand.SurfaceLight,
            onSurface = Color(0xFF15131C),
            surfaceVariant = Color(0xFFECE9F3),
            onSurfaceVariant = Color(0xFF4B4757),
            surfaceContainer = Color(0xFFF2F0F8),
            surfaceContainerHigh = Color(0xFFECE9F4),
            surfaceContainerHighest = Color(0xFFE6E2EF),
            surfaceContainerLow = Color(0xFFF7F5FC),
            surfaceContainerLowest = Color.White,
            outline = Color(0xFF77727F),
            outlineVariant = Color(0xFFD8D4E0),
            error = Brand.ErrorRed,
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            inverseSurface = Color(0xFF2E2C36),
            inverseOnSurface = Color(0xFFF3EFF7),
            inversePrimary = Brand.ElectricVioletLight,
            scrim = Color.Black,
        )
    }
}

/** Conversation bubble colours resolved from the theme plus user preferences. */
data class ConversationColors(
    val outgoingBubble: Color,
    val outgoingText: Color,
    val incomingBubble: Color,
    val incomingText: Color,
    val outgoingTimestamp: Color,
    val incomingTimestamp: Color,
    val background: Color,
    val gradientTop: Color?,
    val gradientBottom: Color?,
    val otpChip: Color,
    val otpChipText: Color,
)

fun conversationColors(
    settings: com.onemessages.app.data.preferences.AppSettings,
    scheme: ColorScheme,
    dark: Boolean,
): ConversationColors {
    val highContrast = settings.bubbleStyle == com.onemessages.app.data.preferences.BubbleStyle.HIGH_CONTRAST
    val neutral = settings.bubbleStyle == com.onemessages.app.data.preferences.BubbleStyle.SURFACE
    val outgoingBubble = when {
        neutral -> scheme.surfaceContainerHighest
        highContrast -> scheme.primary
        else -> scheme.primary
    }
    val outgoingText = when {
        neutral -> scheme.onSurface
        highContrast -> scheme.onPrimary
        else -> scheme.onPrimary
    }
    val incomingBubble = if (highContrast) {
        if (dark) Color(0xFF2A2836) else Color(0xFFEDEAF5)
    } else {
        scheme.surfaceContainerHigh
    }
    val incomingText = if (highContrast) scheme.onSurface else scheme.onSurface
    val background = scheme.background
    val gradient = settings.wallpaper == com.onemessages.app.data.preferences.WallpaperStyle.SOFT_GRADIENT
    return ConversationColors(
        outgoingBubble = outgoingBubble,
        outgoingText = outgoingText,
        incomingBubble = incomingBubble,
        incomingText = incomingText,
        outgoingTimestamp = outgoingText.copy(alpha = 0.72f),
        incomingTimestamp = scheme.onSurfaceVariant,
        background = background,
        gradientTop = if (gradient) scheme.primary.copy(alpha = if (dark) 0.16f else 0.08f) else null,
        gradientBottom = if (gradient) scheme.background else null,
        otpChip = scheme.secondaryContainer,
        otpChipText = scheme.onSecondaryContainer,
    )
}
