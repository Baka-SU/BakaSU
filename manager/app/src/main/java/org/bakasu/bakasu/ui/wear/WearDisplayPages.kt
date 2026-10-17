package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FormatSize
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Opacity
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import kotlin.math.roundToInt
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.theme.BackgroundManager
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.viewmodel.HomeUiAction
import org.bakasu.bakasu.ui.viewmodel.HomeViewModel
import org.bakasu.bakasu.ui.viewmodel.PredictiveBackAnimation
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsUiState
import org.bakasu.bakasu.ui.viewmodel.WearPreference
import org.bakasu.bakasu.ui.viewmodel.WearPreferences
import org.bakasu.bakasu.ui.viewmodel.WearPreferencesViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearSliderCard
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

private fun Int.toHex() = "#%06X".format(this and 0xFFFFFF)

/**
 * One scrolling page in three sections: accent color (follow-system switch and swatches), custom
 * color (hex value and its apply action, the screen's single high-emphasis button) and background
 * (image, removal and dimming).
 */
@Composable
internal fun WearColorPage(state: SettingsUiState, onBack: () -> Unit, onAction: (SettingsUiAction) -> Unit, onOpen: (WearRoute) -> Unit, onImage: () -> Unit) {
    val config = koinInject<ThemeConfig>()
    val backgrounds = koinInject<BackgroundManager>()
    val resources = LocalResources.current
    val swatches = remember(resources) {
        resources.obtainTypedArray(R.array.wear_theme_swatches).run {
            try {
                List(length()) { getColor(it, 0) }
            } finally {
                recycle()
            }
        }
    }
    var hex by rememberSaveable { mutableStateOf(config.seedColor.toHex()) }
    var editingHex by rememberSaveable { mutableStateOf(false) }
    val valid = hex.matches(Regex("#[0-9a-fA-F]{6}"))
    if (editingHex) {
        WearSubPage({ editingHex = false }) {
            WearTextInputPage(stringResource(R.string.wear_custom_color), hex) { text ->
                hex = text.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
                editingHex = false
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            // Keep three equally sized cells; each toggle leaves 5% space on each side.
            val columns = 3
            WearList(onBack = onBack) { spec ->
                item { WearPageHeader(spec, stringResource(R.string.theme_color)) }
                item {
                    WearSettingsSwitchWidget(
                        spec,
                        stringResource(R.string.dynamic_color_title),
                        state.useDynamicColor,
                        { onAction(SettingsUiAction.SetDynamicColor(it)) },
                        icon = Icons.TwoTone.Palette,
                        secondaryLabel = stringResource(R.string.dynamic_color_summary),
                    )
                }
                SegmentedColumn(listOf(WearSelection.Palette, WearSelection.ColorSpec), { it.name }) { selection ->
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(if (selection == WearSelection.Palette) R.string.dynamic_palette_style else R.string.dynamic_color_spec),
                        { onOpen(WearRoute.Selection(selection)) },
                        icon = Icons.TwoTone.Palette,
                        description = if (selection == WearSelection.Palette) paletteLabel(state.dynamicPaletteStyle) else colorSpecLabel(state.dynamicColorSpec),
                    )
                }
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    if (state.useDynamicColor) {
                        item {
                            WearSettingsSwitchWidget(
                                spec,
                                stringResource(R.string.settings_config_use_custom_background_seed_color),
                                config.isUseBackgroundSeedColor,
                                backgrounds::saveUseBackgroundSeedColor,
                                icon = Icons.TwoTone.Image,
                            )
                        }
                    }
                    item {
                        WearSettingsSwitchWidget(
                            spec,
                            stringResource(R.string.settings_custom_enable_high_contrast),
                            config.isHighContrastMode,
                            backgrounds::saveEnableHighContrastMode,
                            icon = Icons.TwoTone.Settings,
                            secondaryLabel = stringResource(R.string.settings_custom_enable_high_contrast_summary),
                        )
                    }
                }
                item { WearSectionHeader(spec, stringResource(R.string.choose_theme_color)) }
                // Swatches share the row equally so they fill the screen width (margins stretch evenly).
                swatches.chunked(columns).forEachIndexed { index, row ->
                    item(key = "swatches-$index") {
                        WearScaledItem(spec) {
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { value ->
                                    Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                        ColorSwatch(Color(value), !state.useDynamicColor && config.seedColor == Color(value).toArgb()) {
                                            hex = Color(value).toArgb().toHex()
                                            onAction(SettingsUiAction.SetDynamicColor(false))
                                            onAction(SettingsUiAction.SetThemeColor(Color(value).toArgb()))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { WearSectionHeader(spec, stringResource(R.string.wear_custom_color)) }
                item { HexColorButton(spec, hex, valid) { editingHex = true } }
                item {
                    WearActionButton(spec, Icons.TwoTone.Check, stringResource(R.string.background_crop_apply), {
                        onAction(SettingsUiAction.SetDynamicColor(false))
                        onAction(SettingsUiAction.SetThemeColor(hex.toColorInt()))
                    }, enabled = valid, colors = ButtonDefaults.buttonColors())
                }
                item { WearSectionHeader(spec, stringResource(R.string.settings_custom_background)) }
                item {
                    WearActionButton(
                        spec,
                        Icons.TwoTone.Image,
                        stringResource(R.string.settings_custom_background_summary),
                        onImage,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    )
                }
                // Dimming only affects the custom background image, so it appears with one.
                if (state.isCustomBackgroundEnabled) {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        item {
                            WearSettingsSwitchWidget(
                                spec,
                                stringResource(R.string.settings_config_enable_blur),
                                config.isEnableBlur,
                                backgrounds::saveEnableBlur,
                                icon = Icons.TwoTone.Image,
                            )
                        }
                    }
                    item {
                        WearActionButton(
                            spec,
                            Icons.TwoTone.Delete,
                            stringResource(R.string.wear_remove_background),
                            { onAction(SettingsUiAction.RemoveCustomBackground) },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        )
                    }
                    item {
                        WearSliderCard(
                            spec,
                            Icons.TwoTone.Image,
                            stringResource(R.string.settings_background_dim),
                            "${(state.backgroundDim * 100).roundToInt()}%",
                            state.backgroundDim,
                            { onAction(SettingsUiAction.SetBackgroundDim(it)) },
                            valueRange = 0f..1f,
                            steps = 19,
                        )
                    }
                    // How much of the background the components let through, matching the phone's
                    // card transparency slider.
                    item {
                        WearSliderCard(
                            spec,
                            Icons.TwoTone.Opacity,
                            stringResource(R.string.settings_card_alpha),
                            "${(state.cardAlpha * 100).roundToInt()}%",
                            state.cardAlpha,
                            { onAction(SettingsUiAction.SetCardAlpha(it)) },
                            valueRange = 0f..1f,
                            steps = 19,
                        )
                    }
                }
            }
        }
    }
}

/** A swatch toggle: the selected color morphs its shape and shows a check and an outline, not color alone. */
@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onSelect: () -> Unit) {
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onSelect() },
        // Fill the wider weighted cell while retaining the Wear toggle's selection animation.
        modifier = Modifier.fillMaxWidth(0.9f).heightIn(min = 48.dp),
        colors = IconToggleButtonDefaults.colors(
            checkedContainerColor = color,
            checkedContentColor = Color.Black,
            uncheckedContainerColor = color,
            uncheckedContentColor = Color.Black,
        ),
        shapes = IconToggleButtonDefaults.variantAnimatedShapes(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        Icon(
            Icons.TwoTone.Check,
            contentDescription = "${stringResource(R.string.theme_color)} ${color.toArgb().toHex()}",
            tint = if (selected) Color.Black else Color.Transparent,
        )
    }
}

/**
 * The editable hex value, previewed by a color dot in the icon slot; the `#RRGGBB` hint is its
 * secondary label, and the button turns red while the value is invalid.
 */
@Composable
private fun TransformingLazyColumnItemScope.HexColorButton(spec: TransformationSpec, hex: String, valid: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(spec),
        colors = if (valid) {
            ButtonDefaults.filledTonalButtonColors()
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        },
        icon = {
            Box(Modifier.size(ButtonDefaults.IconSize).background(if (valid) Color(hex.toColorInt()) else Color.Transparent, CircleShape))
        },
        secondaryLabel = { Text(stringResource(R.string.wear_invalid_color), maxLines = 2) },
    ) { Text(hex, maxLines = 1) }
}

