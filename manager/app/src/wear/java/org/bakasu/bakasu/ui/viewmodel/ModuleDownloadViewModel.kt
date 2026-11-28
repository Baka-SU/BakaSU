package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.DownloadModuleInfo
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.model.DownloadVerificationFailure
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.usecase.EnqueueDownloadUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadUseCase

enum class ModuleDownloadPhase { DOWNLOADING, FAILED, VERIFY_FAILED, COMPLETED }

/** A null [phase] means no download; [resultUri] is set once the service finishes the download. */
data class ModuleDownloadUiState(
    val asset: ModuleReleaseAsset? = null,
    val phase: ModuleDownloadPhase? = null,
    val downloadedBytes: Long = 0L,
    /** The size the server announced, or -1 when it is unknown. */
    val totalBytes: Long = -1L,
    val resultUri: String? = null,
    val verificationFailure: DownloadVerificationFailure? = null,
) {
    /** The downloaded share from 0 to 1, or null when the size is unknown and no share can be given. */
    val fraction: Float?
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

/** Observes a service-owned download; leaving its route does not cancel the transfer. */
class ModuleDownloadViewModel(
    private val enqueue: EnqueueDownloadUseCase,
    private val observe: ObserveDownloadUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ModuleDownloadUiState())
    val state: StateFlow<ModuleDownloadUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun start(asset: ModuleReleaseAsset, module: DownloadModuleInfo? = null) {
        if (job?.isActive == true) return
        if (asset.verificationRequired && asset.expected == null) {
            mutableState.value = ModuleDownloadUiState(asset, ModuleDownloadPhase.VERIFY_FAILED)
            return
        }
        val knownSize = asset.expected?.size ?: asset.size?.takeIf { it > 0L }
            ?: mutableState.value.takeIf { it.asset == asset }?.totalBytes ?: -1L
        mutableState.value = ModuleDownloadUiState(asset, ModuleDownloadPhase.DOWNLOADING, totalBytes = knownSize)
        job = viewModelScope.launch {
            try {
                // The service keeps enforcing published integrity even after this page closes.
                val id = enqueue(asset.downloadUrl, asset.name, asset.expected, module)
                val result = observe(id)
                    .onEach { download ->
                        if (download != null) {
                            mutableState.update {
                                it.copy(
                                    downloadedBytes = download.downloadedBytes,
                                    totalBytes = if (download.status == DownloadStatus.PENDING && download.totalBytes <= 0L) it.totalBytes else download.totalBytes,
                                    verificationFailure = download.verificationFailure,
                                )
                            }
                        }
                    }
                    .first { it == null || it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
                if (result?.status != DownloadStatus.COMPLETED || result.resultUri == null) {
                    mutableState.update {
                        it.copy(phase = result?.verificationFailure?.let { ModuleDownloadPhase.VERIFY_FAILED } ?: ModuleDownloadPhase.FAILED)
                    }
                    return@launch
                }
                mutableState.update { it.copy(phase = ModuleDownloadPhase.COMPLETED, resultUri = result.resultUri) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(phase = ModuleDownloadPhase.FAILED) }
            }
        }
    }
}
