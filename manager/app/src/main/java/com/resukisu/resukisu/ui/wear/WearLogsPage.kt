package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.domain.model.SulogEventType
import com.resukisu.resukisu.domain.model.toSulogDisplayName
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.wear.WearIconText
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearSectionHeader
import com.resukisu.resukisu.ui.component.wear.wearButtonColors
import com.resukisu.resukisu.ui.viewmodel.SulogUiState

@Composable
internal fun WearLogsPage(
    state: SulogUiState,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSelectFile: (String) -> Unit,
) {
    WearList(isLoading = state.isLoading) { spec ->
        item { WearPageHeader(spec, Icons.AutoMirrored.TwoTone.Article, stringResource(R.string.sulog)) }
        item {
            WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowBack, stringResource(R.string.wear_back), onBack)
        }
        if (!state.errorMessage.isNullOrBlank()) {
            item { WearScaledItem(spec) { Text(state.errorMessage) } }
        }
        item {
            WearActionButton(
                spec,
                Icons.TwoTone.PowerSettingsNew,
                stringResource(if (state.isSulogEnabled) R.string.wear_disable_sulog else R.string.wear_enable_sulog),
                onClick = { onSetEnabled(!state.isSulogEnabled) },
                enabled = !state.isLoading,
            )
        }
        when {
            state.isLoading -> Unit
            !state.isSulogEnabled -> {
                item { WearScaledItem(spec) { Text(stringResource(R.string.sulog_disabled_title)) } }
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
                            colors = wearButtonColors(),
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
                    WearScaledItem(spec) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            WearIconText(
                                Icons.AutoMirrored.TwoTone.Article,
                                stringResource(eventLabel),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            entry.timestampText?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(entry.rawLine, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
