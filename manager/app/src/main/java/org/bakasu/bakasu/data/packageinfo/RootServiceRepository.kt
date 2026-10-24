package org.bakasu.bakasu.data.packageinfo

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.IBinder
import android.os.RemoteException
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bakasu.bakasu.data.shell.KsuCliRepository
import org.bakasu.bakasu.domain.model.SuperUserLoadFailure
import org.bakasu.rootService.IKsuInterface

class RootServiceException(val reason: SuperUserLoadFailure, cause: Throwable? = null) : IOException(reason.name, cause)

class RootServiceRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    private val requestMutex = Mutex()
    private val mainExecutor = ContextCompat.getMainExecutor(application)

    private data class Binding(val binder: IBinder, val connection: ServiceConnection)

    suspend fun getInstalledPackages(): List<PackageInfo> = requestMutex.withLock {
        withContext(Dispatchers.IO) {
            val shell = try {
                ksuCliRepository.createRootShell()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw RootServiceException(SuperUserLoadFailure.SERVICE_START_FAILED, error)
            }
            shell.use {
                if (!shell.isRoot) throw RootServiceException(SuperUserLoadFailure.ROOT_ACCESS_REQUIRED)
                var binding = connectService(shell)
                try {
                    try {
                        readPackages(binding.binder)
                    } catch (_: RemoteException) {
                        // Retry the complete snapshot rather than combining pages from two services.
                        unbind(binding.connection)
                        binding = connectService(shell)
                        readPackages(binding.binder)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: RootServiceException) {
                    throw error
                } catch (error: Exception) {
                    throw RootServiceException(SuperUserLoadFailure.PACKAGE_LOAD_FAILED, error)
                } finally {
                    unbind(binding.connection)
                }
            }
        }
    }

    private fun readPackages(binder: IBinder): List<PackageInfo> {
        val service = IKsuInterface.Stub.asInterface(binder)
        val total = service.packageCount
        check(total > 0) { "Root service returned no installed packages" }
        return buildList {
            while (size < total) {
                val page = service.getPackages(size, minOf(100, total - size))
                check(page.isNotEmpty() && page.size <= total - size) { "Root service returned an incomplete package snapshot" }
                addAll(page)
            }
        }
    }

    private suspend fun connectService(shell: Shell): Binding = withTimeoutOrNull(30.seconds) {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    private fun fail() {
                        unbindOnMain(this)
                        if (continuation.isActive) continuation.resumeWithException(RootServiceException(SuperUserLoadFailure.SERVICE_START_FAILED))
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (continuation.isActive) fail()
                    }

                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (binder == null) {
                            fail()
                        } else if (continuation.isActive) {
                            continuation.resume(Binding(binder, this))
                        } else {
                            unbindOnMain(this)
                        }
                    }

                    override fun onNullBinding(name: ComponentName?) = fail()

                    override fun onBindingDied(name: ComponentName?) {
                        if (continuation.isActive) fail()
                    }
                }
                continuation.invokeOnCancellation { unbindOnMain(connection) }
                fun fail(error: Exception) {
                    mainExecutor.execute {
                        if (continuation.isActive) {
                            unbindOnMain(connection)
                            continuation.resumeWithException(RootServiceException(SuperUserLoadFailure.SERVICE_START_FAILED, error))
                        }
                    }
                }
                try {
                    val task = RootService.bindOrTask(Intent(application, KsuService::class.java), mainExecutor, connection)
                    if (task != null) {
                        Shell.EXECUTOR.execute {
                            try {
                                shell.execTask(task)
                            } catch (error: Exception) {
                                fail(error)
                            }
                        }
                    }
                } catch (error: Exception) {
                    fail(error)
                }
            }
        }
    } ?: throw RootServiceException(SuperUserLoadFailure.SERVICE_START_FAILED)

    private suspend fun unbind(connection: ServiceConnection) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        runCatching { RootService.unbind(connection) }
    }

    private fun unbindOnMain(connection: ServiceConnection) {
        mainExecutor.execute { runCatching { RootService.unbind(connection) } }
    }
}
