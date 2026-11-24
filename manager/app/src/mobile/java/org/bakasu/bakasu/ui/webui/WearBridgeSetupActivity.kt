package org.bakasu.bakasu.ui.webui

import android.Manifest
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.model.BridgePeer
import org.bakasu.bakasu.ui.component.ConfirmResult
import org.bakasu.bakasu.ui.component.rememberConfirmDialog
import org.bakasu.bakasu.ui.component.settings.AppBackButton
import org.bakasu.bakasu.ui.component.settings.SettingsBaseWidget
import org.bakasu.bakasu.ui.component.settings.SettingsJumpPageWidget
import org.bakasu.bakasu.ui.component.settings.SettingsSwitchWidget
import org.bakasu.bakasu.ui.component.settings.lazySegmentColumn
import org.bakasu.bakasu.ui.theme.KernelSUTheme
import org.bakasu.bakasu.ui.viewmodel.CompanionBridgeViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Phone receiver, remembered consent and optional system background reconnection. */
class WearBridgeSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KernelSUTheme { PhoneBridgeSettings { finish() } } }
    }
}

private const val TAG = "WearBridgeSetup"

private enum class BridgeSetting { Permissions, PairWatch, Watch, Access, Remember, Disconnect, AppSettings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneBridgeSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = koinViewModel<CompanionBridgeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val failureTitle = stringResource(R.string.operation_failed)
    val failureText = error?.let { stringResource(it.resId) }
    LaunchedEffect(failureText) {
        if (failureText != null) {
            confirm.awaitConfirm(failureTitle, failureText)
            viewModel.clearError()
        }
    }
    LaunchedEffect(viewModel) { viewModel.initialize() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (Build.VERSION.SDK_INT < 31 || granted[Manifest.permission.BLUETOOTH_CONNECT] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.setAccess(true)
        } else {
            viewModel.failed(BridgeFailure.PERMISSION)
        }
    }
    fun enableReceiver() {
        val requested = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (requested.isEmpty()) viewModel.setAccess(true) else permissions.launch(requested.toTypedArray())
    }
    // A single known device only needs the system confirmation dialog, not the discovery list.
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    fun registerWithSystem(peer: BridgePeer) {
        if (Build.VERSION.SDK_INT < 33) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) return
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(peer.address).build())
            .setSingleDevice(true)
            .build()
        runCatching {
            manager.associate(
                request,
                ContextCompat.getMainExecutor(context),
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(sender: IntentSender) {
                        runCatching { chooser.launch(IntentSenderRequest.Builder(sender).build()) }
                    }
                    override fun onAssociationCreated(info: AssociationInfo) {
                        info.deviceMacAddress?.toString()?.let(viewModel::observePresence)
                    }
                    override fun onFailure(message: CharSequence?) = Unit
                },
            )
        }.onFailure { Log.i(TAG, "Companion association skipped", it) }
    }
    val removeTitle = stringResource(R.string.wear_bridge_disconnect)
    val removeMessage = stringResource(R.string.wear_bridge_disconnect_message)
    val peer = state.peer
    val status = when {
        state.connected -> stringResource(R.string.wear_bridge_active_connection, peer?.name.orEmpty())
        state.enabled -> stringResource(R.string.wear_bridge_waiting)
        else -> stringResource(R.string.wear_bridge_access_off)
    }
    val rows = buildList {
        add(BridgeSetting.Access)
        add(BridgeSetting.Permissions)
        if (peer != null) {
            add(BridgeSetting.Watch)
            add(BridgeSetting.Remember)
            add(BridgeSetting.Disconnect)
            add(BridgeSetting.PairWatch)
        }
        add(BridgeSetting.AppSettings)
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.wear_bridge)) }, navigationIcon = { AppBackButton(onClick = onBack) }) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 16.dp)) {
            item { Text(stringResource(R.string.wear_bridge_phone_help), Modifier.padding(16.dp)) }
            lazySegmentColumn(rows) { _, row ->
                when (row) {
                    BridgeSetting.Permissions -> SettingsJumpPageWidget(icon = Icons.Rounded.Bluetooth, title = stringResource(R.string.wear_bridge_permission), enabled = !busy, onClick = { enableReceiver() })

                    BridgeSetting.PairWatch -> SettingsJumpPageWidget(icon = Icons.Rounded.Link, title = stringResource(R.string.wear_bridge_background_restore), enabled = !busy, onClick = {
                        peer?.let(::registerWithSystem)
                    })

                    BridgeSetting.Watch -> SettingsBaseWidget(icon = Icons.Rounded.Bluetooth, title = peer?.name.orEmpty(), description = status)

                    BridgeSetting.Access -> SettingsSwitchWidget(
                        icon = Icons.Rounded.Bluetooth,
                        title = stringResource(R.string.wear_bridge_receiver),
                        description = stringResource(R.string.wear_bridge_receiver_summary),
                        checked = state.enabled,
                        enabled = !busy,
                        onCheckedChange = { if (it) enableReceiver() else viewModel.setAccess(false) },
                    )

                    BridgeSetting.Remember -> SettingsSwitchWidget(
                        title = stringResource(R.string.wear_bridge_remember),
                        checked = state.remembered,
                        enabled = !busy,
                        onCheckedChange = viewModel::setRemembered,
                    )

                    BridgeSetting.Disconnect -> SettingsJumpPageWidget(icon = Icons.Rounded.LinkOff, title = removeTitle, enabled = !busy, onClick = {
                        scope.launch { if (confirm.awaitConfirm(removeTitle, removeMessage) == ConfirmResult.Confirmed) viewModel.disconnect() }
                    })

                    BridgeSetting.AppSettings -> SettingsJumpPageWidget(icon = Icons.Rounded.Settings, title = stringResource(R.string.wear_bridge_app_settings), onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                    })
                }
            }
            item { Text(stringResource(R.string.wear_bridge_background_help), Modifier.padding(16.dp)) }
        }
    }
}
