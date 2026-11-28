package org.bakasu.bakasu.domain.usecase

import android.net.Uri
import org.bakasu.bakasu.data.script.PreparedScript
import org.bakasu.bakasu.data.script.ScriptRepository
import org.bakasu.bakasu.domain.model.ExpectedFile

class PeekOneshotScriptUseCase(private val repository: ScriptRepository) {
    suspend operator fun invoke(uri: String) = repository.peekOneshot(Uri.parse(uri))
}

class PrepareScriptUseCase(private val repository: ScriptRepository) {
    suspend operator fun invoke(uri: String, registryId: String?, expected: ExpectedFile?, owner: String) = repository.prepare(Uri.parse(uri), registryId, expected, owner)
}

class ReleaseScriptUseCase(private val repository: ScriptRepository) {
    operator fun invoke(owner: String) = repository.release(owner)
}

class RunScriptUseCase(private val repository: ScriptRepository) {
    suspend operator fun invoke(owner: String, prepared: PreparedScript, values: Map<String, String>, output: (String, Boolean) -> Unit) = repository.run(owner, prepared, values, output)
}

class GetLastScriptRunUseCase(private val repository: ScriptRepository) {
    operator fun invoke(id: String) = repository.history.last(id)
    fun observe(id: String) = repository.observeLastRun(id)
}
