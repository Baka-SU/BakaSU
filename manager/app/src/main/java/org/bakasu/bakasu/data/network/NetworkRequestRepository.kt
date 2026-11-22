package org.bakasu.bakasu.data.network

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.bakasu.bakasu.domain.model.DocumentPolicy
import org.bakasu.bakasu.domain.model.HttpStatusException

internal class LocalNetworkPermissionRequiredException(cause: IOException) : IOException(cause)

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
        detectLocalNetworkPermission: Boolean = false,
    ): Result<String> = request(
        url = url,
        headers = headers,
        forceNetwork = forceNetwork,
        callTimeoutSeconds = callTimeoutSeconds,
        detectLocalNetworkPermission = detectLocalNetworkPermission,
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
        detectLocalNetworkPermission: Boolean = false,
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
                val localNetworkAttempt = if (detectLocalNetworkPermission && networkStatusRepository.needsLocalNetworkPermission()) {
                    LocalNetworkAttempt(networkStatusRepository)
                } else {
                    null
                }
                val client = localNetworkAttempt?.let { httpClient.newBuilder().eventListener(it).build() } ?: httpClient
                val call = client.newCall(request)
                callTimeoutSeconds?.let { call.timeout().timeout(it, TimeUnit.SECONDS) }
                try {
                    call.execute()
                } catch (error: IOException) {
                    // A failed LAN route can be followed by a successful public route.
                    if (localNetworkAttempt?.failed(error) == true) throw LocalNetworkPermissionRequiredException(error)
                    throw error
                }
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

private class LocalNetworkAttempt(private val networkStatus: NetworkStatusRepository) : EventListener() {
    @Volatile
    private var localConnectionFailed = false

    private var resolvingLocalHost = false

    override fun dnsStart(call: Call, domainName: String) {
        resolvingLocalHost = domainName.trimEnd('.').endsWith(".local", ignoreCase = true)
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        resolvingLocalHost = false
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        val address = inetSocketAddress.address ?: return
        if (proxy.type() == Proxy.Type.DIRECT && networkStatus.isLocalNetworkAddress(address)) {
            localConnectionFailed = true
        }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        localConnectionFailed = false
    }

    fun failed(error: IOException): Boolean = localConnectionFailed || (resolvingLocalHost && error is UnknownHostException)
}
