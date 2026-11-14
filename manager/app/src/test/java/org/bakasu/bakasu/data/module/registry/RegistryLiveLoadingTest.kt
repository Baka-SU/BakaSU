package org.bakasu.bakasu.data.module.registry

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryLiveLoadingTest {
    private val cache = object : RegistryCache {
        private val documents = mutableMapOf<String, String>()
        override fun read(key: String) = documents[key]
        override fun write(key: String, value: String) {
            documents[key] = value
        }
    }
    private var offline = false
    private var build = "first"
    private var readme = "<p>First introduction</p>"
    private var requests = 0
    private val fetcher = RegistryFetcher { url ->
        requests++
        if (offline) throw IOException("Disconnected")
        when {
            url.endsWith("index.json") -> """{"schemaVersion":1,"buildId":"$build","modules":"api/v1/modules.json"}"""
            url.endsWith("modules.json") -> """{"schemaVersion":1,"buildId":"$build","modules":[$summary]}"""
            url.endsWith("example.json") -> """{"schemaVersion":1,"buildId":"$build",$fields,"readme":"api/v1/modules/example/readme"}"""
            url.endsWith("/readme") -> readme
            else -> error("Unexpected URL: $url")
        }
    }
    private val fields = """"id":"example","name":"Example","stable":{"version":"1","versionCode":1,"publishedAt":"2026-11-06"},"updatedAt":"2026-11-06""""
    private val summary get() = """{$fields,"detail":"api/v1/modules/example.json"}"""

    private fun client() = RegistryClient(fetcher = fetcher, cache = cache)

    @Test
    fun artworkLookupReadsTheSavedCatalogWithoutNetworkOrPublishingIt() {
        client().loadCatalog(allowCache = false)
        val before = requests
        val cold = client()
        assertEquals("first", cold.cachedCatalog()?.buildId)
        assertEquals(before, requests)
        assertEquals(null, cold.currentCatalog())
    }

    @Test
    fun liveCatalogRejectsMemoryAndDiskFallbackAndRecoversWithTheNewBuild() {
        val client = client()
        assertEquals("first", client.loadCatalog(allowCache = false).catalog.buildId)
        offline = true
        assertThrows(IOException::class.java) { client.loadCatalog(allowCache = false) }
        assertThrows(IOException::class.java) { client().loadCatalog(allowCache = false) }
        // Update verification still has its existing explicit cache fallback.
        assertNotNull(client().loadCatalog().error)
        offline = false
        build = "second"
        assertEquals("second", client.loadCatalog(allowCache = false).catalog.buildId)
    }

    @Test
    fun liveDetailNeverReturnsAPreviouslyLoadedDetailWhenTheRequestFails() {
        val client = client()
        client.loadCatalog(allowCache = false)
        client.loadDetail("example", allowCache = false)
        offline = true
        assertThrows(IOException::class.java) { client.loadDetail("example", allowCache = false) }
        assertThrows(IOException::class.java) { client().loadDetail("example", allowCache = false) }
        assertTrue(client.loadDetail("example").fromCache)
        offline = false
        build = "second"
        assertEquals("second", client.loadDetail("example", allowCache = false).detail.buildId)
    }

    @Test
    fun liveReadmeRejectsDiskFallbackAndFetchesChangedContentOnRetry() {
        val client = client()
        val detail = client.loadDetail("example", allowCache = false).detail
        assertEquals(readme, client.loadReadme(detail, allowCache = false)?.html)
        offline = true
        val previousRequests = requests
        assertThrows(IOException::class.java) { client.loadReadme(detail, allowCache = false) }
        assertEquals(previousRequests + 1, requests)
        assertThrows(IOException::class.java) { client().loadReadme(detail, allowCache = false) }
        assertEquals(readme, client.loadReadme(detail)?.html)
        offline = false
        readme = "<p>Updated introduction</p>"
        assertEquals(readme, client.loadReadme(detail, allowCache = false)?.html)
    }
}
