package org.bakasu.bakasu.data.webui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.companion.CompanionDeviceService
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.packageinfo.AppIconDataSource
import org.bakasu.bakasu.data.packageinfo.InstalledPackageRepository
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.model.BridgePeer
import org.bakasu.bakasu.domain.model.BridgeRequest
import org.bakasu.bakasu.domain.usecase.RefreshInstalledModulesUseCase
import org.koin.android.ext.android.inject

/** Acknowledges a successfully claimed phone page, instead of assuming startActivity was displayed. */
internal object BridgeActivityLaunch {
    const val PEER = "bridge_peer"
    const val REQUEST = "bridge_request"
    val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    fun ready(request: String?) {
        request?.let { pending[it]?.complete(true) }
    }
    fun failed(request: String?) {
        request?.let { pending[it]?.complete(false) }
    }
}

/** Secure RFCOMM listener. Every accepted socket authenticates before any WebUI operation runs. */
@SuppressLint("MissingPermission") // adapter() checks BLUETOOTH_CONNECT before any Bluetooth access.
class CompanionBridgeService : Service() {
    private val repository: CompanionBridgeRepository by inject()
    private val webUiRepository: WebUiRepository by inject()
    private val packageRepository: InstalledPackageRepository by inject()
    private val icons: AppIconDataSource by inject()
    private val refreshModules: RefreshInstalledModulesUseCase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sockets = ConcurrentHashMap.newKeySet<BluetoothSocket>()
    private var server: BluetoothServerSocket? = null
    private var listener: Job? = null
    private val notifications get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.wear_bridge), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (repository.isWatch && !WearWebUiSession.isActive || !repository.isWatch && !repository.state.value.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            repository.adapter()
            val destination = if (repository.isWatch) "org.bakasu.bakasu.ui.wear.WearMainActivity" else "org.bakasu.bakasu.ui.webui.WearBridgeSetupActivity"
            val content = PendingIntent.getActivity(this, ONGOING_ID, Intent().setClassName(this, destination), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(getString(R.string.wear_bridge))
                .setContentText(getString(R.string.wear_bridge_receiving))
                .setContentIntent(content).setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(ONGOING_ID, notification)
            if (listener?.isActive != true) {
                listener = scope.launch {
                    try {
                        val listeningSocket = repository.adapter().listenUsingRfcommWithServiceRecord(getString(R.string.wear_bridge), CompanionBridgeRepository.SERVICE_UUID)
                        server = listeningSocket
                        repository.setListening(true)
                        while (isActive) {
                            val socket = listeningSocket.accept()
                            sockets.add(socket)
                            launch { serve(socket) }
                        }
                    } catch (error: Exception) {
                        if (isActive) Log.w(TAG, "Bluetooth listener stopped", error)
                    } finally {
                        repository.setListening(false)
                        stopSelf()
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Bluetooth listener unavailable", error)
            stopSelf()
        }
        return if (repository.isWatch) START_NOT_STICKY else START_STICKY
    }

    private suspend fun serve(socket: BluetoothSocket) {
        try {
            repository.connection(socket).use { connection ->
                when (repository.authenticate(connection)) {
                    BridgeHandoff.OPERATION -> serveHandoff(connection)

                    WearWebUiProtocol.CLAIM_PATH -> serveWebUiClaim(connection)

                    WearWebUiProtocol.RPC_PATH -> serveWebUiCall(connection, LocalWebUiBackend(webUiRepository, packageRepository, icons)) {
                        if (packageRepository.packages.value.isEmpty()) packageRepository.refresh()
                    }

                    WearWebUiProtocol.CLOSED_PATH -> {
                        WearWebUiSession.end(connection.input.readUTF(), socket.remoteDevice.address)
                        refreshModules(manual = false, checkUpdates = false)
                        if (!WearWebUiSession.isActive) stopSelf()
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Bluetooth WebUI connection ended", error)
        } finally {
            sockets.remove(socket)
            socket.close()
        }
    }

    private suspend fun serveHandoff(connection: BridgeConnection) {
        val request = CompanionBridgeRepository.handshake(connection.socket) {
            val action = BridgeAction.valueOf(connection.input.readUTF())
            val value = connection.input.readUTF()
            val title = connection.input.readUTF()
            require(BridgeHandoff.valid(action, value))
            val clientNonce = connection.input.readUTF()
            val clientProof = connection.input.readUTF()
            val operation = BridgeHandoff.authenticationAction(action, value, title)
            val key = repository.savedSecret(connection.peer)
            val authenticated = key != null && BridgeAuthentication.matches(
                clientProof,
                BridgeAuthentication.proof(key, "client", operation, clientNonce, connection.challenge),
            )
            val peer = BridgePeer(connection.peer, connection.socket.remoteDevice.name.orEmpty().ifBlank { connection.peer })
            Triple(
                repository.beginRequest(BridgeRequest(UUID.randomUUID().toString(), peer, action, value, title, !authenticated || !repository.state.value.remembered)),
                clientNonce,
                if (authenticated) key else null,
            )
        }
        val pending = request.first
        val details = pending.request
        val operation = BridgeHandoff.authenticationAction(details.action, details.value, details.title)
        try {
            val intent = Intent().setClassName(this, "org.bakasu.bakasu.ui.webui.BridgeRequestActivity")
                .putExtra(BridgeActivityLaunch.REQUEST, details.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val dispatched = repository.showRequest(intent)
            if (!dispatched || withTimeoutOrNull(1_000) { pending.presented.await() } == null) {
                notifyRequest(intent)
            }
            // A small round trip detects cancellation/disconnection while the user reads the phone.
            val answered = withTimeoutOrNull(BridgeHandoff.CONFIRMATION_TIMEOUT_MS) {
                while (!pending.approval.isCompleted) {
                    connection.output.apply {
                        writeUTF("waiting")
                        flush()
                    }
                    CompanionBridgeRepository.handshake(connection.socket, 10) { check(connection.input.readBoolean()) }
                    withTimeoutOrNull(1_000) { pending.approval.await() }
                }
                true
            } == true
            if (!answered) throw BridgeException(BridgeFailure.CONNECTION)
            val remember = pending.approval.await()
            if (remember == null) {
                connection.output.apply {
                    writeUTF("rejected")
                    flush()
                }
                return
            }
            notifications.cancel(OPEN_ID)
            val key = request.third ?: repository.newSecret()
            CompanionBridgeRepository.handshake(connection.socket) {
                connection.output.apply {
                    writeUTF("approved")
                    writeUTF(key)
                    writeUTF(connection.challenge)
                    writeUTF(BridgeAuthentication.proof(key, "server", operation, request.second, connection.challenge))
                    flush()
                }
                check(BridgeAuthentication.matches(connection.input.readUTF(), BridgeAuthentication.proof(key, "client", operation, request.second, connection.challenge)))
                repository.savePeer(details.peer, key, remember)
                repository.markConnected(connection)
                connection.output.apply {
                    writeBoolean(true)
                    flush()
                }
                // The watch creates the WebUI session only now, after approval, then starts listening.
                check(connection.input.readUTF() == "ready")
            }
            val opened = if (details.action == BridgeAction.WEB_UI) {
                val ready = CompletableDeferred<Boolean>()
                BridgeActivityLaunch.pending[details.id] = ready
                try {
                    repository.readyRequest(details.id)
                    withTimeoutOrNull(25_000) { ready.await() } == true
                } finally {
                    BridgeActivityLaunch.pending.remove(details.id)
                }
            } else {
                repository.readyRequest(details.id)
                withTimeoutOrNull(25_000) { pending.opened.await() } == true
            }
            with(WearWebUiProtocol) { connection.output.writeFrame(if (opened) FRAME_OK else FRAME_ERROR, byteArrayOf()) }
        } catch (error: BridgeException) {
            connection.output.apply {
                writeUTF(if (error.reason == BridgeFailure.NOTIFICATIONS) "notifications-required" else "failed")
                flush()
            }
        } finally {
            repository.endRequest(details.id)
            notifications.cancel(OPEN_ID)
        }
    }

    private fun notifyRequest(intent: Intent) {
        if (!notifications.areNotificationsEnabled() || notifications.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE ||
            Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            throw BridgeException(BridgeFailure.NOTIFICATIONS)
        }
        val pending = PendingIntent.getActivity(this, OPEN_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notifications.notify(
            OPEN_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle(getString(R.string.wear_bridge_notification_title))
                .setContentText(getString(R.string.wear_bridge_notification))
                .setContentIntent(pending).setAutoCancel(true).build(),
        )
    }

    override fun onDestroy() {
        repository.incomingRequest.value?.id?.let(repository::endRequest)
        notifications.cancel(OPEN_ID)
        repository.setListening(false)
        repository.closeConnections()
        runCatching { server?.close() }.onFailure { Log.w(TAG, "Listener close failed", it) }
        sockets.forEach { socket -> runCatching { socket.close() }.onFailure { Log.w(TAG, "Socket close failed", it) } }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "CompanionBridge"
        const val CHANNEL = "wear_webui_bridge"
        const val ONGOING_ID = 47001
        const val OPEN_ID = 47002
    }
}

/** CDM can restore the enabled phone receiver when its associated watch is nearby. */
@RequiresApi(31)
class BridgeCompanionDeviceService : CompanionDeviceService() {
    private val repository: CompanionBridgeRepository by inject()
    override fun onDeviceAppeared(address: String) {
        if (!repository.isWatch && repository.state.value.enabled && repository.state.value.peer?.address.equals(address, true)) {
            runCatching { repository.startServer() }.onFailure { Log.w("CompanionBridge", "Background receiver could not start", it) }
        }
    }
}
