package org.bakasu.bakasu.ui.wear.screen.main

import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import org.bakasu.bakasu.BuildConfig
import org.bakasu.bakasu.Natives.KernelPatchImplementation
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.HomeDashboardState
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.wear.WearManagerScreen
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearChipEmphasis
import org.bakasu.bakasu.ui.wear.component.WearDialogIcon
import org.bakasu.bakasu.ui.wear.component.WearDialogVerticalArrangement
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearNoticeDialog
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.wearDialogContentPadding
import org.bakasu.bakasu.ui.wear.component.wearStatusColors
import org.bakasu.bakasu.wear.R as WearR

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** A Home notice: a one-line [title] on the page, with the full [message] in its dialog. */
private data class WearHomeWarning(
    val title: Int,
    val message: String,
    val tone: WearStatusTone,
    val onAction: (() -> Unit)? = null,
)

@Composable
internal fun WearHomePage(
    state: HomeDashboardState,
    error: String?,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    onInstall: (() -> Unit)? = null,
    onVersionInfo: () -> Unit = {},
    onStatusInfo: () -> Unit = {},
    backToTop: Boolean = false,
    hasOperationsPanel: Boolean = false,
) {
    val unknown = stringResource(R.string.unknown)
    val status = state.systemStatus
    val info = state.systemInfo
    val seccomp = wearSeccompStatus(state)
    val managers = managerSignatures(state)
    val warnings = buildList {
        // Warn on non-watch devices without blocking use of a sideloaded Wear APK.
        if (!LocalContext.current.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            add(WearHomeWarning(WearR.string.wear_non_watch_title, stringResource(WearR.string.wear_non_watch_message), WearStatusTone.WARNING))
        }
        if (status.ksuVersion != null && !status.isRootAvailable) {
            add(WearHomeWarning(WearR.string.wear_warning_root, stringResource(R.string.grant_root_failed), WearStatusTone.ERROR))
        }
        if (status.isManager && status.kernelUAPIVersion != status.managerUAPIVersion) {
            val kernelInstall = status.lkmMode == true && (status.kernelUAPIVersion ?: 1) <= status.managerUAPIVersion
            val message = if ((status.kernelUAPIVersion ?: 1) > status.managerUAPIVersion) {
                R.string.require_manager_version
            } else if (status.lkmMode == true) {
                R.string.require_kernel_version
            } else {
                R.string.require_kernel_version_gki
            }
            add(
                WearHomeWarning(
                    WearR.string.wear_warning_version_mismatch,
                    stringResource(message),
                    WearStatusTone.ERROR,
                    onInstall.takeIf { kernelInstall },
                ),
            )
        }
        if (BuildConfig.DEBUG) add(WearHomeWarning(WearR.string.wear_warning_debug, stringResource(R.string.debug_version_notice), WearStatusTone.WARNING))
        if (BuildConfig.IS_PR_BUILD || status.isPrBuild) {
            add(WearHomeWarning(WearR.string.wear_warning_pr_build, stringResource(R.string.home_pr_build_warning), WearStatusTone.WARNING))
        }
        if (status.kernelPatchImplementation == KernelPatchImplementation.OFFICIAL) {
            add(WearHomeWarning(WearR.string.wear_warning_apatch, stringResource(R.string.conflict_with_apatch), WearStatusTone.WARNING))
        }
        if (!status.isOfficialSignature) {
            add(
                WearHomeWarning(
                    WearR.string.wear_warning_unofficial,
                    stringResource(R.string.unofficial_version_notice, stringResource(R.string.app_name)),
                    WearStatusTone.WARNING,
                ),
            )
        }
    }
    var shownWarning by remember { mutableStateOf<WearHomeWarning?>(null) }
    val warningDialog = rememberCustomDialog { dismiss ->
        val warning = shownWarning ?: return@rememberCustomDialog
        val action = warning.onAction
        val icon: @Composable () -> Unit = {
            WearDialogIcon(
                if (warning.tone == WearStatusTone.ERROR) Icons.Rounded.Error else Icons.Rounded.Warning,
                tint = if (warning.tone == WearStatusTone.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (action != null) {
            AlertDialog(
                verticalArrangement = WearDialogVerticalArrangement,
                contentPadding = wearDialogContentPadding(),
                visible = true,
                onDismissRequest = dismiss,
                icon = icon,
                title = { Text(stringResource(warning.title)) },
                text = { Text(warning.message) },
                confirmButton = {
                    AlertDialogDefaults.ConfirmButton(
                        onClick = {
                            dismiss()
                            action()
                        },
                    )
                },
                dismissButton = { AlertDialogDefaults.DismissButton(onClick = dismiss) },
            )
        } else {
            WearNoticeDialog(
                visible = true,
                onDismissRequest = dismiss,
                icon = icon,
                title = { Text(stringResource(warning.title)) },
                text = { Text(warning.message) },
            )
        }
    }
    WearList(
        onBack = onBack,
        onOpenPanel = onOpenPanel.takeIf { status.isRootAvailable },
        panelLabel = stringResource(WearR.string.wear_operations),
        hasOperationsPanel = hasOperationsPanel,
        listState = listState,
        backToTop = backToTop,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.home)) }
        // The first load shows the branded loading screen in WearManagerScreen instead of this list.
        if (state.isInitialDataLoaded) {
            warnings.forEach { warning ->
                item {
                    WearWarningBar(spec, warning) {
                        shownWarning = warning
                        warningDialog.show()
                    }
                }
            }
            item {
                val working = status.ksuVersion != null
                WearChip(
                    spec,
                    label = stringResource(
                        when {
                            working && status.isSafeMode -> R.string.safe_mode
                            working -> R.string.home_working
                            status.kernelVersion.isGKI() -> R.string.home_not_installed
                            else -> R.string.home_unsupported
                        },
                    ),
                    secondaryLabel = stringResource(
                        when {
                            working -> R.string.home_short_info
                            status.kernelVersion.isGKI() -> R.string.home_click_to_install
                            else -> R.string.home_unsupported_reason
                        },
                        info.superuserCount,
                        info.moduleCount,
                    ),
                    headlineBadges = if (working) {
                        listOfNotNull(
                            stringResource(if (status.lkmMode == true) WearR.string.wear_mode_lkm else WearR.string.wear_mode_builtin),
                            if (status.isLateLoadMode) stringResource(R.string.jailbreak_mode) else null,
                            info.architecture.takeIf { it.isNotBlank() && it != "aarch64" },
                        )
                    } else {
                        emptyList()
                    },
                    secondaryLabelStyle = if (working) MaterialTheme.typography.bodyExtraSmall else null,
                    icon = when {
                        working -> Icons.Rounded.TaskAlt
                        status.kernelVersion.isGKI() -> Icons.Rounded.Warning
                        else -> Icons.Rounded.Block
                    },
                    emphasis = if (working) WearChipEmphasis.HIGH else WearChipEmphasis.ERROR,
                    onClick = onInstall.takeIf { status.isRootAvailable || status.kernelVersion.isGKI() },
                )
            }
            if (state.isSimpleMode) {
                item {
                    WearChip(
                        spec,
                        stringResource(R.string.home_version_info),
                        secondaryLabel = listOf(info.deviceModel, info.kernelRelease).filter { it.isNotBlank() }
                            .joinToString(" · ").ifBlank { unknown },
                        icon = Icons.Rounded.Info,
                        onClick = onVersionInfo,
                    )
                }
                item {
                    WearChip(
                        spec,
                        stringResource(R.string.home_status_info),
                        secondaryLabel = listOf(info.selinuxStatus.ifBlank { unknown }, seccomp).joinToString(" · "),
                        icon = Icons.Rounded.CheckCircle,
                        onClick = onStatusInfo,
                    )
                }
            } else {
                item { WearSectionHeader(spec, stringResource(R.string.home_version_info)) }
                wearVersionInfoItems(spec, state, unknown)
                item { WearSectionHeader(spec, stringResource(R.string.home_status_info)) }
                wearStatusInfoItems(spec, state, unknown, seccomp, managers)
            }
        }
        if (!error.isNullOrBlank()) item { WearStatusItem(spec, Icons.Rounded.Error, error, tone = WearStatusTone.ERROR, centered = false) }
    }
}

