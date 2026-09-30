package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.PackageIcon
import com.resukisu.resukisu.ui.component.wear.WearIconAction
import com.resukisu.resukisu.ui.component.wear.WearIconButtonGroup
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearSectionHeader
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import com.resukisu.resukisu.ui.component.wear.wearAppButtonColors
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiState

@Composable
internal fun WearSuperUserPage(
    state: SuperUserUiState,
    error: String?,
    onRefresh: () -> Unit,
    onAppClick: (Int, String) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onLogs: () -> Unit,
    onFilter: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    WearList(isLoading = state.isLoading, isRefreshing = state.isRefreshing, onRefresh = onRefresh,
        onBack = onBack, onOpenPanel = onFilter, panelLabel = stringResource(R.string.advanced_options),
        snap = true, listState = listState,
    ) { spec ->
        item { WearPageHeader(spec, null, stringResource(R.string.superuser)) }
        // Search and SU log as one compact button group, matching the Modules page actions.
        item {
            WearIconButtonGroup(spec, listOf(
                WearIconAction(Icons.TwoTone.Search, stringResource(R.string.search_apps), onSearch),
                WearIconAction(Icons.AutoMirrored.TwoTone.Article, stringResource(R.string.sulog), onLogs),
            ))
        }
        item { WearSectionHeader(spec, Icons.TwoTone.Group, stringResource(R.string.wear_apps)) }
        if (!error.isNullOrBlank()) {
            item { WearStatusItem(spec, Icons.TwoTone.Warning, error) }
        } else if (!state.isLoading && state.appGroupList.isEmpty()) {
            item {
                WearStatusItem(spec, Icons.TwoTone.Group,
                    stringResource(if (state.search.isNotBlank()) R.string.search_no_any_match else R.string.wear_no_apps))
            }
        }
        // An app button list: 32dp app icon, one-line name and a one-line authorization status.
        items(state.appGroupList, key = { "${it.uid}:${it.primaryPackageName}" }) { group ->
            Button(
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                transformation = SurfaceTransformation(spec),
                onClick = { onAppClick(group.uid, group.primaryPackageName) },
                colors = wearAppButtonColors(group.mainApp.packageName, group.allowSu),
                icon = {
                    PackageIcon(
                        packageName = if (group.isWebViewZygote) "android" else group.mainApp.packageName,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                    )
                },
                secondaryLabel = {
                    Icon(
                        imageVector = if (group.allowSu) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (group.allowSu) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(if (group.allowSu) R.string.wear_allowed else R.string.wear_denied),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            ) {
                Text(group.mainApp.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
