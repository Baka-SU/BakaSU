package org.bakasu.bakasu.ui.wear.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.theme.monospaceFontFamily

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */
enum class WearFlashStatus { RUNNING, SUCCESS, FAILED }

/** Renders one transformed item per log line, preserving blank lines. */
fun TransformingLazyColumnScope.wearLogLines(transformationSpec: TransformationSpec, lines: List<String>) {
    items(count = lines.size, contentType = { "wear-log-line" }) { index ->
        WearScaledItem(transformationSpec) {
            Text(
                lines[index],
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = monospaceFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Splits output into lines, without the trailing empty line a final newline produces. */
fun String.toLogLines(): List<String> = if (isEmpty()) emptyList() else trimEnd('\n').split('\n')

/** While [following], keeps the newest line in view unless the user is scrolling. */
@Composable
fun WearFollowLog(listState: TransformingLazyColumnState, lineCount: Int, following: Boolean) {
    LaunchedEffect(lineCount, following) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (following && last > 0 && !listState.isScrollInProgress) listState.scrollToItem(last)
    }
}

@Composable
fun WearLogPage(title: String, lines: List<String>, following: Boolean, onBack: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    WearFollowLog(listState, lines.size, following)
    WearList(onBack = onBack, listState = listState) { spec ->
        item { WearPageHeader(spec, title) }
        wearLogLines(spec, lines)
    }
}

/** Returns an action to save `<prefix>_<date>.log` through the document picker and report the result. */
@Composable
fun rememberWearLogSaver(fileNamePrefix: String, content: () -> String): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.log_saved)
    val failed = stringResource(R.string.operation_failed)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            val text = content()
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(text.toByteArray()) }
                    }
                }.onSuccess { result = saved }.onFailure { result = failed }
            }
        }
    }
    WearFeedbackEffect(result) { result = null }
    return {
        val date = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(Date())
        runCatching { launcher.launch("${fileNamePrefix}_$date.log") }.onFailure { result = failed }
    }
}
