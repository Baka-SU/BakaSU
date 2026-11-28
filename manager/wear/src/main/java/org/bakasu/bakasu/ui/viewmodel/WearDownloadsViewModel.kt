package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.bakasu.bakasu.domain.model.DownloadState
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.usecase.EnqueueDownloadUseCase
import org.bakasu.bakasu.domain.usecase.ObserveDownloadsUseCase
import org.bakasu.bakasu.domain.usecase.RemoveDownloadUseCase

class WearDownloadsViewModel(
    observe: ObserveDownloadsUseCase,
    private val removeDownload: RemoveDownloadUseCase,
    private val enqueueDownload: EnqueueDownloadUseCase,
) : ViewModel() {
    val downloads = observe().map { tasks -> tasks.filter { it.fileName.endsWith(".zip", ignoreCase = true) }.sortedByDescending { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(download: DownloadState): Int? = if (download.status == DownloadStatus.FAILED) enqueueDownload(download.url, download.fileName, download.expected, download.module) else null

    fun remove(id: Int) = removeDownload(id)

    fun clear() = downloads.value.forEach { removeDownload(it.id) }
}
