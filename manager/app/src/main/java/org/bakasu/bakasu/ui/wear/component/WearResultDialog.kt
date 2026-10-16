package org.bakasu.bakasu.ui.wear.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.Text

/** The outcome of an operation the user started, shown once by [WearResultDialog]. */
data class WearResult(val message: String, val success: Boolean)

/**
 * A one-shot operation result as the official Wear confirmation overlay: the success or failure
 * icon and colors with the full message as text, so longer results stay readable. It closes by
 * itself or when dismissed, and [onDismiss] clears the result.
 */
@Composable
fun WearResultDialog(result: WearResult?, onDismiss: () -> Unit) {
    // The overlay keeps the last result while it animates out after [result] is cleared.
    var shown by remember { mutableStateOf(result) }
    if (result != null) shown = result
    val success = shown?.success != false
    ConfirmationDialog(
        visible = result != null,
        onDismissRequest = onDismiss,
        text = { Text(shown?.message.orEmpty()) },
        colors = if (success) ConfirmationDialogDefaults.successColors() else ConfirmationDialogDefaults.failureColors(),
    ) {
        if (success) ConfirmationDialogDefaults.SuccessIcon() else ConfirmationDialogDefaults.FailureIcon()
    }
}
