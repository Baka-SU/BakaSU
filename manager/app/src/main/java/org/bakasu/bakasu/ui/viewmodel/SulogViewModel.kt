package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.SulogEntry
import org.bakasu.bakasu.domain.model.SulogEventFilter
import org.bakasu.bakasu.domain.model.SulogFile
import org.bakasu.bakasu.domain.model.defaultSulogEventFilters
import org.bakasu.bakasu.domain.model.filterSulogEntries
import org.bakasu.bakasu.domain.usecase.CleanSulogUseCase
import org.bakasu.bakasu.domain.usecase.EnableSulogUseCase
import org.bakasu.bakasu.domain.usecase.GetStringSetPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.ObserveSulogStateUseCase
import org.bakasu.bakasu.domain.usecase.RefreshSulogUseCase
import org.bakasu.bakasu.domain.usecase.SetStringSetPreferenceUseCase

data class SulogScreenState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isEnabling: Boolean = false,
    val sulogStatus: String = "",
    val isSulogEnabled: Boolean = false,
    val searchText: String = "",
    val selectedFilters: Set<SulogEventFilter> = emptySet(),
    val files: List<SulogFile> = emptyList(),
    val selectedFilePath: String? = null,
    val entries: List<SulogEntry> = emptyList(),
    val visibleEntries: List<SulogEntry> = emptyList(),
    val errorMessage: String? = null,
)

data class SulogActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onEnableSulog: () -> Unit,
    val onCleanFile: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onToggleFilter: (SulogEventFilter) -> Unit,
    val onSelectFile: (String) -> Unit,
)

data class SulogFileSelector(
    val items: List<String>,
    val selectedIndex: Int,
)

typealias SulogUiState = SulogScreenState

enum class SulogLoading {
    NONE,
    PAGE,
    REFRESH,
    OPERATION,
}

sealed interface SulogUiAction {
    data object LoadInitialData : SulogUiAction
    data object LoadPage : SulogUiAction
    data object Refresh : SulogUiAction
    data object RefreshLatest : SulogUiAction
    data object Enable : SulogUiAction
    data object CleanFile : SulogUiAction
    data class CleanPath(val path: String) : SulogUiAction
    data class Search(val query: String) : SulogUiAction
    data class ToggleFilter(val filter: SulogEventFilter) : SulogUiAction
    data class SelectFile(val path: String) : SulogUiAction
}

sealed interface SulogUiEvent {
    data class Error(val message: String) : SulogUiEvent
    data object Enabled : SulogUiEvent
}

