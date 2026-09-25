package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.FlashOperation
import com.resukisu.resukisu.domain.usecase.IsModuleUriAccessibleUseCase
import com.resukisu.resukisu.domain.usecase.TakeModuleUriPermissionUseCase
import com.resukisu.resukisu.ui.component.wear.WearActionButton
import com.resukisu.resukisu.ui.component.wear.WearInfoCard
import com.resukisu.resukisu.ui.component.wear.WearList
import com.resukisu.resukisu.ui.component.wear.WearPageHeader
import com.resukisu.resukisu.ui.component.wear.WearStatusItem
import com.resukisu.resukisu.ui.viewmodel.FlashUiAction
import com.resukisu.resukisu.ui.viewmodel.FlashUiEvent
import com.resukisu.resukisu.ui.viewmodel.FlashViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun WearModuleInstallPage(
    uri: String,
    requestId: Int,
    onBack: () -> Unit,
    onInstalled: () -> Unit,
) {
    val viewModel = koinViewModel<FlashViewModel>(key = "wear-module-install-$requestId")
    val isUriAccessible = koinInject<IsModuleUriAccessibleUseCase>()
    val takeUriPermission = koinInject<TakeModuleUriPermissionUseCase>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fileError by remember(uri) { mutableStateOf(false) }
    var installError by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri, viewModel) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { event ->
                if (event is FlashUiEvent.Completed && event.code == 0) onInstalled()
                if (event is FlashUiEvent.Error) installError = true
            }
        }
        val accessible = withContext(Dispatchers.IO) {
            runCatching {
                if (!isUriAccessible(uri)) false
                else {
                    takeUriPermission(uri)
                    true
                }
            }.getOrDefault(false)
        }
        if (accessible) viewModel.dispatch(FlashUiAction.Start(FlashOperation.Module(uri)))
        else fileError = true
    }

    val status = when {
        fileError -> stringResource(R.string.wear_module_file_unreadable)
        installError -> stringResource(R.string.operation_failed)
        state.exitCode == null -> stringResource(R.string.wear_installing_module)
        state.exitCode == 0 -> stringResource(R.string.flash_success)
        else -> stringResource(R.string.flash_failed)
    }
    val statusIcon = when {
        fileError || installError || state.exitCode != null && state.exitCode != 0 -> Icons.TwoTone.Error
        state.exitCode == 0 -> Icons.TwoTone.CheckCircle
        else -> Icons.TwoTone.Extension
    }
    val recentOutput = remember(state.output) {
        state.output.lineSequence().filter(String::isNotBlank).toList().takeLast(8).joinToString("\n")
    }

    WearList(isLoading = !fileError && !installError && state.exitCode == null) { spec ->
        item { WearPageHeader(spec, Icons.TwoTone.Extension, stringResource(R.string.wear_install_module)) }
        item {
            WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowBack,
                stringResource(R.string.wear_back), onBack)
        }
        item { WearStatusItem(spec, statusIcon, status) }
        if (!fileError && state.exitCode == 0 && state.showReboot) {
            item {
                WearStatusItem(spec, Icons.TwoTone.Extension, stringResource(R.string.reboot_to_apply))
            }
        }
        if (!fileError && recentOutput.isNotBlank()) {
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    Text(recentOutput, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
