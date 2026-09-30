package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Search
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.wear.WearIconAction
import com.resukisu.resukisu.ui.component.wear.WearIconButtonGroup
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import com.resukisu.resukisu.ui.viewmodel.ModuleUiState

@Composable
internal fun WearModulesPage(
    state: ModuleUiState,
    error: String?,
    onRefresh: () -> Unit,
    onModuleClick: (String) -> Unit,
    onInstallClick: () -> Unit,
    onSearch: () -> Unit,
    onSort: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    WearList(
        isLoading = state.isLoading,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        backToTop = true, snap = true, listState = listState,
        onOpenPanel = onSort, panelLabel = stringResource(R.string.advanced_options),
    ) { spec ->
        item { WearPageHeader(spec, null, stringResource(R.string.module)) }
        // Search on the left and install on the right, as one compact button group.
        item {
            WearIconButtonGroup(spec, listOf(
                WearIconAction(Icons.TwoTone.Search, stringResource(R.string.search_modules), onSearch),
                WearIconAction(Icons.TwoTone.Add, stringResource(R.string.wear_install_module), onInstallClick),
            ))
        }
        when {
            !error.isNullOrBlank() -> item {
                WearStatusItem(spec, Icons.TwoTone.Warning, error)
            }
            state.moduleList.isEmpty() -> item {
                WearStatusItem(spec, Icons.TwoTone.Extension, stringResource(if (state.search.isNotBlank()) R.string.search_no_any_match else R.string.module_empty))
            }
        }
        items(state.moduleList, key = { it.id }) { module ->
            val status = when {
                module.remove -> R.string.wear_pending_removal
                module.enabled -> R.string.wear_enabled
                else -> R.string.wear_disabled
            }
            val statusIcon = when {
                module.remove -> Icons.TwoTone.Delete
                module.enabled -> Icons.Default.CheckCircle
                else -> Icons.Default.Block
            }
            val statusTint = when {
                module.remove -> MaterialTheme.colorScheme.error
                module.enabled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Button(
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                transformation = SurfaceTransformation(spec),
                onClick = { onModuleClick(module.id) },
                // Neutral surface; accent colors stay on the status icon and explicit actions.
                colors = ButtonDefaults.filledTonalButtonColors(),
                icon = { Icon(Icons.TwoTone.Extension, contentDescription = null) },
                secondaryLabel = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = statusTint,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(status),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            ) {
                Text(module.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
