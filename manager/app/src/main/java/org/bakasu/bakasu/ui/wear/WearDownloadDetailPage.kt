package org.bakasu.bakasu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import java.text.DateFormat
import java.util.Date
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsEvent
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsViewModel
import org.bakasu.bakasu.ui.viewmodel.formatFileSize
import org.bakasu.bakasu.ui.wear.component.LocalWearToast
import org.bakasu.bakasu.ui.wear.component.WearActionButton
import org.bakasu.bakasu.ui.wear.component.WearCatalogModuleIcon
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearInfoCard
import org.bakasu.bakasu.ui.wear.component.WearInfoRow
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.koin.compose.viewmodel.koinViewModel

/** Observes an existing transfer; opening this destination never starts a download. */
@Composable
internal fun WearDownloadDetailPage(route: WearRoute.Download, onBack: () -> Unit, onInstall: (String) -> Unit) {
    val viewModel = koinViewModel<WearDownloadsViewModel>()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val verifying by viewModel.verifying.collectAsStateWithLifecycle()
    var downloadId by rememberSaveable(route) { mutableStateOf(route.downloadId) }
    val task = if (downloadId != null) downloads.firstOrNull { it.id == downloadId } else downloads.firstOrNull { it.module?.moduleId == route.moduleId }
    val toast = LocalWearToast.current
    val failed = stringResource(R.string.wear_download_verify_failed)
    LaunchedEffect(viewModel, toast, failed) {
        viewModel.events.collect {
            when (it) {
                is WearDownloadsEvent.Install -> onInstall(it.uri)
                WearDownloadsEvent.VerificationFailed -> toast.show(failed)
            }
        }
    }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, task?.module?.name ?: stringResource(R.string.wear_downloads)) }
        if (task == null) {
            item { WearEmptyState(spec, Icons.Rounded.Download, stringResource(R.string.wear_download_record_missing)) }
        } else {
            item {
                WearInfoCard(spec) {
                    WearCatalogModuleIcon(task.module?.iconUrl, task.module?.iconOrigin)
                    Text(
                        stringResource(
                            when (task.status) {
                                DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> R.string.wear_downloading
                                DownloadStatus.COMPLETED -> R.string.download_complete_title
                                DownloadStatus.FAILED -> R.string.download_failed_title
                            },
                        ),
                    )
                    Text(stringResource(R.string.wear_download_size, formatFileSize(task.downloadedBytes), if (task.totalBytes > 0) formatFileSize(task.totalBytes) else stringResource(R.string.unknown)))
                    if (task.totalBytes > 0) Text(stringResource(R.string.wear_download_percent, task.progress))
                    task.failure?.let { Text(stringResource(it.reason.resId)) }
                    task.verificationFailure?.let { Text(stringResource(it.resId)) }
                }
            }
            item { WearInfoCard(spec) { WearInfoRow(Icons.Rounded.InsertDriveFile, stringResource(R.string.wear_download_file), task.fileName) } }
            item { WearInfoCard(spec) { WearInfoRow(Icons.Rounded.Schedule, stringResource(R.string.wear_download_started), DateFormat.getDateTimeInstance().format(Date(task.startedAt))) } }
            item {
                when (task.status) {
                    DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> WearActionButton(spec, Icons.Rounded.Close, stringResource(R.string.download_cancel), { viewModel.cancel(task.id) })
                    DownloadStatus.FAILED -> WearActionButton(spec, Icons.Rounded.Refresh, stringResource(R.string.network_retry), { downloadId = viewModel.retry(task) ?: downloadId })
                    DownloadStatus.COMPLETED -> WearActionButton(spec, Icons.Rounded.Download, stringResource(R.string.install), { viewModel.install(task) }, enabled = !verifying)
                }
            }
        }
    }
}
