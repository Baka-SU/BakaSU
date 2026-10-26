package org.bakasu.bakasu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.script.ParamError
import org.bakasu.bakasu.data.script.PreparedScript
import org.bakasu.bakasu.data.script.ReleasedException
import org.bakasu.bakasu.data.script.ScriptRunRecord
import org.bakasu.bakasu.domain.model.ExpectedFile
import org.bakasu.bakasu.domain.usecase.GetLastScriptRunUseCase
import org.bakasu.bakasu.domain.usecase.PrepareScriptUseCase
import org.bakasu.bakasu.domain.usecase.ReleaseScriptUseCase
import org.bakasu.bakasu.domain.usecase.RunScriptUseCase
import org.bakasu.bakasu.domain.usecase.SaveModuleActionLogUseCase
import org.bakasu.bakasu.ui.navigation.Route

enum class ScriptRunPhase { PREPARING, FAILED, READY, RUNNING, FINISHED }

data class ScriptRunUiState(
    val phase: ScriptRunPhase = ScriptRunPhase.PREPARING,
    val prepared: PreparedScript? = null,
    val failure: Throwable? = null,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, ParamError> = emptyMap(),
    val lastRun: ScriptRunRecord? = null,
    val output: String = "",
    val exitCode: Int? = null,
) {
    val canRun: Boolean get() = phase == ScriptRunPhase.READY && errors.isEmpty()
}

class ScriptRunViewModel(
    private val route: Route.ScriptRun,
    private val prepareScript: PrepareScriptUseCase,
    private val releaseScript: ReleaseScriptUseCase,
    private val runScript: RunScriptUseCase,
    private val getLastRun: GetLastScriptRunUseCase,
    private val saveActionLog: SaveModuleActionLogUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ScriptRunUiState())
    val state = mutableState.asStateFlow()
    private var owner: String? = null
    private var prepareJob: Job? = null
    private val log = StringBuilder()

    fun load() {
        if (owner != null || state.value.phase == ScriptRunPhase.FINISHED) return
        val token = UUID.randomUUID().toString()
        owner = token
        mutableState.value = ScriptRunUiState()
        prepareJob = viewModelScope.launch {
            try {
                val expected = route.expectedSize?.let { size -> route.expectedSha256?.let { ExpectedFile(size, it) } }
                val prepared = prepareScript(route.uri, route.registryId, expected, token)
                val lastRun = withContext(Dispatchers.IO) { getLastRun(prepared.manifest.id) }
                if (owner != token) return@launch
                mutableState.value = ScriptRunUiState(
                    phase = ScriptRunPhase.READY,
                    prepared = prepared,
                    values = prepared.params.defaults,
                    errors = prepared.params.validate(prepared.params.defaults),
                    lastRun = lastRun,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (owner == token && e !is ReleasedException) {
                    owner = null
                    mutableState.value = ScriptRunUiState(phase = ScriptRunPhase.FAILED, failure = e)
                }
            }
        }
    }

    fun setValue(key: String, value: String) {
        mutableState.update { current ->
            val params = current.prepared?.params ?: return@update current
            if (current.phase != ScriptRunPhase.READY) return@update current
            val values = current.values + (key to value)
            current.copy(values = values, errors = params.validate(values))
        }
    }

    fun run() {
        val current = state.value
        val prepared = current.prepared ?: return
        val token = owner ?: return
        if (!current.canRun) return
        mutableState.update { it.copy(phase = ScriptRunPhase.RUNNING) }
        viewModelScope.launch {
            try {
                val code = runScript(token, prepared, current.values) { text, _ ->
                    val line = "$text\n"
                    synchronized(log) { log.append(line) }
                    mutableState.update {
                        it.copy(output = if (line.startsWith(CLEAR_SCREEN)) line.removePrefix(CLEAR_SCREEN) else it.output + line)
                    }
                }
                owner = null
                val lastRun = withContext(Dispatchers.IO) { getLastRun(prepared.manifest.id) }
                mutableState.update { it.copy(phase = ScriptRunPhase.FINISHED, exitCode = code, lastRun = lastRun) }
            } catch (e: CancellationException) {
                // The application-owned run still records its result and removes its extraction.
                throw e
            } catch (e: Exception) {
                owner = null
                mutableState.update { it.copy(phase = ScriptRunPhase.FAILED, failure = e) }
            }
        }
    }

    suspend fun saveLog(): Result<String> = saveActionLog(synchronized(log) { log.toString() })

    fun leave() {
        if (state.value.phase == ScriptRunPhase.RUNNING) return
        prepareJob?.cancel()
        owner?.let(releaseScript::invoke)
        owner = null
        mutableState.value = ScriptRunUiState()
    }

    override fun onCleared() = leave()

    private companion object {
        const val CLEAR_SCREEN = "\u001B[H\u001B[J"
    }
}
