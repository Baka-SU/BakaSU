package org.bakasu.bakasu.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Run :app:generateNormalReleaseBaselineProfile or :app:generateWearReleaseBaselineProfile
 * with only the corresponding device selected through ANDROID_SERIAL.
 * Match the app language to the device language for the resource-backed Wear selectors.
 * Superuser and Modules require a working KernelSU environment on the watch.
 */
@RunWith(Parameterized::class)
class BaselineProfileGenerator(private val deviceType: String) {

    @Before
    fun checkDevice() {
        assumeTrue((deviceType == "wear") == isWearDevice())
    }

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = BuildConfig.TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        if (deviceType == "wear") waitForWearHome()
    }

    @Test
    fun mainUserJourneys() = baselineProfileRule.collect(
        packageName = BuildConfig.TARGET_PACKAGE,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        if (deviceType == "wear") {
            exerciseWearPages()
        } else {
            scrollHomePagerBackAndForth()
        }
    }

    companion object {
        // The producer copies profiles by filename, so each form factor needs distinct test names.
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun deviceTypes(): List<String> = listOf(BuildConfig.FLAVOR)
    }
}
