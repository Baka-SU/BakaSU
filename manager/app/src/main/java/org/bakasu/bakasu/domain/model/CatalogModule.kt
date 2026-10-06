package org.bakasu.bakasu.domain.model

data class CatalogAuthor(
    val name: String,
    val link: String,
)

data class ModuleReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long?,
    val downloadCount: Int?,
) {
    fun hasSameIdentity(other: ModuleReleaseAsset): Boolean = name == other.name && downloadUrl == other.downloadUrl
}

data class ModuleRelease(
    val name: String,
    val tagName: String,
    val publishedAt: String,
    val assets: List<ModuleReleaseAsset>,
    val descriptionHTML: String = "",
    val versionCode: Int? = null,
    val changelogUrl: String = "",
)

data class CatalogModule(
    val moduleId: String,
    val moduleName: String,
    val authors: String,
    val authorList: List<CatalogAuthor>,
    val summary: String,
    val metamodule: Boolean,
    val stargazerCount: Int,
    val updatedAt: String,
    val createdAt: String,
    val latestRelease: String,
    val latestReleaseTime: String,
    val latestVersionCode: Int,
    val latestAsset: ModuleRelease?,
    val installed: Boolean,
    val sourceUrl: String,
    val releases: List<ModuleRelease>,
    val repositoryUrl: String,
    val readme: String = "",
    val repositoryName: String = "",
    val readmeUrl: String = "",
) {
    val catalogId: String get() = "$repositoryUrl#$moduleId"
    val pageUrl: String get() = sourceUrl.ifBlank { repositoryUrl }
}

data class ModuleReadme(val content: String, val baseUrl: String)

sealed interface ModuleCatalogFailure {
    data object Offline : ModuleCatalogFailure
    data object NotFound : ModuleCatalogFailure
    data class Network(val message: String) : ModuleCatalogFailure
}

sealed interface ModuleCatalogResult<out T> {
    data class Success<T>(val value: T) : ModuleCatalogResult<T>
    data class Failure(val reason: ModuleCatalogFailure) : ModuleCatalogResult<Nothing>
}
