package org.bakasu.bakasu.ui.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Job

/** One load per repository visit or source change; child pages and pause/resume keep the visit. */
@Composable
internal fun rememberWearRepositoryLoading(isOpen: Boolean, repositoryUrl: String?, onRefresh: () -> Job): Boolean {
    // Set before the first page composition, including visits with a cached non-empty list.
    val loading = remember(isOpen, repositoryUrl) { mutableStateOf(isOpen && repositoryUrl != null) }
    LaunchedEffect(isOpen, repositoryUrl) {
        if (isOpen && repositoryUrl != null) {
            val request = onRefresh()
            try {
                request.join()
            } finally {
                request.cancel()
                loading.value = false
            }
        }
    }
    return loading.value
}
