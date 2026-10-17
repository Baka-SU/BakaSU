package org.bakasu.bakasu.data.module

import com.topjohnwu.superuser.io.SuFile
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.domain.model.CatalogAuthor
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.ModuleCatalogFailure
import org.bakasu.bakasu.domain.model.ModuleCatalogResult
import org.bakasu.bakasu.domain.model.ModuleRelease
import org.bakasu.bakasu.domain.model.ModuleReleaseAsset
import org.json.JSONArray
import org.json.JSONObject

// The online module repository (KernelSU Modules API v1)
class ModuleCatalogRepository(
    private val networkStatusRepository: NetworkStatusRepository,
    private val networkRequestRepository: NetworkRequestRepository,
) {
    private companion object {
        const val BASE_URL = "https://modules.kernelsu.org/"
        const val INDEX_URL = BASE_URL + "api/v1/index.json"
        const val SCHEMA_VERSION = 1
    }

    private val refreshMutex = Mutex()
    private val readmePaths = ConcurrentHashMap<String, String>()
    private val mutableModules = MutableStateFlow<List<CatalogModule>>(emptyList())
    private val mutableRefreshing = MutableStateFlow(false)
    private val mutableOffline = MutableStateFlow(false)

    val modules: StateFlow<List<CatalogModule>> = mutableModules.asStateFlow()
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()
    val offline: StateFlow<Boolean> = mutableOffline.asStateFlow()

    suspend fun refresh(): ModuleCatalogResult<List<CatalogModule>> = refreshMutex.withLock {
        if (!networkStatusRepository.isAvailable()) {
            mutableOffline.value = true
            return@withLock ModuleCatalogResult.Failure(ModuleCatalogFailure.Offline)
        }
        mutableOffline.value = false
        mutableRefreshing.value = true
        try {
            withContext(Dispatchers.IO) {
                runCatching { fetchModules() }
                    .fold(
                        onSuccess = {
                            mutableModules.value = it
                            ModuleCatalogResult.Success(it)
                        },
                        onFailure = {
                            ModuleCatalogResult.Failure(
                                ModuleCatalogFailure.Network(it.message.orEmpty()),
                            )
                        },
                    )
            }
        } finally {
            mutableRefreshing.value = false
        }
    }

    suspend fun get(moduleId: String): ModuleCatalogResult<CatalogModule> {
        val module = mutableModules.value.firstOrNull { it.moduleId == moduleId }
            ?: when (val refreshed = refresh()) {
                is ModuleCatalogResult.Failure -> return refreshed

                is ModuleCatalogResult.Success -> refreshed.value.firstOrNull { it.moduleId == moduleId }
                    ?: return ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
            }
        // The README is a separate document, fetched when the module is opened.
        if (module.readme.isNotEmpty()) return ModuleCatalogResult.Success(module)
        val readme = readmePaths[moduleId]?.let { path ->
            withContext(Dispatchers.IO) { networkRequestRepository.fetch(BASE_URL + path).getOrNull() }
        }.orEmpty()
        val withReadme = module.copy(readme = readme)
        if (readme.isNotEmpty()) {
            mutableModules.value = mutableModules.value.map { if (it.moduleId == moduleId) withReadme else it }
        }
        return ModuleCatalogResult.Success(withReadme)
    }

    /** The KernelSU Modules API: the index names the module list, each entry names its detail document. */
    private suspend fun fetchModules(): List<CatalogModule> {
        val index = JSONObject(networkRequestRepository.fetch(INDEX_URL).getOrThrow())
        check(index.optInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported module index" }
        val list = JSONObject(networkRequestRepository.fetch(BASE_URL + index.getString("modules")).getOrThrow())
        check(list.optInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported module list" }
        val items = list.optJSONArray("modules") ?: JSONArray()
        return coroutineScope {
            (0 until items.length()).map { index ->
                async(Dispatchers.IO) { items.optJSONObject(index)?.let { parseModule(it) } }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun parseModule(item: JSONObject): CatalogModule? {
        val moduleId = item.optString("id").takeIf(String::isNotEmpty) ?: return null
        // Only installable modules are listed; other kinds (such as one-shot scripts) are not modules.
        if (item.optString("kind", "module") != "module") return null
        val authorList = item.optJSONArray("authors")?.let { authors ->
            (0 until authors.length()).mapNotNull { index ->
                authors.optJSONObject(index)?.let { author ->
                    val name = author.optString("name").trim()
                    name.takeIf(String::isNotEmpty)?.let { CatalogAuthor(it, author.optString("url").trim()) }
                }
            }
        }.orEmpty()
        val stable = item.optJSONObject("stable")
        val latestVersion = stable?.optString("version").orEmpty()
        val detail = item.optString("detail").takeIf(String::isNotEmpty)?.let { fetchModuleDetail(moduleId, it) }
        val releases = detail?.releases.orEmpty()

        return CatalogModule(
            moduleId = moduleId,
            moduleName = item.optString("name"),
            authors = authorList.joinToString(", ") { it.name },
            authorList = authorList,
            summary = item.optString("summary"),
            metamodule = item.optBoolean("metamodule"),
            stargazerCount = item.optInt("stars"),
            updatedAt = item.optString("updatedAt"),
            createdAt = "",
            latestRelease = latestVersion,
            latestReleaseTime = stable?.optString("publishedAt").orEmpty(),
            latestVersionCode = stable?.opt("versionCode").toIntCompat(),
            latestAsset = detail?.releaseVersions?.indexOf(latestVersion)?.takeIf { it >= 0 }?.let(releases::get)
                ?: releases.firstOrNull(),
            installed = SuFile.open("/data/adb/modules/$moduleId/module.prop").exists(),
            readme = "",
            sourceUrl = detail?.sourceUrl.orEmpty(),
            releases = releases,
        )
    }

    private suspend fun fetchModuleDetail(moduleId: String, path: String): Detail? = networkRequestRepository
        .fetch(BASE_URL + path)
        .getOrNull()
        ?.let { body ->
            val json = JSONObject(body)
            json.optString("readme").takeIf(String::isNotEmpty)?.let { readmePaths[moduleId] = it }
            val releases = json.optJSONArray("releases")?.let { array ->
                (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
            }.orEmpty().filter { it.optJSONObject("zip")?.optString("url")?.isNotEmpty() == true }
            Detail(
                sourceUrl = json.optJSONObject("source")?.optString("url").orEmpty(),
                releases = releases.map { it.toRelease() },
                releaseVersions = releases.map { it.optString("version") },
            )
        }

    /** A repository release publishes exactly one module ZIP. */
    private fun JSONObject.toRelease(): ModuleRelease {
        val tag = optString("tag")
        val zip = getJSONObject("zip")
        return ModuleRelease(
            name = optString("name").ifBlank { optString("version").ifBlank { tag } },
            tagName = tag,
            publishedAt = optString("publishedAt"),
            descriptionHTML = optString("changelogHtml"),
            assets = listOf(
                ModuleReleaseAsset(
                    name = zip.optString("name").ifBlank { zip.optString("url").substringAfterLast('/') },
                    downloadUrl = zip.optString("url"),
                    size = zip.optLong("size"),
                    downloadCount = 0,
                    sha256 = zip.optString("sha256"),
                ),
            ),
        )
    }

    private fun Any?.toIntCompat(): Int = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: 0
        else -> 0
    }

    private data class Detail(
        val sourceUrl: String,
        val releases: List<ModuleRelease>,
        val releaseVersions: List<String>,
    )
}
