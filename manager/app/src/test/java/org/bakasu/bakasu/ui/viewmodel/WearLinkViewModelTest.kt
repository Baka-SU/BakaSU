package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.bakasu.bakasu.data.network.WearLinkException
import org.bakasu.bakasu.data.network.WearLinkFailure
import org.bakasu.bakasu.data.network.WearLinkRepository
import org.bakasu.bakasu.data.network.WearLinkResolution
import org.bakasu.bakasu.data.network.WearLinkTarget
import org.bakasu.bakasu.domain.model.BridgeTransfer
import org.bakasu.bakasu.domain.usecase.ConfigureCompanionBridgeUseCase
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.ObserveCompanionBridgeUseCase
import org.bakasu.bakasu.ui.wear.WearNavigator
import org.bakasu.bakasu.ui.wear.WearRoute
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearLinkViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = mockk<WearLinkRepository>()
    private val preferences = mockk<GetStringPreferenceUseCase>()
    private val bridge = mockk<ConfigureCompanionBridgeUseCase>(relaxed = true)
    private var selectedMode = "webview"
    private lateinit var model: WearLinkViewModel
    private val url = "https://example.com"

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { preferences("wear_link_mode", "auto") } answers { selectedMode }
        val observe = mockk<ObserveCompanionBridgeUseCase>()
        every { observe.transfer } returns MutableStateFlow<BridgeTransfer?>(null)
        every { observe.permissionRequest } returns MutableStateFlow<String?>(null)
        model = WearLinkViewModel(repository, preferences, observe, bridge)
        store.put("link", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun linksUseSavedModeAndInternalNavigationRetainsFailureContext() = runTest(dispatcher) {
        coEvery { repository.resolve(url, "webview") } returns WearLinkResolution(WearLinkTarget.WEBVIEW, emptyList())
        coEvery { repository.continueLink(url, emptyList(), null, false) } throws WearLinkException(WearLinkFailure.WEBVIEW_UNAVAILABLE)
        val navigator = WearNavigator(NavBackStack<NavKey>(WearRoute.Main), model::onNavigation, {}, {})
        backgroundScope.launch {
            model.events.collect { event ->
                if (event is WearLinkEvent.WebView) model.navigateWithinLink { navigator.open(WearRoute.Browser(event.url)) }
            }
        }
        runCurrent()
        model.open(url)
        runCurrent()
        selectedMode = "phone"
        model.navigateWithinLink { navigator.back() }
        model.webViewFailed(url)
        runCurrent()
        assertFalse((model.result.value as WearLinkResult.Failed).automaticLink)
        coVerify(exactly = 1) { repository.continueLink(url, emptyList(), null, false) }
        coVerify(exactly = 0) { repository.resolve(url, "phone") }
    }

    @Test
    fun userBackCancelsPendingWebViewFallback() = runTest(dispatcher) {
        coEvery { repository.resolve(url, "webview") } returns WearLinkResolution(WearLinkTarget.WEBVIEW, emptyList())
        model.open(url)
        runCurrent()
        model.onNavigation()
        model.webViewFailed(url)
        runCurrent()
        coVerify(exactly = 0) { repository.continueLink(any(), any(), any(), any()) }
        assertNull(model.result.value)
    }

    @Test
    fun explicitWatchModuleFailureNeverInvokesPhone() = runTest(dispatcher) {
        coEvery { repository.resolveWebUi("module", "Module", "webview") } throws WearLinkException(WearLinkFailure.WEBVIEW_UNAVAILABLE)
        model.openWebUi("module", "Module")
        runCurrent()
        assertFalse((model.result.value as WearLinkResult.Failed).automaticLink)
        coVerify(exactly = 1) { repository.resolveWebUi("module", "Module", "webview") }
    }

    @Test
    fun automaticModuleWebUiOpensWatchOnlyWhenResolutionFallsBack() = runTest(dispatcher) {
        selectedMode = "auto"
        val events = mutableListOf<WearLinkEvent>()
        backgroundScope.launch { model.events.collect { events += it } }
        coEvery { repository.resolveWebUi("module", "Module", "auto") } returns WearLinkResolution(WearLinkTarget.PHONE, listOf(WearLinkTarget.WEBVIEW))
        model.openWebUi("module", "Module")
        runCurrent()
        assertEquals(emptyList<WearLinkEvent>(), events)
        coEvery { repository.resolveWebUi("module", "Module", "auto") } returns WearLinkResolution(WearLinkTarget.WEBVIEW, emptyList())
        model.openWebUi("module", "Module")
        runCurrent()
        assertEquals(listOf(WearLinkEvent.WebUi("module")), events)
    }
}
