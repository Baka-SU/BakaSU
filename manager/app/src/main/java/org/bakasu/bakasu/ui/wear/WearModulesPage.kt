package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Wysiwyg
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.wear.component.WearAutoMarqueeText
import org.bakasu.bakasu.ui.wear.component.WearBadge
import org.bakasu.bakasu.ui.wear.component.WearCatalogModuleIcon
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearIconAction
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearModuleSwipeActions
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearRefreshItem
import org.bakasu.bakasu.ui.wear.component.WearStatusTone
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.wearStatusColors

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
    hasOperationsPanel: Boolean = false,
) {
    // As on the phone: an empty list shows the loading indicator in place of the page; once it has
    // content, refreshing overlays a contained indicator without removing the list.
    val loading = state.isRefreshing && state.moduleList.isEmpty() && state.search.isEmpty()
    WearErrorDialog(error.takeUnless { state.isRefreshing })
    WearList(
        pageTitle = stringResource(R.string.module),
        isLoading = loading,
        isRefreshing = state.isRefreshing && !loading,
        onRefresh = onRefresh,
        backToTop = true,
        snap = true,
        listState = listState,
        onOpenPanel = onOpenPanel,
        panelLabel = stringResource(R.string.wear_operations),
        hasOperationsPanel = hasOperationsPanel,
    ) { spec ->
        if (state.moduleList.isEmpty() && error.isNullOrBlank()) {
            item {
                WearEmptyState(spec, Icons.Rounded.Extension, stringResource(R.string.module_empty))
            }
        }
        items(state.moduleList, key = { it.id }) { module ->
            WearModuleItem(spec, module, onModuleClick, listState.isScrollInProgress, onWebUi, onExecute, state.moduleIcons[module.id])
        }
    }
}

/**
 * One module row: the row opens the module details. The whole container takes the active color
 * while the module is enabled and applied, a subdued removal color while pending removal and the
 * neutral tonal surface otherwise. META shares the title row, and only the version appears below.
 * Pending changes are conveyed by color and the removal strike-through.
 */
@Composable
internal fun TransformingLazyColumnItemScope.WearModuleItem(
    spec: TransformationSpec,
    module: InstalledModule,
    onModuleClick: (String) -> Unit,
    scrolling: Boolean = false,
    onWebUi: ((InstalledModule) -> Unit)? = null,
    onExecute: ((InstalledModule) -> Unit)? = null,
    artwork: CatalogModule? = null,
) {
    val actions = buildList {
        if (module.enabled && !module.remove) {
            if (module.hasWebUi && onWebUi != null) {
                add(
                    WearIconAction(
                        Icons.AutoMirrored.Rounded.Wysiwyg,
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
    val button: @Composable () -> Unit = {
        WearSettingsJumpPageWidget(
            spec,
            module.name,
            onClick = { onModuleClick(module.id) },
            largeIcon = true,
            transformed = false,
            transformSurface = actions.isEmpty(),
            // A reveal row owns its transformation; a standalone row owns its own.
            colors = when {
                module.remove -> ButtonDefaults.filledTonalButtonColors(
                    // Pending removal has the lowest emphasis while retaining its error hue.
                    containerColor = lerp(
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.colorScheme.errorContainer,
                        0.2f,
                    ),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                module.enabled && !module.update -> ButtonDefaults.filledVariantButtonColors()

                else -> ButtonDefaults.filledTonalButtonColors()
            },
            iconContent = {
                val iconSize = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() + MaterialTheme.typography.labelSmall.lineHeight.toDp() }.coerceAtLeast(ButtonDefaults.LargeIconSize)
                val active = module.enabled && !module.update && !module.remove
                WearCatalogModuleIcon(
                    artwork?.iconUrl?.takeIf { it.isNotBlank() } ?: artwork?.registryMetadata?.iconUrl,
                    artwork?.repositoryUrl?.takeIf { artwork.registryMetadata != null },
                    iconSize,
                    fallbackContainerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    fallbackContentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            secondaryLabelContent = module.version.takeIf { it.isNotBlank() }?.let { version -> { WearAutoMarqueeText(version) } },
            titleContent = {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    WearAutoMarqueeText(
                        module.name,
                        Modifier.weight(1f, fill = false),
                        textDecoration = if (module.remove) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    if (module.metamodule) WearBadge(stringResource(R.string.wear_meta_badge))
                }
            },
        )
    }
    if (actions.isEmpty()) {
        WearRefreshItem(spec, Modifier.minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding), button)
    } else {
        WearModuleSwipeActions(spec, scrolling, actions.first(), actions.getOrNull(1), content = button)
    }
}
