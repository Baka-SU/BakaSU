package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsEvent
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsViewModel
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.koin.compose.viewmodel.koinViewModel

/** The shared download service keeps running when this page or a transfer page closes. */
@Composable
internal fun WearDownloadsPage(onBack: () -> Unit, onInstall: (String) -> Unit) {
    val viewModel = koinViewModel<WearDownloadsViewModel>()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val verifying by viewModel.verifying.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }
    WearErrorDialog(stringResource(R.string.wear_download_verify_failed).takeIf { failed }) { failed = false }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WearDownloadsEvent.Install -> onInstall(event.uri)
                WearDownloadsEvent.VerificationFailed -> failed = true
            }
        }
    }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_downloads)) }
        if (downloads.isEmpty()) item { WearEmptyState(spec, Icons.Rounded.Download, stringResource(R.string.wear_downloads_empty)) }
        downloads.forEach { task ->
            item(key = "task:${task.id}") {
                WearInfoCard(spec) {
                    Text(task.fileName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            when (task.status) {
                                DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> R.string.wear_downloading
                                DownloadStatus.COMPLETED -> R.string.download_complete_title
                                DownloadStatus.FAILED -> if (task.verificationFailure != null) R.string.wear_download_verify_failed else R.string.download_failed_title
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (task.status == DownloadStatus.PENDING || task.status == DownloadStatus.DOWNLOADING) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text(stringResource(R.string.wear_download_percent, task.progress), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (task.status == DownloadStatus.PENDING || task.status == DownloadStatus.DOWNLOADING) {
                item(key = "cancel:${task.id}") {
                    WearActionButton(spec, Icons.Rounded.Close, stringResource(R.string.download_cancel), { viewModel.cancel(task.id) }, colors = ButtonDefaults.filledTonalButtonColors())
                }
            } else if (task.status == DownloadStatus.COMPLETED) {
                item(key = "install:${task.id}") {
                    WearActionButton(spec, Icons.Rounded.InstallMobile, stringResource(R.string.download_install), { viewModel.install(task) }, enabled = !verifying)
                }
            }
        }
    }
}
