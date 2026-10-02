package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.data.file.WearFileRepository
import com.resukisu.resukisu.domain.model.FlashOperation
import com.resukisu.resukisu.domain.model.LkmSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** The phone's LKM install methods: patch a selected image, patch the current slot, or the inactive slot after OTA. */
enum class WearLkmMethod { SELECT_FILE, DIRECT, INACTIVE_SLOT }

/**
 * The choices of the phone Install screen for both LKM patching and AnyKernel3 flashing. They live
 * here, not in the pages, because picking a file leaves the page.
 */
data class WearKernelInstallState(
    val method: WearLkmMethod? = null,
    val bootUri: String? = null,
    val bootName: String? = null,
    val lkmUri: String? = null,
    val lkmName: String? = null,
    val lkmRejected: Boolean = false,
    val kmi: String? = null,
    val partition: String? = null,
    val allowShell: Boolean = false,
    val enableAdb: Boolean = false,
    val forceBackup: Boolean = false,
    val ak3Uri: String? = null,
    val ak3Name: String? = null,
    val slot: String? = null,
    val skipKsud: Boolean = false,
)

class WearKernelInstallViewModel(private val files: WearFileRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(WearKernelInstallState())
    val state = mutableState.asStateFlow()

    fun setMethod(method: WearLkmMethod) = mutableState.update { it.copy(method = method) }

    fun setBootImage(uri: String) {
        mutableState.update { it.copy(method = WearLkmMethod.SELECT_FILE, bootUri = uri, bootName = null) }
        viewModelScope.launch { mutableState.update { it.copy(bootName = files.displayName(uri)) } }
    }

    /** Like the phone, only a `.ko` file is accepted as a local LKM. */
    fun setLkm(uri: String) {
        viewModelScope.launch {
            val name = files.displayName(uri)
            mutableState.update {
                if (name?.endsWith(".ko", ignoreCase = true) == true) it.copy(lkmUri = uri, lkmName = name, lkmRejected = false)
                else it.copy(lkmUri = null, lkmName = null, lkmRejected = true)
            }
        }
    }

    fun clearLkm() = mutableState.update { it.copy(lkmUri = null, lkmName = null, lkmRejected = false) }
    fun setKmi(kmi: String) = mutableState.update { it.copy(kmi = kmi) }
    fun setPartition(partition: String) = mutableState.update { it.copy(partition = partition) }
    fun setAllowShell(enabled: Boolean) = mutableState.update { it.copy(allowShell = enabled) }
    fun setEnableAdb(enabled: Boolean) = mutableState.update { it.copy(enableAdb = enabled) }
    fun setForceBackup(enabled: Boolean) = mutableState.update { it.copy(forceBackup = enabled) }

    fun setAk3(uri: String) {
        mutableState.update { it.copy(ak3Uri = uri, ak3Name = null) }
        viewModelScope.launch { mutableState.update { it.copy(ak3Name = files.displayName(uri)) } }
    }

    fun setSlot(slot: String) = mutableState.update { it.copy(slot = slot) }
    fun setSkipKsud(enabled: Boolean) = mutableState.update { it.copy(skipKsud = enabled) }

    /** The boot patch operation, built as the phone Flash screen builds it from its route. */
    fun bootOperation(defaultPartition: String?): FlashOperation.Boot = state.value.let { s ->
        FlashOperation.Boot(
            bootUri = if (s.method == WearLkmMethod.SELECT_FILE) s.bootUri else null,
            lkm = when {
                s.lkmUri != null -> LkmSelection.LkmUri(s.lkmUri)
                s.kmi != null -> LkmSelection.KmiString(s.kmi)
                else -> LkmSelection.KmiNone
            },
            ota = s.method == WearLkmMethod.INACTIVE_SLOT,
            partition = s.partition ?: defaultPartition,
            allowShell = s.allowShell,
            enableAdb = s.enableAdb,
            forceBackup = s.method == WearLkmMethod.SELECT_FILE && s.forceBackup,
        )
    }
}
