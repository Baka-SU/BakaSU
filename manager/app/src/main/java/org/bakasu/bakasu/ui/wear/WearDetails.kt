package org.bakasu.bakasu.ui.wear

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Wysiwyg
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.flow.SharedFlow
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppControlAction
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.ui.component.PackageIcon
import org.bakasu.bakasu.ui.util.ActivityResumeEffect
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiAction
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiEvent
import org.bakasu.bakasu.ui.viewmodel.AppProfileUiState
import org.bakasu.bakasu.ui.viewmodel.AppProfileViewModel
import org.bakasu.bakasu.ui.viewmodel.TemplateViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearAppProfileConfig
import org.bakasu.bakasu.ui.wear.component.WearAppProfileModePage
import org.bakasu.bakasu.ui.wear.component.WearAppProfileTemplateChoicePage
import org.bakasu.bakasu.ui.wear.component.WearBadge
import org.bakasu.bakasu.ui.wear.component.WearCompactItemSpacing
import org.bakasu.bakasu.ui.wear.component.WearDetailField
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearInfoRow
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearModuleInfoCard
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
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
                    WearStatusItem(spec, Icons.Rounded.Error, error, tone = WearStatusTone.ERROR)
                }
            }
        }
        return
    }

    val unknown = stringResource(R.string.unknown)
    // The module name stands out on its own; the risk follows it, without repeating the name.
    val removeDialog = rememberWearConfirmDialog(
        title = stringResource(R.string.wear_uninstall_module),
        message = stringResource(if (module.metamodule) R.string.wear_metamodule_uninstall_message else R.string.wear_uninstall_message),
        destructive = true,
        subject = module.name,
        onConfirm = { onRemove(module.id, true) },
    )
    WearResultDialog(error?.takeIf { it.isNotBlank() }?.let { WearResult(it, success = false) }, onErrorShown)
    WearList(onBack = onBack, itemSpacing = WearCompactItemSpacing, topContentPadding = 12.dp) { spec ->
        // As in the design, the page opens with the module title card instead of a page title.
        item {
            WearInfoCard(spec, shape = ButtonDefaults.shape, contentPadding = ButtonDefaults.ContentPadding) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Extension, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(module.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            Text(module.version.ifBlank { unknown }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (module.metamodule) {
                                WearBadge(stringResource(R.string.wear_meta_badge))
                            }
                        }
                    }
                }
            }
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_module_information)) }
        // Information rows read like the operation buttons below (icon, label, secondary label), but
        // stay cards, as nothing happens on a tap.
        item { WearModuleInfoCard(spec, compact = true) { WearInfoRow(Icons.Rounded.Person, stringResource(R.string.module_author), module.author.ifBlank { unknown }) } }
        item { WearModuleInfoCard(spec, compact = true) { WearInfoRow(Icons.Rounded.Tag, stringResource(R.string.wear_details), module.id) } }
        if (module.description.isNotBlank()) {
            item { WearModuleInfoCard(spec, compact = true) { WearInfoRow(Icons.Rounded.Description, stringResource(R.string.wear_description), module.description) } }
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
                icon = Icons.Rounded.PowerSettingsNew,
                secondaryLabel = stringResource(
                    when {
                        module.remove -> R.string.wear_pending_removal
                        module.update -> R.string.reboot_to_apply
                        module.enabled -> R.string.wear_enabled
                        else -> R.string.wear_disabled
                    },
                ),
                compact = true,
            )
        }
        if (module.hasWebUi) {
            item {
                WearActionButton(
                    spec,
                    Icons.AutoMirrored.Rounded.Wysiwyg,
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
                    Icons.Rounded.PlayArrow,
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
                    Icons.Rounded.Update,
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
                if (module.remove) Icons.Rounded.Restore else Icons.Rounded.Delete,
                stringResource(if (module.remove) R.string.wear_restore_module else R.string.uninstall),
                if (module.remove) ({ onRemove(module.id, false) }) else removeDialog::show,
                secondaryText = stringResource(if (module.remove) R.string.wear_restore_module_summary else R.string.wear_uninstall_summary),
                // Every part takes the error pair: left unset, the secondary label and icon would keep
                // the filled button's onPrimary and follow the accent color.
                colors = if (module.remove) {
                    ButtonDefaults.filledTonalButtonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        iconColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                },
                compact = true,
            )
        }
    }
}

