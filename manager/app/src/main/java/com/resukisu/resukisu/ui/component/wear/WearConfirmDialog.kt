package com.resukisu.resukisu.ui.component.wear

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.ui.component.DialogHandle
import com.resukisu.resukisu.ui.component.rememberCustomDialog

@Composable
fun rememberWearConfirmDialog(title: String, message: String, onConfirm: () -> Unit): DialogHandle =
    rememberCustomDialog { dismiss ->
        AlertDialog(visible = true, onDismissRequest = dismiss,
            title = { Text(title) }, text = { Text(message) },
            confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { dismiss(); onConfirm() }) },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = dismiss) })
    }
