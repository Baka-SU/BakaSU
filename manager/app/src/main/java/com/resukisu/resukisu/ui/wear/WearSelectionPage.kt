package com.resukisu.resukisu.ui.wear

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Animation
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Circle
import androidx.compose.material.icons.twotone.FolderOpen
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material.icons.twotone.SwapHoriz
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.Square
import androidx.compose.material.icons.twotone.Translate
import androidx.compose.material.icons.twotone.RemoveModerator
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.settings.WearChoicePage
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import com.resukisu.resukisu.ui.viewmodel.WearPreferences
import com.resukisu.resukisu.ui.viewmodel.WearPreferencesViewModel
import java.util.Locale

@Composable
internal fun WearSelectionPage(route: String, state: SettingsUiState, preferences: WearPreferences,
    preferenceViewModel: WearPreferencesViewModel, message: String?, onBack: () -> Unit, onAction: (SettingsUiAction) -> Unit) {
    when (route) {
        "picker-mode" -> WearChoicePage(stringResource(R.string.wear_picker_mode),
            PickerModes.map { it to pickerModeLabel(it) },
            preferences.picker, onBack, message = message, icon = { selectionIcon(route, it) },
            onChoose = preferenceViewModel::setPicker)
        "screen-shape" -> WearChoicePage(stringResource(R.string.wear_screen_shape),
            ScreenShapes.map { it to screenShapeLabel(it) },
            preferences.shape, onBack, icon = { selectionIcon(route, it) }, onChoose = preferenceViewModel::setShape)
        "page-animation" -> WearChoicePage(stringResource(R.string.wear_page_animation),
            PageAnimations.map { it to pageAnimationLabel(it) }, preferences.pageAnimation, onBack,
            icon = { selectionIcon(route, it) }, onChoose = preferenceViewModel::setPageAnimation)
        "link-mode" -> WearChoicePage(stringResource(R.string.wear_link_mode),
            LinkModes.map { it to linkModeLabel(it) },
            preferences.link, onBack, message = message, icon = { selectionIcon(route, it) },
            onChoose = preferenceViewModel::setLink)
        "language" -> {
            val tags = listOf("en", "ar", "az", "bn", "bs", "da", "de", "es", "et", "fa", "fil", "fr", "gl", "hi", "hr", "hu",
                "id", "it", "he", "ja", "kn", "ko", "lt", "lv", "mr", "ms", "nl", "pl", "pt", "pt-BR", "ro", "ru", "sl", "sr",
                "te", "th", "tk", "tr", "uk", "vi", "zh-CN", "zh-HK", "zh-TW")
            val choices = listOf("system" to stringResource(R.string.language_system_default)) + tags.map {
                val locale = Locale.forLanguageTag(it)
                locale.settingTag() to locale.getDisplayName(locale)
            }.sortedBy { it.second }
            WearChoicePage(stringResource(R.string.settings_language), choices,
                state.currentAppLocale?.settingTag() ?: "system", onBack, icon = { selectionIcon(route, it) }) {
                onAction(SettingsUiAction.SetLanguage(it))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onAction(SettingsUiAction.RestartActivity)
            }
        }
        "sucompat" -> WearChoicePage(stringResource(R.string.settings_sucompat),
            (0..2).map { it.toString() to suCompatModeLabel(it) },
            state.suCompatMode.toString(), onBack, enabled = state.suStatus == "supported",
            icon = { selectionIcon(route, it) }) { onAction(SettingsUiAction.SetSuCompatMode(it.toInt())) }
    }
}

private fun selectionIcon(route: String, value: String): ImageVector = when (route) {
    "screen-shape" -> when (value) {
        "round" -> Icons.TwoTone.Circle
        "square" -> Icons.TwoTone.Square
        else -> Icons.TwoTone.AutoAwesome
    }
    "page-animation" -> if (value == "slide") Icons.TwoTone.SwapHoriz else Icons.TwoTone.Animation
    "language" -> Icons.TwoTone.Translate
    "sucompat" -> Icons.TwoTone.RemoveModerator
    "link-mode" -> when (value) {
        "webview" -> Icons.TwoTone.Language
        "phone" -> Icons.TwoTone.Smartphone
        else -> Icons.TwoTone.AutoAwesome
    }
    "picker-mode" -> if (value == "auto") Icons.TwoTone.AutoAwesome else Icons.TwoTone.FolderOpen
    else -> Icons.TwoTone.Settings
}

/** The `language` or `language_COUNTRY` form stored in `app_locale` and parsed by LocaleHelper, as on the phone. */
private fun Locale.settingTag() = if (country.isEmpty()) language else "${language}_$country"

private val PickerModes = listOf("auto", "builtin", "system")
private val LinkModes = listOf("auto", "webview", "phone")
private val ScreenShapes = listOf("auto", "round", "square")
private val PageAnimations = listOf("fade-scale", "slide")

@Composable
internal fun pageAnimationLabel(animation: String) = stringResource(when (animation) {
    "slide" -> R.string.wear_animation_slide
    else -> R.string.wear_animation_fade_scale
})

/** Labels shared by the choice pages and the current-value summaries in settings. */
@Composable
internal fun pickerModeLabel(mode: String) = stringResource(when (mode) {
    "builtin" -> R.string.wear_builtin_picker
    "system" -> R.string.wear_system_picker
    else -> R.string.wear_auto
})

@Composable
internal fun screenShapeLabel(shape: String) = stringResource(when (shape) {
    "round" -> R.string.wear_shape_round
    "square" -> R.string.wear_shape_square
    else -> R.string.wear_auto
})

@Composable
internal fun linkModeLabel(mode: String) = stringResource(when (mode) {
    "webview" -> R.string.wear_watch_webui
    "phone" -> R.string.wear_phone
    else -> R.string.wear_auto
})

@Composable
internal fun suCompatModeLabel(mode: Int) = stringResource(when (mode) {
    1 -> R.string.settings_mode_disable_until_reboot
    2 -> R.string.settings_mode_disable_always
    else -> R.string.settings_mode_default
})
