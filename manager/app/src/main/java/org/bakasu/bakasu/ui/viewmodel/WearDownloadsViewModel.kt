package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.DownloadState
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.usecase.CancelDownloadUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadsUseCase
import org.bakasu.bakasu.domain.usecase.VerifyModuleDownloadUseCase

sealed interface WearDownloadsEvent {
    data class Install(val uri: String) : WearDownloadsEvent
    data object VerificationFailed : WearDownloadsEvent
}

class WearDownloadsViewModel(
    observe: ObserveDownloadsUseCase,
    private val cancelDownload: CancelDownloadUseCase,
    private val verify: VerifyModuleDownloadUseCase,
) : ViewModel() {
    val downloads = observe().map { tasks -> tasks.filter { it.fileName.endsWith(".zip", ignoreCase = true) }.sortedByDescending { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableVerifying = MutableStateFlow(false)
    val verifying = mutableVerifying.asStateFlow()
    private val mutableEvents = MutableSharedFlow<WearDownloadsEvent>(extraBufferCapacity = 1)
    val events = mutableEvents.asSharedFlow()

    fun cancel(id: Int) = cancelDownload(id)

    fun install(download: DownloadState) {
        val uri = download.resultUri ?: return
        if (download.status != DownloadStatus.COMPLETED || mutableVerifying.value) return
        mutableVerifying.value = true
        viewModelScope.launch {
            try {
                if (verify(uri, download.expected?.size ?: 0, download.expected?.sha256.orEmpty())) {
                    mutableEvents.emit(WearDownloadsEvent.Install(uri))
                } else {
                    mutableEvents.emit(WearDownloadsEvent.VerificationFailed)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableEvents.emit(WearDownloadsEvent.VerificationFailed)
            } finally {
                mutableVerifying.value = false
            }
        }
    }
}
