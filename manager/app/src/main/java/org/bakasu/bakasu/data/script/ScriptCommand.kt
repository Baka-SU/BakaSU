package org.bakasu.bakasu.data.script

import androidx.annotation.StringRes
import org.bakasu.bakasu.R

/** What the manager knows about the running KernelSU, exported to the script like ksud does. */
data class ScriptHostInfo(
    val ksuVersion: String,
    val ksuVersionCode: Int,
    val kernelVersionCode: Int,
    val lateLoad: Boolean,
    val uapiVersion: Int,
    val lkmMode: Boolean,
)

/**
 * Builds the shell command that runs a one-shot script's action.sh. Every variable value is
 * single-quoted; only `$PATH` itself is left to the shell to expand.
 */
object ScriptCommand {
    const val BIN_DIR = "/data/adb/ksu/bin"
    const val BUSYBOX = "$BIN_DIR/busybox"
    const val ENTRY = "action.sh"

    /** Quotes [value] for a POSIX shell: `it's` -> `'it'\''s'`. */
    fun quote(value: String): String {
        require('\u0000' !in value) { "NUL cannot be passed through a shell" }
        return "'" + value.replace("'", "'\\''") + "'"
    }

    /**
     * Environment of a run, in order. [values] are the user's inputs per param key (missing
     * keys use the default); throws [IllegalArgumentException] if any of them is invalid.
     */
    private fun environment(
        host: ScriptHostInfo,
        moduleId: String,
        dir: String,
        params: ScriptParams,
        values: Map<String, String>,
    ): List<Pair<String, String>> {
        val env = mutableListOf(
            "ASH_STANDALONE" to "1",
            "KSU" to "true",
            "BAKASU" to "true",
            "KSU_KERNEL_VER_CODE" to host.kernelVersionCode.toString(),
            "KSU_VER_CODE" to host.ksuVersionCode.toString(),
            "KSU_VER" to host.ksuVersion,
            "KSU_UAPI_VER" to host.uapiVersion.toString(),
            "KSU_RUNTIME_MODE" to when {
                host.lateLoad -> "late-load"
                host.lkmMode -> "lkm"
                else -> "built-in"
            },
        )
        if (host.lateLoad) env += "KSU_LATE_LOAD" to "1"
        env += "KSU_MODULE" to moduleId
        env += "KSU_ONESHOT" to "1"
        env += "MODPATH" to dir
        for (param in params.params) {
            val value = values[param.key] ?: param.defaultValue
            param.validate(value)?.let { throw IllegalArgumentException("${param.key}: $it") }
            env += param.envName to param.canonical(value)
        }
        return env
    }

    /** `cd <dir> && <env...> PATH=... busybox sh ./action.sh </dev/null` */
    fun build(host: ScriptHostInfo, moduleId: String, dir: String, params: ScriptParams, values: Map<String, String>): String = buildString {
        append("cd ").append(quote(dir)).append(" && ")
        for ((name, value) in environment(host, moduleId, dir, params, values)) {
            append(name).append('=').append(quote(value)).append(' ')
        }
        append("PATH=\"\$PATH\":").append(quote(BIN_DIR)).append(' ')
        append(quote(BUSYBOX)).append(" sh ./").append(ENTRY)
        // libsu feeds commands through the shell's stdin: the script must never read from it.
        append(" </dev/null")
    }
}

/** Result of a run, by exit code. */
enum class ScriptOutcome(@param:StringRes val resId: Int) {
    SUCCESS(R.string.script_result_success),

    /** Exit code 2: the script decided it does not apply to this device. */
    NOT_APPLICABLE(R.string.script_result_not_applicable),
    FAILURE(R.string.script_result_failed),
    ;

    companion object {
        const val EXIT_NOT_APPLICABLE = 2

        fun of(exitCode: Int): ScriptOutcome = when (exitCode) {
            0 -> SUCCESS
            EXIT_NOT_APPLICABLE -> NOT_APPLICABLE
            else -> FAILURE
        }
    }
}
