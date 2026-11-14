package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.lazy.TransformationSpec
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.InstalledModule

/** The installed module's two-line capsule, shared by its details and update pages. */
@Composable
internal fun TransformingLazyColumnItemScope.WearModuleHeader(
    spec: TransformationSpec,
    module: InstalledModule,
    artwork: CatalogModule? = null,
    version: String? = null,
) {
    val titleStyle = MaterialTheme.typography.labelMedium
    val versionStyle = MaterialTheme.typography.labelSmall
    val iconSize = with(LocalDensity.current) { titleStyle.lineHeight.toDp() + versionStyle.lineHeight.toDp() + 2.dp }.coerceAtLeast(ButtonDefaults.LargeIconSize)
    WearInfoCard(spec, shape = ButtonDefaults.shape, contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WearCatalogModuleIcon(artwork?.iconUrl?.takeIf { it.isNotBlank() } ?: artwork?.registryMetadata?.iconUrl, artwork?.repositoryUrl?.takeIf { artwork.registryMetadata != null }, iconSize)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    WearAutoMarqueeText(module.name, Modifier.weight(1f, fill = false), style = titleStyle)
                    if (module.metamodule) WearBadge(stringResource(R.string.wear_meta_badge))
                }
                WearAutoMarqueeText(version ?: module.version.ifBlank { stringResource(R.string.unknown) }, style = versionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
