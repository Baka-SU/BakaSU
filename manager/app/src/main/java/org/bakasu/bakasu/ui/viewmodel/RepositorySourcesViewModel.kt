package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.RepositoryApiType
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.domain.usecase.AddModuleRepositoryUseCase
import org.bakasu.bakasu.domain.usecase.ObserveRepositorySourcesUseCase
import org.bakasu.bakasu.domain.usecase.RemoveModuleRepositoryUseCase
import org.bakasu.bakasu.domain.usecase.RenameModuleRepositoryUseCase

data class RepositorySourcesUiState(
    val sources: List<RepositorySource> = emptyList(),
    val failures: Map<String, RepositoryFailure> = emptyMap(),
    val url: String = "",
    val apiType: RepositoryApiType = RepositoryApiType.KERNEL_SU,
    val busy: Boolean = false,
    val addingUrl: String? = null,
    val error: RepositoryFailure? = null,
    val added: RepositorySource? = null,
)

class RepositorySourcesViewModel(
    private val observe: ObserveRepositorySourcesUseCase,
    private val addRepository: AddModuleRepositoryUseCase,
    private val renameRepository: RenameModuleRepositoryUseCase,
    private val removeRepository: RemoveModuleRepositoryUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val local = MutableStateFlow(
        RepositorySourcesUiState(
            url = savedState["repository-url"] ?: "",
            apiType = RepositoryApiType.entries.firstOrNull { it.name == savedState.get<String>("repository-api-type") }
                ?: RepositoryApiType.KERNEL_SU,
        ),
    )
    val state = combine(local, observe.sources, observe.failures) { state, sources, failures ->
        state.copy(sources = sources, failures = failures)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        local.value.copy(sources = observe.sources.value, failures = observe.failures.value),
    )

    fun setUrl(value: String) {
        savedState["repository-url"] = value
        local.update { it.copy(url = value, error = null, added = null) }
    }

    fun setApiType(value: RepositoryApiType) {
        savedState["repository-api-type"] = value.name
        local.update { it.copy(apiType = value, error = null, added = null) }
    }

    fun consumeAdded() {
        local.update { it.copy(added = null) }
    }

    fun clearError() {
        local.update { it.copy(error = null) }
    }

    fun add(
        url: String = local.value.url,
        apiType: RepositoryApiType = local.value.apiType,
    ) = perform(addingUrl = url) {
        addRepository(url, apiType).onSuccess { source -> local.update { it.copy(added = source) } }
            .onFailure { error -> local.update { it.copy(error = RepositoryFailure.from(error)) } }
    }

    fun rename(source: RepositorySource, name: String, onSaved: () -> Unit) = perform {
        renameRepository(source.url, name)
        onSaved()
    }

    fun remove(source: RepositorySource) = perform { removeRepository(source.url) }

    private fun perform(addingUrl: String? = null, block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, addingUrl = addingUrl, error = null, added = null) }
        launchTracked(onComplete = { local.update { it.copy(busy = false, addingUrl = null) } }) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = RepositoryFailure.from(e)) }
            }
        }
    }

    private fun launchTracked(onComplete: () -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch { block() }.invokeOnCompletion { onComplete() }
    }
}
