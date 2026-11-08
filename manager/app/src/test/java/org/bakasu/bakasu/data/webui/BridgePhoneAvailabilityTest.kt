package org.bakasu.bakasu.data.webui

import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.model.BridgePeer
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgePhoneAvailabilityTest {
    private val phone = BridgePhoneCandidate(BridgePeer("phone", "Phone"), true, true)
    private val headset = BridgePhoneCandidate(BridgePeer("headset", "Headset"), false, true)

    @Test
    fun pairedAccessoriesDoNotCountAsAPhone() {
        val error = runCatching { selectBridgePhones(listOf(headset), null) }.exceptionOrNull() as BridgeException
        assertEquals(BridgeFailure.NOT_PAIRED, error.reason)
    }

    @Test
    fun disconnectedPhonesAreNotProbedEvenWithConnectedAccessories() {
        val error = runCatching { selectBridgePhones(listOf(phone.copy(connected = false), headset), null) }.exceptionOrNull() as BridgeException
        assertEquals(BridgeFailure.NOT_CONNECTED, error.reason)
    }

    @Test
    fun unknownConnectionsRemainEligibleAndRememberedPhoneComesFirst() {
        val remembered = BridgePhoneCandidate(BridgePeer("remembered", "Phone"), false, null)
        assertEquals(listOf(remembered, phone), selectBridgePhones(listOf(phone, headset, remembered), "REMEMBERED"))
        val unknown = phone.copy(phone = null, connected = null)
        assertEquals(listOf(unknown), selectBridgePhones(listOf(unknown), null))
    }
}
