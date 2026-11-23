package org.bakasu.bakasu.ui.wear.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsEvent

/** Download entry points share installation events and persistent verification failures. */
@Composable
internal fun WearDownloadsFeedback(events: Flow<WearDownloadsEvent>, onInstall: (String) -> Unit) {
    var failed by remember { mutableStateOf(false) }
    val install by rememberUpdatedState(onInstall)
    WearErrorDialog(stringResource(R.string.wear_download_verify_failed).takeIf { failed }) { failed = false }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is WearDownloadsEvent.Install -> install(event.uri)
                WearDownloadsEvent.VerificationFailed -> failed = true
            }
        }
    }
}
