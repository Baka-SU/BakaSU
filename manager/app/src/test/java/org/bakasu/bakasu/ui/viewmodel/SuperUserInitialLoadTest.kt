package org.bakasu.bakasu.ui.viewmodel

import kotlinx.coroutines.Job
import org.bakasu.bakasu.domain.model.SuperUserLoadFailure
import org.bakasu.bakasu.domain.model.SuperUserState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperUserInitialLoadTest {
    @Test
    fun restoredProcessAndCancelledOrCompletedInitialLoadsCanStartAgain() {
        val initial = SuperUserState()
        assertTrue(initial.needsInitialLoad(null))
        assertTrue(initial.needsInitialLoad(Job().apply { cancel() }))
        assertTrue(initial.needsInitialLoad(Job().apply { complete() }))
    }

    @Test
    fun enteringAndResumingDoNotDuplicateAnActiveRequest() {
        val job = Job()
        try {
            assertFalse(SuperUserState().needsInitialLoad(job))
            assertFalse(SuperUserState(refreshing = true).needsInitialLoad(null))
            assertFalse(SuperUserState(loadFailure = SuperUserLoadFailure.ROOT_ACCESS_REQUIRED).needsInitialLoad(job))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun successfulEmptyListsStayLoadedAndFailuresCanBeRetried() {
        val loaded = SuperUserState(hasLoaded = true)
        assertFalse(loaded.needsInitialLoad(null))
        assertFalse(loaded.needsInitialLoad(Job().apply { complete() }))
        SuperUserLoadFailure.entries.forEach { failure ->
            assertTrue(loaded.copy(loadFailure = failure).needsInitialLoad(null))
        }
    }
}
