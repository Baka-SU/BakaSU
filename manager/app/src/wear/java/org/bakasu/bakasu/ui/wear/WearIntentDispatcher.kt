package org.bakasu.bakasu.ui.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import org.bakasu.bakasu.Natives
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.download.DownloadService
import org.bakasu.bakasu.data.file.ZipFileDetector
import org.bakasu.bakasu.data.file.ZipFileInfo
import org.bakasu.bakasu.data.file.ZipType
import org.bakasu.bakasu.data.settings.SettingsPlatformRepository
import org.bakasu.bakasu.ui.wear.screen.module.WearModuleInstaller
import org.koin.compose.koinInject

/** Handles incoming launches with Wear destinations. */
@Composable
internal fun WearIntentDispatcher(
    intentChannel: ReceiveChannel<Intent>,
    navigator: WearNavigator,
    installer: WearModuleInstaller,
) {
    val context = LocalContext.current
    val settings = koinInject<SettingsPlatformRepository>()
    val zipFileDetector = koinInject<ZipFileDetector>()
    LaunchedEffect(intentChannel, navigator, installer) {
        for (intent in intentChannel) {
            if (!Natives.isManager) continue
            when (val action = resolveLaunchIntent(context, intent, settings.intentToken, zipFileDetector)) {
                is LaunchAction.InstallModules -> {
                    for ((index, file) in action.zipFileInfos.withIndex()) {
                        if (action.requiresConfirmation) {
                            installer.confirm(file.uri.toString())
                            snapshotFlow { installer.pendingConfirmation }.first { it == null }
                            // Dismissing the confirmation also cancels the remaining shared ZIPs.
                            if (navigator.top !is WearRoute.ModuleInstall) break
                        } else {
                            installer.install(file.uri.toString())
                        }
                        if (index < action.zipFileInfos.lastIndex) {
                            // Each shared ZIP is confirmed after returning from the previous install.
                            snapshotFlow { navigator.top }.first { it !is WearRoute.ModuleInstall }
                        }
                    }
                }

                is LaunchAction.InstallKernel -> navigator.open(WearRoute.Ak3Install(action.zipFileInfo.uri.toString()))

                is LaunchAction.ExecuteAction -> navigator.open(WearRoute.ModuleAction(action.moduleId))

                is LaunchAction.OpenWebUI -> context.startActivity(WearWebUIActivity.intent(context, action.moduleId))

                null -> Unit
            }
        }
    }
}

private const val SCHEME_KSU = "ksu"
private const val HOST_ACTION = "action"
private const val HOST_WEBUI = "webui"
private const val PARAM_ID = "id"
private const val PARAM_TOKEN = "token"

private sealed interface LaunchAction {
    data class InstallModules(
        val zipFileInfos: List<ZipFileInfo>,
        val requiresConfirmation: Boolean,
    ) : LaunchAction

    data class InstallKernel(
        val zipFileInfo: ZipFileInfo,
    ) : LaunchAction

    data class ExecuteAction(val moduleId: String) : LaunchAction

    data class OpenWebUI(val moduleId: String) : LaunchAction
}

private sealed interface KsuDeepLink {
    data class Action(val moduleId: String) : KsuDeepLink
    data class WebUi(val moduleId: String) : KsuDeepLink
}

private fun resolveLaunchIntent(context: Context, intent: Intent, intentToken: String, zipFileDetector: ZipFileDetector): LaunchAction? {
    if (intent.action == DownloadService.ACTION_INSTALL_MODULE) {
        val token = intent.getStringExtra(DownloadService.EXTRA_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
        if (token != intentToken) return null
        val uriString = intent.getStringExtra(DownloadService.EXTRA_MODULE_URI) ?: return null
        val uri = uriString.toUri()
        val zipFileInfo = zipFileDetector.parseZipFile(context, uri)
        // A validated download notification already represents an explicit module install request.

        return LaunchAction.InstallModules(
            zipFileInfos = listOf(zipFileInfo),
            requiresConfirmation = false,
        )
    }

    val result = when (val deepLink = parseValidatedDeepLink(intent.data, intentToken)) {
        is KsuDeepLink.Action -> LaunchAction.ExecuteAction(deepLink.moduleId)
        is KsuDeepLink.WebUi -> LaunchAction.OpenWebUI(deepLink.moduleId)
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

        return LaunchAction.InstallKernel(
            zipFileInfo = zipFileInfos.first(),
        )
    } else {
        return LaunchAction.InstallModules(
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
