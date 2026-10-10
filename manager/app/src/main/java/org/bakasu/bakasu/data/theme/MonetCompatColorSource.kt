package org.bakasu.bakasu.data.theme

import android.app.Application
import android.util.TypedValue
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import dev.kdrag0n.monet.theme.ColorScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.AppSettingsRepository

class MonetCompatColorSource(
    private val application: Application,
    private val settings: AppSettingsRepository,
) {
    private val fallbackColor = TypedValue().let {
        application.theme.resolveAttribute(android.R.attr.colorPrimary, it, true)
        it.data
    }
    private val mutableSeedColor = MutableStateFlow(fallbackColor)
    val colors = mutableSeedColor.asStateFlow()
    private val refreshMutex = Mutex()
    private var monet: MonetCompat? = null

    fun initialize() {
        // Settings are preloaded before this call. No wallpaper IPC is needed for startup.
        mutableSeedColor.value = settings.getInt("wallpaper_seed_color_cache", fallbackColor)
    }

    fun seedColor(): Int = colors.value

    suspend fun refresh() = refreshMutex.withLock {
        try {
            val instance = withContext(Dispatchers.Main) {
                monet ?: run {
                    MonetCompat.useSystemColorsOnAndroid12 = false
                    runCatching { MonetCompat.enablePaletteCompat() }
                    MonetCompat.setup(application).also { instance ->
                        instance.defaultPrimaryColor = fallbackColor
                        instance.addMonetColorsChangedListener(
                            object : MonetColorsChangedListener {
                                override fun onMonetColorsChanged(
                                    monet: MonetCompat,
                                    monetColors: ColorScheme,
                                    isInitialChange: Boolean,
                                ) {
                                    publish(monet.wallpaperPrimaryColor ?: fallbackColor)
                                }
                            },
                        )
                        monet = instance
                    }
                }
            }
            val color = withContext(Dispatchers.IO) {
                instance.getSelectedWallpaperColor()
            }
            publish(color ?: fallbackColor)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep the last successful color when wallpaper access fails temporarily.
        }
    }

    private fun publish(color: Int) {
        mutableSeedColor.value = color
        if (settings.getInt("wallpaper_seed_color_cache", fallbackColor) != color) {
            settings.putInt("wallpaper_seed_color_cache", color)
        }
    }
}
