package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.module.ModuleCatalogRepository
import org.bakasu.bakasu.domain.model.CatalogModule
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
    suspend operator fun invoke(moduleId: String, sourceUrl: String) = repository.get(moduleId, sourceUrl)
}

class GetModuleReadmeUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(module: CatalogModule) = repository.readme(module)
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
