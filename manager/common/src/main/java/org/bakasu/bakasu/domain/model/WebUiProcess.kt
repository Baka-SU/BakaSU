package org.bakasu.bakasu.domain.model

/** A streaming command handle whose platform implementation stays in the data layer. */
interface WebUiProcess {
    fun start(
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        onComplete: (WebUiCommandResult) -> Unit,
    )

    fun close()
}
