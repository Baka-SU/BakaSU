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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.component.settings.AppBackButton
import org.bakasu.bakasu.ui.component.settings.SettingsBaseWidget
import org.bakasu.bakasu.ui.component.settings.SettingsJumpPageWidget
import org.bakasu.bakasu.ui.component.settings.SettingsSwitchWidget
import org.bakasu.bakasu.ui.component.settings.lazySegmentColumn
import org.bakasu.bakasu.ui.theme.KernelSUTheme
import org.bakasu.bakasu.ui.viewmodel.CompanionBridgeViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Phone-only watch link: pick a Bluetooth-paired watch, then grant it WebUI access. */
class WearBridgeSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KernelSUTheme { PhoneBridgeSettings { finish() } } }
    }
}

private const val TAG = "WearBridgeSetup"

private enum class BridgeSetting { Permissions, PairWatch, Watch, Access, Disconnect, AppSettings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneBridgeSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = koinViewModel<CompanionBridgeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
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
    // The watch chosen in the picker; access is granted as soon as the user confirms.
    var pendingWatch by remember { mutableStateOf<BridgePeer?>(null) }
    // Set while the paired-device list loads, so the picker never flashes an empty list.
    var pickerPending by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
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
    val allowTitle = stringResource(R.string.wear_bridge_allow_access_title)
    val allowMessage = stringResource(R.string.wear_bridge_allow_access_message)
    LaunchedEffect(pendingWatch) {
        val peer = pendingWatch ?: return@LaunchedEffect
        if (confirm.awaitConfirm(allowTitle, allowMessage) == ConfirmResult.Confirmed) {
            viewModel.link(peer.address, peer.name)
            registerWithSystem(peer)
        } else {
            viewModel.unassociate(peer.address)
        }
        pendingWatch = null
    }
    val picker = rememberCustomDialog { dismiss ->
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.wear_bridge_pair_watch)) },
            text = {
                if (peers.isEmpty()) {
                    Text(stringResource(R.string.wear_bridge_no_peers))
                } else {
                    LazyColumn {
                        items(peers, key = { it.address }) { peer ->
                            TextButton(
                                onClick = {
                                    dismiss()
                                    pendingWatch = peer
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(peer.name)
                                    Text(peer.address, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
    LaunchedEffect(pickerPending, busy) {
        if (!pickerPending || busy) return@LaunchedEffect
        pickerPending = false
        if (error == null) picker.show()
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
        add(BridgeSetting.Permissions)
        if (peer == null) add(BridgeSetting.PairWatch) else add(BridgeSetting.Watch)
        if (peer != null) {
            add(BridgeSetting.Access)
            add(BridgeSetting.Disconnect)
        }
        add(BridgeSetting.AppSettings)
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.wear_bridge)) }, navigationIcon = { AppBackButton(onClick = onBack) }) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 16.dp)) {
            item { Text(stringResource(R.string.wear_bridge_phone_help), Modifier.padding(16.dp)) }
            lazySegmentColumn(rows) { _, row ->
                when (row) {
                    BridgeSetting.Permissions -> SettingsJumpPageWidget(icon = Icons.Rounded.Bluetooth, title = stringResource(R.string.wear_bridge_permission), enabled = !busy, onClick = {
                        val requested = buildList {
                            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (requested.isEmpty()) viewModel.refresh() else permissions.launch(requested.toTypedArray())
                    })

                    BridgeSetting.PairWatch -> SettingsJumpPageWidget(icon = Icons.Rounded.Link, title = stringResource(R.string.wear_bridge_pair_watch), enabled = !busy, onClick = {
                        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            viewModel.failed(BridgeFailure.PERMISSION)
                        } else {
                            viewModel.loadPeers()
                            pickerPending = true
                        }
                    })

                    BridgeSetting.Watch -> SettingsBaseWidget(icon = Icons.Rounded.Bluetooth, title = peer?.name.orEmpty(), description = status)

                    BridgeSetting.Access -> SettingsSwitchWidget(
                        icon = Icons.Rounded.Bluetooth,
                        title = stringResource(R.string.wear_bridge_allow_access),
                        description = stringResource(R.string.wear_bridge_allow_access_summary),
                        checked = state.enabled,
                        enabled = !busy,
                        onCheckedChange = viewModel::setAccess,
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
