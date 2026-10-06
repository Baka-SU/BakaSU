package org.bakasu.bakasu.data.module

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.bakasu.bakasu.domain.model.CatalogAuthor
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryException
import org.bakasu.bakasu.domain.model.RepositorySource

object MmrlRepositoryParser {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun canonicalUrl(input: String): String {
        val url = input.trim().toHttpUrlOrNull() ?: fail(RepositoryError.INVALID_URL)
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) fail(RepositoryError.INVALID_URL)
        var path = url.encodedPath.removeSuffix("/")
        if (path.endsWith("/json/modules.json")) path = path.removeSuffix("/json/modules.json")
        return url.newBuilder().encodedPath("$path/").query(null).fragment(null).build().toString()
    }

    fun resourceUrl(base: String, value: String): String = if (value.isBlank()) {
        ""
    } else {
        base.toHttpUrlOrNull()?.resolve(value.trim())
            ?.takeIf { it.scheme == "https" && it.username.isEmpty() && it.password.isEmpty() }?.toString().orEmpty()
    }

    fun parse(url: String, body: String): RepositoryFeed {
        val base = canonicalUrl(url)
        val feed = decode<RepositoryFeedDto>(body)
        val name = feed.name?.takeIf(String::isNotBlank) ?: fail(RepositoryError.INVALID_FEED)
        if (feed.metadata?.version?.let { it !in 0..1 } == true) fail(RepositoryError.UNSUPPORTED_FORMAT)
        val source = RepositorySource(
            base,
            name,
            feed.id.orEmpty(),
            feed.description.orEmpty(),
        )
        val modules = (feed.modules ?: fail(RepositoryError.INVALID_FEED)).map {
            module(source, it ?: fail(RepositoryError.INVALID_FEED))
        }
        if (modules.map { it.moduleId }.distinct().size != modules.size) fail(RepositoryError.INVALID_FEED)
        return RepositoryFeed(source, modules)
    }

    fun discovery(body: String): List<RepositorySource> = decode<List<RepositoryDiscoveryDto?>>(body)
        .mapNotNull { item ->
            if (item == null) fail(RepositoryError.INVALID_FEED)
            val url = runCatching { canonicalUrl(item.url.orEmpty()) }.getOrNull() ?: return@mapNotNull null
            val name = item.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            RepositorySource(
                url,
                name,
                item.id.orEmpty(),
                item.description.orEmpty(),
            )
        }.distinctBy { it.url }

    private fun module(source: RepositorySource, item: RepositoryModuleDto): CatalogModule {
        val id = item.id.orEmpty()
        val name = item.name.orEmpty()
        if (!id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*")) || name.isBlank()) fail(RepositoryError.INVALID_FEED)
        val versionCode = item.versionCode ?: fail(RepositoryError.INVALID_FEED)
        val releases = (item.versions ?: fail(RepositoryError.INVALID_FEED)).map { entry ->
            val release = entry ?: fail(RepositoryError.INVALID_FEED)
            val releaseCode = release.versionCode ?: fail(RepositoryError.INVALID_FEED)
            val zip = resourceUrl(source.url, release.zipUrl.orEmpty()).takeIf(String::isNotBlank) ?: fail(RepositoryError.INVALID_FEED)
            val fileName = zip.toHttpUrlOrNull()!!.pathSegments.last()
                .map { if (it == '/' || it == '\\' || it.isISOControl()) '_' else it }
                .joinToString("")
                .takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "$id-$releaseCode.zip"
            ModuleRelease(
                name = release.version.orEmpty(),
                tagName = release.version.orEmpty(),
                publishedAt = timestamp(release.timestamp),
                assets = listOf(
                    ModuleReleaseAsset(
                        name = fileName,
                        downloadUrl = zip,
                        size = release.size?.takeIf { it >= 0 },
                        downloadCount = null,
                    ),
                ),
                versionCode = releaseCode,
                changelogUrl = resourceUrl(source.url, release.changelog.orEmpty()),
            )
        }.distinctBy { it.versionCode to it.assets.single().downloadUrl }.sortedByDescending { it.versionCode }
        val author = item.author.orEmpty()
        val latest = releases.firstOrNull()
        val latestTime = latest?.publishedAt.orEmpty().ifBlank { timestamp(item.timestamp) }
        return CatalogModule(
            moduleId = id,
            moduleName = name,
            authors = author,
            authorList = if (author.isBlank()) emptyList() else listOf(CatalogAuthor(author, "")),
            summary = item.description.orEmpty(),
            metamodule = false,
            stargazerCount = item.stars ?: 0,
            updatedAt = latestTime,
            createdAt = timestamp(item.track?.added).ifBlank { timestamp(item.added) },
            latestRelease = latest?.name ?: item.version.orEmpty(),
            latestReleaseTime = latestTime,
            latestVersionCode = latest?.versionCode ?: versionCode,
            latestAsset = latest,
            installed = false,
            sourceUrl = resourceUrl(source.url, item.track?.source.orEmpty()),
            releases = releases,
            repositoryUrl = source.url,
            repositoryName = source.name,
            readmeUrl = resourceUrl(source.url, item.readme.orEmpty()),
        )
    }

    private inline fun <reified T> decode(body: String): T = try {
        gson.fromJson(body, object : TypeToken<T>() {}) ?: fail(RepositoryError.INVALID_FEED)
    } catch (_: JsonParseException) {
        fail(RepositoryError.INVALID_FEED)
    }

    private fun timestamp(value: Double?): String {
        val seconds = value?.takeIf { it.isFinite() && it > 0 } ?: return ""
        return runCatching { Instant.ofEpochSecond(seconds.toLong()).toString() }.getOrDefault("")
    }

    private fun fail(reason: RepositoryError): Nothing = throw RepositoryException(reason)
}
