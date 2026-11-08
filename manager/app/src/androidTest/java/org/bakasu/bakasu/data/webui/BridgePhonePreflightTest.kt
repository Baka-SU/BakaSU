package org.bakasu.bakasu.data.webui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.BridgeException
import org.bakasu.bakasu.domain.model.BridgeFailure
import org.bakasu.bakasu.domain.model.BridgeStage
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

class BridgePhonePreflightTest {
    @Test
    @SdkSuppress(minSdkVersion = 37)
    fun knownUnavailablePhoneDoesNotEnterConnectionProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        assumeTrue(adapter != null && adapter.isEnabled)
        val devices = adapter.bondedDevices
        assumeTrue(devices.none { it.isConnected(BluetoothDevice.TRANSPORT_BREDR) || it.isConnected(BluetoothDevice.TRANSPORT_LE) })
        val repository = GlobalContext.get().get<CompanionBridgeRepository>()
        repository.dismissTransfer()
        val stages = mutableListOf<BridgeStage>()
        val observation = launch(start = CoroutineStart.UNDISPATCHED) { repository.transfer.collect { it?.stage?.let(stages::add) } }
        val start = SystemClock.elapsedRealtime()
        val error = runCatching { repository.handoff(BridgeAction.LINK, "https://example.com") }.exceptionOrNull()
        val elapsed = SystemClock.elapsedRealtime() - start
        observation.cancelAndJoin()
        assertTrue(error is BridgeException && error.reason in setOf(BridgeFailure.NOT_PAIRED, BridgeFailure.NOT_CONNECTED))
        assertTrue(BridgeStage.CONNECTING !in stages && BridgeStage.CHECKING !in stages)
        assertTrue("An unavailable phone must skip the 5-second socket probe", elapsed < 5_000)
        println("Phone preflight: bonded=${devices.size}, reason=${(error as BridgeException).reason}, elapsedMs=$elapsed")
        repository.dismissTransfer()
    }
}
