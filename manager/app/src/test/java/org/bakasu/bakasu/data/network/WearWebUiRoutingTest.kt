package org.bakasu.bakasu.data.network

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.data.webui.CompanionBridgeRepository
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WearWebUiRoutingTest {
    @Test
    fun automaticModeTriesPhoneBeforeWatchAndNeverUsesBrowser() = runBlocking {
        val attempts = mutableListOf<WearLinkTarget>()
        val result = resolveWearLink(wearWebUiTargets("auto")) {
            attempts += it
            if (it == WearLinkTarget.PHONE) throw BridgeException(BridgeFailure.NOT_CONNECTED)
        }
        assertEquals(listOf(WearLinkTarget.PHONE, WearLinkTarget.WEBVIEW), attempts)
        assertEquals(WearLinkTarget.WEBVIEW, result.target)
        assertEquals(emptyList<WearLinkTarget>(), result.remaining)
    }

    @Test
    fun successfulPhoneHandoffDoesNotOpenWatchOrPublishFailure() = runBlocking {
        val application = mockk<Application>()
        val bridge = mockk<CompanionBridgeRepository>()
        coEvery { bridge.handoff(BridgeAction.WEB_UI, "module", "Module", false) } returns byteArrayOf()
        val result = WearLinkRepository(application, bridge).resolveWebUi("module", "Module", "auto")
        assertEquals(WearLinkTarget.PHONE, result.target)
        coVerify(exactly = 1) { bridge.handoff(BridgeAction.WEB_UI, "module", "Module", false) }
    }

    @Test
    fun unavailableWatchAfterPhoneFailureRetainsPhoneReason() = runBlocking {
        val application = mockk<Application>()
        every { application.packageManager.hasSystemFeature(any()) } returns false
        val bridge = mockk<CompanionBridgeRepository>()
        coEvery { bridge.handoff(BridgeAction.WEB_UI, "module", "Module", false) } throws BridgeException(BridgeFailure.NOT_PAIRED)
        val failure = runCatching { WearLinkRepository(application, bridge).resolveWebUi("module", "Module", "auto") }.exceptionOrNull() as WearLinkException
        assertEquals(WearLinkFailure.WEBVIEW_UNAVAILABLE, failure.reason)
        assertEquals(BridgeFailure.NOT_PAIRED, failure.phoneFailure)
    }

    @Test
    fun explicitDeviceChoicesStayOnThatDevice() = runBlocking {
        for ((mode, target) in listOf("phone" to WearLinkTarget.PHONE, "webview" to WearLinkTarget.WEBVIEW)) {
            val attempts = mutableListOf<WearLinkTarget>()
            runCatching {
                resolveWearLink(wearWebUiTargets(mode)) {
                    attempts += it
                    throw WearLinkException(WearLinkFailure.WEBVIEW_UNAVAILABLE)
                }
            }
            assertEquals(listOf(target), attempts)
        }
    }

    @Test
    fun cancellationAndTerminalPhoneFailuresDoNotOpenWatch() = runBlocking {
        for (failure in listOf(
            CancellationException("Cancelled"),
            BridgeException(BridgeFailure.REJECTED),
            BridgeException(BridgeFailure.PERMISSION),
            BridgeException(BridgeFailure.RESULT_UNKNOWN),
        )) {
            val attempts = mutableListOf<WearLinkTarget>()
            val error = runCatching {
                resolveWearLink(wearWebUiTargets("auto")) {
                    attempts += it
                    throw failure
                }
            }.exceptionOrNull()
            assertSame(failure, error)
            assertEquals(listOf(WearLinkTarget.PHONE), attempts)
        }
    }
}
