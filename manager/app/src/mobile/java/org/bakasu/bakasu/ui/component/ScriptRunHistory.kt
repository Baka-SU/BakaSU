package org.bakasu.bakasu.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.text.DateFormat
import java.util.Date
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.script.ScriptRunRecord

@Composable
fun scriptLastRunText(record: ScriptRunRecord?): String {
    if (record == null) return stringResource(R.string.script_never_run)
    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.time))
    return if (record.versionCode != null) {
        stringResource(R.string.script_last_run_version, time, record.versionCode, record.exitCode)
    } else {
        stringResource(R.string.script_last_run, time, record.exitCode)
    }
}
