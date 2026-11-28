package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.drawCircularProgressIndicator

@Composable
internal fun WearEdgeProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier, indicatorColor: Color = MaterialTheme.colorScheme.primary) {
    val colors = ProgressIndicatorDefaults.colors(indicatorColor = indicatorColor)
    val strokeWidth = CircularProgressIndicatorDefaults.smallStrokeWidth
    Spacer(
        modifier.fillMaxSize().padding(CircularProgressIndicatorDefaults.FullScreenPadding).drawBehind {
            drawCircularProgressIndicator(
                progress = progress(),
                colors = colors,
                strokeWidth = strokeWidth,
                startAngle = 300f,
                endAngle = 240f,
            )
        },
    )
}
