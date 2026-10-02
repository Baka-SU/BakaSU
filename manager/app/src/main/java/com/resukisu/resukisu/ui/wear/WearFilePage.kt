package com.resukisu.resukisu.ui.wear

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DriveFileRenameOutline
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.FilePresent
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.FolderZip
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.SubdirectoryArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.file.WearFileMode
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearChip
import com.resukisu.resukisu.ui.component.wear.WearChipEmphasis
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import com.resukisu.resukisu.ui.component.wear.WearStatusTone
import com.resukisu.resukisu.ui.component.wear.WearSubPage
import com.resukisu.resukisu.ui.component.wear.WearPageTransition
import com.resukisu.resukisu.ui.component.wear.WearTextInputPage
import com.resukisu.resukisu.ui.component.wear.wearGroupGap
import com.resukisu.resukisu.ui.viewmodel.WearFileEvent
import com.resukisu.resukisu.ui.viewmodel.WearFileViewModel
import org.koin.compose.viewmodel.koinViewModel
import com.resukisu.resukisu.ui.component.wear.rememberWearPageViewModelOwner
import com.resukisu.resukisu.ui.component.wear.ReleaseWearPageViewModels

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * The built-in picker as a Wear list: the task title, the current path as a caption, a low-emphasis
 * entry to the parent folder, then folders and matching files as tonal buttons. In save mode the file
 * name follows and the edge button confirms; otherwise picking a file returns it.
 */
@Composable
internal fun WearFilePage(title: String, mode: WearFileMode, onBack: () -> Unit, onSelected: (String) -> Unit, onSaved: (String) -> Unit,
    requestId: Int, onSelectDirectory: ((String, String) -> Unit)? = null, message: String? = null, busy: Boolean = false) {
    val key = "wear-picker-$mode-$requestId"
    val viewModel = koinViewModel<WearFileViewModel>(viewModelStoreOwner = rememberWearPageViewModelOwner(key))
    ReleaseWearPageViewModels(key, remember(viewModel) { viewModel.state.map { it.loading } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backupFileName = stringResource(R.string.wear_susfs_backup_file_name)
    LaunchedEffect(viewModel, mode) {
        if (viewModel.state.value.directory == null) {
            if (mode == WearFileMode.JSON_DIRECTORY) viewModel.setName(backupFileName)
            viewModel.load(null, mode)
        }
    }
    LaunchedEffect(viewModel) { viewModel.events.collect { event -> when (event) {
        is WearFileEvent.Selected -> onSelected(event.uri)
        is WearFileEvent.Saved -> onSaved(event.path)
        else -> Unit
    } } }
    var editingName by rememberSaveable { mutableStateOf(false) }
    WearPageTransition(if (editingName) "name" else "", if (editingName) 1 else 0) { route ->
        if (route == "name") {
            WearSubPage({ editingName = false }) {
                WearTextInputPage(stringResource(R.string.wear_file_name), state.name) {
                    viewModel.setName(it); editingName = false
                }
            }
        } else {
            val error = state.error ?: if (state.failed) stringResource(R.string.operation_failed) else message
            val directory = state.directory
            val saving = mode == WearFileMode.DIRECTORY || mode == WearFileMode.JSON_DIRECTORY
            WearList(isLoading = busy || state.loading || directory == null && !state.failed, onBack = onBack, snap = true,
                onConfirm = if (saving && directory?.writable == true) ({
                    if (!busy) {
                        if (onSelectDirectory != null) onSelectDirectory(directory.path, state.name) else viewModel.save()
                    }
                }) else null,
            ) { spec ->
                item { WearPageHeader(spec, null, title) }
                directory?.let { item {
                    WearScaledItem(spec) {
                        Text(it.path, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyExtraSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.StartEllipsis)
                    }
                } }
                error?.let { item { WearStatusItem(spec, Icons.TwoTone.Error, it, tone = WearStatusTone.ERROR) } }
                directory?.parent?.let { parent -> item {
                    WearChip(spec, stringResource(R.string.wear_parent_directory), icon = Icons.TwoTone.SubdirectoryArrowLeft, emphasis = WearChipEmphasis.OUTLINED,
                        onClick = { viewModel.load(parent, mode) })
                } }
                if (directory != null && directory.entries.isEmpty()) item {
                    WearStatusItem(spec, Icons.TwoTone.FolderOff, stringResource(R.string.wear_directory_empty))
                }
                items(directory?.entries.orEmpty(), key = { it.path }) { entry ->
                    WearActionButton(spec,
                        icon = when {
                            entry.directory -> Icons.TwoTone.Folder
                            mode == WearFileMode.IMAGE -> Icons.TwoTone.Image
                            mode == WearFileMode.KERNEL_MODULE -> Icons.TwoTone.Memory
                            entry.name.endsWith(".zip", ignoreCase = true) -> Icons.TwoTone.FolderZip
                            else -> Icons.TwoTone.FilePresent
                        },
                        label = entry.name,
                        onClick = { if (entry.directory) viewModel.load(entry.path, mode) else viewModel.select(entry.path) },
                        secondaryText = if (entry.directory) null else Formatter.formatShortFileSize(context, entry.size),
                        colors = ButtonDefaults.filledTonalButtonColors())
                }
                if (saving) {
                    wearGroupGap("save-gap")
                    if (directory?.writable == false) item {
                        WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.wear_directory_unavailable), tone = WearStatusTone.ERROR)
                    }
                    item {
                        WearActionButton(spec, Icons.TwoTone.DriveFileRenameOutline, state.name, { editingName = true },
                            secondaryText = stringResource(R.string.wear_file_name), colors = ButtonDefaults.filledTonalButtonColors())
                    }
                }
            }
        }
    }
}
