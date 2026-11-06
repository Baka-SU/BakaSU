package org.bakasu.bakasu.data.network

import java.net.InetAddress
import java.net.ServerSocket
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkRequestRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun forcedRefreshRequiresTheNetworkDespiteAFreshHttpCacheAndCanRetry() = runBlocking<Unit> {
        val loopback = InetAddress.getByName("127.0.0.1")
        val offline = AtomicBoolean(false)
        ServerSocket(0, 2, loopback).use { server ->
            server.soTimeout = 5000
            Cache(temporaryFolder.newFolder(), 1024 * 1024).use { cache ->
                val client = OkHttpClient.Builder().cache(cache).dns(
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> {
                            if (offline.get()) throw UnknownHostException()
                            return listOf(loopback)
                        }
                    },
                ).build()
                val repository = NetworkRequestRepository(client)
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val received = executor.submit {
                        repeat(2) {
                            server.accept().use { socket ->
                                socket.soTimeout = 5000
                                val reader = socket.getInputStream().bufferedReader()
                                while (!reader.readLine().isNullOrEmpty()) Unit
                                socket.getOutputStream().write(
                                    "HTTP/1.1 200 OK\r\nCache-Control: max-age=3600\r\nContent-Length: 2\r\nConnection: close\r\n\r\n[]".toByteArray(),
                                )
                            }
                        }
                    }
                    val url = "http://repository.test:${server.localPort}/json/modules.json"
                    assertEquals("[]", repository.fetch(url).getOrThrow())
                    offline.set(true)
                    assertEquals("[]", repository.fetch(url).getOrThrow())
                    assertTrue(repository.fetch(url, forceNetwork = true).isFailure)
                    offline.set(false)
                    assertEquals("[]", repository.fetch(url, forceNetwork = true).getOrThrow())
                    received.get(5, TimeUnit.SECONDS)
                } finally {
                    executor.shutdownNow()
                    client.connectionPool.evictAll()
                }
            }
        }
    }

    @Test
    fun fetchUsesHttpWithoutPlatformConnectivityValidation() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val received = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val requestLine = reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        socket.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n[]".toByteArray(),
                        )
                        requestLine
                    }
                }
                val repository = NetworkRequestRepository(OkHttpClient())
                assertEquals("[]", repository.fetch("http://127.0.0.1:${server.localPort}/index.json").getOrThrow())
                assertEquals("GET /index.json HTTP/1.1", received.get(5, TimeUnit.SECONDS))
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
