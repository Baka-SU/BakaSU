package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.wear.component.WearBadge
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearIconButtonGroup
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearModuleSwipeActions
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearStatusItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearModulesPage(
    state: ModuleUiState,
    error: String?,
    onRefresh: () -> Unit,
    onModuleClick: (String) -> Unit,
    onInstallClick: () -> Unit,
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onWebUi: (InstalledModule) -> Unit = {},
    onExecute: (InstalledModule) -> Unit = {},
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    // As on the phone: an empty list shows the loading indicator in place of the page; once it has
    // content, refreshing shows at the pull refresh, in the edge button.
    val loading = state.isRefreshing && state.moduleList.isEmpty() && state.search.isEmpty()
    WearList(
        isLoading = loading,
        isRefreshing = state.isRefreshing && !loading,
        onRefresh = onRefresh,
        backToTop = true,
        snap = true,
        listState = listState,
        onOpenPanel = onSort,
        panelLabel = stringResource(R.string.wear_sort),
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module)) }
        // Search on the left and install on the right, as one compact button group.
        item {
            WearIconButtonGroup(
                spec,
                listOf(
                    WearIconAction(Icons.TwoTone.Search, stringResource(R.string.search_modules), onSearch),
                    WearIconAction(Icons.TwoTone.Add, stringResource(R.string.install), onInstallClick),
                ),
            )
        }
        if (!error.isNullOrBlank()) {
            item {
                WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR)
            }
        }
        if (state.moduleList.isEmpty() && error.isNullOrBlank()) {
            item {
                WearEmptyState(spec, Icons.TwoTone.Extension, stringResource(R.string.module_empty))
            }
        }
        items(state.moduleList, key = { it.id }) { module ->
            WearModuleItem(spec, module, onModuleClick, listState.isScrollInProgress, onWebUi, onExecute)
        }
    }
}

/**
 * One module row: the row opens the module details. The whole container takes the active color
 * while the module is enabled and the neutral tonal surface otherwise. The title is the module name
 * with its metamodule label, and the version and any pending removal follow in the quieter
 * secondary style.
 */
@Composable
internal fun TransformingLazyColumnItemScope.WearModuleItem(
    spec: TransformationSpec,
    module: InstalledModule,
    onModuleClick: (String) -> Unit,
    scrolling: Boolean = false,
    onWebUi: ((InstalledModule) -> Unit)? = null,
    onExecute: ((InstalledModule) -> Unit)? = null,
) {
    val actions = buildList {
        if (module.enabled && !module.remove) {
            if (module.hasWebUi && onWebUi != null) {
                add(
                    WearIconAction(
                        Icons.TwoTone.Language,
                        stringResource(R.string.wear_webui),
                    ) { onWebUi(module) },
                )
            }
            if (module.hasActionScript && onExecute != null) {
                add(
                    WearIconAction(
                        Icons.TwoTone.PlayArrow,
                        stringResource(R.string.action),
                    ) { onExecute(module) },
                )
            }
        }
    }
    val details = listOfNotNull(
        module.version.ifBlank { null },
        if (module.remove) stringResource(R.string.wear_pending_removal) else null,
    ).joinToString(" · ")
    val button: @Composable () -> Unit = {
        Button(
            onClick = { onModuleClick(module.id) },
            modifier = Modifier.fillMaxWidth()
                .then(
                    if (actions.isEmpty()) {
                        Modifier.transformedHeight(this, spec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                    } else {
                        Modifier
                    },
                ),
            // A reveal row owns its transformation; a standalone row owns its own.
            transformation = if (actions.isEmpty()) SurfaceTransformation(spec) else null,
            colors = if (module.enabled) ButtonDefaults.filledVariantButtonColors() else ButtonDefaults.filledTonalButtonColors(),
            icon = { Icon(Icons.TwoTone.Extension, contentDescription = null) },
            secondaryLabel = details.ifBlank { null }?.let { text ->
                {
                    Text(
                        text,
                        color = if (module.remove) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        ) {
            // Long names wrap with balanced lines rather than leaving one character on the last line;
            // the metamodule label follows the first line on its baseline.
            Text(
                module.name,
                Modifier.weight(1f, fill = false).alignByBaseline(),
                style = LocalTextStyle.current.merge(TextStyle(lineBreak = LineBreak.Heading)),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (module.metamodule) {
                WearBadge(stringResource(R.string.wear_meta_badge), Modifier.padding(start = 4.dp).alignByBaseline())
            }
        }
    }
    if (actions.isEmpty()) {
        button()
    } else {
        WearModuleSwipeActions(spec, scrolling, actions.first(), actions.getOrNull(1), button)
    }
}
