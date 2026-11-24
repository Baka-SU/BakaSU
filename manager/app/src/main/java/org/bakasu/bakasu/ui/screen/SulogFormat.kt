package org.bakasu.bakasu.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.SulogEntry
import org.bakasu.bakasu.domain.model.SulogEventFilter
import org.bakasu.bakasu.domain.model.SulogEventType

@Composable
fun sulogFilterLabel(filter: SulogEventFilter): String = stringResource(filter.resId)

@Composable
fun sulogEntryTitle(entry: SulogEntry): String {
    val title = when (entry.eventType) {
        SulogEventType.RootExecve -> entry.fields["comm"]
        SulogEventType.Unknown -> entry.fields["type"]?.replace('_', ' ')?.replaceFirstChar(Char::uppercase)
        else -> null
    }
    return title ?: stringResource(entry.eventType.resId)
}

@Composable
fun sulogEntryDescription(entry: SulogEntry): String? = when (entry.eventType) {
    SulogEventType.DaemonEvent -> entry.fields["boot_id"]?.let { "Boot ID: $it" }
    SulogEventType.Dropped -> entry.fields["ts_ns"]?.let { "Timestamp: $it" }
    else -> entry.fields["argv"] ?: entry.fields["file"]
}

@Composable
fun sulogEntrySummaryTags(entry: SulogEntry): List<String> {
    val comm = entry.fields["comm"]
    val pid = entry.fields["pid"]
    val uid = entry.fields["uid"]
    return when (entry.eventType) {
        SulogEventType.DaemonEvent -> listOfNotNull(
            entry.fields["restart"]?.let {
                stringResource(
                    R.string.sulog_daemon_restart_count,
                    it,
                )
            } ?: stringResource(R.string.sulog_filter_daemon_restart),
        )

        SulogEventType.Dropped -> listOfNotNull(
            entry.fields["dropped"]?.let {
                stringResource(
                    R.string.sulog_drop_count,
                    it,
                )
            },
        )

        else -> listOfNotNull(
            comm?.takeIf { it.isNotBlank() },
            uid?.let { "UID $it" },
            pid?.let { "PID $it" },
        )
    }
}

fun sulogEntryDetailText(entry: SulogEntry) = buildAnnotatedString {
    entry.fields.entries.forEachIndexed { index, (key, value) ->
        if (index > 0) append('\n')
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append("$key: ")
        }
        append(value)
    }
}

fun sulogEntryStatus(entry: SulogEntry): String? = entry.fields["retval"]?.toIntOrNull()?.let(::formatSulogStatus)

private fun formatSulogStatus(retval: Int): String = if (retval == 0) "Success" else "Exit $retval"
