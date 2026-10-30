package org.bakasu.bakasu.ui.wear.component.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import org.bakasu.bakasu.ui.wear.component.wearCompactRow

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** A settings switch; per the Wear switch button guidance the label takes up to 3 lines and the summary up to 2. */
@Composable
fun TransformingLazyColumnItemScope.WearSettingsSwitchWidget(
    transformationSpec: TransformationSpec,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    secondaryLabel: String? = null,
    compact: Boolean = false,
    largeIcon: Boolean = false,
) {
    SwitchButton(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
            .wearCompactRow(compact),
        // A compact switch uses the button's 6dp vertical padding instead of its own 8dp.
        contentPadding = if (compact) ButtonDefaults.ContentPadding else SwitchButtonDefaults.ContentPadding,
        transformation = SurfaceTransformation(transformationSpec),
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        icon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(if (largeIcon) ButtonDefaults.LargeIconSize else ButtonDefaults.IconSize)) } },
        secondaryLabel = secondaryLabel?.let { text -> { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        label = { Text(label, maxLines = 3, overflow = TextOverflow.Ellipsis) },
    )
}
