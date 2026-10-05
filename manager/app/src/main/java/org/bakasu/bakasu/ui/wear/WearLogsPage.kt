package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.FilterList
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.SearchOff
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.transformedHeight
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.SulogEntry
import org.bakasu.bakasu.domain.model.SulogEventFilter
import org.bakasu.bakasu.domain.model.toSulogDisplayName
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearIconButtonGroup
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSectionHeader
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.screen.sulogEntryDescription
import org.bakasu.bakasu.ui.screen.sulogEntryDetailText
import org.bakasu.bakasu.ui.screen.sulogEntryStatus
import org.bakasu.bakasu.ui.screen.sulogEntrySummaryTags
import org.bakasu.bakasu.ui.screen.sulogEntryTitle
import org.bakasu.bakasu.ui.screen.sulogFilterLabel
import org.bakasu.bakasu.ui.viewmodel.SulogUiState

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * The SU log: a compact action group (search, refresh, options), the on/off entry and the status of
 * the log, then one Wear Material 3 [TitleCard] per entry that opens its full fields. Log files,
 * type filters and cleaning live in [WearLogOptionsPage], as the phone keeps them in the app bar.
 */
@Composable
internal fun WearLogsPage(
    state: SulogUiState,
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOptions: () -> Unit,
    onEntry: (SulogEntry) -> Unit,
    error: String? = null,
) {
    // As on the phone: the log refreshes by the pull gesture, whose progress shows in the edge
    // button, and the first load shows only the loading indicator.
    WearList(isLoading = state.isLoading, isRefreshing = state.isRefreshing && !state.isLoading,
        onRefresh = onRefresh, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.sulog)) }
        item {
            WearIconButtonGroup(spec, listOf(
                WearIconAction(Icons.TwoTone.Search, stringResource(R.string.sulog_search_placeholder), onSearch),
                WearIconAction(Icons.TwoTone.FilterList, stringResource(R.string.sulog_filter_title), onOptions),
            ))
        }
        // The active query; tapping it clears the search.
        if (state.searchText.isNotBlank()) item {
            WearActionButton(spec, Icons.TwoTone.SearchOff, state.searchText, onClearSearch,
                secondaryText = stringResource(R.string.wear_clear_search),
                colors = ButtonDefaults.filledTonalButtonColors())
        }
        when (state.sulogStatus) {
            // As on the phone, the log only offers enabling; disabling is the setting's switch.
            "supported" -> if (!state.isSulogEnabled) item {
                WearActionButton(spec, Icons.TwoTone.PowerSettingsNew, stringResource(R.string.wear_enable_sulog), onEnable)
            }
            "unsupported" -> item {
                WearStatusItem(spec, Icons.TwoTone.Warning, stringResource(R.string.sulog_unsupported_title),
                    tone = WearStatusTone.WARNING)
            }
            "managed" -> item {
                WearStatusItem(spec, Icons.TwoTone.Warning, stringResource(R.string.feature_status_managed_summary),
                    tone = WearStatusTone.WARNING)
            }
        }
        if (state.sulogStatus == "supported" && !state.isSulogEnabled) item {
            WearStatusItem(spec, Icons.TwoTone.Warning, stringResource(R.string.sulog_disabled_title))
        }
        val failure = listOfNotNull(error, state.errorMessage).filter { it.isNotBlank() }
        if (failure.isNotEmpty()) item {
            WearStatusItem(spec, Icons.TwoTone.Error,
                (listOf(stringResource(R.string.sulog_failed_to_load)) + failure).joinToString("\n"),
                tone = WearStatusTone.ERROR)
        }
        if (failure.isEmpty()) {
            if (state.visibleEntries.isEmpty()) item {
                if (state.searchText.isNotBlank()) WearStatusItem(spec, Icons.TwoTone.SearchOff, stringResource(R.string.search_no_any_match))
                else WearStatusItem(spec, Icons.AutoMirrored.TwoTone.Article, stringResource(R.string.wear_no_logs))
            }
            itemsIndexed(state.visibleEntries, key = { index, entry -> "$index:${entry.key}" }) { _, entry ->
                val tags = (sulogEntrySummaryTags(entry) + listOfNotNull(sulogEntryStatus(entry))).joinToString(" · ")
                TitleCard(
                    onClick = { onEntry(entry) },
                    title = { Text(sulogEntryTitle(entry), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    // The clock time fits the time slot; the full date stays in the content.
                    time = entry.timestampText?.substringAfter(' ')?.let { { Text(it) } },
                    subtitle = tags.takeIf { it.isNotEmpty() }?.let {
                        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                ) {
                    sulogEntryDescription(entry)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    entry.timestampText?.let {
                        Text(it, style = MaterialTheme.typography.bodyExtraSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
            item { WearStatusItem(spec, Icons.AutoMirrored.TwoTone.Article, stringResource(R.string.wear_no_logs)) }
            return@WearList
        }
        item { WearPageHeader(spec, sulogEntryTitle(entry)) }
        item {
            WearInfoCard(spec) {
                Text(sulogEntryDetailText(entry), style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** The log file, the event types shown and cleaning the selected file. */
@Composable
internal fun WearLogOptionsPage(
    state: SulogUiState,
    onBack: () -> Unit,
    onSelectFile: (String) -> Unit,
    onToggleFilter: (SulogEventFilter) -> Unit,
    onClean: () -> Unit,
) {
    val cleanDialog = rememberWearConfirmDialog(stringResource(R.string.sulog_clean_title),
        stringResource(R.string.confirm_delete), onClean)
    val selectedPath = state.selectedFilePath ?: state.files.firstOrNull()?.path
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.sulog_filter_title)) }
        SegmentedColumn(SulogEventFilter.entries, { "filter-$it" }) { filter ->
            WearSettingsSwitchWidget(spec, sulogFilterLabel(filter), filter in state.selectedFilters,
                { onToggleFilter(filter) })
        }
        if (state.files.isNotEmpty()) {
            item { WearSectionHeader(spec, stringResource(R.string.sulog_log_files)) }
            LazySegmentedColumn(state.files, { it.path }) { file ->
                RadioButton(
                    selected = file.path == selectedPath,
                    onSelect = { onSelectFile(file.path) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(file.name.toSulogDisplayName(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        item {
            WearActionButton(spec, Icons.TwoTone.DeleteSweep, stringResource(R.string.sulog_clean_title),
                cleanDialog::show, enabled = state.selectedFilePath != null && !state.isRefreshing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    iconColor = MaterialTheme.colorScheme.onErrorContainer,
                ))
        }
    }
}
