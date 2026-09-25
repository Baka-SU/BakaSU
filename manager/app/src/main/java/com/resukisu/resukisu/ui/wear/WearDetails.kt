package com.resukisu.resukisu.ui.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import com.resukisu.resukisu.ui.component.WearInfoCard
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.HomeDashboardState
import com.resukisu.resukisu.domain.model.InstalledAppGroup
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.ui.component.WearList
import com.resukisu.resukisu.ui.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.viewmodel.AppProfileUiAction
import com.resukisu.resukisu.ui.viewmodel.AppProfileUiEvent
import com.resukisu.resukisu.ui.viewmodel.AppProfileViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun WearModuleDetail(
    module: InstalledModule?,
    error: String?,
    onBack: () -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onRemove: (String, Boolean) -> Unit,
) {
    var showRemoveDialog by remember { mutableStateOf(false) }
    BackHandler(showRemoveDialog) { showRemoveDialog = false }
    if (module != null) {
        AlertDialog(
            visible = showRemoveDialog,
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.uninstall)) },
            text = {
                Text(
                    stringResource(
                        if (module.metamodule) R.string.metamodule_uninstall_confirm
                        else R.string.module_uninstall_confirm,
                        module.name,
                    )
                )
            },
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(onClick = {
                    showRemoveDialog = false
                    onRemove(module.id, true)
                })
            },
        )
    }
    WearList {
        item { ListHeader { Text(module?.name ?: stringResource(R.string.unknown_module)) } }
        if (!error.isNullOrBlank()) item { Text(error) }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wear_back))
            }
        }
        if (module != null) {
            item {
                WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.module_version))
                    Text(module.version)
                    Text(stringResource(R.string.module_author))
                    Text(module.author)
                }
            }
            if (module.description.isNotBlank()) {
                item {
                    WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                        Text(module.description)
                    }
                }
            }
            item {
                WearSettingsSwitchWidget(
                    label = stringResource(R.string.wear_enabled),
                    checked = module.enabled,
                    onCheckedChange = { onEnabledChange(module.id, it) },
                    enabled = !module.remove,
                )
            }
            item {
                Button(
                    onClick = { showRemoveDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !module.remove,
                ) { Text(stringResource(R.string.uninstall)) }
            }
        }
    }
}

@Composable
internal fun WearAppDetail(
    group: InstalledAppGroup?,
    isManager: Boolean,
    onBack: () -> Unit,
) {
    if (group == null) {
        WearList {
            item { ListHeader { Text(stringResource(R.string.profile)) } }
            item {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wear_back))
                }
            }
            item { Text(stringResource(R.string.wear_no_apps)) }
        }
        return
    }

    val viewModel = koinViewModel<AppProfileViewModel>(
        key = "wear-app-${group.uid}-${group.primaryPackageName}",
        parameters = { parametersOf(group.uid, group.primaryPackageName) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            error = event is AppProfileUiEvent.Error ||
                event is AppProfileUiEvent.SepolicyUpdateFailed
        }
    }

    WearList {
        item { ListHeader { Text(group.mainApp.label) } }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wear_back))
            }
        }
        if (state.isLoading) {
            item { CircularProgressIndicator() }
        } else {
            val profile = state.profile
            if (profile != null) {
                item {
                    WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                        Text(group.mainApp.displayIdentifier)
                        Text(
                            if (profile.allowSu) stringResource(R.string.wear_allowed)
                            else stringResource(R.string.wear_denied)
                        )
                    }
                }
                item {
                    WearSettingsSwitchWidget(
                        label = stringResource(R.string.wear_root_available),
                        checked = profile.allowSu,
                        onCheckedChange = {
                            viewModel.dispatch(AppProfileUiAction.Save(profile.copy(allowSu = it)))
                        },
                        enabled = !isManager && !group.isWebViewZygote,
                    )
                }
                item {
                    WearSettingsSwitchWidget(
                        label = stringResource(R.string.profile_umount_modules),
                        checked = profile.umountModules,
                        onCheckedChange = {
                            viewModel.dispatch(
                                AppProfileUiAction.Save(profile.copy(umountModules = it))
                            )
                        },
                    )
                }
            } else {
                item { Text(stringResource(R.string.operation_failed)) }
                item {
                    Button(
                        onClick = { viewModel.dispatch(AppProfileUiAction.Load) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.network_retry)) }
                }
            }
        }
        if (error) item { Text(stringResource(R.string.operation_failed)) }
    }
}

@Composable
internal fun WearAboutDetail(home: HomeDashboardState, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    WearList {
        item { ListHeader { Text(stringResource(R.string.about)) } }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wear_back))
            }
        }
        item {
            WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.app_name))
                Text(stringResource(R.string.home_manager_version))
                Text(home.systemInfo.managerVersion.first.ifBlank {
                    stringResource(R.string.unknown)
                })
            }
        }
        item {
            WearInfoCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_kernel))
                Text(home.systemInfo.kernelRelease.ifBlank {
                    stringResource(R.string.unknown)
                })
                Text(stringResource(R.string.version))
                Text(home.systemStatus.ksuFullVersion ?: stringResource(R.string.unknown))
            }
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { uriHandler.openUri("https://github.com/ReSukiSU/ReSukiSU") },
            ) { Text(stringResource(R.string.get_source_code)) }
        }
    }
}
