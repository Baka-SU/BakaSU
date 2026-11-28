package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * Applies list measurement and edge padding outside the Material surface's offscreen layer.
 * The child keeps its visual SurfaceTransformation but must not apply transformedHeight again.
 */
@Composable
internal fun TransformingLazyColumnItemScope.WearRefreshItem(
    transformationSpec: TransformationSpec,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.transformedHeight(this, transformationSpec),
        propagateMinConstraints = true,
    ) { content() }
}
