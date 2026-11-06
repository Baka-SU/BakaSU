package org.bakasu.bakasu.data.profile

import android.app.Application
import android.content.pm.PackageManager
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.Natives
import org.bakasu.bakasu.Natives.Profile.Namespace
import org.bakasu.bakasu.Natives.Profile.RootProfileFlag
import org.bakasu.bakasu.data.network.NetworkRequestRepository
import org.bakasu.bakasu.data.network.NetworkStatusRepository
import org.bakasu.bakasu.data.shell.KsuCliRepository
import org.bakasu.bakasu.data.webui.CompanionBridgeRepository
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.domain.model.ProfileTemplateException
import org.bakasu.bakasu.domain.model.ProfileTemplateFailure
import org.bakasu.bakasu.profile.Capabilities
import org.bakasu.bakasu.profile.Groups
import org.json.JSONArray
import org.json.JSONObject

class ProfileTemplateRepository(
    private val application: Application,
    private val networkStatusRepository: NetworkStatusRepository,
    private val networkRequestRepository: NetworkRequestRepository,
    private val ksuCliRepository: KsuCliRepository,
    private val bridge: CompanionBridgeRepository,
) {
    companion object {
        private const val TAG = "ProfileTemplate"
        private const val TEMPLATE_INDEX_URL = "https://kernelsu.org/templates/index.json"
        private const val TEMPLATE_URL = "https://kernelsu.org/templates/%s"
    }

    private val refreshMutex = Mutex()
    private val mutableTemplates = MutableStateFlow<List<ProfileTemplate>>(emptyList())
    private val mutableRefreshing = MutableStateFlow(false)
    private val mutableOffline = MutableStateFlow(false)

    val templates: StateFlow<List<ProfileTemplate>> = mutableTemplates.asStateFlow()
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()
    val offline: StateFlow<Boolean> = mutableOffline.asStateFlow()

    suspend fun refresh(synchronize: Boolean = false): Result<Unit> = refreshMutex.withLock {
        mutableRefreshing.value = true
        try {
            withContext(Dispatchers.IO) {
                runCatching {
                    // Online templates are stored only when the user asks for it, never on a plain refresh.
                    val synchronized = !synchronize || fetchRemoteTemplates()
                    mutableOffline.value = synchronize && !synchronized
                    mutableTemplates.value = ksuCliRepository.listAppProfileTemplates()
                        .mapNotNull(::readTemplate)
                        .sortedWith(
                            compareBy(ProfileTemplate::local).reversed().then(
                                compareBy(
                                    Collator.getInstance(Locale.getDefault()),
                                    ProfileTemplate::id,
                                ),
                            ),
                        )
                }
            }
        } finally {
            mutableRefreshing.value = false
        }
    }

    /** Serves the fixed official template source after the phone has approved the request. */
    internal suspend fun onlineBodiesForPhone(): ByteArray? = withContext(Dispatchers.IO) {
        fetchRemoteBodies()?.let { JSONObject(it).toString().toByteArray() }?.takeIf { it.size <= 1_048_576 }
    }

    /** The official templates by ID, skipping those that fail to download; null without the index. */
    private suspend fun fetchRemoteBodies(): Map<String, String>? {
        val ids = networkRequestRepository.fetch(TEMPLATE_INDEX_URL)
            .mapCatching { JSONArray(it) }
            .getOrElse { return null }
        return (0 until ids.length()).mapNotNull { index ->
            val id = ids.optString(index)
            networkRequestRepository.fetch(TEMPLATE_URL.format(id)).getOrNull()?.let { id to it }
        }.toMap()
    }

    private suspend fun fetchBodiesFromPhone(): Map<String, String>? {
        if (!application.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) return null
        val bytes = bridge.handoff(BridgeAction.TEMPLATES)
        require(bytes.size <= 1_048_576)
        val bodies = JSONObject(bytes.decodeToString())
        return bodies.keys().asSequence().associateWith(bodies::getString)
    }

    /**
     * The official templates for browsing. Nothing is stored; [addOnline] adds a chosen one. Without
     * a usable direct route, a failed download is retried through the paired phone.
     */
    suspend fun fetchOnline(): Result<List<ProfileTemplate>> = withContext(Dispatchers.IO) {
        val online = networkStatusRepository.isAvailable()
        val bodies = try {
            fetchRemoteBodies() ?: fetchBodiesFromPhone()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return@withContext Result.failure(error)
        } ?: return@withContext Result.failure(ProfileTemplateException(if (online) ProfileTemplateFailure.RequestFailed else ProfileTemplateFailure.Offline))
        val templates = bodies.values.mapNotNull { body ->
            runCatching { JSONObject(body).toTemplate() }.getOrNull()?.copy(local = false)
        }
        if (templates.isEmpty() && bodies.isNotEmpty()) {
            Result.failure(ProfileTemplateException(ProfileTemplateFailure.Invalid))
        } else {
            Result.success(templates.sortedWith(compareBy(Collator.getInstance(Locale.getDefault()), ProfileTemplate::name)))
        }
    }

    /**
     * Stores an online template as a local one, which App Profile can select. A local template
     * with the same ID is kept, not overwritten; a copy saved by an earlier synchronization is.
     */
    suspend fun addOnline(template: ProfileTemplate): Result<Unit> = withContext(Dispatchers.IO) {
        if (readTemplate(template.id)?.local == true) {
            return@withContext Result.failure(ProfileTemplateException(ProfileTemplateFailure.Conflict))
        }
        if (!ksuCliRepository.setAppProfileTemplate(template.id, template.copy(local = true).toJson().toString())) {
            return@withContext Result.failure(ProfileTemplateException(ProfileTemplateFailure.Command("add failed")))
        }
        refreshLocalCache()
        Result.success(Unit)
    }

    suspend fun get(id: String): Result<ProfileTemplate> = withContext(Dispatchers.IO) {
        mutableTemplates.value.firstOrNull { it.id == id }
            ?.let { return@withContext Result.success(it) }
        readTemplate(id)?.let { Result.success(it) }
            ?: Result.failure(ProfileTemplateException(ProfileTemplateFailure.NotFound))
    }

    suspend fun save(template: ProfileTemplate, create: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isValidId(template.id)) {
            return@withContext Result.failure(ProfileTemplateException(ProfileTemplateFailure.Invalid))
        }
        if (create && ksuCliRepository.getAppProfileTemplate(template.id).isNotBlank()) {
            return@withContext Result.failure(ProfileTemplateException(ProfileTemplateFailure.Conflict))
        }
        if (!ksuCliRepository.setAppProfileTemplate(
                template.id,
                template.copy(local = true).toJson().toString(),
            )
        ) {
            return@withContext Result.failure(
                ProfileTemplateException(ProfileTemplateFailure.Command("save failed")),
            )
        }
        refreshLocalCache()
        Result.success(Unit)
    }

    suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!ksuCliRepository.deleteAppProfileTemplate(id)) {
            Result.failure(ProfileTemplateException(ProfileTemplateFailure.Command("delete failed")))
        } else {
            refreshLocalCache()
            Result.success(Unit)
        }
    }

    suspend fun import(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val array = runCatching { JSONArray(json) }.getOrElse {
                JSONArray().apply { put(JSONObject(json)) }
            }
            var saved = 0
            (0 until array.length()).forEach { index ->
                val value = array.optJSONObject(index) ?: return@forEach
                val parsed = value.toTemplate() ?: return@forEach
                if (ksuCliRepository.setAppProfileTemplate(
                        parsed.id,
                        parsed.copy(local = true).toJson().toString(),
                    )
                ) {
                    saved++
                }
            }
            check(saved > 0) { "No valid templates" }
            refreshLocalCache()
        }
    }

    suspend fun export(): Result<String> = withContext(Dispatchers.IO) {
        val local = ksuCliRepository.listAppProfileTemplates()
            .mapNotNull(::readTemplate)
            .filter(ProfileTemplate::local)
        if (local.isEmpty()) {
            Result.failure(ProfileTemplateException(ProfileTemplateFailure.Empty))
        } else {
            Result.success(JSONArray(local.map { it.toJson() }).toString())
        }
    }

    private fun refreshLocalCache() {
        mutableTemplates.value =
            ksuCliRepository.listAppProfileTemplates().mapNotNull(::readTemplate)
                .sortedWith(
                    compareBy(ProfileTemplate::local).reversed().thenBy(ProfileTemplate::id),
                )
    }

    /**
     * Without a usable network, as on a watch behind its phone's Bluetooth proxy, the templates are
     * fetched by the paired phone's Manager instead.
     */
    private suspend fun fetchRemoteTemplates(): Boolean = runCatching {
        val bodies = fetchRemoteBodies() ?: fetchBodiesFromPhone()
            ?: return false
        var fetchedAny = bodies.isEmpty()
        bodies.forEach { (id, body) ->
            val template =
                runCatching { JSONObject(body).toTemplate() }.getOrNull() ?: return@forEach
            if (ksuCliRepository.setAppProfileTemplate(
                    id,
                    template.copy(local = false).toJson().toString(),
                )
            ) {
                fetchedAny = true
            }
        }
        fetchedAny
    }.getOrDefault(false)

    private fun readTemplate(id: String): ProfileTemplate? = runCatching {
        JSONObject(ksuCliRepository.getAppProfileTemplate(id)).toTemplate()
    }.getOrNull()

    private fun JSONObject.toTemplate(): ProfileTemplate? = runCatching {
        val namespaceName = optString("namespace").ifBlank { Namespace.INHERITED.name }
        ProfileTemplate(
            id = getString("id"),
            name = localeString("name"),
            description = localeString("description"),
            author = optString("author"),
            local = optBoolean("local"),
            namespace = Namespace.valueOf(namespaceName.uppercase()).ordinal,
            uid = optInt("uid", Natives.ROOT_UID),
            gid = optInt("gid", Natives.ROOT_GID),
            groups = enumOrdinals<Groups>(optJSONArray("groups")).map(Groups::gid),
            capabilities = enumOrdinals<Capabilities>(optJSONArray("capabilities")).map(Capabilities::cap),
            context = optString("context").ifBlank { Natives.KERNEL_SU_DOMAIN },
            rules = optJSONArray("rules").strings(),
            flags = optJSONArray("flags")?.let {
                enumOrdinals<RootProfileFlag>(it).map(RootProfileFlag::ordinal)
            } ?: listOf(RootProfileFlag.NO_NEW_PRIVS.ordinal),
        )
    }.getOrNull()

    private fun ProfileTemplate.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name.ifBlank { id })
        put("description", description.ifBlank { id })
        if (author.isNotEmpty()) put("author", author)
        put("local", local)
        put("namespace", Namespace.entries[namespace].name)
        put("uid", uid)
        put("gid", gid)
        if (groups.isNotEmpty()) {
            put(
                "groups",
                JSONArray(Groups.entries.filter { it.gid in groups }.map { it.name }),
            )
        }
        if (capabilities.isNotEmpty()) {
            put(
                "capabilities",
                JSONArray(Capabilities.entries.filter { it.cap in capabilities }.map { it.name }),
            )
        }
        if (context.isNotEmpty()) put("context", context)
        if (rules.isNotEmpty()) put("rules", JSONArray(rules))
        put(
            "flags",
            JSONArray(RootProfileFlag.entries.filter { it.ordinal in flags }.map { it.name }),
        )
    }

    private fun JSONObject.localeString(key: String): String {
        val fallback = getString(key)
        val locale = Locale.getDefault()
        val localized = optJSONObject("locales") ?: return fallback
        return localized.optJSONObject("${locale.language}_${locale.country}")
            ?.optString(key, fallback)
            ?: localized.optJSONObject(locale.language)?.optString(key, fallback)
            ?: fallback
    }

    private inline fun <reified T : Enum<T>> enumOrdinals(array: JSONArray?): List<T> = array.strings().mapNotNull { runCatching { enumValueOf<T>(it.uppercase()) }.getOrNull() }

    private fun JSONArray?.strings(): List<String> = this?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
    }.orEmpty()

    private fun isValidId(id: String) = Regex("""^([A-Za-z][A-Za-z\d_]*\.)*[A-Za-z][A-Za-z\d_]*$""").matches(id)
}
