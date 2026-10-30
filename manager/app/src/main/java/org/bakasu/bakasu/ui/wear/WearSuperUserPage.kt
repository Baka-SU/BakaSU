package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.ui.component.PackageIcon
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.component.WearBadge
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearIconButtonGroup
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
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
    onOpenPanel: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    backToTop: Boolean = false,
    panelContent: @Composable (() -> Unit)? = null,
) {
    // As on the phone: an empty list shows the loading indicator in place of the page; once it has
    // content, refreshing shows at the pull refresh, in the edge button.
    val loading = state.isRefreshing && state.appGroupList.isEmpty() && state.search.isEmpty()
    WearErrorDialog(error.takeUnless { state.isRefreshing })
    WearList(
        isLoading = loading, isRefreshing = state.isRefreshing && !loading, onRefresh = onRefresh,
        onBack = onBack, onOpenPanel = onOpenPanel, panelLabel = stringResource(R.string.wear_operations),
        panelContent = panelContent,
        snap = true, listState = listState, backToTop = backToTop,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.superuser)) }
        if (state.appGroupList.isEmpty() && error.isNullOrBlank()) {
            item {
                WearEmptyState(spec, Icons.Rounded.Archive, stringResource(R.string.no_apps_found))
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
            add(R.string.wear_badge_root)
        } else if (group.shouldUmount) {
            add(R.string.wear_badge_umount)
        }
        if (group.hasCustomProfile) {
            add(R.string.wear_badge_custom)
        } else if (!group.allowSu) {
            add(R.string.wear_badge_default)
        }
        if (isManager) add(R.string.wear_badge_manager)
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
            val badgeStyle = MaterialTheme.typography.labelSmall.let {
                it.copy(fontSize = it.fontSize * 0.8f, lineHeight = it.lineHeight * 0.8f)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                labels.forEach { label ->
                    val colors = MaterialTheme.colorScheme
                    val (container, content) = when (label) {
                        R.string.wear_badge_root -> colors.primary to Color.Black
                        R.string.wear_badge_default -> colors.primaryContainer to Color.White
                        R.string.wear_badge_umount -> colors.secondaryContainer to colors.onSecondaryContainer
                        R.string.wear_badge_custom -> colors.tertiaryContainer to colors.onTertiaryContainer
                        else -> colors.errorContainer to colors.onErrorContainer
                    }
                    WearBadge(
                        stringResource(label),
                        containerColor = container,
                        contentColor = content,
                        style = badgeStyle,
                    )
                }
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
