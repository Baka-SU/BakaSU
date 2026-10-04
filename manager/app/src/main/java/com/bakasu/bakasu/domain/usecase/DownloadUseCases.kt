package com.bakasu.bakasu.domain.usecase

import com.bakasu.bakasu.data.download.DownloadRepository
import com.bakasu.bakasu.domain.model.DownloadState
import com.bakasu.bakasu.domain.model.ManagerUpdateInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EnqueueDownloadUseCase(private val repository: DownloadRepository) {
    operator fun invoke(url: String, fileName: String): Int = repository.enqueue(url, fileName)
}

class EnqueueManagerUpdateUseCase(private val repository: DownloadRepository) {
    operator fun invoke(update: ManagerUpdateInfo): Int = repository.enqueueManagerUpdate(update)
}

class ObserveDownloadUseCase(private val repository: DownloadRepository) {
    operator fun invoke(id: Int): Flow<DownloadState?> =
        repository.downloads.map { it[id] }
}

