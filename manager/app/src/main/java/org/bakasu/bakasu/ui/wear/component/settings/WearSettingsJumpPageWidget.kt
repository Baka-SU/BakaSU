package org.bakasu.bakasu.ui.wear.component.settings

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.wear.component.WearBadge
import org.bakasu.bakasu.ui.wear.component.WearLoadingIndicator
import org.bakasu.bakasu.ui.wear.component.WearRefreshItem

/**
 * A settings entry that opens another page: a neutral filled tonal button with an optional leading
 * icon and a [description] (summary or current value) as its secondary label.
 */
@Composable
fun TransformingLazyColumnItemScope.WearSettingsJumpPageWidget(
    spec: TransformationSpec,
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    description: String? = null,
    transformed: Boolean = true,
    largeIcon: Boolean = false,
    descriptionMaxLines: Int = 1,
    marquee: Boolean = false,
    isLoading: Boolean = false,
    titleBadge: String? = null,
    iconContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    val loadingDescription = stringResource(R.string.wear_loading)
    val button: @Composable () -> Unit = {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            transformation = if (transformed) SurfaceTransformation(spec) else null,
            contentPadding = if (largeIcon) ButtonDefaults.ButtonWithLargeIconContentPadding else ButtonDefaults.ContentPadding,
            icon = iconContent ?: icon?.let {
                {
                    val iconModifier = Modifier.size(if (largeIcon) ButtonDefaults.LargeIconSize else ButtonDefaults.IconSize)
                    if (isLoading) {
                        WearLoadingIndicator(iconModifier.semantics { contentDescription = loadingDescription })
                    } else {
                        Icon(it, contentDescription = null, modifier = iconModifier)
                    }
                }
            },
            secondaryLabel = description?.let { text -> { Text(text, modifier = if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier, maxLines = if (marquee) 1 else descriptionMaxLines, softWrap = !marquee, overflow = TextOverflow.Ellipsis) } },
        ) {
            if (titleBadge == null) {
                Text(
                    title,
                    modifier = if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    maxLines = if (marquee) {
                        1
                    } else if (description == null) {
                        3
                    } else {
                        2
                    },
                    softWrap = !marquee,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (marquee) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE), maxLines = 1, softWrap = false)
                    WearBadge(titleBadge)
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    WearBadge(titleBadge)
                }
            }
        }
    }
    if (transformed) {
        WearRefreshItem(spec, Modifier.minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding), button)
    } else {
        // Inside a swipe-to-reveal row the row owns displacement, transformation and edge padding.
        button()
    }
}
