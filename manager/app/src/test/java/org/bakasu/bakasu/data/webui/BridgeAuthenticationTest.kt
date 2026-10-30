package org.bakasu.bakasu.data.webui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BridgeAuthenticationTest {
    @Test
    fun acceptsMatchingAppCredentialAndConnection() {
        val proof = BridgeAuthentication.proof("app credential", "server", "rpc", "client nonce", "server nonce")
        assertEquals("5TaWvzMIswjJ3von1PNz1ONzmQil2qRDw8Hmc9Y4qVI=", proof)
    }

    @Test
    fun rejectsAnotherAppCredential() {
        val proof = BridgeAuthentication.proof("another app", "server", "rpc", "client nonce", "server nonce")
        assertFalse(BridgeAuthentication.matches(proof, BridgeAuthentication.proof("paired app", "server", "rpc", "client nonce", "server nonce")))
    }

    @Test
    fun rejectsReflectedProofAndReplayOnAnotherConnectionOrOperation() {
        val proof = BridgeAuthentication.proof("paired app", "server", "rpc", "client nonce", "server nonce")
        for ((role, operation, client, server) in listOf(
            listOf("client", "rpc", "client nonce", "server nonce"),
            listOf("server", "claim", "client nonce", "server nonce"),
            listOf("server", "rpc", "new client nonce", "server nonce"),
            listOf("server", "rpc", "client nonce", "new server nonce"),
        )) {
            assertFalse(BridgeAuthentication.matches(proof, BridgeAuthentication.proof("paired app", role, operation, client, server)))
        }
    }
}
