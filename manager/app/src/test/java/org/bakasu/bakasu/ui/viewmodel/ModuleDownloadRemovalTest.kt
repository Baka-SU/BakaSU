package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bakasu.bakasu.domain.model.DownloadModuleInfo
import org.bakasu.bakasu.domain.model.DownloadState
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.usecase.EnqueueDownloadUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadsUseCase
import org.bakasu.bakasu.domain.usecase.RemoveDownloadUseCase
import org.bakasu.bakasu.domain.usecase.VerifyModuleDownloadUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModuleDownloadRemovalTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun removingATaskEndsItsObserverAndRetryCanComplete() = runTest(dispatcher) {
        val enqueue = mockk<EnqueueDownloadUseCase>()
        val observe = mockk<ObserveDownloadUseCase>()
        val check = mockk<VerifyModuleDownloadUseCase>()
        val task = MutableStateFlow<DownloadState?>(DownloadState(1, "module.zip", "https://example.invalid/module.zip"))
        every { enqueue(any(), any(), any(), any()) } returns 1
        every { observe(1) } returns task
        coEvery { check(any(), any(), any()) } returns true
        val model = ModuleDownloadViewModel(enqueue, observe, check).also { store.put("download", it) }
        val asset = ModuleReleaseAsset("module.zip", "https://example.invalid/module.zip", null, null)
        val module = DownloadModuleInfo("Long module name", "https://example.invalid/icon.png")
        model.start(asset, module)
        runCurrent()
        assertEquals(ModuleDownloadPhase.DOWNLOADING, model.state.value.phase)
        task.value = null
        runCurrent()
        assertEquals(ModuleDownloadPhase.FAILED, model.state.value.phase)
        coVerify(exactly = 0) { check(any(), any(), any()) }
        task.value = DownloadState(1, asset.name, asset.downloadUrl, status = DownloadStatus.COMPLETED, resultUri = "file:///module.zip")
        model.start(asset, module)
        runCurrent()
        assertEquals(ModuleDownloadPhase.COMPLETED, model.state.value.phase)
        assertEquals("file:///module.zip", model.state.value.resultUri)
        verify(exactly = 2) { enqueue(asset.downloadUrl, asset.name, null, module) }
        coVerify(exactly = 1) { check("file:///module.zip", 0, "") }
    }

    @Test
    fun clearingWearDownloadsOnlyRemovesZipTasks() = runTest(dispatcher) {
        val observe = mockk<ObserveDownloadsUseCase>()
        val remove = mockk<RemoveDownloadUseCase>(relaxed = true)
        val tasks = MutableStateFlow(
            listOf(
                DownloadState(1, "active.zip", "one"),
                DownloadState(2, "done.ZIP", "two", status = DownloadStatus.COMPLETED),
                DownloadState(3, "manager.apk", "three"),
            ),
        )
        every { observe() } returns tasks
        val model = WearDownloadsViewModel(observe, remove, mockk()).also { store.put("downloads", it) }
        backgroundScope.launch { model.downloads.collect {} }
        runCurrent()
        assertEquals(listOf(2, 1), model.downloads.value.map { it.id })
        model.clear()
        verify(exactly = 1) { remove(1) }
        verify(exactly = 1) { remove(2) }
        verify(exactly = 0) { remove(3) }
    }
}
