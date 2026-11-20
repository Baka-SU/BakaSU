package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme

/** Fixed and scrolling alerts start at the same safe inset instead of centering short content. */
internal val WearDialogVerticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top)

@Composable
internal fun wearDialogContentPadding(edgeButton: Boolean = false): PaddingValues {
    val padding = if (edgeButton) AlertDialogDefaults.contentWithIconPadding() else AlertDialogDefaults.confirmDismissWithIconContentPadding()
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = padding.calculateStartPadding(direction),
        end = padding.calculateEndPadding(direction),
        top = AlertDialogDefaults.contentPadding().calculateTopPadding(),
        bottom = padding.calculateBottomPadding(),
    )
}

/** The large icon slot used by the Wear M3 dialog samples, with their default safe padding. */
@Composable
fun WearDialogIcon(image: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(image, contentDescription = null, modifier = Modifier.size(ButtonDefaults.LargeIconSize), tint = tint)
}
