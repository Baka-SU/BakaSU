package org.bakasu.bakasu.data.module.registry

import org.bakasu.bakasu.domain.model.CatalogAuthor
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ExpectedFile
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.RegistryModuleMetadata
import org.bakasu.bakasu.domain.model.RepositorySource

object RegistryCatalogMapper {
    fun summary(item: ApiModuleSummary, catalog: RegistryCatalog, source: RepositorySource): CatalogModule {
        val authors = item.authors.filter { it.name.isNotBlank() }.map { CatalogAuthor(it.name.trim(), it.url.orEmpty()) }
        return CatalogModule(
            moduleId = item.id,
            moduleName = item.name.trim().ifBlank { item.id },
            authors = authors.joinToString(", ") { it.name },
            authorList = authors,
            summary = item.summary.trim(),
            metamodule = item.metamodule,
            isScript = item.kind == RegistryKind.SCRIPT,
            stargazerCount = item.stars ?: 0,
            updatedAt = item.updatedAt,
            createdAt = "",
            latestRelease = item.stable.version,
            latestReleaseTime = item.stable.publishedAt,
            latestVersionCode = item.stable.versionCode,
            latestAsset = null,
            sourceUrl = "",
            releases = emptyList(),
            repositoryUrl = source.url,
            repositoryName = source.name,
            detailLoaded = false,
            registryMetadata = RegistryModuleMetadata(
                buildId = catalog.buildId,
                tags = item.tags,
                iconUrl = item.icon?.let { RegistryUrls.resolve(source.url, it) },
                stars = item.stars,
                starsRepository = item.starsRepository,
                fromCache = catalog.fromCache,
            ),
        )
    }

    fun detail(load: RegistryClient.DetailLoad, source: RepositorySource): CatalogModule {
        val detail = load.detail
        val item = load.catalog.find(detail.id)!!.copy(
            name = detail.name, summary = detail.summary, authors = detail.authors,
            tags = detail.tags, metamodule = detail.metamodule, kind = detail.kind, icon = detail.icon, stable = detail.stable,
            stars = detail.stars, starsRepository = detail.starsRepository, updatedAt = detail.updatedAt,
        )
        val summary = summary(item, load.catalog, source)
        val releases = detail.releases.filterNot { it.prerelease }.map(::release)
        return summary.copy(
            detailLoaded = true,
            sourceUrl = detail.source.url,
            readmeUrl = detail.readme?.let { RegistryUrls.resolve(source.url, it) }.orEmpty(),
            releases = releases,
            latestAsset = releases.firstOrNull { it.versionCode == detail.stable.versionCode },
            registryMetadata = summary.registryMetadata!!.copy(
                sourceCommit = detail.source.commit,
                sourceFullName = detail.source.fullName,
                license = detail.license,
                homepage = detail.homepage,
                support = detail.support,
                fromCache = load.fromCache || load.catalog.fromCache,
                healthStale = detail.health?.status == "stale",
            ),
        )
    }

    fun release(item: ApiRelease): ModuleRelease = ModuleRelease(
        name = item.name?.takeIf { it.isNotBlank() } ?: item.tag,
        tagName = item.tag,
        publishedAt = item.publishedAt,
        versionCode = item.versionCode,
        descriptionHTML = item.changelogHtml.orEmpty(),
        tagCommit = item.tagCommit,
        releaseUrl = item.releaseUrl,
        attestationUrl = item.attestationUrl,
        immutable = item.immutable,
        assets = listOf(
            ModuleReleaseAsset(
                name = item.zip.name.substringAfterLast('/').ifBlank { "module-${item.versionCode}.zip" },
                downloadUrl = item.zip.url,
                size = item.zip.size,
                downloadCount = null,
                expected = ExpectedFile(item.zip.size, item.zip.sha256),
                verificationRequired = true,
            ),
        ),
    )
}
