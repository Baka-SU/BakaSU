package org.bakasu.bakasu.baselineprofile

import android.content.pm.PackageManager
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.waitForStableInActiveWindow
import java.util.regex.Pattern

private const val UI_TIMEOUT_MILLIS = 10_000L
private const val SWIPE_STEPS = 30

internal fun isWearDevice(): Boolean = InstrumentationRegistry.getInstrumentation().context
    .packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

internal fun MacrobenchmarkScope.waitForWearHome() {
    waitForWearText("home")
}

/** Exercises the real Wear navigation without changing settings or running root operations. */
internal fun MacrobenchmarkScope.exerciseWearPages() {
    waitForWearHome()
    scrollWearListBackAndForth("home")
    swipeWearPage(forward = true)

    val nextPage = Pattern.compile(
        listOf("superuser", "settings").joinToString("|") { Pattern.quote(wearText(it)) },
    )
    check(device.wait(Until.hasObject(By.text(nextPage)), UI_TIMEOUT_MILLIS)) {
        "Wear pager did not reach Superuser or Settings"
    }
    val fullFeatured = device.hasObject(By.text(wearText("superuser")))
    if (fullFeatured) {
        Log.i("BaselineProfile", "Collecting all four Wear pages with KernelSU")
        scrollWearListBackAndForth("superuser")
        openWearMenu("superuser", "wear_filter_sort")
        clickWearText("wear_filter_sort")
        waitForWearText("show_system_apps")
        device.pressBack()
        waitForWearText("wear_filter_sort")
        device.pressBack()
        waitForWearText("superuser")

        swipeWearPage(forward = true)
        waitForWearText("module")
        scrollWearListBackAndForth("module")
        openWearMenu("module", "wear_sort")
        clickWearText("wear_sort")
        waitForWearText("module_sort_action_first")
        device.pressBack()
        waitForWearText("wear_sort")
        device.pressBack()
        waitForWearText("module")

        swipeWearPage(forward = true)
    } else {
        Log.i("BaselineProfile", "Wear has no full KernelSU environment; collecting Home and Settings only")
    }
    waitForWearText("settings")
    clickWearText("app_settings")
    waitForWearText("wear_settings_updates")
    scrollWearListBackAndForth("app_settings")
    device.pressBack()
    findWearText("settings", scrollDown = false)

    clickWearText("about")
    waitForWearText("app_name")
    scrollWearListBackAndForth("about")
    device.pressBack()
    // The settings list keeps its position after returning from About.
    findWearText("settings", scrollDown = false)

    val returnPages = if (fullFeatured) listOf("module", "superuser", "home") else listOf("home")
    returnPages.forEach { page ->
        swipeWearPage(forward = false)
        findWearText(page, scrollDown = false)
    }
}

private fun MacrobenchmarkScope.swipeWearPage(forward: Boolean) {
    device.waitForStableInActiveWindow()
    val width = device.displayWidth
    // Module rows own horizontal swipe actions; page swipes must start in the header area.
    val y = device.displayHeight / 4
    // The Wear pager reserves a wider left edge for dismiss gestures than the platform does.
    device.swipe(if (forward) width * 4 / 5 else width * 2 / 5, y, if (forward) width / 5 else width * 9 / 10, y, SWIPE_STEPS)
    device.waitForStableInActiveWindow()
}

private fun MacrobenchmarkScope.swipeWearList(down: Boolean) {
    val x = device.displayWidth / 2
    val top = device.displayHeight * 35 / 100
    val bottom = device.displayHeight * 65 / 100
    device.swipe(x, if (down) bottom else top, x, if (down) top else bottom, SWIPE_STEPS)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.scrollWearListBackAndForth(page: String) {
    repeat(2) { swipeWearList(down = true) }
    // Stop at the header: another downward pull on a short list would open its operations menu.
    findWearText(page, scrollDown = false)
}

private fun MacrobenchmarkScope.openWearMenu(page: String, action: String) {
    findWearText(page, scrollDown = false)
    val x = device.displayWidth / 2
    device.swipe(x, device.displayHeight / 4, x, device.displayHeight * 3 / 4, SWIPE_STEPS)
    device.waitForIdle()
    findWearText(action, scrollDown = false)
}

private fun MacrobenchmarkScope.clickWearText(name: String) {
    findWearText(name)
    repeat(10) {
        // Wear snap/fling animations can keep moving hit targets after accessibility becomes idle.
        device.waitForStableInActiveWindow()
        val item = device.findObject(By.clickable(true).hasDescendant(By.text(wearText(name))))
        val centerY = item?.visibleBounds?.centerY()
        // Tap the button's center, away from the scaled text and the round screen's clipped edges.
        if (centerY != null && centerY in device.displayHeight * 35 / 100..device.displayHeight * 65 / 100) {
            item.click()
            device.waitForIdle()
            return
        }
        swipeWearList(down = centerY == null || centerY > device.displayHeight / 2)
    }
    error("Wear item could not be centered: $name")
}

private fun MacrobenchmarkScope.findWearText(name: String, scrollDown: Boolean = true): UiObject2 {
    val selector = By.text(wearText(name))
    repeat(20) {
        device.findObject(selector)?.let { return it }
        swipeWearList(down = scrollDown)
    }
    error("Wear item not found: $name; visible text: " + device.findObjects(By.text(Pattern.compile(".+"))).joinToString { it.text })
}

private fun MacrobenchmarkScope.waitForWearText(name: String) {
    check(device.wait(Until.hasObject(By.text(wearText(name))), UI_TIMEOUT_MILLIS)) {
        "Wear screen did not show: $name; visible text: " +
            device.findObjects(By.text(Pattern.compile(".+"))).joinToString { it.text }
    }
}

private fun MacrobenchmarkScope.wearText(name: String): String {
    val resources = InstrumentationRegistry.getInstrumentation().context.packageManager
        .getResourcesForApplication(packageName)
    return resources.getString(resources.getIdentifier(name, "string", packageName))
}
