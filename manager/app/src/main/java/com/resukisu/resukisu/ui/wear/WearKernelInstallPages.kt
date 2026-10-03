package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowForward
import androidx.compose.material.icons.twotone.AutoFixHigh
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.InstallEnvironment
import com.resukisu.resukisu.ui.component.settings.WearChoicePage
import com.resukisu.resukisu.ui.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearFlashProgress
import com.resukisu.resukisu.ui.component.wear.WearFlashStatus
import com.resukisu.resukisu.ui.component.wear.WearFlashStatusChip
import com.resukisu.resukisu.ui.component.wear.WearFollowLog
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearSectionHeader
import com.resukisu.resukisu.ui.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import com.resukisu.resukisu.ui.component.wear.WearStatusTone
import com.resukisu.resukisu.ui.component.wear.rememberWearConfirmDialog
import com.resukisu.resukisu.ui.component.wear.wearGroupGap
import com.resukisu.resukisu.ui.component.wear.wearLogLines
import com.resukisu.resukisu.ui.viewmodel.KernelFlashUiAction
import com.resukisu.resukisu.ui.viewmodel.KernelFlashUiEvent
import com.resukisu.resukisu.ui.viewmodel.KernelFlashViewModel
import com.resukisu.resukisu.ui.viewmodel.WearKernelInstallState
import com.resukisu.resukisu.ui.viewmodel.WearKernelInstallViewModel
import com.resukisu.resukisu.ui.viewmodel.WearLkmMethod
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** The phone Install screen's two tabs become two entries: LKM patching and AnyKernel3 flashing. */
@Composable
internal fun WearInstallPage(environment: InstallEnvironment, loading: Boolean, onBack: () -> Unit, onOpenPage: (String) -> Unit) {
    WearList(isLoading = loading, onBack = onBack, snap = true) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.install)) }
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.Lkm_install_methods), { onOpenPage("kernel-install-lkm") },
                icon = Icons.TwoTone.Memory,
                description = stringResource(R.string.select_file_tip, environment.defaultPartition))
        }
        // AnyKernel3 flashing needs root, as on the phone.
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.GKI_install_methods), { onOpenPage("kernel-install-ak3") },
                icon = Icons.TwoTone.FileUpload, enabled = environment.rootAvailable,
                description = stringResource(if (environment.rootAvailable) R.string.ak3_select_zip else R.string.root_required))
        }
    }
}

/**
 * LKM install: the method as radio buttons, then the phone's advanced options, then "Next" as the
 * screen's single high-emphasis button. A GKI kernel without a known KMI asks for one first.
 */
