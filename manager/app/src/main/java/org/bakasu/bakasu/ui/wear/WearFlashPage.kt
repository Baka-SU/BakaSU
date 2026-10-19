package org.bakasu.bakasu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ButtonDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.FlashOperation
import org.bakasu.bakasu.domain.usecase.IsModuleUriAccessibleUseCase
import org.bakasu.bakasu.domain.usecase.TakeModuleUriPermissionUseCase
import org.bakasu.bakasu.ui.viewmodel.FlashUiAction
import org.bakasu.bakasu.ui.viewmodel.FlashViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearFlashStatus
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearLogPage
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.rememberWearLogSaver
import org.bakasu.bakasu.ui.wear.component.toLogLines
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Runs a [FlashOperation] with the phone's FlashViewModel: a module ZIP, a boot image patch, or
 * uninstalling. The page shows the current step while running and the result with its follow-up
 * actions afterwards; the output opens as its own log page. [onInstalled] runs once the operation
 * succeeds.
 */
@Composable
internal fun WearFlashPage(operation: FlashOperation, onBack: () -> Unit, onInstalled: () -> Unit) {
    val viewModel = koinViewModel<FlashViewModel>()
    val isUriAccessible = koinInject<IsModuleUriAccessibleUseCase>()
    val takeUriPermission = koinInject<TakeModuleUriPermissionUseCase>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fileError by rememberSaveable { mutableStateOf(false) }
    // FlashUiAction.Start restarts a running operation, so the page sends it once, as the phone does.
    var started by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (started) return@LaunchedEffect
        started = true
        val uri = (operation as? FlashOperation.Module)?.uri
        val accessible = uri == null || withContext(Dispatchers.IO) {
            runCatching {
                isUriAccessible(uri).also { if (it) takeUriPermission(uri) }
            }.getOrDefault(false)
        }
        if (accessible) {
            viewModel.dispatch(FlashUiAction.Start(operation))
        } else {
            fileError = true
        }
    }
    LaunchedEffect(state.exitCode) { if (state.exitCode == 0) onInstalled() }

    val failed = fileError || state.exitCode.let { it != null && it != 0 }
    val status = when {
        failed -> WearFlashStatus.FAILED
        state.exitCode == 0 -> WearFlashStatus.SUCCESS
        else -> WearFlashStatus.RUNNING
    }
    val isModule = operation is FlashOperation.Module
    val lines = remember(state.output) { state.output.toLogLines() }
    val currentStep = lines.lastOrNull { it.isNotBlank() }
    val title = stringResource(
        when {
            operation == FlashOperation.Uninstall -> R.string.settings_uninstall_permanent
            operation == FlashOperation.Restore -> R.string.settings_restore_stock_image
            !isModule -> R.string.install
            status == WearFlashStatus.SUCCESS -> R.string.wear_install_complete
            status == WearFlashStatus.FAILED -> R.string.wear_install_failed
            else -> R.string.wear_install_module
        },
    )
    val logTitle = if (isModule) stringResource(R.string.wear_install_log) else title
    var showLog by rememberSaveable { mutableStateOf(false) }
    val saveLog = rememberWearLogSaver("KernelSU_install_log") { state.output }

    if (showLog) {
        WearLogPage(logTitle, lines, following = status == WearFlashStatus.RUNNING) { showLog = false }
        return
    }

    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, title) }
        when (status) {
            WearFlashStatus.RUNNING -> {
                item { WearSectionHeader(spec, stringResource(R.string.wear_install_status)) }
                item {
                    WearChip(
                        spec,
                        stringResource(if (isModule) R.string.wear_installing_module else R.string.flashing),
                        secondaryLabel = currentStep,
                        icon = Icons.Rounded.Download,
                    )
                }
                item {
                    WearActionButton(
                        spec,
                        Icons.Rounded.Article,
                        logTitle,
                        { showLog = true },
                        secondaryText = stringResource(R.string.wear_install_log_summary),
                    )
                }
            }

            WearFlashStatus.SUCCESS -> {
                item { WearSectionHeader(spec, stringResource(R.string.wear_result)) }
                item {
                    WearChip(
                        spec,
                        stringResource(R.string.flash_success),
                        secondaryLabel = currentStep,
                        icon = Icons.Rounded.CheckCircle,
                    )
                }
                item {
                    WearActionButton(
                        spec,
                        Icons.Rounded.Save,
                        stringResource(R.string.save_log),
                        saveLog,
                        secondaryText = stringResource(R.string.wear_save_log_summary),
                    )
                }
                if (state.showReboot) {
                    item {
                        WearActionButton(
                            spec,
                            Icons.Rounded.Refresh,
                            stringResource(R.string.wear_reboot_device),
                            { viewModel.dispatch(FlashUiAction.Reboot(allowSoftReboot = isModule)) },
                            secondaryText = if (isModule) stringResource(R.string.wear_reboot_apply_module) else null,
                            colors = ButtonDefaults.buttonColors(),
                        )
                    }
                }
            }

            WearFlashStatus.FAILED -> {
                item { WearSectionHeader(spec, stringResource(R.string.wear_result)) }
                item {
                    WearChip(
                        spec,
                        stringResource(if (fileError) R.string.wear_module_file_unreadable else R.string.flash_failed),
                        secondaryLabel = state.exitCode?.takeIf { !fileError }?.let {
                            stringResource(R.string.wear_labeled_value, stringResource(R.string.error_code), it.toString())
                        },
                        icon = Icons.Rounded.Error,
                    )
                }
                // An unreadable ZIP never reaches the installer, so there is no log to check or save.
                if (!fileError) {
                    item {
                        WearActionButton(
                            spec,
                            Icons.Rounded.Article,
                            stringResource(R.string.wear_check_log),
                            { showLog = true },
                            secondaryText = stringResource(R.string.wear_check_log_summary),
                        )
                    }
                    item {
                        WearActionButton(
                            spec,
                            Icons.Rounded.Save,
                            stringResource(R.string.save_log),
                            saveLog,
                            secondaryText = stringResource(R.string.wear_save_log_summary),
                        )
                    }
                }
            }
        }
    }
}
