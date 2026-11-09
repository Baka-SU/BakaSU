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
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.domain.usecase.FetchOnlineProfileTemplatesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplatesUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineTemplateSearchTest {
    @Test
    fun searchesKeepTheFullListAndOnlyManualRefreshUsesTheRefreshIndicator() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val templates = listOf(ProfileTemplate("one", name = "First"), ProfileTemplate("two", name = "Second", description = "Network"))
        var pending = CompletableDeferred<Result<List<ProfileTemplate>>>()
        val fetch = mockk<FetchOnlineProfileTemplatesUseCase>()
        coEvery { fetch() } coAnswers { pending.await() }
        try {
            val model = OnlineTemplateViewModel(mockk<ObserveProfileTemplatesUseCase> { every { this@mockk.invoke() } returns MutableStateFlow(emptyList()) }, fetch, mockk())
            store.put("online", model)
            runCurrent()
            assertTrue(model.state.value.isLoading)
            assertFalse(model.state.value.isRefreshing)
            pending.complete(Result.success(templates))
            runCurrent()
            for ((query, expected) in listOf("FIRST" to listOf(templates[0]), "network" to listOf(templates[1]), "absent" to emptyList(), "" to templates)) {
                model.dispatch(OnlineTemplateUiAction.Search(query))
                runCurrent()
                assertEquals(expected, model.state.value.searchResults)
                assertEquals(templates, model.state.value.templates)
            }
            coVerify(exactly = 1) { fetch() }
            model.dispatch(OnlineTemplateUiAction.Search("two"))
            pending = CompletableDeferred()
            model.dispatch(OnlineTemplateUiAction.Retry)
            runCurrent()
            assertTrue(model.state.value.isLoading)
            assertFalse(model.state.value.isRefreshing)
            assertEquals("two", model.state.value.search)
            model.dispatch(OnlineTemplateUiAction.Retry)
            coVerify(exactly = 2) { fetch() }
            pending.complete(Result.success(templates))
            runCurrent()
            pending = CompletableDeferred()
            model.dispatch(OnlineTemplateUiAction.Load)
            runCurrent()
            assertTrue(model.state.value.isRefreshing)
            assertFalse(model.state.value.isLoading)
            pending.complete(Result.success(templates))
            runCurrent()
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
