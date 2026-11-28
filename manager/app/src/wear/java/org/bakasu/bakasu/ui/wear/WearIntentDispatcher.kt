package org.bakasu.bakasu.ui.wear

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import org.bakasu.bakasu.Natives
import org.bakasu.bakasu.data.file.ZipFileDetector
import org.bakasu.bakasu.data.settings.SettingsPlatformRepository
import org.bakasu.bakasu.ui.navigation.LaunchAction
import org.bakasu.bakasu.ui.navigation.resolveLaunchIntent
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
