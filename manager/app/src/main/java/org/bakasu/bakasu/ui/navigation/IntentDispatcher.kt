package org.bakasu.bakasu.ui.navigation

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.ReceiveChannel
import org.bakasu.bakasu.Natives
import org.bakasu.bakasu.data.file.ModuleUtils
import org.bakasu.bakasu.data.file.ZipFileInfo
import org.bakasu.bakasu.data.file.ZipType
import org.bakasu.bakasu.data.settings.SettingsPlatformRepository
import org.bakasu.bakasu.domain.usecase.PeekOneshotScriptUseCase
import org.bakasu.bakasu.ui.component.InstallConfirmationDialog
import org.bakasu.bakasu.ui.webui.WebUIActivity
import org.koin.compose.koinInject

private fun buildInternalWebUiUri(moduleId: String): Uri = Uri.Builder()
    .scheme("kernelsu")
    .authority("webui")
    .appendQueryParameter("id", moduleId)
    .build()

@SuppressLint("StringFormatInvalid")
@Composable
fun IntentDispatcher(intentChannel: ReceiveChannel<Intent>) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val navigator = LocalNavigator.current
    val isManager = Natives.isManager
    var pendingZipFiles by remember { mutableStateOf<List<ZipFileInfo>>(emptyList()) }
    val settings: SettingsPlatformRepository = koinInject()
    val zipFileDetector: ModuleUtils = koinInject()
    val peekScript = koinInject<PeekOneshotScriptUseCase>()

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
        val action = resolveLaunchIntent(context, intent, settings.intentToken, zipFileDetector) ?: return@CollectIntentChannel

        when (action) {
            is LaunchAction.InstallModules -> {
                val single = action.zipFileInfos.singleOrNull()
                val manifest = single?.let { peekScript(it.uri.toString()) }
                if (single != null && manifest != null) {
                    navigator.push(Route.ScriptRun(single.uri.toString(), offerInstall = true, displayName = manifest.name))
                    return@CollectIntentChannel
                }
                if (action.requiresConfirmation) {
                    pendingZipFiles = action.zipFileInfos
                } else {
                    navigator.push(Route.Flash.modules(action.zipFileInfos.map { it.uri }.map(Uri::toString)))
                }
            }

            is LaunchAction.InstallKernel -> {
                pendingZipFiles = listOf(action.zipFileInfo)
            }

            is LaunchAction.ExecuteAction -> {
                navigator.push(Route.ExecuteModuleAction(action.moduleId, fromShortcut = true))
            }

            is LaunchAction.OpenWebUI -> {
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