@Composable
internal fun WearLkmInstallPage(
    environment: InstallEnvironment,
    state: WearKernelInstallState,
    viewModel: WearKernelInstallViewModel,
    onBack: () -> Unit,
    onSelectBoot: () -> Unit,
    onSelectLkm: () -> Unit,
    onOpenPage: (String) -> Unit,
    onFlash: () -> Unit,
) {
    val methods = buildList {
        add(WearLkmMethod.SELECT_FILE)
        if (environment.isGki && environment.rootAvailable) {
            add(WearLkmMethod.DIRECT)
            if (environment.isAbDevice) add(WearLkmMethod.INACTIVE_SLOT)
        }
    }
    val inactiveSlotDialog = rememberWearConfirmDialog(stringResource(android.R.string.dialog_alert_title),
        stringResource(R.string.install_inactive_slot_warning)) { viewModel.setMethod(WearLkmMethod.INACTIVE_SLOT) }
    val canSelectPartition = state.method == WearLkmMethod.DIRECT || state.method == WearLkmMethod.INACTIVE_SLOT
    val suffix = if (state.method == WearLkmMethod.INACTIVE_SLOT) environment.inactiveSlotSuffix else environment.activeSlotSuffix
    val partition = state.partition ?: defaultPartition(environment)
    val ready = state.method != null && (state.method != WearLkmMethod.SELECT_FILE || state.bootUri != null)
    WearList(onBack = onBack, snap = true) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.Lkm_install_methods)) }
        methods.forEach { method ->
            item(key = method) {
                RadioButton(
                    selected = state.method == method,
                    onSelect = {
                        when (method) {
                            WearLkmMethod.SELECT_FILE -> onSelectBoot()
                            WearLkmMethod.DIRECT -> viewModel.setMethod(method)
                            WearLkmMethod.INACTIVE_SLOT -> inactiveSlotDialog.show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(spec),
                    secondaryLabel = if (method == WearLkmMethod.SELECT_FILE) {
                        { Text(state.bootName ?: stringResource(R.string.select_file_tip, environment.defaultPartition),
                            maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    } else null,
                    label = {
                        Text(stringResource(when (method) {
                            WearLkmMethod.SELECT_FILE -> R.string.select_file
                            WearLkmMethod.DIRECT -> R.string.direct_install
                            WearLkmMethod.INACTIVE_SLOT -> R.string.install_inactive_slot
                        }), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        item { WearSectionHeader(spec, stringResource(R.string.advanced_options)) }
        if (canSelectPartition && environment.availablePartitions.isNotEmpty()) item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.install_select_partition), { onOpenPage("install-partition") },
                icon = Icons.TwoTone.AutoFixHigh, description = "$partition ($suffix)")
        }
        item {
            WearSettingsSwitchWidget(spec, stringResource(R.string.install_upload_lkm_file), state.lkmUri != null,
                { if (it) onSelectLkm() else viewModel.clearLkm() }, icon = Icons.TwoTone.FileOpen,
                secondaryLabel = state.lkmName ?: stringResource(R.string.install_upload_lkm_file_summary))
        }
        if (state.lkmRejected) item {
            WearStatusItem(spec, Icons.TwoTone.Warning, stringResource(R.string.install_only_support_ko_file), tone = WearStatusTone.ERROR)
        }
        item {
            WearSettingsSwitchWidget(spec, stringResource(R.string.allow_shell), state.allowShell, viewModel::setAllowShell,
                secondaryLabel = stringResource(R.string.allow_shell_summary))
        }
        item {
            WearSettingsSwitchWidget(spec, stringResource(R.string.enable_adb), state.enableAdb, viewModel::setEnableAdb,
                secondaryLabel = stringResource(R.string.enable_adb_summary))
        }
        if (state.method == WearLkmMethod.SELECT_FILE) item {
            WearSettingsSwitchWidget(spec, stringResource(R.string.install_force_backup), state.forceBackup, viewModel::setForceBackup,
                secondaryLabel = stringResource(R.string.install_force_backup_summary))
        }
        wearGroupGap("next-gap")
        item {
            WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowForward, stringResource(R.string.install_next), {
                if (state.partition == null) defaultPartition(environment)?.let(viewModel::setPartition)
                if (environment.isGki && state.lkmUri == null && state.kmi == null && environment.currentKmi.isBlank()) {
                    onOpenPage("install-kmi")
                } else onFlash()
            }, enabled = ready, colors = ButtonDefaults.buttonColors())
        }
    }
}

/** The phone preselects the default partition, or the first available one. */
private fun defaultPartition(environment: InstallEnvironment): String? =
    environment.availablePartitions.let { it.getOrNull(it.indexOf(environment.defaultPartition).coerceAtLeast(0)) }

/** Partition and KMI choices use the shared Wear radio list. */
@Composable
internal fun WearKernelChoicePage(
    route: String,
    environment: InstallEnvironment,
    state: WearKernelInstallState,
    viewModel: WearKernelInstallViewModel,
    onBack: () -> Unit,
    onFlash: () -> Unit,
) {
    when (route) {
        "install-partition" -> WearChoicePage(stringResource(R.string.install_select_partition),
            environment.availablePartitions.map { it to it }, state.partition ?: defaultPartition(environment).orEmpty(), onBack) {
            viewModel.setPartition(it); onBack()
        }
        "install-kmi" -> WearChoicePage(stringResource(R.string.select_kmi),
            environment.supportedKmis.map { it to it }, state.kmi.orEmpty(), onBack) {
            viewModel.setKmi(it); onFlash()
        }
    }
}

/** AnyKernel3: pick the ZIP, choose the slot on A/B devices, then flash. */
@Composable
internal fun WearAk3InstallPage(
    environment: InstallEnvironment,
    state: WearKernelInstallState,
    viewModel: WearKernelInstallViewModel,
    onBack: () -> Unit,
    onSelectZip: () -> Unit,
    onFlash: () -> Unit,
) {
    val activeSlot = environment.activeSlotSuffix.removePrefix("_").takeIf { it == "a" || it == "b" }
    LaunchedEffect(environment.isAbDevice, activeSlot) {
        if (environment.isAbDevice && state.slot == null) activeSlot?.let(viewModel::setSlot)
    }
    val ready = state.ak3Uri != null && (!environment.isAbDevice || state.slot != null)
    WearList(onBack = onBack, snap = true) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.GKI_install_methods)) }
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.horizon_kernel), onSelectZip,
                icon = Icons.TwoTone.FileUpload, description = state.ak3Name ?: stringResource(R.string.ak3_select_zip))
        }
        if (environment.isAbDevice) {
            item {
                WearSectionHeader(spec, stringResource(R.string.selected_slot,
                    stringResource(if (state.slot == "b") R.string.slot_b else R.string.slot_a)))
            }
            listOf("a" to R.string.slot_a, "b" to R.string.slot_b).forEach { (slot, label) ->
                item(key = "slot-$slot") {
                    RadioButton(
                        selected = state.slot == slot,
                        onSelect = { viewModel.setSlot(slot) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
        item { WearSectionHeader(spec, stringResource(R.string.advanced_options)) }
        item {
            WearSettingsSwitchWidget(spec, stringResource(R.string.skip_ksud), state.skipKsud, viewModel::setSkipKsud,
                secondaryLabel = stringResource(R.string.skip_ksud_summary))
        }
        wearGroupGap("next-gap")
        item {
            WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowForward, stringResource(R.string.install_next), onFlash,
                enabled = ready, colors = ButtonDefaults.buttonColors())
        }
    }
}

/**
 * AnyKernel3 flashing with the phone's KernelFlashViewModel: status, step progress, the log as
 * monospace lines following the newest output, and reboot once complete.
 */
@Composable
internal fun WearKernelFlashPage(uri: String, slot: String?, skipKsud: Boolean, requestId: Int, onBack: () -> Unit) {
    val viewModel = koinViewModel<KernelFlashViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val flash = state.flash
    var error by rememberSaveable(requestId) { mutableStateOf<String?>(null) }
    // Starting again would restart the flash, so a request starts once, also across Activity recreation.
    var started by rememberSaveable(requestId) { mutableStateOf(false) }
    LaunchedEffect(requestId) {
        if (!started) {
            started = true
            viewModel.dispatch(KernelFlashUiAction.Start(uri, slot, skipKsud))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if (event is KernelFlashUiEvent.Error) error = event.message }
    }
    LaunchedEffect(flash.isCompleted, state.autoExit) {
        if (state.requestUri == uri && state.selectedSlot == slot && flash.isCompleted && state.autoExit) {
            viewModel.dispatch(KernelFlashUiAction.ConsumeAutoExit)
            onBack()
        }
    }
    val interrupted = started && state.sessionLoaded && (state.requestUri != uri || state.selectedSlot != slot)
    val status = when {
        interrupted -> WearFlashStatus.FAILED
        flash.error.isNotEmpty() -> WearFlashStatus.FAILED
        flash.isCompleted -> WearFlashStatus.SUCCESS
        else -> WearFlashStatus.RUNNING
    }
    val listState = rememberTransformingLazyColumnState()
    WearFollowLog(listState, flash.logs.size, following = status == WearFlashStatus.RUNNING)
    WearList(onBack = onBack, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.horizon_kernel)) }
        item {
            if (interrupted) WearStatusItem(spec, Icons.TwoTone.Warning,
                stringResource(R.string.wear_operation_interrupted), tone = WearStatusTone.ERROR)
            else WearFlashStatusChip(spec, status, stringResource(when (status) {
                WearFlashStatus.RUNNING -> R.string.flashing
                WearFlashStatus.SUCCESS -> R.string.horizon_flash_complete
                WearFlashStatus.FAILED -> R.string.flash_failed
            }), detail = if (status == WearFlashStatus.FAILED) flash.error else flash.currentStep)
        }
        if (status == WearFlashStatus.RUNNING) item { WearFlashProgress(spec, flash.progress.takeIf { it > 0f }) }
        if (status == WearFlashStatus.SUCCESS) item {
            WearActionButton(spec, Icons.TwoTone.PowerSettingsNew, stringResource(R.string.reboot),
                { viewModel.dispatch(KernelFlashUiAction.Reboot) }, colors = ButtonDefaults.buttonColors())
        }
        error?.let { message ->
            item { WearStatusItem(spec, Icons.TwoTone.Warning, message.ifBlank { stringResource(R.string.failed_reboot) }, tone = WearStatusTone.ERROR) }
        }
        // The watch shows only the latest lines; the full log stays in the shared flash state.
        if (!interrupted) wearLogLines(spec, flash.logs.takeLast(WearFlashLogLines))
        if (!interrupted && flash.logs.size > WearFlashLogLines) item { WearSectionHeader(spec, stringResource(R.string.wear_log_tail)) }
    }
}

private const val WearFlashLogLines = 256
