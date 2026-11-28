package org.bakasu.bakasu.ui.wear

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.usecase.GetStringPreferenceUseCase
import org.bakasu.bakasu.ui.viewmodel.WearPreference
import org.bakasu.bakasu.ui.viewmodel.wear
import org.bakasu.bakasu.ui.wear.component.WearToastState
import org.koin.android.ext.android.inject

/**
 * Applies the Wear back-key setting and document picker fallback. Some watches report
 * swipe-back as KEYCODE_BACK rather than a system back gesture.
 */
abstract class WearActivity : ComponentActivity() {
    private val getPreference: GetStringPreferenceUseCase by inject()
    protected val toastState = WearToastState()

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
        val pickerMode = getPreference.wear(WearPreference.Picker)
        if (pickerMode == "system" && isSystemDocumentPickerUnavailable(intent)) {
            toastState.show(getString(R.string.wear_no_system_file_picker))
            super.onActivityResult(requestCode, RESULT_CANCELED, null)
            return
        }
        try {
            super.startActivityForResult(withDocumentPickerFallback(intent, pickerMode), requestCode, options)
        } catch (error: ActivityNotFoundException) {
            if (intent.action !in DocumentActions) throw error
            toastState.show(getString(R.string.wear_no_system_file_picker))
            super.onActivityResult(requestCode, RESULT_CANCELED, null)
        }
    }
}
