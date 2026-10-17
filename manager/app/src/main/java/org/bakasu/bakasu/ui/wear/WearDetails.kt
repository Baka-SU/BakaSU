package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.twotone.AccountCircle
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Extension
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppControlAction
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.ui.component.PackageIcon
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiAction
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiEvent
import org.bakasu.bakasu.ui.viewmodel.AppProfileViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearAppProfileConfig
import org.bakasu.bakasu.ui.wear.component.WearCompactItemSpacing
import org.bakasu.bakasu.ui.wear.component.WearDetailField
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearModuleInfoCard
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
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
    WearResultDialog(error?.takeIf { it.isNotBlank() }?.let { WearResult(it, success = false) }, onErrorShown)
    WearList(onBack = onBack, itemSpacing = WearCompactItemSpacing) { spec ->
        // As in the design, the page opens with the module title card instead of a page title.
        item {
            WearModuleInfoCard(spec) {
                Text(module.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(module.version.ifBlank { unknown }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (module.remove) {
                    Text(stringResource(R.string.wear_pending_removal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                if (module.metamodule) {
                    Text(stringResource(R.string.wear_meta_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_module_information)) }
        // Information rows read like the operation buttons below (icon, label, secondary label), but
        // stay cards, as nothing happens on a tap.
        item { WearModuleInfoCard(spec, compact = true) { WearInfoRow(Icons.TwoTone.Person, stringResource(R.string.module_author), module.author.ifBlank { unknown }) } }
        item { WearModuleInfoCard(spec, compact = true) { WearInfoRow(Icons.TwoTone.Tag, stringResource(R.string.wear_details), module.id) } }
        if (module.description.isNotBlank()) {
            item { WearModuleInfoCard(spec, compact = true) { WearInfoRow(Icons.TwoTone.Description, stringResource(R.string.wear_description), module.description) } }
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_module_operations)) }
        // Every operation uses the button's icon, label and secondary label slots: the label names the
        // operation and the secondary label gives its state or what it does.
        item {
            WearSettingsSwitchWidget(
                spec,
                stringResource(R.string.wear_enable_module),
                module.enabled,
                { onEnabledChange(module.id, it) },
                enabled = !module.remove && !module.update,
                icon = Icons.TwoTone.PowerSettingsNew,
                secondaryLabel = stringResource(if (module.enabled) R.string.wear_enabled else R.string.wear_disabled),
                compact = true,
            )
        }
        if (module.hasWebUi) {
            item {
                WearActionButton(
                    spec,
                    Icons.TwoTone.Language,
                    stringResource(R.string.wear_webui),
                    { onWebUi(module) },
                    enabled = module.enabled && !module.remove,
                    secondaryText = stringResource(R.string.wear_webui_summary),
                    compact = true,
                )
            }
        }
        if (module.hasActionScript) {
            item {
                WearActionButton(
                    spec,
                    Icons.TwoTone.PlayArrow,
                    stringResource(R.string.action),
                    { onExecute(module) },
                    enabled = module.enabled && !module.remove,
                    secondaryText = stringResource(R.string.wear_action_summary),
                    compact = true,
                )
            }
        }
        module.moduleUpdate?.takeIf { it.zipUrl.isNotBlank() }?.let { update ->
            item {
                WearActionButton(
                    spec,
                    Icons.TwoTone.Update,
                    stringResource(R.string.module_update),
                    { onUpdate(module) },
                    enabled = !module.remove,
                    secondaryText = stringResource(R.string.wear_update_to, update.version),
                    compact = true,
                )
            }
        }
        item {
            WearActionButton(
                spec,
                Icons.TwoTone.Delete,
                stringResource(R.string.uninstall),
                removeDialog::show,
                enabled = !module.remove,
                secondaryText = stringResource(if (module.remove) R.string.wear_pending_removal else R.string.wear_uninstall_summary),
                // Every part takes the error pair: left unset, the secondary label and icon would keep
                // the filled button's onPrimary and follow the accent color.
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    iconColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                compact = true,
            )
        }
    }
}

/**
 * A read-only row in the layout and type sizes of a Wear button: the icon centered beside its label
 * and a quieter secondary label, which wraps rather than running into the icon.
 */
@Composable
private fun WearInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val profile = state.profile
            if (profile != null) {
                // As in the design, the page opens with the app title card instead of a page title.
                item {
                    WearModuleInfoCard(spec) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PackageIcon(
                                packageName = if (group.isWebViewZygote) "android" else group.mainApp.packageName,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(group.mainApp.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    group.mainApp.displayIdentifier,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
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
                        secondaryLabel = stringResource(R.string.wear_root_access),
                    )
                }
                item {
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.wear_profile_mode),
                        { editingProfile = true },
                        icon = Icons.TwoTone.AccountCircle,
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
                item {
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.affected_applications),
                        { detailPage = "affected" },
                        description = stringResource(R.string.wear_same_uid_apps),
                    )
                }
                if (!group.isWebViewZygote) {
                    item {
                        WearSettingsJumpPageWidget(
                            spec,
                            stringResource(R.string.wear_app_operations),
                            { detailPage = "actions" },
                            description = stringResource(R.string.wear_app_operations_summary),
                        )
                    }
                }
            } else {
                item { WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.operation_failed), tone = WearStatusTone.ERROR) }
            }
        }
    }
}
