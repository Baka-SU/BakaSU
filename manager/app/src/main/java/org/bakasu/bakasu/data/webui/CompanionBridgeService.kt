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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.packageinfo.AppIconDataSource
import org.bakasu.bakasu.data.packageinfo.InstalledPackageRepository
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
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
        if (repository.state.value.peer == null || repository.isWatch && !WearWebUiSession.isActive || !repository.isWatch && !repository.state.value.enabled) {
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
            BridgeConnection(socket).use { connection ->
                when (repository.authenticate(connection)) {
                    CompanionBridgeRepository.PAIR -> Unit

                    CompanionBridgeRepository.OPEN -> {
                        val response = try {
                            if (openPhonePage(socket.remoteDevice.address)) "opened" else "notification"
                        } catch (error: BridgeException) {
                            if (error.reason == BridgeFailure.NOTIFICATIONS) "notifications-required" else "failed"
                        }
                        connection.output.apply {
                            writeUTF(response)
                            flush()
                        }
                    }

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

    private suspend fun openPhonePage(peer: String): Boolean {
        val request = UUID.randomUUID().toString()
        val ready = CompletableDeferred<Boolean>()
        BridgeActivityLaunch.pending[request] = ready
        val intent = Intent().setClassName(this, "org.bakasu.bakasu.ui.webui.RemoteWebUIActivity")
            .putExtra(BridgeActivityLaunch.PEER, peer).putExtra(BridgeActivityLaunch.REQUEST, request).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val dispatched = runCatching { withContext(Dispatchers.Main) { startActivity(intent) } }
                .onFailure { Log.i(TAG, "Phone page needs the notification entry", it) }.isSuccess
            val opened = if (dispatched) withTimeoutOrNull(3_000) { ready.await() } else null
            if (opened == true) return true
            if (opened == false) throw BridgeException(BridgeFailure.CONNECTION)
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
            return false
        } finally {
            BridgeActivityLaunch.pending.remove(request)
        }
    }

    override fun onDestroy() {
        repository.setListening(false)
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
