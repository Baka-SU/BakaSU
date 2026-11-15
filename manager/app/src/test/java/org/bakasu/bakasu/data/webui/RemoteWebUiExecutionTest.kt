package org.bakasu.bakasu.data.webui

import android.bluetooth.BluetoothSocket
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteWebUiExecutionTest {
    @Test
    fun executionCanOutliveTheFifteenSecondHandshakeDeadline() = withExecution { backend, reading, release, closed ->
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<org.bakasu.bakasu.domain.model.WebUiCommandResult> { backend.execute("sleep 20; echo done") }
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            assertFalse("Execution must not close its socket after the handshake timeout", closed.await(16, TimeUnit.SECONDS))
            release.countDown()
            assertEquals(0, result.get(5, TimeUnit.SECONDS).code)
            assertTrue(closed.await(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun closingThePageInterruptsACommandWaitingForItsResult() = withExecution { backend, reading, _, closed ->
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<org.bakasu.bakasu.domain.model.WebUiCommandResult> { backend.execute("sleep 3600") }
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            backend.close()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            assertEquals(-1, result.get(5, TimeUnit.SECONDS).code)
        } finally {
            backend.close()
            executor.shutdownNow()
        }
    }

    private fun withExecution(test: (RemoteWebUiBackend, CountDownLatch, CountDownLatch, CountDownLatch) -> Unit) {
        mockkConstructor(JSONObject::class)
        mockkStatic(Log::class)
        try {
            every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
            every { anyConstructed<JSONObject>().put(any<String>(), any<String>()) } answers { self as JSONObject }
            every { anyConstructed<JSONObject>().put(any<String>(), any<Int>()) } answers { self as JSONObject }
            every { anyConstructed<JSONObject>().toString() } returns "{}"
            every { anyConstructed<JSONObject>().getInt("code") } returns 0
            every { anyConstructed<JSONObject>().optString("stdout") } returns "done"
            every { anyConstructed<JSONObject>().optString("stderr") } returns ""
            val reading = CountDownLatch(1)
            val release = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val response = ByteArrayOutputStream().also {
                with(WearWebUiProtocol) { DataOutputStream(it).writeFrame(FRAME_OK, "{}".toByteArray()) }
            }.toByteArray().inputStream()
            val input = object : InputStream() {
                override fun read(): Int {
                    reading.countDown()
                    check(release.await(25, TimeUnit.SECONDS))
                    if (closed.count == 0L) throw IOException("Socket closed")
                    return response.read()
                }
            }
            val socket = mockk<BluetoothSocket>(relaxed = true)
            every { socket.remoteDevice.address } returns "watch"
            every { socket.inputStream } returns input
            every { socket.outputStream } returns ByteArrayOutputStream()
            every { socket.close() } answers {
                closed.countDown()
                release.countDown()
            }
            val transport = mockk<CompanionBridgeRepository>(relaxed = true)
            every { transport.connect("watch", WearWebUiProtocol.RPC_PATH) } returns BridgeConnection(socket) {}
            test(RemoteWebUiBackend(transport, "watch", "token", "module", "Module"), reading, release, closed)
        } finally {
            unmockkConstructor(JSONObject::class)
            unmockkStatic(Log::class)
        }
    }
}
