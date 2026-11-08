package org.bakasu.bakasu.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WearLinkRepositoryTest {
    @Test
    fun explicitModesNeverCrossDevicesForEitherEntry() = runBlocking {
        for (phoneFirst in listOf(false, true)) {
            val phoneAttempts = mutableListOf<WearLinkTarget>()
            runCatching {
                resolveWearLink(wearLinkTargets("phone", phoneFirst)) {
                    phoneAttempts += it
                    throw BridgeException(BridgeFailure.CONNECTION)
                }
            }
            assertEquals(listOf(WearLinkTarget.PHONE), phoneAttempts)
            val localAttempts = mutableListOf<WearLinkTarget>()
            runCatching {
                resolveWearLink(wearLinkTargets("webview", phoneFirst)) {
                    localAttempts += it
                    throw WearLinkException(WearLinkFailure.BROWSER_FAILED)
                }
            }
            assertEquals(listOf(WearLinkTarget.BROWSER, WearLinkTarget.WEBVIEW), localAttempts)
        }
    }

    @Test
    fun successfulPhoneStopsAutomaticFallback() = runBlocking {
        val attempts = mutableListOf<WearLinkTarget>()
        val result = resolveWearLink(wearLinkTargets("auto", true)) { attempts += it }
        assertEquals(WearLinkTarget.PHONE, result.target)
        assertEquals(listOf(WearLinkTarget.PHONE), attempts)
    }

    @Test
    fun asynchronousWebViewFailureContinuesWithoutRepeatingPhone() = runBlocking {
        val attempts = mutableListOf<WearLinkTarget>()
        val first = resolveWearLink(wearLinkTargets("auto", true)) {
            attempts += it
            if (it == WearLinkTarget.PHONE) throw BridgeException(BridgeFailure.NOT_PAIRED)
        }
        assertEquals(WearLinkTarget.WEBVIEW, first.target)
        assertEquals(BridgeFailure.NOT_PAIRED, first.phoneFailure)
        val failure = runCatching {
            resolveWearLink(first.remaining, first.phoneFailure) {
                attempts += it
                throw WearLinkException(WearLinkFailure.BROWSER_FAILED)
            }
        }.exceptionOrNull() as WearLinkException
        assertEquals(BridgeFailure.NOT_PAIRED, failure.phoneFailure)
        assertEquals(listOf(WearLinkTarget.PHONE, WearLinkTarget.WEBVIEW, WearLinkTarget.BROWSER), attempts)
    }

    @Test
    fun browserFailureContinuesToWebViewThenPhoneInLocalAutomaticMode() = runBlocking {
        val first = resolveWearLink(wearLinkTargets("auto", false)) {
            if (it == WearLinkTarget.BROWSER) throw WearLinkException(WearLinkFailure.BROWSER_FAILED)
        }
        assertEquals(WearLinkTarget.WEBVIEW, first.target)
        val last = resolveWearLink(first.remaining) { assertEquals(WearLinkTarget.PHONE, it) }
        assertEquals(WearLinkTarget.PHONE, last.target)
    }

    @Test
    fun cancellationRejectionPermissionDenialAndUnknownResultsStopFallback() = runBlocking {
        for (failure in listOf(
            CancellationException("Cancelled"),
            BridgeException(BridgeFailure.REJECTED),
            BridgeException(BridgeFailure.PERMISSION),
            BridgeException(BridgeFailure.RESULT_UNKNOWN),
        )) {
            val attempts = mutableListOf<WearLinkTarget>()
            val error = runCatching {
                resolveWearLink(wearLinkTargets("auto", true)) {
                    attempts += it
                    throw failure
                }
            }.exceptionOrNull()
            assertSame(failure, error)
            assertEquals(listOf(WearLinkTarget.PHONE), attempts)
        }
    }
}
