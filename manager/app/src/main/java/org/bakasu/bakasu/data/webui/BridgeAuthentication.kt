package org.bakasu.bakasu.data.webui

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Mutual app authentication binds each secure Bluetooth connection to its fresh nonces and operation. */
internal object BridgeAuthentication {
    fun proof(secret: String, role: String, operation: String, clientNonce: String, serverNonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal("$role|$operation|$clientNonce|$serverNonce".toByteArray(Charsets.UTF_8)))
    }

    fun matches(actual: String, expected: String): Boolean = MessageDigest.isEqual(actual.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
}
