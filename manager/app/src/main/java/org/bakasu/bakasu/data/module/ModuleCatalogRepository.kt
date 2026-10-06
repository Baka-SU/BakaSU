package org.bakasu.bakasu.data.module

import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.domain.model.CatalogAuthor
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleCatalogFailure
import org.bakasu.bakasu.domain.model.ModuleCatalogResult
import org.bakasu.bakasu.domain.model.ModuleReadme
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryException
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.domain.model.RepositorySource
import org.json.JSONArray
import org.json.JSONObject

data class RepositoryFeed(val source: RepositorySource, val modules: List<CatalogModule>, val error: RepositoryFailure? = null)

class ModuleCatalogRepository(
    private val networkStatusRepository: NetworkStatusRepository,
    private val networkRequestRepository: NetworkRequestRepository,
    private val sourceStore: RepositorySourceStore,
) {
    private data class SourceRequest(val source: RepositorySource, val generation: Long, val cached: List<CatalogModule>)

    private val refreshMutexes = mutableMapOf<String, Mutex>()
    private val stateMutex = Mutex()
    private val mutableModules = MutableStateFlow<List<CatalogModule>>(emptyList())
    private val mutableRefreshingSources = MutableStateFlow<Set<String>>(emptySet())
    private val mutableSources = MutableStateFlow(sourceStore.load())
    private val mutableFailures = MutableStateFlow<Map<String, RepositoryFailure>>(emptyMap())
    private val cachedModules = mutableMapOf<String, List<CatalogModule>>()
    private val sourceGenerations = mutableMapOf<String, Long>()

    val modules: StateFlow<List<CatalogModule>> = mutableModules.asStateFlow()
    val refreshingSources: StateFlow<Set<String>> = mutableRefreshingSources.asStateFlow()
    val sources: StateFlow<List<RepositorySource>> = mutableSources.asStateFlow()
    val failures: StateFlow<Map<String, RepositoryFailure>> = mutableFailures.asStateFlow()

    suspend fun refresh(sourceUrl: String? = null, force: Boolean = true): ModuleCatalogResult<List<CatalogModule>> {
        val targets = stateMutex.withLock {
            val sources = mutableSources.value.filter {
                (sourceUrl == null || it.url == sourceUrl) &&
                    (force || (it.url !in cachedModules && it.url !in mutableFailures.value))
            }
            if (sources.isEmpty()) {
                publishModules()
                return ModuleCatalogResult.Success(mutableModules.value)
            }
            if (!networkStatusRepository.isAvailable()) {
                mutableFailures.value = mutableFailures.value + sources.associate { it.url to RepositoryFailure(RepositoryError.OFFLINE) }
                return ModuleCatalogResult.Failure(ModuleCatalogFailure.Offline)
            }
            sources.map { it.url to refreshMutexes.getOrPut(it.url) { Mutex() } }
        }
        val results = coroutineScope {
            targets.map { (url, mutex) ->
                async(Dispatchers.IO) {
                    mutex.withLock refreshSource@{
                        try {
                            val request = stateMutex.withLock {
                                val source = mutableSources.value.firstOrNull { it.url == url } ?: return@refreshSource false
                                if (!force && (url in cachedModules || url in mutableFailures.value)) {
                                    return@refreshSource url !in mutableFailures.value
                                }
                                mutableRefreshingSources.value = mutableRefreshingSources.value + url
                                SourceRequest(source, sourceGenerations[url] ?: 0, cachedModules[url].orEmpty())
                            }
                            val result = attempt { loadSource(request.source, request.cached) }
                            stateMutex.withLock commit@{
                                val current = mutableSources.value.firstOrNull { it.url == url } ?: return@commit false
                                if ((sourceGenerations[url] ?: 0) != request.generation) return@commit false
                                val feed = result.getOrElse { error ->
                                    mutableFailures.value = mutableFailures.value + (url to RepositoryFailure.from(error))
                                    return@commit false
                                }
                                val updated = mutableSources.value.map {
                                    if (it.url == url) feed.source.copy(customName = current.customName) else it
                                }
                                if (attempt { sourceStore.save(updated) }.isFailure) {
                                    mutableFailures.value = mutableFailures.value + (url to RepositoryFailure(RepositoryError.STORAGE))
                                    return@commit false
                                }
                                cachedModules[url] = feed.modules
                                mutableSources.value = updated
                                mutableFailures.value = feed.error?.let { mutableFailures.value + (url to it) } ?: (mutableFailures.value - url)
                                publishModules()
                                feed.error == null
                            }
                        } finally {
                            withContext(NonCancellable) {
                                stateMutex.withLock { mutableRefreshingSources.value = mutableRefreshingSources.value - url }
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        return if (results.all { it }) {
            ModuleCatalogResult.Success(mutableModules.value)
        } else {
            ModuleCatalogResult.Failure(ModuleCatalogFailure.Network(""))
        }
    }

    suspend fun get(moduleId: String, sourceUrl: String): ModuleCatalogResult<CatalogModule> {
        val url = runCatching { MmrlRepositoryParser.canonicalUrl(sourceUrl) }.getOrNull()
            ?: return ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
        fun find() = mutableModules.value.firstOrNull { it.moduleId == moduleId && it.repositoryUrl == url }
        find()?.let {
            return ModuleCatalogResult.Success(it)
        }
        if (mutableSources.value.none { it.url == url }) {
            return ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
        }
        val refreshed = refresh(url)
        return find()?.let { ModuleCatalogResult.Success(it) } ?: when (refreshed) {
            is ModuleCatalogResult.Failure -> refreshed
            is ModuleCatalogResult.Success -> ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
        }
    }

    suspend fun discover(): Result<List<RepositorySource>> = attempt {
        if (!networkStatusRepository.isAvailable()) throw RepositoryException(RepositoryError.OFFLINE)
        val body = networkRequestRepository.fetch("https://mmrl.dev/api/repositories.json").getOrThrow()
        MmrlRepositoryParser.discovery(body)
            .filterNot { it.isBuiltIn }
    }

    suspend fun readme(module: CatalogModule): Result<ModuleReadme?> = attempt {
        if (module.readmeUrl.isNotBlank()) return@attempt fetchReadme(module.readmeUrl)
        val source = module.sourceUrl.toHttpUrlOrNull()
            ?.takeIf { it.scheme == "https" && it.host == "github.com" && it.username.isEmpty() && it.password.isEmpty() }
            ?: return@attempt null
        val owner = source.pathSegments.getOrNull(0).orEmpty()
        val repo = source.pathSegments.getOrNull(1).orEmpty().removeSuffix(".git")
        if (!owner.matches(Regex("[A-Za-z0-9_.-]+")) || !repo.matches(Regex("[A-Za-z0-9_.-]+"))) return@attempt null
        val apiUrl = "https://api.github.com/repos/$owner/$repo/readme"
        val response = networkRequestRepository.request(apiUrl).getOrElse { error ->
            if (error is IllegalStateException && error.message == "HTTP 404") return@attempt null
            throw error
        }
        val url = MmrlRepositoryParser.resourceUrl(apiUrl, JSONObject(response.body.orEmpty()).optString("download_url"))
        if (url.isBlank()) throw RepositoryException(RepositoryError.INVALID_FEED)
        fetchReadme(url)
    }

    private suspend fun fetchReadme(url: String): ModuleReadme {
        val safeUrl = MmrlRepositoryParser.resourceUrl(url, url)
        if (safeUrl.isBlank()) throw RepositoryException(RepositoryError.INVALID_URL)
        val response = networkRequestRepository.request(safeUrl).getOrThrow()
        val baseUrl = MmrlRepositoryParser.resourceUrl(safeUrl, response.url)
        if (baseUrl.isBlank()) throw RepositoryException(RepositoryError.INVALID_URL)
        return ModuleReadme(response.body ?: error("Empty response"), baseUrl)
    }

    suspend fun add(url: String, metadata: RepositorySource? = null): Result<RepositorySource> = attempt {
        val canonical = MmrlRepositoryParser.canonicalUrl(url)
        val generation = stateMutex.withLock {
            mutableSources.value.firstOrNull { it.url == canonical }?.let { return@attempt it }
            requireUserSource(canonical)
            sourceGenerations[canonical] ?: 0
        }
        if (!networkStatusRepository.isAvailable()) throw RepositoryException(RepositoryError.OFFLINE)
        val source = metadata?.takeIf { it.url == canonical } ?: RepositorySource(canonical, canonical)
        val feed = withContext(Dispatchers.IO) { loadSource(source, emptyList()) }
        stateMutex.withLock {
            mutableSources.value.firstOrNull { it.url == canonical }?.let { return@withLock it }
            check((sourceGenerations[canonical] ?: 0) == generation) { "Repository changed while adding" }
            val updated = mutableSources.value + feed.source
            sourceStore.save(updated)
            sourceGenerations[canonical] = generation + 1
            mutableSources.value = updated
            cachedModules[canonical] = feed.modules
            mutableFailures.value = mutableFailures.value - canonical
            publishModules()
            feed.source
        }
    }

    suspend fun rename(url: String, name: String) = stateMutex.withLock {
        requireUserSource(url)
        val trimmed = name.trim()
        require(trimmed.length <= 100)
        if (mutableSources.value.none { it.url == url }) return@withLock
        val updated = mutableSources.value.map {
            if (it.url == url) it.copy(customName = trimmed) else it
        }
        sourceStore.save(updated)
        mutableSources.value = updated
        publishModules()
    }

    suspend fun remove(url: String) = stateMutex.withLock {
        requireUserSource(url)
        val updated = mutableSources.value.filterNot { it.url == url }
        sourceStore.save(updated)
        sourceGenerations[url] = (sourceGenerations[url] ?: 0) + 1
        mutableSources.value = updated
        cachedModules.remove(url)
        mutableFailures.value = mutableFailures.value - url
        mutableRefreshingSources.value = mutableRefreshingSources.value - url
        publishModules()
    }

    private suspend fun publishModules() = withContext(Dispatchers.IO) {
        mutableModules.value = mutableSources.value.flatMap { source ->
            cachedModules[source.url].orEmpty().map { module ->
                val installed = runCatching {
                    SuFile.open("/data/adb/modules/${module.moduleId}/module.prop").exists()
                }.getOrDefault(false)
                module.copy(installed = installed, repositoryName = source.displayName)
            }
        }
    }

    private suspend fun loadSource(source: RepositorySource, cached: List<CatalogModule>): RepositoryFeed {
        if (source.isBuiltIn) return loadKernelSuSource(source, cached)
        val body = networkRequestRepository.fetch("${source.url}json/modules.json").getOrThrow()
        val feed = MmrlRepositoryParser.parse(source.url, body)
        return feed.copy(
            source = feed.source.copy(
                customName = source.customName,
            ),
        )
    }

    private fun requireUserSource(url: String) {
        require(url != RepositorySource.KERNEL_SU_URL)
    }

    private suspend fun loadKernelSuSource(source: RepositorySource, cachedModules: List<CatalogModule>): RepositoryFeed {
        val body = networkRequestRepository.fetch("${source.url}modules.json").getOrThrow()
        val json = JSONArray(body)
        val cached = cachedModules.associateBy { it.moduleId }
        val modules = coroutineScope {
            (0 until json.length()).map { index ->
                async(Dispatchers.IO) { json.optJSONObject(index)?.let { parseKernelSuModule(source, it, cached[it.optString("moduleId")]) } }
            }.awaitAll().filterNotNull()
        }
        return RepositoryFeed(source, modules.map { it.first }, modules.firstNotNullOfOrNull { it.second })
    }

    private suspend fun parseKernelSuModule(source: RepositorySource, item: JSONObject, cached: CatalogModule?): Pair<CatalogModule, RepositoryFailure?>? {
        val moduleId = item.optString("moduleId").takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*")) } ?: return null
        val authorList = item.optJSONArray("authors")?.let { authors ->
            (0 until authors.length()).mapNotNull { index ->
                authors.optJSONObject(index)?.let { author ->
                    author.optString("name").trim().takeIf(String::isNotBlank)?.let { name ->
                        CatalogAuthor(name, stripTicks(author.optString("link")))
                    }
                }
            }
        }.orEmpty()
        val latestReleaseObject = item.optJSONObject("latestRelease")
        val latestRelease = latestReleaseObject?.optString("name", latestReleaseObject.optString("version")).orEmpty()
        val detailResult = attempt {
            JSONObject(networkRequestRepository.fetch("${source.url}module/$moduleId.json").getOrThrow())
        }
        val detail = detailResult.getOrNull()
        val releases = detail?.optJSONArray("releases")?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toKernelSuRelease() }
        } ?: if (detail == null) cached?.releases.orEmpty() else emptyList()
        return CatalogModule(
            moduleId = moduleId,
            moduleName = item.optString("moduleName"),
            authors = authorList.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name } ?: item.optString("authors"),
            authorList = authorList,
            summary = item.optString("summary"),
            metamodule = item.optBoolean("metamodule"),
            stargazerCount = item.optInt("stargazerCount"),
            updatedAt = item.optString("updatedAt"),
            createdAt = item.optString("createdAt"),
            latestRelease = latestRelease,
            latestReleaseTime = latestReleaseObject?.optString("time").orEmpty(),
            latestVersionCode = latestReleaseObject?.opt("versionCode").toIntCompat(),
            latestAsset = releases.firstOrNull { it.name == latestRelease },
            installed = false,
            readme = detail?.optString("readmeHTML") ?: cached?.readme.orEmpty(),
            sourceUrl = detail?.let { stripTicks(it.optString("sourceUrl")) } ?: cached?.sourceUrl.orEmpty(),
            releases = releases,
            repositoryUrl = source.url,
            repositoryName = source.name,
        ) to detailResult.exceptionOrNull()?.let { RepositoryFailure.from(it) }
    }

    private fun JSONObject.toKernelSuRelease(): ModuleRelease {
        val releaseName = optString("name", optString("tagName", optString("version")))
        val assets = optJSONArray("releaseAssets")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { asset ->
                    val name = asset.optString("name")
                    val url = stripTicks(asset.optString("downloadUrl"))
                    if (name.isBlank() || url.isBlank()) {
                        null
                    } else {
                        ModuleReleaseAsset(name, url, asset.optLong("size"), asset.opt("downloadCount").toIntCompat())
                    }
                }
            }
        }.orEmpty()
        return ModuleRelease(
            name = releaseName,
            tagName = optString("tagName", releaseName),
            publishedAt = optString("publishedAt"),
            descriptionHTML = optString("descriptionHTML"),
            assets = assets,
        )
    }

    private fun stripTicks(value: String): String = value.trim().removeSurrounding("`")

    private fun Any?.toIntCompat(): Int = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: 0
        else -> 0
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
