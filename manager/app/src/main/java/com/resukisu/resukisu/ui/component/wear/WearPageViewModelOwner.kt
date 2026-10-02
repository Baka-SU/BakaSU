package com.resukisu.resukisu.ui.component.wear

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStoreOwner
import org.koin.compose.viewmodel.koinViewModel
import com.resukisu.resukisu.ui.viewmodel.WearPageStoresViewModel
import kotlinx.coroutines.flow.Flow

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
fun rememberWearPageViewModelOwner(key: String): ViewModelStoreOwner {
    val stores = koinViewModel<WearPageStoresViewModel>()
    return remember(stores, key) { stores.acquire(key) }
}

@Composable
fun ReleaseWearPageViewModels(key: String, running: Flow<Boolean>? = null) {
    val stores = koinViewModel<WearPageStoresViewModel>()
    val activity = LocalActivity.current
    DisposableEffect(stores, key) {
        onDispose {
            if (activity?.isChangingConfigurations != true) stores.release(key, running)
        }
    }
}
