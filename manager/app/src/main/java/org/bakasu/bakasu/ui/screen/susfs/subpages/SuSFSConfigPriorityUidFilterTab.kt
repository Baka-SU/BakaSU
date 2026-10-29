package org.bakasu.bakasu.ui.screen.susfs.subpages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.component.SuSFSAppSelectionList
import org.bakasu.bakasu.ui.screen.susfs.RegisterSuSFSRefresh
import org.bakasu.bakasu.ui.screen.susfs.SuSFSRefreshRegistrar
import org.bakasu.bakasu.ui.screen.susfs.component.SuSFSDescriptionCard
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.showReplacingSnackbar
import org.bakasu.bakasu.ui.viewmodel.SuSFSUiAction
import org.bakasu.bakasu.ui.viewmodel.SuSFSViewModel
import org.bakasu.bakasu.ui.viewmodel.awaitSuSFSBoolean
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PriorityUidFilterTab(
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    onRegisterRefresh: SuSFSRefreshRegistrar,
) {
    val viewModel = koinViewModel<SuSFSViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current
    val failedMessage = stringResource(R.string.susfs_operation_failed)
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var configLoaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    RegisterSuSFSRefresh(onRegisterRefresh) { config, _ ->
        if (!saving) {
            selected = config.priority_uid_filter
            configLoaded = true
        }
        viewModel.dispatch(SuSFSUiAction.LoadUidFilterApps)
    }

    SuSFSAppSelectionList(
        nestedScrollConnection = nestedScrollConnection,
        innerPadding = innerPadding,
        installedApps = state.uidFilterApps,
        appsLoaded = state.uidFilterAppsLoaded,
        selected = selected,
        enabled = configLoaded && !saving,
        onToggle = { packageName ->
            val installed = state.uidFilterApps.mapTo(hashSetOf()) { it.packageName }
            val current = selected.intersect(installed)
            val updated = if (packageName in selected) current - packageName else current + packageName
            saving = true
            scope.launch {
                try {
                    if (awaitSuSFSBoolean(viewModel) { reply -> SuSFSUiAction.SetPriorityUidFilter(updated, reply) }) {
                        selected = updated
                    } else {
                        snackbar.showReplacingSnackbar(failedMessage)
                    }
                } finally {
                    saving = false
                }
            }
        },
    ) {
        SuSFSDescriptionCard(
            title = stringResource(R.string.susfs_priority_uid_filter_title),
            description = stringResource(R.string.susfs_priority_uid_filter_description),
        )
    }
}
