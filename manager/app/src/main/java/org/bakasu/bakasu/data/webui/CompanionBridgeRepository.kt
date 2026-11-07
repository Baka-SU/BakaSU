package org.bakasu.bakasu.data.webui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.model.BridgePeer
import org.bakasu.bakasu.domain.model.BridgeRequest
import org.bakasu.bakasu.domain.model.BridgeStage
import org.bakasu.bakasu.domain.model.BridgeState
import org.bakasu.bakasu.domain.model.BridgeTransfer

@SuppressLint("MissingPermission") // Constructed only after adapter() checks Bluetooth access.
internal class BridgeConnection(val socket: BluetoothSocket, private val onClosed: (BridgeConnection) -> Unit) : Closeable {
    val peer: String = socket.remoteDevice.address.uppercase(Locale.ROOT)
    val input = DataInputStream(socket.inputStream.buffered())
    val output = DataOutputStream(socket.outputStream.buffered())
    internal var challenge = ""
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

/** Bonded devices request consent over secure RFCOMM; WebUI calls keep the existing app authentication. */
@SuppressLint("MissingPermission") // adapter() checks BLUETOOTH_CONNECT before Bluetooth access.
class CompanionBridgeRepository(
    private val application: Application,
    private val preferences: AppSettingsRepository,
    private val applicationScope: CoroutineScope,
) {
    companion object {
        internal val SERVICE_UUID: UUID = UUID.fromString("4127a3ac-74f1-48fd-a418-7e81b39e117f")
        internal const val WIRE_VERSION = "bakasu-webui-rfcomm-1"
        private const val ADDRESS = "wear_bridge_address"
        private const val NAME = "wear_bridge_name"
        private const val SECRET = "wear_bridge_secret"
        private const val ENABLED = "wear_bridge_enabled"
        private const val REMEMBERED = "wear_bridge_remembered"
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
        remembered = preferences.getBoolean(REMEMBERED, false),
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

    internal fun markConnected(connection: BridgeConnection) {
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

    fun setRemembered(remembered: Boolean) {
        preferences.putBoolean(REMEMBERED, remembered)
        refresh()
    }

    internal fun savePeer(peer: BridgePeer, secret: String, remember: Boolean = false) {
        if (!state.value.peer?.address.equals(peer.address, true)) closeConnections()
        preferences.putString(ADDRESS, peer.address.uppercase(Locale.ROOT))
        preferences.putString(NAME, peer.name)
        preferences.putString(SECRET, secret)
        preferences.putBoolean(REMEMBERED, remember)
        refresh()
    }

    /** Lets the system restart the receiver when the associated watch appears; CDM-only and optional. */
    fun observePresence(address: String) {
        if (isWatch || Build.VERSION.SDK_INT < 31) return
        @Suppress("DEPRECATION")
        runCatching { application.getSystemService(CompanionDeviceManager::class.java)?.startObservingDevicePresence(address) }
            .onFailure { Log.w("CompanionBridge", "Presence observation unavailable", it) }
    }

    internal fun newSecret(): String = UUID.randomUUID().toString() + UUID.randomUUID().toString()

    val needsReceiverSetup get() = !preferences.contains(ENABLED)

    fun setEnabled(enabled: Boolean) {
        if (enabled) adapter()
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
        preferences.putBoolean(REMEMBERED, false)
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

    private val transferMutex = Mutex()
    private var outgoingJob: Job? = null
    private val permissions = BridgePermissionRequest()
    val permissionRequest = permissions.id
    private val mutableTransfer = MutableStateFlow<BridgeTransfer?>(null)
    val transfer = mutableTransfer.asStateFlow()
    private val mutableRequest = MutableStateFlow<BridgeRequest?>(null)
    val incomingRequest = mutableRequest.asStateFlow()
    private var pendingRequest: PendingBridgeRequest? = null

    @Volatile private var resumedActivity: WeakReference<Activity>? = null

    init {
        if (!isWatch) {
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumedActivity = WeakReference(activity)
                }
                override fun onActivityPaused(activity: Activity) {
                    if (resumedActivity?.get() === activity) resumedActivity = null
                }
                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
        }
    }

    fun answerPermission(id: String, granted: Boolean) = permissions.answer(id, granted)

    private suspend fun requirePermission() {
        if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) return
        if (!permissions.await()) throw BridgeException(BridgeFailure.PERMISSION)
    }

    fun dismissTransfer() {
        mutableTransfer.value = null
    }
    fun cancelTransfer() {
        outgoingJob?.cancel()
        permissions.cancel()
        mutableTransfer.value = null
    }

    @Synchronized
    internal fun beginRequest(request: BridgeRequest): PendingBridgeRequest {
        check(pendingRequest == null) { "A phone request is already pending" }
        return PendingBridgeRequest(request).also {
            pendingRequest = it
            mutableRequest.value = request
        }
    }

    @Synchronized
    fun presentRequest(id: String) {
        pendingRequest?.takeIf { it.request.id == id }?.presented?.complete(Unit)
    }

    @Synchronized
    fun approveRequest(id: String, remember: Boolean?) {
        pendingRequest?.takeIf { it.request.id == id }?.approval?.complete(remember)
    }

    @Synchronized
    fun completeRequest(id: String, opened: Boolean) {
        pendingRequest?.takeIf { it.request.id == id }?.opened?.complete(opened)
    }

    @Synchronized
    internal fun readyRequest(id: String) {
        mutableRequest.value?.takeIf { it.id == id }?.let { mutableRequest.value = it.copy(readyToOpen = true) }
    }

    @Synchronized
    internal fun endRequest(id: String) {
        if (pendingRequest?.request?.id != id) return
        pendingRequest?.approval?.cancel()
        pendingRequest?.opened?.cancel()
        pendingRequest = null
        mutableRequest.value = null
    }

    internal suspend fun showRequest(intent: Intent): Boolean = withContext(Dispatchers.Main) {
        resumedActivity?.get()?.takeUnless { it.isFinishing }?.let {
            runCatching { it.startActivity(intent) }.onFailure { error -> Log.w("CompanionBridge", "Request needs a notification", error) }.isSuccess
        } ?: false
    }

    internal fun savedSecret(address: String): String? = preferences.getString(SECRET)?.takeIf { state.value.peer?.address.equals(address, true) }

    /** Permission, consent and credentials are completed before the requested action is started. */
    suspend fun handoff(action: BridgeAction, value: String = "", title: String = ""): ByteArray = transferMutex.withLock {
        outgoingJob = currentCoroutineContext().job
        var token: String? = null
        try {
            mutableTransfer.value = null
            requirePermission()
            mutableTransfer.value = BridgeTransfer(BridgeStage.CONNECTING)
            require(BridgeHandoff.valid(action, value))
            val bonded = bondedPeers()
            if (bonded.isEmpty()) throw BridgeException(BridgeFailure.NOT_PAIRED)
            val candidates = bonded.sortedBy { !it.address.equals(state.value.peer?.address, true) }
            var reachedPhone = false
            for (peer in candidates) {
                try {
                    val result = socketRequest(peer.address) { connection ->
                        val initialServerNonce = handshake(connection.socket, 5) {
                            connection.socket.connect()
                            connection.output.apply {
                                writeUTF(WIRE_VERSION)
                                writeUTF(BridgeHandoff.OPERATION)
                                flush()
                            }
                            check(connection.input.readUTF() == "ready")
                            connection.input.readUTF()
                        }
                        reachedPhone = true
                        val operation = BridgeHandoff.authenticationAction(action, value, title)
                        val nonce = UUID.randomUUID().toString()
                        val previousKey = savedSecret(peer.address)
                        connection.output.apply {
                            writeUTF(action.name)
                            writeUTF(value)
                            writeUTF(title)
                            writeUTF(nonce)
                            writeUTF(previousKey?.let { BridgeAuthentication.proof(it, "client", operation, nonce, initialServerNonce) }.orEmpty())
                            flush()
                        }
                        while (true) {
                            val status = handshake(connection.socket, 10) { connection.input.readUTF() }
                            when (status) {
                                "waiting" -> {
                                    mutableTransfer.value = BridgeTransfer(BridgeStage.WAITING)
                                    connection.output.apply {
                                        writeBoolean(true)
                                        flush()
                                    }
                                }

                                "approved" -> break

                                "rejected" -> throw BridgeException(BridgeFailure.REJECTED)

                                "notifications-required" -> throw BridgeException(BridgeFailure.NOTIFICATIONS)

                                else -> throw BridgeException(BridgeFailure.CONNECTION)
                            }
                        }
                        handshake(connection.socket) {
                            val key = connection.input.readUTF()
                            require(key.length >= 32)
                            val serverNonce = connection.input.readUTF()
                            val proof = connection.input.readUTF()
                            check(BridgeAuthentication.matches(proof, BridgeAuthentication.proof(key, "server", operation, nonce, serverNonce)))
                            savePeer(peer, key)
                            connection.output.apply {
                                writeUTF(BridgeAuthentication.proof(key, "client", operation, nonce, serverNonce))
                                flush()
                            }
                            check(connection.input.readBoolean())
                            markConnected(connection)
                        }
                        mutableTransfer.value = BridgeTransfer(BridgeStage.CONNECTING)
                        if (action == BridgeAction.WEB_UI) {
                            token = WearWebUiSession.offer(value, title, peer.address)
                            startServer()
                            awaitServer()
                        }
                        connection.output.apply {
                            writeUTF("ready")
                            flush()
                        }
                        val bytes = handshake(connection.socket, 30) {
                            val (type, payload) = with(WearWebUiProtocol) { connection.input.readFrame() }
                            if (type != WearWebUiProtocol.FRAME_OK) throw BridgeException(BridgeFailure.CONNECTION)
                            payload
                        }
                        mutableTransfer.value = BridgeTransfer(BridgeStage.OPENED)
                        bytes
                    }
                    return@withLock result
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (reachedPhone) throw error
                }
            }
            throw BridgeException(BridgeFailure.CONNECTION)
        } catch (error: CancellationException) {
            mutableTransfer.value = null
            token?.let {
                WearWebUiSession.end(it)
                stopServer()
            }
            throw error
        } catch (error: Exception) {
            token?.let {
                WearWebUiSession.end(it)
                stopServer()
            }
            val failure = (error as? BridgeException)?.reason ?: BridgeFailure.CONNECTION
            mutableTransfer.value = BridgeTransfer(BridgeStage.FAILED, failure)
            throw BridgeException(failure, error)
        } finally {
            outgoingJob = null
        }
    }

    private suspend fun <T> socketRequest(address: String, block: suspend (BridgeConnection) -> T): T = coroutineScope {
        val socket = adapter().getRemoteDevice(address).createRfcommSocketToServiceRecord(SERVICE_UUID)
        suspendCancellableCoroutine { continuation ->
            val worker = launch(Dispatchers.IO) {
                try {
                    val result = connection(socket).use { block(it) }
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

    private fun credential(address: String): String = preferences.getString(SECRET)?.takeIf { state.value.peer?.address.equals(address, true) }
        ?: throw BridgeException(BridgeFailure.SETUP_REQUIRED)

    internal fun connect(address: String, operation: String): BridgeConnection {
        val key = credential(address)
        val socket = adapter().getRemoteDevice(address).createRfcommSocketToServiceRecord(SERVICE_UUID)
        return connectSocket(socket, operation, key)
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
                markConnected(connection)
                connection
            }
        } catch (error: Exception) {
            runCatching { socket.close() }
            throw BridgeException(BridgeFailure.CONNECTION, error)
        }
    }

    /** Handoffs first require consent; root WebUI calls always require the existing peer and app proof. */
    internal fun authenticate(connection: BridgeConnection): String = handshake(connection.socket) {
        val peer = connection.socket.remoteDevice.address
        check(connection.input.readUTF() == WIRE_VERSION)
        val operation = connection.input.readUTF()
        if (operation == BridgeHandoff.OPERATION) {
            check(!isWatch && state.value.enabled)
            check(adapter().bondedDevices.any { it.address.equals(peer, true) })
            connection.challenge = UUID.randomUUID().toString()
            connection.output.apply {
                writeUTF("ready")
                writeUTF(connection.challenge)
                flush()
            }
            return@handshake operation
        }
        check(isWatch && state.value.peer?.address.equals(peer, true))
        check(operation in setOf(WearWebUiProtocol.CLAIM_PATH, WearWebUiProtocol.RPC_PATH, WearWebUiProtocol.CLOSED_PATH))
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
        markConnected(connection)
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
