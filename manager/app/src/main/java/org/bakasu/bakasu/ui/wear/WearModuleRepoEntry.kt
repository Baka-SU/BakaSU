package org.bakasu.bakasu.ui.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** One load per repository visit or source change; child pages and pause/resume keep the visit. */
@Composable
internal fun WearModuleRepoEntry(isOpen: Boolean, repositoryUrl: String?, onRefresh: () -> Unit) {
    LaunchedEffect(isOpen, repositoryUrl) {
        if (isOpen) onRefresh()
    }
}
