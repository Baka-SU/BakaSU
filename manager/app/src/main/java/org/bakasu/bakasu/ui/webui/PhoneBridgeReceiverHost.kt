package org.bakasu.bakasu.ui.webui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.component.ConfirmResult
import org.bakasu.bakasu.ui.component.rememberConfirmDialog
import org.bakasu.bakasu.ui.viewmodel.CompanionBridgeViewModel
import org.koin.compose.viewmodel.koinViewModel

/** First-use opt-in and foreground restoration do not require selecting a watch in settings. */
@Composable
fun PhoneBridgeReceiverHost() {
    val context = LocalContext.current
    val viewModel = koinViewModel<CompanionBridgeViewModel>()
    val confirm = rememberConfirmDialog()
    val title = stringResource(R.string.wear_bridge_receiver)
    val message = stringResource(R.string.wear_bridge_phone_help)
    val error by viewModel.error.collectAsStateWithLifecycle()
    val errorText = error?.let { stringResource(it.resId) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val allowed = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        viewModel.setAccess(allowed)
    }
    LaunchedEffect(Unit) {
        if (!viewModel.needsReceiverSetup) return@LaunchedEffect
        if (confirm.awaitConfirm(title, message) == ConfirmResult.Confirmed) {
            val required = buildList {
                if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (required.isEmpty()) viewModel.setAccess(true) else permissions.launch(required.toTypedArray())
        } else {
            viewModel.setAccess(false)
        }
    }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            confirm.awaitConfirm(title, errorText)
            viewModel.clearError()
        }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.resumeReceiver()
        onPauseOrDispose { }
    }
}
