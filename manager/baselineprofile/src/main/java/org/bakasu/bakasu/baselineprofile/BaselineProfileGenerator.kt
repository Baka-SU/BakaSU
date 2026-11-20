package org.bakasu.bakasu.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Run :app:generateBaselineProfile with both a phone and a watch connected to merge both profiles.
 * Match the app language to the device language for the resource-backed Wear selectors.
 * Superuser and Modules require a working KernelSU environment on the watch.
 */
@RunWith(Parameterized::class)
class BaselineProfileGenerator(private val deviceType: String) {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = "com.resukisu.resukisu",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        if (deviceType == "wear") waitForWearHome()
    }

    @Test
    fun mainUserJourneys() = baselineProfileRule.collect(
        packageName = "com.resukisu.resukisu",
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
        fun deviceTypes(): List<String> = listOf(if (isWearDevice()) "wear" else "phone")
    }
}
