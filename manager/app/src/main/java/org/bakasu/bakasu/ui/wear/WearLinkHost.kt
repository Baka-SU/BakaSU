package org.bakasu.bakasu.ui.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.network.WearLinkFailure
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.ui.viewmodel.WearLinkEvent
import org.bakasu.bakasu.ui.viewmodel.WearLinkResult
import org.bakasu.bakasu.ui.viewmodel.WearLinkViewModel
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog

/**
 * Shows what [viewModel] opens: a link on the watch goes to [onWebView], a module WebUI starts
 * [WearWebUIActivity], and Back cancels while it waits. The result shows as the official
 * open-on-phone confirmation or the failure confirmation, whose curved text is short; a failed phone connection uses the standard connection failure dialog.
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
    // The watch has no connection settings page anymore, so ask for Bluetooth where it is needed.
    val bluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.dismissResult() }
    val bluetoothFailure = (result as? WearLinkResult.Failed)?.bridgeFailure
    LaunchedEffect(bluetoothFailure) {
        if (bluetoothFailure == BridgeFailure.PERMISSION && Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    BackHandler(loading) { viewModel.cancel() }
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }

    val failed = result as? WearLinkResult.Failed
    // Keep the actual last outcome while its standard dialog animates out.
    var shown by remember { mutableStateOf(result) }
    if (result != null) shown = result
    val shownFailure = shown as? WearLinkResult.Failed
    // These outcomes come from the actual phone dispatch or RFCOMM connection attempt.
    val disconnected = failed?.reason == WearLinkFailure.PHONE_UNAVAILABLE ||
        failed?.bridgeFailure == BridgeFailure.CONNECTION || failed?.bridgeFailure == BridgeFailure.BLUETOOTH_DISABLED
    val phoneText = if (shown == WearLinkResult.PhoneNotification) stringResource(R.string.wear_check_phone) else OpenOnPhoneDialogDefaults.text
    val phoneTextStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = result == WearLinkResult.OpenedOnPhone || result == WearLinkResult.PhoneNotification,
        onDismissRequest = viewModel::dismissResult,
        curvedText = { openOnPhoneDialogCurvedText(phoneText, phoneTextStyle) },
    )
    val connectionText = stringResource(
        if (shownFailure?.bridgeFailure == BridgeFailure.CONNECTION) R.string.wear_phone_connection_failed else R.string.wear_phone_disconnected,
    )
    val connectionTextStyle = ConfirmationDialogDefaults.curvedTextStyle
    FailureConfirmationDialog(
        visible = disconnected,
        onDismissRequest = viewModel::dismissResult,
        curvedText = { confirmationDialogCurvedText(connectionText, connectionTextStyle) },
        content = { ConfirmationDialogDefaults.ConnectionFailureIcon() },
    )
    // Permission, authorization and application errors keep their actionable explanation.
    WearErrorDialog(failed?.bridgeFailure?.takeUnless { disconnected }?.let { wearBridgeFailureText(it) }, viewModel::dismissResult)
    val failureText = stringResource(
        when (shownFailure?.reason) {
            WearLinkFailure.PHONE_UNAVAILABLE -> R.string.wear_phone_disconnected
            WearLinkFailure.PHONE_APP_MISSING -> R.string.wear_phone_app_missing
            WearLinkFailure.PHONE_FAILED -> R.string.wear_phone_open_failed
            WearLinkFailure.BROWSER_FAILED -> R.string.wear_browser_open_failed
            else -> if (shownFailure?.onPhone == true) R.string.wear_phone_open_failed else R.string.wear_link_open_failed
        },
    )
    WearErrorDialog(failureText.takeIf { failed != null && !disconnected && failed.bridgeFailure == null }, viewModel::dismissResult)
}

@Composable
internal fun wearBridgeFailureText(failure: BridgeFailure): String = stringResource(
    if (failure == BridgeFailure.CONNECTION) R.string.wear_phone_connection_failed else failure.resId,
)
