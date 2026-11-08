package org.bakasu.bakasu.ui.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** The visit survives the operations panel, but a new visit waits for its own local template read. */
@Composable
internal fun rememberWearTemplateChoiceLoading(route: WearRoute.AppProfileTemplateChoice?, fetch: suspend () -> Unit): Boolean {
    val loading = remember(route) { mutableStateOf(route != null) }
    LaunchedEffect(route) {
        if (route != null) {
            try {
                fetch()
            } finally {
                loading.value = false
            }
        }
    }
    return loading.value
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
