package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.twotone.Warning
import com.resukisu.resukisu.ui.component.wear.WearStatusTone
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.KernelStatus
import com.resukisu.resukisu.ui.component.settings.LazySegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.component.wear.wearGroupGap
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import com.resukisu.resukisu.ui.viewmodel.WearPreferences
import org.koin.compose.koinInject

internal fun settingsCategoryTitle(category: String?): Int = when (category) {
    "general" -> R.string.wear_general
    "security" -> R.string.wear_security
    "advanced" -> R.string.wear_advanced
    "display" -> R.string.wear_display
    else -> R.string.settings
}

/** A settings switch row: label, phone summary, current value and the action it dispatches. */
private data class SettingsToggle(val label: Int, val summary: Int?, val checked: Boolean, val action: SettingsUiAction)

/**
 * The settings root lists the four categories and About; each category is a mixed list of switches
 * followed, after a group gap, by entries that open further pages with their current value.
 */
@Composable
internal fun WearSettingsPage(
    state: SettingsUiState,
    status: KernelStatus,
    message: String?,
    onAction: (SettingsUiAction) -> Unit,
    onOpenPage: (String) -> Unit,
    onBack: () -> Unit,
    category: String? = null,
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
    val colorSummary = if (state.useDynamicColor) stringResource(R.string.dynamic_color_title)
        else "#%06X".format(themeConfig.seedColor and 0xFFFFFF)
    val dpiSummary = stringResource(R.string.wear_ui_scale, state.currentDpi.toFloat() / state.systemDpi.coerceAtLeast(1))
    val version = wearAppVersion()
    WearList(onBack = onBack, snap = true, listState = listState, backToTop = backToTop) { spec ->
        item { WearPageHeader(spec, null, stringResource(settingsCategoryTitle(category))) }
        if (!message.isNullOrBlank()) item { WearScaledItem(spec) { Text(message) } }
        if (category == null) {
            SegmentedColumn(listOf("general", "security", "advanced", "display"), { it }) { name ->
                WearSettingsJumpPageWidget(spec, stringResource(settingsCategoryTitle(name)),
                    { onOpenPage("settings-$name") }, icon = when (name) {
                        "security" -> Icons.TwoTone.Security
                        "advanced" -> Icons.TwoTone.Build
                        "display" -> Icons.TwoTone.Palette
                        else -> Icons.TwoTone.Settings
                    })
            }
            wearGroupGap("about-gap")
            item {
                WearSettingsJumpPageWidget(spec, stringResource(R.string.about), { onOpenPage("about") },
                    icon = Icons.TwoTone.Info, description = version)
            }
        } else if (category == "display") {
            SegmentedColumn(listOf("color", "dpi"), { it }) { name ->
                WearSettingsJumpPageWidget(spec,
                    stringResource(if (name == "color") R.string.theme_color else R.string.wear_display_scaling),
                    { onOpenPage(name) }, description = if (name == "color") colorSummary else dpiSummary)
            }
        } else {
            val toggles = buildList {
                if (category == "general") {
                    add(SettingsToggle(R.string.settings_check_manager_update, R.string.settings_check_manager_update_summary,
                        state.checkManagerUpdate, SettingsUiAction.SetManagerUpdateCheck(!state.checkManagerUpdate)))
                    if (state.checkManagerUpdate) add(SettingsToggle(R.string.settings_check_beta_update,
                        R.string.settings_check_beta_update_summary, state.checkBetaUpdate,
                        SettingsUiAction.SetBetaUpdateCheck(!state.checkBetaUpdate)))
                    add(SettingsToggle(R.string.settings_check_module_update, R.string.settings_check_module_update_summary,
                        state.checkModuleUpdate, SettingsUiAction.SetModuleUpdateCheck(!state.checkModuleUpdate)))
                    if (status.isFullFeatured) add(SettingsToggle(R.string.settings_soft_reboot, R.string.settings_soft_reboot_summary,
                        status.isLateLoadMode || state.useSoftReboot, SettingsUiAction.SetUseSoftReboot(!state.useSoftReboot)))
                    add(SettingsToggle(R.string.icon_switch_title, R.string.icon_switch_summary,
                        state.useAltIcon, SettingsUiAction.SetAlternateIcon(!state.useAltIcon)))
                }
                if (category == "security" && status.isFullFeatured) {
                    add(SettingsToggle(R.string.settings_kernel_umount, R.string.settings_kernel_umount_summary,
                        state.isKernelUmountEnabled, SettingsUiAction.SetKernelUmount(!state.isKernelUmountEnabled)))
                    add(SettingsToggle(R.string.settings_umount_modules_default, R.string.settings_umount_modules_default_summary,
                        state.defaultUmountModules, SettingsUiAction.SetDefaultUmountModules(!state.defaultUmountModules)))
                    add(SettingsToggle(R.string.settings_sulog, R.string.settings_sulog_summary,
                        state.isSuLogEnabled, SettingsUiAction.SetSuLog(!state.isSuLogEnabled)))
                    add(SettingsToggle(R.string.settings_selinux_hide, R.string.settings_selinux_hide_summary,
                        state.isSelinuxHideEnabled, SettingsUiAction.SetSelinuxHide(!state.isSelinuxHideEnabled)))
                }
                if (category == "advanced" && status.isFullFeatured) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) add(SettingsToggle(R.string.settings_adb_root,
                        R.string.settings_adb_root_summary, state.isAdbRootEnabled, SettingsUiAction.SetAdbRoot(!state.isAdbRootEnabled)))
                    if (status.isLateLoadMode) add(SettingsToggle(R.string.settings_auto_jailbreak, R.string.settings_auto_jailbreak_summary,
                        state.autoJailbreakEnabled, SettingsUiAction.SetAutoJailbreak(!state.autoJailbreakEnabled)))
                }
            }
            LazySegmentedColumn(toggles, { it.label }) { toggle ->
                val enabled = when (toggle.label) {
                    R.string.settings_kernel_umount -> state.kernelUmountStatus == "supported"
                    R.string.settings_sulog -> state.sulogStatus == "supported"
                    R.string.settings_selinux_hide -> state.selinuxHideStatus == "supported"
                    R.string.settings_adb_root -> state.adbRootStatus == "supported"
                    R.string.settings_soft_reboot -> !status.isLateLoadMode
                    else -> true
                }
                WearSettingsSwitchWidget(spec, stringResource(toggle.label), toggle.checked, { onAction(toggle.action) }, enabled,
                    secondaryLabel = toggle.summary?.let { stringResource(it) })
            }
            // Pages that open from this category, with the current value or phone summary as description.
            val pages = buildList {
                if (category == "general") {
                    add(Triple(R.string.settings_language, "language", languageSummary))
                    add(Triple(R.string.sulog, "logs", null))
                    add(Triple(R.string.send_log, "bugreport", null))
                    add(Triple(R.string.wear_link_mode, "link-mode", linkSummary))
                    add(Triple(R.string.wear_picker_mode, "picker-mode", pickerSummary))
                }
                if (category == "security" && status.isFullFeatured) {
                    add(Triple(R.string.settings_sucompat, "sucompat", suCompatSummary))
                    add(Triple(R.string.settings_profile_template, "templates", null))
                }
                if (category == "advanced") {
                    if (status.isFullFeatured) {
                        add(Triple(R.string.dynamic_manager_title, "dynamic-manager", null))
                        if (state.isKernelUmountEnabled) add(Triple(R.string.umount_path_manager, "umount", null))
                    }
                    if (status.lkmMode == true && !status.isLateLoadMode) add(Triple(R.string.settings_uninstall, "uninstall", null))
                }
            }
            if (toggles.isNotEmpty() && pages.isNotEmpty()) wearGroupGap("pages-gap")
            LazySegmentedColumn(pages, { it.second }) { (label, page, summary) ->
                WearSettingsJumpPageWidget(spec, stringResource(label), { onOpenPage(page) }, description = summary ?: when (page) {
                    "templates" -> stringResource(R.string.settings_profile_template_summary)
                    "dynamic-manager" -> stringResource(R.string.dynamic_manager_settings_summary)
                    "umount" -> stringResource(R.string.umount_path_manager_summary)
                    else -> null
                })
            }
            if (category == "security" && !status.isFullFeatured) item {
                WearStatusItem(spec, Icons.TwoTone.Warning, stringResource(R.string.root_required), tone = WearStatusTone.WARNING)
            }
        }
    }
}
