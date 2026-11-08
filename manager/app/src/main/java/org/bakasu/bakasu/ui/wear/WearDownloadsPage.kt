package org.bakasu.bakasu.ui.wear

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DownloadState
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.domain.usecase.LoadModuleIconUseCase
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsEvent
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsViewModel
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearErrorDialog
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSwipeToDelete
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.koin.compose.koinInject
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
    WearDownloadsPage(downloads, verifying, onBack, viewModel::install, viewModel::remove, viewModel::clear)
}

@Composable
internal fun WearDownloadsPage(
    downloads: List<DownloadState>,
    verifying: Boolean,
    onBack: () -> Unit,
    onInstall: (DownloadState) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    var pendingRemoval by remember { mutableStateOf<Int?>(null) }
    val deleteLabel = stringResource(R.string.delete)
    val removeDialog = rememberWearConfirmDialog(
        stringResource(R.string.wear_download_remove_title),
        stringResource(R.string.wear_download_remove_message),
        destructive = true,
        onConfirm = { pendingRemoval?.let(onRemove) },
    )
    val clearDialog = rememberWearConfirmDialog(
        stringResource(R.string.wear_download_clear_all),
        stringResource(R.string.wear_download_clear_message),
        destructive = true,
        onConfirm = onClear,
    )
    WearList(
        onBack = onBack,
        listState = listState,
        onConfirm = (clearDialog::show).takeIf { downloads.isNotEmpty() },
        confirmLabel = stringResource(R.string.wear_download_clear_all).takeIf { downloads.isNotEmpty() },
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_downloads)) }
        if (downloads.isEmpty()) item { WearEmptyState(spec, Icons.Rounded.Download, stringResource(R.string.wear_downloads_empty)) }
        items(downloads, key = { it.id }) { task ->
            WearSwipeToDelete(spec, listState.isScrollInProgress, deleteLabel, {
                pendingRemoval = task.id
                removeDialog.show()
            }) {
                Button(
                    onClick = { if (task.status == DownloadStatus.COMPLETED) onInstall(task) },
                    enabled = !verifying,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    icon = { DownloadModuleIcon(task) },
                    label = { Text(task.module?.name?.takeIf { it.isNotBlank() } ?: task.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = {
                        val active = task.status == DownloadStatus.PENDING || task.status == DownloadStatus.DOWNLOADING
                        Text(
                            if (active && task.totalBytes > 0) {
                                stringResource(R.string.wear_download_percent, task.progress)
                            } else {
                                stringResource(
                                    when (task.status) {
                                        DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> R.string.wear_downloading
                                        DownloadStatus.COMPLETED -> R.string.download_complete_title
                                        DownloadStatus.FAILED -> if (task.verificationFailure != null) R.string.wear_download_verify_failed else R.string.download_failed_title
                                    },
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadModuleIcon(task: DownloadState) {
    val url = task.module?.iconUrl
    val origin = task.module?.iconOrigin
    val loadIcon = koinInject<LoadModuleIconUseCase>()
    val bitmap by produceState(initialValue = url?.let { loadIcon.cached(it, origin) }, url, origin) {
        value = if (url.isNullOrBlank()) null else loadIcon.cached(url, origin) ?: loadIcon(url, origin)
    }
    val modifier = Modifier.size(ButtonDefaults.IconSize)
    val loaded = bitmap
    if (loaded == null) {
        Icon(Icons.Rounded.Extension, contentDescription = null, modifier = modifier)
    } else {
        Image(loaded.asImageBitmap(), contentDescription = null, modifier = modifier.clip(MaterialTheme.shapes.small))
    }
}
