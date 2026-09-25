package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.DeveloperBoard
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.HomeDashboardState
import com.resukisu.resukisu.ui.component.wear.WearIconText
import com.resukisu.resukisu.ui.component.wear.WearInfoCard
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearValueRow
import com.resukisu.resukisu.ui.component.wear.WearDetailField
import com.resukisu.resukisu.ui.component.wear.WearSectionHeader

@Composable
internal fun WearHomePage(
    state: HomeDashboardState,
    error: String?,
) {
    val unknown = stringResource(R.string.unknown)
    val rootStatus = if (state.systemStatus.isRootAvailable) {
        stringResource(R.string.wear_root_working)
    } else {
        stringResource(R.string.wear_root_unavailable)
    }
    val workingMode = when (state.systemStatus.lkmMode) {
        true -> stringResource(R.string.wear_mode_lkm)
        false -> stringResource(R.string.wear_mode_gki)
        null -> null
    }
    WearList(isLoading = !state.isInitialDataLoaded) { spec ->
        item { WearPageHeader(spec, Icons.TwoTone.Home, stringResource(R.string.home)) }
        if (state.isInitialDataLoaded) {
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.TwoTone.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(rootStatus, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (state.systemStatus.isRootAvailable && workingMode != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                workingMode,
                                modifier = Modifier.background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(6.dp),
                                ).padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        if (state.systemStatus.isRootAvailable) {
                            stringResource(
                                R.string.home_short_info,
                                state.systemInfo.superuserCount,
                                state.systemInfo.moduleCount,
                            )
                        } else {
                            stringResource(R.string.home_unsupported)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item { WearSectionHeader(spec, Icons.TwoTone.Info, stringResource(R.string.home_version_info)) }
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    WearDetailField(
                        Icons.TwoTone.Smartphone,
                        stringResource(R.string.home_device_model),
                        state.systemInfo.deviceModel.ifBlank { unknown },
                    )
                    WearDetailField(
                        Icons.TwoTone.Android,
                        stringResource(R.string.home_android_version),
                        state.systemInfo.androidVersion.ifBlank { unknown },
                    )
                }
            }
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    WearDetailField(
                        Icons.TwoTone.DeveloperBoard,
                        stringResource(R.string.home_kernel),
                        state.systemInfo.kernelRelease.ifBlank { unknown },
                    )
                    WearDetailField(
                        Icons.TwoTone.Memory,
                        stringResource(R.string.home_kernel_version),
                        state.systemStatus.ksuFullVersion ?: unknown,
                    )
                }
            }
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    WearDetailField(
                        Icons.TwoTone.Tag,
                        stringResource(R.string.home_manager_version),
                        state.systemInfo.managerVersion.let { (name, code, build) ->
                            "${name.ifBlank { unknown }} ($code/$build)"
                        },
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
    }
}
