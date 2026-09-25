package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import com.resukisu.resukisu.R

/** Compact, contained Wear Material 3 loading animation shared by initial load and refresh. */
@Composable
fun WearLoadingIndicator(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.wear_loading),
) {
    Box(
        modifier = modifier.width(64.dp).height(48.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(percent = 50))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(30.dp),
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            ),
        )
    }
}
