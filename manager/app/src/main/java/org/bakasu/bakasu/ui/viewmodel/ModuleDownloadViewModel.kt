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
import org.bakasu.bakasu.domain.usecase.CancelDownloadUseCase
import org.bakasu.bakasu.domain.usecase.EnqueueDownloadUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadUseCase
import org.bakasu.bakasu.domain.usecase.VerifyModuleDownloadUseCase

enum class ModuleDownloadPhase { DOWNLOADING, FAILED, VERIFY_FAILED, COMPLETED }

/** A null [phase] means no download; [resultUri] is set once a verified file is ready to install. */
data class ModuleDownloadUiState(
    val asset: ModuleReleaseAsset? = null,
    val phase: ModuleDownloadPhase? = null,
    val progress: Int = 0,
    val resultUri: String? = null,
)

/** One repository module download at a time, kept across recompositions of its page. */
class ModuleDownloadViewModel(
    private val enqueue: EnqueueDownloadUseCase,
    private val observe: ObserveDownloadUseCase,
    private val verify: VerifyModuleDownloadUseCase,
    private val cancelDownload: CancelDownloadUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ModuleDownloadUiState())
    val state: StateFlow<ModuleDownloadUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var downloadId: Int? = null

    /** A repository ZIP is offered for installing only when it matches the published size and SHA-256. */
    fun start(asset: ModuleReleaseAsset) {
        if (job?.isActive == true) return
        mutableState.value = ModuleDownloadUiState(asset, ModuleDownloadPhase.DOWNLOADING)
        job = viewModelScope.launch {
            val phase = try {
                val id = enqueue(asset.downloadUrl, asset.name).also { downloadId = it }
                val result = observe(id).filterNotNull()
                    .onEach { download -> mutableState.update { it.copy(progress = download.progress) } }
                    .first { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
                val uri = result.resultUri
                when {
                    result.status == DownloadStatus.FAILED || uri == null -> ModuleDownloadPhase.FAILED

                    verify(uri, asset.size, asset.sha256) -> {
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
            downloadId = null
            mutableState.update { it.copy(phase = phase) }
        }
    }

    /** Stops a running download and returns to no download, as after a failure or a handled result. */
    fun reset() {
        job?.cancel()
        job = null
        downloadId?.let(cancelDownload::invoke)
        downloadId = null
        mutableState.value = ModuleDownloadUiState()
    }

    override fun onCleared() {
        downloadId?.let(cancelDownload::invoke)
    }
}
