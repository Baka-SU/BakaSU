package org.bakasu.bakasu.ui.wear.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText

/**
 * The outcome of an operation the user started, shown once by [WearResultDialog]. A [brief] message,
 * such as "Saved", fits the curved text of the official success and failure confirmations.
 */
data class WearResult(val message: String, val success: Boolean, val brief: Boolean = false)

/**
 * A one-shot operation result as the official Wear confirmation overlay with the success or failure
 * icon and colors: a brief message as curved text, a longer one as body text so it stays readable.
 * It closes by itself or when dismissed, and [onDismiss] clears the result.
 */
@Composable
fun WearResultDialog(result: WearResult?, onDismiss: () -> Unit) {
    // The overlay keeps the last result while it animates out after [result] is cleared.
    var shown by remember { mutableStateOf(result) }
    if (result != null) shown = result
    val success = shown?.success != false
    val brief = shown?.brief == true
    val message = shown?.message.orEmpty()
    val curvedStyle = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialog(
        visible = result != null && brief && success,
        onDismissRequest = onDismiss,
        curvedText = { confirmationDialogCurvedText(message, curvedStyle) },
    )
    FailureConfirmationDialog(
        visible = result != null && brief && !success,
        onDismissRequest = onDismiss,
        curvedText = { confirmationDialogCurvedText(message, curvedStyle) },
    )
    ConfirmationDialog(
        visible = result != null && !brief,
        onDismissRequest = onDismiss,
        text = { Text(message) },
        colors = if (success) ConfirmationDialogDefaults.successColors() else ConfirmationDialogDefaults.failureColors(),
    ) {
        if (success) ConfirmationDialogDefaults.SuccessIcon() else ConfirmationDialogDefaults.FailureIcon()
    }
}
