package org.bakasu.bakasu.ui.wear.screen.module

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
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ExpectedFile
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.model.ModuleUpdateMetadata
import org.bakasu.bakasu.domain.model.ModuleUpdateVerification
import org.bakasu.bakasu.ui.component.ZipFileDetector
import org.bakasu.bakasu.ui.component.ZipFileInfo
import org.bakasu.bakasu.ui.wear.WearNavigator
import org.bakasu.bakasu.ui.wear.WearRoute
import org.bakasu.bakasu.ui.wear.component.WearDialogIcon
import org.bakasu.bakasu.ui.wear.component.WearDialogVerticalArrangement
import org.bakasu.bakasu.ui.wear.component.wearDialogContentPadding
import org.bakasu.bakasu.ui.wear.component.wearEmphasizedBodyStyle
import org.bakasu.bakasu.wear.R as WearR
import org.koin.compose.koinInject

/**
 * Confirms picked ZIPs and repository downloads; previously confirmed updates install after download.
 * Carries published size and SHA-256 requirements into the download route and refreshes modules
 * through [onInstalled] after installation.
 */
@Stable
class WearModuleInstaller internal constructor(
    private val navigator: WearNavigator,
    private val confirming: MutableState<String?>,
    private val pick: () -> Unit,
    val onInstalled: () -> Unit,
) {
    internal val pendingConfirmation: String? get() = confirming.value

    fun pickFile() = pick()

    fun confirm(uri: String) {
        confirming.value = uri
    }

    internal fun dismissConfirmation() {
        confirming.value = null
    }

    fun install(uri: String) = navigator.open(WearRoute.ModuleInstall(uri))

    fun download(module: CatalogModule, asset: ModuleReleaseAsset) {
        if (module.isScript) return
        navigator.open(
            WearRoute.OnlineModuleDownload(
                module.moduleName,
                asset.name,
                asset.downloadUrl,
                asset.expected?.size ?: 0L,
                asset.expected?.sha256.orEmpty(),
                verificationRequired = asset.verificationRequired,
                moduleId = module.moduleId,
                iconUrl = module.iconUrl?.takeIf { it.isNotBlank() } ?: module.registryMetadata?.iconUrl,
                iconOrigin = module.repositoryUrl.takeIf { module.registryMetadata != null },
            ),
        )
    }

    fun update(module: InstalledModule, update: ModuleUpdateMetadata, verification: ModuleUpdateVerification, source: CatalogModule? = null) {
        if (verification == ModuleUpdateVerification.Rejected) return
        val registry = verification as? ModuleUpdateVerification.Registry
        navigator.open(
            WearRoute.OnlineModuleDownload(
                module.name,
                "${module.name}-${update.version}.zip",
                registry?.zipUrl ?: update.zipUrl,
                size = registry?.expected?.size ?: 0L,
                sha256 = registry?.expected?.sha256.orEmpty(),
                update = true,
                verificationRequired = registry != null,
                moduleId = module.id,
                iconUrl = source?.iconUrl?.takeIf { it.isNotBlank() } ?: source?.registryMetadata?.iconUrl,
                iconOrigin = source?.repositoryUrl?.takeIf { source.registryMetadata != null },
            ),
        )
    }

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

/** Carries the published file integrity and whether verification is mandatory. */
internal fun WearRoute.OnlineModuleDownload.asset() = ModuleReleaseAsset(
    fileName,
    url,
    size,
    0,
    expected = sha256.ifBlank { null }?.let { ExpectedFile(size, it) },
    verificationRequired = verificationRequired,
)

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

/** Shows parsed module details before installation; only Confirm starts the install. */
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
        verticalArrangement = WearDialogVerticalArrangement,
        contentPadding = wearDialogContentPadding(),
        visible = uri != null && module != null,
        onDismissRequest = installer::dismissConfirmation,
        icon = { WearDialogIcon(Icons.Rounded.Extension) },
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
        if (version.isNotBlank()) item { WearDialogField(stringResource(R.string.module_version), version) }
        module?.author?.ifBlank { null }?.let { author -> item { WearDialogField(stringResource(R.string.module_author), author) } }
        if (fileName.isNotBlank()) item { WearDialogField(stringResource(WearR.string.wear_file_name), fileName) }
        module?.description?.ifBlank { null }?.let { description ->
            item {
                WearDialogField(
                    stringResource(WearR.string.wear_module_description),
                    description,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun WearDialogField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
