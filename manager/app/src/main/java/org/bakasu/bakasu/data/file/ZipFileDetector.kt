package org.bakasu.bakasu.data.file

import android.content.Context
import android.net.Uri
import java.io.InputStream
import org.bakasu.bakasu.R

enum class ZipType {
    MODULE,
    KERNEL,
    UNKNOWN,
    ;

    companion object {
        fun detect(files: Set<String>): ZipType = when {
            files.any { it.endsWith("module.prop") } -> MODULE
            files.any { it.endsWith("anykernel.sh") } && files.any { it.startsWith("tools/") } -> KERNEL
            else -> UNKNOWN
        }
    }
}

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

class ZipFileDetector {
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
            e.printStackTrace()
        }

        return zipInfo
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
            e.printStackTrace()
        }

        val type = ZipType.detect(foundFiles)

        return ZipFileInfo(
            uri = uri,
            type = type,
            name = props["name"] ?: props["kernel.string"] ?: "Unknown",
            version = props["version"] ?: props["kernel.version"] ?: "",
            author = props["author"] ?: props["kernel.author"] ?: "",
            description = props["description"] ?: "",
            supported = props["supported.versions"] ?: "",
        )
    }
}
