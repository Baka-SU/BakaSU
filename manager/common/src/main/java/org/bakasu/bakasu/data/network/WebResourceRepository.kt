package org.bakasu.bakasu.data.network

import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bakasu.bakasu.domain.model.DocumentPolicy
import org.bakasu.bakasu.domain.model.WebResource

class WebResourceRepository(private val httpClient: OkHttpClient) {
    fun load(url: String, method: String, requestHeaders: Map<String, String>, policy: DocumentPolicy): WebResource? = runCatching {
        if (!policy.allowsRequest(url, method)) return null
        val request = Request.Builder().url(url).headers(requestHeaders.toHeaders()).build()
        val response = httpClient.documentResponse(request, policy)
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        val body = response.body ?: run {
            response.close()
            return null
        }
        val type = body.contentType()
        WebResource(
            mimeType = type?.let { "${it.type}/${it.subtype}" } ?: "text/plain",
            encoding = type?.charset()?.name() ?: "utf-8",
            body = body.byteStream(),
        )
    }.getOrNull()
}
