package org.bakasu.bakasu.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.AppSettingsRepository
import org.koin.compose.koinInject

@Stable
class ThemeConfig(
    private val defaultSeedColor: () -> Int,
) {
    var customBackgroundUri by mutableStateOf<Uri?>(null)
    var backgroundSeedColor by mutableIntStateOf(0)
    var backgroundDim by mutableFloatStateOf(0f)
    var forceDarkMode by mutableStateOf<Boolean?>(null)
    var seedColor by mutableIntStateOf(defaultSeedColor())
    var useDynamicColor by mutableStateOf(false)
    var useBuiltinMonoFont by mutableStateOf(false)
    var monetCompatSeedColor by mutableIntStateOf(defaultSeedColor())
    var dynamicColorSpec by mutableStateOf(ColorSpec.SpecVersion.SPEC_2021)
    var dynamicPaletteStyle by mutableStateOf(PaletteStyle.TonalSpot)

    var backgroundImageLoaded by mutableStateOf(false)
    var isThemeChanging by mutableStateOf(false)
    var isHighContrastMode by mutableStateOf(false)
    private val _isEnableBlur = mutableStateOf(false)
    var isEnableBlur: Boolean
        get() = _isEnableBlur.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        set(value) {
            _isEnableBlur.value = value
        }
    private val _isEnableBlurExp = mutableStateOf(false)
    var isEnableBlurExp: Boolean
        get() = _isEnableBlurExp.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        set(value) {
            _isEnableBlurExp.value = value
        }
    var isUseBackgroundSeedColor by mutableStateOf(false)
    var bottomBarStyle by mutableStateOf(BottomBarStyle.MATERIAL3_EXPRESSIVE)

    fun resetBackgroundState() {
        backgroundImageLoaded = false
        isThemeChanging = true
    }

    fun reset() {
        customBackgroundUri = null
        backgroundSeedColor = 0
        forceDarkMode = null
        seedColor = defaultSeedColor()
        useDynamicColor = false
        monetCompatSeedColor = defaultSeedColor()
        dynamicColorSpec = ColorSpec.SpecVersion.SPEC_2021
        dynamicPaletteStyle = PaletteStyle.TonalSpot
        backgroundImageLoaded = false
        isThemeChanging = false
    }
}

private val BuiltinspaceFontFamily = FontFamily(
    Font(org.bakasu.bakasu.R.font.jetbrains_mono),
)

@Composable
fun monospaceFontFamily(): FontFamily {
    val themeConfig = koinInject<ThemeConfig>()
    return if (themeConfig.useBuiltinMonoFont) {
        BuiltinspaceFontFamily
    } else {
        FontFamily.Monospace
    }
}

@Stable
class BackgroundRenderState {
    var imagePainter: AsyncImagePainter? by mutableStateOf(null)
    var imageBitmap: ImageBitmap? by mutableStateOf(null)
    var blurImageBitmap: ImageBitmap? by mutableStateOf(null)
    var blurViewportSize by mutableStateOf(IntSize(0, 0))
    var blurFrameTick by mutableIntStateOf(0)
    var scrollActive by mutableStateOf(false)
    private val scrollingPagers = mutableStateMapOf<Any, Unit>()
    val pagerScrollActive: Boolean
        get() = scrollingPagers.isNotEmpty()
    var seedColor by mutableIntStateOf(0)

    internal fun updatePagerScroll(owner: Any, active: Boolean) {
        if (active) {
            scrollingPagers[owner] = Unit
        } else {
            scrollingPagers.remove(owner)
        }
    }
}

val LocalBackgroundRenderState = compositionLocalOf<BackgroundRenderState> {
    error("BackgroundRenderState is not provided")
}

