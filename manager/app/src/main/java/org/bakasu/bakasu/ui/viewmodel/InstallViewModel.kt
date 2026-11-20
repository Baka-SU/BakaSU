package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.InstallEnvironment
import org.bakasu.bakasu.domain.usecase.GetInstallEnvironmentUseCase
import org.bakasu.bakasu.domain.usecase.RebootUseCase

data class InstallUiState(
    val environment: InstallEnvironment = InstallEnvironment(),
    val loading: Boolean = true,
    val refreshVersion: Int = 0,
    val error: String? = null,
)

sealed interface InstallUiAction {
    data object Refresh : InstallUiAction
    data object Reboot : InstallUiAction
}

sealed interface InstallUiEvent {
    data class Error(val message: String) : InstallUiEvent
}

class InstallViewModel(
    private val getInstallEnvironment: GetInstallEnvironmentUseCase,
    private val reboot: RebootUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        getInstallEnvironment.cached()?.let { cached ->
            InstallUiState(environment = cached, loading = false)
        } ?: InstallUiState(),
    )
    private val mutableEvents = MutableSharedFlow<InstallUiEvent>(extraBufferCapacity = 1)

    val state: StateFlow<InstallUiState> = mutableState.asStateFlow()
    val events: SharedFlow<InstallUiEvent> = mutableEvents.asSharedFlow()
    private var loadJob: Job? = null

    init {
        if (mutableState.value.loading) load(forceRefresh = false)
    }

    fun dispatch(action: InstallUiAction) {
        when (action) {
            InstallUiAction.Refresh -> load(forceRefresh = true)

            InstallUiAction.Reboot -> viewModelScope.launch {
                reboot().onFailure {
                    mutableEvents.tryEmit(InstallUiEvent.Error(it.message.orEmpty()))
                }
            }
        }
    }

    private fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            runCatching { getInstallEnvironment(forceRefresh) }
                .onSuccess { environment ->
                    mutableState.update { it.copy(environment = environment, loading = false, refreshVersion = it.refreshVersion + if (forceRefresh) 1 else 0) }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    mutableState.update { it.copy(loading = false, error = error.message.orEmpty(), refreshVersion = it.refreshVersion + if (forceRefresh) 1 else 0) }
                    mutableEvents.tryEmit(InstallUiEvent.Error(error.message.orEmpty()))
                }
        }
    }
}
