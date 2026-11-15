package org.bakasu.bakasu.data.webui

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bakasu.bakasu.domain.model.WebUiCommandResult
import org.bakasu.bakasu.domain.model.WebUiProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUiProcessStreamTest {
    @Test
    fun failedWriteIsHandledByTheIoConsumerAndClosesTheProcess() = runBlocking {
        withTimeout(5_000) {
            val process = TestProcess()
            val callbackThread = Thread.currentThread()
            val result = async {
                runCatching {
                    streamWebUiProcess(process) { type, bytes ->
                        assertNotEquals(callbackThread, Thread.currentThread())
                        assertEquals(WearWebUiProtocol.FRAME_STDOUT, type)
                        assertEquals("output", bytes.decodeToString())
                        throw IOException("Disconnected")
                    }
                }
            }
            process.started.await()
            process.stdout("output")
            assertTrue(result.await().exceptionOrNull() is IOException)
            assertTrue(process.closed.get())
            // A shell callback already queued on the UI thread must remain safe after cleanup.
            process.stdout("late output")
        }
    }

    @Test
    fun cancellingIdleStreamClosesTheProcess() = runBlocking {
        withTimeout(5_000) {
            val process = TestProcess()
            val streaming = async { streamWebUiProcess(process) { _, _ -> } }
            process.started.await()
            streaming.cancelAndJoin()
            assertTrue(process.closed.get())
        }
    }

    @Test
    fun slowConnectionDoesNotBlockCallbacksOrGrowTheQueueWithoutBound() = runBlocking {
        withTimeout(10_000) {
            val process = TestProcess()
            val writing = CompletableDeferred<Unit>()
            val releaseWriter = CountDownLatch(1)
            val result = async {
                runCatching {
                    streamWebUiProcess(process) { _, _ ->
                        writing.complete(Unit)
                        check(releaseWriter.await(5, TimeUnit.SECONDS))
                    }
                }
            }
            try {
                process.started.await()
                process.stdout("first")
                writing.await()
                repeat(100) { process.stdout("queued output") }
            } finally {
                releaseWriter.countDown()
            }
            assertTrue(result.await().exceptionOrNull() is IOException)
            assertTrue(process.closed.get())
        }
    }

    private class TestProcess : WebUiProcess {
        val started = CompletableDeferred<Unit>()
        val closed = AtomicBoolean()
        lateinit var stdout: (String) -> Unit

        override fun start(onStdout: (String) -> Unit, onStderr: (String) -> Unit, onComplete: (WebUiCommandResult) -> Unit) {
            stdout = onStdout
            started.complete(Unit)
        }

        override fun close() {
            closed.set(true)
        }
    }
}
