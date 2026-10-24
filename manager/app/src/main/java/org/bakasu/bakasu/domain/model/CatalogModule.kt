package org.bakasu.bakasu.domain.model

import androidx.annotation.StringRes
import org.bakasu.bakasu.R

data class CatalogAuthor(
    val name: String,
    val link: String,
)

data class ModuleReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long?,
    val downloadCount: Int?,
    val expected: ExpectedFile? = null,
    val verificationRequired: Boolean = false,
) {
    fun hasSameIdentity(other: ModuleReleaseAsset): Boolean = name == other.name && downloadUrl == other.downloadUrl

    fun hasSameVerification(other: ModuleReleaseAsset): Boolean = verificationRequired == other.verificationRequired &&
        if (expected == null) other.expected == null else expected.sameAs(other.expected)
}

data class ModuleRelease(
    val name: String,
    val tagName: String,
    val publishedAt: String,
    val assets: List<ModuleReleaseAsset>,
    val descriptionHTML: String = "",
    val versionCode: Int? = null,
    val changelogUrl: String = "",
    val tagCommit: String = "",
    val releaseUrl: String = "",
    val attestationUrl: String? = null,
    val immutable: Boolean = false,
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
    val sourceUrl: String,
    val releases: List<ModuleRelease>,
    val repositoryUrl: String,
    val repositoryName: String = "",
    val readmeUrl: String = "",
    val detailLoaded: Boolean = true,
    val registryMetadata: RegistryModuleMetadata? = null,
    val iconUrl: String? = null,
    val isScript: Boolean = false,
) {
    val catalogId: String get() = "$repositoryUrl#$moduleId"
    val pageUrl: String get() = sourceUrl.ifBlank { repositoryUrl }
}

data class RegistryModuleMetadata(
    val buildId: String,
    val tags: List<String>,
    val iconUrl: String?,
    val stars: Int?,
    val starsRepository: String?,
    val sourceCommit: String = "",
    val sourceFullName: String = "",
    val license: String? = null,
    val homepage: String? = null,
    val support: String? = null,
    val fromCache: Boolean = false,
    val healthStale: Boolean = false,
)

enum class DocumentFormat { MARKDOWN, HTML }

data class ModuleReadme(
    val content: String,
    val baseUrl: String,
    val format: DocumentFormat = DocumentFormat.MARKDOWN,
    val policy: DocumentPolicy = DocumentPolicy.PublicHttps(),
) : java.io.Serializable

/** Presentation data only. The selected repository still owns the release assets and verification. */
data class ModuleReleaseDetails(
    val title: String? = null,
    val publishedAt: String? = null,
    val releaseUrl: String? = null,
    val notes: ModuleReadme? = null,
    val notesFailed: Boolean = false,
)

sealed interface ModuleCatalogFailure {
    @get:StringRes
    val resId: Int

    data object Offline : ModuleCatalogFailure {
        override val resId = R.string.network_offline
    }
    data object NotFound : ModuleCatalogFailure {
        override val resId = R.string.repo_module_unavailable
    }
    data class Network(val message: String) : ModuleCatalogFailure {
        override val resId = R.string.repo_error_network
    }
}

sealed interface ModuleCatalogResult<out T> {
    data class Success<T>(val value: T) : ModuleCatalogResult<T>
    data class Failure(val reason: ModuleCatalogFailure) : ModuleCatalogResult<Nothing>
}
