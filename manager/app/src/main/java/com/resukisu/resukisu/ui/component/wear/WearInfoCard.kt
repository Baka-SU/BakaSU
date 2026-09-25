package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/** A non-interactive information container for Wear Material 3 version 1.5.0. */
@Composable
fun TransformingLazyColumnItemScope.WearInfoCard(
    transformationSpec: TransformationSpec,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transformation = SurfaceTransformation(transformationSpec)
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surfaceContainer
    val painter = remember(transformation, shape, color) {
        transformation.createContainerPainter(ColorPainter(color), shape, border = null)
    }
    Column(
        modifier = Modifier
            .graphicsLayer { with(transformation) { applyContainerTransformation() } }
            .then(modifier.transformedHeight(this, transformationSpec))
            .fillMaxWidth()
            .drawBehind { with(painter) { draw(size) } }
            .graphicsLayer {
                this.shape = shape
                clip = true
                with(transformation) { applyContentTransformation() }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** A single message card, used for the empty and error states of a list. */
@Composable
fun TransformingLazyColumnItemScope.WearStatusItem(
    transformationSpec: TransformationSpec,
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
) {
    WearInfoCard(transformationSpec, modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