@Composable
internal fun WearHomeVersionInfoPage(state: HomeDashboardState, onBack: () -> Unit) {
    val unknown = stringResource(R.string.unknown)
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.home_version_info)) }
        wearVersionInfoItems(spec, state, unknown)
    }
}

@Composable
internal fun WearHomeStatusInfoPage(state: HomeDashboardState, onBack: () -> Unit) {
    val unknown = stringResource(R.string.unknown)
    val seccomp = wearSeccompStatus(state)
    val managers = managerSignatures(state)
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.home_status_info)) }
        wearStatusInfoItems(spec, state, unknown, seccomp, managers)
    }
}

@Composable
private fun TransformingLazyColumnItemScope.WearWarningBar(
    spec: TransformationSpec,
    warning: WearHomeWarning,
    onClick: () -> Unit,
) {
    val (container, content) = wearStatusColors(warning.tone)
    WearSettingsJumpPageWidget(
        spec = spec,
        title = stringResource(warning.title),
        onClick = onClick,
        icon = if (warning.tone == WearStatusTone.ERROR) Icons.Rounded.Error else Icons.Rounded.Warning,
        description = stringResource(WearR.string.wear_tap_to_view),
        colors = ButtonDefaults.buttonColors(
            containerColor = container ?: MaterialTheme.colorScheme.surfaceContainer,
            contentColor = content,
            secondaryContentColor = content,
            iconColor = content,
        ),
    )
}

