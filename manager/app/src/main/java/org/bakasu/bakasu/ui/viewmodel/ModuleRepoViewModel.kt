package org.bakasu.bakasu.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleCatalogResult
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.domain.usecase.GetBooleanPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.GetCatalogModuleUseCase
import org.bakasu.bakasu.domain.usecase.IsCatalogModuleCurrentUseCase
import org.bakasu.bakasu.domain.usecase.ObserveCatalogModulesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import org.bakasu.bakasu.domain.usecase.ObserveRepositorySourcesUseCase
import org.bakasu.bakasu.domain.usecase.RefreshModuleCatalogUseCase
import org.bakasu.bakasu.domain.usecase.SetBooleanPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.TransliterateTextUseCase

enum class CatalogContent(@param:StringRes val resId: Int? = null) {
    LOADING,
    MODULES,
    FAILED,
    SEARCH_EMPTY(R.string.search_no_any_match),
    SOURCE_EMPTY(R.string.repo_source_empty),
}

data class ModuleRepoUiState(
    val modules: List<CatalogModule> = emptyList(),
    val sortStargazerCountFirst: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPullRefreshing: Boolean = false,
    val search: String = "",
    val sources: List<RepositorySource> = emptyList(),
    val repositoryUrl: String? = null,
    val failures: Map<String, RepositoryFailure> = emptyMap(),
) {
    val content: CatalogContent get() = when {
        modules.isNotEmpty() -> CatalogContent.MODULES
        isRefreshing -> CatalogContent.LOADING
        sources.any { it.url == repositoryUrl && it.url in failures } -> CatalogContent.FAILED
        search.isNotBlank() -> CatalogContent.SEARCH_EMPTY
        else -> CatalogContent.SOURCE_EMPTY
    }
}

sealed interface ModuleRepoUiAction {
    data object Refresh : ModuleRepoUiAction
    data object PullRefresh : ModuleRepoUiAction
    data class Search(val query: String) : ModuleRepoUiAction
    data class SetStarsFirst(val enabled: Boolean) : ModuleRepoUiAction
}

class ModuleRepoViewModel(
    private val observeModules: ObserveCatalogModulesUseCase,
    observeRefreshing: ObserveModuleCatalogRefreshingUseCase,
    private val refreshCatalog: RefreshModuleCatalogUseCase,
    getBooleanPreference: GetBooleanPreferenceUseCase,
    private val setBooleanPreference: SetBooleanPreferenceUseCase,
    private val transliterateText: TransliterateTextUseCase,
    private val observeSources: ObserveRepositorySourcesUseCase,
    private val getModule: GetCatalogModuleUseCase,
    private val isCurrent: IsCatalogModuleCurrentUseCase,
    private val savedState: SavedStateHandle,
    repositoryUrl: String? = null,
) : ViewModel() {
    private val search = MutableStateFlow(savedState.get<String>("repository-search") ?: "")
    private val selectedRepository = MutableStateFlow<String?>(savedState["selected-repository"] ?: repositoryUrl)
    private val selectedSources = combine(observeSources.sources, selectedRepository) { sources, selected ->
        val url = sources.firstOrNull { it.url == selected }?.url ?: sources.firstOrNull()?.url
        sources to url
    }

    private fun activeRepository(): String? = observeSources.sources.value.let { sources ->
        sources.firstOrNull { it.url == selectedRepository.value }?.url ?: sources.firstOrNull()?.url
    }

    fun selectRepository(url: String) {
        if (observeSources.sources.value.none { it.url == url }) return
        val changed = activeRepository() != url
        savedState["selected-repository"] = url
        selectedRepository.value = url
        if (changed) updateSearch("")
    }
    private val sortStarsFirst = MutableStateFlow(
        getBooleanPreference("module_repo_sort_star_first", false),
    )
    private val pullRefreshing = MutableStateFlow(false)

    private val catalogState = combine(
        observeModules(),
        selectedSources,
        search,
        sortStarsFirst,
    ) { modules, selection, query, starsFirst ->
        visibleState(
            ModuleRepoUiState(
                modules = modules,
                sortStargazerCountFirst = starsFirst,
                search = query,
                repositoryUrl = selection.second,
            ),
            selection.first,
        )
    }.flowOn(Dispatchers.Default)
    val state: StateFlow<ModuleRepoUiState> = combine(
        catalogState,
        observeRefreshing.sources,
        observeSources.failures,
        pullRefreshing,
    ) { catalog, refreshingSources, failures, pulling ->
        val refreshing = refreshingSources.any { it == catalog.repositoryUrl }
        catalog.copy(isRefreshing = refreshing, isPullRefreshing = pulling && refreshing, failures = failures)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        ModuleRepoUiState(
            sortStargazerCountFirst = sortStarsFirst.value,
            search = search.value,
            isRefreshing = true,
            sources = observeSources.sources.value,
            repositoryUrl = activeRepository(),
            failures = observeSources.failures.value,
        ),
    )
    val uiState: StateFlow<ModuleRepoUiState> = state

    private fun visibleState(catalog: ModuleRepoUiState, sources: List<RepositorySource>): ModuleRepoUiState = catalog.copy(
        modules = catalog.modules.filter { module ->
            module.repositoryUrl == catalog.repositoryUrl &&
                (
                    module.moduleId.contains(catalog.search, true) || module.moduleName.contains(catalog.search, true) ||
                        transliterateText(module.moduleName).contains(catalog.search, true)
                    )
        }.sortedByDescending { if (catalog.sortStargazerCountFirst) it.stargazerCount else 0 },
        sources = sources,
    )

    fun resolveModule(catalogId: String): CatalogModule? = observeModules().value.firstOrNull { module ->
        module.catalogId == catalogId && module.repositoryUrl == activeRepository() &&
            observeSources.sources.value.any { it.url == module.repositoryUrl }
    }

    fun resolveAsset(catalogId: String, asset: ModuleReleaseAsset): CatalogModule? = resolveModule(catalogId)?.takeIf { module ->
        isCurrent(module) && module.latestAsset?.assets?.any { it.hasSameIdentity(asset) && it.hasSameVerification(asset) } == true
    }

    suspend fun resolveModuleForInstall(catalogId: String): CatalogModule? {
        val module = resolveModule(catalogId) ?: return null
        val loaded = (getModule(module.moduleId, module.repositoryUrl) as? ModuleCatalogResult.Success)?.value ?: return null
        return resolveModule(loaded.catalogId)?.takeIf { it.detailLoaded && isCurrent(it) }
    }

    fun updateSearch(value: String) {
        savedState["repository-search"] = value
        search.value = value
    }

    fun setSortStargazerCountFirst(enabled: Boolean) {
        setBooleanPreference("module_repo_sort_star_first", enabled)
        sortStarsFirst.value = enabled
    }

    fun refresh(force: Boolean = true, fromPull: Boolean = false) {
        viewModelScope.launch {
            if (fromPull && pullRefreshing.value) return@launch
            if (fromPull) pullRefreshing.value = true
            try {
                activeRepository()?.let { refreshCatalog(it, force) }
            } finally {
                if (fromPull) pullRefreshing.value = false
            }
        }
    }

    fun dispatch(action: ModuleRepoUiAction) {
        when (action) {
            ModuleRepoUiAction.Refresh -> refresh()
            ModuleRepoUiAction.PullRefresh -> refresh(fromPull = true)
            is ModuleRepoUiAction.Search -> updateSearch(action.query)
            is ModuleRepoUiAction.SetStarsFirst -> setSortStargazerCountFirst(action.enabled)
        }
    }
}
