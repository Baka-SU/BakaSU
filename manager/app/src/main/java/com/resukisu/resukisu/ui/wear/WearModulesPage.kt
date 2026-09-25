package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.viewmodel.ModuleUiState

@Composable
internal fun WearModulesPage(
    state: ModuleUiState,
    error: String?,
    onRefresh: () -> Unit,
    onModuleClick: (String) -> Unit,
) {
    WearList { spec ->
        item { WearPageHeader(spec, Icons.TwoTone.Extension, stringResource(R.string.module)) }
        if (state.isRefreshing && state.moduleList.isEmpty()) {
            item { WearScaledItem(spec) { CircularProgressIndicator() } }
        } else if (!error.isNullOrBlank()) {
            item { WearScaledItem(spec) { Text(error) } }
        } else if (state.moduleList.isEmpty()) {
            item { WearScaledItem(spec) { Text(stringResource(R.string.module_empty)) } }
        }
        items(state.moduleList, key = { it.id }) { module ->
            Button(
                modifier = Modifier.fillMaxWidth()
                    .transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                onClick = { onModuleClick(module.id) },
                icon = { Icon(Icons.TwoTone.Extension, contentDescription = null) },
                secondaryLabel = {
                    Text(
                        when {
                            module.remove -> stringResource(R.string.wear_pending_removal)
                            module.enabled -> stringResource(R.string.wear_enabled)
                            else -> stringResource(R.string.selinux_status_disabled)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            ) {
                Text(module.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Refresh, stringResource(R.string.wear_refresh), onRefresh)
        }
    }
}
