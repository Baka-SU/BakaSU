package com.resukisu.resukisu.data.webui

import com.resukisu.resukisu.domain.model.WebUiModuleInfo
import java.io.File
import java.io.InputStream
import java.util.Properties

internal fun readWebUiModuleInfo(
    moduleId: String,
    resolveFile: (String) -> File,
    openFile: (File) -> InputStream,
): WebUiModuleInfo? {
    if (moduleId.isBlank() || moduleId == "." || moduleId == ".." ||
        moduleId.any { it == '/' || it == '\\' || it == '\u0000' }
    ) {
        return null
    }

    val directory = "/data/adb/modules/$moduleId"
    val propertiesFile = resolveFile("$directory/module.prop")
    if (!propertiesFile.isFile) return null

    val properties = Properties().apply {
        openFile(propertiesFile).bufferedReader(Charsets.UTF_8).use { load(it) }
    }

    return WebUiModuleInfo(
        name = properties.getProperty("name").orEmpty().ifBlank { moduleId },
        enabled = !resolveFile("$directory/disable").exists(),
        remove = resolveFile("$directory/remove").exists(),
        hasWebUi = resolveFile("$directory/webroot").exists(),
    )
}
