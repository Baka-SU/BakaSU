package org.bakasu.bakasu.data.theme

import android.app.Application
import android.os.Build
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

class MonetCompatColorSource(
    private val application: Application,
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
    }

    fun seedColor(): Int = colors.value

    suspend fun refresh() = refreshMutex.withLock {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@withLock
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
    }
}
