package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material3.ArcProgressIndicator
import androidx.wear.compose.material3.ArcProgressIndicatorDefaults
import com.resukisu.resukisu.R

/**
 * Wear Material 3 full screen loading indicator.
 *
 * Wear draws an arc that follows the round display instead of the phone's small circular spinner.
 * The arc keeps its distance from the page content, so it can be layered over a list that is being
 * refreshed without covering anything the user is reading.
 */
@Composable
fun WearLoadingIndicator(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.wear_loading)
    Box(
        modifier = modifier.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ArcProgressIndicator(
            modifier = Modifier.size(ArcProgressIndicatorDefaults.recommendedIndeterminateDiameter),
        )
    }
}
