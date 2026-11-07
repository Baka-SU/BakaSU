package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.domain.model.SulogEntry
import org.bakasu.bakasu.domain.model.SulogEventType
import org.bakasu.bakasu.domain.model.SulogState
import org.bakasu.bakasu.domain.usecase.CleanSulogUseCase
import org.bakasu.bakasu.domain.usecase.EnableSulogUseCase
import org.bakasu.bakasu.domain.usecase.GetStringSetPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.ObserveSulogStateUseCase
import org.bakasu.bakasu.domain.usecase.RefreshSulogUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SulogLoadingTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val source = MutableStateFlow(SulogState())
    private val refresh = mockk<RefreshSulogUseCase>()
    private val enable = mockk<EnableSulogUseCase>()
    private val clean = mockk<CleanSulogUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun everyVisitLoadsImmediatelyEvenWithOldEntriesAndRestorationDoesNotDuplicateIt() = runTest(dispatcher) {
        var completion = CompletableDeferred<Unit>()
        coEvery { refresh(null) } coAnswers {
            completion.await()
            source.value = SulogState(
                isLoading = false,
                selectedFilePath = "latest",
                entries = listOf(SulogEntry("entry", SulogEventType.SuCompat, "su", null, emptyMap())),
            )
            Result.success(Unit)
        }
        val model = viewModel()
        repeat(3) { visit ->
            completion = CompletableDeferred()
            model.dispatch(SulogUiAction.LoadPage)
            assertEquals(SulogLoading.PAGE, model.loading.value)
            model.dispatch(SulogUiAction.LoadInitialData)
            runCurrent()
            assertEquals(SulogLoading.PAGE, model.loading.value)
            coVerify(exactly = visit + 1) { refresh(null) }
            completion.complete(Unit)
            runCurrent()
            assertEquals(SulogLoading.NONE, model.loading.value)
            assertEquals(1, model.state.value.entries.size)
        }
    }

    @Test
    fun emptyAndFailedVisitsFinishAndTheNextVisitStillLoads() = runTest(dispatcher) {
        var completion = CompletableDeferred<Result<Unit>>()
        coEvery { refresh(null) } coAnswers {
            completion.await().also { source.value = SulogState(isLoading = false, errorMessage = it.exceptionOrNull()?.message) }
        }
        val model = viewModel()
        listOf(Result.failure<Unit>(IllegalStateException("offline")), Result.success(Unit)).forEach { result ->
            completion = CompletableDeferred()
            model.dispatch(SulogUiAction.LoadPage)
            assertEquals(SulogLoading.PAGE, model.loading.value)
            runCurrent()
            completion.complete(result)
            runCurrent()
            assertEquals(SulogLoading.NONE, model.loading.value)
            assertEquals(result.exceptionOrNull()?.message, model.state.value.errorMessage)
        }
    }

    @Test
    fun manualRefreshKeepsTheSelectedFileAndPhoneRefreshLatestKeepsItsOldFlags() = runTest(dispatcher) {
        source.value = SulogState(isLoading = false, selectedFilePath = "selected")
        var completion = CompletableDeferred<Unit>()
        coEvery { refresh(any()) } coAnswers {
            source.value = source.value.copy(isRefreshing = true)
            completion.await()
            source.value = source.value.copy(isRefreshing = false)
            Result.success(Unit)
        }
        val model = viewModel()
        runCurrent()
        model.dispatch(SulogUiAction.Refresh)
        assertEquals(SulogLoading.REFRESH, model.loading.value)
        runCurrent()
        coVerify(exactly = 1) { refresh("selected") }
        assertFalse(model.state.value.isLoading)
        assertTrue(model.state.value.isRefreshing)
        model.dispatch(SulogUiAction.LoadInitialData)
        runCurrent()
        coVerify(exactly = 1) { refresh(any()) }
        completion.complete(Unit)
        runCurrent()
        completion = CompletableDeferred()
        model.dispatch(SulogUiAction.RefreshLatest)
        runCurrent()
        coVerify(exactly = 1) { refresh(null) }
        assertFalse(model.state.value.isLoading)
        assertTrue(model.state.value.isRefreshing)
        completion.complete(Unit)
        runCurrent()
        assertEquals(SulogLoading.NONE, model.loading.value)
    }

    @Test
    fun enablingCleaningAndFileSelectionStayOperationLoadsThroughTheFollowingRead() = runTest(dispatcher) {
        source.value = SulogState(isLoading = false, selectedFilePath = "selected")
        var operation = CompletableDeferred<Unit>()
        var read = CompletableDeferred<Unit>()
        coEvery { enable(true) } coAnswers {
            operation.await()
            Result.success(Unit)
        }
        coEvery { clean("selected") } coAnswers {
            operation.await()
            Result.success(Unit)
        }
        coEvery { refresh(any()) } coAnswers {
            read.await()
            Result.success(Unit)
        }
        val model = viewModel()
        runCurrent()
        listOf(SulogUiAction.Enable, SulogUiAction.CleanFile, SulogUiAction.SelectFile("other")).forEach { action ->
            operation = CompletableDeferred()
            read = CompletableDeferred()
            model.dispatch(action)
            assertEquals(SulogLoading.OPERATION, model.loading.value)
            runCurrent()
            operation.complete(Unit)
            runCurrent()
            assertEquals(SulogLoading.OPERATION, model.loading.value)
            read.complete(Unit)
            runCurrent()
            assertEquals(SulogLoading.NONE, model.loading.value)
            assertFalse(model.state.value.isEnabling)
        }
        coVerify(exactly = 1) { enable(true) }
        coVerify(exactly = 1) { clean("selected") }
        coVerify(exactly = 2) { refresh("selected") }
        coVerify(exactly = 1) { refresh("other") }
    }

    @Test
    fun cancelledOldRequestCannotFinishANewerVisit() = runTest(dispatcher) {
        val oldRead = CompletableDeferred<Unit>()
        val oldCleanup = CompletableDeferred<Unit>()
        val newRead = CompletableDeferred<Unit>()
        var calls = 0
        coEvery { refresh(null) } coAnswers {
            if (++calls == 1) {
                try {
                    oldRead.await()
                } finally {
                    withContext(NonCancellable) { oldCleanup.await() }
                }
            } else {
                newRead.await()
            }
            Result.success(Unit)
        }
        val model = viewModel()
        model.dispatch(SulogUiAction.LoadPage)
        runCurrent()
        model.dispatch(SulogUiAction.LoadPage)
        runCurrent()
        oldCleanup.complete(Unit)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(SulogLoading.PAGE, model.loading.value)
        newRead.complete(Unit)
        runCurrent()
        assertEquals(SulogLoading.NONE, model.loading.value)
        model.dispatch(SulogUiAction.LoadPage)
        store.clear()
        runCurrent()
        assertEquals(SulogLoading.NONE, model.loading.value)
    }

    @Test
    fun phoneResumeDoesNotCancelAnOngoingEnableOperation() = runTest(dispatcher) {
        source.value = SulogState(isLoading = false, selectedFilePath = "selected")
        val enabled = CompletableDeferred<Unit>()
        var commandFinished = false
        coEvery { enable(true) } coAnswers {
            enabled.await()
            commandFinished = true
            Result.success(Unit)
        }
        coEvery { refresh(any()) } returns Result.success(Unit)
        val model = viewModel()
        runCurrent()
        model.dispatch(SulogUiAction.Enable)
        runCurrent()
        model.dispatch(SulogUiAction.RefreshLatest)
        runCurrent()
        assertTrue(model.state.value.isEnabling)
        enabled.complete(Unit)
        runCurrent()
        assertTrue(commandFinished)
        assertFalse(model.state.value.isEnabling)
        coVerify(exactly = 1) { refresh(null) }
        coVerify(exactly = 1) { refresh("selected") }
    }

    @Test
    fun restoredProcessLoadsOnceWithoutAnExplicitPageAction() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        coEvery { refresh(null) } coAnswers {
            completion.await()
            source.value = SulogState(isLoading = false)
            Result.success(Unit)
        }
        val model = viewModel()
        repeat(2) { model.dispatch(SulogUiAction.LoadInitialData) }
        runCurrent()
        assertEquals(SulogLoading.PAGE, model.loading.value)
        coVerify(exactly = 1) { refresh(null) }
        completion.complete(Unit)
        runCurrent()
        model.dispatch(SulogUiAction.LoadInitialData)
        runCurrent()
        coVerify(exactly = 1) { refresh(null) }
    }

    private fun viewModel(): SulogViewModel {
        val observe = mockk<ObserveSulogStateUseCase>()
        val preferences = mockk<GetStringSetPreferenceUseCase>()
        every { observe() } returns source
        every { preferences(any(), any()) } returns emptySet()
        return SulogViewModel(observe, refresh, enable, clean, preferences, mockk(relaxed = true)).also { store.put("logs", it) }
    }
}
