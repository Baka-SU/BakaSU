package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

@Composable
fun TransformingLazyColumnItemScope.WearPageHeader(
    transformationSpec: TransformationSpec,
    icon: ImageVector,
    title: String,
) {
    ListHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
    ) {
        WearIconText(icon, title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun TransformingLazyColumnItemScope.WearSectionHeader(
    transformationSpec: TransformationSpec,
    icon: ImageVector,
    title: String,
) {
    ListSubHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
    ) { WearIconText(icon, title) }
}

@Composable
fun TransformingLazyColumnItemScope.WearActionButton(
    transformationSpec: TransformationSpec,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
        icon = { Icon(icon, contentDescription = null) },
    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Scale a custom or text-only item with the same height and visual spec as Wear Material items. */
@Composable
fun TransformingLazyColumnItemScope.WearScaledItem(
    transformationSpec: TransformationSpec,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier.graphicsLayer {
            with(transformationSpec) { applyContainerTransformation(scrollProgress) }
        }.then(modifier.transformedHeight(this, transformationSpec)).graphicsLayer {
            with(transformationSpec) { applyContentTransformation(scrollProgress) }
        },
        content = content,
    )
}
