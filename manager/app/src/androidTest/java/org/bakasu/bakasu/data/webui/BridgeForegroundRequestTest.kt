package org.bakasu.bakasu.data.webui

import android.app.Instrumentation
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.BridgePeer
import org.bakasu.bakasu.domain.model.BridgeRequest
import org.bakasu.bakasu.ui.webui.BridgeRequestActivity
import org.bakasu.bakasu.ui.webui.RemoteWebUIActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class BridgeForegroundRequestTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstForegroundRequestShowsConsentAndOpensWebUiAfterApproval() {
        val repository = GlobalContext.get().get<CompanionBridgeRepository>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(RemoteWebUIActivity::class.java.name, Instrumentation.ActivityResult(0, null), true)
        val id = "foreground-webui"
        val pending = repository.beginRequest(
            BridgeRequest(id, BridgePeer("AA:BB:CC:DD:EE:FF", "Test watch"), BridgeAction.WEB_UI, "test_module", "Test module", true),
        )
        try {
            val intent = Intent(compose.activity, BridgeRequestActivity::class.java)
                .putExtra(BridgeActivityLaunch.REQUEST, id)
            assertTrue(runBlocking { repository.showRequest(intent) })
            compose.waitUntil { pending.presented.isCompleted }
            assertEquals(0, monitor.hits)
            compose.onNodeWithText(compose.activity.getString(R.string.wear_bridge_allow_once)).performClick()
            compose.waitUntil { pending.approval.isCompleted }
            assertEquals(false, runBlocking { pending.approval.await() })
            repository.readyRequest(id)
            compose.waitUntil { pending.opened.isCompleted }
            assertTrue(runBlocking { pending.opened.await() })
            assertEquals(1, monitor.hits)
        } finally {
            repository.endRequest(id)
            instrumentation.removeMonitor(monitor)
        }
    }
}
