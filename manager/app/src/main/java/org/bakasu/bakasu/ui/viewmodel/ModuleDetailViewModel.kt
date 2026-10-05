package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleCatalogFailure
import org.bakasu.bakasu.domain.model.ModuleCatalogResult
import org.bakasu.bakasu.domain.usecase.GetCatalogModuleUseCase

data class ModuleDetailUiState(
    val module: CatalogModule? = null,
    val loading: Boolean = true,
    val error: ModuleCatalogFailure? = null,
)

sealed interface ModuleDetailUiAction {
    data object Retry : ModuleDetailUiAction
}

sealed interface ModuleDetailUiEvent {
    data class Error(val reason: ModuleCatalogFailure) : ModuleDetailUiEvent
}

class ModuleDetailViewModel(
    private val moduleId: String,
    private val getModule: GetCatalogModuleUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ModuleDetailUiState())
    private val mutableEvents = MutableSharedFlow<ModuleDetailUiEvent>(extraBufferCapacity = 1)

    val state: StateFlow<ModuleDetailUiState> = mutableState.asStateFlow()
    val events: SharedFlow<ModuleDetailUiEvent> = mutableEvents.asSharedFlow()

    init {
        load()
    }

    fun dispatch(action: ModuleDetailUiAction) {
        when (action) {
            ModuleDetailUiAction.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            when (val result = getModule(moduleId)) {
                is ModuleCatalogResult.Success -> mutableState.value = ModuleDetailUiState(
                    module = result.value,
                    loading = false,
                )

                is ModuleCatalogResult.Failure -> {
                    mutableState.value = ModuleDetailUiState(
                        loading = false,
                        error = result.reason,
                    )
                    mutableEvents.emit(ModuleDetailUiEvent.Error(result.reason))
                }
            }
        }
    }
}
