package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.domain.model.ProfileTemplateException
import org.bakasu.bakasu.domain.model.ProfileTemplateFailure
import org.bakasu.bakasu.domain.usecase.AddOnlineProfileTemplateUseCase
import org.bakasu.bakasu.domain.usecase.FetchOnlineProfileTemplatesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplatesUseCase

data class OnlineTemplateUiState(
    val templates: List<ProfileTemplate> = emptyList(),
    val loading: Boolean = true,
    val hasLoaded: Boolean = false,
    val loadFailure: ProfileTemplateFailure? = null,
    /** IDs of the local templates, which App Profile can select. */
    val addedIds: Set<String> = emptySet(),
    val adding: Boolean = false,
) {
    val isLoading: Boolean get() = loading && !hasLoaded
    val isRefreshing: Boolean get() = loading && hasLoaded
}

sealed interface OnlineTemplateUiAction {
    data object Load : OnlineTemplateUiAction
    data class Add(val template: ProfileTemplate) : OnlineTemplateUiAction
}

sealed interface OnlineTemplateUiEvent {
    data object Added : OnlineTemplateUiEvent
    data class AddFailed(val reason: ProfileTemplateFailure) : OnlineTemplateUiEvent
}

/** Online templates are browsed here without being stored; only [OnlineTemplateUiAction.Add] stores one. */
class OnlineTemplateViewModel(
    observeTemplates: ObserveProfileTemplatesUseCase,
    private val fetchOnlineTemplates: FetchOnlineProfileTemplatesUseCase,
    private val addOnlineTemplate: AddOnlineProfileTemplateUseCase,
) : ViewModel() {
    private val mutableEvents = MutableSharedFlow<OnlineTemplateUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<OnlineTemplateUiEvent> = mutableEvents.asSharedFlow()
    private val online = MutableStateFlow(OnlineTemplateUiState())

    val state: StateFlow<OnlineTemplateUiState> = combine(online, observeTemplates()) { state, local ->
        state.copy(addedIds = local.filter(ProfileTemplate::local).mapTo(mutableSetOf(), ProfileTemplate::id))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnlineTemplateUiState())

    private var loadJob: Job? = null

    fun cancelLoad() {
        loadJob?.cancel()
        online.update { it.copy(loading = false) }
    }

    init {
        load()
    }

    fun dispatch(action: OnlineTemplateUiAction) {
        when (action) {
            OnlineTemplateUiAction.Load -> load()
            is OnlineTemplateUiAction.Add -> add(action.template)
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        online.update { it.copy(loading = true, loadFailure = null) }
        loadJob = viewModelScope.launch {
            try {
                fetchOnlineTemplates().fold(
                    onSuccess = { templates -> online.update { it.copy(templates = templates, loading = false, hasLoaded = true, loadFailure = null) } },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        online.update { it.copy(loading = false, loadFailure = error.toFailure()) }
                    },
                )
            } finally {
                online.update { it.copy(loading = false) }
            }
        }
    }

    private fun add(template: ProfileTemplate) {
        if (online.value.adding) return
        online.update { it.copy(adding = true) }
        viewModelScope.launch {
            addOnlineTemplate(template).fold(
                onSuccess = { mutableEvents.emit(OnlineTemplateUiEvent.Added) },
                onFailure = { mutableEvents.emit(OnlineTemplateUiEvent.AddFailed(it.toFailure())) },
            )
            online.update { it.copy(adding = false) }
        }
    }

    private fun Throwable.toFailure(): ProfileTemplateFailure = (this as? ProfileTemplateException)?.reason
        ?: ProfileTemplateFailure.Command(message.orEmpty())
}
