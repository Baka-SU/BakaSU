package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.RemoveModerator
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Update
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearSectionHeader
import com.resukisu.resukisu.ui.component.wear.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState

@Composable
internal fun WearSettingsPage(
    state: SettingsUiState,
    message: String?,
    onAction: (SettingsUiAction) -> Unit,
    onLogsClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    WearList { spec ->
        item { WearPageHeader(spec, Icons.TwoTone.Settings, stringResource(R.string.settings)) }
        if (!message.isNullOrBlank()) item { WearScaledItem(spec) { Text(message) } }
        item {
            WearActionButton(
                spec,
                Icons.AutoMirrored.TwoTone.Article,
                stringResource(R.string.sulog),
                onLogsClick,
            )
        }
        item { WearSectionHeader(spec, Icons.TwoTone.Tune, stringResource(R.string.wear_general)) }
        item {
            WearSettingsSwitchWidget(spec,
                label = stringResource(R.string.settings_check_manager_update),
                checked = state.checkManagerUpdate,
                onCheckedChange = { onAction(SettingsUiAction.SetManagerUpdateCheck(it)) },
                icon = Icons.TwoTone.Update,
            )
        }
        item {
            WearSettingsSwitchWidget(spec,
                label = stringResource(R.string.settings_check_module_update),
                checked = state.checkModuleUpdate,
                onCheckedChange = { onAction(SettingsUiAction.SetModuleUpdateCheck(it)) },
                icon = Icons.TwoTone.Extension,
            )
        }
        item {
            WearSettingsSwitchWidget(spec,
                label = stringResource(R.string.settings_soft_reboot),
                checked = state.useSoftReboot,
                onCheckedChange = { onAction(SettingsUiAction.SetUseSoftReboot(it)) },
                icon = Icons.TwoTone.RestartAlt,
            )
        }
        item { WearSectionHeader(spec, Icons.TwoTone.Security, stringResource(R.string.wear_security)) }
        item {
            WearSettingsSwitchWidget(spec,
                label = stringResource(R.string.settings_kernel_umount),
                checked = state.isKernelUmountEnabled,
                onCheckedChange = { onAction(SettingsUiAction.SetKernelUmount(it)) },
                icon = Icons.TwoTone.RemoveModerator,
            )
        }
        item {
            WearSettingsSwitchWidget(spec,
                label = stringResource(R.string.settings_sulog),
                checked = state.isSuLogEnabled,
                onCheckedChange = { onAction(SettingsUiAction.SetSuLog(it)) },
                icon = Icons.AutoMirrored.TwoTone.Article,
            )
        }
        item {
            WearSettingsSwitchWidget(spec,
                label = stringResource(R.string.settings_selinux_hide),
                checked = state.isSelinuxHideEnabled,
                onCheckedChange = { onAction(SettingsUiAction.SetSelinuxHide(it)) },
                icon = Icons.TwoTone.Security,
            )
        }
        item { WearSectionHeader(spec, Icons.TwoTone.Settings, stringResource(R.string.wear_advanced)) }
        item {
            WearSettingsSwitchWidget(spec,
                label = stringResource(R.string.settings_adb_root),
                checked = state.isAdbRootEnabled,
                onCheckedChange = { onAction(SettingsUiAction.SetAdbRoot(it)) },
                icon = Icons.TwoTone.Adb,
            )
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Info, stringResource(R.string.about), onAboutClick)
        }
    }
}
