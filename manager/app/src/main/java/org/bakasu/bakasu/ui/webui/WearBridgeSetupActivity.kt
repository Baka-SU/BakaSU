package org.bakasu.bakasu.ui.webui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
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
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

/** Phone-only CDM consent and Bluetooth receiver settings. */
class WearBridgeSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KernelSUTheme { PhoneBridgeSettings { finish() } } }
    }
}

private enum class BridgeSetting { Permissions, Associate, Code, Generate, Enable, Disconnect, AppSettings }

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
    LaunchedEffect(viewModel) { viewModel.refresh() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && Build.VERSION.SDK_INT < 33) {
            if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                viewModel.failed(BridgeFailure.PERMISSION)
                return@rememberLauncherForActivityResult
            }
            @Suppress("DEPRECATION")
            val device = result.data?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
            device?.let { viewModel.associate(it.address, it.name.orEmpty().ifBlank { it.address }) }
        }
    }
    fun associate() {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            viewModel.failed(BridgeFailure.PERMISSION)
            return
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            viewModel.failed(BridgeFailure.ASSOCIATION)
            return
        }
        val manager = context.getSystemService(CompanionDeviceManager::class.java)
        val request = AssociationRequest.Builder().addDeviceFilter(BluetoothDeviceFilter.Builder().build()).build()
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                manager.associate(
                    request,
                    ContextCompat.getMainExecutor(context),
                    object : CompanionDeviceManager.Callback() {
                        override fun onAssociationPending(sender: IntentSender) {
                            chooser.launch(IntentSenderRequest.Builder(sender).build())
                        }
                        override fun onAssociationCreated(info: AssociationInfo) {
                            val address = info.deviceMacAddress?.toString()
                            if (address == null) viewModel.failed(BridgeFailure.ASSOCIATION) else viewModel.associate(address, info.displayName?.toString() ?: address)
                        }
                        override fun onFailure(message: CharSequence?) {
                            viewModel.failed(BridgeFailure.ASSOCIATION)
                        }
                    },
                )
            } else {
                @Suppress("DEPRECATION")
                manager.associate(
                    request,
                    object : CompanionDeviceManager.Callback() {
                        override fun onDeviceFound(sender: IntentSender) {
                            chooser.launch(IntentSenderRequest.Builder(sender).build())
                        }
                        override fun onFailure(message: CharSequence?) {
                            viewModel.failed(BridgeFailure.ASSOCIATION)
                        }
                    },
                    null,
                )
            }
        } catch (_: Exception) {
            viewModel.failed(BridgeFailure.ASSOCIATION)
        }
    }
    val removeTitle = stringResource(R.string.wear_bridge_disconnect)
    val removeMessage = stringResource(R.string.wear_bridge_disconnect_message)
    val rows = buildList {
        add(BridgeSetting.Permissions)
        add(BridgeSetting.Associate)
        if (state.pairingCode != null) add(BridgeSetting.Code)
        if (state.peer != null) {
            add(BridgeSetting.Generate)
            if (state.paired) add(BridgeSetting.Enable)
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

                    BridgeSetting.Associate -> SettingsJumpPageWidget(icon = Icons.Rounded.Link, title = stringResource(R.string.wear_bridge_associate), enabled = !busy, onClick = { associate() })

                    BridgeSetting.Code -> SettingsBaseWidget(icon = Icons.Rounded.Key, title = stringResource(R.string.wear_bridge_pair_code), description = state.pairingCode)

                    BridgeSetting.Generate -> SettingsJumpPageWidget(icon = Icons.Rounded.Key, title = stringResource(R.string.wear_bridge_generate_code), description = state.peer?.name, enabled = !busy, onClick = { viewModel.generateCode() })

                    BridgeSetting.Enable -> SettingsSwitchWidget(icon = Icons.Rounded.Bluetooth, title = stringResource(R.string.wear_bridge_enable), checked = state.enabled, enabled = !busy, onCheckedChange = viewModel::enable)

                    BridgeSetting.Disconnect -> SettingsJumpPageWidget(title = removeTitle, enabled = !busy, onClick = {
                        scope.launch { if (confirm.awaitConfirm(removeTitle, removeMessage) == ConfirmResult.Confirmed) viewModel.disconnect() }
                    })

                    BridgeSetting.AppSettings -> SettingsJumpPageWidget(icon = Icons.Rounded.Settings, title = stringResource(R.string.wear_bridge_app_settings), onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                    })
                }
            }
            if (state.pairingCode != null) item { Text(stringResource(R.string.wear_bridge_code_help), Modifier.padding(16.dp)) }
            item { Text(stringResource(R.string.wear_bridge_background_help), Modifier.padding(16.dp)) }
        }
    }
}
