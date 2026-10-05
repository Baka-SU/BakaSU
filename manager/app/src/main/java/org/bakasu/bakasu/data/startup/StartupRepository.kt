package org.bakasu.bakasu.data.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bakasu.bakasu.domain.model.StartupState

class StartupRepository {
    private val mutableState = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = mutableState.asStateFlow()

    fun markReady() {
        mutableState.value = StartupState.Ready
    }

    fun markFailed(error: Throwable) {
        mutableState.value = StartupState.Failed(error.message ?: error::class.java.simpleName)
    }
}
