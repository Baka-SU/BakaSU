package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.resukisu.resukisu.domain.usecase.GetStringPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.SetStringPreferenceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WearPreferences(val picker: String = "auto", val link: String = "auto")
class WearPreferencesViewModel(getPreference: GetStringPreferenceUseCase, private val setPreference: SetStringPreferenceUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow(WearPreferences(
        getPreference("wear_file_picker", "auto").orEmpty(), getPreference("wear_link_mode", "auto").orEmpty().let { if (it in LinkModes) it else "auto" },
    ))
    val state = mutableState.asStateFlow()
    fun setPicker(mode: String) {
        setPreference("wear_file_picker", mode)
        mutableState.update { it.copy(picker = mode) }
    }
    fun setLink(mode: String) {
        setPreference("wear_link_mode", mode)
        mutableState.update { it.copy(link = mode) }
    }
}

/** The saved link modes; the former "browser" mode was removed and falls back to automatic. */
private val LinkModes = setOf("auto", "webview", "phone")
