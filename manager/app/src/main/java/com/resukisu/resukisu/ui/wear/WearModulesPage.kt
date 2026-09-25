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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.R
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
) {
    WearList(isLoading = state.isLoading, isRefreshing = state.isRefreshing, onRefresh = onRefresh) { spec ->
        item { WearPageHeader(spec, Icons.TwoTone.Extension, stringResource(R.string.module)) }
        when {
            !error.isNullOrBlank() -> item {
                WearStatusItem(spec, Icons.TwoTone.Warning, error)
            }
            state.moduleList.isEmpty() -> item {
                WearStatusItem(spec, Icons.TwoTone.Extension, stringResource(R.string.module_empty))
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
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                onClick = { onModuleClick(module.id) },
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
