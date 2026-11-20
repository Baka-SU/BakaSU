package org.bakasu.bakasu.data.logging

import java.io.File
import java.time.LocalDate
import org.bakasu.bakasu.domain.model.SulogFile

private val sulogFileNameRegex = Regex("""sulog-(\d{4}-\d{2}-\d{2})(?:-(\d+))?\.log""")

/** Cleaning uses every disk candidate; the UI omits empty files, including the open writer file. */
internal fun listSulogFiles(directory: File, includeEmpty: Boolean): List<SulogFile> = directory.listFiles().orEmpty().mapNotNull { file ->
    val match = sulogFileNameRegex.matchEntire(file.name) ?: return@mapNotNull null
    if (!includeEmpty && file.length() == 0L) return@mapNotNull null
    val date = LocalDate.parse(match.groupValues[1])
    val rotation = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt() ?: 0
    Triple(file, date, rotation)
}.sortedWith(
    compareByDescending<Triple<File, LocalDate, Int>> { it.second }.thenByDescending { it.third },
).map { (file) -> SulogFile(name = file.name, path = file.path) }

internal fun List<SulogFile>.selectedSulogFile(preferredFilePath: String?): SulogFile? = firstOrNull { it.path == preferredFilePath } ?: firstOrNull()
