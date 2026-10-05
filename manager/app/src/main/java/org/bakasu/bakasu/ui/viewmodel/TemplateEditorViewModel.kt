package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.domain.model.ProfileTemplateException
import org.bakasu.bakasu.domain.model.ProfileTemplateFailure
import org.bakasu.bakasu.domain.usecase.DeleteProfileTemplateUseCase
import org.bakasu.bakasu.domain.usecase.GetProfileTemplateUseCase
import org.bakasu.bakasu.domain.usecase.SaveProfileTemplateUseCase

data class TemplateEditorUiState(
    val template: ProfileTemplate = ProfileTemplate(),
    val loading: Boolean = true,
    val loadFailure: ProfileTemplateFailure? = null,
    val readOnly: Boolean = true,
    val isCreation: Boolean = false,
)

sealed interface TemplateEditorUiAction {
    data object Load : TemplateEditorUiAction
    data class Update(val template: ProfileTemplate, val autoSave: Boolean = false) : TemplateEditorUiAction

    data object Save : TemplateEditorUiAction
    data object Delete : TemplateEditorUiAction
}

sealed interface TemplateEditorUiEvent {
    data object Saved : TemplateEditorUiEvent
    data object Deleted : TemplateEditorUiEvent
    data class Error(val reason: ProfileTemplateFailure) : TemplateEditorUiEvent
}

class TemplateEditorViewModel(
    private val templateId: String,
    private val readOnly: Boolean,
    private val isCreation: Boolean,
    private val getTemplate: GetProfileTemplateUseCase,
    private val saveTemplate: SaveProfileTemplateUseCase,
    private val deleteTemplate: DeleteProfileTemplateUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        TemplateEditorUiState(readOnly = readOnly, isCreation = isCreation),
    )
    private val mutableEvents = MutableSharedFlow<TemplateEditorUiEvent>(extraBufferCapacity = 1)

    val state: StateFlow<TemplateEditorUiState> = mutableState.asStateFlow()
    val events: SharedFlow<TemplateEditorUiEvent> = mutableEvents.asSharedFlow()
    private val saveMutex = Mutex()
    private var autoSaveJob: Job? = null

    init {
        dispatch(TemplateEditorUiAction.Load)
    }

    fun dispatch(action: TemplateEditorUiAction) {
        when (action) {
            TemplateEditorUiAction.Load -> load()

            is TemplateEditorUiAction.Update -> {
                mutableState.update { it.copy(template = action.template) }
                if (action.autoSave && !readOnly) scheduleAutoSave()
            }

            TemplateEditorUiAction.Save -> {
                autoSaveJob?.cancel()
                save(create = isCreation)
            }

            TemplateEditorUiAction.Delete -> viewModelScope.launch {
                autoSaveJob?.cancel()
                deleteTemplate(mutableState.value.template.id).fold(
                    onSuccess = { mutableEvents.tryEmit(TemplateEditorUiEvent.Deleted) },
                    onFailure = { mutableEvents.tryEmit(TemplateEditorUiEvent.Error(it.toFailure())) },
                )
            }
        }
    }

    private fun load() {
        if (isCreation) {
            mutableState.update {
                it.copy(
                    template = ProfileTemplate(id = templateId),
                    loading = false,
                    loadFailure = null,
                )
            }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, loadFailure = null) }
            getTemplate(templateId).fold(
                onSuccess = { template ->
                    mutableState.update {
                        it.copy(template = template, loading = false, loadFailure = null)
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(loading = false, loadFailure = error.toFailure())
                    }
                },
            )
        }
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MILLIS.milliseconds)
            persist(create = false, emitSuccess = false)
        }
    }

    private fun save(create: Boolean) {
        viewModelScope.launch {
            persist(create, true)
        }
    }

    private suspend fun persist(create: Boolean, emitSuccess: Boolean) {
        saveMutex.withLock {
            saveTemplate(mutableState.value.template, create).fold(
                onSuccess = {
                    if (emitSuccess) mutableEvents.tryEmit(TemplateEditorUiEvent.Saved)
                },
                onFailure = { mutableEvents.tryEmit(TemplateEditorUiEvent.Error(it.toFailure())) },
            )
        }
    }

    private fun Throwable.toFailure(): ProfileTemplateFailure = (this as? ProfileTemplateException)?.reason
        ?: ProfileTemplateFailure.Command(message.orEmpty())

    private companion object {
        const val AUTO_SAVE_DEBOUNCE_MILLIS = 300L
    }
}
