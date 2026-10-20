package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleCatalogFailure
import org.bakasu.bakasu.domain.model.ModuleCatalogResult
import org.bakasu.bakasu.domain.model.ModuleReadme
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.ModuleReleaseDetails
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.domain.usecase.GetCatalogModuleUseCase
import org.bakasu.bakasu.domain.usecase.GetModuleReadmeUseCase
import org.bakasu.bakasu.domain.usecase.GetModuleReleaseDetailsUseCase
import org.bakasu.bakasu.domain.usecase.IsCatalogModuleCurrentUseCase
import org.bakasu.bakasu.domain.usecase.ObserveCatalogModulesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveRepositorySourcesUseCase
import org.bakasu.bakasu.domain.usecase.RefreshModuleCatalogUseCase

data class ModuleDetailUiState(
    val module: CatalogModule? = null,
    val loading: Boolean = true,
    val error: ModuleCatalogFailure? = null,
    val documents: Map<String, ModuleDocumentState> = emptyMap(),
    val releaseDetails: Map<ModuleRelease, ReleaseDetailsState> = emptyMap(),
    val repositoryFailure: RepositoryFailure? = null,
)

data class ReleaseDetailsState(
    val loading: Boolean = true,
    val details: ModuleReleaseDetails? = null,
    internal val requestToken: Any = Any(),
)

