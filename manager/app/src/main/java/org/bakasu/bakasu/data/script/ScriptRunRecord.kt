package org.bakasu.bakasu.data.script

import androidx.annotation.Keep
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/** The last run of a one-shot script; kept locally, scripts have no installed state. */
@Keep
data class ScriptRunRecord(
    val id: String,
    val versionCode: Int? = null,
    /** Epoch millis. */
    val time: Long,
    val exitCode: Int,
) {
    val outcome: ScriptOutcome get() = ScriptOutcome.of(exitCode)

    fun encode(): String = scriptJson.toJson(this)

    companion object {
        /** Null for missing or unreadable data, or a record of another id. */
        fun decode(text: String?, id: String): ScriptRunRecord? {
            if (text == null) return null
            return runCatching {
                val obj = parseScriptJson(text) as? JsonObject ?: return null
                val storedId = (obj["id"] as? JsonPrimitive)?.takeIf { p -> p.isString }?.asString ?: return null
                if (storedId != id) return null
                val versionCode = obj["versionCode"]?.takeIf { value -> value !is JsonNull }?.let { value ->
                    (value as? JsonPrimitive)?.takeIf { p -> p.isNumber }?.asString?.toIntOrNull() ?: return null
                }
                val time = (obj["time"] as? JsonPrimitive)?.takeIf { p -> p.isNumber }?.asString?.toLongOrNull() ?: return null
                val exitCode = (obj["exitCode"] as? JsonPrimitive)?.takeIf { p -> p.isNumber }?.asString?.toIntOrNull() ?: return null
                ScriptRunRecord(storedId, versionCode, time, exitCode)
            }.getOrNull()
        }
    }
}

/** Key-value storage the run history is kept in (SharedPreferences on the device). */
interface ScriptRunStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

class ScriptRunHistory(private val storage: ScriptRunStorage) {
    fun last(id: String): ScriptRunRecord? = ScriptRunRecord.decode(storage.read(KEY_PREFIX + id), id)

    fun record(record: ScriptRunRecord) = storage.write(KEY_PREFIX + record.id, record.encode())

    private companion object {
        const val KEY_PREFIX = "last_run:"
    }
}
