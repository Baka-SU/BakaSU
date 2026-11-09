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
import org.bakasu.bakasu.domain.model.DynamicManagerApp
import org.bakasu.bakasu.domain.model.DynamicManagerState
import org.bakasu.bakasu.domain.usecase.ObserveDynamicManagerStateUseCase
import org.bakasu.bakasu.domain.usecase.RefreshDynamicManagerUseCase
import org.bakasu.bakasu.domain.usecase.SelectDynamicManagerUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DynamicManagerRetryTest {
    @Test
    fun retryRetainsTheQueryReportsFailureAndDoesNotSelectOrDuplicateRequests() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val source = MutableStateFlow(DynamicManagerState(isLoading = false))
        val refresh = mockk<RefreshDynamicManagerUseCase>()
        val select = mockk<SelectDynamicManagerUseCase>()
        var completion = CompletableDeferred<Result<Unit>>()
        coEvery { refresh() } coAnswers { completion.await() }
        try {
            val model = DynamicManagerViewModel(
                mockk<ObserveDynamicManagerStateUseCase> { every { this@mockk.invoke() } returns source },
                refresh,
                select,
                mockk(),
                mockk(),
            )
            store.put("manager", model)
            model.dispatch(DynamicManagerUiAction.Search("example"))
            model.dispatch(DynamicManagerUiAction.Retry)
            runCurrent()
            assertTrue(model.state.value.isLoading)
            assertFalse(model.state.value.isRefreshing)
            model.dispatch(DynamicManagerUiAction.Retry)
            completion.complete(Result.failure(IllegalStateException("read failed")))
            runCurrent()
            assertTrue(model.state.value.loadFailed)
            assertEquals("example", model.state.value.search)
            coVerify(exactly = 1) { refresh() }
            completion = CompletableDeferred()
            model.dispatch(DynamicManagerUiAction.Retry)
            runCurrent()
            source.value = source.value.copy(apps = listOf(DynamicManagerApp("Manager", "example.manager", 10001, "/app/base.apk")))
            completion.complete(Result.success(Unit))
            runCurrent()
            assertFalse(model.state.value.loadFailed)
            assertEquals("example", model.state.value.search)
            assertEquals(listOf("example.manager"), model.state.value.apps.map { it.packageName })
            assertFalse(model.state.value.isLoading)
            completion = CompletableDeferred()
            model.dispatch(DynamicManagerUiAction.Refresh)
            source.value = source.value.copy(isRefreshing = true)
            runCurrent()
            assertFalse(model.state.value.isLoading)
            assertTrue(model.state.value.isRefreshing)
            completion.complete(Result.success(Unit))
            runCurrent()
            coVerify(exactly = 0) { select(any()) }
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
