package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme

/** The shared expressive loading animation, sized and colored for Wear surfaces. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WearLoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    LoadingIndicator(modifier = modifier.size(48.dp), color = color)
}
