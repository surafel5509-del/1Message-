package com.onemessages.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.onemessages.app.data.preferences.AppSettings
import com.onemessages.app.data.preferences.DensityMode

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }
val LocalConversationColors = staticCompositionLocalOf<ConversationColors> {
    error("ConversationColors not provided")
}
val LocalReducedMotion = staticCompositionLocalOf { false }

data class MessageSpacing(
    val horizontalPadding: Int,
    val verticalGap: Int,
    val groupedGap: Int,
    val maxBubbleWidthFraction: Float,
)

val LocalMessageSpacing = staticCompositionLocalOf {
    MessageSpacing(horizontalPadding = 16, verticalGap = 10, groupedGap = 3, maxBubbleWidthFraction = 0.78f)
}

@Composable
fun OneMessagesTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        com.onemessages.app.data.preferences.ThemeMode.SYSTEM -> systemDark
        com.onemessages.app.data.preferences.ThemeMode.LIGHT -> false
        com.onemessages.app.data.preferences.ThemeMode.DARK -> true
    }
    val scheme = appColorScheme(settings.accent, dark)
    val typography = appTypography(settings.fontScale)
    val conversationColors = conversationColors(settings, scheme, dark)
    val spacing = when (settings.density) {
        DensityMode.COMPACT -> MessageSpacing(12, 6, 2, 0.82f)
        DensityMode.COZY -> MessageSpacing(16, 10, 3, 0.78f)
        DensityMode.COMFORTABLE -> MessageSpacing(20, 16, 5, 0.72f)
    }
    // Our own font-scale setting is applied through the typography set above; Compose already
    // scales sp by the system font scale, which is what we want for accessibility.
    CompositionLocalProvider(
        LocalAppSettings provides settings,
        LocalConversationColors provides conversationColors,
        LocalReducedMotion provides settings.reducedMotion,
        LocalMessageSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = AppShapes,
        ) {
            content()
        }
    }
}

@Composable
fun scopedDensity(scale: Float): Density {
    val current = LocalDensity.current
    return remember(scale, current) { Density(current.density * scale, current.fontScale) }
}
