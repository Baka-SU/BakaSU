package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bakasu.bakasu.data.profile.ProfileRepository
import org.bakasu.bakasu.data.shell.KsuCliRepository
import org.bakasu.bakasu.domain.model.AppControlAction
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.usecase.ControlAppUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppForceStopFeedbackTest {
    @Test
    fun successFeedbackWaitsForForceStopAndFailureNeverReportsSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val control = mockk<ControlAppUseCase>()
        var completion = CompletableDeferred<Result<Unit>>()
        coEvery { control("example.app", AppControlAction.FORCE_STOP) } coAnswers { completion.await() }
        val model = AppProfileViewModel(
            uid = 10000,
            packageName = "example.app",
            getAppGroup = mockk { coEvery { this@mockk.invoke(any(), any()) } returns mockk() },
            getProfile = mockk { coEvery { this@mockk.invoke(any(), any()) } returns AppProfile("example.app") },
            getDefaultUmountModules = mockk { coEvery { this@mockk.invoke() } returns true },
            setProfile = mockk(),
            getSepolicy = mockk(),
            setSepolicy = mockk(),
            controlApp = control,
            validateSepolicy = mockk(),
        )
        store.put("profile", model)
        val events = mutableListOf<AppProfileUiEvent>()
        backgroundScope.launch { model.events.collect { events += it } }
        try {
            runCurrent()
            model.dispatch(AppProfileUiAction.ControlApp(AppControlAction.FORCE_STOP))
            runCurrent()
            assertTrue(events.isEmpty())
            completion.complete(Result.success(Unit))
            runCurrent()
            assertEquals(listOf(AppProfileUiEvent.ForceStopped), events)

            events.clear()
            completion = CompletableDeferred()
            val failure = IllegalStateException("am force-stop failed")
            model.dispatch(AppProfileUiAction.ControlApp(AppControlAction.FORCE_STOP))
            runCurrent()
            completion.complete(Result.failure(failure))
            runCurrent()
            assertEquals(1, events.size)
            assertSame(failure, (events.single() as AppProfileUiEvent.Error).cause)

            events.clear()
            coEvery { control("example.app", AppControlAction.LAUNCH) } returns Result.success(Unit)
            coEvery { control("example.app", AppControlAction.RESTART) } returns Result.success(Unit)
            model.dispatch(AppProfileUiAction.ControlApp(AppControlAction.LAUNCH))
            model.dispatch(AppProfileUiAction.ControlApp(AppControlAction.RESTART))
            runCurrent()
            assertTrue(events.isEmpty())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unsuccessfulShellCommandIsReturnedAsFailure() = runTest {
        val shell = mockk<KsuCliRepository>()
        val repository = ProfileRepository(shell)
        every { shell.forceStopApp("example.app") } returns false
        assertTrue(repository.controlApp("example.app", AppControlAction.FORCE_STOP).isFailure)
        every { shell.forceStopApp("example.app") } returns true
        assertTrue(repository.controlApp("example.app", AppControlAction.FORCE_STOP).isSuccess)
    }
}
