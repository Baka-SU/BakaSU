package org.bakasu.bakasu.ui.wear

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.koin.android.ext.android.inject

/** Some watches translate their swipe into KEYCODE_BACK instead of a system back gesture. */
abstract class WearActivity : ComponentActivity() {
    private val getWearPreference: GetStringPreferenceUseCase by inject()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = if (
        event.keyCode == KeyEvent.KEYCODE_BACK && getWearPreference("wear_block_back_key", "false").toBoolean()
    ) {
        true
    } else {
        super.dispatchKeyEvent(event)
    }
}
