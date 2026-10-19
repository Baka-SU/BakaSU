package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.SetStringPreferenceUseCase

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * The watch-only preferences, each with its saved key, default and, for a choice, its [options]. A
 * saved value that is no longer an option, such as the removed "browser" link mode, reads as the
 * default.
 */
enum class WearPreference(val key: String, val default: String, val options: List<String> = emptyList()) {
    Picker("wear_file_picker", "auto", listOf("auto", "builtin", "system")),
    Link("wear_link_mode", "auto", listOf("auto", "webview", "phone")),
    Shape("wear_screen_shape", "auto", listOf("auto", "round", "square")),
    LongPressHaptics("wear_long_press_haptics", "true"),
    KeepScreenOn("wear_keep_screen_on", "false"),
    BlockBackKey("wear_block_back_key", "false"),
}

/** Reads a Wear preference, falling back to its default. */
fun GetStringPreferenceUseCase.wear(preference: WearPreference): String = invoke(preference.key, preference.default)
    ?.takeIf { preference.options.isEmpty() || it in preference.options } ?: preference.default

data class WearPreferences(
    val picker: String = WearPreference.Picker.default,
    val link: String = WearPreference.Link.default,
    val shape: String = WearPreference.Shape.default,
    val longPressHaptics: Boolean = true,
    val keepScreenOn: Boolean = false,
    val blockBackKey: Boolean = false,
)

class WearPreferencesViewModel(
    private val getPreference: GetStringPreferenceUseCase,
    private val setPreference: SetStringPreferenceUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()

    private fun read() = WearPreferences(
        getPreference.wear(WearPreference.Picker),
        getPreference.wear(WearPreference.Link),
        getPreference.wear(WearPreference.Shape),
        getPreference.wear(WearPreference.LongPressHaptics).toBoolean(),
        getPreference.wear(WearPreference.KeepScreenOn).toBoolean(),
        getPreference.wear(WearPreference.BlockBackKey).toBoolean(),
    )

    fun set(preference: WearPreference, value: String) {
        setPreference(preference.key, value)
        mutableState.update { read() }
    }

    fun set(preference: WearPreference, enabled: Boolean) = set(preference, enabled.toString())
}