@Composable
internal fun WearAppProfileModeRoute(
    uid: Int,
    packageName: String,
    appLabel: String,
    onBack: () -> Unit,
    onOpen: (WearRoute) -> Unit,
) {
    val viewModel = koinViewModel<AppProfileViewModel>(
        key = "wear-app-profile-mode-$uid-$packageName",
        parameters = { parametersOf(uid, packageName) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearAppProfileModeRoute(uid, packageName, appLabel, onBack, onOpen, state, viewModel.events, viewModel::dispatch)
}

@Composable
internal fun WearAppProfileModeRoute(
    uid: Int,
    packageName: String,
    appLabel: String,
    onBack: () -> Unit,
    onOpen: (WearRoute) -> Unit,
    state: AppProfileUiState,
    events: SharedFlow<AppProfileUiEvent>,
    onAction: (AppProfileUiAction) -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var hasResumed by rememberSaveable { mutableStateOf(false) }
    var destinationAfterSave by remember { mutableStateOf<WearRoute?>(null) }
    val failed = stringResource(R.string.failed_to_update_app_profile, appLabel)
    val failedSepolicy = stringResource(R.string.failed_to_update_sepolicy, appLabel)

    fun navigate(destination: WearRoute) {
        if (destination is WearRoute.App) onBack() else onOpen(destination)
    }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is AppProfileUiEvent.Error -> {
                    destinationAfterSave = null
                    error = failed
                }

                AppProfileUiEvent.SepolicyUpdateFailed -> {
                    destinationAfterSave = null
                    error = failedSepolicy
                }

                AppProfileUiEvent.Saved -> {
                    error = null
                    val destination = destinationAfterSave
                    destinationAfterSave = null
                    if (destination != null) navigate(destination)
                }
            }
        }
    }
    ActivityResumeEffect(events) {
        if (hasResumed) {
            onAction(AppProfileUiAction.Load)
        } else {
            hasResumed = true
        }
    }
    WearResultDialog(error?.let { WearResult(it, success = false) }) { error = null }

    val profile = state.profile
    if (profile == null) {
        WearList(isLoading = state.isLoading, onBack = onBack) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.profile)) }
            if (!state.isLoading) item { WearStatusItem(spec, Icons.Rounded.Apps, stringResource(R.string.no_apps_found)) }
        }
    } else {
        WearAppProfileModePage(
            profile = profile,
            onBack = onBack,
            onChooseProfile = {
                if (!profile.allowSu && it == profile) {
                    onBack()
                } else {
                    destinationAfterSave = if (profile.allowSu) null else WearRoute.App(uid, packageName)
                    onAction(AppProfileUiAction.Save(it))
                }
            },
            onChooseTemplate = { onOpen(WearRoute.AppProfileTemplateChoice(uid, packageName)) },
            onChooseCustom = {
                val customProfile = if (profile.allowSu) {
                    profile.copy(rootUseDefault = false, rootTemplate = null)
                } else {
                    profile.copy(nonRootUseDefault = false)
                }
                val destination = if (profile.allowSu) WearRoute.AppProfileCustom(uid, packageName) else WearRoute.App(uid, packageName)
                if (customProfile == profile) {
                    navigate(destination)
                } else {
                    destinationAfterSave = destination
                    onAction(AppProfileUiAction.Save(customProfile))
                }
            },
        )
    }
}

