package org.bakasu.bakasu.ui.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.bakasu.bakasu.domain.model.StartupState
import org.bakasu.bakasu.domain.usecase.ApplyLanguageUseCase
import org.bakasu.bakasu.domain.usecase.EnsureManagerInstalledUseCase
import org.bakasu.bakasu.domain.usecase.ObserveStartupStateUseCase
import org.bakasu.bakasu.ui.viewmodel.HomeUiAction
import org.bakasu.bakasu.ui.viewmodel.HomeViewModel
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiAction
import org.bakasu.bakasu.ui.viewmodel.SuperUserViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/** The Manager on watches; [org.bakasu.bakasu.ui.MainActivity] hands watch launches over to it. */
class WearMainActivity : WearActivity() {
    private val homeViewModel: HomeViewModel by viewModel()
    private val superUserViewModel: SuperUserViewModel by viewModel()
    private val observeStartupState: ObserveStartupStateUseCase by inject()
    private val ensureManagerInstalled: EnsureManagerInstalledUseCase by inject()
    private val applyLanguage: ApplyLanguageUseCase by inject()
    private val intentChannel = Channel<Intent>(capacity = Channel.BUFFERED)

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(applyLanguage::invoke))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { ensureManagerInstalled() }
        // As on the phone, Home and the app list start loading at launch.
        if (savedInstanceState == null) {
            homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
            superUserViewModel.dispatch(SuperUserUiAction.Refresh)
            intent?.let { intentChannel.trySend(it) }
        }
        val startupState = observeStartupState()
        setContent {
            WearManagerTheme {
                when (val state = startupState.collectAsStateWithLifecycle().value) {
                    StartupState.Loading -> WearStartupStatus()
                    is StartupState.Failed -> WearStartupStatus(state.message)
                    StartupState.Ready -> WearManagerScreen(intentChannel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentChannel.trySend(intent)
    }
}
