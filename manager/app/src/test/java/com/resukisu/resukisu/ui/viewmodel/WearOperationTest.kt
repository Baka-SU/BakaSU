package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import com.resukisu.resukisu.data.flash.FlashRepository
import com.resukisu.resukisu.data.flash.HorizonKernelState
import com.resukisu.resukisu.data.file.ModuleFileRepository
import com.resukisu.resukisu.data.module.ModuleActionRepository
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.FlashOperation
import com.resukisu.resukisu.domain.model.FlashOperationUpdate
import com.resukisu.resukisu.domain.model.ModuleActionUpdate
import com.resukisu.resukisu.domain.usecase.ExecuteFlashOperationUseCase
import com.resukisu.resukisu.domain.usecase.ExecuteModuleActionUseCase
import com.resukisu.resukisu.domain.usecase.IsSoftRebootPreferredUseCase
import com.resukisu.resukisu.domain.usecase.RebootUseCase
import com.resukisu.resukisu.domain.usecase.SaveModuleActionLogUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class WearOperationTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun restoredActionDoesNotExecuteWithoutNewStart() = runTest(dispatcher) {
        val execute = Mockito.mock(ExecuteModuleActionUseCase::class.java)
        val model = ExecuteModuleActionViewModel("module", execute,
            Mockito.mock(SaveModuleActionLogUseCase::class.java), autoStart = false)
        advanceUntilIdle()
        assertFalse(model.state.value.started)
        assertFalse(model.state.value.running)
        assertNull(model.state.value.successful)
        Mockito.verifyNoInteractions(execute)
    }

    @Test fun failedActionRetainsOutcomeAndStartsOnce() = runTest(dispatcher) {
        val execute = Mockito.mock(ExecuteModuleActionUseCase::class.java)
        Mockito.`when`(execute("module")).thenReturn(flowOf(
            ModuleActionUpdate.Output("last line"), ModuleActionUpdate.Completed(false)))
        val model = ExecuteModuleActionViewModel("module", execute,
            Mockito.mock(SaveModuleActionLogUseCase::class.java), autoStart = false)
        model.dispatch(ExecuteModuleActionUiAction.Start)
        model.dispatch(ExecuteModuleActionUiAction.Start)
        advanceUntilIdle()
        model.dispatch(ExecuteModuleActionUiAction.Start)
        advanceUntilIdle()
        assertEquals(false, model.state.value.successful)
        assertFalse(model.state.value.running)
        assertEquals("last line\n", model.state.value.output)
        Mockito.verify(execute, Mockito.times(1)).invoke("module")
    }

    @Test fun flashStartOnceDoesNotRestartAndFlushesBoundedTail() = runTest(dispatcher) {
        val execute = Mockito.mock(ExecuteFlashOperationUseCase::class.java)
        Mockito.`when`(execute(FlashOperation.Restore)).thenReturn(flow {
            repeat(10_000) { emit(FlashOperationUpdate.Output("line $it")) }
            emit(FlashOperationUpdate.Completed(false, 1))
        })
        val model = FlashViewModel(Mockito.mock(RebootUseCase::class.java),
            Mockito.mock(IsSoftRebootPreferredUseCase::class.java), execute, displayLogLimit = 1024)
        model.dispatch(FlashUiAction.StartOnce(FlashOperation.Restore))
        model.dispatch(FlashUiAction.StartOnce(FlashOperation.Restore))
        advanceUntilIdle()
        model.dispatch(FlashUiAction.StartOnce(FlashOperation.Restore))
        advanceUntilIdle()
        assertEquals(FlashingStatus.FAILED, model.state.value.flashingStatus)
        assertEquals(1, model.state.value.exitCode)
        assertTrue(model.state.value.output.length <= 1024)
        assertTrue(model.state.value.output.endsWith("line 9999\n"))
        assertTrue(model.state.value.outputTruncated)
        Mockito.verify(execute, Mockito.times(1)).invoke(FlashOperation.Restore)
    }

    @Test fun restoredFlashRemainsUnstarted() = runTest(dispatcher) {
        val execute = Mockito.mock(ExecuteFlashOperationUseCase::class.java)
        val model = FlashViewModel(Mockito.mock(RebootUseCase::class.java),
            Mockito.mock(IsSoftRebootPreferredUseCase::class.java), execute)
        advanceUntilIdle()
        assertFalse(model.state.value.started)
        Mockito.verifyNoInteractions(execute)
    }

    @Test fun leavingPageWaitsForOperationBeforeClearingModels() = runTest(dispatcher) {
        val stores = WearPageStoresViewModel()
        val owner = stores.acquire("action")
        var cleared = false
        val model = object : ViewModel() {
            override fun onCleared() { cleared = true }
        }
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return model as T
            }
        }
        ViewModelProvider.create(owner, factory)["model", ViewModel::class]
        val running = MutableStateFlow(true)
        stores.release("action", running)
        runCurrent()
        assertFalse(cleared)
        assertSame(owner, stores.acquire("action"))
        stores.release("action", running)
        running.value = false
        runCurrent()
        assertTrue(cleared)
        assertNotSame(owner, stores.acquire("action"))
    }

    @Test fun displayTailHandlesOversizedLinesAndClear() {
        val log = DisplayLogBuffer(64)
        log.append("x".repeat(1000))
        assertEquals(64, log.snapshot().length)
        assertTrue(log.truncated)
        log.clear()
        log.append("new screen\n")
        assertEquals("new screen\n", log.snapshot())
        assertFalse(log.truncated)
    }
}
