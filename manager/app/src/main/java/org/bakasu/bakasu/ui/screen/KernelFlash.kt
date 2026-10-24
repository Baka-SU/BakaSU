package org.bakasu.bakasu.ui.screen.kernelFlash

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.component.FlashOutputScreen
import org.bakasu.bakasu.ui.navigation.LocalNavigator
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.showReplacingSnackbar
import org.bakasu.bakasu.ui.viewmodel.FlashState
import org.bakasu.bakasu.ui.viewmodel.FlashingStatus
import org.bakasu.bakasu.ui.viewmodel.KernelFlashUiAction
import org.bakasu.bakasu.ui.viewmodel.KernelFlashUiEvent
import org.bakasu.bakasu.ui.viewmodel.KernelFlashViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KernelFlashScreen(
    kernelUri: String,
    selectedSlot: String? = null,
    skipKsud: Boolean = false,
) {
    val context = LocalContext.current

    val snackBarHost = LocalSnackbarHost.current
    val viewModel = koinViewModel<KernelFlashViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val flashState = uiState.flash
    val horizonFlashComplete = stringResource(R.string.horizon_flash_complete)
    val logText = buildString {
        append(flashState.logs.joinToString("\n"))
        if (flashState.error.isNotEmpty()) append("\n${flashState.error}\n")
        if (flashState.isCompleted) append("\n$horizonFlashComplete\n\n\n")
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is KernelFlashUiEvent.Error -> if (event.message.isNotBlank()) {
                    snackBarHost.showReplacingSnackbar(event.message)
                }
            }
        }
    }

    // 开始刷写
    LaunchedEffect(kernelUri, selectedSlot, skipKsud) {
        viewModel.dispatch(KernelFlashUiAction.Start(kernelUri, selectedSlot, skipKsud))
    }

    LaunchedEffect(flashState.isCompleted, uiState.autoExit) {
        if (flashState.isCompleted && uiState.autoExit) {
            delay(1500.milliseconds)
            viewModel.dispatch(KernelFlashUiAction.ConsumeAutoExit)
            (context as? ComponentActivity)?.finish()
        }
    }

    val navigator = LocalNavigator.current

    BackHandler(flashState.isFlashing) {
        // deny
    }

    val commonState = FlashState(
        status = when {
            flashState.error.isNotEmpty() -> FlashingStatus.FAILED
            flashState.isCompleted -> FlashingStatus.SUCCESS
            else -> FlashingStatus.FLASHING
        },
        output = logText,
        showReboot = flashState.isCompleted,
        successTitleRes = R.string.kernel_flash_complete,
    )
    FlashOutputScreen(
        state = commonState,
        onBack = {
            if (!flashState.isFlashing) navigator.pop()
        },
        logFilePrefix = "KernelSU_kernel_flash_log",
        logContent = uiState.fullLog,
        onReboot = { viewModel.dispatch(KernelFlashUiAction.Reboot) },
    )
}
