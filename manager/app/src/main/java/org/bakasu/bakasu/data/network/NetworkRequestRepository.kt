package org.bakasu.bakasu.data.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.bakasu.bakasu.domain.model.DocumentPolicy
import org.bakasu.bakasu.domain.model.HttpStatusException

data class NetworkResponse(
    val body: String?,
    val headers: Map<String, List<String>>,
    val url: String = "",
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
}

class NetworkRequestRepository(
    private val networkStatusRepository: NetworkStatusRepository,
    private val httpClient: OkHttpClient,
) {
    suspend fun fetch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        forceNetwork: Boolean = false,
        callTimeoutSeconds: Long? = null,
    ): Result<String> = request(
        url = url,
        headers = headers,
        forceNetwork = forceNetwork,
        callTimeoutSeconds = callTimeoutSeconds,
    ).mapCatching { response ->
        response.body ?: error("Empty response")
    }

    suspend fun request(
        url: String,
        headers: Map<String, String> = emptyMap(),
        forceNetwork: Boolean = false,
        callTimeoutSeconds: Long? = null,
        maxResponseBytes: Long? = null,
        documentPolicy: DocumentPolicy? = null,
    ): Result<NetworkResponse> = withContext(Dispatchers.IO) {
        if (!networkStatusRepository.isAvailable()) {
            return@withContext Result.failure(IllegalStateException("offline"))
        }

        runCatching {
            val request = Request.Builder()
                .url(url)
                .apply {
                    headers.forEach { (name, value) -> header(name, value) }
                    if (forceNetwork) cacheControl(CacheControl.FORCE_NETWORK)
                }
                .build()
            val response = if (documentPolicy != null) {
                httpClient.documentResponse(request, documentPolicy, callTimeoutSeconds)
            } else {
                val call = httpClient.newCall(request)
                callTimeoutSeconds?.let { call.timeout().timeout(it, TimeUnit.SECONDS) }
                call.execute()
            }
            response.use {
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                NetworkResponse(
                    body = response.body?.let { body ->
                        if (maxResponseBytes == null) {
                            body.string()
                        } else {
                            require(maxResponseBytes > 0)
                            val buffer = Buffer()
                            val source = body.source()
                            while (buffer.size <= maxResponseBytes) {
                                if (source.read(buffer, minOf(8192L, maxResponseBytes - buffer.size + 1)) == -1L) break
                            }
                            check(buffer.size <= maxResponseBytes) { "Response too large" }
                            buffer.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                        }
                    },
                    headers = response.headers.toMultimap(),
                    url = response.request.url.toString(),
                )
            }
        }
    }
}
