package org.bakasu.bakasu.data.haptics

import android.os.Build
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat
import org.bakasu.bakasu.data.AppSettingsRepository

/**
 * Chooses where Wear Compose reads its crown scroll haptic constants, through the miwearhaptics
 * compatibility layer. On a Xiaomi Watch 5, or with the Xiaomi Watch 5 mode turned on, a missing
 * Google Wear SDK falls back to the Xiaomi SDK; otherwise only the Google SDK is used, as before.
 */
class CrownHapticsRepository(
    private val settings: AppSettingsRepository,
) {
    /**
     * A Xiaomi watch whose model or device name is one of the Xiaomi Watch 5 names, or whose model
     * name reads "Watch 5", with or without spaces.
     */
    val isXiaomiWatch5: Boolean =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) &&
            (
                Build.MODEL in XIAOMI_WATCH_5_MODELS ||
                    Build.DEVICE in XIAOMI_WATCH_5_DEVICES ||
                    Build.MODEL.orEmpty().replace(" ", "").contains("watch5", ignoreCase = true)
                )

    fun isXiaomiWatch5ModeEnabled(): Boolean = settings.getBoolean(KEY_XIAOMI_WATCH_5_MODE, false)

    fun setXiaomiWatch5ModeEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_XIAOMI_WATCH_5_MODE, enabled)
    }

    /**
     * Sets the policy before any screen reads the constants. Wear Compose keeps them for the life
     * of the process, so a changed setting applies after the app restarts.
     */
    fun applyPolicy() {
        val xiaomi = isXiaomiWatch5 || isXiaomiWatch5ModeEnabled()
        WearHapticFeedbackConstantsCompat.setPolicy(
            if (xiaomi) WearHapticFeedbackConstantsCompat.Policy.GOOGLE_FIRST else WearHapticFeedbackConstantsCompat.Policy.GOOGLE_ONLY,
        )
    }

    private companion object {
        const val KEY_XIAOMI_WATCH_5_MODE = "wear_xiaomi_watch5_mode"

        /**
         * The Xiaomi Watch 5 (O61) names from MobileModels (brands/xiaomi-wear): the Bluetooth
         * version is grass / M2501W1, the LTE version grasslte / M2505W1 and the global version
         * blenny / M2501W1.
         */
        val XIAOMI_WATCH_5_MODELS = setOf("M2501W1", "M2505W1")
        val XIAOMI_WATCH_5_DEVICES = setOf("grass", "grasslte", "blenny")
    }
}
