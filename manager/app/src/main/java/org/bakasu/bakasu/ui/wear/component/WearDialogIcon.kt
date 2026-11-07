package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme

/** The large icon slot used by the Wear M3 dialog samples, with their default safe padding. */
@Composable
fun WearDialogIcon(image: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(image, contentDescription = null, modifier = Modifier.size(ButtonDefaults.LargeIconSize), tint = tint)
}
