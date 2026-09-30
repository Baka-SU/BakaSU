package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.resukisu.resukisu.ui.theme.ThemeConfig
import org.koin.compose.koinInject
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * A non-interactive information container for Wear Material 3 version 1.5.0.
 * A null [containerColor] uses the surface container together with the custom background.
 */
@Composable
fun TransformingLazyColumnItemScope.WearInfoCard(
    transformationSpec: TransformationSpec,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    border: BorderStroke? = null,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(10.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val transformation = SurfaceTransformation(transformationSpec)
    val color = containerColor ?: MaterialTheme.colorScheme.surfaceContainer
    val config = koinInject<ThemeConfig>()
    val showImage = containerColor == null && config.customBackgroundUri != null
    val image = rememberAsyncImagePainter(config.customBackgroundUri, contentScale = ContentScale.Crop)
    val painter = remember(transformation, shape, color, border) {
        transformation.createContainerPainter(ColorPainter(color), shape, border)
    }
    val imagePainter = remember(transformation, shape, image) {
        transformation.createContainerPainter(image, shape, border = null)
    }
    val dim = .6f + .4f * config.backgroundDim
    val shade = remember(transformation, shape, dim) {
        transformation.createContainerPainter(ColorPainter(Color.Black.copy(alpha = dim)), shape, border = null)
    }
    Column(
        modifier = Modifier
            .graphicsLayer { with(transformation) { applyContainerTransformation() } }
            .then(modifier.transformedHeight(this, transformationSpec))
            .fillMaxWidth()
            .drawBehind {
                with(painter) { draw(size) }
                if (showImage) {
                    with(imagePainter) { draw(size) }
                    with(shade) { draw(size) }
                }
            }
            .graphicsLayer {
                this.shape = shape
                clip = true
                with(transformation) { applyContentTransformation() }
            }
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** How a status card is colored: neutral surface, yellow warning or red error. */
enum class WearStatusTone { NEUTRAL, WARNING, ERROR }

/**
 * Wear Material 3 has no warning color role, so warnings use a fixed yellow with dark text
 * (about 12:1 contrast). Errors use the theme's error container pair.
 */
private val WarningContainer = Color(0xFFFFD54F)
private val OnWarningContainer = Color(0xFF231B00)

/** The container and content colors for [tone]; a null container keeps the neutral card surface. */
@Composable
fun wearStatusColors(tone: WearStatusTone): Pair<Color?, Color> = when (tone) {
    WearStatusTone.NEUTRAL -> null to MaterialTheme.colorScheme.onSurfaceVariant
    WearStatusTone.WARNING -> WarningContainer to OnWarningContainer
    WearStatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

/** A single message card, used for list empty/error states and the Home warnings. */
@Composable
fun TransformingLazyColumnItemScope.WearStatusItem(
    transformationSpec: TransformationSpec,
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    tone: WearStatusTone = WearStatusTone.NEUTRAL,
) {
    val (container, content) = wearStatusColors(tone)
    WearInfoCard(transformationSpec, modifier.fillMaxWidth(), containerColor = container) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = if (tone == WearStatusTone.NEUTRAL) LocalContentColor.current else content)
            Spacer(Modifier.width(6.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = content,
            )
        }
    }
}
