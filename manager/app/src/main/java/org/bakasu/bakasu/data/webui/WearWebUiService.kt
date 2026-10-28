package org.bakasu.bakasu.data.webui

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bakasu.bakasu.data.packageinfo.AppIconDataSource
import org.bakasu.bakasu.data.packageinfo.InstalledPackageRepository
import org.bakasu.bakasu.data.profile.ProfileTemplateRepository
import org.bakasu.bakasu.domain.usecase.RefreshInstalledModulesUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Answers a phone showing a watch module's WebUI (see [WearWebUiProtocol]) and serves
 * phone-assisted online template downloads. Only Google Play services delivers these events, and
 * only from paired devices signed with the same key; every path and sender is still checked, since
 * the service is exported. Calls run off the binder thread in a process-wide scope, since the
 * service may be unbound as soon as a callback returns.
 */
class WearWebUiService :
    WearableListenerService(),
    KoinComponent {
    private val webUiRepository: WebUiRepository by inject()
    private val packageRepository: InstalledPackageRepository by inject()
    private val appIconDataSource: AppIconDataSource by inject()
    private val refreshModules: RefreshInstalledModulesUseCase by inject()
    private val templateRepository: ProfileTemplateRepository by inject()

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val client = Wearable.getChannelClient(applicationContext)
        val path = channel.path
        if (path !in KNOWN_PATHS) {
            Log.w(TAG, "Closed channel on unknown path $path from ${channel.nodeId}")
            client.close(channel)
            return
        }
        bridgeScope.launch {
            if (!isPairedNode(channel.nodeId)) {
                Log.w(TAG, "Rejected $path from unpaired node ${channel.nodeId}")
                client.close(channel)
                return@launch
            }
            Log.d(TAG, "Serving $path from ${channel.nodeId}")
            when (path) {
                ProfileTemplateRepository.ONLINE_TEMPLATES_PATH ->
                    templateRepository.serveOnlineTemplates(client, channel)

                WearWebUiProtocol.CLAIM_PATH -> serveWebUiClaim(client, channel)

                else -> serveWebUiCall(client, channel, LocalWebUiBackend(webUiRepository, packageRepository, appIconDataSource)) {
                    // Like the local WebUI, the package list is loaded on first use.
                    if (packageRepository.packages.value.isEmpty()) packageRepository.refresh()
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearWebUiProtocol.CLOSED_PATH) {
            Log.w(TAG, "Ignored message on ${event.path} from ${event.sourceNodeId}")
            return
        }
        WearWebUiSession.end(event.data.decodeToString(), event.sourceNodeId)
        // The page may have changed the module, so the module list reloads.
        bridgeScope.launch { refreshModules(manual = false, checkUpdates = false) }
    }

    /** Whether [nodeId] is one of the paired, connected nodes this watch knows about. */
    private suspend fun isPairedNode(nodeId: String): Boolean = runCatching {
        Tasks.await(Wearable.getNodeClient(applicationContext).connectedNodes, 3, TimeUnit.SECONDS)
            .any { it.id == nodeId }
    }.onFailure { Log.w(TAG, "Node check failed for $nodeId", it) }.getOrDefault(false)

    private companion object {
        const val TAG = "WearWebUiService"

        val KNOWN_PATHS = setOf(
            ProfileTemplateRepository.ONLINE_TEMPLATES_PATH,
            WearWebUiProtocol.CLAIM_PATH,
            WearWebUiProtocol.RPC_PATH,
        )

        val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
