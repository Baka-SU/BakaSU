package org.bakasu.bakasu.ui.wear

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.ui.viewmodel.WearPreference
import org.bakasu.bakasu.ui.viewmodel.wear
import org.koin.android.ext.android.inject

/**
 * The base of the Wear activities. Some watches translate their swipe into KEYCODE_BACK instead of a
 * system back gesture, which the block-back-key option swallows; standard SAF requests fall back to
 * the app's own picker where the device has no usable one, or when the picker option asks for it.
 */
abstract class WearActivity : ComponentActivity() {
    private val getPreference: GetStringPreferenceUseCase by inject()

    // This overrides the public Activity.dispatchKeyEvent; lint only sees the androidx.core
    // override in between, which is restricted to its own library group.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = if (
        event.keyCode == KeyEvent.KEYCODE_BACK && getPreference.wear(WearPreference.BlockBackKey).toBoolean()
    ) {
        true
    } else {
        super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(withDocumentPickerFallback(intent, getPreference.wear(WearPreference.Picker)), requestCode, options)
    }
}