@Composable
internal fun WearAppProfileTemplateChoiceRoute(
    uid: Int,
    packageName: String,
    onBack: () -> Unit,
    templateViewModel: TemplateViewModel,
    isPageLoading: Boolean,
    isManualRefreshing: Boolean,
    onOpenPanel: () -> Unit,
    query: String = "",
    onSaved: () -> Unit = onBack,
) {
    val viewModel = koinViewModel<AppProfileViewModel>(
        key = "wear-app-profile-template-$uid-$packageName",
        parameters = { parametersOf(uid, packageName) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val templates by templateViewModel.uiState.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.operation_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AppProfileUiEvent.Error, AppProfileUiEvent.SepolicyUpdateFailed -> error = failed

                AppProfileUiEvent.Saved -> {
                    error = null
                    onSaved()
                }
            }
        }
    }
    WearResultDialog(error?.let { WearResult(it, success = false) }) { error = null }

    val profile = state.profile
    if (profile == null) {
        WearList(isLoading = state.isLoading, onBack = onBack) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.profile_template)) }
            if (!state.isLoading) item { WearStatusItem(spec, Icons.Rounded.Apps, stringResource(R.string.no_apps_found)) }
        }
    } else {
        WearAppProfileTemplateChoicePage(
            profile = profile,
            templates = org.bakasu.bakasu.ui.viewmodel.filterProfileTemplates(templates.templateList, query),
            isRefreshing = isManualRefreshing && !isPageLoading,
            isLoading = templates.isLoading || isPageLoading,
            loadFailed = templates.loadFailed,
            onBack = onBack,
            onOpenPanel = onOpenPanel,
            noMatches = query.isNotBlank() && templates.templateList.isNotEmpty(),
            onChoose = { template: ProfileTemplate ->
                viewModel.dispatch(
                    AppProfileUiAction.Save(
                        profile.copy(
                            rootUseDefault = false,
                            rootTemplate = template.id,
                            uid = template.uid,
                            gid = template.gid,
                            groups = template.groups,
                            capabilities = template.capabilities,
                            context = template.context,
                            rules = template.rules.joinToString("\n"),
                            namespace = template.namespace,
                        ),
                    ),
                )
            },
        )
    }
}

@Composable
internal fun WearAppProfileCustomRoute(
    uid: Int,
    packageName: String,
    appLabel: String,
    onBack: () -> Unit,
    onOpen: (WearRoute) -> Unit,
    templates: org.bakasu.bakasu.ui.viewmodel.TemplateUiState,
    onNonRootProfile: () -> Unit,
) {
    val viewModel = koinViewModel<AppProfileViewModel>(
        key = "wear-app-profile-custom-$uid-$packageName",
        parameters = { parametersOf(uid, packageName) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearAppProfileCustomRoute(uid, packageName, appLabel, onBack, onOpen, templates, onNonRootProfile, state, viewModel.events, viewModel::dispatch)
}

@Composable
internal fun WearAppProfileCustomRoute(
    uid: Int,
    packageName: String,
    appLabel: String,
    onBack: () -> Unit,
    onOpen: (WearRoute) -> Unit,
    templates: org.bakasu.bakasu.ui.viewmodel.TemplateUiState,
    onNonRootProfile: () -> Unit,
    state: AppProfileUiState,
    events: SharedFlow<AppProfileUiEvent>,
    onAction: (AppProfileUiAction) -> Unit,
) {
    var hasResumed by rememberSaveable { mutableStateOf(false) }
    ActivityResumeEffect(events) {
        if (hasResumed) onAction(AppProfileUiAction.Load) else hasResumed = true
    }
    var error by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.failed_to_update_app_profile, appLabel)
    val failedSepolicy = stringResource(R.string.failed_to_update_sepolicy, appLabel)

    LaunchedEffect(events) {
        events.collect { event ->
            error = when (event) {
                is AppProfileUiEvent.Error -> failed
                AppProfileUiEvent.SepolicyUpdateFailed -> failedSepolicy
                AppProfileUiEvent.Saved -> null
            }
        }
    }
    WearResultDialog(error?.let { WearResult(it, success = false) }) { error = null }

    val profile = state.profile
    // A restored stack can still contain the old non-root editor. Its control now lives in details.
    LaunchedEffect(profile?.allowSu) {
        if (profile != null && !profile.allowSu) onNonRootProfile()
    }
    if (profile?.allowSu == true) {
        WearAppProfileConfig(
            profile,
            state.sepolicyValid,
            { onAction(AppProfileUiAction.ValidateSepolicy(it)) },
            { onAction(AppProfileUiAction.Save(it)) },
            onManageTemplates = { onOpen(WearRoute.Templates) },
            onViewTemplate = { onOpen(WearRoute.TemplateEditor(it, readOnly = true, creation = false)) },
            onBack = onBack,
            templates = templates,
            onChooseTemplate = { onOpen(WearRoute.AppProfileTemplateChoice(uid, packageName)) },
        )
    } else {
        WearList(isLoading = state.isLoading, onBack = onBack) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.profile_custom)) }
        }
    }
}

