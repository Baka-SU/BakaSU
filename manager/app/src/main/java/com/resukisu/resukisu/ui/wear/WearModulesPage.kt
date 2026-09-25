package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material.icons.twotone.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.lazy.TransformationSpec
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
    onInstallClick: () -> Unit,
) {
    WearList(
        isLoading = state.isLoading,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
    ) { spec ->
        item { WearPageHeader(spec, Icons.TwoTone.Extension, stringResource(R.string.module)) }
        item { WearInstallModuleButton(spec, onInstallClick) }
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

@Composable
private fun TransformingLazyColumnItemScope.WearInstallModuleButton(
    spec: TransformationSpec,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.wear_install_module)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            modifier = Modifier.width(84.dp).height(48.dp)
                .transformedHeight(this@WearInstallModuleButton, spec)
                .semantics { contentDescription = label },
            transformation = SurfaceTransformation(spec),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(Icons.TwoTone.Add, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
    }
}
