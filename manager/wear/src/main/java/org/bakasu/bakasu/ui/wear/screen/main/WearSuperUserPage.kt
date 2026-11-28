package org.bakasu.bakasu.ui.wear.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.lazy.TransformationSpec
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.ui.component.PackageIcon
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.component.WearBadge
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.wear.R as WearR

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearSuperUserPage(
    state: SuperUserUiState,
    error: String?,
    onAppClick: (Int, String) -> Unit,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    backToTop: Boolean = false,
    hasOperationsPanel: Boolean = false,
) {
    val loading = state.isRefreshing && state.appGroupList.isEmpty() && state.search.isEmpty()
    WearErrorDialog(error.takeUnless { state.isRefreshing })
    WearList(
        pageTitle = stringResource(R.string.superuser),
        isLoading = loading, isRefreshing = state.isRefreshing && !loading,
        onBack = onBack, onOpenPanel = onOpenPanel, panelLabel = stringResource(WearR.string.wear_operations),
        hasOperationsPanel = hasOperationsPanel,
        snap = true, listState = listState, backToTop = backToTop,
    ) { spec ->
        if (state.appGroupList.isEmpty() && error.isNullOrBlank()) {
            item {
                WearEmptyState(spec, Icons.Rounded.Archive, stringResource(R.string.no_apps_found))
            }
        }
        items(state.appGroupList, key = { "${it.uid}:${it.primaryPackageName}" }) { group ->
            WearAppItem(spec, group, onAppClick, group.uid in state.managerUids)
        }
    }
}

@Composable
internal fun TransformingLazyColumnItemScope.WearAppItem(
    spec: TransformationSpec,
    group: InstalledAppGroup,
    onAppClick: (Int, String) -> Unit,
    isManager: Boolean = false,
) {
    val labels = buildList {
        if (group.allowSu) {
            add(WearR.string.wear_badge_root)
        } else if (group.shouldUmount) {
            add(WearR.string.wear_badge_umount)
        }
        if (group.hasCustomProfile) {
            add(WearR.string.wear_badge_custom)
        } else if (!group.allowSu) {
            add(WearR.string.wear_badge_default)
        }
        if (isManager) add(WearR.string.wear_badge_manager)
    }
    WearSettingsJumpPageWidget(
        spec = spec,
        title = group.mainApp.label,
        onClick = { onAppClick(group.uid, group.primaryPackageName) },
        colors = if (group.allowSu) ButtonDefaults.filledVariantButtonColors() else ButtonDefaults.filledTonalButtonColors(),
        largeIcon = true,
        iconContent = {
            PackageIcon(
                packageName = if (group.isWebViewZygote) "android" else group.mainApp.packageName,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.LargeIconSize),
            )
        },
        secondaryLabelContent = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                labels.forEach { label ->
                    val colors = MaterialTheme.colorScheme
                    val (container, content) = when (label) {
                        WearR.string.wear_badge_root -> colors.primary to colors.onPrimary
                        WearR.string.wear_badge_default -> colors.primaryContainer to colors.onPrimaryContainer
                        WearR.string.wear_badge_umount -> colors.secondaryContainer to colors.onSecondaryContainer
                        WearR.string.wear_badge_custom -> colors.tertiaryContainer to colors.onTertiaryContainer
                        else -> colors.errorContainer to colors.onErrorContainer
                    }
                    WearBadge(
                        stringResource(label),
                        containerColor = container,
                        contentColor = content,
                    )
                }
            }
        },
    )
}
