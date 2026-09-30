package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.DownloadState
import com.resukisu.resukisu.domain.model.DownloadStatus
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.domain.usecase.EnqueueDownloadUseCase
import com.resukisu.resukisu.domain.usecase.FetchRemoteTextUseCase
import com.resukisu.resukisu.domain.usecase.ObserveDownloadUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [changelogError] carries the raw failure message, formatted by `module_changelog_failed` like the phone. */
data class WearModuleUpdateUiState(
    val changelog: String? = null,
    val changelogError: String? = null,
    val download: DownloadState? = null,
)

class WearModuleUpdateViewModel(
    private val enqueue: EnqueueDownloadUseCase,
    private val observeDownload: ObserveDownloadUseCase,
    private val fetchRemoteText: FetchRemoteTextUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WearModuleUpdateUiState())
    val state = mutableState.asStateFlow()
    private var task: Job? = null
    private var changelogTask: Job? = null

    /** The update metadata only holds the changelog URL; the phone fetches it before offering the update. */
    fun loadChangelog(module: InstalledModule) {
        if (changelogTask != null) return
        val url = module.moduleUpdate?.changelog ?: return
        changelogTask = viewModelScope.launch {
            fetchRemoteText(url).fold(
                onSuccess = { text -> mutableState.update { it.copy(changelog = text, changelogError = null) } },
                onFailure = { error -> mutableState.update { it.copy(changelogError = error.message.orEmpty()) } },
            )
        }
    }

    fun start(module: InstalledModule) {
        if (task?.isActive == true) return
        val metadata = module.moduleUpdate ?: return
        task = viewModelScope.launch {
            try {
                val id = enqueue(metadata.zipUrl, "${module.name}-${metadata.version}.zip")
                observeDownload(id).filterNotNull().onEach { download -> mutableState.update { it.copy(download = download) } }
                    .first { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutableState.update {
                    it.copy(download = DownloadState(-1, "", metadata.zipUrl, status = DownloadStatus.FAILED))
                }
            }
        }
    }
    fun consume() { mutableState.update { it.copy(download = null) } }
}
