package com.resukisu.resukisu.ui.component.wear

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ArcProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import com.resukisu.resukisu.R

private val PillWidth = 120.dp
private val PillHeight = 56.dp

/**
 * Diameter of the circle the arc is drawn on. It is larger than the pill so that the arc keeps the
 * Wear Material 3 sweep instead of shrinking into the middle of the pill.
 */
private val ArcDiameter = 88.dp

/**
 * The refresh indicator for a list the user pulled up once it reached its end.
 *
 * Wear Material 3 ships no pull to refresh component, so the list reveals a pill holding the Wear
 * loading animation. [ArcProgressIndicator] draws along the bottom of its bounding circle, so that
 * circle is lifted by its own radius to land the arc in the middle of the pill.
 */
@Composable
fun WearRefreshIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.wear_refreshing)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(percent = 50))
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(PillWidth, PillHeight), contentAlignment = Alignment.Center) {
                ArcProgressIndicator(
                    // requiredSize, not size: the circle is taller than the pill and would
                    // otherwise be squashed to the pill's height, moving the arc off centre.
                    modifier = Modifier
                        .offset(y = -(ArcDiameter / 2))
                        .requiredSize(ArcDiameter),
                )
            }
        }
    }
}
