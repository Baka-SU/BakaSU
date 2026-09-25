package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.domain.model.SulogEventType
import com.resukisu.resukisu.domain.model.toSulogDisplayName
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.wear.WearIconText
import com.resukisu.resukisu.ui.component.wear.WearInfoCard
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearSectionHeader
import com.resukisu.resukisu.ui.viewmodel.SulogUiState

@Composable
internal fun WearLogsPage(
    state: SulogUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEnable: () -> Unit,
    onSelectFile: (String) -> Unit,
) {
    WearList { spec ->
        item { WearPageHeader(spec, Icons.AutoMirrored.TwoTone.Article, stringResource(R.string.sulog)) }
        item {
            WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowBack, stringResource(R.string.wear_back), onBack)
        }
        when {
            state.isLoading -> {
                item { WearScaledItem(spec) { CircularProgressIndicator() } }
                item { WearScaledItem(spec) { Text(stringResource(R.string.wear_loading)) } }
            }
            !state.errorMessage.isNullOrBlank() -> {
                item { WearScaledItem(spec) { Text(state.errorMessage) } }
                item {
                    WearActionButton(spec, Icons.TwoTone.Refresh, stringResource(R.string.network_retry), onRefresh)
                }
            }
            !state.isSulogEnabled -> {
                item { WearScaledItem(spec) { Text(stringResource(R.string.sulog_disabled_title)) } }
                item {
                    WearActionButton(
                        spec,
                        Icons.AutoMirrored.TwoTone.Article,
                        stringResource(R.string.sulog_enable_action),
                        onEnable,
                    )
                }
            }
            else -> {
                if (state.files.size > 1) {
                    item { WearSectionHeader(spec, Icons.AutoMirrored.TwoTone.Article, stringResource(R.string.sulog_log_files)) }
                    items(state.files, key = { it.path }) { file ->
                        Button(
                            modifier = Modifier.fillMaxWidth()
                                .transformedHeight(this, spec),
                            transformation = SurfaceTransformation(spec),
                            onClick = { onSelectFile(file.path) },
                            icon = { Icon(Icons.AutoMirrored.TwoTone.Article, contentDescription = null) },
                        ) { Text(file.name.toSulogDisplayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (state.visibleEntries.isEmpty()) {
                    item { WearScaledItem(spec) { Text(stringResource(R.string.wear_no_logs)) } }
                }
                itemsIndexed(
                    state.visibleEntries,
                    key = { index, entry -> "$index:${entry.key}" },
                ) { _, entry ->
                    val eventLabel = when (entry.eventType) {
                        SulogEventType.RootExecve -> R.string.sulog_filter_root_execve
                        SulogEventType.SuCompat -> R.string.sulog_filter_sucompat
                        SulogEventType.IoctlGrantRoot -> R.string.sulog_filter_ioctl_grant_root
                        SulogEventType.DaemonEvent -> R.string.sulog_filter_daemon_restart
                        SulogEventType.Dropped -> R.string.sulog_event_dropped
                        SulogEventType.Unknown -> R.string.sulog_entry_unknown_event
                    }
                    WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                        WearIconText(
                            Icons.AutoMirrored.TwoTone.Article,
                            stringResource(eventLabel),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        entry.timestampText?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                        Text(entry.rawLine, maxLines = 5)
                    }
                }
                item {
                    WearActionButton(spec, Icons.TwoTone.Refresh, stringResource(R.string.wear_refresh), onRefresh)
                }
            }
        }
    }
}
