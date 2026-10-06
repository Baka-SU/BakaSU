package org.bakasu.bakasu.data.module

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.AppSettingsRepository
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryException
import org.bakasu.bakasu.domain.model.RepositorySource

class RepositorySourceStore(
    private val settings: AppSettingsRepository,
    private val context: Context,
) {
    fun load(): List<RepositorySource> = listOf(
        RepositorySource(
            RepositorySource.KERNEL_SU_URL,
            context.getString(R.string.repo_kernel_su),
            description = context.getString(R.string.repo_kernel_su_description),
        ),
    ) + runCatching {
        JsonParser.parseString(settings.getString(KEY) ?: "[]").asJsonArray.mapNotNull { item ->
            runCatching {
                val fields = item.asJsonObject
                RepositorySource(
                    url = MmrlRepositoryParser.canonicalUrl(fields.text("url")),
                    name = fields.text("name"),
                    id = fields.text("id"),
                    description = fields.text("description"),
                    customName = fields.text("customName").trim().take(100),
                ).takeIf { !it.isBuiltIn && it.name.isNotBlank() }
            }.getOrNull()
        }.distinctBy { it.url }
    }.getOrDefault(emptyList())

    suspend fun save(sources: List<RepositorySource>) {
        try {
            val array = JsonArray()
            sources.filterNot { it.isBuiltIn }.forEach { source ->
                array.add(
                    JsonObject().apply {
                        addProperty("url", source.url)
                        addProperty("name", source.name)
                        addProperty("customName", source.customName)
                        addProperty("id", source.id)
                        addProperty("description", source.description)
                    },
                )
            }
            settings.editBlocking { it[stringPreferencesKey(KEY)] = array.toString() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw RepositoryException(RepositoryError.STORAGE)
        }
    }

    private fun JsonElement?.text(): String = this?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString.orEmpty()
    private fun JsonObject.text(key: String) = get(key).text()

    private companion object {
        const val KEY = "module_repository_sources"
    }
}