class BackgroundManager(
    private val settings: AppSettingsRepository,
    private val config: ThemeConfig,
    private val cardConfig: CardConfig,
) {
    private val tag = "BackgroundManager"

    private companion object {
        /** Name used before backgrounds were stored under a per-save name; still deleted on replace. */
        const val LEGACY_BACKGROUND_FILE_NAME = "custom_background.jpg"

        /** Prefix of the per-save background names; the suffix keeps every saved Uri distinct. */
        const val BACKGROUND_FILE_PREFIX = "custom_background_"
    }

    fun saveBackgroundDim(dim: Float) {
        config.backgroundDim = dim
        settings.putFloat("background_dim", dim)
    }

    fun saveEnableBlur(enable: Boolean) {
        config.isEnableBlur = enable
        settings.putBoolean("enable_blur", enable)
    }

    fun saveEnableBlurExp(enable: Boolean) {
        config.isEnableBlurExp = enable
        settings.putBoolean("enable_blur_exp", enable)
    }

    fun saveBottomBarStyle(style: BottomBarStyle) {
        config.bottomBarStyle = style
        settings.putInt("bottom_bar_style", style.ordinal)
    }

    fun saveUseBackgroundSeedColor(enable: Boolean) {
        config.isUseBackgroundSeedColor = enable
        settings.putBoolean("use_background_seed_color", enable)
    }

    fun saveBackgroundSeedColor(color: Int) {
        config.backgroundSeedColor = color
        settings.putInt("cached_seed_color", color)
    }

    fun saveEnableHighContrastMode(enable: Boolean) {
        config.isHighContrastMode = enable
        settings.putBoolean("high_contrast_mode", enable)
    }

    suspend fun saveAndApplyCustomBackground(
        context: Context,
        uri: Uri,
    ): Boolean {
        val appContext = context.applicationContext
        return try {
            val previousUri = config.customBackgroundUri
            val finalUri = withContext(Dispatchers.IO) {
                copyImageToInternalStorage(appContext, uri)
            } ?: return false

            saveBackgroundUri(finalUri)
            saveBackgroundSeedColor(0)
            config.customBackgroundUri = finalUri
            cardConfig.updateBackground(true)
            withContext(Dispatchers.IO) {
                clearBackgroundBlurCache(appContext)
                deleteBackgroundFile(appContext, previousUri)
            }
            config.backgroundImageLoaded = false

            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "保存背景失败: ${e.message}", e)
            false
        }
    }

    fun clearCustomBackground(context: Context) {
        val previousUri = config.customBackgroundUri
        saveBackgroundUri(null)
        saveBackgroundSeedColor(0)
        config.customBackgroundUri = null
        cardConfig.updateBackground(false)
        clearBackgroundBlurCache(context)
        deleteBackgroundFile(context, previousUri)
        config.backgroundImageLoaded = false
    }

    fun loadCustomBackground() {
        val prefs = settings
        val uriString = prefs.getString("custom_background", null)

        val newUri = uriString?.toUri()
        config.backgroundSeedColor = if (newUri != null) prefs.getInt("cached_seed_color", 0) else 0

        if (config.customBackgroundUri?.toString() != newUri?.toString()) {
            Log.d(tag, "加载自定义背景: $uriString")
            config.customBackgroundUri = newUri
            config.backgroundImageLoaded = false
            cardConfig.updateBackground(newUri != null)
        }

        config.backgroundDim = prefs.getFloat("background_dim", 0f).coerceIn(0f, 1f)
        // Read with the other background settings: the Wear theme loads them only through here, so
        // leaving the blur out turned it off again after the app restarted.
        config.isEnableBlur = prefs.getBoolean("enable_blur", false)
        config.isEnableBlurExp = prefs.getBoolean("enable_blur_exp", false)
        config.isUseBackgroundSeedColor = prefs.getBoolean("use_background_seed_color", false)
        config.isHighContrastMode = prefs.getBoolean("high_contrast_mode", false)
    }

    private fun saveBackgroundUri(uri: Uri?) {
        settings.putString("custom_background", uri?.toString())
    }

    fun clearBackgroundBlurCache(context: Context) {
        runCatching {
            backgroundBlurCacheFile(context).delete()
            legacyBackgroundBlurCacheDir(context).deleteRecursively()
        }.onFailure {
            Log.w(tag, "Failed to clear background blur cache: ${it.message}")
        }
    }

    /**
     * Copies the picked image into the app's own storage under a fresh, per-save name.
     *
     * The name has to change on every save: Coil compares image requests field by field and skips
     * a request equal to the one already loaded, so reusing a fixed name would leave the previous
     * image on screen after the user picks another one.
     */
    private fun copyImageToInternalStorage(context: Context, uri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "$BACKGROUND_FILE_PREFIX${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)

            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
            inputStream.close()

            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(tag, "复制图片失败: ${e.message}", e)
            null
        }
    }

    /**
     * Deletes a stored background image once it is no longer referenced. Only files this class
     * writes qualify — the legacy fixed name and its per-save successors — so the blur cache,
     * which shares the same directory under a different name, is left alone.
     */
    private fun deleteBackgroundFile(context: Context, uri: Uri?) {
        val file = uri?.path?.let(::File) ?: return
        val isStoredBackground = file.parentFile == context.filesDir &&
            (file.name == LEGACY_BACKGROUND_FILE_NAME || file.name.startsWith(BACKGROUND_FILE_PREFIX))
        if (!isStoredBackground) return
        runCatching { file.delete() }
            .onFailure { Log.w(tag, "删除背景文件失败: ${it.message}") }
    }
}

internal suspend fun Bitmap.extractSeedColor(
    maxColors: Int = 128,
    fallbackColorArgb: Int = -12417548,
): Int = withContext(Dispatchers.IO) {
    val scaledBitmap = this@extractSeedColor.scale(128, 128)

    val width = scaledBitmap.width
    val height = scaledBitmap.height
    val pixels = IntArray(width * height)
    scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val colorToCountMap: Map<Int, Int> = QuantizerCelebi.quantize(pixels, maxColors)
    val sortedColors: List<Int> = Score.score(colorToCountMap, 10, fallbackColorArgb, true)

    if (scaledBitmap != this@extractSeedColor) {
        scaledBitmap.recycle()
    }

    sortedColors.firstOrNull() ?: fallbackColorArgb
}

internal fun backgroundBlurCacheFile(context: Context): File = File(context.filesDir, "blured_custom_background.jpg")
internal fun legacyBackgroundBlurCacheDir(context: Context): File = File(context.filesDir, "background_blur_cache")

@Composable
@ReadOnlyComposable
fun isInDarkTheme(themeMode: Boolean?): Boolean = when (themeMode) {
    // 强制深色
    true -> true

    // 强制浅色
    false -> false

    null -> isSystemInDarkTheme() // 跟随系统
}
