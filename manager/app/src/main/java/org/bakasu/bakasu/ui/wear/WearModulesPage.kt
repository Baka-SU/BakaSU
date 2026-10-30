package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
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
    onOpenPanel: () -> Unit,
    onWebUi: (InstalledModule) -> Unit = {},
    onExecute: (InstalledModule) -> Unit = {},
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    panelContent: @Composable (() -> Unit)? = null,
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
        onOpenPanel = onOpenPanel,
        panelLabel = stringResource(R.string.wear_operations),
        panelContent = panelContent,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module)) }
        if (!error.isNullOrBlank()) {
            item {
                WearStatusItem(spec, Icons.Rounded.Error, error, tone = WearStatusTone.ERROR)
            }
        }
        if (state.moduleList.isEmpty() && error.isNullOrBlank()) {
            item {
                WearEmptyState(spec, Icons.Rounded.Extension, stringResource(R.string.module_empty))
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
 * alone; the version, any pending removal and the metamodule label follow in the quieter secondary
 * line.
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
                        Icons.Rounded.Language,
                        stringResource(R.string.wear_webui),
                    ) { onWebUi(module) },
                )
            }
            if (module.hasActionScript && onExecute != null) {
                add(
                    WearIconAction(
                        Icons.Rounded.PlayArrow,
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
            icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
            // The metamodule label follows the version on its baseline; a long version is cut so the
            // label always shows whole.
            secondaryLabel = if (details.isNotBlank() || module.metamodule) {
                {
                    if (details.isNotBlank()) {
                        Text(
                            details,
                            Modifier.weight(1f, fill = false).alignByBaseline(),
                            color = LocalContentColor.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (module.metamodule) {
                        WearBadge(
                            stringResource(R.string.wear_meta_badge),
                            Modifier.padding(start = if (details.isNotBlank()) 4.dp else 0.dp).alignByBaseline(),
                        )
                    }
                }
            } else {
                null
            },
        ) {
            // Long names wrap with balanced lines rather than leaving one character on the last line.
            Text(
                module.name,
                style = LocalTextStyle.current.merge(TextStyle(lineBreak = LineBreak.Heading)),
                textDecoration = if (module.remove) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (actions.isEmpty()) {
        button()
    } else {
        WearModuleSwipeActions(spec, scrolling, actions.first(), actions.getOrNull(1), button)
    }
}
