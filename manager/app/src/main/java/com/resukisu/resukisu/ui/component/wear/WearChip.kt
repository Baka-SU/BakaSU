package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/** Chip emphasis levels; a screen should lead with at most one [HIGH] chip. [ERROR] marks a failed state in red. */
enum class WearChipEmphasis { HIGH, MEDIUM, OUTLINED, LOW, ERROR }

/**
 * A full-width chip: an optional 24dp icon, a primary label and an optional secondary label. With
 * [onClick] it is a Wear button of at most three lines (a one-line label over a two-line secondary
 * label). Without [onClick] it only shows status, exposes no click semantics, and keeps a one-line
 * label over a secondary label of up to three lines. Labels are start-aligned when an icon or
 * secondary label is present.
 */
@Composable
fun TransformingLazyColumnItemScope.WearChip(
    transformationSpec: TransformationSpec,
    label: String,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    icon: ImageVector? = null,
    emphasis: WearChipEmphasis = WearChipEmphasis.MEDIUM,
    onClick: (() -> Unit)? = null,
) {
    val labelLines = if (secondaryLabel != null) 1 else if (onClick != null) 3 else 2
    val centered = icon == null && secondaryLabel == null
    val border = if (emphasis == WearChipEmphasis.OUTLINED) ButtonDefaults.outlinedButtonBorder(enabled = true) else null
    if (onClick != null) {
        Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
            transformation = SurfaceTransformation(transformationSpec),
            colors = when (emphasis) {
                WearChipEmphasis.HIGH -> ButtonDefaults.buttonColors()
                WearChipEmphasis.MEDIUM -> ButtonDefaults.filledTonalButtonColors()
                WearChipEmphasis.OUTLINED -> ButtonDefaults.outlinedButtonColors()
                WearChipEmphasis.LOW -> ButtonDefaults.childButtonColors()
                WearChipEmphasis.ERROR -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    iconColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            border = border,
            icon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) } },
            // A Wear button grows to at most three lines: one label line and two for the description.
            secondaryLabel = secondaryLabel?.let { text ->
                { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            },
        ) {
            Text(
                label, maxLines = labelLines, overflow = TextOverflow.Ellipsis,
                modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
        return
    }

    val scheme = MaterialTheme.colorScheme
    val (container, content, secondaryContent) = when (emphasis) {
        WearChipEmphasis.HIGH -> Triple(scheme.primary, scheme.onPrimary, scheme.onPrimary)
        // A null container keeps the surface container and the custom background of other cards.
        WearChipEmphasis.MEDIUM -> Triple(null, scheme.onSurface, scheme.onSurfaceVariant)
        WearChipEmphasis.OUTLINED -> Triple(Color.Transparent, scheme.primary, scheme.onSurfaceVariant)
        WearChipEmphasis.LOW -> Triple(Color.Transparent, scheme.onSurface, scheme.onSurfaceVariant)
        WearChipEmphasis.ERROR -> Triple(scheme.errorContainer, scheme.onErrorContainer, scheme.onErrorContainer)
    }
    WearInfoCard(
        transformationSpec,
        modifier = modifier.heightIn(min = ButtonDefaults.Height),
        containerColor = container,
        border = border,
        shape = ButtonDefaults.shape,
        contentPadding = ButtonDefaults.ContentPadding,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    label, color = content, style = MaterialTheme.typography.labelMedium,
                    maxLines = labelLines, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                )
                if (secondaryLabel != null) Text(
                    secondaryLabel, color = secondaryContent, style = MaterialTheme.typography.labelSmall,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
