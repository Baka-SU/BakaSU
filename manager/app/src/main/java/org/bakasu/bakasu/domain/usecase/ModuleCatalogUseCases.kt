package org.bakasu.bakasu.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import org.bakasu.bakasu.data.module.ModuleCatalogRepository
import org.bakasu.bakasu.data.module.ModuleIconRepository
import org.bakasu.bakasu.data.module.ModuleReleaseRepository
import org.bakasu.bakasu.data.module.registry.KernelSuRegistryRepository
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleReadme
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleUpdateVerification
import org.bakasu.bakasu.domain.model.RepositorySource

class ObserveCatalogModulesUseCase(private val repository: ModuleCatalogRepository) {
    operator fun invoke() = repository.modules
}

class ObserveModuleCatalogRefreshingUseCase(private val repository: ModuleCatalogRepository) {
    val sources get() = repository.refreshingSources
}

class RefreshModuleCatalogUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(sourceUrl: String? = null, force: Boolean = true) = repository.refresh(sourceUrl, force)
}

class GetCatalogModuleUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(moduleId: String, sourceUrl: String, forceDetail: Boolean = false) = repository.get(moduleId, sourceUrl, forceDetail)
}

class IsCatalogModuleCurrentUseCase(private val repository: ModuleCatalogRepository) {
    operator fun invoke(module: CatalogModule): Boolean = repository.isCurrent(module)
}

class GetModuleReadmeUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(module: CatalogModule) = repository.readme(module)
}

class GetModuleReleaseDetailsUseCase(private val repository: ModuleReleaseRepository) {
    operator fun invoke(module: CatalogModule, release: ModuleRelease, retry: Boolean = false) = repository.observe(module, release, retry)

    suspend fun notes(module: CatalogModule, release: ModuleRelease) = repository.observe(module, release).firstOrNull { it.notes != null }?.notes
}

class LoadModuleIconUseCase(private val repository: ModuleIconRepository) {
    fun cached(url: String, origin: String?) = repository.cached(url, origin)
    suspend operator fun invoke(url: String, origin: String?) = repository.load(url, origin)
}

class ResolveModuleUpdateUseCase(private val repository: KernelSuRegistryRepository) {
    suspend operator fun invoke(moduleId: String, updateJsonUrl: String, zipUrl: String, versionCode: Int) = repository.resolveUpdate(moduleId, updateJsonUrl, zipUrl, versionCode)

    fun isCurrent(update: ModuleUpdateVerification.Registry): Boolean = repository.isCurrent(update)
}

class GetModuleUpdateChangelogUseCase(
    private val registry: KernelSuRegistryRepository,
    private val network: NetworkRequestRepository,
) {
    suspend operator fun invoke(url: String): Result<ModuleReadme> = try {
        if (registry.isRegistryDocument(url)) {
            Result.success(registry.changelog(url) ?: error("No changelog"))
        } else {
            network.fetch(url).map { ModuleReadme(it, url) }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}

class ObserveRepositorySourcesUseCase(private val repository: ModuleCatalogRepository) {
    val sources get() = repository.sources
    val failures get() = repository.failures
}

class DiscoverModuleRepositoriesUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke() = repository.discover()
}

class AddModuleRepositoryUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(url: String, metadata: RepositorySource? = null) = repository.add(url, metadata)
}

class RenameModuleRepositoryUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(url: String, name: String) = repository.rename(url, name)
}

class RemoveModuleRepositoryUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(url: String) = repository.remove(url)
}
