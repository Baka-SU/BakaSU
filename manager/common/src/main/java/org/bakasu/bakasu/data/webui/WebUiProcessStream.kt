package org.bakasu.bakasu.data.webui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.domain.model.WebUiCommandResult
import org.bakasu.bakasu.domain.model.WebUiProcess

private sealed interface ProcessEvent {
    data class Output(val type: Byte, val text: String) : ProcessEvent
    data class Exit(val result: WebUiCommandResult) : ProcessEvent
}

/** Shell callbacks may run on the UI thread. Only this IO consumer writes to Bluetooth. */
internal suspend fun streamWebUiProcess(process: WebUiProcess, write: (Byte, ByteArray) -> Unit) = withContext(Dispatchers.IO) {
    val events = Channel<ProcessEvent>(Channel.UNLIMITED)
    try {
        process.start(
            onStdout = { events.trySend(ProcessEvent.Output(WearWebUiProtocol.FRAME_STDOUT, it)) },
            onStderr = { events.trySend(ProcessEvent.Output(WearWebUiProtocol.FRAME_STDERR, it)) },
            onComplete = { events.trySend(ProcessEvent.Exit(it)) },
        )
        for (event in events) {
            when (event) {
                is ProcessEvent.Output -> write(event.type, event.text.toByteArray())

                is ProcessEvent.Exit -> {
                    with(WearWebUiProtocol) { write(FRAME_EXIT, event.result.toJson().toByteArray()) }
                    break
                }
            }
        }
    } finally {
        events.cancel()
        process.close()
    }
}
