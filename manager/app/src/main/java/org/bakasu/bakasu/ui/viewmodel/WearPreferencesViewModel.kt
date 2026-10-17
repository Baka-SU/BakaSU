package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.GetXiaomiWatch5ModeUseCase
import org.bakasu.bakasu.domain.usecase.IsXiaomiWatch5UseCase
import org.bakasu.bakasu.domain.usecase.SetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.SetXiaomiWatch5ModeUseCase

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */
data class WearPreferences(
    val picker: String = "auto",
    val link: String = "auto",
    val shape: String = "auto",
    val longPressHaptics: Boolean = true,
    val keepScreenOn: Boolean = false,
    val blockBackKey: Boolean = false,
    /** The device was recognized as a Xiaomi Watch 5, which turns the mode on by itself. */
    val xiaomiWatch5Detected: Boolean = false,
    val xiaomiWatch5Mode: Boolean = false,
)
class WearPreferencesViewModel(
    getPreference: GetStringPreferenceUseCase,
    private val setPreference: SetStringPreferenceUseCase,
    isXiaomiWatch5: IsXiaomiWatch5UseCase,
    getXiaomiWatch5Mode: GetXiaomiWatch5ModeUseCase,
    private val setXiaomiWatch5Mode: SetXiaomiWatch5ModeUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        WearPreferences(
            getPreference("wear_file_picker", "auto").orEmpty(),
            getPreference("wear_link_mode", "auto").orEmpty().let { if (it in LinkModes) it else "auto" },
            getPreference("wear_screen_shape", "auto").orEmpty(),
            getPreference("wear_long_press_haptics", "true").toBoolean(),
            getPreference("wear_keep_screen_on", "false").toBoolean(),
            getPreference("wear_block_back_key", "false").toBoolean(),
            isXiaomiWatch5(),
            getXiaomiWatch5Mode(),
        ),
    )
    val state = mutableState.asStateFlow()
    fun setPicker(mode: String) {
        setPreference("wear_file_picker", mode)
        mutableState.update { it.copy(picker = mode) }
    }
    fun setLink(mode: String) {
        setPreference("wear_link_mode", mode)
        mutableState.update { it.copy(link = mode) }
    }
    fun setShape(shape: String) {
        setPreference("wear_screen_shape", shape)
        mutableState.update { it.copy(shape = shape) }
    }
    fun setLongPressHaptics(enabled: Boolean) {
        setPreference("wear_long_press_haptics", enabled.toString())
        mutableState.update { it.copy(longPressHaptics = enabled) }
    }
    fun setKeepScreenOn(enabled: Boolean) {
        setPreference("wear_keep_screen_on", enabled.toString())
        mutableState.update { it.copy(keepScreenOn = enabled) }
    }
    fun setBlockBackKey(enabled: Boolean) {
        setPreference("wear_block_back_key", enabled.toString())
        mutableState.update { it.copy(blockBackKey = enabled) }
    }

    /** Saved now; the crown haptics follow it after the app restarts. */
    fun setXiaomiWatch5Mode(enabled: Boolean) {
        setXiaomiWatch5Mode.invoke(enabled)
        mutableState.update { it.copy(xiaomiWatch5Mode = enabled) }
    }
}

/** The saved link modes; the former "browser" mode was removed and falls back to automatic. */
private val LinkModes = setOf("auto", "webview", "phone")
