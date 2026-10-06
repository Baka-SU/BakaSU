package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.domain.usecase.GetBooleanPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.ObserveCatalogModulesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import org.bakasu.bakasu.domain.usecase.ObserveRepositorySourcesUseCase
import org.bakasu.bakasu.domain.usecase.RefreshModuleCatalogUseCase
import org.bakasu.bakasu.domain.usecase.SetBooleanPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.TransliterateTextUseCase

enum class CatalogContent { REPOSITORIES, SOURCE_UNAVAILABLE, LOADING, MODULES, FAILED, SEARCH_EMPTY, SOURCE_EMPTY }

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
        repositoryUrl != null && sources.none { it.url == repositoryUrl } -> CatalogContent.SOURCE_UNAVAILABLE
        repositoryUrl == null && search.isBlank() -> CatalogContent.REPOSITORIES
        modules.isNotEmpty() -> CatalogContent.MODULES
        isRefreshing -> CatalogContent.LOADING
        sources.any { (repositoryUrl == null || it.url == repositoryUrl) && it.url in failures } -> CatalogContent.FAILED
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
    private val repositoryUrl: String? = null,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val sortStarsFirst = MutableStateFlow(
        getBooleanPreference("module_repo_sort_star_first", false),
    )
    private val pullRefreshing = MutableStateFlow(false)

    private val catalogState = combine(
        observeModules(),
        observeRefreshing.sources,
        search,
        sortStarsFirst,
    ) { modules, refreshingSources, query, starsFirst ->
        ModuleRepoUiState(
            modules = modules,
            sortStargazerCountFirst = starsFirst,
            isRefreshing = refreshingSources.any { repositoryUrl == null || it == repositoryUrl },
            search = query,
        )
    }
    val state: StateFlow<ModuleRepoUiState> = combine(
        catalogState,
        observeSources.sources,
        observeSources.failures,
        pullRefreshing,
    ) { catalog, sources, failures, pulling ->
        visibleState(catalog.copy(isPullRefreshing = pulling && catalog.isRefreshing), sources, failures)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        visibleState(
            ModuleRepoUiState(
                modules = observeModules().value,
                sortStargazerCountFirst = sortStarsFirst.value,
                isRefreshing = observeRefreshing.sources.value.any { repositoryUrl == null || it == repositoryUrl },
            ),
            observeSources.sources.value,
            observeSources.failures.value,
        ),
    )
    val uiState: StateFlow<ModuleRepoUiState> = state

    private fun visibleState(catalog: ModuleRepoUiState, sources: List<RepositorySource>, failures: Map<String, RepositoryFailure>): ModuleRepoUiState {
        val sourceUrls = sources.map { it.url }.toSet()
        return catalog.copy(
            modules = catalog.modules.filter { module ->
                module.repositoryUrl in sourceUrls && (repositoryUrl == null || module.repositoryUrl == repositoryUrl) &&
                    (
                        module.moduleId.contains(catalog.search, true) || module.moduleName.contains(catalog.search, true) ||
                            transliterateText(module.moduleName).contains(catalog.search, true)
                        )
            }.sortedWith(
                compareByDescending<CatalogModule> { it.installed }
                    .thenByDescending { if (catalog.sortStargazerCountFirst) it.stargazerCount else 0 },
            ),
            sources = sources,
            repositoryUrl = repositoryUrl,
            failures = failures,
        )
    }

    fun resolveModule(catalogId: String): CatalogModule? = observeModules().value.firstOrNull { module ->
        module.catalogId == catalogId && (repositoryUrl == null || module.repositoryUrl == repositoryUrl) &&
            observeSources.sources.value.any { it.url == module.repositoryUrl }
    }

    fun resolveAsset(catalogId: String, asset: ModuleReleaseAsset): CatalogModule? = resolveModule(catalogId)?.takeIf { module ->
        module.latestAsset?.assets?.any { it.hasSameIdentity(asset) } == true
    }

    fun updateSearch(value: String) {
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
                refreshCatalog(repositoryUrl, force)
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
