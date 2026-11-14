package org.bakasu.bakasu.ui.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Each visit owns its loading reason; unrelated shared refreshes cannot show a pull indicator. */
internal class WearTemplateChoiceLoading(private val scope: CoroutineScope, private val fetch: suspend () -> Unit) {
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set

    suspend fun enter() {
        try {
            fetch()
        } finally {
            loading = false
        }
    }

    fun refresh() {
        if (loading || refreshing) return
        refreshing = true
        scope.launch {
            try {
                fetch()
            } finally {
                refreshing = false
            }
        }
    }
}

@Composable
internal fun rememberWearTemplateLoading(route: WearRoute?, fetch: suspend () -> Unit): WearTemplateChoiceLoading {
    val scope = rememberCoroutineScope()
    val currentFetch by rememberUpdatedState(fetch)
    var lastRoute by remember { mutableStateOf<WearRoute?>(null) }
    var state by remember { mutableStateOf(WearTemplateChoiceLoading(scope) { currentFetch() }) }
    if (route != lastRoute) {
        lastRoute = route
        // Removing the route leaves its last state available throughout the outgoing animation.
        if (route != null) state = WearTemplateChoiceLoading(scope) { currentFetch() }
    }
    LaunchedEffect(route) { if (route != null) state.enter() }
    return state
}

/** Search retains the owning choice page and updates its one results destination. */
internal fun WearNavigator.submitTemplateChoiceSearch(input: WearRoute.AppProfileTemplateSearch, query: String) {
    if (top != input) return
    back()
    if (query.isBlank()) return
    val previous = backStack.dropLast(1).lastOrNull() as? WearRoute.AppProfileTemplateResults
    if (previous?.uid == input.uid && previous.packageName == input.packageName) {
        back()
        back()
    }
    open(WearRoute.AppProfileTemplateResults(input.uid, input.packageName, query.trim()))
}

/** A late save must not close a different screen or a different application's profile. */
internal fun WearNavigator.finishTemplateChoice(result: WearRoute.AppProfileTemplateResults) {
    if (top != result) return
    val parent = WearRoute.AppProfileMode(result.uid, result.packageName)
    if (parent !in backStack) return
    while (top != parent) back()
}