@Composable
private fun wearSeccompStatus(state: HomeDashboardState) = stringResource(
    when (state.systemInfo.seccompStatus) {
        -1 -> R.string.seccomp_status_not_supported
        0 -> R.string.seccomp_status_disabled
        1 -> R.string.seccomp_status_strict
        2 -> R.string.seccomp_status_filter
        else -> R.string.seccomp_status_unknown
    },
)

/** The active managers by signature, one signature per line, or null without a manager list. */
@Composable
private fun managerSignatures(state: HomeDashboardState): String? {
    val list = state.systemInfo.managersList ?: return null
    return list.managers.groupBy { it.signatureIndex }.toSortedMap().map { (index, managers) ->
        val signature = when (index) {
            0 -> stringResource(R.string.app_name)

            255 -> stringResource(R.string.dynamic_managerature)

            else -> if (index >= 1) {
                stringResource(R.string.signature_index, index)
            } else {
                stringResource(R.string.unknown_signature)
            }
        }
        managers.joinToString(", ") { it.uid.toString() } + " ($signature)"
    }.joinToString("\n").ifEmpty { stringResource(R.string.no_active_manager) }
}

private fun TransformingLazyColumnScope.wearVersionInfoItems(spec: TransformationSpec, state: HomeDashboardState, unknown: String) {
    val status = state.systemStatus
    val info = state.systemInfo
    val fields = buildList<Triple<ImageVector, Int, String>> {
        add(Triple(Icons.Rounded.Watch, R.string.home_device_model, info.deviceModel))
        add(Triple(Icons.Rounded.DeveloperBoard, R.string.home_kernel, info.kernelRelease))
        add(Triple(Icons.Rounded.Android, R.string.home_android_version, info.androidVersion))
        if (status.isManager) add(Triple(Icons.Rounded.Memory, R.string.home_kernel_version, status.ksuFullVersion.orEmpty()))
        add(
            Triple(
                Icons.Rounded.Tag,
                R.string.home_manager_version,
                info.managerVersion.let { (name, code, build) -> "$name ($code/$build)" },
            ),
        )
        if (info.susfsEnabled && info.susfsVersion.isNotEmpty()) {
            add(Triple(Icons.Rounded.Settings, R.string.home_susfs_version, info.susfsVersion))
        }
    }
    fields.forEach { (icon, label, value) ->
        item { WearChip(spec, stringResource(label), secondaryLabel = value.ifBlank { unknown }, icon = icon) }
    }
}

private fun TransformingLazyColumnScope.wearStatusInfoItems(
    spec: TransformationSpec,
    state: HomeDashboardState,
    unknown: String,
    seccomp: String,
    managers: String?,
) {
    val status = state.systemStatus
    val info = state.systemInfo
    item {
        WearChip(
            spec,
            stringResource(R.string.home_selinux_status),
            secondaryLabel = info.selinuxStatus.ifBlank { unknown },
            icon = Icons.Rounded.Security,
        )
    }
    item { WearChip(spec, stringResource(R.string.home_seccomp_status), secondaryLabel = seccomp, icon = Icons.Rounded.FilterList) }
    if (managers != null) {
        item { WearChip(spec, stringResource(R.string.multi_manager_list), secondaryLabel = managers, icon = Icons.Rounded.Group) }
    }
    val extras = buildList<Triple<ImageVector, Int, String>> {
        if (status.isFullFeatured) add(Triple(Icons.Rounded.Tune, R.string.home_hook_type, status.hookType))
        if (info.zygiskImplement.isNotEmpty() && info.zygiskImplement != "None") {
            add(Triple(Icons.Rounded.Extension, R.string.home_zygisk_implement, info.zygiskImplement))
        }
        if (info.metaModuleImplement.isNotEmpty() && info.metaModuleImplement != "None") {
            add(Triple(Icons.Rounded.Extension, R.string.home_meta_module_implement, info.metaModuleImplement))
        }
    }
    extras.forEach { (icon, label, value) ->
        item { WearChip(spec, stringResource(label), secondaryLabel = value, icon = icon) }
    }
}
