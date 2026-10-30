package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.bakasu.bakasu.domain.usecase.EnqueueDownloadUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadUseCase
import org.bakasu.bakasu.domain.usecase.VerifyModuleDownloadUseCase

/** [VERIFYING] follows a finished transfer while the file is checked; nothing is downloading then. */
enum class ModuleDownloadPhase { DOWNLOADING, VERIFYING, FAILED, VERIFY_FAILED, COMPLETED }

/** A null [phase] means no download; [resultUri] is set once a verified file is ready to install. */
data class ModuleDownloadUiState(
    val asset: ModuleReleaseAsset? = null,
    val phase: ModuleDownloadPhase? = null,
    val downloadedBytes: Long = 0L,
    /** The size the server announced, or -1 when it is unknown. */
    val totalBytes: Long = -1L,
    val resultUri: String? = null,
) {
    /** The downloaded share from 0 to 1, or null when the size is unknown and no share can be given. */
    val fraction: Float?
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

/** Observes a service-owned download; leaving its route does not cancel the transfer. */
class ModuleDownloadViewModel(
    private val enqueue: EnqueueDownloadUseCase,
    private val observe: ObserveDownloadUseCase,
    private val verify: VerifyModuleDownloadUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ModuleDownloadUiState())
    val state: StateFlow<ModuleDownloadUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    /** A repository ZIP is offered for installing only when it matches the published size and SHA-256. */
    fun start(asset: ModuleReleaseAsset) {
        if (job?.isActive == true) return
        if (asset.verificationRequired && asset.expected == null) {
            mutableState.value = ModuleDownloadUiState(asset, ModuleDownloadPhase.VERIFY_FAILED)
            return
        }
        mutableState.value = ModuleDownloadUiState(asset, ModuleDownloadPhase.DOWNLOADING)
        job = viewModelScope.launch {
            val phase = try {
                // The service keeps enforcing published integrity even after this page closes.
                val id = enqueue(asset.downloadUrl, asset.name, asset.expected)
                val result = observe(id).filterNotNull()
                    .onEach { download ->
                        mutableState.update { it.copy(downloadedBytes = download.downloadedBytes, totalBytes = download.totalBytes) }
                    }
                    .first { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
                val uri = result.resultUri
                if (result.status == DownloadStatus.COMPLETED && uri != null) {
                    mutableState.update { it.copy(phase = ModuleDownloadPhase.VERIFYING) }
                }
                when {
                    result.verificationFailure != null -> ModuleDownloadPhase.VERIFY_FAILED

                    result.status == DownloadStatus.FAILED || uri == null -> ModuleDownloadPhase.FAILED

                    verify(uri, asset.expected?.size ?: 0L, asset.expected?.sha256.orEmpty()) -> {
                        mutableState.update { it.copy(resultUri = uri) }
                        ModuleDownloadPhase.COMPLETED
                    }

                    else -> ModuleDownloadPhase.VERIFY_FAILED
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ModuleDownloadPhase.FAILED
            }
            mutableState.update { it.copy(phase = phase) }
        }
    }
}
