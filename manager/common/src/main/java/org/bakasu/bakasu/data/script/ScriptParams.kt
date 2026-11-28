package org.bakasu.bakasu.data.script

import androidx.annotation.StringRes
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import org.bakasu.bakasu.R

/*
 * params.json (schemaVersion 1) of a one-shot script: the parameters the manager asks for
 * before running action.sh. Each value reaches the script as `KSU_PARAM_<KEY>`.
 */

class ScriptParamsException(message: String) : Exception(message)

/** Why a parameter value is rejected. */
enum class ParamError(@param:StringRes val resId: Int) {
    REQUIRED(R.string.script_param_required),
    TOO_LONG(R.string.script_param_too_long),
    INVALID_CHARACTERS(R.string.script_param_invalid_characters),
    NOT_AN_INTEGER(R.string.script_param_not_integer),
    BELOW_MIN(R.string.script_param_below_min),
    ABOVE_MAX(R.string.script_param_above_max),
    INVALID_OPTION(R.string.script_param_invalid),
    INVALID_BOOLEAN(R.string.script_param_invalid),
}

/**
 * One declared parameter. Values are handled in their canonical string form, which is also
 * what the script receives: `true`/`false`, a decimal integer, an enum value or the raw string.
 */
sealed class ScriptParam {
    abstract val key: String
    abstract val label: String
    abstract val description: String?

    /** Canonical form of the default value. */
    abstract val defaultValue: String

    /** Returns null when [value] is acceptable. */
    abstract fun validate(value: String): ParamError?

    /** Canonical value passed to the script; only call with a value [validate] accepted. */
    open fun canonical(value: String): String = value

    val envName: String get() = ENV_PREFIX + key

    data class Bool(
        override val key: String,
        override val label: String,
        override val description: String?,
        val default: Boolean,
    ) : ScriptParam() {
        override val defaultValue: String get() = default.toString()
        override fun validate(value: String): ParamError? = if (value == "true" || value == "false") null else ParamError.INVALID_BOOLEAN
    }

    data class EnumOption(val value: String, val label: String?)

    data class Choice(
        override val key: String,
        override val label: String,
        override val description: String?,
        val options: List<EnumOption>,
        val default: String,
    ) : ScriptParam() {
        override val defaultValue: String get() = default
        override fun validate(value: String): ParamError? = if (options.any { it.value == value }) null else ParamError.INVALID_OPTION
    }

    data class Text(
        override val key: String,
        override val label: String,
        override val description: String?,
        val default: String,
        val maxLength: Int,
        val required: Boolean,
    ) : ScriptParam() {
        override val defaultValue: String get() = default

        /** Empty is valid unless [required]. */
        override fun validate(value: String): ParamError? = when {
            value.isEmpty() -> if (required) ParamError.REQUIRED else null
            value.any { it == '\u0000' || it == '\n' || it == '\r' } -> ParamError.INVALID_CHARACTERS
            value.length > maxLength -> ParamError.TOO_LONG
            else -> null
        }
    }

    data class Integer(
        override val key: String,
        override val label: String,
        override val description: String?,
        val default: Int,
        val min: Int?,
        val max: Int?,
    ) : ScriptParam() {
        override val defaultValue: String get() = default.toString()

        override fun validate(value: String): ParamError? {
            val number = parseInteger(value) ?: return ParamError.NOT_AN_INTEGER
            return when {
                min != null && number < min -> ParamError.BELOW_MIN
                max != null && number > max -> ParamError.ABOVE_MAX
                else -> null
            }
        }

        override fun canonical(value: String): String = parseInteger(value)!!.toString()

        companion object {
            /** Values are int32 like min / max / default; anything outside is not an integer. */
            fun parseInteger(value: String): Int? {
                val text = value.trim()
                val digits = text.removePrefix("+").removePrefix("-")
                if (digits.length !in 1..10 || digits.any { it !in '0'..'9' }) return null
                return text.toIntOrNull()
            }
        }
    }

    companion object {
        const val ENV_PREFIX = "KSU_PARAM_"
    }
}

data class ScriptParams(val params: List<ScriptParam>) {
    val defaults: Map<String, String> get() = params.associate { it.key to it.defaultValue }

    /** Error per key for [values] (missing values fall back to the default). */
    fun validate(values: Map<String, String>): Map<String, ParamError> = params.mapNotNull { p -> p.validate(values[p.key] ?: p.defaultValue)?.let { p.key to it } }.toMap()

    companion object {
        val EMPTY = ScriptParams(emptyList())
    }
}

/** Parses and validates params.json; any violation of schemaVersion 1 throws [ScriptParamsException]. */
object ScriptParamsParser {
    const val SCHEMA_VERSION = 1
    const val MAX_PARAMS = 16
    const val MAX_OPTIONS = 32
    const val MAX_TEXT_LENGTH = 200
    const val DEFAULT_MAX_LENGTH = 256
    const val MAX_MAX_LENGTH = 1024

    private fun fail(message: String): Nothing = throw ScriptParamsException(message)

