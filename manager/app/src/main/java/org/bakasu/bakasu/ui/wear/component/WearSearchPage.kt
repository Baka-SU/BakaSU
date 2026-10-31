package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.WearSearchViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Shared Wear search layout; business filters and results stay with the calling feature. */
@Composable
fun WearSearchPage(
    title: String,
    historyScope: String,
    initialQuery: String,
    onBack: () -> Unit,
    onAdvanced: (() -> Unit)? = null,
    onSearch: (String) -> Unit,
) {
    val viewModel = koinViewModel<WearSearchViewModel>(key = "wear-search:$historyScope", parameters = { parametersOf(historyScope) })
    val history by viewModel.history.collectAsStateWithLifecycle()
    var query by rememberSaveable(historyScope) { mutableStateOf(initialQuery) }
    var editing by rememberSaveable { mutableStateOf(false) }
    fun submit(value: String) {
        if (value.isBlank()) return
        viewModel.record(value)
        onSearch(value.trim())
    }
    val input = rememberWearRemoteInput(title, onUnavailable = { editing = true }) {
        query = it
        submit(it)
    }
    if (editing) {
        WearSubPage({ editing = false }) {
            WearTextInputPage(title, query, imeAction = ImeAction.Search, autoFocus = true) {
                query = it
                editing = false
                submit(it)
            }
        }
        return
    }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, title) }
        item {
            WearScaledItem(spec) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = input, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors()) {
                        Text(query.ifBlank { stringResource(R.string.wear_search_input) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalIconButton(onClick = { submit(query) }, enabled = query.isNotBlank(), modifier = Modifier.size(ButtonDefaults.Height)) {
                        Icon(Icons.Rounded.Search, contentDescription = title)
                    }
                }
            }
        }
        if (onAdvanced != null) {
            item {
                WearActionButton(spec, Icons.Rounded.Tune, stringResource(R.string.wear_search_advanced), onAdvanced, colors = ButtonDefaults.filledTonalButtonColors())
            }
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_search_history)) }
        history.forEach { previous ->
            item(key = previous) {
                WearActionButton(spec, Icons.Rounded.History, previous, { submit(previous) }, colors = ButtonDefaults.filledTonalButtonColors())
            }
        }
    }
}
