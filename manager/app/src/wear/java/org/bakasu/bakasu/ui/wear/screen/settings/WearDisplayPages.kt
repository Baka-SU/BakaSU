package org.bakasu.bakasu.ui.wear.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.lazy.TransformationSpec
import kotlin.math.roundToInt
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.theme.BackgroundManager
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsUiState
import org.bakasu.bakasu.ui.viewmodel.WearPreference
import org.bakasu.bakasu.ui.viewmodel.WearPreferences
import org.bakasu.bakasu.ui.viewmodel.WearPreferencesViewModel
import org.bakasu.bakasu.ui.viewmodel.resolvedToastStyle
import org.bakasu.bakasu.ui.wear.WearRoute
import org.bakasu.bakasu.ui.wear.WearSelection
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearSliderCard
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.bakasu.bakasu.ui.wear.screen.module.colorSpecLabel
import org.bakasu.bakasu.ui.wear.screen.module.paletteLabel
import org.bakasu.bakasu.ui.wear.screen.module.screenShapeLabel
import org.bakasu.bakasu.ui.wear.screen.module.selectionIcon
import org.bakasu.bakasu.ui.wear.screen.module.timeDisplayLabel
import org.bakasu.bakasu.ui.wear.screen.module.toastDisplayLabel
import org.koin.compose.koinInject

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

private fun Int.toHex() = "#%06X".format(this and 0xFFFFFF)

internal fun Int.toWearColorHsv() = FloatArray(3).also { android.graphics.Color.colorToHSV(this, it) }

@Composable
internal fun WearColorPage(state: SettingsUiState, seed: Int, onBack: () -> Unit, onAction: (SettingsUiAction) -> Unit, onOpen: (WearRoute) -> Unit, onImage: () -> Unit) {
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
    val hex = seed.toHex()
    Box(Modifier.fillMaxSize()) {
        val columns = 3
        WearList(onBack = onBack) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.theme_color)) }
            item {
                WearSettingsSwitchWidget(
                    spec,
                    stringResource(R.string.dynamic_color_title),
                    state.useDynamicColor,
                    { onAction(SettingsUiAction.SetDynamicColor(it)) },
                    icon = Icons.Rounded.Palette,
                    secondaryLabel = stringResource(R.string.dynamic_color_summary),
                )
            }
            SegmentedColumn(listOf(WearSelection.Palette, WearSelection.ColorSpec), { it.name }) { selection ->
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(if (selection == WearSelection.Palette) R.string.dynamic_palette_style else R.string.dynamic_color_spec),
                    { onOpen(WearRoute.Selection(selection)) },
                    icon = Icons.Rounded.Palette,
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
                            icon = Icons.Rounded.Image,
                        )
                    }
                }
                item {
                    WearSettingsSwitchWidget(
                        spec,
                        stringResource(R.string.settings_custom_enable_high_contrast),
                        config.isHighContrastMode,
                        backgrounds::saveEnableHighContrastMode,
                        icon = Icons.Rounded.Contrast,
                        secondaryLabel = stringResource(R.string.settings_custom_enable_high_contrast_summary),
                    )
                }
            }
            item { WearSectionHeader(spec, stringResource(R.string.choose_theme_color)) }
            swatches.chunked(columns).forEachIndexed { index, row ->
                item(key = "swatches-$index") {
                    WearScaledItem(spec) {
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { value ->
                                Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                    ColorSwatch(Color(value), !state.useDynamicColor && config.seedColor == Color(value).toArgb()) {
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
            item { HexColorButton(spec, hex) { onOpen(WearRoute.CustomColor) } }
            item { WearSectionHeader(spec, stringResource(R.string.settings_custom_background)) }
            item {
                WearActionButton(
                    spec,
                    Icons.Rounded.Image,
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
                            icon = Icons.Rounded.Image,
                        )
                    }
                }
                item {
                    WearActionButton(
                        spec,
                        Icons.Rounded.Delete,
                        stringResource(R.string.wear_remove_background),
                        { onAction(SettingsUiAction.RemoveCustomBackground) },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    )
                }
                item {
                    WearSliderCard(
                        spec,
                        Icons.Rounded.Image,
                        stringResource(R.string.settings_background_dim),
                        "${(state.backgroundDim * 100).roundToInt()}%",
                        state.backgroundDim,
                        { onAction(SettingsUiAction.SetBackgroundDim(it)) },
                        valueRange = 0f..1f,
                        steps = 19,
                    )
                }
                item {
                    WearSliderCard(
                        spec,
                        Icons.Rounded.Opacity,
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

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onSelect: () -> Unit) {
    val contentColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onSelect() },
        modifier = Modifier.fillMaxWidth(0.9f).heightIn(min = 48.dp),
        colors = IconToggleButtonDefaults.colors(
            checkedContainerColor = color,
            checkedContentColor = contentColor,
            uncheckedContainerColor = color,
            uncheckedContentColor = contentColor,
        ),
        shapes = IconToggleButtonDefaults.variantAnimatedShapes(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = "${stringResource(R.string.theme_color)} ${color.toArgb().toHex()}",
            tint = if (selected) contentColor else Color.Transparent,
        )
    }
}

@Composable
private fun TransformingLazyColumnItemScope.HexColorButton(spec: TransformationSpec, hex: String, onClick: () -> Unit) {
    WearSettingsJumpPageWidget(
        spec = spec,
        title = hex,
        onClick = onClick,
        description = stringResource(R.string.wear_color_hex),
        iconContent = {
            Box(Modifier.size(ButtonDefaults.IconSize).background(Color(hex.toColorInt()), CircleShape))
        },
    )
}

/** UI scale changes, including reset, remain pending until Apply. */
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
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_display_touch)) }
        SegmentedColumn(listOf(WearSelection.TimeDisplay, WearSelection.ToastDisplay, WearSelection.Language), { it }) { selection ->
            WearSettingsJumpPageWidget(
                spec,
                stringResource(
                    when (selection) {
                        WearSelection.TimeDisplay -> R.string.wear_time_display
                        WearSelection.ToastDisplay -> R.string.wear_toast_display
                        else -> R.string.settings_language
                    },
                ),
                { onOpen(WearRoute.Selection(selection)) },
                icon = selectionIcon(selection, ""),
                description = when (selection) {
                    WearSelection.TimeDisplay -> timeDisplayLabel(preferences.timeDisplay)
                    WearSelection.ToastDisplay -> toastDisplayLabel(preferences.resolvedToastStyle(LocalConfiguration.current.isScreenRound))
                    else -> state.currentAppLocale?.let { it.getDisplayName(it) } ?: stringResource(R.string.language_system_default)
                },
            )
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_display_screen_layout)) }
        item {
            WearSliderCard(
                spec,
                Icons.Rounded.FormatSize,
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
                Icons.Rounded.Check,
                stringResource(R.string.dpi_apply_settings),
                { onAction(SettingsUiAction.ApplyDpi) },
                state.tempDpi != state.currentDpi,
                colors = ButtonDefaults.buttonColors(),
            )
        }
        item {
            WearActionButton(
                spec,
                Icons.Rounded.History,
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
                icon = Icons.Rounded.AspectRatio,
                description = screenShapeLabel(preferences.shape),
            )
        }
        item {
            WearSettingsSwitchWidget(
                spec,
                stringResource(R.string.icon_switch_title),
                state.useAltIcon,
                { onAction(SettingsUiAction.SetAlternateIcon(it)) },
                icon = Icons.Rounded.Apps,
                secondaryLabel = stringResource(R.string.icon_switch_summary),
            )
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_display_interaction)) }
        SegmentedColumn(
            listOf(R.string.wear_keep_screen_on, R.string.simple_mode, R.string.wear_long_press_haptics, R.string.wear_block_back_key, R.string.wear_disable_swipe_back),
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
                icon = when (label) {
                    R.string.wear_long_press_haptics -> Icons.Rounded.Vibration
                    R.string.wear_keep_screen_on -> Icons.Rounded.LightMode
                    R.string.wear_block_back_key -> Icons.Rounded.BackHand
                    R.string.wear_disable_swipe_back -> Icons.Rounded.Swipe
                    else -> Icons.Rounded.ViewAgenda
                },
                secondaryLabel = when (label) {
                    R.string.wear_block_back_key -> stringResource(R.string.wear_block_back_key_summary)
                    R.string.wear_disable_swipe_back -> stringResource(R.string.wear_disable_swipe_back_summary)
                    R.string.simple_mode -> stringResource(R.string.simple_mode_summary)
                    else -> null
                },
            )
        }
    }
}

