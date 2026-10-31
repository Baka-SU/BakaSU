package org.bakasu.bakasu.ui.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.network.WearLinkFailure
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.viewmodel.WearLinkEvent
import org.bakasu.bakasu.ui.viewmodel.WearLinkResult
import org.bakasu.bakasu.ui.viewmodel.WearLinkViewModel
import org.bakasu.bakasu.ui.wear.component.WearDialogIcon
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog

/**
 * Shows what [viewModel] opens: a link on the watch goes to [onWebView], a module WebUI starts
 * [WearWebUIActivity], and Back cancels while it waits. The result shows as the official
 * open-on-phone confirmation or the failure confirmation, whose curved text is short; a disconnected
 * phone shows the connection failure icon.
 */
@Composable
internal fun WearLinkHost(viewModel: WearLinkViewModel, onWebView: (String) -> Unit) {
    val context = LocalContext.current
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WearLinkEvent.WebView -> onWebView(event.url)

                is WearLinkEvent.WebUi -> runCatching { context.startActivity(WearWebUIActivity.intent(context, event.moduleId)) }
                    .onFailure { viewModel.fail(null) }
            }
        }
    }
    BackHandler(loading) { viewModel.cancel() }
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }

    val openText = OpenOnPhoneDialogDefaults.text
    val openStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = result == WearLinkResult.OpenedOnPhone,
        onDismissRequest = viewModel::dismissResult,
        curvedText = { openOnPhoneDialogCurvedText(text = openText, style = openStyle) },
    )
    // The overlay keeps the last failure while it animates out after the result is cleared.
    val failed = result as? WearLinkResult.Failed
    WearErrorDialog(failed?.bridgeFailure?.let { wearBridgeFailureText(it) }, viewModel::dismissResult)
    val phoneNotice = rememberCustomDialog { dismiss ->
        val close = {
            dismiss()
            viewModel.dismissResult()
        }
        AlertDialog(
            visible = true,
            onDismissRequest = close,
            icon = { WearDialogIcon(Icons.Rounded.Info) },
            title = { Text(stringResource(R.string.wear_bridge)) },
            text = { Text(stringResource(R.string.wear_bridge_notification)) },
            edgeButton = { AlertDialogDefaults.EdgeButton(onClick = close) },
        )
    }
    LaunchedEffect(result) { if (result == WearLinkResult.PhoneNotification) phoneNotice.show() else phoneNotice.hide() }
    var shown by remember { mutableStateOf(failed) }
    if (failed != null) shown = failed
    val failureText = stringResource(
        when (shown?.reason) {
            WearLinkFailure.PHONE_UNAVAILABLE -> R.string.wear_phone_disconnected
            WearLinkFailure.PHONE_APP_MISSING -> R.string.wear_phone_app_missing
            WearLinkFailure.PHONE_FAILED -> R.string.wear_phone_open_failed
            WearLinkFailure.BROWSER_FAILED -> R.string.wear_browser_open_failed
            else -> if (shown?.onPhone == true) R.string.wear_phone_open_failed else R.string.wear_link_open_failed
        },
    )
    val failureStyle = ConfirmationDialogDefaults.curvedTextStyle
    FailureConfirmationDialog(
        visible = failed != null && failed.bridgeFailure == null,
        onDismissRequest = viewModel::dismissResult,
        curvedText = { confirmationDialogCurvedText(text = failureText, style = failureStyle) },
    ) {
        if (shown?.reason == WearLinkFailure.PHONE_UNAVAILABLE) {
            ConfirmationDialogDefaults.ConnectionFailureIcon()
        } else {
            ConfirmationDialogDefaults.FailureIcon()
        }
    }
}

@Composable
internal fun wearBridgeFailureText(failure: BridgeFailure): String = stringResource(
    if (failure == BridgeFailure.CONNECTION) R.string.wear_phone_connection_failed else failure.resId,
)
