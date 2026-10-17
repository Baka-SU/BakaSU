package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    /** IDs of the local templates, which App Profile can select. */
    val addedIds: Set<String> = emptySet(),
    val adding: Boolean = false,
)

sealed interface OnlineTemplateUiAction {
    data object Load : OnlineTemplateUiAction
    data class Add(val template: ProfileTemplate) : OnlineTemplateUiAction
}

sealed interface OnlineTemplateUiEvent {
    data class LoadFailed(val reason: ProfileTemplateFailure) : OnlineTemplateUiEvent
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
        online.update { it.copy(loading = true) }
        viewModelScope.launch {
            fetchOnlineTemplates().fold(
                onSuccess = { templates -> online.update { it.copy(templates = templates, loading = false) } },
                onFailure = { error ->
                    online.update { it.copy(loading = false) }
                    mutableEvents.emit(OnlineTemplateUiEvent.LoadFailed(error.toFailure()))
                },
            )
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
