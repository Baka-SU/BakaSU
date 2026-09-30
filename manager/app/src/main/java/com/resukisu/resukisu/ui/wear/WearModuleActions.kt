package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionUiEvent
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionViewModel
import com.resukisu.resukisu.ui.viewmodel.WearModuleUpdateViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun WearModuleUpdatePage(module: InstalledModule?, onBack: () -> Unit, onReady: (String) -> Unit) {
    val viewModel = koinViewModel<WearModuleUpdateViewModel>(key = "wear-update-${module?.id}")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val download = state.download
    LaunchedEffect(module?.id) { module?.let(viewModel::loadChangelog) }
    LaunchedEffect(download?.resultUri) {
        download?.resultUri?.let { uri -> viewModel.consume(); onReady(uri) }
    }
    val working = download?.status in listOf(DownloadStatus.PENDING, DownloadStatus.DOWNLOADING)
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, null, stringResource(R.string.module_update)) }
        if (module?.moduleUpdate != null) {
            item { WearInfoCard(spec) { Text(module.name); Text(module.moduleUpdate.version) } }
            item { WearSectionHeader(spec, Icons.TwoTone.Description, stringResource(R.string.module_changelog)) }
            when {
                state.changelogError != null -> item {
                    WearInfoCard(spec) { Text(stringResource(R.string.module_changelog_failed, state.changelogError.orEmpty())) }
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
            if (download?.status == DownloadStatus.FAILED) item { WearInfoCard(spec) { Text(stringResource(R.string.operation_failed)) } }
            // Like the phone, the update is offered only after its changelog has been read.
            item { WearActionButton(spec, Icons.TwoTone.Update, stringResource(R.string.module_update), { viewModel.start(module) },
                !working && state.changelog != null) }
        }
    }
}

@Composable
internal fun WearExecuteModulePage(moduleId: String, requestId: Int, onBack: () -> Unit, onCompleted: () -> Unit) {
    val viewModel = koinViewModel<ExecuteModuleActionViewModel>(key = "wear-action-$moduleId-$requestId",
        parameters = { parametersOf(moduleId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    var successful by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(viewModel) { viewModel.events.collect { event -> when (event) {
        is ExecuteModuleActionUiEvent.Completed -> { successful = event.successful; onCompleted() }
        is ExecuteModuleActionUiEvent.Error -> successful = false
        else -> Unit
    } } }
    val status = when (successful) {
        true -> WearFlashStatus.SUCCESS
        false -> WearFlashStatus.FAILED
        null -> if (state.running) WearFlashStatus.RUNNING else WearFlashStatus.SUCCESS
    }
    val lines = remember(state.output) { state.output.toLogLines() }
    val listState = rememberTransformingLazyColumnState()
    WearFollowLog(listState, lines.size, following = state.running)
    WearList(onBack = onBack, listState = listState) { spec ->
        item { WearPageHeader(spec, null, stringResource(R.string.action)) }
        item {
            WearFlashStatusChip(spec, status, stringResource(when (status) {
                WearFlashStatus.RUNNING -> R.string.action
                WearFlashStatus.SUCCESS -> R.string.module_action_success
                WearFlashStatus.FAILED -> R.string.operation_failed
            }))
        }
        if (state.running) item { WearFlashProgress(spec) }
        wearLogLines(spec, lines)
    }
}
