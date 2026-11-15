package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.wear.component.LocalWearToast
import org.bakasu.bakasu.ui.wear.component.WearFeedbackEffect
import org.bakasu.bakasu.ui.wear.component.WearToastHost
import org.bakasu.bakasu.ui.wear.component.WearToastState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearFeedbackTest(private val style: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun resultsAreConsumedOnceAndIdenticalMessagesCanBeShownAgainWithoutDialogs() {
        val pending = mutableStateOf<String?>(null)
        lateinit var toast: WearToastState
        var consumed = 0
        compose.setContent {
            MaterialTheme {
                WearToastHost(style) {
                    toast = LocalWearToast.current
                    WearFeedbackEffect(pending.value) {
                        consumed++
                        pending.value = null
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        for ((index, resource) in listOf(R.string.dynamic_manager_set_success, R.string.dynamic_manager_set_success, R.string.dynamic_manager_set_failed).withIndex()) {
            val message = compose.activity.getString(resource)
            compose.runOnIdle { pending.value = message }
            compose.mainClock.advanceTimeBy(500)
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle {
                assertNull(pending.value)
                assertEquals(message, toast.message)
                assertEquals(index + 1, consumed)
                assertEquals(index + 1, toast.sequence)
            }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { assertEquals(index + 1, consumed) }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "style={0}")
        fun styles() = listOf("round", "square")
    }
}
