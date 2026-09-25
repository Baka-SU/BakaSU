package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.DeveloperBoard
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.HomeDashboardState
import com.resukisu.resukisu.ui.component.wear.WearIconText
import com.resukisu.resukisu.ui.component.wear.WearInfoCard
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearValueRow

@Composable
internal fun WearHomePage(
    state: HomeDashboardState,
    error: String?,
    onRefresh: () -> Unit,
) {
    val unknown = stringResource(R.string.unknown)
    val rootStatus = if (state.systemStatus.isRootAvailable) {
        stringResource(R.string.wear_root_available)
    } else {
        stringResource(R.string.wear_root_unavailable)
    }
    WearList { spec ->
        item { WearPageHeader(spec, Icons.TwoTone.Home, stringResource(R.string.home)) }
        if (!state.isInitialDataLoaded) {
            item { WearScaledItem(spec) { CircularProgressIndicator() } }
            item { WearScaledItem(spec) { Text(stringResource(R.string.wear_loading)) } }
        } else {
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    WearIconText(
                        Icons.TwoTone.Security,
                        rootStatus,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    WearIconText(
                        if (state.systemStatus.isFullFeatured) Icons.TwoTone.CheckCircle
                        else Icons.TwoTone.Warning,
                        if (state.systemStatus.isFullFeatured) {
                            stringResource(R.string.home_working)
                        } else {
                            stringResource(R.string.home_unsupported)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    WearIconText(
                        Icons.TwoTone.Smartphone,
                        state.systemInfo.deviceModel.ifBlank { unknown },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    WearValueRow(
                        Icons.TwoTone.Android,
                        stringResource(R.string.wear_android),
                        state.systemInfo.androidVersion.ifBlank { unknown },
                    )
                    WearValueRow(
                        Icons.TwoTone.DeveloperBoard,
                        stringResource(R.string.home_kernel),
                        state.systemInfo.kernelRelease.ifBlank { unknown },
                    )
                }
            }
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    WearValueRow(
                        Icons.TwoTone.Memory,
                        stringResource(R.string.wear_kernel_su),
                        state.systemStatus.ksuFullVersion ?: unknown,
                    )
                    WearValueRow(
                        Icons.TwoTone.Tag,
                        stringResource(R.string.app_name),
                        state.systemInfo.managerVersion.first.ifBlank { unknown },
                    )
                }
            }
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    WearValueRow(
                        Icons.TwoTone.Group,
                        stringResource(R.string.superuser),
                        state.systemInfo.superuserCount.toString(),
                    )
                    WearValueRow(
                        Icons.TwoTone.Extension,
                        stringResource(R.string.module),
                        state.systemInfo.moduleCount.toString(),
                    )
                }
            }
        }
        if (!error.isNullOrBlank()) item {
            WearScaledItem(spec) { Text(error, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Refresh, stringResource(R.string.wear_refresh), onRefresh)
        }
    }
}
