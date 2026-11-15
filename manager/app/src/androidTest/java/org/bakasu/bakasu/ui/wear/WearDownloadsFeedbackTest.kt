package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsEvent
import org.bakasu.bakasu.ui.wear.component.WearDownloadsFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearDownloadsFeedbackTest(private val round: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = MutableSharedFlow<WearDownloadsEvent>(extraBufferCapacity = 1)

    @Test
    fun verificationFailureStaysUntilDismissedAndCanBeShownAgain() {
        show { error("A verification failure must not install") }
        val message = compose.activity.getString(R.string.wear_download_verify_failed)
        repeat(2) {
            compose.runOnIdle { assertTrue(events.tryEmit(WearDownloadsEvent.VerificationFailed)) }
            compose.onNodeWithText(message).assertExists()
            compose.mainClock.advanceTimeBy(10_000)
            compose.onNodeWithText(message).assertExists()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.onNodeWithText(message).assertDoesNotExist()
        }
    }

    @Test
    fun installUsesTheLatestCallbackWithoutRecollectingEvents() {
        val installed = mutableListOf<String>()
        val callback = mutableStateOf<(String) -> Unit>({ error("Obsolete callback") })
        compose.setContent {
            MaterialTheme { AppScaffold { WearDownloadsFeedback(events, callback.value) } }
        }
        compose.runOnIdle { callback.value = { installed.add(it) } }
        compose.runOnIdle { assertTrue(events.tryEmit(WearDownloadsEvent.Install("content://verified-module"))) }
        compose.runOnIdle { assertEquals(listOf("content://verified-module"), installed) }
    }

    private fun show(onInstall: (String) -> Unit) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme { AppScaffold { WearDownloadsFeedback(events, onInstall) } }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}")
        fun screens() = listOf(false, true)
    }
}
