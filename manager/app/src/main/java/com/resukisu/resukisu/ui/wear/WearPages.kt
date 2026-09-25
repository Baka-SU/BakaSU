package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.Button
import com.resukisu.resukisu.ui.component.WearInfoCard
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.HomeDashboardState
import com.resukisu.resukisu.domain.model.SulogEventType
import com.resukisu.resukisu.domain.model.toSulogDisplayName
import com.resukisu.resukisu.ui.component.WearList
import com.resukisu.resukisu.ui.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.viewmodel.ModuleUiState
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import com.resukisu.resukisu.ui.viewmodel.SulogUiState
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiState

@Composable
internal fun WearHomePage(
    state: HomeDashboardState,
    error: String?,
    onRefresh: () -> Unit,
) {
    val rootStatus = if (state.systemStatus.isRootAvailable) {
        stringResource(R.string.wear_root_available)
    } else {
        stringResource(R.string.wear_root_unavailable)
    }
    WearList {
        item { ListHeader { Text(stringResource(R.string.home)) } }
        if (!state.isInitialDataLoaded) {
            item { CircularProgressIndicator() }
            item { Text(stringResource(R.string.wear_loading)) }
        } else {
            item {
                WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(rootStatus, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.systemStatus.isFullFeatured) {
                            stringResource(R.string.home_working)
                        } else {
                            stringResource(R.string.home_unsupported)
                        }
                    )
                }
            }
            item {
                WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wear_kernel_su))
                    Text(state.systemStatus.ksuFullVersion ?: stringResource(R.string.unknown))
                }
            }
            item {
                WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_manager_version))
                    Text(state.systemInfo.managerVersion.first.ifBlank {
                        stringResource(R.string.unknown)
                    })
                }
            }
            item {
                WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_kernel))
                    Text(state.systemInfo.kernelRelease.ifBlank {
                        stringResource(R.string.unknown)
                    })
                }
            }
            item {
                WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_device_model))
                    Text(state.systemInfo.deviceModel.ifBlank {
                        stringResource(R.string.unknown)
                    })
                }
            }
            item {
                WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.superuser))
                    Text(state.systemInfo.superuserCount.toString())
                    Text(stringResource(R.string.module))
                    Text(state.systemInfo.moduleCount.toString())
                }
            }
        }
        if (!error.isNullOrBlank()) item { Text(error) }
        item {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wear_refresh))
            }
        }
    }
}

@Composable
internal fun WearSuperUserPage(
    state: SuperUserUiState,
    isRootAvailable: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onAppClick: (Int, String) -> Unit,
) {
    WearList {
        item { ListHeader { Text(stringResource(R.string.superuser)) } }
        item {
            WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (isRootAvailable) stringResource(R.string.wear_root_available)
                    else stringResource(R.string.wear_root_unavailable)
                )
            }
        }
        item { ListSubHeader { Text(stringResource(R.string.wear_apps)) } }
        if (state.isRefreshing && state.appGroupList.isEmpty()) {
            item { CircularProgressIndicator() }
        } else if (!error.isNullOrBlank()) {
            item { Text(error) }
        } else if (state.appGroupList.isEmpty()) {
            item { Text(stringResource(R.string.wear_no_apps)) }
        }
        items(state.appGroupList, key = { "${it.uid}:${it.primaryPackageName}" }) { group ->
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onAppClick(group.uid, group.primaryPackageName) },
                secondaryLabel = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (group.allowSu) Icons.Default.CheckCircle
                            else Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (group.allowSu) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (group.allowSu) stringResource(R.string.wear_allowed)
                            else stringResource(R.string.wear_denied)
                        )
                    }
                },
            ) {
                Text(group.mainApp.label)
            }
        }
        item {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wear_refresh))
            }
        }
    }
}

@Composable
internal fun WearModulesPage(
    state: ModuleUiState,
    error: String?,
    onRefresh: () -> Unit,
    onModuleClick: (String) -> Unit,
) {
    WearList {
        item { ListHeader { Text(stringResource(R.string.module)) } }
        if (state.isRefreshing && state.moduleList.isEmpty()) {
            item { CircularProgressIndicator() }
        } else if (!error.isNullOrBlank()) {
            item { Text(error) }
        } else if (state.moduleList.isEmpty()) {
            item { Text(stringResource(R.string.module_empty)) }
        }
        items(state.moduleList, key = { it.id }) { module ->
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onModuleClick(module.id) },
                secondaryLabel = {
                    Text(
                        when {
                            module.remove -> stringResource(R.string.wear_pending_removal)
                            module.enabled -> stringResource(R.string.wear_enabled)
                            else -> stringResource(R.string.selinux_status_disabled)
                        }
                    )
                },
            ) {
                Text(module.name)
            }
        }
        item {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wear_refresh))
            }
        }
    }
}

