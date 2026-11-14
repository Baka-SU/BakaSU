package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.ui.wear.component.WearLoadingOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearLoadingOverlayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun waitingBlocksUnderlyingTapsAndRemovingItRestoresInteraction() {
        val loading = mutableStateOf(true)
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { clicks++ })
                    if (loading.value) WearLoadingOverlay()
                }
            }
        }
        compose.onRoot().performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(0, clicks)
            loading.value = false
        }
        compose.onRoot().performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
