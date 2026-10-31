package org.bakasu.bakasu.ui.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.CompanionBridgeViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun WearBridgeSetupPage(onBack: () -> Unit) {
    val viewModel = koinViewModel<CompanionBridgeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var chosen by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val selected = peers.firstOrNull { it.address == chosen } ?: state.peer?.takeIf { chosen.isEmpty() || chosen == it.address }
    WearErrorDialog(error?.let { wearBridgeFailureText(it) }, viewModel::clearError)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(state.paired, state.peer?.address) { if (state.paired) code = "" }
    val disconnect = rememberWearConfirmDialog(stringResource(R.string.wear_bridge_disconnect), stringResource(R.string.wear_bridge_disconnect_message), destructive = true, onConfirm = viewModel::disconnect)
    if (choosing) {
        WearSubPage({ choosing = false }) {
            WearList(onBack = { choosing = false }) { spec ->
                item { WearPageHeader(spec, stringResource(R.string.wear_bridge_phone)) }
                if (peers.isEmpty()) item { WearEmptyState(spec, Icons.Rounded.Bluetooth, stringResource(R.string.wear_bridge_no_peers)) }
                LazySegmentedColumn(peers, { it.address }) { peer ->
                    WearSettingsJumpPageWidget(spec, peer.name, {
                        chosen = peer.address
                        choosing = false
                    }, icon = Icons.Rounded.Bluetooth)
                }
            }
        }
        return
    }
    if (editing) {
        WearSubPage({ editing = false }) {
            WearTextInputPage(stringResource(R.string.wear_bridge_pair_code), code) {
                code = it
                editing = false
            }
        }
        return
    }
    WearList(onBack = onBack, isLoading = busy) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_bridge)) }
        item { WearInfoCard(spec) { Text(stringResource(R.string.wear_bridge_pair_help)) } }
        if (state.paired) {
            item {
                WearInfoCard(spec) {
                    Text(stringResource(if (state.connected) R.string.wear_bridge_active_connection else R.string.wear_bridge_connected, state.peer?.name.orEmpty()))
                }
            }
        }
        item {
            WearActionButton(spec, Icons.Rounded.Bluetooth, stringResource(R.string.wear_bridge_permission), {
                if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    viewModel.refresh()
                }
            })
        }
        SegmentedColumn(listOf(R.string.wear_bridge_phone, R.string.wear_bridge_pair_code), { it }) { field ->
            WearSettingsJumpPageWidget(
                spec,
                stringResource(field),
                { if (field == R.string.wear_bridge_phone) choosing = true else editing = true },
                icon = if (field == R.string.wear_bridge_phone) Icons.Rounded.Bluetooth else Icons.Rounded.Key,
                description = if (field == R.string.wear_bridge_phone) selected?.name else code,
            )
        }
        item {
            WearActionButton(spec, Icons.Rounded.Bluetooth, stringResource(R.string.wear_bridge_pair), {
                selected?.let { viewModel.pair(it, code) }
            }, enabled = selected != null && code.count { it.isLetterOrDigit() } == 12)
        }
        if (state.paired) item { WearActionButton(spec, Icons.Rounded.LinkOff, stringResource(R.string.wear_bridge_disconnect), disconnect::show) }
    }
}
