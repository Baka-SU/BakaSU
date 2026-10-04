package com.bakasu.bakasu.ui.component

import androidx.compose.runtime.Composable
import com.bakasu.bakasu.domain.model.KernelStatus

@Composable
inline fun KsuIsValid(
    status: KernelStatus,
    content: @Composable () -> Unit
) {
    if (status.isFullFeatured)
        content()
}
