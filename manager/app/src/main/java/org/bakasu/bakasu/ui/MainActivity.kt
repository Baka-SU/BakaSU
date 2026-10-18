package org.bakasu.bakasu.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.StartupState
import org.bakasu.bakasu.domain.usecase.ApplyLanguageUseCase
import org.bakasu.bakasu.domain.usecase.EnsureManagerInstalledUseCase
import org.bakasu.bakasu.domain.usecase.ObserveStartupStateUseCase
import org.bakasu.bakasu.ui.activity.util.ThemeUtils
import org.bakasu.bakasu.ui.theme.KernelSUTheme
import org.bakasu.bakasu.ui.viewmodel.HomeUiAction
import org.bakasu.bakasu.ui.viewmodel.HomeViewModel
import org.bakasu.bakasu.ui.viewmodel.ModuleUiAction
import org.bakasu.bakasu.ui.viewmodel.ModuleViewModel
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsUiEvent
import org.bakasu.bakasu.ui.viewmodel.SettingsViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModel()
    private val moduleViewModel: ModuleViewModel by viewModel()
    private val settingsViewModel: SettingsViewModel by viewModel()
    private val observeStartupState: ObserveStartupStateUseCase by inject()
    private val ensureManagerInstalled: EnsureManagerInstalledUseCase by inject()
    private val themeUtils: ThemeUtils by inject()
    private val applyLanguage: ApplyLanguageUseCase by inject()
    private val startupState by lazy { observeStartupState() }

    private var isInitialized = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(applyLanguage::invoke))
    }

    private val intentChannel = Channel<Intent>(capacity = Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            val splashScreen = installSplashScreen()

            // Enable edge to edge
            enableEdgeToEdge()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            super.onCreate(savedInstanceState)

            splashScreen.setKeepOnScreenCondition {
                when (startupState.value) {
                    StartupState.Loading -> true
                    StartupState.Ready -> !homeViewModel.uiState.value.isInitialDataLoaded
                    is StartupState.Failed -> false
                }
            }

            // Keep the home state active even when an incoming intent opens another screen.
            lifecycleScope.launch {
                homeViewModel.uiState.first { it.isInitialDataLoaded }
            }

            lifecycleScope.launch { ensureManagerInstalled() }
            lifecycleScope.launch {
                settingsViewModel.events.collect { event ->
                    when (event) {
                        is SettingsUiEvent.Error -> if (event.message.isNotBlank()) {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG)
                                .show()
                        }

                        is SettingsUiEvent.Message -> {
                            val message = event.formatArg?.let {
                                getString(event.stringResource, it)
                            } ?: getString(event.stringResource)
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        }

                        SettingsUiEvent.RestartActivity -> recreate()
                    }
                }
            }

            // Initialize app state once.
            if (!isInitialized) {
                initializeData()
                isInitialized = true
            }

            if (savedInstanceState == null) intent?.let { intentChannel.trySend(it) }

            setContent {
                KernelSUTheme {
                    when (val state = startupState.collectAsStateWithLifecycle().value) {
                        is StartupState.Failed -> StartupFailureContent(state.message)
                        else -> NavContainer(settingsViewModel, intentChannel)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentChannel.trySend(intent)
    }

    private fun initializeData() {
        lifecycleScope.launch {
            try {
                homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Initialize theme settings.
        themeUtils.initializeThemeSettings(settingsViewModel)
    }

    override fun onResume() {
        try {
            super.onResume()
            themeUtils.onActivityResume()
            synchronizeUiSettings()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun synchronizeUiSettings() {
        if (!isInitialized) return

        settingsViewModel.dispatch(SettingsUiAction.Initialize)
        moduleViewModel.dispatch(ModuleUiAction.ReloadSettings)
    }

    override fun onPause() {
        try {
            super.onPause()
            themeUtils.onActivityPause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@androidx.compose.runtime.Composable
private fun StartupFailureContent(reason: String) {
    Column(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
