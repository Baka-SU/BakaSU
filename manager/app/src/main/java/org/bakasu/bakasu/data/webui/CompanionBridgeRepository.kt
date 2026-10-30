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
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
internal class BridgeConnection(val socket: BluetoothSocket) : Closeable {
    val peer: String = socket.remoteDevice.address.uppercase(Locale.ROOT)
    val input = DataInputStream(socket.inputStream.buffered())
    val output = DataOutputStream(socket.outputStream.buffered())
    override fun close() = socket.close()
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
        internal const val PAIR = "pair"
        internal const val OPEN = "open"
        private const val ADDRESS = "wear_bridge_address"
        private const val NAME = "wear_bridge_name"
        private const val SECRET = "wear_bridge_secret"
        private const val ENABLED = "wear_bridge_enabled"
        private val deadlines = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "webui-bt-handshake").apply { isDaemon = true } }

        internal fun <T> handshake(socket: BluetoothSocket, block: () -> T): T {
            val deadline = deadlines.schedule({ runCatching { socket.close() } }, 15, TimeUnit.SECONDS)
            return try {
                block()
            } finally {
                deadline.cancel(false)
            }
        }
    }

    val isWatch = application.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

    @Volatile private var pendingCode: String? = null

    @Volatile private var codeExpiresAt = 0L
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()
    private val mutableListening = MutableStateFlow(false)
    internal val listening = mutableListening.asStateFlow()

    private fun read() = BridgeState(
        preferences.getString(ADDRESS)?.let { BridgePeer(it, preferences.getString(NAME).orEmpty().ifBlank { it }) },
        !preferences.getString(SECRET).isNullOrBlank(),
        preferences.getBoolean(ENABLED, false),
        pendingCode?.chunked(4)?.joinToString("-"),
    )

    fun refresh() {
        mutableState.value = read()
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

    @Suppress("DEPRECATION")
    internal fun isAssociated(address: String): Boolean {
        if (isWatch) return true
        if (!application.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) return false
        val manager = application.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            manager.myAssociations.any { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
        } else {
            manager.associations.any { it.equals(address, ignoreCase = true) }
        }
    }

    /** Called only after the phone's CDM consent flow has created this association. */
    fun beginPairing(address: String, name: String) {
        adapter()
        if (isWatch || !isAssociated(address)) throw BridgeException(BridgeFailure.ASSOCIATION)
        state.value.peer?.takeIf { !it.address.equals(address, true) }?.let { disconnect() }
        preferences.putString(ADDRESS, address.uppercase(Locale.ROOT))
        preferences.putString(NAME, name)
        preferences.putString(SECRET, null)
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        pendingCode = buildString { repeat(12) { append(alphabet[random.nextInt(alphabet.length)]) } }
        codeExpiresAt = SystemClock.elapsedRealtime() + 120_000
        setEnabled(true)
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            runCatching { application.getSystemService(CompanionDeviceManager::class.java).startObservingDevicePresence(address) }
                .onFailure { Log.w("CompanionBridge", "Presence observation unavailable", it) }
        }
        refresh()
    }

    suspend fun pair(peer: BridgePeer, code: String) = withContext(Dispatchers.IO) {
        try {
            val secret = request(peer.address, PAIR, code.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT)) { it.input.readUTF() }
            require(secret.length >= 32)
            preferences.putString(ADDRESS, peer.address.uppercase(Locale.ROOT))
            preferences.putString(NAME, peer.name)
            preferences.putString(SECRET, secret)
            refresh()
        } catch (error: CancellationException) {
            throw error
        } catch (error: BridgeException) {
            throw error
        } catch (error: Exception) {
            throw BridgeException(BridgeFailure.PAIRING, error)
        }
    }

    fun setEnabled(enabled: Boolean) {
        preferences.putBoolean(ENABLED, enabled)
        refresh()
        if (enabled) startServer() else stopServer()
    }

    @Suppress("DEPRECATION")
    fun disconnect() {
        val peer = state.value.peer
        stopServer()
        if (isWatch) WearWebUiSession.endCurrent()
        if (!isWatch && peer != null) {
            val manager = application.getSystemService(CompanionDeviceManager::class.java)
            if (Build.VERSION.SDK_INT >= 33) {
                manager?.myAssociations?.filter { it.deviceMacAddress?.toString().equals(peer.address, true) }?.forEach { manager.disassociate(it.id) }
            } else if (manager?.associations?.any { it.equals(peer.address, true) } == true) {
                manager.disassociate(peer.address)
            }
        }
        preferences.putString(ADDRESS, null)
        preferences.putString(NAME, null)
        preferences.putString(SECRET, null)
        preferences.putBoolean(ENABLED, false)
        pendingCode = null
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
        application.stopService(Intent(application, CompanionBridgeService::class.java))
    }

    private fun credential(address: String, pairingCode: String?): String {
        if (!isWatch && !isAssociated(address)) throw BridgeException(BridgeFailure.SETUP_REQUIRED)
        return pairingCode ?: preferences.getString(SECRET)?.takeIf { state.value.peer?.address.equals(address, true) }
            ?: throw BridgeException(BridgeFailure.SETUP_REQUIRED)
    }

    internal fun connect(address: String, operation: String): BridgeConnection {
        val key = credential(address, null)
        val socket = adapter().getRemoteDevice(address).createRfcommSocketToServiceRecord(SERVICE_UUID)
        return connectSocket(socket, operation, key)
    }

    /** Setup and opening are cancellable; canceling the Wear spinner closes the actual socket. */
    internal suspend fun <T> request(address: String, operation: String, pairingCode: String? = null, block: (BridgeConnection) -> T): T {
        val key = credential(address, pairingCode)
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
                val connection = BridgeConnection(socket)
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
                connection
            }
        } catch (error: Exception) {
            socket.close()
            throw BridgeException(if (operation == PAIR) BridgeFailure.PAIRING else BridgeFailure.CONNECTION, error)
        }
    }

    /** A connection is never handed to the root backend before peer and app proofs both match. */
    internal fun authenticate(connection: BridgeConnection): String = handshake(connection.socket) {
        val peer = connection.socket.remoteDevice.address
        check(state.value.peer?.address.equals(peer, true) && isAssociated(peer))
        check(connection.input.readUTF() == WIRE_VERSION)
        val operation = connection.input.readUTF()
        check(operation in if (isWatch) setOf(WearWebUiProtocol.CLAIM_PATH, WearWebUiProtocol.RPC_PATH, WearWebUiProtocol.CLOSED_PATH) else setOf(PAIR, OPEN))
        val pairing = operation == PAIR
        val key = if (pairing) pendingCode?.takeIf { SystemClock.elapsedRealtime() < codeExpiresAt } else preferences.getString(SECRET)
        check(!key.isNullOrEmpty())
        val clientNonce = connection.input.readUTF()
        val serverNonce = UUID.randomUUID().toString()
        connection.output.apply {
            writeUTF(serverNonce)
            writeUTF(BridgeAuthentication.proof(key, "server", operation, clientNonce, serverNonce))
            flush()
        }
        val valid = BridgeAuthentication.matches(connection.input.readUTF(), BridgeAuthentication.proof(key, "client", operation, clientNonce, serverNonce))
        if (pairing && !valid) {
            pendingCode = null
            refresh()
        }
        check(valid)
        connection.output.apply {
            writeBoolean(true)
            flush()
        }
        if (pairing) {
            val secret = UUID.randomUUID().toString()
            preferences.putString(SECRET, secret)
            pendingCode = null
            connection.output.apply {
                writeUTF(secret)
                flush()
            }
            refresh()
        }
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
