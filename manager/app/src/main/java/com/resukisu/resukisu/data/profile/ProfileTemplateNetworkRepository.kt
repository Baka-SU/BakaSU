package com.resukisu.resukisu.data.profile

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.resukisu.resukisu.data.network.NetworkRequestRepository
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Online template details travel through the paired phone when its Manager supports this path. */
class ProfileTemplateNetworkRepository(
    private val application: Application,
    private val network: NetworkRequestRepository,
) {
    suspend fun fetchBodies(): List<String> = withContext(Dispatchers.IO) {
        if (application.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            val nodes = runCatching {
                val localId = Tasks.await(Wearable.getNodeClient(application).localNode, 5, TimeUnit.SECONDS).id
                Tasks.await(Wearable.getCapabilityClient(application)
                    .getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE), 5, TimeUnit.SECONDS).nodes
                    .filter { it.id != localId }
            }.getOrNull().orEmpty()
            val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            // A phone without network or a dropped transfer falls back to the watch's own network.
            if (phone != null) runCatching { fetchFromPhone(phone.id) }
                .onFailure { if (it is CancellationException) throw it }
                .onFailure { Log.w("ProfileTemplateNetwork", "Phone template fetch failed", it) }
                .getOrNull()?.let { return@withContext it }
        }
        fetchLocalBodies()
    }

    // The phone only accesses the existing official template endpoint, never a caller-provided URL.
    private suspend fun fetchLocalBodies(): List<String> = coroutineScope {
        val ids = JSONArray(network.fetch("https://kernelsu.org/templates/index.json",
            callTimeoutSeconds = 15).getOrThrow())
        (0 until ids.length()).map { index -> async {
            val id = ids.getString(index)
            require(id.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid template ID" }
            network.fetch("https://kernelsu.org/templates/$id", callTimeoutSeconds = 15).getOrThrow()
        } }.map { it.await() }
    }

    private suspend fun fetchFromPhone(nodeId: String): List<String> {
        val client = Wearable.getChannelClient(application)
        val channel = Tasks.await(client.openChannel(nodeId, PATH), 5, TimeUnit.SECONDS)
        try {
            val input = DataInputStream(Tasks.await(client.getInputStream(channel), 5, TimeUnit.SECONDS))
            return run {
                // Closing the stream bounds a blocking read even if the phone disconnects mid-transfer.
                val timeout = CoroutineScope(Dispatchers.IO).launch { delay(45_000); input.close() }
                try {
                    input.use {
                        check(it.readBoolean()) { "Phone template fetch failed" }
                        val length = it.readInt()
                        require(length in 1..1_048_576) { "Invalid template response size" }
                        val bytes = ByteArray(length)
                        it.readFully(bytes)
                        val bodies = JSONArray(bytes.decodeToString())
                        (0 until bodies.length()).map(bodies::getString)
                    }
                } finally { timeout.cancel() }
            }
        } finally { client.close(channel) }
    }

    suspend fun serve(client: ChannelClient, channel: ChannelClient.Channel) {
        try {
            DataOutputStream(Tasks.await(client.getOutputStream(channel), 5, TimeUnit.SECONDS)).use { output ->
                val result = runCatching { JSONArray(fetchLocalBodies()).toString().toByteArray() }
                output.writeBoolean(result.isSuccess)
                result.getOrNull()?.let { bytes -> output.writeInt(bytes.size); output.write(bytes) }
                output.flush()
            }
        } catch (error: Exception) {
            Log.w("ProfileTemplateNetwork", "Phone template transfer failed", error)
        } finally { client.close(channel) }
    }

    companion object {
        const val PATH = "/resukisu/templates/online"
        const val CAPABILITY = "resukisu_profile_templates"
    }
}
