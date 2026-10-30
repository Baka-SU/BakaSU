package org.bakasu.bakasu.ui.wear

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.KernelStatus
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsUiState
import org.bakasu.bakasu.ui.viewmodel.WearPreferences
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.bakasu.bakasu.ui.wear.component.wearGroupGap
import org.koin.compose.koinInject

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

internal fun settingsCategoryTitle(category: WearSettingsCategory?): Int = when (category) {
    WearSettingsCategory.General -> R.string.wear_general
    WearSettingsCategory.Security -> R.string.wear_security
    WearSettingsCategory.Advanced -> R.string.advanced_options
    WearSettingsCategory.Display -> R.string.wear_display
    null -> R.string.settings
}

private enum class SettingsSection(val title: Int) {
    Updates(R.string.wear_settings_updates),
    LinksFiles(R.string.wear_settings_links_files),
    AppBehavior(R.string.wear_settings_app_behavior),
    Diagnostics(R.string.wear_settings_diagnostics),
    AccessProfiles(R.string.wear_settings_access_profiles),
    MountIsolation(R.string.wear_settings_mount_isolation),
    LoggingPrivacy(R.string.wear_settings_logging_privacy),
    PrivilegesStartup(R.string.wear_settings_privileges_startup),
    ManagerMounts(R.string.wear_settings_manager_mounts),
    KernelMaintenance(R.string.wear_settings_kernel_maintenance),
}

/** A settings switch row: section, label, phone summary, current value and dispatched action. */
private data class SettingsToggle(
    val section: SettingsSection,
    val label: Int,
    val summary: Int?,
    val checked: Boolean,
    val action: SettingsUiAction,
)

private data class SettingsPageLink(
    val section: SettingsSection,
    val label: Int,
    val route: WearRoute,
    val summary: String? = null,
)

private fun settingsToggleIcon(label: Int): ImageVector = when (label) {
    R.string.settings_check_manager_update -> Icons.Rounded.Update
    R.string.settings_check_beta_update -> Icons.Rounded.Science
    R.string.settings_check_module_update -> Icons.Rounded.Extension
    R.string.settings_soft_reboot -> Icons.Rounded.RestartAlt
    R.string.icon_switch_title -> Icons.Rounded.Apps
    R.string.settings_kernel_umount -> Icons.Rounded.DoNotDisturbOn
    R.string.settings_umount_modules_default -> Icons.Rounded.FolderDelete
    R.string.settings_sulog -> Icons.Rounded.Article
    R.string.settings_selinux_hide -> Icons.Rounded.Policy
    R.string.settings_adb_root -> Icons.Rounded.Adb
    R.string.settings_auto_jailbreak -> Icons.Rounded.ElectricalServices
    else -> Icons.Rounded.Settings
}

private fun settingsCategoryIcon(category: WearSettingsCategory): ImageVector = when (category) {
    WearSettingsCategory.General -> Icons.Rounded.Settings
    WearSettingsCategory.Security -> Icons.Rounded.Security
    WearSettingsCategory.Advanced -> Icons.Rounded.Build
    WearSettingsCategory.Display -> Icons.Rounded.Palette
}

private fun settingsRouteIcon(route: WearRoute): ImageVector = when (route) {
    WearRoute.DynamicManager -> Icons.Rounded.Security

    WearRoute.Color -> Icons.Rounded.Palette

    WearRoute.Dpi -> Icons.Rounded.FormatSize

    WearRoute.Logs -> Icons.Rounded.Article

    WearRoute.Bugreport -> Icons.Rounded.BugReport

    WearRoute.Templates -> Icons.Rounded.Fence

    WearRoute.Umount -> Icons.Rounded.FolderOff

    WearRoute.Uninstall -> Icons.Rounded.Delete

    is WearRoute.Selection -> when (route.selection) {
        WearSelection.TimeDisplay -> Icons.Rounded.Schedule
        WearSelection.ScreenShape -> Icons.Rounded.AspectRatio
        WearSelection.Language -> Icons.Rounded.Translate
        WearSelection.LinkMode -> Icons.Rounded.Language
        WearSelection.PickerMode -> Icons.Rounded.FolderOpen
        WearSelection.SuCompat -> Icons.Rounded.RemoveModerator
        WearSelection.Palette, WearSelection.ColorSpec -> Icons.Rounded.Settings
    }

    else -> Icons.Rounded.Settings
}

