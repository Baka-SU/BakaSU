package org.bakasu.bakasu.data.module.registry

import kotlinx.serialization.json.Json

open class RegistryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The document is not valid JSON or violates the protocol. */
class RegistryFormatException(message: String, cause: Throwable? = null) : RegistryException(message, cause)

/** The document declares a schemaVersion this client does not understand. */
class UnsupportedSchemaException(val schemaVersion: Int?) : RegistryException("Unsupported registry schemaVersion: ${schemaVersion ?: "missing"}")

/** Documents of two different builds were observed even after re-reading the index. */
class BuildMismatchException(message: String) : RegistryException(message)

/** The module is not part of the current index (e.g. suspended or removed). */
class ModuleNotInIndexException(val moduleId: String) : RegistryException("Module $moduleId is not in the registry index")

/** Decodes the registry protocol; resource and download policies are enforced when used. */
class RegistryParser(private val json: Json = DEFAULT_JSON) {
    companion object {
        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            isLenient = false
            explicitNulls = false
        }

        // IDs also form cache keys and script identities.
        private val MODULE_ID = Regex("^[a-zA-Z][a-zA-Z0-9._-]+$")

        fun isValidModuleId(id: String): Boolean = id.length in 2..64 && MODULE_ID.matches(id)
    }

    private fun checkHeader(schemaVersion: Int, buildId: String) {
        if (schemaVersion != REGISTRY_API_SCHEMA_VERSION) throw UnsupportedSchemaException(schemaVersion)
        // Cached README entries use one line for the build ID.
        if (buildId.isBlank() || '\n' in buildId || '\r' in buildId) throw RegistryFormatException("Invalid buildId")
    }

    fun parseIndex(text: String): ApiIndex = json.decodeFromString(ApiIndex.serializer(), text).also {
        checkHeader(it.schemaVersion, it.buildId)
    }

    fun parseModules(text: String): ApiModules {
        val document = json.decodeFromString(ApiModules.serializer(), text)
        checkHeader(document.schemaVersion, document.buildId)
        val modules = document.modules.filter { RegistryKind.isSupported(it.kind, it.metamodule) }
        if (modules.any { !isValidModuleId(it.id) } || modules.map { it.id }.toSet().size != modules.size) {
            throw RegistryFormatException("Invalid or duplicate module id")
        }
        return document.copy(modules = modules)
    }

    fun parseDetail(text: String): ApiModuleDetail {
        val detail = json.decodeFromString(ApiModuleDetail.serializer(), text)
        checkHeader(detail.schemaVersion, detail.buildId)
        if (!RegistryKind.isSupported(detail.kind, detail.metamodule)) throw RegistryFormatException("Unsupported kind: ${detail.kind}")
        val releases = detail.releases.filter { it.immutable }
        if (releases.any { it.tag.isBlank() } || releases.map { it.tag }.toSet().size != releases.size) {
            throw RegistryFormatException("Invalid or duplicate release tag")
        }
        return detail.copy(releases = releases)
    }
}
