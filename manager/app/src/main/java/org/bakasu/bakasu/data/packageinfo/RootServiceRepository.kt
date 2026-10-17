package org.bakasu.bakasu.data.packageinfo

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bakasu.rootService.IKsuInterface

/** The root service could not start because the root shell was refused. */
class RootAccessDeniedException : IllegalStateException("Root access denied")

class RootServiceRepository(
    private val application: Application,
) {
    private val requestMutex = Mutex()

    // The service stays bound for the life of the app. Stopping it after each request let the next
    // bind reach the exiting root process (libsu's stop is oneway), so that request failed.
    private var binder: IBinder? = null

    suspend fun getInstalledPackages(): List<PackageInfo> = requestMutex.withLock {
        val service = IKsuInterface.Stub.asInterface(connectedBinder())
        try {
            withContext(Dispatchers.IO) {
                val total = service.packageCount
                buildList {
                    var start = 0
                    while (start < total) {
                        val page = service.getPackages(start, 100)
                        if (page.isEmpty()) break
                        addAll(page)
                        start += page.size
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            binder = null
            throw IllegalStateException("Root service request failed", error)
        }
    }

    private suspend fun connectedBinder(): IBinder {
        binder?.takeIf { it.isBinderAlive }?.let { return it }
        val intent = Intent(application, KsuService::class.java)
        val connected = withTimeoutOrNull(10000.milliseconds) { connectService(intent) }
        if (connected == null) {
            // libsu starts the service from the main shell; only a shell that is not root means
            // root was refused. Other failures, or a check that cannot finish, stay generic errors.
            val granted = withContext(Dispatchers.IO) { runCatching { Shell.getShell().isRoot }.getOrNull() }
            if (granted == false) throw RootAccessDeniedException()
            throw IllegalStateException("Root service unavailable")
        }
        binder = connected
        return connected
    }

    private suspend fun connectService(intent: Intent): IBinder? = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName?) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (continuation.isActive) {
                        continuation.resume(binder)
                    } else {
                        stopServiceOnMain(intent)
                    }
                }

                override fun onNullBinding(name: ComponentName?) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onBindingDied(name: ComponentName?) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }

            continuation.invokeOnCancellation { stopServiceOnMain(intent) }
            runCatching {
                RootService.bind(intent, Shell.EXECUTOR, connection)
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun stopServiceOnMain(intent: Intent) {
        ContextCompat.getMainExecutor(application).execute {
            runCatching { RootService.stop(intent) }
        }
    }
}
