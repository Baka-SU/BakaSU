package org.bakasu.bakasu.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import org.bakasu.bakasu.domain.usecase.LoadModuleIconUseCase
import org.koin.compose.koinInject

/** Decorative thumbnail; missing, pending and failed images occupy no space. */
@Composable
fun CatalogModuleIcon(url: String?, origin: String?, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) return
    val loadIcon = koinInject<LoadModuleIconUseCase>()
    key(url, origin) {
        val bitmap by produceState(loadIcon.cached(url, origin)) {
            if (value == null) value = loadIcon(url, origin)
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}
