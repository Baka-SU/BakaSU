package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.ModuleActionUpdate
import com.resukisu.resukisu.domain.usecase.ExecuteModuleActionUseCase
import com.resukisu.resukisu.domain.usecase.SaveModuleActionLogUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ExecuteModuleActionUiState(
    val output: String = "",
    val started: Boolean = false,
    val running: Boolean = false,
    val successful: Boolean? = null,
    val outputTruncated: Boolean = false,
)

sealed interface ExecuteModuleActionUiAction {
    data object Start : ExecuteModuleActionUiAction
    data object SaveLog : ExecuteModuleActionUiAction
}

sealed interface ExecuteModuleActionUiEvent {
    data class Completed(val successful: Boolean) : ExecuteModuleActionUiEvent
    data class LogSaved(val path: String) : ExecuteModuleActionUiEvent
    data class Error(val message: String) : ExecuteModuleActionUiEvent
}

class ExecuteModuleActionViewModel(
    private val moduleId: String,
    private val executeModuleAction: ExecuteModuleActionUseCase,
    private val saveModuleActionLog: SaveModuleActionLogUseCase,
    autoStart: Boolean = true,
    displayLogLimit: Int = Int.MAX_VALUE,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExecuteModuleActionUiState())
    private val mutableEvents =
        MutableSharedFlow<ExecuteModuleActionUiEvent>(extraBufferCapacity = 1)
    private val log = StringBuilder()
    private val displayLog = DisplayLogBuffer(displayLogLimit)
    private var actionJob: Job? = null
    private var outputJob: Job? = null

    val state: StateFlow<ExecuteModuleActionUiState> = mutableState.asStateFlow()
    val events: SharedFlow<ExecuteModuleActionUiEvent> = mutableEvents.asSharedFlow()

    init {
        if (autoStart) execute()
    }

    fun dispatch(action: ExecuteModuleActionUiAction) {
        when (action) {
            ExecuteModuleActionUiAction.Start -> execute()
            ExecuteModuleActionUiAction.SaveLog -> saveLog()
        }
    }

    private fun execute() {
        if (mutableState.value.started) return
        mutableState.update { it.copy(started = true, running = true) }
        actionJob = viewModelScope.launch {
            executeModuleAction(moduleId).catch { error ->
                flushOutput()
                mutableState.update { it.copy(running = false, successful = false) }
                mutableEvents.emit(ExecuteModuleActionUiEvent.Error(error.message.orEmpty()))
            }.collect { update ->
                when (update) {
                    is ModuleActionUpdate.Output -> appendOutput(update.text, update.isError)
                    is ModuleActionUpdate.Completed -> {
                        flushOutput()
                        mutableState.update { it.copy(running = false, successful = update.successful) }
                        mutableEvents.emit(ExecuteModuleActionUiEvent.Completed(update.successful))
                    }
                }
            }
        }
    }

    private fun appendOutput(text: String, isError: Boolean) {
        val line = "$text\n"
        log.append(line)
        if (isError) return
        if (line.startsWith(CLEAR_SCREEN)) displayLog.clear()
        displayLog.append(line.removePrefix(CLEAR_SCREEN))
        if (outputJob?.isActive != true) outputJob = viewModelScope.launch {
            delay(100)
            publishOutput()
        }
    }

    private fun publishOutput() {
        mutableState.update { it.copy(output = displayLog.snapshot(), outputTruncated = displayLog.truncated) }
    }

    private fun flushOutput() {
        outputJob?.cancel()
        publishOutput()
    }

    private fun saveLog() {
        if (mutableState.value.running) return
        viewModelScope.launch {
            saveModuleActionLog(log.toString())
                .onSuccess { mutableEvents.tryEmit(ExecuteModuleActionUiEvent.LogSaved(it)) }
                .onFailure { mutableEvents.tryEmit(ExecuteModuleActionUiEvent.Error(it.message.orEmpty())) }
        }
    }

    private companion object {
        const val CLEAR_SCREEN = "\u001B[H\u001B[J"
    }
}
