package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.FlashOperation
import com.resukisu.resukisu.domain.usecase.IsModuleUriAccessibleUseCase
import com.resukisu.resukisu.domain.usecase.TakeModuleUriPermissionUseCase
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearFlashProgress
import com.resukisu.resukisu.ui.component.wear.WearFlashStatus
import com.resukisu.resukisu.ui.component.wear.WearFlashStatusChip
import com.resukisu.resukisu.ui.component.wear.WearFollowLog
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.toLogLines
import com.resukisu.resukisu.ui.component.wear.wearLogLines
import com.resukisu.resukisu.ui.viewmodel.FlashUiAction
import com.resukisu.resukisu.ui.viewmodel.FlashUiEvent
import com.resukisu.resukisu.ui.viewmodel.FlashViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun WearModuleInstallPage(
    uri: String,
    requestId: Int,
    onBack: () -> Unit,
    onInstalled: () -> Unit,
    operation: FlashOperation = FlashOperation.Module(uri),
) {
    val viewModel = koinViewModel<FlashViewModel>(key = "wear-module-install-$requestId")
    val isUriAccessible = koinInject<IsModuleUriAccessibleUseCase>()
    val takeUriPermission = koinInject<TakeModuleUriPermissionUseCase>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fileError by rememberSaveable(requestId) { mutableStateOf(false) }
    var installError by rememberSaveable(requestId) { mutableStateOf(false) }
    // FlashUiAction.Start cancels and restarts a running operation, so it must be sent only once per
    // request, even when the page re-enters composition after the Activity is recreated.
    var started by rememberSaveable(requestId) { mutableStateOf(false) }

    LaunchedEffect(uri, viewModel) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { event ->
                if (event is FlashUiEvent.Completed && event.code == 0) onInstalled()
                if (event is FlashUiEvent.Error) installError = true
            }
        }
        if (started) return@LaunchedEffect
        started = true
        val accessible = operation !is FlashOperation.Module || withContext(Dispatchers.IO) {
            runCatching {
                if (!isUriAccessible(uri)) false
                else {
                    takeUriPermission(uri)
                    true
                }
            }.getOrDefault(false)
        }
        if (accessible) viewModel.dispatch(FlashUiAction.Start(operation))
        else fileError = true
    }

    val failed = fileError || installError || state.exitCode.let { it != null && it != 0 }
    val status = when {
        failed -> WearFlashStatus.FAILED
        state.exitCode == 0 -> WearFlashStatus.SUCCESS
        else -> WearFlashStatus.RUNNING
    }
    val lines = remember(state.output) { state.output.toLogLines() }
    val listState = rememberTransformingLazyColumnState()
    WearFollowLog(listState, lines.size, following = status == WearFlashStatus.RUNNING)

    WearList(onBack = onBack, listState = listState) { spec ->
        item { WearPageHeader(spec, null, stringResource(when (operation) {
            FlashOperation.Uninstall -> R.string.settings_uninstall_permanent
            FlashOperation.Restore -> R.string.settings_restore_stock_image
            is FlashOperation.Boot -> R.string.install
            is FlashOperation.Module -> R.string.install
        })) }
        item {
            WearFlashStatusChip(spec, status, stringResource(when {
                fileError -> R.string.wear_module_file_unreadable
                installError -> R.string.operation_failed
                status == WearFlashStatus.FAILED -> R.string.flash_failed
                status == WearFlashStatus.SUCCESS -> R.string.flash_success
                else -> R.string.flashing
            }))
        }
        if (status == WearFlashStatus.RUNNING) item { WearFlashProgress(spec) }
        if (status == WearFlashStatus.SUCCESS && state.showReboot) {
            item {
                WearActionButton(spec, Icons.TwoTone.PowerSettingsNew, stringResource(R.string.reboot), {
                    viewModel.dispatch(FlashUiAction.Reboot(allowSoftReboot = operation is FlashOperation.Module))
                }, colors = ButtonDefaults.buttonColors())
            }
        }
        if (!fileError) wearLogLines(spec, lines)
    }
}
