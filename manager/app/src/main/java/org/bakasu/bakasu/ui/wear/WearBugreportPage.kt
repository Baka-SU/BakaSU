package org.bakasu.bakasu.ui.wear

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.BuildConfig
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.usecase.GenerateBugreportUseCase
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearResult
import org.bakasu.bakasu.ui.wear.component.WearResultDialog
import org.koin.compose.koinInject

/** Saving or sharing the bugreport, as the phone's log sheet does. */
@Composable
internal fun WearBugreportPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generateBugreport = koinInject<GenerateBugreportUseCase>()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<WearResult?>(null) }
    val sendLog = stringResource(R.string.send_log)
    val saved = WearResult(stringResource(R.string.log_saved), success = true, brief = true)
    val failed = WearResult(stringResource(R.string.operation_failed), success = false, brief = true)
    fun run(block: suspend () -> Unit) {
        busy = true
        result = null
        scope.launch {
            runCatching { block() }.onFailure { result = failed }
            busy = false
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri: Uri? ->
        if (uri != null) {
            run {
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                        generateBugreport().inputStream().use { it.copyTo(output) }
                    }
                }
                result = saved
            }
        }
    }
    WearList(isLoading = busy, onBack = onBack) { spec ->
        item { WearPageHeader(spec, sendLog) }
        item {
            WearActionButton(spec, Icons.Rounded.Save, stringResource(R.string.save_log), {
                val current = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm"))
                runCatching { exportLauncher.launch("KernelSU_bugreport_$current.tar.gz") }.onFailure { result = failed }
            })
        }
        item {
            WearActionButton(spec, Icons.Rounded.Share, sendLog, {
                run {
                    val bugreport = withContext(Dispatchers.IO) { generateBugreport() }
                    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", bugreport)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .putExtra(Intent.EXTRA_STREAM, uri).setDataAndType(uri, "application/gzip")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            sendLog,
                        ),
                    )
                }
            })
        }
    }
    WearResultDialog(result) { result = null }
}
