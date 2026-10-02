package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.twotone.Error
import com.resukisu.resukisu.ui.component.wear.WearStatusTone
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.DownloadStatus
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearInfoCard
import com.resukisu.resukisu.ui.component.wear.WearFlashProgress
import com.resukisu.resukisu.ui.component.wear.WearFlashStatus
import com.resukisu.resukisu.ui.component.wear.WearFlashStatusChip
import com.resukisu.resukisu.ui.component.wear.WearFollowLog
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.toLogLines
import com.resukisu.resukisu.ui.component.wear.wearLogLines
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearScaledItem
import com.resukisu.resukisu.ui.component.wear.WearSectionHeader
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionUiAction
import com.resukisu.resukisu.ui.component.wear.rememberWearPageViewModelOwner
import com.resukisu.resukisu.ui.component.wear.ReleaseWearPageViewModels
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionViewModel
import com.resukisu.resukisu.ui.viewmodel.WearModuleUpdateViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearModuleUpdatePage(module: InstalledModule?, onBack: () -> Unit, onReady: (String) -> Unit) {
    val key = "wear-update-${module?.id}"
    val viewModel = koinViewModel<WearModuleUpdateViewModel>(viewModelStoreOwner = rememberWearPageViewModelOwner(key))
    ReleaseWearPageViewModels(key)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val download = state.download
    LaunchedEffect(module?.id) { module?.let(viewModel::loadChangelog) }
    LaunchedEffect(download?.resultUri) {
        download?.resultUri?.let { uri -> viewModel.consume(); onReady(uri) }
    }
    val working = download?.status in listOf(DownloadStatus.PENDING, DownloadStatus.DOWNLOADING)
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module_update)) }
        if (module?.moduleUpdate != null) {
            item { WearInfoCard(spec) { Text(module.name); Text(module.moduleUpdate.version) } }
            item { WearSectionHeader(spec, stringResource(R.string.module_changelog)) }
            when {
                state.changelogError != null -> item {
                    WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.module_changelog_failed, state.changelogError.orEmpty()), tone = WearStatusTone.ERROR)
                }
                state.changelog == null -> item {
                    WearScaledItem(spec) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                }
                else -> state.changelog.orEmpty().chunked(350).forEach { chunk -> item { WearInfoCard(spec) { Text(chunk) } } }
            }
            if (working) item { WearInfoCard(spec) {
                CircularProgressIndicator(progress = { (download?.progress ?: 0) / 100f }, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.module_downloading, module.name))
            } }
            if (download?.status == DownloadStatus.FAILED) item { WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.operation_failed), tone = WearStatusTone.ERROR) }
            // Like the phone, the update is offered only after its changelog has been read.
            item { WearActionButton(spec, Icons.TwoTone.Update, stringResource(R.string.module_update), { viewModel.start(module) },
                !working && state.changelog != null) }
        }
    }
}

@Composable
internal fun WearExecuteModulePage(moduleId: String, requestId: Int, onBack: () -> Unit, onCompleted: () -> Unit) {
    val key = "wear-action-$moduleId-$requestId"
    val owner = rememberWearPageViewModelOwner(key)
    val viewModel = koinViewModel<ExecuteModuleActionViewModel>(viewModelStoreOwner = owner,
        parameters = { parametersOf(moduleId, false, 64 * 1024) })
    ReleaseWearPageViewModels(key, remember(viewModel) { viewModel.state.map { it.running } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    var entered by rememberSaveable(requestId) { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        if (!entered) {
            entered = true
            viewModel.dispatch(ExecuteModuleActionUiAction.Start)
        }
    }
    LaunchedEffect(state.successful) { if (state.successful != null) onCompleted() }
    val interrupted = entered && !state.started
    val status = when (state.successful) {
        true -> WearFlashStatus.SUCCESS
        false -> WearFlashStatus.FAILED
        null -> if (interrupted) WearFlashStatus.FAILED else WearFlashStatus.RUNNING
    }
    val lines = remember(state.output) { state.output.toLogLines() }
    val listState = rememberTransformingLazyColumnState()
    WearFollowLog(listState, lines.size, following = state.running)
    WearList(onBack = onBack, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.action)) }
        item {
            if (interrupted) WearStatusItem(spec, Icons.TwoTone.Error,
                stringResource(R.string.wear_operation_interrupted), tone = WearStatusTone.ERROR)
            else WearFlashStatusChip(spec, status, stringResource(when (status) {
                WearFlashStatus.RUNNING -> R.string.action
                WearFlashStatus.SUCCESS -> R.string.module_action_success
                WearFlashStatus.FAILED -> R.string.operation_failed
            }))
        }
        if (state.running) item { WearFlashProgress(spec) }
        if (state.outputTruncated) item { WearSectionHeader(spec, stringResource(R.string.wear_log_tail)) }
        wearLogLines(spec, lines)
    }
}
