package org.bakasu.bakasu.data.download

import android.app.NotificationManager
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class DownloadServiceRemovalTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun removingAnActiveTransferClosesTheCallCleansTheFileAndCannotReviveNotifications() {
        transfer(removeWhileRunning = true, failure = false)
    }

    @Test
    fun removingACompletedTaskKeepsItsZipAndRemovesItsNotification() {
        transfer(removeWhileRunning = false, failure = false)
    }

    @Test
    fun removingAFailedTaskRemovesItsNotification() {
        transfer(removeWhileRunning = false, failure = true)
    }

    @Test
    fun removingTheForegroundDownloadKeepsAnotherTransferRunning() {
        val repository = GlobalContext.get().get<DownloadRepository>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notifications = context.getSystemService(NotificationManager::class.java)
        val servers = List(2) { ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) }
        val names = List(2) { "wear-parallel-$it-${System.nanoTime()}.zip" }
        val workers = servers.map { server ->
            thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 15_000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) Unit
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 1048576\r\nConnection: close\r\n\r\n".toByteArray())
                        write(ByteArray(65_536))
                        flush()
                    }
                    assertEquals(-1, socket.getInputStream().read())
                }
            }
        }
        val ids = mutableListOf<Int>()
        try {
            servers.forEachIndexed { index, server ->
                compose.runOnIdle { ids += repository.enqueue("http://127.0.0.1:${server.localPort}/${names[index]}", names[index]) }
                compose.waitUntil(10_000) { repository.downloads.value[ids.last()]?.downloadedBytes == 65_536L }
            }
            val removed = ids.last()
            compose.runOnIdle { repository.remove(removed) }
            compose.waitUntil(10_000) { removed !in repository.downloads.value && notifications.activeNotifications.none { it.id == removed || it.id == 100000 + removed } }
            assertEquals(DownloadStatus.DOWNLOADING, repository.downloads.value[ids.first()]?.status)
            assertTrue(notifications.activeNotifications.any { it.id == ids.first() })
            compose.runOnIdle { repository.remove(ids.first()) }
            compose.waitUntil(10_000) { ids.none { it in repository.downloads.value } }
            workers.forEach {
                it.join(3_000)
                assertFalse(it.isAlive)
            }
            names.forEach { assertFalse(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), it).exists()) }
        } finally {
            compose.runOnIdle { ids.forEach(repository::remove) }
            servers.forEach { it.close() }
        }
    }

    private fun transfer(removeWhileRunning: Boolean, failure: Boolean) {
        val repository = GlobalContext.get().get<DownloadRepository>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notifications = context.getSystemService(NotificationManager::class.java)
        val name = "wear-removal-${System.nanoTime()}.zip"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
        val released = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            server.accept().use { socket ->
                socket.soTimeout = 15_000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) Unit
                val output = socket.getOutputStream()
                val size = if (removeWhileRunning) 1_048_576 else 65_536
                output.write("HTTP/1.1 ${if (failure) "503 Unavailable" else "200 OK"}\r\nContent-Length: $size\r\nConnection: close\r\n\r\n".toByteArray())
                if (!failure) output.write(ByteArray(65_536))
                output.flush()
                if (removeWhileRunning) {
                    assertEquals(-1, socket.getInputStream().read())
                    disconnected.countDown()
                }
                released.await(15, TimeUnit.SECONDS)
            }
        }
        var id = -1
        try {
            compose.runOnIdle { id = repository.enqueue("http://127.0.0.1:${server.localPort}/$name", name) }
            compose.waitUntil(15_000) {
                repository.downloads.value[id]?.let {
                    if (removeWhileRunning) it.downloadedBytes >= 65_536 else it.status == if (failure) DownloadStatus.FAILED else DownloadStatus.COMPLETED
                } == true
            }
            if (!removeWhileRunning && !failure) assertTrue(file.isFile)
            compose.runOnIdle { repository.remove(id) }
            compose.waitUntil(10_000) { id !in repository.downloads.value }
            if (removeWhileRunning) assertTrue(disconnected.await(3, TimeUnit.SECONDS))
            released.countDown()
            worker.join(3_000)
            assertFalse(worker.isAlive)
            assertEquals(!removeWhileRunning && !failure, file.exists())
            // Android applies notification cancellation asynchronously.
            compose.waitUntil(5_000) { notifications.activeNotifications.none { it.id == id || it.id == 100000 + id } }
            assertFalse(repository.downloads.value.containsKey(id))
        } finally {
            released.countDown()
            server.close()
            if (id != -1) compose.runOnIdle { repository.remove(id) }
            file.delete()
        }
    }
}