data class ModuleDocumentState(
    val document: ModuleReadme? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

sealed interface ModuleDetailUiAction {
    data object Retry : ModuleDetailUiAction
}

class ModuleDetailViewModel(
    private val moduleId: String,
    private val repositoryUrl: String,
    private val getModule: GetCatalogModuleUseCase,
    private val getReadme: GetModuleReadmeUseCase,
    private val getReleaseDetails: GetModuleReleaseDetailsUseCase,
    private val observeModules: ObserveCatalogModulesUseCase,
    private val observeSources: ObserveRepositorySourcesUseCase,
    private val isCurrent: IsCatalogModuleCurrentUseCase,
    private val refreshCatalog: RefreshModuleCatalogUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        observeModules().value.firstOrNull { module ->
            module.moduleId == moduleId && module.repositoryUrl == repositoryUrl &&
                observeSources.sources.value.any { it.url == repositoryUrl }
        }?.let { ModuleDetailUiState(module = it, loading = false, repositoryFailure = observeSources.failures.value[repositoryUrl]) } ?: ModuleDetailUiState(),
    )

    val state: StateFlow<ModuleDetailUiState> = mutableState.asStateFlow()
    private var detailJob: Job? = null

    fun resolveAsset(asset: ModuleReleaseAsset): CatalogModule? {
        if (observeSources.sources.value.none { it.url == repositoryUrl }) return null
        return observeModules().value.firstOrNull { module ->
            module.moduleId == moduleId && module.repositoryUrl == repositoryUrl &&
                isCurrent(module) && module.releases.any { release -> release.assets.any { it.hasSameIdentity(asset) && it.hasSameVerification(asset) } }
        }
    }

    init {
        if (mutableState.value.module?.let { isCurrent(it) } != true) load()
        viewModelScope.launch {
            combine(observeModules(), observeSources.sources, observeSources.failures) { modules, sources, failures ->
                val configured = sources.any { it.url == repositoryUrl }
                Triple(configured, modules.firstOrNull { it.moduleId == moduleId && it.repositoryUrl == repositoryUrl }, failures[repositoryUrl])
            }.collect { (configured, module, failure) ->
                if (!configured || (module == null && mutableState.value.module != null)) {
                    mutableState.value = ModuleDetailUiState(loading = false, error = ModuleCatalogFailure.NotFound)
                } else if (module != null) {
                    val previous = mutableState.value.module
                    val changedBuild = previous?.registryMetadata?.buildId != module.registryMetadata?.buildId
                    mutableState.update {
                        it.copy(
                            module = module,
                            loading = if (module.detailLoaded) false else it.loading,
                            error = if (module.detailLoaded) null else it.error,
                            documents = if (changedBuild) emptyMap() else it.documents,
                            releaseDetails = if (previous != module) emptyMap() else it.releaseDetails,
                            repositoryFailure = failure,
                        )
                    }
                    if (previous?.detailLoaded == true && !module.detailLoaded) load()
                }
            }
        }
    }

    fun dispatch(action: ModuleDetailUiAction) {
        when (action) {
            ModuleDetailUiAction.Retry -> load(forceDetail = true)
        }
    }

    private fun load(forceDetail: Boolean = false) {
        if (detailJob?.isActive == true && !forceDetail) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            if (forceDetail) refreshCatalog(repositoryUrl)
            when (val result = getModule(moduleId, repositoryUrl, forceDetail)) {
                is ModuleCatalogResult.Success -> mutableState.update {
                    if (observeSources.sources.value.none { source -> source.url == repositoryUrl }) {
                        return@update ModuleDetailUiState(loading = false, error = ModuleCatalogFailure.NotFound)
                    }
                    val current = observeModules().value.firstOrNull { module -> module.catalogId == result.value.catalogId }
                        ?: return@update ModuleDetailUiState(loading = false, error = ModuleCatalogFailure.NotFound)
                    if (!current.detailLoaded || current.registryMetadata?.buildId != result.value.registryMetadata?.buildId) return@update it
                    it.copy(module = current, loading = false, error = null)
                }

                is ModuleCatalogResult.Failure -> {
                    mutableState.update {
                        if (result.reason == ModuleCatalogFailure.NotFound) {
                            ModuleDetailUiState(loading = false, error = result.reason)
                        } else {
                            it.copy(loading = false, error = result.reason)
                        }
                    }
                }
            }
        }
    }

    fun loadReadme(retry: Boolean = false) {
        val module = mutableState.value.module?.takeIf { it.detailLoaded } ?: return
        if (!isCurrent(module)) {
            load(forceDetail = true)
            return
        }
        val url = module.readmeUrl.ifBlank { module.sourceUrl }
        loadDocument(url, retry) { getReadme(module, retry) }
    }

    fun loadReleaseDetails(release: ModuleRelease, retry: Boolean = false) {
        val module = mutableState.value.module ?: return
        if (module.registryMetadata != null || release !in module.releases || !isCurrent(module)) return
        val existing = mutableState.value.releaseDetails[release]
        if (existing?.loading == true || (!retry && existing != null)) return
        val pending = ReleaseDetailsState()
        mutableState.update { it.copy(releaseDetails = it.releaseDetails + (release to pending)) }
        fun publish(transform: (ReleaseDetailsState) -> ReleaseDetailsState) {
            mutableState.update {
                val current = it.releaseDetails[release] ?: return@update it
                if (it.module != module || current.requestToken !== pending.requestToken ||
                    observeSources.sources.value.none { source -> source.url == repositoryUrl }
                ) {
                    return@update it
                }
                it.copy(releaseDetails = it.releaseDetails + (release to transform(current)))
            }
        }
        viewModelScope.launch {
            try {
                getReleaseDetails(module, release, retry).collect { details -> publish { it.copy(details = details) } }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                publish { it.copy(details = it.details ?: ModuleReleaseDetails(notesFailed = release.changelogUrl.isNotBlank())) }
            } finally {
                publish { it.copy(loading = false) }
            }
        }
    }

    private fun loadDocument(url: String, retry: Boolean, fetch: suspend () -> Result<ModuleReadme?>) {
        val document = mutableState.value.documents[url]
        if (document?.loading == true || (!retry && document != null)) return
        val pending = ModuleDocumentState()
        mutableState.update { it.copy(documents = it.documents + (url to pending)) }
        viewModelScope.launch {
            val result = fetch()
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            mutableState.update {
                if (it.documents[url] !== pending) return@update it
                it.copy(
                    documents = it.documents + (
                        url to ModuleDocumentState(
                            document = result.getOrNull(),
                            loading = false,
                            failed = result.isFailure,
                        )
                        ),
                )
            }
        }
    }
}