class SulogViewModel(
    observeState: ObserveSulogStateUseCase,
    private val refreshSulog: RefreshSulogUseCase,
    private val setSulogEnabled: EnableSulogUseCase,
    private val cleanSulog: CleanSulogUseCase,
    getStringSetPreference: GetStringSetPreferenceUseCase,
    private val setStringSetPreference: SetStringSetPreferenceUseCase,
) : ViewModel() {
    private val sourceState = observeState()
    private val enabling = MutableStateFlow(false)
    private val search = MutableStateFlow("")
    private val filters = MutableStateFlow(
        getStringSetPreference(PREF_SULOG_FILTERS)
            .mapNotNull { raw -> SulogEventFilter.entries.firstOrNull { it.name == raw } }
            .toSet()
            .ifEmpty(::defaultSulogEventFilters),
    )
    private var refreshJob: Job? = null
    private var enableJob: Job? = null
    private var loadingJob: Job? = null
    private val mutableLoading = MutableStateFlow(SulogLoading.NONE)

    // Wear observes the request directly so a new page never starts with cached refresh flags.
    val loading: StateFlow<SulogLoading> = mutableLoading.asStateFlow()
    private val mutableEvents = MutableSharedFlow<SulogUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SulogUiEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<SulogUiState> = combine(
        sourceState,
        search,
        filters,
        enabling,
    ) { source, query, selectedFilters, isEnabling ->
        SulogUiState(
            isLoading = source.isLoading,
            isRefreshing = source.isRefreshing,
            isEnabling = isEnabling,
            sulogStatus = source.status,
            isSulogEnabled = source.enabled,
            searchText = query,
            selectedFilters = selectedFilters,
            files = source.files,
            selectedFilePath = source.selectedFilePath,
            entries = source.entries,
            visibleEntries = filterSulogEntries(source.entries, query, selectedFilters),
            errorMessage = source.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SulogUiState(selectedFilters = filters.value),
    )
    val uiState: StateFlow<SulogUiState> = state

    fun dispatch(action: SulogUiAction) {
        when (action) {
            SulogUiAction.LoadInitialData -> if (sourceState.value.isLoading && refreshJob?.isActive != true) refresh(null, SulogLoading.PAGE)

            SulogUiAction.LoadPage -> refresh(null, SulogLoading.PAGE)

            SulogUiAction.Refresh -> refresh(state.value.selectedFilePath)

            SulogUiAction.RefreshLatest -> refresh(null)

            SulogUiAction.Enable -> enable()

            SulogUiAction.CleanFile -> sourceState.value.selectedFilePath?.let(::clean)

            is SulogUiAction.CleanPath -> clean(action.path)

            is SulogUiAction.Search -> search.value = action.query

            is SulogUiAction.ToggleFilter -> {
                filters.value = filters.value.toMutableSet().apply {
                    if (!add(action.filter)) remove(action.filter)
                }
                setStringSetPreference(PREF_SULOG_FILTERS, filters.value.map { it.name }.toSet())
            }

            is SulogUiAction.SelectFile -> {
                refreshJob?.cancel()
                refreshJob = load(SulogLoading.OPERATION) { reportFailure(refreshSulog.selectFile(action.path)) }
            }
        }
    }

    /** The visible page owns this collector; leaving it cancels any pending read. */
    suspend fun observeLive() {
        combine(sourceState, loading) { source, loading ->
            source.enabled && !source.isLoading && loading == SulogLoading.NONE
        }.distinctUntilChanged().collectLatest { idleAndEnabled ->
            if (idleAndEnabled) {
                while (currentCoroutineContext().isActive) {
                    refreshSulog.silently()
                    delay(1_000)
                }
            }
        }
    }

    private fun clean(path: String) {
        refreshJob?.cancel()
        load(SulogLoading.OPERATION) {
            val result = cleanSulog(path)
            currentCoroutineContext().ensureActive()
            result.exceptionOrNull()?.let { mutableEvents.emit(SulogUiEvent.Error(it.message.orEmpty())) }
        }
    }

    private fun enable() {
        if (enableJob?.isActive == true) return
        enableJob = load(SulogLoading.OPERATION) {
            enabling.value = true
            try {
                val result = setSulogEnabled(true)
                currentCoroutineContext().ensureActive()
                result.exceptionOrNull()?.let {
                    mutableEvents.emit(SulogUiEvent.Error(it.message.orEmpty()))
                } ?: run {
                    mutableEvents.emit(SulogUiEvent.Enabled)
                    refresh(state.value.selectedFilePath, SulogLoading.OPERATION)
                    refreshJob?.join()
                }
            } finally {
                enabling.value = false
            }
        }
    }

    private fun refresh(preferredFilePath: String?, kind: SulogLoading = SulogLoading.REFRESH) {
        refreshJob?.cancel()
        refreshJob = load(kind) { read(preferredFilePath) }
    }

    private suspend fun read(preferredFilePath: String?) {
        reportFailure(refreshSulog(preferredFilePath))
    }

    private suspend fun reportFailure(result: Result<Unit>) {
        currentCoroutineContext().ensureActive()
        result.exceptionOrNull()?.let {
            mutableEvents.emit(SulogUiEvent.Error(it.message.orEmpty()))
        }
    }

    private fun load(kind: SulogLoading, block: suspend () -> Unit): Job {
        mutableLoading.value = kind
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) { block() }
        loadingJob = job
        job.invokeOnCompletion {
            if (loadingJob === job) mutableLoading.value = SulogLoading.NONE
        }
        job.start()
        return job
    }

    private companion object {
        const val PREF_SULOG_FILTERS = "sulog_filters"
    }
}
