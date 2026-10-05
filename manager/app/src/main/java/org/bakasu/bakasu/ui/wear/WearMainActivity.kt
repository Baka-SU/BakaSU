package org.bakasu.bakasu.ui.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import org.bakasu.bakasu.domain.model.StartupState
import org.bakasu.bakasu.domain.usecase.ApplyLanguageUseCase
import org.bakasu.bakasu.domain.usecase.EnsureManagerInstalledUseCase
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.ObserveStartupStateUseCase
import org.bakasu.bakasu.ui.viewmodel.HomeUiAction
import org.bakasu.bakasu.ui.viewmodel.HomeViewModel
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiAction
import org.bakasu.bakasu.ui.viewmodel.SuperUserViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/** The Manager on watches; [org.bakasu.bakasu.ui.MainActivity] hands watch launches over to it. */
class WearMainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModel()
    private val superUserViewModel: SuperUserViewModel by viewModel()
    private val observeStartupState: ObserveStartupStateUseCase by inject()
    private val ensureManagerInstalled: EnsureManagerInstalledUseCase by inject()
    private val applyLanguage: ApplyLanguageUseCase by inject()
    private val getPreference: GetStringPreferenceUseCase by inject()

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(applyLanguage::invoke))
    }

    // Standard SAF requests fall back to the app's own picker where the device has no usable one.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(withDocumentPickerFallback(intent, getPreference("wear_file_picker")), requestCode, options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { ensureManagerInstalled() }
        // As on the phone, Home and the app list start loading at launch.
        if (savedInstanceState == null) {
            homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
            superUserViewModel.dispatch(SuperUserUiAction.Refresh)
        }
        val startupState = observeStartupState()
        setContent {
            WearManagerTheme {
                when (val state = startupState.collectAsStateWithLifecycle().value) {
                    StartupState.Loading -> WearStartupStatus()
                    is StartupState.Failed -> WearStartupStatus(state.message)
                    StartupState.Ready -> WearManagerScreen()
                }
            }
        }
    }
}
