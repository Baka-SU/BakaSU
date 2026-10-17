package org.bakasu.bakasu.ui.wear

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.file.ZipFileDetector
import org.bakasu.bakasu.data.file.ZipFileInfo
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.ModuleUpdateMetadata
import org.bakasu.bakasu.ui.wear.component.wearEmphasizedBodyStyle
import org.koin.compose.koinInject

/**
 * Installing a module ZIP on the watch, from any of its three starts. A picked file and a
 * repository download are confirmed first, as on the phone; a module update, whose page already
 * asked, installs as soon as it is downloaded. Repository files must match their published size
 * and SHA-256; update ZIPs publish neither, so they install unverified. After installing, [onInstalled]
 * refreshes what shows modules.
 */
@Stable
class WearModuleInstaller internal constructor(
    private val navigator: WearNavigator,
    private val confirming: MutableState<String?>,
    private val pick: () -> Unit,
    val onInstalled: () -> Unit,
) {
    /** The ZIP waiting for the install confirmation, or null. */
    internal val pendingConfirmation: String? get() = confirming.value

    fun pickFile() = pick()

    fun confirm(uri: String) {
        confirming.value = uri
    }

    internal fun dismissConfirmation() {
        confirming.value = null
    }

    fun install(uri: String) = navigator.open(WearRoute.ModuleInstall(uri))

    fun download(moduleName: String, release: ModuleRelease, asset: ModuleReleaseAsset) = navigator.open(
        WearRoute.OnlineModuleDownload(
            moduleName,
            asset.name,
            asset.downloadUrl,
            asset.size,
            asset.sha256,
            release.name.ifBlank { release.tagName },
        ),
    )

    fun update(module: InstalledModule, update: ModuleUpdateMetadata) = navigator.open(
        WearRoute.OnlineModuleDownload(
            module.name,
            "${module.name}-${update.version}.zip",
            update.zipUrl,
            size = 0L,
            sha256 = "",
            version = update.version,
            update = true,
        ),
    )

    /** A finished download leaves its page; an update also leaves its update page and installs. */
    fun downloaded(route: WearRoute.OnlineModuleDownload, uri: String) {
        navigator.back()
        if (route.update) {
            if (navigator.top is WearRoute.ModuleUpdate) navigator.back()
            install(uri)
        } else {
            confirm(uri)
        }
    }
}

/** The file to download, as the download page starts it; a blank SHA-256 skips the check. */
internal fun WearRoute.OnlineModuleDownload.asset() = ModuleReleaseAsset(fileName, url, size, 0, sha256)

@Composable
internal fun rememberWearModuleInstaller(navigator: WearNavigator, onPickFailed: () -> Unit, onInstalled: () -> Unit): WearModuleInstaller {
    val confirming = rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirming.value = uri.toString()
    }
    val pickFailed by rememberUpdatedState(onPickFailed)
    val installed by rememberUpdatedState(onInstalled)
    return remember(navigator, confirming, launcher) {
        WearModuleInstaller(
            navigator,
            confirming,
            { runCatching { launcher.launch(arrayOf("application/zip", "application/octet-stream")) }.onFailure { pickFailed() } },
            { installed() },
        )
    }
}

/**
 * Confirms installing the ZIP the [installer] waits on with the official Wear [AlertDialog]. The
 * type follows the Wear roles: the dialog title in the title slot's own style, the module name in an
 * emphasized body style (wrapping, never cut), the metadata in quieter body styles and the
 * description in the body text style, all centered like the dialog's own slots; a long description
 * wraps and scrolls with the dialog. Missing `module.prop` fields are left out, and an unreadable
 * one falls back to the file name. Only Confirm installs; Cancel, swiping away and Back only close
 * it. Nothing shows while the details are still read.
 */
@Composable
internal fun WearModuleInstallConfirmDialog(installer: WearModuleInstaller) {
    val uri = installer.pendingConfirmation
    val context = LocalContext.current
    val zipDetector = koinInject<ZipFileDetector>()
    var info by remember { mutableStateOf<ZipFileInfo?>(null) }
    LaunchedEffect(uri) {
        info = null
        if (uri != null) info = withContext(Dispatchers.IO) { zipDetector.parseModuleInfo(context, uri.toUri()) }
    }
    val module = info
    val fileName = uri?.toUri()?.lastPathSegment?.substringAfterLast('/').orEmpty()
    val version = listOfNotNull(
        module?.version?.ifBlank { null },
        module?.versionCode?.ifBlank { null }?.let { "($it)" },
    ).joinToString(" ")
    AlertDialog(
        visible = uri != null && module != null,
        onDismissRequest = installer::dismissConfirmation,
        icon = {
            Icon(
                Icons.Rounded.Extension,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.confirm_installation)) },
        text = {
            Text(
                module?.name?.ifBlank { null } ?: fileName.ifBlank { stringResource(R.string.unknown_module) },
                style = wearEmphasizedBodyStyle(),
            )
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    // Closing first keeps a second tap from starting a second installation.
                    val target = uri ?: return@ConfirmButton
                    installer.dismissConfirmation()
                    installer.install(target)
                },
            )
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = installer::dismissConfirmation) },
    ) {
        // The metadata, one field per item at the list's 4dp spacing.
        if (version.isNotBlank()) item { WearDialogField(stringResource(R.string.module_version), version) }
        module?.author?.ifBlank { null }?.let { author -> item { WearDialogField(stringResource(R.string.module_author), author) } }
        if (fileName.isNotBlank()) item { WearDialogField(stringResource(R.string.wear_file_name), fileName) }
        item { WearDialogField(stringResource(R.string.wear_install_target), Build.MODEL) }
        // The description is its own group: 4dp more above it gives the dialog's 8dp group spacing.
        module?.description?.ifBlank { null }?.let { description ->
            item {
                Text(
                    description,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * One metadata field of a dialog: a quiet small label above its value. Both span the content width
 * and are centered, like the dialog's own title and text slots.
 */
@Composable
private fun WearDialogField(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(value, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
