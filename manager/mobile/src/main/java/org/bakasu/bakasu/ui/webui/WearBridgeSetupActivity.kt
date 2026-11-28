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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import org.bakasu.bakasu.ui.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.component.settings.SettingsBaseWidget
import org.bakasu.bakasu.ui.component.settings.SettingsJumpPageWidget
import org.bakasu.bakasu.ui.component.settings.SettingsSwitchWidget
import org.bakasu.bakasu.ui.component.settings.lazySegmentColumn
import org.bakasu.bakasu.ui.theme.KernelSUTheme
import org.bakasu.bakasu.ui.util.ActivityResumeEffect
import org.bakasu.bakasu.ui.util.adaptiveScaffoldWindowInsets
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

private enum class WatchSetting { Empty, Device, Remember }

private enum class BridgePermissionSetting { NearbyDevices, Notifications, Background, AppSettings }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhoneBridgeSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = koinViewModel<CompanionBridgeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val operationBusy by viewModel.busy.collectAsStateWithLifecycle()
    val listening by viewModel.listening.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var requestingPermissions by rememberSaveable { mutableStateOf(false) }
    var associating by rememberSaveable { mutableStateOf(false) }
    val busy = operationBusy || requestingPermissions || associating
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val failureTitle = stringResource(R.string.operation_failed)
    val failureText = error?.let {
        stringResource(if (it == BridgeFailure.PERMISSION) R.string.wear_bridge_phone_permission_required else it.resId)
    }
    LaunchedEffect(failureText) {
        if (failureText != null) {
            confirm.awaitConfirm(failureTitle, failureText)
            viewModel.clearError()
        }
    }
    ActivityResumeEffect(viewModel) { viewModel.refresh() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (Build.VERSION.SDK_INT < 31 || granted[Manifest.permission.BLUETOOTH_CONNECT] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.setAccess(true)
        } else {
            viewModel.refresh()
            viewModel.failed(BridgeFailure.PERMISSION)
        }
        requestingPermissions = false
    }
    fun enableReceiver() {
        val requested = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (requested.isEmpty()) {
            viewModel.setAccess(true)
        } else {
            requestingPermissions = true
            permissions.launch(requested.toTypedArray())
        }
    }
    // A single known device only needs the system confirmation dialog, not the discovery list.
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        associating = false
        viewModel.refresh()
    }
    fun registerWithSystem(peer: BridgePeer) {
        if (Build.VERSION.SDK_INT < 33 || !state.associationSupported) return
        if (state.associated) {
            viewModel.observePresence(peer.address)
            return
        }
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: run {
            viewModel.failed(BridgeFailure.ASSOCIATION)
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(peer.address).build())
            .setSingleDevice(true)
            .build()
        associating = true
        runCatching {
            manager.associate(
                request,
                ContextCompat.getMainExecutor(context),
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(sender: IntentSender) {
                        runCatching { chooser.launch(IntentSenderRequest.Builder(sender).build()) }
                            .onFailure {
                                associating = false
                                Log.w(TAG, "Could not open companion association", it)
                                viewModel.failed(BridgeFailure.ASSOCIATION_FAILED)
                            }
                    }
                    override fun onAssociationCreated(info: AssociationInfo) {
                        val address = info.deviceMacAddress?.toString()
                        if (address != null) viewModel.observePresence(address) else viewModel.failed(BridgeFailure.ASSOCIATION_FAILED)
                        associating = false
                    }
                    override fun onFailure(message: CharSequence?) {
                        associating = false
                        Log.w(TAG, "Companion association failed: $message")
                        viewModel.failed(BridgeFailure.ASSOCIATION_FAILED)
                    }
                },
            )
        }.onFailure {
            associating = false
            Log.w(TAG, "Companion association failed", it)
            viewModel.failed(BridgeFailure.ASSOCIATION_FAILED)
        }
    }
    fun openAppSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }

    val peer = state.peer
    val removeTitle = stringResource(R.string.wear_bridge_disconnect)
    val removeMessage = stringResource(R.string.wear_bridge_disconnect_message, peer?.name.orEmpty())
    val status = when {
        !state.enabled -> stringResource(R.string.wear_bridge_receiver_off)
        !state.nearbyDevicesGranted -> stringResource(R.string.wear_bridge_phone_permission_required)
        state.connected -> stringResource(R.string.wear_bridge_active_connection, peer?.name.orEmpty())
        listening -> stringResource(R.string.wear_bridge_receiver_waiting)
        operationBusy -> stringResource(R.string.wear_bridge_receiver_starting)
        else -> stringResource(R.string.wear_bridge_receiver_not_ready)
    }
    val watchRows = if (peer == null) listOf(WatchSetting.Empty) else listOf(WatchSetting.Device, WatchSetting.Remember)
    val permissionRows = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(BridgePermissionSetting.NearbyDevices)
        add(BridgePermissionSetting.Notifications)
        if (peer != null && state.associationSupported) add(BridgePermissionSetting.Background)
        add(BridgePermissionSetting.AppSettings)
    }
    val watchTitle = stringResource(R.string.wear_bridge_my_watch)
    val permissionsTitle = stringResource(R.string.wear_bridge_permissions_background)
    val managementTitle = stringResource(R.string.wear_bridge_device_management)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.wear_bridge_phone_title)) },
                navigationIcon = { AppBackButton(onClick = onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                SegmentedColumn(title = stringResource(R.string.wear_bridge_receiver_section)) {
                    item {
                        SettingsSwitchWidget(
                            icon = Icons.Rounded.Bluetooth,
                            title = stringResource(R.string.wear_bridge_receiver),
                            description = status,
                            checked = state.enabled,
                            enabled = !busy,
                            onCheckedChange = { if (it) enableReceiver() else viewModel.setAccess(false) },
                        )
                    }
                }
            }
            lazySegmentColumn(watchRows, title = watchTitle, key = { _, row -> row }) { _, row ->
                when (row) {
                    WatchSetting.Empty -> SettingsBaseWidget(
                        icon = Icons.Rounded.Watch,
                        title = stringResource(R.string.wear_bridge_no_watch),
                        description = stringResource(R.string.wear_bridge_connect_steps),
                    )

                    WatchSetting.Device -> SettingsBaseWidget(icon = Icons.Rounded.Watch, title = peer?.name.orEmpty(), description = status)

                    WatchSetting.Remember -> SettingsSwitchWidget(
                        icon = Icons.Rounded.VerifiedUser,
                        title = stringResource(R.string.wear_bridge_remember),
                        description = stringResource(R.string.wear_bridge_remember_summary),
                        checked = state.remembered,
                        enabled = !busy,
                        onCheckedChange = viewModel::setRemembered,
                    )
                }
            }
            lazySegmentColumn(permissionRows, title = permissionsTitle, key = { _, row -> row }) { _, row ->
                when (row) {
                    BridgePermissionSetting.NearbyDevices -> SettingsJumpPageWidget(
                        icon = Icons.Rounded.Bluetooth,
                        title = stringResource(R.string.wear_bridge_nearby_permission),
                        description = stringResource(if (state.nearbyDevicesGranted) R.string.wear_bridge_permission_allowed else R.string.wear_bridge_permission_not_allowed),
                        enabled = !busy,
                        onClick = { openAppSettings() },
                    )

                    BridgePermissionSetting.Notifications -> SettingsJumpPageWidget(
                        icon = Icons.Rounded.Notifications,
                        title = stringResource(R.string.wear_bridge_notifications_permission),
                        description = stringResource(if (state.notificationsGranted) R.string.wear_bridge_permission_allowed else R.string.wear_bridge_permission_not_allowed),
                        enabled = !busy,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 26) {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            } else {
                                openAppSettings()
                            }
                        },
                    )

                    BridgePermissionSetting.Background -> SettingsJumpPageWidget(
                        icon = Icons.Rounded.Link,
                        title = stringResource(R.string.wear_bridge_background_restore),
                        description = stringResource(
                            when {
                                associating -> R.string.wear_bridge_background_associating
                                state.associated -> R.string.wear_bridge_background_associated
                                else -> R.string.wear_bridge_background_unassociated
                            },
                        ),
                        enabled = !busy,
                        onClick = { peer?.let(::registerWithSystem) },
                    )

                    BridgePermissionSetting.AppSettings -> SettingsJumpPageWidget(
                        icon = Icons.Rounded.Settings,
                        title = stringResource(R.string.wear_bridge_app_settings),
                        description = stringResource(R.string.wear_bridge_background_help),
                        enabled = !busy,
                        onClick = { openAppSettings() },
                    )
                }
            }
            lazySegmentColumn(listOfNotNull(peer), title = if (peer != null) managementTitle else null, key = { _, device -> device.address }) { _, _ ->
                SettingsJumpPageWidget(
                    icon = Icons.Rounded.LinkOff,
                    title = removeTitle,
                    isError = true,
                    enabled = !busy,
                    onClick = {
                        scope.launch { if (confirm.awaitConfirm(removeTitle, removeMessage) == ConfirmResult.Confirmed) viewModel.disconnect() }
                    },
                )
            }
        }
    }
}
