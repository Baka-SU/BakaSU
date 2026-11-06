package org.bakasu.bakasu.data.logging

import android.os.SystemClock
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.InputStreamReader
import java.util.ArrayDeque
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
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SulogState())
    val state: StateFlow<SulogState> = mutableState.asStateFlow()

    suspend fun refresh(
        preferredFilePath: String? = state.value.selectedFilePath,
    ): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update {
                it.copy(isRefreshing = !it.isLoading, errorMessage = null)
            }
            runCatching {
                val files = listSulogFiles(SuFile(SULOG_DIR), includeEmpty = false)
                currentCoroutineContext().ensureActive()
                val selectedFile = files.selectedSulogFile(preferredFilePath)
                val lines = selectedFile?.let { readFile(it.path) }.orEmpty()
                currentCoroutineContext().ensureActive()
                val status = ksuCliRepository.getFeatureStatus("sulog")
                val enabled = if (status == "forced") {
                    ksuCliRepository.getFeatureValue("sulog") == 1L
                } else {
                    ksuCliRepository.getFeaturePersistValue("sulog") == 1L
                }
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
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(ksuCliRepository.execKsud("feature set sulog ${if (enabled) 1 else 0}", true))
            check(ksuCliRepository.execKsud("feature save", true))
        }
    }

    suspend fun clean(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = SuFile(path)
            // The writer may still hold the newest file open, even when it is empty and hidden.
            val success = if (listSulogFiles(SuFile(SULOG_DIR), includeEmpty = true).firstOrNull()?.path == path) {
                file.clear()
            } else {
                file.delete()
            }
            check(success)
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
