package org.bakasu.bakasu.data.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.bakasu.bakasu.domain.model.DocumentFormat
import org.bakasu.bakasu.domain.model.DocumentNavigation
import org.bakasu.bakasu.domain.model.DocumentPolicy
import org.bakasu.bakasu.domain.model.ModuleReadme
import org.bakasu.bakasu.domain.model.navigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSecurityTest {
    private val registry = DocumentPolicy.SameOrigin("https://registry.example/")
    private val publicWeb = DocumentPolicy.PublicHttps()

    @Test
    fun htmlAndMarkdownHaveTheSamePolicyAndNavigationPermissions() {
        for (format in DocumentFormat.entries) {
            val document = ModuleReadme("<script>bad()</script>", "https://repo.example/readme", format, publicWeb)
            assertEquals(publicWeb, document.policy)
            assertEquals(DocumentNavigation.BLOCK, document.navigation("ksu://action?id=x", true, true))
            assertEquals(DocumentNavigation.EXTERNAL, document.navigation("mailto:author@example.org", true, true))
        }
    }

    @Test
    fun anchorsStayInTheDocumentAndExternalNavigationRequiresAMainFrameGesture() {
        val base = "https://registry.example/docs/readme.html"
        val document = ModuleReadme("", base, policy = registry)
        assertEquals(DocumentNavigation.ANCHOR, document.navigation("$base#install", true, true))
        assertEquals(DocumentNavigation.EXTERNAL, document.navigation("https://other.example/", true, true))
        assertEquals(DocumentNavigation.BLOCK, document.navigation("https://other.example/", false, true))
        assertEquals(DocumentNavigation.BLOCK, document.navigation("https://other.example/", true, false))
        assertEquals(DocumentNavigation.BLOCK, document.navigation("https://user:pass@other.example/", true, true))
    }

    @Test
    fun registryChecksTheWholeOriginAndPublicResourcesKeepCdnSupport() {
        assertTrue(registry.allowsRequest("https://registry.example:443/image.png", "GET"))
        for (url in listOf("https://registry.example.evil/image", "https://registry.example:444/image", "http://registry.example/image", "https://user@registry.example/image")) {
            assertFalse(url, registry.allowsRequest(url, "GET"))
        }
        assertFalse(registry.allowsRequest("https://registry.example/image", "POST"))
        assertTrue(publicWeb.allowsRequest("https://cdn.example/image.png", "GET"))
    }

    private fun fakeClient(seen: MutableList<String>, reply: (Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            seen += chain.request().url.toString()
            reply(chain.request())
        }.build()

    private fun response(request: Request, location: String? = null, cached: Boolean = false): Response {
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(if (location == null) 200 else 302).message("Test").body("body".toResponseBody())
        if (location != null) response.header("Location", location)
        if (cached) response.cacheResponse(Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(302).message("Cached").build())
        return response.build()
    }

    @Test
    fun crossOriginRedirectIsRejectedBeforeSendingTheNextRequestIncludingCachedRedirects() {
        for (cached in listOf(false, true)) {
            val seen = mutableListOf<String>()
            val client = fakeClient(seen) { response(it, "https://other.example/target", cached) }
            assertThrows(IOException::class.java) { client.documentResponse(Request.Builder().url("https://registry.example/start").build(), registry) }
            assertEquals(listOf("https://registry.example/start"), seen)
        }
    }

    @Test
    fun permittedRedirectPreservesFinalDocumentUrlAndRelativeLinks() {
        val seen = mutableListOf<String>()
        val client = fakeClient(seen) { response(it, if (it.url.encodedPath == "/start") "/docs/notes.md" else null) }
        client.documentResponse(Request.Builder().url("https://registry.example/start").build(), registry).use { result ->
            val document = NetworkResponse(result.body!!.string(), emptyMap(), result.request.url.toString()).toDocument(registry)
            assertEquals("https://registry.example/docs/notes.md", document.baseUrl)
            assertEquals("https://registry.example/docs/help", URI(document.baseUrl).resolve("help").toString())
            assertEquals(registry, document.policy)
        }
        assertEquals(2, seen.size)
    }

    @Test
    fun publicMediaRedirectsWorkButDowngradesAndPrivateDestinationsAreNeverRequested() {
        val seen = mutableListOf<String>()
        val client = fakeClient(seen) { response(it, if (it.url.host == "repo.example") "https://cdn.example/image.png" else null) }
        client.documentResponse(Request.Builder().url("https://repo.example/image").build(), publicWeb).close()
        assertEquals(listOf("https://repo.example/image", "https://cdn.example/image.png"), seen)
        for (target in listOf("http://cdn.example/image", "http://127.0.0.1:8080/action", "https://127.0.0.1/action", "https://[::1]/action")) {
            seen.clear()
            val redirect = fakeClient(seen) { response(it, target) }
            assertThrows(IOException::class.java) { redirect.documentResponse(Request.Builder().url("https://repo.example/image").build(), publicWeb) }
            assertEquals(1, seen.size)
        }
    }

    @Test
    fun documentRequestsDoNotForwardCredentialsOrReferrerAcrossRedirects() {
        val seen = mutableListOf<String>()
        val client = fakeClient(seen) {
            for (header in listOf("Authorization", "Cookie", "Referer", "Proxy-Authorization")) assertEquals(null, it.header(header))
            assertEquals("image/png", it.header("Accept"))
            response(it, if (seen.size == 1) "https://cdn.example/image" else null)
        }
        val request = Request.Builder().url("https://repo.example/image").header("Accept", "image/png")
            .header("Cookie", "secret=1").header("Authorization", "secret").header("Referer", "https://private.example/token")
            .header("Proxy-Authorization", "secret").build()
        client.documentResponse(request, publicWeb).close()
        assertEquals(2, seen.size)
    }

    @Test
    fun redirectLoopsAreBounded() {
        val seen = mutableListOf<String>()
        val client = fakeClient(seen) { response(it, "/loop") }
        assertThrows(IOException::class.java) { client.documentResponse(Request.Builder().url("https://registry.example/start").build(), registry) }
        assertTrue(seen.size <= 21)
    }

    @Test
    fun failedResourceDoesNotRetryThroughAnotherTransport() {
        val seen = mutableListOf<String>()
        val client = fakeClient(seen) { throw IOException("Offline") }
        val repository = WebResourceRepository(client)
        assertEquals(null, repository.load("https://cdn.example/image", "GET", emptyMap(), publicWeb))
        assertEquals(listOf("https://cdn.example/image"), seen)
    }

    @Test
    fun documentContextSurvivesDialogStateSerialization() {
        for (policy in listOf(registry, publicWeb, DocumentPolicy.PublicHttps("https://lan.example:8443/"))) {
            for (format in DocumentFormat.entries) {
                val document = ModuleReadme("[help](help)", "https://repo.example/docs/notes.md", format, policy)
                val bytes = ByteArrayOutputStream().also { buffer -> ObjectOutputStream(buffer).use { it.writeObject(document) } }.toByteArray()
                val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
                assertEquals(document, restored)
            }
        }
    }

    @Test
    fun directDnsRejectsPrivateAndMixedAnswersButExplicitRepositoryScopeIsLimitedToItsOrigin() {
        val privateAddress = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 1))
        val publicAddress = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
        val dns = object : Dns {
            override fun lookup(hostname: String) = listOf(publicAddress, privateAddress)
        }
        assertThrows(IOException::class.java) { DocumentDns(dns, false).lookup("cdn.example") }
        assertEquals(listOf(publicAddress, privateAddress), DocumentDns(dns, true).lookup("lan.example"))
        val lan = DocumentPolicy.PublicHttps("https://lan.example:8443/repo/")
        assertTrue(lan.allowsPrivateAddress("https://lan.example:8443/readme"))
        assertFalse(lan.allowsPrivateAddress("https://lan.example:8444/readme"))
        assertFalse(lan.allowsPrivateAddress("https://cdn.example/readme"))
    }

    @Test
    fun addressChecksIncludeIpv6UniqueLocalMappedLoopbackAndCarrierGradeNat() {
        for (literal in listOf("0.0.0.0", "10.1.2.3", "127.0.0.1", "169.254.1.2", "172.16.0.1", "192.168.0.1", "100.64.0.1", "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1")) {
            assertFalse(literal, isPublicDocumentAddress(InetAddress.getByName(literal)))
        }
        assertTrue(isPublicDocumentAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(isPublicDocumentAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test
    fun configuredLanOriginCannotRedirectToAnotherPrivateOrigin() {
        val seen = mutableListOf<String>()
        val policy = DocumentPolicy.PublicHttps("https://192.168.1.10:8443/repo/")
        val client = fakeClient(seen) { response(it, "https://192.168.1.11/readme") }
        assertThrows(IOException::class.java) { client.documentResponse(Request.Builder().url("https://192.168.1.10:8443/notes").build(), policy) }
        assertEquals(listOf("https://192.168.1.10:8443/notes"), seen)
    }

    @Test
    fun redirectsShareOneTimeoutBudget() {
        val seen = mutableListOf<String>()
        val timeouts = mutableListOf<Long>()
        val client = fakeClient(seen) { response(it, if (seen.size == 1) "/final" else null) }
        client.documentResponse(Request.Builder().url("https://registry.example/start").build(), registry, 10) { call ->
            timeouts += call.timeout().timeoutNanos()
        }.close()
        assertEquals(2, timeouts.size)
        assertTrue(timeouts.first() <= TimeUnit.SECONDS.toNanos(10))
        assertTrue(timeouts.last() < timeouts.first())
    }
}
