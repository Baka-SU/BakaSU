package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.usecase.LoadModuleIconUseCase
import org.koin.compose.koinInject

@Composable
internal fun WearCatalogModuleIcon(module: CatalogModule, size: Dp = 24.dp) {
    WearCatalogModuleIcon(module.iconUrl?.takeIf { it.isNotBlank() } ?: module.registryMetadata?.iconUrl, module.repositoryUrl.takeIf { module.registryMetadata != null }, size)
}

/** All Wear catalog surfaces use the existing validated icon cache and the same tonal fallback. */
@Composable
internal fun WearCatalogModuleIcon(url: String?, origin: String?, size: Dp = 24.dp) {
    Box(Modifier.size(size).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Extension, null, Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        if (!url.isNullOrBlank()) {
            val loadIcon = koinInject<LoadModuleIconUseCase>()
            key(url, origin) {
                val bitmap by produceState(loadIcon.cached(url, origin)) {
                    if (value == null) value = loadIcon(url, origin)
                }
                bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
            }
        }
    }
}
