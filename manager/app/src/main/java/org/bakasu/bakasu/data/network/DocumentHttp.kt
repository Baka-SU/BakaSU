package org.bakasu.bakasu.data.network

import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.bakasu.bakasu.domain.model.DocumentFormat
import org.bakasu.bakasu.domain.model.DocumentPolicy
import org.bakasu.bakasu.domain.model.ModuleReadme

/** Redirects are checked even when the response comes from the HTTP cache. */
internal fun OkHttpClient.documentResponse(initial: Request, policy: DocumentPolicy, callTimeoutSeconds: Long? = null, onCall: (Call) -> Unit = {}): Response {
    require(callTimeoutSeconds == null || callTimeoutSeconds >= 0)
    val budget = callTimeoutSeconds?.takeIf { it > 0 }?.let { TimeUnit.SECONDS.toNanos(it) }
    val started = System.nanoTime()
    val transport = newBuilder().followRedirects(false).followSslRedirects(false).cookieJar(CookieJar.NO_COOKIES).build()
    var request = initial
    repeat(21) {
        val url = request.url.toString()
        if (!policy.allowsRequest(url, request.method)) throw IOException("Document request not allowed")
        val allowPrivate = policy.allowsPrivateAddress(url)
        // OkHttp can bypass custom DNS for literal IP URLs.
        val host = request.url.host.removePrefix("[").removeSuffix("]")
        if (!allowPrivate && (host.contains(':') || host.all { it.isDigit() || it == '.' }) &&
            !isPublicDocumentAddress(InetAddress.getByName(host))
        ) {
            throw IOException("Private document address not allowed")
        }
        val headers = request.headers.newBuilder()
        for (name in request.headers.names()) {
            if (name.lowercase() !in setOf("accept", "accept-language", "user-agent", "range", "cache-control")) headers.removeAll(name)
        }
        request = request.newBuilder().headers(headers.build()).build()
        // The checked DNS answers are also used for direct connections. A configured
        // proxy remains a trusted transport: its target DNS is outside this app.
        val client = transport.newBuilder().dns(DocumentDns(dns, allowPrivate)).build()
        val call = client.newCall(request)
        budget?.let {
            val remaining = it - (System.nanoTime() - started)
            if (remaining <= 0) throw SocketTimeoutException("Document request timed out")
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)
        }
        onCall(call)
        val response = call.execute()
        if (!response.isRedirect) return response
        val next = response.header("Location")?.let { response.request.url.resolve(it) }
        response.close()
        if (next == null) throw IOException("Invalid document redirect")
        request = request.newBuilder().url(next).build()
    }
    throw IOException("Too many document redirects")
}

internal data class DocumentDns(private val delegate: Dns, private val allowPrivate: Boolean) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty() || (!allowPrivate && addresses.any { !isPublicDocumentAddress(it) })) {
            throw UnknownHostException("Private document address not allowed")
        }
        return addresses
    }
}

internal fun isPublicDocumentAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
    val bytes = address.address.map { it.toInt() and 255 }
    if (bytes.size == 4) {
        return bytes[0] != 0 && bytes[0] < 224 &&
            !(bytes[0] == 100 && bytes[1] in 64..127) &&
            !(bytes[0] == 198 && bytes[1] in 18..19)
    }
    return bytes.size == 16 && bytes[0] in 0x20..0x3f &&
        !(bytes[0] == 0x20 && bytes[1] == 0x02) &&
        !(bytes.take(4) == listOf(0x20, 0x01, 0, 0)) &&
        !(bytes.take(4) == listOf(0x20, 0x01, 0x0d, 0xb8))
}

internal fun NetworkResponse.toDocument(policy: DocumentPolicy): ModuleReadme = ModuleReadme(
    content = body ?: error("Empty document"),
    baseUrl = url,
    format = if (header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() in setOf("text/html", "application/xhtml+xml")) {
        DocumentFormat.HTML
    } else {
        DocumentFormat.MARKDOWN
    },
    policy = policy,
)
