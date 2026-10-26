package org.bakasu.bakasu.data.module

import java.io.IOException
import java.util.Base64
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
import org.bakasu.bakasu.data.module.registry.KernelSuRegistryRepository
import org.bakasu.bakasu.data.module.registry.ModuleNotInIndexException
import org.bakasu.bakasu.data.module.registry.RegistryCatalogMapper
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.data.network.toDocument
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.DocumentFormat
import org.bakasu.bakasu.domain.model.DocumentPolicy
import org.bakasu.bakasu.domain.model.HttpStatusException
import org.bakasu.bakasu.domain.model.ModuleCatalogFailure
import org.bakasu.bakasu.domain.model.ModuleCatalogResult
import org.bakasu.bakasu.domain.model.ModuleReadme
import org.bakasu.bakasu.domain.model.RepositoryApiType
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryException
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.domain.model.documentPolicy
import org.json.JSONObject

data class RepositoryFeed(val source: RepositorySource, val modules: List<CatalogModule>, val error: RepositoryFailure? = null, val registryBuildId: String? = null)

class ModuleCatalogRepository(
    private val networkStatusRepository: NetworkStatusRepository,
    private val networkRequestRepository: NetworkRequestRepository,
    private val sourceStore: RepositorySourceStore,
    private val registry: KernelSuRegistryRepository,
) {
    private data class SourceRequest(val source: RepositorySource, val generation: Long)

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

    fun isCurrent(module: CatalogModule): Boolean {
        if (mutableSources.value.none { it.url == module.repositoryUrl }) return false
        return if (module.registryMetadata != null) {
            val catalog = registry.forSource(module.repositoryUrl).currentCatalog() ?: return false
            module.detailLoaded && module.registryMetadata.buildId == catalog.buildId && catalog.find(module.moduleId) != null
        } else {
            true
        }
    }

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
            if (!networkStatusRepository.isAvailable() && sources.none { it.apiType == RepositoryApiType.KERNEL_SU }) {
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
                                SourceRequest(source, sourceGenerations[url] ?: 0)
                            }
                            val result = attempt { loadSource(request.source) }
                            stateMutex.withLock commit@{
                                val current = mutableSources.value.firstOrNull { it.url == url } ?: return@commit false
                                if ((sourceGenerations[url] ?: 0) != request.generation) return@commit false
                                val feed = result.getOrElse { error ->
                                    mutableFailures.value = mutableFailures.value + (url to if (current.apiType == RepositoryApiType.KERNEL_SU) KernelSuRegistryRepository.failure(error) else RepositoryFailure.from(error))
                                    return@commit false
                                }
                                if (feed.registryBuildId != null && feed.registryBuildId != registry.forSource(url).currentCatalog()?.buildId) return@commit false
                                val updated = mutableSources.value.map {
                                    if (it.url == url) feed.source.copy(customName = current.customName) else it
                                }
                                if (attempt { sourceStore.save(updated) }.isFailure) {
                                    mutableFailures.value = mutableFailures.value + (url to RepositoryFailure(RepositoryError.STORAGE))
                                    return@commit false
                                }
                                val currentDetails = cachedModules[url].orEmpty().associateBy { it.moduleId }
                                cachedModules[url] = if (feed.registryBuildId == null) {
                                    feed.modules
                                } else {
                                    feed.modules.map { incoming ->
                                        val detail = currentDetails[incoming.moduleId]?.takeIf {
                                            it.detailLoaded && it.registryMetadata?.buildId == feed.registryBuildId
                                        }
                                        if (detail == null) {
                                            incoming
                                        } else {
                                            incoming.copy(
                                                sourceUrl = detail.sourceUrl,
                                                readmeUrl = detail.readmeUrl,
                                                releases = detail.releases,
                                                latestAsset = detail.latestAsset,
                                                detailLoaded = true,
                                                registryMetadata = incoming.registryMetadata?.copy(
                                                    sourceCommit = detail.registryMetadata?.sourceCommit.orEmpty(),
                                                    sourceFullName = detail.registryMetadata?.sourceFullName.orEmpty(),
                                                    license = detail.registryMetadata?.license,
                                                    homepage = detail.registryMetadata?.homepage,
                                                    support = detail.registryMetadata?.support,
                                                    healthStale = detail.registryMetadata?.healthStale == true,
                                                    fromCache = incoming.registryMetadata.fromCache || detail.registryMetadata?.fromCache == true,
                                                ),
                                            )
                                        }
                                    }
                                }
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

    suspend fun get(moduleId: String, sourceUrl: String, forceDetail: Boolean = false): ModuleCatalogResult<CatalogModule> {
        val url = runCatching { MmrlRepositoryParser.canonicalUrl(sourceUrl) }.getOrNull()
            ?: return ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
        fun find() = mutableModules.value.firstOrNull { it.moduleId == moduleId && it.repositoryUrl == url }
        val request = stateMutex.withLock {
            val source = mutableSources.value.firstOrNull { it.url == url }
                ?: return ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
            SourceRequest(source, sourceGenerations[url] ?: 0)
        }
        if (request.source.apiType == RepositoryApiType.KERNEL_SU) {
            val registry = registry.forSource(url)
            stateMutex.withLock {
                if ((sourceGenerations[url] ?: 0) != request.generation) return ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
                val module = find()
                if (!forceDetail && module?.detailLoaded == true && module.registryMetadata?.buildId == registry.currentCatalog()?.buildId) {
                    return ModuleCatalogResult.Success(module)
                }
            }
            val result = attempt { registry.loadDetail(moduleId) }
            return stateMutex.withLock {
                val source = mutableSources.value.firstOrNull { it.url == url }
                    ?: return@withLock ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
                if ((sourceGenerations[url] ?: 0) != request.generation) return@withLock ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
                val load = result.getOrElse { error ->
                    if (error is ModuleNotInIndexException && registry.currentCatalog()?.find(moduleId) == null) {
                        cachedModules[url] = cachedModules[url].orEmpty().filterNot { it.moduleId == moduleId }
                        publishModules()
                        return@withLock ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
                    }
                    mutableFailures.value = mutableFailures.value + (url to KernelSuRegistryRepository.failure(error))
                    return@withLock ModuleCatalogResult.Failure(ModuleCatalogFailure.Network(""))
                }
                if (registry.currentCatalog()?.buildId != load.catalog.buildId) {
                    return@withLock ModuleCatalogResult.Failure(ModuleCatalogFailure.Network(""))
                }
                val current = cachedModules[url].orEmpty().associateBy { it.moduleId }
                cachedModules[url] = load.catalog.modules.map { item ->
                    if (item.id == moduleId) {
                        RegistryCatalogMapper.detail(load, source)
                    } else {
                        current[item.id]?.takeIf { it.registryMetadata?.buildId == load.catalog.buildId }
                            ?: RegistryCatalogMapper.summary(item, load.catalog, source)
                    }
                }
                load.error?.let { mutableFailures.value = mutableFailures.value + (url to KernelSuRegistryRepository.failure(it)) }
                publishModules()
                ModuleCatalogResult.Success(find() ?: return@withLock ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound))
            }
        }
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

    suspend fun readme(module: CatalogModule, retry: Boolean = false): Result<ModuleReadme?> = attempt {
        if (module.registryMetadata != null) return@attempt registry.forSource(module.repositoryUrl).readme(module)
        val policy = module.documentPolicy()
        val readmeUrl = module.readmeUrl.toHttpUrlOrNull()
        val readmeParts = readmeUrl?.pathSegments.orEmpty()
        val githubReadme = readmeUrl?.host == "github.com" && readmeParts.size >= 4 && readmeParts[2] in setOf("blob", "raw")
        val directUrl = if (githubReadme) {
            readmeUrl.newBuilder().host("raw.githubusercontent.com")
                .removePathSegment(2).query(null).fragment(null).build().toString()
        } else {
            module.readmeUrl
        }
        // A GitHub project page is a fallback source, not the README document itself.
        val githubProject = readmeUrl?.host == "github.com" && readmeParts.filter(String::isNotBlank).size == 2
        var documentFailure: IOException? = null
        if (directUrl.isNotBlank() && !githubProject) {
            try {
                return@attempt fetchReadme(directUrl, policy, retry)
            } catch (error: IOException) {
                documentFailure = error
            }
        }
        val source = readmeUrl?.takeIf { it.host in setOf("github.com", "raw.githubusercontent.com") }
            ?: module.sourceUrl.toHttpUrlOrNull()
                ?.takeIf { it.host == "github.com" }
        if (source == null || !source.isHttps || source.port != 443 || source.username.isNotEmpty() || source.password.isNotEmpty()) {
            documentFailure?.let { throw it }
            return@attempt null
        }
        val owner = source.pathSegments.getOrNull(0).orEmpty()
        val repo = source.pathSegments.getOrNull(1).orEmpty().removeSuffix(".git")
        if (owner.isBlank() || repo.isBlank()) return@attempt null
        val api = "https://api.github.com/".toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment("repos").addPathSegment(owner).addPathSegment(repo)
        val raw = directUrl.toHttpUrlOrNull()?.takeIf { it.host == "raw.githubusercontent.com" }
        val parts = raw?.pathSegments.orEmpty()
        val refIndex = if (parts.getOrNull(2) == "refs" && parts.getOrNull(3) in setOf("heads", "tags")) 4 else 2
        if (raw != null && parts.size > refIndex + 1) {
            api.addPathSegment("contents")
            parts.drop(refIndex + 1).forEach { api.addPathSegment(it) }
            api.addQueryParameter("ref", parts[refIndex])
        } else {
            api.addPathSegment("readme")
        }
        val apiUrl = api.build().toString()
        val response = networkRequestRepository.request(
            apiUrl,
            headers = mapOf("Accept" to "application/vnd.github+json"),
            forceNetwork = retry,
            documentPolicy = policy,
        ).getOrElse { error ->
            if (error is HttpStatusException && error.statusCode == 404 && documentFailure == null) return@attempt null
            throw error
        }
        val json = JSONObject(response.body.orEmpty())
        val url = MmrlRepositoryParser.resourceUrl(apiUrl, json.optString("download_url"))
        if (url.isBlank()) throw RepositoryException(RepositoryError.INVALID_FEED)
        // Read GitHub's inline content so this fallback does not depend on the raw host
        // that may have caused the original document request to fail.
        if (json.optString("encoding") == "base64") {
            val content = Base64.getDecoder().decode(json.getString("content").filterNot(Char::isWhitespace)).toString(Charsets.UTF_8)
            val format = if (json.optString("name").substringAfterLast('.').lowercase() in setOf("html", "htm")) DocumentFormat.HTML else DocumentFormat.MARKDOWN
            return@attempt ModuleReadme(content, url, format, policy)
        }
        fetchReadme(url, policy, retry)
    }

    private suspend fun fetchReadme(url: String, policy: DocumentPolicy, retry: Boolean): ModuleReadme {
        val response = networkRequestRepository.request(url, forceNetwork = retry, documentPolicy = policy).getOrThrow()
        return response.toDocument(policy)
    }

    suspend fun add(url: String, apiType: RepositoryApiType = RepositoryApiType.MMRL): Result<RepositorySource> = attempt {
        val canonical = MmrlRepositoryParser.canonicalUrl(url)
        val generation = stateMutex.withLock {
            mutableSources.value.firstOrNull { it.url == canonical }?.let { return@attempt it }
            sourceGenerations[canonical] ?: 0
        }
        if (!networkStatusRepository.isAvailable()) throw RepositoryException(RepositoryError.OFFLINE)
        val source = RepositorySource(canonical, canonical, apiType = apiType)
        val feed = withContext(Dispatchers.IO) { loadSource(source) }
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

    private fun publishModules() {
        mutableModules.value = mutableSources.value.flatMap { source ->
            cachedModules[source.url].orEmpty().map { module ->
                module.copy(repositoryName = source.displayName)
            }
        }
    }

    private suspend fun loadSource(source: RepositorySource): RepositoryFeed {
        if (source.apiType == RepositoryApiType.KERNEL_SU) return loadKernelSuSource(source)
        if (!networkStatusRepository.isAvailable()) throw RepositoryException(RepositoryError.OFFLINE)
        val body = networkRequestRepository.fetch("${source.url}json/modules.json").getOrThrow()
        val feed = MmrlRepositoryParser.parse(source.url, body)
        return feed.copy(
            source = feed.source.copy(
                customName = source.customName,
                apiType = source.apiType,
            ),
        )
    }

    private fun requireUserSource(url: String) {
        require(url != RepositorySource.KERNEL_SU_URL)
    }

    private suspend fun loadKernelSuSource(source: RepositorySource): RepositoryFeed {
        val load = registry.forSource(source.url).loadCatalog()
        val modules = load.catalog.modules.map { item ->
            RegistryCatalogMapper.summary(item, load.catalog.copy(fromCache = load.catalog.fromCache || load.error != null), source)
        }
        return RepositoryFeed(source.copy(moduleCount = modules.size), modules, load.error?.let(KernelSuRegistryRepository::failure), load.catalog.buildId)
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
