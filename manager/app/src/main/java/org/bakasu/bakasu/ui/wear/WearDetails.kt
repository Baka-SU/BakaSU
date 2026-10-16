package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Badge
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppControlAction
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiAction
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiEvent
import org.bakasu.bakasu.ui.viewmodel.AppProfileViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearAppProfileConfig
import org.bakasu.bakasu.ui.wear.component.WearDetailField
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearIconButtonGroup
import org.bakasu.bakasu.ui.wear.component.WearIconText
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearModuleInfoCard
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.WearSubPage
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearModuleDetail(
    module: InstalledModule?,
    error: String?,
    onBack: () -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onRemove: (String, Boolean) -> Unit,
    onWebUi: (InstalledModule) -> Unit,
    onExecute: (InstalledModule) -> Unit,
    onUpdate: (InstalledModule) -> Unit,
    onErrorShown: () -> Unit = {},
) {
    if (module == null) {
        WearList(onBack = onBack) { spec ->
            item {
                WearPageHeader(spec, stringResource(R.string.unknown_module))
            }
            if (!error.isNullOrBlank()) {
                item {
                    WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR)
                }
            }
        }
        return
    }

    val unknown = stringResource(R.string.unknown)
    val removeDialog = rememberWearConfirmDialog(
        title = stringResource(R.string.uninstall),
        message = stringResource(
            if (module.metamodule) {
                R.string.metamodule_uninstall_confirm
            } else {
                R.string.module_uninstall_confirm
            },
            module.name,
        ),
        onConfirm = { onRemove(module.id, true) },
    )
    val actions = buildList {
        add(
            WearIconAction(
                Icons.TwoTone.PowerSettingsNew,
                stringResource(if (module.enabled) R.string.wear_disabled else R.string.wear_enabled),
                { onEnabledChange(module.id, !module.enabled) },
                enabled = !module.remove && !module.update,
            ),
        )
        if (module.hasWebUi) add(WearIconAction(Icons.TwoTone.Language, stringResource(R.string.wear_webui), { onWebUi(module) }, module.enabled && !module.remove))
        if (module.hasActionScript) add(WearIconAction(Icons.TwoTone.PlayArrow, stringResource(R.string.action), { onExecute(module) }, module.enabled && !module.remove))
        if (module.moduleUpdate?.zipUrl?.isNotBlank() == true) add(WearIconAction(Icons.TwoTone.Update, stringResource(R.string.module_update), { onUpdate(module) }, !module.remove))
    }
    WearResultDialog(error?.takeIf { it.isNotBlank() }?.let { WearResult(it, success = false) }, onErrorShown)
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_module_details)) }
        item {
            WearModuleInfoCard(spec) {
                Text(module.name, style = MaterialTheme.typography.titleMedium)
                Text(module.version.ifBlank { unknown }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(
                        if (module.remove) {
                            R.string.wear_pending_removal
                        } else if (module.enabled) {
                            R.string.wear_enabled
                        } else {
                            R.string.wear_disabled
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (module.metamodule) Text(stringResource(R.string.wear_meta_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_module_information)) }
        item { WearModuleInfoCard(spec) { WearDetailField(Icons.TwoTone.Person, stringResource(R.string.module_author), module.author.ifBlank { unknown }) } }
        item { WearModuleInfoCard(spec) { WearDetailField(Icons.TwoTone.Folder, stringResource(R.string.info), module.id) } }
        if (module.description.isNotBlank()) {
            item {
                WearModuleInfoCard(spec) {
                    Text(stringResource(R.string.wear_description), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(module.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_module_operations)) }
        actions.chunked(2).forEach { row -> item { WearIconButtonGroup(spec, row) } }
        item {
            WearActionButton(
                spec,
                Icons.TwoTone.Delete,
                stringResource(R.string.uninstall),
                removeDialog::show,
                enabled = !module.remove,
                colors = androidx.wear.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            )
        }
    }
}

@Composable
internal fun WearAppDetail(
    group: InstalledAppGroup?,
    isManager: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpen: (WearRoute) -> Unit,
) {
    if (group == null) {
        WearList(onBack = onBack) { spec ->
            item {
                WearPageHeader(spec, stringResource(R.string.profile))
            }
            item { WearStatusItem(spec, Icons.TwoTone.Apps, stringResource(R.string.no_apps_found)) }
        }
        return
    }

    val viewModel = koinViewModel<AppProfileViewModel>(
        key = "wear-app-${group.uid}-${group.primaryPackageName}",
        parameters = { parametersOf(group.uid, group.primaryPackageName) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var detailPage by rememberSaveable { mutableStateOf("") }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    val failed = stringResource(R.string.failed_to_update_app_profile, group.mainApp.label)
    val failedOperation = stringResource(R.string.operation_failed)
    val failedSepolicy = stringResource(R.string.failed_to_update_sepolicy, group.mainApp.label)
    val suNotAllowed = stringResource(R.string.su_not_allowed, group.mainApp.label)
    val save: (AppProfile) -> Unit = { profile ->
        if (profile.allowSu && group.uid < 2000 && group.uid != 1000) {
            error = suNotAllowed
        } else {
            viewModel.dispatch(AppProfileUiAction.Save(profile))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            error = when (event) {
                is AppProfileUiEvent.Error -> if (detailPage == "actions") failedOperation else failed
                AppProfileUiEvent.SepolicyUpdateFailed -> failedSepolicy
                AppProfileUiEvent.Saved -> null
            }
            // The app list refreshes only when a saved profile changed it.
            if (event is AppProfileUiEvent.Saved) onSaved()
        }
    }

    WearResultDialog(error?.let { WearResult(it, success = false) }) { error = null }
    if (detailPage.isNotEmpty()) {
        WearSubPage({ detailPage = "" }) {
            WearList(onBack = { detailPage = "" }) { spec ->
                item { WearPageHeader(spec, stringResource(if (detailPage == "affected") R.string.affected_applications else R.string.wear_app_operations)) }
                if (detailPage == "affected") {
                    group.apps.forEach { app -> item { WearInfoCard(spec) { WearDetailField(Icons.TwoTone.Apps, app.label, app.displayIdentifier) } } }
                } else {
                    listOf(AppControlAction.LAUNCH to R.string.launch_app, AppControlAction.FORCE_STOP to R.string.force_stop_app).forEach { (action, label) ->
                        item { WearActionButton(spec, Icons.TwoTone.Apps, stringResource(label), { viewModel.dispatch(AppProfileUiAction.ControlApp(action)) }) }
                    }
                }
            }
        }
        return
    }
    if (editingProfile) {
        WearSubPage({ editingProfile = false }) {
            state.profile?.let { profile ->
                WearAppProfileConfig(
                    profile,
                    state.defaultUmountModules,
                    state.sepolicyValid,
                    { viewModel.dispatch(AppProfileUiAction.ValidateSepolicy(it)) },
                    save,
                    onManageTemplates = { onOpen(WearRoute.Templates) },
                    onViewTemplate = { onOpen(WearRoute.TemplateEditor(it, readOnly = true, creation = false)) },
                    onBack = { editingProfile = false },
                )
            }
        }
    } else {
        WearList(isLoading = state.isLoading, onBack = onBack) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.wear_app_details)) }
            val profile = state.profile
            if (profile != null) {
                item {
                    WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                        WearDetailField(Icons.TwoTone.Badge, group.mainApp.label, group.mainApp.displayIdentifier)
                    }
                }
                item { WearSectionHeader(spec, stringResource(R.string.wear_authorization)) }
                item {
                    WearSettingsSwitchWidget(
                        spec,
                        label = stringResource(R.string.superuser),
                        checked = profile.allowSu,
                        onCheckedChange = { save(profile.copy(allowSu = it)) },
                        enabled = !isManager && !group.isWebViewZygote,
                        icon = Icons.TwoTone.Security,
                    )
                }
                item {
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.profile),
                        { editingProfile = true },
                        icon = Icons.TwoTone.Security,
                        enabled = !isManager,
                        description = stringResource(
                            if (if (profile.allowSu) profile.rootUseDefault else profile.nonRootUseDefault) {
                                R.string.profile_default
                            } else if (profile.allowSu && profile.rootTemplate != null) {
                                R.string.profile_template
                            } else {
                                R.string.profile_custom
                            },
                        ),
                    )
                }
                if (!profile.allowSu) {
                    item {
                        WearSettingsSwitchWidget(
                            spec,
                            stringResource(R.string.profile_umount_modules),
                            if (profile.nonRootUseDefault) state.defaultUmountModules else profile.umountModules,
                            { save(profile.copy(umountModules = it, nonRootUseDefault = false)) },
                            enabled = !isManager && !profile.nonRootUseDefault,
                            secondaryLabel = stringResource(R.string.profile_umount_modules_summary),
                            icon = Icons.TwoTone.Extension,
                        )
                    }
                }
                item { WearSectionHeader(spec, stringResource(R.string.wear_app_operations)) }
                item { WearSettingsJumpPageWidget(spec, stringResource(R.string.affected_applications), { detailPage = "affected" }, icon = Icons.TwoTone.Apps) }
                if (!group.isWebViewZygote) item { WearSettingsJumpPageWidget(spec, stringResource(R.string.wear_app_operations), { detailPage = "actions" }, icon = Icons.TwoTone.Apps) }
            } else {
                item { WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.operation_failed), tone = WearStatusTone.ERROR) }
            }
        }
    }
}
