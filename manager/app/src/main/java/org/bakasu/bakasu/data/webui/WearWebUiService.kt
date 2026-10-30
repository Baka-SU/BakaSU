package org.bakasu.bakasu.data.webui

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bakasu.bakasu.data.profile.ProfileTemplateRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Phone-assisted templates retain the same-app Data Layer; module WebUI uses RFCOMM separately. */
class WearWebUiService :
    WearableListenerService(),
    KoinComponent {
    private val templates: ProfileTemplateRepository by inject()
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val client = Wearable.getChannelClient(applicationContext)
        if (channel.path != ProfileTemplateRepository.ONLINE_TEMPLATES_PATH) {
            client.close(channel)
            return
        }
        scope.launch {
            val paired = runCatching {
                Tasks.await(Wearable.getNodeClient(applicationContext).connectedNodes, 3, TimeUnit.SECONDS).any { it.id == channel.nodeId }
            }.onFailure { Log.w("WearTemplates", "Paired node check failed", it) }.getOrDefault(false)
            if (paired) templates.serveOnlineTemplates(client, channel) else client.close(channel)
        }
    }
    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
