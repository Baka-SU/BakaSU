package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import org.bakasu.bakasu.BuildConfig
import org.bakasu.bakasu.Natives.KernelPatchImplementation
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.HomeDashboardState
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearChipEmphasis
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.wearStatusColors

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
    panelContent: @Composable (() -> Unit)? = null,
) {
    val unknown = stringResource(R.string.unknown)
    val status = state.systemStatus
    val info = state.systemInfo
    val seccomp = wearSeccompStatus(state)
    val managers = managerSignatures(state)
    // Build notices are warnings; missing root or incompatible interfaces block features and are errors.
    val warnings = buildList {
        if (status.ksuVersion != null && !status.isRootAvailable) {
            add(WearHomeWarning(R.string.wear_warning_root, stringResource(R.string.grant_root_failed), WearStatusTone.ERROR))
        }
        if (status.isManager && !status.isFullFeatured) {
            val kernelInstall = status.lkmMode == true && (status.kernelUAPIVersion ?: 1) <= status.managerUAPIVersion
            val message = if ((status.kernelUAPIVersion ?: 1) > status.managerUAPIVersion) {
                R.string.require_manager_version
            } else if (status.lkmMode == true) {
                R.string.require_kernel_version
            } else {
                R.string.require_kernel_version_gki
            }
            // As the phone notice says, an outdated LKM kernel is updated from the install page.
            add(
                WearHomeWarning(
                    R.string.wear_warning_version_mismatch,
                    stringResource(message),
                    WearStatusTone.ERROR,
                    onInstall.takeIf { kernelInstall },
                ),
            )
        }
        if (BuildConfig.DEBUG) add(WearHomeWarning(R.string.wear_warning_debug, stringResource(R.string.debug_version_notice), WearStatusTone.WARNING))
        if (BuildConfig.IS_PR_BUILD || status.isPrBuild) {
            add(WearHomeWarning(R.string.wear_warning_pr_build, stringResource(R.string.home_pr_build_warning), WearStatusTone.WARNING))
        }
        if (status.kernelPatchImplementation == KernelPatchImplementation.OFFICIAL) {
            add(WearHomeWarning(R.string.wear_warning_apatch, stringResource(R.string.conflict_with_apatch), WearStatusTone.WARNING))
        }
        if (!status.isOfficialSignature) {
            add(
                WearHomeWarning(
                    R.string.wear_warning_unofficial,
                    stringResource(R.string.unofficial_version_notice, stringResource(R.string.app_name)),
                    WearStatusTone.WARNING,
                ),
            )
        }
    }
    var shownWarning by remember { mutableStateOf<WearHomeWarning?>(null) }
    // The notice is read in a scrolling AlertDialog: a notice with a follow-up action offers it with
    // Cancel, the others close with the edge button.
    val warningDialog = rememberCustomDialog { dismiss ->
        val warning = shownWarning ?: return@rememberCustomDialog
        val action = warning.onAction
        val icon: @Composable () -> Unit = {
            Icon(
                if (warning.tone == WearStatusTone.ERROR) Icons.Rounded.Error else Icons.Rounded.Warning,
                contentDescription = null,
            )
        }
        if (action != null) {
            AlertDialog(
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
            AlertDialog(
                visible = true,
                onDismissRequest = dismiss,
                icon = icon,
                title = { Text(stringResource(warning.title)) },
                text = { Text(warning.message) },
                edgeButton = { AlertDialogDefaults.EdgeButton(onClick = dismiss) },
            )
        }
    }
    WearList(
        onBack = onBack,
        onOpenPanel = onOpenPanel.takeIf { status.isRootAvailable },
        panelLabel = stringResource(R.string.wear_operations),
        panelContent = panelContent,
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
            // The running state is the screen's only high-emphasis chip.
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
                    // Match the phone status card: mode badges belong beside the headline, not in the description.
                    headlineBadges = if (working) {
                        listOfNotNull(
                            stringResource(if (status.lkmMode == true) R.string.wear_mode_lkm else R.string.wear_mode_builtin),
                            if (status.isLateLoadMode) stringResource(R.string.jailbreak_mode) else null,
                        )
                    } else {
                        emptyList()
                    },
                    // The superuser and module counts fit one line in the theme's smallest body style.
                    secondaryLabelStyle = if (working) MaterialTheme.typography.bodyExtraSmall else null,
                    icon = when {
                        working -> Icons.Rounded.TaskAlt
                        status.kernelVersion.isGKI() -> Icons.Rounded.Warning
                        else -> Icons.Rounded.Block
                    },
                    // Not installed or unsupported is an error state, shown in red.
                    emphasis = if (working) WearChipEmphasis.HIGH else WearChipEmphasis.ERROR,
                    // As on the phone, the status opens installation when root or a GKI kernel allows it.
                    onClick = onInstall.takeIf { status.isRootAvailable || status.kernelVersion.isGKI() },
                )
            }
            if (state.isSimpleMode) {
                // Simple mode folds each section into one summary row; the full section opens on its own page.
                // The rows use the tonal surface of the other Home chips, so they follow the dynamic
                // color scheme and the card transparency.
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

/** The full version information that simple mode opens from its summary row. */
@Composable
internal fun WearHomeVersionInfoPage(state: HomeDashboardState, onBack: () -> Unit) {
    val unknown = stringResource(R.string.unknown)
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.home_version_info)) }
        wearVersionInfoItems(spec, state, unknown)
    }
}

/** The full status information that simple mode opens from its summary row. */
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

/**
 * A notice in a standard list button, sized and padded like the other Home rows: its icon and
 * short title, with a "tap to view" hint as the secondary label; the full text opens in a dialog.
 */
@Composable
private fun TransformingLazyColumnItemScope.WearWarningBar(
    spec: TransformationSpec,
    warning: WearHomeWarning,
    onClick: () -> Unit,
) {
    val (container, content) = wearStatusColors(warning.tone)
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        icon = {
            Icon(
                if (warning.tone == WearStatusTone.ERROR) Icons.Rounded.Error else Icons.Rounded.Warning,
                contentDescription = null,
            )
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = container ?: MaterialTheme.colorScheme.surfaceContainer,
            contentColor = content,
            secondaryContentColor = content,
            iconColor = content,
        ),
        secondaryLabel = { Text(stringResource(R.string.wear_tap_to_view), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        transformation = SurfaceTransformation(spec),
    ) {
        Text(stringResource(warning.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