@Composable
internal fun WearLogsPage(
    state: SulogUiState,
    onRefresh: () -> Unit,
    onEnable: () -> Unit,
    onSelectFile: (String) -> Unit,
) {
    WearList {
        item { ListHeader { Text(stringResource(R.string.sulog)) } }
        when {
            state.isLoading -> {
                item { CircularProgressIndicator() }
                item { Text(stringResource(R.string.wear_loading)) }
            }
            !state.errorMessage.isNullOrBlank() -> {
                item { Text(state.errorMessage) }
                item {
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.network_retry))
                    }
                }
            }
            !state.isSulogEnabled -> {
                item { Text(stringResource(R.string.sulog_disabled_title)) }
                item {
                    Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sulog_enable_action))
                    }
                }
            }
            else -> {
                if (state.files.size > 1) {
                    item { ListSubHeader { Text(stringResource(R.string.sulog_log_files)) } }
                    items(state.files, key = { it.path }) { file ->
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectFile(file.path) },
                        ) { Text(file.name.toSulogDisplayName()) }
                    }
                }
                if (state.visibleEntries.isEmpty()) {
                    item { Text(stringResource(R.string.wear_no_logs)) }
                }
                itemsIndexed(
                    state.visibleEntries,
                    key = { index, entry -> "$index:${entry.key}" },
                ) { _, entry ->
                    val eventLabel = when (entry.eventType) {
                        SulogEventType.RootExecve -> R.string.sulog_filter_root_execve
                        SulogEventType.SuCompat -> R.string.sulog_filter_sucompat
                        SulogEventType.IoctlGrantRoot -> R.string.sulog_filter_ioctl_grant_root
                        SulogEventType.DaemonEvent -> R.string.sulog_filter_daemon_restart
                        SulogEventType.Dropped -> R.string.sulog_event_dropped
                        SulogEventType.Unknown -> R.string.sulog_entry_unknown_event
                    }
                    WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(eventLabel), style = MaterialTheme.typography.titleSmall)
                        entry.timestampText?.let { Text(it) }
                        Text(entry.rawLine, maxLines = 5)
                    }
                }
                item {
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wear_refresh))
                    }
                }
            }
        }
    }
}

@Composable
internal fun WearSettingsPage(
    state: SettingsUiState,
    message: String?,
    onAction: (SettingsUiAction) -> Unit,
    onAboutClick: () -> Unit,
) {
    WearList {
        item { ListHeader { Text(stringResource(R.string.settings)) } }
        if (!message.isNullOrBlank()) item { Text(message) }
        item { ListSubHeader { Text(stringResource(R.string.wear_general)) } }
        item {
            WearSettingsSwitchWidget(
                label = stringResource(R.string.settings_check_manager_update),
                checked = state.checkManagerUpdate,
                onCheckedChange = { onAction(SettingsUiAction.SetManagerUpdateCheck(it)) },
            )
        }
        item {
            WearSettingsSwitchWidget(
                label = stringResource(R.string.settings_check_module_update),
                checked = state.checkModuleUpdate,
                onCheckedChange = { onAction(SettingsUiAction.SetModuleUpdateCheck(it)) },
            )
        }
        item {
            WearSettingsSwitchWidget(
                label = stringResource(R.string.settings_soft_reboot),
                checked = state.useSoftReboot,
                onCheckedChange = { onAction(SettingsUiAction.SetUseSoftReboot(it)) },
            )
        }
        item { ListSubHeader { Text(stringResource(R.string.wear_security)) } }
        item {
            WearSettingsSwitchWidget(
                label = stringResource(R.string.settings_kernel_umount),
                checked = state.isKernelUmountEnabled,
                onCheckedChange = { onAction(SettingsUiAction.SetKernelUmount(it)) },
            )
        }
        item {
            WearSettingsSwitchWidget(
                label = stringResource(R.string.settings_sulog),
                checked = state.isSuLogEnabled,
                onCheckedChange = { onAction(SettingsUiAction.SetSuLog(it)) },
            )
        }
        item {
            WearSettingsSwitchWidget(
                label = stringResource(R.string.settings_selinux_hide),
                checked = state.isSelinuxHideEnabled,
                onCheckedChange = { onAction(SettingsUiAction.SetSelinuxHide(it)) },
            )
        }
        item { ListSubHeader { Text(stringResource(R.string.wear_advanced)) } }
        item {
            WearSettingsSwitchWidget(
                label = stringResource(R.string.settings_adb_root),
                checked = state.isAdbRootEnabled,
                onCheckedChange = { onAction(SettingsUiAction.SetAdbRoot(it)) },
            )
        }
        item {
            Button(onClick = onAboutClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about))
            }
        }
    }
}
