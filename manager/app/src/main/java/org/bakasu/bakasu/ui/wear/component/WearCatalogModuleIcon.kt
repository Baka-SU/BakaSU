package org.bakasu.bakasu.ui.wear.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
    key(url, origin) {
        val bitmap = if (!url.isNullOrBlank()) {
            val loadIcon = koinInject<LoadModuleIconUseCase>()
            val loaded by produceState(loadIcon.cached(url, origin)) {
                if (value == null) value = loadIcon(url, origin)
            }
            loaded
        } else {
            null
        }
        // Preserve the detail tile's corner ratio at every size, including the 24dp list tile.
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * (18f / 52f)))
                .background(if (bitmap == null) MaterialTheme.colorScheme.primaryContainer else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap == null) {
                Icon(Icons.Rounded.Extension, null, Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                val scale = remember(bitmap) { catalogIconScale(bitmap) }
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().scale(scale), contentScale = ContentScale.Crop)
            }
        }
    }
}

/** Fill small transparent outer margins without stretching the artwork or removing inner transparency. */
private fun catalogIconScale(bitmap: Bitmap): Float {
    if (!bitmap.hasAlpha()) return 1f
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    var left = bitmap.width
    var top = bitmap.height
    var right = -1
    var bottom = -1
    pixels.forEachIndexed { index, color ->
        if (color ushr 24 != 0) {
            val x = index % bitmap.width
            val y = index / bitmap.width
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
    }
    if (right < left) return 1f
    // ContentScale.Crop already fills the shorter dimension. Only compensate for exposed margins.
    val side = minOf(bitmap.width, bitmap.height).toFloat()
    val filledWidth = bitmap.width - 2 * maxOf(left, bitmap.width - 1 - right)
    val filledHeight = bitmap.height - 2 * maxOf(top, bitmap.height - 1 - bottom)
    val marginScale = (side / minOf(filledWidth, filledHeight).coerceAtLeast(1)).coerceIn(1f, 1.12f)
    // A supplied corner can also be rounder than our tile even when its outer bounds are full.
    val corner = 18f / 52f
    for (step in 0..12) {
        val scale = maxOf(marginScale, 1f + step / 100f)
        val covered = (0..4).all { angle ->
            val radians = angle * PI / 8
            val x = corner * (1f - cos(radians).toFloat())
            val y = corner * (1f - sin(radians).toFloat())
            listOf(x to y, (1f - x) to y, x to (1f - y), (1f - x) to (1f - y)).all { (px, py) ->
                val sourceX = (bitmap.width / 2f + (px - 0.5f) * side / scale).toInt().coerceIn(0, bitmap.width - 1)
                val sourceY = (bitmap.height / 2f + (py - 0.5f) * side / scale).toInt().coerceIn(0, bitmap.height - 1)
                pixels[sourceY * bitmap.width + sourceX] ushr 24 >= 240
            }
        }
        if (covered) return scale
    }
    return 1.12f
}
