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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
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
internal fun WearCatalogModuleIcon(
    url: String?,
    origin: String?,
    size: Dp = 24.dp,
    fallbackContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    fallbackContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
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
        val colors = MaterialTheme.colorScheme
        val metrics = remember(bitmap) { bitmap?.let(::catalogIconMetrics) }
        val background = when {
            metrics == null -> fallbackContainerColor
            !metrics.transparent || metrics.monochrome -> Color.Transparent
            metrics.luminance < 0.45f -> colors.onSurface.copy(alpha = 1f)
            else -> colors.surfaceContainerHigh.copy(alpha = 1f)
        }
        // Preserve the detail tile's corner ratio at every size, including the 24dp list tile.
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * (18f / 52f)))
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap == null) {
                Icon(Icons.Rounded.Extension, null, Modifier.size(size * 0.55f), tint = fallbackContentColor)
            } else {
                Image(
                    bitmap.asImageBitmap(),
                    null,
                    Modifier.fillMaxSize().scale(metrics?.scale ?: 1f),
                    contentScale = ContentScale.Crop,
                    colorFilter = if (metrics?.transparent == true && metrics.monochrome) ColorFilter.tint(colors.onSurface) else null,
                )
            }
        }
    }
}

/** Objective artwork properties; theme colors are selected only by the composable. */
internal data class CatalogIconMetrics(val scale: Float, val transparent: Boolean, val monochrome: Boolean, val luminance: Float)

internal fun catalogIconMetrics(bitmap: Bitmap): CatalogIconMetrics {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    var left = bitmap.width
    var top = bitmap.height
    var right = -1
    var bottom = -1
    var transparent = false
    var gray = true
    val buckets = mutableSetOf<Int>()
    var luminance = 0f
    var weight = 0f
    var minRed = 255
    var maxRed = 0
    var minGreen = 255
    var maxGreen = 0
    var minBlue = 255
    var maxBlue = 0
    fun metrics(scale: Float) = CatalogIconMetrics(scale, transparent, gray || (buckets.size <= 4 && maxOf(maxRed - minRed, maxGreen - minGreen, maxBlue - minBlue) < 32), if (weight > 0f) luminance / weight else 0f)
    pixels.forEachIndexed { index, color ->
        val alpha = color ushr 24
        if (alpha < 255) transparent = true
        if (alpha >= 64) {
            val red = color shr 16 and 255
            val green = color shr 8 and 255
            val blue = color and 255
            minRed = minOf(minRed, red)
            maxRed = maxOf(maxRed, red)
            minGreen = minOf(minGreen, green)
            maxGreen = maxOf(maxGreen, green)
            minBlue = minOf(minBlue, blue)
            maxBlue = maxOf(maxBlue, blue)
            gray = gray && maxOf(red, green, blue) - minOf(red, green, blue) < 12
            if (buckets.size <= 4) buckets.add((red shr 4 shl 8) or (green shr 4 shl 4) or (blue shr 4))
            luminance += Color(color).luminance() * alpha
            weight += alpha
        }
        if (color ushr 24 != 0) {
            val x = index % bitmap.width
            val y = index / bitmap.width
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
    }
    if (right < left || !transparent) return metrics(1f)
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
        if (covered) return metrics(scale)
    }
    return metrics(1.12f)
}
