package org.bakasu.bakasu.ui.wear.component.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader

@Composable
fun WearChoicePage(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onBack: () -> Unit,
    enabled: Boolean = true,
    icon: ((String) -> ImageVector)? = null,
    isLoading: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    labelMaxLines: Int = 3,
    extraContent: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {},
    onChoose: (String) -> Unit,
) {
    WearList(onBack = onBack, snap = true, isLoading = isLoading, onRefresh = onRefresh) { spec ->
        item { WearPageHeader(spec, title) }
        LazySegmentedColumn(choices, { it.first }) { (value, label) ->
            RadioButton(
                selected = value == selected,
                onSelect = { onChoose(value) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                enabled = enabled,
                transformation = SurfaceTransformation(spec),
                icon = icon?.let { { Icon(it(value), contentDescription = null) } },
                label = { Text(label, maxLines = labelMaxLines, overflow = TextOverflow.Ellipsis) },
            )
        }
        extraContent(spec)
    }
}
