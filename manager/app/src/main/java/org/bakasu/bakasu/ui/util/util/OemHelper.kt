package org.bakasu.bakasu.ui.util

import android.annotation.SuppressLint

@SuppressLint("PrivateApi")
private fun getSystemProperty(key: String): String = try {
    val props = Class.forName("android.os.SystemProperties")
    props.getMethod("get", String::class.java).invoke(null, key) as? String ?: ""
} catch (_: Throwable) {
    ""
}

fun isMiui(): Boolean = getSystemProperty("ro.miui.ui.version.name").isNotEmpty()

fun isHyperOS(): Boolean = getSystemProperty("ro.mi.os.version.name").isNotEmpty()

fun isColorOS(): Boolean = getSystemProperty("ro.build.version.oplus.api").isNotEmpty() || getSystemProperty("ro.vendor.oplus.market.name").isNotEmpty()
