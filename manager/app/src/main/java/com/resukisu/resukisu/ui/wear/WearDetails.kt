package com.resukisu.resukisu.ui.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.twotone.RemoveModerator
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import com.resukisu.resukisu.ui.component.wear.WearInfoCard
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.InstalledAppGroup
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import com.resukisu.resukisu.ui.component.wear.WearIconText
import com.resukisu.resukisu.ui.component.wear.WearDetailField
import com.resukisu.resukisu.ui.component.wear.WearSettingsSwitchWidget
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
    val unknown = stringResource(R.string.unknown)
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
    WearList { spec ->
        item {
            WearPageHeader(spec, Icons.TwoTone.Extension, module?.name ?: stringResource(R.string.unknown_module))
        }
        if (!error.isNullOrBlank()) item {
            WearStatusItem(spec, Icons.TwoTone.Warning, error)
        }
        item {
            WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowBack, stringResource(R.string.wear_back), onBack)
        }
        if (module != null) {
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    WearIconText(
                        when {
                            module.remove -> Icons.TwoTone.Delete
                            module.enabled -> Icons.Default.CheckCircle
                            else -> Icons.Default.Block
                        },
                        stringResource(
                            when {
                                module.remove -> R.string.wear_pending_removal
                                module.enabled -> R.string.wear_enabled
                                else -> R.string.wear_disabled
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    WearDetailField(
                        Icons.TwoTone.Tag,
                        stringResource(R.string.module_version),
                        module.version.ifBlank { unknown },
                    )
                    WearDetailField(
                        Icons.TwoTone.Person,
                        stringResource(R.string.module_author),
                        module.author.ifBlank { unknown },
                    )
                    WearDetailField(
                        Icons.TwoTone.Folder,
                        stringResource(R.string.module_package),
                        module.id,
                    )
                }
            }
            if (module.description.isNotBlank()) {
                item {
                    WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                        Text(module.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                WearSettingsSwitchWidget(spec,
                    label = stringResource(R.string.wear_enabled),
                    checked = module.enabled,
                    onCheckedChange = { onEnabledChange(module.id, it) },
                    enabled = !module.remove,
                    icon = Icons.TwoTone.Extension,
                )
            }
            item {
                WearActionButton(spec, Icons.TwoTone.Delete, stringResource(R.string.uninstall),
                    onClick = { showRemoveDialog = true }, enabled = !module.remove)
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
        WearList { spec ->
            item {
                WearPageHeader(spec, Icons.TwoTone.Android, stringResource(R.string.profile))
            }
            item {
                WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowBack, stringResource(R.string.wear_back), onBack)
            }
            item { WearScaledItem(spec) { Text(stringResource(R.string.wear_no_apps)) } }
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

    WearList(isLoading = state.isLoading) { spec ->
        item { WearPageHeader(spec, Icons.TwoTone.Android, group.mainApp.label) }
        item {
            WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowBack, stringResource(R.string.wear_back), onBack)
        }
        if (!state.isLoading) {
            val profile = state.profile
            if (profile != null) {
                item {
                    WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                        WearDetailField(
                            Icons.TwoTone.Android,
                            group.mainApp.label,
                            group.mainApp.displayIdentifier,
                        )
                        WearIconText(
                            Icons.TwoTone.Security,
                            if (profile.allowSu) stringResource(R.string.wear_allowed)
                            else stringResource(R.string.wear_denied),
                        )
                    }
                }
                item {
                    WearSettingsSwitchWidget(spec,
                        label = stringResource(R.string.wear_root_available),
                        checked = profile.allowSu,
                        onCheckedChange = {
                            viewModel.dispatch(AppProfileUiAction.Save(profile.copy(allowSu = it)))
                        },
                        enabled = !isManager && !group.isWebViewZygote,
                        icon = Icons.TwoTone.Security,
                    )
                }
                item {
                    WearSettingsSwitchWidget(spec,
                        label = stringResource(R.string.profile_umount_modules),
                        checked = profile.umountModules,
                        onCheckedChange = {
                            viewModel.dispatch(
                                AppProfileUiAction.Save(profile.copy(umountModules = it))
                            )
                        },
                        icon = Icons.TwoTone.RemoveModerator,
                    )
                }
            } else {
                item { WearScaledItem(spec) { Text(stringResource(R.string.operation_failed)) } }
            }
        }
        if (error) item { WearScaledItem(spec) { Text(stringResource(R.string.operation_failed)) } }
    }
}

