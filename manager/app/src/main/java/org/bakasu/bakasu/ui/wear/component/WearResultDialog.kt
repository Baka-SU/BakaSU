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

/** An operation result whose [brief] message fits the curved confirmation text. */
data class WearResult(val message: String, val success: Boolean, val brief: Boolean = false)

/** Official Wear result feedback: curved text for brief messages, body text for longer ones. */
@Composable
fun WearResultDialog(result: WearResult?, onDismiss: () -> Unit) {
    // Keep the result visible during the exit animation after the caller clears it.
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
