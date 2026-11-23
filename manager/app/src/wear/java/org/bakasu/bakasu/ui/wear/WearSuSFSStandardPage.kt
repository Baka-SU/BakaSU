package org.bakasu.bakasu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.SuSFSConfig
import org.bakasu.bakasu.ui.viewmodel.SuSFSUiAction
import org.bakasu.bakasu.ui.viewmodel.SuSFSViewModel
import org.bakasu.bakasu.ui.viewmodel.awaitSuSFSSlotInfo
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearSubNavigation
import org.bakasu.bakasu.ui.wear.component.WearTextInputPage
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearSuSFSStandardPage(
    config: SuSFSConfig,
    busy: Boolean,
    viewModel: SuSFSViewModel,
    onBack: () -> Unit,
    onCommand: (WearSuSFSCommand, () -> Unit) -> Unit,
) {
    val navigator = rememberWearNavigator(onExit = onBack, initialRoute = WearRoute.SuSFSSection(WearSuSFSSection.Standard.name))
    var release by rememberSaveable { mutableStateOf(config.uname.release) }
    var version by rememberSaveable { mutableStateOf(config.uname.version) }
    var cmdline by rememberSaveable { mutableStateOf(config.cmdline_or_bootconfig) }
    var slotLoading by remember { mutableStateOf(false) }
    var slotFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unset = stringResource(R.string.susfs_standard_not_set)
    WearSubNavigation(navigator) { route ->
        if (route is WearRoute.SuSFSStandardField) {
            val shownField = route.field
            WearTextInputPage(
                stringResource(
                    when (shownField) {
                        "release" -> R.string.susfs_standard_uname_release
                        "version" -> R.string.susfs_standard_uname_version
                        else -> R.string.susfs_standard_cmdline_path
                    },
                ),
                when (shownField) {
                    "release" -> release
                    "version" -> version
                    else -> cmdline
                },
            ) {
                when (shownField) {
                    "release" -> release = it.trim()
                    "version" -> version = it.trim()
                    else -> cmdline = it.trim()
                }
                navigator.back()
            }
        } else if (route == WearRoute.SuSFSUname || route == WearRoute.SuSFSSlots || route == WearRoute.SuSFSCmdline) {
            val shownPage = when (route) {
                WearRoute.SuSFSUname -> "uname"
                WearRoute.SuSFSSlots -> "slots"
                else -> "cmdline"
            }
            val back = navigator::back
            WearList(
                isLoading = busy || slotLoading,
                onBack = back,
                onConfirm = if (shownPage == "slots") {
                    null
                } else {
                    (
                        {
                            val command: WearSuSFSCommand = if (shownPage == "uname") {
                                ({ SuSFSUiAction.SetUname(release, version, it) })
                            } else {
                                ({ SuSFSUiAction.SetCmdlineOrBootconfig(cmdline, it) })
                            }
                            onCommand(command) { while (navigator.backStack.size > 1) navigator.back() }
                        }
                        )
                },
            ) { spec ->
                item {
                    WearPageHeader(
                        spec,
                        stringResource(
                            when (shownPage) {
                                "uname" -> R.string.susfs_standard_uname
                                "slots" -> R.string.susfs_standard_uname_tab_slot_info
                                else -> R.string.susfs_standard_cmdline_or_bootconfig
                            },
                        ),
                    )
                }
                if (shownPage == "uname") {
                    SegmentedColumn(listOf("release", "version"), { it }) { name ->
                        WearSettingsJumpPageWidget(
                            spec,
                            stringResource(
                                if (name == "release") {
                                    R.string.susfs_standard_uname_release
                                } else {
                                    R.string.susfs_standard_uname_version
                                },
                            ),
                            { navigator.open(WearRoute.SuSFSStandardField(name)) },
                            icon = if (name == "release") Icons.Rounded.Memory else Icons.Rounded.Info,
                            description = (if (name == "release") release else version).ifBlank { unset },
                        )
                    }
                    item {
                        WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_standard_uname_tab_slot_info), {
                            navigator.open(WearRoute.SuSFSSlots)
                            slotLoading = true
                            slotFailed = false
                            scope.launch {
                                try {
                                    slotFailed = awaitSuSFSSlotInfo(viewModel) == null
                                } finally {
                                    slotLoading = false
                                }
                            }
                        }, icon = Icons.Rounded.Storage)
                    }
                } else if (shownPage == "slots") {
                    if (slotFailed) {
                        item {
                            WearInfoCard(spec) { Text(stringResource(R.string.susfs_standard_uname_slot_info_load_failed)) }
                        }
                    }
                    if (state.slotInfo?.isEmpty() == true) {
                        item {
                            WearInfoCard(spec) { Text(stringResource(R.string.susfs_standard_uname_slot_info_empty)) }
                        }
                    }
                    state.slotInfo?.takeUnless { slotFailed }?.sortedBy { it.slotName }?.forEach { slot ->
                        item {
                            WearSettingsJumpPageWidget(spec, slot.slotName, {
                                onCommand({ SuSFSUiAction.SetUname(slot.uname.trim(), slot.buildTime.trim(), it) }) { while (navigator.backStack.size > 1) navigator.back() }
                            }, icon = Icons.Rounded.Storage, description = stringResource(R.string.wear_joined, slot.uname, slot.buildTime))
                        }
                    }
                } else {
                    item {
                        WearSettingsJumpPageWidget(
                            spec,
                            stringResource(R.string.susfs_standard_cmdline_path),
                            { navigator.open(WearRoute.SuSFSStandardField("cmdline")) },
                            icon = Icons.Rounded.Terminal,
                            description = cmdline.ifBlank { unset },
                        )
                    }
                }
            }
        } else {
            WearList(isLoading = busy, onBack = onBack) { spec ->
                item { WearPageHeader(spec, stringResource(R.string.susfs_tab_standard)) }
                item { WearSectionHeader(spec, stringResource(R.string.wear_susfs_logging_hiding)) }
                SegmentedColumn(
                    listOf(
                        Triple(R.string.susfs_standard_logging, R.string.susfs_standard_logging_desc, config.logging),
                        Triple(R.string.susfs_standard_avc_log_spoofing, R.string.susfs_standard_avc_log_spoofing_desc, config.avc_log_spoofing),
                        Triple(R.string.susfs_standard_hide_sus_mnts, R.string.susfs_standard_hide_sus_mnts_desc, config.hide_sus_mnts_for_non_su_procs),
                    ),
                    { it.first },
                ) { (label, summary, value) ->
                    WearSettingsSwitchWidget(
                        spec,
                        stringResource(label),
                        value,
                        { checked ->
                            onCommand({ reply ->
                                when (label) {
                                    R.string.susfs_standard_logging -> SuSFSUiAction.EnableLog(checked, reply)
                                    R.string.susfs_standard_avc_log_spoofing -> SuSFSUiAction.EnableAvcLogSpoofing(checked, reply)
                                    else -> SuSFSUiAction.HideSusMnts(checked, reply)
                                }
                            }, {})
                        },
                        icon = when (label) {
                            R.string.susfs_standard_logging -> Icons.Rounded.Article
                            R.string.susfs_standard_avc_log_spoofing -> Icons.Rounded.Policy
                            else -> Icons.Rounded.FolderOff
                        },
                        secondaryLabel = stringResource(summary),
                    )
                }
                item { WearSectionHeader(spec, stringResource(R.string.wear_system_information)) }
                item {
                    WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_standard_uname), {
                        release = config.uname.release
                        version = config.uname.version
                        navigator.open(WearRoute.SuSFSUname)
                    }, icon = Icons.Rounded.Memory, description = stringResource(R.string.wear_joined, config.uname.release, config.uname.version))
                }
                item {
                    WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_standard_cmdline_or_bootconfig), {
                        cmdline = config.cmdline_or_bootconfig
                        navigator.open(WearRoute.SuSFSCmdline)
                    }, icon = Icons.Rounded.Terminal, description = config.cmdline_or_bootconfig.ifBlank { unset })
                }
            }
        }
    }
}