@Composable
internal fun WearAppDetail(
    group: InstalledAppGroup?,
    isManager: Boolean,
    onBack: () -> Unit,
    onOpen: (WearRoute) -> Unit,
) {
    if (group == null) {
        WearList(onBack = onBack) { spec ->
            item {
                WearPageHeader(spec, stringResource(R.string.profile))
            }
            item { WearStatusItem(spec, Icons.Rounded.Apps, stringResource(R.string.no_apps_found)) }
        }
        return
    }

    val viewModel = koinViewModel<AppProfileViewModel>(
        key = "wear-app-${group.uid}-${group.primaryPackageName}",
        parameters = { parametersOf(group.uid, group.primaryPackageName) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearAppDetail(group, isManager, onBack, onOpen, state, viewModel.events, viewModel::dispatch)
}

@Composable
internal fun WearAppDetail(
    group: InstalledAppGroup,
    isManager: Boolean,
    onBack: () -> Unit,
    onOpen: (WearRoute) -> Unit,
    state: AppProfileUiState,
    events: SharedFlow<AppProfileUiEvent>,
    onAction: (AppProfileUiAction) -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var hasResumed by rememberSaveable { mutableStateOf(false) }
    var umountShown by rememberSaveable {
        mutableStateOf(state.profile?.let { !isManager && !it.allowSu && !it.nonRootUseDefault })
    }
    // The route retains its last target across navigation; lazy-item recycling must not replay entry.
    val umountVisibility = remember(umountShown != null) { MutableTransitionState(umountShown == true) }
    LaunchedEffect(state.profile, state.isLoading, isManager) {
        if (!state.isLoading) {
            state.profile?.let {
                val visible = !isManager && !it.allowSu && !it.nonRootUseDefault
                umountShown = visible
                umountVisibility.targetState = visible
            }
        }
    }
    val failed = stringResource(R.string.failed_to_update_app_profile, group.mainApp.label)
    val failedSepolicy = stringResource(R.string.failed_to_update_sepolicy, group.mainApp.label)
    val suNotAllowed = stringResource(R.string.su_not_allowed, group.mainApp.label)
    val save: (AppProfile) -> Unit = { profile ->
        if (profile.allowSu && group.uid < 2000 && group.uid != 1000) {
            error = suNotAllowed
        } else {
            onAction(AppProfileUiAction.Save(profile))
        }
    }
    LaunchedEffect(events) {
        events.collect { event ->
            error = when (event) {
                is AppProfileUiEvent.Error -> failed
                AppProfileUiEvent.SepolicyUpdateFailed -> failedSepolicy
                AppProfileUiEvent.Saved -> null
            }
        }
    }
    ActivityResumeEffect(events) {
        if (hasResumed) {
            onAction(AppProfileUiAction.Load)
        } else {
            hasResumed = true
        }
    }

    WearResultDialog(error?.let { WearResult(it, success = false) }) { error = null }
    // Returning from a profile page reloads its values without replacing the visible app details.
    WearList(isLoading = state.isLoading && state.profile == null, onBack = onBack) { spec ->
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
                    icon = Icons.Rounded.Security,
                    secondaryLabel = stringResource(R.string.wear_root_access),
                )
            }
            item {
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(R.string.wear_profile_mode),
                    { onOpen(WearRoute.AppProfileMode(group.uid, group.primaryPackageName)) },
                    icon = Icons.Rounded.AccountCircle,
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
            item(key = "umount-modules") {
                WearScaledItem(spec, Modifier.testTag("app-profile-umount")) {
                    AnimatedVisibility(
                        visibleState = umountVisibility,
                        // Reserve the full row before revealing its contents; hide it before
                        // collapsing the slot so the next item never covers a visible switch.
                        enter = fadeIn(tween(120, delayMillis = 180)) + expandVertically(tween(180), expandFrom = Alignment.Top, clip = false),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(180, delayMillis = 120), shrinkTowards = Alignment.Top, clip = false),
                    ) {
                        WearSettingsSwitchWidget(
                            spec,
                            stringResource(R.string.profile_umount_modules),
                            profile.umountModules,
                            { save(profile.copy(umountModules = it, nonRootUseDefault = false)) },
                            secondaryLabel = stringResource(R.string.profile_umount_modules_summary),
                            icon = Icons.Rounded.Extension,
                            transformed = false,
                            transformSurface = false,
                        )
                    }
                }
            }
            item { WearSectionHeader(spec, stringResource(R.string.wear_app_operations)) }
            item {
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(R.string.affected_applications),
                    { onOpen(WearRoute.AffectedApps(group.uid, group.primaryPackageName)) },
                    description = stringResource(R.string.wear_same_uid_apps),
                )
            }
            if (!group.isWebViewZygote) {
                item {
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.wear_app_operations),
                        { onOpen(WearRoute.AppActions(group.uid, group.primaryPackageName)) },
                        description = stringResource(R.string.wear_app_operations_summary),
                    )
                }
            }
        } else {
            item { WearStatusItem(spec, Icons.Rounded.Error, stringResource(R.string.operation_failed), tone = WearStatusTone.ERROR) }
        }
    }
}

