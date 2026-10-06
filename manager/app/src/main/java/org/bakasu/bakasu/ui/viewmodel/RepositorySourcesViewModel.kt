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
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.domain.usecase.AddModuleRepositoryUseCase
import org.bakasu.bakasu.domain.usecase.DiscoverModuleRepositoriesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveRepositorySourcesUseCase
import org.bakasu.bakasu.domain.usecase.RemoveModuleRepositoryUseCase
import org.bakasu.bakasu.domain.usecase.RenameModuleRepositoryUseCase

data class RepositorySourcesUiState(
    val sources: List<RepositorySource> = emptyList(),
    val discovered: List<RepositorySource> = emptyList(),
    val failures: Map<String, RepositoryFailure> = emptyMap(),
    val url: String = "",
    val discovering: Boolean = false,
    val busy: Boolean = false,
    val addingUrl: String? = null,
    val discoveryError: RepositoryFailure? = null,
    val error: RepositoryFailure? = null,
    val added: RepositorySource? = null,
)

class RepositorySourcesViewModel(
    private val observe: ObserveRepositorySourcesUseCase,
    private val discover: DiscoverModuleRepositoriesUseCase,
    private val addRepository: AddModuleRepositoryUseCase,
    private val renameRepository: RenameModuleRepositoryUseCase,
    private val removeRepository: RemoveModuleRepositoryUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val local = MutableStateFlow(RepositorySourcesUiState(url = savedState["repository-url"] ?: ""))
    private var discoveryLoaded = false
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
    fun consumeAdded() {
        local.update { it.copy(added = null) }
    }

    fun loadDiscovery(force: Boolean = false) {
        if (local.value.discovering || (!force && discoveryLoaded)) return
        local.update { it.copy(discovering = true, discoveryError = null) }
        launchTracked(onComplete = { local.update { it.copy(discovering = false) } }) {
            discover().onSuccess { items ->
                local.update { it.copy(discovered = items) }
            }.onFailure { error ->
                local.update { it.copy(discoveryError = RepositoryFailure.from(error)) }
            }
            discoveryLoaded = true
        }
    }

    fun add(url: String = local.value.url, metadata: RepositorySource? = null) = perform(addingUrl = url) {
        addRepository(url, metadata).onSuccess { source -> local.update { it.copy(added = source) } }
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
