package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bakasu.bakasu.data.module.ModulePreferencesRepository
import org.bakasu.bakasu.domain.model.InstalledModulesState
import org.bakasu.bakasu.domain.model.ModulePreferences
import org.bakasu.bakasu.domain.usecase.ObserveInstalledModulesUseCase
import org.bakasu.bakasu.domain.usecase.ResolveInstalledModuleIconUseCase
import org.bakasu.bakasu.domain.usecase.SetModuleRemovedUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModuleRemovalFeedbackTest {
    @Test
    fun pendingRemovalIsImmediateDeduplicatedAndClearedOnSuccessOrFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val source = mockk<ObserveInstalledModulesUseCase> { every { this@mockk.invoke() } returns MutableStateFlow(InstalledModulesState()) }
            val preferences = mockk<ModulePreferencesRepository> { every { this@mockk.preferences } returns MutableStateFlow(ModulePreferences()) }
            val icons = mockk<ResolveInstalledModuleIconUseCase> { every { this@mockk.invoke() } returns MutableStateFlow(emptyMap()) }
            val remove = mockk<SetModuleRemovedUseCase>()
            var completion = CompletableDeferred<Result<Unit>>()
            coEvery { remove("directory", true) } coAnswers { completion.await() }
            val model = ModuleViewModel(source, preferences, mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), remove, mockk(), mockk(), mockk(), mockk(), mockk(), icons)
            store.put("module", model)
            runCurrent()
            listOf(Result.success(Unit), Result.failure(IllegalStateException("failed"))).forEachIndexed { index, result ->
                completion = CompletableDeferred()
                repeat(2) { model.dispatch(ModuleUiAction.SetRemoved("directory", true)) }
                runCurrent()
                assertEquals(setOf("directory"), model.state.value.pendingRemovals)
                coVerify(exactly = index + 1) { remove("directory", true) }
                completion.complete(result)
                runCurrent()
                assertEquals(emptySet<String>(), model.state.value.pendingRemovals)
            }
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
