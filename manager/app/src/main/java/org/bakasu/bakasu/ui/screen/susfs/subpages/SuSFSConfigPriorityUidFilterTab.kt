package org.bakasu.bakasu.ui.screen.susfs.subpages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.SuSFSPriorityApp
import org.bakasu.bakasu.ui.component.PackageIcon
import org.bakasu.bakasu.ui.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.component.settings.SettingsBaseWidget
import org.bakasu.bakasu.ui.component.settings.SettingsTextFieldWidget
import org.bakasu.bakasu.ui.component.settings.lazySegmentColumn
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
    val search = remember { TextFieldState() }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var configLoaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    RegisterSuSFSRefresh(onRegisterRefresh) { config, _ ->
        if (!saving) {
            selected = config.priority_uid_filter
            configLoaded = true
        }
        viewModel.dispatch(SuSFSUiAction.LoadPriorityApps)
    }

    val apps = remember(state.priorityApps, selected, search.text.toString()) {
        val installed = state.priorityApps.associateBy { it.packageName }
        val missing = (selected - installed.keys).map { SuSFSPriorityApp(it, it) }
        (missing + state.priorityApps).filter {
            it.label.contains(search.text, ignoreCase = true) ||
                it.packageName.contains(search.text, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
    ) {
        item { Spacer(Modifier.height(innerPadding.calculateTopPadding())) }
        item {
            SuSFSDescriptionCard(
                title = stringResource(R.string.susfs_priority_uid_filter_title),
                description = stringResource(R.string.susfs_priority_uid_filter_description),
            )
        }
        item {
            SegmentedColumn {
                item {
                    SettingsTextFieldWidget(
                        state = search,
                        title = stringResource(R.string.susfs_priority_uid_filter_search),
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
            }
        }
        if (!state.priorityAppsLoaded) {
            item {
                SegmentedColumn {
                    item {
                        SettingsBaseWidget(
                            title = stringResource(R.string.susfs_priority_uid_filter_loading),
                            iconPlaceholder = false,
                        )
                    }
                }
            }
        }
        lazySegmentColumn(items = apps, key = { _, app -> app.packageName }) { _, app ->
            val checked = app.packageName in selected
            SettingsBaseWidget(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    role = Role.Checkbox
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                },
                title = app.label,
                description = app.packageName,
                enabled = configLoaded && state.priorityAppsLoaded && !saving,
                selected = checked,
                leadingContent = {
                    PackageIcon(app.packageName, contentDescription = null, modifier = Modifier.size(40.dp))
                },
                onClick = {
                    val installed = state.priorityApps.mapTo(hashSetOf()) { it.packageName }
                    val current = selected.intersect(installed)
                    val updated = if (checked) current - app.packageName else current + app.packageName
                    saving = true
                    scope.launch {
                        try {
                            if (awaitSuSFSBoolean(viewModel) { reply ->
                                    SuSFSUiAction.SetPriorityUidFilter(updated, reply)
                                }
                            ) {
                                selected = updated
                            } else {
                                snackbar.showReplacingSnackbar(failedMessage)
                            }
                        } finally {
                            saving = false
                        }
                    }
                },
            )
        }
        item { Spacer(Modifier.height(innerPadding.calculateBottomPadding())) }
    }
}
