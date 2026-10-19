package org.bakasu.bakasu.data.module.registry

import android.app.Application
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bakasu.bakasu.data.module.RepositorySourceStore
import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.data.network.documentResponse
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.DocumentFormat
import org.bakasu.bakasu.domain.model.DocumentPolicy
import org.bakasu.bakasu.domain.model.HttpStatusException
import org.bakasu.bakasu.domain.model.ModuleReadme
import org.bakasu.bakasu.domain.model.ModuleUpdateVerification
import org.bakasu.bakasu.domain.model.RepositoryApiType
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryException
import org.bakasu.bakasu.domain.model.RepositoryFailure

class KernelSuRegistryRepository private constructor(
    private val application: Application,
    private val httpClient: OkHttpClient,
    private val networkStatus: NetworkStatusRepository,
    private val sourceStore: RepositorySourceStore?,
    val baseUrl: String,
    private val repositories: ConcurrentHashMap<String, KernelSuRegistryRepository>,
) {
    constructor(
        application: Application,
        httpClient: OkHttpClient,
        networkStatus: NetworkStatusRepository,
        sourceStore: RepositorySourceStore? = null,
    ) : this(application, httpClient, networkStatus, sourceStore, RegistryUrls.DEFAULT_BASE_URL, ConcurrentHashMap()) {
        repositories[baseUrl] = this
    }

    private val commitMutex = Mutex()
    private val details = ConcurrentHashMap<String, ApiModuleDetail>()
    private val diskCache = FileRegistryCache(
        File(application.filesDir, "module_registry").let { root ->
            if (baseUrl == RegistryUrls.DEFAULT_BASE_URL) {
                root
            } else {
                val key = MessageDigest.getInstance("SHA-256").digest(baseUrl.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                File(root, "sources/$key")
            }
        },
    )

    fun forSource(url: String): KernelSuRegistryRepository {
        val parsed = url.trim().toHttpUrlOrNull()
        require(parsed != null && parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty()) { "Invalid registry URL" }
        val normalized = RegistryUrls.normalizeBaseUrl(parsed.newBuilder().query(null).fragment(null).build().toString())
        return repositories.computeIfAbsent(normalized) {
            KernelSuRegistryRepository(application, httpClient, networkStatus, sourceStore, normalized, repositories)
        }
    }

    private fun configuredBaseUrls(): List<String> = sourceStore?.load()
        ?.filter { it.apiType == RepositoryApiType.KERNEL_SU }
        ?.map { forSource(it.url).baseUrl }
        ?.distinct()
        ?: listOf(baseUrl)

    private fun ownerForUrl(url: String): String? {
        val normalized = url.toHttpUrlOrNull()?.toString() ?: return null
        val candidates = configuredBaseUrls().filter { RegistryUrls.isSameOrigin(url, it) }
        return candidates.filter { normalized.startsWith(it) }.maxByOrNull { it.length } ?: candidates.singleOrNull()
    }

    @Volatile
    private var publishedCatalog: RegistryCatalog? = null

    private fun fetch(url: String, owner: Job): String {
        owner.ensureActive()
        if (!networkStatus.isAvailable()) throw RepositoryException(RepositoryError.OFFLINE)
        val request = Request.Builder().url(url).cacheControl(CacheControl.Builder().noCache().build()).build()
        var cancellation: DisposableHandle? = null
        try {
            httpClient.documentResponse(request, DocumentPolicy.SameOrigin(baseUrl)) { call ->
                cancellation?.dispose()
                owner.ensureActive()
                cancellation = owner.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
            }.use { response ->
                owner.ensureActive()
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                val body = response.body ?: error("Empty response")
                check(body.contentLength() <= MAX_DOCUMENT_BYTES) { "Document too large" }
                val source = body.source()
                check(!source.request(MAX_DOCUMENT_BYTES + 1)) { "Document too large" }
                return source.readUtf8().also { owner.ensureActive() }
            }
        } finally {
            cancellation?.dispose()
        }
    }
    private inner class Operation(val baseCatalog: RegistryCatalog?, val owner: Job) {
        val pendingDetails = mutableMapOf<String, ApiModuleDetail>()
        val pendingWrites = linkedMapOf<String, String>()
        val fetcher = RegistryFetcher { url -> fetch(url, owner) }
        val client = RegistryClient(
            baseUrl = baseUrl,
            fetcher = fetcher,
            initialCatalog = baseCatalog,
            cache = object : RegistryCache {
                override fun read(key: String): String? {
                    owner.ensureActive()
                    return (pendingWrites[key] ?: diskCache.read(key)).also { owner.ensureActive() }
                }

                override fun write(key: String, value: String) {
                    owner.ensureActive()
                    pendingWrites[key] = value
                }
            },
        )

        fun rememberDetail(detail: ApiModuleDetail) {
            owner.ensureActive()
            pendingDetails[detail.id] = detail
        }
    }

    private suspend fun <T> withClient(block: Operation.() -> T): T = withContext(Dispatchers.IO) {
        // A separate child lets cancellation close HTTP calls before their coroutine finishes.
        val owner = Job(currentCoroutineContext()[Job])
        val operation = Operation(publishedCatalog, owner)
        try {
            operation.owner.ensureActive()
            val result = try {
                operation.block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operation.owner.ensureActive()
                // A detail failure can follow a successful index refresh.
                commitOperation(operation)
                throw error
            }
            commitOperation(operation)
            result
        } finally {
            owner.complete()
        }
    }

    private suspend fun commitOperation(operation: Operation) = commitMutex.withLock {
        val owner = operation.owner
        owner.ensureActive()
        val catalog = operation.client.currentCatalog()
        if (publishedCatalog?.buildId != operation.baseCatalog?.buildId && publishedCatalog?.buildId != catalog?.buildId) {
            throw BuildMismatchException("Registry operation was superseded")
        }
        operation.pendingWrites.forEach { (key, value) ->
            owner.ensureActive()
            runCatching { diskCache.write(key, value) }
        }
        owner.ensureActive()
        details.putAll(operation.pendingDetails)
        publishedCatalog = catalog ?: publishedCatalog
        publishedCatalog?.let { current ->
            details.entries.removeIf { (_, detail) -> detail.buildId != current.buildId || current.find(detail.id) == null }
        }
    }

    suspend fun loadCatalog(): RegistryClient.CatalogLoad = withClient { client.loadCatalog() }

    suspend fun loadDetail(moduleId: String): RegistryClient.DetailLoad = withClient {
        client.loadDetail(moduleId).also { rememberDetail(it.detail) }
    }

    fun currentCatalog(): RegistryCatalog? = publishedCatalog

    fun isCurrent(update: ModuleUpdateVerification.Registry): Boolean {
        if (update.repositoryUrl != baseUrl) {
            val owner = configuredBaseUrls().firstOrNull { it == update.repositoryUrl } ?: return false
            return forSource(owner).isCurrent(update)
        }
        val catalog = publishedCatalog ?: return false
        if (catalog.buildId != update.buildId || catalog.find(update.moduleId) == null) return false
        val detail = details[update.moduleId]?.takeIf { it.buildId == update.buildId } ?: return false
        return detail.releases.any {
            it.tag == update.release.tagName && it.versionCode == update.release.versionCode &&
                RegistryUpdatePolicy.sameUrl(it.zip.url, update.zipUrl) &&
                it.zip.size == update.expected.size && it.zip.sha256.equals(update.expected.sha256, ignoreCase = true)
        }
    }

    private fun documentOwner(url: String, configuredSource: String): String? = if (url.startsWith("#") && url.contains('@')) {
        if (configuredSource.isBlank()) RegistryUrls.DEFAULT_BASE_URL else ownerForUrl(configuredSource)
    } else {
        ownerForUrl(url)
    }

    fun isRegistryDocument(url: String, configuredSource: String = ""): Boolean = documentOwner(url, configuredSource) != null

    suspend fun changelog(url: String, configuredSource: String = ""): ModuleReadme? {
        val owner = documentOwner(url, configuredSource) ?: return null
        return forSource(owner).changelogForSource(url)
    }

    private suspend fun changelogForSource(url: String): ModuleReadme? = withClient {
        if (url.startsWith("#")) {
            val parts = url.substring(1).split('@', limit = 2)
            if (parts.size != 2) return@withClient null
            val detail = client.loadDetail(parts[0]).detail
            val release = detail.releases.firstOrNull { !it.prerelease && (it.tag == parts[1] || it.version == parts[1]) }
            release?.changelogHtml?.takeIf { it.isNotBlank() }?.let { ModuleReadme(it, client.baseUrl, DocumentFormat.HTML, DocumentPolicy.SameOrigin(client.baseUrl)) }
        } else {
            if (!RegistryUrls.isSameOrigin(url, client.baseUrl) || !RegistryUrls.isHttpsUrl(url)) return@withClient null
            ModuleReadme(fetcher.fetch(url), client.baseUrl, DocumentFormat.MARKDOWN, DocumentPolicy.SameOrigin(client.baseUrl))
        }
    }

    suspend fun readme(module: CatalogModule): ModuleReadme? = withClient {
        val catalog = client.catalog()
        if (catalog.find(module.moduleId) == null) throw ModuleNotInIndexException(module.moduleId)
        val detail = details[module.moduleId]?.takeIf { it.buildId == catalog.buildId }
            ?: client.loadDetail(module.moduleId).detail.also { rememberDetail(it) }
        if (module.registryMetadata?.buildId != detail.buildId) throw BuildMismatchException("README build changed")
        client.loadReadme(detail)?.let { ModuleReadme(it.html, client.baseUrl, DocumentFormat.HTML, DocumentPolicy.SameOrigin(client.baseUrl)) }
    }

    suspend fun resolveUpdate(moduleId: String, updateJsonUrl: String, zipUrl: String, versionCode: Int): ModuleUpdateVerification {
        ownerForUrl(updateJsonUrl)?.let { owner ->
            return forSource(owner).resolveUpdateForSource(moduleId, updateJsonUrl, zipUrl, versionCode)
        }
        val sources = configuredBaseUrls()
        if (sources.any { RegistryUrls.isSameOrigin(updateJsonUrl, it) }) return ModuleUpdateVerification.Rejected
        sources.forEach { owner ->
            val result = forSource(owner).resolveUpdateForSource(moduleId, updateJsonUrl, zipUrl, versionCode)
            if (result != ModuleUpdateVerification.Legacy) return result
        }
        return ModuleUpdateVerification.Legacy
    }

    private suspend fun resolveUpdateForSource(moduleId: String, updateJsonUrl: String, zipUrl: String, versionCode: Int): ModuleUpdateVerification {
        if (RegistryUpdatePolicy.isUnrelatedToRegistry(baseUrl, updateJsonUrl, zipUrl)) {
            return ModuleUpdateVerification.Legacy
        }
        return try {
            withClient {
                val catalog = attempt { client.catalog() }.getOrNull()
                var inIndex = catalog?.let { it.find(moduleId) != null }
                val detail = if (inIndex == true) attempt { client.loadDetail(moduleId).detail.also { rememberDetail(it) } }.getOrNull() else null
                client.currentCatalog()?.let { inIndex = it.find(moduleId) != null }
                when (val result = RegistryUpdatePolicy.decide(client.baseUrl, updateJsonUrl, zipUrl, versionCode, inIndex, detail)) {
                    UpdateVerification.Legacy -> ModuleUpdateVerification.Legacy
                    is UpdateVerification.Registry -> ModuleUpdateVerification.Registry(result.zipUrl, result.expected, RegistryCatalogMapper.release(result.release), moduleId, detail!!.buildId, client.baseUrl)
                    is UpdateVerification.Rejected -> ModuleUpdateVerification.Rejected
                }
            }
        } catch (error: BuildMismatchException) {
            ModuleUpdateVerification.Rejected
        }
    }

    private fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        private const val MAX_DOCUMENT_BYTES = 32L * 1024 * 1024

        fun failure(error: Throwable): RepositoryFailure = when (error) {
            is UnsupportedSchemaException -> RepositoryFailure(RepositoryError.UNSUPPORTED_FORMAT)
            is RegistryException -> RepositoryFailure(RepositoryError.INVALID_FEED)
            is SerializationException -> RepositoryFailure(RepositoryError.INVALID_FEED)
            else -> RepositoryFailure.from(error)
        }
    }
}
