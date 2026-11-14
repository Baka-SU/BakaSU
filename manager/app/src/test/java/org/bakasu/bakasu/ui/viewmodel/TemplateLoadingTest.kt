package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplateOfflineUseCase
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplatesUseCase
import org.bakasu.bakasu.domain.usecase.RefreshProfileTemplatesUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateLoadingTest {
    @Test
    fun visitCompletionPublishesItsTemplatesAndClearsRefreshingBeforeReturning() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val source = MutableStateFlow(emptyList<ProfileTemplate>())
        val refreshing = MutableStateFlow(false)
        val refresh = mockk<RefreshProfileTemplatesUseCase>()
        var templates = listOf(ProfileTemplate("first", "First"))
        coEvery { refresh(false) } coAnswers {
            refreshing.value = true
            source.value = templates
            refreshing.value = false
            Result.success(Unit)
        }
        try {
            val model = TemplateViewModel(
                mockk<ObserveProfileTemplatesUseCase> { every { this@mockk.invoke() } returns source },
                mockk<ObserveProfileTemplateRefreshingUseCase> { every { this@mockk.invoke() } returns refreshing },
                mockk<ObserveProfileTemplateOfflineUseCase> { every { this@mockk.invoke() } returns MutableStateFlow(false) },
                refresh,
                mockk(),
                mockk(),
                mockk(),
            )
            store.put("templates", model)
            runCurrent()
            repeat(2) { visit ->
                templates = listOf(ProfileTemplate("visit-$visit", "Current visit"))
                var displayed: TemplateUiState? = null
                val request = launch {
                    model.fetchTemplates()
                    displayed = model.state.value
                }
                runCurrent()
                request.join()
                assertEquals("The entry must not drop its loading state while combine still exposes old templates", templates, displayed?.templateList)
                assertFalse(requireNotNull(displayed).isRefreshing)
                assertFalse(requireNotNull(displayed).isLoading)
            }
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
