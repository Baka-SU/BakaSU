package org.bakasu.bakasu.data.webui

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteWebUiConcurrencyTest {
    @Test
    fun blockedSpawnDoesNotPreventOrdinaryRpcConnection() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        val spawning = CountDownLatch(1)
        val releaseSpawn = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val ordinaryRequest = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val transport = mockk<CompanionBridgeRepository>()
            every { transport.connect("watch", WearWebUiProtocol.SPAWN_PATH) } answers {
                spawning.countDown()
                check(releaseSpawn.await(5, TimeUnit.SECONDS))
                throw IOException("Closed test stream")
            }
            every { transport.connect("watch", WearWebUiProtocol.RPC_PATH) } answers {
                ordinaryRequest.countDown()
                throw IOException("Closed test request")
            }
            val backend = RemoteWebUiBackend(transport, "watch", "token", "module", "Module")
            backend.spawn("tail -f log").start({}, {}, { completed.countDown() })
            assertTrue(spawning.await(5, TimeUnit.SECONDS))
            val request = executor.submit<String> { backend.listModules() }
            // The process still owns its channel when the ordinary request reaches transport.
            assertTrue(ordinaryRequest.await(5, TimeUnit.SECONDS))
            assertEquals("[]", request.get(5, TimeUnit.SECONDS))
            assertEquals(1L, releaseSpawn.count)
        } finally {
            releaseSpawn.countDown()
            completed.await(5, TimeUnit.SECONDS)
            executor.shutdownNow()
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun spawnedCommandsUseASeparateRfcommServiceFromFilesAndControl() {
        val ordinary = CompanionBridgeRepository.serviceUuid(WearWebUiProtocol.RPC_PATH)
        assertNotEquals(ordinary, CompanionBridgeRepository.serviceUuid(WearWebUiProtocol.SPAWN_PATH))
        assertEquals(ordinary, CompanionBridgeRepository.serviceUuid(WearWebUiProtocol.CLAIM_PATH))
        assertEquals(ordinary, CompanionBridgeRepository.serviceUuid(WearWebUiProtocol.CLOSED_PATH))
    }
}