/**
 * The pending UI scale in a slider card; the apply action is the single high-emphasis
 * button and runs only when the scale changed, and restoring the system scale only resets the
 * pending value.
 */
@Composable
internal fun WearDpiPage(
    state: SettingsUiState,
    onBack: () -> Unit,
    onAction: (SettingsUiAction) -> Unit,
    onOpen: (WearRoute) -> Unit,
    preferences: WearPreferences,
    preferenceViewModel: WearPreferencesViewModel,
    simpleMode: Boolean,
    onSimpleMode: (Boolean) -> Unit,
) {
    val systemDpi = state.systemDpi.coerceAtLeast(1)
    val pending = (state.tempDpi.toFloat() / systemDpi).coerceIn(0.75f, 1.5f)
    // Turning the Xiaomi Watch 5 mode on asks first, as it may not suit the watch it runs on.
    val xiaomiWatch5Dialog = rememberWearConfirmDialog(
        stringResource(R.string.wear_xiaomi_watch5_mode),
        stringResource(R.string.wear_xiaomi_watch5_warning),
        destructive = true,
    ) { preferenceViewModel.setXiaomiWatch5Mode(true) }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_display_touch)) }
        SegmentedColumn(
            listOf(R.string.wear_long_press_haptics, R.string.wear_keep_screen_on, R.string.wear_block_back_key, R.string.wear_disable_swipe_back, R.string.simple_mode),
            { it },
        ) { label ->
            val checked = when (label) {
                R.string.wear_long_press_haptics -> preferences.longPressHaptics
                R.string.wear_keep_screen_on -> preferences.keepScreenOn
                R.string.wear_block_back_key -> preferences.blockBackKey
                R.string.wear_disable_swipe_back -> !state.enableSwipeDismiss
                else -> simpleMode
            }
            WearSettingsSwitchWidget(
                spec,
                stringResource(label),
                checked,
                { value ->
                    when (label) {
                        R.string.wear_long_press_haptics -> preferenceViewModel.set(WearPreference.LongPressHaptics, value)
                        R.string.wear_keep_screen_on -> preferenceViewModel.set(WearPreference.KeepScreenOn, value)
                        R.string.wear_block_back_key -> preferenceViewModel.set(WearPreference.BlockBackKey, value)
                        R.string.wear_disable_swipe_back -> onAction(SettingsUiAction.SetSwipeDismiss(!value))
                        else -> onSimpleMode(value)
                    }
                },
                icon = Icons.TwoTone.Settings,
                secondaryLabel = when (label) {
                    R.string.wear_block_back_key -> stringResource(R.string.wear_block_back_key_summary)
                    R.string.wear_disable_swipe_back -> stringResource(R.string.wear_disable_swipe_back_summary)
                    R.string.simple_mode -> stringResource(R.string.simple_mode_summary)
                    else -> null
                },
            )
        }
        // Recognized Xiaomi Watch 5 devices use the mode on their own; elsewhere it is a manual,
        // confirmed choice. Either way the crown haptics follow it after the app restarts.
        item {
            WearSettingsSwitchWidget(
                spec,
                stringResource(R.string.wear_xiaomi_watch5_mode),
                preferences.xiaomiWatch5Detected || preferences.xiaomiWatch5Mode,
                { enabled -> if (enabled) xiaomiWatch5Dialog.show() else preferenceViewModel.setXiaomiWatch5Mode(false) },
                enabled = !preferences.xiaomiWatch5Detected,
                icon = Icons.TwoTone.Vibration,
                secondaryLabel = stringResource(
                    if (preferences.xiaomiWatch5Detected) R.string.wear_xiaomi_watch5_detected else R.string.wear_xiaomi_watch5_mode_summary,
                ),
            )
        }
        item { WearSectionHeader(spec, stringResource(R.string.predictive_back_settings)) }
        val backOptions = listOfNotNull(
            WearSelection.BackAnimation,
            WearSelection.BackDirection.takeIf { state.predictiveBackAnimation == PredictiveBackAnimation.Scale },
        )
        LazySegmentedColumn(backOptions, { it.name }) { selection ->
            WearSettingsJumpPageWidget(
                spec,
                stringResource(if (selection == WearSelection.BackAnimation) R.string.predictive_back_animation else R.string.predictive_back_exit_direction),
                { onOpen(WearRoute.Selection(selection)) },
                icon = Icons.TwoTone.Settings,
                description = if (selection == WearSelection.BackAnimation) backAnimationLabel(state.predictiveBackAnimation) else backDirectionLabel(state.predictiveBackExitDirection),
            )
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_display_screen_layout)) }
        // The scale label and value sit inside the slider card, above its track.
        item {
            WearSliderCard(
                spec,
                Icons.TwoTone.FormatSize,
                stringResource(R.string.app_dpi_title),
                stringResource(R.string.wear_ui_scale, pending),
                pending,
                { onAction(SettingsUiAction.SetTempDpi((it * systemDpi).roundToInt())) },
                valueRange = 0.75f..1.5f,
                steps = 14,
            )
        }
        item {
            WearActionButton(
                spec,
                Icons.TwoTone.Check,
                stringResource(R.string.dpi_apply_settings),
                { onAction(SettingsUiAction.ApplyDpi) },
                state.tempDpi != state.currentDpi,
                colors = ButtonDefaults.buttonColors(),
            )
        }
        item {
            WearActionButton(
                spec,
                Icons.TwoTone.Restore,
                stringResource(R.string.language_system_default),
                { onAction(SettingsUiAction.SetTempDpi(systemDpi)) },
                state.tempDpi != systemDpi,
                secondaryText = stringResource(R.string.wear_ui_scale, 1f),
                colors = ButtonDefaults.filledTonalButtonColors(),
            )
        }
        item {
            WearSettingsJumpPageWidget(
                spec,
                stringResource(R.string.wear_screen_shape),
                { onOpen(WearRoute.Selection(WearSelection.ScreenShape)) },
                description = screenShapeLabel(preferences.shape),
            )
        }
        if (android.os.Build.VERSION.SDK_INT < 33) {
            item {
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(R.string.settings_language),
                    { onOpen(WearRoute.Selection(WearSelection.Language)) },
                    description = state.currentAppLocale?.let { it.getDisplayName(it) } ?: stringResource(R.string.language_system_default),
                )
            }
        }
    }
}
