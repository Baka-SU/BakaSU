package org.bakasu.bakasu.ui.wear

import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.ModuleUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.viewmodel.SortType
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiAction
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** Page actions have no category headings; detailed controls stay behind their own entries. */
@Composable
internal fun WearOperationsPanel(
    page: WearOperationsPage,
    onBack: () -> Unit,
    onOpen: (WearRoute) -> Unit,
    onSearch: () -> Unit,
    onInstall: () -> Unit,
    rootAvailable: Boolean,
    fullFeatured: Boolean,
    susfsAvailable: Boolean,
) {
    val actions = when (page) {
        WearOperationsPage.Home -> listOfNotNull(
            if (fullFeatured && susfsAvailable) WearIconAction(Icons.Rounded.VisibilityOff, stringResource(R.string.susfs_config_setting_title), { onOpen(WearRoute.SuSFS) }) else null,
            WearIconAction(Icons.Rounded.Article, stringResource(R.string.sulog), { onOpen(WearRoute.Logs) }, fullFeatured),
            WearIconAction(Icons.Rounded.RestartAlt, stringResource(R.string.reboot), { onOpen(WearRoute.Reboot) }, rootAvailable),
        )

        WearOperationsPage.SuperUser -> listOf(
            WearIconAction(Icons.Rounded.Search, stringResource(R.string.search_apps), onSearch),
            WearIconAction(Icons.Rounded.Article, stringResource(R.string.sulog), { onOpen(WearRoute.Logs) }),
            WearIconAction(Icons.Rounded.Backup, stringResource(R.string.wear_allowlist_backup), { onOpen(WearRoute.AllowlistBackup) }),
            WearIconAction(Icons.Rounded.FilterList, stringResource(R.string.wear_filter_sort), { onOpen(WearRoute.AppFilter) }),
        )

        WearOperationsPage.Modules -> listOf(
            WearIconAction(Icons.Rounded.Search, stringResource(R.string.search_modules), onSearch),
            WearIconAction(Icons.Rounded.Add, stringResource(R.string.install), onInstall),
            WearIconAction(Icons.Rounded.Cloud, stringResource(R.string.module_repo), { onOpen(WearRoute.ModuleRepo) }),
            WearIconAction(Icons.Rounded.Sort, stringResource(R.string.wear_sort), { onOpen(WearRoute.ModuleSort) }),
        )

        WearOperationsPage.Logs -> listOf(
            WearIconAction(Icons.Rounded.Search, stringResource(R.string.sulog_search_placeholder), onSearch),
            WearIconAction(Icons.Rounded.FilterList, stringResource(R.string.sulog_filter_title), { onOpen(WearRoute.LogOptions) }),
        )
    }
    WearList(onClosePanel = onBack, snap = true, listState = rememberPanelListState()) { spec ->
        SegmentedColumn(actions, { it.label }) { action ->
            WearSettingsJumpPageWidget(spec, action.label, action.onClick, icon = action.icon, enabled = action.enabled)
        }
    }
}

@Composable
internal fun WearSuperUserPanel(state: SuperUserUiState, onBack: () -> Unit, onAction: (SuperUserUiAction) -> Unit) {
    val listState = rememberPanelListState()
    WearList(onBack = onBack, onClosePanel = onBack, snap = true, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_filter_sort)) }
        item {
            WearSettingsSwitchWidget(
                spec,
                stringResource(R.string.show_system_apps),
                state.showSystemApps,
                { onAction(SuperUserUiAction.SetShowSystemApps(it)) },
            )
        }
        LazySegmentedColumn(SortType.entries, { it }) { sort ->
            RadioButton(
                selected = sort == state.currentSortType,
                onSelect = { onAction(SuperUserUiAction.SetSort(sort)) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                transformation = SurfaceTransformation(spec),
                icon = { Icon(Icons.Rounded.Sort, contentDescription = null) },
                label = { Text(stringResource(sort.displayNameRes), maxLines = 3, overflow = TextOverflow.Ellipsis) },
            )
        }
        item {
            WearSettingsSwitchWidget(
                spec,
                stringResource(R.string.reverse_order),
                state.reverseOrder,
                { onAction(SuperUserUiAction.SetReverseOrder(it)) },
            )
        }
    }
}

@Composable
internal fun WearModulePanel(state: ModuleUiState, onBack: () -> Unit, onAction: (ModuleUiAction) -> Unit) {
    val listState = rememberPanelListState()
    WearList(onBack = onBack, onClosePanel = onBack, snap = true, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_sort)) }
        item {
            WearSettingsSwitchWidget(
                spec,
                stringResource(R.string.module_sort_action_first),
                state.sortActionFirst,
                { onAction(ModuleUiAction.Sort(state.sortEnabledFirst, it)) },
            )
        }
        item {
            WearSettingsSwitchWidget(
                spec,
                stringResource(R.string.module_sort_enabled_first),
                state.sortEnabledFirst,
                { onAction(ModuleUiAction.Sort(it, state.sortActionFirst)) },
            )
        }
    }
}

@Composable
internal fun WearRebootPanel(rootAvailable: Boolean, onBack: () -> Unit, onReboot: (String) -> Unit) {
    val listState = rememberPanelListState()
    val context = LocalContext.current
    val methods = linkedMapOf(
        R.string.reboot to "",
        R.string.reboot_soft to "soft_reboot",
        R.string.reboot_recovery to "recovery",
        R.string.reboot_bootloader to "bootloader",
        R.string.reboot_download to "download",
        R.string.reboot_edl to "edl",
    )
    @Suppress("DEPRECATION")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        context.getSystemService(PowerManager::class.java)?.isRebootingUserspaceSupported == true
    ) {
        methods[R.string.reboot_userspace] = "userspace"
    }
    WearList(onBack = onBack, onClosePanel = onBack, snap = true, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.reboot)) }
        methods.forEach { (label, reason) ->
            item {
                WearActionButton(
                    spec,
                    Icons.Rounded.RestartAlt,
                    stringResource(label),
                    { onReboot(reason) },
                    rootAvailable,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                )
            }
        }
    }
}

@Composable
private fun rememberPanelListState(): androidx.wear.compose.foundation.lazy.TransformingLazyColumnState {
    val state = rememberTransformingLazyColumnState()
    val active = LocalScreenIsActive.current
    // A menu opens at its heading even when its saveable state survived a previous visit or exit animation.
    LaunchedEffect(active) { if (active) state.scrollToItem(0) }
    return state
}
