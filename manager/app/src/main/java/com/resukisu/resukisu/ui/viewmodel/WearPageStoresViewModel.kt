package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Retains page models across configuration changes, releasing them after leaving the page. */
class WearPageStoresViewModel : ViewModel() {
    private val owners = mutableMapOf<String, ViewModelStoreOwner>()
    private val pendingRelease = mutableMapOf<String, Job>()

    fun acquire(key: String): ViewModelStoreOwner {
        pendingRelease.remove(key)?.cancel()
        return owners.getOrPut(key) {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    }

    fun release(key: String, running: Flow<Boolean>?) {
        pendingRelease.remove(key)?.cancel()
        val release = viewModelScope.launch(start = CoroutineStart.LAZY) {
            // Leaving a running flash/script must not cancel its root command.
            running?.first { !it }
            owners.remove(key)?.viewModelStore?.clear()
            pendingRelease.remove(key)
        }
        pendingRelease[key] = release
        release.start()
    }

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }
}
