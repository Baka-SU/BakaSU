package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.TransformationSpec

/**
 * Uses the shared left-swipe and hold-to-reveal gestures. Revealing never deletes; [onDelete] asks for confirmation.
 * Key the enclosing item by its entry, and leave the content transformation to this wrapper.
 */
@Composable
fun TransformingLazyColumnItemScope.WearSwipeToDelete(
    transformationSpec: TransformationSpec,
    scrolling: Boolean,
    deleteLabel: String,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    WearModuleSwipeActions(
        transformationSpec,
        scrolling,
        primary = WearIconAction(Icons.Rounded.Delete, deleteLabel, onDelete, destructive = true),
        content = content,
    )
}
