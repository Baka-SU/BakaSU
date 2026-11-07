package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.usecase.FetchRemoteTextUseCase

data class ModuleChangelogUiState(
    val loading: Boolean = false,
    val content: String = "",
    val error: String? = null,
)

class ModuleChangelogViewModel(
    private val url: String,
    private val fetchRemoteText: FetchRemoteTextUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ModuleChangelogUiState())
    val state = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (url.isBlank() || mutableState.value.loading) return
        mutableState.value = ModuleChangelogUiState(loading = true)
        viewModelScope.launch {
            mutableState.value = fetchRemoteText(url).fold(
                onSuccess = { ModuleChangelogUiState(content = it) },
                onFailure = { ModuleChangelogUiState(error = it.message.orEmpty()) },
            )
        }
    }
}