@Composable
internal fun WearCustomColorPage(
    hsv: FloatArray,
    state: SettingsUiState,
    onHsvChange: (FloatArray) -> Unit,
    onEditHex: (String) -> Unit,
    onAction: (SettingsUiAction) -> Unit,
    onBack: () -> Unit,
) {
    val (hue, saturation, brightness) = hsv
    val color = android.graphics.Color.HSVToColor(hsv)
    val hex = color.toHex()
    WearList(
        onBack = onBack,
        onConfirm = { onAction(SettingsUiAction.ApplyWearThemeColor(color)) },
        confirmLabel = stringResource(R.string.confirm),
        confirmEnabled = !state.applyingThemeColor,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_custom_color)) }
        item { HexColorButton(spec, hex) { onEditHex(hex) } }
        item { WearSliderCard(spec, Icons.Rounded.Palette, stringResource(R.string.wear_color_hue), "${hue.roundToInt()}°", hue, { onHsvChange(floatArrayOf(it, saturation, brightness)) }, 0f..360f, 359) }
        item { WearSliderCard(spec, Icons.Rounded.Opacity, stringResource(R.string.wear_color_saturation), "${(saturation * 100).roundToInt()}%", saturation, { onHsvChange(floatArrayOf(hue, it, brightness)) }, 0f..1f, 99) }
        item { WearSliderCard(spec, Icons.Rounded.LightMode, stringResource(R.string.wear_color_brightness), "${(brightness * 100).roundToInt()}%", brightness, { onHsvChange(floatArrayOf(hue, saturation, it)) }, 0f..1f, 99) }
    }
}

@Composable
internal fun WearColorHexPage(initial: String, onSubmit: (FloatArray) -> Unit) {
    WearTextInputPage(
        stringResource(R.string.wear_color_hex),
        initial,
        canSubmit = { it.trim().removePrefix("#").matches(Regex("[0-9a-fA-F]{6}")) },
    ) {
        onSubmit(("#" + it.trim().removePrefix("#")).toColorInt().toWearColorHsv())
    }
}
