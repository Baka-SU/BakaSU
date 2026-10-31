package org.bakasu.bakasu.data.webui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.model.BridgePeer
import org.bakasu.bakasu.domain.model.BridgeState

@SuppressLint("MissingPermission") // Constructed only after adapter() checks Bluetooth access.
internal class BridgeConnection(val socket: BluetoothSocket, private val onClosed: (BridgeConnection) -> Unit) : Closeable {
    val peer: String = socket.remoteDevice.address.uppercase(Locale.ROOT)
    val input = DataInputStream(socket.inputStream.buffered())
    val output = DataOutputStream(socket.outputStream.buffered())
    private var closed = false

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        try {
            socket.close()
        } finally {
            onClosed(this)
        }
    }
}

/** CDM selects the phone's permitted watch; secure RFCOMM carries mutually authenticated app calls. */
@SuppressLint("MissingPermission") // adapter() checks BLUETOOTH_CONNECT before Bluetooth access.
class CompanionBridgeRepository(
    private val application: Application,
    private val preferences: AppSettingsRepository,
    private val applicationScope: CoroutineScope,
) {
    companion object {
        internal val SERVICE_UUID: UUID = UUID.fromString("4127a3ac-74f1-48fd-a418-7e81b39e117f")
        internal const val WIRE_VERSION = "bakasu-webui-rfcomm-1"
        internal const val PROVISION = "provision"
        internal const val OPEN = "open"
        private const val PROVISION_TIMEOUT_SECONDS = 5L
        private const val ADDRESS = "wear_bridge_address"
        private const val NAME = "wear_bridge_name"
        private const val SECRET = "wear_bridge_secret"
        private const val ENABLED = "wear_bridge_enabled"
        private val deadlines = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "webui-bt-handshake").apply { isDaemon = true } }

        internal fun <T> handshake(socket: BluetoothSocket, timeoutSeconds: Long = 15, block: () -> T): T {
            val deadline = deadlines.schedule({ runCatching { socket.close() } }, timeoutSeconds, TimeUnit.SECONDS)
            return try {
                block()
            } finally {
                deadline.cancel(false)
            }
        }
    }

    val isWatch = application.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

    private val activeConnections = ConcurrentHashMap.newKeySet<BridgeConnection>()
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()
    private val mutableListening = MutableStateFlow(false)
    internal val listening = mutableListening.asStateFlow()

    private fun read() = BridgeState(
        preferences.getString(ADDRESS)?.let { BridgePeer(it, preferences.getString(NAME).orEmpty().ifBlank { it }) },
        preferences.getBoolean(ENABLED, false),
        connected = activeConnections.isNotEmpty(),
    )

    fun refresh() {
        synchronized(activeConnections) {
            mutableState.value = read()
        }
    }

    internal fun connection(socket: BluetoothSocket): BridgeConnection = BridgeConnection(socket) {
        synchronized(activeConnections) {
            activeConnections.remove(it)
            mutableState.update { state -> state.copy(connected = activeConnections.isNotEmpty()) }
        }
    }

    private fun connected(connection: BridgeConnection) {
        synchronized(activeConnections) {
            activeConnections.add(connection)
            mutableState.update { it.copy(connected = true) }
        }
    }

    internal fun adapter(): BluetoothAdapter {
        val adapter = application.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) throw BridgeException(BridgeFailure.UNSUPPORTED)
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            throw BridgeException(BridgeFailure.PERMISSION)
        }
        if (!adapter.isEnabled) throw BridgeException(BridgeFailure.BLUETOOTH_DISABLED)
        return adapter
    }

    fun bondedPeers(): List<BridgePeer> = adapter().bondedDevices.map { BridgePeer(it.address, it.name.orEmpty().ifBlank { it.address }) }.sortedBy { it.name }

    /** The watch or phone this Manager was paired with through the companion device manager. */
    @Suppress("DEPRECATION")
    fun associatedPeer(): BridgePeer? {
        if (isWatch) return null
        if (!application.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) return null
        val manager = application.getSystemService(CompanionDeviceManager::class.java) ?: return null
        if (Build.VERSION.SDK_INT >= 33) {
            val info = manager.myAssociations.firstOrNull() ?: return null
            val address = info.deviceMacAddress?.toString() ?: return null
            val name = info.displayName?.toString().orEmpty()
                .ifBlank { runCatching { adapter().getRemoteDevice(address).name }.getOrNull().orEmpty() }
                .ifBlank { address }
            return BridgePeer(address, name)
        }
        val address = manager.associations.firstOrNull() ?: return null
        val name = runCatching { adapter().getRemoteDevice(address).name }.getOrNull().orEmpty().ifBlank { address }
        return BridgePeer(address, name)
    }

    /** Shows a watch restored from a previous association without granting access yet. */
    fun adopt(address: String, name: String) {
        if (isWatch || state.value.peer != null) return
        preferences.putString(ADDRESS, address.uppercase(Locale.ROOT))
        preferences.putString(NAME, name)
        preferences.putString(SECRET, null)
        preferences.putBoolean(ENABLED, false)
        refresh()
    }

    /** Authorizes [address] and starts the receiver; the watch fetches the key on first use. */
    fun link(address: String, name: String) {
        adapter()
        if (isWatch) throw BridgeException(BridgeFailure.ASSOCIATION)
        state.value.peer?.takeIf { !it.address.equals(address, true) }?.let { disconnect() }
        preferences.putString(ADDRESS, address.uppercase(Locale.ROOT))
        preferences.putString(NAME, name)
        if (preferences.getString(SECRET).isNullOrBlank()) preferences.putString(SECRET, newSecret())
        setEnabled(true)
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            runCatching { application.getSystemService(CompanionDeviceManager::class.java).startObservingDevicePresence(address) }
                .onFailure { Log.w("CompanionBridge", "Presence observation unavailable", it) }
        }
        refresh()
    }

    private fun newSecret(): String = UUID.randomUUID().toString() + UUID.randomUUID().toString()

    fun setEnabled(enabled: Boolean) {
        preferences.putBoolean(ENABLED, enabled)
        refresh()
        if (enabled) startServer() else stopServer()
    }

    @Suppress("DEPRECATION")
    fun unassociate(address: String) {
        if (isWatch) return
        val manager = application.getSystemService(CompanionDeviceManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            manager.myAssociations.filter { it.deviceMacAddress?.toString().equals(address, true) }.forEach { manager.disassociate(it.id) }
        } else if (manager.associations.any { it.equals(address, true) }) {
            manager.disassociate(address)
        }
    }

    fun disconnect() {
        val peer = state.value.peer
        stopServer()
        if (isWatch) WearWebUiSession.endCurrent()
        if (!isWatch && peer != null) unassociate(peer.address)
        preferences.putString(ADDRESS, null)
        preferences.putString(NAME, null)
        preferences.putString(SECRET, null)
        preferences.putBoolean(ENABLED, false)
        refresh()
    }

    internal fun startServer() {
        adapter()
        ContextCompat.startForegroundService(application, Intent(application, CompanionBridgeService::class.java))
    }

    internal suspend fun awaitServer() {
        if (withTimeoutOrNull(10_000) { listening.first { it } } == null) throw BridgeException(BridgeFailure.CONNECTION)
    }
    internal fun setListening(value: Boolean) {
        mutableListening.value = value
    }
    internal fun stopServer() {
        closeConnections()
        application.stopService(Intent(application, CompanionBridgeService::class.java))
    }

    internal fun closeConnections() {
        activeConnections.forEach { connection ->
            runCatching { connection.close() }.onFailure { Log.w("CompanionBridge", "Connection close failed", it) }
        }
    }

    /**
     * Fetches the shared key from a phone that already authorizes this watch. The phone answers
     * only for its stored watch, so no watch-side pairing is needed.
     */
    suspend fun provision(): BridgePeer = withContext(Dispatchers.IO) {
        val cached = state.value.peer
        val candidates = buildList {
            cached?.let(::add)
            bondedPeers().filterNot { peer -> cached?.address.equals(peer.address, true) == true }.forEach(::add)
        }.distinctBy { it.address.uppercase(Locale.ROOT) }
        for (peer in candidates) {
            val secret = runCatching { provisionSocket(peer.address) }.getOrNull() ?: continue
            if (secret.length < 32) continue
            preferences.putString(ADDRESS, peer.address.uppercase(Locale.ROOT))
            preferences.putString(NAME, peer.name)
            preferences.putString(SECRET, secret)
            refresh()
            return@withContext peer
        }
        throw BridgeException(BridgeFailure.SETUP_REQUIRED)
    }

    suspend fun ensureProvisioned(): BridgePeer = state.value.peer?.takeIf { !preferences.getString(SECRET).isNullOrBlank() } ?: provision()

    /** Drops a key the phone no longer accepts so the next call fetches a fresh one. */
    fun forgetSecret() {
        preferences.putString(SECRET, null)
        refresh()
    }

    private fun provisionSocket(address: String): String {
        val socket = adapter().getRemoteDevice(address).createRfcommSocketToServiceRecord(SERVICE_UUID)
        try {
            return handshake(socket, PROVISION_TIMEOUT_SECONDS) {
                socket.connect()
                val connection = connection(socket)
                try {
                    connection.output.apply {
                        writeUTF(WIRE_VERSION)
                        writeUTF(PROVISION)
                        flush()
                    }
                    connection.input.readUTF()
                } finally {
                    connection.close()
                }
            }
        } catch (error: Exception) {
            runCatching { socket.close() }
            throw BridgeException(BridgeFailure.CONNECTION, error)
        }
    }

    private fun credential(address: String): String = preferences.getString(SECRET)?.takeIf { state.value.peer?.address.equals(address, true) }
        ?: throw BridgeException(BridgeFailure.SETUP_REQUIRED)

    internal fun connect(address: String, operation: String): BridgeConnection {
        val key = credential(address)
        val socket = adapter().getRemoteDevice(address).createRfcommSocketToServiceRecord(SERVICE_UUID)
        return connectSocket(socket, operation, key)
    }

    /** Setup and opening are cancellable; canceling the Wear spinner closes the actual socket. */
    internal suspend fun <T> request(address: String, operation: String, block: (BridgeConnection) -> T): T {
        val key = credential(address)
        val socket = adapter().getRemoteDevice(address).createRfcommSocketToServiceRecord(SERVICE_UUID)
        return suspendCancellableCoroutine { continuation ->
            val worker = applicationScope.launch(Dispatchers.IO) {
                try {
                    val result = connectSocket(socket, operation, key).use { connection -> handshake(socket) { block(connection) } }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation {
                runCatching { socket.close() }
                worker.cancel()
            }
        }
    }

    private fun connectSocket(socket: BluetoothSocket, operation: String, key: String): BridgeConnection {
        try {
            return handshake(socket) {
                socket.connect()
                val connection = connection(socket)
                val clientNonce = UUID.randomUUID().toString()
                connection.output.apply {
                    writeUTF(WIRE_VERSION)
                    writeUTF(operation)
                    writeUTF(clientNonce)
                    flush()
                }
                val serverNonce = connection.input.readUTF()
                val serverProof = connection.input.readUTF()
                check(BridgeAuthentication.matches(serverProof, BridgeAuthentication.proof(key, "server", operation, clientNonce, serverNonce)))
                connection.output.apply {
                    writeUTF(BridgeAuthentication.proof(key, "client", operation, clientNonce, serverNonce))
                    flush()
                }
                check(connection.input.readBoolean())
                connected(connection)
                connection
            }
        } catch (error: Exception) {
            runCatching { socket.close() }
            throw BridgeException(BridgeFailure.CONNECTION, error)
        }
    }

    /** A connection is never handed to the root backend before peer and app proofs both match. */
    internal fun authenticate(connection: BridgeConnection): String = handshake(connection.socket) {
        val peer = connection.socket.remoteDevice.address
        check(connection.input.readUTF() == WIRE_VERSION)
        val operation = connection.input.readUTF()
        if (operation == PROVISION) {
            // The phone hands its key to the one watch the user authorized, and only while enabled.
            check(!isWatch && state.value.enabled && state.value.peer?.address.equals(peer, true))
            val secret = preferences.getString(SECRET)?.takeIf { it.isNotBlank() }
            check(!secret.isNullOrEmpty())
            connection.output.apply {
                writeUTF(secret)
                flush()
            }
            connected(connection)
            return@handshake operation
        }
        check(state.value.peer?.address.equals(peer, true))
        check(operation in if (isWatch) setOf(WearWebUiProtocol.CLAIM_PATH, WearWebUiProtocol.RPC_PATH, WearWebUiProtocol.CLOSED_PATH) else setOf(OPEN))
        val key = preferences.getString(SECRET)
        check(!key.isNullOrEmpty())
        val clientNonce = connection.input.readUTF()
        val serverNonce = UUID.randomUUID().toString()
        connection.output.apply {
            writeUTF(serverNonce)
            writeUTF(BridgeAuthentication.proof(key, "server", operation, clientNonce, serverNonce))
            flush()
        }
        check(BridgeAuthentication.matches(connection.input.readUTF(), BridgeAuthentication.proof(key, "client", operation, clientNonce, serverNonce)))
        connection.output.apply {
            writeBoolean(true)
            flush()
        }
        connected(connection)
        operation
    }

    internal fun notifyClosed(address: String, token: String) {
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                connect(address, WearWebUiProtocol.CLOSED_PATH).use { connection ->
                    connection.output.apply {
                        writeUTF(token)
                        flush()
                    }
                }
            }.onFailure { Log.w("CompanionBridge", "Session close failed", it) }
        }
    }
}
