package org.bakasu.bakasu.data.logging

import android.os.SystemClock
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.File
import java.io.InputStreamReader
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.shell.KsuCliRepository
import org.bakasu.bakasu.domain.model.SulogFile
import org.bakasu.bakasu.domain.model.SulogState
import org.bakasu.bakasu.domain.model.parseSulogLines

class SulogRepository(
    private val ksuCliRepository: KsuCliRepository,
    private val directory: File = SuFile(SULOG_DIR),
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SulogState())
    val state: StateFlow<SulogState> = mutableState.asStateFlow()

    private var followLatest = true

    suspend fun refresh(
        preferredFilePath: String? = state.value.selectedFilePath,
        silent: Boolean = false,
    ): Result<Unit> = read(preferredFilePath, silent, queryFeature = !silent)

    suspend fun selectFile(path: String?): Result<Unit> = read(path, silent = false, queryFeature = false)

    private suspend fun read(preferredFilePath: String?, silent: Boolean, queryFeature: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!silent) {
                followLatest = preferredFilePath == null || preferredFilePath == state.value.files.firstOrNull()?.path
                mutableState.update { it.copy(isRefreshing = !it.isLoading, errorMessage = null) }
            }
            try {
                val preferred = if (silent) {
                    state.value.selectedFilePath.takeUnless { followLatest }
                } else {
                    preferredFilePath
                }
                readState(preferred, queryFeature)
                Result.success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(isLoading = false, errorMessage = error.message) }
                Result.failure(error)
            } finally {
                if (!silent) mutableState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun readState(preferredFilePath: String?, queryFeature: Boolean) {
        val files = listSulogFiles(directory, includeEmpty = false)
        currentCoroutineContext().ensureActive()
        val selectedFile = files.selectedSulogFile(preferredFilePath)
        val lines = selectedFile?.let { readFile(it.path) }.orEmpty()
        var status = state.value.status
        var enabled = state.value.enabled
        if (queryFeature) {
            status = ksuCliRepository.getFeatureStatus("sulog")
            enabled = if (status == "forced") {
                ksuCliRepository.getFeatureValue("sulog") == 1L
            } else {
                ksuCliRepository.getFeaturePersistValue("sulog") == 1L
            }
        }
        currentCoroutineContext().ensureActive()
        mutableState.value = SulogState(
            status = status,
            enabled = enabled,
            files = files,
            selectedFilePath = selectedFile?.path,
            entries = parseSulogLines(
                lines = lines,
                currentTimeMillis = System.currentTimeMillis(),
                uptimeMillis = SystemClock.uptimeMillis(),
            ),
            isLoading = false,
        )
    }

    suspend fun setEnabled(enabled: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                check(ksuCliRepository.execKsud("feature set sulog ${if (enabled) 1 else 0}", true))
                check(ksuCliRepository.execKsud("feature save", true))
                currentCoroutineContext().ensureActive()
                Result.success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }

    suspend fun clean(path: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val file = SuFile(path)
                // Keep the active writer's handle; empty files disappear until new records arrive.
                val success = if (listSulogFiles(directory, includeEmpty = true).firstOrNull()?.path == path) {
                    file.clear()
                } else {
                    file.delete()
                }
                check(success)
                val selected = state.value.selectedFilePath.takeUnless { it == path }
                if (selected == null) followLatest = true
                readState(selected, queryFeature = false)
                Result.success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }

    private fun readFile(path: String): List<String> {
        val file = SuFile(path)
        if (!file.isFile) return emptyList()
        val lines = ArrayDeque<String>(LINE_LIMIT)
        SuFileInputStream.open(file).use { input ->
            InputStreamReader(input).buffered().useLines { sequence ->
                sequence.forEach { line ->
                    if (lines.size == LINE_LIMIT) lines.removeFirst()
                    lines.addLast(line)
                }
            }
        }
        return lines.toList()
    }

    private companion object {
        const val SULOG_DIR = "/data/adb/ksu/log"
        const val LINE_LIMIT = 1000
    }
}
