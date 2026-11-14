package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import org.bakasu.bakasu.domain.model.InstalledApp
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearAffectedAppsLayoutTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appNamesAndPackageNamesRemainReadableBesideRealIcons() {
        val packageManager = compose.activity.packageManager
        val apps = listOf(compose.activity.packageName, "android").map { packageName ->
            val info = packageManager.getApplicationInfo(packageName, 0)
            InstalledApp(packageName, packageManager.getApplicationLabel(info).toString(), 1000)
        }
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                screenWidthDp = 192
                screenHeightDp = 192
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp).background(MaterialTheme.colorScheme.background).testTag("page")) {
                        AppScaffold(timeText = {}) {
                            WearAffectedAppsPage(InstalledAppGroup(1000, apps.first().packageName, apps), {})
                        }
                    }
                }
            }
        }
        apps.forEachIndexed { index, app ->
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(app.packageName))
            compose.onNodeWithText(app.label).assertIsDisplayed()
            compose.onNodeWithText(app.packageName).assertIsDisplayed()
            // Let the asynchronous package icon request finish before recording the device render.
            compose.mainClock.advanceTimeBy(1000)
            val bitmap = compose.onNodeWithTag("page").captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null), "affected-app-$index-$round-$fontScale.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
