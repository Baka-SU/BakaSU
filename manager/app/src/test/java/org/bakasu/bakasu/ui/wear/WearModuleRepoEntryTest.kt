package org.bakasu.bakasu.ui.wear

import java.net.UnknownHostException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearModuleRepoEntryTest {
    @Test
    fun waitsForTheRequestAndIgnoresRepeatedTaps() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val response = CompletableDeferred<RepositoryFailure?>()
            var requests = 0
            var opens = 0
            val entry = WearModuleRepoEntry(scope) {
                requests++
                response.await()
            }
            repeat(3) { entry.open { opens++ } }
            assertTrue(entry.isLoading)
            assertEquals(1, requests)
            assertEquals(0, opens)
            response.complete(null)
            assertFalse(entry.isLoading)
            assertEquals(1, opens)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun everyOpeningChecksTheNetworkEvenAfterASuccessfulLoad() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val offline = RepositoryFailure(RepositoryError.OFFLINE)
            var result: RepositoryFailure? = offline
            var requests = 0
            var opens = 0
            val entry = WearModuleRepoEntry(scope) {
                requests++
                result
            }
            // The first failure has no cache. Dismissing it never opens the repository.
            entry.open { opens++ }
            assertEquals(offline, entry.failure)
            entry.cancel()
            assertNull(entry.failure)
            assertEquals(0, opens)

            result = null
            entry.open { opens++ }
            assertEquals(1, opens)

            // A previous success must not bypass the next request or its failure.
            result = offline
            entry.open { opens++ }
            assertEquals(offline, entry.failure)
            assertEquals(1, opens)
            entry.cancel()
            assertEquals(1, opens)

            entry.open { opens++ }
            result = null
            entry.open { opens++ }
            assertEquals(2, opens)
            assertEquals(5, requests)
            assertNull(entry.failure)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun leavingTheMenuIgnoresLateSuccessAndLateFailureAfterReopening() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val responses = mutableListOf<Continuation<RepositoryFailure?>>()
            var opens = 0
            val entry = WearModuleRepoEntry(scope) { suspendCoroutine { responses += it } }
            entry.open { opens++ }
            entry.cancel()
            entry.open { opens++ }
            responses[0].resume(null)
            assertEquals(0, opens)
            assertTrue(entry.isLoading)
            entry.cancel()
            entry.open { opens++ }
            responses[1].resume(RepositoryFailure(RepositoryError.OFFLINE))
            assertNull(entry.failure)
            assertTrue(entry.isLoading)
            responses[2].resume(null)
            assertEquals(1, opens)
            assertFalse(entry.isLoading)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun requestExceptionShowsFailureAndAllowsRetry() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            var offline = true
            var opens = 0
            val entry = WearModuleRepoEntry(scope) {
                if (offline) throw UnknownHostException()
                null
            }
            entry.open { opens++ }
            assertFalse(entry.isLoading)
            assertEquals(0, opens)
            assertEquals(RepositoryError.NETWORK, entry.failure?.reason)
            offline = false
            entry.open { opens++ }
            assertEquals(1, opens)
            assertNull(entry.failure)
        } finally {
            scope.cancel()
        }
    }
}