/**
 * The settings root lists the available categories and About; each category groups its switches and
 * navigation entries by purpose, omitting sections with no available controls.
 */
@Composable
internal fun WearSettingsPage(
    state: SettingsUiState,
    status: KernelStatus,
    onAction: (SettingsUiAction) -> Unit,
    onOpen: (WearRoute) -> Unit,
    onBack: () -> Unit,
    category: WearSettingsCategory? = null,
    preferences: WearPreferences = WearPreferences(),
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    backToTop: Boolean = false,
) {
    val themeConfig = koinInject<ThemeConfig>()
    val languageSummary = state.currentAppLocale?.let { it.getDisplayName(it) }
        ?: stringResource(R.string.language_system_default)
    val pickerSummary = pickerModeLabel(preferences.picker)
    val linkSummary = linkModeLabel(preferences.link)
    val suCompatSummary = suCompatModeLabel(state.suCompatMode)
    val colorSummary = if (state.useDynamicColor) {
        stringResource(R.string.dynamic_color_title)
    } else {
        "#%06X".format(themeConfig.seedColor and 0xFFFFFF)
    }
    val shapeSummary = screenShapeLabel(preferences.shape)
    val version = wearAppVersion()
    WearList(onBack = onBack, snap = true, listState = listState, backToTop = backToTop) { spec ->
        item { WearPageHeader(spec, stringResource(settingsCategoryTitle(category))) }
        if (category == null) {
            val categories = buildList {
                add(WearSettingsCategory.General)
                if (status.isFullFeatured) {
                    add(WearSettingsCategory.Security)
                    add(WearSettingsCategory.Advanced)
                }
            }
            LazySegmentedColumn(categories, { it.name }) { entry ->
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(settingsCategoryTitle(entry)),
                    { onOpen(WearRoute.Settings(entry)) },
                    icon = settingsCategoryIcon(entry),
                )
            }
            SegmentedColumn(listOf(WearRoute.Color, WearRoute.Dpi), { it.toString() }) { route ->
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(if (route == WearRoute.Color) R.string.wear_appearance_theme else R.string.wear_display_touch),
                    { onOpen(route) },
                    icon = settingsRouteIcon(route),
                )
            }
            wearGroupGap("about-gap")
            item {
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(R.string.about),
                    { onOpen(WearRoute.About) },
                    icon = Icons.Rounded.Info,
                    description = version,
                )
            }
        } else if (category == WearSettingsCategory.Display) {
            item { WearSectionHeader(spec, stringResource(R.string.wear_display_appearance)) }
            item {
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(R.string.theme_color),
                    { onOpen(WearRoute.Color) },
                    icon = settingsRouteIcon(WearRoute.Color),
                    description = colorSummary,
                )
            }
            item { WearSectionHeader(spec, stringResource(R.string.wear_display_screen_layout)) }
            SegmentedColumn(listOf(WearRoute.Dpi, WearRoute.Selection(WearSelection.ScreenShape)), { it.toString() }) { route ->
                WearSettingsJumpPageWidget(
                    spec,
                    stringResource(if (route == WearRoute.Dpi) R.string.wear_display_scaling else R.string.wear_screen_shape),
                    { onOpen(route) },
                    icon = settingsRouteIcon(route),
                    description = if (route == WearRoute.Dpi) null else shapeSummary,
                )
            }
            // Android 13+ keeps the app language in the system per-app settings, which Wear does not
            // offer, so the in-app choice is only shown where the shared locale handling applies it.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val language = WearRoute.Selection(WearSelection.Language)
                item { WearSectionHeader(spec, stringResource(R.string.settings_language)) }
                item {
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.settings_language),
                        { onOpen(language) },
                        icon = settingsRouteIcon(language),
                        description = languageSummary,
                    )
                }
            }
        } else {
            val toggles = buildList {
                if (category == WearSettingsCategory.General) {
                    add(
                        SettingsToggle(
                            SettingsSection.Updates,
                            R.string.settings_check_manager_update,
                            R.string.settings_check_manager_update_summary,
                            state.checkManagerUpdate,
                            SettingsUiAction.SetManagerUpdateCheck(!state.checkManagerUpdate),
                        ),
                    )
                    if (state.checkManagerUpdate) {
                        add(
                            SettingsToggle(
                                SettingsSection.Updates,
                                R.string.settings_check_beta_update,
                                R.string.settings_check_beta_update_summary,
                                state.checkBetaUpdate,
                                SettingsUiAction.SetBetaUpdateCheck(!state.checkBetaUpdate),
                            ),
                        )
                    }
                    add(
                        SettingsToggle(
                            SettingsSection.Updates,
                            R.string.settings_check_module_update,
                            R.string.settings_check_module_update_summary,
                            state.checkModuleUpdate,
                            SettingsUiAction.SetModuleUpdateCheck(!state.checkModuleUpdate),
                        ),
                    )
                    if (status.isFullFeatured) {
                        add(
                            SettingsToggle(
                                SettingsSection.AppBehavior,
                                R.string.settings_soft_reboot,
                                R.string.settings_soft_reboot_summary,
                                status.isLateLoadMode || state.useSoftReboot,
                                SettingsUiAction.SetUseSoftReboot(!state.useSoftReboot),
                            ),
                        )
                    }
                    add(
                        SettingsToggle(
                            SettingsSection.AppBehavior,
                            R.string.icon_switch_title,
                            R.string.icon_switch_summary,
                            state.useAltIcon,
                            SettingsUiAction.SetAlternateIcon(!state.useAltIcon),
                        ),
                    )
                }
                if (category == WearSettingsCategory.Security && status.isFullFeatured) {
                    add(
                        SettingsToggle(
                            SettingsSection.MountIsolation,
                            R.string.settings_kernel_umount,
                            R.string.settings_kernel_umount_summary,
                            state.isKernelUmountEnabled,
                            SettingsUiAction.SetKernelUmount(!state.isKernelUmountEnabled),
                        ),
                    )
                    add(
                        SettingsToggle(
                            SettingsSection.MountIsolation,
                            R.string.settings_umount_modules_default,
                            R.string.settings_umount_modules_default_summary,
                            state.defaultUmountModules,
                            SettingsUiAction.SetDefaultUmountModules(!state.defaultUmountModules),
                        ),
                    )
                    add(
                        SettingsToggle(
                            SettingsSection.LoggingPrivacy,
                            R.string.settings_sulog,
                            R.string.settings_sulog_summary,
                            state.isSuLogEnabled,
                            SettingsUiAction.SetSuLog(!state.isSuLogEnabled),
                        ),
                    )
                    // A short Wear summary keeps this row compact; the phone summary wraps around "SELinux".
                    add(
                        SettingsToggle(
                            SettingsSection.LoggingPrivacy,
                            R.string.settings_selinux_hide,
                            R.string.wear_settings_selinux_hide_summary,
                            state.isSelinuxHideEnabled,
                            SettingsUiAction.SetSelinuxHide(!state.isSelinuxHideEnabled),
                        ),
                    )
                }
                if (category == WearSettingsCategory.Advanced && status.isFullFeatured) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        add(
                            SettingsToggle(
                                SettingsSection.PrivilegesStartup,
                                R.string.settings_adb_root,
                                R.string.settings_adb_root_summary,
                                state.isAdbRootEnabled,
                                SettingsUiAction.SetAdbRoot(!state.isAdbRootEnabled),
                            ),
                        )
                    }
                    if (status.isLateLoadMode) {
                        add(
                            SettingsToggle(
                                SettingsSection.PrivilegesStartup,
                                R.string.settings_auto_jailbreak,
                                R.string.settings_auto_jailbreak_summary,
                                state.autoJailbreakEnabled,
                                SettingsUiAction.SetAutoJailbreak(!state.autoJailbreakEnabled),
                            ),
                        )
                    }
                }
            }
            // Pages that open from this category, with the current value or phone summary as description.
            val pages = buildList {
                if (category == WearSettingsCategory.General) {
                    if (status.isFullFeatured) add(SettingsPageLink(SettingsSection.Diagnostics, R.string.sulog, WearRoute.Logs))
                    add(SettingsPageLink(SettingsSection.Diagnostics, R.string.send_log, WearRoute.Bugreport))
                    add(
                        SettingsPageLink(
                            SettingsSection.LinksFiles,
                            R.string.wear_link_mode,
                            WearRoute.Selection(WearSelection.LinkMode),
                            linkSummary,
                        ),
                    )
                    add(
                        SettingsPageLink(
                            SettingsSection.LinksFiles,
                            R.string.wear_picker_mode,
                            WearRoute.Selection(WearSelection.PickerMode),
                            pickerSummary,
                        ),
                    )
                }
                if (category == WearSettingsCategory.Security && status.isFullFeatured) {
                    add(
                        SettingsPageLink(
                            SettingsSection.AccessProfiles,
                            R.string.settings_sucompat,
                            WearRoute.Selection(WearSelection.SuCompat),
                            suCompatSummary,
                        ),
                    )
                    add(SettingsPageLink(SettingsSection.AccessProfiles, R.string.settings_profile_template, WearRoute.Templates))
                }
                if (category == WearSettingsCategory.Advanced) {
                    if (status.isFullFeatured) {
                        add(SettingsPageLink(SettingsSection.ManagerMounts, R.string.dynamic_manager_title, WearRoute.DynamicManager))
                        if (state.isKernelUmountEnabled) add(SettingsPageLink(SettingsSection.ManagerMounts, R.string.umount_path_manager, WearRoute.Umount))
                    }
                    if (status.isFullFeatured && status.lkmMode == true && !status.isLateLoadMode) add(SettingsPageLink(SettingsSection.KernelMaintenance, R.string.settings_uninstall, WearRoute.Uninstall))
                }
            }
            SettingsSection.entries.forEach { section ->
                val sectionToggles = toggles.filter { it.section == section }
                val sectionPages = pages.filter { it.section == section }
                if (sectionToggles.isNotEmpty() || sectionPages.isNotEmpty()) {
                    item(key = section.name) { WearSectionHeader(spec, stringResource(section.title)) }
                    LazySegmentedColumn(sectionToggles, { it.label }) { toggle ->
                        val enabled = when (toggle.label) {
                            R.string.settings_kernel_umount -> state.kernelUmountStatus == "supported"
                            R.string.settings_sulog -> state.sulogStatus == "supported"
                            R.string.settings_selinux_hide -> state.selinuxHideStatus == "supported"
                            R.string.settings_adb_root -> state.adbRootStatus == "supported"
                            R.string.settings_soft_reboot -> !status.isLateLoadMode
                            else -> true
                        }
                        WearSettingsSwitchWidget(
                            spec,
                            stringResource(toggle.label),
                            toggle.checked,
                            { onAction(toggle.action) },
                            enabled,
                            icon = settingsToggleIcon(toggle.label),
                            secondaryLabel = toggle.summary?.let { stringResource(it) },
                        )
                    }
                    LazySegmentedColumn(sectionPages, { it.label }) { entry ->
                        WearSettingsJumpPageWidget(
                            spec,
                            stringResource(entry.label),
                            { onOpen(entry.route) },
                            icon = settingsRouteIcon(entry.route),
                            description = entry.summary ?: when (entry.route) {
                                WearRoute.Templates -> stringResource(R.string.settings_profile_template_summary)
                                WearRoute.DynamicManager -> stringResource(R.string.dynamic_manager_settings_summary)
                                WearRoute.Umount -> stringResource(R.string.umount_path_manager_summary)
                                else -> null
                            },
                        )
                    }
                }
            }
        }
    }
}
