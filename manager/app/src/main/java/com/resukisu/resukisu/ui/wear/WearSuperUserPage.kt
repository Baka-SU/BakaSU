package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AdminPanelSettings
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Security
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
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.PackageIcon
import com.resukisu.resukisu.ui.component.wear.WearIconText
import com.resukisu.resukisu.ui.component.wear.WearInfoCard
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearSectionHeader
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiState

@Composable
internal fun WearSuperUserPage(
    state: SuperUserUiState,
    isRootAvailable: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onAppClick: (Int, String) -> Unit,
) {
    WearList(isLoading = state.isLoading, isRefreshing = state.isRefreshing, onRefresh = onRefresh) { spec ->
        item { WearPageHeader(spec, Icons.TwoTone.AdminPanelSettings, stringResource(R.string.superuser)) }
        item {
            WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                WearIconText(
                    Icons.TwoTone.Security,
                    if (isRootAvailable) stringResource(R.string.wear_root_available)
                    else stringResource(R.string.wear_root_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { WearSectionHeader(spec, Icons.TwoTone.Group, stringResource(R.string.wear_apps)) }
        if (!error.isNullOrBlank()) {
            item { WearScaledItem(spec) { Text(error) } }
        } else if (!state.isLoading && state.appGroupList.isEmpty()) {
            item { WearScaledItem(spec) { Text(stringResource(R.string.wear_no_apps)) } }
        }
        items(state.appGroupList, key = { "${it.uid}:${it.primaryPackageName}" }) { group ->
            Button(
                modifier = Modifier.fillMaxWidth()
                    .transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                onClick = { onAppClick(group.uid, group.primaryPackageName) },
                icon = {
                    PackageIcon(
                        packageName = if (group.isWebViewZygote) "android" else group.mainApp.packageName,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                secondaryLabel = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (group.allowSu) Icons.Default.CheckCircle
                            else Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (group.allowSu) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (group.allowSu) stringResource(R.string.wear_allowed)
                            else stringResource(R.string.wear_denied),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            ) {
                Text(group.mainApp.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
