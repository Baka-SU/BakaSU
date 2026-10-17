package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * Read-only module information, using the official Card's shape, padding and surface roles. A
 * [compact] card takes a button's padding and row height, to line up with compact buttons beside it.
 */
@Composable
fun TransformingLazyColumnItemScope.WearModuleInfoCard(
    spec: TransformationSpec,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
            .wearCompactRow(compact),
        transformation = SurfaceTransformation(spec),
        contentPadding = if (compact) ButtonDefaults.ContentPadding else CardDefaults.ContentPadding,
        content = content,
    )
}
