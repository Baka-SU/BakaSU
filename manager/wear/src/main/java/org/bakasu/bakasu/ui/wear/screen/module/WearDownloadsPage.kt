package org.bakasu.bakasu.ui.wear.screen.module

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DownloadState
import org.bakasu.bakasu.domain.model.DownloadStatus
import org.bakasu.bakasu.ui.viewmodel.WearDownloadsViewModel
import org.bakasu.bakasu.ui.wear.WearRoute
import org.bakasu.bakasu.ui.wear.component.WearAutoMarqueeText
import org.bakasu.bakasu.ui.wear.component.WearCatalogModuleIcon
import org.bakasu.bakasu.ui.wear.component.WearEmptyState
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearPageHeader
import org.bakasu.bakasu.ui.wear.component.WearSwipeToDelete
import org.bakasu.bakasu.ui.wear.component.rememberWearConfirmDialog
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.wear.R as WearR
import org.koin.compose.viewmodel.koinViewModel

/** The shared download service keeps running when this page or a transfer page closes. */
@Composable
internal fun WearDownloadsPage(onBack: () -> Unit, onOpen: (WearRoute.Download) -> Unit) {
    val viewModel = koinViewModel<WearDownloadsViewModel>()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    WearDownloadsPage(downloads, onBack, viewModel::remove, viewModel::clear, { onOpen(WearRoute.Download(it.module?.moduleId.orEmpty(), it.id)) })
}

@Composable
internal fun WearDownloadsPage(
    downloads: List<DownloadState>,
    onBack: () -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onOpen: (DownloadState) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    var pendingRemoval by remember { mutableStateOf<Int?>(null) }
    val deleteLabel = stringResource(R.string.delete)
    val removeDialog = rememberWearConfirmDialog(
        stringResource(WearR.string.wear_download_remove_title),
        stringResource(WearR.string.wear_download_remove_message),
        destructive = true,
        onConfirm = { pendingRemoval?.let(onRemove) },
    )
    val clearDialog = rememberWearConfirmDialog(
        stringResource(WearR.string.wear_download_clear_all),
        stringResource(WearR.string.wear_download_clear_message),
        destructive = true,
        onConfirm = onClear,
    )
    WearList(
        onBack = onBack,
        listState = listState,
        onConfirm = (clearDialog::show).takeIf { downloads.isNotEmpty() },
        confirmLabel = stringResource(WearR.string.wear_download_clear_all).takeIf { downloads.isNotEmpty() },
    ) { spec ->
        item { WearPageHeader(spec, stringResource(WearR.string.wear_downloads)) }
        if (downloads.isEmpty()) item { WearEmptyState(spec, Icons.Rounded.Download, stringResource(WearR.string.wear_downloads_empty)) }
        items(downloads, key = { it.id }) { task ->
            WearSwipeToDelete(spec, listState.isScrollInProgress, deleteLabel, {
                pendingRemoval = task.id
                removeDialog.show()
            }) {
                WearSettingsJumpPageWidget(
                    spec = spec,
                    title = task.module?.name?.takeIf { it.isNotBlank() } ?: task.fileName,
                    onClick = { onOpen(task) },
                    transformed = false,
                    iconContent = { DownloadModuleIcon(task) },
                    secondaryLabelContent = {
                        val active = task.status == DownloadStatus.PENDING || task.status == DownloadStatus.DOWNLOADING
                        WearAutoMarqueeText(
                            if (active && task.totalBytes > 0) {
                                stringResource(WearR.string.wear_download_percent, task.progress)
                            } else {
                                stringResource(
                                    when (task.status) {
                                        DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> WearR.string.wear_downloading
                                        DownloadStatus.COMPLETED -> R.string.download_complete_title
                                        DownloadStatus.FAILED -> if (task.verificationFailure != null) WearR.string.wear_download_verify_failed else R.string.download_failed_title
                                    },
                                )
                            },
                            maxLines = 2,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadModuleIcon(task: DownloadState) {
    WearCatalogModuleIcon(task.module?.iconUrl, task.module?.iconOrigin, ButtonDefaults.IconSize)
}
