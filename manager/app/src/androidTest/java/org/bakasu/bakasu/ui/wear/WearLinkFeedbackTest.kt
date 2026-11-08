package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.network.WearLinkFailure
import org.bakasu.bakasu.ui.viewmodel.WearLinkViewModel
import org.bakasu.bakasu.ui.wear.component.LocalWearToast
import org.bakasu.bakasu.ui.wear.component.WearToastHost
import org.bakasu.bakasu.ui.wear.component.WearToastState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.koin.compose.viewmodel.koinViewModel

class WearLinkFeedbackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun explicitWatchFailureKeepsTheOrdinaryDialog() {
        lateinit var viewModel: WearLinkViewModel
        compose.setContent {
            MaterialTheme {
                AppScaffold {
                    WearToastHost {
                        viewModel = koinViewModel()
                        WearLinkHost(viewModel, false) {}
                    }
                }
            }
        }
        compose.runOnIdle { viewModel.fail(WearLinkFailure.WEBVIEW_UNAVAILABLE) }
        compose.onNodeWithText(compose.activity.getString(R.string.operation_failed)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_link_open_failed)).assertExists()
        compose.runOnIdle { viewModel.dismissResult() }
    }

    @Test
    fun finalAutomaticLinkFailureKeepsTheResultUntilConfirmationDismisses() {
        lateinit var viewModel: WearLinkViewModel
        lateinit var toast: WearToastState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                AppScaffold {
                    WearToastHost {
                        viewModel = koinViewModel()
                        toast = LocalWearToast.current
                        WearLinkHost(viewModel, false) { error("Invalid URL must not navigate") }
                    }
                }
            }
        }
        repeat(2) {
            compose.runOnIdle { viewModel.openAboutLink("file:///invalid") }
            compose.waitUntil { viewModel.result.value != null }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_link_open_failed)).assertExists()
            compose.onNodeWithText(compose.activity.getString(R.string.operation_failed)).assertDoesNotExist()
            compose.runOnIdle {
                assertNotNull(viewModel.result.value)
                assertEquals(0, toast.sequence)
            }
            compose.mainClock.advanceTimeBy(6_000)
            compose.waitUntil { viewModel.result.value == null }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_link_open_failed)).assertDoesNotExist()
        }
        compose.runOnIdle { assertNull(viewModel.result.value) }
    }
}
