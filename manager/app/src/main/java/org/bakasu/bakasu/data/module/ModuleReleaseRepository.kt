package org.bakasu.bakasu.data.module

import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.data.network.NetworkResponse
import org.bakasu.bakasu.data.network.toDocument
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.DocumentFormat
import org.bakasu.bakasu.domain.model.DocumentPolicy
import org.bakasu.bakasu.domain.model.ModuleReadme
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseDetails
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.domain.model.documentPolicy
import org.json.JSONObject

/** Resolves presentation metadata without changing the selected repository's downloadable assets. */
class ModuleReleaseRepository(
    private val network: NetworkRequestRepository,
    private val applicationScope: CoroutineScope,
) {
    private data class Cached(val result: Result<NetworkResponse>, val expiresAt: Long)
    private data class GithubRef(val project: String, val tag: String)

    private val mutex = Mutex()
    private val requests = Semaphore(3)
    private data class RequestKey(val url: String, val policy: DocumentPolicy)
    private val pending = mutableMapOf<RequestKey, Deferred<Result<NetworkResponse>>>()
    private val cache = object : LruCache<RequestKey, Cached>(4 * 1024 * 1024) {
        override fun sizeOf(key: RequestKey, value: Cached): Int = ((key.url.length.toLong() + (value.result.getOrNull()?.body?.length ?: 0)) * 2 + 1)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun observe(module: CatalogModule, release: ModuleRelease, retry: Boolean = false): Flow<ModuleReleaseDetails> = channelFlow {
        if (module.registryMetadata != null || module.repositoryUrl == RepositorySource.KERNEL_SU_URL) {
            send(
                ModuleReleaseDetails(
                    notes = release.descriptionHTML.takeIf(String::isNotBlank)?.let {
                        ModuleReadme(it, module.repositoryUrl, DocumentFormat.HTML, module.documentPolicy())
                    },
                ),
            )
            return@channelFlow
        }
        val policy = module.documentPolicy()
        val primary = async {
            attempt { document(release.changelogUrl, retry, policy) }?.also { send(ModuleReleaseDetails(notes = it)) }
        }
        val reference = release.assets.firstNotNullOfOrNull { githubDownload(it.downloadUrl) }
        val github = reference?.let { attempt { github(it, retry) } }
        val primaryNotes = primary.await()
        val githubPage = github?.text("html_url")?.let(::safeUrl)?.takeIf { githubDownloadPage(it) != null }
        val notes = primaryNotes ?: github?.text("body")?.takeIf(String::isNotBlank)?.let {
            githubPage?.let { page -> ModuleReadme(it, page, policy = policy) }
        }
        send(
            ModuleReleaseDetails(
                title = github?.text("name")?.takeIf(String::isNotBlank),
                publishedAt = github?.text("published_at")?.takeIf(String::isNotBlank),
                releaseUrl = githubPage,
                notes = notes,
                notesFailed = notes == null && release.changelogUrl.isNotBlank(),
            ),
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun document(url: String, retry: Boolean, policy: DocumentPolicy): ModuleReadme? {
        if (url.isBlank()) return null
        val response = response(url, retry, policy)
        val content = response.body?.takeIf(String::isNotBlank) ?: return null
        return response.copy(body = content).toDocument(policy)
    }

    private suspend fun github(reference: GithubRef, retry: Boolean): JSONObject? {
        val url = githubApi(reference.project).addPathSegment("releases").addPathSegment("tags").addPathSegment(reference.tag).build()
        val json = JSONObject(response(url.toString(), retry).body.orEmpty())
        return json.takeIf { !it.optBoolean("draft") && it.optString("tag_name") == reference.tag }
    }

    private fun githubProject(value: String): String? {
        val url = safeUrl(value)?.toHttpUrlOrNull() ?: return null
        if (url.host != "github.com" || url.port != 443) return null
        val parts = url.pathSegments.filter(String::isNotBlank)
        if (parts.size < 2) return null
        val project = parts.take(2).mapIndexed { index, part -> if (index == 1) part.removeSuffix(".git") else part }
        return project.joinToString("/")
    }

    private fun githubDownload(value: String): GithubRef? {
        val project = githubProject(value) ?: return null
        val parts = value.toHttpUrlOrNull()?.pathSegments ?: return null
        if (parts.size != 6 || parts[2] != "releases" || parts[3] != "download" || parts[4].isBlank() || parts[5].isBlank()) return null
        return GithubRef(project, parts[4])
    }

    private fun githubDownloadPage(value: String): GithubRef? {
        val project = githubProject(value) ?: return null
        val parts = value.toHttpUrlOrNull()?.pathSegments ?: return null
        if (parts.size != 5 || parts[2] != "releases" || parts[3] != "tag" || parts[4].isBlank()) return null
        return GithubRef(project, parts[4])
    }

    private fun githubApi(project: String) = "https://api.github.com/".toHttpUrlOrNull()!!.newBuilder()
        .addPathSegment("repos").apply { project.split('/').forEach { addPathSegment(it) } }

    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)

    private fun safeUrl(value: String): String? = value.toHttpUrlOrNull()
        ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }?.toString()

    private suspend fun response(url: String, retry: Boolean, policy: DocumentPolicy = DocumentPolicy.PublicHttps()): NetworkResponse {
        val safe = safeUrl(url) ?: error("Invalid release metadata URL")
        val response = request(safe, retry, policy).getOrThrow()
        if (safe.toHttpUrlOrNull()?.host == "api.github.com") {
            check(response.url.toHttpUrlOrNull()?.host == "api.github.com") { "Invalid GitHub API redirect" }
        }
        return response
    }

    private suspend fun request(url: String, retry: Boolean, policy: DocumentPolicy): Result<NetworkResponse> {
        val key = RequestKey(url, policy)
        val task = mutex.withLock {
            if (!retry) cache.get(key)?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }?.let { return it.result }
            pending[key] ?: applicationScope.async(start = CoroutineStart.LAZY) {
                try {
                    val result = requests.withPermit {
                        network.request(
                            url,
                            headers = if (url.toHttpUrlOrNull()?.host == "api.github.com") mapOf("Accept" to "application/vnd.github+json") else emptyMap(),
                            forceNetwork = retry,
                            documentPolicy = policy,
                            callTimeoutSeconds = 10,
                            maxResponseBytes = 1024L * 1024,
                        )
                    }
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    mutex.withLock {
                        cache.put(key, Cached(result, SystemClock.elapsedRealtime() + if (result.isSuccess) 30 * 60_000 else 60_000))
                    }
                    result
                } finally {
                    withContext(NonCancellable) { mutex.withLock { pending.remove(key) } }
                }
            }.also { pending[key] = it }
        }
        task.start()
        return task.await()
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}
