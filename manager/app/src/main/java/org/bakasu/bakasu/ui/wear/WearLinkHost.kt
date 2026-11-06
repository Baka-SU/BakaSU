package org.bakasu.bakasu.ui.wear

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.network.WearLinkFailure
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.ui.viewmodel.WearLinkEvent
import org.bakasu.bakasu.ui.viewmodel.WearLinkResult
import org.bakasu.bakasu.ui.viewmodel.WearLinkViewModel
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearLoadingIndicator
import org.bakasu.bakasu.ui.wear.component.WearPhoneTransferDialog

/** Opens local pages and hosts the shared permission and phone-transfer feedback. */
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
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val permissionRequest by viewModel.permissionRequest.collectAsStateWithLifecycle()
    var launchedPermission by rememberSaveable { mutableStateOf<String?>(null) }
    val bluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        launchedPermission?.let { viewModel.answerPermission(it, granted) }
        launchedPermission = null
    }
    LaunchedEffect(permissionRequest, launchedPermission) {
        val request = permissionRequest ?: return@LaunchedEffect
        if (launchedPermission == null && Build.VERSION.SDK_INT >= 31) {
            launchedPermission = request
            bluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.cancel() } }
    BackHandler(loading || permissionRequest != null) { viewModel.cancel() }
    WearPhoneTransferDialog(transfer, onCancel = viewModel::cancel, onDismiss = viewModel::dismissTransfer)
    if (loading && transfer == null && permissionRequest == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { WearLoadingIndicator() }
    }
    val failed = result as? WearLinkResult.Failed
    val failureText = stringResource(
        when (failed?.reason) {
            WearLinkFailure.WEBVIEW_UNAVAILABLE -> R.string.wear_link_open_failed
            WearLinkFailure.BROWSER_FAILED -> R.string.wear_browser_open_failed
            else -> R.string.wear_link_open_failed
        },
    )
    WearErrorDialog(failureText.takeIf { failed != null }, viewModel::dismissResult)
}

@Composable
internal fun wearBridgeFailureText(failure: BridgeFailure): String = stringResource(
    if (failure == BridgeFailure.CONNECTION) R.string.wear_phone_connection_failed else failure.resId,
)
