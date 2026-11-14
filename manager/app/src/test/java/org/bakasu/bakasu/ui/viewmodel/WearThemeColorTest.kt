package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppearanceSetting
import org.bakasu.bakasu.domain.model.KernelFeatureSettings
import org.bakasu.bakasu.domain.model.PlatformFeatureStatus
import org.bakasu.bakasu.domain.model.SettingsPlatformSnapshot
import org.bakasu.bakasu.domain.usecase.UpdateAppearanceUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearThemeColorTest {
    @Test
    fun incompleteAndInvalidColorsNeverApplyAndSuccessWaitsForPersistence() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val update = mockk<UpdateAppearanceUseCase>()
        val persisted = CompletableDeferred<Unit>()
        coEvery { update(any()) } coAnswers {
            persisted.await()
            Result.success(SettingsPlatformSnapshot())
        }
        val model = SettingsViewModel(
            mockk { every { this@mockk.invoke() } returns SettingsPlatformSnapshot() },
            mockk { every { this@mockk.invoke() } returns null },
            update,
            mockk(),
            mockk { coEvery { this@mockk.invoke() } returns PlatformFeatureStatus() },
            mockk { coEvery { this@mockk.invoke() } returns KernelFeatureSettings(false, false, false, false, false) },
            mockk(), mockk(), mockk(), mockk(), mockk(), mockk(),
        )
        store.put("settings", model)
        val events = mutableListOf<SettingsUiEvent>()
        backgroundScope.launch { model.events.collect { events += it } }
        try {
            runCurrent()
            model.dispatch(SettingsUiAction.ApplyWearThemeColor("#12"))
            runCurrent()
            assertTrue(events.isEmpty())
            model.dispatch(SettingsUiAction.ApplyWearThemeColor("#ZZZZZZ"))
            runCurrent()
            assertEquals(listOf(SettingsUiEvent.Message(R.string.wear_invalid_color)), events)
            coVerify(exactly = 0) { update(any()) }
            events.clear()
            repeat(3) { model.dispatch(SettingsUiAction.ApplyWearThemeColor("#336699")) }
            runCurrent()
            assertTrue(model.state.value.applyingThemeColor)
            assertTrue(events.isEmpty())
            persisted.complete(Unit)
            runCurrent()
            coVerify(exactly = 1) { update(AppearanceSetting.SeedColor(0xFF336699.toInt())) }
            coVerify(exactly = 1) { update(AppearanceSetting.DynamicColor(false)) }
            assertEquals(listOf(SettingsUiEvent.Message(R.string.wear_theme_color_applied)), events)
            assertFalse(model.state.value.applyingThemeColor)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedPersistenceEmitsAnErrorWithoutSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val update = mockk<UpdateAppearanceUseCase> {
            coEvery { this@mockk.invoke(any()) } returns Result.failure(IllegalStateException("storage"))
        }
        val model = SettingsViewModel(
            mockk { every { this@mockk.invoke() } returns SettingsPlatformSnapshot() },
            mockk { every { this@mockk.invoke() } returns null }, update, mockk(),
            mockk { coEvery { this@mockk.invoke() } returns PlatformFeatureStatus() },
            mockk { coEvery { this@mockk.invoke() } returns KernelFeatureSettings(false, false, false, false, false) },
            mockk(), mockk(), mockk(), mockk(), mockk(), mockk(),
        )
        store.put("settings", model)
        val events = mutableListOf<SettingsUiEvent>()
        backgroundScope.launch { model.events.collect { events += it } }
        try {
            runCurrent()
            model.dispatch(SettingsUiAction.ApplyWearThemeColor("#336699"))
            runCurrent()
            assertEquals(listOf(SettingsUiEvent.Error("storage")), events)
            assertFalse(model.state.value.applyingThemeColor)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