@Composable
internal fun WearAffectedAppsPage(group: InstalledAppGroup?, onBack: () -> Unit) {
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.affected_applications)) }
        if (group == null) {
            item { WearStatusItem(spec, Icons.Rounded.Apps, stringResource(R.string.no_apps_found)) }
        } else {
            group.apps.forEach { app ->
                item {
                    WearInfoCard(spec) {
                        WearDetailField(app.label, app.displayIdentifier) {
                            PackageIcon(
                                packageName = if (app.isWebViewZygote) "android" else app.packageName,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.ExtraLargeIconSize),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WearAppActionsRoute(uid: Int, packageName: String, onBack: () -> Unit) {
    val viewModel = koinViewModel<AppProfileViewModel>(
        key = "wear-app-actions-$uid-$packageName",
        parameters = { parametersOf(uid, packageName) },
    )
    var error by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.operation_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is AppProfileUiEvent.Error) error = failed
        }
    }
    WearResultDialog(error?.let { WearResult(it, success = false) }) { error = null }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_app_operations)) }
        listOf(AppControlAction.LAUNCH to R.string.launch_app, AppControlAction.FORCE_STOP to R.string.force_stop_app).forEach { (action, label) ->
            item { WearActionButton(spec, Icons.Rounded.Apps, stringResource(label), { viewModel.dispatch(AppProfileUiAction.ControlApp(action)) }) }
        }
    }
}
