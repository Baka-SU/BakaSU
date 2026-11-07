package org.bakasu.bakasu.ui.wear

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.data.profile.ProfileTemplateRepository
import org.bakasu.bakasu.data.shell.KsuCliRepository
import org.bakasu.bakasu.domain.usecase.AddOnlineProfileTemplateUseCase
import org.bakasu.bakasu.domain.usecase.FetchOnlineProfileTemplatesUseCase
import org.bakasu.bakasu.domain.usecase.ObserveProfileTemplatesUseCase
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateViewModel
import org.bakasu.bakasu.ui.wear.component.WearToastHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class WearOnlineTemplateNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun detailAndReturnShareTheParentModelAndDoNotFetchAgain() {
        val application = compose.activity.application as Application
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            val body = if (chain.request().url.pathSegments.last() == "index.json") {
                "[\"cloud\"]"
            } else {
                """{"id":"cloud","name":"Cloud template","description":"Details"}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val repository = ProfileTemplateRepository(NetworkStatusRepository(application), NetworkRequestRepository(client), KsuCliRepository(application))
        val stack = NavBackStack<NavKey>(WearRoute.OnlineTemplates)
        lateinit var parent: OnlineTemplateViewModel
        lateinit var detail: OnlineTemplateViewModel
        compose.setContent {
            val provider = rememberViewModelStoreProvider()
            // Seed the actual parent store with an HTTP-backed test model; production lookup below
            // must find it in both entries without creating another model or issuing another fetch.
            val owner = rememberViewModelStoreOwner(OnlineTemplatesContentKey, provider)
            parent = viewModel(viewModelStoreOwner = owner) {
                OnlineTemplateViewModel(ObserveProfileTemplatesUseCase(repository), FetchOnlineProfileTemplatesUseCase(repository), AddOnlineProfileTemplateUseCase(repository))
            }
            MaterialTheme {
                WearToastHost {
                    WearOnlineTemplateFeedback(parent)
                    AppScaffold(timeText = {}) {
                        NavDisplay(
                            backStack = stack,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator(provider)),
                            entryProvider = withPageTransitions(
                                entryProvider {
                                    entry<WearRoute.OnlineTemplates>(clazzContentKey = { OnlineTemplatesContentKey }) {
                                        val model = onlineTemplateViewModel(provider)
                                        assertSame(parent, model)
                                        WearOnlineTemplatePage(model, { stack.add(WearRoute.OnlineTemplateDetail(it)) }, {}, {}, false, false, {})
                                    }
                                    entry<WearRoute.OnlineTemplateDetail> { route ->
                                        detail = onlineTemplateViewModel(provider)
                                        WearOnlineTemplateDetail(detail, route.id) { stack.removeAt(stack.lastIndex) }
                                    }
                                },
                            ) { _, content -> content() },
                        )
                    }
                }
            }
        }
        compose.waitUntil(5_000) { parent.state.value.hasLoaded }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Cloud template"))
        compose.onNodeWithText("Cloud template").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(parent, detail)
            assertEquals(WearRoute.OnlineTemplateDetail("cloud"), stack.last())
        }
        compose.onNode(hasText("Cloud template") and isHeading()).assertExists()
        compose.runOnIdle { stack.removeAt(stack.lastIndex) }
        compose.waitForIdle()
        compose.onNodeWithText("Cloud template").assertExists()
        compose.runOnIdle { assertEquals(2, requests.get()) }
    }
}
