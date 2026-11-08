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
