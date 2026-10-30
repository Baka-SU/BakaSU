package org.bakasu.bakasu.ui.wear

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Square
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import java.util.Locale
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.availablePaletteStyles
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsUiState
import org.bakasu.bakasu.ui.viewmodel.WearPreference
import org.bakasu.bakasu.ui.viewmodel.WearPreferences
import org.bakasu.bakasu.ui.viewmodel.WearPreferencesViewModel
import org.bakasu.bakasu.ui.wear.component.settings.WearChoicePage

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearSelectionPage(
    selection: WearSelection,
    state: SettingsUiState,
    preferences: WearPreferences,
    preferenceViewModel: WearPreferencesViewModel,
    onBack: () -> Unit,
    onAction: (SettingsUiAction) -> Unit,
) {
    when (selection) {
        WearSelection.TimeDisplay -> WearChoicePage(
            stringResource(R.string.wear_time_display),
            WearPreference.TimeDisplay.options.map { it to timeDisplayLabel(it) },
            preferences.timeDisplay,
            onBack,
        ) { preferenceViewModel.set(WearPreference.TimeDisplay, it) }

        WearSelection.Palette -> WearChoicePage(
            stringResource(R.string.dynamic_palette_style),
            state.dynamicColorSpec.availablePaletteStyles().map { it.name to paletteLabel(it) },
            state.dynamicPaletteStyle.name,
            onBack,
        ) { onAction(SettingsUiAction.SetDynamicPaletteStyle(PaletteStyle.valueOf(it))) }

        WearSelection.ColorSpec -> WearChoicePage(
            stringResource(R.string.dynamic_color_spec),
            ColorSpec.SpecVersion.entries.map { it.name to colorSpecLabel(it) },
            state.dynamicColorSpec.name,
            onBack,
        ) { onAction(SettingsUiAction.SetDynamicColorSpec(ColorSpec.SpecVersion.valueOf(it))) }

        WearSelection.PickerMode -> WearChoicePage(
            stringResource(R.string.wear_picker_mode),
            WearPreference.Picker.options.map { it to pickerModeLabel(it) },
            preferences.picker,
            onBack,
            icon = { selectionIcon(selection, it) },
        ) { preferenceViewModel.set(WearPreference.Picker, it) }

        WearSelection.ScreenShape -> WearChoicePage(
            stringResource(R.string.wear_screen_shape),
            WearPreference.Shape.options.map { it to screenShapeLabel(it) },
            preferences.shape,
            onBack,
            icon = { selectionIcon(selection, it) },
        ) { preferenceViewModel.set(WearPreference.Shape, it) }

        WearSelection.LinkMode -> WearChoicePage(
            stringResource(R.string.wear_link_mode),
            WearPreference.Link.options.map { it to linkModeLabel(it) },
            preferences.link,
            onBack,
            icon = { selectionIcon(selection, it) },
        ) { preferenceViewModel.set(WearPreference.Link, it) }

        WearSelection.Language -> {
            val tags = listOf(
                "en", "ar", "az", "bn", "bs", "da", "de", "es", "et", "fa", "fil", "fr", "gl", "hi", "hr", "hu",
                "id", "it", "he", "ja", "kn", "ko", "lt", "lv", "mr", "ms", "nl", "pl", "pt", "pt-BR", "ro", "ru", "sl", "sr",
                "te", "th", "tk", "tr", "uk", "vi", "zh-CN", "zh-HK", "zh-TW",
            )
            val choices = listOf("system" to stringResource(R.string.language_system_default)) + tags.map {
                val locale = Locale.forLanguageTag(it)
                locale.settingTag() to locale.getDisplayName(locale)
            }.sortedBy { it.second }
            WearChoicePage(
                stringResource(R.string.settings_language),
                choices,
                state.currentAppLocale?.settingTag() ?: "system",
                onBack,
                icon = { selectionIcon(selection, it) },
            ) {
                onAction(SettingsUiAction.SetLanguage(it))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onAction(SettingsUiAction.RestartActivity)
            }
        }

        WearSelection.SuCompat -> WearChoicePage(
            stringResource(R.string.settings_sucompat),
            (0..2).map { it.toString() to suCompatModeLabel(it) },
            state.suCompatMode.toString(),
            onBack,
            enabled = state.suStatus == "supported",
            icon = { selectionIcon(selection, it) },
        ) { onAction(SettingsUiAction.SetSuCompatMode(it.toInt())) }
    }
}

