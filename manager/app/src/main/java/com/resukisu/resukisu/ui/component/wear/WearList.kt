package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec

/** A Wear screen whose crown and vertical touch gestures scroll only its own content. */
@Composable
fun WearList(content: TransformingLazyColumnScope.(TransformationSpec) -> Unit) {
    val horizontalPadding = if (LocalConfiguration.current.isScreenRound) 14.dp else 8.dp
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
            state = listState,
            contentPadding = contentPadding,
        ) { content(transformationSpec) }
    }
}
