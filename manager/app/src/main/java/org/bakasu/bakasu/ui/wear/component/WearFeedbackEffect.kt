package org.bakasu.bakasu.ui.wear.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState

/** Shows pending operation feedback once, then consumes it without opening a dialog. */
@Composable
fun WearFeedbackEffect(message: String?, onShown: () -> Unit) {
    val toast = LocalWearToast.current
    val consume = rememberUpdatedState(onShown)
    LaunchedEffect(message, toast) {
        if (message != null) {
            toast.show(message)
            consume.value()
        }
    }
}
