package org.bakasu.bakasu.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WearLinkRepositoryTest {
    @Test
    fun phoneSuccessDoesNotOpenAnyLocalHandler() = runBlocking {
        val attempts = mutableListOf<String>()
        val target = resolvePhoneFirstLink({ attempts += "phone" }, { error("Must not open locally") })
        assertEquals(WearLinkTarget.PHONE, target)
        assertEquals(listOf("phone"), attempts)
    }

    @Test
    fun definitePhoneFailureContinuesToTheAvailableLocalHandlerOnce() = runBlocking {
        for (local in listOf(WearLinkTarget.WEBVIEW, WearLinkTarget.BROWSER)) {
            val attempts = mutableListOf<String>()
            val target = resolvePhoneFirstLink(
                {
                    attempts += "phone"
                    throw BridgeException(BridgeFailure.CONNECTION)
                },
                {
                    attempts += "local"
                    local
                },
            )
            assertEquals(local, target)
            assertEquals(listOf("phone", "local"), attempts)
        }
    }

    @Test
    fun cancellationRejectionPermissionDenialAndUnknownResultsStopFallback() = runBlocking {
        for (failure in listOf(
            CancellationException("Cancelled"),
            BridgeException(BridgeFailure.REJECTED),
            BridgeException(BridgeFailure.PERMISSION),
            BridgeException(BridgeFailure.RESULT_UNKNOWN),
        )) {
            val error = runCatching { resolvePhoneFirstLink({ throw failure }, { error("Must not open locally") }) }.exceptionOrNull()
            assertSame(failure, error)
        }
    }

    @Test
    fun localFailureAfterPhoneFailureIsReportedWithoutRepeatingThePhone() = runBlocking {
        var phoneAttempts = 0
        val error = runCatching {
            resolvePhoneFirstLink(
                {
                    phoneAttempts++
                    throw BridgeException(BridgeFailure.NOT_PAIRED)
                },
                { throw WearLinkException(WearLinkFailure.BROWSER_FAILED) },
            )
        }.exceptionOrNull()
        assertTrue(error is WearLinkException)
        assertEquals(1, phoneAttempts)
    }
}
