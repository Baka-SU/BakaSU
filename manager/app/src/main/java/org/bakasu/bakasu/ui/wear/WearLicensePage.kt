package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Copyright
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** The bundled AboutLibraries data, loaded off the main thread; null while loading. */
@Composable
private fun rememberLibraries(): Libs? {
    val context = LocalContext.current
    val libraries by produceState<Libs?>(null, context) {
        value = withContext(Dispatchers.IO) { Libs.Builder().withJson(context, R.raw.aboutlibraries).build() }
    }
    return libraries
}

/** The open source libraries; each opens its licenses as its own page. */
@Composable
internal fun WearLicensePage(onBack: () -> Unit, onOpenLibrary: (Int) -> Unit) {
    val libraries = rememberLibraries()
    WearList(isLoading = libraries == null, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.open_source_license)) }
        libraries?.libraries?.forEachIndexed { index, library ->
            item(key = "library-$index") {
                WearActionButton(spec, Icons.TwoTone.Copyright, library.name, { onOpenLibrary(index) })
            }
        }
    }
}

/** The licenses of the library at [index]; tapping a license opens its link. */
@Composable
internal fun WearLicenseDetailPage(index: Int, onBack: () -> Unit, onOpenLink: (String) -> Unit) {
    val libraries = rememberLibraries()
    val library = libraries?.libraries?.getOrNull(index)
    WearList(isLoading = libraries == null, onBack = onBack) { spec ->
        if (library != null) {
            item { WearPageHeader(spec, library.name) }
            // Each license is one card: its name above the complete, unaltered text in the
            // theme's small body style, so the whole license scrolls as a single reading block.
            library.licenses.forEach { license ->
                item {
                    Card(
                        onClick = { license.url?.let(onOpenLink) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(license.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            license.licenseContent ?: stringResource(R.string.no_license_text),
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
