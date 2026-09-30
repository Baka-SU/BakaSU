package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.PackageIcon

/** Shown inside [androidx.wear.compose.material3.AppScaffold], which already draws the system time. */
@Composable
fun WearLoadingScreen() {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val iconSize = (minOf(maxWidth, maxHeight) * 0.27f).coerceIn(48.dp, 72.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            PackageIcon(context.packageName, null, Modifier.size(iconSize))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center)
            Box(Modifier.size(24.dp)) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
