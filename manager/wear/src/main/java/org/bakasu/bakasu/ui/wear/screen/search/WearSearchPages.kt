package org.bakasu.bakasu.ui.wear.screen.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
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
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.screen.main.WearAppItem
import org.bakasu.bakasu.ui.wear.screen.main.WearModuleItem

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
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
    WearList(
        onBack = onBack,
        snap = true,
        isLoading = state.isRefreshing && state.appGroupList.isEmpty(),
        isRefreshing = state.isRefreshing && state.appGroupList.isNotEmpty(),
    ) { spec ->
        searchHeader(spec, title, state.search, onEdit)
        when {
            !error.isNullOrBlank() -> item { WearStatusItem(spec, Icons.Rounded.Error, error, tone = WearStatusTone.ERROR) }
            state.appGroupList.isEmpty() -> item { WearEmptyState(spec, Icons.Rounded.SearchOff, stringResource(R.string.no_apps_found)) }
        }
        items(state.appGroupList, key = { "${it.uid}:${it.primaryPackageName}" }) { group ->
            WearAppItem(spec, group, onAppClick, group.uid in state.managerUids)
        }
    }
}

@Composable
internal fun WearModuleSearchResults(
    state: ModuleUiState,
    error: String?,
    onEdit: () -> Unit,
    onModuleClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.search_modules)
    WearList(
        onBack = onBack,
        snap = true,
        isLoading = state.isRefreshing && state.moduleList.isEmpty(),
        isRefreshing = state.isRefreshing && state.moduleList.isNotEmpty(),
    ) { spec ->
        searchHeader(spec, title, state.search, onEdit)
        when {
            !error.isNullOrBlank() -> item { WearStatusItem(spec, Icons.Rounded.Error, error, tone = WearStatusTone.ERROR) }
            state.moduleList.isEmpty() -> item { WearEmptyState(spec, Icons.Rounded.SearchOff, stringResource(R.string.search_no_any_match)) }
        }
        items(state.moduleList, key = { it.id }) { module -> WearModuleItem(spec, module, onModuleClick) }
    }
}

private fun TransformingLazyColumnScope.searchHeader(spec: TransformationSpec, title: String, query: String, onEdit: () -> Unit) {
    item { WearPageHeader(spec, title) }
    item {
        WearActionButton(spec, Icons.Rounded.Search, query, onEdit, colors = ButtonDefaults.filledTonalButtonColors())
    }
}