    fun parse(source: String): ScriptParams {
        // The registry validator decodes params.json with a BOM-stripping TextDecoder.
        val text = source.removePrefix("\uFEFF")
        val root = try {
            parseScriptJson(text)
        } catch (e: JsonParseException) {
            fail("Malformed JSON: ${e.message}")
        } catch (e: IllegalArgumentException) {
            fail("Malformed JSON: ${e.message}")
        } as? JsonObject ?: fail("params.json must be an object")

        val version = root["schemaVersion"]?.let(::integerOf)
        if (version != SCHEMA_VERSION) fail("Unsupported schemaVersion: ${root["schemaVersion"]}")
        val array = root["params"] as? JsonArray ?: fail("params must be an array")
        if (array.size() > MAX_PARAMS) fail("At most $MAX_PARAMS params are allowed")

        val seen = HashSet<String>()
        val params = array.mapIndexed { index, element ->
            val obj = element as? JsonObject ?: fail("params[$index] must be an object")
            parseParam(obj, index).also { if (!seen.add(it.key)) fail("Duplicate key: ${it.key}") }
        }
        return ScriptParams(params)
    }

    private fun parseParam(obj: JsonObject, index: Int): ScriptParam {
        val key = obj.string("key") ?: fail("params[$index].key is required")
        if (key.length !in 1..32 || key[0] !in 'A'..'Z' || key.any { it !in 'A'..'Z' && it !in '0'..'9' && it != '_' }) fail("Invalid key: $key")
        val label = text(obj["label"], "$key.label") ?: fail("$key.label is required")
        val description = text(obj["description"], "$key.description")
        val default = obj["default"]?.takeIf { it !is JsonNull }

        val param = when (obj.string("type")) {
            "bool" -> ScriptParam.Bool(
                key,
                label,
                description,
                default = default?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isBoolean }?.asBoolean ?: fail("$key.default must be a boolean") }
                    ?: false,
            )

            "enum" -> {
                val array = obj["options"] as? JsonArray ?: fail("$key.options must be an array")
                if (array.size() !in 1..MAX_OPTIONS) fail("$key.options must have 1..$MAX_OPTIONS entries")
                val values = HashSet<String>()
                val options = array.map { e ->
                    val o = e as? JsonObject ?: fail("$key.options entries must be objects")
                    val value = o.string("value")?.takeIf { v ->
                        v.length in 1..64 && v.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "._-" }
                    } ?: fail("$key: invalid option value")
                    if (!values.add(value)) fail("$key: duplicate option $value")
                    ScriptParam.EnumOption(value, text(o["label"], "$key.$value.label"))
                }
                val defaultValue = default?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.asString ?: fail("$key.default must be a string") }
                    ?: options.first().value
                ScriptParam.Choice(key, label, description, options, defaultValue)
            }

            "string" -> {
                val maxLength = obj.integer("maxLength", key)?.also {
                    if (it !in 1..MAX_MAX_LENGTH) fail("$key.maxLength must be 1..$MAX_MAX_LENGTH")
                } ?: DEFAULT_MAX_LENGTH
                val required = obj["required"]?.takeIf { it !is JsonNull }?.let {
                    (it as? JsonPrimitive)?.takeIf { p -> p.isBoolean }?.asBoolean ?: fail("$key.required must be a boolean")
                } ?: false
                val defaultValue = default?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.asString ?: fail("$key.default must be a string") }
                    ?: ""
                // An empty default is always fine, even when required: the user has to fill it in.
                ScriptParam.Text(key, label, description, defaultValue, maxLength, required)
            }

            "int" -> {
                val min = obj.integer("min", key)
                val max = obj.integer("max", key)
                if (min != null && max != null && min > max) fail("$key: min > max")
                val defaultValue = default?.let { integerOf(it) ?: fail("$key.default must be an integer") } ?: min ?: 0
                ScriptParam.Integer(key, label, description, defaultValue, min, max)
            }

            else -> fail("$key: unsupported type ${obj["type"]}")
        }
        param.validate(param.defaultValue)?.let { if (it != ParamError.REQUIRED) fail("$key.default is invalid: $it") }
        return param
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.asString

    /**
     * A JSON number that is a 32-bit integer *as a double*, like JSON.parse + zod `.int()` in
     * the registry validator: `5`, `5.0` and `5e0` are all 5.
     */
    private fun integerOf(element: JsonElement): Int? {
        val p = element as? JsonPrimitive ?: return null
        if (!p.isNumber) return null
        val d = p.asDouble
        if (d != Math.floor(d) || d < Int.MIN_VALUE || d > Int.MAX_VALUE) return null
        return d.toInt()
    }

    private fun JsonObject.integer(name: String, key: String): Int? {
        val e = this[name]?.takeIf { it !is JsonNull } ?: return null
        return integerOf(e) ?: fail("$key.$name must be a 32-bit integer")
    }

    /** Trimmed first, like the validator's zod `.trim().min(1).max(200)`. */
    private fun checkText(text: String, what: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_TEXT_LENGTH) fail("$what must be 1..$MAX_TEXT_LENGTH characters")
        return trimmed
    }

    private fun text(element: JsonElement?, what: String): String? = when (element) {
        null, is JsonNull -> null
        is JsonPrimitive -> if (element.isString) checkText(element.asString, what) else fail("$what must be a string")
        else -> fail("$what must be a string")
    }
}

internal val scriptJson = GsonBuilder().setStrictness(Strictness.STRICT).create()

internal fun parseScriptJson(text: String): JsonElement = scriptJson.fromJson(text, JsonElement::class.java) ?: JsonNull.INSTANCE
