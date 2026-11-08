package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.CompactButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.SulogEntry
import org.bakasu.bakasu.domain.model.SulogEventFilter
import org.bakasu.bakasu.domain.model.toSulogDisplayName
import org.bakasu.bakasu.ui.screen.sulogEntryDescription
import org.bakasu.bakasu.ui.screen.sulogEntryDetailText
import org.bakasu.bakasu.ui.screen.sulogEntryStatus
import org.bakasu.bakasu.ui.screen.sulogEntrySummaryTags
import org.bakasu.bakasu.ui.screen.sulogEntryTitle
import org.bakasu.bakasu.ui.screen.sulogFilterLabel
import org.bakasu.bakasu.ui.viewmodel.SulogLoading
import org.bakasu.bakasu.ui.viewmodel.SulogUiState
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearBadge
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearRefreshItem
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearSwipeToDelete
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * The SU log: page actions in its pull-down panel and the status of
 * the log, then one Wear Material 3 [TitleCard] per entry that opens its full fields. Log files,
 * type filters and cleaning live in [WearLogOptionsPage], as the phone keeps them in the app bar.
 */
@Composable
internal fun WearLogsPage(
    state: SulogUiState,
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPanel: (() -> Unit)? = null,
    onEntry: (SulogEntry) -> Unit,
    error: String? = null,
    hasOperationsPanel: Boolean = false,
    onEditSearch: (() -> Unit)? = null,
    loading: SulogLoading = SulogLoading.NONE,
) {
    val pageLoading = state.isLoading || state.isEnabling || loading == SulogLoading.PAGE || loading == SulogLoading.OPERATION
    val refreshing = loading == SulogLoading.REFRESH && !pageLoading
    val failures = listOfNotNull(error, state.errorMessage).filter { it.isNotBlank() }.distinct()
    WearErrorDialog(failures.takeIf { it.isNotEmpty() && !pageLoading && !refreshing }?.joinToString("\n"))
    val entryKeys = remember(state.visibleEntries, state.selectedFilePath) {
        val occurrences = mutableMapOf<String, Int>()
        state.visibleEntries.map { entry ->
            val occurrence = occurrences.getOrDefault(entry.rawLine, 0)
            occurrences[entry.rawLine] = occurrence + 1
            "${state.selectedFilePath}:${entry.rawLine}:$occurrence"
        }
    }
    // Every visit and log operation loads the page; only an explicit refresh keeps the list.
    WearList(
        isLoading = pageLoading,
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        onBack = onBack,
        onOpenPanel = onOpenPanel,
        panelLabel = stringResource(R.string.wear_operations),
        hasOperationsPanel = hasOperationsPanel,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(if (onEditSearch != null) R.string.sulog_search_placeholder else R.string.sulog)) }
        when (state.sulogStatus) {
            "unsupported" -> item { WearStatusItem(spec, Icons.Rounded.Warning, stringResource(R.string.sulog_unsupported_title)) }

            "managed" -> item { WearStatusItem(spec, Icons.Rounded.Warning, stringResource(R.string.feature_status_managed_summary)) }

            "forced" -> item { WearStatusItem(spec, Icons.Rounded.Warning, stringResource(R.string.feature_status_forced_summary)) }

            "supported" if !state.isSulogEnabled -> item {
                WearInfoCard(
                    spec,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = (ButtonDefaults.Height - CompactButtonDefaults.Height) / 2),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.wear_sulog_disabled_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        CompactButton(
                            onClick = onEnable,
                            label = { Text(stringResource(R.string.sulog_enable_action)) },
                            enabled = !state.isLoading && !state.isRefreshing && !state.isEnabling,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        )
                    }
                }
            }
        }
        if (onEditSearch != null) {
            item {
                WearActionButton(
                    spec,
                    Icons.Rounded.Search,
                    state.searchText,
                    onEditSearch,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                )
            }
        }
        if (state.visibleEntries.isNotEmpty() || failures.isEmpty()) {
            if (state.visibleEntries.isEmpty()) {
                item {
                    if (state.searchText.isNotBlank()) {
                        WearEmptyState(spec, Icons.Rounded.SearchOff, stringResource(R.string.search_no_any_match))
                    } else {
                        WearEmptyState(spec, Icons.Rounded.Article, stringResource(R.string.wear_no_logs))
                    }
                }
            }
            itemsIndexed(state.visibleEntries, key = { index, _ -> entryKeys[index] }) { _, entry ->
                val tags = sulogEntrySummaryTags(entry) + listOfNotNull(sulogEntryStatus(entry))
                val description = sulogEntryDescription(entry)?.takeIf { it.isNotBlank() }
                val timestamp = entry.timestampText?.takeIf { it.isNotBlank() }
                WearRefreshItem(spec) {
                    TitleCard(
                        onClick = { onEntry(entry) },
                        title = { Text(sulogEntryTitle(entry), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        transformation = SurfaceTransformation(spec),
                        // The clock time fits the time slot; the full date stays in the content.
                        time = entry.timestampText?.substringAfter(' ')?.let { { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                        subtitle = tags.takeIf { it.isNotEmpty() }?.let { labels ->
                            {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    labels.forEach { label ->
                                        WearBadge(label, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        },
                        content = if (description != null || timestamp != null) {
                            {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    description?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    timestamp?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyExtraSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** Every parsed field of one entry, as the phone's detail dialog shows it. */
@Composable
internal fun WearLogEntryPage(entry: SulogEntry?, onBack: () -> Unit) {
    WearList(onBack = onBack) { spec ->
        if (entry == null) {
            item { WearEmptyState(spec, Icons.Rounded.Article, stringResource(R.string.wear_no_logs)) }
            return@WearList
        }
        item { WearPageHeader(spec, sulogEntryTitle(entry)) }
        item {
            WearInfoCard(spec) {
                Text(
                    sulogEntryDetailText(entry),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** File selection and deletion target each date row independently. */
@Composable
internal fun WearLogOptionsPage(
    state: SulogUiState,
    onBack: () -> Unit,
    onSelectFile: (String) -> Unit,
    onToggleFilter: (SulogEventFilter) -> Unit,
    onClean: (String) -> Unit,
    loading: SulogLoading = SulogLoading.NONE,
) {
    var pendingPath by remember { mutableStateOf<String?>(null) }
    val listState = rememberTransformingLazyColumnState()
    val deleteLabel = stringResource(R.string.delete)
    val cleanDialog = rememberWearConfirmDialog(
        stringResource(R.string.sulog_clean_title),
        stringResource(R.string.confirm_delete),
        destructive = true,
        onConfirm = { pendingPath?.let(onClean) },
    )
    val selectedPath = state.selectedFilePath ?: state.files.firstOrNull()?.path
    WearList(isLoading = loading == SulogLoading.OPERATION || loading == SulogLoading.PAGE, onBack = onBack, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.sulog_filter_title)) }
        SegmentedColumn(SulogEventFilter.entries, { "filter-$it" }) { filter ->
            WearSettingsSwitchWidget(
                spec,
                sulogFilterLabel(filter),
                filter in state.selectedFilters,
                { onToggleFilter(filter) },
            )
        }
        if (state.files.isNotEmpty()) {
            item { WearSectionHeader(spec, stringResource(R.string.sulog_log_files)) }
            LazySegmentedColumn(state.files, { it.path }) { file ->
                WearSwipeToDelete(spec, listState.isScrollInProgress, deleteLabel, {
                    pendingPath = file.path
                    cleanDialog.show()
                }) {
                    RadioButton(
                        selected = file.path == selectedPath,
                        onSelect = { onSelectFile(file.path) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(file.name.toSulogDisplayName(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}
