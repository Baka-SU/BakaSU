package org.bakasu.bakasu.data.network

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.bakasu.bakasu.data.module.registry.RegistryUrls
import org.bakasu.bakasu.domain.model.WebResource

class WebResourceRepository(
    private val httpClient: OkHttpClient,
) {
    private val restrictedClients = ConcurrentHashMap<String, OkHttpClient>()

    fun load(
        url: String,
        method: String,
        requestHeaders: Map<String, String>,
        restrictedOrigin: String? = null,
    ): WebResource? = runCatching {
        val client = if (restrictedOrigin == null) {
            httpClient
        } else {
            restrictedClients.getOrPut(restrictedOrigin) {
                httpClient.newBuilder().followRedirects(false).followSslRedirects(false).addNetworkInterceptor { chain ->
                    if (!RegistryUrls.isAllowedRestrictedResource(chain.request().url.toString(), restrictedOrigin)) throw IOException("Blocked resource")
                    chain.proceed(chain.request())
                }.build()
            }
        }
        if (restrictedOrigin != null && (method != "GET" || !RegistryUrls.isAllowedRestrictedResource(url, restrictedOrigin))) return null
        val request =
            Request.Builder()
                .url(url)
                .method(method, null)
                .headers(requestHeaders.toHeaders())
                .build()
        val response = if (restrictedOrigin == null) {
            client.newCall(request).execute()
        } else {
            restrictedResponse(client, request, restrictedOrigin) ?: return null
        }
        if (restrictedOrigin != null && !response.isSuccessful) {
            response.close()
            return null
        }
        val contentTypes = response.header("content-type", "text/plain; charset=utf-8")
            ?.split(";\\s*".toRegex())
            .orEmpty()
        val mimeType = contentTypes.firstOrNull() ?: "image/*"
        val charset = contentTypes.getOrNull(1)
            ?.split("=\\s*".toRegex())
            ?.getOrNull(1)
            ?: "utf-8"
        val body = response.body ?: run {
            response.close()
            return null
        }
        WebResource(mimeType, charset, body.byteStream())
    }.getOrNull()

    // Follow redirects explicitly: cached responses bypass network interceptors.
    private fun restrictedResponse(client: OkHttpClient, initial: Request, origin: String): Response? {
        var request = initial
        repeat(21) {
            if (!RegistryUrls.isAllowedRestrictedResource(request.url.toString(), origin)) return null
            val response = client.newCall(request).execute()
            if (!response.isRedirect) return response
            val next = response.header("Location")?.let { response.request.url.resolve(it) }
            response.close()
            if (next == null) return null
            request = request.newBuilder().url(next).build()
        }
        return null
    }
}
