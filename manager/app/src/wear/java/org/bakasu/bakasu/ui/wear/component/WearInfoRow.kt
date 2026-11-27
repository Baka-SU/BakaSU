package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme

/** A read-only button-style row, with the icon centered beside wrapping labels. */
@Composable
fun WearInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    largeIcon: Boolean = false,
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    WearDetailField(
        label = label,
        value = value,
        iconSpacing = ButtonDefaults.IconSpacing,
        verticalSpacing = 0.dp,
        labelStyle = labelStyle,
        valueStyle = MaterialTheme.typography.labelSmall,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(if (largeIcon) ButtonDefaults.LargeIconSize else ButtonDefaults.IconSize))
    }
}
