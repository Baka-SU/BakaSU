package org.bakasu.bakasu.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilledTonalLoadingButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled && !loading) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, modifier = Modifier.alpha(if (loading) 0f else 1f))
            if (loading) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(20.dp))
                }
            }
        }
    }
}
