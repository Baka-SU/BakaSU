package org.bakasu.bakasu.data.logging

import androidx.test.platform.app.InstrumentationRegistry
import com.topjohnwu.superuser.io.SuFile
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.koin.core.context.GlobalContext

class SulogRepositoryTest {
    @Test
    fun liveReadingFollowsRotationButKeepsAnExplicitHistoricalSelection() = runBlocking {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "sulog-rotation-${System.nanoTime()}").apply { mkdirs() }
        fun file(date: String, id: Int) = File(directory, "sulog-$date.log").apply { writeText("seq=$id type=root_execve comm=record-$id\n") }
        val old = file("2026-11-06", 1)
        val repository = SulogRepository(GlobalContext.get().get(), SuFile(directory.path))
        try {
            repository.refresh(silent = true).getOrThrow()
            assertEquals(old.path, repository.state.value.selectedFilePath)
            val today = file("2026-11-07", 2)
            repository.refresh(silent = true).getOrThrow()
            assertEquals(today.path, repository.state.value.selectedFilePath)
            repository.selectFile(old.path).getOrThrow()
            val rotated = file("2026-11-07-1", 3)
            repository.refresh(silent = true).getOrThrow()
            assertEquals(old.path, repository.state.value.selectedFilePath)
            repository.clean(today.path).getOrThrow()
            assertEquals(old.path, repository.state.value.selectedFilePath)
            repository.selectFile(null).getOrThrow()
            assertEquals(rotated.path, repository.state.value.selectedFilePath)
            val tomorrow = file("2026-11-08", 4)
            repository.refresh(silent = true).getOrThrow()
            assertEquals(tomorrow.path, repository.state.value.selectedFilePath)
            assertFalse(repository.state.value.isRefreshing)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cleanupAndConcurrentPollingCannotRestoreOldRows() = runBlocking {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "sulog-concurrent-${System.nanoTime()}").apply { mkdirs() }
        val active = File(directory, "sulog-2026-11-07.log").apply {
            writeText((1..2_000).joinToString("\n") { "seq=$it type=root_execve comm=old" })
        }
        val repository = SulogRepository(GlobalContext.get().get(), SuFile(directory.path))
        try {
            repository.refresh(silent = true).getOrThrow()
            assertEquals(1_000, repository.state.value.entries.size)
            coroutineScope {
                val reading = async { repository.refresh(silent = true).getOrThrow() }
                val cleaning = async { repository.clean(active.path).getOrThrow() }
                reading.await()
                cleaning.await()
            }
            assertEquals(0L, active.length())
            assertEquals(emptyList<Any>(), repository.state.value.files)
            assertEquals(emptyList<Any>(), repository.state.value.entries)
        } finally {
            directory.deleteRecursively()
        }
    }
}
