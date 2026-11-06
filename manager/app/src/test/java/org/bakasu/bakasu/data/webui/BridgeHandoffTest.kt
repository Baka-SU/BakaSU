package org.bakasu.bakasu.data.webui

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.BridgePeer
import org.bakasu.bakasu.domain.model.BridgeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeHandoffTest {
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
        val pending = PendingBridgeRequest(BridgeRequest("request", BridgePeer("phone", "Phone"), BridgeAction.TEMPLATES, "", "", true))
        pending.approval.complete(null)
        assertFalse(pending.approval.complete(true))
        assertNull(pending.approval.await())
    }

    @Test
    fun requestOnlyAllowsWebLinksModuleIdsAndFixedTemplateSource() {
        assertTrue(BridgeHandoff.valid(BridgeAction.LINK, "https://example.org/path?q=1"))
        assertTrue(BridgeHandoff.valid(BridgeAction.WEB_UI, "test_module"))
        assertTrue(BridgeHandoff.valid(BridgeAction.TEMPLATES, ""))
        listOf("file:///data/adb", "intent://example", "https:", "javascript:alert(1)").forEach {
            assertFalse(BridgeHandoff.valid(BridgeAction.LINK, it))
        }
        listOf("", "../module", "..", "module/other").forEach {
            assertFalse(BridgeHandoff.valid(BridgeAction.WEB_UI, it))
        }
        assertFalse(BridgeHandoff.valid(BridgeAction.TEMPLATES, "https://example.org"))
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
