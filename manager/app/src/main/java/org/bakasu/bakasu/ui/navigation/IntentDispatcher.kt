package org.bakasu.bakasu.ui.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.channels.ReceiveChannel
import org.bakasu.bakasu.Natives
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.download.DownloadService
import org.bakasu.bakasu.data.settings.SettingsPlatformRepository
import org.bakasu.bakasu.ui.component.InstallConfirmationDialog
import org.bakasu.bakasu.ui.component.ZipFileDetector
import org.bakasu.bakasu.ui.component.ZipFileInfo
import org.bakasu.bakasu.ui.component.ZipType
import org.bakasu.bakasu.ui.webui.WebUIActivity
import org.koin.compose.koinInject

private const val SCHEME_KSU = "ksu"
private const val HOST_ACTION = "action"
private const val HOST_WEBUI = "webui"
private const val PARAM_ID = "id"
private const val PARAM_TOKEN = "token"

private sealed interface PendingAction {
    data class InstallModules(
        val zipFileInfos: List<ZipFileInfo>,
        val requiresConfirmation: Boolean,
    ) : PendingAction

    data class InstallKernel(
        val zipFileInfo: ZipFileInfo,
    ) : PendingAction

    data class ExecuteAction(val moduleId: String) : PendingAction

    data class OpenWebUI(val moduleId: String) : PendingAction
}

private sealed interface KsuDeepLink {
    data class Action(val moduleId: String) : KsuDeepLink
    data class WebUi(val moduleId: String) : KsuDeepLink
}

private fun buildInternalWebUiUri(moduleId: String): Uri = Uri.Builder()
    .scheme("kernelsu")
    .authority(HOST_WEBUI)
    .appendQueryParameter(PARAM_ID, moduleId)
    .build()

private fun resolveIntent(context: Context, intent: Intent, intentToken: String, zipFileDetector: ZipFileDetector): PendingAction? {
    if (intent.action == DownloadService.ACTION_INSTALL_MODULE) {
        val token = intent.getStringExtra(DownloadService.EXTRA_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
        if (token != intentToken) return null
        val uriString = intent.getStringExtra(DownloadService.EXTRA_MODULE_URI) ?: return null
        val uri = uriString.toUri()
        val zipFileInfo = zipFileDetector.parseZipFile(context, uri)
        // no need check that, because this call is from DownloadService, and it must install module
        // if (zipFileInfo.type == ZipType.UNKNOWN) return null

        return PendingAction.InstallModules(
            zipFileInfos = listOf(zipFileInfo),
            requiresConfirmation = false,
        )
    }

    val result = when (val deepLink = parseValidatedDeepLink(intent.data, intentToken)) {
        is KsuDeepLink.Action -> PendingAction.ExecuteAction(deepLink.moduleId)
        is KsuDeepLink.WebUi -> PendingAction.OpenWebUI(deepLink.moduleId)
        null -> null
    }

    if (result != null) return result

    val zipUri: ArrayList<Uri>? = when (intent.action) {
        Intent.ACTION_SEND -> {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let { arrayListOf(it) }
        }

        Intent.ACTION_SEND_MULTIPLE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
        }

        else -> when {
            intent.data != null -> arrayListOf(intent.data!!)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                intent.getParcelableArrayListExtra("uris", Uri::class.java)
            }

            else -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("uris")
            }
        }
    }

    val zipFileInfos = zipUri?.map { uri ->
        zipFileDetector.parseZipFile(context, uri)
    }?.filter { it.type != ZipType.UNKNOWN }

    if (zipFileInfos == null) return null

    val hasKernel = zipFileInfos.any { it.type == ZipType.KERNEL }
    val hasModule = zipFileInfos.any { it.type == ZipType.MODULE }

    if (hasKernel && hasModule) {
        Toast.makeText(
            context,
            context.getString(R.string.both_kernel_and_module_files),
            Toast.LENGTH_SHORT,
        ).show()
        return null
    }

    if (hasKernel) {
        // all is kernels
        if (zipFileInfos.count { it.type == ZipType.KERNEL } != 1) {
            Toast.makeText(
                context,
                context.getString(R.string.more_than_one_kernel_file),
                Toast.LENGTH_SHORT,
            ).show()
            return null
        }

        return PendingAction.InstallKernel(
            zipFileInfo = zipFileInfos.first(),
        )
    } else {
        return PendingAction.InstallModules(
            zipFileInfos = zipFileInfos,
            requiresConfirmation = true,
        )
    }
}

private fun parseValidatedDeepLink(uri: Uri?, intentToken: String): KsuDeepLink? {
    if (uri?.scheme != SCHEME_KSU) return null

    val moduleId = uri.getQueryParameter(PARAM_ID)?.takeIf { it.isNotBlank() } ?: return null
    val token = uri.getQueryParameter(PARAM_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
    if (token != intentToken) return null

    return when (uri.host) {
        HOST_ACTION -> KsuDeepLink.Action(moduleId)
        HOST_WEBUI -> KsuDeepLink.WebUi(moduleId)
        else -> null
    }
}

@SuppressLint("StringFormatInvalid")
@Composable
fun IntentDispatcher(intentChannel: ReceiveChannel<Intent>) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val navigator = LocalNavigator.current
    val isManager = Natives.isManager
    var pendingZipFiles by remember { mutableStateOf<List<ZipFileInfo>>(emptyList()) }
    val settings: SettingsPlatformRepository = koinInject()
    val zipFileDetector: ZipFileDetector = koinInject()

    InstallConfirmationDialog(
        show = pendingZipFiles.isNotEmpty(),
        zipFiles = pendingZipFiles,
        onConfirm = { zipFiles ->
            pendingZipFiles = emptyList()
            if (zipFiles.first().type == ZipType.KERNEL) {
                // flash kernel
                navigator.push(Route.Install(preselectedKernelUri = zipFiles.first().uri.toString()))
            } else {
                // must be flash module
                navigator.push(Route.Flash.modules(zipFiles.map { it.uri }.map(Uri::toString)))
            }
        },
        onDismiss = {
            activity?.finish()
        },
    )

    CollectIntentChannel(intentChannel) { intent ->
        if (!isManager) return@CollectIntentChannel
        val action = resolveIntent(context, intent, settings.intentToken, zipFileDetector) ?: return@CollectIntentChannel

        when (action) {
            is PendingAction.InstallModules -> {
                if (action.requiresConfirmation) {
                    pendingZipFiles = action.zipFileInfos
                } else {
                    navigator.push(Route.Flash.modules(action.zipFileInfos.map { it.uri }.map(Uri::toString)))
                }
            }

            is PendingAction.InstallKernel -> {
                pendingZipFiles = listOf(action.zipFileInfo)
            }

            is PendingAction.ExecuteAction -> {
                navigator.push(Route.ExecuteModuleAction(action.moduleId, fromShortcut = true))
            }

            is PendingAction.OpenWebUI -> {
                val webIntent = Intent(context, WebUIActivity::class.java)
                    .setData(buildInternalWebUiUri(action.moduleId))
                context.startActivity(webIntent)
            }
        }
    }
}

@Composable
private fun CollectIntentChannel(intentChannel: ReceiveChannel<Intent>, onIntent: suspend (Intent) -> Unit) {
    LaunchedEffect(intentChannel) {
        for (intent in intentChannel) {
            onIntent(intent)
        }
    }
}