private fun selectionIcon(selection: WearSelection, value: String): ImageVector = when (selection) {
    WearSelection.TimeDisplay -> Icons.Rounded.Schedule

    WearSelection.ScreenShape -> when (value) {
        "round" -> Icons.Rounded.Circle
        "square" -> Icons.Rounded.Square
        else -> Icons.Rounded.AutoAwesome
    }

    WearSelection.Language -> Icons.Rounded.Translate

    WearSelection.SuCompat -> Icons.Rounded.RemoveModerator

    WearSelection.LinkMode -> when (value) {
        "webview" -> Icons.Rounded.Language
        "phone" -> Icons.Rounded.Smartphone
        else -> Icons.Rounded.AutoAwesome
    }

    WearSelection.PickerMode -> if (value == "auto") Icons.Rounded.AutoAwesome else Icons.Rounded.FolderOpen

    WearSelection.Palette, WearSelection.ColorSpec -> Icons.Rounded.AutoAwesome
}

@Composable
internal fun timeDisplayLabel(mode: String) = stringResource(
    when (mode) {
        "title" -> R.string.wear_time_mode_title
        "hidden" -> R.string.wear_time_mode_hidden
        else -> R.string.wear_time_mode_time
    },
)

/** The language tag stored in app_locale, shared with the phone. */
private fun Locale.settingTag() = if (country.isEmpty()) language else "${language}_$country"

@Composable
internal fun pickerModeLabel(mode: String) = stringResource(
    when (mode) {
        "builtin" -> R.string.wear_builtin_picker
        "system" -> R.string.wear_system_picker
        else -> R.string.wear_auto
    },
)

@Composable
internal fun screenShapeLabel(shape: String) = stringResource(
    when (shape) {
        "round" -> R.string.wear_shape_round
        "square" -> R.string.wear_shape_square
        else -> R.string.wear_auto
    },
)

@Composable
internal fun linkModeLabel(mode: String) = stringResource(
    when (mode) {
        "webview" -> R.string.wear_watch_webui
        "phone" -> R.string.wear_phone
        else -> R.string.wear_auto
    },
)

@Composable
internal fun suCompatModeLabel(mode: Int) = stringResource(
    when (mode) {
        1 -> R.string.settings_mode_disable_until_reboot
        2 -> R.string.settings_mode_disable_always
        else -> R.string.settings_mode_default
    },
)

@Composable
internal fun paletteLabel(style: PaletteStyle) = stringResource(
    when (style) {
        PaletteStyle.TonalSpot -> R.string.wear_palette_tonal_spot
        PaletteStyle.Neutral -> R.string.wear_palette_neutral
        PaletteStyle.Vibrant -> R.string.wear_palette_vibrant
        PaletteStyle.Expressive -> R.string.wear_palette_expressive
        PaletteStyle.Rainbow -> R.string.wear_palette_rainbow
        PaletteStyle.FruitSalad -> R.string.wear_palette_fruit_salad
        PaletteStyle.Monochrome -> R.string.wear_palette_monochrome
        PaletteStyle.Fidelity -> R.string.wear_palette_fidelity
        PaletteStyle.Content -> R.string.wear_palette_content
    },
)

@Composable
internal fun colorSpecLabel(spec: ColorSpec.SpecVersion) = stringResource(
    when (spec) {
        ColorSpec.SpecVersion.SPEC_2021 -> R.string.wear_color_spec_2021
        ColorSpec.SpecVersion.SPEC_2025 -> R.string.wear_color_spec_2025
    },
)
