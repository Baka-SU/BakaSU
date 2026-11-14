package org.bakasu.bakasu.ui.wear.component.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.wear.component.WearAutoMarqueeText
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearRefreshItem

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
    isRefreshing: Boolean = false,
    onOpenPanel: (() -> Unit)? = null,
    hasOperationsPanel: Boolean = false,
    extraContent: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {},
    onChoose: (String) -> Unit,
) {
    WearList(
        onBack = onBack,
        snap = true,
        isLoading = isLoading,
        onRefresh = onRefresh,
        isRefreshing = isRefreshing,
        onOpenPanel = onOpenPanel,
        hasOperationsPanel = hasOperationsPanel,
        panelLabel = stringResource(R.string.wear_operations),
    ) { spec ->
        item { WearPageHeader(spec, title) }
        LazySegmentedColumn(choices, { it.first }) { (value, label) ->
            WearRefreshItem(spec, Modifier.minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)) {
                RadioButton(
                    selected = value == selected,
                    onSelect = { onChoose(value) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    transformation = SurfaceTransformation(spec),
                    icon = icon?.let { { Icon(it(value), contentDescription = null) } },
                    label = { WearAutoMarqueeText(label) },
                )
            }
        }
        extraContent(spec)
    }
}
