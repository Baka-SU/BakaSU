package org.bakasu.bakasu.ui.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.RepositoryFailure

/** A menu request must finish successfully before it can open the repository. */
internal class WearModuleRepoEntry(
    private val scope: CoroutineScope,
    private val refresh: suspend () -> RepositoryFailure?,
) {
    var isLoading by mutableStateOf(false)
        private set
    var failure by mutableStateOf<RepositoryFailure?>(null)
        private set
    private var request: Job? = null

    fun open(onLoaded: () -> Unit) {
        if (isLoading) return
        failure = null
        isLoading = true
        request = scope.launch {
            val result = try {
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                RepositoryFailure.from(error)
            }
            // Some requests finish after cancellation; they must not change a reopened menu.
            ensureActive()
            isLoading = false
            failure = result
            if (result == null) onLoaded()
        }
    }

    fun cancel() {
        request?.cancel()
        request = null
        isLoading = false
        failure = null
    }
}
