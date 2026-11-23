package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * Moves the complete Material surface, including its hit target, outside its offscreen layer.
 * List measurement and edge padding belong to this outer item; the child keeps its visual
 * SurfaceTransformation but must not apply the refresh offset or transformedHeight again.
 */
@Composable
internal fun TransformingLazyColumnItemScope.WearRefreshItem(
    transformationSpec: TransformationSpec,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.wearRefreshOffset().then(modifier).transformedHeight(this, transformationSpec),
        propagateMinConstraints = true,
    ) { content() }
}
