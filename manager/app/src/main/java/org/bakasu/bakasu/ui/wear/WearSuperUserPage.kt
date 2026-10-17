package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.automirrored.twotone.Sort
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Archive
import androidx.compose.material.icons.twotone.Backup
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.GridOn
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.ui.component.PackageIcon
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.component.WearBadge
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearIconButtonGroup
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.wearGroupGap

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearSuperUserPage(
    state: SuperUserUiState,
    error: String?,
    onRefresh: () -> Unit,
    onAppClick: (Int, String) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onLogs: () -> Unit,
    onBackup: () -> Unit,
    onFilter: () -> Unit,
    onActions: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    backToTop: Boolean = false,
) {
    // As on the phone: an empty list shows the loading indicator in place of the page; once it has
    // content, refreshing shows at the pull refresh, in the edge button.
    val loading = state.isRefreshing && state.appGroupList.isEmpty() && state.search.isEmpty()
    // A load that found no apps, and no failure, shows the design's empty screen.
    if (!state.isRefreshing && state.appGroupList.isEmpty() && state.search.isEmpty() && error.isNullOrBlank()) {
        WearAppsEmpty(onActions)
        return
    }
    WearList(
        isLoading = loading, isRefreshing = state.isRefreshing && !loading, onRefresh = onRefresh,
        onBack = onBack, onOpenPanel = onFilter, panelLabel = stringResource(R.string.wear_filter_sort),
        snap = true, listState = listState, backToTop = backToTop,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.superuser)) }
        // Search, SU log and allowlist backup as one compact button group, matching the Modules page actions.
        item {
            WearIconButtonGroup(
                spec,
                listOf(
                    WearIconAction(Icons.TwoTone.Search, stringResource(R.string.search_apps), onSearch),
                    WearIconAction(Icons.AutoMirrored.TwoTone.Article, stringResource(R.string.sulog), onLogs),
                    WearIconAction(Icons.TwoTone.Backup, stringResource(R.string.wear_allowlist_backup), onBackup),
                ),
            )
        }
        // The app list follows the actions directly, separated by the 8dp group gap of the Wear list guidance.
        wearGroupGap("apps-gap")
        if (!error.isNullOrBlank()) {
            item { WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR) }
        }
        if (state.appGroupList.isEmpty() && error.isNullOrBlank()) {
            item {
                WearStatusItem(spec, Icons.TwoTone.Apps, stringResource(R.string.no_apps_in_category))
            }
        }
        // An app button list: 32dp app icon, the app name and the phone's status labels.
        items(state.appGroupList, key = { "${it.uid}:${it.primaryPackageName}" }) { group ->
            WearAppItem(spec, group, onAppClick, group.uid in state.managerUids)
        }
    }
}

/**
 * One app group, as in the design: its icon, the app name as the title (wrapping to three
 * balanced lines when long) and, below it, the phone's status labels: ROOT, UMOUNT, CUSTOM, DEFAULT
 * and MANAGER. A granted app takes the primary container, the others the neutral tonal surface. The
 * large app icon uses the large icon content padding, so it sits centered in the rounded container.
 */
@Composable
internal fun TransformingLazyColumnItemScope.WearAppItem(
    spec: TransformationSpec,
    group: InstalledAppGroup,
    onAppClick: (Int, String) -> Unit,
    isManager: Boolean = false,
) {
    // The same labels and conditions as the phone's superuser list.
    val labels = buildList {
        if (group.allowSu) {
            add("ROOT")
        } else if (group.shouldUmount) {
            add("UMOUNT")
        }
        if (group.hasCustomProfile) {
            add("CUSTOM")
        } else if (!group.allowSu) {
            add("DEFAULT")
        }
        if (isManager) add("MANAGER")
    }
    Button(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(spec),
        onClick = { onAppClick(group.uid, group.primaryPackageName) },
        colors = if (group.allowSu) ButtonDefaults.filledVariantButtonColors() else ButtonDefaults.filledTonalButtonColors(),
        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
        icon = {
            PackageIcon(
                packageName = if (group.isWebViewZygote) "android" else group.mainApp.packageName,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.LargeIconSize),
            )
        },
        secondaryLabel = {
            labels.forEachIndexed { index, label ->
                WearBadge(
                    label,
                    if (index == 0) Modifier else Modifier.padding(start = 4.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        Text(
            group.mainApp.label,
            style = LocalTextStyle.current.merge(TextStyle(lineBreak = LineBreak.Heading)),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The design's empty superuser screen: an icon and "No apps found" in the screen center, and the
 * App actions button at the bottom, which still reaches search, sorting, the SU log, templates and
 * the allowlist backup.
 */
@Composable
private fun WearAppsEmpty(onActions: () -> Unit) {
    ScreenScaffold { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.TwoTone.Archive, contentDescription = null, modifier = Modifier.size(ButtonDefaults.LargeIconSize))
                Text(
                    stringResource(R.string.no_apps_found),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            CompactButton(
                onClick = onActions,
                modifier = Modifier.align(Alignment.BottomCenter),
                label = { Text(stringResource(R.string.wear_app_operations)) },
            )
        }
    }
}

/** The design's App actions page, each entry opening the existing page for it. */
@Composable
internal fun WearAppActionsPage(
    state: SuperUserUiState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onLogs: () -> Unit,
    onTemplates: () -> Unit,
    onBackup: () -> Unit,
) {
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_app_operations)) }
        item { WearSettingsJumpPageWidget(spec, stringResource(R.string.search_apps), onSearch, icon = Icons.TwoTone.Search) }
        item {
            WearSettingsJumpPageWidget(
                spec,
                stringResource(R.string.wear_filter_sort),
                onSort,
                icon = Icons.AutoMirrored.TwoTone.Sort,
                description = stringResource(state.currentSortType.displayNameRes),
            )
        }
        item {
            WearSettingsJumpPageWidget(
                spec,
                stringResource(R.string.sulog),
                onLogs,
                icon = Icons.AutoMirrored.TwoTone.Article,
                description = stringResource(R.string.wear_sulog_summary),
            )
        }
        item {
            WearSettingsJumpPageWidget(
                spec,
                stringResource(R.string.settings_profile_template),
                onTemplates,
                icon = Icons.TwoTone.GridOn,
                description = stringResource(R.string.wear_templates_summary),
            )
        }
        item {
            WearSettingsJumpPageWidget(
                spec,
                stringResource(R.string.wear_allowlist_backup),
                onBackup,
                icon = Icons.TwoTone.Save,
                description = stringResource(R.string.wear_allowlist_backup_summary),
            )
        }
    }
}
