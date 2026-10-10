package org.bakasu.bakasu.ui.component

import androidx.compose.runtime.Composable
import org.bakasu.bakasu.domain.model.KernelStatus

@Composable
inline fun KsuIsValid(
    status: KernelStatus,
    content: @Composable () -> Unit,
) {
    if (status.isFullFeatured) {
        content()
    }
}
