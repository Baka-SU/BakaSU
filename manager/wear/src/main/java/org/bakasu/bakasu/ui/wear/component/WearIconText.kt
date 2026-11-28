package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** Detail values wrap so full versions and identifiers remain readable on a watch. */
@Composable
fun WearDetailField(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    largeIcon: Boolean = false,
) {
    WearDetailField(
        label,
        value,
        modifier,
        iconAlignment = if (largeIcon) Alignment.CenterVertically else Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(if (largeIcon) ButtonDefaults.ExtraLargeIconSize else 18.dp))
    }
}

@Composable
fun WearDetailField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    iconAlignment: Alignment.Vertical = Alignment.CenterVertically,
    iconSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 2.dp,
    labelStyle: TextStyle = MaterialTheme.typography.titleSmall,
    valueStyle: TextStyle = MaterialTheme.typography.bodySmall,
    icon: @Composable () -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = iconAlignment) {
        icon()
        Spacer(Modifier.width(iconSpacing))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
            Text(label, style = labelStyle)
            Text(
                value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
