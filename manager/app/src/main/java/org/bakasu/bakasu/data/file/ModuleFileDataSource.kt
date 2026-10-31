package org.bakasu.bakasu.data.file

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.util.Properties
import org.bakasu.bakasu.R

/** What a picked ZIP file is: a module package, a kernel package, or neither. */
enum class ZipType {
    MODULE,
    KERNEL,
    UNKNOWN,
    ;

    companion object {
        /** Kernel packages ship `anykernel.sh` with the tools it sources; modules ship `module.prop`. */
        fun detect(files: Set<String>): ZipType = when {
            files.any { it.endsWith("module.prop") } -> MODULE
            files.any { it.endsWith("anykernel.sh") } && files.any { it.startsWith("tools/") } -> KERNEL
            else -> UNKNOWN
        }
    }
}

/** The metadata the install dialog and the Wear install page show before installing a ZIP. */
data class ZipFileInfo(
    val uri: Uri,
    val type: ZipType,
    val name: String = "",
    val version: String = "",
    val versionCode: String = "",
    val author: String = "",
    val description: String = "",
    val kernelVersion: String = "",
    val supported: String = "",
)

class ModuleUtils {
    private companion object {
        const val TAG = "ModuleUtils"
    }

    /**
     * Extracts the module name from a module ZIP file.
     *
     * Resolution order:
     * 1. Attempts to read the `name` field from `module.prop` file
     * 2. Falls back to the ZIP file name
     *
     * @param context Context used to access the [ContentResolver] and fallback string
     * @param uri Uri pointing to the module ZIP file
     * @return The extracted module name, or a fallback name if extraction fails
     */
    fun extractModuleName(context: Context, uri: Uri): String {
        if (uri == Uri.EMPTY) {
            Log.e(TAG, "The supplied URI is empty")
            return context.getString(R.string.unknown_module)
        }

        try {
            val name = withInstallArchive(context, uri) { zip ->
                val entry = zip.getEntry("module.prop") ?: return@withInstallArchive null
                val prop = Properties()
                zip.getInputStream(entry).use { prop.load(it) }
                prop.getProperty("name")
            }
            if (!name.isNullOrBlank()) {
                return name.replace(
                    Regex("[^a-zA-Z0-9\\s\\-_.@()\\u4e00-\\u9fa5]"),
                    "",
                ).trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting module name: ${e.message}")
        }

        val fallback = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.removeSuffix(".zip")
            ?.replace(
                Regex("[^a-zA-Z0-9\\s\\-_.@()\\u4e00-\\u9fa5]"),
                "",
            )
            ?.trim()
            ?: context.getString(R.string.unknown_module)

        return fallback
    }

    /**
     * Checks whether the given Uri is accessible and readable.
     *
     * This method attempts to open an InputStream from the Uri.
     *
     * @param context Context used to access the [ContentResolver]
     * @param uri The Uri to be checked
     * @return `true` if the Uri can be opened, `false` otherwise
     */
    fun isUriAccessible(context: Context, uri: Uri): Boolean {
        if (uri == Uri.EMPTY) return false

        return try {
            context.contentResolver.openInputStream(uri)?.use {} != null
        } catch (e: Exception) {
            Log.e(TAG, "URI is inaccessible: $uri", e)
            false
        }
    }

    /**
     * Requests persistable read permission for the given Uri.
     *
     * This allows the app to retain access to the Uri across device reboots.
     *
     * @param context Context used to access the [ContentResolver]
     * @param uri The Uri for which persistable permission is requested
     */
    fun takePersistableUriPermission(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Persistent permissions for URIs have been obtained: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get persistent permissions on URIs: $uri, Error: ${e.message}")
        }
    }

    /**
     * Extracts the module ID from a module ZIP file.
     *
     * This method read the `id` field from `module.prop` file
     *
     * @param context Context used to access the [ContentResolver]
     * @param uri Uri pointing to the module ZIP file
     * @return The module ID if found, or `null` if extraction fails
     */
    fun extractModuleId(context: Context, uri: Uri): String? {
        if (uri == Uri.EMPTY) return null

        return withInstallArchive(context, uri) { zip ->
            val entry = zip.getEntry("module.prop") ?: return@withInstallArchive null
            val prop = Properties()
            zip.getInputStream(entry).use { prop.load(it) }
            prop.getProperty("id")
        }
    }

    /**
     * The module metadata of [uri] read from its `module.prop`, or a [ZipType.MODULE] result with a
     * fallback name when the archive cannot be read.
     */
    fun parseModuleInfo(context: Context, uri: Uri): ZipFileInfo {
        var zipInfo = ZipFileInfo(uri = uri, type = ZipType.MODULE)

        try {
            withInstallArchive(context, uri) { zip ->
                val entries = zip.entries
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.lowercase() == "module.prop" || entry.name.endsWith("/module.prop")) {
                        val props = zip.getInputStream(entry).use(::parseSimpleProps)

                        zipInfo = zipInfo.copy(
                            name = props["name"] ?: context.getString(R.string.unknown_module),
                            version = props["version"] ?: "",
                            versionCode = props["versionCode"] ?: "",
                            author = props["author"] ?: "",
                            description = props["description"] ?: "",
                        )
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing module info: ${e.message}")
        }

        return zipInfo
    }

    /**
     * The [ZipType] and metadata of [uri]: `module.prop` for modules, `anykernel.sh` for kernel
     * packages. Unknown archives keep an empty name so callers can hide them.
     */
    fun parseZipFile(context: Context, uri: Uri): ZipFileInfo {
        val props = mutableMapOf<String, String>()
        val foundFiles = mutableSetOf<String>()

        try {
            withInstallArchive(context, uri) { zip ->
                val entries = zip.entries
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    foundFiles.add(name)

                    when {
                        name.endsWith("module.prop") -> {
                            zip.getInputStream(entry).use { props.putAll(parseSimpleProps(it)) }
                        }

                        name.endsWith("anykernel.sh") -> {
                            zip.getInputStream(entry).use { props.putAll(parseShellVariables(it)) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing zip file: ${e.message}")
        }

        return ZipFileInfo(
            uri = uri,
            type = ZipType.detect(foundFiles),
            name = props["name"] ?: props["kernel.string"] ?: "Unknown",
            version = props["version"] ?: props["kernel.version"] ?: "",
            author = props["author"] ?: props["kernel.author"] ?: "",
            description = props["description"] ?: "",
            supported = props["supported.versions"] ?: "",
        )
    }

    private fun parseSimpleProps(inputStream: InputStream): Map<String, String> {
        val map = mutableMapOf<String, String>()
        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank() && !line.startsWith("#") && line.contains("=")) {
                    val parts = line.split("=", limit = 2)
                    map[parts[0].trim()] = parts[1].trim()
                }
            }
        }
        return map
    }

    private fun parseShellVariables(inputStream: InputStream): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val reader = inputStream.bufferedReader()
        var line: String? = reader.readLine()
        while (line != null) {
            if (line.contains("=")) {
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim()
                    .removeSurrounding("\"").removeSurrounding("'")
                    .split(";")[0].trim()
                map[key] = value
            }
            line = reader.readLine()
        }
        return map
    }
}
