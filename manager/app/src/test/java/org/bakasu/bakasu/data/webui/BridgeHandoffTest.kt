package org.bakasu.bakasu.data.webui

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.model.BridgePeer
import org.bakasu.bakasu.domain.model.BridgeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeHandoffTest {
    @Test
    fun finalAcknowledgementDistinguishesConfirmedFailureAndLegacyUnknownResults() {
        assertTrue(BridgeHandoff.exchange({}, { WearWebUiProtocol.FRAME_OK to byteArrayOf() }).isEmpty())
        for ((payload, expected) in listOf(
            BridgeHandoff.OPEN_FAILED to BridgeFailure.OPEN_FAILED,
            BridgeHandoff.REJECTED to BridgeFailure.REJECTED,
            "" to BridgeFailure.RESULT_UNKNOWN,
        )) {
            val error = runCatching { BridgeHandoff.exchange({}, { WearWebUiProtocol.FRAME_ERROR to payload.toByteArray() }) }.exceptionOrNull()
            assertEquals(expected, (error as BridgeException).reason)
        }
    }

    @Test
    fun lostAcknowledgementAndFailedReadyWriteDoNotClaimThePhoneFailedToOpen() {
        var launches = 0
        val lostReply = runCatching {
            BridgeHandoff.exchange({ launches++ }, { throw IOException("Lost reply") })
        }.exceptionOrNull()
        assertEquals(1, launches)
        assertEquals(BridgeFailure.RESULT_UNKNOWN, (lostReply as BridgeException).reason)
        val failedWrite = runCatching {
            BridgeHandoff.exchange({ throw IOException("Ready write failed") }, { error("Must not read") })
        }.exceptionOrNull()
        assertEquals(BridgeFailure.RESULT_UNKNOWN, (failedWrite as BridgeException).reason)
    }

    @Test
    fun cancellationDoesNotBecomeAnUnknownResult() {
        val cancelled = CancellationException("Cancelled")
        assertEquals(cancelled, runCatching { BridgeHandoff.exchange({}, { throw cancelled }) }.exceptionOrNull())
    }

    @Test
    fun expiredAndRepeatedPhoneRequestsCannotLaunchAgain() = runBlocking {
        fun request() = PendingBridgeRequest(BridgeRequest("request", BridgePeer("phone", "Phone"), BridgeAction.LINK, "https://example.org", "", true))
        var launches = 0
        val expired = request()
        expired.expire()
        expired.launch {
            launches++
            true
        }
        assertFalse(expired.opened.await())
        assertEquals(0, launches)
        val opened = request()
        opened.launch {
            launches++
            true
        }
        opened.expire()
        opened.launch {
            launches++
            true
        }
        assertTrue(opened.opened.await())
        assertEquals(1, launches)
        val rejected = request()
        rejected.approval.complete(true)
        rejected.reject()
        rejected.launch {
            launches++
            true
        }
        assertTrue(rejected.rejected)
        assertFalse(rejected.opened.await())
        assertEquals(1, launches)
    }

    @Test
    fun permissionGrantResumesPendingOperationOnce() = runBlocking {
        val permission = BridgePermissionRequest()
        var opened = 0
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            if (permission.await()) opened++
        }
        val id = checkNotNull(permission.id.value)
        permission.answer(id, true)
        assertNull(permission.id.value)
        permission.answer(id, true)
        request.await()
        assertEquals(1, opened)
        assertNull(permission.id.value)
    }

    @Test
    fun deniedPermissionDoesNotResumeOperation() = runBlocking {
        val permission = BridgePermissionRequest()
        val request = async(start = CoroutineStart.UNDISPATCHED) { permission.await() }
        permission.answer(checkNotNull(permission.id.value), false)
        assertFalse(request.await())
        assertNull(permission.id.value)
    }

    @Test
    fun latePermissionResultCannotResumeCancelledOrNewOperation() = runBlocking {
        val permission = BridgePermissionRequest()
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) { permission.await() }
        val oldId = checkNotNull(permission.id.value)
        permission.cancel()
        cancelled.join()
        assertTrue(cancelled.isCancelled)
        val next = async(start = CoroutineStart.UNDISPATCHED) { permission.await() }
        permission.answer(oldId, true)
        assertFalse(next.isCompleted)
        permission.answer(checkNotNull(permission.id.value), true)
        assertTrue(next.await())
    }

    @Test
    fun onceAndRememberedConsentAreDistinctAndCannotBeOverwritten() = runBlocking {
        val pending = PendingBridgeRequest(BridgeRequest("request", BridgePeer("phone", "Phone"), BridgeAction.LINK, "https://kernelsu.org", "", true))
        assertFalse(pending.approval.isCompleted)
        assertTrue(pending.approval.complete(false))
        assertFalse(pending.approval.complete(true))
        assertEquals(false, pending.approval.await())
    }

    @Test
    fun declinedConsentNeverBecomesApproval() = runBlocking {
        val pending = PendingBridgeRequest(BridgeRequest("request", BridgePeer("phone", "Phone"), BridgeAction.LINK, "https://kernelsu.org", "", true))
        pending.approval.complete(null)
        assertFalse(pending.approval.complete(true))
        assertNull(pending.approval.await())
    }

    @Test
    fun requestOnlyAllowsWebLinksAndModuleIds() {
        assertTrue(BridgeHandoff.valid(BridgeAction.LINK, "https://example.org/path?q=1"))
        assertTrue(BridgeHandoff.valid(BridgeAction.WEB_UI, "test_module"))
        listOf("file:///data/adb", "intent://example", "https:", "javascript:alert(1)").forEach {
            assertFalse(BridgeHandoff.valid(BridgeAction.LINK, it))
        }
        listOf("", "../module", "..", "module/other").forEach {
            assertFalse(BridgeHandoff.valid(BridgeAction.WEB_UI, it))
        }
        assertTrue(runCatching { BridgeAction.valueOf("TEMPLATES") }.isFailure)
    }

    @Test
    fun rememberedCredentialCannotAuthorizeChangedContentOrReplay() {
        val operation = BridgeHandoff.authenticationAction(BridgeAction.LINK, "https://kernelsu.org", "")
        val proof = BridgeAuthentication.proof("remembered key", "client", operation, "watch nonce", "phone nonce")
        for ((action, value, title, serverNonce) in listOf(
            listOf("LINK", "https://example.org", "", "phone nonce"),
            listOf("WEB_UI", "module", "Module", "phone nonce"),
            listOf("LINK", "https://kernelsu.org", "", "next phone nonce"),
        )) {
            val other = BridgeHandoff.authenticationAction(BridgeAction.valueOf(action), value, title)
            assertFalse(BridgeAuthentication.matches(proof, BridgeAuthentication.proof("remembered key", "client", other, "watch nonce", serverNonce)))
        }
    }

    @Test
    fun delayedPhoneConsentDoesNotConsumeWebUiClaimWindow() {
        val confirmedAt = 120_000L
        val session = WebUiSession("token", "module", "Module", "AA:BB", confirmedAt)
        assertFalse(session.claim("another phone", confirmedAt + 1))
        assertTrue(session.claim("aa:bb", confirmedAt + 29_999))
        assertFalse(session.claim("AA:BB", confirmedAt + 29_999))
        assertFalse(WebUiSession("token", "module", "Module", "AA:BB", confirmedAt).claim("AA:BB", confirmedAt + 30_000))
    }
}
