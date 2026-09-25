package com.resukisu.resukisu.ui.wear

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Copyright
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.PackageIcon
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.wearButtonColors
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearScaledItem

@Composable
internal fun WearAboutDetail(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val projectUrl = stringResource(R.string.wear_project_url)
    val licenseUrl = stringResource(R.string.wear_project_license_url)
    val openFailed = stringResource(R.string.operation_failed)
    val openLink: (String) -> Unit = { uri ->
        try {
            uriHandler.openUri(uri)
        } catch (_: IllegalArgumentException) {
            Toast.makeText(context, openFailed, Toast.LENGTH_SHORT).show()
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, openFailed, Toast.LENGTH_SHORT).show()
        }
    }
    val version = BuildConfig.VERSION_NAME.let {
        if (it.matches(Regex("[0-9a-fA-F]{40}"))) it.take(7) else it
    }
    WearList { spec ->
        item {
            ListHeader(
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
            ) { Text(stringResource(R.string.about), color = MaterialTheme.colorScheme.primary) }
        }
        item {
            WearScaledItem(spec) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PackageIcon(context.packageName, null, Modifier.size(72.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.wear_app_version, version, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Code, stringResource(R.string.wear_github),
                onClick = { openLink(projectUrl) })
        }
        item {
            Button(
                onClick = { openLink(licenseUrl) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = wearButtonColors(),
                icon = { Icon(Icons.TwoTone.Copyright, contentDescription = null) },
                secondaryLabel = { Text(stringResource(R.string.license, stringResource(R.string.wear_project_license))) },
            ) { Text(stringResource(R.string.open_source_license)) }
        }
        item {
            WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowBack, stringResource(R.string.wear_back), onBack)
        }
    }
}
