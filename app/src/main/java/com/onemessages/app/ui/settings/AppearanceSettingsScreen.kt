@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.data.preferences.AccentChoice
import com.onemessages.app.data.preferences.BubbleStyle
import com.onemessages.app.data.preferences.DensityMode
import com.onemessages.app.data.preferences.ThemeMode
import com.onemessages.app.data.preferences.WallpaperStyle
import com.onemessages.app.ui.components.SettingsSection
import com.onemessages.app.ui.components.SettingsSwitchRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_appearance)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(stringResource(R.string.appearance_theme)) {
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        ThemeMode.SYSTEM to R.string.appearance_theme_system,
                        ThemeMode.LIGHT to R.string.appearance_theme_light,
                        ThemeMode.DARK to R.string.appearance_theme_dark,
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.appearance_accent)) {
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentChoice.entries.forEach { accent ->
                        val color = com.onemessages.app.ui.theme.appColorScheme(accent, false).primary
                        val selected = settings.accent == accent
                        val label = accentLabel(accent)
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { viewModel.setAccent(accent) }
                                .semantics { contentDescription = label },
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.appearance_bubble_style)) {
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        BubbleStyle.ACCENT to R.string.appearance_bubble_accent,
                        BubbleStyle.SURFACE to R.string.appearance_bubble_surface,
                        BubbleStyle.HIGH_CONTRAST to R.string.appearance_bubble_high_contrast,
                    ).forEach { (style, label) ->
                        FilterChip(
                            selected = settings.bubbleStyle == style,
                            onClick = { viewModel.setBubbleStyle(style) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.appearance_font_size)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Slider(
                        value = settings.fontScale,
                        onValueChange = { viewModel.setFontScale(it) },
                        valueRange = 0.85f..1.4f,
                        steps = 4,
                    )
                    Text(
                        text = "${(settings.fontScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.appearance_example),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            SettingsSection(stringResource(R.string.appearance_density)) {
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        DensityMode.COMPACT to R.string.appearance_density_compact,
                        DensityMode.COZY to R.string.appearance_density_cozy,
                        DensityMode.COMFORTABLE to R.string.appearance_density_comfortable,
                    ).forEach { (density, label) ->
                        FilterChip(
                            selected = settings.density == density,
                            onClick = { viewModel.setDensity(density) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.appearance_wallpaper)) {
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        WallpaperStyle.PLAIN to R.string.appearance_wallpaper_plain,
                        WallpaperStyle.SOFT_GRADIENT to R.string.appearance_wallpaper_soft,
                    ).forEach { (style, label) ->
                        FilterChip(
                            selected = settings.wallpaper == style,
                            onClick = { viewModel.setWallpaper(style) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.appearance_language)) {
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "system" to R.string.appearance_language_system,
                        "en" to R.string.language_english,
                        "am" to R.string.language_amharic,
                    ).forEach { (tag, label) ->
                        FilterChip(
                            selected = settings.languageTag == tag,
                            onClick = { viewModel.setLanguage(tag) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SettingsSection(stringResource(R.string.settings_section_general)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.appearance_reduced_motion),
                    subtitle = stringResource(R.string.appearance_reduced_motion_body),
                    checked = settings.reducedMotion,
                    onCheckedChange = viewModel::setReducedMotion,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.appearance_haptics),
                    subtitle = stringResource(R.string.appearance_haptics_body),
                    checked = settings.haptics,
                    onCheckedChange = viewModel::setHaptics,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.appearance_show_avatars),
                    subtitle = stringResource(R.string.appearance_avatars_body),
                    checked = settings.showAvatars,
                    onCheckedChange = viewModel::setShowAvatars,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun accentLabel(accent: AccentChoice): String = stringResource(
    when (accent) {
        AccentChoice.VIOLET -> R.string.appearance_accent_violet
        AccentChoice.SKY -> R.string.appearance_accent_sky
        AccentChoice.MINT -> R.string.appearance_accent_mint
        AccentChoice.CORAL -> R.string.appearance_accent_coral
        AccentChoice.AMBER -> R.string.appearance_accent_amber
        AccentChoice.ROSE -> R.string.appearance_accent_rose
    }
)
