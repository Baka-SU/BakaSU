package org.bakasu.bakasu.data.webui

import java.io.IOException
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
    val events = Channel<ProcessEvent>(64)
    fun offer(event: ProcessEvent) {
        if (events.trySend(event).isFailure) {
            events.close(IOException("Remote WebUI output queue is full or closed"))
        }
    }
    try {
        process.start(
            onStdout = { offer(ProcessEvent.Output(WearWebUiProtocol.FRAME_STDOUT, it)) },
            onStderr = { offer(ProcessEvent.Output(WearWebUiProtocol.FRAME_STDERR, it)) },
            onComplete = { offer(ProcessEvent.Exit(it)) },
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
