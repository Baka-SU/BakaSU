package com.resukisu.resukisu.ui.component.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem

@Composable
fun WearChoicePage(title: String, choices: List<Pair<String, String>>, selected: String,
    onBack: () -> Unit, enabled: Boolean = true, message: String? = null,
    icon: ((String) -> ImageVector)? = null, onChoose: (String) -> Unit) {
    WearList(onBack = onBack, snap = true) { spec ->
        item { WearPageHeader(spec, null, title) }
        if (!message.isNullOrBlank()) item { WearScaledItem(spec) { Text(message) } }
        LazySegmentedColumn(choices, { it.first }) { (value, label) ->
            RadioButton(
                selected = value == selected,
                onSelect = { onChoose(value) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                enabled = enabled,
                transformation = SurfaceTransformation(spec),
                icon = icon?.let { { Icon(it(value), contentDescription = null) } },
                label = { Text(label, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
