package org.bakasu.bakasu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.SearchOff
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.lazy.TransformationSpec
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * The results of an app search on their own page, as Wear search flows do: the title, the query as a
 * button that reopens the input, then the matching apps. Leaving the page clears the query, so the
 * main list stays unfiltered.
 */
@Composable
internal fun WearAppSearchResults(
    state: SuperUserUiState,
    error: String?,
    onEdit: () -> Unit,
    onAppClick: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.search_apps)
    WearList(onBack = onBack, snap = true) { spec ->
        searchHeader(spec, title, state.search, onEdit)
        when {
            !error.isNullOrBlank() -> item { WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR) }
            state.appGroupList.isEmpty() -> item { WearStatusItem(spec, Icons.TwoTone.SearchOff, stringResource(R.string.no_apps_found)) }
        }
        items(state.appGroupList, key = { "${it.uid}:${it.primaryPackageName}" }) { group ->
            WearAppItem(spec, group, onAppClick)
        }
    }
}

/** The module counterpart of [WearAppSearchResults]. */
@Composable
internal fun WearModuleSearchResults(
    state: ModuleUiState,
    error: String?,
    onEdit: () -> Unit,
    onModuleClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.search_modules)
    WearList(onBack = onBack, snap = true) { spec ->
        searchHeader(spec, title, state.search, onEdit)
        when {
            !error.isNullOrBlank() -> item { WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR) }
            state.moduleList.isEmpty() -> item { WearStatusItem(spec, Icons.TwoTone.SearchOff, stringResource(R.string.search_no_any_match)) }
        }
        items(state.moduleList, key = { it.id }) { module -> WearModuleItem(spec, module, onModuleClick) }
    }
}

private fun TransformingLazyColumnScope.searchHeader(spec: TransformationSpec, title: String, query: String, onEdit: () -> Unit) {
    item { WearPageHeader(spec, title) }
    item {
        WearActionButton(spec, Icons.TwoTone.Search, query, onEdit, colors = ButtonDefaults.filledTonalButtonColors())
    }
}
