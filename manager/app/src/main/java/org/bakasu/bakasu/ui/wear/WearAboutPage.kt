package org.bakasu.bakasu.ui.wear

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.BuildConfig
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.component.PackageIcon
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearChipEmphasis
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearScaledItem
import org.bakasu.bakasu.ui.wear.component.wearGroupGap

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * App identity, icon attribution, support, documentation, community and license destinations.
 */
@Composable
internal fun WearAboutDetail(onBack: () -> Unit, onOpenLink: (String) -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val projectUrl = stringResource(R.string.wear_project_url)
    val supportUrl = stringResource(R.string.home_support_url)
    val learnUrl = stringResource(R.string.home_learn_kernelsu_url)
    val copyright = rememberCustomDialog { dismiss ->
        AlertDialog(
            visible = true,
            onDismissRequest = dismiss,
            icon = {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text(stringResource(R.string.wear_icon_copyright)) },
            text = { Text(stringResource(R.string.about_app_icon, stringResource(R.string.wear_icon_author))) },
            edgeButton = { AlertDialogDefaults.EdgeButton(onClick = dismiss) },
        )
    }
    WearList(onBack = onBack, snap = true) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.about)) }
        item {
            WearScaledItem(spec) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PackageIcon(context.packageName, null, Modifier.size(80.dp))
                }
            }
        }
        item {
            WearScaledItem(spec) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.W700,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        wearAppVersion(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        Build.MODEL,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        wearGroupGap("identity-gap")
        item {
            WearChip(
                spec,
                stringResource(R.string.wear_icon_copyright),
                icon = Icons.Rounded.Copyright,
                onClick = copyright::show,
            )
        }
        item {
            WearChip(
                spec,
                stringResource(R.string.get_source_code),
                secondaryLabel = stringResource(R.string.get_source_code_detail),
                icon = Icons.Rounded.Code,
                onClick = { onOpenLink(projectUrl) },
            )
        }
        item {
            WearChip(
                spec,
                stringResource(R.string.join_telegram_group),
                secondaryLabel = stringResource(R.string.join_telegram_group_detail),
                icon = Icons.Rounded.Group,
                onClick = { onOpenLink("https://t.me/BakaSU") },
            )
        }
        item {
            WearChip(
                spec,
                stringResource(R.string.open_source_license),
                secondaryLabel = stringResource(R.string.license, stringResource(R.string.wear_project_license)),
                icon = Icons.Rounded.Copyright,
                onClick = onLicenses,
            )
        }
        wearGroupGap("support-gap")
        item {
            WearChip(
                spec,
                stringResource(R.string.home_support_title),
                icon = Icons.Rounded.VolunteerActivism,
                emphasis = WearChipEmphasis.MEDIUM,
                onClick = { onOpenLink(supportUrl) },
            )
        }
        item {
            WearChip(
                spec,
                stringResource(R.string.home_learn_kernelsu),
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                emphasis = WearChipEmphasis.MEDIUM,
                onClick = { onOpenLink(learnUrl) },
            )
        }
    }
}

/** The manager version as shown on the phone About page; commit-hash version names are shortened. */
@Composable
internal fun wearAppVersion(): String = stringResource(
    R.string.wear_app_version,
    BuildConfig.VERSION_NAME.let { if (it.matches(Regex("[0-9a-fA-F]{40}"))) it.take(7) else it },
    BuildConfig.VERSION_CODE,
)
