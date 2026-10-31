package org.bakasu.bakasu.data.network

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkRequestRepositoryTest {
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
